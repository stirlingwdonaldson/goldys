#!/usr/bin/env python3
"""Operator-invoked main-host setup. Updates only Pi settings; never prints secrets."""
import os
from pathlib import Path
import re
import sys
import tempfile


def configured(text, token):
    if not re.fullmatch(r'[0-9a-f]{64}', token):
        raise ValueError('Expected the dedicated 32-byte hex worker token')
    settings = {
        'APP_WORKSPACE_ENABLED': 'true',
        'APP_WORKSPACE_ACCEPTING_JOBS': 'true',
        'APP_WORKSPACE_WORKER_TOKEN': token,
        'APP_WORKSPACE_FILES_ENABLED': 'false',
        'PI_BRIDGE_TLS_DIR': '/etc/goldys-pi/tls',
    }
    profiles = []
    kept = []
    for line in text.splitlines():
        match = re.match(r'^\s*(?:export\s+)?([A-Z0-9_]+)\s*=(.*)$', line)
        if match and match[1] == 'COMPOSE_PROFILES':
            value = match[2].strip().strip('\"\'')
            if not re.fullmatch(r'[a-zA-Z0-9_,.-]*', value):
                raise ValueError('Simplify COMPOSE_PROFILES to a comma-separated list first')
            profiles.extend(value.split(','))
        elif not match or match[1] not in settings:
            kept.append(line)
    settings['COMPOSE_PROFILES'] = ','.join(dict.fromkeys(p for p in [*profiles, 'pi'] if p))
    return '\n'.join(kept + [f'{key}={value}' for key, value in settings.items()]) + '\n'


if __name__ == '__main__':
    target = Path(sys.argv[1]).resolve(strict=True)
    token = Path('/etc/goldys-pi/worker-token').read_text().strip()
    result = configured(target.read_text(), token)
    original = target.stat()
    fd, temporary = tempfile.mkstemp(prefix='.pi-config-', dir=target.parent)
    try:
        with os.fdopen(fd, 'w') as output:
            output.write(result)
            output.flush()
            os.fsync(output.fileno())
        os.chown(temporary, original.st_uid, original.st_gid)
        os.chmod(temporary, 0o600)
        os.replace(temporary, target)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)
    print('Pi settings updated; other app settings and accounts are unchanged. No service restarted.')
