# Ask workspace: verified current implementation

Date: 2026-11-15. Research baseline supplied by the parent task: `origin/main` at `807a4e74`; this investigation made no Git mutations or production calls. Findings below derive from code reads, not operational acceptance claims. Symbol references identify the relevant implementation within each linked file. This companion inventory supports a proposal; it does not approve implementation or extend product scope.

## End-to-end flow

1. The app header mounts [app-header.tsx — `AppHeader`](../../frontend/components/app-shell/app-header.tsx) and [ask-goldys-drawer.tsx — `AskGoldysDrawer`](../../frontend/components/ask-goldys/ask-goldys-drawer.tsx). The drawer is visually owner-only (`isOwner`); Ctrl/Cmd-J and `goldys:open-ask` open it. Closing hides the conversation; New chat resets its local state. It is not a route-addressable analysis workspace. No stop button is exposed.

2. [use-ask-goldys.ts — `useAskGoldys`](../../frontend/components/ask-goldys/use-ask-goldys.ts) creates a client UUID per chat, sends only `{threadId,message}`, retains every local `ChatTurn` including answer payload, and rejects duplicate submits while a controller is active. Generation checks suppress stale callbacks after reset/unmount. [stream-lifecycle.ts — `registerThreadStream / stopThreadStream`](../../frontend/lib/stream-lifecycle.ts) tracks one AbortController per thread in this browser module. This is neither a cross-tab nor server concurrency lock.

3. [stream.ts — `streamChat / dispatchEvent`](../../frontend/components/ask-goldys/stream.ts) directly POSTs `/api/conversational/chat`, uses same-origin cookies and the XSRF cookie/header, checks response type, decodes LF/CRLF SSE blocks, validates terminal payloads, and cancels/releases its reader. [next.config.ts — `rewrites`](../../frontend/next.config.ts) proxies `/api/*` to `BACKEND_ORIGIN` (default localhost:8080).

4. [SecurityConfig.java — `securityFilterChain`](../../backend/src/main/java/com/goldys/platform/config/SecurityConfig.java) uses email/password session authentication and cookie CSRF. [ChatController.java — `chat`](../../backend/src/main/java/com/goldys/platform/conversational/ChatController.java) obtains the authenticated account/role, requires `conversational.chat` READ, checks optional assistant availability, then prepares history. Thread CRUD separately requires `conversational.threads` READ/WRITE in [ThreadController.java](../../backend/src/main/java/com/goldys/platform/conversational/ThreadController.java). Owner grants are explicit permission rows, not a backend seniority shortcut.

5. [ConversationService.java — `contextFor / buildContext / summarize`](../../backend/src/main/java/com/goldys/platform/conversational/ConversationService.java) finds only an owned thread, denies an existing foreign ID, or adopts a previously unknown client UUID. It saves the user message transactionally before model generation. Model context is rebuilt from persisted text, with a running summary plus recent messages. When history exceeds six **messages** or roughly 4,000 tokens (characters/4), older messages are summarized synchronously through `ChatModel.call` inside the transaction. The six-message rule is not six exchanges. Raw stored messages are retained. Tool traces are not replayed into model history.

6. [ConversationalAiConfig.java — `conversationalChatClient / assistantService`](../../backend/src/main/java/com/goldys/platform/conversational/ConversationalAiConfig.java) enables the assistant only when `spring.ai.model.chat=openai`, rejects an absent/sentinel key, loads [ask-goldys-system.txt](../../backend/src/main/resources/prompts/ask-goldys-system.txt), and builds Spring AI `ChatClient`. [ChatClientAssistantService.java — `stream / generate`](../../backend/src/main/java/com/goldys/platform/conversational/ChatClientAssistantService.java) prepends venue-local current date/timezone, exposes reporting callbacks except five superseded legacy tools, and uses `ToolCallAdvisor` with [BoundedToolCallingManager.java — `executeToolCalls`](../../backend/src/main/java/com/goldys/platform/conversational/BoundedToolCallingManager.java) capped at eight tool **rounds**, not eight individual calls. A single round can contain multiple calls.

