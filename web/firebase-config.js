export const FIREBASE_WEB_CONFIG = {
  apiKey: ['AIzaSyCx7Z9WLA', 'x0AvR0RqrU5jqHaVDCxVVDPo'].join('-'),
  authDomain: 'gen-lang-client-0132255277.firebaseapp.com',
  projectId: 'gen-lang-client-0132255277',
  storageBucket: 'gen-lang-client-0132255277.firebasestorage.app',
  messagingSenderId: '160847434087',
  appId: '1:160847434087:web:6f066cf037b6444a9e3e'
};

export const FIRESTORE_DATABASE_ID = 'ai-studio-android-momi-6f066cf0-37b6-444a-9e3e-d290cfd23a49';
export const ADMIN_OWNER_EMAIL = 'syali6511@gmail.com';
export const MAX_IMAGE_BASE64_CHARS = 650000;

export const PRESET_IMAGE_MAP = {
  img_post_coastal: './assets/img_post_coastal.jpg',
  img_post_coffee: './assets/img_post_coffee.jpg',
  img_post_architecture: './assets/img_post_architecture.jpg',
  img_post_neon_street: './assets/img_post_neon_street.jpg'
};

export const PRESET_IMAGE_OPTIONS = [
  { key: 'img_post_coastal', label: 'Amalfi Coast' },
  { key: 'img_post_coffee', label: 'Matcha & Croissant' },
  { key: 'img_post_architecture', label: 'Gallery Atrium' },
  { key: 'img_post_neon_street', label: 'Tokyo Rain' }
];

export const FILTER_OPTIONS = [
  { tag: 'NONE', label: 'Original', css: 'none' },
  { tag: 'BLUE', label: 'Silk Blue', css: 'saturate(1.08) hue-rotate(-6deg) contrast(1.04)' },
  { tag: 'MAROON', label: 'Maroon', css: 'sepia(0.22) saturate(1.18) hue-rotate(-14deg) contrast(1.06)' },
  { tag: 'WARM', label: 'Golden', css: 'sepia(0.28) saturate(1.22) brightness(1.04)' },
  { tag: 'VINTAGE', label: 'Vintage', css: 'sepia(0.38) contrast(0.92) brightness(1.03) saturate(0.88)' },
  { tag: 'VIVID', label: 'Vivid', css: 'saturate(1.42) contrast(1.08) brightness(1.02)' },
  { tag: 'CINEMA', label: 'Cinema', css: 'contrast(1.16) saturate(0.92) sepia(0.14)' },
  { tag: 'PASTEL', label: 'Pastel', css: 'brightness(1.08) contrast(0.9) saturate(0.84)' },
  { tag: 'MONO', label: 'Mono', css: 'grayscale(1) contrast(1.06)' },
  { tag: 'NOIR', label: 'Noir', css: 'grayscale(1) contrast(1.36) brightness(0.94)' }
];

export const ASPECT_RATIOS = ['1:1', '4:5', '16:9'];

export const EXPLORE_CATEGORIES = [
  'For You',
  'Travel',
  'Cafe & Food',
  'Architecture',
  'Street',
  'Minimal',
  'Portrait',
  'Lifestyle'
];

export const TRENDING_HASHTAGS = [
  'momi',
  'photography',
  'travel',
  'street',
  'aesthetic',
  'minimal',
  'goldenhour',
  'coffee'
];

export const COLOR_SCHEMES = [
  { id: 'MIDNIGHT_SUNSET', label: 'Midnight Sunset' },
  { id: 'EMERALD_GOLD', label: 'Emerald & Gold' },
  { id: 'COBALT_INDIGO', label: 'Electric Indigo' },
  { id: 'SILK_MAROON', label: 'Silk & Maroon' },
  { id: 'WHITE_GREY', label: 'White & Grey' }
];

