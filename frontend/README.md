# Nirvana Wellness Frontend

Fresha-inspired wellness booking SPA built with React, TypeScript, Vite, Tailwind CSS, React Router, and React Query. It is designed to pair with the Spring Boot backend at `http://localhost:8080`.

## Prerequisites
- Node.js 18+
- npm

## Getting started

```bash
cd frontend
npm install
npm run dev
```

The app defaults to `VITE_API_BASE_URL=http://localhost:8080`. Override by creating a `.env` file (see `.env.example`).

### Scripts
- `npm run dev` – start Vite dev server
- `npm run build` – type-check and build for production
- `npm run preview` – preview the production build
- `npm run lint` – run ESLint on source files

## Project structure
```
frontend/
  src/
    api/           # Axios client + API modules
    components/    # UI components (layout, spa cards, booking widgets, common UI)
    contexts/      # Auth and booking providers
    hooks/         # Custom hooks
    pages/         # Route-level pages
    routes/        # Router setup
    types/         # Shared TypeScript types
    theme/         # Theme tokens
```

## Styling
Tailwind CSS powers styling. Global styles and Tailwind directives live in `src/index.css`. Theme tokens are defined in `src/theme/freshaTheme.ts` and Tailwind config.

## Auth & API
- JWT is stored in `localStorage` under `nirvana_token`.
- Axios interceptor attaches the token to outbound requests.
- Auth and booking flows are encapsulated in contexts and hooks.

## Booking flow
A multi-step booking journey is implemented under `/booking` with steps for selecting service, slot, therapist, review, payment, and confirmation. State persists across steps via `BookingContext`.
