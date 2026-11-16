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
