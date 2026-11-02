#!/usr/bin/env python3
"""Compile and verify the pure grouped-widget contract without app data."""
from pathlib import Path
import os
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
source = root / 'src/com/jeremykenedy/firetv/ui/nativeapp'
with tempfile.TemporaryDirectory(prefix='fire-tv-widgetgroups-') as folder:
    subprocess.run(['javac', '-Xlint:all', '-Werror', '-d', folder,
                    str(source / 'migration/LegacyJson.java'),
                    str(source / 'widgetgroups/WidgetGroups.java'),
                    str(source / 'widgetgroups/WidgetGroupMigration.java'),
                    str(source / 'widgetgroups/LegacyYearProgressModel.java'),
                    str(source / 'widget/weather/WeatherOptions.java'), str(source / 'widgetgroups/WeatherGroupOptions.java'),
                    str(Path(__file__).with_name('WidgetGroupsTest.java'))], check=True)
    subprocess.run(['java', '-cp', folder, 'com.jeremykenedy.firetv.ui.nativeapp.widgetgroups.WidgetGroupsTest'], check=True)
    journal_sources = sorted(path for path in (source / 'migration').glob('*.java') if not path.name.startswith('Android'))
    journal_sources += [source / name for name in (
        'device/protection/LegacyDeviceProtection.java', 'holidays/HolidayScenes.java', 'folders/FolderOptions.java', 'appgrid/AppGridOptions.java',
        'appgrid/HomeTileOrder.java', 'focus/FocusOptions.java', 'layout/LayoutOptions.java',
        'layout/LayoutItem.java', 'cards/CardOptions.java', 'appearance/AppearanceOptions.java',
        'appearance/PaletteRecipes.java', 'inputs/InputEntry.java', 'inputs/InputOptions.java',
        'widgetgroups/WidgetGroupMigration.java', 'widgetgroups/WidgetGroups.java')]
    # The appearance model depends on the observed gradient registry.
    journal_sources.append(source / 'appearance/LegacyGradients.java')
    subprocess.run(['javac', '-Xlint:all', '-Werror', '-d', folder, *map(str, journal_sources),
                    str(Path(__file__).with_name('WidgetGroupJournalTest.java'))], check=True)
    subprocess.run(['java', '-cp', folder, 'com.jeremykenedy.firetv.ui.nativeapp.widgetgroups.WidgetGroupJournalTest'], check=True)
    stub = Path(folder) / 'com/jeremykenedy/firetv/ui/nativeapp/layout/HomeSlots.java'
    stub.parent.mkdir(parents=True, exist_ok=True)
    stub.write_text('package com.jeremykenedy.firetv.ui.nativeapp.layout; public final class HomeSlots { public static final LayoutItem CLOCK = new LayoutItem("clock", LayoutItem.Kind.WIDGET); }')
    sdk = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or Path.home() / 'Library/Android/sdk')
    files = [source / name for name in (
        'migration/LegacyJson.java', 'layout/LayoutItem.java', 'layout/WidgetProvider.java',
        'layout/ClockWidgetProvider.java', 'widget/ClockWidgetView.java',
        'widget/yearprogress/YearProgress.java', 'widget/yearprogress/YearProgressView.java',
        'widget/yearprogress/YearProgressWidgetProvider.java',
        'widgetgroups/WidgetGroups.java', 'widgetgroups/WidgetGroupMigration.java',
        'widgetgroups/WidgetGroupsStore.java', 'widgetgroups/WidgetGroupProviders.java', 'widgetgroups/WidgetGroupsView.java',
        'widgetgroups/WidgetGroupsEditor.java', 'widgetgroups/LegacyYearProgressModel.java',
        'widgetgroups/LegacyYearProgressView.java', 'widgetgroups/LegacyYearProgressProvider.java')]
    subprocess.run(['javac', '-source', '8', '-target', '8', '-Xlint:-options',
                    '-bootclasspath', str(sdk / 'platforms/android-36/android.jar'),
                    '-sourcepath', str(root / 'src') + ':' + str(root / '.build/gen'),
                    '-d', str(Path(folder) / 'android'), str(stub), *map(str, files)], check=True)
    print('Grouped widget views and store compiled against Android API 36.')
