import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {
  ANDROID_APK_URL,
  ANDROID_RELEASES_URL,
  APP_VERSION_CODE,
  APP_VERSION_NAME,
  aspectRatioCss,
  buildPostShareUrl,
  buildProfileShareUrl,
  COLOR_SCHEMES,
  compareSemanticVersions,
  DEFAULT_MONETIZATION_CONFIG,
  escapeHtml,
  extractHashtags,
  extractMentions,
  filterActiveStories24h,
  FILTER_OPTIONS,
  filterCssForTag,
  formatCount,
  formatVideoDurationMs,
  initialsForUsername,
  isOwnerEmail,
  isValidEmailAddress,
  isValidPhoneE164,
  MAX_VIDEO_DURATION_MS,
  normalizeColorScheme,
  normalizeEmail,
  normalizePhoneE164,
  normalizeUsername,
  parseDeepLinkFromLocation,
  POST_BOOST_OPTIONS,
  PRESET_IMAGE_OPTIONS,
  renderRichCaptionHtml,
  resolveImageSrc,
  SHOWCASE_COMMENTS,
  SHOWCASE_PERSONAS,
  sortFeedWithBoostPriority,
  STORY_TTL_MS,
  SUPABASE_WEB_CONFIG,
  tierForTotalPurchased,
  verificationTierForPerks,
  WEB_APP_URL,
  WEB_PERK_CATALOG
} from '../app-config.js';
import {
  SUPABASE_SCHEMA_TABLES,
  buildSupabaseAuthHeaders,
  buildSupabaseGoogleOAuthUrl,
  buildSupabaseRealtimeJoinMessage,
  buildSupabaseRealtimeWsUrl,
  checkSupabaseHealth,
  createSupabaseRealtimeClient,
  extractSupabaseJwtExpiryMs,
  isSafeSupabaseAnonKey,
  isSupabaseClientReady,
  isSupabaseJwtExpiringSoon,
  mapMomiSnapshotToSupabaseBundle,
  mapSupabaseAuthUserToWeb,
  mapSupabaseConfigToWeb,
  mapSupabaseGroupToWeb,
  mapSupabasePageToWeb,
  mapSupabasePostToWeb,
  mapSupabaseProductToWeb,
  mapSupabaseProfileToWeb,
  mapSupabaseShowcaseAccountToWeb,
  mapSupabaseStoryToWeb,
  normalizeSupabaseUrl,
  parseSupabaseOAuthCallback,
  parseSupabaseRealtimeMessage,
  SOCIAL_REALTIME_TABLES,
  supabaseResendSignupOtp,
  supabaseSendWhatsAppOtp,
  supabaseSignUpWithEmail,
  supabaseVerifyEmailOtp,
  supabaseVerifyWhatsAppOtp,
  syncBundleToSupabase,
  uploadWebFileResumableTus,
  uploadWebFileToStorage,
  upsertSupabaseTable
} from '../supabase-client.js';

const REPO_ROOT = path.resolve(import.meta.dirname, '..', '..');

test('Momi v2.3 config limits color styles to 3 palettes and enforces 24-hour story TTL', () => {
  assert.equal(APP_VERSION_NAME, '2.3');
  assert.equal(APP_VERSION_CODE, 14);
  assert.equal(STORY_TTL_MS, 86_400_000);

  assert.deepEqual(
    COLOR_SCHEMES.map((s) => s.id),
    ['WHITE_GREY', 'EMERALD_MAROON', 'GOLD_EMERALD']
  );
  assert.equal(normalizeColorScheme('SILK_MAROON'), 'EMERALD_MAROON');
  assert.equal(normalizeColorScheme('EMERALD_GOLD'), 'GOLD_EMERALD');
  assert.equal(normalizeColorScheme('UNKNOWN'), 'EMERALD_MAROON');

  const now = Date.now();
  const active = filterActiveStories24h(
    [
      { id: 'fresh', createdAtMs: now - 3_600_000 },
      { id: 'expired', createdAtMs: now - STORY_TTL_MS - 60_000 },
      { id: 'future_ok', createdAtMs: now - 10_000 }
    ],
    now
  );
  assert.deepEqual(active.map((s) => s.id), ['fresh', 'future_ok']);
});

test('Web shell assets, PWA manifest, and hosting files exist without Firebase leftovers', () => {
  const required = [
    'web/index.html',
    'web/styles.css',
    'web/app.js',
    'web/studio.js',
    'web/app-config.js',
    'web/supabase-client.js',
    'web/manifest.webmanifest',
    'web/sw.js',
    'web/version.json',
    'web/assets/momi-icon.jpg',
    'web/assets/img_post_coastal.jpg',
    'web/assets/img_post_coffee.jpg',
    'web/assets/img_post_architecture.jpg',
    'web/assets/img_post_neon_street.jpg',
    'web/app-ads.txt'
  ];
  for (const rel of required) {
    assert.ok(fs.existsSync(path.join(REPO_ROOT, rel)), `Missing web asset: ${rel}`);
  }
  assert.equal(fs.existsSync(path.join(REPO_ROOT, 'web', 'firebase-config.js')), false);

  const versionManifest = JSON.parse(fs.readFileSync(path.join(REPO_ROOT, 'web', 'version.json'), 'utf8'));
  assert.equal(versionManifest.versionName, APP_VERSION_NAME);
  assert.equal(versionManifest.versionCode, APP_VERSION_CODE);
  const appAdsTxt = fs.readFileSync(path.join(REPO_ROOT, 'web', 'app-ads.txt'), 'utf8');
  assert.ok(appAdsTxt.includes('start.io, 120122068, DIRECT'));
});

