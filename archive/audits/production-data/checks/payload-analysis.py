#!/usr/bin/env python3
"""Aggregate retained non-PDF data; suppress all scalar identity/personnel values.
Exports counts, field names, ISO business dates and reconciliation deltas only.
"""
import base64,collections,csv,datetime,decimal,hashlib,io,json,pathlib,re,subprocess
ROOT=pathlib.Path(__file__).resolve().parent.parent
sql="""WITH latest AS (SELECT id FROM ingestion_run WHERE connector_name='ctb-revenue'
 ORDER BY started_at DESC LIMIT 1)
SELECT source_system,fetcher_identity,fetched_at,payload_sha256,payload_byte_length,encode(payload_bytes,'base64') AS payload
FROM raw_record WHERE content_type<>'application/pdf' AND (
 source_system='LIGHTSPEED' OR fetcher_identity='ctb-invoices' OR ingestion_run_id IN (SELECT id FROM latest))
ORDER BY fetched_at;"""
cmd=['docker','exec','-i','goldys-prod-postgres-1','sh','-c',
 'PGOPTIONS="-c default_transaction_read_only=on -c statement_timeout=8000 -c lock_timeout=1000" psql -X -q --csv -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"']
csv.field_size_limit(20_000_000)
data=subprocess.check_output(cmd,input='BEGIN READ ONLY;'+sql+'ROLLBACK;',text=True)
out={'scope':'all retained Lightspeed and CTB CSV; latest CTB web pull only', 'lightspeed':[], 'invoice_csv':[]}
out['sampled_raw_integrity']={'payloads':0,'digest_mismatches':0,'byte_length_mismatches':0}
ajax=collections.defaultdict(list)
D=decimal.Decimal
def money(v):return D(str(v or '0').replace('$','').replace(',','').strip() or '0')
csvsets=[]
latest_lines={}
global_suppliers=collections.defaultdict(set)
for r in csv.DictReader(io.StringIO(data)):
 raw=base64.b64decode(r['payload']);fetcher=r['fetcher_identity']
 out['sampled_raw_integrity']['payloads']+=1
 out['sampled_raw_integrity']['digest_mismatches']+=hashlib.sha256(raw).hexdigest()!=r['payload_sha256']
 out['sampled_raw_integrity']['byte_length_mismatches']+=len(raw)!=int(r['payload_byte_length'])
 if fetcher=='ctb-invoices':
  rows=list(csv.DictReader(io.StringIO(raw.decode('utf-8-sig')))); headers=list(rows[0]) if rows else []
  a={'fetched_at':r['fetched_at'],'headers':headers,'rows':len(rows),'blank_descriptions':0,'quantity_fallback':0,'missing_unit_cost':0,'invalid_required_date_rows':0,'invalid_required_line_total':0}
  keys=collections.defaultdict(set);su=collections.defaultdict(set);linekeys=collections.Counter();positions={}
  for row in rows:
   if not (row.get('StockDescription') or '').strip():a['blank_descriptions']+=1;continue
   try:datetime.date.fromisoformat(row.get('Date',''))
   except ValueError:
    try:datetime.datetime.strptime(row.get('Date',''),'%d/%m/%Y')
    except ValueError:a['invalid_required_date_rows']+=1
   try:money(row.get('LineTotalExTax'))
   except decimal.InvalidOperation:a['invalid_required_line_total']+=1
   valid=False
   for token in (row.get('LineQuantity') or '').strip().split():
    try:D(token);valid=True;break
    except decimal.InvalidOperation:pass
   a['quantity_fallback']+=not valid
   a['missing_unit_cost']+=not bool((row.get('LineUnitCostExTax') or '').strip())
   inv=(row.get('Invoice') or '').strip();su[inv].add(row.get('Supplier'));global_suppliers[inv].add(row.get('Supplier'));keys[inv].add(row.get('Date'))
   sig=tuple(row.get(k) for k in ['StockCode','StockDescription','LineQuantity','LineTotalExTax'])
   linekeys[(inv,sig)]+=1
   positions.setdefault(inv,[]).append(sig)
  a['invoice_keys']=len(su);a['invoice_numbers_with_multiple_suppliers']=sum(len(v)>1 for v in su.values())
  a['invoice_numbers_with_multiple_dates']=sum(len(v)>1 for v in keys.values())
  a['repeated_line_excess']=sum(v-1 for v in linekeys.values() if v>1)
  out['invoice_csv'].append(a);csvsets.append(positions)
  if not a['invalid_required_line_total'] and not a['invalid_required_date_rows']:
   latest_lines.update({k:len(v) for k,v in positions.items()})
 elif r['source_system']=='LIGHTSPEED':
  obj=json.loads(raw);text=obj.get('attachment',{}).get('data','');rows=list(csv.DictReader(io.StringIO(text)))
  valid=[x for x in rows if (x.get('Reconciliation Date') or '').strip()]
  dates=collections.defaultdict(lambda:[D(0),D(0),0]);ids=collections.defaultdict(set)
  for x in valid:
   date=x['Reconciliation Date'];dates[date][0]+=money(x.get('Total Inc Tax'))+money(x.get('Total Adjustment Inc Tax'))
   dates[date][1]+=money(x.get('Total Tax'))+money(x.get('Total Adjustment Tax'));dates[date][2]+=1
   if x.get('Sale Number'):ids[date].add(x['Sale Number'])
  a={'fetched_at':r['fetched_at'],'headers':list(rows[0]) if rows else [],'data_rows':len(valid),
     'transaction_number_rows':sum(bool(x.get('Sale Number')) for x in valid),
     'sale_number_distinct_by_date':{k:len(v) for k,v in ids.items()},'business_dates':sorted(dates),
     'negative_total_rows':sum(money(x.get('Total Inc Tax'))<0 for x in valid),
     'nonzero_adjustment_rows':sum(money(x.get('Total Adjustment Inc Tax'))!=0 for x in valid)}
  out['lightspeed'].append(a)
 else:
  obj=json.loads(raw);rows=obj.get('data',[])
  if isinstance(rows,list):ajax[fetcher].extend(rows)
