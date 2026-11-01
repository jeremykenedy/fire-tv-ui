from pathlib import Path
import os
import subprocess
import sys
import tempfile
import unittest
from java_runtime import json_runtime

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
from tile_appearance_patch import patch_picker_fit


class IconOverrideChecks(unittest.TestCase):
    def test_picker_fit_preserves_image_selection_and_other_layout_code(self):
        original = """    iput-object v15, v4, LY2/h;->c:Ljava/lang/Object;
    sget-object v7, Lw0/h;->a:Lw0/J;
    invoke-static/range {v3 .. v11}, LO2/s;->b(Ljava/lang/Object;Ljava/lang/String;LN2/k;LZ/o;Lw0/i;LN/p;III)V
"""
        fitted = patch_picker_fit(original)
        self.assertEqual(original.replace('Lw0/h;->a:', 'Lw0/h;->b:'), fitted)
        with self.assertRaises(ValueError):
            patch_picker_fit(fitted)
        with self.assertRaises(ValueError):
            patch_picker_fit(original + original)

    def test_factory_and_theme_choices_preserve_other_tiles_and_visibility(self):
        jar = json_runtime()
        stubs = {
            'android/content/SharedPreferences.java': '''package android.content;
import java.util.*;
public interface SharedPreferences { String getString(String k,String d); Set<String> getStringSet(String k,Set<String>d); Editor edit();
interface Editor { Editor putString(String k,String v); Editor putStringSet(String k,Set<String>v); Editor remove(String k); boolean commit(); void apply(); }}''',
            'android/content/Context.java': '''package android.content; public class Context { public SharedPreferences prefs; }''',
            'android/content/ContextWrapper.java': '''package android.content; public class ContextWrapper extends Context { public Context base; public Context getBaseContext(){return base;} }''',
            'android/app/Activity.java': '''package android.app; public class Activity extends android.content.ContextWrapper { public int refreshes; public void recreate(){refreshes++;} }''',
            'android/app/AlertDialog.java': '''package android.app; public class AlertDialog { public static class Builder { public Builder(android.content.Context c){} public Builder setTitle(String s){return this;} public Builder setMessage(String s){return this;} public Builder setPositiveButton(String s,Object o){return this;} public void show(){} } }''',
            'com/jeremykenedy/firetv/ui/DeviceSettings.java': '''package com.jeremykenedy.firetv.ui; public class DeviceSettings { public static android.content.SharedPreferences prefs(android.content.Context c){return c.prefs;} }''',
        }
        with tempfile.TemporaryDirectory() as temp:
            paths = []
            for name, value in stubs.items():
                path = Path(temp) / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(value)
                paths.append(str(path))
            subprocess.run(['javac', '-cp', str(jar), '-d', temp, *paths,
                            str(ROOT / 'app/src/com/jeremykenedy/firetv/ui/IconOverrides.java'),
                            str(ROOT / 'tests/IconOverridesTest.java')], check=True)
            subprocess.run(['java', '-cp', temp + os.pathsep + str(jar),
                            'com.jeremykenedy.firetv.ui.IconOverridesTest'], check=True)
