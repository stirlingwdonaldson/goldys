"""Real temporary files with fake Docker/systemd/bridge; no host service mutations."""
import hashlib
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
from test_deployment import load

updater = load('updater', Path(__file__).with_name('update-worker.py'))


def bundle(root, image='sha256:' + 'a' * 64):
    release = root / 'release'
    release.mkdir()
    for name in updater.REQUIRED | updater.OPTIONAL:
        (release / name).write_text(image if name == 'worker-image-id' else 'synthetic ' + name)
    manifest(release)
    return release


def manifest(release):
    (release / 'SHA256SUMS').write_text(''.join(
        hashlib.sha256(p.read_bytes()).hexdigest() + '  ' + p.name + '\n'
        for p in sorted(release.iterdir()) if p.name != 'SHA256SUMS'))


class UpdateTest(unittest.TestCase):
    def test_manifest_rejects_tampering_missing_files_mutable_images_and_traversal(self):
        with tempfile.TemporaryDirectory() as temp:
            release = bundle(Path(temp))
            self.assertEqual(updater.verify_release(release)[0], 'sha256:' + 'a' * 64)
            (release / 'worker-image.tar').write_text('tampered')
            with self.assertRaises(ValueError):
                updater.verify_release(release)
            manifest(release)
            (release / 'worker-image-id').write_text('latest')
            manifest(release)
            with self.assertRaises(ValueError):
                updater.verify_release(release)
            (release / 'SHA256SUMS').write_text('a' * 64 + '  ../outside\n')
            with self.assertRaises(ValueError):
                updater.verify_release(release)
            (release / 'SHA256SUMS').write_text('')
            with self.assertRaises(ValueError):
                updater.verify_release(release)

    def test_config_preserves_origin_and_unrelated_values_without_shell_evaluation(self):
        text = 'GOLDYS_WORKER_IMAGE=sha256:old\nGOLDYS_SPRING_ORIGIN=https://localhost:18444\nOTHER=$(must-not-run)\n'
        changed, origin = updater.configuration(text, 'sha256:' + 'b' * 64)
        self.assertIn('OTHER=$(must-not-run)', changed)
        self.assertEqual(origin, 'https://localhost:18444')
        with self.assertRaises(ValueError):
            updater.configuration(text + 'GOLDYS_WORKER_IMAGE=duplicate\n', 'unused')

    def test_active_or_orphan_workers_block_update(self):
        with patch.object(updater.subprocess, 'run', return_value=subprocess.CompletedProcess([], 0, b'active\n')):
            with self.assertRaises(ValueError):
                updater.require_stopped()
        with patch.object(updater.subprocess, 'run', return_value=subprocess.CompletedProcess([], 0, b'inactive\n')), \
             patch.object(updater.subprocess, 'check_output', return_value=b'goldys-pi-worker-preview\n'):
            with self.assertRaises(ValueError):
                updater.require_stopped()

    def test_update_and_rollback_preserve_secrets_ca_and_never_start_services(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            release = bundle(root)
            runtime = root / 'runtime'
            units = root / 'units'
            runtime.mkdir()
            units.mkdir()
            config = root / 'config'
            old = 'GOLDYS_WORKER_IMAGE=sha256:' + 'b' * 64 + '\nGOLDYS_SPRING_ORIGIN=https://localhost:18444\n'
            config.write_text(old)
            (runtime / 'production-ca.crt').write_text('keep existing CA')
            (runtime / 'check-worker.py').write_text('old helper')
            with patch.object(updater, 'CONFIG', config), patch.object(updater, 'RUNTIME', runtime), \
                 patch.object(updater, 'UNITS', units), patch.object(updater, 'require_stopped'), \
                 patch.object(updater.preflight, 'drained'), patch.object(updater, 'run') as run:
                updater.update(release)
                self.assertIn('sha256:' + 'a' * 64, config.read_text())
                self.assertEqual((runtime / 'production-ca.crt').read_text(), 'keep existing CA')
                backups = list((runtime / 'rollback').iterdir())
                self.assertEqual((backups[0] / config.name).read_text(), old)
                self.assertEqual((backups[0] / 'check-worker.py').read_text(), 'old helper')
                self.assertEqual(config.stat().st_mode & 0o777, 0o600)
                installed_check = (runtime / 'check-worker.py').read_text()
                for name in updater.OPTIONAL:
                    (release / name).unlink()
                (release / 'check-worker.py').write_text('legacy readiness only')
                (release / 'worker-image-id').write_text('sha256:' + 'b' * 64)
                manifest(release)
                updater.update(release)
                self.assertEqual(config.read_text(), old)
                self.assertEqual((runtime / 'check-worker.py').read_text(), installed_check)
                self.assertFalse(any('start' in call.args or 'restart' in call.args for call in run.call_args_list))

    def test_failed_drain_leaves_config_and_runtime_untouched(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            release = bundle(root)
            config = root / 'config'
            config.write_text('GOLDYS_WORKER_IMAGE=old\nGOLDYS_SPRING_ORIGIN=https://localhost:18444\n')
            before = config.read_bytes()
            with patch.object(updater, 'CONFIG', config), patch.object(updater, 'require_stopped'), \
                 patch.object(updater.preflight, 'drained', side_effect=ValueError('not drained')), \
                 patch.object(updater, 'run') as run:
                with self.assertRaises(ValueError):
                    updater.update(release)
                self.assertEqual(config.read_bytes(), before)
                run.assert_not_called()

    def test_certificate_staging_does_not_overwrite_and_creates_private_key(self):
        with tempfile.TemporaryDirectory() as temp:
            output = Path(temp) / 'new'
            script = str(Path(__file__).with_name('renew-certificate.sh'))
            subprocess.run(['bash', script, str(output)], check=True, capture_output=True)
            self.assertEqual((output / 'server.key').stat().st_mode & 0o777, 0o600)
            subprocess.run(['openssl', 'verify', '-CAfile', str(output / 'server.crt'),
                            '-verify_hostname', 'localhost', str(output / 'server.crt')], check=True, capture_output=True)
            self.assertNotEqual(subprocess.run(['bash', script, str(output)], capture_output=True).returncode, 0)


if __name__ == '__main__':
    unittest.main()
