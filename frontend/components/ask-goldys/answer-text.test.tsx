import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { AnswerMarkdown, AnswerText } from "./answer-text";

describe("answer presentation", () => {
  it("renders headings, emphasis, nested lists, tables and code consistently", () => {
    const { container } = render(<AnswerMarkdown text={"## Sales\n\n**Food** and *drinks*\n\n1. First\n   - Nested\n\n| Item | Total |\n| --- | ---: |\n| Food | $42 |\n\n```text\n<safe>\n```"} />);
    expect(screen.getByRole("heading", { name: "Sales" })).toBeInTheDocument();
    expect(container.querySelector("strong")).toHaveTextContent("Food");
    expect(container.querySelector("ol ul")).toHaveTextContent("Nested");
    expect(screen.getByRole("table")).toHaveTextContent("$42");
    expect(container.querySelector("pre code")).toHaveTextContent("<safe>");
  });
  it("shows a bounded preview and preserves the complete original answer on expansion", () => {
    const detail = "Supporting detail. ".repeat(100);
    render(<AnswerText text={`**Sales increased.**\n\n${detail}\n\nFinal qualification.`} />);
    expect(screen.getByText("Sales increased.")).toBeVisible();
    expect(screen.queryByText("Final qualification.")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Read full answer" }));
    expect(screen.getByText("Final qualification.")).toBeVisible();
    expect(screen.getByText(detail.trim())).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "Show less" }));
    expect(screen.queryByText("Final qualification.")).not.toBeInTheDocument();
  });
  it("renders short answers without a disclosure and handles incomplete Markdown safely", () => {
    const { container } = render(<AnswerText text={"A **partial answer\n\n```js\n<unfinished>"} />);
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
    expect(container.querySelector("pre")).toHaveTextContent("<unfinished>");
  });
  it("blocks HTML, remote images and unsafe links while preserving approved citations", () => {
    const { container } = render(<AnswerMarkdown text={'[Source](https://www.bom.gov.au/) [Bad](javascript:alert%281%29) [Other](https://evil.test) ![Evidence](https://evil.test/image) <img src="https://evil.test/raw" />'} />);
    expect(screen.getAllByRole("link")).toHaveLength(1);
    expect(screen.getByRole("link")).toHaveAttribute("referrerpolicy", "no-referrer");
    expect(container.querySelector("img")).toBeNull();
    expect(screen.getByText("Evidence")).toBeInTheDocument();
  });
});
