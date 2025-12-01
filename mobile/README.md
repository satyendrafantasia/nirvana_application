# Nirvana Mobile (Expo + React Native)

A Fresha-inspired mobile client for the Nirvana wellness booking platform. Built with Expo, React Navigation, React Query, Axios, and NativeWind.

## Getting started

```bash
npm install
npm run start
```

Run on Android emulator or device:

```bash
npm run android
```

### Backend URL
Set `EXPO_PUBLIC_API_BASE_URL` in `.env` or `app.json` `expo.extra.API_BASE_URL`.

- Android emulator: `http://10.0.2.2:8080`
- Physical device: `http://<your-lan-ip>:8080`

Ensure your Spring Boot backend is reachable at that address when running the app.
