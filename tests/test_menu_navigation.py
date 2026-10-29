import os
from pathlib import Path
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
        self.tap('Theme and wallpaper')
        self.adb('shell', 'input', 'keyevent', '4')
        self.assert_home()
        self.tap('Preferences')
        self.adb('shell', 'input', 'keyevent', '4')
        self.assert_home()

    def test_home_dismisses_long_press_app_menu(self):
        root = Path(__file__).resolve().parents[1]
        sdk = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
                   or Path.home() / 'Library/Android/sdk')
        work = root / '.build/menu-test'
        classes = work / 'classes'
        dex = work / 'dex'
        classes.mkdir(parents=True, exist_ok=True)
        dex.mkdir(parents=True, exist_ok=True)
        subprocess.run(['javac', '-source', '8', '-target', '8', '-Xlint:-options',
                        '-bootclasspath', str(sdk / 'platforms/android-36/android.jar'),
                        '-d', str(classes), str(root / 'tests/device/HoldKey.java')], check=True)
        subprocess.run([str(sdk / 'build-tools/36.0.0/d8'), '--min-api', '27',
                        '--output', str(dex), str(classes / 'HoldKey.class')], check=True)
        self.adb('push', str(dex / 'classes.dex'), '/data/local/tmp/fire-tv-menu-test.dex')
        self.adb('shell', 'am', 'start', '-a', 'android.intent.action.MAIN',
                 '-c', 'android.intent.category.HOME',
                 '-n', 'com.jeremykenedy.firetv.ui/.MainActivity')
        time.sleep(2)
        self.adb('shell', 'input', 'keyevent', *(['19'] * 8))
        self.adb('shell', 'input', 'keyevent', '20')
        focused = [node for node in self.screen().iter('node')
                   if node.get('focused') == 'true']
        if any(child.get('text') == 'Watch Next'
               for node in focused for child in node.iter('node')):
            self.adb('shell', 'input', 'keyevent', '20')
        self.adb('shell', 'CLASSPATH=/data/local/tmp/fire-tv-menu-test.dex '
                 'app_process /system/bin HoldKey 23 2200')
        self.assertTrue(any(n.get('text') == 'Change Icon' for n in self.screen().iter('node')))
        self.adb('shell', 'input', 'keyevent', '3')
        time.sleep(2)
        self.assert_home()
        self.assertFalse(any(n.get('text') == 'Change Icon' for n in self.screen().iter('node')))
        self.adb('shell', 'rm', '-f', '/data/local/tmp/fire-tv-menu-test.dex')
