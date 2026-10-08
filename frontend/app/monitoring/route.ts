/**
 * Same-origin relay for browser Sentry events (the SDK's `tunnel: "/monitoring"`).
 *
 * Why: the self-hosted Sentry is served over plain HTTP, and an HTTPS page cannot POST to an
 * http:// URL (mixed content). The browser posts here instead and this server forwards it.
 * Next's built-in `tunnelRoute` only supports sentry.io, so this is the documented manual tunnel.
 *
 * Not an open proxy: only envelopes addressed to this deployment's own DSN (same host, project
 * and public key) are forwarded, with a size cap.
 */
export const dynamic = "force-dynamic";

const MAX_ENVELOPE_BYTES = 1_000_000;

function configuredDsn(): URL | null {
  const raw = process.env.SENTRY_DSN;
  if (!raw) return null;
  try {
    return new URL(raw);
  } catch {
    return null;
  }
}

const projectIdOf = (dsn: URL) => dsn.pathname.replace(/^\/+|\/+$/g, "");

export async function POST(request: Request): Promise<Response> {
  const dsn = configuredDsn();
  // Monitoring off for this deployment: accept and drop, so the browser never retries or errors.
  if (!dsn) return new Response(null, { status: 204 });

  const body = await request.arrayBuffer();
  if (body.byteLength > MAX_ENVELOPE_BYTES) return new Response(null, { status: 413 });

  // An envelope's first line is a JSON header naming the DSN it is meant for.
  const headerLine = new TextDecoder()
    .decode(body.slice(0, Math.min(body.byteLength, 8192)))
    .split("\n", 1)[0];
  let envelopeDsn: URL;
  try {
    envelopeDsn = new URL((JSON.parse(headerLine) as { dsn?: string }).dsn ?? "");
  } catch {
    return new Response(null, { status: 400 });
  }

  const projectId = projectIdOf(dsn);
  if (
    envelopeDsn.host !== dsn.host ||
    envelopeDsn.username !== dsn.username ||
    projectIdOf(envelopeDsn) !== projectId
  ) {
    return new Response(null, { status: 403, headers: { "X-Sentry-Tunnel": "dsn-mismatch" } });
  }

  // Default to the DSN's own host; SENTRY_TUNNEL_TARGET can point at the LAN address instead
  // (e.g. http://192.168.4.36:9000) to skip the round trip through Cloudflare.
  const target = (process.env.SENTRY_TUNNEL_TARGET || `${dsn.protocol}//${dsn.host}`).replace(
    /\/+$/,
    "",
  );
  try {
    const upstream = await fetch(`${target}/api/${projectId}/envelope/`, {
      method: "POST",
      body,
      headers: {
        "Content-Type": "application/x-sentry-envelope",
        // Authenticate explicitly with the DSN's public key. Relying on Relay to read the DSN out
        // of the envelope header alone was rejected (403) by the self-hosted instance.
        "X-Sentry-Auth": `Sentry sentry_version=7, sentry_key=${dsn.username}, sentry_client=goldys-tunnel/1.0`,
      },
    });
    // Pass Sentry's own (non-sensitive) reason through, and mark the response as upstream so a
    // 403 from Sentry can be told apart from this route's own DSN-mismatch 403.
    return new Response(await upstream.text(), {
      status: upstream.status,
      headers: { "X-Sentry-Tunnel": "upstream" },
    });
  } catch {
    // Sentry unreachable: losing a monitoring event must never surface as an app error.
    return new Response(null, { status: 202, headers: { "X-Sentry-Tunnel": "unreachable" } });
  }
}
