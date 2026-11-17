import { describe, it, expect } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { AskGoldysLink } from "./ask-goldys-link";

function renderLink(seniority?: string) {
  return render(<AskGoldysLink seniority={seniority} />);
}

describe("AskGoldysLink", () => {
  it("shows the Ask button for an Owner", () => {
    renderLink("OWNER");
    expect(screen.getByRole("link", { name: /ask/i })).toBeInTheDocument();
  });

  it("routes the keyboard shortcut and existing investigation links to the page", () => {
    window.history.replaceState(null, "", "/sales?investigation=saved-job");
    renderLink("OWNER");
    expect(screen.getByRole("link", { name: /ask/i })).toHaveAttribute("href", "/ask-goldys?investigation=saved-job");
    fireEvent.keyDown(window, { key: "j", ctrlKey: true });
    expect(window.location.pathname).toBe("/ask-goldys");
    expect(window.location.search).toBe("?investigation=saved-job");
  });

  it("hides itself for a non-Owner", () => {
    renderLink("MANAGER");
    expect(screen.queryByRole("link", { name: /ask/i })).not.toBeInTheDocument();
  });

  it("fails closed when seniority is absent", () => {
    renderLink();
    expect(screen.queryByRole("link", { name: /ask/i })).not.toBeInTheDocument();
  });
});