test('extractHashtags and extractMentions parse, deduplicate, and normalize tags', () => {
  const caption = 'Sunset walk #Amalfi #Momi #amalfi with @Elena.Studio and @Kenji_Photo!';
  assert.deepEqual(extractHashtags(caption, ['#Travel']), ['travel', 'amalfi', 'momi']);
  assert.deepEqual(extractMentions(caption, ['@aria.studio']), ['aria.studio', 'elena.studio', 'kenji_photo']);
});

test('all 10 filter options and aspect ratios work as expected', () => {
  assert.equal(FILTER_OPTIONS.length, 10);
  assert.equal(PRESET_IMAGE_OPTIONS.length, 4);
  assert.equal(filterCssForTag('NONE'), 'none');
  assert.notEqual(filterCssForTag('MAROON'), 'none');
  assert.ok(filterCssForTag('BLUE', 10, 1.2, 0.1).includes('brightness('));
  assert.equal(aspectRatioCss('1:1'), '1 / 1');
  assert.equal(aspectRatioCss('4:5'), '4 / 5');
  assert.equal(aspectRatioCss('16:9'), '16 / 9');
});

test('rich caption rendering escapes HTML and creates clickable hashtag/mention buttons', () => {
  const html = renderRichCaptionHtml('<script>alert(1)</script> #Momi @Aria.Studio');
  assert.ok(!html.includes('<script>'));
  assert.ok(html.includes('&lt;script&gt;'));
  assert.ok(html.includes('data-hashtag="momi"'));
  assert.ok(html.includes('data-mention="aria.studio"'));
});

test('username, count formatting, and image resolution utilities work accurately', () => {
  assert.equal(initialsForUsername('aria.studio'), 'AS');
  assert.equal(normalizeUsername('Syed.Ali@gmail.com', 'uid123'), 'syed.ali');
  assert.equal(formatCount(1250), '1.3K');
  assert.equal(resolveImageSrc({ imageKey: 'img_post_coffee' }), './assets/img_post_coffee.jpg');
  assert.ok(resolveImageSrc({ imageData: 'QUJD' }).startsWith('data:image/jpeg;base64,QUJD'));
  assert.equal(escapeHtml('"a&b"'), '&quot;a&amp;b&quot;');
});

test('share URL builders, deep-link parser, version comparison, and Android APK links work properly', () => {
  assert.equal(WEB_APP_URL, 'https://moosvi5.github.io/MOMI/');
  assert.equal(ANDROID_APK_URL, 'https://github.com/MOOSVI5/MOMI/releases/latest/download/Momi-latest.apk');
  assert.equal(ANDROID_RELEASES_URL, 'https://github.com/MOOSVI5/MOMI/releases/latest');
  assert.equal(compareSemanticVersions('2.3', '2.2'), 1);
  assert.equal(compareSemanticVersions('2.3.0', '2.3'), 0);
  assert.equal(compareSemanticVersions('2.2', '2.3'), -1);

  const postUrl = buildPostShareUrl('post_17321_abc');
  assert.equal(postUrl, 'https://moosvi5.github.io/MOMI/?post=post_17321_abc');
  assert.deepEqual(parseDeepLinkFromLocation('?post=post_17321_abc', ''), {
    postId: 'post_17321_abc',
    username: null
  });
  assert.deepEqual(parseDeepLinkFromLocation('', '#post-post_legacy_9'), {
    postId: 'post_legacy_9',
    username: null
  });

  const userUrl = buildProfileShareUrl('@Aria.Studio');
  assert.equal(userUrl, 'https://moosvi5.github.io/MOMI/?user=Aria.Studio');
  assert.deepEqual(parseDeepLinkFromLocation('?user=Aria.Studio', ''), {
    postId: null,
    username: 'Aria.Studio'
  });

  const indexHtml = fs.readFileSync(path.join(REPO_ROOT, 'web', 'index.html'), 'utf8');
  assert.ok(indexHtml.includes(ANDROID_APK_URL));
  assert.ok(indexHtml.includes('id="verifyOtpBtn"'));
  assert.ok(indexHtml.includes('id="resendOtpBtn"'));
});

