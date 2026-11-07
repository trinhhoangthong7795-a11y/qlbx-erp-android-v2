import { Capacitor } from '@capacitor/core';
import { Directory, Filesystem } from '@capacitor/filesystem';
import { Share } from '@capacitor/share';
import { initializeApp } from 'firebase/app';
import { getAuth, onAuthStateChanged, setPersistence, indexedDBLocalPersistence, signInWithEmailAndPassword, signOut } from 'firebase/auth';
import { getFirestore, doc, getDoc, collection, getDocs, writeBatch } from 'firebase/firestore';

// Firebase web configuration for the user's erp-parking project.
// Authentication and cloud backup are opt-in; IndexedDB remains the local source of truth.
const firebaseConfig = {
  apiKey: 'AIzaSyBJEK7sSDAKA0MMuXAsz_ikNnY6qfR1as',
  authDomain: 'erp-parking.firebaseapp.com',
  projectId: 'erp-parking',
  storageBucket: 'erp-parking.firebasestorage.app',
  messagingSenderId: '244247226417',
  appId: '1:244247226417:web:7cf6e67c5b35aac5fbcd10',
  measurementId: 'G-G70HZYSLDG',
};

const firebaseApp = initializeApp(firebaseConfig);
const auth = getAuth(firebaseApp);
const firestore = getFirestore(firebaseApp);
const cloudState = { user: null, profile: null, ready: false };
let resolveAuthReady;
const authReady = new Promise(resolve => { resolveAuthReady = resolve; });
const persistenceReady = setPersistence(auth, indexedDBLocalPersistence).catch(error => {
  console.warn('Firebase persistence fallback:', error);
});
onAuthStateChanged(auth, async user => {
  cloudState.user = user || null;
  cloudState.profile = null;
  if (user) {
    try {
      const snap = await getDoc(doc(firestore, 'users', user.uid));
      if (snap.exists()) cloudState.profile = snap.data();
      if (!cloudState.profile?.active || !['super_admin', 'admin', 'operator', 'viewer'].includes(cloudState.profile?.role)) {
        await signOut(auth);
        cloudState.user = null;
        cloudState.profile = null;
      }
    } catch (error) {
      console.error('Firebase profile lookup failed:', error);
    }
  }
  cloudState.ready = true;
  resolveAuthReady();
  renderCloudUI();
});
function renderCloudUI() {
  const status = document.getElementById('cloud-status');
  const account = document.getElementById('cloud-account');
  const upload = document.querySelector('[data-action="cloud-upload"]');
  const logoutButton = document.querySelector('[data-action="cloud-logout"]');
  if (status) status.textContent = cloudState.user
    ? `${cloudState.user.email} · ${cloudState.profile?.role || 'chưa có quyền'}`
    : 'Chưa đăng nhập';
  if (account) {
    const identity = cloudState.user ? `${cloudState.user.uid}:${cloudState.profile?.role || ''}` : 'signed-out';
    if (account.dataset.cloudIdentity !== identity) {
      account.dataset.cloudIdentity = identity;
      account.innerHTML = cloudState.user
        ? `<p><strong>${cloudState.user.email}</strong></p><p class="hint">Vai trò: ${cloudState.profile?.role || '—'} · ${cloudState.profile?.active ? 'Đang hoạt động' : 'Chưa kích hoạt'}</p>`
        : '<p class="hint">Chưa đăng nhập. Dùng tài khoản do quản trị viên cấp.</p><button class="btn btn-primary" data-action="cloud-login">Đăng nhập Firebase</button>';
    }
  }
  if (upload) upload.hidden = !cloudState.user || !['super_admin', 'admin', 'operator'].includes(cloudState.profile?.role);
  if (logoutButton) logoutButton.hidden = !cloudState.user;
}
// Watch only direct content replacements inside the app view. Do not observe the whole
// document subtree: renderCloudUI mutates nested status/account nodes and would otherwise
// trigger itself forever, freezing the WebView main thread.
const cloudContent = document.getElementById('app-content');
const cloudUiObserver = new MutationObserver(() => renderCloudUI());
if (cloudContent) cloudUiObserver.observe(cloudContent, { childList: true });
renderCloudUI();

