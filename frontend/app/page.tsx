export default function Home() {
  return (
    <main className="container flex min-h-screen flex-col items-center justify-center gap-4 py-16">
      <h1 className="text-3xl font-semibold tracking-tight">
        Goldy&apos;s Unified Data Platform
      </h1>
      <p className="text-muted-foreground max-w-prose text-center">
        Scaffold only — this page is a placeholder. The reconciliation UI
        (spec Requirement 5) and connector status views are not built yet;
        add shadcn/ui components with{" "}
        <code className="rounded bg-muted px-1 py-0.5">
          bunx shadcn@latest add &lt;component&gt;
        </code>{" "}
        as those screens get built.
      </p>
    </main>
  );
}
