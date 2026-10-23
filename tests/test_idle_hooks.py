from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]


class IdleHookChecks(unittest.TestCase):
    def test_built_launcher_uses_selected_delay_and_fresh_resume_timestamp(self):
        root = ROOT / '.build/decoded/smali'
        if not root.exists():
            self.skipTest('Build the launcher before verifying its actual dispatch hooks.')
        threshold = (root / 'Q3/S6.smali').read_text()
        self.assertNotIn('const-wide/32 v5, 0x15f90', threshold)
        self.assertIn('DeviceSettings;->launcherIdleDelay()J', threshold)
        dispatch = (root / 'Q3/T6.smali').read_text()
        self.assertIn('    .locals 7', dispatch)
        self.assertIn('LQ3/T3;->l1:LN/g0;', dispatch)
        self.assertIn('sub-long v3, v5, v3', dispatch)
        self.assertIn('IdleScreensaver;->start(Landroid/content/Context;J)V', dispatch)
        main = (root / 'com/overdevs/at4k/MainActivity.smali').read_text()
        resume = re.search(r'\.method public final onResume\(\)V.*?\.end method', main, re.S).group(0)
        self.assertIn('currentTimeMillis()J', resume)
        self.assertIn('LQ3/T3;->S(J)V', resume)
