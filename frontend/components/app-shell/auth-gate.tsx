"use client";

import { useEffect, type ReactNode } from "react";
import { usePathname, useRouter } from "next/navigation";
import { ErrorState } from "@/components/states/error-state";
import { Skeleton } from "@/components/ui/skeleton";
import { loginHref } from "@/lib/auth-redirect";
import { useCurrentUser } from "./current-user-provider";

/**
 * Renders the app only for a signed-in user. Nothing behind the shell (sidebar, data, Ask
 * Goldy's) mounts until `/api/me` confirms a session, so a signed-out visitor never sees the app.
 *
 * - signed out (401): sent to the login page, which returns them here afterwards;
 * - backend unreachable or erroring: a full-screen error with a retry, not the app. The one
 *   exception is a demo build (`NEXT_PUBLIC_DEMO_MODE` not "false"), where running on fixtures
 *   with no backend at all is the point; a 401 from a real backend still gates even there.
 */
export function AuthGate({ children }: { children: ReactNode }) {
  const { status, error, refresh } = useCurrentUser();
  const router = useRouter();
  const pathname = usePathname();
  const demoBuild = process.env.NEXT_PUBLIC_DEMO_MODE !== "false";

  useEffect(() => {
    if (status === "unauthenticated") router.replace(loginHref(pathname));
  }, [status, pathname, router]);

  if (status === "authenticated" || (status === "error" && demoBuild)) return <>{children}</>;

  if (status === "error") {
    return (
      <main className="flex min-h-screen items-center justify-center p-4">
        <div className="w-full max-w-md">
          <ErrorState
            title="Couldn't reach Goldy's"
            message={error?.message ?? "We couldn't check whether you're signed in."}
            correlationId={error?.correlationId}
            onRetry={refresh}
          />
        </div>
      </main>
    );
  }

  // Checking the session, or on the way to the login page.
  return (
    <main className="flex min-h-screen items-center justify-center p-4" aria-busy="true">
      <span className="sr-only">Checking your sign-in…</span>
      <Skeleton className="h-10 w-48" />
    </main>
  );
}
