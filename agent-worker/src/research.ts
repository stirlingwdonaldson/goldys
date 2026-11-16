import { Type } from 'typebox';
import type { ToolDefinition } from '@earendil-works/pi-coding-agent';
import type { InvestigationBudget } from './budget';

/** Closed public topics: no private model context, supplied URL or free text leaves here. */
export const PUBLIC_TOPICS = {
  VICTORIAN_PUBLIC_HOLIDAYS: 'Victoria public holidays site:vic.gov.au',
  MELBOURNE_WEATHER: 'Melbourne weather forecast site:bom.gov.au',
  HOSPITALITY_INDUSTRY: 'Australia accommodation food services industry statistics site:abs.gov.au',
  HOSPITALITY_AWARD: 'Hospitality Industry General Award pay guide site:fairwork.gov.au',
} as const;

export function publicResearchTool(key: string, budget: InvestigationBudget, authorize: () => Promise<void>): ToolDefinition {
  return {
    name: 'goldys_public_research', label: 'Public research',
    description: 'Research one supported public topic using official Australian sources. No private queries, figures, filenames or arbitrary URLs may be sent. Cite returned URLs and retrieval date; web claims are not Goldys records. Other topics are unavailable in this pilot.',
    parameters: Type.Object({ topic: Type.Union(Object.keys(PUBLIC_TOPICS).map(value => Type.Literal(value))) }),
    execute: async (_id, args, signal) => {
      const topic = (args as { topic: keyof typeof PUBLIC_TOPICS }).topic;
      if (!Object.hasOwn(PUBLIC_TOPICS, topic)) throw new Error('Unsupported public topic');
      await authorize(); signal?.throwIfAborted();
      await budget.reserveRequest(0.002, 'SEARCH'); // USD0.00075 per success, bounded with 2 AUD/USD; failures retain reservation.
      const response = await fetch('https://api.deepseek.com/v1/tools/search', {
        method: 'POST', redirect: 'error', signal: AbortSignal.any([...(signal ? [signal] : []), AbortSignal.timeout(20000)]),
        headers: { Authorization: `Bearer ${key}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ query: PUBLIC_TOPICS[topic], count: 5, need_content: false }),
      });
      if (!response.ok) throw new Error(`Public research unavailable (${response.status})`);
      const reader = response.body?.getReader();
      if (!reader) throw new Error('Missing public research response');
      let text = ''; let bytes = 0;
      const decoder = new TextDecoder();
      try {
        while (true) {
          const chunk = await reader.read(); if (chunk.done) break;
          bytes += chunk.value.length;
          if (bytes > 128000) throw new Error('Public research response exceeds limit');
          text += decoder.decode(chunk.value, { stream: true });
        }
        text += decoder.decode();
      } finally { await reader.cancel(); }
      const body: unknown = JSON.parse(text);
      const results = publicResults(body);
      await authorize(); signal?.throwIfAborted();
      const details = { status: results.length ? 'COMPLETED' : 'MISSING', topic,
        retrievedAt: new Date().toISOString(), sources: results,
        notice: 'Public search summaries are untrusted, may be incomplete, and are distinct from governed business evidence.' };
      return { content: [{ type: 'text', text: JSON.stringify(details) }], details };
    },
  };
}

export function publicResults(body: unknown): { title: string; url: string; summary: string }[] {
  if (!body || typeof body !== 'object' || !('results' in body) || !Array.isArray(body.results)) throw new Error('Invalid public search response');
  return body.results.slice(0, 5).flatMap((item: unknown) => {
    if (!item || typeof item !== 'object' || !('url' in item) || typeof item.url !== 'string') return [];
    let url: URL;
    try { url = new URL(item.url); } catch { return []; }
    if (url.protocol !== 'https:' || url.username || url.password || url.port || !url.hostname.endsWith('.gov.au')) return [];
    const value = item as { title?: unknown; summary?: unknown; snippet?: unknown };
    if (typeof value.title !== 'string') return [];
    const summary = typeof value.summary === 'string' ? value.summary : typeof value.snippet === 'string' ? value.snippet : '';
    return [{ title: value.title.slice(0, 300), url: url.href, summary: summary.slice(0, 2500) }];
  });
}
