# Offline analysis sandbox

Install only on the authorized analysis VM. The local broker holds Docker privilege;
the Pi worker receives only its Unix socket, fixed extraction/validation scripts and
its own runtime credentials. Generated Python receives none of those credentials.

`goldys-sandbox.service` runs the broker. `goldys-pi-worker.service` starts the pinned
worker image from `/etc/goldys-agent/worker.conf`; the broker's immutable image ID is
in `/etc/goldys-agent/sandbox.conf`. Docker's `runsc` runtime uses gVisor's systrap
platform with networking disabled. AppArmor remains enabled.

Build the analysis Dockerfile and pin its resulting image ID before starting the
broker. The Ubuntu base is digest-pinned; apt packages are resolved at build time,
so archive each exact image's scan report and ID. This is not a reproducible apt snapshot.
The reviewed image includes Python, pandas, NumPy, openpyxl, XlsxWriter, matplotlib,
python-docx, reportlab, Pillow, pypdf, poppler and Tesseract.

Bounds: one execution, 60 seconds, 768 MiB RAM/no swap, one CPU, 64 tasks, read-only
root, no capabilities/privilege escalation, 128 MiB scratch, 100 MiB output tmpfs,
five inputs/outputs and 25 MiB per file. Private staging is removed after execution.
The broker keeps its slot through response serialization; large output is serialized
incrementally. A disconnect kills the complete container. Supervisor cleanup removes
labelled orphan containers and staging mounts after crashes.

`configure-egress.py` limits only the dedicated worker UID: private Spring loopback,
reviewed DeepSeek public addresses on port 443 and existing DNS resolvers. It blocks
other IPv4/IPv6 destinations. Scripts have no network namespace interfaces for egress.
Provider DNS changes require the worker's reviewed startup refresh; arbitrary fetches
are never permitted. The Docker daemon socket is never mounted into either runtime.

Run the synthetic checks on the analysis VM after the socket becomes ready:

```sh
sudo -u goldys-worker python3 /opt/goldys-agent/acceptance.py
sudo -u goldys-worker python3 /opt/goldys-agent/extraction_acceptance.py
sudo -u goldys-worker python3 /opt/goldys-agent/size_acceptance.py
sudo python3 /opt/goldys-agent/orphan_acceptance.py
```

The orphan check deliberately crashes only this broker; run it with no investigation
active. These checks do not use provider credentials. Local boundary tests:
`python3 -m unittest discover -s analysis-sandbox -p 'test_*.py'`.

Extraction is bounded and untrusted, with visible partial/OCR limitations. Output
validation runs in another disposable sandbox; it rejects active Office/PDF content,
formula-leading CSV cells, unsupported formats and filesystem links. It is not an
antivirus guarantee. Production file policy and representative extraction accuracy
still require acceptance. See [preview operations](../ops/pi-preview/README.md).
