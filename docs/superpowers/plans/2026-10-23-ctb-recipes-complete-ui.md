# CTB recipe detail, evidence and relationship implementation

Approved scope: complete read-only recipe display, exact source tracing, plain-text method
extraction, latest compatible observations and bidirectional recipe navigation. Extend the
existing connector and projections; no source writes or new connector/service dependency.

## Implementation

1. Keep every CTB catalogue entry visible, including missing-detail shells. Fetch the list once
   per enabled connector run. Keep source list, cost and detail observation times distinct.
2. Capture each optional response raw before parsing. Preserve section-specific success/failure
   chains with bitemporal versions. Normalize scalar business fields and empty states, retaining
   their field paths and capture metadata. Rebuild resolved sections from canonical state.
3. Extract HTML-bearing JSON strings through the JDK parser into plain text, preserving method
   instructions, paragraph/list boundaries and entity characters. Never insert source HTML into
   the DOM. Drop script/style content, preserve original bytes in the ledger.
4. Select complete compositions atomically; compare equivalent legacy costs by actual capture
   time. Keep legacy flattened ingredients and POS modal/name associations explicitly qualified.
5. Expose permission-checked `/api/recipes/{id}/details`; keep existing list/composition/ingredient
   APIs compatible. Carry endpoint, source entity/field references, SHA-256, raw ID and run ID.
   Raw references require connectors READ. Recursive cost inputs include descendant evidence.
6. Use native React Query on current main, including AbortSignal contracts and demo fixtures.
   Stable `/recipes/{id}` pages link to children and parents. Display all normalized business
   fields in expandable sections, separate CTB/POS/calculated figures, and distinguish zero,
   unchecked, empty, missing, stale, unsupported and failed captures.
7. Verify the real optional survey, complete-snapshot history/removals, replay, failures,
   compatible cost precedence, source permissions and relationship navigation. Run full gates
   and browser checks. Live source validation writes only local coverage output.

## Concurrency

Worktree: `/tmp/goldys-ctb-recipe-integration`, branch `codex/ctb-recipe-integration`.
Current main includes #208–#210. Preserve #210 ingredient qualifications and native React Query.
V100 belongs to #210; unshipped recipe snapshots use V101, sections/evidence V102.

## Evidence and limits

The supplied 23 October survey includes all 670 catalogue IDs on five endpoints (3350 unique
recipe/endpoint pairs, 3372 responses including repeats). It establishes 24 nonempty method
fields, dietary selections on two recipes, ten dietary options, 23 allergen options and 17
pieces of equipment. Recipe facts/multiple-line ingredient methods are only sampled. No
populated equipment or ingredient-method schema is invented. Nutrition basis cannot be inferred
from all-zero values. Archived/deleted recipes and other outlets were not enumerated by this
survey. Credentials are not fixtures. Sanitised captures are not original ledger evidence.

## Release boundary

Collection/replay remain disabled by default. Push, deployment, production migration, enabling
collection and production backfill are separate authorised operations. No editing source recipes,
automatic dietary inference, unrelated provenance redesign or graph visualisation is included.
