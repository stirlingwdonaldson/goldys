import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("tanda_token", Path(__file__).with_name("tanda-token.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class TokenTest(unittest.TestCase):
    def test_private_atomic_creation_and_no_overwrite(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "token"
            module.save_token(target, "test-token-not-a-real-credential")
            self.assertEqual(target.stat().st_mode & 0o777, 0o600)
            with self.assertRaises(ValueError):
                module.save_token(target, "a-different-test-token")
            self.assertEqual(target.read_text().strip(), "test-token-not-a-real-credential")

    def test_invalid_token_and_unsafe_directory(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "token"
            with self.assertRaises(ValueError):
                module.save_token(target, "token\nheader")
            Path(directory).chmod(0o755)
            with self.assertRaises(ValueError):
                module.save_token(target, "test-token-not-a-real-credential")
            self.assertFalse(target.exists())


if __name__ == "__main__":
    unittest.main()
