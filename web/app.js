import {
  ADMIN_OWNER_EMAIL,
  ANDROID_APK_URL,
  ANDROID_RELEASES_URL,
  APP_VERSION_CODE,
  APP_VERSION_NAME,
  ASPECT_RATIOS,
  buildPostShareUrl,
  buildProfileShareUrl,
  compareSemanticVersions,
  DEFAULT_MONETIZATION_CONFIG,
  escapeHtml,
  EXPLORE_CATEGORIES,
  extractHashtags,
  extractMentions,
  FILTER_OPTIONS,
  filterActiveStories24h,
  filterCssForTag,
  formatCount,
  initialsForUsername,
  isOwnerEmail,
  MAX_IMAGE_BASE64_CHARS,
  normalizeColorScheme,
  normalizeUsername,
  parseDeepLinkFromLocation,
  POST_BOOST_OPTIONS,
  PRESET_IMAGE_OPTIONS,
  renderRichCaptionHtml,
  resolveImageSrc,
  resolveVideoSrc,
  SHOWCASE_COMMENTS,
  SHOWCASE_PERSONAS,
  sortFeedWithBoostPriority,
  STORY_TTL_MS,
  SUPABASE_WEB_CONFIG,
  tierForTotalPurchased,
  TRENDING_HASHTAGS,
  verificationTierForPerks,
  WEB_APP_URL,
  WEB_PERK_CATALOG,
  aspectRatioCss
} from './app-config.js';
import {
  buildSupabaseGoogleOAuthUrl,
  checkSupabaseHealth,
  clearSupabaseAuthSession,
  createSupabaseRealtimeClient,
  deleteSupabaseRows,
  fetchSupabaseFeed,
  fetchUserSupabaseWorkspace,
  isSupabaseClientReady,
  loadSupabaseAuthSession,
  mapMomiSnapshotToSupabaseBundle,
  mapSupabaseChatMessageToWeb,
  mapSupabaseConfigToWeb,
  mapSupabaseGroupToWeb,
  mapSupabaseLiveStreamToWeb,
  mapSupabaseMerchantToWeb,
  mapSupabaseNotificationToWeb,
  mapSupabaseOrderToWeb,
  mapSupabasePageToWeb,
  mapSupabasePostToWeb,
  mapSupabaseProductToWeb,
  mapSupabaseProfileToWeb,
  mapSupabaseShowcaseAccountToWeb,
  mapSupabaseStoryToWeb,
  mapSupabaseThreadToWeb,
  mapSupabaseTransactionToWeb,
  mapSupabaseWalletToWeb,
  parseSupabaseOAuthCallback,
  patchSupabaseRows,
  saveSupabaseAuthSession,
  selectSupabaseTable,
  SUPABASE_SCHEMA_TABLES,
  supabaseFetchAuthUser,
  supabaseRefreshSession,
  supabaseResendSignupOtp,
  supabaseSignInWithPassword,
  supabaseSignOut,
  supabaseSignUpWithEmail,
  supabaseVerifyEmailOtp,
  syncBundleToSupabase,
  uploadWebFileToStorage,
  upsertSupabaseTable
} from './supabase-client.js';
import {
  assembleVideoChunksToObjectUrl,
  compressFileToBase64,
  exportCanvasToBase64Jpeg,
  playMomiAlertChime,
  renderEditedCanvas
} from './studio.js';

const GIFT_CATALOG = [
  { key: 'rose', name: 'Silk Rose', credits: 10 },
  { key: 'coffee', name: 'Matcha Cup', credits: 25 },
  { key: 'spark', name: 'Momi Spark', credits: 50 },
  { key: 'crown', name: 'Studio Crown', credits: 200 }
];

const state = {
  user: null,
  profile: null,
  wallet: null,
  monetizationConfig: { ...DEFAULT_MONETIZATION_CONFIG },
  creators: [],
  posts: [],
  stories: [],
  liveStreams: [],
  notifications: [],
  threads: [],
  activeThreadId: null,
  activeMessages: [],
  likedIds: new Set(),
  bookmarkIds: new Set(),
  seenStoryIds: new Set(),
  followingUids: new Set(),
  postLikersByPostId: new Map(),
  postViewersByPostId: new Map(),
  merchants: [],
  products: [],
  orders: [],
  transactions: [],
  pages: [],
  pageFollowedIds: new Set(),
  groups: [],
  groupJoinedIds: new Set(),
  showcaseAccounts: [],
  activeCommunityFilter: null,
  communitiesTab: 'pages',
  nav: 'home',
  homeFilter: 'For You',
  exploreQuery: '',
  exploreCategory: 'For You',
  exploreLayout: 'notes',
  marketCategory: 'All',
  profileTab: 'posts',
  ownerConsoleTab: 'starters',
  dismissedAnnouncementId: localStorage.getItem('momi_dismissed_ann') || '',
  dismissedUpdateKey: localStorage.getItem('momi_dismissed_update') || '',
  availableUpdate: null,
  loadedBuildMeta: null,
  swRegistration: null,
  pendingMutations: {
    likes: new Map(),
    bookmarks: new Map(),
    follows: new Map(),
    minViews: new Map(),
    minLikes: new Map(),
    createdPosts: new Map(),
    deletedPostIds: new Map(),
    profile: null
  },
  viewedProfileUid: null,
  carouselIndex: {},
  videoCache: {},
  themeMode: localStorage.getItem('momi_theme') || 'dark',
  scheme: normalizeColorScheme(localStorage.getItem('momi_scheme') || 'EMERALD_MAROON'),
  soundEnabled: localStorage.getItem('momi_sound') !== 'false',
  aiChatHistory: [
    { role: 'assistant', text: 'Hi! I am Momi AI. Ask me for photo captions, video hooks, bio ideas, Page/Group growth tips, or Momi feature help anytime.' }
  ],
  aiBusy: false,
  pollTimers: [],
  realtimeClient: null,
  realtimeState: 'idle',
  activePostModalId: null,
  activeEngagersPostId: null,
  activeEngagersRefresh: null,
  activeLiveRoomRefresh: null,
  livePollTimer: null,
  liveTimer: null,
  liveStreamMedia: null,
  liveCameraOn: true,
  liveMicOn: true,
  seenNotifIds: new Set(),
  seenMsgKeys: new Set(),
  initialSnapshotsLoaded: { notifs: false, threads: false },
  syncError: null,
  isLoadingFeed: true,
  showcaseBusy: false,
  adminBusy: false,
  supabaseBusy: false,
  supabaseStatus: isSupabaseClientReady(SUPABASE_WEB_CONFIG)
    ? 'Supabase Auth, PostgreSQL, Realtime WebSockets & Storage active.'
    : 'Waiting for SUPABASE_WEB_CONFIG (url + anonKey).',
  pendingDeepLink: parseDeepLinkFromLocation(window.location.search, window.location.hash),
  studio: {
    mode: 'POST', // 'POST' | 'CLIP' | 'STORY'
    imageKey: 'img_post_coastal',
    uploadedSrc: null,
    uploadedFile: null,
    uploadedVideoUrl: null,
    uploadedVideoFile: null,
    videoDurationMs: 0,
    filterTag: 'NONE',
    aspectRatio: '1:1',
    rotationDeg: 0,
    brightness: 0,
    contrast: 1,
    warmth: 0,
    caption: '',
    location: '',
    category: 'Travel',
    pageId: null,
    groupId: null,
    communityName: '',
    isReel: false,
    hashtags: [],
    taggedUsernames: [],
    publishing: false
  }
};

let deferredPwaPrompt = null;

// DOM Refs
const authGate = document.getElementById('authGate');
const mainShell = document.getElementById('mainShell');
const mainStage = document.getElementById('mainStage');
const authErrorBox = document.getElementById('authErrorBox');
const modalBackdrop = document.getElementById('modalBackdrop');
const modalContent = document.getElementById('modalContent');
const toastBar = document.getElementById('toastBar');
const mobileDrawerBackdrop = document.getElementById('mobileDrawerBackdrop');

const authInfoBox = document.getElementById('authInfoBox');

const hostnameEl = document.getElementById('currentHostnameLabel');
if (hostnameEl) hostnameEl.textContent = window.location.hostname || 'localhost';

const pendingLinkNotice = document.getElementById('pendingLinkNotice');
if (pendingLinkNotice) {
  if (state.pendingDeepLink.postId) {
    pendingLinkNotice.textContent = 'Shared Momi post ready — sign in to open it directly.';
    pendingLinkNotice.classList.remove('hidden');
  } else if (state.pendingDeepLink.username) {
    pendingLinkNotice.textContent = `Shared creator @${state.pendingDeepLink.username} — sign in to view their profile.`;
    pendingLinkNotice.classList.remove('hidden');
  }
}

async function applyWebAppReloadUpdate() {
  try {
    if ('serviceWorker' in navigator) {
      const regs = await navigator.serviceWorker.getRegistrations();
      await Promise.all(regs.map((r) => r.update().catch(() => undefined)));
      if (state.swRegistration?.waiting) {
        state.swRegistration.waiting.postMessage({ type: 'SKIP_WAITING' });
      }
    }
    if ('caches' in window) {
      const keys = await caches.keys();
      await Promise.all(keys.map((k) => caches.delete(k)));
    }
  } catch {
    // Fallback to hard reload
  }
  window.location.reload();
}

async function checkForWebAndAppUpdates({ manual = false } = {}) {
  try {
    if (state.swRegistration) {
      state.swRegistration.update().catch(() => undefined);
    }
    let manifest = null;
    try {
      const res = await fetch(`./version.json?t=${Date.now()}`, { cache: 'no-store' });
      if (res.ok) {
        manifest = await res.json();
      }
    } catch {
      manifest = null;
    }

    if (manifest && !state.loadedBuildMeta) {
      state.loadedBuildMeta = {
        commitSha: String(manifest.commitSha || ''),
        buildNumber: Number(manifest.buildNumber || 0),
        versionName: String(manifest.versionName || APP_VERSION_NAME)
      };
    }

    const cfg = state.monetizationConfig || DEFAULT_MONETIZATION_CONFIG;
    const manifestVer = String(manifest?.versionName || APP_VERSION_NAME).replace(/^[vV]/, '').trim();
    const manifestCode = Number(manifest?.versionCode || 0);
    const manifestBuild = Number(manifest?.buildNumber || 0);
    const manifestSha = String(manifest?.commitSha || '').trim();
    const supaVer = String(cfg.latestVersionName || APP_VERSION_NAME).replace(/^[vV]/, '').trim();

    const newerVersionFromManifest = compareSemanticVersions(manifestVer, APP_VERSION_NAME) > 0 ||
      (manifestCode > 0 && manifestCode > APP_VERSION_CODE);
    const newerVersionFromSupabase = compareSemanticVersions(supaVer, APP_VERSION_NAME) > 0;
    const pushedNewBuildOnSameVersion = Boolean(
      state.loadedBuildMeta && manifest && (
        (manifestBuild > 0 && manifestBuild > (state.loadedBuildMeta.buildNumber || 0)) ||
        (manifestSha && state.loadedBuildMeta.commitSha && manifestSha !== 'local' && manifestSha !== state.loadedBuildMeta.commitSha)
      )
    );
    const swWaiting = Boolean(state.swRegistration?.waiting);

    const candidateVer = compareSemanticVersions(supaVer, manifestVer) > 0 ? supaVer : manifestVer;
    const updateKey = `${candidateVer}+${manifestBuild || manifestSha || 'supa'}`;

    if (newerVersionFromManifest || newerVersionFromSupabase || pushedNewBuildOnSameVersion || swWaiting) {
      const isDismissed = !manual && state.dismissedUpdateKey === updateKey;
      if (!isDismissed) {
        state.availableUpdate = {
          key: updateKey,
          versionName: candidateVer || APP_VERSION_NAME,
          buildNumber: manifestBuild,
          commitSha: manifestSha,
          releaseNotes: cfg.releaseNotes || manifest?.releaseNotes || 'A newer Momi update is live with fixes and improvements.',
          apkUrl: manifest?.apkUrl || ANDROID_APK_URL,
          webReloadRequired: Boolean(newerVersionFromManifest || pushedNewBuildOnSameVersion || swWaiting)
        };
        if (state.user) {
          if (state.nav === 'home') renderHome();
          renderRightRail();
        }
      }
      if (manual) {
        showToast(`Update available: Momi v${candidateVer || APP_VERSION_NAME}!`);
      }
    } else if (manual) {
      showToast(`Momi Web is up to date (v${APP_VERSION_NAME}).`);
    }
  } catch {
    if (manual) {
      showToast(`Running Momi v${APP_VERSION_NAME}.`);
    }
  }
}

if ('serviceWorker' in navigator) {
  navigator.serviceWorker.register('./sw.js').then((reg) => {
    state.swRegistration = reg;
    reg.addEventListener('updatefound', () => {
      const installing = reg.installing;
      if (!installing) return;
      installing.addEventListener('statechange', () => {
        if (installing.state === 'installed' && navigator.serviceWorker.controller) {
          checkForWebAndAppUpdates({ manual: false });
        }
      });
    });
  }).catch(() => undefined);
}

checkForWebAndAppUpdates({ manual: false });
setInterval(() => checkForWebAndAppUpdates({ manual: false }), 90_000);
document.addEventListener('visibilitychange', () => {
  if (document.visibilityState === 'visible') {
    checkForWebAndAppUpdates({ manual: false });
  }
});

window.addEventListener('beforeinstallprompt', (e) => {
  e.preventDefault();
  deferredPwaPrompt = e;
  document.getElementById('installPwaBtn')?.classList.remove('hidden');
});

document.getElementById('installPwaBtn')?.addEventListener('click', async () => {
  if (!deferredPwaPrompt) return;
  deferredPwaPrompt.prompt();
  await deferredPwaPrompt.userChoice;
  deferredPwaPrompt = null;
  document.getElementById('installPwaBtn')?.classList.add('hidden');
});

function applyAppearance() {
  document.body.dataset.theme = state.themeMode;
  document.body.dataset.scheme = state.scheme;
  const themeLbl = document.getElementById('themeModeLabel');
  if (themeLbl) themeLbl.textContent = state.themeMode === 'dark' ? 'Dark' : 'Light';
  const soundLbl = document.getElementById('soundModeLabel');
  if (soundLbl) soundLbl.textContent = state.soundEnabled ? 'On' : 'Muted';
  const sel = document.getElementById('schemeSelect');
  if (sel) sel.value = state.scheme;
  const dSel = document.getElementById('drawerSchemeSelect');
  if (dSel) dSel.value = state.scheme;
}

applyAppearance();

function toggleThemeMode() {
  state.themeMode = state.themeMode === 'dark' ? 'light' : 'dark';
  localStorage.setItem('momi_theme', state.themeMode);
  applyAppearance();
}

function toggleSoundMode() {
  state.soundEnabled = !state.soundEnabled;
  localStorage.setItem('momi_sound', String(state.soundEnabled));
  applyAppearance();
  if (state.soundEnabled) playMomiAlertChime();
  showToast(state.soundEnabled ? 'Alert chimes enabled' : 'Alert chimes muted');
}

document.getElementById('themeToggleBtn')?.addEventListener('click', toggleThemeMode);
document.getElementById('drawerThemeBtn')?.addEventListener('click', toggleThemeMode);
document.getElementById('soundToggleBtn')?.addEventListener('click', toggleSoundMode);
document.getElementById('drawerSoundBtn')?.addEventListener('click', toggleSoundMode);

document.getElementById('schemeSelect')?.addEventListener('change', (e) => {
  state.scheme = e.target.value;
  localStorage.setItem('momi_scheme', state.scheme);
  applyAppearance();
});
document.getElementById('drawerSchemeSelect')?.addEventListener('change', (e) => {
  state.scheme = e.target.value;
  localStorage.setItem('momi_scheme', state.scheme);
  applyAppearance();
});

function openMobileDrawer() {
  mobileDrawerBackdrop?.classList.remove('hidden');
  mobileDrawerBackdrop?.setAttribute('aria-hidden', 'false');
}

function closeMobileDrawer() {
  mobileDrawerBackdrop?.classList.add('hidden');
  mobileDrawerBackdrop?.setAttribute('aria-hidden', 'true');
}

document.getElementById('mobileMenuBtn')?.addEventListener('click', openMobileDrawer);
document.getElementById('closeMobileDrawerBtn')?.addEventListener('click', closeMobileDrawer);
mobileDrawerBackdrop?.addEventListener('click', (e) => {
  if (e.target === mobileDrawerBackdrop) closeMobileDrawer();
});
document.getElementById('navAiBtn')?.addEventListener('click', () => openAiAssistantModal());
document.getElementById('drawerAiBtn')?.addEventListener('click', () => {
  closeMobileDrawer();
  openAiAssistantModal();
});

function showToast(message) {
  if (!toastBar) return;
  toastBar.textContent = message;
  toastBar.classList.remove('hidden');
  clearTimeout(showToast._timer);
  showToast._timer = setTimeout(() => toastBar.classList.add('hidden'), 3200);
}

function makeClientId(prefix) {
  const rand = crypto.randomUUID ? crypto.randomUUID().replace(/-/g, '').slice(0, 12) : Math.random().toString(36).slice(2, 14);
  return `${prefix}_${Date.now()}_${rand}`;
}

function creatorLookup(uid) {
  if (!uid) return null;
  if (state.profile?.userId === uid) return state.profile;
  return state.creators.find((c) => c.userId === uid) || null;
}

function avatarHtml(profileLike, size = 38) {
  const username = profileLike?.username || profileLike?.authorUsername || 'momi';
  const matchedCreator = profileLike?.userId ? creatorLookup(profileLike.userId) : null;
  const avatarUrl = profileLike?.avatarUrl || matchedCreator?.avatarUrl || null;
  const avatarData = profileLike?.avatarData || matchedCreator?.avatarData || null;
  if (avatarUrl || avatarData) {
    return `<img class="avatar" src="${resolveImageSrc({ avatarUrl, imageData: avatarData })}" alt="@${escapeHtml(username)}" style="width:${size}px;height:${size}px;" />`;
  }
  const initials = initialsForUsername(username);
  return `<span class="avatar" style="width:${size}px;height:${size}px;font-size:${Math.max(11, Math.round(size * 0.36))}px;" aria-label="@${escapeHtml(username)}">${escapeHtml(initials)}</span>`;
}

function openModal(html) {
  modalContent.innerHTML = html;
  modalBackdrop.classList.remove('hidden');
}

function closeModal() {
  stopLiveBroadcast();
  if (state.livePollTimer) {
    clearInterval(state.livePollTimer);
    state.livePollTimer = null;
  }
  state.activePostModalId = null;
  state.activeEngagersPostId = null;
  state.activeEngagersRefresh = null;
  state.activeLiveRoomRefresh = null;
  modalBackdrop.classList.add('hidden');
  modalContent.innerHTML = '';
  if (window.location.search.includes('post=') || window.location.hash.startsWith('#post-')) {
    window.history.replaceState({}, '', window.location.pathname);
  }
}

modalBackdrop?.addEventListener('click', (e) => {
  if (e.target === modalBackdrop) closeModal();
});

document.addEventListener('keydown', (e) => {
  if (e.key === 'Escape' && !modalBackdrop.classList.contains('hidden')) closeModal();
});

// Authentication (Supabase Auth: Google OAuth + Email/Password)
const googleSignInBtn = document.getElementById('googleSignInBtn');
const defaultGoogleSignInHtml = googleSignInBtn.innerHTML;
const toggleEmailAuthBtn = document.getElementById('toggleEmailAuthBtn');
const emailAuthForm = document.getElementById('emailAuthForm');
const authDisplayNameWrap = document.getElementById('authDisplayNameWrap');
const authDisplayNameInput = document.getElementById('authDisplayNameInput');
const authEmailInput = document.getElementById('authEmailInput');
const authPasswordInput = document.getElementById('authPasswordInput');
const emailSignInBtn = document.getElementById('emailSignInBtn');
const toggleAuthModeBtn = document.getElementById('toggleAuthModeBtn');
const otpVerifyBox = document.getElementById('otpVerifyBox');
const otpTargetLabel = document.getElementById('otpTargetLabel');
const authOtpInput = document.getElementById('authOtpInput');
const verifyOtpBtn = document.getElementById('verifyOtpBtn');
const resendOtpBtn = document.getElementById('resendOtpBtn');

let signInPending = false;
let isEmailSignUpMode = false;
let pendingOtpEmail = '';

function isStandalonePwaMode() {
  return Boolean(
    window.matchMedia?.('(display-mode: standalone)')?.matches ||
    window.navigator?.standalone === true
  );
}

function showAuthError(message, { includeOpenInChrome = false } = {}) {
  authInfoBox?.classList.add('hidden');
  authErrorBox.textContent = message;
  if (includeOpenInChrome) {
    const link = document.createElement('a');
    link.href = WEB_APP_URL;
    link.target = '_blank';
    link.rel = 'noopener noreferrer';
    link.textContent = ' Open in Chrome Browser →';
    link.style.color = '#fde047';
    link.style.fontWeight = '700';
    link.style.textDecoration = 'underline';
    link.style.marginLeft = '6px';
    authErrorBox.appendChild(link);
  }
  authErrorBox.classList.remove('hidden');
}

function showAuthInfo(message) {
  authErrorBox?.classList.add('hidden');
  if (!authInfoBox) return;
  authInfoBox.textContent = message;
  authInfoBox.classList.remove('hidden');
}

function clearAuthNotices() {
  authErrorBox?.classList.add('hidden');
  authInfoBox?.classList.add('hidden');
}

function showOtpVerificationPanel(email) {
  pendingOtpEmail = String(email || '').trim().toLowerCase();
  if (!otpVerifyBox) return;
  if (otpTargetLabel) {
    otpTargetLabel.textContent = `Enter the 6-digit verification code sent to ${pendingOtpEmail}.`;
  }
  otpVerifyBox.classList.remove('hidden');
  authOtpInput?.focus();
}

function hideOtpVerificationPanel() {
  pendingOtpEmail = '';
  if (authOtpInput) authOtpInput.value = '';
  otpVerifyBox?.classList.add('hidden');
}

function setSignInBusy(busy) {
  signInPending = busy;
  googleSignInBtn.disabled = busy;
  if (emailSignInBtn) emailSignInBtn.disabled = busy;
  if (verifyOtpBtn) verifyOtpBtn.disabled = busy;
  if (resendOtpBtn) resendOtpBtn.disabled = busy;
  if (busy) {
    googleSignInBtn.textContent = 'Connecting to Supabase...';
  } else {
    googleSignInBtn.innerHTML = defaultGoogleSignInHtml;
  }
}

function updateEmailAuthModeUi() {
  if (!authDisplayNameWrap || !emailSignInBtn || !toggleAuthModeBtn) return;
  authDisplayNameWrap.classList.toggle('hidden', !isEmailSignUpMode);
  emailSignInBtn.textContent = isEmailSignUpMode ? 'Create Momi Account' : 'Sign In with Email';
  toggleAuthModeBtn.textContent = isEmailSignUpMode
    ? 'Already have an account? Sign in'
    : 'Need an account? Create one';
  if (authPasswordInput) {
    authPasswordInput.autocomplete = isEmailSignUpMode ? 'new-password' : 'current-password';
  }
}

toggleEmailAuthBtn?.addEventListener('click', () => {
  clearAuthNotices();
  emailAuthForm?.classList.toggle('hidden');
  const open = !emailAuthForm?.classList.contains('hidden');
  toggleEmailAuthBtn.textContent = open ? 'Hide Email Form' : 'Use Email & Password';
  if (!open) hideOtpVerificationPanel();
  if (open) authEmailInput?.focus();
});

toggleAuthModeBtn?.addEventListener('click', () => {
  clearAuthNotices();
  hideOtpVerificationPanel();
  isEmailSignUpMode = !isEmailSignUpMode;
  updateEmailAuthModeUi();
});

googleSignInBtn.addEventListener('click', () => {
  if (signInPending) return;
  clearAuthNotices();
  hideOtpVerificationPanel();
  const redirectTarget = `${window.location.origin}${window.location.pathname}${window.location.search}`;
  const oauthUrl = buildSupabaseGoogleOAuthUrl(SUPABASE_WEB_CONFIG, redirectTarget);
  if (!oauthUrl) {
    showAuthError('Supabase configuration is not ready.');
    return;
  }
  setSignInBusy(true);
  sessionStorage.setItem('momi_redirect_pending', '1');
  window.location.assign(oauthUrl);
});

emailAuthForm?.addEventListener('submit', async (e) => {
  e.preventDefault();
  if (signInPending) return;
  clearAuthNotices();
  const email = String(authEmailInput?.value || '').trim().toLowerCase();
  const password = String(authPasswordInput?.value || '');
  const displayName = String(authDisplayNameInput?.value || '').trim();
  if (!email.includes('@')) {
    showAuthError('Enter a valid email address.');
    return;
  }
  if (password.length < 6) {
    showAuthError('Password must be at least 6 characters.');
    return;
  }
  setSignInBusy(true);
  if (emailSignInBtn) emailSignInBtn.textContent = isEmailSignUpMode ? 'Creating account...' : 'Signing in...';
  try {
    if (isEmailSignUpMode) {
      const result = await supabaseSignUpWithEmail(SUPABASE_WEB_CONFIG, email, password, displayName);
      if (result.session) {
        hideOtpVerificationPanel();
        applyAuthSession(result.session);
      } else {
        showAuthInfo(`Verification code sent to ${email}. Enter the 6-digit code below to activate your single Momi profile.`);
        showOtpVerificationPanel(email);
      }
    } else {
      const session = await supabaseSignInWithPassword(SUPABASE_WEB_CONFIG, email, password);
      hideOtpVerificationPanel();
      applyAuthSession(session);
    }
  } catch (err) {
    const msg = err?.message || 'Supabase email authentication failed.';
    if (/confirm/i.test(msg)) {
      showAuthInfo('Your email needs verification first. Enter the 6-digit code sent to your inbox or tap Resend Code.');
      showOtpVerificationPanel(email);
    } else {
      showAuthError(msg);
    }
  } finally {
    setSignInBusy(false);
    updateEmailAuthModeUi();
  }
});

verifyOtpBtn?.addEventListener('click', async () => {
  if (signInPending) return;
  clearAuthNotices();
  const email = pendingOtpEmail || String(authEmailInput?.value || '').trim().toLowerCase();
  const code = String(authOtpInput?.value || '').trim();
  if (!email.includes('@')) {
    showAuthError('Enter your email address above first.');
    return;
  }
  if (code.length < 4) {
    showAuthError('Enter the verification code sent to your email.');
    return;
  }
  setSignInBusy(true);
  verifyOtpBtn.textContent = 'Verifying...';
  try {
    const { session } = await supabaseVerifyEmailOtp(SUPABASE_WEB_CONFIG, email, code, 'signup');
    hideOtpVerificationPanel();
    applyAuthSession(session);
  } catch (err) {
    showAuthError(err?.message || 'Verification code invalid or expired. Tap Resend Code.');
  } finally {
    setSignInBusy(false);
    verifyOtpBtn.textContent = 'Verify Code & Continue';
  }
});

resendOtpBtn?.addEventListener('click', async () => {
  if (signInPending) return;
  clearAuthNotices();
  const email = pendingOtpEmail || String(authEmailInput?.value || '').trim().toLowerCase();
  if (!email.includes('@')) {
    showAuthError('Enter your email address above first.');
    return;
  }
  setSignInBusy(true);
  try {
    await supabaseResendSignupOtp(SUPABASE_WEB_CONFIG, email);
    showAuthInfo(`Fresh 6-digit verification code sent to ${email}.`);
  } catch (err) {
    showAuthError(err?.message || 'Could not resend verification code.');
  } finally {
    setSignInBusy(false);
  }
});

async function handleSignOutClick() {
  closeMobileDrawer();
  cleanupSubscriptions();
  await supabaseSignOut(SUPABASE_WEB_CONFIG);
  applyAuthSession(null);
}

document.getElementById('signOutBtn')?.addEventListener('click', handleSignOutClick);
document.getElementById('drawerSignOutBtn')?.addEventListener('click', handleSignOutClick);

function cleanupSubscriptions() {
  stopLiveBroadcast();
  if (state.realtimeClient) {
    state.realtimeClient.stop();
    state.realtimeClient = null;
  }
  if (realtimeDebounceTimer) {
    clearTimeout(realtimeDebounceTimer);
    realtimeDebounceTimer = null;
  }
  if (state.livePollTimer) {
    clearInterval(state.livePollTimer);
    state.livePollTimer = null;
  }
  state.activePostModalId = null;
  state.activeEngagersPostId = null;
  state.activeEngagersRefresh = null;
  state.activeLiveRoomRefresh = null;
  state.realtimeState = 'idle';
  state.pollTimers.forEach((id) => clearInterval(id));
  state.pollTimers = [];
  state.initialSnapshotsLoaded = { notifs: false, threads: false };
}

function handleSyncError(err, label = 'Cloud sync') {
  console.warn(`[Momi Web] ${label}:`, err);
  const code = err?.code || 'sync-error';
  const msg = err?.message || 'Unable to reach Supabase cloud feed.';
  if (code !== 'resource-exhausted') {
    state.syncError = `${label}: ${msg}`;
  }
  state.isLoadingFeed = false;
  renderMainStage();
}

function loadCachedWebWorkspace(uid) {
  try {
    const rawProf = localStorage.getItem(`momi_cached_profile_${uid}`);
    const prof = rawProf ? JSON.parse(rawProf) : null;
    const rawPosts = localStorage.getItem('momi_cached_posts_v1');
    const posts = rawPosts ? JSON.parse(rawPosts) : [];
    const rawStories = localStorage.getItem('momi_cached_stories_v1');
    const stories = rawStories ? filterActiveStories24h(JSON.parse(rawStories)) : [];
    return {
      profile: prof && prof.userId === uid ? prof : null,
      posts: Array.isArray(posts) ? posts : [],
      stories: Array.isArray(stories) ? stories : []
    };
  } catch {
    return { profile: null, posts: [], stories: [] };
  }
}

function saveCachedWebWorkspace(uid) {
  try {
    if (uid && state.profile && state.profile.userId === uid) {
      localStorage.setItem(`momi_cached_profile_${uid}`, JSON.stringify(state.profile));
    }
    if (Array.isArray(state.posts) && state.posts.length > 0) {
      const compactPosts = state.posts.slice(0, 60).map((p) => ({
        ...p,
        imageData: null,
        videoData: null,
        extraImageDatas: [],
        audioData: null
      }));
      localStorage.setItem('momi_cached_posts_v1', JSON.stringify(compactPosts));
    }
    if (Array.isArray(state.stories)) {
      const compactStories = filterActiveStories24h(state.stories).slice(0, 30).map((s) => ({
        ...s,
        imageData: null,
        videoData: null,
        audioData: null
      }));
      localStorage.setItem('momi_cached_stories_v1', JSON.stringify(compactStories));
    }
  } catch {
    // Ignore storage quota issues
  }
}

function buildFallbackProfile(user) {
  const email = String(user.email || '').trim().toLowerCase();
  const username = normalizeUsername(email || user.displayName, user.uid);
  const displayName = (user.displayName || username).slice(0, 80);
  const avatarKeys = ['avatar_aria', 'avatar_elena', 'avatar_kenji', 'avatar_nord', 'avatar_matcha', 'avatar_sora'];
  const avatarKey = avatarKeys[Math.abs(user.uid.charCodeAt(0) + user.uid.length) % avatarKeys.length];
  return {
    userId: user.uid,
    email,
    username,
    displayName,
    category: 'Visual Creator',
    bio: 'Capturing moments across Momi mobile & web.',
    website: '',
    avatarKey,
    avatarUrl: user.photoURL || null,
    avatarData: null,
    followersCount: 0,
    followingCount: 0,
    isVerified: isOwnerEmail(email),
    role: isOwnerEmail(email) ? 'OWNER' : 'USER',
    moderationScopes: [],
    partnerConsent: false
  };
}

function applyAuthSession(session) {
  cleanupSubscriptions();
  const user = session?.user || null;
  state.user = user;
  state.syncError = null;
  if (!user) {
    state.profile = null;
    state.isLoadingFeed = false;
    updateAdminUiVisibility();
    mainShell.classList.add('hidden');
    authGate.classList.remove('hidden');
    return;
  }
  const cached = loadCachedWebWorkspace(user.uid);
  state.profile = cached.profile || buildFallbackProfile(user);
  if (cached.posts.length > 0 && state.posts.length === 0) {
    state.posts = cached.posts;
  }
  if (cached.stories.length > 0 && state.stories.length === 0) {
    state.stories = cached.stories;
  }
  state.isLoadingFeed = state.posts.length === 0;
  authGate.classList.add('hidden');
  mainShell.classList.remove('hidden');
  updateAdminUiVisibility();
  renderAll();
  initializeSupabaseWorkspace(user);
}