out['csv_order_comparisons']=[]
out['all_csv_invoice_numbers_multiple_suppliers']=sum(len(v)>1 for v in global_suppliers.values())
canonical_sql="SELECT invoice_number,source_record_ref FROM canonical_invoice_line WHERE superseded_at IS NULL;"
canonical_data=subprocess.check_output(cmd,input='BEGIN READ ONLY;'+canonical_sql+'ROLLBACK;',text=True)
tail=0;affected=set()
for row in csv.DictReader(io.StringIO(canonical_data)):
 inv=row['invoice_number'];ref=row['source_record_ref']
 try:seq=int(ref.rsplit(':',1)[1])
 except ValueError:continue
 if inv in latest_lines and seq>latest_lines[inv]:tail+=1;affected.add(inv)
out['current_lines_beyond_latest_parseable_csv_length']={'lines':tail,'invoice_keys':len(affected)}
for prev,cur in zip(csvsets,csvsets[1:]):
 same=set(prev)&set(cur)
 out['csv_order_comparisons'].append({'common_invoices':len(same),'same_multiset_different_order':sum(collections.Counter(prev[k])==collections.Counter(cur[k]) and prev[k]!=cur[k] for k in same),
 'invoices_with_changed_line_count':sum(len(prev[k])!=len(cur[k]) for k in same)})
out['latest_ctb_web']={}
for fetcher,rows in ajax.items():
 a={'rows':len(rows),'fields':sorted({k for row in rows for k in row}),'null_counts':{k:sum(row.get(k) is None for row in rows) for k in sorted({k for row in rows for k in row})}}
 if fetcher=='ctb-invoices-ajax':
  ids=collections.defaultdict(set);sup=collections.defaultdict(set)
  for x in rows:ids[x.get('invoiceNo')].add(x.get('invoiceId'));sup[x.get('invoiceNo')].add(x.get('supplierId'))
  a['distinct_invoice_ids']=len({x.get('invoiceId') for x in rows});a['distinct_supplier_ids']=len({x.get('supplierId') for x in rows})
  a['invoice_numbers_multiple_source_ids']=sum(len(v)>1 for v in ids.values());a['invoice_numbers_multiple_supplier_ids']=sum(len(v)>1 for v in sup.values())
 if fetcher=='ctb-sale-recipe-links':
  a['distinct_sale_stock_codes']=len({x.get('saleItemStockCode') for x in rows});a['distinct_nonnull_recipe_ids']=len({x.get('recipeId') for x in rows if x.get('recipeId') not in [None,0,'', '0']})
  a['nonzero_recipe_link_rows']=sum(x.get('recipeId') not in [None,0,'','0'] for x in rows)
  a['active_recipe_rows']=sum(x.get('isRecipeActive') is True for x in rows)
  codes=collections.defaultdict(set)
  for x in rows:
   if x.get('recipeId') not in [None,0,'','0']:codes[x.get('saleItemStockCode')].add(x.get('recipeId'))
  a['sale_codes_multiple_recipe_ids']=sum(len(v)>1 for v in codes.values())
 if fetcher=='ctb-revenue':
  a['revenue_rows']=sum('revenueId' in x for x in rows);a['sale_item_rows']=sum('stockDescription' in x for x in rows)
  a['sale_dates_present']=sum(x.get('saleDate') not in [None,0,''] for x in rows if 'stockDescription' in x)
 out['latest_ctb_web'][fetcher]=a
(ROOT/'evidence/payload-analysis.json').write_text(json.dumps(out,indent=2)+'\n')
print('Aggregate-only payload analysis written; sampled',len(list(csv.DictReader(io.StringIO(data)))),'non-PDF payloads.')
