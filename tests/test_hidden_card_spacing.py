from pathlib import Path
import re
import struct
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
from patch_launcher import patch_hidden_card_spacing

SOURCE = (ROOT / 'tests/fixtures/hidden-card-spacing.smali').read_text()


def spacer_heights(source, columns, mini, bottom, widgets, hidden, picture):
    lines = [line.strip() for line in source.splitlines() if line.strip()]
    labels = {line: index for index, line in enumerate(lines) if line.startswith(':')}
    values = {'v39': int(picture)}
    methods = {'s': mini, 'q': bottom, 'j': widgets, 'm': hidden, 'f': columns}
    heights = []
    result = None
    index = 0
    while index < len(lines):
        line = lines[index]
        operation = line.split()[0]
        if operation.startswith('invoke-virtual') and 'LQ3/T3;' in line:
            result = methods[re.search(r'->(\w)\(', line).group(1)]
        elif operation.startswith('invoke-static') and 'layout/d;->g(' in line:
            result = values['v0']
        elif operation.startswith('invoke-static') and 'Lx/b;->b(' in line:
            heights.append(values['v0'])
        elif operation in ('move-result', 'move-result-object'):
            values[line.split()[1]] = result
        elif operation.startswith('const'):
            register, literal = line.split(' ', 1)[1].split(', ', 1)
            literal = literal.split(' #', 1)[0]
            if operation == 'const/high16':
                values[register] = struct.unpack('!f', int(literal, 16).to_bytes(4, 'big'))[0]
            else:
                values[register] = int(literal, 16)
        elif operation == 'int-to-float':
            destination, origin = line.split(' ', 1)[1].split(', ')
            values[destination] = float(values[origin])
        elif operation == 'add-int/lit8':
            destination, origin, literal = line.split(' ', 1)[1].split(', ')
            values[destination] = values[origin] + int(literal, 16)
        elif operation in ('mul-float/2addr', 'add-float/2addr', 'sub-float/2addr'):
            destination, origin = line.split(' ', 1)[1].split(', ')
            left, right = values[destination], values[origin]
            values[destination] = left * right if operation.startswith('mul') else left + right if operation.startswith('add') else left - right
        elif operation == 'add-float':
            destination, left, right = line.split(' ', 1)[1].split(', ')
            values[destination] = values[left] + values[right]
        elif operation in ('if-eqz', 'if-nez'):
            register, label = line.split(' ', 1)[1].split(', ')
            condition = not values[register] if operation == 'if-eqz' else bool(values[register])
            if condition:
                index = labels[label]
                continue
        elif operation.startswith('goto'):
            index = labels[line.split()[1]]
            continue
        elif not (line.startswith(':') or operation.startswith('invoke-') or operation == 'move-object/from16'):
            raise AssertionError('Unsupported spacer fixture instruction: ' + line)
        index += 1
    return heights


class HiddenCardSpacingChecks(unittest.TestCase):
    def test_actual_spacer_branches_collapse_only_vacated_card_space(self):
        patched = patch_hidden_card_spacing(SOURCE)
        for columns in (6, 7):
            for mini in (False, True):
                for bottom in (False, True):
                    for widgets in (False, True):
                        for hidden in (False, True):
                            for picture in (False, True):
                                with self.subTest(columns=columns, mini=mini, bottom=bottom, widgets=widgets, hidden=hidden, picture=picture):
                                    before = spacer_heights(SOURCE, columns, mini, bottom, widgets, hidden, picture)
                                    after = spacer_heights(patched, columns, mini, bottom, widgets, hidden, picture)
                                    if hidden and not widgets and not picture and mini:
                                        self.assertEqual(before, [350])
                                        self.assertEqual(after, [50])
                                    elif hidden and not widgets and not picture and bottom:
                                        self.assertEqual(before, [288 + 20 * (columns - 5)])
                                        self.assertEqual(after, [])
                                    else:
                                        self.assertEqual(after, before)

    def test_source_drift_and_reapplication_fail_closed(self):
        for changed in (SOURCE.replace('0x15e', '0x15f'), SOURCE.replace('if-nez v39, :cond_39', 'if-nez v38, :cond_39'), SOURCE + SOURCE,
                        patch_hidden_card_spacing(SOURCE)):
            with self.subTest(changed=changed[:30]):
                with self.assertRaises(ValueError):
                    patch_hidden_card_spacing(changed)


if __name__ == '__main__':
    unittest.main()