async function bootstrapSupabaseAuth() {
  try {
    const oauthCallback = parseSupabaseOAuthCallback(window.location.hash, window.location.search);
    if (oauthCallback) {
      sessionStorage.removeItem('momi_redirect_pending');
      if (oauthCallback.errorDescription) {
        window.history.replaceState({}, '', `${window.location.pathname}${window.location.search}`);
        showAuthError(
          `Google Sign-In via Supabase failed: ${oauthCallback.errorDescription}. Check Supabase Auth -> Google provider or sign in with Email.`,
          { includeOpenInChrome: isStandalonePwaMode() }
        );
        applyAuthSession(null);
        return;
      }
      if (oauthCallback.accessToken) {
        const fetchedUser = await supabaseFetchAuthUser(SUPABASE_WEB_CONFIG, oauthCallback.accessToken);
        window.history.replaceState({}, '', `${window.location.pathname}${window.location.search}`);
        if (fetchedUser) {
          const session = {
            accessToken: oauthCallback.accessToken,
            refreshToken: oauthCallback.refreshToken,
            expiresAt: Date.now() + oauthCallback.expiresIn * 1000,
            user: fetchedUser
          };
          saveSupabaseAuthSession(session);
          applyAuthSession(session);
          return;
        }
      }
    }

    let saved = loadSupabaseAuthSession();
    if (saved) {
      const expiringSoon = !saved.expiresAt || Date.now() > saved.expiresAt - 60_000;
      if (expiringSoon && saved.refreshToken) {
        const refreshed = await supabaseRefreshSession(SUPABASE_WEB_CONFIG, saved.refreshToken);
        if (refreshed) {
          saved = refreshed;
        } else {
          clearSupabaseAuthSession();
          saved = null;
        }
      }
      if (saved) {
        applyAuthSession(saved);
        state.realtimeClient?.updateAccessToken(saved.accessToken);
        return;
      }
    }

    if (sessionStorage.getItem('momi_redirect_pending') === '1') {
      sessionStorage.removeItem('momi_redirect_pending');
      showAuthError(
        'Google Sign-In did not return a Supabase session. Confirm Google OAuth is enabled in Supabase Auth -> Providers -> Google (or use Email & Password).',
        { includeOpenInChrome: isStandalonePwaMode() }
      );
    }
    applyAuthSession(null);
  } catch (err) {
    showAuthError(err?.message || 'Unable to restore Supabase session.');
    applyAuthSession(null);
  }
}

bootstrapSupabaseAuth();

async function initializeSupabaseWorkspace(user) {
  registerDatabaseListenersOnLogin(user);
  startSupabasePolling(user.uid);
  await ensureUserSeededInSupabase(user);
  await refreshAllFromSupabase({ showErrors: true });
}

async function ensureUserSeededInSupabase(user) {
  const uid = user.uid;
  const email = String(user.email || '').trim().toLowerCase();
  const fallback = buildFallbackProfile(user);
  try {
    const queryFilter = email
      ? `select=*&or=(user_id.eq.${encodeURIComponent(uid)},email.ilike.${encodeURIComponent(email)})&limit=2`
      : `select=*&user_id=eq.${encodeURIComponent(uid)}&limit=1`;
    const existingProfiles = await selectSupabaseTable(
      SUPABASE_WEB_CONFIG,
      'profiles',
      queryFilter
    );
    const matched = existingProfiles.find((p) => p.user_id === uid) || existingProfiles[0] || null;
    if (matched) {
      const hydrated = mapSupabaseProfileToWeb(matched);
      state.profile = { ...state.profile, ...hydrated, userId: hydrated.userId || uid };
      saveCachedWebWorkspace(uid);
      if (email && !matched.email && matched.user_id === uid) {
        await patchSupabaseRows(SUPABASE_WEB_CONFIG, 'profiles', `user_id=eq.${encodeURIComponent(uid)}`, { email }).catch(() => undefined);
      }
    } else {
      await upsertSupabaseTable(
        SUPABASE_WEB_CONFIG,
        'profiles',
        [{
          user_id: uid,
          email,
          username: fallback.username,
          display_name: fallback.displayName,
          category: fallback.category,
          bio: fallback.bio,
          website: '',
          avatar_key: fallback.avatarKey,
          avatar_url: fallback.avatarUrl,
          avatar_data: null,
          followers_count: 0,
          following_count: 0,
          is_verified: fallback.isVerified,
          role: fallback.role,
          moderation_scopes: [],
          partner_consent: false
        }],
        '',
        globalThis.fetch,
        'user_id'
      ).catch(() => undefined);
    }

    const existingWallets = await selectSupabaseTable(
      SUPABASE_WEB_CONFIG,
      'wallets',
      `select=*&user_id=eq.${encodeURIComponent(uid)}&limit=1`
    );
    if (existingWallets.length === 0) {
      await upsertSupabaseTable(
        SUPABASE_WEB_CONFIG,
        'wallets',
        [{
          user_id: uid,
          balance: 0,
          total_earned: 0,
          total_spent: 0,
          total_purchased: 0,
          account_tier: 'STANDARD',
          verification_tier: 'NONE',
          unlocked_perks: []
        }],
        '',
        globalThis.fetch,
        'user_id'
      ).catch(() => undefined);
    }
  } catch (err) {
    handleSyncError(err, 'Account setup');
  }
}

const PENDING_MUTATION_TTL_MS = 12_000;

function pruneExpiredPendingMutations(now = Date.now()) {
  const pm = state.pendingMutations;
  const pruneMap = (map) => {
    for (const [k, entry] of map.entries()) {
      if (!entry || entry.expiresAt <= now) map.delete(k);
    }
  };
  pruneMap(pm.likes);
  pruneMap(pm.bookmarks);
  pruneMap(pm.follows);
  pruneMap(pm.minViews);
  pruneMap(pm.minLikes);
  pruneMap(pm.createdPosts);
  pruneMap(pm.deletedPostIds);
  if (pm.profile && pm.profile.expiresAt <= now) pm.profile = null;
}

function shouldDeferDomRefresh() {
  if (state.nav === 'create' || state.nav === 'messages') return true;
  if (modalBackdrop && !modalBackdrop.classList.contains('hidden')) return true;
  const activeEl = document.activeElement;
  if (activeEl && mainStage?.contains(activeEl) && ['INPUT', 'TEXTAREA', 'SELECT'].includes(activeEl.tagName)) {
    return true;
  }
  const playingMedia = mainStage?.querySelectorAll('video, audio') || [];
  for (const el of playingMedia) {
    if (!el.paused && !el.ended) return true;
  }
  return false;
}

async function refreshAllFromSupabase({ showErrors = false, forceRender = false } = {}) {
  if (!state.user) return;
  try {
    pruneExpiredPendingMutations();
    const [feed, workspace] = await Promise.all([
      fetchSupabaseFeed(SUPABASE_WEB_CONFIG),
      fetchUserSupabaseWorkspace(SUPABASE_WEB_CONFIG, state.user.uid)
    ]);

    if (!feed) {
      if (showErrors) {
        state.syncError = 'Unable to load feed from Supabase. Check network connection or run supabase/enable_sync.sql.';
      }
      state.isLoadingFeed = false;
      renderAll();
      return;
    }

    state.syncError = null;
    const pm = state.pendingMutations;

    if (Array.isArray(feed.profiles)) {
      const mappedCreators = feed.profiles.map(mapSupabaseProfileToWeb);
      if (pm.profile?.data) {
        const overrideIdx = mappedCreators.findIndex((c) => c.userId === pm.profile.data.userId);
        if (overrideIdx >= 0) mappedCreators[overrideIdx] = { ...mappedCreators[overrideIdx], ...pm.profile.data };
      }
      state.creators = mappedCreators;
      const myEmail = String(state.user.email || '').trim().toLowerCase();
      const mine = mappedCreators.find((p) => p.userId === state.user.uid) ||
        (myEmail ? mappedCreators.find((p) => p.email === myEmail) : null);
      if (mine) {
        state.profile = { ...state.profile, ...mine, ...(pm.profile?.data || {}) };
      } else if (pm.profile?.data) {
        state.profile = { ...state.profile, ...pm.profile.data };
      }
    }

    if (Array.isArray(feed.posts)) {
      const serverPosts = feed.posts
        .map(mapSupabasePostToWeb)
        .filter((p) => !pm.deletedPostIds.has(p.id))
        .map((p) => {
          const minV = pm.minViews.get(p.id)?.value || 0;
          const minL = pm.minLikes.get(p.id)?.value || 0;
          return {
            ...p,
            viewsCount: Math.max(p.viewsCount || 0, minV),
            likesCount: Math.max(p.likesCount || 0, minL)
          };
        });
      const serverIdSet = new Set(serverPosts.map((p) => p.id));
      const pendingCreated = [];
      for (const entry of pm.createdPosts.values()) {
        if (entry?.post && !serverIdSet.has(entry.post.id) && !pm.deletedPostIds.has(entry.post.id)) {
          pendingCreated.push(entry.post);
        }
      }
      state.posts = sortFeedWithBoostPriority([...pendingCreated, ...serverPosts]);
    }
    if (Array.isArray(feed.stories)) {
      state.stories = filterActiveStories24h(feed.stories.map(mapSupabaseStoryToWeb));
    }
    if (Array.isArray(feed.postInteractions)) {
      const likersMap = new Map();
      const viewersMap = new Map();
      feed.postInteractions.forEach((row) => {
        const pid = String(row.target_id || '').trim();
        const uid = String(row.user_id || '').trim();
        if (!pid || !uid) return;
        if (row.target_type === 'LIKED_POST') {
          if (!likersMap.has(pid)) likersMap.set(pid, new Set());
          likersMap.get(pid).add(uid);
        } else if (row.target_type === 'VIEWED_POST') {
          if (!viewersMap.has(pid)) viewersMap.set(pid, new Set());
          viewersMap.get(pid).add(uid);
        }
      });
      for (const [pid, entry] of pm.likes.entries()) {
        if (!likersMap.has(pid)) likersMap.set(pid, new Set());
        if (entry.value) likersMap.get(pid).add(state.user.uid);
        else likersMap.get(pid).delete(state.user.uid);
      }
      state.postLikersByPostId = likersMap;
      state.postViewersByPostId = viewersMap;
    }
    if (Array.isArray(feed.products)) {
      state.products = feed.products.map(mapSupabaseProductToWeb);
    }
    if (Array.isArray(feed.merchants)) {
      state.merchants = feed.merchants.map(mapSupabaseMerchantToWeb);
    }
    if (Array.isArray(feed.pages)) {
      state.pages = feed.pages.map(mapSupabasePageToWeb);
    }
    if (Array.isArray(feed.groups)) {
      state.groups = feed.groups.map(mapSupabaseGroupToWeb);
    }
    if (Array.isArray(feed.showcaseAccounts)) {
      const mappedAccounts = feed.showcaseAccounts.map(mapSupabaseShowcaseAccountToWeb);
      const mapByName = new Map(mappedAccounts.map((a) => [a.username, a]));
      state.showcaseAccounts = SHOWCASE_PERSONAS.map((p) => {
        const stored = mapByName.get(p.username);
        return stored || {
          username: p.username,
          displayName: p.displayName,
          avatarKey: p.avatarKey,
          bio: `${p.category} creator in ${p.location}`,
          category: p.category,
          location: p.location,
          isAuthorized: false
        };
      });
    }
    if (Array.isArray(feed.liveStreams)) {
      const cutoff = Date.now() - 15 * 60 * 1000;
      state.liveStreams = feed.liveStreams
        .map(mapSupabaseLiveStreamToWeb)
        .filter((s) => s.isLive && s.updatedAtMs >= cutoff);
    }
    if (feed.appConfig) {
      state.monetizationConfig = mapSupabaseConfigToWeb(feed.appConfig);
    }

    if (workspace) {
      const nextFollows = new Set(workspace.follows.map((f) => f.target_uid));
      for (const [uid, entry] of pm.follows.entries()) {
        if (entry.value) nextFollows.add(uid);
        else nextFollows.delete(uid);
      }
      state.followingUids = nextFollows;

      const likes = new Set();
      const saves = new Set();
      const seen = new Set();
      workspace.interactions.forEach((row) => {
        if (row.target_type === 'LIKED_POST') likes.add(row.target_id);
        else if (row.target_type === 'BOOKMARK') saves.add(row.target_id);
        else if (row.target_type === 'SEEN_STORY') seen.add(row.target_id);
      });
      for (const [pid, entry] of pm.likes.entries()) {
        if (entry.value) likes.add(pid);
        else likes.delete(pid);
      }
      for (const [pid, entry] of pm.bookmarks.entries()) {
        if (entry.value) saves.add(pid);
        else saves.delete(pid);
      }
      state.likedIds = likes;
      state.bookmarkIds = saves;
      state.seenStoryIds = seen;
      state.pageFollowedIds = new Set((workspace.pageFollows || []).map((pf) => pf.page_id));
      state.groupJoinedIds = new Set((workspace.groupMembers || []).map((gm) => gm.group_id));

      const notifs = workspace.notifications.map(mapSupabaseNotificationToWeb);
      if (state.initialSnapshotsLoaded.notifs) {
        const fresh = notifs.find((n) => n.isUnread && !state.seenNotifIds.has(n.id));
        if (fresh) triggerAlert(`${fresh.actorUsername}: ${fresh.message}`);
      }
      notifs.forEach((n) => state.seenNotifIds.add(n.id));
      state.initialSnapshotsLoaded.notifs = true;
      state.notifications = notifs;

      const threads = workspace.threads.map(mapSupabaseThreadToWeb);
      if (state.initialSnapshotsLoaded.threads) {
        const unread = threads.find((t) =>
          t.unreadUid === state.user.uid &&
          (t.unreadCount || 0) > 0 &&
          !state.seenMsgKeys.has(`${t.id}:${t.lastMessage}:${t.timeLabel}`)
        );
        if (unread) {
          const otherName = unread.userAId === state.user.uid ? unread.userBUsername : unread.userAUsername;
          triggerAlert(`New message from @${otherName}: ${unread.lastMessage}`);
        }
      }
      threads.forEach((t) => state.seenMsgKeys.add(`${t.id}:${t.lastMessage}:${t.timeLabel}`));
      state.initialSnapshotsLoaded.threads = true;
      state.threads = threads;

      if (workspace.wallet) {
        state.wallet = mapSupabaseWalletToWeb(workspace.wallet);
      }
      state.transactions = workspace.transactions.map(mapSupabaseTransactionToWeb);
      state.orders = workspace.orders.map(mapSupabaseOrderToWeb);
    }

    state.isLoadingFeed = false;
    saveCachedWebWorkspace(state.user.uid);
    maybeConsumePendingDeepLink();

    updateAdminUiVisibility();
    updateBadges();
    renderRightRail();
    if (forceRender || !shouldDeferDomRefresh()) {
      renderMainStage();
    }
  } catch (err) {
    if (showErrors) handleSyncError(err, 'Supabase feed refresh');
  }
}

let realtimeDebounceTimer = null;

function scheduleRealtimeFeedRefresh() {
  if (realtimeDebounceTimer) return;
  realtimeDebounceTimer = setTimeout(() => {
    realtimeDebounceTimer = null;
    if (state.user) {
      refreshAllFromSupabase({ showErrors: false });
    }
  }, 350);
}

function refreshUiImmediatelyFromRealtime() {
  if (!state.user) return;
  updateBadges();
  renderRightRail();
  if (!shouldDeferDomRefresh()) {
    renderMainStage();
  }
}

function applyRealtimePostEvent(evt) {
  const type = String(evt?.eventType || '').toUpperCase();
  const pm = state.pendingMutations;
  if (type === 'DELETE') {
    const deletedId = String(evt?.oldRecord?.id || evt?.record?.id || '').trim();
    if (!deletedId) return;
    state.posts = state.posts.filter((p) => p.id !== deletedId);
    if (state.user?.uid) saveCachedWebWorkspace(state.user.uid);
    refreshUiImmediatelyFromRealtime();
    return;
  }
  if (!evt?.record) return;
  const mapped = mapSupabasePostToWeb(evt.record);
  if (!mapped?.id || pm.deletedPostIds.has(mapped.id)) return;
  const minV = pm.minViews.get(mapped.id)?.value || 0;
  const minL = pm.minLikes.get(mapped.id)?.value || 0;
  const merged = {
    ...mapped,
    viewsCount: Math.max(mapped.viewsCount || 0, minV),
    likesCount: Math.max(mapped.likesCount || 0, minL)
  };
  const idx = state.posts.findIndex((p) => p.id === merged.id);
  const next = idx >= 0
    ? state.posts.map((p, i) => (i === idx ? merged : p))
    : [merged, ...state.posts];
  state.posts = sortFeedWithBoostPriority(next);
  if (state.user?.uid) saveCachedWebWorkspace(state.user.uid);
  refreshUiImmediatelyFromRealtime();
}

function applyRealtimeCommentEvent(evt) {
  const type = String(evt?.eventType || '').toUpperCase();
  const postId = String(evt?.record?.post_id || evt?.oldRecord?.post_id || '').trim();
  if (postId) {
    const post = state.posts.find((p) => p.id === postId);
    if (post && type === 'INSERT') {
      post.commentsCount = (post.commentsCount || 0) + 1;
      refreshUiImmediatelyFromRealtime();
    } else if (post && type === 'DELETE') {
      post.commentsCount = Math.max(0, (post.commentsCount || 0) - 1);
      refreshUiImmediatelyFromRealtime();
    }
  }
  if (state.activePostModalId && (!postId || postId === state.activePostModalId)) {
    refreshOpenPostModalComments(state.activePostModalId);
  }
}

function applyRealtimeLikeOrInteractionEvent(evt) {
  const type = String(evt?.eventType || '').toUpperCase();
  const isDelete = type === 'DELETE';
  const row = isDelete ? (evt?.oldRecord || evt?.record || {}) : (evt?.record || {});
  const table = String(evt?.table || '').toLowerCase();
  const targetType = table === 'likes'
    ? 'LIKED_POST'
    : String(row.target_type || '').trim().toUpperCase();
  const targetId = String(row.target_id || row.post_id || '').trim();
  const actorUid = String(row.user_id || '').trim();
  if (!targetId) return;

  if (targetType === 'LIKED_POST' && actorUid) {
    if (!state.postLikersByPostId.has(targetId)) {
      state.postLikersByPostId.set(targetId, new Set());
    }
    const likersSet = state.postLikersByPostId.get(targetId);
    const hadUser = likersSet.has(actorUid);
    let changed = false;
    if (isDelete && hadUser) {
      likersSet.delete(actorUid);
      changed = true;
    } else if (!isDelete && !hadUser) {
      likersSet.add(actorUid);
      changed = true;
    }
    if (actorUid === state.user?.uid && !state.pendingMutations.likes.has(targetId)) {
      if (isDelete) state.likedIds.delete(targetId);
      else state.likedIds.add(targetId);
    }
    if (changed) {
      const post = state.posts.find((p) => p.id === targetId);
      if (post && !state.pendingMutations.minLikes.has(targetId)) {
        post.likesCount = Math.max(0, (post.likesCount || 0) + (isDelete ? -1 : 1));
      }
      refreshUiImmediatelyFromRealtime();
      if (state.activeEngagersPostId === targetId && typeof state.activeEngagersRefresh === 'function') {
        state.activeEngagersRefresh();
      }
    }
  } else if (targetType === 'VIEWED_POST' && actorUid && !isDelete) {
    if (!state.postViewersByPostId.has(targetId)) {
      state.postViewersByPostId.set(targetId, new Set());
    }
    state.postViewersByPostId.get(targetId).add(actorUid);
    if (state.activeEngagersPostId === targetId && typeof state.activeEngagersRefresh === 'function') {
      state.activeEngagersRefresh();
    }
  } else if (targetType === 'BOOKMARK' && actorUid === state.user?.uid && !state.pendingMutations.bookmarks.has(targetId)) {
    if (isDelete) state.bookmarkIds.delete(targetId);
    else state.bookmarkIds.add(targetId);
    refreshUiImmediatelyFromRealtime();
  }
}

function registerDatabaseListenersOnLogin(user) {
  if (!user?.uid) return;
  startSupabaseRealtime(user.uid);
}

function startSupabaseRealtime(uid) {
  if (state.realtimeClient) {
    state.realtimeClient.stop();
  }
  const client = createSupabaseRealtimeClient(SUPABASE_WEB_CONFIG, {
    onStatusChange(status, detail) {
      state.realtimeState = status;
      if (detail) {
        state.supabaseStatus = detail;
      }
      if (state.nav === 'admin' || state.nav === 'profile') {
        renderMainStage();
      }
    },
    onTableChange(evt) {
      if (!state.user || state.user.uid !== uid) return;
      const table = evt?.table || '';
      if (table === 'live_frames' || table === 'live_comments' || table === 'live_streams') {
        if (typeof state.activeLiveRoomRefresh === 'function') {
          state.activeLiveRoomRefresh();
        }
        if (table === 'live_streams') {
          scheduleRealtimeFeedRefresh();
        }
        return;
      }
      if (table === 'chat_messages') {
        if (state.nav === 'messages' && state.activeThreadId) {
          loadMessagesForThread(state.activeThreadId);
        } else {
          scheduleRealtimeFeedRefresh();
        }
        return;
      }
      scheduleRealtimeFeedRefresh();
    }
  });
  client.registerTableListeners({
    posts: (evt) => {
      if (state.user?.uid !== uid) return;
      applyRealtimePostEvent(evt);
    },
    comments: (evt) => {
      if (state.user?.uid !== uid) return;
      applyRealtimeCommentEvent(evt);
    },
    user_interactions: (evt) => {
      if (state.user?.uid !== uid) return;
      applyRealtimeLikeOrInteractionEvent(evt);
    },
    likes: (evt) => {
      if (state.user?.uid !== uid) return;
      applyRealtimeLikeOrInteractionEvent(evt);
    }
  });
  state.realtimeClient = client;
  client.start();
}

function startSupabasePolling(uid) {
  // Low-frequency safety reconciliation sweep alongside primary WebSocket CDC Realtime
  const feedTimer = setInterval(() => {
    if (!document.hidden && state.user?.uid === uid) {
      refreshAllFromSupabase({ showErrors: false });
      if (state.nav === 'messages' && state.activeThreadId) {
        loadMessagesForThread(state.activeThreadId);
      }
    }
  }, 15000);
  state.pollTimers.push(feedTimer);
}

function maybeConsumePendingDeepLink() {
  if (!state.user || !state.profile) return;
  if (state.pendingDeepLink.postId) {
    const targetPostId = state.pendingDeepLink.postId;
    state.pendingDeepLink.postId = null;
    if (pendingLinkNotice) pendingLinkNotice.classList.add('hidden');
    openPostDetailModal(targetPostId);
    return;
  }
  if (state.pendingDeepLink.username && state.creators.length > 0) {
    const targetName = state.pendingDeepLink.username;
    state.pendingDeepLink.username = null;
    if (pendingLinkNotice) pendingLinkNotice.classList.add('hidden');
    const creator = state.creators.find((c) => (c.username || '').toLowerCase() === targetName.toLowerCase());
    if (creator) {
      state.viewedProfileUid = creator.userId;
      state.profileTab = 'posts';
      navigateTo('profile', { keepViewedUid: true });
    } else {
      state.exploreCategory = 'For You';
      state.exploreQuery = `@${targetName}`;
      navigateTo('explore');
    }
  }
}

function triggerAlert(text) {
  if (state.soundEnabled) playMomiAlertChime();
  showToast(text);
  if ('Notification' in window && Notification.permission === 'granted' && document.hidden) {
    new Notification('Momi', { body: text, icon: './assets/momi-icon.jpg' });
  }
}

function updateBadges() {
  const cfg = state.monetizationConfig || DEFAULT_MONETIZATION_CONFIG;
  const hasUnreadAnnouncement = Boolean(
    (cfg.announcementTitle || cfg.announcementBody || cfg.releaseNotes) &&
    state.dismissedAnnouncementId !== (cfg.announcementId || 'latest')
  ) ? 1 : 0;
  const unreadNotifs = state.notifications.filter((n) => n.isUnread).length + hasUnreadAnnouncement;
  const unreadMsgs = state.threads.filter((t) => t.unreadUid === state.user?.uid && (t.unreadCount || 0) > 0).length;
  const liveCount = state.liveStreams.length;

  const setBadge = (id, count) => {
    const el = document.getElementById(id);
    if (!el) return;
    el.textContent = count > 99 ? '99+' : String(count);
    el.classList.toggle('hidden', count <= 0);
  };
  setBadge('navNotifBadge', unreadNotifs);
  setBadge('navMsgBadge', unreadMsgs);
  setBadge('navLiveBadge', liveCount);

  document.getElementById('mobileNotifDot')?.classList.toggle('hidden', unreadNotifs <= 0);
  document.getElementById('mobileMsgDot')?.classList.toggle('hidden', unreadMsgs <= 0);
}

// Navigation
document.querySelectorAll('[data-nav]').forEach((btn) => {
  btn.addEventListener('click', () => {
    closeMobileDrawer();
    navigateTo(btn.dataset.nav);
  });
});

function navigateTo(target, opts = {}) {
  state.nav = target;
  if (target === 'profile' && !opts.keepViewedUid) {
    state.viewedProfileUid = state.user?.uid || null;
  }
  document.querySelectorAll('[data-nav]').forEach((b) => {
    b.classList.toggle('active', b.dataset.nav === target);
  });
  renderMainStage();
}

function updateAdminUiVisibility() {
  const show = isOwnerAdmin();
  ['navAdminBtn', 'drawerAdminBtn', 'mobileAdminBtn'].forEach((id) => {
    document.getElementById(id)?.classList.toggle('hidden', !show);
  });
}

function renderAll() {
  updateAdminUiVisibility();
  updateBadges();
  renderRightRail();
  renderMainStage();
}

function renderRightRail() {
  const rightUserCard = document.getElementById('rightUserCard');
  const rightAndroidCard = document.getElementById('rightAndroidCard');
  const rightLiveCard = document.getElementById('rightLiveCard');
  const rightCreatorsCard = document.getElementById('rightCreatorsCard');
  if (!state.profile || !rightUserCard) return;

  const balance = state.wallet?.balance || 0;
  const tier = state.wallet?.accountTier || 'STANDARD';
  rightUserCard.innerHTML = `
    <div style="display:flex;align-items:center;gap:12px;">
      ${avatarHtml(state.profile, 46)}
      <div style="min-width:0;flex:1;">
        <div class="post-author-name">@${escapeHtml(state.profile.username)} ${state.profile.isVerified ? '<span class="verified-dot">&#10003;</span>' : ''}</div>
        <div class="muted small">${escapeHtml(state.profile.displayName)}</div>
      </div>
      <button type="button" class="btn-outline btn-sm" id="railProfileBtn">Profile</button>
    </div>
    <div style="display:flex;align-items:center;justify-content:space-between;margin-top:12px;padding-top:10px;border-top:1px solid var(--border);gap:8px;flex-wrap:wrap;">
      <div>
        <div style="font-weight:700;font-size:0.9rem;">${formatCount(balance)} Credits</div>
        <div class="muted small">${escapeHtml(tier)}</div>
      </div>
      <div style="display:flex;gap:6px;flex-wrap:wrap;">
        ${isOwnerAdmin() ? '<button type="button" class="owner-pill-btn" id="railAdminBtn">&#9881; Owner</button>' : ''}
        <button type="button" class="btn-primary btn-sm" id="railWalletBtn">Wallet</button>
      </div>
    </div>
  `;
  document.getElementById('railProfileBtn').onclick = () => navigateTo('profile');
  document.getElementById('railWalletBtn').onclick = () => navigateTo('wallet');
  document.getElementById('railAdminBtn')?.addEventListener('click', () => navigateTo('admin'));

  if (rightAndroidCard) {
    rightAndroidCard.innerHTML = `
      <div class="rail-title-row">
        <h3>Momi v${escapeHtml(APP_VERSION_NAME)}</h3>
        <span class="desktop-badge">APK &amp; WEB</span>
      </div>
      <p class="muted small" style="margin:0 0 10px;">
        Native mobile camera studio, notifications &amp; Play Billing wallet — synced with Web via Supabase.
      </p>
      <div class="android-promo-actions">
        <a class="btn-primary btn-sm" href="${ANDROID_APK_URL}" target="_blank" rel="noopener noreferrer">Download APK</a>
        <a class="btn-outline btn-sm" href="${ANDROID_RELEASES_URL}" target="_blank" rel="noopener noreferrer">Releases</a>
        <button type="button" class="btn-ghost btn-sm" id="railCheckUpdatesBtn">Check Updates</button>
        <button type="button" class="btn-ghost btn-sm" id="railCopyAndroidLinkBtn">Copy</button>
      </div>
    `;
    document.getElementById('railCheckUpdatesBtn').onclick = () => checkForWebAndAppUpdates({ manual: true });
    document.getElementById('railCopyAndroidLinkBtn').onclick = async () => {
      await navigator.clipboard?.writeText(ANDROID_APK_URL).catch(() => undefined);
      showToast('Android APK download link copied!');
    };
  }

  rightLiveCard.innerHTML = `
    <div class="rail-title-row">
      <h3>Live on Momi</h3>
      <button type="button" class="btn-ghost btn-sm" id="railGoLiveBtn">Go Live</button>
    </div>
    ${
      state.liveStreams.length === 0
        ? '<p class="muted small" style="margin:0;">No active rooms right now. Start a Live Room anytime!</p>'
        : state.liveStreams.slice(0, 4).map((s) => `
          <div style="display:flex;align-items:center;justify-content:space-between;gap:10px;margin-bottom:10px;">
            <div style="display:flex;align-items:center;gap:10px;min-width:0;">
              ${avatarHtml(s, 34)}
              <div style="min-width:0;">
                <div style="font-weight:700;font-size:0.86rem;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">@${escapeHtml(s.username)}</div>
                <div class="muted small" style="overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">${escapeHtml(s.title || 'Live Broadcast')}</div>
              </div>
            </div>
            <button type="button" class="btn-primary btn-sm" data-open-live="${escapeHtml(s.userId)}">Watch</button>
          </div>
        `).join('')
    }
  `;
  document.getElementById('railGoLiveBtn').onclick = () => openGoLiveModal();
  rightLiveCard.querySelectorAll('[data-open-live]').forEach((b) => {
    b.onclick = () => openWatchLiveModal(b.dataset.openLive);
  });

  const others = state.creators.filter((c) => c.userId !== state.user?.uid).slice(0, 5);
  rightCreatorsCard.innerHTML = `
    <div class="rail-title-row">
      <h3>Creators to Follow</h3>
      <button type="button" class="btn-ghost btn-sm" id="railSeeAllBtn">Explore</button>
    </div>
    ${
      others.length === 0
        ? '<p class="muted small" style="margin:0;">Invite friends to Momi to see more creators here.</p>'
        : others.map((c) => {
          const isFollowing = state.followingUids.has(c.userId);
          return `
            <div style="display:flex;align-items:center;justify-content:space-between;gap:10px;margin-bottom:10px;">
              <button type="button" class="post-author-btn" data-open-user="${escapeHtml(c.userId)}">
                ${avatarHtml(c, 34)}
                <div class="post-author-meta">
                  <span class="post-author-name" style="font-size:0.84rem;">@${escapeHtml(c.username)}</span>
                  <span class="muted small">${escapeHtml(c.category || 'Creator')}</span>
                </div>
              </button>
              <button type="button" class="btn-sm ${isFollowing ? 'btn-outline' : 'btn-primary'}" data-follow-uid="${escapeHtml(c.userId)}" data-follow-name="${escapeHtml(c.username)}">
                ${isFollowing ? 'Following' : 'Follow'}
              </button>
            </div>
          `;
        }).join('')
    }
  `;
  document.getElementById('railSeeAllBtn').onclick = () => navigateTo('explore');
  bindUserAndFollowButtons(rightCreatorsCard);
}

function renderMainStage() {
  if (!state.user) return;
  switch (state.nav) {
    case 'home': renderHome(); break;
    case 'explore': renderExplore(); break;
    case 'clips': renderClips(); break;
    case 'create': renderCreateStudio(); break;
    case 'live': renderLiveLobby(); break;
    case 'communities': renderCommunities(); break;
    case 'marketplace': renderMarketplace(); break;
    case 'messages': renderMessages(); break;
    case 'notifications': renderNotifications(); break;
    case 'wallet': renderWallet(); break;
    case 'profile': renderProfile(); break;
    case 'admin': renderAdminConsole(); break;
    default: renderHome();
  }
}

function getFilteredHomePosts() {
  if (state.homeFilter === 'Following') {
    return state.posts.filter((p) => state.followingUids.has(p.userId) || p.userId === state.user?.uid);
  }
  if (state.homeFilter === 'Saved') {
    return state.posts.filter((p) => state.bookmarkIds.has(p.id));
  }
  return state.posts;
}

function isOwnerAdmin() {
  return isOwnerEmail(state.user?.email);
}

function boostBadgeLabelForPost(post) {
  if (!post) return '';
  if (post.recommendedLabel) return post.recommendedLabel;
  if (post.boostTier === 'OWNER_RECOMMENDED') return 'Recommended by Momi';
  if (post.boostTier === 'SUPER_BOOST') return 'Super Boosted';
  if (post.boostTier === 'SPOTLIGHT' || post.isBoosted) return 'Boosted Spotlight';
  return '';
}

function getPostFrames(post) {
  const urls = Array.isArray(post.extraImageUrls) ? post.extraImageUrls : [];
  const datas = Array.isArray(post.extraImageDatas) ? post.extraImageDatas : [];
  const frames = [];
  if (post.imageUrl || post.imageData || post.imageKey) {
    frames.push({ imageUrl: post.imageUrl, imageData: post.imageData, imageKey: post.imageKey });
  }
  const extraCount = Math.max(urls.length, datas.length);
  for (let i = 0; i < extraCount; i += 1) {
    frames.push({
      imageUrl: urls[i] || null,
      imageData: datas[i] || null,
      imageKey: post.imageKey
    });
  }
  return frames.length > 0 ? frames : [{ imageKey: post.imageKey || 'img_post_coastal' }];
}

