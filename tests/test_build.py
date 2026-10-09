import hashlib
import json
from pathlib import Path
import sys
import unittest
import xml.etree.ElementTree as ET
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
from patch_launcher import classes, java_hash, replace_once
from capture_previews import full_component, preview_file


class BuildChecks(unittest.TestCase):
    def test_pinned_base(self):
        config = json.loads((ROOT / 'build.json').read_text())
        self.assertEqual(hashlib.sha256((ROOT / 'vendor/launcher-base.apk').read_bytes()).hexdigest(),
                         config['base_sha256'])

    def test_unknown_input_fails_closed(self):
        for value in ('unexpected', 'anchor anchor'):
            with self.assertRaises(ValueError):
                replace_once(value, 'anchor', 'new')

    def test_menu_hash_is_java_compatible(self):
        self.assertEqual(java_hash('Premium'), 0x503d6637)
        self.assertEqual(java_hash('Help'), 0x224361)

    def test_preview_components_and_assets(self):
        self.assertEqual(full_component('com.tv/.Dream'), 'com.tv/com.tv.Dream')
        self.assertEqual(preview_file('com.tv/.Dream'), preview_file('com.tv/com.tv.Dream'))
        with self.assertRaises(ValueError):
            full_component('../private/data')
        index = json.loads((ROOT / 'app/previews/index.json').read_text())
        for component, filename in index.items():
            self.assertEqual(filename, preview_file(component))
            image = ROOT / 'app/previews' / filename
            self.assertTrue(image.is_file(), component)
            self.assertTrue(image.read_bytes().startswith(b'\xff\xd8'), component)
        apk = ROOT / '.build/combined.apk'
        if (ROOT / '.build/decoded').exists():
            self.assertTrue(apk.is_file(), 'Complete the APK build before checking its artifacts')
        if apk.exists():
            with ZipFile(apk) as archive:
                self.assertIsNone(archive.testzip())
                self.assertIn('classes.dex', archive.namelist())
                self.assertIn('classes2.dex', archive.namelist())
                for filename in index.values():
                    self.assertEqual(archive.read('assets/screensaver-previews/' + filename),
                                     (ROOT / 'app/previews' / filename).read_bytes())

    def test_manifest_and_features(self):
        path = ROOT / '.build/decoded/AndroidManifest.xml'
        if not path.exists():
            self.skipTest('Build the APK for artifact checks')
        android = '{http://schemas.android.com/apk/res/android}'
        root = ET.parse(path).getroot()
        self.assertEqual(root.get('package'), 'com.jeremykenedy.firetv.ui')
        self.assertEqual(root.find('application').get(android + 'label'), 'Fire TV UI')
        permissions = {node.get(android + 'name') for node in root.findall('uses-permission')}
        self.assertIn('android.permission.WRITE_SECURE_SETTINGS', permissions)
        self.assertIn('android.permission.QUERY_ALL_PACKAGES', permissions)
        self.assertNotIn('com.android.vending.BILLING', permissions)
        self.assertIn('android.permission.INTERNET', permissions)
        self.assertIn('android.permission.ACCESS_NETWORK_STATE', permissions)
        for child in root.find('application'):
            self.assertNotIn('datatransport', child.get(android + 'name', ''))
            self.assertNotIn('billingclient', child.get(android + 'name', ''))
        self.assertTrue(any(node.get(android + 'name') == 'com.amazon.ftv.screensaver'
                            for node in root.find('queries').findall('package')))
        self.assertTrue(any('ScreensaverActivity' in node.get(android + 'name', '')
                            for node in root.find('application').findall('activity')))

    def test_help_and_feature_state(self):
        decoded = ROOT / '.build/decoded'
        if not decoded.exists():
            self.skipTest('Build the APK for artifact checks')
        paths = classes(decoded)
        main = paths['Lcom/jeremykenedy/firetv/ui/MainActivity;'].read_text()
        self.assertIn('DeviceSettings;->initialize', main)
        self.assertNotIn('->getBoolean(Ljava/lang/String;Z)Z', main)
        self.assertNotIn('app_launch_count', main)
        self.assertNotIn('app_launch_count', paths['LQ3/T3;'].read_text())
        telemetry = paths['Lcom/google/android/datatransport/cct/CctBackendFactory;'].read_text()
        self.assertNotIn('new-instance', telemetry.split('.method public create')[1])
        settings = paths['LQ3/M7;'].read_text()
        self.assertIn('ScreensaverAction;', settings)
        self.assertNotIn('Choose installed TV screensaver', settings)
        self.assertNotIn('Premium', settings)
        self.assertNotIn('one-time purchase', settings)
        self.assertNotIn('AT4K', settings)
        self.assertNotIn('View project', settings)
        for feature in ('Custom Video Wallpaper', 'Clock and Weather', 'Year Progress',
                        'Custom Multiple Images Slideshow', 'ICON PACKS & CUSTOM ICONS'):
            self.assertIn(feature, settings)
        self.assertEqual(paths['LQ3/r8;'].read_text().count('Support via BuyMeACoffee'), 0)


if __name__ == '__main__':
    unittest.main()
