# Review Analytics & Classification

**Status:** Draft — for review
**Date:** 2026-10-14
**Scope:** Canonicalize OpenTable/Google guest reviews into a bitemporal `canonical_review`
(populating the already-present but empty `canonical_guest`), classify each review's
sentiment, aspects and predicted rating as **read-only** ML output, and serve a **Reviews
analytics page** whose interactive views turn that classified data into actionable insight.

**Related:**
- `docs/superpowers/specs/2026-10-14-review-classification-design.md` (+ `.schema.json`) —
  the review classification JSON contract this feature produces and consumes.
- `docs/superpowers/specs/2026-10-13-opentable-reviews-menu-reservations-design.md` — the
  slice-1/slice-2 split this builds on.
- `docs/superpowers/specs/2026-10-13-product-categorisation-design.md` — the classification
  template (embed → classify → store → read → report → UI).

## 1. Objective

OpenTable/Google guest reviews arrive raw-only today, so reputation is invisible. Give the
pub a first-class, actionable view of it: canonicalize reviews, classify what each review is
saying (sentiment, the aspects it mentions, and a predicted star rating), and expose that
through an analytics page — trend, aspect breakdown, rating distribution, source comparison,
and a filterable explorer — so the owner can answer *"are we improving?"*, *"what are we
getting praised or dragged on?"*, and *"which reviews need attention?"*.

## 2. Current baseline

- **Reviews ingest is raw-only.** `OpenTableReviewsCsvParser` → `OpenTableReview`
  (`reviewId`, `source`, `reviewDate`, `overallRating`, `comment`). The `opentable-reviews`
  upload validates the CSV and parks typed rows; nothing canonicalizes them.
- **`canonical_guest` exists but is empty.** Migration `V55` + `CanonicalGuest` entity are
  present; `CanonicalGuestRepository` is deliberately empty, `CanonicalGuest.create(...)` is
  unused, and `canonical_reservation.guest_id` is null. Reviews carry the clean `Guest ID`
  that slice 2 uses to populate guests.
- **`canonical_review` does not exist.**
- **Product classification is live**: `com.goldys.platform.classification`, the data-agnostic
  `product-classify-service` (embed/cluster), the Jev `ProductClassifier` port (single-category
  `Choice`), and migrations through `V64`. The Products page is the UI/analytics template.

## 3. Design decisions

1. **Canonicalize first.** Reviews become bitemporal `canonical_review` before classification;
   classification reads canonical, never raw.
2. **Read-only classification.** Results are ML output, never human-edited: no curation queue,
   no `NEEDS_REVIEW`, no manual override. A re-run overwrites (`latest-wins`).
3. **The page is the product.** The classifier is the enabler; the analytics page is the
   deliverable. There is no classification-admin UI (no run button, no taxonomy editing).