function renderPostCardHtml(post) {
  const frames = getPostFrames(post);
  const idx = Math.min(state.carouselIndex[post.id] || 0, Math.max(0, frames.length - 1));
  const currentFrame = frames[idx] || { imageKey: post.imageKey };
  const isLiked = state.likedIds.has(post.id);
  const isSaved = state.bookmarkIds.has(post.id);
  const isMine = post.userId === state.user?.uid;
  const isFollowing = state.followingUids.has(post.userId);
  const isVideo = post.mediaType === 'VIDEO' || post.isReel || Boolean(post.videoUrl || post.videoData);
  const videoSrc = resolveVideoSrc(post) || state.videoCache[post.id] || '';
  const ratio = aspectRatioCss(post.aspectRatio);
  const filterCss = filterCssForTag(post.filterTag);
  const boostBadge = boostBadgeLabelForPost(post);
  const isRecommended = post.boostTier === 'OWNER_RECOMMENDED' || Boolean(post.recommendedLabel);

  const tags = Array.isArray(post.hashtags) ? post.hashtags : [];
  const tagged = Array.isArray(post.taggedUsernames) ? post.taggedUsernames : [];

  return `
    <article class="post-card" data-post-id="${escapeHtml(post.id)}">
      <header class="post-header">
        <button type="button" class="post-author-btn" data-open-user="${escapeHtml(post.userId)}">
          ${avatarHtml({ userId: post.userId, username: post.authorUsername }, 38)}
          <div class="post-author-meta">
            <span class="post-author-name">
              @${escapeHtml(post.authorUsername)}
              ${post.authorVerified ? '<span class="verified-dot">&#10003;</span>' : ''}
            </span>
            <span class="muted small">${escapeHtml(post.location || post.category || 'Momi Feed')}</span>
          </div>
        </button>
        <div style="display:flex;align-items:center;gap:6px;flex-wrap:wrap;justify-content:flex-end;">
          ${post.communityName ? `<button type="button" class="community-pill" data-open-community="${escapeHtml(post.pageId || post.groupId || '')}" data-community-type="${post.pageId ? 'PAGE' : 'GROUP'}" data-community-name="${escapeHtml(post.communityName)}">&#9670; ${escapeHtml(post.communityName)}</button>` : ''}
          ${boostBadge ? `<span class="boost-pill ${isRecommended ? 'recommended' : ''}">&#9733; ${escapeHtml(boostBadge)}</span>` : ''}
          ${isOwnerAdmin() && !post.id.startsWith('showcase_') ? `<button type="button" class="btn-outline btn-sm" data-admin-recommend="${escapeHtml(post.id)}">${isRecommended ? 'Unfeature' : 'Recommend'}</button>` : ''}
          ${isMine && !post.id.startsWith('showcase_') ? `<button type="button" class="btn-outline btn-sm" data-boost-post="${escapeHtml(post.id)}">Boost</button>` : ''}
          ${!isMine ? `<button type="button" class="btn-sm ${isFollowing ? 'btn-outline' : 'btn-primary'}" data-follow-uid="${escapeHtml(post.userId)}" data-follow-name="${escapeHtml(post.authorUsername)}">${isFollowing ? 'Following' : 'Follow'}</button>` : ''}
          ${isMine || isOwnerAdmin() ? `<button type="button" class="btn-ghost btn-sm" data-delete-post="${escapeHtml(post.id)}" title="Delete post">Delete</button>` : ''}
        </div>
      </header>

      <div class="post-media-wrap" style="aspect-ratio:${ratio};" data-open-post="${escapeHtml(post.id)}">
        ${
          isVideo && videoSrc
            ? `<video class="post-media-video" src="${escapeHtml(videoSrc)}" poster="${escapeHtml(resolveImageSrc(currentFrame))}" controls playsinline preload="metadata" data-stop-open="true"></video>`
            : `<img class="post-media-img" src="${resolveImageSrc(currentFrame)}" alt="${escapeHtml(post.caption || 'Momi post')}" style="filter:${filterCss};" loading="lazy" />`
        }
        ${isVideo && !videoSrc ? `<span class="media-badge">Clip</span>` : ''}
        ${!isVideo && post.filterTag && post.filterTag !== 'NONE' ? `<span class="media-badge">${escapeHtml(post.filterTag)}</span>` : ''}
        ${!isVideo && frames.length > 1 ? `
          <button type="button" class="carousel-btn prev" data-slide="${escapeHtml(post.id)}:-1" aria-label="Previous photo">&lsaquo;</button>
          <button type="button" class="carousel-btn next" data-slide="${escapeHtml(post.id)}:1" aria-label="Next photo">&rsaquo;</button>
          <span class="media-badge" style="top:auto;bottom:12px;">${idx + 1} / ${frames.length}</span>
        ` : ''}
      </div>

      <div class="post-body">
        <div class="post-actions">
          <div class="post-actions-left">
            <button type="button" class="action-btn ${isLiked ? 'liked' : ''}" data-like-post="${escapeHtml(post.id)}">
              ${isLiked ? '&#9829;' : '&#9825;'} <span>${formatCount(post.likesCount)}</span>
            </button>
            <button type="button" class="action-btn" data-open-post="${escapeHtml(post.id)}">
              &#128172; <span>${formatCount(post.commentsCount)}</span>
            </button>
            <button type="button" class="action-btn" data-share-post="${escapeHtml(post.id)}">
              Share
            </button>
          </div>
          <button type="button" class="action-btn ${isSaved ? 'bookmarked' : ''}" data-bookmark-post="${escapeHtml(post.id)}">
            ${isSaved ? 'Saved' : 'Save'}
          </button>
        </div>

        <div class="post-stats-row">
          <button type="button" class="post-stats-link" data-open-engagers="${escapeHtml(post.id)}:0">
            <strong>${formatCount(post.likesCount)}</strong> likes
          </button>
          <span>&middot;</span>
          <button type="button" class="post-stats-link" data-open-engagers="${escapeHtml(post.id)}:1">
            <strong>${formatCount(post.viewsCount)}</strong> views
          </button>
          <span>&middot;</span>
          <button type="button" class="post-stats-link" data-open-post="${escapeHtml(post.id)}">
            ${formatCount(post.commentsCount)} comments
          </button>
        </div>

        ${post.audioTitle ? `<div class="muted small">&#9835; ${escapeHtml(post.audioTitle)}</div>` : ''}
        <div class="post-caption">${renderRichCaptionHtml(post.caption || '')}</div>

        ${(tags.length || tagged.length) ? `
          <div class="tag-pill-row">
            ${tagged.map((u) => `<button type="button" class="tag-pill" data-mention="${escapeHtml(u)}">@${escapeHtml(u)}</button>`).join('')}
            ${tags.map((t) => `<button type="button" class="tag-pill" data-hashtag="${escapeHtml(t)}">#${escapeHtml(t)}</button>`).join('')}
          </div>
        ` : ''}
      </div>
    </article>
  `;
}

function renderHome() {
  const posts = getFilteredHomePosts();
  const cfg = state.monetizationConfig || DEFAULT_MONETIZATION_CONFIG;
  const annId = cfg.announcementId || 'latest';
  const showAnnouncement = Boolean(
    (cfg.announcementTitle || cfg.announcementBody || cfg.releaseNotes) &&
    state.dismissedAnnouncementId !== annId
  );

  mainStage.innerHTML = `
    <div class="stage-column">
      <div class="section-header">
        <h2>Home Feed</h2>
        <div class="chip-row">
          ${['For You', 'Following', 'Saved'].map((f) => `
            <button type="button" class="chip ${state.homeFilter === f ? 'active' : ''}" data-home-filter="${f}">${f}</button>
          `).join('')}
          <button type="button" class="chip" id="homeOpenCommunitiesChip">&#9670; Pages &amp; Groups</button>
          <button type="button" class="chip" id="homeOpenAiChip">&#10022; Momi AI</button>
          ${isOwnerAdmin() ? '<button type="button" class="owner-pill-btn" id="homeOpenAdminBtn">&#9881; Owner Console</button>' : ''}
        </div>
      </div>

      ${state.availableUpdate ? `
        <div class="announcement-banner" role="region" aria-label="Pushed app update available" style="border-color:var(--primary);">
          <div style="flex:1;min-width:200px;">
            <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap;">
              <span class="badge-pill">New Update · v${escapeHtml(state.availableUpdate.versionName)}</span>
              <strong>${state.availableUpdate.webReloadRequired ? 'New pushed Momi update is live!' : 'New Android APK release available'}</strong>
            </div>
            <div class="small muted" style="margin-top:6px;line-height:1.45;">
              ${escapeHtml(state.availableUpdate.releaseNotes)} (Running Web v${escapeHtml(APP_VERSION_NAME)})
            </div>
            <div class="chip-row" style="margin-top:8px;">
              ${state.availableUpdate.webReloadRequired ? '<button type="button" class="btn-primary btn-sm" id="reloadUpdatedWebBtn">Update &amp; Reload Web</button>' : ''}
              <a class="btn-outline btn-sm" href="${escapeHtml(state.availableUpdate.apkUrl || ANDROID_APK_URL)}" target="_blank" rel="noopener noreferrer" style="text-decoration:none;">Download Android APK</a>
            </div>
          </div>
          <button type="button" class="btn-ghost btn-sm" id="dismissVersionUpdateBtn" data-update-key="${escapeHtml(state.availableUpdate.key)}">Later</button>
        </div>
      ` : ''}

      ${showAnnouncement ? `
        <div class="announcement-banner" role="region" aria-label="In-app announcement">
          <div style="flex:1;min-width:200px;">
            <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap;">
              <span class="badge-pill">Momi v${escapeHtml(cfg.latestVersionName || APP_VERSION_NAME)}</span>
              <strong>${escapeHtml(cfg.announcementTitle || 'Momi Update Notice')}</strong>
            </div>
            ${cfg.announcementBody ? `<div class="small" style="margin-top:6px;line-height:1.45;">${escapeHtml(cfg.announcementBody)}</div>` : ''}
            ${cfg.releaseNotes ? `<div class="muted small" style="margin-top:4px;">What's new: ${escapeHtml(cfg.releaseNotes)}</div>` : ''}
          </div>
          <button type="button" class="btn-ghost btn-sm" id="dismissAnnouncementBtn" data-ann-id="${escapeHtml(annId)}">Dismiss</button>
        </div>
      ` : ''}

      ${state.syncError ? `
        <div class="sync-error-banner" role="alert">
          <div style="flex:1;min-width:220px;">
            <strong>Cloud feed notice</strong>
            <div class="small muted">${escapeHtml(state.syncError)}</div>
          </div>
          <button type="button" class="btn-outline btn-sm" id="retrySyncBtn">Retry Sync</button>
        </div>
      ` : ''}

      <section class="stories-tray" aria-label="Stories and live streams">
        <button type="button" class="story-tile" id="homeGoLiveTile">
          <span class="avatar-ring"><span class="avatar" style="width:58px;height:58px;font-size:1.3rem;">&#9673;</span></span>
          <span style="color:#fb7185;font-weight:700;">Go Live</span>
        </button>
        ${state.liveStreams.map((s) => `
          <button type="button" class="story-tile" data-open-live="${escapeHtml(s.userId)}">
            <span class="avatar-ring">${avatarHtml(s, 58)}</span>
            <span class="live-pill-badge">LIVE</span>
            <span>@${escapeHtml(s.username)}</span>
          </button>
        `).join('')}
        ${state.stories.map((st) => `
          <button type="button" class="story-tile" data-open-story="${escapeHtml(st.id)}">
            <span class="avatar-ring ${state.seenStoryIds.has(st.id) ? 'seen' : ''}">${avatarHtml({ userId: st.userId, username: st.username }, 58)}</span>
            <span>@${escapeHtml(st.username)}</span>
          </button>
        `).join('')}
      </section>

      ${
        posts.length === 0
          ? `<div class="card-panel" style="text-align:center;padding:36px;">
              <h3 style="margin:0 0 8px;">${state.isLoadingFeed ? 'Loading your Momi feed...' : `No posts in ${escapeHtml(state.homeFilter)} yet`}</h3>
              <p class="muted" style="margin:0 0 16px;">${state.isLoadingFeed ? 'Fetching latest posts, clips & stories from Supabase.' : 'Create a post in Studio or follow creators in Explore.'}</p>
              <button type="button" class="btn-primary" id="emptyCreateBtn">Open Create Studio</button>
            </div>`
          : posts.map(renderPostCardHtml).join('')
      }
    </div>
  `;

  document.getElementById('homeOpenCommunitiesChip').onclick = () => navigateTo('communities');
  document.getElementById('homeOpenAiChip').onclick = () => openAiAssistantModal();
  document.getElementById('homeOpenAdminBtn')?.addEventListener('click', () => navigateTo('admin'));
  document.getElementById('reloadUpdatedWebBtn')?.addEventListener('click', () => applyWebAppReloadUpdate());
  document.getElementById('dismissVersionUpdateBtn')?.addEventListener('click', (e) => {
    const k = e.currentTarget.dataset.updateKey || '';
    state.dismissedUpdateKey = k;
    localStorage.setItem('momi_dismissed_update', k);
    state.availableUpdate = null;
    renderHome();
  });
  document.getElementById('dismissAnnouncementBtn')?.addEventListener('click', (e) => {
    const id = e.currentTarget.dataset.annId || 'latest';
    state.dismissedAnnouncementId = id;
    localStorage.setItem('momi_dismissed_ann', id);
    updateBadges();
    renderHome();
  });

  const retryBtn = document.getElementById('retrySyncBtn');
  if (retryBtn && state.user) {
    retryBtn.onclick = () => {
      state.syncError = null;
      state.isLoadingFeed = true;
      renderHome();
      refreshAllFromSupabase({ showErrors: true, forceRender: true });
    };
  }
  mainStage.querySelectorAll('[data-home-filter]').forEach((b) => {
    b.onclick = () => {
      state.homeFilter = b.dataset.homeFilter;
      renderHome();
    };
  });
  document.getElementById('homeGoLiveTile').onclick = () => openGoLiveModal();
  const emptyCreateBtn = document.getElementById('emptyCreateBtn');
  if (emptyCreateBtn) emptyCreateBtn.onclick = () => navigateTo('create');
  bindFeedInteractions(mainStage);
}

function renderExplore() {
  const q = state.exploreQuery.trim().toLowerCase();
  const cleanTag = q.startsWith('#') ? q.slice(1) : '';
  const cleanUser = q.startsWith('@') ? q.slice(1) : '';
  const filteredPosts = state.posts.filter((p) => {
    if (state.exploreCategory !== 'For You' && p.category !== state.exploreCategory) return false;
    if (!q) return true;
    const tags = Array.isArray(p.hashtags) ? p.hashtags : [];
    const tagged = Array.isArray(p.taggedUsernames) ? p.taggedUsernames : [];
    if (cleanTag) return tags.some((t) => t.toLowerCase().includes(cleanTag)) || (p.caption || '').toLowerCase().includes(`#${cleanTag}`);
    if (cleanUser) return (p.authorUsername || '').toLowerCase().includes(cleanUser) || tagged.some((u) => u.toLowerCase().includes(cleanUser));
    return (
      (p.caption || '').toLowerCase().includes(q) ||
      (p.authorUsername || '').toLowerCase().includes(q) ||
      (p.location || '').toLowerCase().includes(q) ||
      tags.some((t) => t.toLowerCase().includes(q)) ||
      tagged.some((u) => u.toLowerCase().includes(q))
    );
  });

  mainStage.innerHTML = `
    <div class="stage-wide">
      <div class="section-header">
        <h2>Explore &amp; Discover</h2>
        <div class="chip-row">
          <button type="button" class="chip ${state.exploreLayout === 'notes' ? 'active' : ''}" id="layoutNotesBtn">Discover Notes</button>
          <button type="button" class="chip ${state.exploreLayout === 'grid' ? 'active' : ''}" id="layoutGridBtn">3-Col Grid</button>
        </div>
      </div>

      <div style="display:flex;gap:10px;flex-wrap:wrap;">
        <input id="exploreSearchInput" type="search" class="input" style="flex:1;min-width:220px;" placeholder="Search #hashtags, @creators, locations, or captions..." value="${escapeHtml(state.exploreQuery)}" />
        ${state.exploreQuery ? '<button type="button" class="btn-outline" id="clearExploreBtn">Clear</button>' : ''}
      </div>

      <div class="chip-row">
        ${TRENDING_HASHTAGS.map((tag) => `
          <button type="button" class="chip ${state.exploreQuery.toLowerCase() === `#${tag}` ? 'active' : ''}" data-trend-tag="${tag}">#${tag}</button>
        `).join('')}
      </div>

      <div class="chip-row">
        ${EXPLORE_CATEGORIES.map((cat) => `
          <button type="button" class="chip ${state.exploreCategory === cat ? 'active' : ''}" data-explore-cat="${escapeHtml(cat)}">${escapeHtml(cat)}</button>
        `).join('')}
      </div>

      ${
        state.exploreLayout === 'notes'
          ? `<div class="discovery-grid">
              ${filteredPosts.map((p) => {
                const badge = boostBadgeLabelForPost(p);
                const isVid = p.mediaType === 'VIDEO' || Boolean(p.videoUrl);
                return `
                  <article class="discovery-card" data-open-post="${escapeHtml(p.id)}">
                    <div style="position:relative;">
                      <img class="discovery-thumb" src="${resolveImageSrc(p)}" alt="${escapeHtml(p.caption || '')}" style="filter:${filterCssForTag(p.filterTag)};" loading="lazy" />
                      ${isVid ? '<span class="media-badge">&#9654; Video</span>' : ''}
                    </div>
                    <div class="discovery-meta">
                      ${badge ? `<span class="boost-pill ${p.boostTier === 'OWNER_RECOMMENDED' ? 'recommended' : ''}">${escapeHtml(badge)}</span>` : ''}
                      <div class="discovery-title">${escapeHtml(p.caption || 'Visual Note')}</div>
                      <div class="discovery-footer">
                        <span>@${escapeHtml(p.authorUsername)}</span>
                        <span>&#9829; ${formatCount(p.likesCount)}</span>
                      </div>
                    </div>
                  </article>
                `;
              }).join('')}
            </div>`
          : `<div class="profile-grid-3">
              ${filteredPosts.map((p) => `
                <button type="button" class="profile-tile" data-open-post="${escapeHtml(p.id)}">
                  <img src="${resolveImageSrc(p)}" alt="${escapeHtml(p.caption || '')}" style="filter:${filterCssForTag(p.filterTag)};" loading="lazy" />
                </button>
              `).join('')}
            </div>`
      }
    </div>
  `;

  document.getElementById('layoutNotesBtn').onclick = () => { state.exploreLayout = 'notes'; renderExplore(); };
  document.getElementById('layoutGridBtn').onclick = () => { state.exploreLayout = 'grid'; renderExplore(); };
  const input = document.getElementById('exploreSearchInput');
  input.oninput = (e) => { state.exploreQuery = e.target.value; renderExplore(); document.getElementById('exploreSearchInput')?.focus(); };
  const clearBtn = document.getElementById('clearExploreBtn');
  if (clearBtn) clearBtn.onclick = () => { state.exploreQuery = ''; renderExplore(); };
  mainStage.querySelectorAll('[data-trend-tag]').forEach((b) => {
    b.onclick = () => { state.exploreQuery = `#${b.dataset.trendTag}`; renderExplore(); };
  });
  mainStage.querySelectorAll('[data-explore-cat]').forEach((b) => {
    b.onclick = () => { state.exploreCategory = b.dataset.exploreCat; renderExplore(); };
  });
  bindFeedInteractions(mainStage);
}

function renderClips() {
  const clips = state.posts.filter((p) => p.isReel || p.mediaType === 'VIDEO' || Boolean(p.videoUrl || p.videoData));
  mainStage.innerHTML = `
    <div class="stage-column">
      <div class="section-header">
        <h2>Momi Clips &amp; Videos</h2>
        <button type="button" class="btn-primary btn-sm" id="uploadClipQuickBtn">Upload Video Clip</button>
      </div>
      ${
        clips.length === 0
          ? `<div class="card-panel" style="text-align:center;padding:32px;">
              <h3 style="margin:0 0 8px;">No video clips yet</h3>
              <p class="muted" style="margin:0 0 14px;">Upload any MP4/WebM video in Create Studio or share from Android.</p>
              <button type="button" class="btn-primary" id="emptyClipCreateBtn">Create Video Clip</button>
            </div>`
          : clips.map(renderPostCardHtml).join('')
      }
    </div>
  `;
  document.getElementById('uploadClipQuickBtn').onclick = () => {
    state.studio.mode = 'CLIP';
    state.studio.isReel = true;
    navigateTo('create');
  };
  document.getElementById('emptyClipCreateBtn')?.addEventListener('click', () => {
    state.studio.mode = 'CLIP';
    state.studio.isReel = true;
    navigateTo('create');
  });
  bindFeedInteractions(mainStage);
}

function renderCreateStudio() {
  const s = state.studio;
  const activeSrc = s.uploadedSrc || resolveImageSrc({ imageKey: s.imageKey });
  const otherCreators = state.creators.filter((c) => c.userId !== state.user?.uid);
  const isClipMode = s.mode === 'CLIP';
  const isStoryMode = s.mode === 'STORY';

  mainStage.innerHTML = `
    <div class="stage-wide">
      <div class="section-header">
        <div>
          <h2>Create Studio</h2>
          <div class="muted small">Upload photos or HD video clips directly to Supabase Storage</div>
        </div>
        <div class="chip-row">
          <button type="button" class="chip ${s.mode === 'POST' ? 'active' : ''}" data-studio-mode="POST">Photo Post</button>
          <button type="button" class="chip ${s.mode === 'CLIP' ? 'active' : ''}" data-studio-mode="CLIP">Video Clip</button>
          <button type="button" class="chip ${s.mode === 'STORY' ? 'active' : ''}" data-studio-mode="STORY">24h Story</button>
        </div>
      </div>

      <div class="studio-layout">
        <div class="card-panel">
          <div class="studio-canvas-box">
            ${
              s.uploadedVideoUrl
                ? `<video id="studioVideoPreview" src="${escapeHtml(s.uploadedVideoUrl)}" controls playsinline muted style="max-height:420px;width:100%;"></video>`
                : `<canvas id="studioCanvas"></canvas>`
            }
          </div>

          <div style="display:flex;flex-wrap:wrap;gap:10px;align-items:center;">
            <label class="btn-primary btn-sm" for="studioFileInput">${isClipMode ? 'Choose Cover Photo' : 'Upload Photo'}</label>
            <input id="studioFileInput" type="file" accept="image/*" class="hidden" />
            <label class="btn-outline btn-sm" for="studioVideoInput">${s.uploadedVideoUrl ? 'Change Video' : 'Upload Video (.mp4)'}</label>
            <input id="studioVideoInput" type="file" accept="video/*" class="hidden" />
            ${!s.uploadedVideoUrl ? '<button type="button" class="btn-outline btn-sm" id="studioRotateBtn">Rotate 90&deg;</button>' : ''}
            <button type="button" class="btn-ghost btn-sm" id="studioResetEditBtn">Reset</button>
          </div>

          ${!s.uploadedVideoUrl ? `
            <div>
              <div class="muted small" style="margin-bottom:6px;">Quick Presets</div>
              <div class="chip-row">
                ${PRESET_IMAGE_OPTIONS.map((opt) => `
                  <button type="button" class="chip ${!s.uploadedSrc && s.imageKey === opt.key ? 'active' : ''}" data-preset-key="${opt.key}">${escapeHtml(opt.label)}</button>
                `).join('')}
              </div>
            </div>

            <div>
              <div class="muted small" style="margin-bottom:6px;">Aspect Ratio</div>
              <div class="chip-row">
                ${ASPECT_RATIOS.map((r) => `
                  <button type="button" class="chip ${s.aspectRatio === r ? 'active' : ''}" data-aspect-btn="${r}">${r}</button>
                `).join('')}
              </div>
            </div>

            <div>
              <div class="muted small" style="margin-bottom:6px;">Color Filters</div>
              <div class="filter-strip">
                ${FILTER_OPTIONS.map((f) => `
                  <button type="button" class="filter-thumb ${s.filterTag === f.tag ? 'active' : ''}" data-filter-tag="${f.tag}">${escapeHtml(f.label)}</button>
                `).join('')}
              </div>
            </div>

            <div class="field-grid-2">
              <label class="small muted">Brightness (<span id="brightVal">${s.brightness}</span>)
                <input id="brightSlider" type="range" min="-35" max="35" value="${s.brightness}" style="width:100%;" />
              </label>
              <label class="small muted">Contrast (<span id="contrastVal">${s.contrast}</span>)
                <input id="contrastSlider" type="range" min="0.7" max="1.4" step="0.05" value="${s.contrast}" style="width:100%;" />
              </label>
            </div>
            <label class="small muted">Warmth (<span id="warmthVal">${s.warmth}</span>)
              <input id="warmthSlider" type="range" min="-0.3" max="0.45" step="0.05" value="${s.warmth}" style="width:100%;" />
            </label>
          ` : `
            <div class="consent-row">
              <div>
                <strong>Video Ready for Upload</strong>
                <div class="muted small">Selected video will stream directly via Supabase Video Storage.</div>
              </div>
              <button type="button" class="btn-outline btn-sm" id="clearStudioVideoBtn">Remove Video</button>
            </div>
          `}
        </div>

        <div class="card-panel">
          <div>
            <label class="small muted" for="studioCaption">${isStoryMode ? 'Story Caption' : 'Caption (supports #hashtags and @mentions)'}</label>
            <textarea id="studioCaption" class="textarea" rows="4" placeholder="Write what you are sharing today... #momi" style="margin-top:6px;">${escapeHtml(s.caption)}</textarea>
          </div>

          ${!isStoryMode ? `
            <div class="field-grid-2">
              <div>
                <label class="small muted" for="studioLocation">Location</label>
                <input id="studioLocation" class="input" placeholder="e.g. Tokyo, Lahore" value="${escapeHtml(s.location)}" style="margin-top:6px;" />
              </div>
              <div>
                <label class="small muted" for="studioCategory">Category</label>
                <select id="studioCategory" class="select" style="margin-top:6px;">
                  ${EXPLORE_CATEGORIES.filter((c) => c !== 'For You').map((c) => `
                    <option value="${escapeHtml(c)}" ${s.category === c ? 'selected' : ''}>${escapeHtml(c)}</option>
                  `).join('')}
                </select>
              </div>
            </div>

            <div>
              <label class="small muted">Hashtags</label>
              <div style="display:flex;gap:8px;margin-top:6px;">
                <input id="customTagInput" class="input" placeholder="Add #tag" />
                <button type="button" class="btn-outline btn-sm" id="addCustomTagBtn">Add</button>
              </div>
              <div class="chip-row" style="margin-top:10px;">
                ${TRENDING_HASHTAGS.map((t) => `
                  <button type="button" class="chip ${s.hashtags.includes(t) ? 'active' : ''}" data-studio-tag="${t}">#${t}</button>
                `).join('')}
                ${s.hashtags.filter((t) => !TRENDING_HASHTAGS.includes(t)).map((t) => `
                  <button type="button" class="chip active" data-studio-tag="${escapeHtml(t)}">#${escapeHtml(t)} &times;</button>
                `).join('')}
              </div>
            </div>

            ${otherCreators.length > 0 ? `
              <div>
                <label class="small muted">Tag Creators</label>
                <div class="chip-row" style="margin-top:8px;">
                  ${otherCreators.slice(0, 8).map((c) => `
                    <button type="button" class="chip ${s.taggedUsernames.includes(c.username) ? 'active' : ''}" data-tag-creator="${escapeHtml(c.username)}">@${escapeHtml(c.username)}</button>
                  `).join('')}
                </div>
              </div>
            ` : ''}

            ${(state.pages.length > 0 || state.groups.length > 0) ? `
              <div>
                <label class="small muted" for="studioCommunitySelect">Post to Page or Group (Optional)</label>
                <select id="studioCommunitySelect" class="select" style="margin-top:6px;">
                  <option value="">Personal Momi Feed</option>
                  ${state.pages.map((pg) => `
                    <option value="PAGE:${escapeHtml(pg.id)}" ${s.pageId === pg.id ? 'selected' : ''}>Page: ${escapeHtml(pg.name)}</option>
                  `).join('')}
                  ${state.groups.map((gr) => `
                    <option value="GROUP:${escapeHtml(gr.id)}" ${s.groupId === gr.id ? 'selected' : ''}>Group: ${escapeHtml(gr.name)}</option>
                  `).join('')}
                </select>
              </div>
            ` : ''}

            <label class="consent-row" style="cursor:pointer;">
              <span>Feature in Momi Clips</span>
              <input id="studioIsReel" type="checkbox" ${s.isReel || isClipMode ? 'checked' : ''} />
            </label>
          ` : ''}

          <button type="button" class="btn-primary btn-lg full-width" id="publishPostBtn" ${s.publishing ? 'disabled' : ''}>
            ${s.publishing ? 'Uploading & Publishing...' : (isStoryMode ? 'Share 24h Story' : (isClipMode || s.uploadedVideoUrl ? 'Publish Video Clip' : 'Publish Post'))}
          </button>
        </div>
      </div>
    </div>
  `;

  mainStage.querySelectorAll('[data-studio-mode]').forEach((b) => {
    b.onclick = () => {
      s.mode = b.dataset.studioMode;
      s.isReel = s.mode === 'CLIP';
      renderCreateStudio();
    };
  });

  const canvas = document.getElementById('studioCanvas');
  const redraw = () => {
    if (canvas) renderEditedCanvas(canvas, activeSrc, s).catch(() => undefined);
  };
  redraw();

  document.getElementById('studioFileInput').onchange = (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    if (s.uploadedSrc?.startsWith('blob:')) URL.revokeObjectURL(s.uploadedSrc);
    s.uploadedSrc = URL.createObjectURL(file);
    s.uploadedFile = file;
    renderCreateStudio();
  };

  document.getElementById('studioVideoInput').onchange = (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    if (s.uploadedVideoUrl?.startsWith('blob:')) URL.revokeObjectURL(s.uploadedVideoUrl);
    s.uploadedVideoUrl = URL.createObjectURL(file);
    s.uploadedVideoFile = file;
    s.mode = 'CLIP';
    s.isReel = true;
    renderCreateStudio();
    const vidEl = document.getElementById('studioVideoPreview');
    if (vidEl) {
      vidEl.onloadedmetadata = () => {
        s.videoDurationMs = Math.round((vidEl.duration || 0) * 1000);
      };
    }
  };

  document.getElementById('clearStudioVideoBtn')?.addEventListener('click', () => {
    if (s.uploadedVideoUrl?.startsWith('blob:')) URL.revokeObjectURL(s.uploadedVideoUrl);
    s.uploadedVideoUrl = null;
    s.uploadedVideoFile = null;
    s.videoDurationMs = 0;
    s.mode = 'POST';
    renderCreateStudio();
  });

  document.getElementById('studioRotateBtn')?.addEventListener('click', () => {
    s.rotationDeg = (s.rotationDeg + 90) % 360;
    redraw();
  });

  document.getElementById('studioResetEditBtn').onclick = () => {
    Object.assign(s, {
      filterTag: 'NONE',
      aspectRatio: '1:1',
      rotationDeg: 0,
      brightness: 0,
      contrast: 1,
      warmth: 0
    });
    renderCreateStudio();
  };

  mainStage.querySelectorAll('[data-preset-key]').forEach((b) => {
    b.onclick = () => { s.uploadedSrc = null; s.uploadedFile = null; s.imageKey = b.dataset.presetKey; renderCreateStudio(); };
  });
  mainStage.querySelectorAll('[data-aspect-btn]').forEach((b) => {
    b.onclick = () => { s.aspectRatio = b.dataset.aspectBtn; renderCreateStudio(); };
  });
  mainStage.querySelectorAll('[data-filter-tag]').forEach((b) => {
    b.onclick = () => { s.filterTag = b.dataset.filterTag; renderCreateStudio(); };
  });

  const brightSlider = document.getElementById('brightSlider');
  if (brightSlider) brightSlider.oninput = (e) => { s.brightness = Number(e.target.value); document.getElementById('brightVal').textContent = s.brightness; redraw(); };
  const contrastSlider = document.getElementById('contrastSlider');
  if (contrastSlider) contrastSlider.oninput = (e) => { s.contrast = Number(e.target.value); document.getElementById('contrastVal').textContent = s.contrast; redraw(); };
  const warmthSlider = document.getElementById('warmthSlider');
  if (warmthSlider) warmthSlider.oninput = (e) => { s.warmth = Number(e.target.value); document.getElementById('warmthVal').textContent = s.warmth; redraw(); };

  document.getElementById('studioCaption').oninput = (e) => { s.caption = e.target.value; };
  document.getElementById('studioLocation')?.addEventListener('input', (e) => { s.location = e.target.value; });
  document.getElementById('studioCategory')?.addEventListener('change', (e) => { s.category = e.target.value; });
  document.getElementById('studioCommunitySelect')?.addEventListener('change', (e) => {
    const raw = e.target.value;
    if (!raw) {
      s.pageId = null;
      s.groupId = null;
      s.communityName = '';
      return;
    }
    const [kind, id] = raw.split(':');
    if (kind === 'PAGE') {
      const pg = state.pages.find((x) => x.id === id);
      s.pageId = id;
      s.groupId = null;
      s.communityName = pg?.name || 'Page';
    } else if (kind === 'GROUP') {
      const gr = state.groups.find((x) => x.id === id);
      s.groupId = id;
      s.pageId = null;
      s.communityName = gr?.name || 'Group';
    }
  });
  document.getElementById('studioIsReel')?.addEventListener('change', (e) => { s.isReel = e.target.checked; });

  document.getElementById('addCustomTagBtn')?.addEventListener('click', () => {
    const val = document.getElementById('customTagInput').value.trim().replace(/^#/, '').toLowerCase().replace(/[^a-z0-9_]/g, '');
    if (val && !s.hashtags.includes(val) && s.hashtags.length < 15) {
      s.hashtags.push(val);
      renderCreateStudio();
    }
  });
  mainStage.querySelectorAll('[data-studio-tag]').forEach((b) => {
    b.onclick = () => {
      const t = b.dataset.studioTag;
      s.hashtags = s.hashtags.includes(t) ? s.hashtags.filter((x) => x !== t) : [...s.hashtags, t].slice(0, 15);
      renderCreateStudio();
    };
  });
  mainStage.querySelectorAll('[data-tag-creator]').forEach((b) => {
    b.onclick = () => {
      const u = b.dataset.tagCreator;
      s.taggedUsernames = s.taggedUsernames.includes(u) ? s.taggedUsernames.filter((x) => x !== u) : [...s.taggedUsernames, u].slice(0, 10);
      renderCreateStudio();
    };
  });

  document.getElementById('publishPostBtn').onclick = async () => {
    if (!state.profile || s.publishing) return;
    s.publishing = true;
    renderCreateStudio();
    try {
      await ensureUserSeededInSupabase(state.user);

      let imageUrl = null;
      let imageData = null;
      let videoUrl = null;
      const itemId = makeClientId(isStoryMode ? 'story' : 'post');

      // Upload image/cover frame to Supabase Storage
      const hasCustomOrEditedImage = Boolean(s.uploadedSrc) || s.filterTag !== 'NONE' || s.aspectRatio !== '1:1' || s.rotationDeg !== 0 || s.brightness !== 0 || s.contrast !== 1 || s.warmth !== 0;
      if (hasCustomOrEditedImage && !s.uploadedVideoFile) {
        const exportCanvas = document.createElement('canvas');
        await renderEditedCanvas(exportCanvas, activeSrc, s);
        const blob = await new Promise((resolve) => exportCanvas.toBlob(resolve, 'image/jpeg', 0.88));
        if (blob) {
          imageUrl = await uploadWebFileToStorage(
            SUPABASE_WEB_CONFIG,
            'momi-media',
            `posts/${state.user.uid}/${itemId}.jpg`,
            blob,
            'image/jpeg'
          );
        }
        if (!imageUrl) {
          imageData = exportCanvasToBase64Jpeg(exportCanvas, MAX_IMAGE_BASE64_CHARS);
        }
      }

      // Upload video to Supabase Storage if selected
      if (s.uploadedVideoFile) {
        const ext = (s.uploadedVideoFile.name.split('.').pop() || 'mp4').toLowerCase().replace(/[^a-z0-9]/g, '') || 'mp4';
        videoUrl = await uploadWebFileToStorage(
          SUPABASE_WEB_CONFIG,
          'momi-videos',
          `videos/${state.user.uid}/${itemId}.${ext}`,
          s.uploadedVideoFile,
          s.uploadedVideoFile.type || 'video/mp4'
        );
        if (!videoUrl) {
          throw new Error('Could not upload video file to Supabase Storage. Ensure supabase/enable_sync.sql has been run.');
        }
      }

      if (isStoryMode) {
        await upsertSupabaseTable(
          SUPABASE_WEB_CONFIG,
          'stories',
          [{
            id: itemId,
            user_id: state.user.uid,
            username: state.profile.username,
            avatar_key: state.profile.avatarKey || 'avatar_aria',
            image_key: s.imageKey,
            image_url: imageUrl,
            image_data: imageData,
            media_type: videoUrl ? 'VIDEO' : 'IMAGE',
            video_url: videoUrl,
            video_duration_ms: s.videoDurationMs || 0,
            caption: s.caption.trim().slice(0, 300)
          }],
          '',
          globalThis.fetch,
          'id'
        );
        showToast('Story published to Momi!');
      } else {
        const finalHashtags = extractHashtags(s.caption, s.hashtags);
        const finalTags = extractMentions(s.caption, s.taggedUsernames);
        const isVideo = Boolean(videoUrl);
        const postRow = {
          id: itemId,
          user_id: state.user.uid,
          author_username: state.profile.username,
          author_display_name: state.profile.displayName,
          author_avatar_key: state.profile.avatarKey || 'avatar_aria',
          author_verified: Boolean(state.profile.isVerified),
          location: s.location.trim().slice(0, 120),
          image_key: s.imageKey,
          image_url: imageUrl,
          image_data: imageData,
          media_type: isVideo ? 'VIDEO' : 'IMAGE',
          video_url: videoUrl,
          video_duration_ms: s.videoDurationMs || 0,
          filter_tag: s.filterTag,
          aspect_ratio: s.aspectRatio,
          tagged_usernames: finalTags,
          hashtags: finalHashtags,
          caption: s.caption.trim().slice(0, 1000),
          likes_count: 0,
          views_count: 0,
          comments_count: 0,
          category: s.category || 'Travel',
          is_reel: Boolean(s.isReel || isVideo),
          page_id: s.pageId || null,
          group_id: s.groupId || null,
          community_name: (s.communityName || '').slice(0, 80)
        };
        const mappedCreated = mapSupabasePostToWeb(postRow);
        state.pendingMutations.createdPosts.set(itemId, { post: mappedCreated, expiresAt: Date.now() + 12000 });
        state.posts = sortFeedWithBoostPriority([mappedCreated, ...state.posts.filter((p) => p.id !== itemId)]);
        await upsertSupabaseTable(
          SUPABASE_WEB_CONFIG,
          'posts',
          [postRow],
          '',
          globalThis.fetch,
          'id'
        );
        showToast('Published to Momi across Web & Android!');
      }

      if (s.uploadedSrc?.startsWith('blob:')) URL.revokeObjectURL(s.uploadedSrc);
      if (s.uploadedVideoUrl?.startsWith('blob:')) URL.revokeObjectURL(s.uploadedVideoUrl);

      state.studio = {
        mode: 'POST',
        imageKey: 'img_post_coastal',
        uploadedSrc: null,
        uploadedFile: null,
        uploadedVideoUrl: null,
        uploadedVideoFile: null,
        videoDurationMs: 0,
        filterTag: 'NONE',
        aspectRatio: '1:1',
        rotationDeg: 0,
        brightness: 0,
        contrast: 1,
        warmth: 0,
        caption: '',
        location: '',
        category: 'Travel',
        pageId: null,
        groupId: null,
        communityName: '',
        isReel: false,
        hashtags: [],
        taggedUsernames: [],
        publishing: false
      };
      await refreshAllFromSupabase({ showErrors: false });
      navigateTo('home');
    } catch (err) {
      s.publishing = false;
      renderCreateStudio();
      showToast(err?.message || 'Publish failed.');
    }
  };
}

function renderLiveLobby() {
  mainStage.innerHTML = `
    <div class="stage-wide">
      <div class="section-header">
        <div>
          <h2>Momi Live Rooms</h2>
          <div class="muted small">Go live with webcam or Live Studio Stage &middot; interactive real-time chat &amp; gifts</div>
        </div>
        <button type="button" class="btn-primary" id="lobbyGoLiveBtn">Start Live Studio</button>
      </div>
      ${
        state.liveStreams.length === 0
          ? `<div class="card-panel" style="text-align:center;padding:40px 24px;">
              <h3 style="margin:0 0 8px;">No broadcasts active right now</h3>
              <p class="muted" style="margin:0 0 18px;">Start your own live studio room from Web or Android — your audience can chat, send hearts, and send gifts immediately.</p>
              <button type="button" class="btn-primary" id="lobbyEmptyLiveBtn">Go Live Now</button>
            </div>`
          : `<div class="discovery-grid">
              ${state.liveStreams.map((s) => `
                <article class="card-panel" style="gap:12px;">
                  <div style="display:flex;align-items:center;gap:10px;">
                    ${avatarHtml({ userId: s.userId, username: s.username }, 44)}
                    <div style="min-width:0;">
                      <div class="post-author-name">@${escapeHtml(s.username)}</div>
                      <div class="muted small">${formatCount(s.viewerCount || 0)} watching &middot; ${formatCount(s.likesCount || 0)} hearts</div>
                    </div>
                    <span class="badge-pill live">LIVE</span>
                  </div>
                  <div style="font-weight:700;">${escapeHtml(s.title || 'Live Broadcast')}</div>
                  <button type="button" class="btn-primary btn-sm" data-open-live="${escapeHtml(s.userId)}">Join Live Room</button>
                </article>
              `).join('')}
            </div>`
      }
    </div>
  `;
  document.getElementById('lobbyGoLiveBtn').onclick = () => openGoLiveModal();
  const emptyBtn = document.getElementById('lobbyEmptyLiveBtn');
  if (emptyBtn) emptyBtn.onclick = () => openGoLiveModal();
  mainStage.querySelectorAll('[data-open-live]').forEach((b) => {
    b.onclick = () => openWatchLiveModal(b.dataset.openLive);
  });
}

function renderMarketplace() {
  const categories = ['All', 'Collectibles', 'Fashion', 'Tech', 'Art', 'Home', 'Digital'];
  const filtered = state.marketCategory === 'All'
    ? state.products
    : state.products.filter((p) => (p.category || '').toLowerCase() === state.marketCategory.toLowerCase());

  mainStage.innerHTML = `
    <div class="stage-wide">
      <div class="section-header">
        <div>
          <h2>Momi Marketplace</h2>
          <div class="muted small">${state.products.length} listings &middot; ${state.orders.length} orders</div>
        </div>
        <div class="chip-row">
          <button type="button" class="btn-primary btn-sm" id="marketSellBtn">+ Sell Item</button>
          <button type="button" class="btn-outline btn-sm" id="marketOrdersBtn">Orders (${state.orders.length})</button>
        </div>
      </div>

      <div class="chip-row">
        ${categories.map((c) => `
          <button type="button" class="chip ${state.marketCategory === c ? 'active' : ''}" data-market-cat="${escapeHtml(c)}">${escapeHtml(c)}</button>
        `).join('')}
      </div>

      ${renderSponsoredPartnerBannerHtml('marketplace')}

      <div class="discovery-grid">
        ${
          filtered.length === 0
            ? '<div class="card-panel"><p class="muted" style="margin:0;">No marketplace listings in this category yet. Tap "+ Sell Item" to publish one!</p></div>'
            : filtered.map((prod) => `
              <article class="discovery-card" data-open-product="${escapeHtml(prod.id)}">
                <div style="position:relative;">
                  <img class="discovery-thumb" src="${resolveImageSrc({ imageUrl: prod.imageUrl, imageData: prod.imageData, imageKey: prod.imageKey })}" alt="${escapeHtml(prod.title)}" loading="lazy" />
                  ${prod.isFeatured ? '<span class="media-badge" style="background:var(--primary);">Featured</span>' : ''}
                </div>
                <div class="discovery-meta">
                  <div style="font-weight:700;">${escapeHtml(prod.title)}</div>
                  <div class="muted small">${escapeHtml(prod.storeName)} &middot; ${prod.freeShipping ? 'Free shipping' : 'Standard'}</div>
                  <div class="discovery-footer">
                    <strong style="color:var(--primary);font-size:1rem;">$${Number(prod.priceUsd || 0).toFixed(2)}</strong>
                    <span class="btn-outline btn-sm">${prod.sellerUid === state.user?.uid ? 'Manage' : 'View'}</span>
                  </div>
                </div>
              </article>
            `).join('')
        }
      </div>
    </div>
  `;

  mainStage.querySelectorAll('[data-market-cat]').forEach((b) => {
    b.onclick = () => {
      state.marketCategory = b.dataset.marketCat;
      renderMarketplace();
    };
  });
  document.getElementById('marketSellBtn').onclick = () => openSellProductModal();
  document.getElementById('marketOrdersBtn').onclick = () => openOrdersModal();
  mainStage.querySelectorAll('[data-open-product]').forEach((el) => {
    el.onclick = () => openProductDetailModal(el.dataset.openProduct);
  });
}

function renderMessages() {
  if (!state.activeThreadId && state.threads.length > 0) {
    selectThread(state.threads[0].id);
  }
  const activeThread = state.threads.find((t) => t.id === state.activeThreadId) || null;
  const uid = state.user?.uid;

  mainStage.innerHTML = `
    <div class="stage-wide">
      <div class="section-header">
        <h2>Direct Messages</h2>
        <span class="muted small">Real-time cross-device sync via Supabase</span>
      </div>
      <div class="messages-split">
        <div class="threads-pane">
          ${
            state.threads.length === 0
              ? '<p class="muted small" style="padding:18px;">No conversations yet. Open any creator profile to send a message.</p>'
              : state.threads.map((t) => {
                const otherUid = t.userAId === uid ? t.userBId : t.userAId;
                const otherName = t.userAId === uid ? t.userBUsername : t.userAUsername;
                const isUnread = t.unreadUid === uid && (t.unreadCount || 0) > 0;
                return `
                  <button type="button" class="thread-row ${t.id === state.activeThreadId ? 'active' : ''}" data-select-thread="${escapeHtml(t.id)}">
                    ${avatarHtml({ userId: otherUid, username: otherName }, 40)}
                    <div style="min-width:0;flex:1;">
                      <div style="font-weight:${isUnread ? '800' : '600'};font-size:0.9rem;">@${escapeHtml(otherName)}</div>
                      <div class="muted small" style="overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">${escapeHtml(t.lastMessage || 'Start chatting')}</div>
                    </div>
                    ${isUnread ? `<span class="badge-pill">${t.unreadCount}</span>` : ''}
                  </button>
                `;
              }).join('')
          }
        </div>

        <div class="chat-pane">
          ${
            !activeThread
              ? '<div style="margin:auto;padding:24px;" class="muted">Select a conversation on the left.</div>'
              : `
                <div class="chat-header">
                  ${avatarHtml({ userId: activeThread.userAId === uid ? activeThread.userBId : activeThread.userAId, username: activeThread.userAId === uid ? activeThread.userBUsername : activeThread.userAUsername }, 34)}
                  <strong>@${escapeHtml(activeThread.userAId === uid ? activeThread.userBUsername : activeThread.userAUsername)}</strong>
                </div>
                <div id="chatScrollBox" class="chat-messages">
                  ${state.activeMessages.map((m) => `
                    <div class="bubble ${m.senderUid === uid ? 'mine' : ''}">
                      ${m.messageType === 'IMAGE' && (m.mediaUrl || m.imageData) ? `<img src="${resolveImageSrc({ imageUrl: m.mediaUrl, imageData: m.imageData })}" alt="Attachment" />` : ''}
                      ${m.messageType === 'VIDEO' && (m.mediaUrl || m.videoData) ? `<video src="${escapeHtml(resolveVideoSrc(m))}" controls playsinline></video>` : ''}
                      ${m.messageType === 'AUDIO' && (m.mediaUrl || m.audioData) ? `<audio src="${escapeHtml(m.mediaUrl || `data:audio/mp4;base64,${m.audioData}`)}" controls style="max-width:240px;"></audio>` : ''}
                      <div>${escapeHtml(m.text)}</div>
                    </div>
                  `).join('')}
                </div>
                <form id="chatSendForm" class="chat-compose">
                  <label class="btn-outline btn-sm" for="chatPhotoInput" title="Attach Photo">Photo</label>
                  <input id="chatPhotoInput" type="file" accept="image/*" class="hidden" />
                  <input id="chatMsgInput" class="input" placeholder="Message..." />
                  <button type="submit" class="btn-primary btn-sm">Send</button>
                </form>
              `
          }
        </div>
      </div>
    </div>
  `;

  mainStage.querySelectorAll('[data-select-thread]').forEach((b) => {
    b.onclick = () => selectThread(b.dataset.selectThread);
  });

  const scrollBox = document.getElementById('chatScrollBox');
  if (scrollBox) scrollBox.scrollTop = scrollBox.scrollHeight;

  const form = document.getElementById('chatSendForm');
  if (form && activeThread) {
    form.onsubmit = async (e) => {
      e.preventDefault();
      const input = document.getElementById('chatMsgInput');
      const text = input.value.trim();
      if (!text) return;
      input.value = '';
      await sendChatMessage(activeThread, { text, messageType: 'TEXT' });
    };
    document.getElementById('chatPhotoInput').onchange = async (e) => {
      const file = e.target.files?.[0];
      if (!file) return;
      const msgId = makeClientId('msg');
      const uploadedUrl = await uploadWebFileToStorage(
        SUPABASE_WEB_CONFIG,
        'momi-media',
        `chat/${activeThread.id}/${msgId}.jpg`,
        file,
        file.type || 'image/jpeg'
      );
      const b64 = uploadedUrl ? null : await compressFileToBase64(file, 900, 620000);
      await sendChatMessage(activeThread, {
        msgId,
        text: 'Photo',
        messageType: 'IMAGE',
        mediaUrl: uploadedUrl,
        imageData: b64
      });
    };
  }
}

async function loadMessagesForThread(threadId) {
  if (!threadId) return;
  const rows = await selectSupabaseTable(
    SUPABASE_WEB_CONFIG,
    'chat_messages',
    `select=*&thread_id=eq.${encodeURIComponent(threadId)}&order=created_at.asc&limit=100`
  );
  state.activeMessages = rows.map(mapSupabaseChatMessageToWeb);
  if (state.nav === 'messages') renderMessages();
}

function selectThread(threadId) {
  state.activeThreadId = threadId;
  loadMessagesForThread(threadId);
  const t = state.threads.find((x) => x.id === threadId);
  if (t && t.unreadUid === state.user?.uid && (t.unreadCount || 0) > 0) {
    patchSupabaseRows(
      SUPABASE_WEB_CONFIG,
      'threads',
      `id=eq.${encodeURIComponent(threadId)}`,
      { unread_uid: '', unread_count: 0 }
    ).catch(() => undefined);
  }
}

async function sendChatMessage(thread, { msgId = makeClientId('msg'), text, messageType = 'TEXT', mediaUrl = null, imageData = null }) {
  if (!state.profile) return;
  const uid = state.user.uid;
  const otherUid = thread.userAId === uid ? thread.userBId : thread.userAId;
  await upsertSupabaseTable(
    SUPABASE_WEB_CONFIG,
    'chat_messages',
    [{
      id: msgId,
      thread_id: thread.id,
      participant_uids: thread.participantUids,
      sender_uid: uid,
      sender_username: state.profile.username,
      text: text.slice(0, 1000),
      message_type: messageType,
      media_url: mediaUrl,
      image_data: imageData
    }],
    '',
    globalThis.fetch,
    'id'
  );
  await patchSupabaseRows(
    SUPABASE_WEB_CONFIG,
    'threads',
    `id=eq.${encodeURIComponent(thread.id)}`,
    {
      last_message: text.slice(0, 500),
      last_sender_uid: uid,
      unread_uid: otherUid,
      unread_count: (thread.unreadCount || 0) + 1,
      time_label: 'Just now'
    }
  );
  await loadMessagesForThread(thread.id);
}

function renderNotifications() {
  const desktopPerm = 'Notification' in window ? Notification.permission : 'unsupported';
  const cfg = state.monetizationConfig || DEFAULT_MONETIZATION_CONFIG;
  const annId = cfg.announcementId || 'latest';
  const hasAnnouncement = Boolean(cfg.announcementTitle || cfg.announcementBody || cfg.releaseNotes);

  mainStage.innerHTML = `
    <div class="stage-column">
      <div class="section-header">
        <h2>Activity &amp; In-App Notices</h2>
        <div class="chip-row">
          <button type="button" class="btn-outline btn-sm" id="testChimeBtn">Test Sound</button>
          ${desktopPerm === 'default' ? '<button type="button" class="btn-primary btn-sm" id="enableDesktopNotifsBtn">Enable Desktop Popups</button>' : ''}
          <button type="button" class="btn-ghost btn-sm" id="markAllReadBtn">Mark All Read</button>
        </div>
      </div>

      ${hasAnnouncement ? `
        <div class="announcement-banner" role="region" aria-label="Pinned Momi update announcement">
          <div style="flex:1;min-width:200px;">
            <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap;">
              <span class="badge-pill">In-App Update &middot; v${escapeHtml(cfg.latestVersionName || APP_VERSION_NAME)}</span>
              <strong>${escapeHtml(cfg.announcementTitle || 'Momi Update Notice')}</strong>
            </div>
            ${cfg.announcementBody ? `<div class="small" style="margin-top:6px;line-height:1.45;">${escapeHtml(cfg.announcementBody)}</div>` : ''}
            ${cfg.releaseNotes ? `<div class="muted small" style="margin-top:4px;">Release notes: ${escapeHtml(cfg.releaseNotes)}</div>` : ''}
          </div>
        </div>
      ` : ''}

      ${
        state.notifications.length === 0
          ? '<div class="card-panel"><p class="muted" style="margin:0;">No activity notifications yet. Likes, comments, follows, and gift alerts appear here.</p></div>'
          : state.notifications.map((n) => `
            <div class="card-panel" style="flex-direction:row;align-items:center;justify-content:space-between;${n.isUnread ? 'border-color:var(--primary);' : ''}">
              <div style="display:flex;align-items:center;gap:12px;">
                ${avatarHtml({ userId: n.actorUid, username: n.actorUsername }, 40)}
                <div>
                  <div><strong>@${escapeHtml(n.actorUsername)}</strong> ${escapeHtml(n.message)}</div>
                  <div class="muted small">${escapeHtml(n.timeLabel || n.section || 'Recent')}</div>
                </div>
              </div>
              <button type="button" class="btn-outline btn-sm" data-open-user="${escapeHtml(n.actorUid)}">View</button>
            </div>
          `).join('')
      }
    </div>
  `;

  document.getElementById('testChimeBtn').onclick = () => {
    playMomiAlertChime();
    showToast('Momi alert chime played');
  };
  const permBtn = document.getElementById('enableDesktopNotifsBtn');
  if (permBtn) {
    permBtn.onclick = async () => {
      const res = await Notification.requestPermission();
      showToast(res === 'granted' ? 'Desktop popup notifications enabled' : 'Desktop notifications dismissed');
      renderNotifications();
    };
  }
  document.getElementById('markAllReadBtn').onclick = async () => {
    if (hasAnnouncement) {
      state.dismissedAnnouncementId = annId;
      localStorage.setItem('momi_dismissed_ann', annId);
    }
    await patchSupabaseRows(
      SUPABASE_WEB_CONFIG,
      'notifications',
      `user_id=eq.${encodeURIComponent(state.user.uid)}&is_unread=eq.true`,
      { is_unread: false }
    );
    state.notifications = state.notifications.map((n) => ({ ...n, isUnread: false }));
    updateBadges();
    renderNotifications();
    showToast('Marked notifications as read');
  };
  bindUserAndFollowButtons(mainStage);
}

function renderWallet() {
  const w = state.wallet || { balance: 0, totalEarned: 0, totalSpent: 0, totalPurchased: 0, accountTier: 'STANDARD', verificationTier: 'NONE', unlockedPerks: [] };
  const consentOn = Boolean(state.profile?.partnerConsent);
  const myPosts = state.posts.filter((p) => p.userId === state.user?.uid && !p.id.startsWith('showcase_')).slice(0, 6);

  mainStage.innerHTML = `
    <div class="stage-column">
      <div class="section-header">
        <h2>Wallet, Boosts &amp; Account Perks</h2>
      </div>
      <div class="card-panel" style="background:var(--gradient);color:#fff;border:none;">
        <div class="small" style="opacity:0.9;">Available Balance</div>
        <div style="font-size:2.4rem;font-weight:800;">${formatCount(w.balance)} Credits</div>
        <div style="display:flex;gap:18px;flex-wrap:wrap;font-size:0.86rem;opacity:0.92;">
          <span>Earned: ${w.totalEarned || 0}</span>
          <span>Purchased: ${w.totalPurchased || 0}</span>
          <span>Tier: ${escapeHtml(w.accountTier)}</span>
          <span>Verification: ${escapeHtml(w.verificationTier)}</span>
        </div>
        <div class="small" style="opacity:0.9;margin-top:4px;">Credits purchased in the Momi Android app sync here immediately via Supabase.</div>
      </div>

      <div class="card-panel">
        <div class="consent-row">
          <div>
            <strong>Creator Growth &amp; Owner Perks Consent</strong>
            <div class="muted small">Opt in to allow Momi's owner to grant bonus credits, recommended post spotlights, and exclusive perks.</div>
          </div>
          <label style="display:flex;align-items:center;gap:8px;font-weight:700;cursor:pointer;">
            <input id="walletConsentToggle" type="checkbox" ${consentOn ? 'checked' : ''} />
            <span>${consentOn ? 'Opted In' : 'Off'}</span>
          </label>
        </div>
      </div>

      <div class="card-panel">
        <h3 style="margin:0;">Boost Your Posts with Credits</h3>
        <div class="muted small">Elevate your photos &amp; clips higher in For You &amp; Search discovery.</div>
        ${
          myPosts.length === 0
            ? '<p class="muted small" style="margin:0;">Publish a post first, then boost it right here or from Home Feed.</p>'
            : myPosts.map((p) => {
              const badge = boostBadgeLabelForPost(p);
              return `
                <div style="display:flex;align-items:center;justify-content:space-between;gap:12px;padding:10px 0;border-bottom:1px solid var(--border);">
                  <div style="min-width:0;flex:1;">
                    <div style="font-weight:700;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">${escapeHtml(p.caption || 'Momi Post')}</div>
                    <div class="muted small">${badge ? escapeHtml(badge) : 'Standard Feed Placement'} &middot; ${formatCount(p.likesCount)} likes</div>
                  </div>
                  <button type="button" class="btn-sm btn-primary" data-boost-post="${escapeHtml(p.id)}">
                    ${p.isBoosted ? 'Upgrade Boost' : 'Boost'}
                  </button>
                </div>
              `;
            }).join('')
        }
      </div>

      <div class="card-panel">
        <h3 style="margin:0;">Unlock Account Perks with Credits</h3>
        ${WEB_PERK_CATALOG.map((perk) => {
          const unlocked = (w.unlockedPerks || []).includes(perk.key);
          return `
            <div style="display:flex;align-items:center;justify-content:space-between;gap:12px;padding:10px 0;border-bottom:1px solid var(--border);">
              <div>
                <div style="font-weight:700;">${escapeHtml(perk.name)}</div>
                <div class="muted small">${escapeHtml(perk.desc)}</div>
              </div>
              <button type="button" class="btn-sm ${unlocked ? 'btn-outline' : 'btn-primary'}" data-unlock-perk="${perk.key}" ${unlocked ? 'disabled' : ''}>
                ${unlocked ? 'Unlocked' : `${perk.cost} Credits`}
              </button>
            </div>
          `;
        }).join('')}
      </div>

      <div class="card-panel">
        <h3 style="margin:0;">Recent Credit Activity</h3>
        ${
          state.transactions.length === 0
            ? '<p class="muted small" style="margin:0;">No credit transactions yet.</p>'
            : state.transactions.slice(0, 12).map((tx) => {
              const isCredit = tx.type === 'TOP_UP' || tx.type === 'OWNER_GRANT' || (tx.type === 'GIFT' && tx.recipientUid === state.user?.uid);
              const label = tx.type === 'POST_BOOST'
                ? 'Post Boost Activated'
                : tx.type === 'OWNER_GRANT'
                  ? `Owner Grant${tx.perkKey ? ` (${tx.perkKey})` : ''}`
                  : tx.type === 'PERK_UNLOCK'
                    ? `Perk Unlocked (${tx.perkKey || 'Perk'})`
                    : `${tx.type} &middot; @${escapeHtml(tx.senderUsername)} &rarr; @${escapeHtml(tx.recipientUsername)}`;
              return `
                <div style="display:flex;justify-content:space-between;padding:8px 0;border-bottom:1px solid var(--border);font-size:0.88rem;">
                  <span>${label}</span>
                  <strong>${isCredit ? '+' : '-'}${tx.creditsAmount} cr</strong>
                </div>
              `;
            }).join('')
        }
      </div>
    </div>
  `;

  document.getElementById('walletConsentToggle').onchange = (e) => updatePartnerConsent(e.target.checked);
  mainStage.querySelectorAll('[data-boost-post]').forEach((b) => {
    b.onclick = () => {
      const target = state.posts.find((p) => p.id === b.dataset.boostPost);
      if (target) openPostBoostModal(target);
    };
  });

  mainStage.querySelectorAll('[data-unlock-perk]').forEach((b) => {
    b.onclick = async () => {
      const perk = WEB_PERK_CATALOG.find((p) => p.key === b.dataset.unlockPerk);
      if (!perk) return;
      if ((w.balance || 0) < perk.cost) {
        showToast('Not enough credits yet. Top up credits in the Momi Android app.');
        return;
      }
      const txId = makeClientId('perk');
      const nextPerks = [...new Set([...(w.unlockedPerks || []), perk.key])];
      const nextTier = tierForTotalPurchased(w.totalPurchased || 0, nextPerks);
      const nextVerif = verificationTierForPerks(w.verificationTier || 'NONE', nextPerks);
      await patchSupabaseRows(SUPABASE_WEB_CONFIG, 'wallets', `user_id=eq.${encodeURIComponent(state.user.uid)}`, {
        balance: w.balance - perk.cost,
        total_spent: (w.totalSpent || 0) + perk.cost,
        account_tier: nextTier,
        verification_tier: nextVerif,
        unlocked_perks: nextPerks,
        last_tx_id: txId
      });
      await upsertSupabaseTable(SUPABASE_WEB_CONFIG, 'credit_transactions', [{
        tx_id: txId,
        type: 'PERK_UNLOCK',
        sender_uid: state.user.uid,
        sender_username: state.profile.username,
        recipient_uid: state.user.uid,
        recipient_username: state.profile.username,
        credits_amount: perk.cost,
        price_usd: 0,
        payment_method: 'WALLET_CREDITS',
        perk_key: perk.key,
        recipient_claimed: true,
        status: 'COMPLETED'
      }], '', globalThis.fetch, 'tx_id');
      await refreshAllFromSupabase({ showErrors: false });
      showToast(`Unlocked ${perk.name}!`);
    };
  });
}

function renderProfile() {
  const targetUid = state.viewedProfileUid || state.user?.uid;
  const isOwn = Boolean(state.user?.uid) && targetUid === state.user?.uid;
  if (isOwn && !state.profile && state.user) {
    state.profile = buildFallbackProfile(state.user);
  }
  let profile = isOwn ? state.profile : state.creators.find((c) => c.userId === targetUid);
  if (!profile && targetUid) {
    const postWithAuthor = state.posts.find(
      (p) => p.userId === targetUid || (p.authorUsername && p.authorUsername.toLowerCase() === targetUid.toLowerCase())
    );
    if (postWithAuthor) {
      profile = {
        userId: postWithAuthor.userId || targetUid,
        username: postWithAuthor.authorUsername || 'creator',
        displayName: postWithAuthor.authorDisplayName || postWithAuthor.authorUsername || 'Creator',
        avatarKey: postWithAuthor.authorAvatarKey || 'avatar_aria',
        avatarUrl: null,
        avatarData: null,
        bio: '',
        website: '',
        category: postWithAuthor.category || 'Creator',
        followersCount: 0,
        followingCount: 0,
        isVerified: Boolean(postWithAuthor.authorVerified)
      };
    }
  }

  if (!profile) {
    mainStage.innerHTML = `
      <div class="stage-column">
        <div class="card-panel" style="text-align:center;padding:32px 20px;">
          <h3 style="margin:0 0 8px;">Creator Profile</h3>
          <p class="muted" style="margin-bottom:18px;">This creator profile is still syncing.</p>
          <div class="chip-row" style="justify-content:center;">
            <button type="button" class="btn-primary" id="goToMyProfileBtn">My Profile</button>
            <button type="button" class="btn-outline" id="profileBackToFeedBtn">Home Feed</button>
          </div>
        </div>
      </div>
    `;
    document.getElementById('goToMyProfileBtn')?.addEventListener('click', () => { state.viewedProfileUid = state.user?.uid || null; renderProfile(); });
    document.getElementById('profileBackToFeedBtn')?.addEventListener('click', () => navigateTo('home'));
    return;
  }

  const userPosts = state.posts.filter((p) => p.userId === profile.userId);
  const savedPosts = isOwn ? state.posts.filter((p) => state.bookmarkIds.has(p.id)) : [];
  const displayed = state.profileTab === 'saved' && isOwn ? savedPosts : userPosts;
  const isFollowing = state.followingUids.has(profile.userId);

  const ownerAdmin = isOwnerAdmin();
  const showcasePostCount = state.posts.filter((p) => p.id.startsWith('showcase_')).length;
  const showcaseStoryCount = state.stories.filter((s) => s.id.startsWith('showcase_')).length;
  const cfg = state.monetizationConfig || DEFAULT_MONETIZATION_CONFIG;
  const consentingCreators = state.creators.filter((c) => Boolean(c.partnerConsent));
  const recommendCandidates = state.posts.filter((p) => !p.id.startsWith('showcase_')).slice(0, 12);

  mainStage.innerHTML = `
    <div class="stage-wide">
      <div class="card-panel">
        <div style="display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:16px;">
          <div style="display:flex;align-items:center;gap:18px;">
            ${avatarHtml(profile, 76)}
            <div>
              <div class="post-author-name" style="font-size:1.2rem;">
                @${escapeHtml(profile.username)}
                ${profile.isVerified ? '<span class="verified-dot">&#10003;</span>' : ''}
              </div>
              <div style="font-weight:700;">${escapeHtml(profile.displayName)}</div>
              <div class="muted small">${escapeHtml(profile.category || 'Creator')}</div>
              <div style="display:flex;gap:16px;margin-top:8px;font-size:0.9rem;">
                <span><strong>${userPosts.length}</strong> posts</span>
                <span><strong>${profile.followersCount || 0}</strong> followers</span>
                <span><strong>${profile.followingCount || 0}</strong> following</span>
              </div>
            </div>
          </div>

          <div class="chip-row">
            <button type="button" class="btn-outline" id="shareProfileBtn">Share</button>
            ${
              isOwn
                ? '<button type="button" class="btn-outline" id="editProfileBtn">Edit Profile</button><button type="button" class="btn-primary" id="profileCreateBtn">New Post</button>'
                : `<button type="button" class="${isFollowing ? 'btn-outline' : 'btn-primary'}" data-follow-uid="${escapeHtml(profile.userId)}" data-follow-name="${escapeHtml(profile.username)}">${isFollowing ? 'Following' : 'Follow'}</button>
                   <button type="button" class="btn-outline" id="messageCreatorBtn">Message</button>`
            }
          </div>
        </div>
        ${profile.bio ? `<p style="margin:6px 0 0;line-height:1.45;">${escapeHtml(profile.bio)}</p>` : ''}
        ${profile.website ? `<a href="${escapeHtml(profile.website)}" target="_blank" rel="noopener noreferrer" style="color:var(--primary);font-size:0.88rem;">${escapeHtml(profile.website)}</a>` : ''}
      </div>

      ${isOwn ? `
        <div class="card-panel">
          <div class="consent-row">
            <div>
              <strong>Partner Boost &amp; Owner Perks Consent</strong>
              <div class="muted small">Allow Momi's owner to award bonus credits, VIP perks, and verified status when unlocked.</div>
            </div>
            <label style="display:flex;align-items:center;gap:8px;font-weight:700;cursor:pointer;">
              <input id="profileConsentToggle" type="checkbox" ${profile.partnerConsent ? 'checked' : ''} />
              <span>${profile.partnerConsent ? 'Opted In' : 'Off'}</span>
            </label>
          </div>
        </div>
      ` : ''}

      ${isOwn && ownerAdmin ? `
        <div class="card-panel" style="border-color:color-mix(in srgb, var(--primary) 45%, var(--border));">
          <div style="display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:12px;">
            <div>
              <strong>Owner Admin Account (${escapeHtml(ADMIN_OWNER_EMAIL)})</strong>
              <div class="muted small">Authorize Showcase Starters, publish posts/videos as starters, add likes/views, send In-App Announcements, and manage Monetization.</div>
            </div>
            <button type="button" class="owner-pill-btn" id="openFullOwnerConsoleBtn">&#9881; Open Owner Console</button>
          </div>
        </div>
      ` : ''}

      ${isOwn ? `
        <div class="chip-row">
          <button type="button" class="chip ${state.profileTab === 'posts' ? 'active' : ''}" id="profTabPosts">Posts (${userPosts.length})</button>
          <button type="button" class="chip ${state.profileTab === 'saved' ? 'active' : ''}" id="profTabSaved">Saved (${savedPosts.length})</button>
        </div>
      ` : ''}

      <div class="profile-grid-3">
        ${displayed.map((p) => `
          <button type="button" class="profile-tile" data-open-post="${escapeHtml(p.id)}">
            <img src="${resolveImageSrc(p)}" alt="${escapeHtml(p.caption || '')}" style="filter:${filterCssForTag(p.filterTag)};" loading="lazy" />
          </button>
        `).join('')}
      </div>
    </div>
  `;

  document.getElementById('shareProfileBtn').onclick = () => shareProfileExternally(profile);
  document.getElementById('profileConsentToggle')?.addEventListener('change', (e) => updatePartnerConsent(e.target.checked));
  document.getElementById('openFullOwnerConsoleBtn')?.addEventListener('click', () => navigateTo('admin'));
  document.getElementById('editProfileBtn')?.addEventListener('click', () => openEditProfileModal());
  document.getElementById('profileCreateBtn')?.addEventListener('click', () => navigateTo('create'));
  document.getElementById('messageCreatorBtn')?.addEventListener('click', () => openOrCreateDirectThread(profile));
  document.getElementById('profTabPosts')?.addEventListener('click', () => { state.profileTab = 'posts'; renderProfile(); });
  document.getElementById('profTabSaved')?.addEventListener('click', () => { state.profileTab = 'saved'; renderProfile(); });
  bindFeedInteractions(mainStage);
}

async function publishShowcaseBurst(uid, { silent = false } = {}) {
  if (!uid || state.showcaseBusy) return;
  state.showcaseBusy = true;
  if (!silent && (state.nav === 'profile' || state.nav === 'admin')) renderMainStage();
  try {
    const stamp = Date.now();
    const authAccounts = state.showcaseAccounts.filter((a) => a.isAuthorized);
    const pool = (authAccounts.length > 0 ? authAccounts : SHOWCASE_PERSONAS).map((acct) => {
      const base = SHOWCASE_PERSONAS.find((p) => p.username === acct.username) || SHOWCASE_PERSONAS[0];
      return {
        ...base,
        username: acct.username,
        displayName: acct.displayName || base.displayName,
        avatarKey: acct.avatarKey || base.avatarKey,
        category: acct.category || base.category,
        location: acct.location || base.location
      };
    });
    const batchPersonas = [...pool].sort(() => Math.random() - 0.5).slice(0, 4);
    const postRows = [];
    const commentRows = [];
    const storyRows = [];
    batchPersonas.forEach((persona, idx) => {
      const postId = `showcase_${persona.username.replace(/[^a-z0-9]/gi, '_')}_${stamp}_${idx}`;
      const commenter = pool[(idx + 2) % pool.length];
      const commentText = SHOWCASE_COMMENTS[(idx + Math.floor(stamp / 1000)) % SHOWCASE_COMMENTS.length];
      postRows.push({
        id: postId,
        user_id: uid,
        author_username: persona.username,
        author_display_name: persona.displayName,
        author_avatar_key: persona.avatarKey,
        author_verified: true,
        location: persona.location,
        image_key: persona.imageKey,
        media_type: 'IMAGE',
        filter_tag: persona.filterTag,
        aspect_ratio: persona.aspectRatio,
        hashtags: persona.hashtags,
        tagged_usernames: extractMentions(persona.caption),
        caption: persona.caption,
        likes_count: 14 + idx * 7,
        views_count: 95 + idx * 32,
        comments_count: 1,
        category: persona.category,
        is_reel: idx % 2 === 0
      });
      commentRows.push({
        id: `showcase_cmt_${stamp}_${idx}`,
        post_id: postId,
        user_id: uid,
        author_username: commenter.username,
        author_avatar_key: commenter.avatarKey,
        text: commentText,
        likes_count: 2
      });
      if (idx < 2) {
        storyRows.push({
          id: `showcase_story_${stamp}_${idx}`,
          user_id: uid,
          username: persona.username,
          avatar_key: persona.avatarKey,
          image_key: persona.imageKey,
          media_type: 'IMAGE',
          caption: persona.location
        });
      }
    });
    await upsertSupabaseTable(SUPABASE_WEB_CONFIG, 'posts', postRows, '', globalThis.fetch, 'id');
    await upsertSupabaseTable(SUPABASE_WEB_CONFIG, 'comments', commentRows, '', globalThis.fetch, 'id');
    if (storyRows.length > 0) {
      await upsertSupabaseTable(SUPABASE_WEB_CONFIG, 'stories', storyRows, '', globalThis.fetch, 'id');
    }
    await refreshAllFromSupabase({ showErrors: false });
    if (!silent) showToast(`Published ${postRows.length} starter showcase posts & stories!`);
  } catch (err) {
    if (!silent) showToast(err?.message || 'Could not publish showcase burst');
  } finally {
    state.showcaseBusy = false;
    if (!silent && (state.nav === 'profile' || state.nav === 'admin')) renderMainStage();
  }
}

async function engagePostsWithShowcase(uid) {
  if (!uid || state.showcaseBusy) return;
  state.showcaseBusy = true;
  if (state.nav === 'profile' || state.nav === 'admin') renderMainStage();
  try {
    const targets = state.posts.slice(0, 4);
    if (targets.length === 0) {
      showToast('No posts available yet — publish starter posts first.');
      return;
    }
    const stamp = Date.now();
    const commentRows = targets.map((p, idx) => {
      const persona = SHOWCASE_PERSONAS[(idx + Math.floor(stamp / 1000)) % SHOWCASE_PERSONAS.length];
      const commentText = SHOWCASE_COMMENTS[(idx + 1) % SHOWCASE_COMMENTS.length];
      return {
        id: `showcase_cmt_${stamp}_${idx}`,
        post_id: p.id,
        user_id: uid,
        author_username: persona.username,
        author_avatar_key: persona.avatarKey,
        text: commentText,
        likes_count: 1
      };
    });
    await upsertSupabaseTable(SUPABASE_WEB_CONFIG, 'comments', commentRows, '', globalThis.fetch, 'id');
    await Promise.all(targets.map((p) => {
      const nextLikes = (p.likesCount || 0) + 1;
      const nextViews = (p.viewsCount || 0) + 12;
      state.pendingMutations.minLikes.set(p.id, { value: nextLikes, expiresAt: Date.now() + 12000 });
      state.pendingMutations.minViews.set(p.id, { value: nextViews, expiresAt: Date.now() + 12000 });
      return patchSupabaseRows(
        SUPABASE_WEB_CONFIG,
        'posts',
        `id=eq.${encodeURIComponent(p.id)}`,
        { likes_count: nextLikes, views_count: nextViews, comments_count: (p.commentsCount || 0) + 1 }
      );
    }));
    await refreshAllFromSupabase({ showErrors: false });
    showToast(`Added starter likes, views & comments to ${targets.length} posts!`);
  } catch (err) {
    showToast(err?.message || 'Could not add showcase engagement');
  } finally {
    state.showcaseBusy = false;
    if (state.nav === 'profile' || state.nav === 'admin') renderMainStage();
  }
}

async function removeAllShowcaseContent() {
  if (!state.user || state.showcaseBusy) return;
  if (!window.confirm('Remove all starter showcase posts and stories from Momi?')) return;
  state.showcaseBusy = true;
  if (state.nav === 'profile' || state.nav === 'admin') renderMainStage();
  try {
    await deleteSupabaseRows(SUPABASE_WEB_CONFIG, 'posts', 'id=like.showcase_%25');
    await deleteSupabaseRows(SUPABASE_WEB_CONFIG, 'stories', 'id=like.showcase_%25');
    await refreshAllFromSupabase({ showErrors: false });
    showToast('Removed all starter showcase posts and stories.');
  } catch (err) {
    showToast(err?.message || 'Failed removing starter showcase content');
  } finally {
    state.showcaseBusy = false;
    if (state.nav === 'profile' || state.nav === 'admin') renderMainStage();
  }
}

// Modals & Shared Actions
function bindUserAndFollowButtons(container) {
  container.querySelectorAll('[data-open-user]').forEach((b) => {
    b.onclick = (e) => {
      e.stopPropagation();
      state.viewedProfileUid = b.dataset.openUser;
      state.profileTab = 'posts';
      navigateTo('profile', { keepViewedUid: true });
    };
  });
  container.querySelectorAll('[data-follow-uid]').forEach((b) => {
    b.onclick = async (e) => {
      e.stopPropagation();
      const targetUid = b.dataset.followUid;
      const targetUsername = b.dataset.followName || 'creator';
      await toggleFollow(targetUid, targetUsername);
    };
  });
}

function bindFeedInteractions(container) {
  bindUserAndFollowButtons(container);

  container.querySelectorAll('[data-stop-open]').forEach((el) => {
    el.onclick = (e) => e.stopPropagation();
  });
  container.querySelectorAll('[data-open-post]').forEach((el) => {
    el.onclick = (e) => {
      if (e.target.closest('video')) return;
      openPostDetailModal(el.dataset.openPost);
    };
  });
  container.querySelectorAll('[data-open-story]').forEach((el) => {
    el.onclick = () => openStoryModal(el.dataset.openStory);
  });
  container.querySelectorAll('[data-open-live]').forEach((el) => {
    el.onclick = () => openWatchLiveModal(el.dataset.openLive);
  });
  container.querySelectorAll('[data-like-post]').forEach((b) => {
    b.onclick = (e) => {
      e.stopPropagation();
      togglePostLike(b.dataset.likePost);
    };
  });
  container.querySelectorAll('[data-open-engagers]').forEach((b) => {
    b.onclick = (e) => {
      e.stopPropagation();
      const [postId, tabStr] = String(b.dataset.openEngagers || '').split(':');
      if (postId) openPostEngagersModal(postId, Number(tabStr || 0));
    };
  });
  container.querySelectorAll('[data-bookmark-post]').forEach((b) => {
    b.onclick = (e) => {
      e.stopPropagation();
      toggleBookmark(b.dataset.bookmarkPost);
    };
  });
  container.querySelectorAll('[data-boost-post]').forEach((b) => {
    b.onclick = (e) => {
      e.stopPropagation();
      const post = state.posts.find((p) => p.id === b.dataset.boostPost);
      if (post) openPostBoostModal(post);
    };
  });
  container.querySelectorAll('[data-admin-recommend]').forEach((b) => {
    b.onclick = (e) => {
      e.stopPropagation();
      const post = state.posts.find((p) => p.id === b.dataset.adminRecommend);
      if (post) adminToggleRecommendPost(post);
    };
  });
  container.querySelectorAll('[data-open-community]').forEach((el) => {
    el.onclick = (e) => {
      e.stopPropagation();
      const id = el.dataset.openCommunity;
      const type = el.dataset.communityType || 'PAGE';
      if (id) {
        state.activeCommunityFilter = { id, type, name: el.dataset.communityName || 'Community' };
        state.communitiesTab = type === 'GROUP' ? 'groups' : 'pages';
        navigateTo('communities');
      }
    };
  });
  container.querySelectorAll('[data-delete-post]').forEach((b) => {
    b.onclick = async (e) => {
      e.stopPropagation();
      if (!window.confirm('Delete this post permanently?')) return;
      const pid = b.dataset.deletePost;
      state.pendingMutations.deletedPostIds.set(pid, Date.now() + 12000);
      state.posts = state.posts.filter((p) => p.id !== pid);
      renderMainStage();
      await deleteSupabaseRows(SUPABASE_WEB_CONFIG, 'posts', `id=eq.${encodeURIComponent(pid)}`);
      showToast('Post deleted');
    };
  });
  container.querySelectorAll('[data-share-post]').forEach((b) => {
    b.onclick = async (e) => {
      e.stopPropagation();
      const post = state.posts.find((p) => p.id === b.dataset.sharePost);
      await sharePostExternally(post || { id: b.dataset.sharePost, authorUsername: 'momi', caption: '' });
    };
  });
  container.querySelectorAll('[data-slide]').forEach((b) => {
    b.onclick = (e) => {
      e.stopPropagation();
      const [postId, deltaStr] = b.dataset.slide.split(':');
      const post = state.posts.find((p) => p.id === postId);
      const count = post ? getPostFrames(post).length : 1;
      const cur = state.carouselIndex[postId] || 0;
      state.carouselIndex[postId] = (cur + Number(deltaStr) + count) % count;
      renderMainStage();
    };
  });
  container.querySelectorAll('[data-hashtag]').forEach((b) => {
    b.onclick = (e) => {
      e.stopPropagation();
      closeModal();
      state.exploreCategory = 'For You';
      state.exploreQuery = `#${b.dataset.hashtag}`;
      navigateTo('explore');
    };
  });
  container.querySelectorAll('[data-mention]').forEach((b) => {
    b.onclick = (e) => {
      e.stopPropagation();
      closeModal();
      const target = b.dataset.mention.toLowerCase();
      const creator = state.creators.find((c) => c.username.toLowerCase() === target);
      if (creator) {
        state.viewedProfileUid = creator.userId;
        navigateTo('profile', { keepViewedUid: true });
      } else {
        state.exploreQuery = `@${target}`;
        navigateTo('explore');
      }
    };
  });
}

async function togglePostLike(postId) {
  const post = state.posts.find((p) => p.id === postId);
  if (!post || !state.profile) return;
  const uid = state.user.uid;
  const already = state.likedIds.has(postId);
  const nextLikes = Math.max(0, (post.likesCount || 0) + (already ? -1 : 1));
  post.likesCount = nextLikes;
  if (already) {
    state.likedIds.delete(postId);
    state.postLikersByPostId.get(postId)?.delete(uid);
  } else {
    state.likedIds.add(postId);
    if (!state.postLikersByPostId.has(postId)) state.postLikersByPostId.set(postId, new Set());
    state.postLikersByPostId.get(postId).add(uid);
  }
  state.pendingMutations.likes.set(postId, { value: !already, expiresAt: Date.now() + 12000 });
  state.pendingMutations.minLikes.set(postId, { value: nextLikes, expiresAt: Date.now() + 12000 });
  renderMainStage();

  if (already) {
    await deleteSupabaseRows(
      SUPABASE_WEB_CONFIG,
      'user_interactions',
      `user_id=eq.${encodeURIComponent(uid)}&target_type=eq.LIKED_POST&target_id=eq.${encodeURIComponent(postId)}`
    );
  } else {
    await upsertSupabaseTable(
      SUPABASE_WEB_CONFIG,
      'user_interactions',
      [{ user_id: uid, target_type: 'LIKED_POST', target_id: postId }],
      '',
      globalThis.fetch,
      'user_id,target_type,target_id'
    );
    if (post.userId && post.userId !== uid) {
      await upsertSupabaseTable(SUPABASE_WEB_CONFIG, 'notifications', [{
        id: makeClientId('notif'),
        user_id: post.userId,
        actor_uid: uid,
        actor_username: state.profile.username,
        actor_avatar_key: state.profile.avatarKey || 'avatar_aria',
        message: 'liked your post.',
        time_label: 'Just now',
        section: 'Today',
        preview_image_key: post.imageKey || 'img_post_coastal',
        is_follow_action: false,
        is_following_back: false,
        is_unread: true
      }], '', globalThis.fetch, 'id');
    }
  }
  await patchSupabaseRows(SUPABASE_WEB_CONFIG, 'posts', `id=eq.${encodeURIComponent(postId)}`, { likes_count: nextLikes });
}

async function toggleBookmark(postId) {
  const uid = state.user.uid;
  const already = state.bookmarkIds.has(postId);
  state.pendingMutations.bookmarks.set(postId, { value: !already, expiresAt: Date.now() + 12000 });
  if (already) {
    state.bookmarkIds.delete(postId);
    renderMainStage();
    await deleteSupabaseRows(
      SUPABASE_WEB_CONFIG,
      'user_interactions',
      `user_id=eq.${encodeURIComponent(uid)}&target_type=eq.BOOKMARK&target_id=eq.${encodeURIComponent(postId)}`
    );
    showToast('Removed from Saved');
  } else {
    state.bookmarkIds.add(postId);
    renderMainStage();
    await upsertSupabaseTable(
      SUPABASE_WEB_CONFIG,
      'user_interactions',
      [{ user_id: uid, target_type: 'BOOKMARK', target_id: postId }],
      '',
      globalThis.fetch,
      'user_id,target_type,target_id'
    );
    showToast('Saved to your profile');
  }
}

async function toggleFollow(targetUid, targetUsername) {
  const uid = state.user.uid;
  if (!state.profile || !targetUid || targetUid === uid) return;
  const followId = `${uid}_${targetUid}`;
  const isFollowing = state.followingUids.has(targetUid);
  state.pendingMutations.follows.set(targetUid, { value: !isFollowing, expiresAt: Date.now() + 12000 });
  if (isFollowing) {
    state.followingUids.delete(targetUid);
    renderAll();
    await deleteSupabaseRows(SUPABASE_WEB_CONFIG, 'follows', `id=eq.${encodeURIComponent(followId)}`);
  } else {
    state.followingUids.add(targetUid);
    renderAll();
    await upsertSupabaseTable(SUPABASE_WEB_CONFIG, 'follows', [{
      id: followId,
      follower_uid: uid,
      target_uid: targetUid,
      follower_username: state.profile.username,
      target_username: targetUsername || 'creator'
    }], '', globalThis.fetch, 'id');
    await upsertSupabaseTable(SUPABASE_WEB_CONFIG, 'notifications', [{
      id: makeClientId('notif'),
      user_id: targetUid,
      actor_uid: uid,
      actor_username: state.profile.username,
      actor_avatar_key: state.profile.avatarKey || 'avatar_aria',
      message: 'started following you.',
      time_label: 'Just now',
      section: 'Today',
      is_follow_action: true,
      is_following_back: false,
      is_unread: true
    }], '', globalThis.fetch, 'id');
  }
}

async function openOrCreateDirectThread(otherProfile) {
  if (!state.profile || !otherProfile || otherProfile.userId === state.user.uid) return;
  const pair = [state.profile, otherProfile].sort((a, b) => a.userId.localeCompare(b.userId));
  const threadId = `${pair[0].userId}_${pair[1].userId}`;
  const existing = state.threads.find((t) => t.id === threadId);
  if (!existing) {
    await upsertSupabaseTable(SUPABASE_WEB_CONFIG, 'threads', [{
      id: threadId,
      participant_uids: [pair[0].userId, pair[1].userId],
      user_a_id: pair[0].userId,
      user_a_username: pair[0].username,
      user_a_display_name: pair[0].displayName,
      user_a_avatar_key: pair[0].avatarKey || 'avatar_aria',
      user_b_id: pair[1].userId,
      user_b_username: pair[1].username,
      user_b_display_name: pair[1].displayName,
      user_b_avatar_key: pair[1].avatarKey || 'avatar_aria',
      last_message: '',
      last_sender_uid: '',
      unread_uid: '',
      time_label: 'Now',
      unread_count: 0,
      is_online: true
    }], '', globalThis.fetch, 'id');
    await refreshAllFromSupabase({ showErrors: false });
  }
  selectThread(threadId);
  navigateTo('messages');
}

async function resolveVideoForPost(post) {
  const direct = resolveVideoSrc(post);
  if (direct) return direct;
  if (state.videoCache[post.id]) return state.videoCache[post.id];
  if (post.videoData) {
    const url = await assembleVideoChunksToObjectUrl([post.videoData]);
    state.videoCache[post.id] = url;
    return url;
  }
  return null;
}

async function shareExternalText({ title, text, url, toastMessage }) {
  if (navigator.share) {
    try {
      await navigator.share({ title, text, url });
      return;
    } catch (err) {
      if (err?.name === 'AbortError') return;
    }
  }
  await navigator.clipboard?.writeText(url).catch(() => undefined);
  showToast(toastMessage || `Copied link: ${url}`);
}

async function sharePostExternally(post) {
  const shareUrl = buildPostShareUrl(post.id, WEB_APP_URL);
  const captionSnippet = String(post.caption || '').trim().replace(/\s+/g, ' ').slice(0, 120);
  const body = captionSnippet
    ? `@${post.authorUsername || 'momi'} on Momi: "${captionSnippet}"`
    : `See @${post.authorUsername || 'momi'}'s post on Momi`;
  await shareExternalText({
    title: `Momi post by @${post.authorUsername || 'momi'}`,
    text: `${body}\n\nOpen post: ${shareUrl}\nGet Momi for Android: ${ANDROID_APK_URL}`,
    url: shareUrl,
    toastMessage: `Post link copied! (${shareUrl})`
  });
}

async function shareProfileExternally(profile) {
  const shareUrl = buildProfileShareUrl(profile.username, WEB_APP_URL);
  await shareExternalText({
    title: `@${profile.username} on Momi`,
    text: `Check out @${profile.username} on Momi: ${shareUrl}\nGet Momi for Android: ${ANDROID_APK_URL}`,
    url: shareUrl,
    toastMessage: `Profile link copied for @${profile.username}`
  });
}

async function openPostDetailModal(postId) {
  let post = state.posts.find((p) => p.id === postId);
  if (!post) {
    const rows = await selectSupabaseTable(
      SUPABASE_WEB_CONFIG,
      'posts',
      `select=*&id=eq.${encodeURIComponent(postId)}&limit=1`
    );
    if (rows[0]) post = mapSupabasePostToWeb(rows[0]);
  }
  if (!post) {
    showToast('Post not found or was removed.');
    return;
  }
  if (!window.location.search.includes(`post=${encodeURIComponent(postId)}`)) {
    window.history.replaceState({}, '', `${window.location.pathname}?post=${encodeURIComponent(postId)}`);
  }
  post.viewsCount = (post.viewsCount || 0) + 1;
  state.pendingMutations.minViews.set(postId, { value: post.viewsCount, expiresAt: Date.now() + 12000 });
  if (state.user?.uid) {
    if (!state.postViewersByPostId.has(postId)) state.postViewersByPostId.set(postId, new Set());
    state.postViewersByPostId.get(postId).add(state.user.uid);
    upsertSupabaseTable(
      SUPABASE_WEB_CONFIG,
      'user_interactions',
      [{ user_id: state.user.uid, target_type: 'VIEWED_POST', target_id: postId }],
      '',
      globalThis.fetch,
      'user_id,target_type,target_id'
    ).catch(() => undefined);
  }
  patchSupabaseRows(
    SUPABASE_WEB_CONFIG,
    'posts',
    `id=eq.${encodeURIComponent(postId)}`,
    { views_count: post.viewsCount }
  ).catch(() => undefined);

  const videoUrl = await resolveVideoForPost(post).catch(() => null);
  const commentRows = await selectSupabaseTable(
    SUPABASE_WEB_CONFIG,
    'comments',
    `select=*&post_id=eq.${encodeURIComponent(postId)}&order=created_at.asc&limit=60`
  );
  const comments = commentRows.map((c) => ({
    id: c.id,
    userId: c.user_id,
    authorUsername: c.author_username,
    text: c.text
  }));
  const morePosts = state.posts.filter((p) => p.id !== postId).slice(0, 4);

  state.activePostModalId = postId;
  openModal(`
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:14px;gap:10px;">
      <strong>@${escapeHtml(post.authorUsername)}</strong>
      <div style="display:flex;align-items:center;gap:8px;">
        <button type="button" class="btn-outline btn-sm" id="modalSharePostBtn">Share Link</button>
        <button type="button" class="btn-ghost btn-sm" id="closeModalBtn">Close</button>
      </div>
    </div>
    <div class="post-detail-split">
      <div style="background:#000;border-radius:14px;overflow:hidden;display:grid;place-items:center;">
        ${
          videoUrl
            ? `<video src="${escapeHtml(videoUrl)}" poster="${escapeHtml(resolveImageSrc(post))}" controls autoplay playsinline style="width:100%;max-height:70vh;"></video>`
            : `<img src="${resolveImageSrc(post)}" alt="${escapeHtml(post.caption || '')}" style="width:100%;max-height:70vh;object-fit:contain;filter:${filterCssForTag(post.filterTag)};" />`
        }
      </div>
      <div style="display:flex;flex-direction:column;gap:12px;">
        <div class="post-caption">${renderRichCaptionHtml(post.caption || '')}</div>
        <div class="post-stats-row">
          <button type="button" class="post-stats-link" data-open-engagers="${escapeHtml(post.id)}:0"><strong>${formatCount(post.likesCount)}</strong> likes</button>
          <span>&middot;</span>
          <button type="button" class="post-stats-link" data-open-engagers="${escapeHtml(post.id)}:1"><strong>${formatCount(post.viewsCount)}</strong> views</button>
          <span>&middot;</span>
          <span>${formatCount(post.commentsCount)} comments</span>
        </div>
        <div id="modalCommentsList" style="flex:1;max-height:320px;overflow-y:auto;display:flex;flex-direction:column;gap:8px;border-top:1px solid var(--border);padding-top:10px;">
          ${
            comments.length === 0
              ? '<p class="muted small">Be the first to comment.</p>'
              : comments.map((c) => `<div><strong>@${escapeHtml(c.authorUsername)}</strong> ${escapeHtml(c.text)}</div>`).join('')
          }
        </div>
        <form id="modalCommentForm" style="display:flex;gap:8px;">
          <input id="modalCommentInput" class="input" placeholder="Add a comment..." />
          <button type="submit" class="btn-primary btn-sm">Post</button>
        </form>
      </div>
    </div>
    ${morePosts.length ? `
      <div style="margin-top:18px;border-top:1px solid var(--border);padding-top:14px;">
        <div style="font-weight:700;margin-bottom:10px;">More to Explore</div>
        <div class="discovery-grid">
          ${morePosts.map((mp) => `
            <article class="discovery-card" data-open-post="${escapeHtml(mp.id)}">
              <img class="discovery-thumb" src="${resolveImageSrc(mp)}" alt="${escapeHtml(mp.caption || '')}" />
              <div class="discovery-meta"><div class="discovery-title">${escapeHtml(mp.caption || '@' + mp.authorUsername)}</div></div>
            </article>
          `).join('')}
        </div>
      </div>
    ` : ''}
  `);

  document.getElementById('closeModalBtn').onclick = closeModal;
  document.getElementById('modalSharePostBtn').onclick = () => sharePostExternally(post);
  bindFeedInteractions(modalContent);

  document.getElementById('modalCommentForm').onsubmit = async (e) => {
    e.preventDefault();
    const input = document.getElementById('modalCommentInput');
    const text = input.value.trim();
    if (!text || !state.profile) return;
    input.value = '';
    await upsertSupabaseTable(SUPABASE_WEB_CONFIG, 'comments', [{
      id: makeClientId('cmt'),
      post_id: postId,
      user_id: state.user.uid,
      author_username: state.profile.username,
      author_avatar_key: state.profile.avatarKey || 'avatar_aria',
      text: text.slice(0, 500),
      likes_count: 0
    }], '', globalThis.fetch, 'id');
    post.commentsCount = (post.commentsCount || 0) + 1;
    await patchSupabaseRows(SUPABASE_WEB_CONFIG, 'posts', `id=eq.${encodeURIComponent(postId)}`, { comments_count: post.commentsCount });
    refreshOpenPostModalComments(postId);
  };
}

function openPostEngagersModal(postId, initialTab = 0) {
  const post = state.posts.find((p) => p.id === postId);
  if (!post) return;
  let activeTab = Number(initialTab) === 1 ? 1 : 0;

  const renderEngagersBody = () => {
    const uidSet = activeTab === 0
      ? (state.postLikersByPostId.get(postId) || new Set())
      : (state.postViewersByPostId.get(postId) || new Set());
    const users = [...uidSet].map((uid) => {
      if (state.profile?.userId === uid) return state.profile;
      return state.creators.find((c) => c.userId === uid) || { userId: uid, username: 'momi.creator', displayName: 'Momi Member' };
    });
    const totalCount = activeTab === 0 ? (post.likesCount || 0) : (post.viewsCount || 0);
    const extraCount = Math.max(0, totalCount - users.length);

    openModal(`
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;">
        <h3 style="margin:0;">Post Activity &middot; @${escapeHtml(post.authorUsername)}</h3>
        <button type="button" class="btn-ghost btn-sm" id="closeModalBtn">Close</button>
      </div>
      <div class="chip-row" style="margin-bottom:14px;">
        <button type="button" class="chip ${activeTab === 0 ? 'active' : ''}" id="engTabLikes">Likes (${formatCount(post.likesCount)})</button>
        <button type="button" class="chip ${activeTab === 1 ? 'active' : ''}" id="engTabViews">Views (${formatCount(post.viewsCount)})</button>
      </div>
      <div class="engagers-list">
        ${
          users.length === 0
            ? `<p class="muted small" style="margin:8px 0;">${activeTab === 0 ? 'No tracked profile likes on this post yet.' : 'Viewers appear here as signed-in members open this post.'}</p>`
            : users.map((u) => {
              const isMe = u.userId === state.user?.uid;
              const isFollowing = state.followingUids.has(u.userId);
              return `
                <div style="display:flex;align-items:center;justify-content:space-between;gap:10px;">
                  <button type="button" class="post-author-btn" data-open-user="${escapeHtml(u.userId)}">
                    ${avatarHtml(u, 38)}
                    <div class="post-author-meta">
                      <span class="post-author-name">@${escapeHtml(u.username)} ${u.isVerified ? '<span class="verified-dot">&#10003;</span>' : ''}</span>
                      <span class="muted small">${escapeHtml(u.displayName || '')}</span>
                    </div>
                  </button>
                  ${!isMe ? `<button type="button" class="btn-sm ${isFollowing ? 'btn-outline' : 'btn-primary'}" data-follow-uid="${escapeHtml(u.userId)}" data-follow-name="${escapeHtml(u.username)}">${isFollowing ? 'Following' : 'Follow'}</button>` : '<span class="pill">You</span>'}
                </div>
              `;
            }).join('')
        }
        ${extraCount > 0 ? `<div class="muted small" style="padding-top:6px;">+ ${formatCount(extraCount)} additional ${activeTab === 0 ? 'likes' : 'views'} recorded.</div>` : ''}
      </div>
    `);
    document.getElementById('closeModalBtn').onclick = closeModal;
    document.getElementById('engTabLikes').onclick = () => { activeTab = 0; renderEngagersBody(); };
    document.getElementById('engTabViews').onclick = () => { activeTab = 1; renderEngagersBody(); };
    modalContent.querySelectorAll('[data-open-user]').forEach((b) => {
      b.onclick = () => {
        closeModal();
        state.viewedProfileUid = b.dataset.openUser;
        state.profileTab = 'posts';
        navigateTo('profile', { keepViewedUid: true });
      };
    });
    bindUserAndFollowButtons(modalContent);
  };
  state.activeEngagersPostId = postId;
  state.activeEngagersRefresh = renderEngagersBody;
  renderEngagersBody();
}

async function refreshOpenPostModalComments(postId) {
  if (state.activePostModalId !== postId) return;
  const listEl = document.getElementById('modalCommentsList');
  if (!listEl) return;
  const commentRows = await selectSupabaseTable(
    SUPABASE_WEB_CONFIG,
    'comments',
    `select=*&post_id=eq.${encodeURIComponent(postId)}&order=created_at.asc&limit=60`
  );
  if (state.activePostModalId !== postId) return;
  listEl.innerHTML = commentRows.length === 0
    ? '<p class="muted small">Be the first to comment.</p>'
    : commentRows.map((c) => `<div><strong>@${escapeHtml(c.author_username)}</strong> ${escapeHtml(c.text)}</div>`).join('');
  listEl.scrollTop = listEl.scrollHeight;
}

function openStoryModal(storyId) {
  const story = state.stories.find((s) => s.id === storyId);
  if (!story) return;
  state.seenStoryIds.add(storyId);
  upsertSupabaseTable(
    SUPABASE_WEB_CONFIG,
    'user_interactions',
    [{ user_id: state.user.uid, target_type: 'SEEN_STORY', target_id: storyId }],
    '',
    globalThis.fetch,
    'user_id,target_type,target_id'
  ).catch(() => undefined);

  const videoSrc = resolveVideoSrc(story);
  openModal(`
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;">
      <strong>@${escapeHtml(story.username)} &middot; Story</strong>
      <button type="button" class="btn-ghost btn-sm" id="closeModalBtn">Close</button>
    </div>
    <div style="text-align:center;background:#000;border-radius:16px;overflow:hidden;">
      ${
        videoSrc
          ? `<video src="${escapeHtml(videoSrc)}" poster="${escapeHtml(resolveImageSrc(story))}" controls autoplay playsinline style="max-height:72vh;width:100%;"></video>`
          : `<img src="${resolveImageSrc(story)}" alt="${escapeHtml(story.caption || '')}" style="max-height:72vh;width:100%;object-fit:contain;" />`
      }
    </div>
    ${story.caption ? `<p style="margin:12px 0 0;">${escapeHtml(story.caption)}</p>` : ''}
  `);
  document.getElementById('closeModalBtn').onclick = closeModal;
}

function openEditProfileModal() {
  const p = state.profile;
  if (!p) return;
  openModal(`
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:14px;">
      <h3 style="margin:0;">Edit Profile</h3>
      <button type="button" class="btn-ghost btn-sm" id="closeModalBtn">Close</button>
    </div>
    <form id="editProfileForm" style="display:grid;gap:12px;">
      <div>
        <label class="small muted" for="editAvatarInput">Upload New Avatar</label>
        <input id="editAvatarInput" type="file" accept="image/*" class="input" />
      </div>
      <div>
        <label class="small muted">Display Name</label>
        <input id="editDisplayName" class="input" value="${escapeHtml(p.displayName)}" required />
      </div>
      <div>
        <label class="small muted">Username</label>
        <input id="editUsername" class="input" value="${escapeHtml(p.username)}" required />
      </div>
      <div>
        <label class="small muted">Category</label>
        <input id="editCategory" class="input" value="${escapeHtml(p.category || '')}" />
      </div>
      <div>
        <label class="small muted">Bio</label>
        <textarea id="editBio" class="textarea" rows="3">${escapeHtml(p.bio || '')}</textarea>
      </div>
      <div>
        <label class="small muted">Website</label>
        <input id="editWebsite" class="input" value="${escapeHtml(p.website || '')}" />
      </div>
      <button type="submit" class="btn-primary">Save Profile</button>
    </form>
  `);
  document.getElementById('closeModalBtn').onclick = closeModal;
  let newAvatarFile = null;
  document.getElementById('editAvatarInput').onchange = (e) => {
    newAvatarFile = e.target.files?.[0] || null;
  };
  document.getElementById('editProfileForm').onsubmit = async (e) => {
    e.preventDefault();
    let avatarUrl = p.avatarUrl || null;
    let avatarData = p.avatarData || null;
    if (newAvatarFile) {
      const uploaded = await uploadWebFileToStorage(
        SUPABASE_WEB_CONFIG,
        'momi-avatars',
        `avatars/${state.user.uid}.jpg`,
        newAvatarFile,
        newAvatarFile.type || 'image/jpeg'
      );
      if (uploaded) {
        avatarUrl = `${uploaded}?t=${Date.now()}`;
      } else {
        avatarData = await compressFileToBase64(newAvatarFile, 360, 180000);
      }
    }
    const patch = {
      display_name: document.getElementById('editDisplayName').value.trim().slice(0, 80),
      username: document.getElementById('editUsername').value.trim().toLowerCase().replace(/[^a-z0-9._]/g, '').slice(0, 50),
      category: document.getElementById('editCategory').value.trim().slice(0, 80),
      bio: document.getElementById('editBio').value.trim().slice(0, 300),
      website: document.getElementById('editWebsite').value.trim().slice(0, 200),
      avatar_url: avatarUrl,
      avatar_data: avatarData
    };
    const optimistic = {
      displayName: patch.display_name || p.displayName,
      username: patch.username || p.username,
      category: patch.category,
      bio: patch.bio,
      website: patch.website,
      avatarUrl: patch.avatar_url,
      avatarData: patch.avatar_data
    };
    state.pendingMutations.profile = { data: optimistic, expiresAt: Date.now() + 12000 };
    state.profile = { ...state.profile, ...optimistic };
    state.creators = state.creators.map((c) => (c.userId === state.user.uid ? { ...c, ...optimistic } : c));
    saveCachedWebWorkspace(state.user.uid);
    await patchSupabaseRows(SUPABASE_WEB_CONFIG, 'profiles', `user_id=eq.${encodeURIComponent(state.user.uid)}`, patch);
    await refreshAllFromSupabase({ showErrors: false });
    closeModal();
    showToast('Profile updated');
  };
}

function drawStudioStageFrame(canvas, { username, title, category, cameraOn, micOn, seq, videoEl }) {
  if (!canvas) return '';
  const ctx = canvas.getContext('2d');
  const w = canvas.width;
  const h = canvas.height;
  if (cameraOn && videoEl && videoEl.readyState >= 2) {
    ctx.drawImage(videoEl, 0, 0, w, h);
  } else {
    const grad = ctx.createLinearGradient(0, 0, w, h);
    grad.addColorStop(0, '#4c0d29');
    grad.addColorStop(0.55, '#19142b');
    grad.addColorStop(1, '#091830');
    ctx.fillStyle = grad;
    ctx.fillRect(0, 0, w, h);

    // Animated studio pulse rings
    ctx.strokeStyle = 'rgba(224, 82, 121, 0.35)';
    ctx.lineWidth = 3;
    const pulse = 54 + (seq % 6) * 8;
    ctx.beginPath();
    ctx.arc(w / 2, h / 2 - 14, pulse, 0, Math.PI * 2);
    ctx.stroke();

    ctx.fillStyle = '#e05279';
    ctx.beginPath();
    ctx.arc(w / 2, h / 2 - 14, 38, 0, Math.PI * 2);
    ctx.fill();

    ctx.fillStyle = '#ffffff';
    ctx.font = 'bold 24px sans-serif';
    ctx.textAlign = 'center';
    ctx.fillText(`@${username}`, w / 2, h / 2 + 52);

    ctx.fillStyle = 'rgba(255,255,255,0.82)';
    ctx.font = '15px sans-serif';
    ctx.fillText(title.slice(0, 38), w / 2, h / 2 + 80);

    ctx.fillStyle = 'rgba(255,255,255,0.65)';
    ctx.font = '12px sans-serif';
    ctx.fillText(`${category} · ${micOn ? 'Audio Live' : 'Mic Muted'}`, w / 2, h / 2 + 104);
  }
  return (canvas.toDataURL('image/jpeg', 0.62).split(',')[1] || '').slice(0, 180000);
}

function stopLiveBroadcast() {
  if (state.liveTimer) {
    clearInterval(state.liveTimer);
    state.liveTimer = null;
  }
  if (state.liveStreamMedia) {
    state.liveStreamMedia.getTracks().forEach((t) => t.stop());
    state.liveStreamMedia = null;
  }
}

function openGoLiveModal() {
  if (!state.profile) return;
  state.liveCameraOn = true;
  state.liveMicOn = true;
  let isLiveActive = false;
  let seq = 0;

  openModal(`
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:14px;">
      <div style="display:flex;align-items:center;gap:8px;">
        <h3 style="margin:0;">Momi Live Studio</h3>
        <span id="studioLiveStatusPill" class="pill">Preview Ready</span>
      </div>
      <button type="button" class="btn-ghost btn-sm" id="closeModalBtn">Close</button>
    </div>
    <div class="post-detail-split">
      <div style="display:grid;gap:12px;">
        <div class="live-stage-box">
          <video id="livePreviewVideo" autoplay muted playsinline style="width:100%;height:320px;object-fit:cover;"></video>
          <canvas id="liveStageFallbackCanvas" width="480" height="360" class="hidden" style="width:100%;height:320px;object-fit:cover;"></canvas>
          <div class="live-stage-overlay">
            <span class="badge-pill live" id="studioLiveTag">PREVIEW</span>
            <span class="pill" id="studioLiveStats" style="background:rgba(0,0,0,0.6);color:#fff;">0 watching &middot; 0 &#9829;</span>
          </div>
        </div>
        <div style="display:flex;gap:8px;flex-wrap:wrap;">
          <button type="button" class="btn-outline btn-sm" id="toggleLiveCamBtn">Camera: On</button>
          <button type="button" class="btn-outline btn-sm" id="toggleLiveMicBtn">Mic: On</button>
        </div>
        <div class="field-grid-2">
          <input id="liveTitleInput" class="input" value="Live from Momi Studio" placeholder="Stream title..." />
          <select id="liveCategorySelect" class="select">
            <option value="Live">Talk &amp; Chill</option>
            <option value="Music">Music &amp; Audio</option>
            <option value="Travel">Travel &amp; Explore</option>
            <option value="Art">Art &amp; Studio</option>
          </select>
        </div>
        <div style="display:flex;gap:10px;flex-wrap:wrap;">
          <button type="button" class="btn-primary btn-lg" id="startLiveBtn" style="flex:1;">Go Live Now</button>
          <button type="button" class="btn-danger hidden" id="endLiveBtn" style="flex:1;">End Broadcast</button>
        </div>
      </div>

      <div style="display:flex;flex-direction:column;gap:10px;">
        <strong>Live Room Chat</strong>
        <div id="broadcasterChatBox" style="flex:1;min-height:240px;max-height:340px;overflow-y:auto;display:flex;flex-direction:column;gap:8px;background:var(--surface-alt);border:1px solid var(--border);border-radius:14px;padding:12px;">
          <div class="muted small">Messages and gifts from viewers appear here as soon as you go live.</div>
        </div>
        <form id="broadcasterChatForm" style="display:flex;gap:8px;">
          <input id="broadcasterChatInput" class="input" placeholder="Send a message to viewers..." />
          <button type="submit" class="btn-primary btn-sm">Send</button>
        </form>
      </div>
    </div>
  `);

  const vid = document.getElementById('livePreviewVideo');
  const fallbackCanvas = document.getElementById('liveStageFallbackCanvas');
  const snapCanvas = document.createElement('canvas');
  snapCanvas.width = 480;
  snapCanvas.height = 360;

  const updateStageVisual = () => {
    const hasCam = state.liveCameraOn && Boolean(state.liveStreamMedia);
    if (hasCam) {
      vid?.classList.remove('hidden');
      fallbackCanvas?.classList.add('hidden');
    } else {
      vid?.classList.add('hidden');
      fallbackCanvas?.classList.remove('hidden');
      drawStudioStageFrame(fallbackCanvas, {
        username: state.profile.username,
        title: document.getElementById('liveTitleInput')?.value || 'Live Studio',
        category: document.getElementById('liveCategorySelect')?.value || 'Live',
        cameraOn: false,
        micOn: state.liveMicOn,
        seq,
        videoEl: null
      });
    }
  };

  navigator.mediaDevices?.getUserMedia({ video: true, audio: false }).then((stream) => {
    state.liveStreamMedia = stream;
    if (vid) vid.srcObject = stream;
    updateStageVisual();
  }).catch(() => {
    state.liveCameraOn = false;
    const camBtn = document.getElementById('toggleLiveCamBtn');
    if (camBtn) camBtn.textContent = 'Stage Mode (No Camera)';
    updateStageVisual();
  });

  document.getElementById('toggleLiveCamBtn').onclick = () => {
    state.liveCameraOn = !state.liveCameraOn;
    document.getElementById('toggleLiveCamBtn').textContent = state.liveCameraOn ? 'Camera: On' : 'Camera: Stage Card';
    updateStageVisual();
  };

  document.getElementById('toggleLiveMicBtn').onclick = () => {
    state.liveMicOn = !state.liveMicOn;
    document.getElementById('toggleLiveMicBtn').textContent = state.liveMicOn ? 'Mic: On' : 'Mic: Muted';
    updateStageVisual();
  };

  const endStreamInSupabase = async () => {
    if (!isLiveActive) return;
    isLiveActive = false;
    await patchSupabaseRows(
      SUPABASE_WEB_CONFIG,
      'live_streams',
      `id=eq.${encodeURIComponent(state.user.uid)}`,
      { is_live: false, viewer_count: 0 }
    ).catch(() => undefined);
    await refreshAllFromSupabase({ showErrors: false });
  };

  document.getElementById('closeModalBtn').onclick = async () => {
    await endStreamInSupabase();
    closeModal();
  };

  document.getElementById('startLiveBtn').onclick = async () => {
    const uid = state.user.uid;
    const title = (document.getElementById('liveTitleInput').value.trim() || 'Live Studio Session').slice(0, 140);
    const category = document.getElementById('liveCategorySelect').value || 'Live';
    isLiveActive = true;

    await ensureUserSeededInSupabase(state.user);
    await upsertSupabaseTable(SUPABASE_WEB_CONFIG, 'live_streams', [{
      id: uid,
      user_id: uid,
      username: state.profile.username,
      display_name: state.profile.displayName,
      avatar_key: state.profile.avatarKey || 'avatar_aria',
      author_verified: Boolean(state.profile.isVerified),
      title,
      category,
      is_live: true,
      viewer_count: 1,
      likes_count: 0,
      gifts_credits: 0
    }], '', globalThis.fetch, 'id');

    const publishFrame = async () => {
      seq += 1;
      const b64 = drawStudioStageFrame(snapCanvas, {
        username: state.profile.username,
        title: document.getElementById('liveTitleInput')?.value || title,
        category,
        cameraOn: state.liveCameraOn,
        micOn: state.liveMicOn,
        seq,
        videoEl: vid
      });
      updateStageVisual();
      await upsertSupabaseTable(SUPABASE_WEB_CONFIG, 'live_frames', [{
        stream_id: uid,
        user_id: uid,
        seq,
        frame_data: b64,
        camera_enabled: state.liveCameraOn,
        mic_muted: !state.liveMicOn
      }], '', globalThis.fetch, 'stream_id').catch(() => undefined);
      await patchSupabaseRows(
        SUPABASE_WEB_CONFIG,
        'live_streams',
        `id=eq.${encodeURIComponent(uid)}`,
        { updated_at: new Date().toISOString() }
      ).catch(() => undefined);
    };

    // Publish first stage frame immediately so viewers never wait on a blank canvas
    await publishFrame();

    document.getElementById('startLiveBtn').classList.add('hidden');
    document.getElementById('endLiveBtn').classList.remove('hidden');
    document.getElementById('studioLiveStatusPill').textContent = 'LIVE NOW';
    document.getElementById('studioLiveTag').textContent = 'LIVE';

    state.liveTimer = setInterval(publishFrame, 1500);
    const pollBroadcasterRoom = async () => {
      const [streams, comments] = await Promise.all([
        selectSupabaseTable(SUPABASE_WEB_CONFIG, 'live_streams', `select=*&id=eq.${encodeURIComponent(uid)}&limit=1`),
        selectSupabaseTable(SUPABASE_WEB_CONFIG, 'live_comments', `select=*&stream_id=eq.${encodeURIComponent(uid)}&order=created_at.asc&limit=40`)
      ]);
      const st = streams[0];
      const statsEl = document.getElementById('studioLiveStats');
      if (st && statsEl) {
        statsEl.textContent = `${formatCount(st.viewer_count || 0)} watching · ${formatCount(st.likes_count || 0)} ♥ · ${formatCount(st.gifts_credits || 0)} cr`;
      }
      const chatBox = document.getElementById('broadcasterChatBox');
      if (chatBox && comments.length > 0) {
        chatBox.innerHTML = comments.map((c) => `<div><strong>@${escapeHtml(c.username)}</strong> ${escapeHtml(c.text)}</div>`).join('');
        chatBox.scrollTop = chatBox.scrollHeight;
      }
    };
    state.activeLiveRoomRefresh = pollBroadcasterRoom;
    state.livePollTimer = setInterval(pollBroadcasterRoom, 5000);

    await refreshAllFromSupabase({ showErrors: false });
    showToast('You are LIVE on Momi!');
  };

  document.getElementById('broadcasterChatForm').onsubmit = async (e) => {
    e.preventDefault();
    const input = document.getElementById('broadcasterChatInput');
    const text = input.value.trim();
    if (!text) return;
    input.value = '';
    await upsertSupabaseTable(SUPABASE_WEB_CONFIG, 'live_comments', [{
      id: makeClientId('lc'),
      stream_id: state.user.uid,
      user_id: state.user.uid,
      username: state.profile.username,
      avatar_key: state.profile.avatarKey || 'avatar_aria',
      text: text.slice(0, 300)
    }], '', globalThis.fetch, 'id');
  };

  document.getElementById('endLiveBtn').onclick = async () => {
    await endStreamInSupabase();
    closeModal();
    showToast('Live broadcast ended');
  };
}

function openWatchLiveModal(streamId) {
  const stream = state.liveStreams.find((s) => s.userId === streamId || s.id === streamId);
  if (!stream) return;
  const startedAtMs = Date.now();

  patchSupabaseRows(
    SUPABASE_WEB_CONFIG,
    'live_streams',
    `id=eq.${encodeURIComponent(streamId)}`,
    { viewer_count: (stream.viewerCount || 0) + 1 }
  ).catch(() => undefined);

  openModal(`
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;">
      <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap;">
        <strong>@${escapeHtml(stream.username)}</strong>
        <span class="badge-pill live">LIVE</span>
        <span class="muted small">${escapeHtml(stream.title)}</span>
      </div>
      <button type="button" class="btn-ghost btn-sm" id="closeModalBtn">Leave</button>
    </div>
    <div class="post-detail-split">
      <div>
        <div class="live-stage-box">
          <div id="liveAnimatedBackdrop" class="live-stage-backdrop">
            <div class="live-host-ring">${avatarHtml(stream, 78)}</div>
            <div style="text-align:center;">
              <div style="font-weight:800;font-size:1.15rem;color:#fff;">@${escapeHtml(stream.username)}</div>
              <div style="color:rgba(255,255,255,0.82);font-size:0.9rem;margin-top:2px;">${escapeHtml(stream.title || 'Momi Live Studio')}</div>
            </div>
            <div class="eq-bars" aria-hidden="true"><span></span><span></span><span></span><span></span><span></span></div>
            <div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap;justify-content:center;">
              <span class="pill" id="viewerLiveHardwarePill" style="background:rgba(255,255,255,0.14);color:#fff;">Audio &amp; Stage Live</span>
              <span class="pill" id="viewerLiveElapsed" style="background:rgba(255,255,255,0.14);color:#fff;">00:00</span>
            </div>
          </div>
          <img id="liveRemoteFrameImg" class="hidden" alt="Live stream video feed" style="width:100%;height:340px;object-fit:cover;position:relative;z-index:1;" />
          <div id="liveHeartsLayer" style="position:absolute;inset:0;pointer-events:none;z-index:2;overflow:hidden;"></div>
          <div class="live-stage-overlay" style="z-index:3;">
            <span class="badge-pill live">LIVE STUDIO</span>
            <span class="pill" id="viewerLiveStatsPill" style="background:rgba(0,0,0,0.65);color:#fff;">
              ${formatCount(Math.max(1, stream.viewerCount || 1))} watching &middot; ${formatCount(stream.likesCount || 0)} &#9829;
            </span>
          </div>
        </div>
        <div style="display:flex;gap:8px;flex-wrap:wrap;margin-top:12px;">
          <button type="button" class="btn-outline btn-sm" id="liveHeartBtn">&#9829; Send Heart</button>
          ${GIFT_CATALOG.map((g) => `
            <button type="button" class="btn-primary btn-sm" data-send-gift="${g.key}:${g.credits}:${escapeHtml(g.name)}">${escapeHtml(g.name)} (${g.credits})</button>
          `).join('')}
        </div>
      </div>
      <div style="display:flex;flex-direction:column;gap:10px;">
        <strong>Live Chat</strong>
        <div id="liveChatBox" style="flex:1;min-height:240px;max-height:340px;overflow-y:auto;display:flex;flex-direction:column;gap:8px;background:var(--surface-alt);border:1px solid var(--border);border-radius:14px;padding:12px;">
          <div class="muted small">Joined @${escapeHtml(stream.username)}'s live room! Say hello below.</div>
        </div>
        <form id="liveChatForm" style="display:flex;gap:8px;">
          <input id="liveChatInput" class="input" placeholder="Say hello in live chat..." />
          <button type="submit" class="btn-primary btn-sm">Send</button>
        </form>
      </div>
    </div>
  `);

  const spawnFloatingHeart = () => {
    const layer = document.getElementById('liveHeartsLayer');
    if (!layer) return;
    const heart = document.createElement('span');
    heart.className = 'floating-heart';
    heart.textContent = '♥';
    heart.style.left = `${15 + Math.round(Math.random() * 70)}%`;
    layer.appendChild(heart);
    setTimeout(() => heart.remove(), 1700);
  };

  state.liveTimer = setInterval(() => {
    const el = document.getElementById('viewerLiveElapsed');
    if (!el) return;
    const secs = Math.max(0, Math.floor((Date.now() - startedAtMs) / 1000));
    const mm = String(Math.floor(secs / 60)).padStart(2, '0');
    const ss = String(secs % 60).padStart(2, '0');
    el.textContent = `${mm}:${ss}`;
  }, 1000);

  const pollRoom = async () => {
    const [streams, frames, comments] = await Promise.all([
      selectSupabaseTable(SUPABASE_WEB_CONFIG, 'live_streams', `select=*&id=eq.${encodeURIComponent(streamId)}&limit=1`),
      selectSupabaseTable(SUPABASE_WEB_CONFIG, 'live_frames', `select=*&stream_id=eq.${encodeURIComponent(streamId)}&limit=1`),
      selectSupabaseTable(SUPABASE_WEB_CONFIG, 'live_comments', `select=*&stream_id=eq.${encodeURIComponent(streamId)}&order=created_at.asc&limit=50`)
    ]);
    const st = streams[0];
    const statsPill = document.getElementById('viewerLiveStatsPill');
    if (st && statsPill) {
      statsPill.textContent = `${formatCount(Math.max(1, st.viewer_count || 1))} watching · ${formatCount(st.likes_count || 0)} ♥`;
    }
    const frame = frames[0];
    const hwPill = document.getElementById('viewerLiveHardwarePill');
    if (frame && hwPill) {
      const camLabel = frame.camera_enabled ? 'Cam Live' : 'Stage Mode';
      const micLabel = frame.mic_muted ? 'Mic Muted' : 'Audio Live';
      hwPill.textContent = `${camLabel} · ${micLabel}`;
    }
    const img = document.getElementById('liveRemoteFrameImg');
    const backdrop = document.getElementById('liveAnimatedBackdrop');
    if (frame?.frame_data && frame.camera_enabled && img) {
      img.src = `data:image/jpeg;base64,${frame.frame_data}`;
      img.classList.remove('hidden');
      backdrop?.classList.add('hidden');
    } else {
      img?.classList.add('hidden');
      backdrop?.classList.remove('hidden');
    }
    const box = document.getElementById('liveChatBox');
    if (box && comments.length > 0) {
      box.innerHTML = comments.map((c) => `<div><strong>@${escapeHtml(c.username)}</strong> ${escapeHtml(c.text)}</div>`).join('');
      box.scrollTop = box.scrollHeight;
    }
  };
  state.activeLiveRoomRefresh = pollRoom;
  pollRoom();
  state.livePollTimer = setInterval(pollRoom, 5000);

  document.getElementById('closeModalBtn').onclick = () => {
    patchSupabaseRows(
      SUPABASE_WEB_CONFIG,
      'live_streams',
      `id=eq.${encodeURIComponent(streamId)}`,
      { viewer_count: Math.max(0, stream.viewerCount || 0) }
    ).catch(() => undefined);
    closeModal();
  };

  document.getElementById('liveHeartBtn').onclick = async () => {
    spawnFloatingHeart();
    stream.likesCount = (stream.likesCount || 0) + 1;
    await patchSupabaseRows(
      SUPABASE_WEB_CONFIG,
      'live_streams',
      `id=eq.${encodeURIComponent(streamId)}`,
      { likes_count: stream.likesCount }
    ).catch(() => undefined);
    pollRoom();
  };

  document.getElementById('liveChatForm').onsubmit = async (e) => {
    e.preventDefault();
    const input = document.getElementById('liveChatInput');
    const text = input.value.trim();
    if (!text || !state.profile) return;
    input.value = '';
    await upsertSupabaseTable(SUPABASE_WEB_CONFIG, 'live_comments', [{
      id: makeClientId('lc'),
      stream_id: streamId,
      user_id: state.user.uid,
      username: state.profile.username,
      avatar_key: state.profile.avatarKey || 'avatar_aria',
      text: text.slice(0, 300)
    }], '', globalThis.fetch, 'id');
    pollRoom();
  };

  modalContent.querySelectorAll('[data-send-gift]').forEach((b) => {
    b.onclick = async () => {
      const [giftKey, creditsStr, giftName] = b.dataset.sendGift.split(':');
      const credits = Number(creditsStr);
      const w = state.wallet;
      if (!w || (w.balance || 0) < credits) {
        showToast('Not enough credits. Buy credits in the Momi Android app first.');
        return;
      }
      if (stream.userId === state.user.uid) {
        showToast('You cannot gift your own broadcast.');
        return;
      }
      const txId = makeClientId('gift');
      await patchSupabaseRows(SUPABASE_WEB_CONFIG, 'wallets', `user_id=eq.${encodeURIComponent(state.user.uid)}`, {
        balance: w.balance - credits,
        total_spent: (w.totalSpent || 0) + credits,
        last_tx_id: txId
      });
      await upsertSupabaseTable(SUPABASE_WEB_CONFIG, 'credit_transactions', [{
        tx_id: txId,
        type: 'GIFT',
        sender_uid: state.user.uid,
        sender_username: state.profile.username,
        recipient_uid: stream.userId,
        recipient_username: stream.username,
        credits_amount: credits,
        price_usd: 0,
        payment_method: 'WALLET_CREDITS',
        gift_key: giftKey,
        gift_name: giftName,
        stream_id: streamId,
        recipient_claimed: false,
        status: 'COMPLETED'
      }], '', globalThis.fetch, 'tx_id');
      await patchSupabaseRows(
        SUPABASE_WEB_CONFIG,
        'live_streams',
        `id=eq.${encodeURIComponent(streamId)}`,
        { gifts_credits: (stream.giftsCredits || 0) + credits }
      );
      await refreshAllFromSupabase({ showErrors: false });
      showToast(`Sent ${giftName} (${credits} cr) to @${stream.username}!`);
    };
  });
}

function openProductDetailModal(productId) {
  const prod = state.products.find((p) => p.id === productId);
  if (!prod) return;
  const isOwn = prod.sellerUid === state.user?.uid;
  const videoUrl = resolveVideoSrc(prod);

  openModal(`
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:14px;">
      <strong>${escapeHtml(prod.storeName)} &middot; @${escapeHtml(prod.sellerUsername)}</strong>
      <button type="button" class="btn-ghost btn-sm" id="closeModalBtn">Close</button>
    </div>
    <div class="post-detail-split">
      <div style="background:#000;border-radius:14px;overflow:hidden;display:grid;place-items:center;">
        ${
          videoUrl
            ? `<video src="${escapeHtml(videoUrl)}" poster="${escapeHtml(resolveImageSrc(prod))}" controls playsinline style="width:100%;max-height:60vh;"></video>`
            : `<img src="${resolveImageSrc(prod)}" alt="${escapeHtml(prod.title)}" style="width:100%;max-height:60vh;object-fit:contain;" />`
        }
      </div>
      <div style="display:flex;flex-direction:column;gap:12px;">
        <h3 style="margin:0;">${escapeHtml(prod.title)}</h3>
        <div style="font-size:1.5rem;font-weight:800;color:var(--primary);">$${Number(prod.priceUsd || 0).toFixed(2)}</div>
        <div class="muted small">${escapeHtml(prod.category)} &middot; ${prod.freeShipping ? 'Free Shipping' : 'Standard Shipping'}</div>
        ${prod.description ? `<p style="margin:0;line-height:1.5;">${escapeHtml(prod.description)}</p>` : ''}
        ${
          isOwn
            ? `<button type="button" class="btn-danger" id="deleteMarketProdBtn">Remove Listing</button>`
            : `<form id="marketBuyForm" style="display:grid;gap:10px;border-top:1px solid var(--border);padding-top:12px;">
                <label class="small muted">Delivery Address &amp; Contact</label>
                <textarea id="marketAddressInput" class="textarea" rows="2" placeholder="Street, city, phone number..." required></textarea>
                <div style="display:flex;gap:10px;">
                  <button type="button" class="btn-outline" id="msgSellerBtn">Message Seller</button>
                  <button type="submit" class="btn-primary" style="flex:1;">Buy Now ($${Number(prod.priceUsd || 0).toFixed(2)})</button>
                </div>
              </form>`
        }
      </div>
    </div>
  `);
  document.getElementById('closeModalBtn').onclick = closeModal;
  document.getElementById('deleteMarketProdBtn')?.addEventListener('click', async () => {
    await deleteSupabaseRows(SUPABASE_WEB_CONFIG, 'market_products', `id=eq.${encodeURIComponent(prod.id)}`);
    closeModal();
    await refreshAllFromSupabase({ showErrors: false });
    showToast('Listing removed');
  });
  document.getElementById('msgSellerBtn')?.addEventListener('click', () => {
    closeModal();
    openOrCreateDirectThread({
      userId: prod.sellerUid,
      username: prod.sellerUsername,
      displayName: prod.storeName,
      avatarKey: 'avatar_aria'
    });
  });
  document.getElementById('marketBuyForm')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const addr = document.getElementById('marketAddressInput').value.trim();
    if (!addr || !state.profile) return;
    await upsertSupabaseTable(SUPABASE_WEB_CONFIG, 'market_orders', [{
      id: makeClientId('order'),
      buyer_uid: state.user.uid,
      buyer_username: state.profile.username,
      seller_uid: prod.sellerUid,
      seller_store_name: prod.storeName,
      product_id: prod.id,
      product_title: prod.title,
      product_image_key: prod.imageKey || 'img_post_coastal',
      product_image_data: prod.imageData || null,
      price_usd: Number(prod.priceUsd),
      shipping_address: addr.slice(0, 300),
      status: 'PLACED'
    }], '', globalThis.fetch, 'id');
    closeModal();
    await refreshAllFromSupabase({ showErrors: false });
    showToast('Order placed!');
  });
}

