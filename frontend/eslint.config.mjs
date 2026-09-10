// ESLint flat config.
//
// This repo previously used `next lint`, which Next.js deprecated in 15.5 and
// removed in 16. It also had no config committed at all, so `bun run lint`
// dropped into an interactive "How would you like to configure ESLint?" prompt
// and could never run unattended. eslint-config-next 15 still ships eslintrc
// configs rather than a flat one, hence FlatCompat.
import { dirname } from "path";
import { fileURLToPath } from "url";
import { FlatCompat } from "@eslint/eslintrc";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const compat = new FlatCompat({
  baseDirectory: __dirname,
});

const eslintConfig = [
  {
    ignores: [".next/**", "node_modules/**", "next-env.d.ts"],
  },
  ...compat.extends("next/core-web-vitals", "next/typescript"),
];

export default eslintConfig;
