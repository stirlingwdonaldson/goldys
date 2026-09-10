import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Goldy's Platform",
  description: "Unified data platform - reconciliation, reporting, and reporting AI for Goldy's pub.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
