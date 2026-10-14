# Review Analytics & Classification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Canonicalize OpenTable/Google reviews into bitemporal `canonical_review` (populating `canonical_guest`), classify each review's sentiment/aspects/predicted-rating as read-only ML output, and serve a Reviews analytics page with interactive views.

**Architecture:** Two stacked halves. (A) Reviews canonicalization — a new `canonical_review` bitemporal entity mirrors `CanonicalReservation` (close-and-reinsert supersession), wired into the existing `opentable-reviews` CSV ingest; reviews' clean `Guest ID` populates the existing-but-empty `canonical_guest`. (B) Review classification — a `ReviewClassifier` port + Jev adapter in the `classification` package produces read-only results into a non-bitemporal `review_classification` table; a `semantic.ReviewMetricsQuery` leaf is implemented in `classification` (`ResolvedReviewMetricsQuery`) and consumed by `application`/`api`; the frontend renders six analytics views.

**Tech Stack:** Java 25, Spring Boot 3.5, Spring Data JPA, Flyway, PostgreSQL 16 (Testcontainers), Next.js 15 / React 19 / TypeScript / Tailwind (Bun), Recharts / `@tanstack/react-table`.

**Spec:** `docs/superpowers/specs/2026-10-14-review-analytics-design.md` — the plan argues from the spec; executors read both. The review classification JSON contract is `docs/superpowers/specs/2026-10-14-review-classification-design.md` (+ `.schema.json`).

## Global Constraints

- Schema changes are Flyway migrations only (`hibernate.ddl-auto: validate`); never edit an applied migration. Next versions are `V65`+.
- `canonical_review` is bitemporal (extends `BitemporalEntity`): immutable fact fields, `superseded_at` is the only mutable column. `review_classification` and `review_classification_run` are derived/curated state — they do **not** extend `BitemporalEntity` and carry no `valid_from`/`superseded_at`.
- `semantic` is a leaf (no in-platform deps). `api` never reads `canonical`/`classification`. `classification` reads `canonical` (the documented deviation). `reporting` depends only on `semantic` + `auth`.
- Permission resource is `reviews` (READ = view), seeded `ALL × OWNER` only.
- Ratings/confidence are `numeric(3,2)` / `numeric(6,4)`; money (none here) `numeric(19,4)`.
- ML clients sit behind ports. `product-classify-service` is untouched. The Jev HTTP schema is pinned at the adapter (one-file correction).
- Classification is **read-only**: no curation queue, no `NEEDS_REVIEW`, no manual override; re-classification upserts (`latest-wins`).
- Build: `cd backend && ./gradlew test spotlessCheck`; `cd frontend && bun run typecheck && bun run lint && bun run test`. Commit on `feature/review-analytics`; never `main`.
- Frontend demo mode: every `Api` method gets a live + demo implementation.

## Review Focus

The five input classes the spec implies but no single task's happy-path test would otherwise catch:

1. **A review with no text** (comment, private note and tags all blank) → feature text falls back to source + rating; it classifies (or is skipped with no row), never throws. → Task 10.
2. **A Google review with blank stars** (no overall/sub-ratings) → `predicted_rating` fills the gap; the ratings view buckets null given ratings without an NPE. → Task 12.
3. **A re-uploaded review whose fields changed** → `canonical_review` supersedes (close-and-reinsert); an unchanged re-upload is a no-op. → Task 3.
4. **A new Guest ID** → a minimal `canonical_guest` row is created and `canonical_review.guest_id` links to it; a second review with the same Guest ID reuses it. → Task 4/6.
5. **A review with sub-ratings but no overall rating (or vice versa)** → overview average and rating distribution compute correctly over nulls. → Task 12.

## File structure

- `backend/src/main/resources/db/migration/` — `V65__canonical_review.sql`, `V66__review_classification.sql`, `V67__seed_reviews_permission.sql`.
- `backend/src/main/java/com/goldys/platform/canonical/` — `CanonicalReview` (entity), `ReviewInput` (record), `ReviewRecorded` (event), `ReviewFact` (public read view), `CanonicalReviewRepository`, `CanonicalReviewService`, `CanonicalReviewIngest` (public), `CanonicalReviewQuery` (public read facade), `CanonicalGuestService`, `CanonicalGuestIngest` (public); modify `CanonicalGuestRepository`.
- `backend/src/main/java/com/goldys/platform/connectors/opentable/` — `OpenTableReviewDetail` (record); modify `OpenTableReviewsCsvParser`, `OpenTableReviewsCsvIngestService`.
- `backend/src/main/java/com/goldys/platform/classification/` — `ReviewClassifier`, `ReviewClassificationRequest`, `ReviewClassification`, `Sentiment`, `AspectSentiment`, `ReviewClassificationEntity`, `ReviewClassificationRepository`, `ReviewClassificationRun`, `ReviewClassificationRunRepository`, `JevReviewClassifier`, `ReviewFeatureTextExtractor`, `ReviewClassificationService`, `ReviewClassificationBackfillRunner`, `ResolvedReviewMetricsQuery`.
- `backend/src/main/java/com/goldys/platform/semantic/` — `ReviewMetricsQuery`, `ReviewOverview`, `SentimentBucket`, `AspectSentimentSummary`, `RatingBucket`, `SourceSummary`, `ReviewView`, `AspectSentimentView`, `ReviewFilter`.
- `backend/src/main/java/com/goldys/platform/application/` — `ReviewReportingService`.
- `backend/src/main/java/com/goldys/platform/api/` — `ReviewController`.
- `frontend/lib/api/` — modify `types.ts`, `live.ts`, `demo.ts`, `index.ts`.
- `frontend/app/(app)/reviews/` + `frontend/components/reviews/` — page + view components.

---

### Task 1: Flyway migrations

**Files:**
- Create: `backend/src/main/resources/db/migration/V65__canonical_review.sql`
- Create: `backend/src/main/resources/db/migration/V66__review_classification.sql`
- Create: `backend/src/main/resources/db/migration/V67__seed_reviews_permission.sql`

**Interfaces:**
- Produces: `canonical_review`, `review_classification`, `review_classification_run` tables; `reviews` permission row.

- [ ] **Step 1: Write `V65__canonical_review.sql`**

