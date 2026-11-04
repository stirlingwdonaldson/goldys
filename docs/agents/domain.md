# Domain documentation

One context: `GLOSSARY.md` holds accepted business terms; `docs/decisions/` holds
durable tradeoffs. Use those existing paths instead of creating `docs/adr/` or a
new context map. Load the glossary and relevant decisions only for the task.

`docs/prd.md` owns product scope; `docs/srs.md` owns behaviour and acceptance.
Source records under `docs/requirements/` are scoped by the disposition table;
their proposed wording and historical instructions never override current scope.
Manager permissions and customer-balance reporting remain undecided.

Update a definition in place when approved meaning changes. Record an ADR only
for a real tradeoff that is hard to reverse and surprising without context.
Implementation status belongs in GitHub issues and the short app-state summary.