function openSellProductModal() {
  const existingMerchant = state.merchants.find((m) => m.userId === state.user?.uid);
  openModal(`
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:14px;">
      <h3 style="margin:0;">Sell on Momi Marketplace</h3>
      <button type="button" class="btn-ghost btn-sm" id="closeModalBtn">Close</button>
    </div>
    <form id="sellMarketForm" style="display:grid;gap:12px;">
      <div class="field-grid-2">
        <div>
          <label class="small muted">Store Name *</label>
          <input id="sellStoreName" class="input" value="${escapeHtml(existingMerchant?.storeName || `${state.profile?.displayName || 'Momi'} Shop`)}" required />
        </div>
        <div>
          <label class="small muted">Category</label>
          <select id="sellCategory" class="select">
            <option value="Collectibles">Collectibles</option>
            <option value="Fashion">Fashion</option>
            <option value="Tech">Tech</option>
            <option value="Art">Art</option>
            <option value="Home">Home</option>
            <option value="Digital">Digital</option>
          </select>
        </div>
      </div>
      <div>
        <label class="small muted">Product Title *</label>
        <input id="sellTitle" class="input" placeholder="e.g. Vintage Film Camera" required />
      </div>
      <div class="field-grid-2">
        <div>
          <label class="small muted">Price (USD) *</label>
          <input id="sellPrice" type="number" step="0.01" min="0.50" class="input" placeholder="29.99" required />
        </div>
        <div>
          <label class="small muted">Product Photo</label>
          <input id="sellPhotoInput" type="file" accept="image/*" class="input" />
        </div>
      </div>
      <div>
        <label class="small muted">Description</label>
        <textarea id="sellDesc" class="textarea" rows="3" placeholder="Condition, sizing, or shipping notes..."></textarea>
      </div>
      <label class="consent-row">
        <span>Free Shipping Badge</span>
        <input id="sellFreeShip" type="checkbox" checked />
      </label>
      <button type="submit" class="btn-primary btn-lg">Publish Listing</button>
    </form>
  `);
  document.getElementById('closeModalBtn').onclick = closeModal;
  document.getElementById('sellMarketForm').onsubmit = async (e) => {
    e.preventDefault();
    const storeName = document.getElementById('sellStoreName').value.trim().slice(0, 80);
    const category = document.getElementById('sellCategory').value;
    const title = document.getElementById('sellTitle').value.trim().slice(0, 120);
    const price = Math.max(0.5, Number(document.getElementById('sellPrice').value) || 1);
    const desc = document.getElementById('sellDesc').value.trim().slice(0, 1000);
    const freeShipping = Boolean(document.getElementById('sellFreeShip').checked);
    const file = document.getElementById('sellPhotoInput').files?.[0];
    const prodId = makeClientId('prod');
    let imageUrl = null;
    if (file) {
      imageUrl = await uploadWebFileToStorage(
        SUPABASE_WEB_CONFIG,
        'momi-media',
        `market/${state.user.uid}/${prodId}.jpg`,
        file,
        file.type || 'image/jpeg'
      );
    }

    await ensureUserSeededInSupabase(state.user);
    await upsertSupabaseTable(SUPABASE_WEB_CONFIG, 'merchants', [{
      user_id: state.user.uid,
      owner_username: state.profile.username,
      store_name: storeName,
      category,
      is_verified: Boolean(existingMerchant?.isVerified)
    }], '', globalThis.fetch, 'user_id');

    await upsertSupabaseTable(SUPABASE_WEB_CONFIG, 'market_products', [{
      id: prodId,
      seller_uid: state.user.uid,
      seller_username: state.profile.username,
      store_name: storeName,
      title,
      description: desc,
      category,
      price_usd: price,
      original_price_usd: 0,
      discount_percent: 0,
      free_shipping: freeShipping,
      image_key: 'img_post_coastal',
      image_url: imageUrl,
      stock_count: 5
    }], '', globalThis.fetch, 'id');

    closeModal();
    await refreshAllFromSupabase({ showErrors: false });
    showToast('Listing published to Momi Marketplace!');
  };
}

