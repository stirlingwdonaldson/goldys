import Image from "next/image";
import { Bot } from "lucide-react";

const providers: Record<string, { name: string; icon: string }> = {
  openai: { name: "OpenAI", icon: "openai" },
  "openai-codex": { name: "OpenAI", icon: "openai" },
  anthropic: { name: "Anthropic", icon: "anthropic" },
  google: { name: "Google", icon: "gemini" },
  "google-vertex": { name: "Google", icon: "gemini" },
  deepseek: { name: "DeepSeek", icon: "deepseek" },
  "goldys-deepseek": { name: "DeepSeek", icon: "deepseek" },
};

export function ProviderLabel({ provider, model }: { provider?: string | null; model?: string | null }) {
  const identity = provider ? providers[provider.toLowerCase()] : undefined;
  return <span className="inline-flex min-w-0 items-center gap-2 text-xs text-muted-foreground">
    {identity ? <Image src={`/providers/${identity.icon}.svg`} width={20} height={20} alt="" className="shrink-0" /> : <Bot className="size-5 shrink-0" aria-hidden="true" />}
    <span className="min-w-0 break-words">{identity?.name ?? provider ?? "Provider not recorded"}{model ? ` · ${model}` : ""}</span>
  </span>;
}