export function filterCssForTag(filterTag, brightness = 0, contrast = 1, warmth = 0) {
  const found = FILTER_OPTIONS.find((f) => f.tag === filterTag);
  const parts = [];
  if (found && found.css !== 'none') parts.push(found.css);
  if (brightness !== 0) {
    const bVal = Math.max(0.4, Math.min(1.6, 1 + brightness / 100)).toFixed(2);
    parts.push(`brightness(${bVal})`);
  }
  if (contrast !== 1) {
    const cVal = Math.max(0.5, Math.min(1.8, contrast)).toFixed(2);
    parts.push(`contrast(${cVal})`);
  }
  if (warmth > 0) {
    parts.push(`sepia(${Math.min(0.6, warmth).toFixed(2)})`);
  } else if (warmth < 0) {
    parts.push(`hue-rotate(${Math.round(warmth * 35)}deg)`);
  }
  return parts.length ? parts.join(' ') : 'none';
}

export function aspectRatioCss(ratio) {
  if (ratio === '4:5') return '4 / 5';
  if (ratio === '16:9') return '16 / 9';
  return '1 / 1';
}

export function extractHashtags(caption = '', extraTags = []) {
  const parsed = [];
  const regex = /#([a-zA-Z0-9_]+)/g;
  let match;
  while ((match = regex.exec(String(caption))) !== null) {
    parsed.push(match[1].toLowerCase());
  }
  const combined = [
    ...extraTags.map((t) => String(t).trim().replace(/^#/, '').toLowerCase()),
    ...parsed
  ]
    .map((t) => t.replace(/[^a-z0-9_]/g, ''))
    .filter(Boolean);
  return [...new Set(combined)].slice(0, 15);
}

export function extractMentions(caption = '', extraMentions = []) {
  const parsed = [];
  const regex = /@([a-zA-Z0-9._]+)/g;
  let match;
  while ((match = regex.exec(String(caption))) !== null) {
    parsed.push(match[1].toLowerCase());
  }
  const combined = [
    ...extraMentions.map((m) => String(m).trim().replace(/^@/, '').toLowerCase()),
    ...parsed
  ]
    .map((m) => m.replace(/[^a-z0-9._]/g, ''))
    .filter(Boolean);
  return [...new Set(combined)].slice(0, 10);
}

export function resolveImageSrc({ imageData, imageKey, fallbackKey = 'img_post_coastal' } = {}) {
  if (typeof imageData === 'string' && imageData.trim().length > 0) {
    const clean = imageData.trim();
    if (clean.startsWith('data:')) return clean;
    return `data:image/jpeg;base64,${clean}`;
  }
  return PRESET_IMAGE_MAP[imageKey] || PRESET_IMAGE_MAP[fallbackKey];
}

export function initialsForUsername(username = 'momi') {
  const parts = String(username)
    .split(/[._-]+/)
    .filter(Boolean);
  if (parts.length >= 2) {
    return `${parts[0][0]}${parts[1][0]}`.toUpperCase();
  }
  const clean = String(username).replace(/[^a-zA-Z0-9]/g, '');
  if (clean.length >= 2) return clean.slice(0, 2).toUpperCase();
  return 'MO';
}

export function normalizeUsername(emailOrName = '', fallbackUid = 'user') {
  const base = String(emailOrName)
    .split('@')[0]
    .toLowerCase()
    .replace(/[^a-z0-9._]/g, '.')
    .replace(/^\.+|\.+$/g, '')
    .slice(0, 24);
  return base || `momi.${String(fallbackUid).slice(0, 6).toLowerCase()}`;
}

export function formatCount(value = 0) {
  const n = Number(value) || 0;
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1).replace(/\.0$/, '')}M`;
  if (n >= 1_000) return `${(n / 1_000).toFixed(1).replace(/\.0$/, '')}K`;
  return String(n);
}

export function escapeHtml(str = '') {
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

export function renderRichCaptionHtml(caption = '') {
  const safe = escapeHtml(caption);
  return safe
    .replace(/(^|\s)#([a-zA-Z0-9_]+)/g, (_, lead, tag) =>
      `${lead}<button type="button" class="inline-tag" data-hashtag="${tag.toLowerCase()}">#${tag}</button>`
    )
    .replace(/(^|\s)@([a-zA-Z0-9._]+)/g, (_, lead, user) =>
      `${lead}<button type="button" class="inline-mention" data-mention="${user.toLowerCase()}">@${user}</button>`
    )
    .replace(/\n/g, '<br>');
}
