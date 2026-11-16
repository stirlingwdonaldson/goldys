import { expect, test } from 'bun:test';
import { ModelRuntime } from '@earendil-works/pi-coding-agent';
import { InMemoryCredentialStore } from '@earendil-works/pi-ai';
import { DEEPSEEK_PREVIEW, registerDeepSeek } from '../src/provider';

test('reviewed DeepSeek overlay is local and fixes model origin and cap', async () => {
  const runtime = await ModelRuntime.create({ credentials: new InMemoryCredentialStore(),
    modelsPath: null, refreshOnCreate: false, allowModelNetwork: false });
  registerDeepSeek(runtime);
  const model = runtime.getModel(DEEPSEEK_PREVIEW.provider, DEEPSEEK_PREVIEW.model);
  expect(model?.baseUrl).toBe('https://api.deepseek.com');
  expect(model?.maxTokens).toBe(8192);
  expect(model?.contextWindow).toBe(1_000_000);
  const worstAud = (1_000_000 * 0.3 + 8192 * 1.2) / 1_000_000 * 2;
  expect(DEEPSEEK_PREVIEW.requestAud).toBeGreaterThan(worstAud);
});
