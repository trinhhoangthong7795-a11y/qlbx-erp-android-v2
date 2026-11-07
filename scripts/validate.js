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
check('Capacitor native bridge loaded', html.includes('src/native-bridge.js'));
check('Firebase SDK dependency', !!pkg.dependencies?.firebase);
check('Firebase Email/Password auth implementation', bridge.includes('signInWithEmailAndPassword'));
check('Firestore explicit cloud backup and restore', bridge.includes('replaceCloud') && bridge.includes('restoreCloud'));
check('Role and active-account enforcement', bridge.includes('profile?.active') && bridge.includes('viewer'));
check('Firestore deny-by-default rules', rules.includes('allow read, write: if false'));
for (const [name, ok] of checks) console.log((ok ? 'PASS ' : 'FAIL ') + name);
if (checks.some(([, ok]) => !ok)) process.exit(1);
