import { DEFAULT_MONETIZATION_CONFIG, SUPABASE_WEB_CONFIG } from './firebase-config.js';

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
  'app_config'
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

export function mapFirestoreSnapshotToSupabaseBundle({
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
    username: String(p.username || 'momi.creator').slice(0, 50),
    display_name: String(p.displayName || 'Momi Creator').slice(0, 80),
    category: String(p.category || '').slice(0, 80),
    bio: String(p.bio || '').slice(0, 300),
    website: String(p.website || '').slice(0, 200),
    avatar_key: String(p.avatarKey || 'avatar_aria').slice(0, 64),
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
    image_data: p.imageData || null,
    extra_image_datas: Array.isArray(p.extraImageDatas) ? p.extraImageDatas : [],
    media_type: String(p.mediaType || 'IMAGE'),
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
    audio_data: p.audioData || null,
    is_boosted: Boolean(p.isBoosted),
    boost_tier: String(p.boostTier || ''),
    recommended_label: String(p.recommendedLabel || '').slice(0, 40),
    boosted_by_uid: p.boostedByUid || null,
    boosted_at: p.boostedAt ? toIsoTimestamp(p.boostedAt) : null,
    created_at: toIsoTimestamp(p.createdAt)
  }));

  const storyRows = realStories.map((s) => ({
    id: String(s.id || ''),
    user_id: String(s.userId || ''),
    username: String(s.username || 'momi.creator').slice(0, 50),
    avatar_key: String(s.avatarKey || 'avatar_aria').slice(0, 64),
    image_key: String(s.imageKey || 'img_post_coastal').slice(0, 64),
    image_data: s.imageData || null,
    media_type: String(s.mediaType || 'IMAGE'),
    video_data: s.videoData || null,
    video_duration_ms: Number(s.videoDurationMs || 0),
    audio_title: String(s.audioTitle || '').slice(0, 120),
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
    image_data: p.imageData || null,
    extra_image_datas: Array.isArray(p.extraImageDatas) ? p.extraImageDatas : [],
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
    monetag_enabled: Boolean(cfg.monetagEnabled),
    monetag_direct_link_url: String(cfg.monetagDirectLinkUrl || '').slice(0, 300),
    monetag_web_zone_id: String(cfg.monetagWebZoneId || '').slice(0, 40),
    monetag_web_meta_tag: String(cfg.monetagWebMetaTag || '').slice(0, 120),
    partner_promo_title: String(cfg.partnerPromoTitle || '').slice(0, 80),
    partner_promo_subtitle: String(cfg.partnerPromoSubtitle || '').slice(0, 180),
    partner_promo_cta: String(cfg.partnerPromoCta || '').slice(0, 40),
    gamezop_enabled: Boolean(cfg.gamezopEnabled),
    gamezop_portal_url: String(cfg.gamezopPortalUrl || '').slice(0, 300),
    gamezop_publisher_id: String(cfg.gamezopPublisherId || '').slice(0, 40),
    gamezop_banner_title: String(cfg.gamezopBannerTitle || '').slice(0, 80),
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
        message: `Connected to Supabase (${latencyMs} ms) — schema & RLS verified.`
      };
    }
    let hint = `Supabase responded HTTP ${res.status}.`;
    if (res.status === 404) {
      hint = 'Supabase reachable, but app_config table missing — run supabase/schema.sql in SQL Editor.';
    } else if (res.status === 401 || res.status === 403) {
      hint = 'Supabase rejected key or RLS policy — check publishable key & run supabase/schema.sql.';
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
  const res = await fetchImpl(`${baseUrl}/rest/v1/${table}${conflictQuery}`, {
    method: 'POST',
    headers: {
      ...buildSupabaseAuthHeaders(anonKey, accessToken),
      'Content-Type': 'application/json',
      Prefer: 'resolution=merge-duplicates,return=minimal'
    },
    body: JSON.stringify(rows)
  });
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
    const [postsRes, storiesRes, profilesRes, productsRes, configRes] = await Promise.all([
      fetchImpl(`${baseUrl}/rest/v1/posts?select=*&order=created_at.desc&limit=50`, { headers }),
      fetchImpl(`${baseUrl}/rest/v1/stories?select=*&order=created_at.desc&limit=20`, { headers }),
      fetchImpl(`${baseUrl}/rest/v1/profiles?select=*&limit=50`, { headers }),
      fetchImpl(`${baseUrl}/rest/v1/market_products?select=*&order=created_at.desc&limit=30`, { headers }),
      fetchImpl(`${baseUrl}/rest/v1/app_config?select=*&id=eq.monetization&limit=1`, { headers })
    ]);
    if (!postsRes.ok && !profilesRes.ok) return null;
    const posts = postsRes.ok ? await postsRes.json() : [];
    const stories = storiesRes.ok ? await storiesRes.json() : [];
    const profiles = profilesRes.ok ? await profilesRes.json() : [];
    const products = productsRes.ok ? await productsRes.json() : [];
    const configs = configRes.ok ? await configRes.json() : [];
    return { posts, stories, profiles, products, appConfig: configs[0] || null };
  } catch {
    return null;
  }
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
    imageData: row.image_data || null,
    extraImageDatas: Array.isArray(row.extra_image_datas) ? row.extra_image_datas : [],
    mediaType: String(row.media_type || 'IMAGE'),
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
    audioData: row.audio_data || null,
    isBoosted: Boolean(row.is_boosted),
    boostTier: String(row.boost_tier || ''),
    recommendedLabel: String(row.recommended_label || ''),
    isSponsored: Boolean(row.is_sponsored),
    sponsorLabel: String(row.sponsor_label || ''),
    directLinkUrl: String(row.direct_link_url || ''),
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
    imageData: row.image_data || null,
    mediaType: String(row.media_type || 'IMAGE'),
    videoData: row.video_data || null,
    videoDurationMs: Number(row.video_duration_ms || 0),
    audioTitle: String(row.audio_title || ''),
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
    imageData: row.image_data || null,
    extraImageDatas: Array.isArray(row.extra_image_datas) ? row.extra_image_datas : [],
    videoData: row.video_data || null,
    videoDurationMs: Number(row.video_duration_ms || 0),
    stockCount: Number(row.stock_count || 0),
    isFeatured: Boolean(row.is_featured),
    createdAt: row.created_at ? { seconds: Math.floor(new Date(row.created_at).getTime() / 1000) } : null
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
    monetagEnabled: Boolean(row.monetag_enabled ?? DEFAULT_MONETIZATION_CONFIG.monetagEnabled),
    monetagDirectLinkUrl: String(row.monetag_direct_link_url || ''),
    monetagWebZoneId: String(row.monetag_web_zone_id || ''),
    monetagWebMetaTag: String(row.monetag_web_meta_tag || ''),
    partnerPromoTitle: String(row.partner_promo_title || DEFAULT_MONETIZATION_CONFIG.partnerPromoTitle),
    partnerPromoSubtitle: String(row.partner_promo_subtitle || DEFAULT_MONETIZATION_CONFIG.partnerPromoSubtitle),
    partnerPromoCta: String(row.partner_promo_cta || DEFAULT_MONETIZATION_CONFIG.partnerPromoCta),
    gamezopEnabled: Boolean(row.gamezop_enabled ?? false),
    gamezopPortalUrl: String(row.gamezop_portal_url || ''),
    gamezopPublisherId: String(row.gamezop_publisher_id || ''),
    gamezopBannerTitle: String(row.gamezop_banner_title || DEFAULT_MONETIZATION_CONFIG.gamezopBannerTitle)
  };
}

export function mapSupabaseProfileToWeb(row = {}) {
  return {
    userId: String(row.user_id || ''),
    username: String(row.username || 'momi.creator'),
    displayName: String(row.display_name || row.username || 'Creator'),
    category: String(row.category || 'Creator'),
    bio: String(row.bio || ''),
    website: String(row.website || ''),
    avatarKey: String(row.avatar_key || 'avatar_aria'),
    avatarData: row.avatar_data || null,
    followersCount: Number(row.followers_count || 0),
    followingCount: Number(row.following_count || 0),
    isVerified: Boolean(row.is_verified),
    role: String(row.role || 'USER'),
    moderationScopes: Array.isArray(row.moderation_scopes) ? row.moderation_scopes : [],
    partnerConsent: Boolean(row.partner_consent)
  };
}

