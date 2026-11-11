"""Connect card selection and Menu to the same controls without changing provider launch."""
from pathlib import Path
import re
import shutil

ROOT = Path(__file__).resolve().parents[1]
SIGNATURE = '.method public static final m(LQ3/S8;Ln4/a;Le0/p;ZZZZJIJJLn4/a;Ln4/a;Ln4/c;IILn4/a;JLN2/k;LN/p;II)V'
OLD = '''    new-instance v54, Lcom/jeremykenedy/firetv/ui/CardKeyAction;
    move-object/from16 v55, v4
    move-object/from16 v56, v19
    move-object/from16 v57, p0
    invoke-direct/range {v54 .. v57}, Lcom/jeremykenedy/firetv/ui/CardKeyAction;-><init>(Ln4/c;Landroid/content/Context;LQ3/S8;)V
    move-object/from16 v4, v54
    invoke-static {v13, v4}, Landroidx/compose/ui/input/key/a;->a(LZ/o;Ln4/c;)LZ/o;'''
NEW = '''    new-instance v54, Lcom/jeremykenedy/firetv/ui/CardClickAction;
    move-object/from16 v55, v19
    move-object/from16 v56, p0
    move-object/from16 v57, v2
    invoke-direct/range {v54 .. v57}, Lcom/jeremykenedy/firetv/ui/CardClickAction;-><init>(Landroid/content/Context;LQ3/S8;Ln4/a;)V
    move-object/from16 v2, v54
    new-instance v58, Lcom/jeremykenedy/firetv/ui/CardKeyAction;
    move-object/from16 v59, v4
    move-object/from16 v60, v19
    move-object/from16 v61, p0
    move-object/from16 v62, v54
    invoke-direct/range {v58 .. v62}, Lcom/jeremykenedy/firetv/ui/CardKeyAction;-><init>(Ln4/c;Landroid/content/Context;LQ3/S8;Ljava/lang/Runnable;)V
    move-object/from16 v4, v58
    invoke-static {v13, v4}, Landroidx/compose/ui/input/key/a;->a(LZ/o;Ln4/c;)LZ/o;'''


def patch_card_controls(class_paths, decoded):
    path = class_paths['La/a;']
    text = path.read_text()
    pattern = re.compile(r'^' + re.escape(SIGNATURE) + r'\n.*?^\.end method', re.M | re.S)
    matches = list(pattern.finditer(text))
    if len(matches) != 1:
        raise ValueError('Expected one featured card renderer.')
    method = matches[0].group(0)
    if method.count('    .locals 58') != 1 or method.count(OLD) != 1:
        raise ValueError('Expected the exact maintained featured card key wrapper.')
    method = method.replace('    .locals 58', '    .locals 63', 1).replace(OLD, NEW, 1)
    text = text[:matches[0].start()] + method + text[matches[0].end():]
    path.write_text(text)
    destination = decoded / 'smali/com/jeremykenedy/firetv/ui'
    destination.mkdir(parents=True, exist_ok=True)
    for name in ('CardClickAction.smali', 'CardKeyAction.smali'):
        shutil.copy(ROOT / 'app/smali' / name, destination / name)
