from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class CardSourceChecks(unittest.TestCase):
    def test_empty_available_hidden_and_unavailable_sources_have_distinct_messages(self):
        with tempfile.TemporaryDirectory() as folder:
            subprocess.run(['javac', '-d', folder,
                            str(ROOT / 'app/src/com/jeremykenedy/firetv/ui/CardSourceStatus.java'),
                            str(ROOT / 'tests/CardSourceStatusTest.java')], check=True)
            subprocess.run(['java', '-cp', folder,
                            'com.jeremykenedy.firetv.ui.CardSourceStatusTest'], check=True)
