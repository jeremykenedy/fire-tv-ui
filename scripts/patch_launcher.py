#!/usr/bin/env python3
"""Apply checked changes to the pinned launcher before packaging."""

import json
from pathlib import Path
import re
import shutil
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
CONFIG = json.loads((ROOT / "build.json").read_text())
ANDROID = "{http://schemas.android.com/apk/res/android}"
ET.register_namespace("android", "http://schemas.android.com/apk/res/android")


def replace_once(text, old, new):
    if text.count(old) != 1:
        raise ValueError(f"Expected one patch anchor, found {text.count(old)}: {old[:100]}")
    return text.replace(old, new, 1)


def java_hash(value):
    result = 0
    for character in value:
        result = (31 * result + ord(character)) & 0xffffffff
    return result if result < 0x80000000 else result - 0x100000000


def classes(decoded):
    result = {}
    for path in (decoded / "smali").rglob("*.smali"):
        text = path.read_text()
        name = re.search(r"^\.class[^\n]*? (L[^;]+;)$", text, re.M).group(1)
        if name in result:
            raise ValueError(f"Duplicate class: {name}")
        result[name] = path
    return result


def replace_method(text, signature, replacement):
    pattern = re.compile(r"^" + re.escape(signature) + r"\n.*?^\.end method", re.M | re.S)
    text, count = pattern.subn(lambda _: replacement.rstrip(), text)
    if count != 1:
        raise ValueError(f"Expected one method: {signature}")
    return text


