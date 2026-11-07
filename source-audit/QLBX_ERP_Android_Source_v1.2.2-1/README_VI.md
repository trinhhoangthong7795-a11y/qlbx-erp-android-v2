# QLBX ERP v1.2.2 – Android source

- Package ID: `vn.qlbx.erp`
- Android wrapper: Capacitor 7
- Data: IndexedDB local on device; no Firebase dependency
- Exports: native Android share sheet for generated Blob downloads (JSON, CSV, PNG)

## Build

```bash
npm install
npm test
npm run build
npx cap add android
npx cap sync android
cd android
./gradlew assembleDebug
```

The PDF print flow and file restore picker still require acceptance testing on a physical Android device.
