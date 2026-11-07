import { Capacitor } from '@capacitor/core';
import { Directory, Filesystem } from '@capacitor/filesystem';
import { Share } from '@capacitor/share';

// Preserve normal browser downloads. On Android, route generated Blob files
// through the native share sheet so the user can save them to Downloads/Drive.
const native = Capacitor.isNativePlatform();
const originalCreateObjectURL = URL.createObjectURL.bind(URL);
const blobUrls = new Map();
URL.createObjectURL = (blob) => {
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
  const saved = await Filesystem.writeFile({
    path,
    data: dataUrl,
    directory: Directory.Cache,
    recursive: true,
  });
  await Share.share({
    title: 'QLBX ERP',
    text: `Tệp xuất từ QLBX ERP: ${filename}`,
    url: saved.uri,
    dialogTitle: 'Lưu hoặc chia sẻ tệp QLBX',
  });
}

document.addEventListener('click', async (event) => {
  if (!native) return;
  const anchor = event.target?.closest?.('a[download]');
  if (!anchor) return;
  const blob = blobUrls.get(anchor.href);
  if (!blob) return;
  event.preventDefault();
  event.stopImmediatePropagation();
  try {
    await shareBlob(blob, anchor.download || 'QLBX_export');
  } catch (error) {
    console.error('QLBX native export failed:', error);
    alert(`Không thể xuất tệp: ${error?.message || error}`);
  }
}, true);

window.addEventListener('DOMContentLoaded', () => {
  if (native) {
    console.info('QLBX ERP Android native bridge ready');
    document.documentElement.dataset.qlbxNative = 'android';
  }
});
