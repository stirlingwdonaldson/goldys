"use client";

import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { useRouter } from "next/navigation";
import { Sparkles } from "lucide-react";
import {
  CommandDialog,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
  CommandShortcut,
} from "@/components/ui/command";
import { NAV_GROUPS, SETTINGS_ITEM } from "@/lib/nav";
import { isOwner } from "@/lib/roles";
import { useCurrentUser } from "./current-user-provider";
import { OPEN_ASK_EVENT } from "@/components/ask-goldys/ask-goldys-drawer";

interface CommandPaletteValue {
  open: boolean;
  setOpen: (open: boolean) => void;
}

const CommandPaletteContext = createContext<CommandPaletteValue>({ open: false, setOpen: () => {} });

export function useCommandPalette(): CommandPaletteValue {
  return useContext(CommandPaletteContext);
}

/**
 * ⌘K / Ctrl+K jumps to any page or action without the mouse (heuristic 7).
 * Pages come from the shared nav model, so the palette never drifts from the sidebar.
 */
export function CommandPaletteProvider({ children }: { children: ReactNode }) {
  const [open, setOpen] = useState(false);
  const router = useRouter();
  const { user } = useCurrentUser();

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        setOpen((o) => !o);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  const value = useMemo(() => ({ open, setOpen }), [open]);

  function go(href: string) {
    setOpen(false);
    router.push(href);
  }

  return (
    <CommandPaletteContext.Provider value={value}>
      {children}
      <CommandDialog open={open} onOpenChange={setOpen}>
        <CommandInput placeholder="Search pages and actions…" />
        <CommandList>
          <CommandEmpty>Nothing matches that. Try a page name like “Sales”.</CommandEmpty>
          {isOwner(user?.seniority) ? (
            <CommandGroup heading="Actions">
              <CommandItem
                value="Ask Goldy's question"
                onSelect={() => {
                  setOpen(false);
                  window.dispatchEvent(new Event(OPEN_ASK_EVENT));
                }}
              >
                <Sparkles />
                Ask Goldy&apos;s a question
                <CommandShortcut>⌘J</CommandShortcut>
              </CommandItem>
            </CommandGroup>
          ) : null}
          {NAV_GROUPS.map((group) => (
            <CommandGroup key={group.label} heading={group.label}>
              {group.items.map((item) => (
                <CommandItem
                  key={item.href}
                  value={[item.title, ...(item.keywords ?? [])].join(" ")}
                  onSelect={() => go(item.href)}
                >
                  <item.icon />
                  {item.title}
                </CommandItem>
              ))}
            </CommandGroup>
          ))}
          <CommandGroup heading="Account">
            <CommandItem
              value={[SETTINGS_ITEM.title, ...(SETTINGS_ITEM.keywords ?? [])].join(" ")}
              onSelect={() => go(SETTINGS_ITEM.href)}
            >
              <SETTINGS_ITEM.icon />
              {SETTINGS_ITEM.title}
            </CommandItem>
          </CommandGroup>
        </CommandList>
      </CommandDialog>
    </CommandPaletteContext.Provider>
  );
}