7. [ReportingToolCallbacks.java — `toCallback / validatedArguments`](../../backend/src/main/java/com/goldys/platform/conversational/ReportingToolCallbacks.java) adapts typed tool inputs, returns structured errors for denial/invalid arguments, and calls [ToolDispatcher.java — `dispatch`](../../backend/src/main/java/com/goldys/platform/reporting/ToolDispatcher.java). The dispatcher enforces tool resource READ and every referenced metric permission before execution, invokes existing reporting/semantic services, and records timing/failure metrics. JSON construction failures become a bounded corrective tool response. Arbitrary execution exceptions escape and become generic generation errors. The model never receives a direct database connection through this path.

8. [ConversationContext.java — `record / toAnswerPayload`](../../backend/src/main/java/com/goldys/platform/conversational/ConversationContext.java) accumulates widgets, provenance trace, notices, as-of time, and at most one latest dashboard draft. `ChatClientAssistantService.stream` deliberately collects all generated text before publication; it may retry an English unexecuted lookup promise once, replace an unsupported zero claim when all metric evidence is missing, or synthesize one tools-disabled response after the round cap. The SSE transport therefore does **not** provide live model-token feedback during the main computation. It emits text chunks only after completion, followed by `answer`, or a generic `error`.

9. [ChatController.java — `handle / send`](../../backend/src/main/java/com/goldys/platform/conversational/ChatController.java) persists nonblank assistant text and `payload.trace()` before forwarding `answer`. [AnswerPayload.java](../../backend/src/main/java/com/goldys/platform/conversational/AnswerPayload.java) also contains widgets/notices/asOf/draft, but those fields are not stored with the message. [answer-block.tsx — `AnswerBlock`](../../frontend/components/ask-goldys/answer-block.tsx) renders the text, notices, validated widgets, trace/trust, and a dashboard proposal. Errors replace the answer rendering.

10. A draft comes from [CreateDashboardDraftTool.java — `execute / validate`](../../backend/src/main/java/com/goldys/platform/conversational/CreateDashboardDraftTool.java) and is validated but not saved. User Save dashboard invokes [dashboard-writes.ts](../../frontend/components/dashboards/dashboard-writes.ts) through `Api`; an optional dashboard ID causes update. [SavedDashboardApplicationService.java — `create / update / render`](../../backend/src/main/java/com/goldys/platform/application/SavedDashboardApplicationService.java) retains ownership/visibility/revisions and reauthorizes metric reads when rendering current query configuration. It is an existing artifact destination worth retaining.

## Persistence and context limits

The following are code-observed limitations or risks, not experimentally reproduced production failures.

- The transcript store has no run/turn entity, client request id, idempotency constraint, event cursor, artifact identity, token/cost ledger, status, cancellation marker, or active-run lock. `ConversationThread` has no optimistic version; repositories do not request row locks. Concurrent requests can append interleaved user/assistant messages and race summary/timestamp updates. Retries append a second user message rather than identify the failed turn.
- Ordering is only `createdAt ASC`; equal timestamps have no explicit stable sequence tie-breaker. Thread listing loads every message of every thread to derive a 120-character preview (an unbounded N+1 read). Neither list nor transcript is paginated.
- `buildContext` reloads all history each turn and re-summarizes the entire older prefix together with the already-derived prior summary. There is no summary high-water mark, so older information is repeatedly folded. If summarization is unavailable/blank, all history remains. Exceptions during the synchronous summary call are not caught here; they can roll back the new user message and fail before SSE starts. The running summary becomes a system message, so a replacement should deliberately distinguish instructions from summarized untrusted conversation content.
- `ChatRequest` validates nonblank text only; it has no explicit message-length ceiling. The six-message window is not a hard context/token budget because recent messages can be arbitrarily large and failed/absent compaction retains full history.
- The SSE emitter uses timeout `0L`; the Reactor subscription is discarded. There is no `onCompletion`, `onTimeout`, or `onError` disposal hook, nor a backend cancel endpoint. Browser abort suppresses local output but is not proof model/tool work stopped. A send IOException completes the emitter with error without cancelling the subscription. The subscription error callback sends an error but does not explicitly complete the emitter. No heartbeats, event IDs, reconnect/resume, or durable terminal event exist.
- An answer consisting only of widgets/draft with blank prose is forwarded but not persisted. A save failure emits a generic error and suppresses terminal answer delivery; earlier streamed text may already be visible. On ordinary model failure the saved user message remains unmatched. No durable failure record explains it.
- The stream parser accepts EOF without requiring an `answer` or `error`; a truncated stream can leave partial text looking finished. It ignores unknown event names and has no negotiated protocol version. Existing widget schema versioning does not version the transport.

