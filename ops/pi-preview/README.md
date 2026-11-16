# Isolated Pi preview

This directory documents a private, synthetic-data preview, not production activation.
The preview uses a separate PostgreSQL 16 container/volume, a Spring process bound to
127.0.0.1:18443 with a dedicated TLS certificate, and a live Next.js build bound to
127.0.0.1:3117. Never source production environment files or target production Compose.

Runtime files are private under `/tmp/goldys-pi-preview`; they are disposable and must
not be committed. The dedicated model key stays on the analysis VM at
`/etc/goldys-agent/deepseek-api-key`, root-owned 0600. The worker receives it as a
read-only runtime credential; neither the browser nor generated scripts receive it.

The analysis VM's `goldys-sandbox.service` runs the local Unix-socket broker. It
creates one gVisor sandbox at a time, with a read-only image, no network, no
capabilities, UID 65532, 768 MiB RAM, one CPU, 64 tasks, 128 MiB scratch, 100 MiB
output tmpfs and 25 MiB individual file limit. A run lasts at most 60 seconds;
investigations retain their existing fifteen-minute deadline. Disconnect kills the
container, including descendants. `ExecStopPost` and startup remove labelled
orphans and temporary mounts. The trusted worker has no Docker socket.

Public research is restricted to four fixed public topics and official Australian
government search results. It sends no user text, filenames, metric values or model
context to search, and never fetches arbitrary URLs. Unsupported topics are unavailable.
The search and model share the A$5 reservation counter. The reviewed DeepSeek Flash
upper bound is A$0.65/request (1M input, 8192 output, peak prices and 2 AUD/USD),
and A$0.002/search. Spring durably reserves each request before dispatch; SDK retries are disabled. Successful completion releases unused admission capacity, while failed/unknown calls and interrupted investigations retain their reservations. Native model responses retain reported token usage; this is not provider invoice reconciliation. Pricing source: https://api-docs.deepseek.com/quick_start/pricing/.

File bytes and manifests are transactionally stored together in the preview database,
with a 1 GiB retained-byte admission ceiling. Each investigation has five uploads and
five artifacts, at most 100 MiB per kind. All content inherits the entire thread's
conservative policy generation, including unknown user text, extraction, scripts,
prose and compaction. This does not claim selective dependency declassification.
Original uploads remain untrusted. Extractors run offline; generated artifacts pass
a second isolated format/active-content check before registration. Files expire after
thirty days unless kept; deleted/expired bytes are removed and unavailable metadata
remains visible. This is not an agreed production hold/backup policy.

Exact startup, sign-in, test results and continuation state are recorded in
[the implementation record](../../docs/design/pi-workspace-implementation-2026-11-15.md).

## Open the private website

From a Mac that can SSH to the development VM:

```sh
ssh -N -L 3117:127.0.0.1:3117 stiring@192.168.4.50
```

Keep that terminal open and visit **http://127.0.0.1:3117**. On the development VM,
read `/tmp/goldys-pi-preview/test-account.txt` yourself for the dedicated account's
password; email is `pi-owner@example.test`. For example, use a second SSH terminal
and `cat /tmp/goldys-pi-preview/test-account.txt`. Do not paste it into chat.
Choose **Investigations**. The legacy Ask button is intentionally separate.

This is a live Spring/Pi/DeepSeek integration using synthetic sales for 2–15 November
2026. It has separate loopback ports, PostgreSQL container/volume and TLS identity.
No production data, credentials, Compose stack or routing is used. Do not upload real
staff/customer/business files: sensitive-file and hold/backup policy remain unresolved.

Try:

1. Ask for net sales for 2–8 November 2026 (synthetic expected total **A$9,100**),
   then ask a follow-up or close/reopen the drawer to check history.
2. Ask for a dashboard draft with total net sales and a daily series. Review the
   query and press **Save dashboard**; drafting itself never saves it.
3. Upload a small CSV, XLSX, DOCX, PDF or PNG/JPEG after starting a conversation.
   Ask to extract it, calculate with Python and generate a CSV or chart. Download
   the private artifact; try Keep or Delete. OCR/preview limits remain visible.
4. Ask for Victorian public-holiday research with official citations. Supported
   public topics are holidays, Melbourne weather, hospitality industry statistics
   and the hospitality award. Arbitrary queries/URLs are deliberately unavailable.
5. Ask for a long Python operation and press Stop. Reopening preserves finished
   results. A genuinely interrupted attempt offers explicit Retry.
6. Missing dates remain missing. Budget exhaustion preserves completed output and
   refuses further calls. Deleting a conversation removes retained files and attempts.

## Operate the existing preview

On the development VM, use:

```sh
/tmp/goldys-pi-workspace-proposal/ops/pi-preview/preview.sh status
/tmp/goldys-pi-workspace-proposal/ops/pi-preview/preview.sh logs
/tmp/goldys-pi-workspace-proposal/ops/pi-preview/preview.sh stop
/tmp/goldys-pi-workspace-proposal/ops/pi-preview/preview.sh start
```

`restart` stops/starts only the named preview resources and reapplies synthetic
resolved fixtures after Spring rebuilds projections. Runtime setup files and built
assets must still exist; `/tmp` is disposable, so this is not reboot-proof hosting.
Never rerun `seed.ts` merely to restart: it would rotate the synthetic account password.
The script does not install or change anything on the analysis VM.

On `goldys@192.168.5.42`, worker/broker commands and logs are:

```sh
sudo systemctl status goldys-pi-worker goldys-sandbox
sudo journalctl -u goldys-pi-worker -u goldys-sandbox -n 100 --no-pager
sudo systemctl stop goldys-pi-worker
sudo systemctl start goldys-sandbox goldys-pi-worker
```

Stop the worker before stopping the preview for an extended period; otherwise it
restarts while Spring is unavailable. The key remains root-owned at
`/etc/goldys-agent/deepseek-api-key`. To replace it, use `sudoedit` and put only the
key on one line, then restart `goldys-pi-worker`. Generated scripts never receive it.

## Production gates and limits

Manager grants, representative business-file extraction accuracy, production
sensitive-file/hold/backup policies, owner acceptance, precise selective dependency
tracking, provider invoice/FX reconciliation, remaining image findings and static
quality gates need acceptance before production. Whole-context policy restriction
remains mandatory. Governed dataset export currently covers daily gross/net/GST
sales for at most 31 days; other datasets are unavailable. Limits are binary MiB
in code (25 per file, 100 staged in total), with five selected inputs per execution.
No push, merge, production deployment or remote CI is implied by local checks.
