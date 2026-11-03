# Review Classification — JSON Structure

**Status:** Draft — for review
**Date:** 2026-10-14
**Scope:** The JSON contract for classifying OpenTable/Google guest reviews with the
existing classification setup. This spec defines the *shape* of a classified review — its
deterministic facts and its Jev-inferred classification — plus the vocabulary (sentiment
labels, aspects, source) and the classifier request/response. It does **not** design the
full pipeline (canonical review storage, the `ReviewClassifier` port implementation,
endpoints, or reporting).

## 1. Objective

OpenTable/Google reviews arrive raw-only today (`OpenTableReview`, the `opentable-reviews`
connector). The reviews slice (`canonical_review`, `canonical_guest` population) is recorded
future work. This spec defines the JSON document a review classification produces, so that:

- a review's **deterministic facts** (identity, dates, ratings, free text) are captured
  exactly as the source provided them; and
- its **inferred classification** (overall + per-aspect sentiment, predicted rating, aspect
  tags) is captured separately, produced by the Jev classifier.

The document is the unit a review flows through classification as, and the shape that any
downstream storage, query, or reporting view reads.

## 2. Governing boundary: facts vs. inference

The document has two halves with one hard rule: **everything outside `classification` is
written in from data (the source CSV / raw ledger), never inferred; everything inside
`classification` is Jev-inferred, never back-filled from the source.**

| Block | Origin | Notes |
|---|---|---|
| `reviewId`, `source`, `guest`, `visitedAt`, `reviewedAt` | deterministic (data) | parsed straight from the CSV |
| `ratings` | deterministic (data) | the source's star/yes-no ratings, `null` where blank |
| `text` | deterministic (data) | `comment`, `privateNote`, `tags`, `keywords` |
| `classification` | Jev-inferred | `sentiment`, `predictedRating`, `aspects` — the only ML output |

The Python `product-classify-service` stays untouched: it is data-agnostic (`{texts:[…]}` →
`{vectors:[…]}`) and knows nothing about reviews. Java assembles the deterministic facts from
the parsed review, builds feature text, and stamps the `classification` object with whatever
Jev returns.

## 3. The classified-review document

