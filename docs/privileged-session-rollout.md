# Privileged sessions and account recovery

## Before switching traffic

Apply Flyway V84 on the same primary database used by every API instance. Deploy the frontend and API together. Previous access tokens lack the required issuer, audience and session ID. Existing privileged refresh tokens lack evidence of MFA. Customers and staff using the previous browser build will be asked to sign in again. Keep rollback-compatible data backups and check the migration in staging first.

Set a random, environment-specific `JWT_SECRET` of at least 32 bytes. Configure `JWT_ISSUER` and `JWT_AUDIENCE` per trust boundary. The default access lifetime is 15 minutes. Rotation preserves the session family. Logout, account recovery and password reset revoke the applicable refresh sessions, so the corresponding access tokens also stop authorizing requests.

Confirm the public HTTPS origin in `PUBLIC_SITE_URL` and the exact trusted origins in `CORS_ALLOWED_ORIGINS`. For an email code, enable `EMAIL_ENABLED`, set `EMAIL_FROM_ADDRESS` to an approved sender, and test delivery to a controlled mailbox. For a phone code, configure the selected SMS provider. Firebase phone mode requires the Firebase project and domain configuration on the actual client. Staff enter their password, then verify one registered contact. Direct email-link, Google and mobile-only sign-in remain available to normal customers.

A browser on the same HTTPS site holds its access token in memory and renews it with a host-only, HttpOnly, Secure, SameSite=Strict cookie restricted to `/api/auth/browser-session`. Cookie exchange, cookie logout and the OAuth session exchange require the custom non-simple browser header. New sessions are checked against the database on each authenticated request. Native JSON callers retain the bearer and refresh response. A bundled Capacitor runtime, or a cross-site WebView built with `VITE_AUTH_TRANSPORT=memory`, holds both tokens in RAM and requires sign-in after a process restart. Use a specific HTTPS app origin such as `https://localhost` for a WebView CORS grant. The tracked repository contains the web app and API. Validate the packaged APK, its origin and its provider callback on a device before shipping it. Local HTTP USB forwarding depends on the Android build's loopback network policy.

## Enrollment check

Verified registration proves exactly one address or number before creating an owner account. Changing a spa's public email or telephone does not change its owner's authentication contact. The account profile API has no email, phone or verification-flag fields in its update request. Existing accounts with privileged roles or an enabled MFA flag require a reachable verified contact. Run this read-only preflight with the release operator's read role. It returns counts and excludes personal contact values.

```sql
SELECT COUNT(*) AS privileged_without_verified_contact
FROM app_user u
WHERE u.is_active = 1
  AND (u.mfa_enabled = 1 OR EXISTS (
    SELECT 1 FROM user_roles r WHERE r.user_id = u.id
      AND r.role NOT IN ('ROLE_USER', 'ROLE_CUSTOMER')
  ))
  AND NOT ((u.email_verified = 1 AND COALESCE(u.email, '') <> '')
        OR (u.phone_verified = 1 AND COALESCE(u.phone, '') <> ''));
```

Separately test each configured delivery channel. A stored verification flag cannot demonstrate current control of the mailbox or phone. Confirm that a controlled admin, owner and front desk account can complete their real second factor before rolling out to staff.

## Operator-assisted enrollment and recovery

This is an operational procedure for legacy staff without a verified contact, a lost second factor, or a privileged password reset. The app deliberately refuses password-only privileged recovery. There is no public override endpoint. A privileged account with its password and working registered factor uses normal sign-in.

1. Open a restricted support case naming the immutable Nirvana user ID, affected spa, reason and requested factor. Authenticate the operator using their individually identified administrative session. Recover a platform administrator only with a separate authorized platform administrator or designated organizational break-glass custodian.
2. Independently verify the requester's identity and current authority using existing contract records and an established business contact. Confirm using a channel obtained independently of the request. The proposed new contact, a listing phone number or a caller's assertion alone cannot establish business authority. Obtain a second named approver. Record the actor IDs, time, scope and approval in the restricted audit system.
3. Prove possession of the replacement contact through a fresh challenge from the configured provider. Bind the provider proof to the exact user, case and proposed destination. Keep only the verification outcome and reference in the case. Never place credentials, tokens or OTPs in the ticket.
4. Through the restricted maintenance role, open one database transaction. Lock the approved `app_user` row by its immutable ID. Recheck privileged role and spa membership, and check that the incoming address/number belongs to no other account. Update only the factor approved for this case and its corresponding verification flag. Clear the flag of any compromised factor. Revoke all live `refresh_token` rows for that user, invalidate outstanding OTP, reset and registration proofs for the affected contacts, and update `credentials_changed_at`. Preserve roles, spa membership, bookings and packages. Leave the account's MFA requirement in force. If a password must be replaced, use the application's bcrypt encoder through the controlled recovery process and a private handoff. Abort on mismatched scope or proof.
5. Record the outcome against the same case with the authenticated operator, approver, user ID and timestamp. Notify the established business contact through the previously verified channel. Verify that earlier sessions fail and that the recovered user can sign in by password plus the new factor. A business must designate the operators, independent proof procedure and audit destination before offering assisted recovery.

If independent identity proof or an authorized operator is unavailable, defer recovery. Keep the account locked until the business resolves ownership. Live provider delivery, real administrative recovery and a physical Android device require operational verification; local automated tests use isolated records and a test delivery adapter.