test('starter showcase personas, OTP auth, AI assistant, engagers modal, and Supabase sync are wired', () => {
  const validFilters = new Set(FILTER_OPTIONS.map((f) => f.tag));
  assert.equal(SHOWCASE_PERSONAS.length, 6);
  assert.ok(SHOWCASE_COMMENTS.length >= 4);

  for (const persona of SHOWCASE_PERSONAS) {
    assert.ok(persona.username.length > 2);
    assert.ok(persona.displayName.length > 2);
    assert.ok(validFilters.has(persona.filterTag), `Invalid filter tag ${persona.filterTag}`);
    assert.ok(['1:1', '4:5', '16:9'].includes(persona.aspectRatio));
    assert.ok(persona.hashtags.length > 0);
  }

  const appJs = fs.readFileSync(path.join(REPO_ROOT, 'web', 'app.js'), 'utf8');
  assert.equal(/firebase|monetag|gamezop/i.test(appJs), false);
  assert.ok(appJs.includes('createSupabaseRealtimeClient'));
  assert.ok(appJs.includes('registerDatabaseListenersOnLogin'));
  assert.ok(appJs.includes('applyRealtimePostEvent'));
  assert.ok(appJs.includes('applyRealtimeCommentEvent'));
  assert.ok(appJs.includes('applyRealtimeLikeOrInteractionEvent'));
  assert.ok(appJs.includes('handleSyncError'));
  assert.ok(appJs.includes('removeAllShowcaseContent'));
  assert.ok(appJs.includes('publishShowcaseBurst'));
  assert.ok(appJs.includes('openPostEngagersModal'));
  assert.ok(appJs.includes('openAiAssistantModal'));
  assert.ok(appJs.includes('supabaseVerifyEmailOtp'));
  assert.ok(appJs.includes('supabaseResendSignupOtp'));
  assert.ok(appJs.includes('ensureUserSeededInSupabase'));
  assert.ok(appJs.includes('supabaseSignInWithPassword'));
  assert.ok(appJs.includes('supabaseSignUpWithEmail'));
  assert.ok(appJs.includes('buildSupabaseGoogleOAuthUrl'));
  assert.ok(appJs.includes('parseSupabaseOAuthCallback'));
  assert.ok(appJs.includes('saveSupabaseAuthSession'));
  assert.equal(typeof supabaseVerifyEmailOtp, 'function');
  assert.equal(typeof supabaseResendSignupOtp, 'function');

  const swJs = fs.readFileSync(path.join(REPO_ROOT, 'web', 'sw.js'), 'utf8');
  assert.ok(swJs.includes('momi-web-v9'));
});

test('boost sorting, monetization defaults, and web perks stay clean without Monetag/Gamezop', () => {
  assert.equal(DEFAULT_MONETIZATION_CONFIG.admobEnabled, true);
  assert.equal(DEFAULT_MONETIZATION_CONFIG.startIoEnabled, true);
  assert.equal(DEFAULT_MONETIZATION_CONFIG.latestVersionName, '2.3');
  assert.equal('monetagEnabled' in DEFAULT_MONETIZATION_CONFIG, false);
  assert.equal('gamezopEnabled' in DEFAULT_MONETIZATION_CONFIG, false);

  const sorted = sortFeedWithBoostPriority([
    { id: 'showcase_1', isBoosted: true, boostTier: 'OWNER_RECOMMENDED' },
    { id: 'post_1', isBoosted: false },
    { id: 'post_2', isBoosted: true, boostTier: 'SPOTLIGHT' },
    { id: 'post_3', isBoosted: true, boostTier: 'SUPER_BOOST' },
    { id: 'post_4', isBoosted: true, boostTier: 'OWNER_RECOMMENDED', recommendedLabel: 'Recommended by Momi' }
  ]);
  assert.deepEqual(sorted.map((p) => p.id), ['post_4', 'post_3', 'post_2', 'showcase_1', 'post_1']);

  assert.deepEqual(POST_BOOST_OPTIONS.map((o) => o.creditsCost), [100, 250]);
  assert.deepEqual(WEB_PERK_CATALOG.map((p) => p.cost), [100, 250, 500, 1000]);
  assert.equal(tierForTotalPurchased(0, ['VIP_PATRON_PASS']), 'VIP');
  assert.equal(verificationTierForPerks('NONE', ['PRIORITY_VERIFICATION']), 'PENDING_PERK');
});

