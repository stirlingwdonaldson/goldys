#!/usr/bin/env python3
"""Validate audit outputs locally. Does not query production or execute application code."""
import csv
import json
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parent.parent
REPO = ROOT.parents[2]
required = [
    '00-executive-summary.md', '01-deployment-state.md', '02-database-inventory.md',
    '03-source-coverage.md', '04-ingestion-integrity.md', '05-information-loss.md',
    '06-relational-integrity.md', '07-canonical-and-projections.md',
    '08-analytical-capabilities.md', '09-frontend-backend-parity.md',
    '10-architecture-recommendations.md', '11-prioritised-remediation.md',
    '12-reproducible-checks.md', 'source-coverage.csv', 'table-inventory.csv',
    'field-preservation.csv', 'data-quality-scorecard.csv', 'findings.csv',
    'current-entity-model.mmd', 'current-data-lineage.mmd', 'proposed-data-architecture.mmd',
]
for name in required:
    assert (ROOT/name).is_file() and (ROOT/name).stat().st_size>100, name
print('Required outputs:',len(required),'present and populated')
for path in ROOT.glob('*.csv'):
    rows=list(csv.reader(path.open()))
    assert all(len(r)==len(rows[0]) for r in rows),path
    print(path.name,':',len(rows)-1,'rows;',len(rows[0]),'columns; valid CSV')
for path in (ROOT/'evidence').glob('*.json'):
    json.load(path.open())
for row in csv.DictReader((ROOT/'table-inventory.csv').open()):
    for field in ['population_and_missingness','columns','constraints','indexes']:
        json.loads(row[field])
tables={r['table_name'] for r in csv.DictReader((ROOT/'table-inventory.csv').open())}
diagram=(ROOT/'current-entity-model.mmd').read_text()
assert tables==set(re.findall(r'^    (\w+) \{',diagram,re.M))
print('ER model covers all',len(tables),'live tables; metadata JSON valid')
for path in ROOT.glob('*.md'):
    for target in re.findall(r'\]\(([^)]+)\)',path.read_text()):
        if not target.startswith(('http','#')) and target!='code-reference-index.md':
            assert (path.parent/target.split('#')[0]).exists(),(path,target)
for path in (ROOT/'checks').glob('*.sql'):
    text=re.sub(r'--[^\n]*','',path.read_text())
    assert not re.search(r'\b(?:INSERT|UPDATE|DELETE|ALTER|DROP|CREATE|TRUNCATE|GRANT|REVOKE|COPY)\b',text,re.I),path
print('Markdown links valid; SQL probes contain no write-operation tokens')
# Resolve abbreviated code references into precise paths + line ranges for readers.
source_files=list((REPO/'backend/src/main').rglob('*'))+list((REPO/'frontend').rglob('*'))
by_name={}
for p in source_files:
    if p.is_file() and p.suffix in {'.java','.ts','.tsx','.yml'}:
        by_name.setdefault(p.name,[]).append(p)
refs={}
for report in list(ROOT.glob('*.md'))+list(ROOT.glob('*.csv')):
    if report.name=='code-reference-index.md':continue
    for match in re.finditer(r'([A-Za-z0-9_-]+\.(?:java|tsx|ts|yml)):(\d+)(?:[–-](\d+))?',report.read_text()):
        name,start,end=match.groups();candidates=by_name.get(name,[])
        assert candidates,(report.name,name)
        # Duplicate page.tsx paths are documented explicitly in report prose; do not guess a page.
        if len(candidates)>1:continue
        p=candidates[0];last=int(end or start)
        assert 1<=int(start)<=last<=len(p.read_text().splitlines()),(report.name,p,last)
        key=str(p.relative_to(REPO));refs.setdefault(key,set()).add(start+('–'+end if end else ''))
index=['# Precise source reference index','',
       'Backend abbreviated class/file references in reports resolve to these full repository paths.',
       'Line ranges refer to audit checkout `f6140b8`. Backend data code is unchanged from release metadata `c3377bb`.',
       'Frontend page paths are stated explicitly in reports; ambiguous `page.tsx` basenames are not guessed here.','',
       '| Repository path | Referenced lines |','|---|---|']
for p,ranges in sorted(refs.items()):index.append('| `'+p+'` | '+', '.join(sorted(ranges))+' |')
(ROOT/'code-reference-index.md').write_text('\n'.join(index)+'\n')
print('Verified',len(refs),'unique source-file references and line bounds; source index written')
ids={r['finding_id'] for r in csv.DictReader((ROOT/'findings.csv').open())}
assert ids=={f'F{i:02d}' for i in range(1,24)}
print('23 findings have complete identifiers')
