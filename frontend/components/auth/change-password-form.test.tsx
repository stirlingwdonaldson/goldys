import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api";
import { ChangePasswordForm } from "./change-password-form";

const { changePassword, refresh, push } = vi.hoisted(() => ({
  changePassword: vi.fn(),
  refresh: vi.fn(),
  push: vi.fn(),
}));
vi.mock("@/lib/api", async importOriginal => ({
  ...(await importOriginal<typeof import("@/lib/api")>()),
  changePassword,
}));
vi.mock("@/components/app-shell/current-user-provider", () => ({
  useCurrentUser: () => ({ refresh }),
}));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));

beforeEach(() => {
  vi.clearAllMocks();
});

function fill(current = "oldpassword", next = "newpassword123", confirm = next) {
  render(<ChangePasswordForm />);
  fireEvent.change(screen.getByLabelText("Current password"), { target: { value: current } });
  fireEvent.change(screen.getByLabelText("New password"), { target: { value: next } });
  fireEvent.change(screen.getByLabelText("Confirm new password"), { target: { value: confirm } });
  fireEvent.click(screen.getByRole("button", { name: "Change password" }));
}

it("changes the password, refreshes the session and lands on the dashboard", async () => {
  changePassword.mockResolvedValue(undefined);
  fill();

  await waitFor(() =>
    expect(changePassword).toHaveBeenCalledWith({
      currentPassword: "oldpassword",
      newPassword: "newpassword123",
    }),
  );
  expect(refresh).toHaveBeenCalled();
  expect(push).toHaveBeenCalledWith("/dashboard");
});

it("blocks a mismatched confirmation without calling the API", () => {
  fill("oldpassword", "newpassword123", "different123");

  expect(changePassword).not.toHaveBeenCalled();
  expect(screen.getByRole("alert")).toHaveTextContent("do not match");
});

it("shows the backend message for a wrong current password", async () => {
  changePassword.mockRejectedValue(new ApiError("VALIDATION_FAILED", "Current password is incorrect"));
  fill();

  expect(await screen.findByRole("alert")).toHaveTextContent("Current password is incorrect");
  expect(push).not.toHaveBeenCalled();
});
