# IdleGrid dashboard

React + Vite + Tailwind dashboard for the IdleGrid Master service.

## Run locally

From this folder:

```bash
npm install
npm run dev
```

Open `http://localhost:5173`. Vite proxies `/api` requests to the backend at `http://localhost:8080`.

To use another backend URL, set `VITE_API_BASE_URL` before starting Vite.

## Structure

- `src/api` - backend request boundary
- `src/appRoutes` - route definitions
- `src/components` - reusable layout, dashboard, and job UI
- `src/context` - live data and theme state
- `src/pages` - route-level screens
- `src/styles` - Tailwind entrypoint and global styles
