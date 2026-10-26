from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class RecommendationArtworkChecks(unittest.TestCase):
    def test_artwork_stays_with_its_source_and_reviewed_public_origin(self):
        with tempfile.TemporaryDirectory() as folder:
            subprocess.run(['javac', '-d', folder,
                            str(ROOT / 'app/src/com/jeremykenedy/firetv/ui/RecommendationArtworkRules.java'),
                            str(ROOT / 'tests/RecommendationArtworkRulesTest.java')], check=True)
            subprocess.run(['java', '-cp', folder,
                            'com.jeremykenedy.firetv.ui.RecommendationArtworkRulesTest'], check=True)
