import { expect, test } from 'bun:test';
import { SpringJobs, type Claim } from '../src/jobs';

const claim: Claim = { id: crypto.randomUUID(), threadId: crypto.randomUUID(), fence: crypto.randomUUID(),
  prompt: 'Synthetic', deadline: new Date(Date.now() + 10000).toISOString(), revision: 0, snapshot: null };

test('worker origin rejects insecure, credential-bearing and path-bearing URLs', () => {
  for (const origin of ['http://spring.test', 'https://user:pass@spring.test', 'https://spring.test/path', 'https://spring.test/?x=y']) {
    expect(() => new SpringJobs(origin, 'x'.repeat(32))).toThrow();
  }
  expect(() => new SpringJobs('https://spring.test', '')).toThrow();
});

test('tool bridge binds the authenticated job and fence, never a model-supplied URL or role', async () => {
  const original = globalThis.fetch;
  const requests: { url: string; init: RequestInit }[] = [];
  globalThis.fetch = (async (url: string | URL, init: RequestInit) => {
    requests.push({ url: String(url), init });
    if (String(url).endsWith('/tools')) return Response.json([{ id: 'GET_METRIC', name: 'goldys_get_metric',
      description: 'Synthetic governed metric', parameters: { type: 'object', properties: {} } }]);
    return Response.json({ widget: null, notices: ['No source data'], provenance: [] });
  }) as typeof fetch;
  try {
    const host = new SpringJobs('https://spring.test', 'x'.repeat(32));
    const tools = await host.tools(claim);
    const result = await tools[0].execute('synthetic-call', { metric: 'sales.net' }, new AbortController().signal, undefined, {} as never);
    expect(result.details).toEqual({ widget: null, notices: ['No source data'], provenance: [] });
    expect(requests[1].url).toBe(`https://spring.test/api/workspace-worker/${claim.id}/reporting`);
    expect(JSON.parse(requests[1].init.body as string)).toEqual({ fence: claim.fence, tool: 'GET_METRIC', arguments: { metric: 'sales.net' } });
    expect(requests[1].init.redirect).toBe('error');
  } finally { globalThis.fetch = original; }
});
