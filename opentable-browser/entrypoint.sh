#!/usr/bin/env bash
# Runs headed Chromium on a virtual display plus a socat relay for DevTools, and exits if either
# stops so Docker restarts the pair together.
set -euo pipefail

# A crash can leave Chromium's profile lock behind, which would stop it starting again.
rm -f /profile/SingletonLock /profile/SingletonSocket /profile/SingletonCookie

# Headed Chromium binds DevTools to 127.0.0.1 whatever --remote-debugging-address says, so relay
# it to the container network on 9223. The backend connects by container IP (DevTools rejects
# hostname Host headers), and the port is reachable only on the internal opentable-cdp network.
socat TCP-LISTEN:9223,fork,reuseaddr TCP:127.0.0.1:9222 &

# --no-sandbox: Chromium's sandbox needs user namespaces that Docker's default seccomp profile
# blocks. The browser only ever visits OpenTable for this one account, which bounds the risk.
# --remote-allow-origins=*: DevTools WebSocket origin check; safe only because 9223 is private.
xvfb-run --auto-servernum --server-args="-screen 0 1280x800x24" \
  chromium \
    --no-sandbox \
    --no-first-run \
    --no-default-browser-check \
    --disable-features=Translate,MediaRouter \
    --user-data-dir=/profile \
    --remote-debugging-port=9222 \
    --remote-allow-origins='*' \
    --window-size=1280,800 \
    --window-position=0,0 \
    about:blank &

# Exit when either process exits (bash's wait -n), so restart: unless-stopped revives both.
wait -n
exit 1
