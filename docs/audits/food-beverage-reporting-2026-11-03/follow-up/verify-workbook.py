"""Compare a browser-downloaded workbook with its backend JSON; requires openpyxl."""
import json
import pathlib
import sys
from decimal import Decimal
import openpyxl

report = json.loads(pathlib.Path(sys.argv[1]).read_text())
book = openpyxl.load_workbook(sys.argv[2])
summary = list(book['Weekly summary'].values)
assert summary[1][:3] == (report['from'], report['to'], report['timezone'])
assert summary[2][1] == report['generatedAt']
count = 0
for week_index, week in enumerate(report['weeks']):
    sheet = list(book[week['start']].values)
    assert sheet[1][:3] == (week['start'], week['end'], report['timezone'])
    assert sheet[2][1] == report['generatedAt']
    rows = sheet[4:]
    assert len(rows) == len(week['rows'])
    for i, (actual, expected) in enumerate(zip(rows, week['rows'])):
        assert actual[:3] == (expected['section'], expected['label'], expected['unit'])
        for a, e in zip(actual[3:11], [*expected['days'], expected['total']]):
            assert (a is None) == (e is None), (expected['id'], a, e)
            if e is not None:
                assert Decimal(str(a)) == Decimal(str(e)), (expected['id'], a, e)
        assert actual[11] == expected['status']
        assert actual[12] == '\n'.join(expected['notes'])
        assert summary[4 + week_index][1 + i] == expected['total']
        count += 1
assert all(cell.data_type != 'f' for sheet in book for row in sheet for cell in row)
print(f'PASS: {count} rows; daily values, totals, nulls, statuses, notes and summary agree; no formulas')
