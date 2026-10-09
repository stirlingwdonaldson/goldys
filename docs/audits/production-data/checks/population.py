#!/usr/bin/env python3
"""Generate metadata-driven, bounded aggregate-only table probes, then run them."""
import csv
import io
import json
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
schema = list(csv.reader((ROOT / 'evidence/schema.csv').read_text().splitlines()))
cols = {}
for row in schema[1:]:
    if row and row[0] == 'table_name':
        break
    cols.setdefault(row[0], []).append(row)
statements = []
for table, rows in cols.items():
    names = {r[2] for r in rows}
    expressions = ['count(*) AS row_count']
    if 'superseded_at' in names:
        expressions += ['count(*) FILTER (WHERE superseded_at IS NULL) AS current_rows',
                        'count(*) FILTER (WHERE superseded_at IS NOT NULL) AS superseded_rows']
    for r in rows:
        col, typ = r[2], r[3]
        expressions.append(f'count(*) FILTER (WHERE "{col}" IS NULL) AS "{col}_nulls"')
        if typ == 'date' or typ.startswith('timestamp'):
            expressions += [f'min("{col}") AS "{col}_min"', f'max("{col}") AS "{col}_max"']
        if col in {'source_system','logical_entity_id','product_name_key','stock_code','supplier_name','invoice_number','staff_ref','source_record_ref'}:
            expressions.append(f'count(DISTINCT "{col}") AS "{col}_distinct"')
    statements.append(f"SELECT '{table}' AS table_name,row_to_json(s) AS aggregate FROM (SELECT " + ','.join(expressions) + f' FROM public."{table}") s;')
sqlpath = ROOT / 'checks/population.sql'
sqlpath.write_text('-- P01: Exact aggregate-only population/missingness; small table heaps verified via S04.\n' + '\n'.join(statements)+'\n')
if '--metadata-only' in sys.argv:
    result = (ROOT/'evidence/population.csv').read_text()
else:
    result = subprocess.check_output(['python3', str(ROOT/'checks/probe.py'), str(sqlpath)], text=True)
    (ROOT/'evidence/population.csv').write_text(result)
out=[]
for row in csv.reader(io.StringIO(result)):
    if row and row[0] != 'table_name':
        a=json.loads(row[1]); out.append([row[0],a['row_count'],'exact',a.get('current_rows',''),a.get('superseded_rows',''),json.dumps(a,sort_keys=True)])
with (ROOT/'table-inventory.csv').open('w') as f:
    constraints={}
    indexes={}
    section='columns'
    for row in schema:
        if row and row[0]=='table_name' and len(row)>1 and row[1]=='conname':section='constraints';continue
        if row and row[0]=='tablename':section='indexes';continue
        if row and row[0]=='schemaname':section='done';continue
        if section=='constraints' and len(row)==4:constraints.setdefault(row[0],[]).append({'name':row[1],'kind':row[2],'definition':row[3]})
        if section=='indexes' and len(row)==3:indexes.setdefault(row[0],[]).append({'name':row[1],'definition':row[2]})
    w=csv.writer(f,lineterminator='\n');w.writerow(['table_name','rows','count_method','current_rows','superseded_rows','population_and_missingness','columns','constraints','indexes'])
    for row in out:
        table=row[0]
        column_meta=[{'ordinal':int(r[1]),'name':r[2],'native_type':r[3],'nullable':r[4]=='t','default':r[5]} for r in cols[table]]
        w.writerow(row+[json.dumps(column_meta),json.dumps(constraints.get(table,[])),json.dumps(indexes.get(table,[]))])
print('Inventoried',len(out),'tables; metadata/null/date aggregates only.')
