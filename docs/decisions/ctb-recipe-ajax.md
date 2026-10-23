# CTB recipe access through the existing authenticated AJAX connector

Status: implementation decision approved in the recipe integration conversation,
2026-10-23. Production enablement/deployment remains a separate operation.

The repository already runs a scheduled authenticated CTB AJAX pull, but older
architecture and PRD wording still restricted that path to discovery. The approved
recipe integration extends that established one-way path, supported by three real
HARs and a verified authenticated read-only access test. Standard invoice exports
do not supply recipe composition, yields or sub-recipe usage.

The existing connector boundary, raw byte ledger, bitemporal canonical facts,
resolved-only business reads and permission model remain in force. Source failure
is observable, refresh is bounded/paced and full details are disabled by default
until rollout. Internal CTB endpoints are unsupported and schema drift remains an
operational risk; real fixture coverage and last-complete-state retention address
that risk. Nothing writes recipe corrections back to CTB.
