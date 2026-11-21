import { FILTER_OPTIONS, filterCssForTag, MAX_IMAGE_BASE64_CHARS } from './firebase-config.js';

let sharedAudioCtx = null;

export function playMomiAlertChime() {
  try {
    const AudioCtx = window.AudioContext || window.webkitAudioContext;
    if (!AudioCtx) return;
    if (!sharedAudioCtx) sharedAudioCtx = new AudioCtx();
    if (sharedAudioCtx.state === 'suspended') {
      sharedAudioCtx.resume().catch(() => undefined);
    }
    const now = sharedAudioCtx.currentTime;
    const notes = [587.33, 880.0]; // Warm D5 -> A5 chime
    notes.forEach((freq, idx) => {
      const osc = sharedAudioCtx.createOscillator();
      const gain = sharedAudioCtx.createGain();
      osc.type = 'sine';
      osc.frequency.setValueAtTime(freq, now + idx * 0.09);
      gain.gain.setValueAtTime(0.001, now + idx * 0.09);
      gain.gain.exponentialRampToValueAtTime(0.11, now + idx * 0.09 + 0.02);
      gain.gain.exponentialRampToValueAtTime(0.0001, now + idx * 0.09 + 0.34);
      osc.connect(gain);
      gain.connect(sharedAudioCtx.destination);
      osc.start(now + idx * 0.09);
      osc.stop(now + idx * 0.09 + 0.36);
    });
  } catch {
    // Ignore WebAudio restriction before first user gesture
  }
}

export function loadImageElement(src) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.onload = () => resolve(img);
    img.onerror = () => reject(new Error('Could not decode selected image.'));
    img.src = src;
  });
}

export function computeCropBox(naturalW, naturalH, aspectRatio) {
  let targetRatio = naturalW / naturalH;
  if (aspectRatio === '1:1') targetRatio = 1;
  else if (aspectRatio === '4:5') targetRatio = 0.8;
  else if (aspectRatio === '16:9') targetRatio = 16 / 9;

  const srcRatio = naturalW / naturalH;
  if (srcRatio > targetRatio) {
    const cropW = Math.round(naturalH * targetRatio);
    const sx = Math.round((naturalW - cropW) / 2);
    return { sx, sy: 0, sw: cropW, sh: naturalH };
  }
  const cropH = Math.round(naturalW / targetRatio);
  const sy = Math.round((naturalH - cropH) / 2);
  return { sx: 0, sy, sw: naturalW, sh: cropH };
}

export async function renderEditedCanvas(canvas, src, {
  filterTag = 'NONE',
  aspectRatio = '1:1',
  rotationDeg = 0,
  brightness = 0,
  contrast = 1,
  warmth = 0,
  maxEdge = 1080
} = {}) {
  const img = await loadImageElement(src);
  const rotatedOdd = Math.abs(rotationDeg % 180) === 90;
  const rotW = rotatedOdd ? img.naturalHeight : img.naturalWidth;
  const rotH = rotatedOdd ? img.naturalWidth : img.naturalHeight;

  // First draw rotated source to an intermediate canvas if rotated
  const tmpCanvas = document.createElement('canvas');
  tmpCanvas.width = rotW;
  tmpCanvas.height = rotH;
  const tmpCtx = tmpCanvas.getContext('2d');
  tmpCtx.save();
  tmpCtx.translate(rotW / 2, rotH / 2);
  tmpCtx.rotate((rotationDeg * Math.PI) / 180);
  tmpCtx.drawImage(img, -img.naturalWidth / 2, -img.naturalHeight / 2);
  tmpCtx.restore();

  const crop = computeCropBox(rotW, rotH, aspectRatio);
  const scale = Math.min(1, maxEdge / Math.max(crop.sw, crop.sh));
  const outW = Math.max(1, Math.round(crop.sw * scale));
  const outH = Math.max(1, Math.round(crop.sh * scale));

  canvas.width = outW;
  canvas.height = outH;
  const ctx = canvas.getContext('2d');
  ctx.save();
  ctx.filter = filterCssForTag(filterTag, brightness, contrast, warmth);
  ctx.drawImage(tmpCanvas, crop.sx, crop.sy, crop.sw, crop.sh, 0, 0, outW, outH);
  ctx.restore();
  return canvas;
}

export function exportCanvasToBase64Jpeg(canvas, maxChars = MAX_IMAGE_BASE64_CHARS) {
  let quality = 0.86;
  while (quality >= 0.4) {
    const dataUrl = canvas.toDataURL('image/jpeg', quality);
    const base64 = dataUrl.split(',')[1] || '';
    if (base64.length <= maxChars) return base64;
    quality -= 0.1;
  }
  const smallCanvas = document.createElement('canvas');
  smallCanvas.width = Math.round(canvas.width * 0.65);
  smallCanvas.height = Math.round(canvas.height * 0.65);
  smallCanvas.getContext('2d').drawImage(canvas, 0, 0, smallCanvas.width, smallCanvas.height);
  return (smallCanvas.toDataURL('image/jpeg', 0.72).split(',')[1] || '').slice(0, maxChars);
}

export async function compressFileToBase64(file, maxEdge = 960, maxChars = MAX_IMAGE_BASE64_CHARS) {
  const objectUrl = URL.createObjectURL(file);
  try {
    const canvas = document.createElement('canvas');
    await renderEditedCanvas(canvas, objectUrl, {
      filterTag: 'NONE',
      aspectRatio: '1:1',
      maxEdge
    });
    return exportCanvasToBase64Jpeg(canvas, maxChars);
  } finally {
    URL.revokeObjectURL(objectUrl);
  }
}

export async function assembleVideoChunksToObjectUrl(chunks = []) {
  if (!chunks.length) return null;
  const byteArrays = chunks.map((b64) => {
    const bin = atob(b64);
    const bytes = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i += 1) bytes[i] = bin.charCodeAt(i);
    return bytes;
  });
  const blob = new Blob(byteArrays, { type: 'video/mp4' });
  return URL.createObjectURL(blob);
}

export { FILTER_OPTIONS };
