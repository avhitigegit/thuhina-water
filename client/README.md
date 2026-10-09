# Thuhina Water – web app (`client/`)

Next.js 16 (App Router) + TypeScript (strict) + Tailwind CSS 4 + Radix dialogs (shadcn/ui style) + TanStack Query +
React Hook Form / Zod. All business rules are in the API – this app only shows data and calls `/api` (design P1).

## Run on this computer

```bash
# 1. Database + API (from the repo root):  cd deploy && docker compose up -d --build db api
# 2. Web app with hot reload:
npm install
npm run dev            # http://localhost:3000 – /api is forwarded to http://localhost:8080 (API_PROXY_URL)
```

Logins (local/UAT seed data, password `demo1234`): `nimal.admin` (Admin), `shanika.acc` (Accountant), `kasun.d`
(Delivery Staff).

## Scripts

| Script | What it does |
|---|---|
| `npm run dev` | Development server on port 3000 |
| `npm run build` / `npm start` | Production build / run it |
| `npm run lint` | ESLint (no warnings allowed) |
| `npm run typecheck` | TypeScript check |
| `npm run format` / `format:check` | Prettier |
| `npm test` | Component and unit tests (Vitest) |
| `npm run api:types` | Regenerate `src/lib/api/schema.d.ts` from the running API's OpenAPI (local profile). Commit the result. |

## Where things go (design 3.3)

| Folder | What |
|---|---|
| `src/app/login`, `src/app/change-password` | Pages outside the app shell |
| `src/app/(app)/…` | Protected pages; one folder per route (`/master-data/customers` …) |
| `src/components/layout` | `AppShell`, `Sidebar` (from the `/auth/me` menu), `TopBar`, `PageActions` |
| `src/components/shared` | `DataTable`, `FormDialog`, `SidePanel`, `ConfirmDialog`, `ReasonDialog`, `DateField`, `MoneyText`, `StatusBadge`, `KpiCard`, `StepBar`, `Tabs`, `Toaster` |
| `src/features/<module>` | API hooks (TanStack Query), forms and components of one module |
| `src/lib` | API client, generated API types, formatters, permissions, routes, toast |

Rules for screens:

- Use the prototype page as the spec; the prototype CSS classes (`card`, `btn`, `badge`, `banner`, `field`, `kpi` …)
  are in `src/app/globals.css`.
- Hide write buttons for view-only roles with `<Can permission={P.CUSTOMERS_EDIT}>…</Can>` or `useCan(...)`.
  The server checks every call anyway.
- Dates: `DateField` (value is ISO `2026-10-05`, shown `05/10/2026`). Money: `MoneyText` / `formatMoney`
  (`Rs. 1,350.00`).
- API errors show as a red toast automatically; set `meta: { silent: true }` on a query or mutation when the screen
  shows the error itself (e.g. field errors from `ApiError.fieldErrors`).
- `/ui-check` (Admin) shows every shared component with sample data.
