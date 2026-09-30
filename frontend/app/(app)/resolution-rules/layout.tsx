import type { Metadata } from "next";
import type { ReactNode } from "react";

export const metadata: Metadata = { title: "Resolution rules" };

export default function ResolutionRulesLayout({ children }: { children: ReactNode }) {
  return <>{children}</>;
}
