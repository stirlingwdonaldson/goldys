import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { DatePicker, parseIsoDate, toIsoDate } from "./date-picker";

describe("date conversions", () => {
  it("round-trips a yyyy-mm-dd string through a local date without shifting the day", () => {
    const date = parseIsoDate("2026-10-09");
    expect(date?.getDate()).toBe(9);
    expect(toIsoDate(date!)).toBe("2026-10-09");
  });

  it("rejects anything that isn't yyyy-mm-dd", () => {
    expect(parseIsoDate("")).toBeUndefined();
    expect(parseIsoDate("09/10/2026")).toBeUndefined();
  });
});

describe("DatePicker", () => {
  it("shows the chosen date and picks a new one from the calendar", () => {
    const onChange = vi.fn();
    render(<DatePicker aria-label="From" value="2026-10-09" onChange={onChange} />);
    const trigger = screen.getByRole("button", { name: "From" });
    expect(trigger).toHaveTextContent("9 Oct 2026");
    expect(trigger).toHaveAttribute("data-value", "2026-10-09");

    fireEvent.click(trigger);
    fireEvent.click(screen.getByRole("button", { name: /October 11th, 2026/ }));
    expect(onChange).toHaveBeenCalledWith("2026-10-11");
  });

  it("shows the placeholder with no date and can clear an optional one", () => {
    const onChange = vi.fn();
    const { rerender } = render(<DatePicker aria-label="To" value="" onChange={onChange} clearable />);
    expect(screen.getByRole("button", { name: "To" })).toHaveTextContent("Pick a date");

    rerender(<DatePicker aria-label="To" value="2026-10-09" onChange={onChange} clearable />);
    fireEvent.click(screen.getByRole("button", { name: "To" }));
    fireEvent.click(screen.getByRole("button", { name: "Clear" }));
    expect(onChange).toHaveBeenCalledWith("");
  });
});
