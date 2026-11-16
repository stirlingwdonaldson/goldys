"""Offline deployment contract checks; no services, real environment or keys are used."""
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


egress = load('egress', ROOT / 'analysis-sandbox/configure-egress.py')
preflight = load('preflight', Path(__file__).with_name('check-worker.py'))
app_config = load('app_config', Path(__file__).with_name('configure-app.py'))


class DeploymentTest(unittest.TestCase):
    def test_configuration_preserves_unrelated_values_and_profiles(self):
        old = 'POSTGRES_PASSWORD=synthetic\nCOMPOSE_PROFILES="forecast,other"\nAPP_WORKSPACE_ENABLED=false\nAPP_WORKSPACE_ENABLED=false\n'
        result = app_config.configured(old, 'a' * 64)
        self.assertIn('POSTGRES_PASSWORD=synthetic\n', result)
        self.assertIn('COMPOSE_PROFILES=forecast,other,pi\n', result)
        self.assertEqual(result.count('APP_WORKSPACE_ENABLED='), 1)
        self.assertEqual(app_config.configured(result, 'a' * 64), result)


    def test_compose_uses_same_activation_for_frontend_and_existing_backend(self):
        # Empty env-file plus an explicit synthetic environment: never load .env.prod.
        env = {'PATH': os.environ['PATH'], 'POSTGRES_PASSWORD': 'synthetic-only',
               'SFTP_PASSWORD': 'synthetic-only', 'SENTRY_AUTH_TOKEN': '',
               'APP_WORKSPACE_ENABLED': 'true', 'APP_WORKSPACE_WORKER_TOKEN': 'synthetic-worker-token'}
        raw = subprocess.check_output(['docker', 'compose', '--env-file', '/dev/null',
            '--project-name', 'goldys-pi-config-check', '-f', str(ROOT / 'docker-compose.prod.yml'),
            'config', '--format', 'json'], env=env)
        services = json.loads(raw)['services']
        backend = services['backend']['environment']
        frontend = services['frontend']['build']['args']
        self.assertEqual(backend['APP_WORKSPACE_ENABLED'], 'true')
        self.assertEqual(frontend['NEXT_PUBLIC_WORKSPACE_ENABLED'], 'true')
        self.assertEqual(backend['APP_WORKSPACE_FILES_ENABLED'], 'false')
        self.assertEqual(frontend['NEXT_PUBLIC_WORKSPACE_FILES_ENABLED'], 'false')
        self.assertNotIn('APP_WORKSPACE_WORKER_TOKEN', frontend)
        self.assertNotIn('NEXT_PUBLIC_WORKSPACE_PREVIEW', frontend)
        self.assertEqual(backend['SPRING_DATASOURCE_URL'], 'jdbc:postgresql://postgres:5432/goldys')
        self.assertNotIn('ports', services['backend'])

    def test_spring_destination_rejects_private_dns_and_url_credentials(self):
        for url in ['http://platform.swd.sh', 'https://user:pass@platform.swd.sh',
                    'https://platform.swd.sh/private', 'https://platform.swd.sh?q=x']:
            with self.assertRaises(ValueError):
                egress.spring_destination(url)
        with patch.object(egress.socket, 'getaddrinfo', return_value=[(0, 0, 0, '', ('192.168.1.2', 443))]):
            with self.assertRaises(ValueError):
                egress.spring_destination('https://platform.swd.sh')
        with patch.object(egress.socket, 'getaddrinfo', return_value=[(0, 0, 0, '', ('1.1.1.1', 443)), (0, 0, 0, '', ('2606:4700::1111', 443))]):
            addresses, port = egress.spring_destination('https://platform.swd.sh')
            self.assertEqual(port, 443)
            self.assertEqual(len(addresses), 2)
        for port in (18443, 18444):
            with patch.object(egress.socket, 'getaddrinfo', return_value=[(0, 0, 0, '', ('127.0.0.1', port))]):
                self.assertEqual(egress.spring_destination(f'https://localhost:{port}')[1], port)
        with patch.object(egress.socket, 'getaddrinfo', return_value=[(0, 0, 0, '', ('127.0.0.1', 8080))]):
            with self.assertRaises(ValueError):
                egress.spring_destination('https://localhost:8080')

    def test_preflight_never_follows_redirect_with_worker_token(self):
        with self.assertRaises(ValueError):
            preflight.NoRedirect().redirect_request(None, None, 302, '', {}, 'https://elsewhere.test')

    def test_production_service_has_no_preview_or_script_access(self):
        unit = (Path(__file__).parent / 'goldys-pi-production.service').read_text()
        self.assertIn('Conflicts=goldys-pi-worker.service', unit)
        self.assertIn('--env=GOLDYS_SPRING_ORIGIN ', unit)
        self.assertIn('--env=GOLDYS_SANDBOX_ENABLED=false', unit)
        self.assertNotIn('preview-ca', unit)
        self.assertNotIn('18443', unit)
        self.assertIn('production-ca.crt', unit)
        self.assertNotIn('docker.sock', unit)
        self.assertNotIn('--mount=type=bind,src=/run/goldys-sandbox', unit)
        self.assertIn('check-worker.py', unit)


if __name__ == '__main__':
    unittest.main()