function openOrdersModal() {
  openModal(`
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:14px;">
      <h3 style="margin:0;">Marketplace Orders (${state.orders.length})</h3>
      <button type="button" class="btn-ghost btn-sm" id="closeModalBtn">Close</button>
    </div>
    <div style="display:grid;gap:10px;">
      ${
        state.orders.length === 0
          ? '<p class="muted">No orders yet.</p>'
          : state.orders.map((o) => `
            <div class="consent-row" style="align-items:flex-start;">
              <div>
                <strong>${escapeHtml(o.productTitle)} ($${Number(o.priceUsd || 0).toFixed(2)})</strong>
                <div class="muted small">Store: ${escapeHtml(o.sellerStoreName)} &middot; Status: ${escapeHtml(o.status)}</div>
                <div class="muted small">Ship to: ${escapeHtml(o.shippingAddress)}</div>
              </div>
            </div>
          `).join('')
      }
    </div>
  `);
  document.getElementById('closeModalBtn').onclick = closeModal;
}

async function updatePartnerConsent(enabled) {
  if (!state.user) return;
  await patchSupabaseRows(
    SUPABASE_WEB_CONFIG,
    'profiles',
    `user_id=eq.${encodeURIComponent(state.user.uid)}`,
    { partner_consent: Boolean(enabled), consent_updated_at: new Date().toISOString() }
  );
  if (state.profile) state.profile.partnerConsent = Boolean(enabled);
  showToast(enabled ? 'Partner Boost & Owner Perks consent enabled' : 'Partner consent turned off');
}

