# syntax=docker/dockerfile:1
# A plain, headed Chromium for the OpenTable assisted sign-in. An owner signs in to GuestCenter
# through a live view relayed by the backend; the backend then drives this browser over the
# DevTools protocol. Deliberately no stealth patches or fingerprint spoofing: a human signs in.
#
# Reachable only on the internal `opentable-cdp` network (see docker-compose*.yml). DevTools is
# full control of this browser and its OpenTable session, so never publish its port.
FROM debian:bookworm-slim

RUN apt-get update \
 && apt-get install -y --no-install-recommends \
      chromium xvfb xauth socat bash ca-certificates \
      fonts-noto fonts-noto-color-emoji \
 && rm -rf /var/lib/apt/lists/*

RUN useradd --system --create-home --uid 1002 browser \
 && mkdir -p /profile \
 && chown browser:browser /profile

COPY entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod 0755 /usr/local/bin/entrypoint.sh

USER browser
VOLUME ["/profile"]
EXPOSE 9223
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
