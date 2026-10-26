from pathlib import Path
import hashlib
import json
import subprocess
import tempfile
import unittest
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]


class BundledIconChecks(unittest.TestCase):
    def test_filesystem_preserves_custom_and_racing_files_and_retries_indexing(self):
        with tempfile.TemporaryDirectory() as temp:
            subprocess.run(['javac', '-d', temp,
                            str(ROOT / 'app/src/com/jeremykenedy/firetv/ui/IconFiles.java'),
                            str(ROOT / 'tests/IconFilesTest.java')], check=True)
            subprocess.run(['java', '-cp', temp, 'com.jeremykenedy.firetv.ui.IconFilesTest', temp], check=True)

    def test_signed_payload_contains_the_same_32_canonical_source_images(self):
        config = json.loads((ROOT / 'build.json').read_text())
        apk = ROOT / 'dist' / ('fire-tv-ui-' + config['version'] + '-unsigned.apk')
        if not apk.exists():
            self.skipTest('Build the unsigned native APK first.')
        with ZipFile(apk) as archive:
            manifest = json.loads(archive.read('assets/input-icons/manifest.json'))
            self.assertEqual(len(manifest), 32)
            self.assertEqual(len({row['file'] for row in manifest}), 32)
            self.assertEqual({row['file'] for row in manifest},
                             {row['file'] for row in json.loads((ROOT / 'icons/catalog.json').read_text())})
            for row in manifest:
                data = archive.read('assets/input-icons/' + row['file'])
                self.assertEqual(data, (ROOT / 'icons' / row['file']).read_bytes())
                self.assertEqual(len(data), row['bytes'])
                self.assertEqual(hashlib.sha256(data).hexdigest(), row['sha256'])
            self.assertEqual(archive.read('assets/input-icons/NOTICE.txt'), (ROOT / 'icons/NOTICE.txt').read_bytes())
            self.assertEqual(archive.read('assets/input-icons/catalog.json'), (ROOT / 'icons/catalog.json').read_bytes())
