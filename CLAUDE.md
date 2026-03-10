# CLAUDE.md — Personal Tracker

Vanilla TypeScript + Vite personal tracker. Data stored in GitHub Gists. Deployed to GitHub Pages. Zero production dependencies.

## Commands

```bash
npm run dev      # Start Vite dev server (port 5173)
npm run build    # TypeScript type-check + Vite production build → dist/
npm run preview  # Preview production build locally
```

No test, lint, or formatting commands. TypeScript strict mode is the safety net.

## Code Conventions

- **Files**: `kebab-case.ts` — **Functions/variables**: `camelCase` — **CSS classes**: `kebab-case`
- **localStorage keys**: `pt_` prefix (e.g., `pt_github_token`, `pt_gist_id`)
- **Entry metadata fields**: underscore prefix (`_id`, `_created`, `_updated`)
- Named exports only — no default exports
- Type imports: `import type { X } from './types'`
- Async/await with try-catch and toast notifications for errors
- `escHtml()` for XSS prevention when inserting user content
- TypeScript `strict: true` with `noUnusedLocals`, `noUnusedParameters`, `noUncheckedIndexedAccess`
