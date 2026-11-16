#!/usr/bin/env python3
"""Analysis-VM preflight. Prints no credentials; never claims a job or calls a model."""
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


def check(origin, token_path):
    url = urlsplit(origin)
    if (url.scheme != 'https' or not url.hostname or url.username or url.password
            or url.path not in ('', '/') or url.query or url.fragment):
        raise ValueError('Expected a fixed HTTPS application origin')
    token = Path(token_path).read_text().strip()
    if len(token) < 32 or re.search(r'\s', token):
        raise ValueError('Worker token must contain at least 32 non-whitespace characters')
    request = urllib.request.Request(origin.rstrip('/') + '/api/workspace-worker/ready',
                                     headers={'X-Workspace-Token': token, 'Accept': 'application/json'})
    with urllib.request.build_opener(NoRedirect(), urllib.request.HTTPSHandler(context=ssl.create_default_context(cafile='/opt/goldys-agent/production-ca.crt'))).open(request, timeout=15) as response:
        if json.load(response) != {'status': 'UP'}:
            raise ValueError('Unexpected worker readiness response')


if __name__ == '__main__':
    try:
        check(os.environ['GOLDYS_SPRING_ORIGIN'], '/etc/goldys-agent/production-worker-token')
    except Exception:
        raise SystemExit('Worker preflight failed: check HTTPS routing, backend activation and matching token. No job was claimed.')
    print('Worker token and HTTPS application bridge verified; no job claimed.')
