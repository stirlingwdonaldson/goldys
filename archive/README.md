# Historical documentation

These records describe dated investigations, proposals and implementation sessions.
Their approvals, open issues and deployment claims apply to the recorded snapshot;
they do not establish current behaviour or authorize new operations.

Use [current documentation](../docs/README.md) for project work.

| Collection | Contents |
|---|---|
| [Audits](audits/) | Production-data, data-foundation, classification and reporting investigations, with evidence and reproducible checks. |
| [Designs](designs/) | Historical feature designs and source-coverage records, including proposals that were not implemented. |
| [Frontend](frontend/) | Dated frontend audit, target proposals and verification baseline. |
| [Handoffs](handoffs/) | Session context that may contain superseded implementation details. |
| [Data-handling investigation](data-handling-drift-plan.md) | The 15 October investigation, decisions and subsequent execution evidence. |

Audit CSV, JSON, SQL, diagram and log evidence is retained unchanged. Checks that read
repository files use the archive paths; running a probe still requires its documented
access and approval. Archiving does not establish that findings have been resolved.

Implementation plans and the duplicate product summary were removed. Their final
pre-cleanup versions are available in Git at
[`b2377f97`](https://github.com/stirlingwdonaldson/goldys/tree/b2377f97).
Do not execute an old plan as current instructions.
