#!/usr/bin/env python3
"""Host-test sanitized setup state, faults and media; compile the native manager UI."""
from pathlib import Path
import re
import os
import subprocess
import sys
import tempfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
NATIVE = ROOT / 'native'
SOURCE = NATIVE / 'src/com/jeremykenedy/firetv/ui/nativeapp'
sys.path.insert(0, str(ROOT / 'tests'))
from java_runtime import json_runtime
from android_stubs import SOURCES

sources = dict(SOURCES)
sources['com/jeremykenedy/firetv/ui/nativeapp/migration/AndroidPresentationInventory.java'] = 'package com.jeremykenedy.firetv.ui.nativeapp.migration;\npublic final class AndroidPresentationInventory {\n public static PresentationMappingContext read(android.content.Context context, PreferenceSnapshot legacy) { return PresentationMappingContext.empty(); }\n}'

# Resource constants are compile-only; tests exercise the real reviewed catalog entries.
catalog = (SOURCE / 'icons/IconCatalog.java').read_text()
resources = sorted(set(re.findall(r'R.drawable.(\w+)', catalog)))
sources['com/jeremykenedy/firetv/ui/preview/R.java'] = 'package com.jeremykenedy.firetv.ui.preview; public final class R {public static final class drawable {' + ''.join(f'public static final int {name}={i+1};' for i, name in enumerate(resources)) + '}}'
with tempfile.TemporaryDirectory(prefix='fire-tv-setups-') as temporary:
    target = Path(temporary)
    stubs = []
    for name, value in sources.items():
        path = target / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(value)
        stubs.append(path)
    dependencies = [SOURCE / 'backup/PortableBackup.java', SOURCE / 'accessibility/AccessibilityPreferences.java', SOURCE / 'holidays/HolidayScenes.java', SOURCE / 'migration/PreferenceSnapshot.java', SOURCE / 'migration/PrivateMediaReference.java',
                    SOURCE / 'inputs/InputEntry.java', SOURCE / 'inputs/InputOptions.java', SOURCE / 'migration/LegacyPresentationMapper.java',
                    SOURCE / 'migration/LegacyJson.java', SOURCE / 'migration/LegacyFolderMigration.java', SOURCE / 'migration/LegacyAppearanceMapper.java', SOURCE / 'migration/LauncherMigration.java', SOURCE / 'migration/PresentationMappingContext.java', SOURCE / 'appgrid/AppGridOptions.java', SOURCE / 'appgrid/RetainedHomeChoices.java', SOURCE / 'appgrid/HomeTileOrder.java',
                    SOURCE / 'focus/FocusOptions.java', SOURCE / 'cards/CardOptions.java',
                    SOURCE / 'layout/LayoutItem.java', SOURCE / 'layout/LayoutOptions.java',
                    SOURCE / 'folders/FolderOptions.java', SOURCE / 'folders/FolderPreferences.java',
                    SOURCE / 'icons/IconCatalog.java', SOURCE / 'appearance/LegacyGradients.java', SOURCE / 'appearance/AppearanceOptions.java', SOURCE / 'appearance/AppearancePreferences.java', SOURCE / 'appearance/PaletteRecipes.java', SOURCE / 'widget/worldclock/WorldClocks.java',
                    SOURCE / 'widget/breathing/BreathingSession.java', SOURCE / 'widgetgroups/WidgetGroupPresentationPlan.java', SOURCE / 'migration/PresentationSnapshot.java', SOURCE / 'widgetgroups/WidgetGroups.java', SOURCE / 'widgetgroups/WidgetGroupMigration.java', SOURCE / 'widgetgroups/WidgetGroupsStore.java',
                    SOURCE / 'widgetgroups/WeatherGroupOptions.java', SOURCE / 'widget/weather/WeatherOptions.java']
    managers = sorted(path for path in (SOURCE / 'setups').glob('*.java') if path.name != 'SavedSetupsActivity.java')
    jar = json_runtime()
    subprocess.run(['javac', '-Xlint:unchecked', '-cp', str(jar), '-d', str(target),
                    *map(str, stubs + dependencies + managers), str(HERE / 'SetupTest.java')], check=True)
    subprocess.run(['java', '-cp', str(target) + os.pathsep + str(jar),
                    'com.jeremykenedy.firetv.ui.nativeapp.setups.SetupTest', str(target)], check=True)
    android = Path.home() / 'Library/Android/sdk/platforms/android-36/android.jar'
    subprocess.run(['javac', '-source', '8', '-target', '8', '-Xlint:-options', '-bootclasspath', str(android), '-classpath', str(NATIVE / '.build/classes'),
                    '-d', str(target / 'android'), *map(str, dependencies + [SOURCE / 'SourceStartup.java', SOURCE / 'migration/AndroidPresentationInventory.java', SOURCE / 'migration/IdentityInventory.java', SOURCE / 'migration/AndroidPrivateMedia.java'] + [SOURCE / ('providers/' + name) for name in ('RecommendationKeys.java', 'OwnedPlexIdentity.java', 'LegacyRecommendationIdentities.java', 'AndroidRecommendationIdentityInventory.java', 'TvRecommendations.java')] + list((SOURCE / 'setups').glob('*.java'))),
                    str(target / 'com/jeremykenedy/firetv/ui/preview/R.java'), str(HERE / 'SetupInstrumentation.java')], check=True)
print('Saved setup host checks and isolated Android API 36 manager compilation passed.')
