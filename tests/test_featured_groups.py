from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class FeaturedGroupChecks(unittest.TestCase):
    def test_stable_slots_internal_rotation_refresh_removal_and_manual_selection(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            stubs = {
                'com/jeremykenedy/firetv/ui/FeaturedCardClock.java': 'package com.jeremykenedy.firetv.ui; class FeaturedCardClock { static void feedsChanged(){} }',
                'android/content/Context.java': 'package android.content; public class Context {}',
                'android/net/Uri.java': 'package android.net; public class Uri { public String toString(){return "";} }',
                'com/jeremykenedy/firetv/ui/FeedOrder.java': 'package com.jeremykenedy.firetv.ui; class FeedOrder { static String key(android.content.Context c,String s,long id){return s+id;} }',
                'com/jeremykenedy/firetv/ui/HiddenCards.java': 'package com.jeremykenedy.firetv.ui; class HiddenCards { static class Card {String key;} static Card card(String s,android.net.Uri u,boolean w){return null;} }',
            }
            paths = []
            for name, contents in stubs.items():
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(contents)
                paths.append(str(path))
            subprocess.run(['javac', '-d', folder, *paths,
                            str(ROOT / 'app/src/com/jeremykenedy/firetv/ui/FeaturedCardRules.java'),
                            str(ROOT / 'app/src/com/jeremykenedy/firetv/ui/FeaturedGroups.java'),
                            str(ROOT / 'tests/FeaturedGroupsTest.java')], check=True)
            subprocess.run(['java', '-cp', folder, 'com.jeremykenedy.firetv.ui.FeaturedGroupsTest'], check=True)
