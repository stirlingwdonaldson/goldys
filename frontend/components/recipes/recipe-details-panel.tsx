"use client";
import Link from "next/link";
import { useApi } from "@/lib/demo-mode";
import { useQuery } from "@tanstack/react-query";
import type { RecipeEvidence, RecipeSection, RecipeReference, RecipeField } from "@/lib/api/types";
import { ErrorState } from "@/components/states/error-state";
import { LoadingState } from "@/components/states/loading-state";

const labels: Record<string,string>={ CATALOGUE_ENTRY:"Catalogue listing",MAJOR_FLAVOUR:"Major flavours",MAJOR_INGREDIENT:"Major ingredients",COOKING_METHOD:"Cooking methods",SECTION:"Sections",DISH_TYPE:"Dish types",HEADING:"Headings",isActive:"Active",isLocked:"Locked", HEADER:"Recipe details and method",STOCKS:"Direct ingredient details",USAGES:"Sub-recipe usage details",EQUIPMENT:"Equipment",EQUIPMENT_OPTIONS:"Available equipment choices",NUTRITION:"Nutrition",DIETARY:"Dietary selections",ALLERGENS:"Allergen selections",INGREDIENT_METHODS:"Ingredient methods",LEGACY_COST:"Legacy CTB costs",POS_PRICE_INPUT:"POS price and cost evidence",recipeDesc:"Method",recipeHandyHints:"Handy hints",isChecked:"Selected in CTB",noOfPortion:"Number of portions",portionIncludingLabour:"Cost per portion including labour",recipeId:"CTB recipe ID"};
function label(path:string){
 return path.replace(/^data\.?/,"").split(".").map(part=>labels[part]??part.replace(/([a-z])([A-Z])/g,"$1 $2").replace(/_/g," ").replace(/\[(\d+)\]/g,(_,n)=>` ${Number(n)+1}`)).join(" · ").replace(/^./,c=>c.toUpperCase())||"Recorded entries";
}
function when(value:string){return new Date(value).toLocaleString("en-AU",{timeZone:"Australia/Melbourne"});}
function stale(value:string,limit:number){return Date.now()-new Date(value).getTime()>limit*1000;}
function Source({evidence,fields=[]}:{evidence:RecipeEvidence;fields?:RecipeField[]}){
 return <details className="rounded border p-3 text-xs"><summary className="cursor-pointer font-medium">Source: {evidence.sourceSystem} · {when(evidence.capturedAt)}</summary>
  <dl className="mt-2 grid gap-2"><div><dt>Import</dt><dd>{evidence.fetcher}</dd></div><div><dt>Endpoint</dt><dd className="break-all">{evidence.endpoint}</dd></div>
  {evidence.sourceRecordRef?<div><dt>Source reference</dt><dd className="break-all">{evidence.sourceRecordRef}</dd></div>:null}
  {evidence.ingestionRunId?<div><dt>Ingestion run</dt><dd>{evidence.ingestionRunId}</dd></div>:null}
  {evidence.entityRefs?.length?<div><dt>Contributing source entities</dt><dd className="max-h-40 overflow-auto break-all">{evidence.entityRefs.join(", ")}</dd></div>:null}
  {evidence.sha256?<div><dt>Capture SHA-256</dt><dd className="break-all">{evidence.sha256}</dd></div>:null}</dl>
  {evidence.rawRecordId?<Link className="mt-2 inline-block underline" href={`/data?layer=raw&id=${encodeURIComponent(evidence.rawRecordId)}`}>Inspect retained response</Link>:<p className="mt-2 text-muted-foreground">Raw evidence access is restricted or unavailable.</p>}
  {fields.length?<details className="mt-3"><summary className="cursor-pointer">Field paths in the retained response</summary><ul className="mt-2 space-y-1">{fields.map(field=><li className="break-all" key={field.path}><code>{field.path}</code></li>)}</ul><p className="mt-2">Text is extracted as plain text; the retained response preserves its original representation.</p></details>:null}
 </details>;
}
function Relationships({title,items}:{title:string;items:RecipeReference[]}){
 return <div><h3 className="font-medium">{title}</h3>{items.length?<ul className="mt-1 space-y-1">{items.map((item,index)=><li key={`${item.recipeId}:${index}`}><Link className="underline" href={`/recipes/${item.recipeId}`}>{item.name??"Unnamed recipe"} · #{item.recipeId}</Link>{!item.available?" · Details not received":""}</li>)}</ul>:<p className="text-muted-foreground">No links recorded in captured recipe data.</p>}</div>;
}
function fieldValue(section:string,field:RecipeField):string {
 if(field.value==null)return "Not supplied";
 if(field.type==="EMPTY_ARRAY"||field.type==="EMPTY_OBJECT")return "No entries recorded";
 if(field.value==="")return "Empty in CTB";
 if(field.type==="BOOLEAN")return field.value==="true"?"Yes":"No";
 if(section==="NUTRITION"&&field.value==="0")return "0 (CTB reported zero)";
 return field.value;
}
function Section({section,limit}:{section:RecipeSection;limit:number}){
 return <details className="rounded border p-4" open={section.section==="HEADER"}><summary className="cursor-pointer font-medium">{labels[section.section]??label(section.section)}{stale(section.evidence.capturedAt,limit)?" · Refresh overdue":""}</summary>
  <div className="mt-3 flex flex-col gap-3">{section.notice?<p className="text-muted-foreground">{section.notice}</p>:null}
  {section.section==="NUTRITION"?<p className="text-muted-foreground">Values as reported by CTB. Its screen shows per-serving and per-100g figures, but this response does not identify the basis. Zero does not establish nutritional composition.</p>:null}
  {section.section==="ALLERGENS"?<p className="text-muted-foreground">Unchecked means not selected in CTB; it does not establish that an allergen is absent.</p>:null}
  <dl className="divide-y">{section.fields.map(field=><div className="grid gap-1 py-2 sm:grid-cols-2" key={field.path}><dt title={`Source field: ${field.path}`} className="text-muted-foreground">{label(field.path)}</dt><dd className="whitespace-pre-wrap break-words">{fieldValue(section.section,field)}</dd></div>)}</dl>
  <Source evidence={section.evidence} fields={section.fields}/></div>
 </details>;
}
export function RecipeDetailsPanel({recipeId}:{recipeId:number}){
 const api=useApi();
 const result=useQuery({queryKey:["getRecipeDetails",recipeId],queryFn:({signal})=>api.getRecipeDetails(recipeId,{signal})});
 if(result.isPending)return <LoadingState rows={4}/>;
 if(result.isLoadingError)return <ErrorState title="Couldn't load recipe information" message={result.error.message} onRetry={result.refetch}/>;
 const data=result.data;
 if(!data)return <p role="status">Recipe details have not been received. This ID may be missing or unavailable in the captured catalogue.</p>;
 return <div className="flex flex-col gap-4">
  <h2 className="text-lg font-medium">{data.name??"Unnamed recipe"} · CTB #{data.recipeId}</h2>
  <p className="text-sm text-muted-foreground">{data.costSelection}</p>
  {data.collectionEnabled===false?<p role="status" className="text-sm">Complete recipe collection is disabled. Displayed observations will not refresh through this job.</p>:null}
  {data.collection?<p className="text-xs text-muted-foreground">Latest CTB connector attempt: {data.collection.lastRunAt?when(data.collection.lastRunAt):"Not recorded"} · {data.collection.status}. This run covers multiple datasets; section capture times below establish recipe freshness.</p>:null}
  <div className="grid gap-4 sm:grid-cols-2"><Relationships title="Uses sub-recipes" items={data.uses}/><Relationships title="Used in" items={data.usedIn}/></div>
  {data.latestAttempts.map(section=><p className="text-xs text-muted-foreground" key={section.section}>Last attempt: {labels[section.section]??label(section.section)} · {when(section.evidence.capturedAt)} · {section.successful?"Received":"Failed; previous successful values retained where available"}{!section.successful?` · ${section.notice??"Unavailable"}`:""}</p>)}
  {["HEADER","STOCKS","USAGES","EQUIPMENT","NUTRITION","DIETARY","ALLERGENS","INGREDIENT_METHODS","MAJOR_FLAVOUR","MAJOR_INGREDIENT","COOKING_METHOD","SECTION","DISH_TYPE","HEADING"].filter(name=>!data.sections.some(s=>s.section===name)).map(name=><p className="text-sm text-muted-foreground" key={`missing-${name}`}>{labels[name]??label(name)}: No successful observation received.</p>)}
  {data.sections.map((section,index)=><Section key={`${section.section}:${section.evidence.rawRecordId??section.evidence.capturedAt}:${index}`} section={section} limit={data.freshnessLimitSeconds??36*60*60}/>)}
  {data.compositionSources.length?<details className="rounded border p-4"><summary className="cursor-pointer font-medium">Calculation input sources</summary><p className="my-2 text-sm text-muted-foreground">The estimate uses the direct ingredient lines, explicit sub-recipe usages and each linked recipe observation. Source contributions and recomputed amounts remain separate.</p>{data.compositionSources.map(e=><Source key={e.rawRecordId??e.endpoint} evidence={e}/>)}</details>:null}
 </div>;
}