function openPostBoostModal(post) {
  const balance = state.wallet?.balance || 0;
  openModal(`
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:14px;">
      <h3 style="margin:0;">Boost Your Post</h3>
      <button type="button" class="btn-ghost btn-sm" id="closeModalBtn">Close</button>
    </div>
    <p class="muted small" style="margin-top:0;">Available balance: <strong>${formatCount(balance)} Credits</strong></p>
    <div style="display:grid;gap:12px;">
      ${POST_BOOST_OPTIONS.map((opt) => `
        <div class="consent-row">
          <div>
            <strong>${escapeHtml(opt.title)}</strong>
            <div class="muted small">${escapeHtml(opt.subtitle)}</div>
          </div>
          <button type="button" class="btn-primary btn-sm" data-confirm-boost="${opt.tier}">${opt.creditsCost} cr</button>
        </div>
      `).join('')}
    </div>
  `);
  document.getElementById('closeModalBtn').onclick = closeModal;
  modalContent.querySelectorAll('[data-confirm-boost]').forEach((b) => {
    b.onclick = async () => {
      const opt = POST_BOOST_OPTIONS.find((o) => o.tier === b.dataset.confirmBoost);
      if (!opt || !state.user || !state.profile) return;
      const w = state.wallet;
      if (!w || (w.balance || 0) < opt.creditsCost) {
        showToast('Not enough credits. Buy credits in the Momi Android app.');
        return;
      }
      const txId = makeClientId('boost');
      await patchSupabaseRows(SUPABASE_WEB_CONFIG, 'wallets', `user_id=eq.${encodeURIComponent(state.user.uid)}`, {
        balance: w.balance - opt.creditsCost,
        total_spent: (w.totalSpent || 0) + opt.creditsCost,
        last_tx_id: txId
      });
      await upsertSupabaseTable(SUPABASE_WEB_CONFIG, 'credit_transactions', [{
        tx_id: txId,
        type: 'POST_BOOST',
        sender_uid: state.user.uid,
        sender_username: state.profile.username,
        recipient_uid: state.user.uid,
        recipient_username: state.profile.username,
        credits_amount: opt.creditsCost,
        price_usd: 0,
        payment_method: 'WALLET_CREDITS',
        post_id: post.id,
        recipient_claimed: true,
        status: 'COMPLETED'
      }], '', globalThis.fetch, 'tx_id');
      await patchSupabaseRows(SUPABASE_WEB_CONFIG, 'posts', `id=eq.${encodeURIComponent(post.id)}`, {
        is_boosted: true,
        boost_tier: opt.tier,
        recommended_label: opt.badgeLabel,
        boosted_by_uid: state.user.uid,
        boosted_at: new Date().toISOString()
      });
      closeModal();
      await refreshAllFromSupabase({ showErrors: false });
      showToast(`${opt.title} activated across Momi!`);
    };
  });
}

