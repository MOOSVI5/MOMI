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
  normalizeUsername,
  parseDeepLinkFromLocation,
  PRESET_IMAGE_OPTIONS,
  renderRichCaptionHtml,
  resolveImageSrc,
  SHOWCASE_COMMENTS,
  SHOWCASE_PERSONAS,
  WEB_APP_URL
} from '../firebase-config.js';

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

  const swJs = fs.readFileSync(path.join(REPO_ROOT, 'web', 'sw.js'), 'utf8');
  assert.ok(swJs.includes('momi-web-v3'));
});
