"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { CheckCircle2, Loader2, LogOut, MonitorSmartphone } from "lucide-react";
import { useApi } from "@/lib/demo-mode";
import {
  isApiError,
  type OpenTableFrame,
  type OpenTableInput,
  type OpenTableSessionState,
  type OpenTableSyncMode,
} from "@/lib/api";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { PermissionDenied } from "@/components/states/permission-denied";
import { cn } from "@/lib/utils";

/** Keys sent as key events; everything printable goes as text. Mirrors the server allow-list. */
const KEY_EVENTS = new Set([
  "Enter",
  "Backspace",
  "Tab",
  "Escape",
  "Delete",
  "ArrowUp",
  "ArrowDown",
  "ArrowLeft",
  "ArrowRight",
  "Home",
  "End",
]);
const MAX_TEXT = 256;
const MOVE_INTERVAL_MS = 80;

/** Maps a pointer position on the displayed frame to the remote page's CSS pixels. */
export function toRemotePoint(
  clientX: number,
  clientY: number,
  rect: { left: number; top: number; width: number; height: number },
  frame: { width: number; height: number },
): { x: number; y: number } {
  const x = ((clientX - rect.left) / rect.width) * frame.width;
  const y = ((clientY - rect.top) / rect.height) * frame.height;
  return {
    x: Math.round(Math.min(Math.max(x, 0), frame.width)),
    y: Math.round(Math.min(Math.max(y, 0), frame.height)),
  };
}

/** CDP modifier bitmask: Alt=1, Ctrl=2, Meta=4, Shift=8. */
function modifiers(e: { altKey: boolean; ctrlKey: boolean; metaKey: boolean; shiftKey: boolean }) {
  return (e.altKey ? 1 : 0) | (e.ctrlKey ? 2 : 0) | (e.metaKey ? 4 : 0) | (e.shiftKey ? 8 : 0);
}

/** What a keydown should become: a key event, typed text, or nothing (shortcuts stay local). */
export function classifyKey(e: {
  key: string;
  ctrlKey: boolean;
  metaKey: boolean;
  altKey: boolean;
}): "key" | "text" | null {
  if (KEY_EVENTS.has(e.key)) return "key";
  if (e.key.length === 1 && !e.ctrlKey && !e.metaKey) return "text";
  return null;
}

function buttonName(button: number): "left" | "middle" | "right" {
  return button === 1 ? "middle" : button === 2 ? "right" : "left";
}

/**
 * Sync OpenTable: shows the VM's browser at the GuestCenter login, relays the owner's clicks and
 * typing to it, and closes the loop once the backend detects the sign-in and starts the sync run.
 * Nothing typed here is logged or stored; it goes straight to the remote browser.
 */
