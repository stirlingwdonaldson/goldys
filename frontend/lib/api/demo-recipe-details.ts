import type { RecipeDetails, RecipeEvidence } from "./types";
export function demoRecipeDetails(recipeId: number): RecipeDetails | null {
 if (![3,8].includes(recipeId)) return null;
 const evidence: RecipeEvidence = { rawRecordId: null, sourceSystem: "CTB", fetcher: "ctb-recipe-definition", endpoint: "/RecipeBook/GetRecipeByRecipeId", sourceRecordRef: null, capturedAt: "2026-10-20T02:00:00Z", ingestionRunId: null, sha256: null };
 return { recipeId, name: recipeId===3?"Cheeseburger":"Burger sauce",costSelection:"Latest complete CTB composition; source figures remain separate from POS inputs",
 sections:[{recipeId,section:"HEADER",successful:true,notice:null,evidence,fields:[
  {path:"recipeDesc",value:"Mix thoroughly.\nRest for 10 minutes.",type:"TEXT"},
  {path:"isActive",value:"true",type:"BOOLEAN"},
  {path:"recipeHandyHints",value:"",type:"TEXT"}]},
 {recipeId,section:"EQUIPMENT",successful:true,notice:null,evidence:{...evidence,endpoint:"/RecipeEquipment/SearchRecipeEquipments"},fields:[{path:"data",value:"[]",type:"EMPTY_ARRAY"}]},
 {recipeId,section:"NUTRITION",successful:true,notice:null,evidence:{...evidence,endpoint:"/RecipeBook/GetRecipeNutritionInformation"},fields:[{path:"data.energy",value:"0",type:"NUMBER"}]}],
 latestAttempts:[{recipeId,section:"DIETARY",successful:false,notice:"Latest collection failed; no dietary observation available",fields:[],evidence:{...evidence,capturedAt:"2026-10-21T02:00:00Z"}}],
 usedIn:recipeId===8?[{recipeId:3,name:"Cheeseburger",available:true}]:[],
 uses:recipeId===3?[{recipeId:8,name:"Burger sauce",available:true}]:[],compositionSources:[evidence] };
}
