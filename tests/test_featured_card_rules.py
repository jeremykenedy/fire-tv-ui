from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class FeaturedCardRuleChecks(unittest.TestCase):
    def test_hidden_identity_is_stable_and_scoped_to_one_provider_item(self):
        with tempfile.TemporaryDirectory() as folder:
            subprocess.run(['javac', '-d', folder,
                            str(ROOT / 'app/src/com/jeremykenedy/firetv/ui/FeaturedCardRules.java'),
                            str(ROOT / 'tests/FeaturedCardRulesTest.java')], check=True)
            subprocess.run(['java', '-cp', folder,
                            'com.jeremykenedy.firetv.ui.FeaturedCardRulesTest'], check=True)