Persisted history is shown by [conversations-screen.tsx — `ThreadTranscript / MessageBlock`](../../frontend/components/conversations/conversations-screen.tsx). It supports listing, rename, delete and reading text plus tool names/descriptions; it does not hydrate the drawer or resume a selected thread. It cannot restore widgets, notices, draft or original as-of because the store never saved them. Deletion calls the browser stream registry before mutation, but server work can still race deletion. Rename accepts an unvalidated title body and does not touch timestamps in `ConversationThread.rename`; the DB length ceiling is 200.

## Schema and configuration dependency inventory

- [V13__seed_conversational_permission.sql](../../backend/src/main/resources/db/migration/V13__seed_conversational_permission.sql): `conversational.chat`: explicit ALL × OWNER grants.

- [V26__seed_ask_goldys_permissions.sql](../../backend/src/main/resources/db/migration/V26__seed_ask_goldys_permissions.sql): Includes owner `conversational.threads` permission.

- [V27__conversation_threads.sql](../../backend/src/main/resources/db/migration/V27__conversation_threads.sql): `conversation_thread`: UUID, account FK, title(200), summary, created/updated/last-message timestamps, user/update index. `conversation_message`: UUID, thread FK with cascade delete, role(16), content text, trace JSONB, created timestamp and thread/time index. No run/artifact/version/sequence columns.

- [V18__saved_dashboards.sql](../../backend/src/main/resources/db/migration/V18__saved_dashboards.sql): Initial saved-dashboard storage and owner permission.

- [V25__dashboard_documents_v2.sql](../../backend/src/main/resources/db/migration/V25__dashboard_documents_v2.sql): Dashboard document v2, revision and share tables; query configurations remain independently persisted artifacts.

- [application.yml](../../backend/src/main/resources/application.yml): `SPRING_AI_MODEL_CHAT` defaults none, `OPENAI_API_KEY` sentinel, `OPENAI_BASE_URL`, `OPENAI_MODEL` defaults gpt-4o-mini, temperature 0, prompt resource, venue timezone Australia/Melbourne. This is repository configuration, not checked deployment values.

- [build.gradle](../../backend/build.gradle): Java 25, Spring Boot 3.5.0, Spring AI BOM 1.1.8 and OpenAI model starter; MVC/JPA/validation/security, PostgreSQL/Flyway, Reactor via AI dependencies. No pi dependency here.

- [package.json](../../frontend/package.json): Bun package manager; Next 15/React 19 ranges, TanStack Query, Zod 4.6.5, Radix/shadcn primitives, Recharts, Vitest and Playwright. No pi/AI SDK dependency declared.


Spring AI cannot be removed as a chat-only cleanup. These other current consumers use the same optional `ChatModel`:

- [OpenAiClusterNamer.java — `suggest`](../../backend/src/main/java/com/goldys/platform/classification/OpenAiClusterNamer.java): taxonomy/review classification names; no model or failure returns empty suggestions.

- [LlmInvoicePdfExtractor.java — `extract / callLlm`](../../backend/src/main/java/com/goldys/platform/connectors/ctb/LlmInvoicePdfExtractor.java): supplier-invoice fallback extraction; text+schema hash cache, empty result without model, extraction error on failure.

- [OcrDocumentTextExtractor.java — `extractText / transcribe`](../../backend/src/main/java/com/goldys/platform/connectors/ctb/OcrDocumentTextExtractor.java): PDF pages rendered to images and transcribed with multimodal model; no model/page model failure yields empty text.