def patch(decoded):
    class_paths = classes(decoded)
    main = class_paths["Lcom/overdevs/at4k/MainActivity;"]
    text = main.read_text()
    text = replace_once(text,
        '    const-string v0, "event"\n\n'
        '    invoke-static {p1, v0}, Lo4/j;->e(Ljava/lang/Object;Ljava/lang/String;)V',
        '    const-string v0, "event"\n\n'
        '    invoke-static {p1, v0}, Lo4/j;->e(Ljava/lang/Object;Ljava/lang/String;)V\n\n'
        '    invoke-virtual {p1}, Landroid/view/KeyEvent;->getKeyCode()I\n'
        '    move-result v1\n'
        '    const/4 v2, 0x4\n'
        '    if-eq v1, v2, :settings_dismiss_key\n'
        '    const/16 v2, 0x52\n'
        '    if-ne v1, v2, :settings_key_done\n'
        '    :settings_dismiss_key\n'
        '    iget-object v0, p0, Lcom/overdevs/at4k/MainActivity;->A:LQ3/T3;\n'
        '    if-eqz v0, :settings_key_done\n'
        '    iget-object v2, v0, LQ3/T3;->m0:LN/g0;\n'
        '    invoke-virtual {v2}, LN/g0;->getValue()Ljava/lang/Object;\n'
        '    move-result-object v2\n'
        '    check-cast v2, Ljava/lang/Boolean;\n'
        '    invoke-virtual {v2}, Ljava/lang/Boolean;->booleanValue()Z\n'
        '    move-result v2\n'
        '    if-eqz v2, :settings_key_done\n'
        '    invoke-virtual {p1}, Landroid/view/KeyEvent;->getAction()I\n'
        '    move-result v2\n'
        '    const/4 v1, 0x1\n'
        '    if-ne v2, v1, :settings_key_consumed\n'
        '    invoke-virtual {v0}, LQ3/T3;->u()Ljava/lang/String;\n'
        '    move-result-object v2\n'
        '    if-nez v2, :settings_submenu_close\n'
        '    iget-object v2, v0, LQ3/T3;->m0:LN/g0;\n'
        '    sget-object v1, Ljava/lang/Boolean;->FALSE:Ljava/lang/Boolean;\n'
        '    invoke-virtual {v2, v1}, LN/g0;->setValue(Ljava/lang/Object;)V\n'
        '    :settings_submenu_close\n'
        '    const/4 v1, 0x0\n'
        '    invoke-virtual {v0, v1}, LQ3/T3;->T(Ljava/lang/String;)V\n'
        '    :settings_key_consumed\n'
        '    const/4 v1, 0x1\n'
        '    return v1\n'
        '    :settings_key_done')
    text = replace_once(text,
        '    const-string v5, "is_premium"\n\n'
        '    invoke-interface {p1, v5, v3}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z\n\n'
        '    move-result p1', '    const/4 p1, 0x1')
    text = replace_once(text,
        '    invoke-super {p0, p1}, Lc/k;->onCreate(Landroid/os/Bundle;)V',
        '    invoke-super {p0, p1}, Lc/k;->onCreate(Landroid/os/Bundle;)V\n\n'
        '    invoke-static {p0}, Lcom/jeremykenedy/firetv/ui/DeviceSettings;->initialize(Landroid/content/Context;)V')
    home_signature = '.method public final onNewIntent(Landroid/content/Intent;)V'
    home_pattern = re.compile(r'^' + re.escape(home_signature) + r'\n.*?^\.end method', re.M | re.S)
    home = home_pattern.search(text).group(0)
    # Ordinary Home delivery keeps the existing scene. Only a focused popup needs the legacy reset.
    home = replace_once(home, '    :cond_2\n    return-void',
        '    invoke-static {p0}, Lcom/jeremykenedy/firetv/ui/HomePresentation;->closeTransientWindow(Landroid/app/Activity;)V\n\n'
        '    :cond_2\n    return-void')
    text = replace_method(text, home_signature, home)
    counter_start = text.index('    const-string v5, "app_launch_count"')
    counter_end = text.index('    new-instance v4, Lcom/overdevs/at4k/PackageChangedReceiver;', counter_start)
    text = text[:counter_start] + text[counter_end:]
    text += '\n.method public startActivity(Landroid/content/Intent;)V\n    .locals 0\n'
    text += '    invoke-static {p0, p1}, Lcom/jeremykenedy/firetv/ui/FactoryHome;->beforeLaunch(Landroid/content/Context;Landroid/content/Intent;)V\n'
    text += '    invoke-super {p0, p1}, Landroid/app/Activity;->startActivity(Landroid/content/Intent;)V\n    return-void\n.end method\n'
    resume_signature = '.method public final onResume()V'
    resume = re.search(r'^' + re.escape(resume_signature) + r'\n.*?^\.end method', text, re.M | re.S).group(0)
    resume = replace_once(resume, '    .locals 2', '    .locals 3')
    resume = replace_once(resume,
        '    invoke-super {p0}, Landroid/app/Activity;->onResume()V',
        '    invoke-super {p0}, Landroid/app/Activity;->onResume()V\n'
        '    invoke-static {p0}, Lcom/jeremykenedy/firetv/ui/HomePresentation;->resumed(Landroid/app/Activity;)V\n'
        '    iget-object v0, p0, Lcom/overdevs/at4k/MainActivity;->A:LQ3/T3;\n'
        '    if-eqz v0, :resume_idle_reset_done\n'
        '    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J\n'
        '    move-result-wide v1\n'
        '    invoke-virtual {v0, v1, v2}, LQ3/T3;->S(J)V\n'
        '    invoke-virtual {v0}, LQ3/T3;->refreshFeaturedRotation()V\n'
        '    invoke-virtual {v0}, LQ3/T3;->refreshLauncherLayout()V\n'
        '    :resume_idle_reset_done\n'
        '    invoke-static {p0}, Lcom/jeremykenedy/firetv/ui/FeaturedGroupBridge;->start(Landroid/content/Context;)V\n'
        '    const/4 v1, 0x1\n'
        '    invoke-static {v1}, Lcom/jeremykenedy/firetv/ui/MenuBackgroundView;->resumed(Z)V')
    text = replace_method(text, resume_signature, resume)
    text += '\n.method public onPause()V\n    .locals 1\n'
    text += '    invoke-static {p0}, Lcom/jeremykenedy/firetv/ui/HomePresentation;->paused(Landroid/app/Activity;)V\n'
    text += '    invoke-static {}, Lcom/jeremykenedy/firetv/ui/FeaturedCardClock;->stop()V\n'
    text += '    const/4 v0, 0x0\n    invoke-static {v0}, Lcom/jeremykenedy/firetv/ui/MenuBackgroundView;->resumed(Z)V\n'
    text += '    invoke-super {p0}, Landroid/app/Activity;->onPause()V\n    return-void\n.end method\n'
    main.write_text(text)

    hra = class_paths["Lcom/overdevs/at4k/Hra;"]
    text = hra.read_text()
    text = replace_once(text,
        '.method public final onAccessibilityEvent(Landroid/view/accessibility/AccessibilityEvent;)V\n    .locals 6',
        '.method public final onAccessibilityEvent(Landroid/view/accessibility/AccessibilityEvent;)V\n    .locals 6\n\n'
        '    invoke-static {p1}, Lcom/jeremykenedy/firetv/ui/FactoryHome;->bypass(Landroid/view/accessibility/AccessibilityEvent;)Z\n'
        '    move-result v0\n'
        '    if-eqz v0, :factory_home_redirect_normal\n'
        '    return-void\n'
        '    :factory_home_redirect_normal')
    text = replace_method(text, '.method public final onKeyEvent(Landroid/view/KeyEvent;)Z',
        '.method public final onKeyEvent(Landroid/view/KeyEvent;)Z\n    .locals 2\n\n'
        '    invoke-static {p1}, Lcom/jeremykenedy/firetv/ui/FactoryHome;->homeKey(Landroid/view/KeyEvent;)V\n'
        '    iget-boolean v0, p0, Lcom/overdevs/at4k/Hra;->h:Z\n'
        '    iget-object v1, p0, Lcom/overdevs/at4k/Hra;->j:Landroid/content/Intent;\n'
        '    invoke-static {p0, p1, v0, v1}, Lcom/jeremykenedy/firetv/ui/HomeKeyDispatch;->dispatch(Landroid/content/Context;Landroid/view/KeyEvent;ZLandroid/content/Intent;)Z\n'
        '    move-result v0\n'
        '    return v0\n.end method')
    hra.write_text(text)
    apps = class_paths["LQ3/B7;"]
    text = apps.read_text()
    text = replace_once(text,
        '    move-result-object v4\n\n    new-instance v7, Ljava/util/HashSet;',
        '    move-result-object v4\n\n'
        '    invoke-static {v4, v5}, Lcom/jeremykenedy/firetv/ui/FactoryHome;->addTile(Ljava/util/List;Landroid/content/pm/PackageManager;)V\n'
        '    invoke-static {v4, v5}, Lcom/jeremykenedy/firetv/ui/NativeLiveTv;->addTile(Ljava/util/List;Landroid/content/pm/PackageManager;)V\n\n'
        '    new-instance v7, Ljava/util/HashSet;')
    apps.write_text(text)
    artwork = class_paths["LQ3/A7;"]
    text = artwork.read_text()
    text = replace_once(text,
        '    :goto_6\n    const-string v0, "_"',
        '    :goto_6\n'
        '    invoke-static {v4, v11, v7}, Lcom/jeremykenedy/firetv/ui/NativeTileArtwork;->uri(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;\n'
        '    move-result-object v7\n\n'
        '    const-string v0, "_"')
    artwork.write_text(text)
    launch = class_paths["LQ3/O4;"]
    text = launch.read_text()
    text = replace_once(text,
        '    :cond_0\n    invoke-virtual {v1}, Landroid/content/Context;->getPackageManager()Landroid/content/pm/PackageManager;',
        '    :cond_0\n'
        '    invoke-static {v1, p1}, Lcom/jeremykenedy/firetv/ui/NativeLiveTv;->open(Landroid/content/Context;Ljava/lang/String;)Z\n'
        '    move-result v0\n'
        '    if-nez v0, :goto_0\n'
        '    invoke-static {v1, p1}, Lcom/jeremykenedy/firetv/ui/FactoryHome;->open(Landroid/content/Context;Ljava/lang/String;)Z\n'
        '    move-result v0\n'
        '    if-nez v0, :goto_0\n'
        '    invoke-virtual {v1}, Landroid/content/Context;->getPackageManager()Landroid/content/pm/PackageManager;')
    launch.write_text(text)
    context_menu = class_paths["LQ3/U0;"]
    text = context_menu.read_text()
    text = replace_once(text,
        '    iget-object v3, v2, LQ3/q;->b:Ljava/lang/String;\n\n'
        '    invoke-virtual {v1, v3}, Landroid/content/pm/PackageManager;->getLeanbackLaunchIntentForPackage(Ljava/lang/String;)Landroid/content/Intent;',
        '    iget-object v3, v2, LQ3/q;->b:Ljava/lang/String;\n\n'
        '    invoke-static {v0, v3}, Lcom/jeremykenedy/firetv/ui/NativeLiveTv;->open(Landroid/content/Context;Ljava/lang/String;)Z\n'
        '    move-result v4\n'
        '    if-nez v4, :goto_f\n'
        '    invoke-static {v0, v3}, Lcom/jeremykenedy/firetv/ui/FactoryHome;->open(Landroid/content/Context;Ljava/lang/String;)Z\n'
        '    move-result v4\n'
        '    if-nez v4, :goto_f\n'
        '    invoke-virtual {v1, v3}, Landroid/content/pm/PackageManager;->getLeanbackLaunchIntentForPackage(Ljava/lang/String;)Landroid/content/Intent;')
    context_menu.write_text(text)
    intents = class_paths["LQ3/ya;"]
    text = intents.read_text()
    text = replace_once(text,
        '.method public static final G(Landroid/content/pm/PackageManager;Ljava/lang/String;)Landroid/content/Intent;\n    .locals 1',
        '.method public static final G(Landroid/content/pm/PackageManager;Ljava/lang/String;)Landroid/content/Intent;\n    .locals 1\n\n'
        '    invoke-static {p0, p1}, Lcom/jeremykenedy/firetv/ui/NativeLiveTv;->launchIntent(Landroid/content/pm/PackageManager;Ljava/lang/String;)Landroid/content/Intent;\n'
        '    move-result-object v0\n'
        '    if-eqz v0, :native_factory_intent\n'
        '    return-object v0\n'
        '    :native_factory_intent\n'
        '    invoke-static {p0, p1}, Lcom/jeremykenedy/firetv/ui/FactoryHome;->launchIntent(Landroid/content/pm/PackageManager;Ljava/lang/String;)Landroid/content/Intent;\n'
        '    move-result-object v0\n'
        '    if-eqz v0, :ordinary_launch_intent\n'
        '    return-object v0\n'
        '    :ordinary_launch_intent')
    intents.write_text(text)
    poster = class_paths["LQ3/a0;"]
    text = poster.read_text()
    text = replace_once(text,
        '    iget-object v0, v0, LQ3/q;->b:Ljava/lang/String;\n\n'
        '    invoke-virtual {v2}, Landroid/content/Context;->getPackageManager()Landroid/content/pm/PackageManager;',
        '    iget-object v0, v0, LQ3/q;->b:Ljava/lang/String;\n\n'
        '    invoke-static {v2, v0}, Lcom/jeremykenedy/firetv/ui/NativeLiveTv;->open(Landroid/content/Context;Ljava/lang/String;)Z\n'
        '    move-result v6\n'
        '    if-nez v6, :goto_4\n'
        '    invoke-static {v2, v0}, Lcom/jeremykenedy/firetv/ui/FactoryHome;->open(Landroid/content/Context;Ljava/lang/String;)Z\n'
        '    move-result v6\n'
        '    if-nez v6, :goto_4\n'
        '    invoke-virtual {v2}, Landroid/content/Context;->getPackageManager()Landroid/content/pm/PackageManager;')
    poster.write_text(text)
    animated = class_paths["LA3/a;"]
    text = animated.read_text()
    signature = '.method public static final j(Ln4/a;LQ3/q;Ln4/c;Landroid/content/Context;[Lf0/d;LQ3/t;Landroid/view/View;)V'
    pattern = re.compile(r'^' + re.escape(signature) + r'\n.*?^\.end method', re.M | re.S)
    method = pattern.search(text).group(0)
    method = replace_once(method,
        '    invoke-virtual {p1, p0}, Landroid/content/pm/PackageManager;->getLeanbackLaunchIntentForPackage(Ljava/lang/String;)Landroid/content/Intent;',
        '    invoke-static {p1, p0}, Lcom/jeremykenedy/firetv/ui/NativeLiveTv;->launchIntent(Landroid/content/pm/PackageManager;Ljava/lang/String;)Landroid/content/Intent;\n'
        '    move-result-object p2\n'
        '    if-nez p2, :cond_3\n'
        '    invoke-static {p1, p0}, Lcom/jeremykenedy/firetv/ui/FactoryHome;->launchIntent(Landroid/content/pm/PackageManager;Ljava/lang/String;)Landroid/content/Intent;\n'
        '    move-result-object p2\n'
        '    if-nez p2, :cond_3\n'
        '    invoke-virtual {p1, p0}, Landroid/content/pm/PackageManager;->getLeanbackLaunchIntentForPackage(Ljava/lang/String;)Landroid/content/Intent;')
    for invocation in (
            '    invoke-virtual {p3, p2, p4}, Landroid/content/Context;->startActivity(Landroid/content/Intent;Landroid/os/Bundle;)V',
            '    invoke-virtual {p3, p2}, Landroid/content/Context;->startActivity(Landroid/content/Intent;)V'):
        method = replace_once(method, invocation,
            '    invoke-static {p3, p2}, Lcom/jeremykenedy/firetv/ui/FactoryHome;->beforeLaunch(Landroid/content/Context;Landroid/content/Intent;)V\n' + invocation)
    animated.write_text(replace_method(text, signature, method))

    preferences = class_paths['LQ3/T3;']
    text = preferences.read_text()
    prompt_start = text.index('    iget-object v2, v1, LQ3/T3;->a:Landroid/content/SharedPreferences;\n\n'
                              '    const-string v6, "app_launch_count"')
    prompt_end = text.index('    invoke-static {v4}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;', prompt_start)
    text = text[:prompt_start] + '    const/4 v4, 0x0\n\n' + text[prompt_end:]
    preferences.write_text(text)

    state = class_paths["LQ3/r8;"]
    text = state.read_text()
    text = replace_once(text,
        '    sget-object v0, Ljava/lang/Boolean;->FALSE:Ljava/lang/Boolean;',
        '    sget-object v0, Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;')
    text = replace_once(text,
        '    sput-object v3, LQ3/r8;->f:LA4/u;\n\n',
        '    sput-object v3, LQ3/r8;->f:LA4/u;\n\n'
        '    sget-object v0, Ljava/lang/Boolean;->FALSE:Ljava/lang/Boolean;\n\n')
    text = replace_method(text, '.method public final a(ILN/p;)V',
        '.method public final a(ILN/p;)V\n    .locals 0\n    return-void\n.end method')
    state.write_text(text)

    persist = class_paths["LQ3/W6;"]
    persist.write_text(replace_once(persist.read_text(),
        '    const-string v0, "is_premium"\n\n    iget-boolean v1, p0, LQ3/W6;->l:Z',
        '    const-string v0, "is_premium"\n\n    const/4 v1, 0x1'))

    idle = class_paths["LQ3/T6;"]
    idle.write_text(replace_method(idle.read_text(),
        ".method public final k(Ljava/lang/Object;)Ljava/lang/Object;",
        """.method public final k(Ljava/lang/Object;)Ljava/lang/Object;
    .locals 7
    sget-object v0, Lg4/a;->g:Lg4/a;
    iget v1, p0, LQ3/T6;->k:I
    invoke-static {p1}, Lq4/a;->l0(Ljava/lang/Object;)V
    if-nez v1, :idle_restore_focus
    iget-object v1, p0, LQ3/T6;->l:LQ3/T3;
    invoke-virtual {v1}, LQ3/T3;->M()Z
    move-result v2
    if-eqz v2, :idle_focus_delay
    iget-object v2, p0, LQ3/T6;->m:Landroid/content/Context;
    iget-object v3, v1, LQ3/T3;->l1:LN/g0;
    invoke-virtual {v3}, LN/g0;->getValue()Ljava/lang/Object;
    move-result-object v3
    check-cast v3, Ljava/lang/Number;
    invoke-virtual {v3}, Ljava/lang/Number;->longValue()J
    move-result-wide v3
    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J
    move-result-wide v5
    sub-long v3, v5, v3
    invoke-static {v2, v3, v4}, Lcom/jeremykenedy/firetv/ui/IdleScreensaver;->start(Landroid/content/Context;J)V
    const/4 v2, 0x0
    invoke-virtual {v1, v2}, LQ3/T3;->V(Z)V
    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J
    move-result-wide v2
    invoke-virtual {v1, v2, v3}, LQ3/T3;->S(J)V
    goto :idle_done
    :idle_focus_delay
    const/4 v1, 0x1
    iput v1, p0, LQ3/T6;->k:I
    const-wide/16 v1, 0x64
    invoke-static {v1, v2, p0}, Lx4/v;->e(JLf4/c;)Ljava/lang/Object;
    move-result-object p1
    if-ne p1, v0, :idle_restore_focus
    return-object v0
    :idle_restore_focus
    iget-object p1, p0, LQ3/T6;->o:Ljava/util/List;
    const/4 v0, 0x0
    invoke-static {v0, p1}, Lc4/r;->D0(ILjava/util/List;)Ljava/lang/Object;
    move-result-object p1
    check-cast p1, Le0/p;
    if-eqz p1, :idle_done
    invoke-static {p1}, LQ3/M7;->T(Le0/p;)V
    :idle_done
    sget-object p1, Lb4/n;->a:Lb4/n;
    return-object p1
.end method"""))

    idle_clock = class_paths["LQ3/S6;"]
    idle_threshold = replace_once(idle_clock.read_text(),
        '    invoke-virtual {p1}, LQ3/T3;->y()Z',
        '    invoke-static {}, Lcom/jeremykenedy/firetv/ui/DeviceSettings;->launcherIdleEnabled()Z')
    idle_clock.write_text(replace_once(idle_threshold,
        '    const-wide/32 v5, 0x15f90',
        '    invoke-static {}, Lcom/jeremykenedy/firetv/ui/DeviceSettings;->launcherIdleDelay()J\n'
        '    move-result-wide v5'))

    featured = class_paths["LQ3/S8;"]
    text = featured.read_text()
    signature = '.method public static a(LQ3/S8;)LQ3/S8;'
    pattern = re.compile(r'^' + re.escape(signature) + r'\n.*?^\.end method', re.M | re.S)
    copy = pattern.search(text).group(0)
    copy = replace_once(copy, '    .locals 13',
        '    .locals 13\n\n    iget-object v2, p0, LQ3/S8;->b:Ljava/lang/String;\n'
        '    invoke-static {v2}, Lcom/jeremykenedy/firetv/ui/FeaturedCardMenu;->label(Ljava/lang/String;)Ljava/lang/String;\n'
        '    move-result-object v2')
    copy = replace_once(copy, '    const-string v2, "Watch Next"\n\n', '')
    featured.write_text(replace_method(text, signature, copy))

    mapper = class_paths["Landroid/support/v4/media/session/b;"]
    mapper.write_text(replace_method(mapper.read_text(),
        '.method public static M(Ljava/util/List;Ljava/util/List;)Ljava/util/ArrayList;',
        '.method public static M(Ljava/util/List;Ljava/util/List;)Ljava/util/ArrayList;\n'
        '    .locals 1\n'
        '    invoke-static {p0, p1}, Lcom/jeremykenedy/firetv/ui/FeaturedProgramMapper;->map(Ljava/util/List;Ljava/util/List;)Ljava/util/ArrayList;\n'
        '    move-result-object v0\n    return-object v0\n.end method'))
    card_slot = class_paths["LQ3/r3;"]
    card_slot.write_text(replace_once(card_slot.read_text(),
        '    check-cast v4, LQ3/S8;',
        '    check-cast v4, LQ3/S8;\n'
        '    invoke-static {v4}, Lcom/jeremykenedy/firetv/ui/FeaturedGroupBridge;->current(LQ3/S8;)LQ3/S8;\n'
        '    move-result-object v4'))

    for descriptor, cursor, finish, temporary in (
            ("LQ3/F8;", "v7", ":cond_c", "v8"),
            ("LQ3/H8;", "v8", ":cond_10", "v14")):
        loader = class_paths[descriptor]
        contents = loader.read_text()
        context = "v0" if descriptor == "LQ3/F8;" else "v13"
        watched = "0x0" if descriptor == "LQ3/F8;" else "0x1"
        hook = ("    if-eqz v0, " + finish + "\n\n")
        contents = replace_once(contents, hook, hook
            + "    iget-object " + context + ", v1, " + descriptor + "->l:Landroid/content/Context;\n"
            + "    const/4 " + temporary + ", " + watched + "\n"
            + "    invoke-static {" + context + ", " + cursor + ", " + temporary + "}, Lcom/jeremykenedy/firetv/ui/HiddenCards;->visible(Landroid/content/Context;Landroid/database/Cursor;Z)Z\n"
            + "    move-result " + context + "\n"
            + "    if-eqz " + context + ", :goto_0\n\n")
        if descriptor == "LQ3/F8;":
            contents = replace_once(contents, '->query(Landroid/net/Uri;[Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;Ljava/lang/String;)Landroid/database/Cursor;\n\n    move-result-object v7\n',
                '->query(Landroid/net/Uri;[Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;Ljava/lang/String;)Landroid/database/Cursor;\n\n    move-result-object v7\n'
                '    iget-object v0, v1, LQ3/F8;->l:Landroid/content/Context;\n'
                '    invoke-static {v0, v7}, Lcom/jeremykenedy/firetv/ui/CardOrder;->prepare(Landroid/content/Context;Landroid/database/Cursor;)Landroid/database/Cursor;\n'
                '    move-result-object v7\n')
        loader.write_text(contents)

    feed_loader = class_paths["LQ3/E7;"]
    feed_loader.write_text(replace_once(feed_loader.read_text(),
        '    sget-object v0, LQ3/J8;->c:Ljava/util/ArrayList;',
        '    iget-object v0, v1, LQ3/E7;->r:Landroid/content/Context;\n'
        '    invoke-static {v0, v10}, Lcom/jeremykenedy/firetv/ui/FeedOrderBridge;->order(Landroid/content/Context;Ljava/util/List;)Ljava/util/ArrayList;\n'
        '    move-result-object v10\n'
        '    sget-object v0, LQ3/J8;->c:Ljava/util/ArrayList;'))

    main_background = class_paths["LQ3/T5;"]
    main_background.write_text(replace_once(main_background.read_text(),
        '    if-eqz v4, :cond_f',
        '    iget-object v14, v0, LQ3/T5;->z:Landroid/content/Context;\n'
        '    invoke-static {v14, v7}, Lcom/jeremykenedy/firetv/ui/MenuBackgroundLayer;->draw(Landroid/content/Context;LN/p;)Z\n'
        '    move-result v14\n'
        '    if-nez v14, :goto_3\n'
        '    if-eqz v4, :cond_f'))

    featured_image = class_paths["La/a;"]
    image_signature = '.method public static final n(LQ3/S8;IILC/d;LN2/k;LN/Z;Landroid/content/Context;LN/Z;LN/p;)V'
    image = featured_image.read_text()
    image_pattern = re.compile(r'^' + re.escape(image_signature) + r'\n.*?^\.end method', re.M | re.S)
    poster = image_pattern.search(image).group(0)
    poster = replace_once(poster, 'sget-object p0, Lw0/h;->a:Lw0/J;',
        '''invoke-static/range {p6 .. p6}, Lcom/jeremykenedy/firetv/ui/FeaturedCardsActivity;->fit(Landroid/content/Context;)Z
    move-result p0
    if-eqz p0, :featured_fill_artwork
    sget-object p0, Lw0/h;->b:Lw0/J;
    goto :featured_scale_ready
    :featured_fill_artwork
    sget-object p0, Lw0/h;->a:Lw0/J;
    :featured_scale_ready''')
    featured_image.write_text(replace_method(image, image_signature, poster))

    renderer_signature = '.method public static final m(LQ3/S8;Ln4/a;Le0/p;ZZZZJIJJLn4/a;Ln4/a;Ln4/c;IILn4/a;JLN2/k;LN/p;II)V'
    renderer = featured_image.read_text()
    renderer_pattern = re.compile(r'^' + re.escape(renderer_signature) + r'\n.*?^\.end method', re.M | re.S)
    card_render = renderer_pattern.search(renderer).group(0)
    card_render = replace_once(card_render, '    .locals 54', '    .locals 58')
    card_render = replace_once(card_render,
        '    invoke-static {v13, v4}, Landroidx/compose/ui/input/key/a;->a(LZ/o;Ln4/c;)LZ/o;',
        '    new-instance v54, Lcom/jeremykenedy/firetv/ui/CardKeyAction;\n'
        '    move-object/from16 v55, v4\n'
        '    move-object/from16 v56, v19\n'
        '    move-object/from16 v57, p0\n'
        '    invoke-direct/range {v54 .. v57}, Lcom/jeremykenedy/firetv/ui/CardKeyAction;-><init>(Ln4/c;Landroid/content/Context;LQ3/S8;)V\n'
        '    move-object/from16 v4, v54\n'
        '    invoke-static {v13, v4}, Landroidx/compose/ui/input/key/a;->a(LZ/o;Ln4/c;)LZ/o;')
    featured_image.write_text(replace_method(renderer, renderer_signature, card_render))

    rotation = class_paths["LQ3/h8;"]
    rotation.write_text(replace_method(rotation.read_text(),
        '.method public final k(Ljava/lang/Object;)Ljava/lang/Object;',
        '.method public final k(Ljava/lang/Object;)Ljava/lang/Object;\n'
        '    .locals 1\n    sget-object v0, Lb4/n;->a:Lb4/n;\n'
        '    return-object v0\n.end method'))

    model = class_paths["LQ3/T3;"]
    text = replace_once(model.read_text(), '# instance fields',
        '# instance fields\n.field public final featuredRotation:LN/g0;\n')
    text = replace_once(text,
        '    iput-object v0, v1, LQ3/T3;->a:Landroid/content/SharedPreferences;',
        '    iput-object v0, v1, LQ3/T3;->a:Landroid/content/SharedPreferences;\n'
        '    invoke-static {v0}, Lcom/jeremykenedy/firetv/ui/FeaturedCardTiming;->setting(Landroid/content/SharedPreferences;)Ljava/lang/Integer;\n'
        '    move-result-object v2\n'
        '    sget-object v3, LN/T;->l:LN/T;\n'
        '    invoke-static {v2, v3}, LN/d;->M(Ljava/lang/Object;LN/I0;)LN/g0;\n'
        '    move-result-object v2\n'
        '    iput-object v2, v1, LQ3/T3;->featuredRotation:LN/g0;')
    text += '''
.method public final refreshFeaturedRotation()V
    .locals 2
    iget-object v0, p0, LQ3/T3;->a:Landroid/content/SharedPreferences;
    invoke-static {v0}, Lcom/jeremykenedy/firetv/ui/FeaturedCardTiming;->setting(Landroid/content/SharedPreferences;)Ljava/lang/Integer;
    move-result-object v0
    iget-object v1, p0, LQ3/T3;->featuredRotation:LN/g0;
    invoke-virtual {v1, v0}, LN/g0;->setValue(Ljava/lang/Object;)V
    return-void
.end method
'''
    text += '''
.method public final refreshLauncherLayout()V
    .locals 2
    iget-object v0, p0, LQ3/T3;->a:Landroid/content/SharedPreferences;
    invoke-static {v0}, Lcom/jeremykenedy/firetv/ui/LauncherLayout;->columns(Landroid/content/SharedPreferences;)Ljava/lang/Integer;
    move-result-object v0
    iget-object v1, p0, LQ3/T3;->i:LN/g0;
    invoke-virtual {v1, v0}, LN/g0;->setValue(Ljava/lang/Object;)V
    return-void
.end method
'''
    model.write_text(text)
    wallpaper = class_paths["LO4/m;"]
    wallpaper_text = replace_once(wallpaper.read_text(),
        '    :cond_17\n    :goto_f\n    const v4, -0x34120e8a',
        '    :cond_17\n    :goto_f\n'
        '    invoke-static {v2}, Lcom/jeremykenedy/firetv/ui/FeaturedGroupBridge;->current(LQ3/S8;)LQ3/S8;\n'
        '    move-result-object v2\n'
        '    const v4, -0x34120e8a')
    wallpaper.write_text(replace_once(wallpaper_text,
        '    filled-new-array {v2, v3, v4, v7}, [Ljava/lang/Object;',
        '    iget-object v9, v0, LQ3/T3;->featuredRotation:LN/g0;\n'
        '    invoke-virtual {v9}, LN/g0;->getValue()Ljava/lang/Object;\n'
        '    move-result-object v9\n'
        '    filled-new-array {v2, v3, v4, v7, v9}, [Ljava/lang/Object;'))

    settings = class_paths["LQ3/M7;"]
    text = settings.read_text()
    text = replace_once(text, '    filled-new-array/range {v49 .. v55}, [Ljava/lang/String;',
        '    const-string v56, "Startup and protection"\n\n'
        '    const-string v57, \"Advanced\"\n\n'
        '    const-string v58, "Animated backgrounds"\n\n'
        '    filled-new-array/range {v49 .. v58}, [Ljava/lang/String;')
    text = replace_method(text, '.method public static final H(ZLn4/a;Ljava/util/Map;LN/p;I)V',
        '.method public static final H(ZLn4/a;Ljava/util/Map;LN/p;I)V\n    .locals 0\n    return-void\n.end method')
    menu_signature = '.method public static final G(Ljava/util/List;ILjava/util/List;Ljava/util/Map;Ln4/c;Ln4/a;LN/p;I)V'
    menu_pattern = re.compile(r'^' + re.escape(menu_signature) + r'\n.*?^\.end method', re.M | re.S)
    menu = menu_pattern.search(text).group(0)
    menu = replace_once(menu, '    const/16 v4, 0xc\n\n    int-to-float v4, v4',
        '    const/16 v4, 0x6\n\n    int-to-float v4, v4')
    menu = replace_once(menu, '    const/4 v11, 0x0\n\n'
        '    invoke-static/range {v4 .. v11}, LQ3/M7;->I',
        '    const-string v11, "Advanced"\n\n'
        '    invoke-virtual {v11, v4}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z\n\n'
        '    move-result v11\n\n'
        '    if-eqz v11, :screensaver_settings_action\n\n'
        '    sget-object v11, Landroidx/compose/ui/platform/AndroidCompositionLocals_androidKt;->b:LN/S0;\n\n'
        '    invoke-virtual {v10, v11}, LN/p;->k(LN/m0;)Ljava/lang/Object;\n\n'
        '    move-result-object v11\n\n'
        '    check-cast v11, Landroid/content/Context;\n\n'
        '    new-instance v9, Lcom/jeremykenedy/firetv/ui/AdvancedAction;\n\n'
        '    invoke-direct {v9, v11}, Lcom/jeremykenedy/firetv/ui/AdvancedAction;-><init>(Landroid/content/Context;)V\n\n'
        '    goto :native_settings_action_done\n\n'
        '    :screensaver_settings_action\n'
        '    const-string v11, "TV screensaver"\n\n'
        '    invoke-virtual {v11, v4}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z\n\n'
        '    move-result v11\n\n'
        '    if-eqz v11, :normal_settings_action\n\n'
        '    sget-object v11, Landroidx/compose/ui/platform/AndroidCompositionLocals_androidKt;->b:LN/S0;\n\n'
        '    invoke-virtual {v10, v11}, LN/p;->k(LN/m0;)Ljava/lang/Object;\n\n'
        '    move-result-object v11\n\n'
        '    check-cast v11, Landroid/content/Context;\n\n'
        '    new-instance v9, Lcom/jeremykenedy/firetv/ui/ScreensaverAction;\n\n'
        '    invoke-direct {v9, v11}, Lcom/jeremykenedy/firetv/ui/ScreensaverAction;-><init>(Landroid/content/Context;)V\n\n'
        '    goto :native_settings_action_done\n\n'
        '    :normal_settings_action\n'
        '    const-string v11, "Animated backgrounds"\n'
        '    invoke-virtual {v11, v4}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z\n'
        '    move-result v11\n'
        '    if-eqz v11, :startup_settings_action\n'
        '    sget-object v11, Landroidx/compose/ui/platform/AndroidCompositionLocals_androidKt;->b:LN/S0;\n'
        '    invoke-virtual {v10, v11}, LN/p;->k(LN/m0;)Ljava/lang/Object;\n'
        '    move-result-object v11\n'
        '    check-cast v11, Landroid/content/Context;\n'
        '    new-instance v9, Lcom/jeremykenedy/firetv/ui/MenuBackgroundAction;\n'
        '    invoke-direct {v9, v11}, Lcom/jeremykenedy/firetv/ui/MenuBackgroundAction;-><init>(Landroid/content/Context;)V\n'
        '    goto :native_settings_action_done\n'
        '    :startup_settings_action\n'
        '    const-string v11, "Startup and protection"\n\n'
        '    invoke-virtual {v11, v4}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z\n\n'
        '    move-result v11\n\n'
        '    if-eqz v11, :native_settings_action_done\n\n'
        '    sget-object v11, Landroidx/compose/ui/platform/AndroidCompositionLocals_androidKt;->b:LN/S0;\n\n'
        '    invoke-virtual {v10, v11}, LN/p;->k(LN/m0;)Ljava/lang/Object;\n\n'
        '    move-result-object v11\n\n'
        '    check-cast v11, Landroid/content/Context;\n\n'
        '    new-instance v9, Lcom/jeremykenedy/firetv/ui/SystemControlsAction;\n\n'
        '    invoke-direct {v9, v11}, Lcom/jeremykenedy/firetv/ui/SystemControlsAction;-><init>(Landroid/content/Context;)V\n\n'
        '    :native_settings_action_done\n'
        '    const/4 v11, 0x0\n\n'
        '    invoke-static/range {v4 .. v11}, LQ3/M7;->I')
    text = replace_method(text, menu_signature, menu)
    text = replace_once(text,
        '    move/from16 v1, p15\n\n'
        '    invoke-static/range {v0 .. v12}, LA3/a;->A(Ljava/lang/String;ZZZLe0/p;Ln4/a;Ln4/a;Ln4/a;Ln4/a;Ln4/c;LN/p;II)V',
        '    move/from16 v1, p15\n\n'
        '    move-object/from16 v0, p1\n'
        '    invoke-static {v13, v10, v0}, Lcom/jeremykenedy/firetv/ui/ScreensaverSettingsRow;->draw(Ljava/util/Map;LN/p;Ln4/a;)V')

    text = replace_once(text,
        '    const-string v0, "Cards_PosterMode"\n\n    move-object/from16 v13, p29',
        '    move-object/from16 v13, p29\n'
        '    const-string v0, "Cards_PosterMode"')
    poster_start = text.index('    const-string v0, "Cards_PosterMode"')
    poster_end = text.index('    const-string v0, "Cards_HideWatchNext"', poster_start)
    poster_row = text[poster_start:poster_end]
    poster_row = replace_once(poster_row, '    const/4 v7, 0x0',
        '    new-instance v7, Lcom/jeremykenedy/firetv/ui/CardFocusAction;\n'
        '    const-string v12, "Cards_Sources"\n'
        '    invoke-direct {v7, v13, v12}, Lcom/jeremykenedy/firetv/ui/CardFocusAction;-><init>(Ljava/util/Map;Ljava/lang/String;)V')
    poster_row = replace_once(poster_row,
        '    invoke-static/range {v0 .. v12}, LA3/a;->A',
        '    invoke-static {v13, v10, v6}, Lcom/jeremykenedy/firetv/ui/FeaturedCardSetupRow;->draw(Ljava/util/Map;LN/p;Ln4/a;)V\n\n'
        '    invoke-static/range {v0 .. v12}, LA3/a;->A')
    text = text[:poster_start] + poster_row + text[poster_end:]
    channel_signature = '.method public static final j(Ljava/util/List;Ljava/util/Map;Ln4/a;Ln4/c;Ln4/a;LN/p;I)V'
    channel_pattern = re.compile(r'^' + re.escape(channel_signature) + r'\n.*?^\.end method', re.M | re.S)
    channel_manager = channel_pattern.search(text).group(0)
    channel_manager = replace_once(channel_manager, '    .locals 71', '    .locals 74')
    channel_manager = replace_once(channel_manager,
        '    invoke-static {v4, v15}, Landroidx/compose/ui/input/key/a;->a(LZ/o;Ln4/c;)LZ/o;',
        '    move-object/from16 v71, v15\n'
        '    move-object/from16 v72, v40\n'
        '    move-object/from16 v73, v9\n'
        '    invoke-static/range {v71 .. v73}, Lcom/jeremykenedy/firetv/ui/ChannelCardsKeyAction;->wrap(Ln4/c;LQ3/l5;LN/p;)Ln4/c;\n'
        '    move-result-object v15\n'
        '    invoke-static {v4, v15}, Landroidx/compose/ui/input/key/a;->a(LZ/o;Ln4/c;)LZ/o;')
    channel_manager = replace_once(channel_manager,
        '    const v6, -0x14adc380',
        '    move-object/from16 v71, v40\n'
        '    move-object/from16 v72, v9\n'
        '    move-object/from16 v73, p4\n'
        '    invoke-static/range {v71 .. v73}, Lcom/jeremykenedy/firetv/ui/ChannelManageRow;->draw(LQ3/l5;LN/p;Ln4/a;)V\n\n'
        '    const v6, -0x14adc380')
    text = replace_method(text, channel_signature, channel_manager)

    text = replace_once(text,
        '    :cond_4\n    const/high16 v0, 0x10000000\n\n'
        '    :try_start_0\n    invoke-static {p2}, Landroid/media/tv/TvContract;->buildChannelUriForPassthroughInput',
        '    :cond_4\n'
        '    invoke-static {p1, p2}, Lcom/jeremykenedy/firetv/ui/TvInputs;->openFireTv(Landroid/content/Context;Ljava/lang/String;)Z\n'
        '    move-result v0\n'
        '    if-eqz v0, :standard_tv_input\n'
        '    return-void\n'
        '    :standard_tv_input\n'
        '    const/high16 v0, 0x10000000\n\n'
        '    :try_start_0\n    invoke-static {p2}, Landroid/media/tv/TvContract;->buildChannelUriForPassthroughInput')
    text = replace_method(text, '.method public static final i(Landroid/content/Context;Landroid/content/SharedPreferences;Ln4/a;Ljava/util/Map;LN/p;I)V',
        (ROOT / 'app/smali/backup-panel.smali').read_text())
    text = replace_once(text, '    const-string v3, "Rate on Play Store"',
        '    move-object/from16 v8, p0\n\n    const-string v3, "Close"')
    # Remove the paid-tier section while retaining the full feature guide.
    help_pattern = r'(const-string v0, ")(\\nAT4K Launcher.*?)("\n)'
    match = re.search(help_pattern, text)
    if not match:
        raise ValueError('Help text anchor was not found')
    help_text = match.group(2)
    divider = r'\u2550' * 28
    section = r'\n\n' + divider + r'\nPREMIUM\n' + divider
    start = help_text.index(section)
    end = help_text.index(r'\n\n' + divider + r'\nTHEME MODE & WALLPAPERS', start)
    help_text = help_text[:start] + help_text[end:]
    help_text = help_text.replace(r'\u2022 Premium\n', r'\u2022 TV screensaver\n')
    help_text = help_text.replace(' (Premium)', '').replace(' - Premium', '').replace(' (Free)', '')
    help_text = help_text.replace('Screensaver: Activates a minimal clock screensaver',
        'Screensavers: Open the dedicated screensaver controls')
    help_text = help_text.replace('after ~2 minutes of inactivity.',
        'to choose the active app, enabled state, preview and idle delay.')
    help_text = help_text.replace(r'\nLICENSES & CREDITS',
        r'\nTV SCREENSAVER\n\nOpen Settings > TV screensaver. '
        r'Select an installed screensaver to make it active, or turn the TV screensaver on or off. '
        r'Appearance opens these same controls. The selected delay is shared by launcher and system idle handling. '
        r'If setup is needed, follow the on-screen instructions from your computer. '
        r'The selected screensaver and enabled state are read back from the TV.\n\nLICENSES & CREDITS')
    help_text = help_text.replace('Select an installed screensaver to make it active,',
        'Each row includes a small sample preview. Select an installed screensaver to make it active,')
    text = text[:match.start(2)] + help_text + text[match.end(2):]
    settings.write_text(text)

    focus = class_paths['LQ3/k7;']
    text = focus.read_text()
    new_hash = java_hash('Screensavers')
    hash_literal = f'-0x{-new_hash:x}' if new_hash < 0 else f'0x{new_hash:x}'
    text = replace_once(text, '        0x503d6637 -> :sswitch_0',
        f'        {hash_literal} -> :sswitch_0')
    def order_switch(match):
        rows = match.group(1).splitlines()
        rows.sort(key=lambda row: int(row.strip().split()[0], 0))
        return '    .sparse-switch\n' + '\n'.join(rows) + '\n    .end sparse-switch'
    text = re.sub(r'    \.sparse-switch\n(.*?)\n    \.end sparse-switch', order_switch, text, flags=re.S)
    focus.write_text(text)

    project = class_paths['LQ3/F2;']
    text = project.read_text()
    start = text.index('    const-string v2, "context"')
    end = text.index('    :pswitch_0', start)
    text = text[:start] + '    return-object v0\n\n' + text[end:]
    text = replace_once(text,
        '    :pswitch_2\n    new-instance v2, Landroid/content/Intent;\n\n'
        '    const-string v3, "android.settings.SETTINGS"\n\n'
        '    invoke-direct {v2, v3}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V\n\n'
        '    invoke-virtual {v1, v2}, Landroid/content/Context;->startActivity(Landroid/content/Intent;)V',
        '    :pswitch_2\n'
        '    invoke-static {v1}, Lcom/jeremykenedy/firetv/ui/SystemSettings;->open(Landroid/content/Context;)V')
    project.write_text(text)

    boot = class_paths['Lcom/overdevs/at4k/BootReceiver;']
    boot.write_text(replace_method(boot.read_text(),
        '.method public final onReceive(Landroid/content/Context;Landroid/content/Intent;)V',
        '.method public final onReceive(Landroid/content/Context;Landroid/content/Intent;)V\n'
        '    .locals 0\n    return-void\n.end method'))

    telemetry = class_paths['Lcom/google/android/datatransport/cct/CctBackendFactory;']
    telemetry.write_text(replace_method(telemetry.read_text(),
        '.method public create(Li3/c;)Li3/g;',
        '.method public create(Li3/c;)Li3/g;\n    .locals 1\n'
        '    const/4 v0, 0x0\n    return-object v0\n.end method'))

    replacements = {
        'com.overdevs.at4k': CONFIG['package'],
        'com/overdevs/at4k': CONFIG['package'].replace('.', '/'),
        'AT4K Launcher': CONFIG['name'], 'AT4K': CONFIG['name'], 'At4k': CONFIG['name'],
        'Rate on Play Store': 'Close',
        'Enjoying AT4K Launcher?': '',
        'If you like using the app, please take a moment to rate and review it on the Play Store. It helps a lot!':
            '',
        'Developed by Avadhesh': 'Fire TV UI',
        'Privacy policy: https://at4klauncher.com/privacy': 'No analytics or tracking. Settings and backups stay on this TV.',
        'Premium_Close': 'Screensavers_Close', 'Premium0': 'Screensavers0',
        '"Premium"': '"Screensavers"',
        'TV screensaver': 'Screensavers',
        'Enable screensaver': 'Launcher idle screensaver',
        'Premium feature!': 'Feature available', 'Premium feature': 'Feature available',
        'Get premium to unlock this': 'Feature available',
        'Premium Unlocked Successfully!': 'Settings updated',
        'Buy Premium': 'Features', 'Unlock Fire TV UI Premium': 'Fire TV UI',
        'Support via BuyMeACoffee': 'Fire TV UI',
        'onPremiumPurchased': 'onFeaturesReady', 'isPremiumLocked': 'isFeatureLocked',
        'at4kbackup': 'fire-tv-ui-backup',
        'at4k.allapps': 'fire-tv-ui.allapps',
        'Scan to purchase via Buy Me A Coffee, then enter your license code below.': 'All features are available.',
        'https://at4klic.cutelittle.workers.dev/validate': 'https://github.com/jeremykenedy/fire-tv-ui',
    }
    text_paths = list((decoded / 'smali').rglob('*.smali'))
    text_paths += list((decoded / 'res').rglob('*.xml'))
    text_paths.append(decoded / 'AndroidManifest.xml')
    for path in text_paths:
        text = path.read_text()
        for old, new in replacements.items():
            text = text.replace(old, new)
        # Normalize the punctuation in UI prose.
        text = text.replace(r'\u2014', '-').replace(r'\u2013', '-')
        path.write_text(text)

    destination = decoded / 'smali/com/jeremykenedy/firetv/ui'
    destination.mkdir(parents=True, exist_ok=True)
    shutil.copy(ROOT / 'app/smali/ScreensaverAction.smali', destination)
    shutil.copy(ROOT / 'app/smali/ScreensaverSettingsRow.smali', destination)
    shutil.copy(ROOT / 'app/smali/BackupAction.smali', destination)
    shutil.copy(ROOT / 'app/smali/SystemControlsAction.smali', destination)
    shutil.copy(ROOT / 'app/smali/AdvancedAction.smali', destination)
    shutil.copy(ROOT / 'app/smali/CardSourcesAction.smali', destination)
    shutil.copy(ROOT / 'app/smali/CardFocusAction.smali', destination)
    shutil.copy(ROOT / 'app/smali/CardKeyAction.smali', destination)
    shutil.copy(ROOT / 'app/smali/FeedOrderBridge.smali', destination)
    shutil.copy(ROOT / 'app/smali/FeaturedGroupBridge.smali', destination)
    shutil.copy(ROOT / 'app/smali/FeaturedProgramMapper.smali', destination)
    shutil.copy(ROOT / 'app/smali/MenuBackgroundLayer.smali', destination)
    shutil.copy(ROOT / 'app/smali/MenuBackgroundAction.smali', destination)
    shutil.copy(ROOT / 'app/smali/FeaturedCardSetupRow.smali', destination)
    shutil.copy(ROOT / 'app/smali/ChannelCardsAction.smali', destination)
    shutil.copy(ROOT / 'app/smali/ChannelManageRow.smali', destination)
    shutil.copy(ROOT / 'app/smali/ChannelCardsKeyAction.smali', destination)
    patch_manifest(decoded)

    manifest = ET.parse(decoded / 'AndroidManifest.xml')
    application = manifest.getroot().find('application')
    ET.SubElement(application, 'provider', {
        ANDROID + 'name': CONFIG['package'] + '.PlexArtworkProvider',
        ANDROID + 'authorities': CONFIG['package'] + '.plex.art',
        ANDROID + 'exported': 'false',
    })
    ET.SubElement(application, 'provider', {
        ANDROID + 'name': CONFIG['package'] + '.RecommendationArtworkProvider',
        ANDROID + 'authorities': CONFIG['package'] + '.recommendation.art',
        ANDROID + 'exported': 'false',
    })
    manifest.write(decoded / 'AndroidManifest.xml', encoding='utf-8', xml_declaration=True)

    metadata = decoded / 'apktool.yml'
    text = metadata.read_text()
    text = re.sub(r'  versionCode: \d+', f"  versionCode: {CONFIG['version_code']}", text)
    text = re.sub(r'  versionName: .*', f"  versionName: {CONFIG['version']}", text)
    metadata.write_text(text)

    for path in (decoded / 'res').glob('mipmap-*/ic_launcher*.png'):
        source = ('icon-foreground.png' if 'foreground' in path.name else
                  'icon-background.png' if 'background' in path.name else 'icon.png')
        shutil.copy(ROOT / 'art' / source, path)
    for path in (decoded / 'res').glob('drawable-*/full.png'):
        shutil.copy(ROOT / 'art/tv-banner.png', path)
    shutil.copy(ROOT / 'art/icon.png', decoded / 'res/drawable/play.png')
    # Compiled profiles refer to the original dex layout.
    shutil.rmtree(decoded / 'assets/dexopt', ignore_errors=True)
    shutil.copytree(ROOT / 'app/previews', decoded / 'assets/screensaver-previews', dirs_exist_ok=True)
    shutil.copy(ROOT / 'app/simple-setup.json', decoded / 'assets/simple-setup.json')
    (decoded / 'assets/native-tiles').mkdir(exist_ok=True)
    for image in (ROOT / 'app/native-tiles').glob('*.png'):
        shutil.copy(image, decoded / 'assets/native-tiles' / image.name)
    (decoded / 'assets/input-icons').mkdir(exist_ok=True)
    for icon in ('xbox-one.png', 'wii-u.png'):
        shutil.copy(ROOT / 'icons' / icon, decoded / 'assets/input-icons' / icon)
    shutil.copy(ROOT / 'app/screensavers.json', decoded / 'assets/screensavers.json')
    shutil.copy(ROOT / 'app/preference-types.json', decoded / 'assets/preference-types.json')


