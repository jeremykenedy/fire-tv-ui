from pathlib import Path
import subprocess
import tempfile
import unittest
from timing_preferences import PREFERENCES

ROOT = Path(__file__).resolve().parents[1]


class FeaturedCardTimingChecks(unittest.TestCase):
    def test_engine_intervals_manual_mode_and_safe_defaults(self):
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp)
            stub = folder / 'android/content/SharedPreferences.java'
            stub.parent.mkdir(parents=True)
            stub.write_text(PREFERENCES)
            subprocess.run(['javac', '-d', temp, str(stub),
                            str(ROOT / 'app/src/com/jeremykenedy/firetv/ui/FeaturedCardTiming.java'),
                            str(ROOT / 'tests/FeaturedCardTimingTest.java')], check=True)
            subprocess.run(['java', '-cp', temp,
                            'com.jeremykenedy.firetv.ui.FeaturedCardTimingTest'], check=True)