async function adminToggleRecommendPost(post) {
  if (!isOwnerAdmin() || !state.profile) return;
  const currentlyRecommended = post.boostTier === 'OWNER_RECOMMENDED' || Boolean(post.recommendedLabel);
  if (currentlyRecommended) {
    await patchSupabaseRows(SUPABASE_WEB_CONFIG, 'posts', `id=eq.${encodeURIComponent(post.id)}`, {
      is_boosted: false,
      boost_tier: '',
      recommended_label: '',
      boosted_by_uid: null,
      boosted_at: null
    });
  } else {
    await patchSupabaseRows(SUPABASE_WEB_CONFIG, 'posts', `id=eq.${encodeURIComponent(post.id)}`, {
      is_boosted: true,
      boost_tier: 'OWNER_RECOMMENDED',
      recommended_label: 'Recommended by Momi',
      boosted_by_uid: state.user.uid,
      boosted_at: new Date().toISOString()
    });
    if (post.userId && post.userId !== state.user.uid) {
      await upsertSupabaseTable(SUPABASE_WEB_CONFIG, 'notifications', [{
        id: makeClientId('notif'),
        user_id: post.userId,
        actor_uid: state.user.uid,
        actor_username: state.profile.username,
        actor_avatar_key: state.profile.avatarKey || 'avatar_aria',
        message: 'featured your post under Recommended by Momi!',
        time_label: 'Just now',
        section: 'Today',
        preview_image_key: post.imageKey || 'img_post_coastal',
        is_follow_action: false,
        is_following_back: false,
        is_unread: true
      }], '', globalThis.fetch, 'id');
    }
  }
  await refreshAllFromSupabase({ showErrors: false });
  showToast(currentlyRecommended ? 'Removed Momi recommendation' : 'Post featured as Recommended by Momi!');
}

async function saveAdMonetizationConfigFromForm() {
  if (!isOwnerAdmin() || state.adminBusy) return;
  state.adminBusy = true;
  renderMainStage();
  try {
    const existingCfg = state.monetizationConfig || DEFAULT_MONETIZATION_CONFIG;
    const row = {
      id: 'monetization',
      admob_enabled: Boolean(document.getElementById('cfgAdmobEnabled').checked),
      admob_banner_unit_id: document.getElementById('cfgAdmobBanner').value.trim().slice(0, 120),
      admob_mrec_unit_id: document.getElementById('cfgAdmobMrec').value.trim().slice(0, 120),
      admob_interstitial_unit_id: document.getElementById('cfgAdmobInterstitial').value.trim().slice(0, 120),
      start_io_enabled: Boolean(document.getElementById('cfgStartIoEnabled').checked),
      partner_promo_title: (document.getElementById('cfgPromoTitle').value.trim() || DEFAULT_MONETIZATION_CONFIG.partnerPromoTitle).slice(0, 80),
      partner_promo_subtitle: (document.getElementById('cfgPromoSubtitle').value.trim() || DEFAULT_MONETIZATION_CONFIG.partnerPromoSubtitle).slice(0, 180),
      partner_promo_cta: (document.getElementById('cfgPromoCta').value.trim() || DEFAULT_MONETIZATION_CONFIG.partnerPromoCta).slice(0, 40),
      announcement_id: existingCfg.announcementId || '',
      announcement_title: existingCfg.announcementTitle || '',
      announcement_body: existingCfg.announcementBody || '',
      latest_version_name: existingCfg.latestVersionName || APP_VERSION_NAME,
      release_notes: existingCfg.releaseNotes || '',
      updated_by_email: ADMIN_OWNER_EMAIL
    };
    await upsertSupabaseTable(SUPABASE_WEB_CONFIG, 'app_config', [row], '', globalThis.fetch, 'id');
    await refreshAllFromSupabase({ showErrors: false });
    showToast('Live AdMob & Start.io config saved across Android & Web!');
  } catch (err) {
    showToast(err?.message || 'Failed saving monetization config');
  } finally {
    state.adminBusy = false;
    renderMainStage();
  }
}

function openOwnerGrantModal(creator) {
  if (!isOwnerAdmin()) return;
  openModal(`
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:14px;">
      <h3 style="margin:0;">Owner Grant for @${escapeHtml(creator.username)}</h3>
      <button type="button" class="btn-ghost btn-sm" id="closeModalBtn">Close</button>
    </div>
    <p class="muted small" style="margin-top:0;">This creator opted into Partner Boost &amp; Owner Perks consent.</p>
    <form id="ownerGrantForm" style="display:grid;gap:12px;">
      <div>
        <label class="small muted">Bonus Credits (0 - 5000)</label>
        <input id="grantCreditsInput" type="number" min="0" max="5000" value="100" class="input" />
      </div>
      <div>
        <label class="small muted">Unlock In-App Perk</label>
        <select id="grantPerkSelect" class="select">
          <option value="">None</option>
          ${WEB_PERK_CATALOG.map((p) => `<option value="${p.key}">${escapeHtml(p.name)}</option>`).join('')}
        </select>
      </div>
      <label style="display:flex;align-items:center;gap:8px;">
        <input id="grantVerifyCheck" type="checkbox" ${creator.isVerified ? 'checked' : ''} />
        <span>Grant Verified Creator Checkmark</span>
      </label>
      <button type="submit" class="btn-primary">Send Owner Grant</button>
    </form>
  `);
  document.getElementById('closeModalBtn').onclick = closeModal;
  document.getElementById('ownerGrantForm').onsubmit = async (e) => {
    e.preventDefault();
    const credits = Math.max(0, Math.min(5000, Number(document.getElementById('grantCreditsInput').value) || 0));
    const perkKey = document.getElementById('grantPerkSelect').value || null;
    const grantVerified = Boolean(document.getElementById('grantVerifyCheck').checked);
    if (credits === 0 && !perkKey && !grantVerified) {
      showToast('Select credits, a perk, or verified status to grant.');
      return;
    }
    const existingRows = await selectSupabaseTable(SUPABASE_WEB_CONFIG, 'wallets', `select=*&user_id=eq.${encodeURIComponent(creator.userId)}&limit=1`);
    const existingWallet = existingRows[0] ? mapSupabaseWalletToWeb(existingRows[0]) : null;
    const txId = makeClientId('grant');
    const prevPerks = existingWallet?.unlockedPerks || [];
    const nextPerks = perkKey ? [...new Set([...prevPerks, perkKey])] : prevPerks;
    const nextTier = tierForTotalPurchased(existingWallet?.totalPurchased || 0, nextPerks);
    const nextVerif = verificationTierForPerks(grantVerified ? 'VERIFIED' : (existingWallet?.verificationTier || 'NONE'), nextPerks);

    await upsertSupabaseTable(SUPABASE_WEB_CONFIG, 'wallets', [{
      user_id: creator.userId,
      balance: (existingWallet?.balance || 0) + credits,
      total_earned: (existingWallet?.totalEarned || 0) + credits,
      total_spent: existingWallet?.totalSpent || 0,
      total_purchased: existingWallet?.totalPurchased || 0,
      account_tier: nextTier,
      verification_tier: nextVerif,
      unlocked_perks: nextPerks,
      last_tx_id: txId
    }], '', globalThis.fetch, 'user_id');

    await upsertSupabaseTable(SUPABASE_WEB_CONFIG, 'credit_transactions', [{
      tx_id: txId,
      type: 'OWNER_GRANT',
      sender_uid: state.user.uid,
      sender_username: state.profile.username,
      recipient_uid: creator.userId,
      recipient_username: creator.username,
      credits_amount: credits,
      price_usd: 0,
      payment_method: 'OWNER_ADMIN',
      perk_key: perkKey,
      recipient_claimed: true,
      status: 'COMPLETED'
    }], '', globalThis.fetch, 'tx_id');

    if (grantVerified && !creator.isVerified) {
      await patchSupabaseRows(SUPABASE_WEB_CONFIG, 'profiles', `user_id=eq.${encodeURIComponent(creator.userId)}`, { is_verified: true });
    }
    if (creator.userId !== state.user.uid) {
      await upsertSupabaseTable(SUPABASE_WEB_CONFIG, 'notifications', [{
        id: makeClientId('notif'),
        user_id: creator.userId,
        actor_uid: state.user.uid,
        actor_username: state.profile.username,
        actor_avatar_key: state.profile.avatarKey || 'avatar_aria',
        message: 'granted you an official Momi Creator Perk & Bonus!',
        time_label: 'Just now',
        section: 'Today',
        is_follow_action: false,
        is_following_back: false,
        is_unread: true
      }], '', globalThis.fetch, 'id');
    }
    closeModal();
    await refreshAllFromSupabase({ showErrors: false });
    showToast(`Sent Owner Grant to @${creator.username}!`);
  };
}

async function runSupabaseWebHealthCheck() {
  if (!isOwnerAdmin() || state.supabaseBusy) return;
  state.supabaseBusy = true;
  if (state.nav === 'profile' || state.nav === 'admin') renderMainStage();
  try {
    const res = await checkSupabaseHealth(SUPABASE_WEB_CONFIG);
    state.supabaseStatus = res.message;
    showToast(res.message);
  } finally {
    state.supabaseBusy = false;
    if (state.nav === 'profile' || state.nav === 'admin') renderMainStage();
  }
}

async function syncSupabaseDataFromWeb() {
  if (!isOwnerAdmin() || state.supabaseBusy) return;
  state.supabaseBusy = true;
  state.supabaseStatus = 'Syncing Momi data to Supabase...';
  if (state.nav === 'profile' || state.nav === 'admin') renderMainStage();
  try {
    const bundle = mapMomiSnapshotToSupabaseBundle({
      profile: state.profile,
      creators: state.creators,
      posts: state.posts,
      stories: state.stories,
      merchants: state.merchants,
      products: state.products,
      monetizationConfig: state.monetizationConfig,
      wallet: state.wallet
    });
    const count = await syncBundleToSupabase(SUPABASE_WEB_CONFIG, bundle);
    state.supabaseStatus = `Synced ${count} live records to Supabase!`;
    showToast(state.supabaseStatus);
  } catch (err) {
    state.supabaseStatus = err?.message || 'Supabase sync failed — run supabase/enable_sync.sql in SQL Editor.';
    showToast(state.supabaseStatus);
  } finally {
    state.supabaseBusy = false;
    if (state.nav === 'profile' || state.nav === 'admin') renderMainStage();
  }
}

