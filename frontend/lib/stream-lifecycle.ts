const active = new Map<string, AbortController>();

export function registerThreadStream(id: string, controller: AbortController) {
  active.get(id)?.abort();
  active.set(id, controller);
  return () => { if (active.get(id) === controller) active.delete(id); };
}

export function stopThreadStream(id: string) {
  active.get(id)?.abort();
  active.delete(id);
}
