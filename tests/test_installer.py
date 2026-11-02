from pathlib import Path
import subprocess
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]


class InstallerChecks(unittest.TestCase):
    def test_retired_installer_rejects_explicit_and_default_apks(self):
        for args in ([], ['--serial', 'isolated-test-serial', '--apk', 'review.apk']):
            with self.subTest(args=args):
                result = subprocess.run([sys.executable, str(ROOT / 'scripts/install.py'), *args],
                                        capture_output=True, text=True)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn('No APK was installed or device settings changed.', result.stderr)


if __name__ == '__main__':
    unittest.main()
