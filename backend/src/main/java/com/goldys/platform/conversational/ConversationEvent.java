package com.goldys.platform.conversational;

/** A single event in the streamed conversation, mapped to an SSE named event. */
public sealed interface ConversationEvent
    permits ConversationEvent.TextDelta, ConversationEvent.Answer, ConversationEvent.Error {

  /** A chunk of the model's prose summary (SSE event `text`). */
  record TextDelta(String delta) implements ConversationEvent {}

  /** The terminal structured payload (SSE event `answer`). */
  record Answer(AnswerPayload payload) implements ConversationEvent {}

  /** A terminal failure (SSE event `error`). */
  record Error(String message) implements ConversationEvent {}
}
