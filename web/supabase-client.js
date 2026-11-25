import { DEFAULT_MONETIZATION_CONFIG, SUPABASE_WEB_CONFIG } from './app-config.js';

export const SUPABASE_SCHEMA_TABLES = [
  'profiles',
  'posts',
  'comments',
  'stories',
  'follows',
  'user_interactions',
  'notifications',
  'threads',
  'chat_messages',
  'live_streams',
  'live_frames',
  'live_comments',
  'merchants',
  'market_products',
  'market_orders',
  'content_reports',
  'wallets',
  'credit_transactions',
  'app_config',
  'pages',
  'page_follows',
  'groups',
  'group_members',
  'showcase_accounts'
];

export const SUPABASE_STORAGE_BUCKETS = ['momi-media', 'momi-avatars', 'momi-videos'];

function isPrivateHost(host = '') {
  if (!host || host === 'localhost' || host.endsWith('.local') || host.endsWith('.internal')) {
    return true;
  }
  const parts = host.split('.');
  if (parts.length === 4 && parts.every((p) => /^\d+$/.test(p) && Number(p) <= 255)) {
    const a = Number(parts[0]);
    const b = Number(parts[1]);
    if (a === 10 || a === 127 || a === 0) return true;
    if (a === 169 && b === 254) return true;
    if (a === 192 && b === 168) return true;
    if (a === 172 && b >= 16 && b <= 31) return true;
  }
  return false;
}

export function normalizeSupabaseUrl(raw = '') {
  const trimmed = String(raw || '').trim().replace(/\/+$/, '');
  if (!trimmed.startsWith('https://') || /\s/.test(trimmed)) return '';
  try {
    const parsed = new URL(trimmed);
    if (parsed.protocol !== 'https:' || parsed.username || parsed.password || parsed.search || parsed.hash) {
      return '';
    }
    const host = parsed.hostname.toLowerCase().replace(/\.+$/, '');
    if (!host || !host.includes('.') || isPrivateHost(host)) return '';
    return parsed.port ? `https://${host}:${parsed.port}` : `https://${host}`;
  } catch {
    return '';
  }
}

export function isSafeSupabaseAnonKey(rawKey = '') {
  const clean = String(rawKey || '').trim();
  if (clean.length < 16 || /\s/.test(clean)) return false;
  if (clean.toLowerCase().startsWith('sb_secret_')) return false;
  return true;
}

export function isLegacySupabaseJwt(rawKey = '') {
  const clean = String(rawKey || '').trim();
  return clean.startsWith('eyJ') && clean.split('.').length >= 3;
}

export function buildSupabaseAuthHeaders(anonKey = '', accessToken = '') {
  const cleanAnon = String(anonKey || '').trim();
  const cleanAccess = String(accessToken || '').trim();
  const headers = {
    apikey: cleanAnon
  };
  const bearer = cleanAccess || (isLegacySupabaseJwt(cleanAnon) ? cleanAnon : '');
  if (bearer) {
    headers.Authorization = `Bearer ${bearer}`;
  }
  return headers;
}

export const SUPABASE_AUTH_STORAGE_KEY = 'momi_supabase_auth_v1';

