from pathlib import Path
import runpy
import subprocess
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]


class InstallerChecks(unittest.TestCase):
    def test_update_keeps_explicit_home_setting_operations_out_of_installation(self):
        calls = []
        icons = []
        with tempfile.TemporaryDirectory() as folder:
            apk = Path(folder) / 'review.apk'
            apk.touch()
            argv = ['install.py', '--serial', 'isolated-test-serial', '--apk', str(apk)]
            with patch.object(sys, 'argv', argv), \
                    patch.dict(sys.modules, {'install_icons': SimpleNamespace(install_icons=icons.append)}), \
                    patch('subprocess.run', side_effect=lambda args, **kwargs: calls.append(args)), \
                    patch('subprocess.check_output', side_effect=lambda args, **kwargs: (
                        'com.jeremykenedy.firetv.ui/com.jeremykenedy.firetv.ui.HomeRedirectService\n'
                        if 'settings' in args else '34\n')):
                runpy.run_path(str(ROOT / 'scripts/install.py'), run_name='__main__')
        self.assertEqual(calls[0], ['adb', '-s', 'isolated-test-serial', 'install', '-r', str(apk)])
        self.assertTrue(all(call[:3] == ['adb', '-s', 'isolated-test-serial'] for call in calls))
        self.assertFalse(any('broadcast' in call or 'settings' in call for call in calls))
        self.assertEqual(icons, ['isolated-test-serial'])
        self.assertEqual(calls[-1][-2:], ['-n', 'com.jeremykenedy.firetv.ui/.MainActivity'])

    def test_rejected_apk_does_not_grant_access_copy_icons_or_open_the_launcher(self):
        icons = []
        with tempfile.TemporaryDirectory() as folder:
            apk = Path(folder) / 'review.apk'
            apk.touch()
            argv = ['install.py', '--serial', 'isolated-test-serial', '--apk', str(apk)]
            with patch.object(sys, 'argv', argv), \
                    patch.dict(sys.modules, {'install_icons': SimpleNamespace(install_icons=icons.append)}), \
                    patch('subprocess.run', side_effect=subprocess.CalledProcessError(1, 'adb')) as run, \
                    patch('subprocess.check_output') as read:
                with self.assertRaises(subprocess.CalledProcessError):
                    runpy.run_path(str(ROOT / 'scripts/install.py'), run_name='__main__')
        self.assertEqual(run.call_count, 1)
        read.assert_not_called()
        self.assertEqual(icons, [])
