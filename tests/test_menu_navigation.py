import os
import re
import subprocess
import time
import unittest
import xml.etree.ElementTree as ET


@unittest.skipUnless(os.environ.get('FIRE_TV_UI_MENU_TEST_SERIAL'),
                     'Set FIRE_TV_UI_MENU_TEST_SERIAL to a TV for remote navigation checks.')
class MenuNavigationChecks(unittest.TestCase):
    def adb(self, *args):
        return subprocess.check_output(
            ['adb', '-s', os.environ['FIRE_TV_UI_MENU_TEST_SERIAL'], *args], text=True, timeout=30)

    def screen(self):
        path = '/sdcard/fire-tv-ui-menu-test.xml'
        for attempt in range(3):
            self.adb('shell', 'rm', '-f', path)
            self.adb('shell', 'uiautomator', 'dump', path)
            try:
                return ET.fromstring(self.adb('shell', 'cat', path))
            except (subprocess.CalledProcessError, ET.ParseError):
                if attempt == 2:
                    raise
                time.sleep(0.5)

    def tap(self, label):
        matches = [node for node in self.screen().iter('node')
                   if label in (node.get('text'), node.get('content-desc'))]
        self.assertTrue(matches, 'Missing control: ' + label)
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', matches[0].get('bounds')))
        self.adb('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))

    def assert_home(self):
        screen = self.screen()
        self.assertFalse(any(node.get('text') == 'Settings' for node in screen.iter('node')))
        self.assertTrue(any(node.get('content-desc') == 'Preferences'
                            for node in screen.iter('node')))
        self.assertIn('com.jeremykenedy.firetv.ui/.MainActivity',
                      self.adb('shell', 'dumpsys', 'activity', 'activities'))

    def test_remote_menu_dismissal_and_home(self):
        self.adb('shell', 'am', 'start', '-a', 'android.intent.action.MAIN',
                 '-c', 'android.intent.category.HOME',
                 '-n', 'com.jeremykenedy.firetv.ui/.MainActivity')
        self.assert_home()
        for key in (4, 4, 82, 3):
            self.tap('Preferences')
            self.assertTrue(any(node.get('text') == 'Settings'
                                for node in self.screen().iter('node')))
            self.adb('shell', 'input', 'keyevent', str(key))
            self.assert_home()
        self.tap('Preferences')
        self.tap('Theme Mode')
        self.adb('shell', 'input', 'keyevent', '4')
        self.assert_home()
        self.tap('Preferences')
        self.adb('shell', 'input', 'keyevent', '4')
        self.assert_home()
