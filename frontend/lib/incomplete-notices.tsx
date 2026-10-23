"use client";

import { createContext, useContext, useEffect, useState, type ReactNode } from "react";

const STORAGE_KEY = "goldys-show-incomplete-notices";
const IncompleteNoticesContext = createContext<{ showIncomplete: boolean; setShowIncomplete: (value: boolean) => void }>({
  showIncomplete: false,
  setShowIncomplete: () => {},
});

export function useIncompleteNotices() {
  return useContext(IncompleteNoticesContext);
}

export function IncompleteNoticesProvider({ children }: { children: ReactNode }) {
  const [showIncomplete, setShowIncompleteState] = useState(false);
  useEffect(() => {
    try {
      setShowIncompleteState(window.localStorage.getItem(STORAGE_KEY) === "true");
    } catch {
      // Storage may be unavailable; keep the default for this session.
    }
  }, []);

  function setShowIncomplete(value: boolean) {
    setShowIncompleteState(value);
    try {
      window.localStorage.setItem(STORAGE_KEY, String(value));
    } catch {
      // The preference still works for this session when storage is unavailable.
    }
  }

  return (
    <IncompleteNoticesContext.Provider value={{ showIncomplete, setShowIncomplete }}>
      {children}
    </IncompleteNoticesContext.Provider>
  );
}
