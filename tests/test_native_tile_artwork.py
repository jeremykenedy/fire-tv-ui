from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class NativeTileArtworkChecks(unittest.TestCase):
    def test_existing_custom_art_wins_and_missing_native_art_gets_bundled_fallback(self):
        stubs = {
            'android/content/Context.java': '''package android.content;
public class Context { public java.io.File directory; public final android.content.res.AssetManager assets=new android.content.res.AssetManager(); public java.io.File getFilesDir(){return directory;} public android.content.res.AssetManager getAssets(){return assets;} }''',
            'android/content/res/AssetManager.java': '''package android.content.res;
public class AssetManager { public int opens; public boolean fail; public java.io.InputStream open(String path)throws java.io.IOException{opens++; if(fail)throw new java.io.IOException("Unavailable"); return new java.io.FileInputStream(new java.io.File(System.getProperty("asset.root"),path));} }''',
            'android/net/Uri.java': '''package android.net; public class Uri { private final java.io.File file; private Uri(java.io.File f){file=f;} public static Uri fromFile(java.io.File f){return new Uri(f);} public String toString(){return file.toURI().toString();} }''',
        }
        with tempfile.TemporaryDirectory() as folder:
            sources = []
            for name, content in stubs.items():
                file = Path(folder) / name
                file.parent.mkdir(parents=True, exist_ok=True)
                file.write_text(content)
                sources.append(str(file))
            source = ROOT / 'app/src/com/jeremykenedy/firetv/ui/NativeTileArtwork.java'
            subprocess.run(['javac', '-d', folder, *sources, str(source),
                            str(ROOT / 'tests/NativeTileArtworkTest.java')], check=True)
            subprocess.run(['java', '-Dasset.root=' + str(ROOT / 'app'), '-cp', folder,
                            'com.jeremykenedy.firetv.ui.NativeTileArtworkTest', folder], check=True)