test('Supabase migration schema SQL, URL safety, and snapshot bundle mapping match 1:1', () => {
  const schemaPath = path.join(REPO_ROOT, 'supabase', 'schema.sql');
  assert.ok(fs.existsSync(schemaPath), 'Missing supabase/schema.sql');
  const schemaSql = fs.readFileSync(schemaPath, 'utf8');
  for (const tbl of SUPABASE_SCHEMA_TABLES) {
    assert.ok(schemaSql.includes(`public.${tbl}`), `Missing table public.${tbl} in supabase/schema.sql`);
  }

  assert.equal(normalizeSupabaseUrl(' https://DEMO.supabase.co/ '), 'https://demo.supabase.co');
  assert.equal(normalizeSupabaseUrl('http://demo.supabase.co'), '');
  assert.equal(normalizeSupabaseUrl('https://localhost:54321'), '');
  assert.equal(isSafeSupabaseAnonKey('sb_secret_not_allowed'), false);
  assert.equal(isSafeSupabaseAnonKey('sb_publishable_ok_123456'), true);
  assert.equal(isSupabaseClientReady({ url: 'https://demo.supabase.co', anonKey: 'sb_publishable_ok_123456' }), true);

  const bundle = mapMomiSnapshotToSupabaseBundle({
    profile: { userId: 'u1', email: 'ali@momi.app', username: 'ali', displayName: 'Ali', partnerConsent: true },
    creators: [],
    posts: [
      { id: 'p1', userId: 'u1', authorUsername: 'ali', authorDisplayName: 'Ali', isBoosted: true, boostTier: 'OWNER_RECOMMENDED' },
      { id: 'showcase_1', userId: 'u1', authorUsername: 'elena', authorDisplayName: 'Elena' }
    ],
    stories: [{ id: 's1', userId: 'u1', username: 'ali', caption: 'Hi' }],
    monetizationConfig: { ...DEFAULT_MONETIZATION_CONFIG, latestVersionName: '2.3' },
    wallet: { userId: 'u1', balance: 250, accountTier: 'CREATOR_PRO', unlockedPerks: ['CREATOR_BOOST'] }
  });

  assert.equal(bundle.schemaVersion, 'momi_supabase_v1');
  assert.equal(bundle.tables.profiles.length, 1);
  assert.equal(bundle.tables.profiles[0].email, 'ali@momi.app');
  assert.equal(bundle.tables.posts.length, 1);
  assert.equal(bundle.tables.posts[0].boost_tier, 'OWNER_RECOMMENDED');
  assert.equal(bundle.tables.app_config[0].latest_version_name, '2.3');
  assert.equal(bundle.tables.wallets[0].balance, 250);
});

test('Supabase web auth headers omit Authorization Bearer for sb_publishable_ keys unless user JWT exists', async () => {
  const pubHeaders = buildSupabaseAuthHeaders('sb_publishable_LbrQBb_example_123456');
  assert.equal(pubHeaders.apikey, 'sb_publishable_LbrQBb_example_123456');
  assert.equal('Authorization' in pubHeaders, false);

  const pubUserHeaders = buildSupabaseAuthHeaders(
    'sb_publishable_LbrQBb_example_123456',
    'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.user_payload.sig'
  );
  assert.equal(
    pubUserHeaders.Authorization,
    'Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.user_payload.sig'
  );

  const legacyHeaders = buildSupabaseAuthHeaders('eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.anon_payload.sig');
  assert.equal(
    legacyHeaders.Authorization,
    'Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.anon_payload.sig'
  );

  let seenHeaders = null;
  const health = await checkSupabaseHealth(
    { url: 'https://qotuvugkapaspowhbsbf.supabase.co', anonKey: 'sb_publishable_LbrQBb_example_123456' },
    async (_url, init) => {
      seenHeaders = init.headers;
      return { ok: true, status: 200 };
    }
  );
  assert.equal(health.connected, true);
  assert.equal(seenHeaders.apikey, 'sb_publishable_LbrQBb_example_123456');
  assert.equal('Authorization' in seenHeaders, false);
  assert.equal(isSupabaseClientReady(SUPABASE_WEB_CONFIG), true);
  assert.equal(normalizeSupabaseUrl(SUPABASE_WEB_CONFIG.url), 'https://qotuvugkapaspowhbsbf.supabase.co');
});

test('syncBundleToSupabase deduplicates usernames, orders profiles before child tables, and mappers hydrate cleanly', async () => {
  const enableSyncSql = fs.readFileSync(path.join(REPO_ROOT, 'supabase', 'enable_sync.sql'), 'utf8');
  assert.equal(enableSyncSql.includes('CREATE POLICY IF NOT EXISTS'), false);
  assert.ok(enableSyncSql.includes('DROP POLICY IF EXISTS'));
  assert.ok(enableSyncSql.includes('idx_profiles_email_lower'));

  const bundle = mapMomiSnapshotToSupabaseBundle({
    creators: [
      { userId: 'u1', email: 'ali@momi.app', username: 'ali.studio', displayName: 'Ali' },
      { userId: 'u2', email: 'dup@momi.app', username: 'ALI.STUDIO', displayName: 'Duplicate Username' }
    ],
    posts: [
      { id: 'p1', userId: 'u1', authorUsername: 'ali.studio', authorDisplayName: 'Ali', caption: 'Hi' },
      { id: 'p_orphan', userId: 'u_missing', authorUsername: 'ghost', authorDisplayName: 'Ghost', caption: 'Skip' }
    ],
    stories: [{ id: 's1', userId: 'u1', username: 'ali.studio', caption: 'Story' }],
    monetizationConfig: { ...DEFAULT_MONETIZATION_CONFIG, latestVersionName: '2.3' }
  });

  const calls = [];
  const total = await syncBundleToSupabase(
    { url: 'https://qotuvugkapaspowhbsbf.supabase.co', anonKey: 'sb_publishable_LbrQBb_example_123456' },
    bundle,
    '',
    async (url, init) => {
      calls.push({ url, body: JSON.parse(init.body) });
      return { ok: true, status: 201 };
    }
  );

  assert.equal(total, 4);
  assert.ok(calls[0].url.includes('/rest/v1/profiles?on_conflict=user_id'));
  assert.equal(calls[0].body.length, 1);
  assert.ok(calls[1].url.includes('/rest/v1/posts?on_conflict=id'));
  assert.equal(calls[1].body.length, 1);

  assert.equal(mapSupabasePostToWeb(bundle.tables.posts[0]).caption, 'Hi');
  assert.equal(mapSupabaseStoryToWeb(bundle.tables.stories[0]).caption, 'Story');
  assert.equal(mapSupabaseProfileToWeb(bundle.tables.profiles[0]).username, 'ali.studio');
  assert.equal(mapSupabaseProfileToWeb(bundle.tables.profiles[0]).email, 'ali@momi.app');
  assert.equal(mapSupabaseConfigToWeb(bundle.tables.app_config[0]).latestVersionName, '2.3');
  assert.equal(mapSupabaseProductToWeb({ id: 'pr1', seller_uid: 'u1', price_usd: 25 }).priceUsd, 25);
});

