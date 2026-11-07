# QLBX ERP Android validation

- Source baseline: uploaded QLBX_ERP_v1.2.2.html
- Working branch: erp-group/mobile-from-original-v1.2.2
- Commit: af35ae6548922b05bac191ad496b52396d2ede98
- npm install: failure
- npm test: success
- Vite build: failure
- Capacitor Android add: failure
- Capacitor sync: failure
- Gradle APK build: skipped

## Resolved package versions
qlbx-erp-android@1.2.2 /home/runner/work/qlbx-erp-android-v2/qlbx-erp-android-v2
├── UNMET DEPENDENCY @capacitor/android@7.4.3
├── UNMET DEPENDENCY @capacitor/cli@7.4.3
├── UNMET DEPENDENCY @capacitor/core@7.4.3
├── UNMET DEPENDENCY @capacitor/filesystem@7.4.3
├── UNMET DEPENDENCY @capacitor/share@7.4.3
└── UNMET DEPENDENCY vite@6.4.1

npm error code ELSPROBLEMS
npm error missing: @capacitor/android@7.4.3, required by qlbx-erp-android@1.2.2
npm error missing: @capacitor/cli@7.4.3, required by qlbx-erp-android@1.2.2
npm error missing: @capacitor/core@7.4.3, required by qlbx-erp-android@1.2.2
npm error missing: @capacitor/filesystem@7.4.3, required by qlbx-erp-android@1.2.2
npm error missing: @capacitor/share@7.4.3, required by qlbx-erp-android@1.2.2
npm error missing: vite@6.4.1, required by qlbx-erp-android@1.2.2
npm error A complete log of this run can be found in: /home/runner/.npm/_logs/2026-11-07T13_33_04_694Z-debug-0.log

- dist/index.html: missing
- APK: not generated
