# Recipe diagrams, first slice

Two read-only React Flow diagrams reuse the existing FlowCanvas, step nodes and
column/tree layouts. No source collection, recipe editing or new matching rules.

- Recipes: explicit folder hierarchy with recipes attached by folder ID. Root folders
  start expanded; branches can expand/collapse, with expand-all available. Recipe
  nodes open existing detail URLs and preserve the list context.
- Recipe detail: current recipe → direct stock ingredients and explicit sub-recipe
  usages. Each source line remains distinct; signed quantities, units and source
  contributions remain visible. Sub-recipes link to their own ingredient diagrams.
  There is no recursive composition fetch or new cost calculation.
- Tables and recipe prose remain available alongside the diagrams.

## Data boundary

The existing catalog API supplies folder IDs/parents, while the recipe list previously
exposed only folder names. Add nullable `folderId` to RecipeCosting from the complete
resolved definition already loaded by RecipeDefinitionReadAdapter.status. No migration,
canonical access in a controller, new endpoint or new permission grant is needed.
Missing/unknown folder identity is shown under “Folder not linked”; duplicate display
names never establish identity. Invalid/cyclic ancestry is detached rather than recursed.

Existing real CTB header/folder captures establish folderId and parentId semantics;
RecipeDefinitionSelectionTest verifies the additive field using captured recipe #314.
The existing real recipe browser fixtures verify measured pudding/custard relationships.
Demo contracts carry matching explicit folder IDs.

## Interaction

Keep large trees usable by collapsing branches, with zoom/pan controls and a lower
minimum zoom for these diagrams only. Shared FlowCanvas gives actionable nodes button
semantics and activates folder/recipe nodes with Enter or Space, preserving the existing
mouse click behavior. No connection editing is enabled.

Deploy backend and frontend together to obtain folder ownership. An older backend
continues to load, but its missing folder IDs appear as unlinked recipes.
