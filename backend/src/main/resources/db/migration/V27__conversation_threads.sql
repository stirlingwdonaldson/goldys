-- Ask Goldy's conversation persistence: a thread groups a conversation under its owner;
-- messages carry the role, content and the tool trace (provenance) of the assistant's answer.
-- The tool_trace JSONB holds List<AnswerPayload.TraceEntry>. Messages cascade with their thread.

CREATE TABLE conversation_thread (
    id uuid PRIMARY KEY,
    user_account_id uuid NOT NULL,
    title varchar(200) NOT NULL,
    summary text,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    last_message_at timestamp(6) with time zone,
    CONSTRAINT conversation_thread_user_fk FOREIGN KEY (user_account_id) REFERENCES user_account (id)
);

CREATE INDEX idx_conversation_thread_user ON conversation_thread (user_account_id, updated_at DESC);

CREATE TABLE conversation_message (
    id uuid PRIMARY KEY,
    thread_id uuid NOT NULL,
    role varchar(16) NOT NULL,
    content text NOT NULL,
    tool_trace jsonb,
    created_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT conversation_message_thread_fk FOREIGN KEY (thread_id) REFERENCES conversation_thread (id) ON DELETE CASCADE
);

CREATE INDEX idx_conversation_message_thread ON conversation_message (thread_id, created_at ASC);
