import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {
  ANDROID_APK_URL,
  ANDROID_RELEASES_URL,
  aspectRatioCss,
  buildPostShareUrl,
  buildProfileShareUrl,
  escapeHtml,
  extractHashtags,
  extractMentions,
  FILTER_OPTIONS,
  filterCssForTag,
  FIREBASE_WEB_CONFIG,
  FIRESTORE_DATABASE_ID,
  formatCount,
  initialsForUsername,
  normalizeMonetagDirectUrl,
  normalizeUsername,
  parseDeepLinkFromLocation,
  POST_BOOST_OPTIONS,
  PRESET_IMAGE_OPTIONS,
  renderRichCaptionHtml,
  resolveGamezopUrl,
  resolveImageSrc,
  sanitizeGamezopPublisherId,
  sanitizeMonetagZoneId,
  SHOWCASE_COMMENTS,
  SHOWCASE_PERSONAS,
  sortFeedWithBoostPriority,
  tierForTotalPurchased,
  verificationTierForPerks,
  WEB_APP_URL,
  WEB_PERK_CATALOG,
  DEFAULT_MONETIZATION_CONFIG
} from '../firebase-config.js';
import {
  SUPABASE_SCHEMA_TABLES,
  buildSupabaseAuthHeaders,
  checkSupabaseHealth,
  isSafeSupabaseAnonKey,
  isSupabaseClientReady,
  mapFirestoreSnapshotToSupabaseBundle,
  normalizeSupabaseUrl
} from '../supabase-client.js';

const REPO_ROOT = path.resolve(import.meta.dirname, '..', '..');

test('Firebase web config matches google-services.json and firebase_applet_config.xml', () => {
  const googleServices = JSON.parse(
    fs.readFileSync(path.join(REPO_ROOT, 'app', 'google-services.json'), 'utf8')
  );
  const appletConfigXml = fs.readFileSync(
    path.join(REPO_ROOT, 'app', 'src', 'main', 'res', 'values', 'firebase_applet_config.xml'),
    'utf8'
  );

  assert.equal(FIREBASE_WEB_CONFIG.projectId, googleServices.project_info.project_id);
  assert.equal(FIREBASE_WEB_CONFIG.messagingSenderId, googleServices.project_info.project_number);
  assert.equal(FIREBASE_WEB_CONFIG.storageBucket, googleServices.project_info.storage_bucket);
  assert.ok(appletConfigXml.includes(`>${FIRESTORE_DATABASE_ID}<`));
});

test('Web shell assets, PWA manifest, and hosting files exist', () => {
  const required = [
    'web/index.html',
    'web/styles.css',
    'web/app.js',
    'web/studio.js',
    'web/firebase-config.js',
    'web/manifest.webmanifest',
    'web/sw.js',
    'web/assets/momi-icon.jpg',
    'web/assets/img_post_coastal.jpg',
    'web/assets/img_post_coffee.jpg',
    'web/assets/img_post_architecture.jpg',
    'web/assets/img_post_neon_street.jpg'
  ];
  for (const rel of required) {
    assert.ok(fs.existsSync(path.join(REPO_ROOT, rel)), `Missing web asset: ${rel}`);
  }
});

test('extractHashtags and extractMentions parse, deduplicate, and normalize tags', () => {
  const caption = 'Sunset walk #Amalfi #Momi #amalfi with @Elena.Studio and @Kenji_Photo!';
  assert.deepEqual(extractHashtags(caption, ['#Travel']), ['travel', 'amalfi', 'momi']);
  assert.deepEqual(extractMentions(caption, ['@aria.studio']), ['aria.studio', 'elena.studio', 'kenji_photo']);
});