function downloadSupabaseMigrationSnapshot() {
  if (!isOwnerAdmin()) return;
  const bundle = mapMomiSnapshotToSupabaseBundle({
    profile: state.profile,
    creators: state.creators,
    posts: state.posts,
    stories: state.stories,
    merchants: state.merchants,
    products: state.products,
    monetizationConfig: state.monetizationConfig,
    wallet: state.wallet
  });
  const blob = new Blob([JSON.stringify(bundle, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `momi-supabase-snapshot-${new Date().toISOString().slice(0, 10)}.json`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
  showToast('Downloaded Supabase JSON snapshot!');
}

// ---------------------------------------------------------------------------
// Pages & Groups (Momi Communities)
// ---------------------------------------------------------------------------

function renderCommunities() {
  if (state.activeCommunityFilter) {
    const f = state.activeCommunityFilter;
    const communityPosts = state.posts.filter((p) =>
      f.type === 'PAGE' ? p.pageId === f.id : p.groupId === f.id
    );
    mainStage.innerHTML = `
      <div class="stage-column">
        <div class="section-header">
          <div>
            <button type="button" class="btn-ghost btn-sm" id="backToCommunitiesBtn">&larr; All Pages &amp; Groups</button>
            <h2 style="margin:6px 0 0;">${escapeHtml(f.name)}</h2>
            <div class="muted small">${f.type === 'PAGE' ? 'Creator Page' : 'Community Group'} &middot; ${communityPosts.length} posts</div>
          </div>
          <button type="button" class="btn-primary btn-sm" id="postInCommunityBtn">+ Post Here</button>
        </div>
        ${
          communityPosts.length === 0
            ? '<div class="card-panel"><p class="muted" style="margin:0;">No posts in this community yet. Tap "+ Post Here" to start the conversation!</p></div>'
            : communityPosts.map(renderPostCardHtml).join('')
        }
      </div>
    `;
    document.getElementById('backToCommunitiesBtn').onclick = () => {
      state.activeCommunityFilter = null;
      renderCommunities();
    };
    document.getElementById('postInCommunityBtn').onclick = () => {
      state.studio.pageId = f.type === 'PAGE' ? f.id : null;
      state.studio.groupId = f.type === 'GROUP' ? f.id : null;
      state.studio.communityName = f.name;
      navigateTo('create');
    };
    bindFeedInteractions(mainStage);
    return;
  }

  mainStage.innerHTML = `
    <div class="stage-wide">
      <div class="section-header">
        <div>
          <h2>Pages &amp; Groups</h2>
          <div class="muted small">Create creator pages, join interest groups, and publish directly to your communities</div>
        </div>
        <div class="chip-row">
          <button type="button" class="chip ${state.communitiesTab === 'pages' ? 'active' : ''}" id="commTabPages">Pages (${state.pages.length})</button>
          <button type="button" class="chip ${state.communitiesTab === 'groups' ? 'active' : ''}" id="commTabGroups">Groups (${state.groups.length})</button>
          <button type="button" class="btn-primary btn-sm" id="commCreateBtn">+ ${state.communitiesTab === 'pages' ? 'Create Page' : 'Create Group'}</button>
        </div>
      </div>

      ${
        state.communitiesTab === 'pages'
          ? `<div class="discovery-grid">
              ${
                state.pages.length === 0
                  ? '<div class="card-panel"><p class="muted" style="margin:0;">No Creator Pages yet. Tap "+ Create Page" to launch yours!</p></div>'
                  : state.pages.map((pg) => {
                    const isFollowed = state.pageFollowedIds.has(pg.id);
                    return `
                      <article class="community-card">
                        <div style="display:flex;align-items:center;gap:10px;">
                          ${avatarHtml({ userId: pg.ownerUid, username: pg.ownerUsername }, 42)}
                          <div style="min-width:0;flex:1;">
                            <div style="font-weight:800;font-size:0.98rem;">${escapeHtml(pg.name)} ${pg.isVerified ? '<span class="verified-dot">&#10003;</span>' : ''}</div>
                            <div class="muted small">@${escapeHtml(pg.handle || pg.ownerUsername)} &middot; ${escapeHtml(pg.category)}</div>
                          </div>
                        </div>
                        ${pg.bio ? `<div class="small muted" style="line-height:1.45;">${escapeHtml(pg.bio)}</div>` : ''}
                        <div style="display:flex;align-items:center;justify-content:space-between;gap:8px;margin-top:auto;">
                          <span class="muted small">${formatCount(pg.followersCount)} followers</span>
                          <div style="display:flex;gap:6px;">
                            <button type="button" class="btn-outline btn-sm" data-view-comm="PAGE:${escapeHtml(pg.id)}" data-comm-name="${escapeHtml(pg.name)}">Open</button>
                            <button type="button" class="btn-sm ${isFollowed ? 'btn-outline' : 'btn-primary'}" data-toggle-page="${escapeHtml(pg.id)}">${isFollowed ? 'Following' : 'Follow'}</button>
                          </div>
                        </div>
                      </article>
                    `;
                  }).join('')
              }
            </div>`
          : `<div class="discovery-grid">
              ${
                state.groups.length === 0
                  ? '<div class="card-panel"><p class="muted" style="margin:0;">No Community Groups yet. Tap "+ Create Group" to start one!</p></div>'
                  : state.groups.map((gr) => {
                    const isMember = state.groupJoinedIds.has(gr.id);
                    return `
                      <article class="community-card">
                        <div style="display:flex;align-items:center;justify-content:space-between;gap:8px;">
                          <div>
                            <div style="font-weight:800;font-size:0.98rem;">${escapeHtml(gr.name)}</div>
                            <div class="muted small">${escapeHtml(gr.category)} &middot; ${escapeHtml(gr.privacy)} Group</div>
                          </div>
                          <span class="pill">${formatCount(gr.membersCount)} members</span>
                        </div>
                        ${gr.description ? `<div class="small muted" style="line-height:1.45;">${escapeHtml(gr.description)}</div>` : ''}
                        <div style="display:flex;align-items:center;justify-content:space-between;gap:8px;margin-top:auto;">
                          <span class="muted small">Host @${escapeHtml(gr.ownerUsername)}</span>
                          <div style="display:flex;gap:6px;">
                            <button type="button" class="btn-outline btn-sm" data-view-comm="GROUP:${escapeHtml(gr.id)}" data-comm-name="${escapeHtml(gr.name)}">Open</button>
                            <button type="button" class="btn-sm ${isMember ? 'btn-outline' : 'btn-primary'}" data-toggle-group="${escapeHtml(gr.id)}">${isMember ? 'Joined' : 'Join'}</button>
                          </div>
                        </div>
                      </article>
                    `;
                  }).join('')
              }
            </div>`
      }
    </div>
  `;

  document.getElementById('commTabPages').onclick = () => { state.communitiesTab = 'pages'; renderCommunities(); };
  document.getElementById('commTabGroups').onclick = () => { state.communitiesTab = 'groups'; renderCommunities(); };
  document.getElementById('commCreateBtn').onclick = () => {
    if (state.communitiesTab === 'pages') openCreatePageModal();
    else openCreateGroupModal();
  };
  mainStage.querySelectorAll('[data-view-comm]').forEach((b) => {
    b.onclick = () => {
      const [type, id] = b.dataset.viewComm.split(':');
      state.activeCommunityFilter = { id, type, name: b.dataset.commName || 'Community' };
      renderCommunities();
    };
  });
  mainStage.querySelectorAll('[data-toggle-page]').forEach((b) => {
    b.onclick = () => toggleFollowPage(b.dataset.togglePage);
  });
  mainStage.querySelectorAll('[data-toggle-group]').forEach((b) => {
    b.onclick = () => toggleJoinGroup(b.dataset.toggleGroup);
  });
}

async function toggleFollowPage(pageId) {
  if (!state.user) return;
  const uid = state.user.uid;
  const pg = state.pages.find((x) => x.id === pageId);
  if (!pg) return;
  const already = state.pageFollowedIds.has(pageId);
  const nextCount = Math.max(0, (pg.followersCount || 0) + (already ? -1 : 1));
  pg.followersCount = nextCount;
  if (already) state.pageFollowedIds.delete(pageId);
  else state.pageFollowedIds.add(pageId);
  renderCommunities();
  if (already) {
    await deleteSupabaseRows(SUPABASE_WEB_CONFIG, 'page_follows', `page_id=eq.${encodeURIComponent(pageId)}&user_id=eq.${encodeURIComponent(uid)}`);
  } else {
    await upsertSupabaseTable(SUPABASE_WEB_CONFIG, 'page_follows', [{ id: `${uid}_${pageId}`, page_id: pageId, user_id: uid }], '', globalThis.fetch, 'id');
  }
  await patchSupabaseRows(SUPABASE_WEB_CONFIG, 'pages', `id=eq.${encodeURIComponent(pageId)}`, { followers_count: nextCount });
}

async function toggleJoinGroup(groupId) {
  if (!state.user || !state.profile) return;
  const uid = state.user.uid;
  const gr = state.groups.find((x) => x.id === groupId);
  if (!gr) return;
  const already = state.groupJoinedIds.has(groupId);
  const nextCount = Math.max(0, (gr.membersCount || 0) + (already ? -1 : 1));
  gr.membersCount = nextCount;
  if (already) state.groupJoinedIds.delete(groupId);
  else state.groupJoinedIds.add(groupId);
  renderCommunities();
  if (already) {
    await deleteSupabaseRows(SUPABASE_WEB_CONFIG, 'group_members', `group_id=eq.${encodeURIComponent(groupId)}&user_id=eq.${encodeURIComponent(uid)}`);
  } else {
    await upsertSupabaseTable(SUPABASE_WEB_CONFIG, 'group_members', [{
      id: `${uid}_${groupId}`,
      group_id: groupId,
      user_id: uid,
      username: state.profile.username,
      role: 'MEMBER'
    }], '', globalThis.fetch, 'id');
  }
  await patchSupabaseRows(SUPABASE_WEB_CONFIG, 'groups', `id=eq.${encodeURIComponent(groupId)}`, { members_count: nextCount });
}

function openCreatePageModal() {
  if (!state.profile) return;
  openModal(`
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:14px;">
      <h3 style="margin:0;">Create Momi Page</h3>
      <button type="button" class="btn-ghost btn-sm" id="closeModalBtn">Close</button>
    </div>
    <form id="createPageForm" style="display:grid;gap:12px;">
      <div>
        <label class="small muted">Page Name *</label>
        <input id="pageNameInput" class="input" placeholder="e.g. Momi Street Photography" required />
      </div>
      <div class="field-grid-2">
        <div>
          <label class="small muted">Handle</label>
          <input id="pageHandleInput" class="input" placeholder="street.photo" />
        </div>
        <div>
          <label class="small muted">Category</label>
          <select id="pageCategorySelect" class="select">
            <option value="Creator">Creator</option>
            <option value="Brand">Brand &amp; Studio</option>
            <option value="Travel">Travel</option>
            <option value="Art">Art &amp; Design</option>
            <option value="Music">Music &amp; Audio</option>
          </select>
        </div>
      </div>
      <div>
        <label class="small muted">Bio</label>
        <textarea id="pageBioInput" class="textarea" rows="3" placeholder="What is your Page about?"></textarea>
      </div>
      <button type="submit" class="btn-primary">Create Page</button>
    </form>
  `);
  document.getElementById('closeModalBtn').onclick = closeModal;
  document.getElementById('createPageForm').onsubmit = async (e) => {
    e.preventDefault();
    const name = document.getElementById('pageNameInput').value.trim().slice(0, 80);
    if (!name) return;
    const handleRaw = document.getElementById('pageHandleInput').value.trim() || name;
    const handle = handleRaw.toLowerCase().replace(/[^a-z0-9._]/g, '').slice(0, 40);
    const id = makeClientId('page');
    await ensureUserSeededInSupabase(state.user);
    await upsertSupabaseTable(SUPABASE_WEB_CONFIG, 'pages', [{
      id,
      owner_uid: state.user.uid,
      owner_username: state.profile.username,
      name,
      handle,
      category: document.getElementById('pageCategorySelect').value,
      bio: document.getElementById('pageBioInput').value.trim().slice(0, 300),
      cover_key: 'img_post_coastal',
      is_verified: Boolean(state.profile.isVerified),
      followers_count: 1
    }], '', globalThis.fetch, 'id');
    await upsertSupabaseTable(SUPABASE_WEB_CONFIG, 'page_follows', [{ id: `${state.user.uid}_${id}`, page_id: id, user_id: state.user.uid }], '', globalThis.fetch, 'id');
    closeModal();
    await refreshAllFromSupabase({ showErrors: false });
    showToast(`Page "${name}" created!`);
  };
}

function openCreateGroupModal() {
  if (!state.profile) return;
  openModal(`
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:14px;">
      <h3 style="margin:0;">Create Community Group</h3>
      <button type="button" class="btn-ghost btn-sm" id="closeModalBtn">Close</button>
    </div>
    <form id="createGroupForm" style="display:grid;gap:12px;">
      <div>
        <label class="small muted">Group Name *</label>
        <input id="groupNameInput" class="input" placeholder="e.g. Film Shooters Club" required />
      </div>
      <div class="field-grid-2">
        <div>
          <label class="small muted">Category</label>
          <select id="groupCategorySelect" class="select">
            <option value="Community">Community</option>
            <option value="Travel">Travel</option>
            <option value="Coffee">Food &amp; Coffee</option>
            <option value="Art">Art &amp; Design</option>
            <option value="City">City Life</option>
          </select>
        </div>
        <div>
          <label class="small muted">Privacy</label>
          <select id="groupPrivacySelect" class="select">
            <option value="PUBLIC">Public Group</option>
            <option value="PRIVATE">Private Group</option>
          </select>
        </div>
      </div>
      <div>
        <label class="small muted">Description</label>
        <textarea id="groupDescInput" class="textarea" rows="3" placeholder="Group rules and topics..."></textarea>
      </div>
      <button type="submit" class="btn-primary">Create Group</button>
    </form>
  `);
  document.getElementById('closeModalBtn').onclick = closeModal;
  document.getElementById('createGroupForm').onsubmit = async (e) => {
    e.preventDefault();
    const name = document.getElementById('groupNameInput').value.trim().slice(0, 80);
    if (!name) return;
    const id = makeClientId('grp');
    await ensureUserSeededInSupabase(state.user);
    await upsertSupabaseTable(SUPABASE_WEB_CONFIG, 'groups', [{
      id,
      owner_uid: state.user.uid,
      owner_username: state.profile.username,
      name,
      category: document.getElementById('groupCategorySelect').value,
      description: document.getElementById('groupDescInput').value.trim().slice(0, 300),
      privacy: document.getElementById('groupPrivacySelect').value,
      cover_key: 'img_post_architecture',
      members_count: 1
    }], '', globalThis.fetch, 'id');
    await upsertSupabaseTable(SUPABASE_WEB_CONFIG, 'group_members', [{
      id: `${state.user.uid}_${id}`,
      group_id: id,
      user_id: state.user.uid,
      username: state.profile.username,
      role: 'ADMIN'
    }], '', globalThis.fetch, 'id');
    closeModal();
    await refreshAllFromSupabase({ showErrors: false });
    showToast(`Group "${name}" created!`);
  };
}

// ---------------------------------------------------------------------------
// Dedicated Owner Admin Console & Showcase Starter Management
// ---------------------------------------------------------------------------

function renderAdminConsole() {
  if (!isOwnerAdmin()) {
    mainStage.innerHTML = `
      <div class="stage-column">
        <div class="card-panel" style="text-align:center;padding:36px;">
          <h2 style="margin:0 0 8px;">Owner Console Restricted</h2>
          <p class="muted" style="margin:0 0 16px;">Sign in with the owner account (${escapeHtml(ADMIN_OWNER_EMAIL)}) to manage Showcase Starters, In-App Announcements, and Monetization.</p>
          <button type="button" class="btn-primary" id="adminBackHomeBtn">Return Home</button>
        </div>
      </div>
    `;
    document.getElementById('adminBackHomeBtn').onclick = () => navigateTo('home');
    return;
  }

  const cfg = state.monetizationConfig || DEFAULT_MONETIZATION_CONFIG;
  const consentingCreators = state.creators.filter((c) => Boolean(c.partnerConsent));
  const recommendCandidates = state.posts.filter((p) => !p.id.startsWith('showcase_')).slice(0, 15);
  const showcasePostCount = state.posts.filter((p) => p.id.startsWith('showcase_')).length;
  const showcaseStoryCount = state.stories.filter((s) => s.id.startsWith('showcase_')).length;
  const starters = state.showcaseAccounts.length > 0
    ? state.showcaseAccounts
    : SHOWCASE_PERSONAS.map((p) => ({
      username: p.username,
      displayName: p.displayName,
      avatarKey: p.avatarKey,
      bio: `${p.category} creator in ${p.location}`,
      category: p.category,
      location: p.location,
      isAuthorized: false
    }));

  mainStage.innerHTML = `
    <div class="stage-wide">
      <div class="card-panel">
        <div class="section-header">
          <div>
            <h2 style="margin:0;">Owner Admin Console</h2>
            <div class="muted small">Signed in as <strong>${escapeHtml(state.user?.email || ADMIN_OWNER_EMAIL)}</strong> &middot; Web &amp; Android live sync</div>
          </div>
          <div class="chip-row">
            <button type="button" class="chip ${state.ownerConsoleTab === 'starters' ? 'active' : ''}" data-admin-tab="starters">Showcase Starters (${starters.filter((s) => s.isAuthorized).length}/${starters.length})</button>
            <button type="button" class="chip ${state.ownerConsoleTab === 'announcements' ? 'active' : ''}" data-admin-tab="announcements">In-App Updates</button>
            <button type="button" class="chip ${state.ownerConsoleTab === 'monetizers' ? 'active' : ''}" data-admin-tab="monetizers">Monetizers</button>
            <button type="button" class="chip ${state.ownerConsoleTab === 'grants' ? 'active' : ''}" data-admin-tab="grants">Grants (${consentingCreators.length})</button>
            <button type="button" class="chip ${state.ownerConsoleTab === 'recommend' ? 'active' : ''}" data-admin-tab="recommend">Recommend Posts</button>
            <button type="button" class="chip ${state.ownerConsoleTab === 'supabase' ? 'active' : ''}" data-admin-tab="supabase">Supabase</button>
          </div>
        </div>

        ${state.ownerConsoleTab === 'starters' ? `
          <div style="display:grid;gap:14px;">
            <div class="consent-row" style="flex-wrap:wrap;gap:10px;">
              <div>
                <strong>Authorized Showcase Starters (${showcasePostCount} posts &middot; ${showcaseStoryCount} stories)</strong>
                <div class="muted small">Authorize starter accounts, edit their profile bio/name, publish custom photo/video posts as any authorized starter, or boost posts with starter likes, views &amp; comments.</div>
              </div>
              <div class="chip-row">
                <button type="button" class="btn-primary btn-sm" id="adminBoostPostBtn">Add Likes / Views</button>
                <button type="button" class="btn-outline btn-sm" id="showcaseBurstBtn" ${state.showcaseBusy ? 'disabled' : ''}>Publish 4 Burst Posts</button>
                <button type="button" class="btn-outline btn-sm" id="showcaseEngageBtn" ${state.showcaseBusy ? 'disabled' : ''}>Engage Recent</button>
                <button type="button" class="btn-danger btn-sm" id="showcaseRemoveBtn" ${state.showcaseBusy ? 'disabled' : ''}>Clear Starter Posts</button>
              </div>
            </div>

            <div class="discovery-grid">
              ${starters.map((st) => `
                <article class="card-panel" style="gap:10px;${st.isAuthorized ? 'border-color:var(--primary);' : ''}">
                  <div style="display:flex;align-items:center;gap:10px;">
                    ${avatarHtml({ userId: st.username, username: st.username }, 44)}
                    <div style="min-width:0;flex:1;">
                      <div style="font-weight:700;">@${escapeHtml(st.username)}</div>
                      <div class="muted small">${escapeHtml(st.displayName)} &middot; ${escapeHtml(st.category)}</div>
                    </div>
                    <span class="pill" style="${st.isAuthorized ? 'background:color-mix(in srgb, var(--primary) 18%, transparent);color:var(--primary);' : ''}">
                      ${st.isAuthorized ? 'Authorized' : 'Paused'}
                    </span>
                  </div>
                  <div class="small muted">${escapeHtml(st.bio || `${st.category} in ${st.location}`)}</div>
                  <div class="chip-row" style="margin-top:auto;">
                    <button type="button" class="btn-sm ${st.isAuthorized ? 'btn-outline' : 'btn-primary'}" data-toggle-starter="${escapeHtml(st.username)}">
                      ${st.isAuthorized ? 'Pause' : 'Authorize'}
                    </button>
                    <button type="button" class="btn-outline btn-sm" data-edit-starter="${escapeHtml(st.username)}">Edit Profile</button>
                    <button type="button" class="btn-primary btn-sm" data-post-starter="${escapeHtml(st.username)}" ${!st.isAuthorized ? 'disabled title="Authorize starter first"' : ''}>Post / Video</button>
                  </div>
                </article>
              `).join('')}
            </div>
          </div>
        ` : ''}

        ${state.ownerConsoleTab === 'announcements' ? `
          <form id="ownerAnnouncementForm" style="display:grid;gap:12px;">
            <p class="muted small" style="margin:0;">Publish an In-App Announcement &amp; Release Notes banner to all Android and Web users instantly — no APK redownload needed.</p>
            <div class="owner-form-grid">
              <div>
                <label class="small muted">Latest Version Label</label>
                <input id="annVersionInput" class="input" value="${escapeHtml(cfg.latestVersionName || APP_VERSION_NAME)}" placeholder="${escapeHtml(APP_VERSION_NAME)}" />
              </div>
              <div>
                <label class="small muted">Announcement Title *</label>
                <input id="annTitleInput" class="input" value="${escapeHtml(cfg.announcementTitle || '')}" placeholder="e.g. Pages, Groups & Smooth Live Rooms are live!" required />
              </div>
            </div>
            <div>
              <label class="small muted">Announcement Body</label>
              <textarea id="annBodyInput" class="textarea" rows="3" placeholder="Tell creators what changed...">${escapeHtml(cfg.announcementBody || '')}</textarea>
            </div>
            <div>
              <label class="small muted">Release Notes Summary</label>
              <input id="annNotesInput" class="input" value="${escapeHtml(cfg.releaseNotes || '')}" placeholder="e.g. Pages & Groups, 2-color metallic icon, smooth feed sync" />
            </div>
            <div class="chip-row">
              <button type="submit" class="btn-primary" ${state.adminBusy ? 'disabled' : ''}>Publish In-App Announcement</button>
              <button type="button" class="btn-outline btn-sm" id="clearAnnouncementBtn">Clear Active Banner</button>
            </div>
          </form>
        ` : ''}

        ${state.ownerConsoleTab === 'monetizers' ? `
          <form id="ownerMonetizationForm" style="display:grid;gap:12px;">
            <div class="owner-form-grid">
              <label class="consent-row"><span>Enable AdMob (Android)</span><input id="cfgAdmobEnabled" type="checkbox" ${cfg.admobEnabled ? 'checked' : ''} /></label>
              <label class="consent-row"><span>Enable Start.io (Android)</span><input id="cfgStartIoEnabled" type="checkbox" ${cfg.startIoEnabled ? 'checked' : ''} /></label>
            </div>
            <div class="owner-form-grid">
              <div>
                <label class="small muted">AdMob Banner Unit ID</label>
                <input id="cfgAdmobBanner" class="input" placeholder="ca-app-pub-.../..." value="${escapeHtml(cfg.admobBannerUnitId || '')}" />
              </div>
              <div>
                <label class="small muted">AdMob MREC Unit ID</label>
                <input id="cfgAdmobMrec" class="input" placeholder="ca-app-pub-.../..." value="${escapeHtml(cfg.admobMrecUnitId || '')}" />
              </div>
              <div>
                <label class="small muted">AdMob Interstitial Unit ID</label>
                <input id="cfgAdmobInterstitial" class="input" placeholder="ca-app-pub-.../..." value="${escapeHtml(cfg.admobInterstitialUnitId || '')}" />
              </div>
            </div>
            <div class="owner-form-grid">
              <div>
                <label class="small muted">Creator Showcase Card Title</label>
                <input id="cfgPromoTitle" class="input" value="${escapeHtml(cfg.partnerPromoTitle || '')}" />
              </div>
              <div>
                <label class="small muted">Creator Showcase CTA</label>
                <input id="cfgPromoCta" class="input" value="${escapeHtml(cfg.partnerPromoCta || '')}" />
              </div>
            </div>
            <div>
              <label class="small muted">Creator Showcase Subtitle</label>
              <input id="cfgPromoSubtitle" class="input" value="${escapeHtml(cfg.partnerPromoSubtitle || '')}" />
            </div>
            <div style="display:flex;gap:10px;flex-wrap:wrap;align-items:center;">
              <button type="submit" class="btn-primary" ${state.adminBusy ? 'disabled' : ''}>Save Live Monetization Config</button>
            </div>
          </form>
        ` : ''}

        ${state.ownerConsoleTab === 'grants' ? `
          <div style="display:grid;gap:10px;">
            ${
              consentingCreators.length === 0
                ? '<p class="muted small" style="margin:0;">No consenting creators opted in yet. Creators can opt in from Wallet or Profile.</p>'
                : consentingCreators.map((c) => `
                  <div class="creator-grant-row">
                    <div style="display:flex;align-items:center;justify-content:space-between;gap:10px;flex-wrap:wrap;">
                      <div>
                        <strong>@${escapeHtml(c.username)}</strong> ${c.isVerified ? '<span class="verified-dot">&#10003;</span>' : ''}
                        <div class="muted small">${escapeHtml(c.displayName)} &middot; Opted In</div>
                      </div>
                      <button type="button" class="btn-outline btn-sm" data-grant-open="${escapeHtml(c.userId)}">Grant Credits / Perk</button>
                    </div>
                  </div>
                `).join('')
            }
          </div>
        ` : ''}

        ${state.ownerConsoleTab === 'recommend' ? `
          <div style="display:grid;gap:10px;">
            ${
              recommendCandidates.length === 0
                ? '<p class="muted small" style="margin:0;">No creator posts synced yet.</p>'
                : recommendCandidates.map((p) => {
                  const isRec = p.boostTier === 'OWNER_RECOMMENDED' || Boolean(p.recommendedLabel);
                  return `
                    <div class="consent-row">
                      <div style="min-width:0;flex:1;">
                        <strong>@${escapeHtml(p.authorUsername)}</strong>
                        <div class="muted small" style="overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">${escapeHtml(p.caption || p.category || 'Post')}</div>
                      </div>
                      <button type="button" class="btn-sm ${isRec ? 'btn-outline' : 'btn-primary'}" data-admin-recommend="${escapeHtml(p.id)}">
                        ${isRec ? 'Remove Rec' : 'Recommend'}
                      </button>
                    </div>
                  `;
                }).join('')
            }
          </div>
        ` : ''}

        ${state.ownerConsoleTab === 'supabase' ? `
          <div style="display:grid;gap:12px;">
            <div style="display:flex;align-items:center;justify-content:space-between;gap:10px;flex-wrap:wrap;">
              <div>
                <strong>Supabase PostgreSQL &amp; Storage (Primary Backend)</strong>
                <div class="muted small">${escapeHtml(state.supabaseStatus)}</div>
              </div>
              <span class="pill">${isSupabaseClientReady(SUPABASE_WEB_CONFIG) ? 'Configured ✓' : 'Pending'}</span>
            </div>
            <div style="display:flex;gap:10px;flex-wrap:wrap;align-items:center;">
              <button type="button" class="btn-outline btn-sm" id="testSupabaseWebBtn" ${state.supabaseBusy ? 'disabled' : ''}>
                ${state.supabaseBusy ? 'Testing...' : 'Test Connection'}
              </button>
              <button type="button" class="btn-primary btn-sm" id="syncSupabaseWebBtn" ${state.supabaseBusy ? 'disabled' : ''}>
                ${state.supabaseBusy ? 'Syncing...' : 'Sync Snapshot'}
              </button>
              <button type="button" class="btn-outline btn-sm" id="downloadSupabaseJsonBtn">
                Export JSON
              </button>
              <a class="btn-outline btn-sm" href="https://supabase.com/dashboard/project/qotuvugkapaspowhbsbf/sql/new" target="_blank" rel="noopener noreferrer" style="text-decoration:none;">
                SQL Editor &#8599;
              </a>
            </div>
          </div>
        ` : ''}
      </div>
    </div>
  `;

  mainStage.querySelectorAll('[data-admin-tab]').forEach((b) => {
    b.onclick = () => { state.ownerConsoleTab = b.dataset.adminTab; renderAdminConsole(); };
  });
  mainStage.querySelectorAll('[data-toggle-starter]').forEach((b) => {
    b.onclick = () => toggleShowcaseAuthorize(b.dataset.toggleStarter);
  });
  mainStage.querySelectorAll('[data-edit-starter]').forEach((b) => {
    b.onclick = () => openEditStarterModal(b.dataset.editStarter);
  });
  mainStage.querySelectorAll('[data-post-starter]').forEach((b) => {
    b.onclick = () => openPublishAsStarterModal(b.dataset.postStarter);
  });
  document.getElementById('adminBoostPostBtn')?.addEventListener('click', () => openBoostWithStarterModal());
  document.getElementById('showcaseBurstBtn')?.addEventListener('click', () => publishShowcaseBurst(state.user.uid));
  document.getElementById('showcaseEngageBtn')?.addEventListener('click', () => engagePostsWithShowcase(state.user.uid));
  document.getElementById('showcaseRemoveBtn')?.addEventListener('click', () => removeAllShowcaseContent());
  document.getElementById('ownerAnnouncementForm')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    await saveInAppAnnouncementFromForm();
  });
  document.getElementById('clearAnnouncementBtn')?.addEventListener('click', async () => {
    await saveInAppAnnouncementFromForm({ clear: true });
  });
  const monForm = document.getElementById('ownerMonetizationForm');
  if (monForm) {
    monForm.onsubmit = async (e) => {
      e.preventDefault();
      await saveAdMonetizationConfigFromForm();
    };
  }
  mainStage.querySelectorAll('[data-grant-open]').forEach((b) => {
    b.onclick = () => {
      const target = state.creators.find((c) => c.userId === b.dataset.grantOpen);
      if (target) openOwnerGrantModal(target);
    };
  });
  document.getElementById('testSupabaseWebBtn')?.addEventListener('click', () => runSupabaseWebHealthCheck());
  document.getElementById('syncSupabaseWebBtn')?.addEventListener('click', () => syncSupabaseDataFromWeb());
  document.getElementById('downloadSupabaseJsonBtn')?.addEventListener('click', () => downloadSupabaseMigrationSnapshot());
  bindFeedInteractions(mainStage);
}

async function toggleShowcaseAuthorize(username) {
  if (!isOwnerAdmin()) return;
  const acct = state.showcaseAccounts.find((a) => a.username === username);
  if (!acct) return;
  const nextVal = !acct.isAuthorized;
  acct.isAuthorized = nextVal;
  renderAdminConsole();
  await upsertSupabaseTable(SUPABASE_WEB_CONFIG, 'showcase_accounts', [{
    username: acct.username,
    display_name: acct.displayName,
    avatar_key: acct.avatarKey,
    bio: acct.bio,
    category: acct.category,
    location: acct.location,
    is_authorized: nextVal
  }], '', globalThis.fetch, 'username');
  showToast(nextVal ? `Authorized @${username}` : `Paused @${username}`);
}

function openEditStarterModal(username) {
  if (!isOwnerAdmin()) return;
  const acct = state.showcaseAccounts.find((a) => a.username === username);
  if (!acct) return;
  openModal(`
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:14px;">
      <h3 style="margin:0;">Edit Starter @${escapeHtml(acct.username)}</h3>
      <button type="button" class="btn-ghost btn-sm" id="closeModalBtn">Close</button>
    </div>
    <form id="editStarterForm" style="display:grid;gap:12px;">
      <div>
        <label class="small muted">Display Name</label>
        <input id="stDisplayName" class="input" value="${escapeHtml(acct.displayName)}" required />
      </div>
      <div class="field-grid-2">
        <div>
          <label class="small muted">Category</label>
          <input id="stCategory" class="input" value="${escapeHtml(acct.category)}" />
        </div>
        <div>
          <label class="small muted">Location</label>
          <input id="stLocation" class="input" value="${escapeHtml(acct.location)}" />
        </div>
      </div>
      <div>
        <label class="small muted">Bio</label>
        <textarea id="stBio" class="textarea" rows="3">${escapeHtml(acct.bio)}</textarea>
      </div>
      <label class="consent-row">
        <span>Authorized to Publish &amp; Engage</span>
        <input id="stAuthorized" type="checkbox" ${acct.isAuthorized ? 'checked' : ''} />
      </label>
      <button type="submit" class="btn-primary">Save Starter Profile</button>
    </form>
  `);
  document.getElementById('closeModalBtn').onclick = closeModal;
  document.getElementById('editStarterForm').onsubmit = async (e) => {
    e.preventDefault();
    const row = {
      username: acct.username,
      display_name: document.getElementById('stDisplayName').value.trim().slice(0, 80) || acct.displayName,
      avatar_key: acct.avatarKey,
      category: document.getElementById('stCategory').value.trim().slice(0, 60) || acct.category,
      location: document.getElementById('stLocation').value.trim().slice(0, 120),
      bio: document.getElementById('stBio').value.trim().slice(0, 300),
      is_authorized: Boolean(document.getElementById('stAuthorized').checked)
    };
    await upsertSupabaseTable(SUPABASE_WEB_CONFIG, 'showcase_accounts', [row], '', globalThis.fetch, 'username');
    closeModal();
    await refreshAllFromSupabase({ showErrors: false });
    showToast(`Updated starter @${acct.username}`);
  };
}

function openPublishAsStarterModal(username) {
  if (!isOwnerAdmin()) return;
  const acct = state.showcaseAccounts.find((a) => a.username === username);
  if (!acct || !acct.isAuthorized) {
    showToast('Authorize this starter account first.');
    return;
  }
  openModal(`
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:14px;">
      <h3 style="margin:0;">Publish Post/Video as @${escapeHtml(acct.username)}</h3>
      <button type="button" class="btn-ghost btn-sm" id="closeModalBtn">Close</button>
    </div>
    <form id="publishStarterForm" style="display:grid;gap:12px;">
      <div>
        <label class="small muted">Caption *</label>
        <textarea id="stPostCaption" class="textarea" rows="3" placeholder="Write caption with #hashtags..." required></textarea>
      </div>
      <div class="field-grid-2">
        <div>
          <label class="small muted">Photo Upload (optional)</label>
          <input id="stPostPhoto" type="file" accept="image/*" class="input" />
        </div>
        <div>
          <label class="small muted">Video Upload (.mp4, optional)</label>
          <input id="stPostVideo" type="file" accept="video/*" class="input" />
        </div>
      </div>
      <div class="field-grid-2">
        <div>
          <label class="small muted">Initial Likes</label>
          <input id="stInitLikes" type="number" min="0" max="5000" value="24" class="input" />
        </div>
        <div>
          <label class="small muted">Initial Views</label>
          <input id="stInitViews" type="number" min="0" max="50000" value="180" class="input" />
        </div>
      </div>
      <button type="submit" class="btn-primary" id="stPostSubmitBtn">Publish as @${escapeHtml(acct.username)}</button>
    </form>
  `);
  document.getElementById('closeModalBtn').onclick = closeModal;
  document.getElementById('publishStarterForm').onsubmit = async (e) => {
    e.preventDefault();
    const btn = document.getElementById('stPostSubmitBtn');
    btn.disabled = true;
    btn.textContent = 'Publishing...';
    try {
      const postId = `showcase_${acct.username.replace(/[^a-z0-9]/gi, '_')}_${Date.now()}`;
      const caption = document.getElementById('stPostCaption').value.trim().slice(0, 1000);
      const photoFile = document.getElementById('stPostPhoto').files?.[0];
      const videoFile = document.getElementById('stPostVideo').files?.[0];
      let imageUrl = null;
      let videoUrl = null;
      if (photoFile) {
        imageUrl = await uploadWebFileToStorage(SUPABASE_WEB_CONFIG, 'momi-media', `posts/${state.user.uid}/${postId}.jpg`, photoFile, photoFile.type || 'image/jpeg');
      }
      if (videoFile) {
        const ext = (videoFile.name.split('.').pop() || 'mp4').toLowerCase().replace(/[^a-z0-9]/g, '') || 'mp4';
        videoUrl = await uploadWebFileToStorage(SUPABASE_WEB_CONFIG, 'momi-videos', `videos/${state.user.uid}/${postId}.${ext}`, videoFile, videoFile.type || 'video/mp4');
      }
      const postRow = {
        id: postId,
        user_id: state.user.uid,
        author_username: acct.username,
        author_display_name: acct.displayName,
        author_avatar_key: acct.avatarKey || 'avatar_elena',
        author_verified: true,
        location: acct.location || '',
        image_key: 'img_post_coastal',
        image_url: imageUrl,
        media_type: videoUrl ? 'VIDEO' : 'IMAGE',
        video_url: videoUrl,
        hashtags: extractHashtags(caption),
        tagged_usernames: extractMentions(caption),
        caption,
        likes_count: Math.max(0, Number(document.getElementById('stInitLikes').value) || 0),
        views_count: Math.max(0, Number(document.getElementById('stInitViews').value) || 0),
        comments_count: 0,
        category: acct.category || 'Travel',
        is_reel: Boolean(videoUrl)
      };
      const mapped = mapSupabasePostToWeb(postRow);
      state.pendingMutations.createdPosts.set(postId, { post: mapped, expiresAt: Date.now() + 12000 });
      await upsertSupabaseTable(SUPABASE_WEB_CONFIG, 'posts', [postRow], '', globalThis.fetch, 'id');
      closeModal();
      await refreshAllFromSupabase({ showErrors: false });
      showToast(`Published post as @${acct.username}!`);
    } catch (err) {
      btn.disabled = false;
      btn.textContent = `Publish as @${acct.username}`;
      showToast(err?.message || 'Starter publish failed');
    }
  };
}

function openBoostWithStarterModal() {
  if (!isOwnerAdmin()) return;
  const authStarters = state.showcaseAccounts.filter((s) => s.isAuthorized);
  const candidates = state.posts.slice(0, 15);
  if (candidates.length === 0) {
    showToast('No posts available to engage yet.');
    return;
  }
  openModal(`
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:14px;">
      <h3 style="margin:0;">Add Starter Likes, Views &amp; Comment</h3>
      <button type="button" class="btn-ghost btn-sm" id="closeModalBtn">Close</button>
    </div>
    <form id="boostStarterForm" style="display:grid;gap:12px;">
      <div>
        <label class="small muted">Target Post</label>
        <select id="boostTargetPost" class="select">
          ${candidates.map((p) => `<option value="${escapeHtml(p.id)}">@${escapeHtml(p.authorUsername)}: ${escapeHtml((p.caption || 'Post').slice(0, 48))}</option>`).join('')}
        </select>
      </div>
      <div>
        <label class="small muted">Starter Account</label>
        <select id="boostStarterAcct" class="select">
          ${(authStarters.length > 0 ? authStarters : state.showcaseAccounts).map((s) => `<option value="${escapeHtml(s.username)}">@${escapeHtml(s.username)}</option>`).join('')}
        </select>
      </div>
      <div class="field-grid-2">
        <div>
          <label class="small muted">Add Likes</label>
          <input id="boostAddLikes" type="number" min="0" max="500" value="5" class="input" />
        </div>
        <div>
          <label class="small muted">Add Views</label>
          <input id="boostAddViews" type="number" min="0" max="5000" value="40" class="input" />
        </div>
      </div>
      <div>
        <label class="small muted">Optional Comment</label>
        <input id="boostCommentText" class="input" placeholder="e.g. Incredible framing!" />
      </div>
      <button type="submit" class="btn-primary">Apply Starter Engagement</button>
    </form>
  `);
  document.getElementById('closeModalBtn').onclick = closeModal;
  document.getElementById('boostStarterForm').onsubmit = async (e) => {
    e.preventDefault();
    const postId = document.getElementById('boostTargetPost').value;
    const starterUser = document.getElementById('boostStarterAcct').value;
    const addLikes = Math.max(0, Number(document.getElementById('boostAddLikes').value) || 0);
    const addViews = Math.max(0, Number(document.getElementById('boostAddViews').value) || 0);
    const commentText = document.getElementById('boostCommentText').value.trim().slice(0, 500);
    const post = state.posts.find((p) => p.id === postId);
    if (!post) return;

    const nextLikes = (post.likesCount || 0) + addLikes;
    const nextViews = (post.viewsCount || 0) + addViews;
    const nextComments = (post.commentsCount || 0) + (commentText ? 1 : 0);
    state.pendingMutations.minLikes.set(postId, { value: nextLikes, expiresAt: Date.now() + 12000 });
    state.pendingMutations.minViews.set(postId, { value: nextViews, expiresAt: Date.now() + 12000 });

    if (commentText) {
      await upsertSupabaseTable(SUPABASE_WEB_CONFIG, 'comments', [{
        id: `showcase_cmt_${Date.now()}`,
        post_id: postId,
        user_id: state.user.uid,
        author_username: starterUser,
        author_avatar_key: 'avatar_elena',
        text: commentText,
        likes_count: 1
      }], '', globalThis.fetch, 'id');
    }
    await patchSupabaseRows(SUPABASE_WEB_CONFIG, 'posts', `id=eq.${encodeURIComponent(postId)}`, {
      likes_count: nextLikes,
      views_count: nextViews,
      comments_count: nextComments
    });
    closeModal();
    await refreshAllFromSupabase({ showErrors: false });
    showToast(`Added +${addLikes} likes and +${addViews} views!`);
  };
}

async function saveInAppAnnouncementFromForm({ clear = false } = {}) {
  if (!isOwnerAdmin() || state.adminBusy) return;
  state.adminBusy = true;
  renderAdminConsole();
  try {
    const cfg = state.monetizationConfig || DEFAULT_MONETIZATION_CONFIG;
    const row = {
      id: 'monetization',
      admob_enabled: Boolean(cfg.admobEnabled),
      admob_banner_unit_id: cfg.admobBannerUnitId || '',
      admob_mrec_unit_id: cfg.admobMrecUnitId || '',
      admob_interstitial_unit_id: cfg.admobInterstitialUnitId || '',
      start_io_enabled: Boolean(cfg.startIoEnabled),
      partner_promo_title: cfg.partnerPromoTitle || DEFAULT_MONETIZATION_CONFIG.partnerPromoTitle,
      partner_promo_subtitle: cfg.partnerPromoSubtitle || DEFAULT_MONETIZATION_CONFIG.partnerPromoSubtitle,
      partner_promo_cta: cfg.partnerPromoCta || DEFAULT_MONETIZATION_CONFIG.partnerPromoCta,
      announcement_id: clear ? '' : `ann_${Date.now()}`,
      announcement_title: clear ? '' : document.getElementById('annTitleInput').value.trim().slice(0, 120),
      announcement_body: clear ? '' : document.getElementById('annBodyInput').value.trim().slice(0, 500),
      latest_version_name: document.getElementById('annVersionInput')?.value.trim().slice(0, 32) || APP_VERSION_NAME,
      release_notes: clear ? '' : document.getElementById('annNotesInput').value.trim().slice(0, 400),
      updated_by_email: ADMIN_OWNER_EMAIL
    };
    await upsertSupabaseTable(SUPABASE_WEB_CONFIG, 'app_config', [row], '', globalThis.fetch, 'id');
    await refreshAllFromSupabase({ showErrors: false });
    showToast(clear ? 'Cleared In-App Announcement banner' : 'Published In-App Announcement to Web & Android!');
  } catch (err) {
    showToast(err?.message || 'Could not save announcement');
  } finally {
    state.adminBusy = false;
    renderAdminConsole();
  }
}

// ---------------------------------------------------------------------------
// Momi AI Assistant Modal (Built-in Smart Assistant + Optional Gemini Key)
// ---------------------------------------------------------------------------

function buildLocalMomiAiReply(promptText) {
  const q = String(promptText || '').toLowerCase();
  const handle = state.profile?.username ? `@${state.profile.username}` : 'creator';
  if (/caption|photo|post|pic|frame/.test(q)) {
    return `Here are 3 ready-to-post captions for ${handle}:\n1. Golden hour light hitting every corner just right. #momi #photography #goldenhour\n2. Keeping today quiet, crisp, and intentional. #aesthetic #minimal #momi\n3. One frame from today worth saving for later. #street #travel #momi`;
  }
  if (/video|clip|hook|short|film/.test(q)) {
    return `High-retention Video Clip hooks for ${handle}:\n- "Wait for the final 2 seconds of today's cut..."\n- "3 lighting changes in 10 seconds — shot on Momi Studio."\n- "Behind the scenes before the sunset faded (#clips #momi #studio)."`;
  }
  if (/bio|profile|dp|page|group/.test(q)) {
    return `Bio & Community tips:\n- Keep your Momi bio to 1 crisp line + location + focus (e.g. "Visual Diary | Street & Cafe Frames | Co-host @momi").\n- Launch a Page for curated drops and a Public Group for daily creator feedback.`;
  }
  if (/grow|like|view|reach|engag/.test(q)) {
    return `Momi Growth Checklist:\n1. Post during golden-hour evening hours with 3-5 focused #hashtags.\n2. Share a 24h Story right after publishing a new post or video clip.\n3. Reply to early viewers & comment on creator notes inside Explore.`;
  }
  return `Momi AI Assistant Ready (${handle}):\nI can draft captions, #hashtags, video clip hooks, bio refreshes, and Page/Group prompts for your next upload. Ask me anything like "Write a travel caption" or paste your Gemini API key above for live Gemini 2.5 Flash replies!`;
}

async function generateMomiAiResponse(promptText) {
  const savedKey = String(localStorage.getItem('momi_gemini_api_key') || '').trim();
  if (savedKey) {
    try {
      const res = await fetch(
        `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${encodeURIComponent(savedKey)}`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            systemInstruction: {
              parts: [{ text: 'You are Momi AI, a concise, upbeat social creator assistant inside Momi (a visual social app for photos, video clips, 24h stories, live rooms, Pages & Groups, and creator marketplace).' }]
            },
            contents: [{ role: 'user', parts: [{ text: promptText }] }]
          })
        }
      );
      if (res.ok) {
        const data = await res.json();
        const txt = data?.candidates?.[0]?.content?.parts?.[0]?.text;
        if (txt && txt.trim()) return { text: txt.trim(), source: 'Gemini 2.5 Flash' };
      }
    } catch {
      // Fall back cleanly to built-in Momi assistant
    }
  }
  return { text: buildLocalMomiAiReply(promptText), source: 'Momi Built-in AI' };
}

function openAiAssistantModal() {
  const templates = [
    { label: 'Travel Photo', caption: 'Golden hour shadows and calm streets along the coast today. #travel #momi #goldenhour' },
    { label: 'Video Clip Hook', caption: '3 quick frames from today’s studio session — wait for the final cut! #clips #studio #momi' },
    { label: 'Community Group Poll', caption: 'Which color palette fits this weekend’s drop better? Tell me below! #community #design #momi' },
    { label: 'Creator Page Intro', caption: 'Welcome to our official Momi Creator Page — behind-the-scenes drops, live Q&As & tutorials every week. #creator #momi' }
  ];
  const savedKey = String(localStorage.getItem('momi_gemini_api_key') || '').trim();
  const statusLabel = savedKey ? 'Gemini 2.5 Flash Connected' : 'Momi Built-in AI Ready';

  openModal(`
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;">
      <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap;">
        <h3 style="margin:0;">Momi AI Assistant</h3>
        <span class="badge-pill" id="aiStatusPill">${escapeHtml(statusLabel)}</span>
      </div>
      <button type="button" class="btn-ghost btn-sm" id="closeModalBtn">Close</button>
    </div>

    <div class="ai-chat-thread" id="aiChatThread">
      ${state.aiChatHistory.map((m) => `
        <div class="ai-msg ${m.role === 'user' ? 'user' : 'assistant'}">${escapeHtml(m.text).replace(/\n/g, '<br>')}</div>
      `).join('')}
    </div>

    <form id="aiChatForm" style="display:flex;gap:8px;margin-top:10px;">
      <input id="aiChatInput" class="input" placeholder="Ask Momi AI for captions, video hooks, bio ideas..." required />
      <button type="submit" class="btn-primary btn-sm" id="aiSendBtn" ${state.aiBusy ? 'disabled' : ''}>${state.aiBusy ? 'Thinking...' : 'Ask AI'}</button>
    </form>

    <div class="chip-row" style="margin-top:10px;">
      <button type="button" class="chip" data-ai-quick="Write 3 aesthetic photo captions with hashtags">Photo Captions</button>
      <button type="button" class="chip" data-ai-quick="Give me viral video clip hooks">Video Hooks</button>
      <button type="button" class="chip" data-ai-quick="Polish my creator bio and Page intro">Bio &amp; Page Ideas</button>
    </div>

    <div class="card-panel" style="margin-top:14px;gap:8px;background:var(--surface-alt);">
      <div class="small muted">Optional: Save your personal Gemini API key locally to upgrade from Built-in AI to live Gemini 2.5 Flash replies.</div>
      <div style="display:flex;gap:8px;">
        <input id="aiGeminiKeyInput" type="password" class="input" placeholder="AIza... (optional Gemini API key)" value="${escapeHtml(savedKey)}" />
        <button type="button" class="btn-outline btn-sm" id="saveAiKeyBtn">Save Key</button>
      </div>
    </div>

    <div style="margin-top:14px;">
      <div style="font-weight:700;margin-bottom:8px;">Quick Studio Starter Templates</div>
      <div style="display:grid;gap:8px;">
        ${templates.map((t, idx) => `
          <div class="consent-row">
            <div style="min-width:0;flex:1;">
              <strong>${escapeHtml(t.label)}</strong>
              <div class="muted small">${escapeHtml(t.caption)}</div>
            </div>
            <button type="button" class="btn-outline btn-sm" data-use-ai-tpl="${idx}">Use in Studio</button>
          </div>
        `).join('')}
      </div>
    </div>
  `);

  const threadEl = document.getElementById('aiChatThread');
  if (threadEl) threadEl.scrollTop = threadEl.scrollHeight;
  document.getElementById('closeModalBtn').onclick = closeModal;

  const submitPrompt = async (text) => {
    const clean = String(text || '').trim();
    if (!clean || state.aiBusy) return;
    state.aiBusy = true;
    state.aiChatHistory.push({ role: 'user', text: clean });
    openAiAssistantModal();
    const reply = await generateMomiAiResponse(clean);
    state.aiChatHistory.push({ role: 'assistant', text: reply.text });
    state.aiBusy = false;
    if (document.getElementById('aiChatThread')) {
      openAiAssistantModal();
    }
  };

  document.getElementById('aiChatForm').onsubmit = (e) => {
    e.preventDefault();
    const val = document.getElementById('aiChatInput').value;
    submitPrompt(val);
  };
  modalContent.querySelectorAll('[data-ai-quick]').forEach((b) => {
    b.onclick = () => submitPrompt(b.dataset.aiQuick);
  });
  document.getElementById('saveAiKeyBtn').onclick = () => {
    const val = document.getElementById('aiGeminiKeyInput').value.trim();
    if (val) localStorage.setItem('momi_gemini_api_key', val);
    else localStorage.removeItem('momi_gemini_api_key');
    showToast(val ? 'Gemini key saved — live Gemini 2.5 Flash enabled!' : 'Using Built-in Momi AI Assistant.');
    openAiAssistantModal();
  };
  modalContent.querySelectorAll('[data-use-ai-tpl]').forEach((b) => {
    b.onclick = () => {
      const tpl = templates[Number(b.dataset.useAiTpl)];
      if (tpl) {
        state.studio.caption = tpl.caption;
        state.studio.hashtags = extractHashtags(tpl.caption);
      }
      closeModal();
      navigateTo('create');
      showToast('Template loaded into Create Studio');
    };
  });
}
