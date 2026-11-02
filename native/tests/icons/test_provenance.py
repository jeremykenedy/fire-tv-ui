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
        review = json.loads((ROOT / 'native/icons/catalog-review.json').read_text())['remainingAssets']
        self.assertEqual(40, len(review))
        self.assertEqual(len(review), len({item['id'] for item in review}))
        bundled = {item['id'] for item in provenance}
        self.assertEqual(set(legacy), bundled | {item['id'] for item in review})
        for item in review:
            self.assertTrue(item['localHashMatches'])
            self.assertTrue(item['reason'])
            self.assertEqual(item['decision'] == 'include', item['id'] in bundled)
            if item['decision'] == 'include' and item['sourceUrl']:
                self.assertTrue(item['upstreamHashMatches'])
                self.assertTrue(item['licenseEvidence'])
        entries = (ROOT / "native/src/com/jeremykenedy/firetv/ui/nativeapp/icons/IconCatalog.java").read_text()
        self.assertEqual(bundled, set(re.findall(r'new Entry\("([^\"]+)"', entries)))
        expected = set()
        for item in provenance:
            original = legacy[item['id']]
            self.assertEqual(item['sourceSha256'], hashlib.sha256((ROOT / original['source']).read_bytes()).hexdigest())
            image = ROOT / 'native/res/drawable-nodpi' / item['resource']
            self.assertEqual(item['imageSha256'], hashlib.sha256(image.read_bytes()).hexdigest())
            expected.add(image.name)
        self.assertEqual(expected, {path.name for path in (ROOT / 'native/res/drawable-nodpi').glob('icon_*.png')})
        notice = (ROOT / 'native/res/raw/icon_notices.txt').read_text()
        self.assertTrue(notice.startswith((ROOT / 'native/icons/NOTICE.txt').read_text()))
        for license_file in ['project-MIT.txt', 'simple-icons-CC0.txt']:
            self.assertIn((ROOT / 'native/icons' / license_file).read_text(), notice)

if __name__ == '__main__':
    unittest.main()
