from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class RecommendationChecks(unittest.TestCase):
    def test_only_requested_recommendations_with_original_actions_are_eligible(self):
        with tempfile.TemporaryDirectory() as folder:
            subprocess.run(['javac', '-d', folder,
                            str(ROOT / 'app/src/com/jeremykenedy/firetv/ui/RecommendationRules.java'),
                            str(ROOT / 'tests/RecommendationRulesTest.java')], check=True)
            subprocess.run(['java', '-cp', folder,
                            'com.jeremykenedy.firetv.ui.RecommendationRulesTest'], check=True)
