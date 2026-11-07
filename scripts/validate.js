const fs = require('fs');
const checks = [];
function file(path) { try { return fs.readFileSync(path, 'utf8'); } catch { return ''; } }
const html = file('index.html');
const bridge = file('src/native-bridge.js');
const rules = file('firestore.rules');
const pkg = JSON.parse(file('package.json') || '{}');
function check(name, ok) { checks.push([name, !!ok]); }
check('HTML entry point', html.includes('<html') && html.includes('QLBX ERP'));
check('IndexedDB local persistence', html.includes("indexedDB.open('qlbx_erp'"));
check('Mobile viewport safe area', html.includes('viewport-fit=cover'));
check('Mobile navigation and responsive hardening', html.includes('mobile-nav') && html.includes('Android/mobile hardening'));
check('Navigation uses querySelectorAll for all view buttons', html.includes("document.querySelectorAll('[data-view]').forEach") && !html.includes("$('[data-view]').forEach"));
check('Capacitor native bridge loaded', html.includes('src/native-bridge.js'));
check('Firebase SDK dependency', !!pkg.dependencies?.firebase);
check('Firebase Email/Password auth implementation', bridge.includes('signInWithEmailAndPassword'));
check('Startup login gate with offline fallback', html.includes('id=\"auth-gate\"') && html.includes('id=\"startup-login-form\"') && html.includes('auth-offline'));
check('Startup login submits through Firebase auth', html.includes(\"e.target.id==='startup-login-form'\") && html.includes('QLBXFirebase.login'));
check('Navigation click delegation uses capture phase', html.includes(\"document.addEventListener('click',onClick,true)\"));
check('Persisted active Firebase session can dismiss startup gate', html.includes('qlbx-auth-state') && bridge.includes('qlbx-auth-state'));
check('Firestore explicit cloud backup and restore', bridge.includes('replaceCloud') && bridge.includes('restoreCloud'));
check('Role and active-account enforcement', bridge.includes('profile?.active') && bridge.includes('viewer'));
check('Firebase UI observer cannot create a WebView mutation loop', bridge.includes('cloudUiObserver.observe(cloudContent, { childList: true })') && !bridge.includes('observe(document.body, { childList: true, subtree: true })'));
check('Firestore deny-by-default rules', rules.includes('allow read, write: if false'));
for (const [name, ok] of checks) console.log((ok ? 'PASS ' : 'FAIL ') + name);
if (checks.some(([, ok]) => !ok)) process.exit(1);
