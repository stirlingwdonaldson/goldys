import { readFile } from 'node:fs/promises';
import { join } from 'node:path';
import type { ModelRuntime } from '@earendil-works/pi-coding-agent';

/** Reviewed 16 Nov 2026: peak USD prices, 2 AUD/USD conservative bound.
 * Full 1M input + 8192 output at 0.30/1.20 USD per million is below A$0.65.
 * Provider-side search is never enabled by this model registration.
 */
export const DEEPSEEK_PREVIEW = {
  provider: 'goldys-deepseek', model: 'deepseek-flash', requestAud: 0.65, maxOutputTokens: 8192,
} as const;

export function registerDeepSeek(runtime: ModelRuntime): void {
  runtime.registerProvider(DEEPSEEK_PREVIEW.provider, {
    baseUrl: 'https://api.deepseek.com', api: 'openai-completions',
    models: [{ id: DEEPSEEK_PREVIEW.model, name: 'DeepSeek V4.1 Flash (reviewed preview)',
      reasoning: false, input: ['text'], contextWindow: 1_000_000, maxTokens: 8192,
      cost: { input: 0.3, output: 1.2, cacheRead: 0.006, cacheWrite: 0.3 } }],
  });
}

/** Read only the explicitly injected systemd credential; no ambient key discovery. */
export async function configuredSecret(name: string, fallback: string): Promise<string> {
  const directory = process.env.CREDENTIALS_DIRECTORY;
  const value = directory
    ? await readFile(join(directory, name), 'utf8')
    : process.env[fallback];
  if (!value?.trim() || /\s/.test(value.trim())) throw new Error('Missing or invalid model credential');
  return value.trim();
}

export const modelCredential = () => configuredSecret('model-api-key', 'GOLDYS_MODEL_API_KEY');
