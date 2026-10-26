from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class MenuBackgroundMotionChecks(unittest.TestCase):
    def test_motion_continuity_and_existing_choice_compatibility(self):
        source = ROOT / 'app/src/com/jeremykenedy/firetv/ui'
        with tempfile.TemporaryDirectory() as folder:
            subprocess.run(['javac', '-d', folder,
                            str(source / 'MenuBackgroundOptions.java'),
                            str(source / 'MenuBackgroundClock.java'),
                            str(source / 'MenuBackgroundMotion.java'),
                            str(ROOT / 'tests/MenuBackgroundMotionTest.java')], check=True)
            subprocess.run(['java', '-cp', folder,
                            'com.jeremykenedy.firetv.ui.MenuBackgroundMotionTest'], check=True)
