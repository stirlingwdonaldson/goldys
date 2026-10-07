"use client";

import { useApi } from "@/lib/demo-mode";
import { ConversationsScreen } from "@/components/conversations/conversations-screen";

export default function ConversationsPage() {
  const api = useApi();
  return <ConversationsScreen api={api} />;
}
