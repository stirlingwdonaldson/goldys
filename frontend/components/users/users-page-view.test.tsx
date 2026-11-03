import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { ApiError, type ManagedUser } from "@/lib/api";
import { UsersPageView } from "./users-page-view";

const api = {
  listUsers: vi.fn(),
  createUser: vi.fn(),
  updateUser: vi.fn(),
  resetUserPassword: vi.fn(),
};
vi.mock("@/lib/demo-mode", () => ({ useApi: () => api }));

const users: ManagedUser[] = [
  {
    id: "1",
    email: "owner@goldys.example",
    displayName: "Fixture Owner",
    active: true,
    createdAt: "2026-09-19T09:00:00Z",
    self: true,
  },
  {
    id: "2",
    email: "bar@goldys.example",
    displayName: "Bar Manager",
    active: false,
    createdAt: "2026-09-24T09:00:00Z",
    self: false,
  },
];

beforeEach(() => {
  vi.clearAllMocks();
});

it("renders each account with the single owner role and status", () => {
  render(<UsersPageView users={users} loading={false} error={null} reload={() => {}} />);

  expect(screen.getByText("owner@goldys.example")).toBeInTheDocument();
  expect(screen.getByText("bar@goldys.example")).toBeInTheDocument();
  expect(screen.getAllByText("Owner")).toHaveLength(2);
  expect(screen.getByText("Deactivated")).toBeInTheDocument();
});

it("disables deactivation on the caller's own row", () => {
  render(<UsersPageView users={users} loading={false} error={null} reload={() => {}} />);

  expect(screen.getByRole("button", { name: "Deactivate owner@goldys.example" })).toBeDisabled();
});

it("creates an account from the dialog", async () => {
  api.createUser.mockResolvedValue({ ...users[1], id: "3", email: "new@example.com" });
  render(<UsersPageView users={users} loading={false} error={null} reload={() => {}} />);

  fireEvent.click(screen.getByRole("button", { name: "Add user" }));
  fireEvent.change(screen.getByLabelText("Full name"), { target: { value: "New User" } });
  fireEvent.change(screen.getByLabelText("Email"), { target: { value: "new@example.com" } });
  fireEvent.change(screen.getByLabelText("Temporary password"), {
    target: { value: "password123" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Create user" }));

  await waitFor(() =>
    expect(api.createUser).toHaveBeenCalledWith({
      displayName: "New User",
      email: "new@example.com",
      password: "password123",
    }),
  );
});

it("shows a server error from a mutation", async () => {
  api.updateUser.mockRejectedValue(new ApiError("VALIDATION_FAILED", "The last active owner cannot be deactivated"));
  const activeUsers = users.map(user => (user.id === "2" ? { ...user, active: true } : user));
  render(<UsersPageView users={activeUsers} loading={false} error={null} reload={() => {}} />);

  fireEvent.click(screen.getByRole("button", { name: "Deactivate bar@goldys.example" }));
  fireEvent.click(screen.getByRole("button", { name: "Deactivate account" }));

  expect(await screen.findByRole("alert")).toHaveTextContent(
    "The last active owner cannot be deactivated",
  );
});