test('Supabase Auth helpers build Google OAuth URLs, parse callbacks, and map user metadata', () => {
  const oauthUrl = buildSupabaseGoogleOAuthUrl(
    { url: 'https://qotuvugkapaspowhbsbf.supabase.co' },
    'https://moosvi5.github.io/MOMI/'
  );
  assert.ok(oauthUrl.startsWith('https://qotuvugkapaspowhbsbf.supabase.co/auth/v1/authorize?'));
  assert.ok(oauthUrl.includes('provider=google'));
  assert.ok(oauthUrl.includes('redirect_to=https%3A%2F%2Fmoosvi5.github.io%2FMOMI%2F'));

  const parsedToken = parseSupabaseOAuthCallback('#access_token=jwt_abc&refresh_token=ref_123&expires_in=3600', '');
  assert.equal(parsedToken.accessToken, 'jwt_abc');
  assert.equal(parsedToken.refreshToken, 'ref_123');
  assert.equal(parsedToken.expiresIn, 3600);
  assert.equal(parsedToken.errorDescription, '');

  const parsedError = parseSupabaseOAuthCallback('', '?error=access_denied&error_description=User%20cancelled');
  assert.equal(parsedError.errorDescription, 'User cancelled');

  const mappedUser = mapSupabaseAuthUserToWeb({
    id: 'supa-uuid-1',
    email: 'syali6511@gmail.com',
    email_confirmed_at: '2026-01-01T00:00:00Z',
    user_metadata: { full_name: 'Syed Ali', avatar_url: 'https://example.com/a.jpg' }
  });
  assert.equal(mappedUser.uid, 'supa-uuid-1');
  assert.equal(mappedUser.email, 'syali6511@gmail.com');
  assert.equal(mappedUser.displayName, 'Syed Ali');
  assert.equal(mappedUser.photoURL, 'https://example.com/a.jpg');
  assert.equal(mappedUser.emailVerified, true);
});

test('Owner admin email helper, Pages & Groups, Showcase Starters, Announcements, and zero forbidden terms work', () => {
  assert.equal(isOwnerEmail('syali6511@gmail.com'), true);
  assert.equal(isOwnerEmail('  SYALI6511@GMAIL.COM  '), true);
  assert.equal(isOwnerEmail('other@example.com'), false);
  assert.equal(isOwnerEmail(''), false);

  const enableSyncSql = fs.readFileSync(path.join(REPO_ROOT, 'supabase', 'enable_sync.sql'), 'utf8');
  for (const tbl of ['pages', 'page_follows', 'groups', 'group_members', 'showcase_accounts', 'user_interactions']) {
    assert.ok(SUPABASE_SCHEMA_TABLES.includes(tbl), `Missing ${tbl} in SUPABASE_SCHEMA_TABLES`);
    assert.ok(enableSyncSql.includes(`public.${tbl}`), `Missing public.${tbl} in enable_sync.sql`);
  }

  const page = mapSupabasePageToWeb({ id: 'pg1', owner_uid: 'u1', name: 'Momi Photos', followers_count: 42 });
  assert.equal(page.name, 'Momi Photos');
  assert.equal(page.followersCount, 42);

  const group = mapSupabaseGroupToWeb({ id: 'gr1', owner_uid: 'u1', name: 'Street Club', members_count: 19 });
  assert.equal(group.name, 'Street Club');
  assert.equal(group.membersCount, 19);

  const starter = mapSupabaseShowcaseAccountToWeb({ username: 'elena.maris', is_authorized: true });
  assert.equal(starter.username, 'elena.maris');
  assert.equal(starter.isAuthorized, true);

  const cfg = mapSupabaseConfigToWeb({
    announcement_id: 'ann_1',
    announcement_title: 'In-App Update',
    announcement_body: 'Live now!',
    latest_version_name: '2.3',
    release_notes: 'Pages & Groups added'
  });
  assert.equal(cfg.announcementId, 'ann_1');
  assert.equal(cfg.latestVersionName, '2.3');

  const webFiles = ['index.html', 'styles.css', 'app.js', 'studio.js', 'manifest.webmanifest', 'app-config.js', 'supabase-client.js'];
  for (const rel of webFiles) {
    const content = fs.readFileSync(path.join(REPO_ROOT, 'web', rel), 'utf8');
    assert.equal(
      /\b(instagram|facebook|rednote|tiktok|monetag|gamezop|firebase|firestore)\b/i.test(content),
      false,
      `Forbidden mention found in web/${rel}`
    );
  }
});

