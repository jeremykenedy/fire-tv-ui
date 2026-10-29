"""Keep the pinned Compose icon focus background separate from its artwork."""


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
        '    invoke-static {v5}, Lcom/jeremykenedy/firetv/ui/IconFocus;->configure(Landroid/content/Context;)V')
    method = replace_once(method,
        '    invoke-direct/range {p20 .. p25}, LQ3/C0;-><init>(Lr/c;JJ)V\n\n'
        '    move-object/from16 v5, p20',
        '    invoke-direct/range {p20 .. p25}, LQ3/C0;-><init>(Lr/c;JJ)V\n\n'
        '    move-object/from16 p21, v8\n'
        '    move/from16 p22, v53\n'
        '    invoke-virtual/range {p20 .. p22}, LQ3/C0;->setArtwork(LQ3/q;Z)V\n\n'
        '    move-object/from16 v5, p20')
    renderer.write_text(text[:start] + method + text[end:])

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
