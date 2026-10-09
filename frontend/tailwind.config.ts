import type { Config } from "tailwindcss";
import { fontFamily } from "tailwindcss/defaultTheme";
import tailwindcssAnimate from "tailwindcss-animate";

// shadcn/ui's standard tailwind config shape (CSS-variable-driven theme).
// `bunx shadcn add` may rewrite this file; the `brand`, `status`, `source` and
// `sidebar` color groups and the font families below are Goldy's own additions
// (docs/design/ui-direction.md) and must survive any such rewrite.
const config: Config = {
  darkMode: ["class"],
  content: [
    "./app/**/*.{ts,tsx}",
    "./components/**/*.{ts,tsx}",
  ],
  theme: {
    container: {
      center: true,
      padding: "2rem",
      screens: { "2xl": "1400px" },
    },
    extend: {
      fontFamily: {
        // Set by geist/font in app/layout.tsx.
        sans: ["var(--font-geist-sans)", ...fontFamily.sans],
        mono: ["var(--font-geist-mono)", ...fontFamily.mono],
      },
      colors: {
        brand: {
          DEFAULT: "hsl(var(--brand))",
          foreground: "hsl(var(--brand-foreground))",
        },
        border: "hsl(var(--border))",
        input: "hsl(var(--input))",
        ring: "hsl(var(--ring))",
        background: "hsl(var(--background))",
        foreground: "hsl(var(--foreground))",
        primary: {
          DEFAULT: "hsl(var(--primary))",
          foreground: "hsl(var(--primary-foreground))",
        },
        secondary: {
          DEFAULT: "hsl(var(--secondary))",
          foreground: "hsl(var(--secondary-foreground))",
        },
        destructive: {
          DEFAULT: "hsl(var(--destructive))",
          foreground: "hsl(var(--destructive-foreground))",
          soft: "hsl(var(--destructive-soft))",
        },
        muted: {
          DEFAULT: "hsl(var(--muted))",
          foreground: "hsl(var(--muted-foreground))",
        },
        accent: {
          DEFAULT: "hsl(var(--accent))",
          foreground: "hsl(var(--accent-foreground))",
        },
        popover: {
          DEFAULT: "hsl(var(--popover))",
          foreground: "hsl(var(--popover-foreground))",
        },
        card: {
          DEFAULT: "hsl(var(--card))",
          foreground: "hsl(var(--card-foreground))",
        },
        chart: {
          "1": "hsl(var(--chart-1))",
          "2": "hsl(var(--chart-2))",
          "3": "hsl(var(--chart-3))",
          "4": "hsl(var(--chart-4))",
          "5": "hsl(var(--chart-5))",
        },
        status: {
          success: {
            DEFAULT: "hsl(var(--status-success))",
            soft: "hsl(var(--status-success-soft))",
          },
          conflict: {
            DEFAULT: "hsl(var(--status-conflict))",
            soft: "hsl(var(--status-conflict-soft))",
          },
          missing: {
            DEFAULT: "hsl(var(--status-missing))",
            soft: "hsl(var(--status-missing-soft))",
          },
          info: {
            DEFAULT: "hsl(var(--status-info))",
            soft: "hsl(var(--status-info-soft))",
          },
        },
        source: {
          lightspeed: {
            DEFAULT: "hsl(var(--source-lightspeed))",
            soft: "hsl(var(--source-lightspeed-soft))",
          },
          ctb: {
            DEFAULT: "hsl(var(--source-ctb))",
            soft: "hsl(var(--source-ctb-soft))",
          },
          opentable: {
            DEFAULT: "hsl(var(--source-opentable))",
            soft: "hsl(var(--source-opentable-soft))",
          },
          deputy: {
            DEFAULT: "hsl(var(--source-deputy))",
            soft: "hsl(var(--source-deputy-soft))",
          },
        },
        sidebar: {
          DEFAULT: "hsl(var(--sidebar-background))",
          foreground: "hsl(var(--sidebar-foreground))",
          primary: "hsl(var(--sidebar-primary))",
          "primary-foreground": "hsl(var(--sidebar-primary-foreground))",
          accent: "hsl(var(--sidebar-accent))",
          "accent-foreground": "hsl(var(--sidebar-accent-foreground))",
          border: "hsl(var(--sidebar-border))",
          ring: "hsl(var(--sidebar-ring))",
        },
      },
      // Goldy's type steps beyond Tailwind's scale (docs/design/ui-direction.md).
      fontSize: {
        "2xs": ["0.65625rem", { lineHeight: "1rem" }], // 10.5px: key hints, source monograms
        ui: ["0.8125rem", { lineHeight: "1.25rem" }], // 13px: labels, compact controls
        nav: ["0.84375rem", { lineHeight: "1.25rem" }], // 13.5px: sidebar items
        kpi: ["1.6875rem", { lineHeight: "1.15", letterSpacing: "-0.02em", fontWeight: "600" }], // 27px figures
      },
      boxShadow: {
        // The raised inset panel and right-hand sheets.
        panel: "0 1px 2px rgba(23,23,26,0.04), 0 4px 16px rgba(23,23,26,0.04)",
        sheet: "-12px 0 32px rgba(23,23,26,0.08)",
      },
      borderRadius: {
        lg: "var(--radius)",
        md: "calc(var(--radius) - 2px)",
        sm: "calc(var(--radius) - 4px)",
      },
    },
  },
  plugins: [tailwindcssAnimate],
};

export default config;
