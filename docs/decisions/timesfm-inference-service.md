# TimesFM inference service (second runtime)

**Status:** Accepted
**Date:** 2026-10-12

## Context

Goldy's forecasting feature (`docs/superpowers/specs/2026-10-12-timesfm-forecasting-design.md`)
predicts a resolved business metric using Google's **TimesFM 3.0** zero-shot model. TimesFM is a
330M-parameter time-series foundation model that is **Python/PyTorch-only**: it has no JVM runtime
and no Java SDK.

The backend is a Java 25 / Spring Boot modular monolith, and the architecture invariants
(`docs/system-context.md`, `docs/architecture/current-state.md`) state "no microservices … single
deployable". Running the model therefore cannot be absorbed into the existing deployable.

## Decision

Add a **second runtime**: a self-hosted Python (FastAPI) inference service
(`forecast-service/`) that loads TimesFM 3.0 and exposes a small, **data-agnostic** HTTP contract
(`POST /forecast`, `GET /health`). The Java backend calls it behind the `TimesFmClient` port
(`com.goldys.platform.forecasting`), exactly as a connector sits behind `SourceConnector`.

This is a **deliberate, documented exception** to the "single deployable" invariant, not a widening
of it:

- The Java backend remains a single monolith; no backend logic moves into Python.
- The inference service is an external dependency behind a port, with the same standing as
  PostgreSQL or the external LLM the conversational AI already calls.
- The service knows nothing about Goldy's schema, metrics, or permissions — Java does all data
  preparation (via the resolved semantic layer) and hands the service aligned numeric arrays.

## Consequences

- **Deploy** adds a container to the stack (plus a model-download/cache volume).
- **Latency** — 330M params on CPU is seconds-to-tens-of-seconds per forecast; the async
  `forecast_run` lifecycle absorbs this, and the port makes a GPU box or a lighter checkpoint a
  deploy-only change.
- **Licensing** — TimesFM 3.0's open weights are under a **non-commercial license**. This is fine
  for the pub's own internal forecasting; commercializing the product would require the Google
  Cloud (BigQuery ML / Vertex AI) route, which is commercially licensed.
