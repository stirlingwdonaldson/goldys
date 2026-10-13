import { describe, it, expect, vi, beforeEach } from "vitest";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { ApiError } from "@/lib/api/errors";
import type {
  OpenTableInput,
  OpenTableSessionHandlers,
  OpenTableSessionState,
} from "@/lib/api";
import { OpenTableSyncDialog, classifyKey, toRemotePoint } from "./opentable-sync-dialog";

const fake = vi.hoisted(() => ({
  handlers: null as OpenTableSessionHandlers | null,
  getOpenTableSession: vi.fn(),
  startOpenTableSession: vi.fn(),
  sendOpenTableInput: vi.fn(async (input: OpenTableInput) => {
    void input;
  }),
  endOpenTableSession: vi.fn(async () => {}),
  watchOpenTableSession: vi.fn(),
}));
vi.mock("@/lib/demo-mode", () => ({ useApi: () => fake }));

function state(patch: Partial<OpenTableSessionState>): OpenTableSessionState {
  return {
    state: "DISCONNECTED",
    mode: "INCREMENTAL",
    runStatus: null,
    detail: null,
    at: "2026-10-13T00:00:00Z",
    ...patch,
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  fake.handlers = null;
  fake.getOpenTableSession.mockResolvedValue(state({}));
  fake.startOpenTableSession.mockResolvedValue(state({ state: "LOGIN_REQUIRED" }));
  fake.watchOpenTableSession.mockImplementation((h: OpenTableSessionHandlers) => {
    fake.handlers = h;
    return () => {};
  });
});

describe("toRemotePoint", () => {
  it("scales the displayed frame to the remote viewport", () => {
    const rect = { left: 100, top: 50, width: 640, height: 400 };
    expect(toRemotePoint(420, 250, rect, { width: 1280, height: 800 })).toEqual({ x: 640, y: 400 });
  });

  it("clamps points outside the frame", () => {
    const rect = { left: 0, top: 0, width: 640, height: 400 };
    expect(toRemotePoint(-20, 900, rect, { width: 1280, height: 800 })).toEqual({ x: 0, y: 800 });
  });
});

describe("classifyKey", () => {
  const plain = { ctrlKey: false, metaKey: false, altKey: false };
  it("sends editing keys as key events and printable keys as text", () => {
    expect(classifyKey({ key: "Enter", ...plain })).toBe("key");
    expect(classifyKey({ key: "Backspace", ...plain })).toBe("key");
    expect(classifyKey({ key: "a", ...plain })).toBe("text");
    expect(classifyKey({ key: "@", ...plain })).toBe("text");
  });

  it("keeps shortcuts and unlisted keys local", () => {
    expect(classifyKey({ key: "v", ...plain, metaKey: true })).toBeNull();
    expect(classifyKey({ key: "F12", ...plain })).toBeNull();
  });
});

describe("OpenTableSyncDialog", () => {
  it("explains when assisted sync isn't enabled on the server", async () => {
    fake.getOpenTableSession.mockRejectedValue(new ApiError("NOT_CONFIGURED", "not enabled"));

    render(<OpenTableSyncDialog open onOpenChange={() => {}} onSynced={() => {}} />);

    expect(await screen.findByText(/isn't switched on here/i)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Start sync" })).not.toBeInTheDocument();
  });

  it("shows an explicit locked state for non-owners", async () => {
    fake.getOpenTableSession.mockRejectedValue(new ApiError("NOT_PERMITTED", "denied"));

    render(<OpenTableSyncDialog open onOpenChange={() => {}} onSynced={() => {}} />);

    expect(await screen.findByText(/don't have access to the OpenTable sign-in/i)).toBeInTheDocument();
  });

  it("relays typing to the live view while signing in", async () => {
    render(<OpenTableSyncDialog open onOpenChange={() => {}} onSynced={() => {}} />);

    fireEvent.click(await screen.findByRole("button", { name: "Full history" }));
    fireEvent.click(screen.getByRole("button", { name: "Start sync" }));
    await waitFor(() => expect(fake.startOpenTableSession).toHaveBeenCalledWith("FULL"));
    act(() => {
      fake.handlers!.onState(state({ state: "LOGIN_REQUIRED", mode: "FULL" }));
      fake.handlers!.onFrame({ jpegBase64: "AAAA", width: 1280, height: 800 });
    });

    expect(await screen.findByAltText(/live view of the OpenTable sign-in/i)).toBeInTheDocument();
    const view = screen.getByRole("application");
    fireEvent.keyDown(view, { key: "x" });
    fireEvent.keyDown(view, { key: "Enter" });

    expect(fake.sendOpenTableInput).toHaveBeenCalledWith({ type: "text", text: "x" });
    expect(fake.sendOpenTableInput).toHaveBeenCalledWith(
      expect.objectContaining({ type: "key", action: "down", key: "Enter" }),
    );
  });

  it("reports a finished sync once and hides the live view", async () => {
    const onSynced = vi.fn();
    render(<OpenTableSyncDialog open onOpenChange={() => {}} onSynced={onSynced} />);
    await waitFor(() => expect(fake.handlers).not.toBeNull());

    act(() => fake.handlers!.onState(state({ state: "SYNCING" })));
    expect(await screen.findByText(/Signed in\. Syncing now/)).toBeInTheDocument();
    act(() => {
      fake.handlers!.onState(state({ state: "DONE", runStatus: "SUCCESS", detail: "Sync complete" }));
      fake.handlers!.onState(state({ state: "DONE", runStatus: "SUCCESS", detail: "Sync complete" }));
    });

    expect(onSynced).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole("application")).not.toBeInTheDocument();
  });

  it("doesn't report a sync when reopening replays an earlier result", async () => {
    const onSynced = vi.fn();
    render(<OpenTableSyncDialog open onOpenChange={() => {}} onSynced={onSynced} />);
    await waitFor(() => expect(fake.handlers).not.toBeNull());

    act(() => fake.handlers!.onState(state({ state: "DONE", runStatus: "SUCCESS" })));

    expect(await screen.findByText("Sync complete")).toBeInTheDocument();
    expect(onSynced).not.toHaveBeenCalled();
  });

  it("signing out clears the OpenTable login", async () => {
    const onOpenChange = vi.fn();
    render(<OpenTableSyncDialog open onOpenChange={onOpenChange} onSynced={() => {}} />);

    fireEvent.click(await screen.findByRole("button", { name: /Sign out of OpenTable/ }));

    await waitFor(() => expect(fake.endOpenTableSession).toHaveBeenCalledWith(true));
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });
});
