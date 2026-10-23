from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class PlexSessionChecks(unittest.TestCase):
    def test_late_pair_or_server_response_cannot_reconnect_after_a_new_choice(self):
        with tempfile.TemporaryDirectory() as folder:
            subprocess.run(['javac', '-d', folder,
                            str(ROOT / 'app/src/com/jeremykenedy/firetv/ui/PlexSession.java'),
                            str(ROOT / 'tests/PlexSessionTest.java')], check=True)
            subprocess.run(['java', '-cp', folder,
                            'com.jeremykenedy.firetv.ui.PlexSessionTest'], check=True)
