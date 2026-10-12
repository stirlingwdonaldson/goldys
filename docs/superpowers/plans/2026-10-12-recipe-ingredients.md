# Recipe Ingredients (the "recipes.ingredients" slice) Implementation Plan

**Goal:** Make CTB recipe ingredient lines first-class — canonical → resolved read model → REST →
Ask Goldy's tool → Recipes UI. This is the deferred "recipes.ingredients" slice of the food-cost
goal, a direct sequel to Slice D (recipe composition).

**Architecture:** Follow `docs/architecture/adding-a-domain.md` and mirror the Slice D shapes
(`CanonicalRecipeCost*`, `ResolvedRecipe*`, `RecipeProjector`, `RecipeMetricsQuery`,
`GetRecipeCostingTool`). Single-source (CTB): no reconciliation engine or override table.

**Spec:** `docs/superpowers/specs/2026-10-12-recipe-ingredients-design.md`

## Global constraints

- Flyway migrations only (`hibernate.ddl-auto: validate`); never edit an applied migration. Next
  versions are `V49` and `V50`.
- `raw_record` is immutable (never written); canonical is append-only with supersession.
- Business consumers (`api`/`reporting`) read resolved via `semantic`, never canonical/raw.
- Money/quantity `numeric(14,4)`; null when undefined, never a fabricated zero.
- CTB JSON contains literal `\u0000` escapes; parsers strip them.
- `recipes.ingredients` seeded `ALL × OWNER` only; no BOH/FOH guesses.
- Checks: `cd backend && ./gradlew test spotlessCheck`; `cd frontend && bun run typecheck && bun run
  lint && bun run test`. Work on a branch; never commit to `main`.

## Tasks

1. **Migrations** — `V49__recipe_ingredients.sql` (canonical + resolved; resolved uses a surrogate
   `id uuid` PK because the natural key has nullable columns) and
   `V50__seed_recipe_ingredients_permission.sql`.
2. **Canonical set** — `RecipeIngredientInput`, `CanonicalRecipeIngredient`,
   `CanonicalRecipeIngredientRepository`, `CanonicalRecipeIngredientService`,
   `RecipeIngredientRecorded`, `RecipeIngredientIngest`, `CanonicalRecipeIngredientQuery`,
   `RecipeIngredientView`.
3. **Parser** — `CtbRecipeIngredientsParser.parse(json, rawRecordId, parentRecipeId)`; envelope-aware,
   strips `\u0000`, defensive numeric parse, dedupes exact duplicates, non-JSON throws
   `CONNECTOR_SCHEMA_MISMATCH`.
4. **Replay** — `RecipeIngredientCanonicalizationRunner`
   (`app.recipe-ingredients-canonicalize`, `@Order(HIGHEST_PRECEDENCE)`); reads
   `rawLedger.rawPayloadsForFetcherWithRef("CTB", "ctb-recipe-ingredients")`, derives
   `parentRecipeId` from `source_record_ref`. Extend `RawLedgerQuery` with `rawPayloadsForFetcherWithRef`.
5. **Projection** — `ResolvedRecipeIngredient` + repository, `RecipeIngredientProjector.recomputeAll()`,
   `RecipeIngredientProjectionListener`, registered in `StartupProjectionSeeder`.
6. **Semantic + application + API** — `RecipeIngredientMetricsQuery` (leaf) + `RecipeIngredient`;
   `ResolvedRecipeIngredientQuery` (reconciliation); `RecipeIngredientReportingService`
   (`recipes.ingredients` READ); `RecipeIngredientController` (`GET /api/recipes/{id}/ingredients`).
7. **Tool** — `GetRecipeIngredientsTool` + `GetRecipeIngredientsInput` + `ToolId.GET_RECIPE_INGREDIENTS`;
   register in the `ConversationalEvalTest` catalogue list.
8. **Permissions + boundaries** — seed `recipes.ingredients`; extend `ArchitectureBoundariesTest`
   (controller/tool/semantic rules) and `PermissionBoundaryIntegrationTest`.
9. **Frontend** — `RecipeIngredient` type + `getRecipeIngredients` on the `Api` (live + demo), and
   the ingredient-breakdown detail panel on the Recipes page.
10. **Docs** — `source-access.md` (ingredients no longer raw-only; sub-recipe roll-up and freshness
    non-goals), `matching-and-identity.md` (line identity + duplicate quirk), new dated spec+plan.
