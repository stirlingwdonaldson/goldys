"""Synthetic probes and transport only; no external notifications."""
import json
from pathlib import Path
import unittest
from unittest.mock import patch, Mock
from test_deployment import load

monitor = load('monitor', Path(__file__).with_name('monitor.py'))
preflight = monitor.preflight


class MonitorTest(unittest.TestCase):
    def test_probes_are_independent_and_fail_closed(self):
        with patch.object(monitor, 'worker_running', return_value=False), \
             patch.object(preflight, 'health', return_value={'stalled': True}), \
             patch.object(monitor, 'certificate_current', return_value=False):
            self.assertEqual(monitor.probes('https://localhost:18444'),
                             dict(worker=False, bridge=True, investigations=False, certificate=False))
        with patch.object(monitor, 'worker_running', return_value=True), \
             patch.object(preflight, 'health', side_effect=Exception('private sentinel')), \
             patch.object(monitor, 'certificate_current', return_value=True):
            self.assertEqual(monitor.probes('https://localhost:18444'),
                             dict(worker=True, bridge=False, investigations=False, certificate=True))

    def test_health_rejects_missing_or_nonboolean_fields_and_drain_requires_both_conditions(self):
        for state in ({}, {'acceptingJobs': False, 'active': 'false', 'stalled': False}):
            with patch.object(preflight, 'request', return_value=state), self.assertRaises(ValueError):
                preflight.health('https://localhost', 'unused')
        for accepting, active in ((True, False), (False, True), (True, True)):
            with patch.object(preflight, 'health', return_value=dict(acceptingJobs=accepting, active=active)), self.assertRaises(ValueError):
                preflight.drained('https://localhost', 'unused')
        with patch.object(preflight, 'health', return_value=dict(acceptingJobs=False, active=False)):
            preflight.drained('https://localhost', 'unused')

    def test_expiry_threshold_uses_verified_peer_certificate(self):
        connection = Mock()
        context = Mock()
        context.wrap_socket.return_value.__enter__ = Mock(return_value=connection)
        context.wrap_socket.return_value.__exit__ = Mock()
        connection.getpeercert.return_value = {'notAfter': 'fixture'}
        with patch.object(monitor.ssl, 'create_default_context', return_value=context) as tls, \
             patch.object(monitor.socket, 'create_connection') as connect, \
             patch.object(monitor.ssl, 'cert_time_to_seconds', return_value=100 + 30 * 86400), \
             patch.object(monitor.time, 'time', return_value=100):
            connect.return_value.__enter__.return_value = Mock()
            self.assertFalse(monitor.certificate_current('https://localhost:18444'))
            tls.assert_called_once_with(cafile=monitor.CA)
            self.assertEqual(context.wrap_socket.call_args.kwargs['server_hostname'], 'localhost')

    def test_checkin_contains_only_fixed_operational_fields_and_rejects_redirects(self):
        with patch.object(monitor.urllib.request, 'build_opener') as opener:
            opener.return_value.open.return_value.__enter__.return_value.status = 200
            monitor.checkin('http://synthetic@ops-sentry/1', 'worker', False)
            request = opener.return_value.open.call_args.args[0]
            self.assertEqual(request.full_url, 'http://ops-sentry/api/1/envelope/')
            envelope = [json.loads(line) for line in request.data.decode().splitlines()]
            self.assertEqual(envelope[1], {'type': 'check_in'})
            self.assertEqual(set(envelope[2]), {'check_in_id', 'monitor_slug', 'status', 'environment', 'monitor_config'})
            self.assertEqual(envelope[2]['status'], 'error')
            self.assertIsInstance(opener.call_args.args[0], preflight.NoRedirect)

    def test_failures_never_print_exception_details(self):
        with patch.object(monitor, 'probes', return_value={'worker': False}), \
             patch.object(monitor.Path, 'read_text', side_effect=Exception('secret sentinel')), \
             patch.dict(monitor.os.environ, {'CREDENTIALS_DIRECTORY': '/synthetic'}), \
             patch('builtins.print') as output:
            self.assertEqual(monitor.main(), 1)
            self.assertNotIn('sentinel', str(output.call_args_list))


if __name__ == '__main__':
    unittest.main()
