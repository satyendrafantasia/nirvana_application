export const API_BASE_URL =
  process.env.EXPO_PUBLIC_API_BASE_URL ?? 'http://10.0.2.2:8080';
// Android emulator: http://10.0.2.2:8080 reaches a backend on your host machine.
// Physical device: replace with http://<your-lan-ip>:8080 so the phone can access your backend over Wi-Fi.
