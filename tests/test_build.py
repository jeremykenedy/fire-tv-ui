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
    def test_rotation_setting_restarts_the_effect_without_recreating_home(self):
        decoded = ROOT / '.build/decoded'
        if not decoded.exists():
            self.skipTest('Build the native launcher to check rotation lifecycle wiring.')
        built = classes(decoded)
        model = built['LQ3/T3;'].read_text()
        self.assertIn('featuredRotation:LN/g0;', model)
        self.assertIn('.method public final refreshFeaturedRotation()V', model)
        main = built['Lcom/jeremykenedy/firetv/ui/MainActivity;'].read_text()
        resume = main.split('.method public final onResume()V', 1)[1].split('.end method', 1)[0]
        self.assertIn('LQ3/T3;->refreshFeaturedRotation()V', resume)
        self.assertNotIn('->recreate()', resume)
        wallpaper = built['LO4/m;'].read_text()
        self.assertIn('iget-object v9, v0, LQ3/T3;->featuredRotation:LN/g0;', wallpaper)
        self.assertIn('filled-new-array {v2, v3, v4, v7, v9}, [Ljava/lang/Object;', wallpaper)

    def test_appearance_screensaver_row_opens_the_dedicated_controls(self):
        decoded = ROOT / '.build/decoded'
        if not decoded.exists():
            self.skipTest('Build the launcher to check the actual Appearance route.')
        built = classes(decoded)
        launcher = built['LQ3/M7;'].read_text()
        start = launcher.index('const-string v0, "Appearance_Screensaver"')
        end = launcher.index(':goto_19e', start)
        row = launcher[start:end]
        self.assertIn('ScreensaverSettingsRow;->draw', row)
        self.assertNotIn('LA3/a;->A(', row,
                         'The legacy toggle must not remain rendered alongside its replacement.')
        shortcut = built['Lcom/jeremykenedy/firetv/ui/ScreensaverSettingsRow;'].read_text()
        self.assertIn('ScreensaverAction;-><init>', shortcut)
        self.assertIn('move-object v6, p2', shortcut)
        self.assertIn('Appearance_Favorites', shortcut)
        self.assertNotIn('SharedPreferences', shortcut)

    def test_grouped_cards_keep_positions_and_backgrounds_keep_main_lifecycle_hooks(self):
        decoded = ROOT / '.build/decoded'
        if not decoded.exists():
            self.skipTest('Build the launcher before verifying actual native integration.')
        built = classes(decoded)
        rotation = built['LQ3/h8;'].read_text()
        self.assertNotIn('LQ3/T3;->U(I)V', rotation)
        self.assertNotIn('LQ3/T3;->t0:LN/g0;', rotation)
        row = built['LQ3/r3;'].read_text()
        self.assertLess(row.index('FeaturedGroupBridge;->current'), row.index('new-instance v9, LN4/k;'),
                        'Both rendering and the existing click action must receive the current child.')
        main = built['Lcom/jeremykenedy/firetv/ui/MainActivity;'].read_text()
        bridge = built['Lcom/jeremykenedy/firetv/ui/FeaturedGroupBridge;'].read_text()
        self.assertIn('FeaturedGroups;->feedKey(Ljava/lang/String;J)', bridge)
        self.assertIn('FeaturedGroups;->current(Ljava/lang/Object;Ljava/lang/String;)', bridge)
        self.assertIn('FeaturedGroupBridge;->start', main)
        self.assertIn('FeaturedCardClock;->stop', main)
        self.assertIn('MenuBackgroundView;->resumed(Z)V', main)
        resume = main[main.index('.method public final onResume()V'):]
        resume = resume[:resume.index('.end method')]
        self.assertLess(resume.index('\n    :resume_idle_reset_done\n'), resume.index('FeaturedGroupBridge;->start'),
                        'Cold Main has no model until Compose assigns it; its clock must still start.')
        self.assertLess(resume.index('\n    :resume_idle_reset_done\n'), resume.index('MenuBackgroundView;->resumed'),
                        'Native backgrounds must start on the first resume too.')
        background = built['LQ3/T5;'].read_text()
        self.assertIn('MenuBackgroundLayer;->draw', background)
        self.assertIn('if-nez v14, :goto_3', background)
        manifest = (decoded / 'AndroidManifest.xml').read_text()
        self.assertIn('MenuBackgroundActivity', manifest)

    def test_featured_setup_keeps_required_base_navigation_callbacks(self):
        decoded = ROOT / '.build/decoded'
        if not decoded.exists():
            self.skipTest('Build the native launcher to check navigation callback wiring.')
        built = classes(decoded)
        base = built['LA3/a;'].read_text()
        self.assertIn('const-string v3, "onSelect"', base)
        self.assertIn('const-string v3, "onNavigateLeft"', base)
        setup = built['Lcom/jeremykenedy/firetv/ui/FeaturedCardSetupRow;'].read_text()
        manager = built['Lcom/jeremykenedy/firetv/ui/ChannelManageRow;'].read_text()
        for row in (setup, manager):
            self.assertIn('move-object v6, p2', row,
                          'Every added row must receive the actual nonnull left-navigation callback.')
            self.assertNotIn('const/4 v6, 0x0', row)
            self.assertNotIn('const/4 v4, 0x0', row, 'The focus requester is required too.')
        launcher = built['LQ3/M7;'].read_text()
        setup_call = 'invoke-static {v13, v10, v6}, Lcom/jeremykenedy/firetv/ui/FeaturedCardSetupRow;->draw'
        self.assertIn(setup_call, launcher)
        self.assertIn('move-object/from16 v73, p4', launcher,
                      'Manage cards must navigate left using the existing onDismiss callback.')

    def test_featured_card_copy_preserves_the_producer_source_and_visibility(self):
        decoded = ROOT / '.build/decoded'
        if not decoded.exists():
            self.skipTest('Build the native launcher to check its featured card copy.')
        classes_by_name = classes(decoded)
        card = classes_by_name['LQ3/S8;'].read_text()
        start = card.index('.method public static a(LQ3/S8;)LQ3/S8;')
        copy = card[start:card.index('.end method', start)]
        self.assertIn('iget-object v2, p0, LQ3/S8;->b:Ljava/lang/String;', copy)
        self.assertNotIn('const-string v2, "Watch Next"', copy)
        self.assertIn('iget-boolean v6, p0, LQ3/S8;->f:Z', copy)
        mapper = classes_by_name['Landroid/support/v4/media/session/b;'].read_text()
        start = mapper.index('.method public static M(Ljava/util/List;Ljava/util/List;)Ljava/util/ArrayList;')
        mapper = mapper[start:mapper.index('.end method', start)]
        self.assertIn('FeaturedProgramMapper;->map', mapper)
        programs = classes_by_name['Lcom/jeremykenedy/firetv/ui/FeaturedProgramMapper;'].read_text()
        self.assertIn('const/4 v6, 0x1', programs, 'Keep the verified preview inclusion behavior unchanged.')
        self.assertNotIn('const/16 v3, 0xa', programs, 'One app must not consume a global ten-item cap.')

    def test_idle_uses_selected_saver_and_reviewed_preview_mappings(self):
        entries = json.loads((ROOT / 'app/screensavers.json').read_text())
        mapped = {item['id']: item for item in entries if 'preview' in item}
        self.assertEqual(len(mapped), 28)
        private_or_missing = {'androsaver', 'aquarium-4k', 'aquarium-live', 'contour-flow',
                              'nebula-drift', 'skyburst-nocturne', 'snoozy', 'split-flap-drift'}
        self.assertFalse(private_or_missing.intersection(mapped))
        controls = {item['id']: item['previewControls'] for item in entries if 'previewControls' in item}
        self.assertEqual(controls, {
            'contour-flow': 'com.jeremykenedy.contourflow/.ContourFlowActivity',
            'nebula-drift': 'com.jeremykenedy.nebuladrift/.NebulaActivity',
            'split-flap-drift': 'com.jeremykenedy.splitflapdrift/.SplitFlapActivity',
        })
        self.assertEqual(mapped['aerial']['preview'],
                         'com.neilturner.aerialviews/com.neilturner.aerialviews.ui.screensaver.TestActivity')
        for entry in mapped.values():
            self.assertEqual(entry['preview'].split('/', 1)[0], entry['package'])
        decoded = ROOT / '.build/decoded'
        if decoded.exists():
            idle = classes(decoded)['LQ3/T6;'].read_text()
            self.assertIn('IdleScreensaver;->start', idle)
            self.assertNotIn('com.neilturner.aerialviews', idle)
            self.assertIn('LQ3/T3;->V(Z)V', idle)
            self.assertIn('LQ3/T3;->S(J)V', idle)

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
                'a9642a20efe37fd3b39974d04f59b17eb4940935dbaca60c4b6cbf655eef03d0', 62276),
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
            release_tag = 'v2.0.0' if identifier == 'cloud-drift-clock' else 'v1.0.1' if identifier == 'blue-meridian' else {name: 'v1.1.0' for name in ('cloud-drift-clock', 'pipeworks-dream', 'nebula-drift', 'aqua-surface', 'split-flap-drift', 'canopy-drift')}.get(identifier, 'v1.0.0')
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
