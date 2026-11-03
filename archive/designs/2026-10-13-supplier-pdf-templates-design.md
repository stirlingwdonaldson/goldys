# Supplier PDF templates: deterministic extraction for non-templated suppliers (design)

Status: proposal — awaiting approval before implementation.

## Problem

`HybridInvoicePdfExtractor` tries five deterministic supplier templates, then falls back to the LLM
for anything they don't match. Every non-templated supplier therefore goes through the LLM, which is
(a) *non-deterministic* — now mitigated by the `V51` extraction cache — and (b) *less accurate*: it
hallucinates stock codes (`5412029.0`, `FUEL` vs `FUEL LEVY`), which is a chunk of the unresolved
`PDF_ONLY_LINE` population. The unresolved flags concentrate in the non-templated suppliers.

## Facts to design around

- **Templates exist for 5 layouts**: Infiniti Group (custom `DeterministicPdfExtractor`), Beverage
  Case, Liquor Wet, Produce Pack, and a generic `DeterministicInvoicePdfExtractor` — all columnar
  via `LinearizedColumnPdfExtractor`.
- **The LLM fallback** (`LlmInvoicePdfExtractor`, gpt-4o-mini, temperature 0, JSON schema) is
  cached by `llm_extraction_cache` (V51), so re-runs are deterministic. Templates no longer fix
  *correctness* — they buy *accuracy* and *cost* (no model call).
- **Top non-templated suppliers** by unresolved `PDF_ONLY_LINE` flags (production, 2026-10-13):
  Grange Meat Co (77), G & T Chickens (76), Good Bar Trade (66), Paramount Liquor (49),
  Tim & Terry Oyster Supply (46), Air Liquide (44), Summit Security Group (24), Alsco (24),
  Sealane food services (18), Jules Valentine Garden Maintenance (18), Oranges & Lemons (17),
  Hop Nation (13), Long Fine Hospitality Supplies (11), …
- The `LinearizedColumnPdfExtractor` shape: a header regex + a fixed columns-per-row + a `mapRow`.
  A non-matching header returns an empty invoice so the hybrid extractor falls through to the next
  template or the LLM.

## Approach

1. **Extract ground truth.** For one target supplier, pull 2–3 raw PDFs from `raw_record` and get
   their PDFBox text (run the app's `PdfBoxTextExtractor` on them, or install `poppler-utils` and
   `pdftotext` the exported bytes).
2. **Map the layout.** Identify the header marker and the per-line column structure.
3. **Write the extractor.** `extends LinearizedColumnPdfExtractor` for a columnar table, or
   `implements DeterministicPdfExtractor` for an irregular/grouped layout (the `InfinitiGroup`
   template is the reference for the latter).
4. **Test against fixtures.** Add a `*InvoicePdfExtractorTest` with the real extracted text (the
   pattern used by the existing template tests).

## Target order

Grange Meat Co → G & T Chickens → Good Bar Trade → Paramount Liquor → Tim & Terry Oyster Supply →
Air Liquide → then the long tail by flag count.

## Acceptance criteria

- For a matched supplier PDF, the template returns the same lines as (or better lines than) the LLM,
  with **no hallucinated stock codes**.
- A PDF whose layout the template matches no longer reaches the LLM (observable: no `LLM fallback`
  log line for that supplier).
- A re-run of the re-enrich produces **no new flags** for that supplier.

## Open questions / risks

- Layouts must be confirmed against real PDFs before writing the template — a guessed header regex
  produces wrong lines, not "fall through".
- Some suppliers may use grouped/irregular layouts that don't fit `LinearizedColumnPdfExtractor` and
  need a bespoke extractor.
- Cost is one template per supplier; the marginal value drops after the top ~6 (long tail).

## Deferred

- This is an accuracy/cost improvement, not a correctness fix — V51 already made the LLM fallback
  deterministic. Do it opportunistically, not as a blocker.
