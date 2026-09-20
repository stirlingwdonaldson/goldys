import Link from "next/link";

export default function NotFound() {
  return (
    <main className="mx-auto flex min-h-screen max-w-5xl items-center px-8">
      <div className="flex flex-col items-start rounded-lg border border-dashed px-6 py-16">
        <h1 className="text-lg font-semibold">Page not found</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          The page you&apos;re looking for doesn&apos;t exist.
        </p>
        <Link
          href="/dashboard"
          className="mt-4 text-sm font-medium text-primary underline underline-offset-4"
        >
          Back to dashboard
        </Link>
      </div>
    </main>
  );
}