Match `V55__canonical_guest.sql` DDL style. `canonical_review` columns (all fact columns `updatable = false` in the entity, nullable unless noted): the bitemporal base (`id uuid PK`, `logical_entity_id uuid NOT NULL`, `source_system varchar NOT NULL`, `source_record_ref varchar NOT NULL`, `raw_record_id uuid NOT NULL`, `valid_from timestamptz NOT NULL`, `valid_to timestamptz`, `recorded_at timestamptz NOT NULL`, `superseded_at timestamptz`), then `source varchar(16) NOT NULL`, `guest_id uuid` (nullable FK `canonical_guest(id)`), `guest_name varchar` (nullable, denormalized from `Guest Name`), `reviewed_at timestamptz NOT NULL`, `visited_at timestamptz`, `overall_rating int`, `food_rating int`, `service_rating int`, `ambience_rating int`, `value_rating int`, `noise_label varchar`, `would_recommend boolean`, `comment text`, `private_note text`, `tags text[]`, `keywords text[]`. Indexes: unique `(source_system, source_record_ref) WHERE superseded_at IS NULL`; index on `(reviewed_at) WHERE superseded_at IS NULL`; index on `guest_id`.

- [ ] **Step 2: Write `V66__review_classification.sql`**

`review_classification` — `review_id varchar(512) PRIMARY KEY`, `sentiment varchar(16) NOT NULL`, `sentiment_confidence numeric(6,4) NOT NULL`, `predicted_rating numeric(3,2)`, `aspects jsonb NOT NULL`, `classified_at timestamptz NOT NULL`.

`review_classification_run` — `id uuid PK`, `kind varchar(16) NOT NULL`, `status varchar(16) NOT NULL`, `review_count int`, `started_at timestamptz NOT NULL`, `finished_at timestamptz`, `error_message text`.

- [ ] **Step 3: Write `V67__seed_reviews_permission.sql`**

```sql
INSERT INTO permission (id, department, seniority, resource, can_read, can_write) VALUES
    (gen_random_uuid(), 'ALL', 'OWNER', 'reviews', true, true);
```

- [ ] **Step 4: Verify migrations apply and Hibernate validates** — `cd backend && ./gradlew compileJava` → PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/db/migration/V65__canonical_review.sql backend/src/main/resources/db/migration/V66__review_classification.sql backend/src/main/resources/db/migration/V67__seed_reviews_permission.sql
git commit -m "feat(reviews): Flyway migrations for canonical_review, review_classification + reviews permission"
```

---

### Task 2: `CanonicalReview` entity, `ReviewInput`, `CanonicalReviewRepository`

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalReview.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/ReviewInput.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalReviewRepository.java`
- Test: `backend/src/test/java/com/goldys/platform/canonical/CanonicalReviewTest.java`

**Interfaces:**
- Produces:
  - `record ReviewInput(String sourceSystem, String reviewId, String source, Instant reviewedAt, Instant visitedAt, Integer overallRating, Integer foodRating, Integer serviceRating, Integer ambienceRating, Integer valueRating, String noiseLabel, Boolean wouldRecommend, String comment, String privateNote, List<String> tags, List<String> keywords, String guestId, String guestName, UUID rawRecordId)` — `guestId` is the source `Guest ID` string (null when absent).
  - `CanonicalReview` (package-private `@Entity`, extends `BitemporalEntity`): `static CanonicalReview create(UUID logicalEntityId, String sourceSystem, String sourceRecordRef, UUID rawRecordId, Instant validFrom, Instant recordedAt, ReviewInput input)`; `boolean sameFact(ReviewInput input)`; accessors for every fact field. `guest_id` column is computed from `input.guestId()` as `UUID.nameUUIDFromBytes("guest:" + guestId)` (null when `guestId` null); `guest_name` column maps `input.guestName()`; `sameFact` covers every field including `guestName`.
  - `CanonicalReviewRepository extends BitemporalRepository<CanonicalReview>` (package-private) with `Optional<CanonicalReview> lockCurrentReview(String ref, String source)` (`@Lock(PESSIMISTIC_WRITE)`, `supersededAt is null`), `List<CanonicalReview> findAllCurrent()`, `List<CanonicalReview> findCurrentByReviewedAtBetween(Instant from, Instant to)`.

- [ ] **Step 1: Write the failing test** `CanonicalReviewTest` (pure JUnit, mirror `ProductCategoryTest`):
  - `sameFact_detects_equal_and_changed_inputs` — two `ReviewInput`s differing only in `overallRating` are not the same fact; identical inputs are.
  - `create_computes_guest_uuid_from_guest_id` — `create(...)` with `guestId="6ac9e28d…"` sets `guestId()` to `UUID.nameUUIDFromBytes("guest:6ac9e28d…")`; with `guestId=null` it is null.
  - `create_sets_ratings_and_source` — fields copied verbatim.

- [ ] **Step 2: Run test to verify it fails** — `cd backend && ./gradlew test --tests '*CanonicalReviewTest'` → FAIL (compile).

- [ ] **Step 3: Implement the entity, record and repository**

Mirror `CanonicalReservation` (package-private entity, all `updatable = false`, `sameFact` using `Objects.equals` for every field; `tags`/`keywords` compared element-wise on `List<String>`). Map `tags`/`keywords` to `text[]` via `@Column(columnDefinition = "text[]")` + an `@JdbcTypeCode(SqlTypes.ARRAY)`/`String[]` field. `ReviewInput` mirrors `ReservationInput` (plain record, no builder needed — it has a single construction site).

- [ ] **Step 4: Run test to verify it passes** — PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/canonical/CanonicalReview.java backend/src/main/java/com/goldys/platform/canonical/ReviewInput.java backend/src/main/java/com/goldys/platform/canonical/CanonicalReviewRepository.java backend/src/test/java/com/goldys/platform/canonical/CanonicalReviewTest.java
git commit -m "feat(reviews): CanonicalReview entity, ReviewInput and repository"
```

---

### Task 3: `CanonicalReviewService`, `CanonicalReviewIngest`, `CanonicalReviewQuery`

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalReviewService.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalReviewIngest.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalReviewQuery.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/ReviewRecorded.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/ReviewFact.java`
- Test: `backend/src/test/java/com/goldys/platform/canonical/CanonicalReviewServiceTest.java`

