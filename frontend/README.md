# Event Booking frontend

React + TypeScript, built with Vite.

```bash
npm ci          # install exact versions from package-lock.json
npm run dev     # http://localhost:5173, /api is proxied to the backend on :8080
npm run lint    # oxlint
npm run build   # type-check and build into dist/
```

In Docker, `dist/` is packed into the Spring Boot jar, so one server on :8080 serves both the screens and the API.
The token from login is kept in `localStorage`. The frontend contains no secrets.