def patch_manifest(decoded):
    path = decoded / 'AndroidManifest.xml'
    tree = ET.parse(path)
    root = tree.getroot()
    app = root.find('application')
    app.set(ANDROID + 'label', CONFIG['name'])
    for permission in ['android.permission.WRITE_SECURE_SETTINGS', 'android.permission.QUERY_ALL_PACKAGES',
                       'android.permission.MANAGE_EXTERNAL_STORAGE', 'android.permission.PACKAGE_USAGE_STATS',
                       'android.permission.REQUEST_INSTALL_PACKAGES', 'android.permission.REQUEST_DELETE_PACKAGES',
                       'android.permission.FOREGROUND_SERVICE', 'android.permission.FOREGROUND_SERVICE_SPECIAL_USE',
                       'android.permission.POST_NOTIFICATIONS']:
        ET.SubElement(root, 'uses-permission', {ANDROID + 'name': permission})
    ET.SubElement(root, 'uses-permission', {
        ANDROID + 'name': 'android.permission.WRITE_EXTERNAL_STORAGE',
        ANDROID + 'maxSdkVersion': '29',
    })
    app.set(ANDROID + 'requestLegacyExternalStorage', 'true')
    queries = root.find('queries')
    ET.SubElement(queries, 'package', {ANDROID + 'name': 'com.amazon.ftv.screensaver'})
    ET.SubElement(queries, 'package', {ANDROID + 'name': 'com.amazon.tv.launcher'})
    ET.SubElement(queries, 'package', {ANDROID + 'name': 'com.amazon.tv.livetv'})
    ET.SubElement(queries, 'package', {ANDROID + 'name': 'com.amazon.tv.settings.v2'})
    query = ET.SubElement(queries, 'intent')
    ET.SubElement(query, 'action', {ANDROID + 'name': 'android.service.dreams.DreamService'})
    for query in list(queries):
        action = query.find('action')
        if action is not None and 'billing' in action.get(ANDROID + 'name', '').lower():
            queries.remove(query)
    for child in list(root):
        if child.get(ANDROID + 'name') == 'com.android.vending.BILLING':
            root.remove(child)
    for child in list(app):
        name = child.get(ANDROID + 'name', '')
        if 'billingclient' in name or name.startswith('com.google.android.datatransport.') \
                or name.startswith('com.google.android.gms.'):
            app.remove(child)
    ET.SubElement(app, 'activity', {
        ANDROID + 'name': CONFIG['package'] + '.ScreensaverActivity',
        ANDROID + 'label': 'Screensavers',
        ANDROID + 'exported': 'true',
        ANDROID + 'screenOrientation': 'landscape',
        ANDROID + 'theme': '@android:style/Theme.Translucent.NoTitleBar',
    })
    ET.SubElement(app, 'activity', {
        ANDROID + 'name': CONFIG['package'] + '.BackupActivity',
        ANDROID + 'label': 'Backup and restore',
        ANDROID + 'exported': 'false',
        ANDROID + 'screenOrientation': 'landscape',
        ANDROID + 'theme': '@android:style/Theme.Translucent.NoTitleBar',
    })
    for name in ('TimersActivity', 'MemoryActivity', 'CatalogActivity', 'SystemControlsActivity', 'AdvancedActivity', 'PreviewCreditsActivity', 'DiagnosticsActivity', 'UpdatesActivity', 'CardSourcesActivity', 'PlexCardsActivity', 'PlexItemActivity', 'FeaturedCardsActivity', 'HiddenCardsActivity', 'ChannelCardsActivity', 'PlexFeedSettingsActivity', 'PlexHomeActivity', 'RecommendationCardsActivity', 'RecommendationItemActivity', 'FeedOrderActivity', 'MenuBackgroundActivity'):
        ET.SubElement(app, 'activity', {
            ANDROID + 'name': CONFIG['package'] + '.' + name,
            ANDROID + 'exported': 'false',
            ANDROID + 'screenOrientation': 'landscape',
            ANDROID + 'theme': '@android:style/Theme.Translucent.NoTitleBar',
        })
    listener = ET.SubElement(app, 'service', {
        ANDROID + 'name': CONFIG['package'] + '.RecommendationService',
        ANDROID + 'label': 'Fire TV UI app recommendation cards',
        ANDROID + 'exported': 'false',
        ANDROID + 'permission': 'android.permission.BIND_NOTIFICATION_LISTENER_SERVICE',
    })
    listener_filter = ET.SubElement(listener, 'intent-filter')
    ET.SubElement(listener_filter, 'action', {
        ANDROID + 'name': 'android.service.notification.NotificationListenerService',
    })
    provider = ET.SubElement(app, 'provider', {
        ANDROID + 'name': CONFIG['package'] + '.PackageFileProvider',
        ANDROID + 'authorities': CONFIG['package'] + '.install',
        ANDROID + 'exported': 'false',
        ANDROID + 'grantUriPermissions': 'true',
    })
    service = ET.SubElement(app, 'service', {
        ANDROID + 'name': CONFIG['package'] + '.ProtectionService',
        ANDROID + 'exported': 'true',
        ANDROID + 'permission': 'android.permission.BIND_VPN_SERVICE',
        ANDROID + 'foregroundServiceType': 'specialUse',
    })
    vpn_filter = ET.SubElement(service, 'intent-filter')
    ET.SubElement(vpn_filter, 'action', {ANDROID + 'name': 'android.net.VpnService'})
    ET.SubElement(service, 'property', {
        ANDROID + 'name': 'android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE',
        ANDROID + 'value': 'Local TV settings guard and scoped Amazon update traffic blocker',
    })
    receiver = ET.SubElement(app, 'receiver', {
        ANDROID + 'name': CONFIG['package'] + '.DeviceBootReceiver',
        ANDROID + 'exported': 'false',
    })
    boot_filter = ET.SubElement(receiver, 'intent-filter')
    for action in ('android.intent.action.BOOT_COMPLETED', 'android.intent.action.MY_PACKAGE_REPLACED'):
        ET.SubElement(boot_filter, 'action', {ANDROID + 'name': action})
    ET.SubElement(app, 'receiver', {
        ANDROID + 'name': CONFIG['package'] + '.BackupReceiver',
        ANDROID + 'exported': 'true',
        ANDROID + 'permission': 'android.permission.DUMP',
    })
    selection_receiver = ET.SubElement(app, 'receiver', {
        ANDROID + 'name': CONFIG['package'] + '.ScreensaverSelectionReceiver',
        ANDROID + 'exported': 'true',
        ANDROID + 'permission': 'android.permission.WRITE_SECURE_SETTINGS',
    })
    selection_filter = ET.SubElement(selection_receiver, 'intent-filter')
    ET.SubElement(selection_filter, 'action', {
        ANDROID + 'name': CONFIG['package'] + '.SELECT_SCREENSAVER',
    })
    service = ET.SubElement(app, 'service', {
        ANDROID + 'name': CONFIG['package'] + '.HomeRedirectService',
        ANDROID + 'label': CONFIG['name'] + ' Home button',
        ANDROID + 'exported': 'true',
        ANDROID + 'permission': 'android.permission.BIND_ACCESSIBILITY_SERVICE',
    })
    intent = ET.SubElement(service, 'intent-filter')
    ET.SubElement(intent, 'action', {ANDROID + 'name': 'android.accessibilityservice.AccessibilityService'})
    ET.SubElement(service, 'meta-data', {
        ANDROID + 'name': 'android.accessibilityservice',
        ANDROID + 'resource': '@xml/fire_tv_home_redirect',
    })
    shutil.copy(ROOT / 'app/fire_tv_home_redirect.xml', decoded / 'res/xml/fire_tv_home_redirect.xml')
    tree.write(path, encoding='utf-8', xml_declaration=True)


if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument('decoded', type=Path)
    patch(parser.parse_args().decoded)
