import type { Metadata } from "next";
import type { ReactNode } from "react";

export const metadata: Metadata = { title: "Flow lab" };

export default function FlowLabLayout({ children }: { children: ReactNode }) {
  return <>{children}</>;
}
