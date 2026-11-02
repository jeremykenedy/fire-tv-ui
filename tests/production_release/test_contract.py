import importlib.util
import json
import os
import shutil
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('production_build', ROOT / 'native/production-build.py')
build = importlib.util.module_from_spec(spec)
spec.loader.exec_module(build)


class ProductionReleaseContractTest(unittest.TestCase):
    def setUp(self):
        self.config = build.release_config()
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.path = Path(self.tmp.name) / 'config.json'

    def check_config(self):
        self.path.write_text(json.dumps(self.config))
        return build.release_config(self.path)

    def test_version_must_be_semver_and_upgrade_shipped_code(self):
        for version, code in [('2.0', 79), ('02.0.0', 79), ('2.0.0-alpha', 79), ('2.0.0', 78), ('2.0.0', True)]:
            with self.subTest(version=version, code=code):
                self.config.update(version=version, versionCode=code)
                with self.assertRaises(ValueError):
                    self.check_config()

    def test_certificate_pin_cannot_be_replaced(self):
        self.config['signerSha256'] = 'a' * 64
        with self.assertRaises(ValueError):
            self.check_config()

    def test_actual_signer_and_debug_metadata_are_rejected(self):
        with self.assertRaises(ValueError):
            build.verify_signer_output('Signer #1 certificate SHA-256 digest: ' + 'a' * 64,
                                       self.config['signerSha256'])
        header = "package: name='com.jeremykenedy.firetv.ui' versionCode='79' versionName='2.0.0'"
        build.verify_apk_metadata(header, self.config)
        with self.assertRaises(ValueError):
            build.verify_apk_metadata(header + "\napplication-debuggable", self.config)
        with self.assertRaises(ValueError):
            build.verify_apk_metadata(header.replace("'79'", "'78'"), self.config)

    def test_release_labels_are_verified_in_compiled_manifest(self):
        badging = "application-label:'Fire TV UI'"
        tree = '\n'.join('      E: service (line=1)\n        A: name(0x01010003)="' + name
                         + '"\n        A: label(0x01010001)="' + label + '"'
                         for name, label in build.RELEASE_LABELS.items())
        build.verify_release_labels(badging, tree)
        with self.assertRaises(ValueError):
            build.verify_release_labels("application-label:'Fire TV UI Source Candidate'", tree)
        with self.assertRaises(ValueError):
            build.verify_release_labels(badging, tree.replace('Fire TV UI Home keys', 'Candidate'))

    def test_missing_signing_material_does_not_generate_key(self):
        key = Path(self.tmp.name) / 'missing.jks'
        with patch.dict(os.environ, {'FIRE_TV_UI_KEYSTORE': str(key),
                                     'FIRE_TV_UI_PASSWORD_FILE': str(key.with_suffix('.pass'))}):
            with self.assertRaises(ValueError):
                build.production_signer()
        self.assertFalse(key.exists())

    def test_unsigned_build_does_not_access_the_production_signer(self):
        with patch.object(build, 'production_signer', side_effect=AssertionError('signer accessed')):
            with patch.object(build, 'run', side_effect=RuntimeError('compile reached')):
                with self.assertRaisesRegex(RuntimeError, 'compile reached'):
                    build.build(Path(self.tmp.name) / 'out', Path('/sdk'), release=True, unsigned=True)

    def test_source_change_rejected(self):
        with patch.object(build, 'input_hashes', return_value={'src/A.java': 'changed'}):
            with self.assertRaises(RuntimeError):
                build.assert_frozen({'src/A.java': 'original'})

    def test_production_application_class_is_required(self):
        root = Path(self.tmp.name) / 'native'
        (root / 'production-src').mkdir(parents=True)
        shutil.copy(ROOT / 'native/AndroidManifest.xml', root)
        shutil.copy(ROOT / 'native/production-src/AndroidManifest.xml', root / 'production-src')
        shutil.copytree(ROOT / 'native/production-res', root / 'production-res')
        manifest = root / 'production-src/AndroidManifest.xml'
        tree = build.ET.parse(manifest)
        tree.getroot().find('application').set(build.attr('name'), 'example.OtherApplication')
        tree.write(manifest)
        with self.assertRaisesRegex(ValueError, 'application attributes'):
            build.validate(root)

    def test_protection_service_cannot_be_exposed(self):
        root = Path(self.tmp.name) / 'native'
        (root / 'production-src').mkdir(parents=True)
        shutil.copy(ROOT / 'native/AndroidManifest.xml', root)
        shutil.copy(ROOT / 'native/production-src/AndroidManifest.xml', root / 'production-src')
        shutil.copytree(ROOT / 'native/production-res', root / 'production-res')
        manifest = root / 'production-src/AndroidManifest.xml'
        tree = build.ET.parse(manifest)
        service = next(node for node in tree.getroot().findall('application/service')
                       if node.get(build.attr('name')) == build.PACKAGE + '.nativeapp.device.protection.LegacyProtectionService')
        service.set(build.attr('exported'), 'true')
        tree.write(manifest)
        with self.assertRaisesRegex(ValueError, 'Device protection service'):
            build.validate(root)

    def test_vpn_manifest_contract_rejects_exposure_and_extra_actions(self):
        for mutation in ('exported', 'permission', 'action', 'subtype', 'activity'):
            with self.subTest(mutation=mutation):
                root = Path(self.tmp.name) / mutation
                (root / 'production-src').mkdir(parents=True)
                shutil.copy(ROOT / 'native/AndroidManifest.xml', root)
                shutil.copy(ROOT / 'native/production-src/AndroidManifest.xml', root / 'production-src')
                shutil.copytree(ROOT / 'native/production-res', root / 'production-res')
                manifest = root / 'production-src/AndroidManifest.xml'
                tree = build.ET.parse(manifest)
                application = tree.getroot().find('application')
                service = next(node for node in application.findall('service')
                               if node.get(build.attr('name')) == build.PACKAGE + '.ProtectionService')
                if mutation == 'exported':
                    service.set(build.attr('exported'), 'true')
                elif mutation == 'permission':
                    service.attrib.pop(build.attr('permission'))
                elif mutation == 'action':
                    build.ET.SubElement(service.find('intent-filter'), 'action',
                                        {build.attr('name'): 'example.Unreviewed'})
                elif mutation == 'subtype':
                    service.find('property').set(build.attr('value'), 'unreviewed')
                else:
                    activity = next(node for node in application.findall('activity')
                                    if node.get(build.attr('name')) == build.PACKAGE + '.nativeapp.device.updates.UpdateBlockActivity')
                    activity.set(build.attr('exported'), 'true')
                tree.write(manifest)
                with self.assertRaises(ValueError):
                    build.validate(root)

    def test_preview_cannot_register_a_vpn_service(self):
        root = Path(self.tmp.name) / 'native'
        (root / 'production-src').mkdir(parents=True)
        shutil.copy(ROOT / 'native/AndroidManifest.xml', root)
        shutil.copy(ROOT / 'native/production-src/AndroidManifest.xml', root / 'production-src')
        shutil.copytree(ROOT / 'native/production-res', root / 'production-res')
        manifest = root / 'AndroidManifest.xml'
        tree = build.ET.parse(manifest)
        service = build.ET.SubElement(tree.getroot().find('application'), 'service')
        intent = build.ET.SubElement(service, 'intent-filter')
        build.ET.SubElement(intent, 'action', {build.attr('name'): 'android.net.VpnService'})
        tree.write(manifest)
        with self.assertRaisesRegex(ValueError, 'Preview must not register a VPN'):
            build.validate(root)

    def test_preview_and_production_manifest_contract(self):
        build.validate()
        preview = build.ET.parse(ROOT / 'native/AndroidManifest.xml').getroot()
        production = build.ET.parse(ROOT / 'native/production-src/AndroidManifest.xml').getroot()
        self.assertEqual(preview.get('package'), build.PREVIEW)
        self.assertEqual(production.find('application').get(build.attr('debuggable')), 'true')


if __name__ == '__main__':
    unittest.main()
