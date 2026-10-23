from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class HiddenCardChecks(unittest.TestCase):
    def test_hide_one_preserves_next_source_and_restore_choices(self):
        sources = {
            'android/content/Context.java': '''package android.content; public class Context { public String getPackageName(){return "ui.test";} public final Prefs prefs=new Prefs(); public ContentResolver getContentResolver(){return new ContentResolver();} }''',
            'android/content/ComponentName.java': '''package android.content; public class ComponentName { public String getClassName(){return "ui.test.PlexItemActivity";} }''',
            'android/content/Intent.java': '''package android.content; public class Intent { public static final int URI_INTENT_SCHEME=1; private String value; public static Intent parseUri(String value,int flags){Intent i=new Intent();i.value=value;return i;} public android.net.Uri getData(){return android.net.Uri.parse(value.split("#Intent;")[0]);} public ComponentName getComponent(){return value.contains("component=ui.test/.PlexItemActivity;")?new ComponentName():null;} }''',
            'android/content/ContentResolver.java': '''package android.content; public class ContentResolver { public android.database.Cursor query(android.net.Uri u,String[] p,String s,String[] a,String o){return null;} }''',
            'android/content/Prefs.java': '''package android.content; import java.util.*; public class Prefs { public boolean fail; private Set<String> saved=new HashSet<>(); public Set<String> getStringSet(String k,Set<String> fallback){return saved;} public Editor edit(){return new Editor();} public class Editor { Set<String> pending; public Editor putStringSet(String k,Set<String> v){pending=new HashSet<>(v);return this;} public boolean commit(){if(fail)return false;saved=pending;return true;} } }''',
            'android/database/Cursor.java': '''package android.database; public class Cursor implements AutoCloseable { public void close(){} public boolean moveToNext(){return false;} private final String[] values; public Cursor(String source,String title,String id,String intent){values=new String[]{source,title,id,"",intent};} public int getColumnIndex(String name){return java.util.Arrays.asList("package_name","title","internal_provider_id","content_id","intent_uri").indexOf(name);} public boolean isNull(int n){return values[n]==null;} public String getString(int n){return values[n];} }''',
            'android/net/Uri.java': '''package android.net; public class Uri { private final String value; private Uri(String v){value=v;} public static Uri parse(String v){return new Uri(v);} public String toString(){return value;} private java.net.URI uri(){return java.net.URI.create(value);} public String getScheme(){return uri().getScheme();} public String getAuthority(){return uri().getAuthority();} public String getQuery(){return uri().getRawQuery();} public String getFragment(){return uri().getRawFragment();} public String getEncodedPath(){return uri().getRawPath();} public java.util.List<String> getPathSegments(){return java.util.Arrays.asList(uri().getPath().substring(1).split("/"));} }''',
            'com/jeremykenedy/firetv/ui/DeviceSettings.java': '''package com.jeremykenedy.firetv.ui; public class DeviceSettings { public static android.content.Prefs prefs(android.content.Context c){return c.prefs;} }''',
        }
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp)
            files = []
            for name, content in sources.items():
                target = folder / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(content)
                files.append(str(target))
            subprocess.run(['javac', '-d', temp, *files,
                            str(ROOT / 'app/src/com/jeremykenedy/firetv/ui/FeaturedCardRules.java'),
                            str(ROOT / 'app/src/com/jeremykenedy/firetv/ui/PlexValues.java'),
                            str(ROOT / 'app/src/com/jeremykenedy/firetv/ui/HiddenCards.java'),
                            str(ROOT / 'tests/HiddenCardsTest.java')], check=True)
            subprocess.run(['java', '-cp', temp,
                            'com.jeremykenedy.firetv.ui.HiddenCardsTest'], check=True)