export function OpenTableSyncDialog({
  open,
  onOpenChange,
  onSynced,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Called when a sync run finishes, so the page can refresh connector status. */
  onSynced: () => void;
}) {
  const api = useApi();
  const [session, setSession] = useState<OpenTableSessionState | null>(null);
  const [frame, setFrame] = useState<OpenTableFrame | null>(null);
  const [problem, setProblem] = useState<"not-configured" | "not-permitted" | "error" | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [mode, setMode] = useState<OpenTableSyncMode>("INCREMENTAL");
  const [starting, setStarting] = useState(false);
  const imageRef = useRef<HTMLImageElement>(null);
  const viewRef = useRef<HTMLDivElement>(null);
  const lastMove = useRef(0);
  // The previous state seen on the stream: onSynced fires only on an observed SYNCING → DONE/FAILED
  // transition, not when reopening the dialog replays an earlier sync's final state.
  const previousStateRef = useRef<string | null>(null);
  // Held in a ref so a new callback identity from the parent never reopens the stream.
  const onSyncedRef = useRef(onSynced);
  useEffect(() => {
    onSyncedRef.current = onSynced;
  }, [onSynced]);

  // Load state and follow the stream while the dialog is open.
  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    previousStateRef.current = null;
    setProblem(null);
    api
      .getOpenTableSession()
      .then((s) => {
        if (!cancelled) setSession(s);
      })
      .catch((e: unknown) => {
        if (cancelled) return;
        if (isApiError(e) && e.code === "NOT_PERMITTED") setProblem("not-permitted");
        else if (isApiError(e) && e.code === "NOT_CONFIGURED") setProblem("not-configured");
        else {
          setProblem("error");
          setErrorMessage(isApiError(e) ? e.message : null);
        }
      });
    const unsubscribe = api.watchOpenTableSession({
      onState: (s) => {
        if (cancelled) return;
        setSession(s);
        if (s.state !== "LOGIN_REQUIRED") setFrame(null);
        const finished = s.state === "DONE" || s.state === "FAILED";
        if (finished && previousStateRef.current === "SYNCING") onSyncedRef.current();
        previousStateRef.current = s.state;
      },
      onFrame: (f) => {
        if (!cancelled) setFrame(f);
      },
      onClosed: () => {},
    });
    return () => {
      cancelled = true;
      unsubscribe();
    };
  }, [api, open]);

  const send = useCallback(
    (input: OpenTableInput) => {
      // Fire-and-forget: a dropped keystroke is better than blocking typing on a round trip.
      api.sendOpenTableInput(input).catch(() => {});
    },
    [api],
  );

  // Wheel needs a non-passive listener to stop the dialog scrolling instead.
  useEffect(() => {
    const image = imageRef.current;
    if (!image || !frame) return;
    const onWheel = (e: WheelEvent) => {
      e.preventDefault();
      const p = toRemotePoint(e.clientX, e.clientY, image.getBoundingClientRect(), frame);
      send({ type: "wheel", ...p, deltaX: e.deltaX, deltaY: e.deltaY });
    };
    image.addEventListener("wheel", onWheel, { passive: false });
    return () => image.removeEventListener("wheel", onWheel);
  }, [frame, send]);

  async function start() {
    setStarting(true);
    try {
      const s = await api.startOpenTableSession(mode);
      setSession(s);
      // Focus the view so typing goes to the remote page straight away.
      requestAnimationFrame(() => viewRef.current?.focus());
    } catch (e) {
      setProblem("error");
      setErrorMessage(isApiError(e) ? e.message : null);
    } finally {
      setStarting(false);
    }
  }

  async function close(signOut: boolean) {
    // Leaving mid-sign-in ends the view; once syncing, the run carries on without us.
    if (signOut || session?.state === "LOGIN_REQUIRED") {
      await api.endOpenTableSession(signOut).catch(() => {});
    }
    onOpenChange(false);
  }

  function pointer(e: React.PointerEvent<HTMLImageElement>, action: "down" | "up" | "move") {
    if (!frame) return;
    if (action === "move") {
      const now = performance.now();
      if (now - lastMove.current < MOVE_INTERVAL_MS) return;
      lastMove.current = now;
    } else {
      e.preventDefault();
      viewRef.current?.focus();
    }
    const p = toRemotePoint(e.clientX, e.clientY, e.currentTarget.getBoundingClientRect(), frame);
    send({
      type: "mouse",
      action,
      ...p,
      button: action === "move" ? "none" : buttonName(e.button),
      clickCount: action === "move" ? 0 : 1,
    });
  }

  function keyDown(e: React.KeyboardEvent<HTMLDivElement>) {
    const kind = classifyKey(e);
    if (kind === null) return; // Let paste/copy shortcuts work locally (paste is relayed below).
    e.preventDefault();
    if (kind === "key") send({ type: "key", action: "down", key: e.key, modifiers: modifiers(e) });
    else send({ type: "text", text: e.key });
  }

  function keyUp(e: React.KeyboardEvent<HTMLDivElement>) {
    if (classifyKey(e) === "key") {
      e.preventDefault();
      send({ type: "key", action: "up", key: e.key, modifiers: modifiers(e) });
    }
  }

  function paste(e: React.ClipboardEvent<HTMLDivElement>) {
    e.preventDefault();
    const text = e.clipboardData.getData("text/plain");
    for (let i = 0; i < text.length; i += MAX_TEXT) {
      send({ type: "text", text: text.slice(i, i + MAX_TEXT) });
    }
  }

  const state = session?.state ?? "DISCONNECTED";
  const idle = state === "DISCONNECTED" || state === "DONE" || state === "FAILED";

  return (
    <Dialog open={open} onOpenChange={(next) => (next ? onOpenChange(true) : void close(false))}>
      <DialogContent className="max-w-5xl">
        <DialogHeader>
          <DialogTitle>Sync OpenTable</DialogTitle>
          <DialogDescription>
            Sign in to OpenTable below. Once you&apos;re in, the sync runs on its own. You can
            close this window and carry on.
          </DialogDescription>
        </DialogHeader>

        {problem === "not-permitted" ? (
          <PermissionDenied
            subject="the OpenTable sign-in"
            message="Only owners can sign in to OpenTable from here."
          />
        ) : problem === "not-configured" ? (
          <Alert>
            <MonitorSmartphone aria-hidden="true" />
            <AlertTitle>Assisted sync isn&apos;t switched on here</AlertTitle>
            <AlertDescription>
              This server isn&apos;t running the OpenTable browser yet. Use Upload CSV on the
              OpenTable card instead.
            </AlertDescription>
          </Alert>
        ) : problem === "error" ? (
          <Alert variant="destructive">
            <AlertTitle>Couldn&apos;t reach the OpenTable browser</AlertTitle>
            <AlertDescription>
              {errorMessage ?? "Something went wrong. Try again in a minute."}
            </AlertDescription>
          </Alert>
        ) : (
          <div className="flex flex-col gap-3">
            <StateBanner session={session} />

            {state === "LOGIN_REQUIRED" ? (
              <div
                ref={viewRef}
                tabIndex={0}
                role="application"
                aria-label="OpenTable sign-in. Click to focus, then type as normal."
                onKeyDown={keyDown}
                onKeyUp={keyUp}
                onPaste={paste}
                className="overflow-hidden rounded-lg border bg-muted outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                {frame ? (
                  // eslint-disable-next-line @next/next/no-img-element -- live data: URL frames, not a static asset
                  <img
                    ref={imageRef}
                    src={`data:image/jpeg;base64,${frame.jpegBase64}`}
                    alt="Live view of the OpenTable sign-in page"
                    draggable={false}
                    style={{ aspectRatio: `${frame.width} / ${frame.height}` }}
                    className="block w-full cursor-default select-none"
                    onPointerDown={(e) => pointer(e, "down")}
                    onPointerUp={(e) => pointer(e, "up")}
                    onPointerMove={(e) => pointer(e, "move")}
                    onContextMenu={(e) => e.preventDefault()}
                  />
                ) : (
                  <div className="flex aspect-[16/10] items-center justify-center text-sm text-muted-foreground">
                    <Loader2 className="mr-2 size-4 animate-spin" aria-hidden="true" />
                    Opening OpenTable…
                  </div>
                )}
              </div>
            ) : null}

            {idle ? (
              <fieldset className="flex flex-wrap items-center gap-2">
                <legend className="sr-only">What to sync</legend>
                <Button
                  type="button"
                  size="sm"
                  variant={mode === "INCREMENTAL" ? "default" : "outline"}
                  aria-pressed={mode === "INCREMENTAL"}
                  onClick={() => setMode("INCREMENTAL")}
                >
                  Recent and upcoming
                </Button>
                <Button
                  type="button"
                  size="sm"
                  variant={mode === "FULL" ? "default" : "outline"}
                  aria-pressed={mode === "FULL"}
                  onClick={() => setMode("FULL")}
                >
                  Full history
                </Button>
                <span className="text-xs text-muted-foreground">
                  {mode === "FULL"
                    ? "Everything since 2020. Takes about 5 minutes."
                    : "Last 60 days, upcoming bookings and recent reviews. Under a minute."}
                </span>
              </fieldset>
            ) : null}
          </div>
        )}

        <DialogFooter className="gap-2 sm:justify-between">
          <Button type="button" variant="ghost" size="sm" onClick={() => void close(true)}>
            <LogOut aria-hidden="true" />
            Sign out of OpenTable
          </Button>
          <div className="flex gap-2">
            <Button type="button" variant="outline" onClick={() => void close(false)}>
              Close
            </Button>
            {problem === null && idle ? (
              <Button type="button" onClick={() => void start()} disabled={starting}>
                {starting ? "Starting…" : "Start sync"}
              </Button>
            ) : null}
          </div>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function StateBanner({ session }: { session: OpenTableSessionState | null }) {
  if (!session || session.state === "DISCONNECTED") {
    return (
      <p className="text-sm text-muted-foreground">
        {session?.detail ??
          "Choose what to sync, then Start sync. If OpenTable still has you signed in, it starts straight away."}
      </p>
    );
  }
  if (session.state === "LOGIN_REQUIRED") {
    return (
      <p className="text-sm">
        Click the page below, then sign in as you normally would.
        {session.detail ? <span className="text-muted-foreground"> {session.detail}</span> : null}
      </p>
    );
  }
  if (session.state === "SYNCING") {
    return (
      <Alert>
        <Loader2 className="animate-spin" aria-hidden="true" />
        <AlertTitle>Signed in. Syncing now</AlertTitle>
        <AlertDescription>You can close this window; the sync keeps going.</AlertDescription>
      </Alert>
    );
  }
  const ok = session.state === "DONE";
  return (
    <Alert variant={ok ? "default" : "destructive"} className={cn(ok && "border-0 bg-status-success-soft")}>
      {ok ? <CheckCircle2 aria-hidden="true" /> : null}
      <AlertTitle>{ok ? "Sync complete" : "Sync didn't finish cleanly"}</AlertTitle>
      <AlertDescription>
        {session.detail ?? (ok ? "OpenTable data is up to date." : "See the OpenTable card for details.")}
      </AlertDescription>
    </Alert>
  );
}
