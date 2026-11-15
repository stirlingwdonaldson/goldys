import { Type } from 'typebox';
import type { AgentSession, ToolDefinition } from '@earendil-works/pi-coding-agent';
import { guardModelRequests, InvestigationBudget, type ModelChargeBound } from './budget';
import { snapshot, stopSession, type NativeSnapshot } from './session';

export interface Claim {
  id: string;
  threadId: string;
  fence: string;
  prompt: string;
  deadline: string;
  revision: number;
  snapshot: NativeSnapshot | null;
}
export interface FollowUp { id: string; prompt: string; state: string }
export interface JobHost {
  settle(claim: Claim, revision: number, state: NativeSnapshot): Promise<FollowUp | null>;
  heartbeat(claim: Claim): Promise<void>;
  publish(claim: Claim, revision: number, state: NativeSnapshot, completed: boolean): Promise<void>;
}

/** One claim, one native session. Failure escapes to supervision; never replay a claim. */
export async function runClaim(claim: Claim, host: JobHost, session: AgentSession, chargeBound: ModelChargeBound, signal?: AbortSignal): Promise<void> {
  try {
    guardModelRequests(session, chargeBound, new InvestigationBudget(), () => host.heartbeat(claim));
  } catch (error) {
    session.dispose();
    throw error;
  }
  let revision = claim.revision;
  let failure: unknown;
  let stopped = false;
  let publications = Promise.resolve();
  const deadline = Date.parse(claim.deadline);
  if (!Number.isFinite(deadline) || deadline <= Date.now()) {
    session.dispose();
    throw new Error('Investigation deadline ended');
  }
  const stop = (error: unknown) => {
    failure ??= error;
    stopped = true;
    void stopSession(session);
  };
  // Serial persistence retains native tree ordering; no separately writable transcript.
  const persist = (completed: boolean) => {
    const state = snapshot(session.sessionManager);
    publications = publications.then(async () => {
      if (stopped) return;
      await host.publish(claim, revision, state, completed);
      revision++;
    }).catch(stop);
  };
  let heartbeatRequest: Promise<void> | null = null;
  const heartbeat = () => {
    if (heartbeatRequest || stopped) return;
    heartbeatRequest = host.heartbeat(claim).catch(stop).finally(() => { heartbeatRequest = null; });
  };
  const unsubscribe = session.subscribe(event => {
    // Pi 1.5.2 emits message_end before appending it to SessionManager. Defer one
    // microtask so the snapshot includes the completed message, not the prior tree.
    if (event.type === 'message_end') queueMicrotask(() => persist(false));
  });
  let timer = setInterval(() => { void heartbeat(); }, 5000);
  const onAbort = () => stop(new Error("Worker stopped"));
  signal?.addEventListener("abort", onAbort, { once: true });
  const deadlineTimer = setTimeout(() => stop(new Error('Investigation deadline ended')), deadline - Date.now());
  try {
    signal?.throwIfAborted();
    await host.heartbeat(claim);
    signal?.throwIfAborted();
    let prompt: string | null = claim.prompt;
    while (prompt !== null) {
      await session.prompt(prompt);
      await publications;
      if (failure) throw failure;
      requireCompletedTurn(session);
      clearInterval(timer);
      await heartbeatRequest;
      if (failure) throw failure;
      // Spring atomically completes or takes a durable command. Pi owns every turn in
      // this same native session; pending commands are not a second transcript.
      const next = await host.settle(claim, revision, snapshot(session.sessionManager));
      revision++;
      if (failure) throw failure;
      prompt = next?.prompt ?? null;
      if (prompt !== null) timer = setInterval(() => { void heartbeat(); }, 5000);
    }
  } finally {
    stopped = true;
    clearInterval(timer);
    clearTimeout(deadlineTimer);
    signal?.removeEventListener("abort", onAbort);
    unsubscribe();
    await stopSession(session);
    session.dispose();
  }
}

function requireCompletedTurn(session: AgentSession): void {
  const last = session.agent.state.messages.at(-1);
  if (last?.role !== 'assistant' || last.stopReason === 'error' || last.stopReason === 'aborted') {
    throw new Error('Pi investigation interrupted');
  }
}

