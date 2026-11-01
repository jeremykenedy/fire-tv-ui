from pathlib import Path
import hashlib
import json
import subprocess
import tempfile
import unittest
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]


class BundledIconChecks(unittest.TestCase):
    def test_catalog_source_hashes_match_bundled_vector_bytes(self):
        catalog = json.loads((ROOT / 'icons/catalog.json').read_text())
        for entry in catalog:
            with self.subTest(icon=entry['id']):
                source = (ROOT / entry['source']).read_bytes()
                self.assertEqual(hashlib.sha256(source).hexdigest(), entry['source_sha256'])

    def test_audio_brands_have_distinct_bundled_artwork(self):
        catalog = json.loads((ROOT / 'icons/catalog.json').read_text())
        by_id = {entry['id']: entry for entry in catalog}
        brands = {'sony', 'kenwood', 'insignia', 'denon', 'yamaha', 'onkyo',
                  'pioneer', 'marantz', 'bose', 'jbl', 'sonos', 'samsung',
                  'lg', 'vizio', 'harman-kardon', 'klipsch'}
        self.assertTrue(brands.issubset(by_id))
        hashes = set()
        for brand in brands:
            entry = by_id[brand]
            image = (ROOT / 'icons' / entry['file']).read_bytes()
            self.assertEqual(image[:8], b'\x89PNG\r\n\x1a\n')
            self.assertEqual(int.from_bytes(image[16:20], 'big'), 1920)
            self.assertEqual(int.from_bytes(image[20:24], 'big'), 1080)
            hashes.add(hashlib.sha256(image).hexdigest())
        self.assertEqual(len(hashes), len(brands))

    def test_filesystem_preserves_custom_and_racing_files_and_retries_indexing(self):
        with tempfile.TemporaryDirectory() as temp:
            subprocess.run(['javac', '-d', temp,
                            str(ROOT / 'app/src/com/jeremykenedy/firetv/ui/IconFiles.java'),
                            str(ROOT / 'tests/IconFilesTest.java')], check=True)
            subprocess.run(['java', '-cp', temp, 'com.jeremykenedy.firetv.ui.IconFilesTest', temp], check=True)

    def test_signed_payload_contains_the_complete_canonical_icon_catalog(self):
        config = json.loads((ROOT / 'build.json').read_text())
        apk = ROOT / 'dist' / ('fire-tv-ui-' + config['version'] + '.apk')
        if not apk.exists():
            apk = ROOT / 'dist' / ('fire-tv-ui-' + config['version'] + '-unsigned.apk')
        if not apk.exists():
            self.skipTest('Build the native APK first.')
        with ZipFile(apk) as archive:
            manifest = json.loads(archive.read('assets/input-icons/manifest.json'))
            catalog = json.loads((ROOT / 'icons/catalog.json').read_text())
            self.assertEqual(len(manifest), len(catalog))
            self.assertEqual(len({row['file'] for row in manifest}), len(catalog))
            self.assertEqual({row['file'] for row in manifest},
                             {row['file'] for row in catalog})
            for row in manifest:
                data = archive.read('assets/input-icons/' + row['file'])
                self.assertEqual(data, (ROOT / 'icons' / row['file']).read_bytes())
                self.assertEqual(len(data), row['bytes'])
                self.assertEqual(hashlib.sha256(data).hexdigest(), row['sha256'])
            self.assertEqual(archive.read('assets/input-icons/NOTICE.txt'), (ROOT / 'icons/NOTICE.txt').read_bytes())
            self.assertEqual(archive.read('assets/input-icons/catalog.json'), (ROOT / 'icons/catalog.json').read_bytes())