## Reuse versus replacement boundary

Retain the authenticated Spring application, PermissionService/ToolDispatcher, bounded typed reporting inputs, semantic metrics and query services, metric provenance, missing/denied behavior, schema-v2 widgets, dashboard validation/persistence/rendering and existing desktop visual primitives. A new runtime should adapt to these boundaries rather than duplicate their business rules.

Replace or substantially evolve the drawer-local thread lifecycle, text-only transcript persistence, SSE protocol/lifecycle and Spring-AI-specific orchestration adapter if the proposal adopts pi. `AssistantService` itself accepts Spring AI `Message`, so it is not a provider-neutral port as currently written. Separate context persistence from framework message conversion and summary execution. Keep non-chat Spring AI consumers operational during any migration. Treat durable runs, event replay, cancellation, ownership, idempotency and artifact storage as explicit backend work, not UI polish.

The live/demo split is incomplete for chat: `Api` has thread/dashboard operations in both live and demo implementations, while `streamChat` bypasses `Api` and always calls the real chat endpoint. A workspace transport needs matching live/demo contracts and fixtures. The browser chat test mocks the network response; it does not demonstrate actual demo model behavior.

## Concrete affected source inventory

Primary files already traced above should be reviewed as a change set, with these adjacent contracts and adapters:

- [AssistantService.java](../../backend/src/main/java/com/goldys/platform/conversational/AssistantService.java)

- [ConversationEvent.java](../../backend/src/main/java/com/goldys/platform/conversational/ConversationEvent.java)

- [ChatRequest.java](../../backend/src/main/java/com/goldys/platform/conversational/ChatRequest.java)

- [ConversationThread.java](../../backend/src/main/java/com/goldys/platform/conversational/ConversationThread.java)

- [ConversationMessage.java](../../backend/src/main/java/com/goldys/platform/conversational/ConversationMessage.java)

- [ConversationThreadRepository.java](../../backend/src/main/java/com/goldys/platform/conversational/ConversationThreadRepository.java)

- [ConversationMessageRepository.java](../../backend/src/main/java/com/goldys/platform/conversational/ConversationMessageRepository.java)

- [CreateDashboardDraftInput.java](../../backend/src/main/java/com/goldys/platform/conversational/CreateDashboardDraftInput.java)

- [DashboardDraft.java](../../backend/src/main/java/com/goldys/platform/conversational/DashboardDraft.java)

- [types.ts](../../frontend/components/ask-goldys/types.ts)

- [chat-exchange.tsx](../../frontend/components/ask-goldys/chat-exchange.tsx)

- [types.ts](../../frontend/lib/api/types.ts)

- [live.ts](../../frontend/lib/api/live.ts)

- [demo.ts](../../frontend/lib/api/demo.ts)

- [dashboard-schema.ts](../../frontend/lib/api/dashboard-schema.ts)

- [response.ts](../../frontend/lib/api/response.ts)

- [parse.ts](../../frontend/components/widgets/parse.ts)

- [widget-renderer.tsx](../../frontend/components/widgets/widget-renderer.tsx)

- [page.tsx](../../frontend/app/(app)/conversations/page.tsx)

- [SavedDashboardController.java](../../backend/src/main/java/com/goldys/platform/api/SavedDashboardController.java)

- [DashboardWidgetValidator.java](../../backend/src/main/java/com/goldys/platform/dashboard/DashboardWidgetValidator.java)


## Verification inventory and gaps

These are existing regression suites to carry forward or adapt, not a claim they were run in this research task. The controller SSE test supplies `Flux.empty()` and checks HTTP 200 only; it is not a test of cancellation, event framing, persistence-before-delivery, reconnect, or concurrent turns. `ConversationServiceTest` uses mocked repositories/model. Browser chat coverage stubs SSE responses. New workspace work needs database-backed concurrency/idempotency/ownership tests and transport disconnect/replay tests in addition to preserving these contracts.

