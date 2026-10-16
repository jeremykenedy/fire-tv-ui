import os
import re
import subprocess
import time
import unittest
import xml.etree.ElementTree as ET


@unittest.skipUnless(os.environ.get('FIRE_TV_UI_DIAGNOSTICS_TEST_SERIAL'),
                     'Set FIRE_TV_UI_DIAGNOSTICS_TEST_SERIAL for native diagnostics checks.')
class DiagnosticsChecks(unittest.TestCase):
    def adb(self, *args):
        return subprocess.check_output(['adb', '-s', os.environ['FIRE_TV_UI_DIAGNOSTICS_TEST_SERIAL'],
                                        *args], text=True, timeout=30)

    def screen(self):
        path = '/sdcard/fire-tv-ui-diagnostics-test.xml'
        self.adb('shell', 'uiautomator', 'dump', path)
        return ET.fromstring(self.adb('shell', 'cat', path))

    def tap(self, label):
        nodes = [n for n in self.screen().iter('node')
                 if label in (n.get('text'), n.get('content-desc'))]
        self.assertTrue(nodes, 'Missing control: ' + label)
        left, top, right, bottom = map(int, re.findall(r'\d+', nodes[0].get('bounds')))
        self.adb('shell', 'input', 'tap', str((left + right) // 2), str((top + bottom) // 2))

    def settings(self):
        return {key: self.adb('shell', 'settings', 'get', 'secure', key).strip()
                for key in ('screensaver_components', 'screensaver_enabled', 'screensaver_activate_on_sleep',
                            'screensaver_activate_on_dock', 'sleep_timeout')}

    def test_report_navigation_save_and_settings_preservation(self):
        before = self.settings()
        self.adb('shell', 'am', 'start', '-W', '-n', 'com.jeremykenedy.firetv.ui/.MainActivity')
        self.tap('Preferences')
        self.tap('Advanced')
        self.tap('Diagnostics')
        if any(n.get('text') == 'Diagnostics' and n.get('focused') == 'true'
               for n in self.screen().iter('node')):
            self.adb('shell', 'input', 'keyevent', '23')
        deadline = time.monotonic() + 15
        while True:
            screen = self.screen()
            text = '\n'.join(n.get('text', '') for n in screen.iter('node'))
            if 'Report ready.' in text:
                break
            self.assertLess(time.monotonic(), deadline, text)
        for expected in ('Selected screensaver', 'Active DreamService', 'Installed screensavers',
                         'Android-reported display mode', 'TV RAM:', 'TV internal storage:',
                         'Recent Fire TV UI exits only', 'Screensaver FPS: unavailable',
                         'Actual screensaver surface buffer: unavailable'):
            self.assertIn(expected, text)
        self.assertNotIn('Could not complete', text)
        self.assertNotIn('Fire TV UI content size: 0x0', text)
        self.assertNotIn('Enabled: 1', text)
        self.assertIn('Enabled: On' if before['screensaver_enabled'] == '1' else 'Enabled:', text)
        self.tap('Save report on TV')
        # Card buttons focus on the first touch; use the remote to activate.
        self.adb('shell', 'input', 'keyevent', '23')
        screen = self.screen()
        text = '\n'.join(n.get('text', '') for n in screen.iter('node'))
        self.assertIn('Saved to TV Downloads/', text)
        name = re.search(r'Saved to TV Downloads/(fire-tv-ui-diagnostics-\d+\.txt)', text).group(1)
        saved = self.adb('shell', 'cat', '/sdcard/Download/' + name)
        self.assertIn('Fire TV UI diagnostics', saved)
        self.assertIn('This is not a screensaver crash check.', saved)
        self.adb('shell', 'input', 'keyevent', '4')
        self.assertTrue(any(n.get('text') == 'Advanced' for n in self.screen().iter('node')))
        self.adb('shell', 'input', 'keyevent', '4')
        self.assertEqual(before, self.settings())
