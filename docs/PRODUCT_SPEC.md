# QLBX ERP new_ver1 — Product Specification

## Product direction
A completely new, Android-native, mobile-first vehicle-yard ERP. This is not a migration of the v1.2.2 HTML/WebView interface. Build a native Kotlin + Jetpack Compose app with Firebase Android SDK.

## Operating model
- One vehicle yard.
- Core actions: vehicle entry (Nhập bãi) and vehicle exit (Xuất bãi). No inter-yard transfer.
- Firebase Authentication Email/Password is the identity provider.
- Cloud Firestore is the shared database.
- Local Room storage is the operational cache and offline queue.
- Offline work must remain usable; queued changes sync when connectivity returns.
- Use server timestamps and stable document IDs; prevent duplicate active vehicle identifiers transactionally.

## Roles
- super_admin: full system administration and all data operations.
- admin: manage vehicles, yard layout, catalog, reports, users within granted policy.
- operator: enter/exit vehicles, assign/move parking positions, edit permitted vehicle data.
- viewer: read-only access.
- Role and active status must be validated from a protected user profile document, not trusted from client input alone.

## Vehicle identity and fields
Two identity modes:
1. Plated vehicle: registration plate is its primary identifier.
2. Unplated/new vehicle: last 6 VIN digits are its primary identifier.

Only the plate or last 6 VIN digits is mandatory. Other vehicle fields are optional.
Vehicle models supported in catalog include VF5, VFE34, Limo Green, Minio, EC Van, VF6, VF7, VF8 and VF9; catalog remains editable.
Vehicle colors and departments/teams/depot values are catalog-driven.

## Core screens
1. Sign in: Firebase email/password, show actionable auth errors; offline access only where a valid previously authenticated local session/policy allows it.
2. Overview: current yard KPIs, entry/exit trend, aging buckets (0–10, 11–20, 21–30, >30 days), alerts.
3. Vehicle entry: fast and full entry in one flow; choose plated or VIN identity; validate required identifier; detect duplicate active identifier before saving.
4. Vehicle exit: find vehicle, confirm exit, capture exit date/time, person and exit reason/destination when applicable; remove its current slot occupancy.
5. Vehicle management: live search on every typed character; filters; details; edit current and completed history with audit trail; permanent deletion one record at a time with multi-step confirmation; no bulk delete.
6. Yard map: 5 rows/sections by default; each row has 4 horizontal lanes × 20 longitudinal slots; horizontal and vertical scrolling; add/remove rows and slots; no manual slot-lock feature. Empty slots show faded labels; occupied slots show plate or last 6 VIN and distinguish vehicle identity type by more than color alone.
7. Slot actions: empty slot can search vehicle, choose waiting-to-assign list, or create entry; occupied slot can exit vehicle, remove it to waiting list, move it to another slot, or edit vehicle.
8. Activity log: newest first; detail includes full vehicle data, entry, exit, current/previous position where applicable, and edits. Do not retain position history after exit or after removal from the old cell.
9. Reports: default all vehicles; daily/weekly/monthly entry-exit chart; age buckets; exportable report image at 1080 px for Zalo; include selected special events, maximum 5 events per report. Special-event history is retained in full.
10. System: user/role management, editable catalogs, manual backup and restore, Excel export by full data or groups, optional cleanup of old logs only after backup verification.

## Audit and data rules
- Every edit to vehicle or completed movement history records before/after values, actor, timestamp and reason where supplied.
- Duplicate active plate or VIN must be blocked. If a 6-digit VIN search returns multiple historical matches, show all matching records and let the operator choose.
- A vehicle that exits is no longer considered currently inside.
- Re-entry creates a new movement event or updates the active vehicle state without misreporting it as a newly exited vehicle.
- Reports use actual vehicle entry/exit timestamps, never import/upload time.
- "New vehicles exited" counts vehicles that actually exited; a vehicle with an in→out→in cycle is currently inside and must not be counted as exited.
- Exported reports must include special events selected for that report.

## File operations — acceptance-critical
- Use Android Storage Access Framework (ACTION_OPEN_DOCUMENT / ACTION_CREATE_DOCUMENT); never depend on a hard-coded filesystem path or broad storage permission.
- Support XLSX import and export from the phone's file picker.
- Import must validate file type, workbook structure, headers and rows; show a preview, row-level errors and an explicit confirmation before writing.
- Export must generate a valid XLSX workbook, save through Android document picker, and report success only after the output stream closes successfully.
- Support full backup/restore in a documented portable format; restore replaces the whole database only after explicit confirmation and validation.
- Export filtered or all logs and grouped data.
- Test with files selected from Downloads, Documents, Google Drive/Files providers and Android share targets where available.
- Verify round-trip: export -> import into clean test database -> compare record counts and key fields.
- Test cancellation, invalid file, duplicate rows, large workbook, low storage and interrupted operations.

## Firebase collections (initial proposal)
- users/{uid}: displayName, role, active, createdAt, updatedAt.
- yards/main/vehicles/{vehicleId}: identityType, plate, vinLast6, model, color, department, team, depot, entryAt, entryPerson, status, currentSlotId, createdAt, updatedAt.
- yards/main/movements/{movementId}: vehicleId, action, occurredAt, actorUid, snapshot, note.
- yards/main/slots/{slotId}: rowKey, laneIndex, positionIndex, label, occupiedVehicleId, sortOrder, active.
- yards/main/logs/{logId}: entityType, entityId, action, before, after, actorUid, createdAt.
- yards/main/events/{eventId}: eventDate, description, createdBy, createdAt.
- yards/main/catalogs/{catalogId}: type, values, updatedAt.
- yards/main/meta/config: yard layout and schema version.

All writes must be authorized by Firestore Security Rules. Client role checks are UX only, never the security boundary. Use transactions/batched writes for vehicle/slot/movement consistency.

## Design principles
- Native Android Material 3.
- Mobile-only composition: one-hand reach, large touch targets (minimum 48dp), clear hierarchy, bottom navigation, sheets for quick tasks.
- Bright/light visual direction by default; optional dark theme can be added later.
- No desktop-first tables squeezed into phone width; use cards, compact lists and detail screens.
- Every action button must have a real implemented handler, loading state, success/error feedback and test coverage.

## Release gates
1. Gradle build and static checks pass.
2. Unit tests for validation, duplicate detection, reporting calculations and import/export parsing pass.
3. Firebase Auth/Firestore emulator or test-project integration tests pass.
4. UI tests cover primary flows and every navigation item.
5. XLSX import/export round-trip tests pass.
6. Install and exercise on a real Android phone; verify touch behavior, file picker, network loss/recovery, app restart and data persistence.
7. No production release until the user accepts the APK on their device.
