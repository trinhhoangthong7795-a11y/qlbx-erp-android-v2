# Firebase integration review

- Authentication uses Email/Password and persists the session in IndexedDB-backed Firebase Auth persistence.
- Every signed-in account must have an active `users/{uid}` profile with an approved role.
- `operator`, `admin`, and `super_admin` can replace the cloud dataset; `viewer` can restore/read only.
- Cloud upload and restore are explicit destructive replacement actions with confirmation in the app.
- This version does not claim automatic multi-device synchronization. A later phase must add change tracking, tombstones, conflict resolution, and device-level testing before enabling background sync.
