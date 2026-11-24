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

// Populate url & anonKey after running supabase/schema.sql (NEVER place service_role / sb_secret_ keys here)
export const SUPABASE_WEB_CONFIG = {
  url: 'https://qotuvugkapaspowhbsbf.supabase.co',
  anonKey: 'sb_publishable_LbrQBbU_4qH18qjopmNwIg_ALsMhlln'
};

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

export function resolveImageSrc({ imageUrl, avatarUrl, imageData, imageKey, fallbackKey = 'img_post_coastal' } = {}) {
  const remoteUrl = [imageUrl, avatarUrl].find((u) => typeof u === 'string' && /^https?:\/\//i.test(u.trim()));
  if (remoteUrl) return remoteUrl.trim();
  if (typeof imageData === 'string' && imageData.trim().length > 0) {
    const clean = imageData.trim();
    if (clean.startsWith('data:') || /^https?:\/\//i.test(clean)) return clean;
    return `data:image/jpeg;base64,${clean}`;
  }
  return PRESET_IMAGE_MAP[imageKey] || PRESET_IMAGE_MAP[fallbackKey];
}

export function resolveVideoSrc(item = {}) {
  const url = [item?.videoUrl, item?.mediaUrl].find((u) => typeof u === 'string' && /^https?:\/\//i.test(u.trim()));
  if (url) return url.trim();
  if (typeof item?.videoData === 'string' && item.videoData.trim().length > 0) {
    const clean = item.videoData.trim();
    if (clean.startsWith('data:')) return clean;
    return `data:video/mp4;base64,${clean}`;
  }
  return '';
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

export const WEB_APP_URL = 'https://moosvi5.github.io/MOMI/';
export const ANDROID_APK_URL = 'https://github.com/MOOSVI5/MOMI/releases/latest/download/Momi-v2.1.apk';
export const ANDROID_RELEASES_URL = 'https://github.com/MOOSVI5/MOMI/releases/latest';

export const SHOWCASE_PERSONAS = [
  {
    username: 'elena.maris',
    displayName: 'Elena Maris',
    avatarKey: 'avatar_elena',
    imageKey: 'img_post_coastal',
    filterTag: 'WARM',
    aspectRatio: '4:5',
    category: 'Travel',
    location: 'Positano, Amalfi Coast',
    hashtags: ['amalfi', 'goldenhour', 'travel', 'momi'],
    caption: 'Golden hour spilling across the harbor stairs before sunset boats head back. #amalfi #goldenhour #momi'
  },
  {
    username: 'matcha.ritual',
    displayName: 'Matcha Ritual',
    avatarKey: 'avatar_matcha',
    imageKey: 'img_post_coffee',
    filterTag: 'PASTEL',
    aspectRatio: '1:1',
    category: 'Cafe & Food',
    location: 'Chiado, Lisbon',
    hashtags: ['matcha', 'coffee', 'cafeculture', 'momi'],
    caption: 'Slow iced ceremonial matcha and flaky croissants with morning light on stone tables. #matcha #cafeculture'
  },
  {
    username: 'atelier.nord',
    displayName: 'Atelier Nord',
    avatarKey: 'avatar_nord',
    imageKey: 'img_post_architecture',
    filterTag: 'CINEMA',
    aspectRatio: '4:5',
    category: 'Architecture',
    location: 'Copenhagen, Denmark',
    hashtags: ['architecture', 'minimal', 'design', 'momi'],
    caption: 'Sunbeams cutting diagonally across raw concrete and white oak galleries. #architecture #minimal'
  },
  {
    username: 'kenji.lens',
    displayName: 'Kenji Takahashi',
    avatarKey: 'avatar_kenji',
    imageKey: 'img_post_neon_street',
    filterTag: 'BLUE',
    aspectRatio: '1:1',
    category: 'Street',
    location: 'Shinjuku, Tokyo',
    hashtags: ['tokyo', 'street', 'photography', 'momi'],
    caption: 'Cobalt and maroon neon reflections after a sudden midnight rainstorm. #tokyo #street #photography'
  },
  {
    username: 'sora.frames',
    displayName: 'Sora Takahashi',
    avatarKey: 'avatar_sora',
    imageKey: 'img_post_neon_street',
    filterTag: 'MAROON',
    aspectRatio: '16:9',
    category: 'Street',
    location: 'Osaka, Japan',
    hashtags: ['nightwalk', 'aesthetic', 'momi'],
    caption: 'Framing late-night alley signs and warm lantern reflections after dinner. #nightwalk #aesthetic'
  },
  {
    username: 'layla.travels',
    displayName: 'Layla Al-Noor',
    avatarKey: 'avatar_aria',
    imageKey: 'img_post_coastal',
    filterTag: 'VIVID',
    aspectRatio: '4:5',
    category: 'Lifestyle',
    location: 'Bosphorus, Istanbul',
    hashtags: ['coastal', 'travel', 'lifestyle', 'momi'],
    caption: 'Breeze off the water and seaside tea stops with @elena.maris today. #coastal #travel'
  }
];

export const SHOWCASE_COMMENTS = [
  'Love the light and framing on this shot!',
  'This palette looks incredible on Momi!',
  'Adding this spot straight to my travel list.',
  'Crisp tones and such a clean composition!',
  'Bookmarking this for inspiration #momi'
];

export function buildPostShareUrl(postId, baseUrl = WEB_APP_URL) {
  const cleanId = String(postId || '').trim();
  const normalizedBase = String(baseUrl || WEB_APP_URL).split('?')[0].split('#')[0];
  if (!cleanId) return normalizedBase;
  return `${normalizedBase}?post=${encodeURIComponent(cleanId)}`;
}

export function buildProfileShareUrl(username, baseUrl = WEB_APP_URL) {
  const cleanUser = String(username || '').trim().replace(/^@/, '');
  const normalizedBase = String(baseUrl || WEB_APP_URL).split('?')[0].split('#')[0];
  if (!cleanUser) return normalizedBase;
  return `${normalizedBase}?user=${encodeURIComponent(cleanUser)}`;
}

export function parseDeepLinkFromLocation(search = '', hash = '') {
  const safeId = (val) => {
    const clean = String(val || '').trim();
    return /^[a-zA-Z0-9._-]{1,128}$/.test(clean) ? clean : null;
  };
  const params = new URLSearchParams(String(search || '').replace(/^\?/, ''));
  const postFromQuery = safeId(params.get('post') || params.get('p'));
  const userFromQuery = safeId((params.get('user') || params.get('u') || '').replace(/^@/, ''));

  const cleanHash = String(hash || '').replace(/^#/, '');
  const postFromHash = cleanHash.startsWith('post-') ? safeId(cleanHash.slice(5)) : null;
  const userFromHash = cleanHash.startsWith('user-') ? safeId(cleanHash.slice(5).replace(/^@/, '')) : null;

  return {
    postId: postFromQuery || postFromHash || null,
    username: userFromQuery || userFromHash || null
  };
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

export const DEFAULT_MONETIZATION_CONFIG = {
  admobEnabled: true,
  admobBannerUnitId: '',
  admobMrecUnitId: '',
  admobInterstitialUnitId: '',
  startIoEnabled: true,
  monetagEnabled: true,
  monetagDirectLinkUrl: '',
  monetagWebZoneId: '',
  monetagWebMetaTag: '',
  partnerPromoTitle: 'Featured Creator Deals & Partner Showcase',
  partnerPromoSubtitle: 'Explore verified creator collections, partner offers & exclusive community perks.',
  partnerPromoCta: 'Explore Partner',
  gamezopEnabled: false,
  gamezopPortalUrl: '',
  gamezopPublisherId: '',
  gamezopBannerTitle: 'Momi Arcade · Play Instant Games',
  updatedByEmail: ''
};

export const POST_BOOST_OPTIONS = [
  {
    tier: 'SPOTLIGHT',
    title: 'Spotlight Boost',
    badgeLabel: 'Boosted Spotlight',
    subtitle: 'Elevate your post higher in For You & Search discovery',
    creditsCost: 100
  },
  {
    tier: 'SUPER_BOOST',
    title: 'Super Boost',
    badgeLabel: 'Super Boosted',
    subtitle: 'Priority placement across Home Feed, Explore & Creator Spotlight',
    creditsCost: 250
  }
];

export const WEB_PERK_CATALOG = [
  {
    key: 'SUPPORTER_FRAME',
    name: 'Supporter Profile Badge & Live Glow',
    desc: 'Stand out in creator live chat & unlock Supporter account tier.',
    cost: 100,
    tierGranted: 'SUPPORTER',
    verificationStageGranted: 'NONE'
  },
  {
    key: 'CREATOR_BOOST',
    name: 'Creator Spotlight Boost',
    desc: 'Featured badge in Creator Discovery & elevated Creator Pro status.',
    cost: 250,
    tierGranted: 'CREATOR_PRO',
    verificationStageGranted: 'CREATOR_PRO'
  },
  {
    key: 'PRIORITY_VERIFICATION',
    name: 'Priority Verification Fast-Track',
    desc: 'Queue your account for official identity & creator verification review.',
    cost: 500,
    tierGranted: 'CREATOR_PRO',
    verificationStageGranted: 'PENDING_PERK'
  },
  {
    key: 'VIP_PATRON_PASS',
    name: 'Momi VIP Patron Pass',
    desc: 'Top VIP patron tier with priority support & subscriber perks.',
    cost: 1000,
    tierGranted: 'VIP',
    verificationStageGranted: 'CREATOR_PRO'
  }
];

export function normalizeMonetagDirectUrl(raw = '') {
  const trimmed = String(raw || '').trim();
  if (!trimmed.startsWith('https://')) return '';
  return trimmed.slice(0, 300);
}

export function sanitizeMonetagZoneId(raw = '') {
  const clean = String(raw || '').trim().replace(/[^a-zA-Z0-9_-]/g, '');
  return clean.slice(0, 40);
}

export function sanitizeGamezopPublisherId(raw = '') {
  const clean = String(raw || '').trim().replace(/[^a-zA-Z0-9_-]/g, '');
  return clean.slice(0, 40);
}

export function resolveGamezopUrl(cfg = {}) {
  if (!cfg?.gamezopEnabled) return '';
  const direct = normalizeMonetagDirectUrl(cfg.gamezopPortalUrl);
  if (direct) return direct;
  const pubId = sanitizeGamezopPublisherId(cfg.gamezopPublisherId);
  return pubId ? `https://www.gamezop.com/?id=${pubId}` : '';
}

export function sortFeedWithBoostPriority(posts = []) {
  if (!Array.isArray(posts) || posts.length <= 1) return Array.isArray(posts) ? posts : [];
  const tierRank = (p) => {
    const id = String(p?.id || '');
    const isPrioritized = Boolean(p?.isBoosted || p?.recommendedLabel) && !id.startsWith('showcase_');
    if (!isPrioritized) return 0;
    if (p?.boostTier === 'OWNER_RECOMMENDED') return 4;
    if (p?.boostTier === 'SUPER_BOOST') return 3;
    if (p?.boostTier === 'SPOTLIGHT') return 2;
    return 1;
  };
  return posts
    .map((p, idx) => ({ p, idx, rank: tierRank(p) }))
    .sort((a, b) => (b.rank !== a.rank ? b.rank - a.rank : a.idx - b.idx))
    .map((entry) => entry.p);
}

export function tierForTotalPurchased(totalPurchased = 0, unlockedPerks = []) {
  const perks = Array.isArray(unlockedPerks) ? unlockedPerks : [];
  if (perks.includes('VIP_PATRON_PASS') || totalPurchased >= 3000) return 'VIP';
  if (perks.includes('CREATOR_BOOST') || perks.includes('PRIORITY_VERIFICATION') || totalPurchased >= 1200) return 'CREATOR_PRO';
  if (perks.includes('SUPPORTER_FRAME') || totalPurchased >= 200) return 'SUPPORTER';
  return 'STANDARD';
}

export function verificationTierForPerks(current = 'NONE', unlockedPerks = []) {
  if (current === 'VERIFIED') return 'VERIFIED';
  const perks = Array.isArray(unlockedPerks) ? unlockedPerks : [];
  if (perks.includes('PRIORITY_VERIFICATION')) return 'PENDING_PERK';
  if (perks.includes('CREATOR_BOOST') || perks.includes('VIP_PATRON_PASS')) return 'CREATOR_PRO';
  return 'NONE';
}

