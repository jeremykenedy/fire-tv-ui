from pathlib import Path
import re
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
from patch_launcher import patch_hidden_card_widget_row

SOURCE = (ROOT / 'tests/fixtures/hidden-card-widget-row.smali').read_text()


def selected_row(source, hidden, widgets, picture):
    lines = [line.strip() for line in source.splitlines() if line.strip()]
    labels = {line: index for index, line in enumerate(lines) if line.startswith(':')}
    values = {'v39': picture}
    methods = {'m': hidden, 'j': widgets}
    exits = {':goto_17': None, ':cond_2e': 'cards'}
    index = 0
    while index < len(lines):
        line = lines[index]
        operation = line.split()[0]
        target = None
        if operation == 'invoke-virtual':
            result = methods[re.search(r'->(\w)\(', line).group(1)]
        elif operation == 'move-result':
            values[line.split()[1]] = result
        elif operation in ('if-eqz', 'if-nez'):
            register, label = line.split(' ', 1)[1].split(', ')
            if bool(values[register]) == (operation == 'if-nez'):
                target = label
        elif operation.startswith('goto'):
            target = line.split()[1]
        elif not (line.startswith(':') or operation.startswith(('move-object', 'move-wide', 'iget-object', 'const/4'))):
            raise AssertionError('Unsupported row fixture instruction: ' + line)
        if target in exits:
            return exits[target]
        if target:
            index = labels[target]
        else:
            index += 1
    return 'widgets'


class HiddenCardWidgetRowChecks(unittest.TestCase):
    def test_original_gate_only_loses_widgets_when_cards_hidden(self):
        patched = patch_hidden_card_widget_row(SOURCE)
        cases = (
            # hidden, widgets, picture, original row, patched row
            (False, False, False, 'cards', 'cards'),
            (False, True, False, 'widgets', 'widgets'),
            (True, False, False, None, None),
            (True, True, False, None, 'widgets'),
            (False, False, True, 'cards', 'cards'),
            (False, True, True, 'cards', 'cards'),
            (True, False, True, 'cards', 'cards'),
            (True, True, True, 'cards', 'cards'),
        )
        for hidden, widgets, picture, before, after in cases:
            with self.subTest(hidden=hidden, widgets=widgets, picture=picture):
                self.assertEqual(selected_row(SOURCE, hidden, widgets, picture), before)
                self.assertEqual(selected_row(patched, hidden, widgets, picture), after)

    def test_source_drift_and_reapplication_fail_closed(self):
        for changed in (SOURCE.replace('if-eqz v2, :cond_1f', 'if-eqz v2, :cond_1e'),
                        SOURCE.replace('if-eqz v39, :cond_1e', 'if-eqz v38, :cond_1e'),
                        SOURCE + SOURCE, patch_hidden_card_widget_row(SOURCE)):
            with self.assertRaises(ValueError):
                patch_hidden_card_widget_row(changed)


if __name__ == '__main__':
    unittest.main()
