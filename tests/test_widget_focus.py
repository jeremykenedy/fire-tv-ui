from pathlib import Path
import re
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
from patch_launcher import patch_widget_focus

SOURCE = (ROOT / 'tests/fixtures/widget-focus.smali').read_text()


def up_callback(source, hidden, widgets, picture, favorites_empty):
    lines = [line.strip() for line in source.splitlines() if line.strip()]
    labels = {line: index for index, line in enumerate(lines) if line.startswith(':')}
    values = {'v39': picture, 'v22': 'fallback', 'v51': 'fallback', 'v2': None, 'v1': 'remember', 'v8': 0}
    index = 0
    while index < len(lines):
        line = lines[index]
        operation = line.split()[0]
        if 'LQ3/T3;->' in line:
            result = {'m': hidden, 'j': widgets, 'k': 'favorites'}[re.search(r'->(\w)\(', line).group(1)]
        elif 'List;->isEmpty()' in line:
            result = favorites_empty
        elif 'LN/p;->f(' in line:
            result = True
        elif 'LN/p;->K()' in line:
            result = 'remember'
        elif operation == 'move-result' or operation == 'move-result-object':
            values[line.split()[1]] = result
        elif operation.startswith('const'):
            register, literal = line.split(' ', 1)[1].split(', ')
            values[register] = int(literal, 16)
        elif operation.startswith('move-object') or operation == 'move/from16':
            destination, origin = line.split(' ', 1)[1].split(', ')
            values[destination] = values[origin]
        elif operation == 'new-instance':
            values[line.split()[1].rstrip(',')] = 'picture'
        elif operation in ('if-eqz', 'if-nez', 'if-ne'):
            arguments = line.split(' ', 1)[1].split(', ')
            condition = values[arguments[0]] != values[arguments[1]] if operation == 'if-ne' else bool(values[arguments[0]]) == (operation == 'if-nez')
            if condition:
                index = labels[arguments[-1]]
                continue
        elif operation.startswith('goto'):
            index = labels[line.split()[1]]
            continue
        elif not (line.startswith(':') or operation.startswith(('invoke-', 'move-wide', 'check-cast'))):
            raise AssertionError('Unsupported focus fixture instruction: ' + line)
        index += 1
    return values['v2'] if ':goto_29' in labels else values['v3']


class WidgetFocusChecks(unittest.TestCase):
    def test_visible_widgets_use_default_up_search_in_both_app_grids(self):
        patched = patch_widget_focus(SOURCE)
        for hidden in (False, True):
            for widgets in (False, True):
                for picture in (False, True):
                    for empty in (False, True):
                        for before, after in zip(SOURCE.split('    # remaining apps\n'), patched.split('    # remaining apps\n')):
                            with self.subTest(hidden=hidden, widgets=widgets, picture=picture, empty=empty, remaining=':goto_29' in before):
                                old = up_callback(before, hidden, widgets, picture, empty)
                                new = up_callback(after, hidden, widgets, picture, empty)
                                if hidden and widgets and not picture:
                                    self.assertEqual(new, 0)
                                    self.assertEqual(old, 'fallback' if empty or ':goto_29' not in before else 0)
                                else:
                                    self.assertEqual(new, old)

    def test_source_drift_and_reapplication_fail_closed(self):
        for changed in (SOURCE.replace('if-nez v3, :cond_40', 'if-nez v3, :cond_41'),
                        SOURCE.replace('if-nez v2, :cond_67', 'if-nez v2, :cond_68'),
                        SOURCE + SOURCE, patch_widget_focus(SOURCE)):
            with self.assertRaises(ValueError):
                patch_widget_focus(changed)


if __name__ == '__main__':
    unittest.main()