- [AssistantCompletionRecoveryTest.java](../../backend/src/test/java/com/goldys/platform/conversational/AssistantCompletionRecoveryTest.java)

- [BoundedRoundsTest.java](../../backend/src/test/java/com/goldys/platform/conversational/BoundedRoundsTest.java)

- [ChatClientAssistantServiceTest.java](../../backend/src/test/java/com/goldys/platform/conversational/ChatClientAssistantServiceTest.java)

- [ChatRequestTest.java](../../backend/src/test/java/com/goldys/platform/conversational/ChatRequestTest.java)

- [ConversationMessageSerializationTest.java](../../backend/src/test/java/com/goldys/platform/conversational/ConversationMessageSerializationTest.java)

- [ConversationRepositoryTest.java](../../backend/src/test/java/com/goldys/platform/conversational/ConversationRepositoryTest.java)

- [ConversationServiceTest.java](../../backend/src/test/java/com/goldys/platform/conversational/ConversationServiceTest.java)

- [ConversationalAiConfigTest.java](../../backend/src/test/java/com/goldys/platform/conversational/ConversationalAiConfigTest.java)

- [ConversationalAiConfigWithKeyTest.java](../../backend/src/test/java/com/goldys/platform/conversational/ConversationalAiConfigWithKeyTest.java)

- [ConversationalBiIntegrationTest.java](../../backend/src/test/java/com/goldys/platform/conversational/ConversationalBiIntegrationTest.java)

- [DashboardDraftToolTest.java](../../backend/src/test/java/com/goldys/platform/conversational/DashboardDraftToolTest.java)

- [ReportingToolCallbacksTest.java](../../backend/src/test/java/com/goldys/platform/conversational/ReportingToolCallbacksTest.java)

- [ThreadControllerTest.java](../../backend/src/test/java/com/goldys/platform/conversational/ThreadControllerTest.java)

- [ConversationalEvalHarness.java](../../backend/src/test/java/com/goldys/platform/conversational/eval/ConversationalEvalHarness.java)

- [ConversationalEvalTest.java](../../backend/src/test/java/com/goldys/platform/conversational/eval/ConversationalEvalTest.java)

- [LiveLlmEvalTest.java](../../backend/src/test/java/com/goldys/platform/conversational/eval/LiveLlmEvalTest.java)

- [ChatControllerSseTest.java](../../backend/src/test/java/com/goldys/platform/api/ChatControllerSseTest.java)

- [ChatControllerTest.java](../../backend/src/test/java/com/goldys/platform/api/ChatControllerTest.java)

- [answer-block.test.tsx](../../frontend/components/ask-goldys/answer-block.test.tsx)

- [ask-goldys-drawer.test.tsx](../../frontend/components/ask-goldys/ask-goldys-drawer.test.tsx)

- [stream.test.ts](../../frontend/components/ask-goldys/stream.test.ts)

- [use-ask-goldys.test.ts](../../frontend/components/ask-goldys/use-ask-goldys.test.ts)

- [conversations-screen.test.tsx](../../frontend/components/conversations/conversations-screen.test.tsx)

- [ArchitectureBoundariesTest.java](../../backend/src/test/java/com/goldys/platform/architecture/ArchitectureBoundariesTest.java)

- [ask-goldys.spec.ts](../../frontend/tests/browser/ask-goldys.spec.ts)

- [dashboard-schema.test.ts](../../frontend/lib/api/dashboard-schema.test.ts)

- [live-dashboard.test.ts](../../frontend/lib/api/live-dashboard.test.ts)

- [dashboard-writes.test.ts](../../frontend/components/dashboards/dashboard-writes.test.ts)

- [OpenAiClusterNamerTest.java](../../backend/src/test/java/com/goldys/platform/classification/OpenAiClusterNamerTest.java)


Research verification: relative linked files were checked to exist when this document was generated. No application source was changed, no tests/builds/model calls were run, and no production, credential, or deployed configuration access occurred. This is a source inspection, not a live latency/cancellation experiment. Requirements and design constraints remain governed by the existing PRD/SRS/system-context and DESIGN documents; no acceptance claim or permission expansion is made here.
