# QLBX ERP — new_ver1

A clean, Android-native rebuild of the vehicle-yard ERP.

## Technology
- Kotlin + Jetpack Compose (native Android UI)
- Firebase Android SDK: Authentication and Cloud Firestore
- Room local database and WorkManager for offline-first workflows
- Android document picker / document creator for import and export
- Apache POI for XLSX workbook support

## Firebase setup
Place the Firebase Android configuration file at `app/google-services.json`.
The Google Services Gradle plugin is applied only when this file exists, so the project can still build its offline shell before Firebase configuration is supplied.

## Current implementation status
- Native Android project skeleton
- Mobile-first sign-in and dashboard shell
- Firebase dependencies declared
- Full vehicle workflows, Firestore repositories, offline queue, yard map, reports and verified XLSX import/export are pending implementation and device testing.

## Build
Run `./gradlew assembleDebug` with JDK 17 and Android SDK 36 installed.
