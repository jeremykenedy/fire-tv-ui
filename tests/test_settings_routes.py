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
    ('Retro Flight', 'com.jeremykenedy.retroflight',
     'com.jeremykenedy.retroflight/.RetroFlightDreamService'),
    ('Rain on Glass', 'com.jeremykenedy.rainonglass',
     'com.jeremykenedy.rainonglass/.RainDreamService'),
    ('Rainforest Cascade', 'com.jeremykenedy.rainforestcascade',
     'com.jeremykenedy.rainforestcascade/.WaterfallDreamService'),
    ('Blue Meridian', 'com.jeremykenedy.bluemeridian',
     'com.jeremykenedy.bluemeridian/.BlueMeridianDreamService'),
    ('Helios Lightfield', 'com.jeremykenedy.helioslightfield',
     'com.jeremykenedy.helioslightfield/.HeliosLightfieldDreamService'),
    ('Pipeworks Dream', 'com.jeremykenedy.pipeworksdream',
     'com.jeremykenedy.pipeworksdream/.PipeworksDreamService'),
    ('Slot Vision', 'com.jeremykenedy.reelatrium',
     'com.jeremykenedy.reelatrium/.ReelAtriumDreamService'),
    ('Hearth Presence', 'com.jeremykenedy.hearthpresence',
     'com.jeremykenedy.hearthpresence/.HearthDreamService'),
    ('Aqua Surface Drift', 'com.jeremykenedy.aquasurfacedrift',
     'com.jeremykenedy.aquasurfacedrift/.AquaSurfaceDreamService'),
    ('Cloud Drift Clock', 'com.jeremykenedy.clouddriftclock',
     'com.jeremykenedy.clouddriftclock/.CloudDreamService'),
    ('Earthbound Orbit', 'com.jeremykenedy.earthboundorbit',
     'com.jeremykenedy.earthboundorbit/.EarthboundOrbitDreamService'),
    ('Perseid Passage', 'com.jeremykenedy.perseidpassage',
     'com.jeremykenedy.perseidpassage/.MeteorDreamService'),
    ('Split-Flap Drift', 'com.jeremykenedy.splitflapdrift',
     'com.jeremykenedy.splitflapdrift/.SplitFlapDreamService'),
    ('Canopy Drift', 'com.jeremykenedy.canopydrift',
     'com.jeremykenedy.canopydrift/.CanopyDreamService'),
    ('Contour Flow', 'com.jeremykenedy.contourflow',
     'com.jeremykenedy.contourflow/.ContourFlowDreamService'),
    ('Skyburst Nocturne', 'com.jeremykenedy.skyburstnocturne',
     'com.jeremykenedy.skyburstnocturne/.FireworksDreamService'),
    ('Pulse Circuit', 'com.jeremykenedy.pulsecircuit',
     'com.jeremykenedy.pulsecircuit/.PulseDreamService'),
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
        self.shell('am', 'force-stop', UI_PACKAGE)
        self.shell('am', 'start', '-W', '-n', UI_PACKAGE + '/.ScreensaverActivity')
        time.sleep(0.5)

    def read_window(self, path):
        self.shell('uiautomator', 'dump', path)
        return ET.fromstring(self.shell('cat', path))

    def test_configure_any_saver_preserves_active_selection_timer_and_focus(self):
        self.assertEqual(self.shell('getprop', 'ro.kernel.qemu'), '1',
                         'This test may only change settings on an emulator.')
        before_component = self.setting('screensaver_components')
        before_enabled = self.setting('screensaver_enabled')
        before_timeout = self.shell('settings', 'get', 'system', 'screen_off_timeout')
        before_sleep = self.setting('sleep_timeout')
        self.assertTrue(before_component, 'The emulator needs an existing screensaver selection.')
        self.assertNotIn(',', before_component, 'The test requires one selected screensaver component.')

        requested = {package.strip() for package in
                     os.environ.get('FIRE_TV_UI_TEST_PACKAGES', '').split(',') if package.strip()}
        available = {package for _, package, _ in SCREENSAVERS}
        self.assertFalse(requested - available, 'Unknown settings test packages: ' +
                         ', '.join(sorted(requested - available)))
        selected = [entry for entry in SCREENSAVERS if not requested or entry[1] in requested]

        try:
            for label, package, dream in selected:
                with self.subTest(screensaver=label):
                    installed = self.shell('pm', 'path', package)
                    self.assertIn('package:', installed, package + ' is not installed on the emulator.')
                    self.launch_picker()
                    target = 'Configure ' + label
                    before_bounds = None
                    for _ in range(100):
                        self.shell('input', 'keyevent', 'DPAD_LEFT', 'DPAD_LEFT')
                        window = self.read_window(ORIGINAL_SETTINGS_ACTIVITY)
                        focused = [node for node in window.iter()
                                   if node.get('focused') == 'true']
                        if any(node.get('content-desc') == target for node in focused):
                            before_bounds = next(node.get('bounds') for node in focused
                                                 if node.get('content-desc') == target)
                            break
                        self.shell('input', 'keyevent', 'DPAD_DOWN')
                    self.assertIsNotNone(before_bounds, 'Configure was not reachable: ' + label)
                    self.shell('input', 'keyevent', 'DPAD_CENTER')

                    expected_activity = package + '/.SettingsActivity'
                    expected_title = (label if package in ('com.jeremykenedy.reelatrium',
                                                           'com.jeremykenedy.hearthpresence')
                                      else label + ' settings')
                    for _ in range(20):
                        resumed = [line for line in self.shell('dumpsys', 'activity', 'activities').splitlines()
                                   if 'mResumedActivity:' in line or 'topResumedActivity=' in line]
                        if any(expected_activity in line for line in resumed):
                            break
                        time.sleep(0.25)
                    self.assertTrue(any(expected_activity in line for line in resumed), resumed)
                    window = self.read_window(ORIGINAL_SETTINGS_ACTIVITY)
                    titles = [node.get('text', '') for node in window.iter()]
                    self.assertIn(expected_title, titles)
                    self.assertEqual(self.setting('screensaver_components'), before_component)
                    self.assertEqual(self.setting('screensaver_enabled'), before_enabled)
                    self.assertEqual(self.shell('settings', 'get', 'system', 'screen_off_timeout'), before_timeout)
                    self.assertEqual(self.setting('sleep_timeout'), before_sleep)
                    self.shell('input', 'keyevent', 'BACK')
                    time.sleep(0.5)
                    returned = self.read_window(ORIGINAL_SETTINGS_ACTIVITY)
                    focused = [node for node in returned.iter()
                               if node.get('focused') == 'true']
                    self.assertTrue(any(node.get('content-desc') == target for node in focused),
                                    'Configure focus was not restored for ' + label)
                    self.assertEqual(next(node.get('bounds') for node in focused
                                          if node.get('content-desc') == target), before_bounds)

        finally:
            self.write_setting('screensaver_components', before_component)
            self.write_setting('screensaver_enabled', before_enabled)
            self.assertEqual(self.setting('screensaver_components'), before_component)
            self.assertEqual(self.setting('screensaver_enabled'), before_enabled)


if __name__ == '__main__':
    unittest.main()
