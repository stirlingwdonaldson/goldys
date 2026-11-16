"""Single-slot, local-only sandbox broker. Never install on the application VM.

The trusted worker can submit bytes, not container options or host paths. Docker
privilege stays in this service; generated Python only runs inside runsc.
"""
import base64
import json
import os
from pathlib import Path
import select
import socket
import socketserver
import subprocess
import tempfile
import stat
import shutil
import sys
import threading
import time
import uuid

IMAGE = os.environ.get('GOLDYS_ANALYSIS_IMAGE', '')
SOCKET = '/run/goldys-sandbox/broker.sock'
LABEL = 'goldys.analysis.sandbox=true'
MAX_REQUEST = 140 * 1024 * 1024
MAX_OUTPUT = 25 * 1024 * 1024
MAX_TOTAL = 100 * 1024 * 1024
SLOT = threading.Lock()


def docker(*args, timeout=15):
    return subprocess.run(['/usr/bin/docker', *args], check=True, capture_output=True,
                          timeout=timeout)


def filename(value):
    if not isinstance(value, str) or not value or len(value) > 120:
        raise ValueError('Invalid file name')
    if value in ('.', '..') or any(c not in 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789._-' for c in value):
        raise ValueError('Invalid file name')
    return value


def request(value):
    if not isinstance(value, dict) or set(value) - {'code', 'inputs', 'seconds'}:
        raise ValueError('Invalid execution request')
    code = value.get('code')
    if not isinstance(code, str) or not code.strip() or len(code.encode()) > 65536:
        raise ValueError('Code must contain 1–65536 bytes')
    seconds = value.get('seconds', 60)
    if type(seconds) is not int or not 1 <= seconds <= 60:
        raise ValueError('Execution limit must be 1–60 seconds')
    inputs = value.get('inputs', [])
    if not isinstance(inputs, list) or len(inputs) > 5:
        raise ValueError('At most five input files')
    decoded = {}
    total = 0
    for item in inputs:
        if not isinstance(item, dict) or set(item) != {'name', 'base64'}:
            raise ValueError('Invalid input')
        name = filename(item['name'])
        if name in decoded:
            raise ValueError('Duplicate input name')
        data = base64.b64decode(item['base64'], validate=True)
        total += len(data)
        if len(data) > MAX_OUTPUT or total > MAX_TOTAL:
            raise ValueError('Input size limit exceeded')
        decoded[name] = data
    return code, decoded, seconds


def collect_outputs(folder):
    """Read only regular, single-link files after every sandbox process has stopped."""
    outputs = []
    total = 0
    for item in folder.iterdir():
        name = filename(item.name)
        info = item.lstat()
        if not stat.S_ISREG(info.st_mode) or info.st_nlink != 1:
            raise ValueError('Output must be a regular file without links')
        total += info.st_size
        if info.st_size > MAX_OUTPUT or total > MAX_TOTAL or len(outputs) >= 5:
            raise ValueError('Output size/count limit exceeded')
        fd = os.open(item, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
        with os.fdopen(fd, 'rb') as stream:
            payload = stream.read(MAX_OUTPUT + 1)
        if len(payload) != info.st_size:
            raise ValueError('Output length mismatch')
        outputs.append({'name': name, 'base64': base64.b64encode(payload).decode()})
    return outputs


def disconnected(connection):
    if select.select([connection], [], [], 0)[0]:
        return not connection.recv(1, socket.MSG_PEEK | socket.MSG_DONTWAIT)
    return False


def execute(value, connection):
    code, inputs, seconds = request(value)
    value.clear()
    name = 'goldys-analysis-' + uuid.uuid4().hex
    with tempfile.TemporaryDirectory(prefix='stage-', dir='/var/lib/goldys-sandbox') as scratch:
        root = Path(scratch)
        root.chmod(0o755)
        (root / 'script.py').write_text(code)
        (root / 'script.py').chmod(0o444)
        folder = root / 'inputs'
        folder.mkdir(mode=0o755)
        folder.chmod(0o755)
        for key, content in inputs.items():
            (folder / key).write_bytes(content)
            (folder / key).chmod(0o444)
        inputs.clear()
        content = None
        output = root / 'output'
        output.mkdir()
        subprocess.run(['/usr/bin/mount', '-t', 'tmpfs', '-o',
                        'size=100m,nosuid,nodev,noexec,uid=65532,gid=65532,mode=0700',
                        'tmpfs', str(output)], check=True, capture_output=True)
        try:
            docker('create', '--name', name, '--label', LABEL, '--runtime=runsc',
                   '--network=none', '--read-only', '--user=65532:65532',
                   '--cap-drop=ALL', '--security-opt=no-new-privileges',
                   '--memory=768m', '--memory-swap=768m', '--cpus=1', '--pids-limit=64',
                   '--ulimit=nofile=128:128', '--ulimit=fsize=26214400:26214400',
                   '--log-driver=local', '--log-opt=max-size=1m', '--log-opt=max-file=1', '--log-opt=compress=false',
                   '--tmpfs=/tmp:rw,noexec,nosuid,nodev,size=128m,mode=1777',
                   '--mount', 'type=bind,src=' + str(output) + ',dst=/output',
                   '--mount', 'type=bind,src=' + str(root / 'script.py') + ',dst=/script.py,readonly',
                   '--mount', 'type=bind,src=' + str(folder) + ',dst=/inputs,readonly',
                   IMAGE, 'python3', '-I', '-B', '/script.py')
            docker('start', name)
            end = time.monotonic() + seconds
            status = 'COMPLETED'
            while True:
                state = json.loads(docker('inspect', '--format={{json .State}}', name).stdout)
                if not state['Running']:
                    if state['ExitCode'] != 0:
                        status = 'FAILED'
                    break
                if disconnected(connection):
                    status = 'CANCELLED'
                    break
                if time.monotonic() >= end:
                    status = 'LIMIT_EXCEEDED'
                    break
                time.sleep(0.1)
            if status != 'COMPLETED':
                docker('kill', name) if state['Running'] else None
            # Remove descendants and all writable container access before reading bytes.
            captured = docker('logs', '--tail=200', name)
            logs = (captured.stdout + captured.stderr)[-65536:].decode('utf-8', errors='replace')
            outputs = collect_outputs(output) if status == 'COMPLETED' else []
            return {'status': status, 'stdout': logs, 'outputs': outputs}
        finally:
            try:
                docker('rm', '-f', name)
            finally:
                subprocess.run(['/usr/bin/umount', str(output)], check=True, capture_output=True)


class Handler(socketserver.StreamRequestHandler):
    def handle(self):
        self.connection.settimeout(15)
        acquired = False
        try:
            acquired = SLOT.acquire(blocking=False)
            if not acquired:
                self.wfile.write(b'{"status":"BUSY"}\n')
                return
            size = self.rfile.readline(16)
            length = int(size)
            if not 0 < length <= MAX_REQUEST:
                raise ValueError('Request size limit exceeded')
            body = self.rfile.read(length)
            if len(body) != length:
                raise ValueError('Incomplete request')
            value = json.loads(body)
            del body
            result = execute(value, self.connection)
        except (ValueError, KeyError, TypeError):
            result = {'status': 'REJECTED', 'notice': 'Invalid bounded execution request or output.'}
        except Exception:
            # Never log scripts, input bytes, Docker diagnostics or private output.
            result = {'status': 'FAILED', 'notice': 'Sandbox execution failed.'}
        try:
            # Retain the slot while serializing/writing large bounded output, too.
            for chunk in json.JSONEncoder().iterencode(result):
                self.wfile.write(chunk.encode())
            self.wfile.write(b'\n')
        except OSError:
            pass
        finally:
            if acquired:
                SLOT.release()


class Server(socketserver.ThreadingUnixStreamServer):
    daemon_threads = True


def cleanup():
    for container in docker('ps', '-aq', '--filter=label=' + LABEL).stdout.decode().split():
        docker('rm', '-f', container)
    root = Path('/var/lib/goldys-sandbox')
    root.mkdir(mode=0o700, exist_ok=True)
    for folder in root.glob('stage-*'):
        output = folder / 'output'
        if os.path.ismount(output):
            subprocess.run(['/usr/bin/umount', str(output)], check=True, capture_output=True)
        shutil.rmtree(folder)


def main():
    if not IMAGE.startswith('sha256:') or len(IMAGE) != 71:
        raise SystemExit('A local immutable analysis image ID is required')
    # After broker loss no orphan gets adopted or replayed.
    cleanup()
    Path(SOCKET).unlink(missing_ok=True)
    with Server(SOCKET, Handler) as server:
        os.chmod(SOCKET, 0o660)
        os.chown(SOCKET, 0, 65532)
        server.serve_forever()


if __name__ == '__main__':
    cleanup() if '--cleanup' in sys.argv else main()
