#!/usr/bin/env python3
"""Inspect bounded retained payloads in memory; export field paths/types/counts only.
No external source calls; no payload, guest, employee, supplier or product values exported.
"""
import base64
import collections
import csv
import io
import json
import pathlib
import subprocess

ROOT=pathlib.Path(__file__).resolve().parent.parent
sql="""WITH chosen AS (
 SELECT id,fetcher_identity,fetched_at,content_type,
 row_number() OVER (PARTITION BY fetcher_identity ORDER BY fetched_at DESC) AS rn
 FROM raw_record WHERE content_type IN ('application/json','text/csv')
) SELECT c.fetcher_identity,c.fetched_at,c.content_type,
 encode(r.payload_bytes,'base64') AS payload
 FROM chosen c JOIN raw_record r ON r.id=c.id
 WHERE (c.fetcher_identity='lightspeed-insights' OR c.rn<=4)
 ORDER BY c.fetcher_identity,c.fetched_at;"""
# SQL bytes intentionally stay in memory; stdout is the sanitised structural summary.
cmd=['docker','exec','-i','goldys-prod-postgres-1','sh','-c',
 'PGOPTIONS="-c default_transaction_read_only=on -c statement_timeout=8000 -c lock_timeout=1000" psql -X -q --csv -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"']
csv.field_size_limit(10_000_000)
data=subprocess.check_output(cmd,input='BEGIN READ ONLY;'+sql+'ROLLBACK;',text=True)
summaries=[]
for record in csv.DictReader(io.StringIO(data)):
    raw=base64.b64decode(record['payload']); info={k:record[k] for k in ['fetcher_identity','fetched_at','content_type']}
    paths=collections.defaultdict(collections.Counter)
    arrays=collections.defaultdict(list)
    def walk(x,path='$',depth=0):
        paths[path][type(x).__name__]+=1
        if depth>6:return
        if isinstance(x,dict):
            for k,v in x.items():walk(v,path+'.'+k,depth+1)
        if isinstance(x,list):
            arrays[path].append(len(x))
            for v in x[:200]:walk(v,path+'[]',depth+1)
    if record['content_type']=='application/json':
        obj=json.loads(raw);walk(obj)
        info['fields']={k:dict(v) for k,v in sorted(paths.items())};info['array_lengths']=dict(arrays)
        if isinstance(obj,dict) and isinstance(obj.get('totalCount'),(int,float)):info['source_totalCount']=obj['totalCount']
        attachment=obj.get('attachment',{}) if isinstance(obj,dict) else {}
        text=attachment.get('data') if isinstance(attachment,dict) else None
    else:text=raw.decode('utf-8-sig')
    if text:
        rows=list(csv.reader(io.StringIO(text)))
        if rows:
            headers=rows[0];body=rows[1:]
            info['csv_headers']=headers;info['csv_rows']=len(body)
            info['csv_missing']={h:sum(i>=len(r) or not r[i].strip() for r in body) for i,h in enumerate(headers)}
            info['csv_distinct_counts']={h:len({r[i] for r in body if i<len(r) and r[i].strip()}) for i,h in enumerate(headers) if any(s in h.lower() for s in ['id','date','time','code','quantity','invoice'])}
            dates={h:sorted({r[i] for r in body if i<len(r) and r[i].strip()}) for i,h in enumerate(headers) if 'date' in h.lower()}
            # Only ISO calendar dates are exported; other values remain private.
            import re
            info['iso_date_ranges']={h:[v[0],v[-1]] for h,v in dates.items() if v and all(re.fullmatch(r'\d{4}-\d{2}-\d{2}',s) for s in v)}
    summaries.append(info)
(ROOT/'evidence/payload-shapes.json').write_text(json.dumps(summaries,indent=2)+'\n')
print('Sanitised structural summaries for',len(summaries),'retained payloads.')
