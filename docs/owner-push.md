# Claimed spa booking alerts

On the HTTPS site, an approved listing claimant opens Owner dashboard and selects
**Enable booking alerts** on each device. Enrolling covers that owner's approved
listings. A notification opens an authenticated booking review. The owner can
confirm or decline using the same cancellation and refund rules as the SMS flow.

## EC2 configuration

Use the existing Firebase project and web app. Enable the Firebase Cloud Messaging
API and the FCM Registration API. In Firebase project settings, copy the web app's
appId and messagingSenderId from General, and its public Web Push certificate key
from Cloud Messaging. Set these in the backend environment:

```
FIREBASE_WEB_APP_ID=
FIREBASE_MESSAGING_SENDER_ID=
FIREBASE_WEB_PUSH_PUBLIC_KEY=
FIREBASE_PUSH_ENABLED=true
```

Keep the existing FIREBASE_PROJECT_ID and FIREBASE_WEB_API_KEY. Configure Google
Application Default Credentials for the backend service with permission
cloudmessaging.messages.create on that project. On EC2 this can be a workload
identity federation credential configuration or a protected service account file
referenced by GOOGLE_APPLICATION_CREDENTIALS. Only the backend service should be
able to read the credential. Restart nirvana-api after configuration.

## Verification

Use an approved test claimant and a device you control. Grant notification
permission, book a future slot at its spa, and tap the alert. Verify that signing
in as the claimant opens the correct booking and a different user cannot open
it. Test decline with a disposable booking because it invokes cancellation.
Check booking_alert_job for audience SPA_PUSH and state DONE or FAILED. DONE
records provider acceptance or a stale subscription skipped by the worker;
device display and the spa's answer are separate events.
