# TimesFM inference service (second runtime)

**Status:** Accepted 2026-10-12; ownership clarified 2026-11-12

Goldy's runs forecasting in a separate Python runtime because its TimesFM predictor depends on
Python/PyTorch. Java retains authorization, resolved semantic reads, the immutable internal-input
snapshot, asynchronous run ownership and persisted results. The runtime is reached through
`ForecastModelClient`; `ForecastModelHttpClient` is its HTTP adapter.

## Current ownership

The original data-agnostic description no longer describes the implementation. Python accepts
aligned net revenue, food/beverage and reservation series, assembles venue calendar and weather
features, applies the existing booking-maturity and feature-selection policy, and runs TimesFM
with optional XGBoost diagnostics and food/beverage splits. These remain together in the runtime
so feature preparation and model execution use the same frame. Python has no access to Goldy's
database or permission tables. Java selects and authorizes the internal inputs before dispatch.
This clarification records existing ownership; it does not change forecast targets, financial
classification, maturity policy or the standing exception to the single-deployable architecture.

`POST /forecast` returns points, model identity, optional diagnostics and runtime warnings.
Missing diagnostics stay null. A diagnostic failure retains a successful headline forecast with
an explicit notice. Optional calendar/weather failures also produce notices alongside the existing
feature fallback behavior. Java persists those warnings separately from diagnostics and includes
them in the run's notices after reload. Historical rows without retained warnings remain unknown;
the migration does not reconstruct past runtime outcomes. Older runtime responses without explicit
model identity retain the existing TimesFM 3.0 compatibility label.

The internal-input snapshot does not freeze external calendar/weather responses, runtime
configuration or model weights. Reproducing external feature inputs exactly is separate work;
this change does not claim complete forecast reproducibility.

## Run ownership and verification

Submission persists QUEUED before dispatching to the in-memory executor. Rejected dispatch becomes
FAILED. Startup marks both QUEUED and RUNNING runs from the previous process FAILED with a restart
reason, so users can submit a new run. Completed work cannot be executed again. A per-run in-process
guard prevents simultaneous duplicate execution in the current single-instance deployment; adding
replicas requires a database execution claim rather than relying on that guard. No broker or
automatic retry policy is introduced.

PostgreSQL tests cover persisted outcomes and interrupted runs. Python CI uses a fake TimesFM
predictor and blocks network connections; it does not download model weights. Production deployment
and model licensing are separate operational decisions, not established by these tests.