test('Supabase Realtime CDC WebSocket helpers and SQL publication cover all public tables', () => {
  const wsUrl = buildSupabaseRealtimeWsUrl({
    url: 'https://qotuvugkapaspowhbsbf.supabase.co',
    anonKey: 'sb_publishable_LbrQBbU_4qH18qjopmNwIg_ALsMhlln'
  });
  assert.equal(
    wsUrl,
    'wss://qotuvugkapaspowhbsbf.supabase.co/realtime/v1/websocket?apikey=sb_publishable_LbrQBbU_4qH18qjopmNwIg_ALsMhlln&vsn=1.0.0'
  );

  const joinMsg = buildSupabaseRealtimeJoinMessage('7', 'user_jwt_123');
  assert.equal(joinMsg.topic, 'realtime:public');
  assert.equal(joinMsg.event, 'phx_join');
  assert.equal(joinMsg.ref, '7');
  assert.equal(joinMsg.payload.access_token, 'user_jwt_123');
  const joinedTables = joinMsg.payload.config.postgres_changes.map((entry) => entry.table);
  assert.ok(joinedTables.includes('posts'));
  assert.ok(joinedTables.includes('comments'));
  assert.ok(joinedTables.includes('user_interactions'));

  const wildcardJoin = buildSupabaseRealtimeJoinMessage('8', '', []);
  assert.deepEqual(wildcardJoin.payload.config.postgres_changes, [{ event: '*', schema: 'public' }]);
  assert.deepEqual(SOCIAL_REALTIME_TABLES, ['posts', 'comments', 'user_interactions', 'likes']);

  const parsedChange = parseSupabaseRealtimeMessage(JSON.stringify({
    topic: 'realtime:public',
    event: 'postgres_changes',
    payload: {
      data: {
        schema: 'public',
        table: 'posts',
        type: 'INSERT',
        record: { id: 'post_99' }
      }
    }
  }));
  assert.equal(parsedChange.kind, 'postgres_changes');
  assert.equal(parsedChange.table, 'posts');
  assert.equal(parsedChange.eventType, 'INSERT');
  assert.equal(parsedChange.record.id, 'post_99');

  const parsedReply = parseSupabaseRealtimeMessage(JSON.stringify({
    topic: 'realtime:public',
    event: 'phx_reply',
    payload: { status: 'ok' },
    ref: '7'
  }));
  assert.equal(parsedReply.kind, 'phx_reply');
  assert.equal(parsedReply.status, 'ok');

  let mockSocketInstance = null;
  class MockWebSocket {
    constructor(url) {
      this.url = url;
      this.readyState = 1;
      this.sent = [];
      mockSocketInstance = this;
    }
    send(data) {
      this.sent.push(JSON.parse(data));
    }
    close() {
      this.readyState = 3;
    }
  }
  const seenEvents = [];
  const rtClient = createSupabaseRealtimeClient(
    {
      url: 'https://qotuvugkapaspowhbsbf.supabase.co',
      anonKey: 'sb_publishable_LbrQBbU_4qH18qjopmNwIg_ALsMhlln'
    },
    {
      getAccessToken: () => 'jwt_login_1',
      WebSocketImpl: MockWebSocket
    }
  );
  rtClient.registerTableListeners({
    posts: (e) => seenEvents.push(`posts:${e.eventType}`),
    comments: (e) => seenEvents.push(`comments:${e.eventType}`),
    user_interactions: (e) => seenEvents.push(`user_interactions:${e.eventType}`),
    likes: (e) => seenEvents.push(`likes:${e.eventType}`)
  });
  rtClient.start();
  mockSocketInstance.onopen();
  for (const [table, eventType] of [['posts', 'INSERT'], ['comments', 'INSERT'], ['user_interactions', 'DELETE'], ['likes', 'INSERT']]) {
    mockSocketInstance.onmessage({
      data: JSON.stringify({
        topic: 'realtime:public',
        event: 'postgres_changes',
        payload: { data: { schema: 'public', table, type: eventType, record: { id: 'x1' }, old_record: { id: 'x1' } } }
      })
    });
  }
  assert.deepEqual(seenEvents, ['posts:INSERT', 'comments:INSERT', 'user_interactions:DELETE', 'likes:INSERT']);
  rtClient.stop();

  for (const sqlRel of ['supabase/enable_sync.sql', 'supabase/schema.sql']) {
    const sqlText = fs.readFileSync(path.join(REPO_ROOT, sqlRel), 'utf8');
    assert.ok(sqlText.includes('supabase_realtime'), `Missing supabase_realtime publication in ${sqlRel}`);
    assert.ok(sqlText.includes('REPLICA IDENTITY FULL'), `Missing REPLICA IDENTITY FULL in ${sqlRel}`);
    for (const tbl of SUPABASE_SCHEMA_TABLES) {
      assert.ok(sqlText.includes(`'${tbl}'`), `Missing Realtime table entry '${tbl}' in ${sqlRel}`);
    }
  }
});

