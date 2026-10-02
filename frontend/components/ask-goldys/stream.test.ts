import { describe, it, expect } from "vitest";
import { parseSseBlock } from "./stream";

describe("parseSseBlock", () => {
  it("parses a named event with JSON data", () => {
    const block = 'event: text\ndata: {"delta":"hello"}\n\n';
    expect(parseSseBlock(block)).toEqual({ event: "text", data: '{"delta":"hello"}' });
  });

  it("defaults the event name to message", () => {
    expect(parseSseBlock('data: {"a":1}\n\n')).toEqual({ event: "message", data: '{"a":1}' });
  });

  it("joins multi-line data", () => {
    expect(parseSseBlock('event: answer\ndata: {"a":1}\ndata: {"b":2}\n\n')).toEqual({
      event: "answer",
      data: '{"a":1}\n{"b":2}',
    });
  });
});
