/** Where the auth gate sends a signed-out visitor, remembering the page they asked for. */
export function loginHref(pathname: string | null): string {
  if (!pathname || pathname === "/" || pathname.startsWith("/login")) return "/login";
  return `/login?next=${encodeURIComponent(pathname)}`;
}

/**
 * The page to return to after signing in: the `?next=` the auth gate set, if it is a same-origin
 * app path. Never `//host`, `/\host` or a full URL, which would make the login page an open
 * redirect.
 */
export function nextPath(search: string): string {
  const next = new URLSearchParams(search).get("next");
  if (next && next.startsWith("/") && !next.startsWith("//") && !next.startsWith("/\\")) return next;
  return "/dashboard";
}
