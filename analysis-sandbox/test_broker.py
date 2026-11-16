import base64
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import broker


class BoundaryTests(unittest.TestCase):
    def test_input_limits_and_duplicate_names(self):
        good = {'name': 'sales.csv', 'base64': base64.b64encode(b'a,b\n1,2').decode()}
        self.assertEqual(broker.request({'code': 'pass', 'inputs': [good]})[1]['sales.csv'], b'a,b\n1,2')
        for inputs in ([good, good], [good] * 6, [{'name': '../key', 'base64': ''}], [{'name': 'x', 'base64': '!'}]):
            with self.assertRaises(ValueError):
                broker.request({'code': 'pass', 'inputs': inputs})
        with patch.object(broker, 'MAX_OUTPUT', 2):
            with self.assertRaises(ValueError):
                broker.request({'code': 'pass', 'inputs': [good]})

    def test_no_runtime_options_or_unbounded_execution(self):
        for body in ({'code': 'pass', 'image': 'other'}, {'code': 'pass', 'seconds': 61},
                     {'code': 'pass', 'seconds': True}, {'code': ' '}, {'code': 'a' * 65537}):
            with self.assertRaises(ValueError):
                broker.request(body)

    def test_output_links_directories_and_count_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            (folder / 'report.csv').write_text('a,b\n1,2')
            self.assertEqual(broker.collect_outputs(folder)[0]['name'], 'report.csv')
            (folder / 'link.csv').symlink_to('/etc/passwd')
            with self.assertRaises(ValueError):
                broker.collect_outputs(folder)
            (folder / 'link.csv').unlink()
            (folder / 'subfolder').mkdir()
            with self.assertRaises(ValueError):
                broker.collect_outputs(folder)
            (folder / 'subfolder').rmdir()
            for index in range(5):
                (folder / str(index)).write_text('x')
            with self.assertRaises(ValueError):
                broker.collect_outputs(folder)

    def test_hardlinks_rejected(self):
        import os
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            (folder / 'one').write_text('x')
            os.link(folder / 'one', folder / 'two')
            with self.assertRaises(ValueError):
                broker.collect_outputs(folder)


if __name__ == '__main__':
    unittest.main()
