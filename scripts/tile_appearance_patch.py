"""Expose individual tile artwork and input label controls in the native menus."""


def replace_once(text, old, new):
    if text.count(old) != 1:
        raise ValueError('Expected one tile appearance anchor: ' + old[:100])
    return text.replace(old, new, 1)


def menu_action(label, action):
    return (
        '    move-object/from16 v3, p0\n'
        f'    const/4 v10, {action}\n'
        '    new-instance v12, Lcom/jeremykenedy/firetv/ui/TileAppearanceAction;\n'
        '    invoke-direct {v12, v2, v3, v10}, Lcom/jeremykenedy/firetv/ui/TileAppearanceAction;-><init>(Landroid/content/Context;LQ3/q;I)V\n'
        '    new-instance v10, LQ3/d1;\n'
        f'    const-string v11, "{label}"\n'
        '    const/4 v13, 0x0\n    const/4 v14, 0x0\n    const/16 v15, 0xc\n'
        '    invoke-direct/range {v10 .. v15}, LQ3/d1;-><init>(Ljava/lang/String;Ln4/a;ZZI)V\n'
        '    invoke-virtual {v1, v10}, Ld4/b;->add(Ljava/lang/Object;)Z\n\n')


def patch_picker_fit(text):
    return replace_once(text, '    sget-object v7, Lw0/h;->a:Lw0/J;',
                        '    sget-object v7, Lw0/h;->b:Lw0/J;')


def patch_tile_appearance(class_paths):
    menu = class_paths['LA3/a;']
    text = menu.read_text()
    anchor = ('    move-object/from16 v12, p11\n\n'
              '    invoke-direct/range {v10 .. v15}, LQ3/d1;-><init>(Ljava/lang/String;Ln4/a;ZZI)V\n\n'
              '    invoke-virtual {v1, v10}, Ld4/b;->add(Ljava/lang/Object;)Z\n')
    actions = ('\n    sget-object v2, Landroidx/compose/ui/platform/AndroidCompositionLocals_androidKt;->b:LN/S0;\n'
               '    invoke-virtual {v5, v2}, LN/p;->k(LN/m0;)Ljava/lang/Object;\n'
               '    move-result-object v2\n    check-cast v2, Landroid/content/Context;\n')
    actions += menu_action('Use factory icon', '0x0')
    actions += menu_action('Use theme icon', '0x1')
    actions += ('    move-object/from16 v3, p0\n'
                '    iget-object v3, v3, LQ3/q;->b:Ljava/lang/String;\n'
                '    const-string v10, "tvinput_name:"\n'
                '    invoke-virtual {v3, v10}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z\n'
                '    move-result v3\n    if-eqz v3, :tile_appearance_added\n')
    actions += menu_action('Rename input', '0x2')
    actions += menu_action('Use factory name', '0x3')
    actions += '    :tile_appearance_added\n'
    menu.write_text(replace_once(text, anchor, anchor + actions))

    dialog = class_paths['LQ3/c1;']
    text = dialog.read_text()
    anchor = ('    invoke-static {v0, v2}, Landroidx/compose/foundation/layout/a;->i(LZ/o;F)LZ/o;\n\n'
              '    move-result-object v0\n\n    sget-object v2, Lx/i;->a:Lx/a;')
    dialog.write_text(replace_once(text, anchor,
        '    invoke-static {v0, v2}, Landroidx/compose/foundation/layout/a;->i(LZ/o;F)LZ/o;\n\n'
        '    move-result-object v0\n\n'
        '    invoke-static {v9}, Ll4/j;->k(LN/p;)Ls/D0;\n'
        '    move-result-object v2\n'
        '    invoke-static {v0, v2}, Ll4/j;->o(LZ/o;Ls/D0;)LZ/o;\n'
        '    move-result-object v0\n\n    sget-object v2, Lx/i;->a:Lx/a;'))

    picker = class_paths['LQ3/T7;']
    picker.write_text(replace_once(picker.read_text(), '"Remove Icon"', '"Use factory icon"'))
    thumbnails = class_paths['LQ3/S7;']
    thumbnails.write_text(patch_picker_fit(thumbnails.read_text()))
    remove = class_paths['LQ3/a4;']
    text = remove.read_text()
    anchor = ('    iget-object v1, p0, LQ3/a4;->h:Landroid/content/SharedPreferences;\n\n'
              '    invoke-static {v1, v0}, LQ3/M7;->u(Landroid/content/SharedPreferences;LQ3/T3;)V')
    remove.write_text(replace_once(text, anchor,
        '    iget-object v1, p0, LQ3/a4;->h:Landroid/content/SharedPreferences;\n\n'
        '    invoke-static {v1, v4}, Lcom/jeremykenedy/firetv/ui/IconOverrides;->markFactory(Landroid/content/SharedPreferences;Ljava/lang/String;)V\n'
        '    invoke-static {v1, v0}, LQ3/M7;->u(Landroid/content/SharedPreferences;LQ3/T3;)V'))

    loader = class_paths['LQ3/A7;']
    text = loader.read_text()
    anchor = '    :goto_6\n    const-string v0, "_"'
    loader.write_text(replace_once(text, anchor,
        '    :goto_6\n'
        '    invoke-static {v4, v11}, Lcom/jeremykenedy/firetv/ui/IconOverrides;->pack(Landroid/content/Context;Ljava/lang/String;)Ljava/lang/String;\n'
        '    move-result-object v13\n'
        '    const-string v0, "_"'))

    inputs = class_paths['LQ3/B7;']
    text = inputs.read_text()
    anchor = '    invoke-direct {v2, v1, v12, v8, v5}, LQ3/q;-><init>(Ljava/lang/String;Ljava/lang/String;LN/g0;LN/g0;)V'
    inputs.write_text(replace_once(text, anchor,
        '    invoke-static {v11, v12, v1}, Lcom/jeremykenedy/firetv/ui/InputNames;->displayName(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;\n'
        '    move-result-object v1\n\n' + anchor))

    main = class_paths['Lcom/overdevs/at4k/MainActivity;']
    text = main.read_text()
    anchor = '    invoke-super {p0}, Landroid/app/Activity;->onResume()V'
    main.write_text(replace_once(text, anchor, anchor +
        '\n    invoke-static {}, Lcom/jeremykenedy/firetv/ui/IconOverrides;->consumeRefresh()Z\n'
        '    move-result v0\n    if-eqz v0, :tile_appearance_refresh_done\n'
        '    iget-object v0, p0, Lcom/overdevs/at4k/MainActivity;->A:LQ3/T3;\n'
        '    if-eqz v0, :tile_appearance_refresh_done\n'
        '    invoke-virtual {v0}, LQ3/T3;->refreshTileAppearance()V\n'
        '    :tile_appearance_refresh_done\n'))
    model = class_paths['LQ3/T3;']
    model.write_text(model.read_text() + REFRESH)

    action = menu.parents[1] / 'com/jeremykenedy/firetv/ui/TileAppearanceAction.smali'
    action.parent.mkdir(parents=True, exist_ok=True)
    action.write_text(ACTION)


