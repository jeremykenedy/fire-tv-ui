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
    def test_idle_uses_selected_saver_and_reviewed_preview_mappings(self):
        entries = json.loads((ROOT / 'app/screensavers.json').read_text())
        mapped = {item['id']: item for item in entries if 'preview' in item}
        self.assertEqual(set(mapped), {'slot-vision', 'hearth-presence', 'rainforest-cascade'})
        for entry in mapped.values():
            self.assertEqual(entry['preview'], entry['package'] + '/.PreviewActivity')
        decoded = ROOT / '.build/decoded'
        if decoded.exists():
            idle = classes(decoded)['LQ3/T6;'].read_text()
            self.assertIn('IdleScreensaver;->start', idle)
            self.assertNotIn('com.neilturner.aerialviews', idle)
            self.assertIn('LQ3/T3;->V(Z)V', idle)
            self.assertIn('LQ3/T3;->S(J)V', idle)

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

    def test_twilight_hearth_catalog_release_and_preview_are_pinned(self):
        entries = json.loads((ROOT / 'app/screensavers.json').read_text())
        entry = next(item for item in entries if item['id'] == 'twilight-hearth')
        self.assertEqual(entry['package'], 'com.jeremykenedy.twilighthearth')
        self.assertEqual(entry['dream'], 'com.jeremykenedy.twilighthearth/.HearthDreamService')
        self.assertEqual(entry['repo'], 'jeremykenedy/twilight-hearth')
        self.assertEqual(entry['tag'], 'v1.1.0')
        self.assertEqual(entry['url'],
                         'https://github.com/jeremykenedy/twilight-hearth/releases/download/'
                         'v1.1.0/twilight-hearth.apk')
        self.assertEqual(entry['bytes'], 27207363)
        self.assertEqual(entry['sha256'],
                         'dd39810f8bf2a67af7f133554897741323d9a14d606b3fc1a441285646b440f5')

        component = full_component(entry['dream'])
        index = json.loads((ROOT / 'app/previews/index.json').read_text())
        filename = preview_file(entry['dream'])
        self.assertEqual(index[component], filename)
        sources = json.loads((ROOT / 'app/previews/sources.json').read_text())
        source = sources[component]
        original = ROOT / source['source_file']
        preview = ROOT / 'app/previews' / filename
        self.assertTrue(original.is_file())
        self.assertEqual(hashlib.sha256(original.read_bytes()).hexdigest(), source['source_sha256'])
        self.assertEqual(hashlib.sha256(preview.read_bytes()).hexdigest(), source['preview_sha256'])

    def test_hearth_release_pin_matches_verified_artifact_and_physical_preview(self):
        entries = json.loads((ROOT / 'app/screensavers.json').read_text())
        entry = next(item for item in entries if item['id'] == 'hearth-presence')
        self.assertEqual(entry['package'], 'com.jeremykenedy.hearthpresence')
        self.assertEqual(entry['dream'], 'com.jeremykenedy.hearthpresence/.HearthDreamService')
        self.assertEqual(entry['tag'], 'v1.0.0')
        self.assertEqual(entry['url'], 'https://github.com/jeremykenedy/hearth-presence/releases/'
                         'download/v1.0.0/hearth-presence.apk')
        self.assertEqual(entry['sha256'],
                         '96067a79be86a0f29f034ee4bcc647e9da7195978b690a7d982f28a17b78278f')
        self.assertEqual(entry['bytes'], 69011424)
        sources = json.loads((ROOT / 'app/previews/sources.json').read_text())
        source = sources[full_component(entry['dream'])]
        self.assertIn('physical Fire TV AFTDEC012E', source['capture_note'])
        self.assertEqual(hashlib.sha256((ROOT / source['source_file']).read_bytes()).hexdigest(),
                         source['source_sha256'])
        image = ROOT / 'app/previews' / preview_file(entry['dream'])
        self.assertEqual(hashlib.sha256(image.read_bytes()).hexdigest(), source['preview_sha256'])

    def test_rainforest_update_preserves_identity_and_physical_preview(self):
        entries = json.loads((ROOT / 'app/screensavers.json').read_text())
        self.assertEqual(len(entries), 30)
        entry = next(item for item in entries if item['id'] == 'rainforest-cascade')
        self.assertEqual(entry['package'], 'com.jeremykenedy.rainforestcascade')
        self.assertEqual(entry['dream'], 'com.jeremykenedy.rainforestcascade/.WaterfallDreamService')
        self.assertEqual(entry['tag'], 'v1.1.0')
        self.assertEqual(entry['url'], 'https://github.com/jeremykenedy/rainforest-cascade/releases/'
                         'download/v1.1.0/rainforest-cascade.apk')
        self.assertEqual(entry['sha256'],
                         '88602f840deae1e9a7fa8de5b1dda03601c70d0d70399ab11262e1817e7de31f')
        self.assertEqual(entry['bytes'], 72053957)
        source = json.loads((ROOT / 'app/previews/sources.json').read_text())[
            full_component(entry['dream'])]
        self.assertIn('physical Fire TV AFTDEC012E', source['capture_note'])
        self.assertEqual(hashlib.sha256((ROOT / source['source_file']).read_bytes()).hexdigest(),
                         source['source_sha256'])
        image = ROOT / 'app/previews' / preview_file(entry['dream'])
        self.assertEqual(hashlib.sha256(image.read_bytes()).hexdigest(), source['preview_sha256'])

    def test_slot_release_preserves_installed_identity_and_verified_download(self):
        entries = json.loads((ROOT / 'app/screensavers.json').read_text())
        entry = next(item for item in entries if item['id'] == 'slot-vision')
        self.assertEqual(entry['name'], 'Slot Vision')
        self.assertEqual(entry['package'], 'com.jeremykenedy.reelatrium')
        self.assertEqual(entry['dream'], 'com.jeremykenedy.reelatrium/.ReelAtriumDreamService')
        self.assertEqual(entry['url'], 'https://github.com/jeremykenedy/slot-vision/releases/'
                         'download/v1.0.0/slot-vision.apk')
        self.assertEqual(entry['sha256'],
                         '0ee21b586f3b306e6da57cacc149bebece35a01e6ef2c7af1512c0ddc51354e4')
        self.assertEqual(entry['bytes'], 62076478)
        source = json.loads((ROOT / 'app/previews/sources.json').read_text())[
            full_component(entry['dream'])]
        self.assertEqual(source['source_project'], 'jeremykenedy/slot-vision')
        self.assertEqual(hashlib.sha256((ROOT / source['source_file']).read_bytes()).hexdigest(),
                         source['source_sha256'])
        image = ROOT / 'app/previews' / preview_file(entry['dream'])
        self.assertEqual(hashlib.sha256(image.read_bytes()).hexdigest(), source['preview_sha256'])

    def test_new_screensaver_catalog_releases_and_previews_are_pinned(self):
        expected = {
            'nebula-drift': (
                'Nebula Drift', 'com.jeremykenedy.nebuladrift',
                'com.jeremykenedy.nebuladrift/.NebulaDreamService', 'jeremykenedy/nebula-drift',
                '85964a8c4d63e9a4c3ba0624c5902e1b20d500614c676a709b393a561a6cb108', 29368),
            'vortex-spiral': (
                'Vortex Spiral', 'com.jeremykenedy.vortexspiral',
                'com.jeremykenedy.vortexspiral/.VortexDreamService', 'jeremykenedy/vortex-spiral',
                'dc689afed574024d97fc40ae40912cfc2d3d5ad6db3c1008ce2273118c6e72af', 49869),
            'signal-rain': (
                'Signal Rain', 'com.jeremykenedy.signalrain',
                'com.jeremykenedy.signalrain/.SignalRainDreamService', 'jeremykenedy/signal-rain',
                '39a05659217658d39d44200d931ace8fb4edfe5034372ee35c945159fe392615', 33485),
            'retro-flight': (
                'Retro Flight', 'com.jeremykenedy.retroflight',
                'com.jeremykenedy.retroflight/.RetroFlightDreamService', 'jeremykenedy/retro-flight',
                '7845db142ef5f4cb4e92d96c1b6fa92ee2f7bd86fe7c47cd52627b37cc92cbf2', 53965),
            'pipeworks-dream': (
                'Pipeworks Dream', 'com.jeremykenedy.pipeworksdream',
                'com.jeremykenedy.pipeworksdream/.PipeworksDreamService', 'jeremykenedy/pipeworks-dream',
                '49dbc66e28e69cb3cd4b4c70cc320bae127b2d450fc96ae11006a0df6c9eeb23', 66253),
            'aqua-surface': (
                'Aqua Surface Drift', 'com.jeremykenedy.aquasurfacedrift',
                'com.jeremykenedy.aquasurfacedrift/.AquaSurfaceDreamService', 'jeremykenedy/aqua-surface-drift',
                'cdab28be93938018c8fefd8cf0103c94d9c0c3413ce3b4f7c0359cca7d93cf82', 45773),
            'cloud-drift-clock': (
                'Cloud Drift Clock', 'com.jeremykenedy.clouddriftclock',
                'com.jeremykenedy.clouddriftclock/.CloudDreamService', 'jeremykenedy/cloud-drift-clock',
                '11b72b93a82012605348698b8ff53ee1be13fb8b0ffe0d128e6a1f4e23233d27', 320287),
            'earthbound-orbit': (
                'Earthbound Orbit', 'com.jeremykenedy.earthboundorbit',
                'com.jeremykenedy.earthboundorbit/.EarthboundOrbitDreamService',
                'jeremykenedy/earthbound-orbit',
                'b8ff3f377513360bd246ae569ac0872cfd9ed06d75a90d7f13199ae0715b21b9', 582419),
            'perseid-passage': (
                'Perseid Passage', 'com.jeremykenedy.perseidpassage',
                'com.jeremykenedy.perseidpassage/.MeteorDreamService',
                'jeremykenedy/perseid-passage',
                '9fa45a8f906fd9783d2831dcc594d3387e8f6cc5f95900be1b2447b450df4799', 33652),
            'split-flap-drift': (
                'Split-Flap Drift', 'com.jeremykenedy.splitflapdrift',
                'com.jeremykenedy.splitflapdrift/.SplitFlapDreamService',
                'jeremykenedy/split-flap-drift',
                'bf728f8c0c717fc791117c05e6f072fc5872c13dbd43ef9fd4de366cc230b820', 25272),
            'canopy-drift': (
                'Canopy Drift', 'com.jeremykenedy.canopydrift',
                'com.jeremykenedy.canopydrift/.CanopyDreamService',
                'jeremykenedy/canopy-drift',
                '268293380036ac64bbcb200921f9d8b16be8efa6f60dab304b2c29794e5781d1', 193229),
            'contour-flow': (
                'Contour Flow', 'com.jeremykenedy.contourflow',
                'com.jeremykenedy.contourflow/.ContourFlowDreamService',
                'jeremykenedy/contour-flow',
                'b6b2e05780b95a9e3b7ceb10bbe02a9701ba1bd6ca8172f47b972dd0c7416447', 29368),
            'skyburst-nocturne': (
                'Skyburst Nocturne', 'com.jeremykenedy.skyburstnocturne',
                'com.jeremykenedy.skyburstnocturne/.FireworksDreamService',
                'jeremykenedy/skyburst-nocturne',
                '2ce571933d6f4a8017ada3c5598d21edc363b51598f4d4c302bafebb0863a57a', 37748),
            'pulse-circuit': (
                'Pulse Circuit', 'com.jeremykenedy.pulsecircuit',
                'com.jeremykenedy.pulsecircuit/.PulseDreamService',
                'jeremykenedy/pulse-circuit',
                '5e71095d0b2966d1cf803ad9299ad7a233309848d1aea8e27423cfcf8ff96c95', 33485),
        }
        entries = {item['id']: item for item in json.loads((ROOT / 'app/screensavers.json').read_text())}
        index = json.loads((ROOT / 'app/previews/index.json').read_text())
        sources = json.loads((ROOT / 'app/previews/sources.json').read_text())
        for identifier, (name, package, dream, repo, digest, byte_count) in expected.items():
            entry = entries[identifier]
            self.assertEqual(entry['name'], name)
            self.assertEqual(entry['package'], package)
            self.assertEqual(entry['dream'], dream)
            self.assertEqual(entry['repo'], repo)
            release_tag = 'v1.0.1' if identifier == 'cloud-drift-clock' else 'v1.0.0'
            self.assertEqual(entry['tag'], release_tag)
            asset = 'aqua-surface-drift.apk' if identifier == 'aqua-surface' else f'{identifier}.apk'
            self.assertEqual(entry['url'],
                             f'https://github.com/{repo}/releases/download/{release_tag}/{asset}')
            self.assertEqual(entry['sha256'], digest)
            self.assertEqual(entry['bytes'], byte_count)

            component = full_component(dream)
            filename = preview_file(dream)
            self.assertEqual(index[component], filename)
            record = sources[component]
            source = ROOT / record['source_file']
            preview = ROOT / 'app/previews' / filename
            self.assertTrue(source.is_file())
            self.assertEqual(hashlib.sha256(source.read_bytes()).hexdigest(), record['source_sha256'])
            self.assertEqual(hashlib.sha256(preview.read_bytes()).hexdigest(), record['preview_sha256'])

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
        self.assertTrue(any(node.get(android + 'name') == 'com.amazon.tv.settings.v2'
                            for node in root.find('queries').findall('package')))
        self.assertTrue(any('ScreensaverActivity' in node.get(android + 'name', '')
                            for node in root.find('application').findall('activity')))
        receiver = next(node for node in root.find('application').findall('receiver')
                        if node.get(android + 'name', '').endswith('.ScreensaverSelectionReceiver'))
        self.assertEqual(receiver.get(android + 'exported'), 'true')
        self.assertEqual(receiver.get(android + 'permission'),
                         'android.permission.WRITE_SECURE_SETTINGS')
        self.assertTrue(any(action.get(android + 'name')
                            == 'com.jeremykenedy.firetv.ui.SELECT_SCREENSAVER'
                            for action in receiver.findall('intent-filter/action')))

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