test('JWT expiry detection flags expired PGRST303 tokens and preserves fresh tokens', () => {
  const toB64Url = (obj) => Buffer.from(JSON.stringify(obj), 'utf8')
    .toString('base64')
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
  const pastSec = 1700000000;
  const futureSec = Math.floor(Date.now() / 1000) + 3600;
  const expiredJwt = `${toB64Url({ alg: 'HS256' })}.${toB64Url({ sub: 'u1', exp: pastSec })}.sig`;
  const freshJwt = `${toB64Url({ alg: 'HS256' })}.${toB64Url({ sub: 'u1', exp: futureSec })}.sig`;

  assert.equal(extractSupabaseJwtExpiryMs(expiredJwt), pastSec * 1000);
  assert.equal(isSupabaseJwtExpiringSoon(expiredJwt), true);
  assert.equal(isSupabaseJwtExpiringSoon(freshJwt), false);
  assert.equal(isSupabaseJwtExpiringSoon(''), false);
});

test('Publish Story button defines smooth CSS transitions and Supabase upload feedback states', () => {
  const css = fs.readFileSync(path.join(REPO_ROOT, 'web', 'styles.css'), 'utf8');
  const appJs = fs.readFileSync(path.join(REPO_ROOT, 'web', 'app.js'), 'utf8');

  assert.ok(css.includes('.btn-publish-story'), 'Missing .btn-publish-story CSS class');
  assert.ok(css.includes('.btn-publish-story.is-publishing'), 'Missing .btn-publish-story.is-publishing CSS state');
  assert.ok(css.includes('.btn-publish-story.is-saved'), 'Missing .btn-publish-story.is-saved CSS state');
  assert.ok(css.includes('.publish-status-track.is-visible'), 'Missing .publish-status-track.is-visible CSS state');
  assert.ok(css.includes('transition:'), 'Missing CSS transition definitions');
  assert.ok(css.includes('@keyframes momiUploadProgress'), 'Missing upload progress keyframe animation');

  assert.ok(appJs.includes('btn-publish-story'), 'web/app.js must apply btn-publish-story class');
  assert.ok(appJs.includes('Publish Story'), 'web/app.js must label the story action Publish Story');
  assert.ok(appJs.includes('Uploading & saving story...'), 'web/app.js must show story upload feedback');
  assert.ok(appJs.includes('Saved to Supabase!'), 'web/app.js must show saved confirmation transition');
  assert.ok(appJs.includes("publishBtn.classList.add('is-publishing')"), 'web/app.js must transition button in place');
});