ACTION = '''.class public final Lcom/jeremykenedy/firetv/ui/TileAppearanceAction;
.super Ljava/lang/Object;
.implements Ln4/a;

.field private final context:Landroid/content/Context;
.field private final tile:LQ3/q;
.field private final action:I

.method public constructor <init>(Landroid/content/Context;LQ3/q;I)V
    .locals 0
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    iput-object p1, p0, Lcom/jeremykenedy/firetv/ui/TileAppearanceAction;->context:Landroid/content/Context;
    iput-object p2, p0, Lcom/jeremykenedy/firetv/ui/TileAppearanceAction;->tile:LQ3/q;
    iput p3, p0, Lcom/jeremykenedy/firetv/ui/TileAppearanceAction;->action:I
    return-void
.end method

.method public final a()Ljava/lang/Object;
    .locals 4
    iget-object v0, p0, Lcom/jeremykenedy/firetv/ui/TileAppearanceAction;->context:Landroid/content/Context;
    iget-object v1, p0, Lcom/jeremykenedy/firetv/ui/TileAppearanceAction;->tile:LQ3/q;
    iget-object v2, v1, LQ3/q;->b:Ljava/lang/String;
    iget v3, p0, Lcom/jeremykenedy/firetv/ui/TileAppearanceAction;->action:I
    packed-switch v3, :actions
    invoke-static {v0, v2}, Lcom/jeremykenedy/firetv/ui/InputNames;->reset(Landroid/content/Context;Ljava/lang/String;)V
    goto :done
    :factory
    const/4 v3, 0x1
    invoke-static {v0, v2, v3}, Lcom/jeremykenedy/firetv/ui/IconOverrides;->restore(Landroid/content/Context;Ljava/lang/String;Z)V
    goto :done
    :theme
    const/4 v3, 0x0
    invoke-static {v0, v2, v3}, Lcom/jeremykenedy/firetv/ui/IconOverrides;->restore(Landroid/content/Context;Ljava/lang/String;Z)V
    goto :done
    :rename
    iget-object v1, v1, LQ3/q;->a:Ljava/lang/String;
    invoke-static {v0, v2, v1}, Lcom/jeremykenedy/firetv/ui/InputNames;->edit(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;)V
    :done
    sget-object v0, Lb4/n;->a:Lb4/n;
    return-object v0
    :actions
    .packed-switch 0x0
        :factory
        :theme
        :rename
    .end packed-switch
.end method
'''


REFRESH = '''
.method public final refreshTileAppearance()V
    .locals 3
    new-instance v0, LQ3/F6;
    iget-object v1, p0, LQ3/T3;->a:Landroid/content/SharedPreferences;
    const/4 v2, 0x0
    invoke-direct {v0, p0, v1, v2}, LQ3/F6;-><init>(LQ3/T3;Landroid/content/SharedPreferences;Lf4/c;)V
    sget-object v1, Lb4/n;->a:Lb4/n;
    invoke-virtual {v0, v1}, LQ3/F6;->k(Ljava/lang/Object;)Ljava/lang/Object;
    invoke-static {}, LQ3/r;->b()V
    return-void
.end method
'''
