# Pi worker image scan — 16 November 2026

Local packaging evidence only; the migration is not deployment-ready.

- Scanner: Trivy 0.76.2, image `aquasec/trivy@sha256:8f6bb947dbc528902e11f2f5be52f7b519f6a39d5d148f41166f2040499768fb`.
- Runtime base: `oven/bun:1.4.2-distroless@sha256:1a0c31c7c5f9d193aedf60fe1cebdeb76ac8f6e29f24be8dd8cbd6df72df26ec`.
- Built image ID: `sha256:658193c4bbb429295dfa1a92b31ea5d49b71052d21488ef203fbbb94f50fa13d` (linux/amd64).
- Report time: 2026-11-16T03:09:22Z (14:09 Melbourne).
- Scan input: an exported copy of this image, with no Docker socket, credentials or production mounts.
- Result: zero critical/high findings; 16 medium, eight low and three unknown-severity Debian findings. Node-package scanning reported no findings. This does not prove bundled native/Bun code is vulnerability-free.

The initial full Debian runtime had three critical and 64 high findings. The
same-version distroless runtime removes that unnecessary package surface. Its exact
production dependency tree passed all sixteen synthetic Pi tests in an intermediate
image stage, and empty-configuration startup failed before service access under a
read-only root, disabled network and resource caps. Test sources do not ship.
The first scan failed because its 512 MB scratch space could not hold the database;
the completed scans used a dedicated temporary database cache. A failed scan is not
a zero-finding result. CI gates high/critical findings and scan errors; remote CI has
not executed. Remaining findings below are not waived or accepted by this report.

## Remaining package findings

| Advisory | Severity | Package | Installed | Fixed version reported |
|---|---|---|---|---|
| DSA-6572-1 | UNKNOWN | ca-certificates | 20250419 | 20250419+deb13u1 |
| CVE-2026-18374 | MEDIUM | libc6 | 2.41-12+deb13u3 | None reported |
| CVE-2026-19499 | MEDIUM | libc6 | 2.41-12+deb13u3 | None reported |
| CVE-2026-19542 | MEDIUM | libc6 | 2.41-12+deb13u3 | None reported |
| CVE-2026-5435 | MEDIUM | libc6 | 2.41-12+deb13u3 | None reported |
| CVE-2026-5450 | MEDIUM | libc6 | 2.41-12+deb13u3 | 2.41-12+deb13u4 |
| CVE-2026-5928 | MEDIUM | libc6 | 2.41-12+deb13u3 | 2.41-12+deb13u4 |
| CVE-2026-6238 | MEDIUM | libc6 | 2.41-12+deb13u3 | None reported |
| CVE-2026-6368 | MEDIUM | libc6 | 2.41-12+deb13u3 | None reported |
| CVE-2026-6791 | MEDIUM | libc6 | 2.41-12+deb13u3 | None reported |
| CVE-2026-77117 | MEDIUM | libc6 | 2.41-12+deb13u3 | None reported |
| CVE-2026-80489 | MEDIUM | libc6 | 2.41-12+deb13u3 | None reported |
| CVE-2026-84243 | MEDIUM | libc6 | 2.41-12+deb13u3 | None reported |
| CVE-2026-8674 | MEDIUM | libc6 | 2.41-12+deb13u3 | None reported |
| CVE-2026-86805 | MEDIUM | libc6 | 2.41-12+deb13u3 | None reported |
| CVE-2026-89092 | MEDIUM | libc6 | 2.41-12+deb13u3 | None reported |
| CVE-2026-95818 | MEDIUM | libc6 | 2.41-12+deb13u3 | None reported |
| CVE-2010-4756 | LOW | libc6 | 2.41-12+deb13u3 | None reported |
| CVE-2018-20796 | LOW | libc6 | 2.41-12+deb13u3 | None reported |
| CVE-2019-1010022 | LOW | libc6 | 2.41-12+deb13u3 | None reported |
| CVE-2019-1010023 | LOW | libc6 | 2.41-12+deb13u3 | None reported |
| CVE-2019-1010024 | LOW | libc6 | 2.41-12+deb13u3 | None reported |
| CVE-2019-1010025 | LOW | libc6 | 2.41-12+deb13u3 | None reported |
| CVE-2019-9192 | LOW | libc6 | 2.41-12+deb13u3 | None reported |
| CVE-2026-97399 | LOW | libc6 | 2.41-12+deb13u3 | None reported |
| CVE-2026-100290 | UNKNOWN | libc6 | 2.41-12+deb13u3 | None reported |
| CVE-2026-115819 | UNKNOWN | libc6 | 2.41-12+deb13u3 | None reported |

Rebuild/rescan against current databases before deployment. Review remaining findings,
including available libc/certificate patches; no risk acceptance is implied. Separate-VM
containment, provider handling, live execution and owner pilot remain unexecuted.