test('WhatsApp OTP auth, single-email-one-account enforcement, PGRST204 retry, and 80m video + thumbnail support work', async () => {
  assert.equal(MAX_VIDEO_DURATION_MS, 4_800_000);
  assert.equal(formatVideoDurationMs(4_800_000), '1:20:00');
  assert.equal(formatVideoDurationMs(65_000), '1:05');

  assert.equal(normalizePhoneE164('+92 300-1234567'), '+923001234567');
  assert.equal(normalizePhoneE164('00923001234567'), '+923001234567');
  assert.equal(normalizePhoneE164('123'), '');
  assert.equal(isValidPhoneE164('+923001234567'), true);
  assert.equal(isValidPhoneE164('bad'), false);
  assert.equal(normalizeEmail('  User@Example.COM '), 'user@example.com');
  assert.equal(isValidEmailAddress('user@example.com'), true);

  const schemaSql = fs.readFileSync(path.join(REPO_ROOT, 'supabase', 'schema.sql'), 'utf8');
  const enableSyncSql = fs.readFileSync(path.join(REPO_ROOT, 'supabase', 'enable_sync.sql'), 'utf8');
  for (const sqlText of [schemaSql, enableSyncSql]) {
    assert.ok(sqlText.includes('idx_profiles_phone_e164'));
    assert.ok(sqlText.includes('4800000'));
    assert.ok(sqlText.includes('524288000'));
  }

  const cfg = { url: 'https://qotuvugkapaspowhbsbf.supabase.co', anonKey: 'sb_publishable_LbrQBbU_4qH18qjopmNwIg_ALsMhlln' };

  // WhatsApp OTP send & verify
  const sentRequests = [];
  const otpResult = await supabaseSendWhatsAppOtp(
    cfg,
    '+92 300 1234567',
    { displayName: 'Ali', email: '' },
    async (url, init) => {
      sentRequests.push({ url, body: JSON.parse(init.body) });
      return { ok: true, status: 200, json: async () => ({}) };
    }
  );
  assert.equal(otpResult.phone, '+923001234567');
  assert.ok(sentRequests[0].url.endsWith('/auth/v1/otp'));
  assert.equal(sentRequests[0].body.channel, 'whatsapp');
  assert.equal(sentRequests[0].body.phone, '+923001234567');

  const verifyResult = await supabaseVerifyWhatsAppOtp(
    cfg,
    '+923001234567',
    '123456',
    { displayName: 'Ali', email: 'ali@momi.app' },
    async (url, init) => {
      if (url.includes('/rest/v1/profiles')) {
        return { ok: true, status: 200, json: async () => [] };
      }
      const parsed = JSON.parse(init.body);
      assert.equal(parsed.type, 'sms');
      assert.equal(parsed.phone, '+923001234567');
      assert.equal(parsed.token, '123456');
      return {
        ok: true,
        status: 200,
        json: async () => ({
          access_token: 'jwt_wa_1',
          refresh_token: 'ref_wa_1',
          expires_in: 3600,
          user: { id: 'wa_uid_1', phone: '+923001234567', phone_confirmed_at: '2026-01-01T00:00:00Z' }
        })
      };
    }
  );
  assert.equal(verifyResult.session.user.uid, 'wa_uid_1');
  assert.equal(verifyResult.session.user.phone, '+923001234567');
  assert.equal(verifyResult.session.user.email, 'ali@momi.app');

  // Single-email-single-account guard on signup
  await assert.rejects(
    () => supabaseSignUpWithEmail(cfg, 'taken@momi.app', 'secret123', 'Ali', async (url) => {
      if (url.includes('/rest/v1/profiles')) {
        return { ok: true, status: 200, json: async () => [{ user_id: 'u_existing', email: 'taken@momi.app' }] };
      }
      return { ok: true, status: 200, json: async () => ({}) };
    }),
    /One email can only have one Momi account/i
  );

  // PGRST204 retry strips missing column
  const upsertBodies = [];
  await upsertSupabaseTable(
    cfg,
    'profiles',
    [{ user_id: 'u1', username: 'ali', phone: '+923001234567' }],
    '',
    async (_url, init) => {
      upsertBodies.push(JSON.parse(init.body));
      if (upsertBodies.length === 1) {
        return {
          ok: false,
          status: 400,
          text: async () => '{"code":"PGRST204","message":"Could not find the \'phone\' column of \'profiles\' in the schema cache"}'
        };
      }
      return { ok: true, status: 201, text: async () => '' };
    },
    'user_id'
  );
  assert.equal(upsertBodies.length, 2);
  assert.equal('phone' in upsertBodies[1][0], false);

  // TUS resumable chunked upload for large 80m video blobs
  const tusCalls = [];
  const largeFakeFile = {
    size: 50 * 1024 * 1024,
    type: 'video/mp4',
    slice: (start, end) => new Uint8Array(end - start)
  };
  const uploadedUrl = await uploadWebFileToStorage(
    cfg,
    'momi-videos',
    'videos/u1/long_80m.mp4',
    largeFakeFile,
    'video/mp4',
    async (url, init) => {
      tusCalls.push({ url, method: init.method, headers: init.headers });
      if (init.method === 'POST') {
        return {
          ok: true,
          status: 201,
          headers: { get: (k) => (k.toLowerCase() === 'location' ? '/storage/v1/upload/resumable/tok_1' : null) }
        };
      }
      const off = Number(init.headers['Upload-Offset'] || 0);
      const len = init.body?.byteLength || 0;
      return {
        ok: true,
        status: 204,
        headers: { get: (k) => (k.toLowerCase() === 'upload-offset' ? String(off + len) : null) }
      };
    }
  );
  assert.equal(uploadedUrl, 'https://qotuvugkapaspowhbsbf.supabase.co/storage/v1/object/public/momi-videos/videos/u1/long_80m.mp4');
  assert.equal(tusCalls[0].method, 'POST');
  assert.ok(tusCalls[0].url.endsWith('/storage/v1/upload/resumable'));
  assert.equal(tusCalls.filter((c) => c.method === 'PATCH').length, 10);
  assert.equal(typeof uploadWebFileResumableTus, 'function');

  const indexHtml = fs.readFileSync(path.join(REPO_ROOT, 'web', 'index.html'), 'utf8');
  const appJs = fs.readFileSync(path.join(REPO_ROOT, 'web', 'app.js'), 'utf8');
  assert.ok(indexHtml.includes('id="toggleWhatsAppAuthBtn"'));
  assert.ok(indexHtml.includes('id="waOtpVerifyBox"'));
  assert.ok(appJs.includes('studioThumbInput'));
  assert.ok(appJs.includes('80:00'));
});


