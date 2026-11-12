#!/usr/bin/env python3
"""Verify every legacy entry has an explicit review and native assets match reviewed bytes."""
import hashlib
import json
import re
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[3]

class ProvenanceTest(unittest.TestCase):
    def test_complete_review_and_bundled_bytes(self):
        legacy = {item['id']: item for item in json.loads((ROOT / 'icons/catalog.json').read_text())}
        provenance = json.loads((ROOT / 'native/icons/provenance.json').read_text())['assets']
        review_document = json.loads((ROOT / 'native/icons/catalog-review.json').read_text())
        review = review_document['remainingAssets']
        replacements = {item['id']: item for item in review_document.get('approvedReplacements', [])}
        self.assertEqual(40, len(review))
        self.assertEqual(len(review), len({item['id'] for item in review}))
        bundled = {item['id'] for item in provenance}
        self.assertEqual(set(legacy), bundled)
        for item in review:
            self.assertTrue(item['localHashMatches'])
            self.assertTrue(item['reason'])
            self.assertEqual(item['decision'] == 'include' or item['id'] in replacements, item['id'] in bundled)
            if item['decision'] == 'include' and item['sourceUrl']:
                self.assertTrue(item['upstreamHashMatches'])
                self.assertTrue(item['licenseEvidence'])
        entries = (ROOT / "native/src/com/jeremykenedy/firetv/ui/nativeapp/icons/IconCatalog.java").read_text()
        labels = dict(re.findall(r'new Entry\("([^\"]+)", "([^\"]+)"', entries))
        self.assertEqual({item['id']: item['name'] for item in legacy.values()}, labels)
        expected = set()
        for item in provenance:
            original = legacy[item['id']]
            source_file = item.get('sourceFile', original['source'])
            self.assertEqual(item['sourceSha256'], hashlib.sha256((ROOT / source_file).read_bytes()).hexdigest())
            if item['id'] in replacements:
                replacement = replacements[item['id']]
                self.assertEqual(source_file, replacement['sourceFile'])
                self.assertEqual(item['sourceSha256'], replacement['sourceSha256'])
                self.assertEqual(item['licenseEvidence'], replacement['licenseEvidence'])
                self.assertNotEqual(original['source_sha256'], item['sourceSha256'])
                self.assertTrue(replacement['reason'])
            image = ROOT / 'native/res/drawable-nodpi' / item['resource']
            self.assertEqual(item['imageSha256'], hashlib.sha256(image.read_bytes()).hexdigest())
            if item['id'] in replacements:
                self.assertNotEqual(item['imageSha256'], hashlib.sha256(
                    (ROOT / 'icons' / original['file']).read_bytes()).hexdigest())
            expected.add(image.name)
        self.assertEqual(expected, {path.name for path in (ROOT / 'native/res/drawable-nodpi').glob('icon_*.png')})
        notice = (ROOT / 'native/res/raw/icon_notices.txt').read_text()
        self.assertTrue(notice.startswith((ROOT / 'native/icons/NOTICE.txt').read_text()))
        for license_file in ['project-MIT.txt', 'simple-icons-CC0.txt']:
            self.assertIn((ROOT / 'native/icons' / license_file).read_text(), notice)

if __name__ == '__main__':
    unittest.main()