4. **Classify on ingest + backfill.** Each review is classified as it canonicalizes; a one-shot
   runner backfills history. A minimal run record gives observability ("when was history last
   classified") without a manual trigger.

## 4. Data model

All schema changes are Flyway migrations (`hibernate.ddl-auto: validate`). Next versions are
`V65`+.

### 4.1 `canonical_review` (new, bitemporal) — `V65`

Mirrors `CanonicalReservation`'s `BitemporalEntity` base. Logical identity =
`UUID.nameUUIDFromBytes("review:" + reviewId)`; source identity = (`source_system` = `OPENTABLE`,
`source_record_ref` = `Review ID`). `Review ID` is stable (`OT-…` / `RT-…`).

Typed facts (parser already emits most; `sameFact` covers them all so a changed field
supersedes and an unchanged re-upload is a no-op):

| Column | Type | Notes |
|---|---|---|
| `source` | varchar(16) | `OpenTable` / `Google` (sub-source within `OPENTABLE`) |
| `guest_id` | uuid | populated this slice; no FK — linkage by logical id (mirrors `canonical_reservation.guest_id`) |
| `guest_name` | varchar | denormalized from `Guest Name` (mirrors `canonical_reservation`'s guest name columns) |
| `reviewed_at` | timestamptz | `Review Date` |
| `visited_at` | timestamptz | `Visit Date` |
| `overall_rating` | int | 1–5, nullable |
| `food_rating` / `service_rating` / `ambience_rating` / `value_rating` | int | 1–5, nullable |
| `noise_label` | varchar | free text (`Quiet`, `Do not recall`, …), nullable |
| `would_recommend` | boolean | nullable |
| `comment` | text | nullable, multi-line |
| `private_note` | text | nullable |
| `tags` | text[] | `Tags Added by Diner`, nullable |
| `keywords` | text[] | nullable |
| reply fields | (see §11) | deferred — see Open questions |

### 4.2 `canonical_guest` population (no migration)

Populate the existing `canonical_guest` via its `create(...)` factory from the reviews' clean
`Guest ID` (reviews are the only source carrying it). Guests absent from `canonical_guest` are
created; `canonical_review.guest_id` is linked. Name-matching reservations → guests remains
out of scope (masked PII; see the OpenTable design spec).

### 4.3 `review_classification` (derived, **not** bitemporal) — `V66`

Keyed by logical review identity, mirroring how `product_category_assignment` is keyed by
`product_name_key` rather than a canonical row id:

| Column | Type | Notes |
|---|---|---|
| `review_id` | varchar(512) PK | the `Review ID` string |
| `sentiment` | varchar(16) NOT NULL | `POSITIVE` / `NEUTRAL` / `NEGATIVE` |
| `sentiment_confidence` | numeric(6,4) NOT NULL | 0..1 |
| `predicted_rating` | numeric(3,2) | 1..5, nullable |
| `aspects` | jsonb NOT NULL | array of `{"aspect","label","confidence"}` |
| `classified_at` | timestamptz NOT NULL | |

`latest-wins`: re-classification upserts the single row for a review. Derived state, so it
carries no `valid_from`/`superseded_at` and never extends `BitemporalEntity`.

### 4.4 `review_classification_run` (observability) — `V67`

Minimal, mirroring `classification_run`: `id uuid PK`, `kind varchar(16)`, `status varchar(16)`,
`review_count int`, `started_at timestamptz`, `finished_at timestamptz`, `error_message text`.
Used only by the backfill runner; on-ingest classification is accounted for by the existing
ingestion-run stage, not here.

## 5. Classification

Lives in `com.goldys.platform.classification` (same package as product categorisation), reading
`canonical` directly — the deliberate, spec-documented deviation that package already makes.

- **`ReviewClassifier` port** — `ReviewClassification classify(ReviewClassificationRequest)`.
- **Records** (per the JSON spec): `ReviewClassificationRequest(String featureText,
  List<String> aspects)`; `ReviewClassification(Sentiment sentiment, BigDecimal
  predictedRating, List<AspectSentiment> aspects)`; `Sentiment(String label, BigDecimal
  confidence)`; `AspectSentiment(String aspect, String label, BigDecimal confidence)`.
- **`JevReviewClassifier`** — HTTP adapter for the new Jev response schema (sentiment +
  aspects + rating); the product `Choice` schema cannot express this. The exact Jev HTTP
  schema is an external unknown: the port and value types are fixed by this spec, only the
  adapter's JSON mapping follows the Jev docs, so a correction is one-file.
- **`ReviewFeatureTextExtractor`** — builds feature text from `comment` + `private_note` +
  `tags`; a review with no text yields a non-empty fallback (source + rating) and is never
  classified (→ no `review_classification` row, or a `NONE`/skipped marker).
- **`ReviewClassificationService`** — orchestrates: on each canonicalized review, classify and
  upsert `review_classification`; `backfill()` classifies history under a run record. Reads
  `canonical_review` + `canonical_guest`; writes only `review_classification`.

`product-classify-service` is untouched — it remains data-agnostic.

## 6. Semantic + API

- **`semantic.ReviewMetricsQuery`** (leaf interface) with records:
  - `ReviewOverview overview(LocalDate from, LocalDate to)` — totals, average rating, sentiment
    split, % would-recommend.
  - `List<SentimentBucket> sentimentTrend(from, to)` — sentiment per period.
  - `List<AspectSentimentSummary> aspectBreakdown(from, to)` — per aspect: mentions, and
    positive/neutral/negative counts.
  - `List<RatingBucket> ratingDistribution(from, to)` — star histogram + predicted-vs-given.
  - `List<SourceSummary> sourceComparison(from, to)` — per source (OpenTable vs Google).
  - `List<ReviewView> reviews(ReviewFilter filter)` — filterable list (source, sentiment,
    aspect, rating, date range, free-text search), paged.
- **`application.ReviewReportingService`** + **`api.ReviewController`** (`/api/reviews/…`):
  read-only endpoints per method above, each gated by `permissions.require(role,
  new ResourceKey("reviews"), READ)`.
- **Permission** `reviews`, seeded `ALL × OWNER` (BOH/FOH grants stay deferred per the PRD open
  questions).

## 7. Frontend — Reviews page

`frontend/app/(app)/reviews/page.tsx`, mirroring the Products page structure (PageHeader +
tabs/filters + `useApiData`). A shared filter bar (date range, source, sentiment, aspect,
search) drives every view:

1. **Overview** — KPI strip: total reviews, average rating, sentiment split, % would-recommend.
2. **Sentiment trend** — positive/neutral/negative stacked over time, with an
   improving/declining signal.
3. **Aspects** — per-aspect frequency × sentiment ("service: 40 mentions, 65% negative,
   rising") — the actionable core.
4. **Ratings** — star histogram + predicted-vs-given (fills Google's blank stars).
5. **Source** — OpenTable vs Google side-by-side.
6. **Review explorer** — filterable/sortable table, drill-down to one review's full text,
   sub-ratings, and per-aspect sentiment + confidence.

Demo mode (`NEXT_PUBLIC_DEMO_MODE`, default on) requires fixtures for every `Api` method
(live + demo implementations).

## 8. Non-goals

- No manual curation/override of classification results.
- No reply *automation* — reply data is shown (if included), never written.
- No cross-source reconciliation of reviews vs reservations beyond `guest_id` linkage.
- No scheduled pull — reviews remain a manual CSV drop / re-upload (recurrence-ready).
- No new Ask Goldy's reporting tool in this slice (page only); a `get_review_insights` tool is
  a recorded follow-on, not built here.
- No guest name-matching (reservations → guests) — still blocked on masked PII.

## 9. Testing

- **Canonical**: parser-to-canonical mapping, `sameFact` supersession (changed fact →
  close-and-reinsert; unchanged → no-op), guest population from `Guest ID`.
- **Classification**: `JevReviewClassifier` JSON mapping (sentiment/aspects/rating), low/null
  feature-text fallback, upsert `latest-wins`, backfill run marks `SUCCEEDED`/`FAILED`.
- **Semantic**: each `ReviewMetricsQuery` method against fixed fakes (trend, aspect rollup,
  rating histogram, source comparison, filtered list).
- **API/arch/permission**: `ReviewController` never imports `canonical`/`classification`;
  `ArchitectureBoundariesTest` extended (classification may not reach `api`/`reporting`; `api`
  may not reach `classification`; `ReviewMetricsQuery` implemented in `classification`);
  Testcontainers permission boundary for `reviews` READ.
- **Frontend**: page renders each view from the mock harness; empty-state; filter wiring.
- Full gate: `./gradlew test spotlessCheck` and `bun run typecheck && bun run lint && bun run test`.

## 10. Rollout

- Migrations `V65`–`V67` are additive (new tables + a nullable FK), no data migration; rollback
  is "revert the deploy".
- Ingest path: re-uploading the reviews CSV now canonicalizes (close-and-reinsert) and
  classifies on the way; the raw ledger still appends byte-faithfully.
- Backfill: a one-shot runner classifies the historical reviews already in the raw ledger.

## 11. Open questions

- **Sentiment scale** — 3-point (`POSITIVE/NEUTRAL/NEGATIVE`) now, or 5-point? (defaulting 3.)
- **Aspect taxonomy** — is `food/service/ambience/value/noise/wait_time` right, or should it
  derive from the real `Tags Added by Diner` / `Keywords` distributions?
- **Reply tracking** — include the reply fields (count/author/date/text) as a "responded vs
  not" actionable view, or defer? (Data exists; flagged as lower priority.)
- **`Noise Rating`** — include as a typed rating once its semantics are confirmed? (left out of
  the first cut, like the JSON spec.)
- **Predicted-vs-given** — should `predicted_rating` only fill *blank* stars (Google), or also
  be shown alongside given ratings everywhere as a consistency check?

## 12. Out of scope (recorded, not designed here)

- The `ReviewClassifier` Jev HTTP schema details (fixed at the adapter, per §5).
- Ask Goldy's `get_review_insights` reporting tool.
- Guest name-matching and reservations↔reviews reconciliation.
