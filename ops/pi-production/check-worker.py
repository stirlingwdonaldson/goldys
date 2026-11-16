#!/usr/bin/env python3
"""Analysis-VM preflight. Prints no credentials; never claims a job or calls a model."""
import argparse
import json
import os
from pathlib import Path
import re
import ssl
import urllib.error
import urllib.request
from urllib.parse import urlsplit


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise ValueError('Worker origin redirected; configure the direct HTTPS application origin')


def request(origin, token_path, endpoint):
    url = urlsplit(origin)
    if (url.scheme != 'https' or not url.hostname or url.username or url.password
            or url.path not in ('', '/') or url.query or url.fragment):
        raise ValueError('Expected a fixed HTTPS application origin')
    token = Path(token_path).read_text().strip()
    if len(token) < 32 or re.search(r'\s', token):
        raise ValueError('Worker token must contain at least 32 non-whitespace characters')
    request = urllib.request.Request(origin.rstrip('/') + '/api/workspace-worker/' + endpoint,
                                     headers={'X-Workspace-Token': token, 'Accept': 'application/json'})
    with urllib.request.build_opener(NoRedirect(), urllib.request.HTTPSHandler(context=ssl.create_default_context(cafile='/opt/goldys-agent/production-ca.crt'))).open(request, timeout=15) as response:
        return json.loads(response.read(4096))


def check(origin, token_path):
    if request(origin, token_path, 'ready') != {'status': 'UP'}:
        raise ValueError('Unexpected worker readiness response')


def health(origin, token_path):
    result = request(origin, token_path, 'health')
    if (not isinstance(result, dict) or set(result) != {'acceptingJobs', 'active', 'stalled'}
            or any(type(value) is not bool for value in result.values())):
        raise ValueError('Unexpected operational health response')
    return result


def drained(origin, token_path):
    result = health(origin, token_path)
    if result['acceptingJobs'] or result['active']:
        raise ValueError('Pause admission and drain or explicitly Stop work first')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--drained', action='store_true')
    args = parser.parse_args()
    try:
        origin = os.environ['GOLDYS_SPRING_ORIGIN']
        token = '/etc/goldys-agent/production-worker-token'
        if args.drained:
            drained(origin, token)
        else:
            check(origin, token)
    except Exception:
        raise SystemExit('Worker preflight failed: check HTTPS routing, backend activation and matching token. No job was claimed.')
    print('Worker token and HTTPS application bridge verified; no job claimed.')
