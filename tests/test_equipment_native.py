import os
import re
import subprocess
import time
import unittest
import xml.etree.ElementTree as ET


@unittest.skipUnless(os.environ.get('FIRE_TV_UI_EQUIPMENT_TEST_SERIAL'),
                     'Set FIRE_TV_UI_EQUIPMENT_TEST_SERIAL for physical Fire OS equipment recognition checks.')
class EquipmentNativeChecks(unittest.TestCase):
    def shell(self, *args):
        return subprocess.check_output(
            ['adb', '-s', os.environ['FIRE_TV_UI_EQUIPMENT_TEST_SERIAL'], 'shell', *args],
            text=True, timeout=30)

    def screen(self):
        self.shell('uiautomator', 'dump', '/sdcard/fire-tv-dialog-test.xml')
        xml = self.shell('cat', '/sdcard/fire-tv-dialog-test.xml')
        return ET.fromstring(xml)

    def screen_text(self):
        return [node.get('text') for node in self.screen().iter('node') if node.get('text')]

    def select_settings_card(self, label):
        for attempt in range(10):
            nodes = list(self.screen().iter('node'))
            targets = [node for node in nodes if node.get('focusable') == 'true'
                       and ' '.join((node.get('content-desc') or '').split()) == label]
            focused = [node for node in nodes if node.get('focused') == 'true']
            self.assertTrue(targets and focused, 'Missing settings card or remote focus: ' + label)
            target = list(map(int, re.findall(r'\d+', targets[0].get('bounds'))))
            current = list(map(int, re.findall(r'\d+', focused[0].get('bounds'))))
            dx = (target[0] + target[2] - current[0] - current[2]) // 2
            dy = (target[1] + target[3] - current[1] - current[3]) // 2
            if abs(dy) > 40:
                key = '20' if dy > 0 else '19'
            elif abs(dx) > 40:
                key = '22' if dx > 0 else '21'
            else:
                self.shell('input', 'keyevent', '23')
                time.sleep(1)
                return
            self.shell('input', 'keyevent', key)
            time.sleep(0.2)
        self.fail('Could not focus settings card: ' + label)

    def test_equipment_control_opens_repeatedly_from_settings_gui(self):
        for attempt in range(3):
            self.shell('am', 'start', '-n', 'com.amazon.tv.launcher/.ui.MainSettingsActivity')
            time.sleep(1)
            self.select_settings_card('Equipment Control')
            deadline = time.monotonic() + 12
            while time.monotonic() < deadline:
                texts = self.screen_text()
                if 'Receiver' in texts:
                    break
                time.sleep(0.5)
            self.assertNotIn('Equipment Control is not available', texts)
            self.assertIn('Receiver', texts)

    def test_known_connected_remote_recovers_after_recognition_flag_is_cleared(self):
        before = self.shell('settings', 'get', 'global', 'show_dc_settings').strip()
        self.assertEqual(before, '1', 'Pair the physical remote before running this test.')
        try:
            # Reproduce Fire OS losing recognition while the same remote is connected.
            self.shell('settings', 'put', 'global', 'show_dc_settings', '0')
            self.shell('am', 'start', '-a', 'com.amazon.tv.oobe.settings.ACTION.DEVICE_CONTROL',
                       '-d', 'devicecontrolsettings://')
            deadline = time.monotonic() + 8
            while time.monotonic() < deadline:
                if self.shell('settings', 'get', 'global', 'show_dc_settings').strip() == '1':
                    break
                time.sleep(0.25)
            self.assertEqual(self.shell('settings', 'get', 'global', 'show_dc_settings').strip(), '1',
                             'The connected, previously recognized remote lost Equipment Control.')
            deadline = time.monotonic() + 15
            while time.monotonic() < deadline:
                texts = self.screen_text()
                if 'Receiver' in texts and 'Equipment Control is not available' not in texts:
                    return
                time.sleep(0.5)
            self.fail('Equipment Control did not recover after remote recognition returned.')
        finally:
            self.shell('settings', 'put', 'global', 'show_dc_settings', before)