test('all 10 filter options and aspect ratios match Firestore schema', () => {
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

test('share URL builders, deep-link parser, and Android app recommendation links work properly', () => {
  assert.equal(WEB_APP_URL, 'https://moosvi5.github.io/MOMI/');
  assert.equal(ANDROID_APK_URL, 'https://github.com/MOOSVI5/MOMI/releases/latest/download/Momi-v2.0.apk');
  assert.equal(ANDROID_RELEASES_URL, 'https://github.com/MOOSVI5/MOMI/releases/latest');

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
});

test('starter showcase personas and Firestore Web sync resilience are configured correctly', () => {
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
  assert.ok(appJs.includes('experimentalAutoDetectLongPolling: true'));
  assert.ok(appJs.includes('handleSyncError'));
  assert.ok(appJs.includes('removeAllShowcaseContent'));
  assert.ok(appJs.includes('publishShowcaseBurst'));
  assert.ok(appJs.includes('syncMonetagWebIntegration'));

  const swJs = fs.readFileSync(path.join(REPO_ROOT, 'web', 'sw.js'), 'utf8');
  assert.ok(swJs.includes('momi-web-v3'));
});

test('monetization config defaults, URL sanitization, boost sorting, and web perks align with Firestore rules', () => {
  assert.equal(DEFAULT_MONETIZATION_CONFIG.admobEnabled, true);
  assert.equal(DEFAULT_MONETIZATION_CONFIG.monetagEnabled, true);
  assert.equal(normalizeMonetagDirectUrl('  https://otieu.com/4/9001234 '), 'https://otieu.com/4/9001234');
  assert.equal(normalizeMonetagDirectUrl('http://insecure.example.com'), '');
  assert.equal(normalizeMonetagDirectUrl('javascript:alert(1)'), '');
  assert.equal(sanitizeMonetagZoneId(' 9012345;alert(1) '), '9012345alert1');

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

  assert.equal(DEFAULT_MONETIZATION_CONFIG.gamezopEnabled, false);
  assert.equal(resolveGamezopUrl(DEFAULT_MONETIZATION_CONFIG), '');
  assert.equal(sanitizeGamezopPublisherId(' 9012;drop '), '9012drop');
  assert.equal(
    resolveGamezopUrl({ gamezopEnabled: true, gamezopPublisherId: '9012' }),
    'https://www.gamezop.com/?id=9012'
  );
  assert.equal(
    resolveGamezopUrl({ gamezopEnabled: true, gamezopPublisherId: '9012', gamezopPortalUrl: 'https://4829.play.gamezop.com' }),
    'https://4829.play.gamezop.com'
  );
  assert.equal(
    resolveGamezopUrl({ gamezopEnabled: true, gamezopPublisherId: '9012', gamezopPortalUrl: 'http://insecure.gamezop.com' }),
    'https://www.gamezop.com/?id=9012'
  );
});

test('Supabase migration schema SQL, URL safety, and Firestore bundle mapping match 1:1', () => {
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

  const bundle = mapFirestoreSnapshotToSupabaseBundle({
    profile: { userId: 'u1', username: 'ali', displayName: 'Ali', partnerConsent: true },
    creators: [],
    posts: [
      { id: 'p1', userId: 'u1', authorUsername: 'ali', authorDisplayName: 'Ali', isBoosted: true, boostTier: 'OWNER_RECOMMENDED' },
      { id: 'showcase_1', userId: 'u1', authorUsername: 'elena', authorDisplayName: 'Elena' }
    ],
    stories: [{ id: 's1', userId: 'u1', username: 'ali', caption: 'Hi' }],
    monetizationConfig: { ...DEFAULT_MONETIZATION_CONFIG, gamezopEnabled: true, gamezopPublisherId: '9012' },
    wallet: { userId: 'u1', balance: 250, accountTier: 'CREATOR_PRO', unlockedPerks: ['CREATOR_BOOST'] }
  });

  assert.equal(bundle.schemaVersion, 'momi_supabase_v1');
  assert.equal(bundle.tables.profiles.length, 1);
  assert.equal(bundle.tables.posts.length, 1);
  assert.equal(bundle.tables.posts[0].boost_tier, 'OWNER_RECOMMENDED');
  assert.equal(bundle.tables.app_config[0].gamezop_publisher_id, '9012');
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
});