**Interfaces:**
- Consumes: `CanonicalReviewRepository` (Task 2), `ApplicationEventPublisher`.
- Produces:
  - `record ReviewRecorded(String reviewId, String source, Integer overallRating, String comment, String privateNote, List<String> tags)` — the canonicalization event the classification listener reacts to (mirrors `ReservationRecorded`).
  - `record ReviewFact(String reviewId, String source, Instant reviewedAt, Instant visitedAt, Integer overallRating, Integer foodRating, Integer serviceRating, Integer ambienceRating, Integer valueRating, String noiseLabel, Boolean wouldRecommend, String comment, String privateNote, List<String> tags, List<String> keywords, String guestName)` — the public read view (public because `classification`/`semantic` consumers cannot see the package-private entity).
  - `CanonicalReviewIngest` (public `@Service`): `void record(ReviewInput input)`.
  - `CanonicalReviewQuery` (public `@Service` read facade): `List<ReviewFact> currentReviews(Instant from, Instant to)` (delegates to `findCurrentByReviewedAtBetween`, mapping entity → `ReviewFact`), `List<ReviewFact> allCurrentReviews()`.
  - `CanonicalReviewService` (package-private): `@Transactional CanonicalReview record(ReviewInput input)` — logical id `UUID.nameUUIDFromBytes("review:" + reviewId)`; `lockCurrentReview`; `sameFact` → return existing; else `supersede(now)` + save, then save `create(..., now, now, input)` (`validFrom = recordedAt = now`); after saving, publish `new ReviewRecorded(reviewId, source, overallRating, comment, privateNote, tags)`.

