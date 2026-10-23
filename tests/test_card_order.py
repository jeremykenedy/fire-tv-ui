from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class CardOrderChecks(unittest.TestCase):
    def test_order_survives_refresh_without_changing_other_feeds(self):
        with tempfile.TemporaryDirectory() as folder:
            subprocess.run(['javac', '-d', folder,
                            str(ROOT / 'app/src/com/jeremykenedy/firetv/ui/FeaturedCardRules.java'),
                            str(ROOT / 'app/src/com/jeremykenedy/firetv/ui/CardOrderRules.java'),
                            str(ROOT / 'tests/CardOrderTest.java')], check=True)
            subprocess.run(['java', '-cp', folder,
                            'com.jeremykenedy.firetv.ui.CardOrderTest'], check=True)
