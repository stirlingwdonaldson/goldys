#!/usr/bin/env python3
"""Update an already installed, drained, stopped worker. Never starts services."""
import argparse
import fcntl
import hashlib
import importlib.util
import os
from pathlib import Path
import re
import shutil
import socket
import subprocess
import tempfile
from datetime import datetime, timezone

spec = importlib.util.spec_from_file_location('preflight', Path(__file__).with_name('check-worker.py'))
preflight = importlib.util.module_from_spec(spec)
spec.loader.exec_module(preflight)

CONFIG = Path('/etc/goldys-agent/production-worker.conf')
RUNTIME = Path('/opt/goldys-agent')
UNITS = Path('/etc/systemd/system')
REQUIRED = {'worker-image.tar', 'worker-image-id', 'configure-egress.py', 'check-worker.py',
            'goldys-pi-production.service', 'production-ca.crt', 'install.sh'}
OPTIONAL = {'monitor.py', 'goldys-pi-monitor.service', 'goldys-pi-monitor.timer', 'update-worker.py'}


def verify_release(release):
    entries = {}
    for line in (release / 'SHA256SUMS').read_text().splitlines():
        digest, name = line.split('  ', 1)
        if (name not in REQUIRED | OPTIONAL or name in entries
                or not re.fullmatch('[0-9a-f]{64}', digest)):
            raise ValueError('Invalid release manifest')
        path = release / name
        if path.is_symlink() or not path.is_file():
            raise ValueError('Invalid release file')
        with path.open('rb') as source:
            if hashlib.file_digest(source, 'sha256').hexdigest() != digest:
                raise ValueError('Release checksum mismatch')
        entries[name] = digest
    if not REQUIRED <= entries.keys():
        raise ValueError('Incomplete release manifest')
    image = (release / 'worker-image-id').read_text().strip()
    if not re.fullmatch('sha256:[0-9a-f]{64}', image):
        raise ValueError('Expected immutable image ID')
    return image, entries


def configuration(text, image):
    # Never source a shell file or print it. Preserve every setting except the exact image key.
    if len(re.findall(r'^GOLDYS_WORKER_IMAGE=.*$', text, re.M)) != 1:
        raise ValueError('Expected one existing image setting')
    origins = re.findall(r'^GOLDYS_SPRING_ORIGIN=(https://localhost:18444)\s*$', text, re.M)
    if len(origins) != 1:
        raise ValueError('Expected the private production bridge')
    return re.sub(r'^GOLDYS_WORKER_IMAGE=.*$', f'GOLDYS_WORKER_IMAGE={image}', text, flags=re.M), origins[0]


def run(*args):
    return subprocess.run(args, check=True, stdout=subprocess.DEVNULL,
                          stderr=subprocess.DEVNULL, timeout=300)


def require_stopped():
    for unit in ('goldys-pi-worker', 'goldys-pi-production'):
        state = subprocess.run(['systemctl', 'show', '--property=ActiveState', '--value', unit],
                               capture_output=True, check=True, timeout=10).stdout.strip()
        if state not in (b'inactive', b'failed'):
            raise ValueError('Both workers must be stopped')
    # Check actual containers too: failed/stopped units alone do not prove termination.
    running = subprocess.check_output(['docker', 'ps', '--format={{.Names}}'], timeout=10).decode().splitlines()
    if set(running) & {'goldys-pi-worker-preview', 'goldys-pi-worker-production'}:
        raise ValueError('A Pi container is still running')


def atomic_copy(source, target, mode):
    with tempfile.NamedTemporaryFile(dir=target.parent, delete=False) as staged:
        temporary = Path(staged.name)
        try:
            with source.open('rb') as stream:
                shutil.copyfileobj(stream, staged)
            staged.flush()
            os.fchmod(staged.fileno(), mode)
            os.fsync(staged.fileno())
            os.replace(temporary, target)
        finally:
            temporary.unlink(missing_ok=True)


def update(release):
    image, entries = verify_release(release)
    text, origin = configuration(CONFIG.read_text(), image)
    require_stopped()
    preflight.drained(origin, '/etc/goldys-agent/production-worker-token')
    run('docker', 'load', '--input', str(release / 'worker-image.tar'))
    run('docker', 'image', 'inspect', image)
    files = {'configure-egress.py': RUNTIME, 'check-worker.py': RUNTIME,
             'goldys-pi-production.service': UNITS}
    # Pre-monitoring bundles lack health()/--drained; retain the installed compatible
    # preflight so a worker rollback does not break monitoring or the next update.
    if 'monitor.py' not in entries:
        files.pop('check-worker.py')
    files.update({name: UNITS if name.endswith(('.service', '.timer')) else RUNTIME
                  for name in OPTIONAL & entries.keys()})
    backup = RUNTIME / 'rollback' / datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    backup.mkdir(parents=True, mode=0o700)
    shutil.copy2(CONFIG, backup / CONFIG.name)
    for name, destination in files.items():
        if (destination / name).exists():
            shutil.copy2(destination / name, backup / name)
    # Token, CA, origin and model credentials are never overwritten by an image update.
    for name, destination in files.items():
        atomic_copy(release / name, destination / name, 0o644)
    with tempfile.TemporaryDirectory() as temp:
        staged = Path(temp) / 'config'
        staged.write_text(text)
        staged.chmod(0o600)
        atomic_copy(staged, CONFIG, 0o600)
    run('systemctl', 'disable', 'goldys-pi-worker.service')
    run('systemctl', 'daemon-reload')
    print('Worker update installed and left stopped; verify readiness before enabling admission.')
    print(f'Previous configuration and scripts retained in {backup}')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('release', type=Path)
    args = parser.parse_args()
    if os.geteuid() != 0 or socket.gethostname() != 'svc-goldys-agent':
        raise SystemExit('Run as root on svc-goldys-agent only.')
    try:
        with open('/run/lock/goldys-pi-update.lock', 'w') as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            update(args.release.resolve())
    except Exception:
        raise SystemExit('Worker update failed; keep admission paused and the worker stopped. No secrets logged.')


if __name__ == '__main__':
    main()
