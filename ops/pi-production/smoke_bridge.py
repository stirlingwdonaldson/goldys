#!/usr/bin/env python3
"""Isolated real nginx/TLS check. Uses synthetic keys and a disposable test container."""
import http.server
import json
from pathlib import Path
import socket
import ssl
import subprocess
import tempfile
import threading
import time
import urllib.error
import urllib.request
import uuid

IMAGE = 'goldys-pi-bridge:verification'


class Backend(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path != '/api/workspace-worker/ready' or self.headers.get('X-Workspace-Token') != 'synthetic-only':
            self.send_error(403)
            return
        self.send_response(200)
        self.end_headers()
        self.wfile.write(b'{"status":"UP"}')

    def log_message(self, *args):
        pass


def main():
    subprocess.run(['docker', 'build', '-t', IMAGE, str(Path(__file__).parent)], check=True)
    backend = http.server.ThreadingHTTPServer(('127.0.0.1', 0), Backend)
    threading.Thread(target=backend.serve_forever, daemon=True).start()
    with socket.socket() as port_socket:
        port_socket.bind(('127.0.0.1', 0))
        port = port_socket.getsockname()[1]
    name = 'goldys-pi-bridge-check-' + uuid.uuid4().hex[:10]
    with tempfile.TemporaryDirectory(prefix='pi-bridge-check-') as directory:
        root = Path(directory)
        root.chmod(0o755)
        subprocess.run(['openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-days', '1',
                        '-subj', '/CN=localhost', '-addext', 'subjectAltName=IP:127.0.0.1',
                        '-keyout', str(root/'key'), '-out', str(root/'cert')], check=True, capture_output=True)
        (root/'key').chmod(0o644)  # Disposable synthetic key, removed at exit.
        config = Path(__file__).with_name('nginx.conf').read_text()
        config = config.replace('listen 8443 ssl;', f'listen 127.0.0.1:{port} ssl;').replace('http://backend:8080', f'http://127.0.0.1:{backend.server_port}')
        (root/'nginx.conf').write_text(config)
        (root/'nginx.conf').chmod(0o644)
        (root/'cert').chmod(0o644)
        try:
            subprocess.run(['docker', 'run', '-d', '--name', name, '--network=host', '--read-only',
                '--user=101:101', '--cap-drop=ALL', '--security-opt=no-new-privileges',
                '--memory=128m', '--pids-limit=32', '--cpus=0.25',
                '--tmpfs=/tmp:rw,noexec,nosuid,nodev,size=64m,mode=1777',
                '-v', f'{root}/nginx.conf:/etc/nginx/nginx.conf:ro',
                '-v', f'{root}/cert:/etc/pi/server.crt:ro', '-v', f'{root}/key:/etc/pi/server.key:ro',
                '--entrypoint=nginx', IMAGE, '-g', 'daemon off;'], check=True, capture_output=True)
            context = ssl.create_default_context(cafile=str(root/'cert'))
            origin = f'https://127.0.0.1:{port}'
            for attempt in range(40):
                try:
                    request = urllib.request.Request(origin+'/api/workspace-worker/ready', headers={'X-Workspace-Token':'synthetic-only'})
                    with urllib.request.urlopen(request, context=context, timeout=2) as response:
                        assert json.load(response) == {'status':'UP'}
                    break
                except urllib.error.URLError:
                    if attempt == 39:
                        raise
                    time.sleep(0.25)
            for path, expected in [('/api/auth/login',404), ('/api/workspace-worker/ready',403)]:
                try:
                    urllib.request.urlopen(origin+path, context=context, timeout=3)
                    raise AssertionError('Unexpected route access')
                except urllib.error.HTTPError as error:
                    assert error.code == expected, error.code
            print('PASS: pinned nginx, verified TLS, forwarded worker token, denied missing token, app routes inaccessible')
        except Exception:
            subprocess.run(['docker', 'logs', name], check=False)
            raise
        finally:
            subprocess.run(['docker','rm','-f',name], capture_output=True)
            backend.shutdown()


if __name__ == '__main__':
    main()