class WorkspaceRequestError extends Error {
  constructor(readonly status: number) { super(`Workspace request rejected (${status})`); }
}

/** Fixed private Spring origin; model input can never choose a URL or authorization header. */
export class SpringJobs implements JobHost {
  constructor(private readonly origin: string, private readonly token: string) {
    const url = new URL(origin);
    if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash || url.pathname !== '/') {
      throw new Error('Worker requires an HTTPS Spring origin');
    }
    if (token.length < 32) throw new Error('Worker token is not configured');
  }
  private async post(path: string, body: unknown, signal?: AbortSignal): Promise<Response> {
    const response = await fetch(new URL(`/api/workspace-worker/${path}`, this.origin), {
      method: 'POST', redirect: 'error', signal: signal ? AbortSignal.any([signal, AbortSignal.timeout(10000)]) : AbortSignal.timeout(10000),
      headers: { 'Content-Type': 'application/json', 'X-Workspace-Token': this.token },
      body: JSON.stringify(body),
    });
    if (!response.ok) throw new WorkspaceRequestError(response.status);
    return response;
  }
  async claim(): Promise<Claim | null> {
    const response = await this.post('claim', {});
    const text = await response.text();
    if (!text) return null;
    const value = JSON.parse(text) as Claim;
    if (![value.id, value.threadId, value.fence].every(id => /^[0-9a-f-]{36}$/.test(id))
      || typeof value.prompt !== 'string' || value.prompt.length > 16000
      || !Number.isSafeInteger(value.revision) || value.revision < 0
      || !Number.isFinite(Date.parse(value.deadline))) throw new Error('Invalid workspace claim');
    return value;
  }
  async tools(claim: Claim): Promise<ToolDefinition[]> {
    const response = await this.post(`${claim.id}/tools`, { fence: claim.fence });
    const specs = await response.json() as { id: string; name: string; description: string; parameters: Record<string, unknown> }[];
    if (!Array.isArray(specs) || specs.length > 32) throw new Error('Invalid reporting catalogue');
    return specs.map(spec => {
      if (!/^[A-Z_]+$/.test(spec.id) || !/^goldys_[a-z0-9_]+$/.test(spec.name)
        || typeof spec.description !== 'string' || spec.parameters?.type !== 'object') {
        throw new Error('Invalid reporting tool');
      }
      return {
        name: spec.name, label: spec.name, description: spec.description,
        parameters: Type.Unsafe(spec.parameters),
        execute: async (_callId, arguments_, signal) => {
          try {
            const result = await this.post(`${claim.id}/reporting`, {
              fence: claim.fence, tool: spec.id, arguments: arguments_,
            }, signal);
            const details: unknown = await result.json();
            return { content: [{ type: 'text' as const, text: JSON.stringify(details) }], details };
          } catch (error) {
            if (!(error instanceof WorkspaceRequestError) || error.status !== 403) throw error;
            return { content: [{ type: 'text' as const, text: 'Not permitted to access this reporting result.' }],
              details: { status: 'NOT_PERMITTED' } };
          }
        },
      };
    });
  }
  async interrupt(claim: Claim): Promise<void> {
    await this.post(`${claim.id}/interrupt`, { fence: claim.fence });
  }
  async settle(claim: Claim, revision: number, snapshot: NativeSnapshot): Promise<FollowUp | null> {
    const response = await this.post(`${claim.id}/settle`, { fence: claim.fence, revision, snapshot, completed: false });
    const body = await response.text();
    if (!body) return null;
    const next = JSON.parse(body) as FollowUp;
    if (typeof next.prompt !== 'string' || !next.prompt.trim() || next.prompt.length > 16000 || next.state !== 'TAKEN') {
      throw new Error('Invalid workspace follow-up');
    }
    return next;
  }
  async heartbeat(claim: Claim): Promise<void> { await this.post(`${claim.id}/heartbeat`, { fence: claim.fence }); }
  async publish(claim: Claim, revision: number, snapshot: NativeSnapshot, completed: boolean): Promise<void> {
    await this.post(`${claim.id}/snapshot`, { fence: claim.fence, revision, snapshot, completed });
  }
}
