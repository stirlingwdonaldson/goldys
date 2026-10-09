"use client";

import type { ReactNode } from "react";
import { toast as sonner } from "sonner";
import { Toaster } from "@/components/ui/sonner";

type ToastTone = "info" | "error" | "success";

interface ToastInput {
  title: string;
  description?: string;
  tone?: ToastTone;
  /** An optional inline action, e.g. "View" or "Undo" where the backend supports reversal. */
  action?: { label: string; onClick: () => void };
}

interface ToastApi {
  toast: (input: ToastInput) => void;
}

function toast({ title, description, tone = "info", action }: ToastInput) {
  const options = { description, action };
  if (tone === "success") sonner.success(title, options);
  else if (tone === "error") sonner.error(title, options);
  else sonner(title, options);
}

/**
 * Thin wrapper over Sonner that keeps the app's `useToast()` call sites stable.
 * Sonner owns its own store, so the hook needs no context.
 */
export function useToast(): ToastApi {
  return { toast };
}

export function ToastProvider({ children }: { children: ReactNode }) {
  return (
    <>
      {children}
      <Toaster />
    </>
  );
}
