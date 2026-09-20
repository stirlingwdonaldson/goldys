import type { Metadata } from "next";
import type { ReactNode } from "react";

export const metadata: Metadata = { title: "Reconciliation" };

export default function ReconciliationLayout({ children }: { children: ReactNode }) {
  return <>{children}</>;
}