async function requireProfile() {
  await authReady;
  if (!cloudState.user || !cloudState.profile?.active) throw new Error('Bạn chưa đăng nhập hoặc tài khoản chưa được kích hoạt trong Firebase.');
  return cloudState.profile;
}
async function login(email, password) {
  await persistenceReady;
  const credential = await signInWithEmailAndPassword(auth, email, password);
  const snap = await getDoc(doc(firestore, 'users', credential.user.uid));
  const profile = snap.exists() ? snap.data() : null;
  if (!profile?.active || !['super_admin', 'admin', 'operator', 'viewer'].includes(profile.role)) {
    await signOut(auth);
    throw new Error('Tài khoản chưa được cấp hồ sơ hoặc chưa được kích hoạt. Hãy liên hệ quản trị viên.');
  }
  cloudState.user = credential.user;
  cloudState.profile = profile;
  return profile;
}
async function logout() { await signOut(auth); }
function canWrite(profile) { return ['super_admin', 'admin', 'operator'].includes(profile.role); }
const stores = ['vehicles', 'logs', 'slots', 'events', 'meta'];
function safeDocId(value) {
  const id = encodeURIComponent(String(value ?? ''));
  if (!id || id.length > 1400) throw new Error('ID bản ghi Firebase không hợp lệ.');
  return id;
}
async function replaceCloud(payload) {
  const profile = await requireProfile();
  if (!canWrite(profile)) throw new Error('Tài khoản chỉ có quyền xem, không thể ghi dữ liệu lên Firebase.');
  if (!payload || payload.app !== 'QLBX ERP' || payload.version !== 1) throw new Error('Dữ liệu sao lưu không đúng định dạng QLBX ERP.');
  let count = 0;
  for (const store of stores) {
    const rows = Array.isArray(payload[store]) ? payload[store] : [];
    const keyField = store === 'meta' ? 'key' : 'id';
    const ids = new Set(rows.map(row => safeDocId(row?.[keyField])));
    const existing = await getDocs(collection(firestore, 'yards', 'main', store));
    const ops = [];
    rows.forEach(row => {
      if (!row || row[keyField] == null) throw new Error(`Bản ghi ${store} thiếu khóa ${keyField}.`);
      ops.push({ type: 'set', ref: doc(firestore, 'yards', 'main', store, safeDocId(row[keyField])), data: { ...row, _qlbxStore: store, _qlbxSyncedAt: new Date().toISOString() } });
    });
    existing.forEach(snap => { if (!ids.has(snap.id)) ops.push({ type: 'delete', ref: snap.ref }); });
    for (let i = 0; i < ops.length; i += 400) {
      const batch = writeBatch(firestore);
      ops.slice(i, i + 400).forEach(op => op.type === 'set' ? batch.set(op.ref, op.data) : batch.delete(op.ref));
      await batch.commit();
    }
    count += rows.length;
  }
  return { count };
}
async function restoreCloud() {
  await requireProfile();
  const payload = { app: 'QLBX ERP', version: 1, exportedAt: new Date().toISOString() };
  for (const store of stores) {
    const snap = await getDocs(collection(firestore, 'yards', 'main', store));
    payload[store] = snap.docs.map(item => {
      const data = item.data();
      delete data._qlbxStore;
      delete data._qlbxSyncedAt;
      return data;
    });
  }
  return payload;
}
window.QLBXFirebase = { login, logout, replaceCloud, restoreCloud, getStatus: async () => { await authReady; return { user: cloudState.user, profile: cloudState.profile }; } };

// Preserve normal browser downloads. On Android, route generated Blob files
// through the native share sheet so users can save them to Downloads or Drive.
const native = Capacitor.isNativePlatform();
const originalCreateObjectURL = URL.createObjectURL.bind(URL);
const blobUrls = new Map();
URL.createObjectURL = blob => {
  const url = originalCreateObjectURL(blob);
  if (blob instanceof Blob) blobUrls.set(url, blob);
  return url;
};
function safeName(value) {
  return String(value || 'QLBX_export').replace(/[\\/:*?"<>|]+/g, '_').slice(0, 120);
}
async function shareBlob(blob, filename) {
  const dataUrl = await new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(reader.error || new Error('Không đọc được tệp xuất.'));
    reader.onload = () => resolve(String(reader.result).split(',')[1] || '');
    reader.readAsDataURL(blob);
  });
  const path = `QLBX/${safeName(filename)}`;
  const saved = await Filesystem.writeFile({ path, data: dataUrl, directory: Directory.Cache, recursive: true });
  await Share.share({ title: 'QLBX ERP', text: `Tệp xuất từ QLBX ERP: ${filename}`, url: saved.uri, dialogTitle: 'Lưu hoặc chia sẻ tệp QLBX' });
}
document.addEventListener('click', async event => {
  if (!native) return;
  const anchor = event.target?.closest?.('a[download]');
  if (!anchor) return;
  const blob = blobUrls.get(anchor.href);
  if (!blob) return;
  event.preventDefault();
  event.stopImmediatePropagation();
  try { await shareBlob(blob, anchor.download || 'QLBX_export'); }
  catch (error) { console.error('QLBX native export failed:', error); alert(`Không thể xuất tệp: ${error?.message || error}`); }
}, true);
window.addEventListener('DOMContentLoaded', () => {
  if (native) {
    console.info('QLBX ERP Android native bridge ready');
    document.documentElement.dataset.qlbxNative = 'android';
  }
});
