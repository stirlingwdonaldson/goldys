import type { AgentSession } from '@earendil-works/pi-coding-agent';

export interface ModelChargeBound {
  provider: string;
  model: string;
  /** Reviewed worst-case AUD charge for a full-context request at maxOutputTokens. */
  requestAud: number;
  maxOutputTokens: number;
}

/** Conservative reservations include failures/retries; unknown prices never mean free. */
export class InvestigationBudget {
  private reservedMicros = 0;
  exhausted = false;
  constructor(private readonly limitMicros = 5_000_000, private readonly persist?: (id: string, kind: 'MODEL' | 'SEARCH', aud: number) => Promise<void>) {
    if (!Number.isSafeInteger(limitMicros) || limitMicros < 0 || limitMicros > 5_000_000) {
      throw new Error('Invalid investigation budget');
    }
  }
  reserve(aud: number): void {
    if (!Number.isFinite(aud) || aud <= 0) throw new Error('A reviewed positive charge bound is required');
    const micros = Math.ceil(aud * 1_000_000);
    if (!Number.isSafeInteger(micros) || this.reservedMicros + micros > this.limitMicros) {
      this.exhausted = true;
      throw new Error('Investigation charge limit reached');
    }
    this.reservedMicros += micros;
  }
  async reserveRequest(aud: number, kind: 'MODEL' | 'SEARCH'): Promise<void> {
    this.reserve(aud);
    await this.persist?.(crypto.randomUUID(), kind, aud);
  }
  get reservedAud(): number { return this.reservedMicros / 1_000_000; }
}

/** Pi uses this same stream function for turns and compaction. Install before prompting. */
export function guardModelRequests(
  session: AgentSession, bound: ModelChargeBound, budget: InvestigationBudget,
  authorize: () => Promise<void>,
): void {
  if (!Number.isSafeInteger(bound.maxOutputTokens) || bound.maxOutputTokens < 1
    || !Number.isFinite(bound.requestAud) || bound.requestAud <= 0) throw new Error('Invalid model charge bound');
  const stream = session.agent.streamFunction;
  session.agent.streamFunction = async (model, context, options) => {
    if (model.provider !== bound.provider || model.id !== bound.model) throw new Error('Unpriced model request');
    await authorize();
    options?.signal?.throwIfAborted();
    await budget.reserveRequest(bound.requestAud, 'MODEL');
    return stream(model, context, { ...options, maxTokens: bound.maxOutputTokens, maxRetries: 0 });
  };
}
