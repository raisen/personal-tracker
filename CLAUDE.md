# CLAUDE.md — Personal Tracker

## Project Overview

A minimal, zero-dependency personal tracker web app built with vanilla TypeScript and Vite. Data is stored in GitHub Gists (no server required). Deployed to GitHub Pages.

## Commands

```bash
npm run dev      # Start Vite dev server (port 5173)
npm run build    # TypeScript type-check + Vite production build → dist/
npm run preview  # Preview production build locally
```

There are no test, lint, or formatting commands configured. TypeScript strict mode is the primary safety net.

## Tech Stack

- **Language**: TypeScript (strict mode, ES2020 target)
- **Build**: Vite 6
- **UI**: Vanilla DOM manipulation — no framework (React, Vue, etc.)
- **Styling**: Plain CSS with custom properties, system font stack, automatic dark mode via `prefers-color-scheme`
- **Backend**: GitHub Gist API (v3) with personal access tokens
- **Storage**: localStorage for auth tokens, sessionStorage for transient state
- **CI/CD**: GitHub Actions → GitHub Pages (triggers on push to `main`)
- **Node**: 20 (CI)

Zero production dependencies.

## Project Structure

```
src/
├── main.ts              # Entry point: hash-based router, view controller
├── api.ts               # GitHub Gist API wrapper (generic request<T>())
├── auth.ts              # Token/gist ID management via localStorage
├── types.ts             # Shared TypeScript interfaces
├── utils.ts             # Helpers (CSV export, date formatting, escHtml)
├── style.css            # Global styles with dark mode support
├── components/
│   ├── field-renderer.ts  # Renders form fields from FieldConfig
│   ├── field-editor.ts    # Modal editor for field configuration
│   ├── modal.ts           # Generic modal dialog factory
│   └── toast.ts           # Toast notification system
└── views/
    ├── entry.ts           # Create/edit daily entries
    ├── history.ts         # Browse and manage past entries
    ├── settings.ts        # Configure tracker fields and prompts
    ├── insights.ts        # AI prompt interface with clipboard
    └── setup.ts           # GitHub token auth and gist selection
```

## Architecture & Data Flow

1. User authenticates with a GitHub personal access token (stored in localStorage as `pt_github_token`)
2. App loads tracker config + data from a private Gist
3. Entries are stored as JSON arrays in the Gist
4. Shareable links use URL hash: `#gist=<id>`

**Gist files:**
- `tracker-config.json` — field definitions (`TrackerConfig`)
- `tracker-data.json` — array of entries (`TrackerData`)

## Code Conventions

### Naming
- **Functions/variables**: `camelCase`
- **Files**: `kebab-case.ts`
- **CSS classes**: `kebab-case`
- **localStorage keys**: `pt_` prefix (e.g., `pt_github_token`, `pt_gist_id`)
- **Entry metadata fields**: underscore prefix (`_id`, `_created`, `_updated`)

### Patterns
- Factory functions that return DOM elements: `createModal()`, `renderField()`
- Discriminated unions on `field.type` for field rendering
- Named exports only — no default exports
- Explicit relative imports with extensions omitted (bundler resolution)
- Type imports: `import type { X } from './types'`
- Async/await for all API calls
- Try-catch with user-facing toast notifications for errors
- `escHtml()` for XSS prevention when inserting user content

### TypeScript
- `strict: true` with additional strictness flags:
  - `noUnusedLocals`, `noUnusedParameters`
  - `noFallthroughCasesInSwitch`
  - `noUncheckedIndexedAccess`
- Use `as` type assertions sparingly
- Prefer generics over `any` (see `request<T>()` in api.ts)

### Styling
- CSS custom properties for theming
- Dark mode via `@media (prefers-color-scheme: dark)`
- Spacing in multiples of 8px
- Border radius: 8px standard, 12px large
- Responsive layout with max-width container and flexbox/grid

## CI/CD

GitHub Actions workflow (`.github/workflows/static.yml`):
- Triggers on push to `main` or manual dispatch
- Runs `npm ci` → `npm run build` → deploys `dist/` to GitHub Pages
- Node 20 with npm caching
