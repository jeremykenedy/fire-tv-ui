#!/usr/bin/env python3
"""Check release boundary failures without building an app, reading a key or requiring secrets."""
import importlib.util
import json
import os
from pathlib import Path
from types import SimpleNamespace
import tempfile
import unittest
from unittest.mock import patch

BUILD = Path(__file__).resolve().parents[2] / 'build.py'
spec = importlib.util.spec_from_file_location('preview_build', BUILD)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class PreviewReleaseTest(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.root = Path(self.folder.name).resolve()
        self.config = json.loads((BUILD.parent / 'preview-release.json').read_text())
        self.manifest = '<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.jeremykenedy.firetv.ui.preview"><application android:debuggable="false" android:allowBackup="false" /></manifest>'
        (self.root / 'AndroidManifest.xml').write_text(self.manifest)
        self.path = self.root / 'release.json'
        self.path.write_text(json.dumps(self.config))
        self.root_patch = patch.object(module, 'ROOT', self.root)
        self.root_patch.start()
        self.addCleanup(self.root_patch.stop)
        self.addCleanup(self.folder.cleanup)

    def args(self, **values):
        defaults = dict(debug=False, unsigned=True, output_dir=None, keystore=None, ks_pass_env=None, key_pass_env=None)
        defaults.update(values)
        return SimpleNamespace(**defaults)

    def load(self, **changes):
        self.path.write_text(json.dumps({**self.config, **changes}))
        return module.release_config(self.path)

    def test_reviewed_alpha_config_and_unsigned_never_require_a_secret(self):
        self.assertEqual(self.load()['version'], '2.0.0-alpha.3')
        self.assertEqual(self.load()['versionCode'], 4)
        with patch.dict(os.environ, {}, clear=True):
            module.signing_options(self.args(), self.config)

    def test_production_or_unversioned_and_debug_configuration_rejected(self):
        for change in ({'applicationId': 'com.jeremykenedy.firetv.ui'}, {'version': '2.0.0'},
                       {'version': '02.0.0-alpha.1'}, {'version': '2.0.0-alpha.01'},
                       {'versionCode': 1}, {'versionCode': True}, {'versionCode': 2100000001},
                       {'debuggable': True}, {'allowBackup': True}, {'minSdk': 26},
                       {'targetSdk': 35}, {'signerSha256': 'unknown'}, {'extra': 'unreviewed'}):
            with self.subTest(change=change), self.assertRaises(ValueError): self.load(**change)

    def test_duplicate_or_outside_configuration_rejected(self):
        self.path.write_text(self.path.read_text().replace('"schemaVersion": 1', '"schemaVersion": 1, "schemaVersion": 1'))
        with self.assertRaises(ValueError): module.release_config(self.path)
        with tempfile.NamedTemporaryFile(suffix='.json') as outside, self.assertRaises(ValueError):
            module.release_config(Path(outside.name))

    def test_manifest_cannot_register_home_or_accessibility_takeover(self):
        for addition in ('<activity><intent-filter><category android:name="android.intent.category.HOME" /></intent-filter></activity>',
                         '<service><intent-filter><action android:name="android.accessibilityservice.AccessibilityService" /></intent-filter></service>'):
            (self.root / 'AndroidManifest.xml').write_text(self.manifest.replace('/></manifest>', '>' + addition + '</application></manifest>'))
            with self.assertRaises(ValueError): module.release_config(self.path)
        (self.root / 'AndroidManifest.xml').write_text(self.manifest.replace('android:debuggable="false"', 'android:debuggable="true"'))
        with self.assertRaises(ValueError): module.release_config(self.path)

    def test_release_flags_cannot_fall_back_to_lab_key(self):
        for args in (self.args(debug=True), self.args(unsigned=False), self.args(keystore=self.path),
                     self.args(output_dir=self.root), self.args(output_dir=self.root / 'src/out')):
            with self.subTest(args=args), self.assertRaises(ValueError): module.signing_options(args, self.config)
        with self.assertRaises(ValueError): module.signing_options(self.args(ks_pass_env='TEST_PASS'), None)

    def test_signing_requires_explicit_nonempty_environment_names(self):
        key = self.root / 'fixture.jks'; key.write_bytes(b'not a signing key')
        args = self.args(unsigned=False, keystore=key, ks_pass_env='TEST_PREVIEW_PASS')
        with patch.dict(os.environ, {}, clear=True):
            with self.assertRaises(ValueError): module.signing_options(args, self.config)
        with patch.dict(os.environ, {'TEST_PREVIEW_PASS': 'synthetic fixture value'}, clear=True):
            module.signing_options(args, self.config)
            with self.assertRaises(ValueError): module.signing_options(self.args(unsigned=False, keystore=key, ks_pass_env='bad-name'), self.config)

    def test_secret_values_never_enter_signing_command(self):
        output = self.root / 'signed.apk'
        args = self.args(unsigned=False, keystore=self.path, ks_pass_env='TEST_PREVIEW_PASS', key_pass_env='TEST_KEY_PASS')
        commands = []
        def sign(*command): commands.append(command); output.write_bytes(b'signed fixture')
        with patch.object(module, 'run', side_effect=sign), patch.object(module, 'certificate', return_value=self.config['signerSha256']):
            self.assertEqual(module.sign_release(self.root, self.path, output, self.config, args), self.config['signerSha256'])
        self.assertIn('env:TEST_PREVIEW_PASS', commands[0]); self.assertIn('env:TEST_KEY_PASS', commands[0])
        self.assertNotIn('synthetic fixture value', str(commands))

    def test_wrong_actual_signer_removes_the_candidate(self):
        output = self.root / 'signed.apk'
        args = self.args(unsigned=False, keystore=self.path, ks_pass_env='TEST_PREVIEW_PASS')
        with (patch.object(module, 'run', side_effect=lambda *args: output.write_bytes(b'wrong signer')),
              patch.object(module, 'certificate', return_value='0' * 64)):
            with self.assertRaises(ValueError): module.sign_release(self.root, self.path, output, self.config, args)
        self.assertFalse(output.exists())

    def actual_manifest(self, **values):
        current = dict(package=self.config['applicationId'], code=self.config['versionCode'], version=self.config['version'], floor=27, target=36, debug='false', backup='false')
        current.update(values)
        badging = "package: name='{package}' versionCode='{code}' versionName='{version}'\nminSdkVersion:'{floor}'\ntargetSdkVersion:'{target}'\n".format(**current)
        tree = ('  E: manifest (line=1)\n      E: application (line=2)\n'
                '        A: http://schemas.android.com/apk/res/android:debuggable(0x0101000f)={debug}\n'
                '        A: http://schemas.android.com/apk/res/android:allowBackup(0x01010280)={backup}\n').format(**current)
        return badging, tree

    def test_actual_apk_manifest_requires_all_reviewed_values(self):
        with patch.object(module.subprocess, 'check_output', side_effect=self.actual_manifest()):
            module.verify_apk(self.root, self.path, self.config)
        for change in ({'package': 'com.example.foreign'}, {'code': 1}, {'version': '0.1.0-preview'},
                       {'floor': 26}, {'target': 35}, {'debug': 'true'}, {'backup': 'true'}):
            with self.subTest(change=change), patch.object(module.subprocess, 'check_output', side_effect=self.actual_manifest(**change)), self.assertRaises(ValueError):
                module.verify_apk(self.root, self.path, self.config)

    def test_release_configuration_is_a_frozen_build_input(self):
        (self.root / 'src').mkdir(); (self.root / 'res').mkdir()
        script = self.root / 'build.py'; script.write_text('fixture build script')
        with patch.object(module, '__file__', str(script)):
            before = module.source_hashes(self.path)
            self.path.write_text(self.path.read_text() + '\n')
            self.assertNotEqual(before, module.source_hashes(self.path))
            self.assertIn('release.json', before)


if __name__ == '__main__':
    unittest.main()