Annotated example, using the mixed review from `reviews_sample.csv` (2 stars, "Disinterested
wait staff…") to show per-aspect sentiment:

```jsonc
{
  // ── identity & provenance (deterministic) ──────────────
  "reviewId": "OT-140126-39943-230066535913",   // stable key; OT-… (OpenTable) / RT-… (Google)
  "source": "OpenTable",                        // enum: "OpenTable" | "Google"
  "guest": {
    "id": "6ac9e28dea382244a46e5053",           // Guest ID (reviews carry a clean one)
    "name": "Mary"
  },

  // ── temporal (deterministic) ───────────────────────────
  "visitedAt":  "2026-10-11T12:30:00Z",        // Visit Date
  "reviewedAt": "2026-10-12T12:36:59Z",        // Review Date

  // ── given ratings (deterministic; null where the export left them blank) ──
  "ratings": {
    "overall": 2,                              // 1..5, null if absent (Google rows often blank)
    "food": 2,
    "service": 1,
    "ambience": 2,
    "value": 2,
    "noiseLabel": "Quiet",                     // free text ("Quiet", "Do not recall", …)
    "wouldRecommend": false                    // Yes/No → bool
  },

  // ── free text (deterministic) ──────────────────────────
  "text": {
    "comment": "Disinterested wait staff. Plonked food down…",   // primary classification input
    "privateNote": "Girl serving at the bar friendly…",          // secondary, often richer
    "tags": [],                                 // Tags Added by Diner (split into an array)
    "keywords": []                              // Keywords
  },

  // ── classification output (Jev-inferred) ───────────────
  "classification": {
    "sentiment": { "label": "NEGATIVE", "confidence": 0.94 },   // overall verdict
    "predictedRating": 1.8,                     // 1..5 float, derived from text
    "aspects": [                                // sparse — only aspects detected in the text
      { "aspect": "service",  "label": "NEGATIVE", "confidence": 0.97 },
      { "aspect": "food",     "label": "NEGATIVE", "confidence": 0.71 },
      { "aspect": "ambience", "label": "NEUTRAL",  "confidence": 0.62 }
    ]
  }
}
```

Conventions (matching the repo): camelCase keys; timestamps are ISO-8601 UTC strings (the
`Instant` serialization of the parsed review); enums are `UPPER_SNAKE` strings, the same
style as `AssignmentStatus.NEEDS_REVIEW` / `AssignmentSource.MANUAL`.

## 4. Vocabulary

### 4.1 Source

`"OpenTable"` | `"Google"` — the `Source` column. OpenTable rows carry sub-ratings; Google
rows in the export leave them blank (which is what `predictedRating` fills).

### 4.2 Sentiment label

`"POSITIVE"` | `"NEUTRAL"` | `"NEGATIVE"`, each paired with a `confidence` in `[0, 1]`. The
label enum is extendable to a 5-point scale later without changing the shape.

### 4.3 Aspects

Lowercase taxonomy keys. Start with the sub-rating dimensions the export already gives us,
plus `wait_time`:

`food` · `service` · `ambience` · `value` · `noise` · `wait_time`

`aspects` is sparse: only aspects actually detected in the text appear. A mixed review
("great food, slow service") yields two entries with opposite labels rather than one
flattened verdict. The final list is an open question (see §7) — it should be driven by the
`Tags Added by Diner` / `Keywords` columns once a real sample is inspected.

## 5. CSV → JSON field mapping

| JSON field | Source column | Transform |
|---|---|---|
| `reviewId` | `Review ID` | identity, as-is |
| `source` | `Source` | as-is |
| `guest.id` | `Guest ID` | as-is |
| `guest.name` | `Guest Name` | as-is |
| `reviewedAt` | `Review Date` | parsed → ISO-8601 UTC |
| `visitedAt` | `Visit Date` | parsed → ISO-8601 UTC |
| `ratings.overall` | `Overall Rating` | int 1–5, blank → `null` |
| `ratings.food` | `Food` | int 1–5, blank → `null` |
| `ratings.service` | `Service` | int 1–5, blank → `null` |
| `ratings.ambience` | `Ambience` | int 1–5, blank → `null` |
| `ratings.value` | `Value` | int 1–5, blank → `null` |
| `ratings.noiseLabel` | `Noise` | free text, blank → `null` |
| `ratings.wouldRecommend` | `Would Recommend` | `Yes`/`No` → bool |
| `text.comment` | `Comment` | blank → `null` |
| `text.privateNote` | `Private Note from Guest` | blank → `null` |
| `text.tags` | `Tags Added by Diner` | split → `string[]` |
| `text.keywords` | `Keywords` | split → `string[]` |

`Noise Rating` (a sub-rating on the noise dimension) is omitted from the first cut: it is a
rating of a *free-text* `Noise` field whose meaning is unverified, and folding it in would
muddle the `ratings` block. It stays available in the raw ledger and can be added once its
semantics are confirmed.

## 6. Jev classifier contract

The existing `ProductClassifier` (Jev `Choice`) returns a single category id + probabilities,
which cannot express sentiment + aspects + rating. Reviews therefore need a sibling port,
`ReviewClassifier`, with its own request/response:

```jsonc
// request — deterministic feature text built from comment + privateNote + tags
{
  "featureText": "Disinterested wait staff. Plonked food down…",
  "aspects": ["food", "service", "ambience", "value", "noise", "wait_time"]
}

// response — what Jev infers (shape matches classification in §3)
{
  "sentiment":       { "label": "NEGATIVE", "confidence": 0.94 },
  "predictedRating": 1.8,
  "aspects": [
    { "aspect": "service",  "label": "NEGATIVE", "confidence": 0.97 },
    { "aspect": "food",     "label": "NEGATIVE", "confidence": 0.71 }
  ]
}
```

The exact Jev HTTP schema is an external unknown (mirroring the product categorisation
plan's note about the `Choice` schema): the port and the value shape are fixed by this spec,
only the adapter's JSON mapping follows the Jev docs, so a schema correction is one-file.

## 7. Open questions

- **Aspect taxonomy** — is `food/service/ambience/value/noise/wait_time` the right starting
  set, or should it derive from the real `Tags Added by Diner` / `Keywords` distributions?
- **Sentiment scale** — 3-point (`POSITIVE/NEUTRAL/NEGATIVE`) now, or 5-point from the start?
- **`Noise Rating`** — include as a `ratings.noiseScore` once its semantics are confirmed?
- **Batch envelope** — batch runs wrap the document as `{ "reviews": [ … ] }`; confirm this
  is the intended transport shape or whether reviews are classified one-at-a-time.

## 8. Out of scope (recorded, not designed here)

- `canonical_review` schema and `canonical_guest` population (slice 2 of the OpenTable
  reviews design).
- The `ReviewClassifier` port *implementation* (Jev adapter, error handling, properties).
- Endpoints, permission resources, reporting tools, and frontend screens for reviews.
