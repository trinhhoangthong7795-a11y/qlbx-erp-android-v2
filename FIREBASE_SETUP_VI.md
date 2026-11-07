# Firebase security setup for QLBX ERP

This repository contains a proposed Firestore security baseline for the QLBX ERP Android app. It is **not yet wired into the app runtime**.

## Collections planned for the single yard

- `users/{uid}`: account profile and role.
- `yards/main/vehicles/{id}`
- `yards/main/logs/{id}`
- `yards/main/slots/{id}`
- `yards/main/events/{id}`
- `yards/main/meta/{key}`

## Roles

- `super_admin`: full administration.
- `admin`: manage users and operational data.
- `operator`: create and update operational data.
- `viewer`: read-only.

## First administrator bootstrap

1. Enable Email/Password in Firebase Authentication.
2. Create the first account in Authentication.
3. Copy its UID.
4. In Firestore, create `users/{UID}` with fields:
   - `role`: `super_admin`
   - `active`: `true`
   - `displayName`: a display label
5. Deploy `firestore.rules`.

Do not allow users to choose their own role during public registration. The first super-admin must be bootstrapped manually in the Firebase Console.

## Important

- These rules deny all access unless the user has an active profile.
- Viewer accounts can read but cannot write operational records.
- The app's Firebase Authentication and Firestore sync code still needs to be implemented and tested before this ruleset is used in production.
