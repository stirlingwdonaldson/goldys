import type { Metadata } from "next";
import type { ReactNode } from "react";

export const metadata: Metadata = { title: "My work" };

export default function MyWorkLayout({ children }: { children: ReactNode }) {
  return <>{children}</>;
}