- [ ] **Step 1: Write the failing test** `CanonicalReviewServiceTest` (fakes for repository + `Clock` via constructor-injected `Clock`, and a recording `ApplicationEventPublisher`):
  - `record_unchanged_is_noop` — recording the same `ReviewInput` twice returns the existing version and saves only once.
  - `record_changed_supersedes_and_inserts` — recording a changed `overallRating` supersedes the current row (`supersededAt` set) and inserts a successor with the same `logicalEntityId` (Review Focus #3).
  - `logical_id_is_deterministic` — two records with the same `reviewId` share one `logicalEntityId`.
  - `publishes_review_recorded` — recording a new review publishes a `ReviewRecorded` carrying the review's `reviewId`/`source`/`overallRating`/`comment`/`tags`.
  - `query_filters_by_reviewed_at` — `CanonicalReviewQuery.currentReviews(from, to)` returns only `ReviewFact`s with `reviewedAt` in range.

- [ ] **Step 2: Run test to verify it fails** — `cd backend && ./gradlew test --tests '*CanonicalReviewServiceTest'` → FAIL (compile).

- [ ] **Step 3: Implement the four classes + two records**

`CanonicalReviewService` mirrors `CanonicalReservationService.recordAt` with an injected `Clock` (so tests control time), and after saving publishes `new ReviewRecorded(...)` (mirroring `ReservationRecorded`). `CanonicalReviewIngest` is a one-line delegate (like `CanonicalReservationIngest`). `CanonicalReviewQuery` is a public facade that maps `CanonicalReview` → `ReviewFact` (so consumers never see the package-private entity).

- [ ] **Step 4: Run test to verify it passes** — PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/canonical/CanonicalReviewService.java backend/src/main/java/com/goldys/platform/canonical/CanonicalReviewIngest.java backend/src/main/java/com/goldys/platform/canonical/CanonicalReviewQuery.java backend/src/main/java/com/goldys/platform/canonical/ReviewRecorded.java backend/src/main/java/com/goldys/platform/canonical/ReviewFact.java backend/src/test/java/com/goldys/platform/canonical/CanonicalReviewServiceTest.java
git commit -m "feat(reviews): canonical review record service, ingest and read facade"
```

---

### Task 4: Guest population — `CanonicalGuestService`, `CanonicalGuestIngest`

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalGuestService.java`
- Create: `backend/src/main/java/com/goldys/platform/canonical/CanonicalGuestIngest.java`
- Modify: `backend/src/main/java/com/goldys/platform/canonical/CanonicalGuestRepository.java`
- Test: `backend/src/test/java/com/goldys/platform/canonical/CanonicalGuestServiceTest.java`

**Interfaces:**
- Consumes: `CanonicalGuestRepository` (adds `@Lock Optional<CanonicalGuest> lockCurrentGuest(String ref, String source)` and `List<CanonicalGuest> findCurrentByRef(String ref)`).
- Produces:
  - `CanonicalGuestIngest` (public `@Service`): `void ensure(String guestId, String guestName, UUID rawRecordId)` — creates a minimal guest if absent.
  - `CanonicalGuestService` (package-private): `@Transactional void ensure(String guestId, String guestName, UUID rawRecordId, Instant now)` — logical id `UUID.nameUUIDFromBytes("guest:" + guestId)`; `lockCurrentGuest(guestId, "OPENTABLE")`; if absent, save `CanonicalGuest.create(logicalId, "OPENTABLE", guestId, rawRecordId, now, now, guestName, null, null, …all remaining args null)`. If present, no-op (guest facts from reviews are minimal; nothing to supersede).

- [ ] **Step 1: Write the failing test** `CanonicalGuestServiceTest` (fake repository + injected `Clock`):
  - `ensure_creates_minimal_guest_once` — first `ensure("g1","Mary",rawId)` saves a guest with `guestName()=="Mary"` and logical id `nameUUIDFromBytes("guest:g1")`; second call does not save again (Review Focus #4).
  - `ensure_links_to_same_logical_id` — the created guest's `logicalEntityId` equals the UUID `CanonicalReview` will FK to.

- [ ] **Step 2: Run test to verify it fails** — `cd backend && ./gradlew test --tests '*CanonicalGuestServiceTest'` → FAIL (compile).

- [ ] **Step 3: Implement the service + facade + repository queries** — mirror `CanonicalReservationService`'s lock-then-create shape.

- [ ] **Step 4: Run test to verify it passes** — PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/canonical/CanonicalGuestService.java backend/src/main/java/com/goldys/platform/canonical/CanonicalGuestIngest.java backend/src/main/java/com/goldys/platform/canonical/CanonicalGuestRepository.java backend/src/test/java/com/goldys/platform/canonical/CanonicalGuestServiceTest.java
git commit -m "feat(reviews): populate canonical_guest from review Guest ID"
```

---

### Task 5: Reviews parser — `OpenTableReviewDetail`

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/connectors/opentable/OpenTableReviewDetail.java`
- Modify: `backend/src/main/java/com/goldys/platform/connectors/opentable/OpenTableReviewsCsvParser.java`
- Test: `backend/src/test/java/com/goldys/platform/connectors/opentable/OpenTableReviewsCsvParserTest.java` (extend)

**Interfaces:**
- Consumes: the CSV shape in `backend/src/test/resources/fixtures/opentable/reviews_sample.csv`.
- Produces:
  - `record OpenTableReviewDetail(String reviewId, String source, Instant reviewDate, Instant visitDate, String guestName, String guestId, Integer overallRating, Integer foodRating, Integer serviceRating, Integer ambienceRating, Integer valueRating, String noiseLabel, Boolean wouldRecommend, String comment, String privateNote, List<String> tags, List<String> keywords)` — all fields after `source` nullable.
  - `OpenTableReviewsCsvParser.parse(byte[])` now returns `List<OpenTableReviewDetail>` (replaces `OpenTableReview`).

- [ ] **Step 1: Write the failing test** — extend `OpenTableReviewsCsvParserTest`:
  - `mapsSubRatingsAndGuestId` — row 1 maps `food=5, service=5, ambience=5, value=5`, `noiseLabel="Do not recall"`, `wouldRecommend=false` (blank), `guestId="6a8e7ffc…"`, `visitDate` parsed.
  - `googleRowHasNullRatings` — the `RT-…` row yields null `overallRating`/sub-ratings and `wouldRecommend` (Review Focus #2 input).
  - `handlesMultilineComment` — existing assertion, now on `OpenTableReviewDetail.comment()`.
  - `missingColumnThrows` — unchanged (still `CONNECTOR_SCHEMA_MISMATCH` on a missing `Review ID`/`Source`/`Review Date`).

- [ ] **Step 2: Run test to verify it fails** — `cd backend && ./gradlew test --tests '*OpenTableReviewsCsvParserTest'` → FAIL (compile).

- [ ] **Step 3: Implement `OpenTableReviewDetail` + extend the parser**

Add optional columns (`Visit Date`, `Guest Name`, `Guest ID`, `Food`, `Service`, `Ambience`, `Value`, `Noise`, `Would Recommend`, `Private Note from Guest`, `Tags Added by Diner`, `Keywords`) to the `columnIndex`/`row` mapping; each blank → `null` (integers via the existing `integer(...)`, booleans via `Yes/No → true/false`). `Tags Added by Diner`/`Keywords` split on comma into `List<String>` (empty list when blank). `OpenTableReview` is deleted.

- [ ] **Step 4: Run test to verify it passes** — PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/connectors/opentable/OpenTableReviewDetail.java backend/src/main/java/com/goldys/platform/connectors/opentable/OpenTableReviewsCsvParser.java backend/src/test/java/com/goldys/platform/connectors/opentable/OpenTableReviewsCsvParserTest.java
git rm backend/src/main/java/com/goldys/platform/connectors/opentable/OpenTableReview.java
git commit -m "feat(reviews): parse full review detail (sub-ratings, guest id, recommend, tags)"
```

---

### Task 6: Wire reviews ingest to canonicalize + ensure guests

**Files:**
- Modify: `backend/src/main/java/com/goldys/platform/connectors/opentable/OpenTableReviewsCsvIngestService.java`
- Test: `backend/src/test/java/com/goldys/platform/connectors/opentable/OpenTableReviewsCsvIngestServiceTest.java` (extend)

**Interfaces:**
- Consumes: `IngestionService`, `OpenTableReviewsCsvParser` (Task 5), `CanonicalReviewIngest` (Task 3), `CanonicalGuestIngest` (Task 4).
- Produces: `void ingest(byte[] csv)` — `ingestPush("OPENTABLE", "opentable-reviews", FILE_EXPORT, "text/csv", …, "opentable-reviews")`, then for each `OpenTableReviewDetail`: if `guestId` present, `guestIngest.ensure(guestId, guestName, rawId)`; then `reviewIngest.record(toInput(detail, rawId))`.

- [ ] **Step 1: Write the failing test** — extend `OpenTableReviewsCsvIngestServiceTest` (fakes for `IngestionService`/`CanonicalReviewIngest`/`CanonicalGuestIngest`, real parser over a fixture):
  - `ingestCanonicalizesEachReview` — the fake `CanonicalReviewIngest` receives one `ReviewInput` per parsed row with the right `reviewId`/`source`/`overallRating`.
  - `ingestEnsuresGuestBeforeReview` — for a row with a `Guest ID`, `CanonicalGuestIngest.ensure` is called with `(guestId, guestName, rawId)` before `record` for that row.
  - `blankGuestIdSkipsEnsure` — a row without `Guest ID` does not call `ensure`.

- [ ] **Step 2: Run test to verify it fails** — `cd backend && ./gradlew test --tests '*OpenTableReviewsCsvIngestServiceTest'` → FAIL.

- [ ] **Step 3: Implement `toInput` + the canonicalizing loop** — mirror `OpenTableReservationsCsvIngestService.ingest`/`toInput`.

- [ ] **Step 4: Run test to verify it passes** — PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/connectors/opentable/OpenTableReviewsCsvIngestService.java backend/src/test/java/com/goldys/platform/connectors/opentable/OpenTableReviewsCsvIngestServiceTest.java
git commit -m "feat(reviews): canonicalize reviews on ingest and populate guests"
```

---

### Task 7: Canonical integration test (Testcontainers)

**Files:**
- Create: `backend/src/test/java/com/goldys/platform/canonical/CanonicalReviewIntegrationTest.java`

**Interfaces:**
- Consumes: Tasks 1–6. Asserts `V65` applies, `ddl-auto=validate` passes, and the supersession + guest-link behavior end-to-end.

- [ ] **Step 1: Write the failing test** — mirror `CanonicalGuestMigrationTest`/reservation integration test:
  - `recordsAndSupersedesReview` — record a review, re-record with changed `overallRating`, assert one superseded row + one current row sharing `logicalEntityId`.
  - `populatesGuestAndLinks` — ingest a review with a `Guest ID`, assert a `canonical_guest` row exists and `canonical_review.guest_id` references it.

- [ ] **Step 2: Run test to verify it fails** — `cd backend && ./gradlew test --tests '*CanonicalReviewIntegrationTest'` → FAIL.

- [ ] **Step 3: (Implementation is Tasks 1–6; this test pins them.)** Fix any wiring the test reveals.

- [ ] **Step 4: Run test to verify it passes** — PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/java/com/goldys/platform/canonical/CanonicalReviewIntegrationTest.java
git commit -m "test(reviews): canonical review supersession + guest link integration"
```

---

### Task 8: `ReviewClassifier` port + records + `ReviewClassificationEntity`/repository

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/classification/ReviewClassifier.java`
- Create: `backend/src/main/java/com/goldys/platform/classification/ReviewClassificationRequest.java`
- Create: `backend/src/main/java/com/goldys/platform/classification/ReviewClassification.java`
- Create: `backend/src/main/java/com/goldys/platform/classification/Sentiment.java`
- Create: `backend/src/main/java/com/goldys/platform/classification/AspectSentiment.java`
- Create: `backend/src/main/java/com/goldys/platform/classification/ReviewClassificationEntity.java`
- Create: `backend/src/main/java/com/goldys/platform/classification/ReviewClassificationRepository.java`

**Interfaces:**
- Produces:
  - `public interface ReviewClassifier { ReviewClassification classify(ReviewClassificationRequest request); }`
  - `public record ReviewClassificationRequest(String featureText, List<String> aspects)`
  - `public record Sentiment(String label, BigDecimal confidence)`
  - `public record AspectSentiment(String aspect, String label, BigDecimal confidence)`
  - `public record ReviewClassification(Sentiment sentiment, BigDecimal predictedRating, List<AspectSentiment> aspects)` — `predictedRating` nullable.
  - `ReviewClassificationEntity` (`@Entity @Table(name="review_classification")`, `@Id String reviewId`): `static ReviewClassificationEntity create(String reviewId, ReviewClassification c, Instant now)`; accessors `reviewId()`, `sentiment()`, `sentimentConfidence()`, `predictedRating()`, `aspectsJson()`, `classifiedAt()`. `aspects` serialized to `jsonb` via a Jackson `ObjectMapper` (store the `aspectsJson` string).
  - `ReviewClassificationRepository extends JpaRepository<ReviewClassificationEntity, String>` (package-private).

- [ ] **Step 1: Write the failing test** `ReviewClassificationEntityTest` (pure JUnit):
  - `createSerializesAspectsToJson` — `create("OT-…", new ReviewClassification(new Sentiment("NEGATIVE", 0.94), 1.8, List.of(new AspectSentiment("service","NEGATIVE",0.97))), now)` yields `sentiment()=="NEGATIVE"`, `aspectsJson()` containing `"aspect":"service"`.
  - `predictedRatingMayBeNull` — a `ReviewClassification` with null rating creates with null `predictedRating()`.

- [ ] **Step 2: Run test to verify it fails** — `cd backend && ./gradlew test --tests '*ReviewClassificationEntityTest'` → FAIL (compile).

- [ ] **Step 3: Implement the port, records and entity** — records mirror the JSON spec; entity mirrors `ProductCategoryAssignment` (no surrogate id, `@Id` on `reviewId`).

- [ ] **Step 4: Run test to verify it passes** — PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/classification backend/src/test/java/com/goldys/platform/classification/ReviewClassificationEntityTest.java
git commit -m "feat(reviews): ReviewClassifier port, records and classification entity"
```

---

### Task 9: `JevReviewClassifier` adapter

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/classification/JevReviewClassifier.java`
- Test: `backend/src/test/java/com/goldys/platform/classification/JevReviewClassifierTest.java`

**Interfaces:**
- Consumes: `ReviewClassifier`, the records (Task 8), the existing `JevProperties` (`classification.jev.*`).
- Produces: `@Component JevReviewClassifier implements ReviewClassifier` — POSTs the review-classification request against the Jev API; maps the response `{"sentiment":{"label":…,"confidence":…},"predictedRating":…,"aspects":[{"aspect":…,"label":…,"confidence":…}]}` to `ReviewClassification`.

- [ ] **Step 1: Write the failing test** `JevReviewClassifierTest` (mock the JDK `HttpClient`/`ObjectMapper` as `TimesFmHttpClient` does):
  - `maps_jev_response_to_review_classification` — a body with sentiment + rating + two aspects maps to `ReviewClassification` with the right labels/confidences/rating.
  - `non_2xx_throws_jev_exception` — a 500 → `JevException` with an operator-facing message (never the body).
  - `null_predicted_rating_maps_to_null` — response omitting `predictedRating` yields null.

- [ ] **Step 2: Run test to verify it fails** — `cd backend && ./gradlew test --tests '*JevReviewClassifierTest'` → FAIL (compile).

- [ ] **Step 3: Implement the adapter** — mirror `JevProductClassifier`/`TimesFmHttpClient`: JDK `HttpClient` pinned to HTTP/1.1, non-2xx/IO/unparseable → `JevException`. The exact Jev HTTP request/response schema is confirmed against the Jev docs during this task; the port + value types are fixed regardless.

- [ ] **Step 4: Run test to verify it passes** — PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/classification/JevReviewClassifier.java backend/src/test/java/com/goldys/platform/classification/JevReviewClassifierTest.java
git commit -m "feat(reviews): Jev review classifier adapter"
```

---

### Task 10: `ReviewFeatureTextExtractor` + `ReviewClassificationService` + backfill

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/classification/ReviewFeatureTextExtractor.java`
- Create: `backend/src/main/java/com/goldys/platform/classification/ReviewClassificationService.java`
- Create: `backend/src/main/java/com/goldys/platform/classification/ReviewClassificationRun.java`
- Create: `backend/src/main/java/com/goldys/platform/classification/ReviewClassificationRunRepository.java`
- Create: `backend/src/main/java/com/goldys/platform/classification/ReviewClassificationBackfillRunner.java`
- Test: `backend/src/test/java/com/goldys/platform/classification/ReviewClassificationServiceTest.java`

**Interfaces:**
- Consumes: `CanonicalReviewQuery` (Task 3), `ReviewRecorded` (Task 3), `ReviewClassifier` (Task 8), `ReviewClassificationRepository` (Task 8), `ReviewClassificationRunRepository`, `@Qualifier("applicationTaskExecutor") TaskExecutor`.
- Produces:
  - `ReviewFeatureTextExtractor`: `String featureText(String source, Integer overallRating, String comment, String privateNote, List<String> tags)` — joins comment + private note + tags; a blank result falls back to `"source + overall rating"` (Review Focus #1), never null/empty.
  - `ReviewClassificationRun` (`@Entity @Table(name="review_classification_run")`): `static ReviewClassificationRun create(String kind, Instant now)`; `markSucceeded(int count, Instant now)`; `markFailed(String message, Instant now)`; accessors `status()`, `reviewCount()`, `errorMessage()`.
  - `ReviewClassificationService`: `void classify(String reviewId, String source, Integer overallRating, String comment, String privateNote, List<String> tags)` — build feature text, call `classifier.classify(new ReviewClassificationRequest(text, DEFAULT_ASPECTS))`, upsert `review_classification` (`latest-wins`); a `ReviewClassifier` throw is swallowed-and-skipped (read-only: one bad review must not fail a run). `@TransactionalEventListener(phase = AFTER_COMMIT) void onReviewRecorded(ReviewRecorded event)` — delegates to `classify` (the "classify on ingest" bridge; `classification` → `canonical` is the allowed leaf direction). `void backfill()` — iterate `CanonicalReviewQuery.allCurrentReviews()`, classify each review missing a `review_classification` row, under a `review_classification_run` (kind `BACKFILL`, `RUNNING`→`SUCCEEDED`/`FAILED`).
  - `ReviewClassificationBackfillRunner implements org.springframework.boot.ApplicationRunner` — on startup, submits `backfill()` to the task executor (non-blocking; no-op when all reviews are already classified).

- [ ] **Step 1: Write the failing test** `ReviewClassificationServiceTest` (fake `ReviewClassifier`, recording fake repository, fake `CanonicalReviewQuery`):
  - `classify_upserts_latest_wins` — classifying twice overwrites the single row (assert repository saw the second write with the new confidence).
  - `feature_text_falls_back_to_source_and_rating` — blank comment/note/tags yields a non-empty string containing the source and rating (Review Focus #1).
  - `classifier_throw_is_skipped_not_fatal` — a fake classifier that throws leaves no row and does not propagate (read-only robustness).
  - `on_review_recorded_classifies` — calling `onReviewRecorded(new ReviewRecorded(...))` invokes `classify` for that review.
  - `backfill_marks_run_succeeded` — `backfill()` over N reviews marks the run `SUCCEEDED` with `review_count == N`; a throwing classifier for one review marks the run `FAILED` with `errorMessage`.

- [ ] **Step 2: Run test to verify it fails** — `cd backend && ./gradlew test --tests '*ReviewClassificationServiceTest'` → FAIL (compile).

- [ ] **Step 3: Implement the extractor + service** — mirror `ProductClassificationService`'s `submit`/`execute` shape (run persisted as `RUNNING`, work on the `applicationTaskExecutor`). `DEFAULT_ASPECTS = List.of("food","service","ambience","value","noise","wait_time")`.

- [ ] **Step 4: Run test to verify it passes** — PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/classification/ReviewFeatureTextExtractor.java backend/src/main/java/com/goldys/platform/classification/ReviewClassificationService.java backend/src/main/java/com/goldys/platform/classification/ReviewClassificationRun.java backend/src/main/java/com/goldys/platform/classification/ReviewClassificationRunRepository.java backend/src/main/java/com/goldys/platform/classification/ReviewClassificationBackfillRunner.java backend/src/test/java/com/goldys/platform/classification/ReviewClassificationServiceTest.java
git commit -m "feat(reviews): review classification orchestration (classify on ingest + backfill)"
```

---

### Task 11: `semantic.ReviewMetricsQuery` + records

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/semantic/ReviewMetricsQuery.java`
- Create: `backend/src/main/java/com/goldys/platform/semantic/ReviewOverview.java`
- Create: `backend/src/main/java/com/goldys/platform/semantic/SentimentBucket.java`
- Create: `backend/src/main/java/com/goldys/platform/semantic/AspectSentimentSummary.java`
- Create: `backend/src/main/java/com/goldys/platform/semantic/RatingBucket.java`
- Create: `backend/src/main/java/com/goldys/platform/semantic/SourceSummary.java`
- Create: `backend/src/main/java/com/goldys/platform/semantic/ReviewView.java`
- Create: `backend/src/main/java/com/goldys/platform/semantic/AspectSentimentView.java`
- Create: `backend/src/main/java/com/goldys/platform/semantic/ReviewFilter.java`

**Interfaces:**
- Produces (leaf interface + plain records; no test — compile is the check):
  - `public interface ReviewMetricsQuery { ReviewOverview overview(LocalDate from, LocalDate to); List<SentimentBucket> sentimentTrend(LocalDate from, LocalDate to); List<AspectSentimentSummary> aspectBreakdown(LocalDate from, LocalDate to); List<RatingBucket> ratingDistribution(LocalDate from, LocalDate to); List<SourceSummary> sourceComparison(LocalDate from, LocalDate to); List<ReviewView> reviews(ReviewFilter filter); }`
  - `record ReviewOverview(long total, double averageRating, long positive, long neutral, long negative, double wouldRecommendPct)`
  - `record SentimentBucket(LocalDate periodStart, long positive, long neutral, long negative)` — weekly buckets.
  - `record AspectSentimentSummary(String aspect, long mentions, long positive, long neutral, long negative)`
  - `record RatingBucket(int stars, long given, long predicted)` — stars 1..5.
  - `record SourceSummary(String source, long total, double averageRating, long positive, long negative)`
  - `record ReviewView(String reviewId, String source, Instant reviewedAt, Integer overallRating, String sentiment, double sentimentConfidence, BigDecimal predictedRating, String comment, String guestName, List<AspectSentimentView> aspects)`
  - `record AspectSentimentView(String aspect, String label, double confidence)` — the per-aspect sentiment in the read view (semantic cannot reference the `classification.AspectSentiment` type).
  - `record ReviewFilter(LocalDate from, LocalDate to, String source, String sentiment, String aspect, Integer minRating, String query)` — all nullable.

- [ ] **Step 1: Write the files** — leaf interface only.

- [ ] **Step 2: Verify compile** — `cd backend && ./gradlew compileJava` → PASS.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/semantic
git commit -m "feat(reviews): semantic ReviewMetricsQuery + records"
```

---

### Task 12: `ResolvedReviewMetricsQuery`

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/classification/ResolvedReviewMetricsQuery.java`
- Test: `backend/src/test/java/com/goldys/platform/classification/ResolvedReviewMetricsQueryTest.java`

**Interfaces:**
- Consumes: `CanonicalReviewQuery` (Task 3), `ReviewClassificationRepository` (Task 8), `ReviewMetricsQuery` (Task 11).
- Produces: `ResolvedReviewMetricsQuery implements ReviewMetricsQuery` — joins current `canonical_review` (from `CanonicalReviewQuery`) to `review_classification` in memory (pub scale), computing each method.

- [ ] **Step 1: Write the failing test** `ResolvedReviewMetricsQueryTest` (fakes returning fixed review + classification sets):
  - `overview_computes_average_and_split` — two reviews (one positive, one negative) → `total==2`, `averageRating` over non-null given ratings, `positive==1`, `negative==1` (Review Focus #5: a review with a null `overallRating` is excluded from the average, not an NPE).
  - `rating_distribution_counts_predicted_and_given` — a Google review with null given rating but `predictedRating==4` counts under `predicted`, not `given` (Review Focus #2).
  - `aspect_breakdown_rolls_up_sentiment` — two reviews mentioning `service` (one positive, one negative) → `AspectSentimentSummary("service", 2, 1, 0, 1)`.
  - `reviews_filters_by_aspect` — `reviews(ReviewFilter(..., aspect="service", …))` returns only reviews whose `aspects` contain `service`.

- [ ] **Step 2: Run test to verify it fails** — `cd backend && ./gradlew test --tests '*ResolvedReviewMetricsQueryTest'` → FAIL (compile).

- [ ] **Step 3: Implement `ResolvedReviewMetricsQuery`** — parse `aspectsJson` back to `List<AspectSentiment>` via the same `ObjectMapper` Task 8 uses, map each to `semantic.AspectSentimentView` for `ReviewView`, and aggregate with `long` counters / `double` averages in memory.

- [ ] **Step 4: Run test to verify it passes** — PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/classification/ResolvedReviewMetricsQuery.java backend/src/test/java/com/goldys/platform/classification/ResolvedReviewMetricsQueryTest.java
git commit -m "feat(reviews): ResolvedReviewMetricsQuery over canonical + classification"
```

---

### Task 13: `ReviewReportingService` + `ReviewController` + permission boundary

**Files:**
- Create: `backend/src/main/java/com/goldys/platform/application/ReviewReportingService.java`
- Create: `backend/src/main/java/com/goldys/platform/api/ReviewController.java`
- Test: `backend/src/test/java/com/goldys/platform/application/ReviewReportingServiceTest.java`
- Test: `backend/src/test/java/com/goldys/platform/api/ReviewControllerTest.java`
- Test: `backend/src/test/java/com/goldys/platform/classification/ReviewPermissionBoundaryTest.java` (Testcontainers)

**Interfaces:**
- Consumes: `ReviewMetricsQuery` (semantic), `PermissionService` + `CurrentUserService` (auth).
- Produces:
  - `ReviewReportingService`: one method per `ReviewMetricsQuery` method, each taking `UserRole role` first and calling `permissions.require(role, new ResourceKey("reviews"), READ)`.
  - `ReviewController` (`@RestController @RequestMapping("/api/reviews")`): `GET /overview`, `/sentiment-trend`, `/aspects`, `/ratings`, `/sources`, and `GET /reviews` (with query params `from,to,source,sentiment,aspect,minRating,q`); each delegates to `reporting.<method>(currentUser.roleOf(user), …)`.

- [ ] **Step 1: Write the failing service test** `ReviewReportingServiceTest`: `authorizes_and_returns_overview` — fake `PermissionService` records the `require` on `reviews` READ; `denies_without_permission` — a throwing `PermissionService` propagates.

- [ ] **Step 2: Run test to verify it fails** — `cd backend && ./gradlew test --tests '*ReviewReportingServiceTest'` → FAIL.

- [ ] **Step 3: Implement `ReviewReportingService`** — copy `ProductCategoryReportingService`/`RecipeReportingService` shape.

- [ ] **Step 4: Write the failing controller test** `ReviewControllerTest`: `GET /api/reviews/overview` returns `200` + the service's `ReviewOverview`; verify the controller imports neither `canonical` nor `classification`.

- [ ] **Step 5: Run test to verify it fails** — `cd backend && ./gradlew test --tests '*ReviewControllerTest'` → FAIL.

- [ ] **Step 6: Implement `ReviewController`** — copy `RecipeController`/`ProductCategoryController` shape.

- [ ] **Step 7: Write the failing permission boundary test** `ReviewPermissionBoundaryTest` (mirror `ProductCategoryPermissionBoundaryTest`): a user with `ALL×OWNER` reads `GET /api/reviews/overview`; a user with no `reviews` grant gets denied.

- [ ] **Step 8: Run tests to verify they pass** — all three PASS.

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/goldys/platform/application/ReviewReportingService.java backend/src/main/java/com/goldys/platform/api/ReviewController.java backend/src/test/java/com/goldys/platform/application backend/src/test/java/com/goldys/platform/api backend/src/test/java/com/goldys/platform/classification/ReviewPermissionBoundaryTest.java
git commit -m "feat(reviews): reporting service, REST controller and permission boundary"
```

---

### Task 14: Frontend Api contract

**Files:**
- Modify: `frontend/lib/api/types.ts`, `frontend/lib/api/live.ts`, `frontend/lib/api/demo.ts`, `frontend/lib/api/index.ts`
- Test: `frontend/lib/api/demo.test.ts` (extend)

**Interfaces:**
- Produces types: `ReviewOverview`, `SentimentBucket`, `AspectSentimentSummary`, `RatingBucket`, `SourceSummary`, `ReviewView`, `ReviewFilter` (mirror the backend records), and `Api` methods:
  - `getReviewOverview(from: string, to: string): Promise<ReviewOverview>`
  - `getReviewSentimentTrend(from: string, to: string): Promise<SentimentBucket[]>`
  - `getReviewAspects(from: string, to: string): Promise<AspectSentimentSummary[]>`
  - `getReviewRatings(from: string, to: string): Promise<RatingBucket[]>`
  - `getReviewSources(from: string, to: string): Promise<SourceSummary[]>`
  - `getReviews(filter: ReviewFilter): Promise<ReviewView[]>`

- [ ] **Step 1: Write the failing demo test** — `demo.test.ts`: `getReviewOverview` returns a fixture with a non-zero `total` and a `negative` count; `getReviews` with `aspect:"service"` returns only reviews whose `aspects` include `service`.

- [ ] **Step 2: Run test to verify it fails** — `cd frontend && bun run test -- demo` → FAIL.

- [ ] **Step 3: Implement the contract** — `types.ts` adds the interfaces + `Api` methods; `live.ts` adds `fetchApi<…>(`/api/reviews/…`)` calls; `demo.ts` adds `await delay(300)` fixtures (a few reviews with mixed sentiment/aspects, a null-rating Google row); `index.ts` re-exports the types.

- [ ] **Step 4: Run test to verify it passes** — PASS. Run `bun run typecheck` → PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/lib/api
git commit -m "feat(reviews): frontend Api contract for review analytics"
```

---

### Task 15: Reviews page + views

**Files:**
- Create: `frontend/app/(app)/reviews/page.tsx`
- Create: `frontend/components/reviews/overview-strip.tsx`
- Create: `frontend/components/reviews/sentiment-trend.tsx`
- Create: `frontend/components/reviews/aspect-breakdown.tsx`
- Create: `frontend/components/reviews/rating-distribution.tsx`
- Create: `frontend/components/reviews/source-comparison.tsx`
- Create: `frontend/components/reviews/review-explorer.tsx`
- Test: `frontend/app/(app)/reviews/reviews-page.test.tsx`

**Interfaces:**
- Consumes: the `Api` methods (Task 14), `useApiData` mock harness, Recharts `BarChart`/`LineChart`/`AreaChart`, `@tanstack/react-table` `DataTable`, design-system tones.

- [ ] **Step 1: Write the failing page test** `reviews-page.test.tsx` (Vitest + the `use-api-data` mock harness, mirroring `recipes-page.test.tsx`): renders the six views from fixtures; the aspect view shows a `service` row with a `negative` count; the explorer shows a review whose `sentiment === "NEGATIVE"`; empty state when `getReviewOverview` returns `total: 0`.

- [ ] **Step 2: Run test to verify it fails** — `cd frontend && bun run test -- reviews-page` → FAIL.

- [ ] **Step 3: Implement the page + views** — a shared filter bar (date range, source, sentiment, aspect, search) drives all six views via `useApiData`; reuse the Products/Kitchen page structure and chart styles (`widgets/chart-style.ts`).

- [ ] **Step 4: Run test to verify it passes** — PASS.

- [ ] **Step 5: Run frontend checks** — `cd frontend && bun run typecheck && bun run lint && bun run test` → all pass.

- [ ] **Step 6: Commit**

```bash
git add frontend/app/(app)/reviews frontend/components/reviews
git commit -m "feat(reviews): Reviews analytics page with six views"
```

---

### Task 16: Architecture boundary tests + whole-feature verification

**Files:**
- Modify: `backend/src/test/java/com/goldys/platform/architecture/ArchitectureBoundariesTest.java`

**Interfaces:**
- Consumes: all prior tasks.

- [ ] **Step 1: Write the failing boundary assertion** — extend `ArchitectureBoundariesTest` with **one** new rule. The existing `classificationDoesNotReachHigherPackages` (classification → api/application/reporting/conversational/reconciliation/ingestion/connectors/dashboard/widget/forecasting/config/metrics), `apiDoesNotReadCanonical`, and `apiDoesNotReachClassification` already constrain the review code — the `@TransactionalEventListener` on `canonical.ReviewRecorded` is compliant because `classification` → `canonical` is the allowed leaf direction. Add:
  - `reviewMetricsQueryImplementedInClassification` — `classes().that().implement(ReviewMetricsQuery.class).should().resideInAPackage("..classification..")` (copy `productCategoryMetricsImplementedInClassification`; add the `ReviewMetricsQuery` import).

- [ ] **Step 2: Run test to verify it fails** — `cd backend && ./gradlew test --tests '*ArchitectureBoundariesTest'` → FAIL.

- [ ] **Step 3: Implement** — add the rule (copy `productCategoryMetricsImplementedInClassification`).

- [ ] **Step 4: Run test to verify it passes** — PASS.

- [ ] **Step 5: Backend full gate** — `cd backend && ./gradlew test spotlessCheck` → all green.

- [ ] **Step 6: Frontend full gate** — `cd frontend && bun run typecheck && bun run lint && bun run test` → all pass. (`bun run build` optional, once, not concurrent with typecheck.)

- [ ] **Step 7: Commit** (only if lint/format fixes were needed)

```bash
git commit -am "chore(reviews): final checks and formatting"
```

---

## Execution notes (not steps)

- The Jev review-classification HTTP schema (Task 9) is the one external unknown: the port + `ReviewClassification` value types are fixed, only the adapter's JSON mapping follows the Jev docs — so a schema correction is one-file.
- `review_classification.aspects` is stored as `jsonb` and round-tripped through one shared `ObjectMapper` (Tasks 8 and 12) — keep that mapper injected, not `new`-ed.
- `CanonicalReview` and `CanonicalGuest` stay package-private; connectors and `classification` reach them only through the public `CanonicalReviewIngest`/`CanonicalReviewQuery`/`CanonicalGuestIngest` facades.
- Tasks are ordered for a single implementer, but Tasks 8–10 (classification) depend only on Task 3's read facade and the Task 1 tables, so they can start once Task 3 lands; Tasks 14–15 (frontend) depend only on the Api contract and the backend record shapes (Task 11). Parallelise under subagent-driven execution where the dependency graph allows.
