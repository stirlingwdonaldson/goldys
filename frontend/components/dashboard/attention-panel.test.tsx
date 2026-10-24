import { render, screen } from "@testing-library/react";
import { expect, it } from "vitest";
import { AttentionPanel } from "./attention-panel";

it("keeps unavailable queue qualifications next to a zero-decision fallback", () => {
  render(<AttentionPanel items={[]} loading={false} openConflicts={0} notices={["Not checked: data feed status."]} />);
  expect(screen.getByRole("status")).toHaveTextContent("Not checked: data feed status.");
  expect(screen.getByText(/does not establish complete source coverage/)).toBeInTheDocument();
  expect(screen.queryByText("All numbers reconcile")).not.toBeInTheDocument();
});
