# Frontend

## Local dev

Run this from `frontend`:

```powershell
npm run dev
```

The dev script checks `localhost:8080` before starting Vite. If the backend is not running, it starts `../backend/run-local.ps1` and waits until the API is ready. This prevents Vite proxy requests to `/api` from returning `502 Bad Gateway`.

The backend needs Postgres on `localhost:5432`. If it is not running, the script tries:

```powershell
docker compose up -d postgres-ai
```

If Docker Desktop is closed, open it first and run `npm run dev` again.

Use this only when you intentionally want to run the frontend without checking or starting the backend:

```powershell
npm run dev:frontend
```

# React + TypeScript + Vite

This template provides a minimal setup to get React working in Vite with HMR and some Oxlint rules.

Currently, two official plugins are available:

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react) uses [Oxc](https://oxc.rs)
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react-swc) uses [SWC](https://swc.rs/)

## React Compiler

The React Compiler is not enabled on this template because of its impact on dev & build performances. To add it, see [this documentation](https://react.dev/learn/react-compiler/installation).

## Expanding the Oxlint configuration

If you are developing a production application, we recommend enabling type-aware lint rules by installing `oxlint-tsgolint` and editing `.oxlintrc.json`:

```json
{
  "$schema": "./node_modules/oxlint/configuration_schema.json",
  "plugins": ["react", "typescript", "oxc"],
  "options": {
    "typeAware": true
  },
  "rules": {
    "react/rules-of-hooks": "error",
    "react/only-export-components": ["warn", { "allowConstantExport": true }]
  }
}
```

See the [Oxlint rules documentation](https://oxc.rs/docs/guide/usage/linter/rules) for the full list of rules and categories.
