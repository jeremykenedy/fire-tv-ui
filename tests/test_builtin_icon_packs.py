from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
from icon_pack_patch import patch_builtin_icon_packs
from patch_launcher import classes


class BuiltInIconPackPatchChecks(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        jar = ROOT / '.cache/apktool.jar'
        if not jar.is_file():
            raise unittest.SkipTest('Build the native APK first to cache verified Apktool.')
        cls.fixture = tempfile.TemporaryDirectory()
        decoded = Path(cls.fixture.name) / 'decoded'
        subprocess.run(['java', '-jar', str(jar), 'd', '-r', '-o', str(decoded),
                        str(ROOT / 'vendor/launcher-base.apk')], check=True, capture_output=True)
        cls.original = classes(decoded)

    @classmethod
    def tearDownClass(cls):
        cls.fixture.cleanup()

    def copy_paths(self, folder):
        result = {}
        for index, name in enumerate(('LQ3/F3;', 'Lq0/c;',
                'Landroid/support/v4/media/session/b;', 'LQ3/A7;', 'LQ3/U2;')):
            path = Path(folder) / f'{index}.smali'
            shutil.copy2(self.original[name], path)
            result[name] = path
        return result

    def test_picker_reload_and_individual_preview_use_installed_artwork(self):
        with tempfile.TemporaryDirectory() as folder:
            paths = self.copy_paths(folder)
            original = {name: path.read_text() for name, path in paths.items()}
            patch_builtin_icon_packs(paths)
            picker = paths['LQ3/F3;'].read_text()
            self.assertIn('"builtin:clean_line"', picker)
            self.assertIn('"builtin:muted_solid"', picker)
            self.assertIn('"transparent_mode"', picker)
            self.assertIn('queryIntentActivities', picker)
            self.assertIn('BuiltInIconPacks;->mappings', paths['Lq0/c;'].read_text())
            self.assertIn('BuiltInIconPacks;->render', paths['Lq0/c;'].read_text())
            self.assertIn(':installed_pack_artwork', paths['LQ3/A7;'].read_text())
            self.assertIn(':installed_pack_preview', paths['LQ3/U2;'].read_text())
            # Existing external-pack parsing and PM resources remain available.
            for name, before in original.items():
                after = paths[name].read_text()
                for operation in ('queryIntentActivities', 'getResourcesForApplication',
                                  'getXml', 'setTint', 'Bitmap;->compress'):
                    self.assertEqual(before.count(operation), after.count(operation), (name, operation))

    def test_changed_vendor_seam_fails_without_partial_file_changes(self):
        with tempfile.TemporaryDirectory() as folder:
            paths = self.copy_paths(folder)
            last = paths['LQ3/U2;']
            last.write_text(last.read_text().replace(':try_start_0', ':vendor_changed', 1))
            before = {name: path.read_bytes() for name, path in paths.items()}
            with self.assertRaises(ValueError):
                patch_builtin_icon_packs(paths)
            self.assertEqual(before, {name: path.read_bytes() for name, path in paths.items()})
