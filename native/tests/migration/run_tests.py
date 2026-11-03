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
        'device/protection/LegacyDeviceProtection.java', 'holidays/HolidayScenes.java', 'folders/FolderOptions.java', 'appgrid/AppGridOptions.java', 'appgrid/HomeTileOrder.java', 'focus/FocusOptions.java', 'layout/LayoutOptions.java',
        'widgetgroups/WidgetGroups.java', 'widgetgroups/WidgetGroupMigration.java', 'widgetgroups/WidgetGroupPresentationPlan.java', 'widgetgroups/WeatherGroupOptions.java', 'widget/weather/WeatherOptions.java', 'layout/LayoutItem.java', 'cards/CardOptions.java', 'appearance/AppearanceOptions.java', 'appearance/LegacyGradients.java', 'appearance/PaletteRecipes.java', 'inputs/InputEntry.java', 'inputs/InputOptions.java')]
    sources += models
    subprocess.run(['javac', '-Xlint:all', '-Werror', '-d', str(target),
                    *map(str, sources), *map(str, Path(__file__).parent.glob('*Test.java'))], check=True)
    subprocess.run(['java', '-cp', str(target),
                    'com.jeremykenedy.firetv.ui.nativeapp.migration.MigrationTest'], check=True)
    subprocess.run(['java', '-cp', str(target),
                    'com.jeremykenedy.firetv.ui.nativeapp.migration.PresentationMappingTest'], check=True)
    subprocess.run(['java', '-cp', str(target),
                    'com.jeremykenedy.firetv.ui.nativeapp.migration.LegacyFolderTest'], check=True)
    subprocess.run(['java', '-cp', str(target),
                    'com.jeremykenedy.firetv.ui.nativeapp.migration.IdentityInventoryTest'], check=True)
    subprocess.run(['javac', '-source', '8', '-target', '8', '-Xlint:-options',
                    '-bootclasspath', str(SDK / 'platforms/android-36/android.jar'),
                    '-d', str(target / 'android'), *map(str, SOURCE.glob('*.java')), *map(str, models),
                    *[str(ROOT / ('src/com/jeremykenedy/firetv/ui/nativeapp/providers/' + name))
                      for name in ('RecommendationKeys.java', 'OwnedPlexIdentity.java', 'LegacyRecommendationIdentities.java', 'AndroidRecommendationIdentityInventory.java', 'TvRecommendations.java')],
                    *[str(ROOT / ('src/com/jeremykenedy/firetv/ui/nativeapp/locks/' + name))
                      for name in ('LockStore.java', 'LockState.java', 'LockResult.java')]], check=True)
print('Opt-in Android migration store compiled against API 36. No application data accessed.')
