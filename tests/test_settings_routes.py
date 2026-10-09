import os
import subprocess
import time
import unittest
import xml.etree.ElementTree as ET

SERIAL = os.environ.get('FIRE_TV_UI_TEST_SERIAL')
UI_PACKAGE = 'com.jeremykenedy.firetv.ui'
ORIGINAL_SETTINGS_ACTIVITY = '/sdcard/fire-tv-ui-settings-test.xml'
SCREENSAVERS = (
    ('Twilight Hearth', 'com.jeremykenedy.twilighthearth',
     'com.jeremykenedy.twilighthearth/.HearthDreamService'),
    ('Nebula Drift', 'com.jeremykenedy.nebuladrift',
     'com.jeremykenedy.nebuladrift/.NebulaDreamService'),
    ('Vortex Spiral', 'com.jeremykenedy.vortexspiral',
     'com.jeremykenedy.vortexspiral/.VortexDreamService'),
    ('Signal Rain', 'com.jeremykenedy.signalrain',
     'com.jeremykenedy.signalrain/.SignalRainDreamService'),
)


@unittest.skipUnless(SERIAL, 'Set FIRE_TV_UI_TEST_SERIAL to an Android TV emulator.')
class SettingsRouteChecks(unittest.TestCase):
    def adb(self, *args):
        return subprocess.run(['adb', '-s', SERIAL, *args], check=True,
                              text=True, stdout=subprocess.PIPE).stdout.strip()

    def shell(self, *args):
        return self.adb('shell', *args)

    def setting(self, key):
        value = self.shell('settings', 'get', 'secure', key)
        return None if value == 'null' else value

    def write_setting(self, key, value):
        value = value or 'null'
        result = self.shell(
            'am', 'broadcast', '--include-stopped-packages', '-n',
            UI_PACKAGE + '/.BackupReceiver', '--es', 'operation', 'setting',
            '--es', 'namespace', 'secure', '--es', 'key', key, '--es', 'value', value)
        self.assertIn('result=0, data="ok"', result, result)

    def launch_picker(self):
        for _, package, _ in SCREENSAVERS:
            self.shell('am', 'force-stop', package)
        self.shell('am', 'force-stop', UI_PACKAGE)
        self.shell('am', 'start', '-W', '-n', UI_PACKAGE + '/.ScreensaverActivity')
        time.sleep(0.5)

    def read_window(self, path):
        self.shell('uiautomator', 'dump', path)
        return ET.fromstring(self.shell('cat', path))

    def test_each_new_app_opens_its_declared_settings_activity(self):
        self.assertEqual(self.shell('getprop', 'ro.kernel.qemu'), '1',
                         'This test may only change settings on an emulator.')
        before_component = self.setting('screensaver_components')
        before_enabled = self.setting('screensaver_enabled')
        self.assertTrue(before_component, 'The emulator needs an existing screensaver selection.')
        self.assertNotIn(',', before_component, 'The test requires one selected screensaver component.')

        try:
            for label, package, dream in SCREENSAVERS:
                with self.subTest(screensaver=label):
                    installed = self.shell('pm', 'path', package)
                    self.assertIn('package:', installed, package + ' is not installed on the emulator.')
                    self.write_setting('screensaver_components', dream)
                    self.assertEqual(self.setting('screensaver_components'), dream)
                    self.launch_picker()
                    window = self.read_window(ORIGINAL_SETTINGS_ACTIVITY)
                    current = [node.get('text', '') for node in window.iter()
                               if node.get('text', '').startswith('Current screensaver: ')]
                    self.assertEqual(current, ['Current screensaver: ' + label])

                    self.shell('input', 'keyevent', 'DPAD_UP')
                    self.shell('input', 'keyevent', 'DPAD_LEFT')
                    window = self.read_window(ORIGINAL_SETTINGS_ACTIVITY)
                    settings_button = [node for node in window.iter()
                                       if node.get('text') == 'Screensaver settings']
                    self.assertEqual(len(settings_button), 1)
                    self.assertEqual(settings_button[0].get('focused'), 'true')
                    self.shell('input', 'keyevent', 'DPAD_CENTER')

                    expected_activity = package + '/.SettingsActivity'
                    expected_title = label + ' settings'
                    for _ in range(20):
                        resumed = self.shell('dumpsys', 'activity', 'activities')
                        if expected_activity in resumed and 'mResumedActivity' in resumed:
                            break
                        time.sleep(0.25)
                    self.assertIn(expected_activity, resumed)
                    window = self.read_window(ORIGINAL_SETTINGS_ACTIVITY)
                    titles = [node.get('text', '') for node in window.iter()]
                    self.assertIn(expected_title, titles)

        finally:
            self.write_setting('screensaver_components', before_component)
            self.write_setting('screensaver_enabled', before_enabled)
            self.assertEqual(self.setting('screensaver_components'), before_component)
            self.assertEqual(self.setting('screensaver_enabled'), before_enabled)


if __name__ == '__main__':
    unittest.main()
