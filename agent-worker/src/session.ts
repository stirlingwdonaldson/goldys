import {
  createAgentSession, createExtensionRuntime, SessionManager, SettingsManager,
  type AgentSession, type CreateAgentSessionOptions, type FileEntry,
  type ResourceLoader, type ToolDefinition,
} from '@earendil-works/pi-coding-agent';

export const PI_VERSION = '1.5.2' as const;
export interface NativeSnapshot {
  piVersion: typeof PI_VERSION;
  entries: FileEntry[];
  leafId: string | null;
}

/** Spring owns durable bytes; Pi alone interprets the native conversation tree. */
export function snapshot(manager: SessionManager): NativeSnapshot {
  const header = manager.getHeader();
  if (!header) throw new Error('Pi session has no header');
  // Detach cumulative event objects before an asynchronous persistence call.
  return structuredClone({ piVersion: PI_VERSION, entries: [header, ...manager.getEntries()], leafId: manager.getLeafId() });
}

/** Only accept snapshots retrieved through the authorized Spring job protocol. */
export function restore(cwd: string, saved: NativeSnapshot): SessionManager {
  if (saved.piVersion !== PI_VERSION || saved.entries[0]?.type !== 'session') {
    throw new Error('Unsupported Pi snapshot');
  }
  const ids = new Set<string>();
  for (const entry of saved.entries.slice(1)) {
    if (entry.type === 'session' || !('parentId' in entry) || ids.has(entry.id)
      || (entry.parentId !== null && !ids.has(entry.parentId))) {
      throw new Error('Invalid Pi snapshot tree');
    }
    ids.add(entry.id);
  }
  if (saved.leafId !== null && !ids.has(saved.leafId)) throw new Error('Invalid Pi snapshot leaf');
  const manager = SessionManager.inMemory(cwd, undefined, structuredClone(saved.entries));
  if (saved.leafId === null) manager.resetLeaf();
  else manager.branch(saved.leafId);
  return manager;
}

export function trustedResources(systemPrompt: string): ResourceLoader {
  return {
    getExtensions: () => ({ extensions: [], errors: [], runtime: createExtensionRuntime() }),
    getSkills: () => ({ skills: [], diagnostics: [] }),
    getPrompts: () => ({ prompts: [], diagnostics: [] }),
    getThemes: () => ({ themes: [], diagnostics: [] }),
    getAgentsFiles: () => ({ agentsFiles: [] }),
    getSystemPrompt: () => systemPrompt,
    getSystemPromptSource: () => undefined,
    getAppendSystemPrompt: () => [],
    getAppendSystemPromptSources: () => [],
    extendResources: () => {},
    reload: async () => {},
  };
}

type SessionOptions = Required<Pick<CreateAgentSessionOptions, 'cwd' | 'agentDir' | 'model' | 'modelRuntime'>> & {
  systemPrompt: string;
  tools: ToolDefinition[];
  saved?: NativeSnapshot;
};

export async function openSession(options: SessionOptions): Promise<AgentSession> {
  const names = options.tools.map(tool => tool.name);
  if (new Set(names).size !== names.length || names.some(name => !/^goldys_[a-z0-9_]+$/.test(name))) {
    throw new Error('Only exact, distinct Goldys tool names are allowed');
  }
  const { session } = await createAgentSession({
    cwd: options.cwd,
    agentDir: options.agentDir,
    model: options.model,
    modelRuntime: options.modelRuntime,
    thinkingLevel: 'off',
    resourceLoader: trustedResources(options.systemPrompt),
    tools: names,
    customTools: options.tools,
    sessionManager: options.saved ? restore(options.cwd, options.saved) : SessionManager.inMemory(options.cwd),
    settingsManager: SettingsManager.inMemory({
      retry: { enabled: false },
      compaction: { enabled: true },
      cacheWarming: 'off',
    }),
  });
  if (session.getActiveToolNames().sort().join(',') !== [...names].sort().join(',')) {
    session.dispose();
    throw new Error('Unexpected active Pi tools');
  }
  return session;
}

/** Admission must be fenced in Spring before this call; abort alone leaves queued work. */
export async function stopSession(session: AgentSession): Promise<void> {
  session.clearQueue();
  await session.abort();
  session.clearQueue();
}
