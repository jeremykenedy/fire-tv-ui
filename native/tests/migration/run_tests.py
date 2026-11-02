#!/usr/bin/env python3
"""Run sanitized host migration regressions and compile the opt-in Android adapter."""
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'src/com/jeremykenedy/firetv/ui/nativeapp/migration'
SDK = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
           or Path.home() / 'Library/Android/sdk')
with tempfile.TemporaryDirectory(prefix='fire-tv-migration-') as directory:
    target = Path(directory)
    sources = sorted(path for path in SOURCE.glob('*.java') if not path.name.startswith('Android'))
    models = [ROOT / ('src/com/jeremykenedy/firetv/ui/nativeapp/' + name) for name in (
        'appgrid/AppGridOptions.java', 'focus/FocusOptions.java', 'layout/LayoutOptions.java',
        'layout/LayoutItem.java', 'cards/CardOptions.java', 'appearance/AppearanceOptions.java', 'appearance/PaletteRecipes.java')]
    sources += models
    subprocess.run(['javac', '-Xlint:all', '-Werror', '-d', str(target),
                    *map(str, sources), *map(str, Path(__file__).parent.glob('*Test.java'))], check=True)
    subprocess.run(['java', '-cp', str(target),
                    'com.jeremykenedy.firetv.ui.nativeapp.migration.MigrationTest'], check=True)
    subprocess.run(['java', '-cp', str(target),
                    'com.jeremykenedy.firetv.ui.nativeapp.migration.PresentationMappingTest'], check=True)
    subprocess.run(['javac', '-source', '8', '-target', '8', '-Xlint:-options',
                    '-bootclasspath', str(SDK / 'platforms/android-36/android.jar'),
                    '-d', str(target / 'android'), *map(str, SOURCE.glob('*.java')), *map(str, models)], check=True)
print('Opt-in Android migration store compiled against API 36. No application data accessed.')
