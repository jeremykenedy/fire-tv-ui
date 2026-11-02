import hashlib
import json
from pathlib import Path
import sys
import unittest
import xml.etree.ElementTree as ET
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
from capture_previews import full_component, preview_file


class BuildChecks(unittest.TestCase):







    def test_prism_published_contract_and_physical_preview(self):
        entries = json.loads((ROOT / 'app/screensavers.json').read_text())
        entry = next(item for item in entries if item['id'] == 'prism-clock')
        self.assertEqual(entry['package'], 'com.jeremykenedy.prismclock')
        self.assertEqual(entry['dream'], entry['package'] + '/.PrismDreamService')
        self.assertEqual(entry['tag'], 'v1.1.0')
        self.assertEqual(entry['bytes'], 127947)
        self.assertEqual(entry['sha256'],
                         '6d1a3204ca648033c3c0c1d68adcceb29e90ed1ff61ec24b7f65e1326b7621e8')
        self.assertEqual(entry['url'], 'https://github.com/jeremykenedy/prism-clock/releases/'
                         'download/v1.1.0/prism-clock.apk')
        source = json.loads((ROOT / 'app/previews/sources.json').read_text())[
            full_component(entry['dream'])]
        self.assertIn('physical Fire TV', source['capture_note'])
        self.assertEqual(hashlib.sha256((ROOT / source['source_file']).read_bytes()).hexdigest(),
                         source['source_sha256'])
        preview = ROOT / 'app/previews' / preview_file(entry['dream'])
        self.assertEqual(hashlib.sha256(preview.read_bytes()).hexdigest(), source['preview_sha256'])

        self.assertEqual(source['settings_schema_count'], 12)
        self.assertEqual(source['clock_visibility'], {'key': 'show_clock', 'type': 'boolean',
                                                    'default': True, 'randomAllowed': False})
        self.assertTrue((ROOT / 'app/previews/licenses/prism-clock-NOTICE.txt').is_file())
        self.assertTrue((ROOT / 'app/previews/licenses/prism-clock-APACHE-2.0.txt').is_file())

    def test_nibbles_published_pin_and_native_preview_match(self):
        entries = json.loads((ROOT / 'app/screensavers.json').read_text())
        entry = next(item for item in entries if item['id'] == 'nibbles-hd')
        self.assertEqual(entry['package'], 'com.jeremykenedy.nibbleshd')
        self.assertEqual(entry['dream'], entry['package'] + '/.NibblesDreamService')
        self.assertEqual(entry['preview'], entry['package'] + '/.PreviewActivity')
        self.assertEqual(entry['tag'], 'v1.0.0')
        self.assertEqual(entry['bytes'], 41727)
        self.assertEqual(entry['sha256'],
                         'c49bc22e5cbf5067da4bbcb1cd63140d31b0a49a83f6826e111986ee4b7d7971')
        self.assertEqual(entry['url'], 'https://github.com/jeremykenedy/nibbles-hd/'
                         'releases/download/v1.0.0/nibbles-hd.apk')
        source = json.loads((ROOT / 'app/previews/sources.json').read_text())[
            full_component(entry['dream'])]
        self.assertEqual(source['settings_schema_count'], 8)
        self.assertEqual(source['min_sdk'], 23)
        self.assertEqual(source['target_sdk'], 36)
        self.assertEqual(source['signer_sha256'],
                         'cdf8f4f4fd70dbb133fe8b05b6ab165eb3e98b033ddcb7d305cf0d84d874bc75')
        self.assertEqual(hashlib.sha256((ROOT / source['source_file']).read_bytes()).hexdigest(),
                         source['source_sha256'])
        preview = ROOT / 'app/previews' / preview_file(entry['dream'])
        self.assertEqual(hashlib.sha256(preview.read_bytes()).hexdigest(), source['preview_sha256'])
        self.assertIn('Original autonomous snake gameplay',
                      (ROOT / 'app/previews/licenses/nibbles-hd-NOTICE.txt').read_text())
        self.assertTrue((ROOT / 'app/previews/licenses/nibbles-hd-APACHE-2.0.txt').is_file())

    def test_lumen_published_pin_matches_native_preview_and_offline_notices(self):
        entries = json.loads((ROOT / 'app/screensavers.json').read_text())
        entry = next(item for item in entries if item['id'] == 'lumen-breaker')
        self.assertEqual(entry['package'], 'com.jeremykenedy.lumenbreaker')
        self.assertEqual(entry['dream'], entry['package'] + '/.LumenDreamService')
        self.assertEqual(entry['preview'], entry['package'] + '/.PreviewActivity')
        self.assertEqual(entry['tag'], 'v1.0.0')
        self.assertEqual(entry['bytes'], 74551)
        self.assertEqual(entry['sha256'],
                         'fc0513f85fc8563cd8463546108bbf5ea9c459ab72465c669b126fcc6c14269c')
        source = json.loads((ROOT / 'app/previews/sources.json').read_text())[
            full_component(entry['dream'])]
        self.assertEqual(source['settings_schema_count'], 9)
        self.assertEqual(source['min_sdk'], 23)
        self.assertEqual(source['target_sdk'], 36)
        self.assertEqual(source['signer_sha256'],
                         'd3404786108a0b54fab7bedb1119d3851bd688cdea9c4712155a9582852182e4')
        self.assertEqual(hashlib.sha256((ROOT / source['source_file']).read_bytes()).hexdigest(),
                         source['source_sha256'])
        preview = ROOT / 'app/previews' / preview_file(entry['dream'])
        self.assertEqual(hashlib.sha256(preview.read_bytes()).hexdigest(), source['preview_sha256'])
        self.assertIn('original to Lumen Breaker',
                      (ROOT / 'app/previews/licenses/lumen-breaker-NOTICE.txt').read_text())
        self.assertTrue((ROOT / 'app/previews/licenses/lumen-breaker-APACHE-2.0.txt').is_file())




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

    def test_weather_public_release_uses_current_native_preview_and_explicit_settings(self):
        entries = json.loads((ROOT / 'app/screensavers.json').read_text())
        entry = next(item for item in entries if item['id'] == 'skycast-weather')
        self.assertEqual(entry['package'], 'com.jeremykenedy.skycastweather')
        self.assertEqual(entry['dream'], entry['package'] + '/.WeatherDreamService')
        self.assertEqual(entry['preview'], entry['package'] + '/.PreviewActivity')
        self.assertEqual(entry['tag'], 'v1.0.0')
        self.assertEqual(entry['url'], 'https://github.com/jeremykenedy/skycast-weather/releases/download/v1.0.0/skycast-weather.apk')
        self.assertEqual(entry['sha256'], 'b8680ebc7314e8687281e5d405aa84d1b0f38d1f6505193f652b96fc9cf0e7e0')
        self.assertEqual(entry['bytes'], 39552020)
        source = json.loads((ROOT / 'app/previews/sources.json').read_text())[full_component(entry['dream'])]
        self.assertEqual(source['settings_keys'], ['units', 'days', 'motion', 'show_weather', 'location'])
        self.assertFalse(source['randomizable'])
        self.assertIn('overlay OFF', source['capture_note'])
        self.assertEqual(hashlib.sha256((ROOT / source['source_file']).read_bytes()).hexdigest(), source['source_sha256'])
        preview = ROOT / 'app/previews' / preview_file(entry['dream'])
        self.assertEqual(hashlib.sha256(preview.read_bytes()).hexdigest(), source['preview_sha256'])
        notice = (ROOT / 'app/previews/licenses/skycast-weather-NOTICE.txt').read_text()
        self.assertIn('anotherview', notice)
        self.assertIn('Creative Commons Attribution 3.0', notice)

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
        self.assertEqual(entry['tag'], 'v1.1.0')
        self.assertEqual(entry['url'], 'https://github.com/jeremykenedy/hearth-presence/releases/'
                         'download/v1.1.0/hearth-presence.apk')
        self.assertEqual(entry['sha256'],
                         '9541eb4fe2f4d7d69ac1f85b38144f5b5926274291ebec796d8150d27e07b781')
        self.assertEqual(entry['bytes'], 121223466)
        sources = json.loads((ROOT / 'app/previews/sources.json').read_text())
        source = sources[full_component(entry['dream'])]
        self.assertIn('physical Fire TV AFTDEC012E', source['capture_note'])
        self.assertEqual(hashlib.sha256((ROOT / source['source_file']).read_bytes()).hexdigest(),
                         source['source_sha256'])
        image = ROOT / 'app/previews' / preview_file(entry['dream'])
        self.assertEqual(hashlib.sha256(image.read_bytes()).hexdigest(), source['preview_sha256'])

        self.assertEqual(source['settings_schema_count'], 11)
        self.assertEqual(source['license'], 'CC-BY-3.0')
        self.assertIn('free video library', (ROOT / 'app/previews/licenses/hearth-presence-NOTICE.txt').read_text())

    def test_rainforest_update_preserves_identity_and_physical_preview(self):
        entries = json.loads((ROOT / 'app/screensavers.json').read_text())
        self.assertEqual(len(entries), 36)
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
                '4d2d7db1d07fb7a317d4461c8946969ba83b949b7a38801d9377b4275a039f30', 29368),
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
                '62a038159d8db3b04346e23b0ae68707781fa48299ae37a6f77626f9ae646082', 62276),
            'aqua-surface': (
                'Aqua Surface Drift', 'com.jeremykenedy.aquasurfacedrift',
                'com.jeremykenedy.aquasurfacedrift/.AquaSurfaceDreamService', 'jeremykenedy/aqua-surface-drift',
                'b0aa42f82a1561e7c27fba8229c83d00eff93498a4e3e404c580137333021049', 45773),
            'cloud-drift-clock': (
                'Cloud Drift Clock', 'com.jeremykenedy.clouddriftclock',
                'com.jeremykenedy.clouddriftclock/.CloudDreamService', 'jeremykenedy/cloud-drift-clock',
                'abb8fdf3abdd1c94b95106b6bd0cbdf85822ccffd4caec9cdbd6dcb99919fae8', 40297575),
            'blue-meridian': (
                'Blue Meridian', 'com.jeremykenedy.bluemeridian',
                'com.jeremykenedy.bluemeridian/.BlueMeridianDreamService', 'jeremykenedy/blue-meridian',
                '7f3b575f45d7afa5ca68ff8510d84f5b42c6ac6c3c21903628898f6a5dff5ec2', 2569417),
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
                '9cbf91acf586bdf0cb056fef5d53e204ddc9be38f9e5c994a603bbd666cbb351', 29368),
            'canopy-drift': (
                'Canopy Drift', 'com.jeremykenedy.canopydrift',
                'com.jeremykenedy.canopydrift/.CanopyDreamService',
                'jeremykenedy/canopy-drift',
                '5175918229ce69cc423bfeb35daa0729ecc7be853801fa7e367a5de06ca4e14c', 197325),
            'contour-flow': (
                'Contour Flow', 'com.jeremykenedy.contourflow',
                'com.jeremykenedy.contourflow/.ContourFlowDreamService',
                'jeremykenedy/contour-flow',
                '6b4302ee3220c25df5b40c3e7ff6d72d6c859b4403203f0577a01a9b7cc2ebf6', 66572),
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
            release_tag = 'v1.1.1' if identifier == 'pipeworks-dream' else 'v1.1.0' if identifier == 'contour-flow' else 'v2.0.0' if identifier == 'cloud-drift-clock' else 'v1.0.1' if identifier == 'blue-meridian' else {name: 'v1.1.0' for name in ('cloud-drift-clock', 'pipeworks-dream', 'nebula-drift', 'aqua-surface', 'split-flap-drift', 'canopy-drift')}.get(identifier, 'v1.0.0')
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




if __name__ == '__main__':
    unittest.main()
