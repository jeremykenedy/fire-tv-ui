"""Apply app focus choices without changing the pinned artwork or animation."""

from pathlib import Path


def replace_once(text, old, new):
    if text.count(old) != 1:
        raise ValueError('Expected one icon focus anchor: ' + old[:100])
    return text.replace(old, new, 1)


def patch_icon_focus(class_paths):
    renderer = class_paths['LA3/a;']
    text = renderer.read_text()
    start = text.index('.method public static final g(LQ3/q;')
    end = text.index('.end method', start)
    method = text[start:end]
    method = replace_once(method,
        '    check-cast v5, Landroid/content/Context;',
        '    check-cast v5, Landroid/content/Context;\n\n'
        '    invoke-static {v5}, Lcom/jeremykenedy/firetv/ui/IconFocus;->configure(Landroid/content/Context;)V\n'
        '    invoke-static {}, Lcom/jeremykenedy/firetv/ui/IconFocusRevision;->observe()V')
    # Compose remembers the draw lambda across pack changes. Its artwork model
    # must follow the currently rendered tile, including a reused lambda.
    method = replace_once(method,
        '    :cond_c0\n    check-cast v5, Ln4/c;',
        '    :cond_c0\n'
        '    move-object/from16 p20, v5\n'
        '    move-object/from16 p21, v8\n'
        '    move/from16 p22, v53\n'
        '    check-cast p20, LQ3/C0;\n'
        '    invoke-virtual/range {p20 .. p22}, LQ3/C0;->setArtwork(LQ3/q;Z)V\n\n'
        '    check-cast v5, Ln4/c;')
    method = replace_once(method,
        '    const/16 v0, 0x12\n\n    int-to-float v0, v0',
        '    const/16 v0, 0x12\n\n    int-to-float v0, v0\n'
        '    invoke-static {v0}, Lcom/jeremykenedy/firetv/ui/IconFocus;->shadowSize(F)F\n'
        '    move-result v0')
    renderer.write_text(text[:start] + method + text[end:])

    scale = class_paths['LQ3/G0;']
    scale_text = replace_once(scale.read_text(),
        '    :pswitch_5\n    check-cast p1, Lg0/O;',
        '    :pswitch_5\n    check-cast p1, Lg0/O;\n'
        '    invoke-static {}, Lcom/jeremykenedy/firetv/ui/IconFocusRevision;->observe()V')
    scale.write_text(replace_once(scale_text,
        '    const v1, 0x3e3851ec    # 0.18f\n\n'
        '    mul-float/2addr v0, v1\n\n'
        '    const/high16 v1, 0x3f800000    # 1.0f\n\n'
        '    add-float/2addr v0, v1',
        '    invoke-static {v0}, Lcom/jeremykenedy/firetv/ui/IconFocus;->scale(F)F\n'
        '    move-result v0'))

    folder = class_paths['LQ3/C1;']
    folder_text = replace_once(folder.read_text(),
        '    const v6, 0x3f970a3d    # 1.18f',
        '    invoke-static {}, Lcom/jeremykenedy/firetv/ui/IconFocusRevision;->observe()V\n'
        '    const/high16 v6, 0x3f800000    # 1.0f\n'
        '    invoke-static {v6}, Lcom/jeremykenedy/firetv/ui/IconFocus;->scale(F)F\n'
        '    move-result v6')
    folder.write_text(replace_once(folder_text,
        '    int-to-float v11, v8\n\n'
        '    invoke-static {v14, v11, v2}, Lcom/overdevs/at4k/a;->a(LZ/o;FLC/d;)LZ/o;',
        '    int-to-float v11, v8\n'
        '    invoke-static {v11}, Lcom/jeremykenedy/firetv/ui/IconFocus;->shadowSize(F)F\n'
        '    move-result v11\n\n'
        '    invoke-static {v14, v11, v2}, Lcom/overdevs/at4k/a;->a(LZ/o;FLC/d;)LZ/o;'))

    revision = renderer.parents[1] / 'com/jeremykenedy/firetv/ui/IconFocusRevision.smali'
    revision.parent.mkdir(parents=True, exist_ok=True)
    revision.write_text((Path(__file__).resolve().parents[1] / 'app/smali/IconFocusRevision.smali').read_text())

    model = class_paths['LQ3/T3;']
    model.write_text(model.read_text() + REFRESH)
    main = class_paths['Lcom/overdevs/at4k/MainActivity;']
    anchor = '    invoke-super {p0}, Landroid/app/Activity;->onResume()V'
    main.write_text(replace_once(main.read_text(), anchor, anchor +
        '\n    iget-object v0, p0, Lcom/overdevs/at4k/MainActivity;->A:LQ3/T3;\n'
        '    if-eqz v0, :icon_focus_refresh_done\n'
        '    invoke-virtual {v0}, LQ3/T3;->refreshIconFocusShadow()V\n'
        '    :icon_focus_refresh_done\n'))

    background = class_paths['LQ3/C0;']
    text = background.read_text()
    text = replace_once(text, '# direct methods',
        '.field private artwork:LQ3/q;\n\n.field private iconMode:Z\n\n# direct methods')
    text += '''
.method public setArtwork(LQ3/q;Z)V
    .locals 0
    iput-object p1, p0, LQ3/C0;->artwork:LQ3/q;
    iput-boolean p2, p0, LQ3/C0;->iconMode:Z
    return-void
.end method
'''
    text = replace_once(text,
        '    invoke-static {v1, v2, v3, v4, p1}, Lg0/M;->u(JJF)J',
        '    iget-object v5, p0, LQ3/C0;->artwork:LQ3/q;\n'
        '    iget-boolean v6, p0, LQ3/C0;->iconMode:Z\n'
        '    if-eqz v6, :focus_banner\n'
        '    iget-object v6, v5, LQ3/q;->d:LN/g0;\n'
        '    invoke-virtual {v6}, LN/g0;->getValue()Ljava/lang/Object;\n'
        '    move-result-object v6\n'
        '    if-nez v6, :focus_bitmap\n'
        '    :focus_banner\n'
        '    iget-object v6, v5, LQ3/q;->c:LN/g0;\n'
        '    invoke-virtual {v6}, LN/g0;->getValue()Ljava/lang/Object;\n'
        '    move-result-object v6\n'
        '    :focus_bitmap\n'
        '    check-cast v6, Lg0/h;\n'
        '    const/4 v5, 0x0\n'
        '    if-eqz v6, :focus_color\n'
        '    iget-object v5, v6, Lg0/h;->a:Landroid/graphics/Bitmap;\n'
        '    :focus_color\n'
        '    invoke-static {v1, v2, v3, v4, v5}, Lcom/jeremykenedy/firetv/ui/IconFocus;->background(JJLandroid/graphics/Bitmap;)J\n'
        '    move-result-wide v3\n\n'
        '    invoke-static {v1, v2, v3, v4, p1}, Lg0/M;->u(JJF)J')
    background.write_text(text)

    border = class_paths['LQ3/I0;']
    text = border.read_text()
    text = replace_once(text,
        '    iget-boolean v2, v0, LQ3/I0;->g:Z\n\n    if-eqz v2, :cond_1',
        '    iget-boolean v2, v0, LQ3/I0;->g:Z\n'
        '    invoke-static {v2}, Lcom/jeremykenedy/firetv/ui/IconFocus;->borderEnabled(Z)Z\n'
        '    move-result v2\n\n    if-eqz v2, :cond_1')
    text = replace_once(text,
        '    iget-wide v4, v0, LQ3/I0;->k:J\n\n    :goto_0',
        '    iget-wide v4, v0, LQ3/I0;->k:J\n'
        '    invoke-static {v4, v5}, Lcom/jeremykenedy/firetv/ui/IconFocus;->border(J)J\n'
        '    move-result-wide v4\n\n    :goto_0')
    border.write_text(text)


REFRESH = '''
.method public final refreshIconFocusShadow()V
    .locals 3
    iget-object v0, p0, LQ3/T3;->a:Landroid/content/SharedPreferences;
    const-string v1, "icon_shadow"
    const/4 v2, 0x1
    invoke-interface {v0, v1, v2}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
    move-result v0
    invoke-static {v0}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;
    move-result-object v0
    iget-object v1, p0, LQ3/T3;->k:LN/g0;
    invoke-virtual {v1, v0}, LN/g0;->setValue(Ljava/lang/Object;)V
    invoke-static {}, Lcom/jeremykenedy/firetv/ui/IconFocus;->consumeSizeRefresh()Z
    move-result v0
    if-eqz v0, :icon_focus_no_size_change
    invoke-static {}, Lcom/jeremykenedy/firetv/ui/IconFocusRevision;->changed()V
    :icon_focus_no_size_change
    return-void
.end method
'''
