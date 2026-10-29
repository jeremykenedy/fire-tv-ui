"""Keep the original vertical renderer and add an opt-in horizontal Home grid."""

import re


def once(text, before, after):
    if text.count(before) != 1:
        raise ValueError(f'Expected one launcher grid anchor: {before[:90]}')
    return text.replace(before, after, 1)


def method(text, name):
    matches = list(re.finditer(r'^\.method[^\n]* ' + re.escape(name) + r'\(.*?^\.end method', text, re.M | re.S))
    if len(matches) != 1:
        raise ValueError(f'Expected one grid method {name}')
    return matches[0].group(0)


def patch_launcher_grid(paths):
    destination = paths['LQ3/Z4;'].parent.parent / 'com/jeremykenedy/firetv/ui'
    destination.mkdir(parents=True, exist_ok=True)

    # Separate copies leave the existing vertical scroll nodes and folder grids intact.
    clones = {
        'Landroidx/compose/foundation/f;': 'Lcom/jeremykenedy/firetv/ui/HorizontalAppScroll;',
        'Landroidx/compose/foundation/ScrollSemanticsElement;': 'Lcom/jeremykenedy/firetv/ui/HorizontalAppSemantics;',
        'Landroidx/compose/foundation/ScrollingLayoutElement;': 'Lcom/jeremykenedy/firetv/ui/HorizontalAppLayout;',
    }
    for original, replacement in clones.items():
        text = paths[original].read_text()
        for before, after in clones.items():
            text = text.replace(before, after)
        if original.endswith('/f;'):
            text = once(text, 'sget-object v2, Lu/V;->g:Lu/V;', 'sget-object v2, Lu/V;->h:Lu/V;')
        else:
            node = 'Ls/A0;' if 'Semantics' in original else 'Ls/E0;'
            text = once(text, 'const/4 v1, 0x1\n\n    iput-boolean v1, v0, ' + node + '->u:Z',
                        'const/4 v1, 0x0\n\n    iput-boolean v1, v0, ' + node + '->u:Z')
            text = once(text, 'const/4 v0, 0x1\n\n    iput-boolean v0, p1, ' + node + '->u:Z',
                        'const/4 v0, 0x0\n\n    iput-boolean v0, p1, ' + node + '->u:Z')
            text = text.replace('isVertical=true', 'isVertical=false')
        (destination / (replacement.split('/')[-1][:-1] + '.smali')).write_text(text)

    bridge = '''.class public final Lcom/jeremykenedy/firetv/ui/LauncherGridScroll;
.super Ljava/lang/Object;

.method public static modifier(LZ/o;LN/p;FFI)LZ/o;
    .locals 6
    invoke-static {}, Lcom/jeremykenedy/firetv/ui/LauncherLayout;->horizontal()Z
    move-result v0
    if-nez v0, :horizontal
    return-object p0
    :horizontal
    const v0, 0x41e7263
    invoke-virtual {p1, v0}, LN/p;->V(I)V
    invoke-virtual {p1}, LN/p;->K()Ljava/lang/Object;
    move-result-object v0
    sget-object v1, LN/l;->a:LN/T;
    if-ne v0, v1, :remembered
    new-instance v0, Ls/D0;
    const/4 v1, 0x0
    invoke-direct {v0, v1}, Ls/D0;-><init>(I)V
    invoke-virtual {p1, v0}, LN/p;->f0(Ljava/lang/Object;)V
    :remembered
    check-cast v0, Ls/D0;
    const/4 v1, 0x0
    invoke-virtual {p1, v1}, LN/p;->p(Z)V
    new-instance v1, Lcom/jeremykenedy/firetv/ui/HorizontalAppScroll;
    invoke-direct {v1, v0}, Lcom/jeremykenedy/firetv/ui/HorizontalAppScroll;-><init>(Ls/D0;)V
    invoke-static {p0, v1}, LZ/a;->a(LZ/o;Ln4/f;)LZ/o;
    move-result-object v0
    int-to-float v1, p4
    add-float v2, p2, p3
    mul-float/2addr v1, v2
    sub-float/2addr v1, p3
    const/high16 v2, 0x42800000    # 64dp for focused artwork at both ends
    add-float/2addr v1, v2
    invoke-static {v0, v1}, Landroidx/compose/foundation/layout/d;->n(LZ/o;F)LZ/o;
    move-result-object v0
    const/high16 v1, 0x42000000    # 32dp at each scroll boundary
    const/4 v2, 0x0
    invoke-static {v0, v1, v2}, Landroidx/compose/foundation/layout/a;->j(LZ/o;FF)LZ/o;
    move-result-object v0
    return-object v0
.end method
'''
    (destination / 'LauncherGridScroll.smali').write_text(bridge)

    grid = paths['LQ3/Z4;'].read_text().replace('LQ3/Z4;', 'Lcom/jeremykenedy/firetv/ui/LauncherAppGrid;')
    grid = once(grid, '    :cond_9\n    add-int/lit8 v15, v13, -0x1',
                '    :cond_9\n'
                '    invoke-static {v4}, Lcom/jeremykenedy/firetv/ui/LauncherLayout;->gridWidth(F)F\n'
                '    move-result v4\n'
                '    add-int/lit8 v15, v13, -0x1')
    grid = once(grid, '    :goto_9\n    invoke-interface {v14}, Ljava/util/List;->size()I',
                '    :goto_9\n'
                '    invoke-interface {v14}, Ljava/util/List;->size()I\n'
                '    move-result v4\n'
                '    invoke-static {v4, v13}, Lcom/jeremykenedy/firetv/ui/LauncherLayout;->gridColumns(II)I\n'
                '    move-result v13\n'
                '    invoke-interface {v14}, Ljava/util/List;->size()I')
    # Low scratch registers are restored before the original Column setup resumes.
    grid = once(grid, '    :goto_a\n    iget-boolean v1, v0, Lcom/jeremykenedy/firetv/ui/LauncherAppGrid;->g:Z',
                '    :goto_a\n'
                '    move/from16 v16, v2\n'
                '    move/from16 v17, v4\n'
                '    move-object/from16 v18, v9\n'
                '    move-object/from16 v19, v6\n'
                '    move-object/from16 v20, v5\n'
                '    move/from16 v21, v31\n'
                '    move/from16 v22, v8\n'
                '    move/from16 v23, v13\n'
                '    invoke-static/range {v19 .. v23}, Lcom/jeremykenedy/firetv/ui/LauncherGridScroll;->modifier(LZ/o;LN/p;FFI)LZ/o;\n'
                '    move-result-object v6\n'
                '    move/from16 v2, v16\n'
                '    move/from16 v4, v17\n'
                '    move-object/from16 v9, v18\n'
                '    iget-boolean v1, v0, Lcom/jeremykenedy/firetv/ui/LauncherAppGrid;->g:Z')
    (destination / 'LauncherAppGrid.smali').write_text(grid)

    menu = paths['LQ3/M7;'].read_text()
    renderer = method(menu, 'd').replace(' final d(', ' final appGrid(')
    renderer = renderer.replace('LQ3/Z4;', 'Lcom/jeremykenedy/firetv/ui/LauncherAppGrid;')
    renderer = renderer.replace('LQ3/k4;', 'Lcom/jeremykenedy/firetv/ui/LauncherAppGridRecompose;')
    paths['LQ3/M7;'].write_text(menu + '\n' + renderer + '\n')
    callback = paths['LQ3/k4;'].read_text().replace('LQ3/k4;', 'Lcom/jeremykenedy/firetv/ui/LauncherAppGridRecompose;')
    callback = callback.replace('LQ3/M7;->d(', 'LQ3/M7;->appGrid(')
    (destination / 'LauncherAppGridRecompose.smali').write_text(callback)

    # The legacy favorites focus callback always scrolls the whole page to the
    # top. Expanded rows must let the existing parent scroll reveal focus.
    focus = paths['LQ3/F5;'].read_text()
    focus = once(focus, '    new-instance v0, LQ3/I5;',
                 '    invoke-static {}, Lcom/jeremykenedy/firetv/ui/LauncherLayout;->multipleHomeRows()Z\n'
                 '    move-result v0\n'
                 '    if-eqz v0, :legacy_favorites_scroll\n'
                 '    sget-object v0, Lb4/n;->a:Lb4/n;\n'
                 '    return-object v0\n'
                 '    :legacy_favorites_scroll\n'
                 '    new-instance v0, LQ3/I5;')
    paths['LQ3/F5;'].write_text(focus)
    page = paths['LQ3/T5;'].read_text()
    page = once(page, '    :cond_38\n    invoke-static {v3, v0}, Landroidx/compose/foundation/layout/d;->g(LZ/o;F)LZ/o;',
                '    :cond_38\n'
                '    invoke-static {v0}, Lcom/jeremykenedy/firetv/ui/LauncherLayout;->homeSpacer(F)F\n'
                '    move-result v0\n'
                '    invoke-static {v3, v0}, Landroidx/compose/foundation/layout/d;->g(LZ/o;F)LZ/o;')
    paths['LQ3/T5;'].write_text(page)

    home = paths['La/a;'].read_text()
    first = method(home, 'a')
    first = once(first,
                 '    invoke-static/range {p0 .. p1}, Lc4/r;->O0(Ljava/lang/Iterable;I)Ljava/util/List;\n\n    move-result-object v4',
                 '    invoke-static {}, Lcom/jeremykenedy/firetv/ui/LauncherLayout;->expandHomeApps()Z\n'
                 '    move-result v4\n'
                 '    if-eqz v4, :vertical_first_apps\n'
                 '    move-object/from16 v4, p0\n'
                 '    goto :first_apps_ready\n'
                 '    :vertical_first_apps\n'
                 '    invoke-static/range {p0 .. p1}, Lc4/r;->O0(Ljava/lang/Iterable;I)Ljava/util/List;\n'
                 '    move-result-object v4\n'
                 '    :first_apps_ready')
    first = first.replace('LQ3/M7;->d(', 'LQ3/M7;->appGrid(')
    home = home.replace(method(home, 'a'), first, 1)
    remainder = method(home, 'i')
    remainder = re.sub(r'(    \.locals \d+\n)', r'\1\n'
                       '    invoke-static {}, Lcom/jeremykenedy/firetv/ui/LauncherLayout;->horizontal()Z\n'
                       '    move-result v0\n'
                       '    if-eqz v0, :vertical_remaining_apps\n'
                       '    return-void\n'
                       '    :vertical_remaining_apps\n', remainder, count=1)
    home = home.replace(method(home, 'i'), remainder, 1)
    paths['La/a;'].write_text(home)
