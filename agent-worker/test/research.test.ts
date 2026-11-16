import { expect, test, spyOn } from 'bun:test';
import { InvestigationBudget } from '../src/budget';
import { PUBLIC_TOPICS, publicResearchTool, publicResults } from '../src/research';

test('research never forwards free text and never follows returned URLs', async () => {
  const request = spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({ results: [] })));
  try {
    const budget = new InvestigationBudget();
    const tool = publicResearchTool('synthetic', budget, async () => {});
    await tool.execute('id', { topic: 'MELBOURNE_WEATHER', query: 'private payroll sentinel' }, undefined, undefined, {} as never);
    expect(request.mock.calls[0][0]).toBe('https://api.deepseek.com/v1/tools/search');
    expect(String(request.mock.calls[0][1]?.body)).toBe(JSON.stringify({ query: PUBLIC_TOPICS.MELBOURNE_WEATHER, count: 5, need_content: false }));
    expect(budget.reservedAud).toBe(0.002);
    await expect(tool.execute('id', { topic: 'private payroll sentinel' }, undefined, undefined, {} as never)).rejects.toThrow('Unsupported');
    expect(request).toHaveBeenCalledTimes(1);
  } finally { request.mockRestore(); }
});

test('citation output rejects non-public and non-HTTPS destinations', () => {
  const results = publicResults({ results: [
    { title: 'private', url: 'http://127.0.0.1/admin' },
    { title: 'credentials', url: 'https://a:b@vic.gov.au/' },
    { title: 'suffix attack', url: 'https://vic.gov.au.attacker.test/' },
    { title: 'official', url: 'https://www.vic.gov.au/public-holidays', summary: 'Public dates' },
  ] });
  expect(results).toEqual([{ title: 'official', url: 'https://www.vic.gov.au/public-holidays', summary: 'Public dates' }]);
});


test('one malformed citation does not discard other valid public evidence', () => {
  expect(publicResults({ results: [
    { title: 'malformed', url: 'not a URL' },
    { title: 'official', url: 'https://www.bom.gov.au/', snippet: 'Public weather' },
  ] })).toEqual([{ title: 'official', url: 'https://www.bom.gov.au/', summary: 'Public weather' }]);
});

test('search failure retains the reservation and does not publish sources', async () => {
  const request = spyOn(globalThis, 'fetch').mockResolvedValue(new Response('private upstream sentinel', { status: 429 }));
  try {
    const budget = new InvestigationBudget();
    const tool = publicResearchTool('synthetic', budget, async () => {});
    await expect(tool.execute('id', { topic: 'MELBOURNE_WEATHER' }, undefined, undefined, {} as never))
      .rejects.toThrow('Public research unavailable (429)');
    expect(budget.reservedAud).toBe(0.002);
    expect(request).toHaveBeenCalledTimes(1);
  } finally { request.mockRestore(); }
});

test('revoked authorization after search prevents citation publication', async () => {
  const request = spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({ results: [
    { title: 'Official', url: 'https://www.bom.gov.au/' },
  ] })));
  let checks = 0;
  try {
    const tool = publicResearchTool('synthetic', new InvestigationBudget(), async () => {
      if (++checks === 2) throw new Error('Authorization revoked');
    });
    await expect(tool.execute('id', { topic: 'MELBOURNE_WEATHER' }, undefined, undefined, {} as never))
      .rejects.toThrow('Authorization revoked');
    expect(checks).toBe(2);
  } finally { request.mockRestore(); }
});