export function loadSupabaseAuthSession() {
  try {
    if (typeof globalThis.localStorage === 'undefined') return null;
    const raw = globalThis.localStorage.getItem(SUPABASE_AUTH_STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    if (!parsed?.accessToken || !parsed?.user?.id) return null;
    return parsed;
  } catch {
    return null;
  }
}

export function saveSupabaseAuthSession(session) {
  try {
    if (typeof globalThis.localStorage === 'undefined') return;
    if (!session) {
      globalThis.localStorage.removeItem(SUPABASE_AUTH_STORAGE_KEY);
      return;
    }
    globalThis.localStorage.setItem(SUPABASE_AUTH_STORAGE_KEY, JSON.stringify(session));
  } catch {
    // Ignore storage quota / private mode errors
  }
}

export function clearSupabaseAuthSession() {
  saveSupabaseAuthSession(null);
}

export function getActiveSupabaseAccessToken() {
  return String(loadSupabaseAuthSession()?.accessToken || '').trim();
}

export function extractSupabaseJwtExpiryMs(token = '') {
  const parts = String(token || '').trim().split('.');
  if (parts.length < 2 || !parts[1]) return null;
  try {
    const b64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const pad = b64 + '='.repeat((4 - (b64.length % 4)) % 4);
    const jsonText = typeof globalThis.atob === 'function'
      ? globalThis.atob(pad)
      : Buffer.from(pad, 'base64').toString('utf8');
    const payload = JSON.parse(jsonText);
    return typeof payload?.exp === 'number' ? payload.exp * 1000 : null;
  } catch {
    return null;
  }
}

export function isSupabaseJwtExpiringSoon(token = '', skewMs = 60_000, nowMs = Date.now()) {
  const clean = String(token || '').trim();
  if (!clean) return false;
  const expMs = extractSupabaseJwtExpiryMs(clean);
  if (expMs == null) return false;
  return nowMs + skewMs >= expMs;
}

let inFlightWebRefresh = null;

export async function ensureFreshWebAccessToken(
  cfg = SUPABASE_WEB_CONFIG,
  fetchImpl = globalThis.fetch,
  forceRefresh = false,
  explicitToken = ''
) {
  const explicit = String(explicitToken || '').trim();
  const session = loadSupabaseAuthSession();
  if (explicit && (!forceRefresh || explicit !== session?.accessToken)) {
    if (!isSupabaseJwtExpiringSoon(explicit)) return explicit;
    if (!session?.refreshToken) return explicit;
  }
  if (!session?.accessToken) return explicit;
  const expSoon = forceRefresh ||
    isSupabaseJwtExpiringSoon(session.accessToken) ||
    (typeof session.expiresAt === 'number' && Date.now() + 60_000 >= session.expiresAt);
  if (!expSoon) return session.accessToken;
  if (!session.refreshToken) {
    return isSupabaseJwtExpiringSoon(session.accessToken, 0) ? '' : session.accessToken;
  }
  if (!inFlightWebRefresh) {
    inFlightWebRefresh = supabaseRefreshSession(cfg, session.refreshToken, fetchImpl)
      .finally(() => { inFlightWebRefresh = null; });
  }
  const refreshed = await inFlightWebRefresh;
  if (refreshed?.accessToken) return refreshed.accessToken;
  const current = loadSupabaseAuthSession();
  if (!current?.accessToken) return '';
  return isSupabaseJwtExpiringSoon(current.accessToken, 0) ? '' : current.accessToken;
}

export function isSupabaseClientReady(cfg = SUPABASE_WEB_CONFIG) {
  return Boolean(normalizeSupabaseUrl(cfg?.url) && isSafeSupabaseAnonKey(cfg?.anonKey));
}

function toIsoTimestamp(value) {
  if (!value) return new Date().toISOString();
  if (typeof value === 'string') return value;
  if (typeof value?.toDate === 'function') return value.toDate().toISOString();
  if (typeof value?.seconds === 'number') return new Date(value.seconds * 1000).toISOString();
  return new Date().toISOString();
}

export function mapMomiSnapshotToSupabaseBundle({
  profile = null,
  creators = [],
  posts = [],
  stories = [],
  merchants = [],
  products = [],
  monetizationConfig = DEFAULT_MONETIZATION_CONFIG,
  wallet = null
} = {}) {
  const realPosts = (Array.isArray(posts) ? posts : []).filter((p) => !String(p?.id || '').startsWith('showcase_'));
  const realStories = (Array.isArray(stories) ? stories : []).filter((s) => !String(s?.id || '').startsWith('showcase_'));
  const allCreators = Array.isArray(creators) ? creators : [];
  const profileRows = (profile && !allCreators.some((c) => c.userId === profile.userId)
    ? [profile, ...allCreators]
    : allCreators
  ).map((p) => ({
    user_id: String(p.userId || p.id || ''),
    email: String(p.email || '').trim().toLowerCase().slice(0, 160),
    username: String(p.username || 'momi.creator').slice(0, 50),
    display_name: String(p.displayName || 'Momi Creator').slice(0, 80),
    category: String(p.category || '').slice(0, 80),
    bio: String(p.bio || '').slice(0, 300),
    website: String(p.website || '').slice(0, 200),
    avatar_key: String(p.avatarKey || 'avatar_aria').slice(0, 64),
    avatar_url: p.avatarUrl || null,
    avatar_data: p.avatarData || null,
    followers_count: Number(p.followersCount || 0),
    following_count: Number(p.followingCount || 0),
    is_verified: Boolean(p.isVerified),
    role: String(p.role || 'USER'),
    moderation_scopes: Array.isArray(p.moderationScopes) ? p.moderationScopes : [],
    assigned_by_email: p.assignedByEmail || null,
    partner_consent: Boolean(p.partnerConsent),
    consent_updated_at: p.consentUpdatedAt ? toIsoTimestamp(p.consentUpdatedAt) : null,
    created_at: toIsoTimestamp(p.createdAt)
  }));

  const postRows = realPosts.map((p) => ({
    id: String(p.id || ''),
    user_id: String(p.userId || ''),
    author_username: String(p.authorUsername || 'momi.creator').slice(0, 50),
    author_display_name: String(p.authorDisplayName || 'Momi Creator').slice(0, 80),
    author_avatar_key: String(p.authorAvatarKey || 'avatar_aria').slice(0, 64),
    author_verified: Boolean(p.authorVerified),
    location: String(p.location || '').slice(0, 120),
    image_key: String(p.imageKey || 'img_post_coastal').slice(0, 64),
    image_url: p.imageUrl || null,
    image_data: p.imageData || null,
    extra_image_urls: Array.isArray(p.extraImageUrls) ? p.extraImageUrls : [],
    extra_image_datas: Array.isArray(p.extraImageDatas) ? p.extraImageDatas : [],
    media_type: String(p.mediaType || 'IMAGE'),
    video_url: p.videoUrl || null,
    video_data: p.videoData || null,
    video_duration_ms: Number(p.videoDurationMs || 0),
    filter_tag: String(p.filterTag || 'NONE'),
    aspect_ratio: String(p.aspectRatio || '1:1'),
    tagged_usernames: Array.isArray(p.taggedUsernames) ? p.taggedUsernames : [],
    hashtags: Array.isArray(p.hashtags) ? p.hashtags : [],
    caption: String(p.caption || '').slice(0, 1000),
    likes_count: Number(p.likesCount || 0),
    views_count: Number(p.viewsCount || 0),
    comments_count: Number(p.commentsCount || 0),
    category: String(p.category || 'For You').slice(0, 40),
    is_reel: Boolean(p.isReel),
    audio_title: String(p.audioTitle || '').slice(0, 120),
    audio_url: p.audioUrl || null,
    audio_data: p.audioData || null,
    is_boosted: Boolean(p.isBoosted),
    boost_tier: String(p.boostTier || ''),
    recommended_label: String(p.recommendedLabel || '').slice(0, 40),
    boosted_by_uid: p.boostedByUid || null,
    boosted_at: p.boostedAt ? toIsoTimestamp(p.boostedAt) : null,
    page_id: p.pageId || null,
    group_id: p.groupId || null,
    community_name: String(p.communityName || '').slice(0, 80),
    created_at: toIsoTimestamp(p.createdAt)
  }));

  const storyRows = realStories.map((s) => ({
    id: String(s.id || ''),
    user_id: String(s.userId || ''),
    username: String(s.username || 'momi.creator').slice(0, 50),
    avatar_key: String(s.avatarKey || 'avatar_aria').slice(0, 64),
    image_key: String(s.imageKey || 'img_post_coastal').slice(0, 64),
    image_url: s.imageUrl || null,
    image_data: s.imageData || null,
    media_type: String(s.mediaType || 'IMAGE'),
    video_url: s.videoUrl || null,
    video_data: s.videoData || null,
    video_duration_ms: Number(s.videoDurationMs || 0),
    audio_title: String(s.audioTitle || '').slice(0, 120),
    audio_url: s.audioUrl || null,
    audio_data: s.audioData || null,
    caption: String(s.caption || '').slice(0, 300),
    created_at: toIsoTimestamp(s.createdAt)
  }));

  const merchantRows = (Array.isArray(merchants) ? merchants : []).map((m) => ({
    user_id: String(m.userId || m.id || ''),
    owner_username: String(m.ownerUsername || 'momi.seller').slice(0, 50),
    store_name: String(m.storeName || 'Momi Store').slice(0, 80),
    tagline: String(m.tagline || '').slice(0, 160),
    category: String(m.category || 'Collectibles').slice(0, 40),
    location: String(m.location || '').slice(0, 120),
    avatar_key: String(m.avatarKey || 'avatar_aria').slice(0, 64),
    avatar_url: m.avatarUrl || null,
    avatar_data: m.avatarData || null,
    is_verified: Boolean(m.isVerified),
    verified_by_email: m.verifiedByEmail || null,
    total_sales: Number(m.totalSales || 0),
    created_at: toIsoTimestamp(m.createdAt)
  }));

  const productRows = (Array.isArray(products) ? products : []).map((p) => ({
    id: String(p.id || ''),
    seller_uid: String(p.sellerUid || ''),
    seller_username: String(p.sellerUsername || 'momi.seller').slice(0, 50),
    store_name: String(p.storeName || 'Momi Store').slice(0, 80),
    title: String(p.title || 'Momi Item').slice(0, 120),
    description: String(p.description || '').slice(0, 1000),
    category: String(p.category || 'Collectibles').slice(0, 40),
    price_usd: Math.max(0.01, Number(p.priceUsd || 0.01)),
    original_price_usd: Math.max(0, Number(p.originalPriceUsd || 0)),
    discount_percent: Math.min(95, Math.max(0, Number(p.discountPercent || 0))),
    free_shipping: Boolean(p.freeShipping ?? true),
    image_key: String(p.imageKey || 'img_post_coastal').slice(0, 64),
    image_url: p.imageUrl || null,
    image_data: p.imageData || null,
    extra_image_urls: Array.isArray(p.extraImageUrls) ? p.extraImageUrls : [],
    extra_image_datas: Array.isArray(p.extraImageDatas) ? p.extraImageDatas : [],
    video_url: p.videoUrl || null,
    video_data: p.videoData || null,
    video_duration_ms: Number(p.videoDurationMs || 0),
    stock_count: Math.max(0, Number(p.stockCount || 0)),
    is_featured: Boolean(p.isFeatured),
    created_at: toIsoTimestamp(p.createdAt)
  }));

  const cfg = monetizationConfig || DEFAULT_MONETIZATION_CONFIG;
  const appConfigRows = [{
    id: 'monetization',
    admob_enabled: Boolean(cfg.admobEnabled),
    admob_banner_unit_id: String(cfg.admobBannerUnitId || '').slice(0, 120),
    admob_mrec_unit_id: String(cfg.admobMrecUnitId || '').slice(0, 120),
    admob_interstitial_unit_id: String(cfg.admobInterstitialUnitId || '').slice(0, 120),
    start_io_enabled: Boolean(cfg.startIoEnabled),
    partner_promo_title: String(cfg.partnerPromoTitle || '').slice(0, 80),
    partner_promo_subtitle: String(cfg.partnerPromoSubtitle || '').slice(0, 180),
    partner_promo_cta: String(cfg.partnerPromoCta || '').slice(0, 40),
    announcement_id: String(cfg.announcementId || '').slice(0, 80),
    announcement_title: String(cfg.announcementTitle || '').slice(0, 120),
    announcement_body: String(cfg.announcementBody || '').slice(0, 500),
    latest_version_name: String(cfg.latestVersionName || '2.3').slice(0, 32),
    release_notes: String(cfg.releaseNotes || '').slice(0, 400),
    updated_by_email: String(cfg.updatedByEmail || '').slice(0, 120)
  }];

  const walletRows = wallet ? [{
    user_id: String(wallet.userId || wallet.id || ''),
    balance: Number(wallet.balance || 0),
    total_earned: Number(wallet.totalEarned || 0),
    total_spent: Number(wallet.totalSpent || 0),
    total_purchased: Number(wallet.totalPurchased || 0),
    account_tier: String(wallet.accountTier || 'STANDARD'),
    verification_tier: String(wallet.verificationTier || 'NONE'),
    unlocked_perks: Array.isArray(wallet.unlockedPerks) ? wallet.unlockedPerks : [],
    last_tx_id: wallet.lastTxId || null,
    created_at: toIsoTimestamp(wallet.createdAt)
  }] : [];

  return {
    schemaVersion: 'momi_supabase_v1',
    exportedAtUtc: new Date().toISOString(),
    tables: {
      profiles: profileRows,
      posts: postRows,
      stories: storyRows,
      merchants: merchantRows,
      market_products: productRows,
      wallets: walletRows,
      app_config: appConfigRows
    }
  };
}

export async function checkSupabaseHealth(cfg = SUPABASE_WEB_CONFIG, fetchImpl = globalThis.fetch) {
  const baseUrl = normalizeSupabaseUrl(cfg?.url);
  if (!baseUrl || !isSafeSupabaseAnonKey(cfg?.anonKey)) {
    return {
      configured: false,
      connected: false,
      message: 'Waiting for SUPABASE_WEB_CONFIG (url + anonKey) after running supabase/schema.sql.'
    };
  }
  const anonKey = String(cfg.anonKey).trim();
  const started = Date.now();
  try {
    const res = await fetchImpl(`${baseUrl}/rest/v1/app_config?select=id&limit=1`, {
      method: 'GET',
      headers: {
        ...buildSupabaseAuthHeaders(anonKey),
        Accept: 'application/json'
      }
    });
    const latencyMs = Math.max(1, Date.now() - started);
    if (res.ok) {
      return {
        configured: true,
        connected: true,
        latencyMs,
        message: `Connected to Supabase (${latencyMs} ms) — primary PostgreSQL & Storage active.`
      };
    }
    let hint = `Supabase responded HTTP ${res.status}.`;
    if (res.status === 404) {
      hint = 'Supabase reachable, but app_config table missing — run supabase/enable_sync.sql in SQL Editor.';
    } else if (res.status === 401 || res.status === 403) {
      hint = 'Supabase rejected key or RLS policy — run supabase/enable_sync.sql in SQL Editor.';
    }
    return {
      configured: true,
      connected: false,
      latencyMs,
      message: hint
    };
  } catch (err) {
    return {
      configured: true,
      connected: false,
      message: `Network check failed: ${err?.message || 'unknown error'}`
    };
  }
}

export async function selectSupabaseTable(
  cfg = SUPABASE_WEB_CONFIG,
  table,
  queryString = 'select=*&limit=50',
  fetchImpl = globalThis.fetch
) {
  const baseUrl = normalizeSupabaseUrl(cfg?.url);
  if (!baseUrl || !isSafeSupabaseAnonKey(cfg?.anonKey)) return [];
  if (!SUPABASE_SCHEMA_TABLES.includes(table)) return [];
  const anonKey = String(cfg.anonKey).trim();
  const cleanQuery = String(queryString || 'select=*').replace(/^\?/, '');
  try {
    let token = await ensureFreshWebAccessToken(cfg, fetchImpl);
    let res = await fetchImpl(`${baseUrl}/rest/v1/${table}?${cleanQuery}`, {
      method: 'GET',
      headers: {
        ...buildSupabaseAuthHeaders(anonKey, token),
        Accept: 'application/json'
      }
    });
    if (res.status === 401) {
      token = await ensureFreshWebAccessToken(cfg, fetchImpl, true);
      res = await fetchImpl(`${baseUrl}/rest/v1/${table}?${cleanQuery}`, {
        method: 'GET',
        headers: {
          ...buildSupabaseAuthHeaders(anonKey, token),
          Accept: 'application/json'
        }
      });
    }
    if (!res.ok) return [];
    const data = await res.json();
    return Array.isArray(data) ? data : [];
  } catch {
    return [];
  }
}

export async function upsertSupabaseTable(
  cfg = SUPABASE_WEB_CONFIG,
  table,
  rows = [],
  accessToken = '',
  fetchImpl = globalThis.fetch,
  onConflict = ''
) {
  const baseUrl = normalizeSupabaseUrl(cfg?.url);
  if (!baseUrl || !isSafeSupabaseAnonKey(cfg?.anonKey)) {
    throw new Error('Supabase URL and anonKey are not configured yet.');
  }
  if (!SUPABASE_SCHEMA_TABLES.includes(table)) {
    throw new Error(`Unsupported table: ${table}`);
  }
  if (!Array.isArray(rows) || rows.length === 0) return 0;

  const conflictQuery = onConflict ? `?on_conflict=${encodeURIComponent(onConflict)}` : '';
  const anonKey = String(cfg.anonKey).trim();
  let effectiveToken = await ensureFreshWebAccessToken(cfg, fetchImpl, false, accessToken);
  let res = await fetchImpl(`${baseUrl}/rest/v1/${table}${conflictQuery}`, {
    method: 'POST',
    headers: {
      ...buildSupabaseAuthHeaders(anonKey, effectiveToken),
      'Content-Type': 'application/json',
      Prefer: 'resolution=merge-duplicates,return=minimal'
    },
    body: JSON.stringify(rows)
  });
  if (res.status === 401 && !accessToken) {
    effectiveToken = await ensureFreshWebAccessToken(cfg, fetchImpl, true);
    res = await fetchImpl(`${baseUrl}/rest/v1/${table}${conflictQuery}`, {
      method: 'POST',
      headers: {
        ...buildSupabaseAuthHeaders(anonKey, effectiveToken),
        'Content-Type': 'application/json',
        Prefer: 'resolution=merge-duplicates,return=minimal'
      },
      body: JSON.stringify(rows)
    });
  }
  if (!res.ok) {
    let detail = '';
    try {
      detail = await res.text();
    } catch {
      detail = '';
    }
    throw new Error(`Upsert ${table} failed (HTTP ${res.status})${detail ? `: ${detail.slice(0, 140)}` : ''}`);
  }
  return rows.length;
}

export async function patchSupabaseRows(
  cfg = SUPABASE_WEB_CONFIG,
  table,
  filterQuery = '',
  patch = {},
  fetchImpl = globalThis.fetch
) {
  const baseUrl = normalizeSupabaseUrl(cfg?.url);
  if (!baseUrl || !isSafeSupabaseAnonKey(cfg?.anonKey) || !filterQuery) return false;
  if (!SUPABASE_SCHEMA_TABLES.includes(table)) return false;
  const anonKey = String(cfg.anonKey).trim();
  const cleanFilter = String(filterQuery).replace(/^\?/, '');
  let token = await ensureFreshWebAccessToken(cfg, fetchImpl);
  let res = await fetchImpl(`${baseUrl}/rest/v1/${table}?${cleanFilter}`, {
    method: 'PATCH',
    headers: {
      ...buildSupabaseAuthHeaders(anonKey, token),
      'Content-Type': 'application/json',
      Prefer: 'return=minimal'
    },
    body: JSON.stringify(patch)
  });
  if (res.status === 401) {
    token = await ensureFreshWebAccessToken(cfg, fetchImpl, true);
    res = await fetchImpl(`${baseUrl}/rest/v1/${table}?${cleanFilter}`, {
      method: 'PATCH',
      headers: {
        ...buildSupabaseAuthHeaders(anonKey, token),
        'Content-Type': 'application/json',
        Prefer: 'return=minimal'
      },
      body: JSON.stringify(patch)
    });
  }
  return res.ok;
}

export async function deleteSupabaseRows(
  cfg = SUPABASE_WEB_CONFIG,
  table,
  filterQuery = '',
  fetchImpl = globalThis.fetch
) {
  const baseUrl = normalizeSupabaseUrl(cfg?.url);
  if (!baseUrl || !isSafeSupabaseAnonKey(cfg?.anonKey) || !filterQuery) return false;
  if (!SUPABASE_SCHEMA_TABLES.includes(table)) return false;
  const anonKey = String(cfg.anonKey).trim();
  const cleanFilter = String(filterQuery).replace(/^\?/, '');
  let token = await ensureFreshWebAccessToken(cfg, fetchImpl);
  let res = await fetchImpl(`${baseUrl}/rest/v1/${table}?${cleanFilter}`, {
    method: 'DELETE',
    headers: {
      ...buildSupabaseAuthHeaders(anonKey, token),
      Prefer: 'return=minimal'
    }
  });
  if (res.status === 401) {
    token = await ensureFreshWebAccessToken(cfg, fetchImpl, true);
    res = await fetchImpl(`${baseUrl}/rest/v1/${table}?${cleanFilter}`, {
      method: 'DELETE',
      headers: {
        ...buildSupabaseAuthHeaders(anonKey, token),
        Prefer: 'return=minimal'
      }
    });
  }
  return res.ok;
}

export async function mergeSupabaseProfileIntoTarget(
  cfg = SUPABASE_WEB_CONFIG,
  sourceUserId = '',
  targetProfile = {},
  fetchImpl = globalThis.fetch
) {
  const baseUrl = normalizeSupabaseUrl(cfg?.url);
  const cleanSource = String(sourceUserId || '').trim();
  const cleanTarget = String(targetProfile?.userId || '').trim();
  if (!baseUrl || !isSafeSupabaseAnonKey(cfg?.anonKey) || !cleanSource || !cleanTarget || cleanSource === cleanTarget) {
    return { movedPosts: 0 };
  }
  const anonKey = String(cfg.anonKey).trim();
  const token = await ensureFreshWebAccessToken(cfg, fetchImpl);
  const headers = buildSupabaseAuthHeaders(anonKey, token);
  const encSource = encodeURIComponent(cleanSource);

  const targetRow = {
    user_id: cleanTarget,
    username: String(targetProfile.username || 'momi.user').slice(0, 50),
    email: String(targetProfile.email || '').trim().toLowerCase().slice(0, 160),
    display_name: String(targetProfile.displayName || targetProfile.username || 'Momi Creator').slice(0, 80),
    category: String(targetProfile.category || '').slice(0, 80),
    bio: String(targetProfile.bio || '').slice(0, 300),
    website: String(targetProfile.website || '').slice(0, 200),
    avatar_key: String(targetProfile.avatarKey || 'avatar_aria').slice(0, 64),
    avatar_url: targetProfile.avatarUrl || null,
    is_verified: Boolean(targetProfile.isVerified)
  };
  await patchSupabaseRows(cfg, 'profiles', `user_id=eq.${encSource}`, { email: '' }, fetchImpl).catch(() => {});
  await upsertSupabaseTable(cfg, 'profiles', [targetRow], token, fetchImpl, 'user_id');

  const countRes = await fetchImpl(`${baseUrl}/rest/v1/posts?select=id&user_id=eq.${encSource}`, { headers }).catch(() => null);
  const sourcePosts = countRes && countRes.ok ? await countRes.json() : [];
  const movedPosts = Array.isArray(sourcePosts) ? sourcePosts.length : 0;

  await patchSupabaseRows(cfg, 'posts', `user_id=eq.${encSource}`, {
    user_id: cleanTarget,
    author_username: targetRow.username,
    author_display_name: targetRow.display_name,
    author_avatar_key: targetRow.avatar_key,
    author_avatar_url: targetRow.avatar_url,
    author_verified: targetRow.is_verified
  }, fetchImpl);
  await patchSupabaseRows(cfg, 'stories', `user_id=eq.${encSource}`, {
    user_id: cleanTarget,
    username: targetRow.username,
    avatar_key: targetRow.avatar_key
  }, fetchImpl);
  await patchSupabaseRows(cfg, 'comments', `user_id=eq.${encSource}`, {
    user_id: cleanTarget,
    author_username: targetRow.username,
    author_avatar_key: targetRow.avatar_key
  }, fetchImpl);
  await patchSupabaseRows(cfg, 'pages', `owner_uid=eq.${encSource}`, { owner_uid: cleanTarget, owner_username: targetRow.username }, fetchImpl).catch(() => {});
  await patchSupabaseRows(cfg, 'groups', `owner_uid=eq.${encSource}`, { owner_uid: cleanTarget, owner_username: targetRow.username }, fetchImpl).catch(() => {});
  await patchSupabaseRows(cfg, 'market_products', `seller_uid=eq.${encSource}`, { seller_uid: cleanTarget, seller_username: targetRow.username }, fetchImpl).catch(() => {});
  await patchSupabaseRows(cfg, 'merchants', `user_id=eq.${encSource}`, { user_id: cleanTarget, owner_username: targetRow.username }, fetchImpl).catch(() => {});
  await patchSupabaseRows(cfg, 'notifications', `user_id=eq.${encSource}`, { user_id: cleanTarget }, fetchImpl).catch(() => {});

  const edgesRes = await fetchImpl(
    `${baseUrl}/rest/v1/follows?select=*&or=(follower_uid.eq.${encSource},target_uid.eq.${encSource})`,
    { headers }
  ).catch(() => null);
  if (edgesRes && edgesRes.ok) {
    const edges = await edgesRes.json();
    if (Array.isArray(edges) && edges.length > 0) {
      const migratedEdges = [];
      for (const edge of edges) {
        const nextFollower = edge.follower_uid === cleanSource ? cleanTarget : edge.follower_uid;
        const nextTarget = edge.target_uid === cleanSource ? cleanTarget : edge.target_uid;
        if (!nextFollower || !nextTarget || nextFollower === nextTarget) continue;
        migratedEdges.push({
          id: `${nextFollower}_${nextTarget}`,
          follower_uid: nextFollower,
          target_uid: nextTarget,
          follower_username: edge.follower_uid === cleanSource ? targetRow.username : edge.follower_username,
          target_username: edge.target_uid === cleanSource ? targetRow.username : edge.target_username,
          created_at: edge.created_at || new Date().toISOString()
        });
      }
      if (migratedEdges.length > 0) {
        await upsertSupabaseTable(cfg, 'follows', migratedEdges, token, fetchImpl, 'id', true).catch(() => {});
      }
    }
  }

  await deleteSupabaseRows(cfg, 'profiles', `user_id=eq.${encSource}`, fetchImpl);
  return { movedPosts };
}

export async function uploadWebFileToStorage(
  cfg = SUPABASE_WEB_CONFIG,
  bucket = 'momi-media',
  objectPath = '',
  blobOrFile,
  contentType = 'application/octet-stream',
  fetchImpl = globalThis.fetch
) {
  const baseUrl = normalizeSupabaseUrl(cfg?.url);
  if (!baseUrl || !isSafeSupabaseAnonKey(cfg?.anonKey)) return null;
  if (!SUPABASE_STORAGE_BUCKETS.includes(bucket) || !objectPath || !blobOrFile) return null;
  const anonKey = String(cfg.anonKey).trim();
  const safePath = String(objectPath)
    .split('/')
    .map((seg) => encodeURIComponent(seg))
    .join('/');
  try {
    let token = await ensureFreshWebAccessToken(cfg, fetchImpl);
    let res = await fetchImpl(`${baseUrl}/storage/v1/object/${bucket}/${safePath}`, {
      method: 'POST',
      headers: {
        ...buildSupabaseAuthHeaders(anonKey, token),
        'Content-Type': contentType || blobOrFile.type || 'application/octet-stream',
        'x-upsert': 'true'
      },
      body: blobOrFile
    });
    if (res.status === 401) {
      token = await ensureFreshWebAccessToken(cfg, fetchImpl, true);
      res = await fetchImpl(`${baseUrl}/storage/v1/object/${bucket}/${safePath}`, {
        method: 'POST',
        headers: {
          ...buildSupabaseAuthHeaders(anonKey, token),
          'Content-Type': contentType || blobOrFile.type || 'application/octet-stream',
          'x-upsert': 'true'
        },
        body: blobOrFile
      });
    }
    if (!res.ok) return null;
    return `${baseUrl}/storage/v1/object/public/${bucket}/${safePath}`;
  } catch {
    return null;
  }
}

export async function syncBundleToSupabase(
  cfg = SUPABASE_WEB_CONFIG,
  bundle,
  accessToken = '',
  fetchImpl = globalThis.fetch
) {
  const tables = bundle?.tables || {};
  let totalSynced = 0;

  // Deduplicate profiles by user_id and username so PostgreSQL UNIQUE constraint never aborts batch
  const seenUids = new Set();
  const seenUsernames = new Set();
  const safeProfiles = (tables.profiles || []).filter((p) => {
    const uid = String(p?.user_id || '').trim();
    const uname = String(p?.username || '').trim().toLowerCase();
    if (!uid || !uname || seenUids.has(uid) || seenUsernames.has(uname)) return false;
    seenUids.add(uid);
    seenUsernames.add(uname);
    return true;
  });

  if (safeProfiles.length > 0) {
    totalSynced += await upsertSupabaseTable(cfg, 'profiles', safeProfiles, accessToken, fetchImpl, 'user_id');
  }

  const knownUids = new Set(safeProfiles.map((p) => p.user_id));
  const safePosts = (tables.posts || []).filter((p) => p?.id && knownUids.has(p.user_id));
  if (safePosts.length > 0) {
    totalSynced += await upsertSupabaseTable(cfg, 'posts', safePosts, accessToken, fetchImpl, 'id');
  }

  const safeStories = (tables.stories || []).filter((s) => s?.id && knownUids.has(s.user_id));
  if (safeStories.length > 0) {
    totalSynced += await upsertSupabaseTable(cfg, 'stories', safeStories, accessToken, fetchImpl, 'id');
  }

  const safeProducts = (tables.market_products || []).filter((p) => p?.id && knownUids.has(p.seller_uid));
  if (safeProducts.length > 0) {
    totalSynced += await upsertSupabaseTable(cfg, 'market_products', safeProducts, accessToken, fetchImpl, 'id');
  }

  if (Array.isArray(tables.app_config) && tables.app_config.length > 0) {
    totalSynced += await upsertSupabaseTable(cfg, 'app_config', tables.app_config, accessToken, fetchImpl, 'id');
  }

  return totalSynced;
}

export async function fetchSupabaseFeed(cfg = SUPABASE_WEB_CONFIG, fetchImpl = globalThis.fetch) {
  const baseUrl = normalizeSupabaseUrl(cfg?.url);
  if (!baseUrl || !isSafeSupabaseAnonKey(cfg?.anonKey)) return null;
  const anonKey = String(cfg.anonKey).trim();
  const headers = {
    ...buildSupabaseAuthHeaders(anonKey),
    Accept: 'application/json'
  };
  try {
    const [
      postsRes,
      storiesRes,
      profilesRes,
      productsRes,
      merchantsRes,
      liveRes,
      configRes,
      pagesRes,
      groupsRes,
      showcaseRes,
      engagersRes
    ] = await Promise.all([
      fetchImpl(`${baseUrl}/rest/v1/posts?select=*&order=created_at.desc&limit=120`, { headers }),
      fetchImpl(`${baseUrl}/rest/v1/stories?select=*&order=created_at.desc&limit=50`, { headers }),
      fetchImpl(`${baseUrl}/rest/v1/profiles?select=*&limit=100`, { headers }),
      fetchImpl(`${baseUrl}/rest/v1/market_products?select=*&order=created_at.desc&limit=80`, { headers }),
      fetchImpl(`${baseUrl}/rest/v1/merchants?select=*&limit=60`, { headers }),
      fetchImpl(`${baseUrl}/rest/v1/live_streams?select=*&is_live=eq.true&order=updated_at.desc&limit=30`, { headers }),
      fetchImpl(`${baseUrl}/rest/v1/app_config?select=*&id=eq.monetization&limit=1`, { headers }),
      fetchImpl(`${baseUrl}/rest/v1/pages?select=*&order=followers_count.desc&limit=60`, { headers }).catch(() => ({ ok: false })),
      fetchImpl(`${baseUrl}/rest/v1/groups?select=*&order=members_count.desc&limit=60`, { headers }).catch(() => ({ ok: false })),
      fetchImpl(`${baseUrl}/rest/v1/showcase_accounts?select=*&order=username.asc&limit=40`, { headers }).catch(() => ({ ok: false })),
      fetchImpl(`${baseUrl}/rest/v1/user_interactions?select=user_id,target_type,target_id&target_type=in.(LIKED_POST,VIEWED_POST)&limit=1000`, { headers }).catch(() => ({ ok: false }))
    ]);
    if (!postsRes.ok && !profilesRes.ok) return null;
    const posts = postsRes.ok ? await postsRes.json() : [];
    const stories = storiesRes.ok ? await storiesRes.json() : [];
    const profiles = profilesRes.ok ? await profilesRes.json() : [];
    const products = productsRes.ok ? await productsRes.json() : [];
    const merchants = merchantsRes.ok ? await merchantsRes.json() : [];
    const liveStreams = liveRes.ok ? await liveRes.json() : [];
    const configs = configRes.ok ? await configRes.json() : [];
    const pages = pagesRes.ok ? await pagesRes.json() : [];
    const groups = groupsRes.ok ? await groupsRes.json() : [];
    const showcaseAccounts = showcaseRes.ok ? await showcaseRes.json() : [];
    const postInteractions = engagersRes.ok ? await engagersRes.json() : [];
    return {
      posts,
      stories,
      profiles,
      products,
      merchants,
      liveStreams,
      appConfig: configs[0] || null,
      pages,
      groups,
      showcaseAccounts,
      postInteractions
    };
  } catch {
    return null;
  }
}

export async function fetchUserSupabaseWorkspace(cfg = SUPABASE_WEB_CONFIG, uid = '', fetchImpl = globalThis.fetch) {
  if (!uid || !isSupabaseClientReady(cfg)) return null;
  const eUid = encodeURIComponent(uid);
  const [
    follows,
    interactions,
    notifications,
    threads,
    wallets,
    transactions,
    orders,
    pageFollows,
    groupMembers
  ] = await Promise.all([
    selectSupabaseTable(cfg, 'follows', `select=*&follower_uid=eq.${eUid}`, fetchImpl),
    selectSupabaseTable(cfg, 'user_interactions', `select=*&user_id=eq.${eUid}`, fetchImpl),
    selectSupabaseTable(cfg, 'notifications', `select=*&user_id=eq.${eUid}&order=created_at.desc&limit=50`, fetchImpl),
    selectSupabaseTable(cfg, 'threads', `select=*&participant_uids=cs.{${eUid}}&order=updated_at.desc&limit=40`, fetchImpl),
    selectSupabaseTable(cfg, 'wallets', `select=*&user_id=eq.${eUid}&limit=1`, fetchImpl),
    selectSupabaseTable(cfg, 'credit_transactions', `select=*&or=(sender_uid.eq.${eUid},recipient_uid.eq.${eUid})&order=created_at.desc&limit=40`, fetchImpl),
    selectSupabaseTable(cfg, 'market_orders', `select=*&or=(buyer_uid.eq.${eUid},seller_uid.eq.${eUid})&order=created_at.desc&limit=30`, fetchImpl),
    selectSupabaseTable(cfg, 'page_follows', `select=*&user_id=eq.${eUid}`, fetchImpl),
    selectSupabaseTable(cfg, 'group_members', `select=*&user_id=eq.${eUid}`, fetchImpl)
  ]);
  return {
    follows,
    interactions,
    notifications,
    threads,
    wallet: wallets[0] || null,
    transactions,
    orders,
    pageFollows,
    groupMembers
  };
}

export function mapSupabasePostToWeb(row = {}) {
  return {
    id: String(row.id || ''),
    userId: String(row.user_id || ''),
    authorUsername: String(row.author_username || 'momi.creator'),
    authorDisplayName: String(row.author_display_name || row.author_username || 'Creator'),
    authorAvatarKey: String(row.author_avatar_key || 'avatar_aria'),
    authorVerified: Boolean(row.author_verified),
    location: String(row.location || ''),
    imageKey: String(row.image_key || 'img_post_coastal'),
    imageUrl: row.image_url || null,
    imageData: row.image_data || null,
    extraImageUrls: Array.isArray(row.extra_image_urls) ? row.extra_image_urls : [],
    extraImageDatas: Array.isArray(row.extra_image_datas) ? row.extra_image_datas : [],
    mediaType: String(row.media_type || 'IMAGE'),
    videoUrl: row.video_url || null,
    videoData: row.video_data || null,
    videoDurationMs: Number(row.video_duration_ms || 0),
    filterTag: String(row.filter_tag || 'NONE'),
    aspectRatio: String(row.aspect_ratio || '1:1'),
    taggedUsernames: Array.isArray(row.tagged_usernames) ? row.tagged_usernames : [],
    hashtags: Array.isArray(row.hashtags) ? row.hashtags : [],
    caption: String(row.caption || ''),
    likesCount: Number(row.likes_count || 0),
    viewsCount: Number(row.views_count || 0),
    commentsCount: Number(row.comments_count || 0),
    category: String(row.category || 'For You'),
    isReel: Boolean(row.is_reel),
    audioTitle: String(row.audio_title || ''),
    audioUrl: row.audio_url || null,
    audioData: row.audio_data || null,
    isBoosted: Boolean(row.is_boosted),
    boostTier: String(row.boost_tier || ''),
    recommendedLabel: String(row.recommended_label || ''),
    isSponsored: Boolean(row.is_sponsored),
    sponsorLabel: String(row.sponsor_label || ''),
    directLinkUrl: String(row.direct_link_url || ''),
    pageId: row.page_id || null,
    groupId: row.group_id || null,
    communityName: String(row.community_name || ''),
    createdAt: row.created_at ? { seconds: Math.floor(new Date(row.created_at).getTime() / 1000) } : null
  };
}

export function mapSupabaseStoryToWeb(row = {}) {
  return {
    id: String(row.id || ''),
    userId: String(row.user_id || ''),
    username: String(row.username || 'momi.creator'),
    avatarKey: String(row.avatar_key || 'avatar_aria'),
    imageKey: String(row.image_key || 'img_post_coastal'),
    imageUrl: row.image_url || null,
    imageData: row.image_data || null,
    mediaType: String(row.media_type || 'IMAGE'),
    videoUrl: row.video_url || null,
    videoData: row.video_data || null,
    videoDurationMs: Number(row.video_duration_ms || 0),
    audioTitle: String(row.audio_title || ''),
    audioUrl: row.audio_url || null,
    audioData: row.audio_data || null,
    caption: String(row.caption || ''),
    createdAt: row.created_at ? { seconds: Math.floor(new Date(row.created_at).getTime() / 1000) } : null
  };
}

export function mapSupabaseProductToWeb(row = {}) {
  return {
    id: String(row.id || ''),
    sellerUid: String(row.seller_uid || ''),
    sellerUsername: String(row.seller_username || 'momi.seller'),
    storeName: String(row.store_name || 'Momi Store'),
    title: String(row.title || 'Momi Item'),
    description: String(row.description || ''),
    category: String(row.category || 'Collectibles'),
    priceUsd: Number(row.price_usd || 0),
    originalPriceUsd: Number(row.original_price_usd || 0),
    discountPercent: Number(row.discount_percent || 0),
    freeShipping: Boolean(row.free_shipping ?? true),
    imageKey: String(row.image_key || 'img_post_coastal'),
    imageUrl: row.image_url || null,
    imageData: row.image_data || null,
    extraImageUrls: Array.isArray(row.extra_image_urls) ? row.extra_image_urls : [],
    extraImageDatas: Array.isArray(row.extra_image_datas) ? row.extra_image_datas : [],
    videoUrl: row.video_url || null,
    videoData: row.video_data || null,
    videoDurationMs: Number(row.video_duration_ms || 0),
    stockCount: Number(row.stock_count || 0),
    isFeatured: Boolean(row.is_featured),
    createdAt: row.created_at ? { seconds: Math.floor(new Date(row.created_at).getTime() / 1000) } : null
  };
}

export function mapSupabaseMerchantToWeb(row = {}) {
  return {
    userId: String(row.user_id || ''),
    ownerUsername: String(row.owner_username || 'momi.seller'),
    storeName: String(row.store_name || 'Momi Store'),
    tagline: String(row.tagline || ''),
    category: String(row.category || 'Collectibles'),
    location: String(row.location || ''),
    avatarKey: String(row.avatar_key || 'avatar_aria'),
    avatarUrl: row.avatar_url || null,
    avatarData: row.avatar_data || null,
    isVerified: Boolean(row.is_verified),
    totalSales: Number(row.total_sales || 0)
  };
}

export function mapSupabaseConfigToWeb(row = {}) {
  return {
    ...DEFAULT_MONETIZATION_CONFIG,
    admobEnabled: Boolean(row.admob_enabled ?? DEFAULT_MONETIZATION_CONFIG.admobEnabled),
    admobBannerUnitId: String(row.admob_banner_unit_id || ''),
    admobMrecUnitId: String(row.admob_mrec_unit_id || ''),
    admobInterstitialUnitId: String(row.admob_interstitial_unit_id || ''),
    startIoEnabled: Boolean(row.start_io_enabled ?? DEFAULT_MONETIZATION_CONFIG.startIoEnabled),
    partnerPromoTitle: String(row.partner_promo_title || DEFAULT_MONETIZATION_CONFIG.partnerPromoTitle),
    partnerPromoSubtitle: String(row.partner_promo_subtitle || DEFAULT_MONETIZATION_CONFIG.partnerPromoSubtitle),
    partnerPromoCta: String(row.partner_promo_cta || DEFAULT_MONETIZATION_CONFIG.partnerPromoCta),
    announcementId: String(row.announcement_id || ''),
    announcementTitle: String(row.announcement_title || ''),
    announcementBody: String(row.announcement_body || ''),
    latestVersionName: String(row.latest_version_name || '2.3'),
    releaseNotes: String(row.release_notes || '')
  };
}

export function mapSupabasePageToWeb(row = {}) {
  return {
    id: String(row.id || ''),
    ownerUid: String(row.owner_uid || ''),
    ownerUsername: String(row.owner_username || 'momi.creator'),
    name: String(row.name || 'Creator Page'),
    handle: String(row.handle || ''),
    category: String(row.category || 'Creator'),
    bio: String(row.bio || ''),
    coverKey: String(row.cover_key || 'img_post_coastal'),
    avatarUrl: row.avatar_url || null,
    isVerified: Boolean(row.is_verified),
    followersCount: Number(row.followers_count || 0)
  };
}

export function mapSupabaseGroupToWeb(row = {}) {
  return {
    id: String(row.id || ''),
    ownerUid: String(row.owner_uid || ''),
    ownerUsername: String(row.owner_username || 'momi.creator'),
    name: String(row.name || 'Momi Group'),
    category: String(row.category || 'Community'),
    description: String(row.description || ''),
    privacy: String(row.privacy || 'PUBLIC'),
    coverKey: String(row.cover_key || 'img_post_architecture'),
    membersCount: Number(row.members_count || 1)
  };
}

export function mapSupabaseShowcaseAccountToWeb(row = {}) {
  return {
    username: String(row.username || ''),
    displayName: String(row.display_name || row.username || 'Starter'),
    avatarKey: String(row.avatar_key || 'avatar_elena'),
    bio: String(row.bio || ''),
    category: String(row.category || 'Creator'),
    location: String(row.location || ''),
    isAuthorized: Boolean(row.is_authorized)
  };
}

export function mapSupabaseProfileToWeb(row = {}) {
  return {
    userId: String(row.user_id || ''),
    email: String(row.email || '').trim().toLowerCase(),
    username: String(row.username || 'momi.creator'),
    displayName: String(row.display_name || row.username || 'Creator'),
    category: String(row.category || 'Creator'),
    bio: String(row.bio || ''),
    website: String(row.website || ''),
    avatarKey: String(row.avatar_key || 'avatar_aria'),
    avatarUrl: row.avatar_url || null,
    avatarData: row.avatar_data || null,
    followersCount: Number(row.followers_count || 0),
    followingCount: Number(row.following_count || 0),
    isVerified: Boolean(row.is_verified),
    role: String(row.role || 'USER'),
    moderationScopes: Array.isArray(row.moderation_scopes) ? row.moderation_scopes : [],
    partnerConsent: Boolean(row.partner_consent)
  };
}

export function mapSupabaseLiveStreamToWeb(row = {}) {
  return {
    id: String(row.id || row.user_id || ''),
    userId: String(row.user_id || row.id || ''),
    username: String(row.username || 'momi.creator'),
    displayName: String(row.display_name || row.username || 'Creator'),
    avatarKey: String(row.avatar_key || 'avatar_aria'),
    authorVerified: Boolean(row.author_verified),
    title: String(row.title || 'Live Broadcast'),
    category: String(row.category || 'Live'),
    isLive: Boolean(row.is_live),
    viewerCount: Number(row.viewer_count || 0),
    likesCount: Number(row.likes_count || 0),
    giftsCredits: Number(row.gifts_credits || 0),
    updatedAtMs: row.updated_at ? new Date(row.updated_at).getTime() : Date.now()
  };
}

export function mapSupabaseThreadToWeb(row = {}) {
  return {
    id: String(row.id || ''),
    participantUids: Array.isArray(row.participant_uids) ? row.participant_uids : [],
    userAId: String(row.user_a_id || ''),
    userAUsername: String(row.user_a_username || ''),
    userADisplayName: String(row.user_a_display_name || ''),
    userAAvatarKey: String(row.user_a_avatar_key || 'avatar_aria'),
    userBId: String(row.user_b_id || ''),
    userBUsername: String(row.user_b_username || ''),
    userBDisplayName: String(row.user_b_display_name || ''),
    userBAvatarKey: String(row.user_b_avatar_key || 'avatar_aria'),
    lastMessage: String(row.last_message || ''),
    lastSenderUid: String(row.last_sender_uid || ''),
    unreadUid: String(row.unread_uid || ''),
    timeLabel: String(row.time_label || 'Now'),
    unreadCount: Number(row.unread_count || 0),
    isOnline: Boolean(row.is_online)
  };
}

export function mapSupabaseChatMessageToWeb(row = {}) {
  return {
    id: String(row.id || ''),
    threadId: String(row.thread_id || ''),
    participantUids: Array.isArray(row.participant_uids) ? row.participant_uids : [],
    senderUid: String(row.sender_uid || ''),
    senderUsername: String(row.sender_username || ''),
    text: String(row.text || ''),
    messageType: String(row.message_type || 'TEXT'),
    mediaUrl: row.media_url || null,
    imageData: row.image_data || null,
    videoData: row.video_data || null,
    videoDurationMs: Number(row.video_duration_ms || 0),
    audioData: row.audio_data || null,
    audioDurationMs: Number(row.audio_duration_ms || 0),
    createdAt: row.created_at
  };
}

export function mapSupabaseNotificationToWeb(row = {}) {
  return {
    id: String(row.id || ''),
    userId: String(row.user_id || ''),
    actorUid: String(row.actor_uid || ''),
    actorUsername: String(row.actor_username || ''),
    actorAvatarKey: String(row.actor_avatar_key || 'avatar_aria'),
    message: String(row.message || ''),
    timeLabel: String(row.time_label || 'Just now'),
    section: String(row.section || 'Today'),
    previewImageKey: row.preview_image_key || null,
    isFollowAction: Boolean(row.is_follow_action),
    isFollowingBack: Boolean(row.is_following_back),
    isUnread: Boolean(row.is_unread)
  };
}

export function mapSupabaseWalletToWeb(row = {}) {
  return {
    userId: String(row.user_id || ''),
    balance: Number(row.balance || 0),
    totalEarned: Number(row.total_earned || 0),
    totalSpent: Number(row.total_spent || 0),
    totalPurchased: Number(row.total_purchased || 0),
    accountTier: String(row.account_tier || 'STANDARD'),
    verificationTier: String(row.verification_tier || 'NONE'),
    unlockedPerks: Array.isArray(row.unlocked_perks) ? row.unlocked_perks : [],
    lastTxId: row.last_tx_id || null
  };
}

export function mapSupabaseTransactionToWeb(row = {}) {
  return {
    txId: String(row.tx_id || ''),
    type: String(row.type || ''),
    senderUid: String(row.sender_uid || ''),
    senderUsername: String(row.sender_username || ''),
    recipientUid: String(row.recipient_uid || ''),
    recipientUsername: String(row.recipient_username || ''),
    creditsAmount: Number(row.credits_amount || 0),
    priceUsd: Number(row.price_usd || 0),
    paymentMethod: String(row.payment_method || ''),
    giftKey: row.gift_key || null,
    giftName: row.gift_name || null,
    streamId: row.stream_id || null,
    perkKey: row.perk_key || null,
    postId: row.post_id || null,
    recipientClaimed: Boolean(row.recipient_claimed ?? true),
    status: String(row.status || 'COMPLETED')
  };
}

export function mapSupabaseOrderToWeb(row = {}) {
  return {
    id: String(row.id || ''),
    buyerUid: String(row.buyer_uid || ''),
    buyerUsername: String(row.buyer_username || ''),
    sellerUid: String(row.seller_uid || ''),
    sellerStoreName: String(row.seller_store_name || ''),
    productId: String(row.product_id || ''),
    productTitle: String(row.product_title || ''),
    productImageKey: String(row.product_image_key || 'img_post_coastal'),
    productImageData: row.product_image_data || null,
    priceUsd: Number(row.price_usd || 0),
    shippingAddress: String(row.shipping_address || ''),
    status: String(row.status || 'PLACED')
  };
}

// ---------------------------------------------------------------------------
// Supabase Auth (Google OAuth + Email/Password + Session Refresh)
// ---------------------------------------------------------------------------

export function mapSupabaseAuthUserToWeb(user = {}) {
  const meta = user.user_metadata || {};
  return {
    uid: String(user.id || ''),
    id: String(user.id || ''),
    email: String(user.email || ''),
    displayName: String(meta.full_name || meta.name || user.email?.split('@')[0] || 'Momi Creator'),
    photoURL: meta.avatar_url || meta.picture || null,
    emailVerified: Boolean(user.email_confirmed_at || user.confirmed_at)
  };
}

export function buildSupabaseGoogleOAuthUrl(cfg = SUPABASE_WEB_CONFIG, redirectTo = '') {
  const baseUrl = normalizeSupabaseUrl(cfg?.url);
  if (!baseUrl) return '';
  const params = new URLSearchParams({
    provider: 'google',
    redirect_to: redirectTo || (typeof globalThis.location !== 'undefined' ? globalThis.location.origin + globalThis.location.pathname : '')
  });
  return `${baseUrl}/auth/v1/authorize?${params.toString()}`;
}

export function parseSupabaseOAuthCallback(hashStr = '', searchStr = '') {
  const cleanHash = String(hashStr || '').replace(/^#/, '');
  const cleanSearch = String(searchStr || '').replace(/^\?/, '');
  const hashParams = new URLSearchParams(cleanHash);
  const searchParams = new URLSearchParams(cleanSearch);
  const errorDesc = hashParams.get('error_description') || hashParams.get('error') || searchParams.get('error_description') || searchParams.get('error') || '';
  const accessToken = hashParams.get('access_token') || '';
  const refreshToken = hashParams.get('refresh_token') || '';
  const expiresIn = Number(hashParams.get('expires_in') || 3600);
  if (!accessToken && !errorDesc) return null;
  return {
    accessToken,
    refreshToken,
    expiresIn,
    errorDescription: errorDesc
  };
}

async function parseSupabaseAuthError(res, fallbackMsg) {
  try {
    const data = await res.json();
    return data?.msg || data?.error_description || data?.message || data?.error || fallbackMsg;
  } catch {
    return fallbackMsg;
  }
}

export async function supabaseFetchAuthUser(cfg = SUPABASE_WEB_CONFIG, accessToken = '', fetchImpl = globalThis.fetch) {
  const baseUrl = normalizeSupabaseUrl(cfg?.url);
  if (!baseUrl || !isSafeSupabaseAnonKey(cfg?.anonKey) || !accessToken) return null;
  const anonKey = String(cfg.anonKey).trim();
  const res = await fetchImpl(`${baseUrl}/auth/v1/user`, {
    method: 'GET',
    headers: {
      apikey: anonKey,
      Authorization: `Bearer ${accessToken}`
    }
  });
  if (!res.ok) return null;
  const data = await res.json();
  return data?.id ? mapSupabaseAuthUserToWeb(data) : null;
}

export async function supabaseSignInWithPassword(cfg = SUPABASE_WEB_CONFIG, email, password, fetchImpl = globalThis.fetch) {
  const baseUrl = normalizeSupabaseUrl(cfg?.url);
  if (!baseUrl || !isSafeSupabaseAnonKey(cfg?.anonKey)) throw new Error('Supabase configuration missing.');
  const anonKey = String(cfg.anonKey).trim();
  const res = await fetchImpl(`${baseUrl}/auth/v1/token?grant_type=password`, {
    method: 'POST',
    headers: { apikey: anonKey, 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: String(email || '').trim(), password: String(password || '') })
  });
  if (!res.ok) {
    throw new Error(await parseSupabaseAuthError(res, `Sign in failed (HTTP ${res.status})`));
  }
  const data = await res.json();
  if (!data?.access_token || !data?.user?.id) throw new Error('Invalid Supabase login response.');
  const session = {
    accessToken: data.access_token,
    refreshToken: data.refresh_token || '',
    expiresAt: Date.now() + Number(data.expires_in || 3600) * 1000,
    user: mapSupabaseAuthUserToWeb(data.user)
  };
  saveSupabaseAuthSession(session);
  return session;
}

export async function supabaseSignUpWithEmail(cfg = SUPABASE_WEB_CONFIG, email, password, displayName = '', fetchImpl = globalThis.fetch) {
  const baseUrl = normalizeSupabaseUrl(cfg?.url);
  if (!baseUrl || !isSafeSupabaseAnonKey(cfg?.anonKey)) throw new Error('Supabase configuration missing.');
  const anonKey = String(cfg.anonKey).trim();
  const body = { email: String(email || '').trim(), password: String(password || '') };
  if (displayName && displayName.trim()) {
    body.data = { full_name: displayName.trim() };
  }
  const res = await fetchImpl(`${baseUrl}/auth/v1/signup`, {
    method: 'POST',
    headers: { apikey: anonKey, 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  });
  if (!res.ok) {
    throw new Error(await parseSupabaseAuthError(res, `Sign up failed (HTTP ${res.status})`));
  }
  const data = await res.json();
  if (data?.access_token && data?.user?.id) {
    const session = {
      accessToken: data.access_token,
      refreshToken: data.refresh_token || '',
      expiresAt: Date.now() + Number(data.expires_in || 3600) * 1000,
      user: mapSupabaseAuthUserToWeb(data.user)
    };
    saveSupabaseAuthSession(session);
    return { session, confirmationRequired: false };
  }
  return { session: null, confirmationRequired: true };
}

export async function supabaseVerifyEmailOtp(
  cfg = SUPABASE_WEB_CONFIG,
  email,
  token,
  type = 'signup',
  fetchImpl = globalThis.fetch
) {
  const baseUrl = normalizeSupabaseUrl(cfg?.url);
  if (!baseUrl || !isSafeSupabaseAnonKey(cfg?.anonKey)) throw new Error('Supabase configuration missing.');
  const anonKey = String(cfg.anonKey).trim();
  const cleanEmail = String(email || '').trim().toLowerCase();
  const cleanCode = String(token || '').trim().replace(/\s+/g, '');
  if (!cleanEmail || cleanCode.length < 4) {
    throw new Error('Enter the verification code sent to your email.');
  }
  const res = await fetchImpl(`${baseUrl}/auth/v1/verify`, {
    method: 'POST',
    headers: { apikey: anonKey, 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: cleanEmail, token: cleanCode, type })
  });
  if (!res.ok) {
    throw new Error(await parseSupabaseAuthError(res, `Verification failed (HTTP ${res.status})`));
  }
  const data = await res.json();
  if (!data?.access_token || !data?.user?.id) {
    throw new Error('Email verified. Please sign in with your email and password.');
  }
  const session = {
    accessToken: data.access_token,
    refreshToken: data.refresh_token || '',
    expiresAt: Date.now() + Number(data.expires_in || 3600) * 1000,
    user: mapSupabaseAuthUserToWeb(data.user)
  };
  saveSupabaseAuthSession(session);
  return { session };
}

export async function supabaseResendSignupOtp(
  cfg = SUPABASE_WEB_CONFIG,
  email,
  fetchImpl = globalThis.fetch
) {
  const baseUrl = normalizeSupabaseUrl(cfg?.url);
  if (!baseUrl || !isSafeSupabaseAnonKey(cfg?.anonKey)) throw new Error('Supabase configuration missing.');
  const anonKey = String(cfg.anonKey).trim();
  const cleanEmail = String(email || '').trim().toLowerCase();
  if (!cleanEmail.includes('@')) throw new Error('Valid email is required.');
  const res = await fetchImpl(`${baseUrl}/auth/v1/resend`, {
    method: 'POST',
    headers: { apikey: anonKey, 'Content-Type': 'application/json' },
    body: JSON.stringify({ type: 'signup', email: cleanEmail })
  });
  if (!res.ok) {
    throw new Error(await parseSupabaseAuthError(res, `Resend code failed (HTTP ${res.status})`));
  }
  return true;
}

export async function supabaseRefreshSession(cfg = SUPABASE_WEB_CONFIG, refreshToken, fetchImpl = globalThis.fetch) {
  const baseUrl = normalizeSupabaseUrl(cfg?.url);
  if (!baseUrl || !isSafeSupabaseAnonKey(cfg?.anonKey) || !refreshToken) return null;
  const anonKey = String(cfg.anonKey).trim();
  try {
    const res = await fetchImpl(`${baseUrl}/auth/v1/token?grant_type=refresh_token`, {
      method: 'POST',
      headers: { apikey: anonKey, 'Content-Type': 'application/json' },
      body: JSON.stringify({ refresh_token: refreshToken })
    });
    if (!res.ok) {
      if (res.status === 400 || res.status === 401) {
        clearSupabaseAuthSession();
      }
      return null;
    }
    const data = await res.json();
    if (!data?.access_token || !data?.user?.id) return null;
    const session = {
      accessToken: data.access_token,
      refreshToken: data.refresh_token || refreshToken,
      expiresAt: Date.now() + Number(data.expires_in || 3600) * 1000,
      user: mapSupabaseAuthUserToWeb(data.user)
    };
    saveSupabaseAuthSession(session);
    return session;
  } catch {
    return null;
  }
}

export async function supabaseSignOut(cfg = SUPABASE_WEB_CONFIG, accessToken = '', fetchImpl = globalThis.fetch) {
  const baseUrl = normalizeSupabaseUrl(cfg?.url);
  const token = accessToken || getActiveSupabaseAccessToken();
  clearSupabaseAuthSession();
  if (!baseUrl || !isSafeSupabaseAnonKey(cfg?.anonKey) || !token) return;
  try {
    await fetchImpl(`${baseUrl}/auth/v1/logout`, {
      method: 'POST',
      headers: {
        apikey: String(cfg.anonKey).trim(),
        Authorization: `Bearer ${token}`
      }
    });
  } catch {
    // Ignore logout network failures after clearing local session
  }
}

// ---------------------------------------------------------------------------
// Supabase Realtime CDC WebSocket Client (Phoenix Protocol)
// ---------------------------------------------------------------------------

export const SOCIAL_REALTIME_TABLES = ['posts', 'comments', 'user_interactions', 'likes'];

export function buildSupabaseRealtimeWsUrl(cfg = SUPABASE_WEB_CONFIG) {
  const baseUrl = normalizeSupabaseUrl(cfg?.url);
  if (!baseUrl || !isSafeSupabaseAnonKey(cfg?.anonKey)) return '';
  const wsBase = baseUrl.replace(/^https:\/\//i, 'wss://');
  const anonKey = encodeURIComponent(String(cfg.anonKey).trim());
  return `${wsBase}/realtime/v1/websocket?apikey=${anonKey}&vsn=1.0.0`;
}

export function buildSupabaseRealtimeJoinMessage(
  ref = '1',
  accessToken = '',
  tables = SUPABASE_SCHEMA_TABLES
) {
  const cleanTables = Array.isArray(tables)
    ? [...new Set(tables.map((t) => String(t || '').trim()).filter(Boolean))]
    : [];
  const postgresChanges = cleanTables.length > 0
    ? cleanTables.map((table) => ({ event: '*', schema: 'public', table }))
    : [{ event: '*', schema: 'public' }];
  const payload = {
    config: {
      broadcast: { self: false },
      presence: { key: '' },
      postgres_changes: postgresChanges
    }
  };
  const token = String(accessToken || '').trim();
  if (token) {
    payload.access_token = token;
  }
  return {
    topic: 'realtime:public',
    event: 'phx_join',
    payload,
    ref: String(ref || '1')
  };
}

export function parseSupabaseRealtimeMessage(rawData) {
  if (!rawData) return null;
  let frame = null;
  if (typeof rawData === 'string') {
    try {
      frame = JSON.parse(rawData);
    } catch {
      return null;
    }
  } else if (typeof rawData === 'object') {
    frame = rawData;
  }
  if (!frame || typeof frame !== 'object') return null;

  if (frame.event === 'postgres_changes') {
    const inner = frame.payload?.data || frame.payload || {};
    const table = String(inner.table || '').trim();
    if (!table) return null;
    return {
      kind: 'postgres_changes',
      table,
      schema: String(inner.schema || 'public'),
      eventType: String(inner.type || inner.eventType || 'UPDATE').toUpperCase(),
      record: inner.record || inner.new || null,
      oldRecord: inner.old_record || inner.old || null
    };
  }

  if (frame.event === 'phx_reply') {
    return {
      kind: 'phx_reply',
      topic: String(frame.topic || ''),
      status: String(frame.payload?.status || ''),
      ref: frame.ref ? String(frame.ref) : ''
    };
  }

  if (frame.event === 'system') {
    return {
      kind: 'system',
      status: String(frame.payload?.status || ''),
      message: String(frame.payload?.message || '')
    };
  }

  return null;
}

export function createSupabaseRealtimeClient(
  cfg = SUPABASE_WEB_CONFIG,
  {
    getAccessToken = getActiveSupabaseAccessToken,
    onTableChange = () => undefined,
    onStatusChange = () => undefined,
    tables = SUPABASE_SCHEMA_TABLES,
    WebSocketImpl = globalThis.WebSocket
  } = {}
) {
  let ws = null;
  let stopped = true;
  let refSeq = 0;
  let reconnectDelayMs = 1500;
  let heartbeatTimer = null;
  let reconnectTimer = null;
  let usedWildcardFallback = false;
  const tableListeners = new Map();

  const nextRef = () => {
    refSeq += 1;
    return String(refSeq);
  };

  const clearTimers = () => {
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer);
      heartbeatTimer = null;
    }
    if (reconnectTimer) {
      clearTimeout(reconnectTimer);
      reconnectTimer = null;
    }
  };

  const sendFrame = (frame) => {
    if (!ws || ws.readyState !== 1) return false;
    try {
      ws.send(JSON.stringify(frame));
      return true;
    } catch {
      return false;
    }
  };

  const dispatchTableEvent = (parsed) => {
    const tbl = String(parsed?.table || '').trim().toLowerCase();
    const handlers = tableListeners.get(tbl);
    if (handlers && handlers.size > 0) {
      for (const fn of handlers) {
        try {
          fn(parsed);
        } catch (err) {
          console.warn(`[Momi Web] Realtime listener error (${tbl}):`, err);
        }
      }
    }
    onTableChange(parsed);
  };

  const scheduleReconnect = () => {
    if (stopped || reconnectTimer) return;
    const delay = reconnectDelayMs;
    reconnectDelayMs = Math.min(20000, Math.round(reconnectDelayMs * 1.7));
    onStatusChange('reconnecting', `Reconnecting WebSocket in ${Math.round(delay / 1000)}s...`);
    reconnectTimer = setTimeout(() => {
      reconnectTimer = null;
      connectSocket();
    }, delay);
  };

  const connectSocket = () => {
    if (stopped) return;
    const wsUrl = buildSupabaseRealtimeWsUrl(cfg);
    if (!wsUrl || typeof WebSocketImpl !== 'function') {
      onStatusChange('unavailable', 'WebSocket Realtime unavailable in this environment');
      return;
    }
    clearTimers();
    try {
      ws?.close(1000, 'reconnect');
    } catch {
      // Ignore close failure
    }
    onStatusChange('connecting', 'Connecting to Supabase Realtime...');
    try {
      ws = new WebSocketImpl(wsUrl);
    } catch (err) {
      onStatusChange('error', err?.message || 'WebSocket connection failed');
      scheduleReconnect();
      return;
    }

    ws.onopen = () => {
      if (stopped) {
        try {
          ws?.close(1000, 'stopped');
        } catch {
          // Ignore close error
        }
        return;
      }
      reconnectDelayMs = 1500;
      usedWildcardFallback = false;
      const token = String(getAccessToken?.() || '').trim();
      sendFrame(buildSupabaseRealtimeJoinMessage(nextRef(), token, tables));
      heartbeatTimer = setInterval(() => {
        sendFrame({
          topic: 'phoenix',
          event: 'heartbeat',
          payload: {},
          ref: nextRef()
        });
      }, 30000);
    };

    ws.onmessage = (evt) => {
      const parsed = parseSupabaseRealtimeMessage(evt?.data);
      if (!parsed) return;
      if (parsed.kind === 'postgres_changes') {
        dispatchTableEvent(parsed);
      } else if (parsed.kind === 'phx_reply' && parsed.topic === 'realtime:public') {
        if (parsed.status === 'ok') {
          onStatusChange('connected', 'Supabase Realtime Live (Posts, Comments & Likes)');
        } else if (!usedWildcardFallback && Array.isArray(tables) && tables.length > 0) {
          usedWildcardFallback = true;
          const token = String(getAccessToken?.() || '').trim();
          sendFrame(buildSupabaseRealtimeJoinMessage(nextRef(), token, []));
        } else {
          onStatusChange('error', `Realtime channel status: ${parsed.status || 'rejected'}`);
        }
      } else if (parsed.kind === 'system' && parsed.status === 'error') {
        onStatusChange('error', parsed.message || 'Realtime channel error');
      }
    };

    ws.onerror = () => {
      onStatusChange('error', 'Supabase Realtime socket error — falling back briefly');
    };

    ws.onclose = () => {
      clearTimers();
      if (!stopped) {
        scheduleReconnect();
      } else {
        onStatusChange('disconnected', 'Supabase Realtime disconnected');
      }
    };
  };

  const onTable = (tableName, handler) => {
    const key = String(tableName || '').trim().toLowerCase();
    if (!key || typeof handler !== 'function') return () => undefined;
    if (!tableListeners.has(key)) tableListeners.set(key, new Set());
    tableListeners.get(key).add(handler);
    return () => {
      tableListeners.get(key)?.delete(handler);
    };
  };

  return {
    start() {
      if (!stopped) return;
      stopped = false;
      connectSocket();
    },
    stop() {
      stopped = true;
      clearTimers();
      tableListeners.clear();
      try {
        ws?.close(1000, 'client_stop');
      } catch {
        // Ignore close error
      }
      ws = null;
      onStatusChange('disconnected', 'Supabase Realtime stopped');
    },
    onTable,
    registerTableListeners(listenerMap = {}) {
      const unsubs = Object.entries(listenerMap).map(([tbl, fn]) => onTable(tbl, fn));
      return () => {
        unsubs.forEach((unsub) => unsub());
      };
    },
    updateAccessToken(token = '') {
      const clean = String(token || '').trim();
      if (!clean || stopped) return;
      sendFrame({
        topic: 'realtime:public',
        event: 'access_token',
        payload: { access_token: clean },
        ref: nextRef()
      });
    }
  };
}

