# Historical documentation archive

Historical documentation lives in a separate local Git repository on this VM:

`/srv/ai/projects/goldys-documentation-archive`

Its root README describes the original Goldy's snapshot and archive layout. The
`archive/` directory contains the 235 historical files plus their original index:
audit evidence, feature designs, frontend proposals, handoffs and the data-handling
investigation. These records describe dated snapshots, not current requirements,
approvals or deployment state.

This is a local repository with no remote configured. Other machines and CI do not
need it. Historical links in current documentation use the original Goldy's commit
on GitHub; the local archive preserves the exported files unchanged.

The two classification JSON inputs used by a regression test remain under
`backend/src/test/resources/fixtures/product-classification/`. The release preflight
owns its read-only SQL helper. Neither depends on the archive repository.

Removed implementation plans remain recoverable from Goldy's Git history at
`b2377f97`; they were not added to the archive.
