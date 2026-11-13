import { Sparkles } from "lucide-react";
import { Bubble, BubbleContent } from "@/components/ui/bubble";
import { Message, MessageContent, MessageHeader } from "@/components/ui/message";
import { MessageScrollerItem } from "@/components/ui/message-scroller";
import type { Api } from "@/lib/api/types";
import { AnswerBlock } from "./answer-block";
import type { ChatTurn } from "./types";

export function ChatExchange({ turn, api, onRetry }: { turn: ChatTurn; api: Api; onRetry?: () => void }) {
  return (
    <MessageScrollerItem messageId={turn.id} scrollAnchor className="space-y-6">
      <Message align="end">
        <MessageContent>
          <MessageHeader>You</MessageHeader>
          <Bubble variant="muted" align="end"><BubbleContent className="whitespace-pre-wrap">{turn.question}</BubbleContent></Bubble>
        </MessageContent>
      </Message>
      <Message>
        <MessageContent>
          <MessageHeader className="gap-2 px-0"><Sparkles className="size-3.5" aria-hidden="true" />Goldy&apos;s</MessageHeader>
          <Bubble variant="ghost" className="w-full">
            <BubbleContent className="w-full overflow-visible tabular-nums">
              <AnswerBlock summary={turn.summary} answer={turn.answer} error={turn.error} api={api} onRetry={onRetry} />
              {turn.working ? <p role="status" className="mt-2 text-sm text-muted-foreground">{turn.summary ? "Preparing results…" : "Working…"}</p> : null}
            </BubbleContent>
          </Bubble>
        </MessageContent>
      </Message>
    </MessageScrollerItem>
  );
}
