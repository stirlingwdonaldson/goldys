#!/usr/bin/env bash
# Stages a new certificate/key pair only; never changes active TLS or services.
set -euo pipefail
out=${1:?Usage: renew-certificate.sh /absolute/new/private-directory}
[[ "$out" = /* && ! -e "$out" ]] || { echo 'Use a new absolute directory.' >&2; exit 1; }
umask 077
mkdir -m 0700 -- "$out"
openssl req -x509 -newkey rsa:3072 -nodes -days 825 -subj /CN=localhost \
  -addext 'subjectAltName=DNS:localhost,IP:127.0.0.1,IP:::1' \
  -keyout "$out/server.key" -out "$out/server.crt" 2>/dev/null
openssl x509 -in "$out/server.crt" -noout -checkend 2592000 >/dev/null
openssl verify -CAfile "$out/server.crt" -verify_hostname localhost "$out/server.crt" >/dev/null
chmod 0644 "$out/server.crt"
echo 'Certificate staged. Follow the approved paused/drained renewal procedure before activation.'
