"""Extend the pinned native pack picker without changing installed-pack handling."""


def replace_once(text, old, new):
    if text.count(old) != 1:
        raise ValueError('Expected one built-in icon pack anchor: ' + old[:100])
    return text.replace(old, new, 1)


def patch_method(text, signature, transform):
    start = text.index(signature + '\n')
    end = text.index('.end method', start)
    return text[:start] + transform(text[start:end]) + text[end:]


def bitmap_hook(method, locals_count, delegate=False):
    call = ('    invoke-static {p0, p1, p2, p3, p4}, Lq0/c;->K('
            'Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;II)Lg0/h;\n'
            '    move-result-object v0\n    return-object v0\n') if delegate else (
            '    invoke-static {p0, p1, p2, p3, p4}, Lcom/jeremykenedy/firetv/ui/BuiltInIconPacks;->render('
            'Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;II)Landroid/graphics/Bitmap;\n'
            '    move-result-object v0\n'
            '    if-nez v0, :builtin_bitmap\n    return-object v0\n'
            '    :builtin_bitmap\n    new-instance v1, Lg0/h;\n'
            '    invoke-direct {v1, v0}, Lg0/h;-><init>(Landroid/graphics/Bitmap;)V\n'
            '    return-object v1\n')
    anchor = f'    .locals {locals_count}\n'
    return replace_once(method, anchor, anchor + '\n'
        '    invoke-static {p1}, Lcom/jeremykenedy/firetv/ui/BuiltInIconPacks;->supports(Ljava/lang/String;)Z\n'
        '    move-result v0\n    if-eqz v0, :installed_pack\n' + call +
        '    :installed_pack\n')


def patch_builtin_icon_packs(class_paths):
    pending = {}
    provider = class_paths['LQ3/F3;']
    text = provider.read_text()
    anchor = '    iget-object v0, p0, LQ3/F3;->l:LN/Z;'
    choices = ''
    for pack, label in [('clean_line', 'Clean line'), ('muted_solid', 'Muted solid')]:
        choices += ('    new-instance v0, LQ3/M3;\n'
                    f'    const-string v1, "builtin:{pack}"\n'
                    f'    const-string v2, "{label}"\n'
                    '    const/4 v3, 0x0\n'
                    '    invoke-direct {v0, v1, v2, v3}, LQ3/M3;-><init>('
                    'Ljava/lang/String;Ljava/lang/String;Landroid/graphics/drawable/Drawable;)V\n'
                    '    invoke-interface {p1, v0}, Ljava/util/List;->add(Ljava/lang/Object;)Z\n\n')
    pending[provider] = replace_once(text, anchor, choices + anchor)

    loader = class_paths['Lq0/c;']
    text = loader.read_text()
    text = patch_method(text, '.method public static final K(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;II)Lg0/h;',
                        lambda m: bitmap_hook(m, 7))
    text = patch_method(text, '.method public static final O(Landroid/content/Context;Ljava/lang/String;)Ljava/util/LinkedHashMap;',
                        lambda m: replace_once(m, '    .locals 3\n', '    .locals 3\n\n'
                            '    invoke-static {p0, p1}, Lcom/jeremykenedy/firetv/ui/BuiltInIconPacks;->mappings('
                            'Landroid/content/Context;Ljava/lang/String;)Ljava/util/LinkedHashMap;\n'
                            '    move-result-object v0\n    if-eqz v0, :installed_pack\n'
                            '    return-object v0\n    :installed_pack\n'))
    pending[loader] = text

    loader = class_paths['Landroid/support/v4/media/session/b;']
    pending[loader] = patch_method(loader.read_text(),
        '.method public static final N(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;II)Lg0/h;',
        lambda m: bitmap_hook(m, 4, delegate=True))

    startup = class_paths['LQ3/A7;']
    text = startup.read_text()
    anchor = ('    if-eqz v29, :cond_1a\n\n'
              '    move-object/from16 v29, v11')
    pending[startup] = replace_once(text, anchor,
        '    if-eqz v29, :cond_1a\n\n'
        '    invoke-static {v13}, Lcom/jeremykenedy/firetv/ui/BuiltInIconPacks;->supports(Ljava/lang/String;)Z\n'
        '    move-result v0\n    if-eqz v0, :installed_pack_artwork\n'
        '    move-object v0, v11\n'
        '    move-object/from16 v11, v29\n'
        '    iget-object v11, v11, Lg0/h;->a:Landroid/graphics/Bitmap;\n'
        '    move-object/from16 v29, v0\n'
        '    move/from16 v31, v4\n    move/from16 v37, v5\n'
        '    move-object/from16 v36, v10\n'
        '    goto/16 :try_start_29\n\n'
        '    :installed_pack_artwork\n'
        '    move-object/from16 v29, v11')

    preview = class_paths['LQ3/U2;']
    text = preview.read_text()
    anchor = ('    const/4 v4, 0x0\n\n    :try_start_0\n'
              '    invoke-virtual {p1}, Landroid/content/Context;->getPackageManager()Landroid/content/pm/PackageManager;')
    pending[preview] = replace_once(text, anchor,
        '    const/4 v4, 0x0\n\n'
        '    invoke-static {v1}, Lcom/jeremykenedy/firetv/ui/BuiltInIconPacks;->supports(Ljava/lang/String;)Z\n'
        '    move-result v5\n    if-eqz v5, :installed_pack_preview\n'
        '    const/16 v5, 0x78\n'
        '    invoke-static {p1, v1, v3, v5, v5}, Lq0/c;->K('
        'Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;II)Lg0/h;\n'
        '    move-result-object p1\n    goto/16 :goto_2\n'
        '    :installed_pack_preview\n\n    :try_start_0\n'
        '    invoke-virtual {p1}, Landroid/content/Context;->getPackageManager()Landroid/content/pm/PackageManager;')

    for path, content in pending.items():
        path.write_text(content)
