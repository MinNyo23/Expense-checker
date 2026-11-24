# Supabase mobile integration

This Android app uses the Supabase Kotlin SDK with the publishable/anon key and the following modules:

- Auth for email/password sessions
- PostgREST for database access
- Storage for private images
- Realtime for live updates

The app must never contain a service-role key, query `user_accounts`, or fall back to anonymous database access.

## Authentication flow

The app restores the Supabase Auth session at startup. After sign-in or session refresh, it loads `profiles` by `auth_user_id`. Only a verified profile with a non-empty `couple_id` is allowed into the main app. A missing mapping displays the account-pending screen.

Existing accounts must be created in Supabase Auth and linked by a trusted administrator or backend. The mobile app must not write `profiles.auth_user_id`.

### Signup email OTP prerequisites

The signup screen now expects Supabase Auth to send a six-digit email verification code. These are Dashboard/SMTP prerequisites for the deployment owner; this repository does not modify them:

1. In **Authentication > Providers > Email**, keep **Confirm email** enabled. If it is disabled, Supabase may create a session immediately and there is no signup OTP gate.
2. In **Authentication > Email Templates**, ensure the signup/confirmation template includes the token placeholder `{{ .Token }}` in the message. The mobile app verifies the submitted code with `verifyEmailOtp` using the `signup` OTP type.
3. Configure a reliable SMTP provider under the Supabase project settings for production delivery. The default email service is intended for testing and is rate-limited.
4. Confirm the configured email template and redirect settings match the project’s intended mobile/browser recovery experience. Do not put service-role keys or SMTP credentials in the Android project.

After a successful signup, the app displays the code entry dialog and supports sending a new signup code. After verification, the app resolves the authenticated user against `profiles`; an unlinked user remains on the account-pending screen rather than bypassing the backend link check.

### Forgot-password email

Forgot password uses Supabase `resetPasswordForEmail`, which sends a password recovery **link**, not a six-digit recovery OTP. Users should open the link from the email app or browser and check spam/promotions folders if delivery is delayed. The app shows this distinction explicitly so the signup OTP and password recovery flows are not confused.

## Data access

Expenses and memos are loaded with the verified profile's `couple_id`. The repositories reject a caller-provided couple ID that differs from the authenticated profile. Database authorization is enforced again by Supabase RLS.

## Private images

Images use the private `images` bucket. The repository validates the content signature and MIME type, limits files to 5 MiB, generates a UUID filename, and uploads only to:

```text
<authenticated-user-id>/<uuid>.<extension>
```

The memo stores this object path in `memos.image_url`. The app creates a short-lived signed URL for display and removes the Storage object when a memo with an image is deleted.

## Build/security requirements

- Android API 26 or newer is required by the Supabase Kotlin SDK.
- Cleartext HTTP is disabled in the manifest.
- Keep `SUPABASE_URL` and `SUPABASE_ANON_KEY` in the Secrets Gradle Plugin or CI-provided BuildConfig values.
- Never log passwords, access tokens, refresh tokens, or keys.
- Backend schema and RLS migrations are managed in Supabase, not copied from the mobile settings screen.
