import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('maintenance_build', ROOT / 'maintenance/build_repair.py')
recipe = importlib.util.module_from_spec(spec)
spec.loader.exec_module(recipe)


class MaintenanceRecipeTest(unittest.TestCase):
    def test_default_and_historical_recipes_reconstruct_the_reviewed_source(self):
        with tempfile.TemporaryDirectory(prefix='maintenance-recipes-') as folder:
            for version in recipe.VERSIONS:
                with self.subTest(version=version):
                    output = Path(folder) / version
                    command = [sys.executable, str(ROOT / 'maintenance/build_repair.py'),
                               '--verify-only', '--output-dir', str(output)]
                    if version != recipe.DEFAULT_VERSION:
                        command += ['--version', version]
                    subprocess.run(command, check=True, capture_output=True, text=True)
                    metadata = json.loads((ROOT / 'maintenance' / version / 'release.json').read_text())
                    provenance = json.loads((output / 'build-provenance.json').read_text())
                    self.assertEqual(provenance['verifiedSourceTree'], metadata['expectedSourceTree'])
                    self.assertEqual(provenance['version'], version)
                    self.assertEqual(provenance['versionCode'], metadata['versionCode'])
                    self.assertEqual(provenance['phase'], 'source-verified')
                    self.assertFalse(provenance['signed'])
                    self.assertFalse(provenance['independentReplacement'])
                    self.assertNotIn('artifacts', provenance)
                    self.assertFalse((output / 'source/.build').exists())

    def test_patch_tampering_is_rejected_before_reconstruction(self):
        with tempfile.TemporaryDirectory(prefix='maintenance-tamper-') as folder:
            path = Path(folder) / 'repair.patch'
            path.write_bytes(b'unreviewed repair')
            metadata = json.loads((ROOT / 'maintenance/1.18.0/release.json').read_text())
            self.assertNotEqual(hashlib.sha256(path.read_bytes()).hexdigest(), metadata['patchSha256'])
            with self.assertRaisesRegex(ValueError, 'checksum'):
                recipe.reconstruct(Path(folder), metadata, path)
            self.assertFalse((Path(folder) / 'source').exists())

    def test_output_is_never_reused(self):
        with tempfile.TemporaryDirectory(prefix='maintenance-existing-') as folder:
            sentinel = Path(folder) / 'keep.txt'
            sentinel.write_text('Keep existing output')
            with self.assertRaises(FileExistsError):
                recipe.build(Path(folder), True)
            self.assertEqual(sentinel.read_text(), 'Keep existing output')


if __name__ == '__main__':
    unittest.main()
