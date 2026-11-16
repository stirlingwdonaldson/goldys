#!/usr/bin/env python3
"""Read-only Pi probes; fixed status codes only. No job IDs, payloads or exception text."""
import importlib.util
import json
import os
from pathlib import Path
import socket
import ssl
import subprocess
import time
import urllib.request
from urllib.parse import urlsplit
import uuid

spec = importlib.util.spec_from_file_location('preflight', Path(__file__).with_name('check-worker.py'))
preflight = importlib.util.module_from_spec(spec)
spec.loader.exec_module(preflight)
CA = '/opt/goldys-agent/production-ca.crt'
TOKEN = '/etc/goldys-agent/production-worker-token'


def worker_running():
    unit = subprocess.run(['systemctl', 'is-active', '--quiet', 'goldys-pi-production'],
                          stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=10)
    container = subprocess.run(['docker', 'inspect', '--format={{.State.Running}}',
                                'goldys-pi-worker-production'], capture_output=True, timeout=10)
    return unit.returncode == 0 and container.returncode == 0 and container.stdout.strip() == b'true'


def certificate_current(origin):
    url = urlsplit(origin)
    # Inspect the certificate actually served through the tunnel, not a stale disk copy.
    context = ssl.create_default_context(cafile=CA)
    with socket.create_connection((url.hostname, url.port or 443), timeout=10) as connection:
        with context.wrap_socket(connection, server_hostname=url.hostname) as secured:
            expiry = ssl.cert_time_to_seconds(secured.getpeercert()['notAfter'])
    return expiry - time.time() > 30 * 86400


def probes(origin):
    results = {}
    try:
        results['worker'] = worker_running()
    except Exception:
        results['worker'] = False
    try:
        state = preflight.health(origin, TOKEN)
        results['bridge'] = True
        results['investigations'] = not state['stalled']
    except Exception:
        results['bridge'] = results['investigations'] = False
    try:
        results['certificate'] = certificate_current(origin)
    except Exception:
        results['certificate'] = False
    return results


def checkin(dsn, probe, healthy):
    """Sentry's documented check_in envelope; no SDK breadcrumbs or environment capture."""
    url = urlsplit(dsn)
    prefix, _, project = url.path.rstrip('/').rpartition('/')
    if (url.scheme not in ('http', 'https') or not url.hostname or not url.username
            or url.password or not project.isdigit() or url.query or url.fragment
            or probe not in ('worker', 'bridge', 'investigations', 'certificate')):
        raise ValueError('Invalid monitoring configuration')
    host = url.netloc.rsplit('@', 1)[-1]
    endpoint = f'{url.scheme}://{host}{prefix}/api/{project}/envelope/'
    payload = {'check_in_id': uuid.uuid4().hex, 'monitor_slug': f'goldys-pi-{probe}',
               'status': 'ok' if healthy else 'error', 'environment': 'production',
               'monitor_config': {'schedule': {'type': 'interval', 'value': 1, 'unit': 'minute'},
                                  'checkin_margin': 3, 'max_runtime': 2,
                                  'failure_issue_threshold': 2, 'recovery_threshold': 1}}
    body = '\n'.join(json.dumps(item) for item in ({'dsn': dsn}, {'type': 'check_in'}, payload)) + '\n'
    request = urllib.request.Request(endpoint, data=body.encode(),
                                     headers={'Content-Type': 'application/x-sentry-envelope'})
    # HTTP is supported for the existing private ops-sentry LAN destination only.
    with urllib.request.build_opener(preflight.NoRedirect()).open(request, timeout=10) as response:
        if response.status not in (200, 202):
            raise ValueError('Monitoring delivery failed')


def main():
    results = probes(os.environ.get('GOLDYS_SPRING_ORIGIN', 'https://localhost:18444'))
    delivery = True
    try:
        dsn = (Path(os.environ['CREDENTIALS_DIRECTORY']) / 'sentry-dsn').read_text().strip()
        for probe, healthy in results.items():
            checkin(dsn, probe, healthy)
    except Exception:
        delivery = False
    for probe, healthy in results.items():
        print(f'pi_{probe}={"ok" if healthy else "failed"}')
    print(f'pi_alert_delivery={"ok" if delivery else "failed_or_unconfigured"}')
    return 0 if all(results.values()) and delivery else 1


if __name__ == '__main__':
    raise SystemExit(main())
