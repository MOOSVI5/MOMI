import { initializeApp } from 'https://www.gstatic.com/firebasejs/11.6.0/firebase-app.js';
import {
  getAuth,
  GoogleAuthProvider,
  onAuthStateChanged,
  signInWithPopup,
  signInWithRedirect,
  signOut
} from 'https://www.gstatic.com/firebasejs/11.6.0/firebase-auth.js';
import {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  getFirestore,
  increment,
  limit,
  onSnapshot,
  orderBy,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
  writeBatch
} from 'https://www.gstatic.com/firebasejs/11.6.0/firebase-firestore.js';
import {
  ADMIN_OWNER_EMAIL,
  ASPECT_RATIOS,
  COLOR_SCHEMES,
  escapeHtml,
  EXPLORE_CATEGORIES,
  extractHashtags,
  extractMentions,
  FILTER_OPTIONS,
  filterCssForTag,
  FIREBASE_WEB_CONFIG,
  FIRESTORE_DATABASE_ID,
  formatCount,
  initialsForUsername,
  MAX_IMAGE_BASE64_CHARS,
  normalizeUsername,
  PRESET_IMAGE_OPTIONS,
  renderRichCaptionHtml,
  resolveImageSrc,
  TRENDING_HASHTAGS,
  aspectRatioCss
} from './firebase-config.js';
import {
  assembleVideoChunksToObjectUrl,
  compressFileToBase64,
  exportCanvasToBase64Jpeg,
  playMomiAlertChime,
  renderEditedCanvas
} from './studio.js';

const firebaseApp = initializeApp(FIREBASE_WEB_CONFIG);
const auth = getAuth(firebaseApp);
const db = getFirestore(firebaseApp, FIRESTORE_DATABASE_ID);
const googleProvider = new GoogleAuthProvider();
googleProvider.setCustomParameters({ prompt: 'select_account' });

const GIFT_CATALOG = [
  { key: 'rose', name: 'Silk Rose', credits: 10 },
  { key: 'coffee', name: 'Matcha Cup', credits: 25 },
  { key: 'spark', name: 'Momi Spark', credits: 50 },
  { key: 'crown', name: 'Studio Crown', credits: 200 }
];

const PERK_CATALOG = [
  { key: 'PRIORITY_VERIFICATION', name: 'Creator Pro Review', cost: 250, desc: 'Fast-track creator verification review & profile badge.' },
  { key: 'CUSTOM_THEMES', name: 'VIP Studio Flair', cost: 100, desc: 'Unlock VIP supporter flair in live chat & comments.' },
  { key: 'CREATOR_ANALYTICS', name: 'Audience Insights', cost: 500, desc: 'Unlock extended creator reach & view counters.' }
];

const state = {
  user: null,
  profile: null,
  wallet: null,
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
  products: [],
  orders: [],
  transactions: [],
  nav: 'home',
  homeFilter: 'For You',
  exploreQuery: '',
  exploreCategory: 'For You',
  exploreLayout: 'rednote',
  profileTab: 'posts',
  viewedProfileUid: null,
  carouselIndex: {},
  videoCache: {},
  themeMode: localStorage.getItem('momi_theme') || 'dark',
  scheme: localStorage.getItem('momi_scheme') || 'MIDNIGHT_SUNSET',
  soundEnabled: localStorage.getItem('momi_sound') !== 'false',
  unsubs: [],
  msgUnsubs: null,
  liveUnsubs: [],
  liveTimer: null,
  liveStreamMedia: null,
  seenNotifIds: new Set(),
  seenMsgKeys: new Set(),
  initialSnapshotsLoaded: { notifs: false, threads: false },
  studio: {
    imageKey: 'img_post_coastal',
    uploadedSrc: null,
    filterTag: 'NONE',
    aspectRatio: '1:1',
    rotationDeg: 0,
    brightness: 0,
    contrast: 1,
    warmth: 0,
    caption: '',
    location: '',
    category: 'Travel',
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

document.getElementById('currentHostnameLabel').textContent = window.location.hostname || 'localhost';

if ('serviceWorker' in navigator) {
  navigator.serviceWorker.register('./sw.js').catch(() => undefined);
}

window.addEventListener('beforeinstallprompt', (e) => {
  e.preventDefault();
  deferredPwaPrompt = e;
  document.getElementById('installPwaBtn').classList.remove('hidden');
});

document.getElementById('installPwaBtn').addEventListener('click', async () => {
  if (!deferredPwaPrompt) return;
  deferredPwaPrompt.prompt();
  await deferredPwaPrompt.userChoice;
  deferredPwaPrompt = null;
  document.getElementById('installPwaBtn').classList.add('hidden');
});

function applyAppearance() {
  document.body.dataset.theme = state.themeMode;
  document.body.dataset.scheme = state.scheme;
  document.getElementById('themeModeLabel').textContent = state.themeMode === 'dark' ? 'Dark' : 'Light';
  document.getElementById('soundModeLabel').textContent = state.soundEnabled ? 'On' : 'Muted';
  document.getElementById('schemeSelect').value = state.scheme;
}

applyAppearance();

document.getElementById('themeToggleBtn').addEventListener('click', () => {
  state.themeMode = state.themeMode === 'dark' ? 'light' : 'dark';
  localStorage.setItem('momi_theme', state.themeMode);
  applyAppearance();
});

document.getElementById('soundToggleBtn').addEventListener('click', () => {
  state.soundEnabled = !state.soundEnabled;
  localStorage.setItem('momi_sound', String(state.soundEnabled));
  applyAppearance();
  if (state.soundEnabled) playMomiAlertChime();
  showToast(state.soundEnabled ? 'Alert chimes enabled' : 'Alert chimes muted');
});

document.getElementById('schemeSelect').addEventListener('change', (e) => {
  state.scheme = e.target.value;
  localStorage.setItem('momi_scheme', state.scheme);
  applyAppearance();
});

function showToast(message) {
  toastBar.textContent = message;
  toastBar.classList.remove('hidden');
  clearTimeout(showToast._timer);
  showToast._timer = setTimeout(() => toastBar.classList.add('hidden'), 3200);
}

function makeClientId(prefix) {
  const rand = crypto.randomUUID ? crypto.randomUUID().replace(/-/g, '').slice(0, 12) : Math.random().toString(36).slice(2, 14);
  return `${prefix}_${Date.now()}_${rand}`;
}

function avatarHtml(profileLike, size = 38) {
  const username = profileLike?.username || profileLike?.authorUsername || 'momi';
  if (profileLike?.avatarData) {
    return `<img class="avatar" src="${resolveImageSrc({ imageData: profileLike.avatarData })}" alt="@${escapeHtml(username)}" style="width:${size}px;height:${size}px;" />`;
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
  state.liveUnsubs.forEach((u) => u());
  state.liveUnsubs = [];
  modalBackdrop.classList.add('hidden');
  modalContent.innerHTML = '';
}

modalBackdrop.addEventListener('click', (e) => {
  if (e.target === modalBackdrop) closeModal();
});

document.addEventListener('keydown', (e) => {
  if (e.key === 'Escape' && !modalBackdrop.classList.contains('hidden')) closeModal();
});

// Authentication
document.getElementById('googleSignInBtn').addEventListener('click', async () => {
  authErrorBox.classList.add('hidden');
  try {
    await signInWithPopup(auth, googleProvider);
  } catch (err) {
    if (err?.code === 'auth/popup-blocked' || err?.code === 'auth/cancelled-popup-request') {
      await signInWithRedirect(auth, googleProvider);
    } else {
      if (err?.code === 'auth/unauthorized-domain') {
        const host = window.location.hostname || 'this domain';
        authErrorBox.textContent = `Domain "${host}" is not yet authorized. Open Firebase Console -> Authentication -> Settings -> Authorized domains (project gen-lang-client-0132255277) and add "${host}".`;
      } else {
        authErrorBox.textContent = err?.message || 'Google Sign-In failed. Check Authorized Domains in Firebase Auth.';
      }
      authErrorBox.classList.remove('hidden');
    }
  }
});

document.getElementById('signOutBtn').addEventListener('click', async () => {
  cleanupSubscriptions();
  await signOut(auth);
});

function cleanupSubscriptions() {
  stopLiveBroadcast();
  state.unsubs.forEach((u) => u());
  state.unsubs = [];
  if (state.msgUnsubs) {
    state.msgUnsubs();
    state.msgUnsubs = null;
  }
  state.liveUnsubs.forEach((u) => u());
  state.liveUnsubs = [];
  state.initialSnapshotsLoaded = { notifs: false, threads: false };
}

onAuthStateChanged(auth, async (user) => {
  cleanupSubscriptions();
  state.user = user;
  if (!user) {
    mainShell.classList.add('hidden');
    authGate.classList.remove('hidden');
    return;
  }
  authGate.classList.add('hidden');
  mainShell.classList.remove('hidden');
  await ensureUserSeeded(user);
  startRealtimeSync(user.uid);
});

async function ensureUserSeeded(user) {
  const userRef = doc(db, 'users', user.uid);
  const snap = await getDoc(userRef);
  if (!snap.exists()) {
    const username = normalizeUsername(user.email || user.displayName, user.uid);
    const displayName = (user.displayName || username).slice(0, 80);
    const avatarKeys = ['avatar_aria', 'avatar_elena', 'avatar_kenji', 'avatar_nord', 'avatar_matcha', 'avatar_sora'];
    const avatarKey = avatarKeys[Math.abs(user.uid.charCodeAt(0) + user.uid.length) % avatarKeys.length];
    await setDoc(userRef, {
      userId: user.uid,
      username,
      displayName,
      category: 'Visual Creator',
      bio: 'Capturing moments across Momi mobile & desktop.',
      website: '',
      avatarKey,
      followersCount: 0,
      followingCount: 0,
      isVerified: user.email === ADMIN_OWNER_EMAIL && Boolean(user.emailVerified),
      role: 'USER',
      moderationScopes: [],
      assignedByEmail: null,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp()
    });
  }

  const walletRef = doc(db, 'wallets', user.uid);
  const walletSnap = await getDoc(walletRef);
  if (!walletSnap.exists()) {
    await setDoc(walletRef, {
      userId: user.uid,
      balance: 0,
      totalEarned: 0,
      totalSpent: 0,
      totalPurchased: 0,
      accountTier: 'STANDARD',
      verificationTier: 'NONE',
      unlockedPerks: [],
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp()
    });
  }
}

function startRealtimeSync(uid) {
  const pushUnsub = (u) => state.unsubs.push(u);

  pushUnsub(onSnapshot(doc(db, 'users', uid), (snap) => {
    if (snap.exists()) {
      state.profile = { ...snap.data(), userId: snap.id };
      renderAll();
    }
  }));

  pushUnsub(onSnapshot(doc(db, 'wallets', uid), (snap) => {
    if (snap.exists()) {
      state.wallet = snap.data();
      renderRightRail();
      if (state.nav === 'wallet') renderMainStage();
    }
  }));

  pushUnsub(onSnapshot(query(collection(db, 'users'), limit(50)), (snap) => {
    state.creators = snap.docs.map((d) => ({ ...d.data(), userId: d.id }));
    renderRightRail();
    if (state.nav === 'explore' || state.nav === 'create') renderMainStage();
  }));

  pushUnsub(onSnapshot(query(collection(db, 'posts'), orderBy('createdAt', 'desc'), limit(40)), (snap) => {
    state.posts = snap.docs.map((d) => ({ ...d.data(), id: d.id }));
    renderMainStage();
  }));

  pushUnsub(onSnapshot(query(collection(db, 'stories'), orderBy('createdAt', 'desc'), limit(30)), (snap) => {
    state.stories = snap.docs.map((d) => ({ ...d.data(), id: d.id }));
    if (state.nav === 'home') renderMainStage();
  }));

  pushUnsub(onSnapshot(query(collection(db, 'liveStreams'), where('isLive', '==', true), limit(20)), (snap) => {
    state.liveStreams = snap.docs.map((d) => ({ ...d.data(), id: d.id }));
    updateBadges();
    renderRightRail();
    if (state.nav === 'home' || state.nav === 'live') renderMainStage();
  }));

  pushUnsub(onSnapshot(collection(db, 'users', uid, 'likedPosts'), (snap) => {
    state.likedIds = new Set(snap.docs.map((d) => d.id));
    renderMainStage();
  }));

  pushUnsub(onSnapshot(collection(db, 'users', uid, 'bookmarks'), (snap) => {
    state.bookmarkIds = new Set(snap.docs.map((d) => d.id));
    renderMainStage();
  }));

  pushUnsub(onSnapshot(collection(db, 'users', uid, 'seenStories'), (snap) => {
    state.seenStoryIds = new Set(snap.docs.map((d) => d.id));
    if (state.nav === 'home') renderMainStage();
  }));

  pushUnsub(onSnapshot(query(collection(db, 'follows'), where('followerUid', '==', uid)), (snap) => {
    state.followingUids = new Set(snap.docs.map((d) => d.data().targetUid));
    renderRightRail();
    renderMainStage();
  }));

  pushUnsub(onSnapshot(query(collection(db, 'users', uid, 'notifications'), orderBy('createdAt', 'desc'), limit(35)), (snap) => {
    const list = snap.docs.map((d) => ({ ...d.data(), id: d.id }));
    if (state.initialSnapshotsLoaded.notifs) {
      const fresh = list.find((n) => n.isUnread && !state.seenNotifIds.has(n.id));
      if (fresh) triggerAlert(`${fresh.actorUsername}: ${fresh.message}`);
    }
    list.forEach((n) => state.seenNotifIds.add(n.id));
    state.initialSnapshotsLoaded.notifs = true;
    state.notifications = list;
    updateBadges();
    if (state.nav === 'notifications') renderMainStage();
  }));

  pushUnsub(onSnapshot(query(collection(db, 'threads'), where('participantUids', 'array-contains', uid), limit(30)), (snap) => {
    const list = snap.docs.map((d) => ({ ...d.data(), id: d.id }));
    if (state.initialSnapshotsLoaded.threads) {
      const unread = list.find((t) => t.unreadUid === uid && t.unreadCount > 0 && !state.seenMsgKeys.has(`${t.id}:${t.lastMessage}:${t.timeLabel}`));
      if (unread) triggerAlert(`New message from @${unread.userAId === uid ? unread.userBUsername : unread.userAUsername}: ${unread.lastMessage}`);
    }
    list.forEach((t) => state.seenMsgKeys.add(`${t.id}:${t.lastMessage}:${t.timeLabel}`));
    state.initialSnapshotsLoaded.threads = true;
    state.threads = list;
    updateBadges();
    if (state.nav === 'messages') renderMainStage();
  }));

  pushUnsub(onSnapshot(query(collection(db, 'marketProducts'), orderBy('createdAt', 'desc'), limit(30)), (snap) => {
    state.products = snap.docs.map((d) => ({ ...d.data(), id: d.id }));
    if (state.nav === 'marketplace') renderMainStage();
  }));

  pushUnsub(onSnapshot(query(collection(db, 'marketOrders'), where('buyerUid', '==', uid), limit(25)), (snap) => {
    state.orders = snap.docs.map((d) => ({ ...d.data(), id: d.id }));
    if (state.nav === 'marketplace') renderMainStage();
  }));

  pushUnsub(onSnapshot(query(collection(db, 'creditTransactions'), where('recipientUid', '==', uid), limit(30)), async (snap) => {
    const incoming = snap.docs.map((d) => ({ ...d.data(), id: d.id }));
    for (const tx of incoming) {
      if (tx.type === 'GIFT' && tx.recipientClaimed === false) {
        await claimGiftTransaction(uid, tx).catch(() => undefined);
      }
    }
    state.transactions = incoming;
    if (state.nav === 'wallet') renderMainStage();
  }));
}

async function claimGiftTransaction(uid, tx) {
  const walletRef = doc(db, 'wallets', uid);
  const walletSnap = await getDoc(walletRef);
  if (!walletSnap.exists()) return;
  const current = walletSnap.data();
  const batch = writeBatch(db);
  batch.update(walletRef, {
    balance: (current.balance || 0) + tx.creditsAmount,
    totalEarned: (current.totalEarned || 0) + tx.creditsAmount,
    lastTxId: tx.txId,
    updatedAt: serverTimestamp()
  });
  batch.update(doc(db, 'creditTransactions', tx.id), {
    recipientClaimed: true,
    updatedAt: serverTimestamp()
  });
  await batch.commit();
  triggerAlert(`You received ${tx.creditsAmount} credits (${tx.giftName || 'Gift'}) from @${tx.senderUsername}!`);
}

function triggerAlert(text) {
  if (state.soundEnabled) playMomiAlertChime();
  showToast(text);
  if ('Notification' in window && Notification.permission === 'granted' && document.hidden) {
    new Notification('Momi', { body: text, icon: './assets/momi-icon.jpg' });
  }
}

function updateBadges() {
  const unreadNotifs = state.notifications.filter((n) => n.isUnread).length;
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

  document.getElementById('mobileNotifDot').classList.toggle('hidden', unreadNotifs <= 0);
  document.getElementById('mobileMsgDot').classList.toggle('hidden', unreadMsgs <= 0);
}

// Navigation
document.querySelectorAll('[data-nav]').forEach((btn) => {
  btn.addEventListener('click', () => navigateTo(btn.dataset.nav));
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

function renderAll() {
  updateBadges();
  renderRightRail();
  renderMainStage();
}

function renderRightRail() {
  const rightUserCard = document.getElementById('rightUserCard');
  const rightLiveCard = document.getElementById('rightLiveCard');
  const rightCreatorsCard = document.getElementById('rightCreatorsCard');
  if (!state.profile) return;

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
    <div style="display:flex;align-items:center;justify-content:space-between;margin-top:12px;padding-top:10px;border-top:1px solid var(--border);">
      <div>
        <div style="font-weight:700;font-size:0.9rem;">${formatCount(balance)} Credits</div>
        <div class="muted small">${escapeHtml(tier)}</div>
      </div>
      <button type="button" class="btn-primary btn-sm" id="railWalletBtn">Wallet</button>
    </div>
  `;
  document.getElementById('railProfileBtn').onclick = () => navigateTo('profile');
  document.getElementById('railWalletBtn').onclick = () => navigateTo('wallet');

  rightLiveCard.innerHTML = `
    <div class="rail-title-row">
      <h3>Live on Momi</h3>
      <button type="button" class="btn-ghost btn-sm" id="railGoLiveBtn">Go Live</button>
    </div>
    ${
      state.liveStreams.length === 0
        ? '<p class="muted small" style="margin:0;">No active broadcasts right now. Start one from your desktop webcam!</p>'
        : state.liveStreams.slice(0, 4).map((s) => `
          <div style="display:flex;align-items:center;justify-content:space-between;gap:10px;margin-bottom:10px;">
            <div style="display:flex;align-items:center;gap:10px;min-width:0;">
              ${avatarHtml(s, 34)}
              <div style="min-width:0;">
                <div style="font-weight:700;font-size:0.86rem;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">@${escapeHtml(s.username)}</div>
                <div class="muted small">${escapeHtml(s.title || 'Live Broadcast')}</div>
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
    case 'marketplace': renderMarketplace(); break;
    case 'messages': renderMessages(); break;
    case 'notifications': renderNotifications(); break;
    case 'wallet': renderWallet(); break;
    case 'profile': renderProfile(); break;
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

function renderPostCardHtml(post) {
  const frames = [
    ...(post.imageData || post.imageKey ? [{ imageData: post.imageData, imageKey: post.imageKey }] : []),
    ...(Array.isArray(post.extraImageDatas) ? post.extraImageDatas.map((d) => ({ imageData: d, imageKey: post.imageKey })) : [])
  ];
  const idx = Math.min(state.carouselIndex[post.id] || 0, Math.max(0, frames.length - 1));
  const currentFrame = frames[idx] || { imageKey: post.imageKey };
  const isLiked = state.likedIds.has(post.id);
  const isSaved = state.bookmarkIds.has(post.id);
  const isMine = post.userId === state.user?.uid;
  const isFollowing = state.followingUids.has(post.userId);
  const isVideo = post.mediaType === 'VIDEO' || post.isReel;
  const cachedVideoUrl = state.videoCache[post.id];
  const ratio = aspectRatioCss(post.aspectRatio);
  const filterCss = filterCssForTag(post.filterTag);

  const tags = Array.isArray(post.hashtags) ? post.hashtags : [];
  const tagged = Array.isArray(post.taggedUsernames) ? post.taggedUsernames : [];

  return `
    <article class="post-card" data-post-id="${escapeHtml(post.id)}">
      <header class="post-header">
        <button type="button" class="post-author-btn" data-open-user="${escapeHtml(post.userId)}">
          ${avatarHtml({ username: post.authorUsername, avatarData: null }, 38)}
          <div class="post-author-meta">
            <span class="post-author-name">
              @${escapeHtml(post.authorUsername)}
              ${post.authorVerified ? '<span class="verified-dot">&#10003;</span>' : ''}
            </span>
            <span class="muted small">${escapeHtml(post.location || post.category || 'Momi Feed')}</span>
          </div>
        </button>
        <div style="display:flex;align-items:center;gap:8px;">
          ${!isMine ? `<button type="button" class="btn-sm ${isFollowing ? 'btn-outline' : 'btn-primary'}" data-follow-uid="${escapeHtml(post.userId)}" data-follow-name="${escapeHtml(post.authorUsername)}">${isFollowing ? 'Following' : 'Follow'}</button>` : ''}
          ${isMine ? `<button type="button" class="btn-ghost btn-sm" data-delete-post="${escapeHtml(post.id)}" title="Delete post">Delete</button>` : ''}
        </div>
      </header>

      <div class="post-media-wrap" style="aspect-ratio:${ratio};" data-open-post="${escapeHtml(post.id)}">
        ${
          isVideo && cachedVideoUrl
            ? `<video class="post-media-video" src="${cachedVideoUrl}" controls playsinline></video>`
            : `<img class="post-media-img" src="${resolveImageSrc(currentFrame)}" alt="${escapeHtml(post.caption || 'Momi post')}" style="filter:${filterCss};" loading="lazy" />`
        }
        ${isVideo && !cachedVideoUrl ? `<span class="media-badge">${post.videoChunksCount ? 'Click to Load Video' : 'Clip'}</span>` : ''}
        ${!isVideo && post.filterTag && post.filterTag !== 'NONE' ? `<span class="media-badge">${escapeHtml(post.filterTag)}</span>` : ''}
        ${frames.length > 1 ? `
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
  mainStage.innerHTML = `
    <div class="stage-column">
      <div class="section-header">
        <h2>Home Feed</h2>
        <div class="chip-row">
          ${['For You', 'Following', 'Saved'].map((f) => `
            <button type="button" class="chip ${state.homeFilter === f ? 'active' : ''}" data-home-filter="${f}">${f}</button>
          `).join('')}
        </div>
      </div>

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
            <span class="avatar-ring ${state.seenStoryIds.has(st.id) ? 'seen' : ''}">${avatarHtml(st, 58)}</span>
            <span>@${escapeHtml(st.username)}</span>
          </button>
        `).join('')}
      </section>

      ${
        posts.length === 0
          ? `<div class="card-panel" style="text-align:center;padding:36px;">
              <h3 style="margin:0 0 8px;">No posts in ${escapeHtml(state.homeFilter)} yet</h3>
              <p class="muted" style="margin:0 0 16px;">Create a post in Desktop Studio or follow creators in Explore.</p>
              <button type="button" class="btn-primary" id="emptyCreateBtn">Open Create Studio</button>
            </div>`
          : posts.map(renderPostCardHtml).join('')
      }
    </div>
  `;

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
          <button type="button" class="chip ${state.exploreLayout === 'rednote' ? 'active' : ''}" id="layoutRedNoteBtn">Discover Notes</button>
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
        state.exploreLayout === 'rednote'
          ? `<div class="rednote-grid">
              ${filteredPosts.map((p) => `
                <article class="rednote-card" data-open-post="${escapeHtml(p.id)}">
                  <img class="rednote-thumb" src="${resolveImageSrc(p)}" alt="${escapeHtml(p.caption || '')}" style="filter:${filterCssForTag(p.filterTag)};" loading="lazy" />
                  <div class="rednote-meta">
                    <div class="rednote-title">${escapeHtml(p.caption || 'Visual Note')}</div>
                    <div class="rednote-footer">
                      <span>@${escapeHtml(p.authorUsername)}</span>
                      <span>&#9829; ${formatCount(p.likesCount)}</span>
                    </div>
                  </div>
                </article>
              `).join('')}
            </div>`
          : `<div class="ig-grid-3">
              ${filteredPosts.map((p) => `
                <button type="button" class="ig-tile" data-open-post="${escapeHtml(p.id)}">
                  <img src="${resolveImageSrc(p)}" alt="${escapeHtml(p.caption || '')}" style="filter:${filterCssForTag(p.filterTag)};" loading="lazy" />
                </button>
              `).join('')}
            </div>`
      }
    </div>
  `;

  document.getElementById('layoutRedNoteBtn').onclick = () => { state.exploreLayout = 'rednote'; renderExplore(); };
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
  const clips = state.posts.filter((p) => p.isReel || p.mediaType === 'VIDEO');
  mainStage.innerHTML = `
    <div class="stage-column">
      <div class="section-header">
        <h2>Momi Clips</h2>
        <span class="muted small">${clips.length} video &amp; featured clips</span>
      </div>
      ${
        clips.length === 0
          ? '<div class="card-panel"><p class="muted" style="margin:0;">No clips published yet. Check "Publish as Clip" in Create Studio or upload from Android.</p></div>'
          : clips.map(renderPostCardHtml).join('')
      }
    </div>
  `;
  bindFeedInteractions(mainStage);
}

function renderCreateStudio() {
  const s = state.studio;
  const activeSrc = s.uploadedSrc || resolveImageSrc({ imageKey: s.imageKey });
  const otherCreators = state.creators.filter((c) => c.userId !== state.user?.uid);

  mainStage.innerHTML = `
    <div class="stage-wide">
      <div class="section-header">
        <h2>Desktop Photo &amp; Post Studio</h2>
        <span class="muted small">Edits bake directly into uploaded JPEG &amp; sync with Android</span>
      </div>

      <div class="studio-layout">
        <div class="card-panel">
          <div class="studio-canvas-box">
            <canvas id="studioCanvas"></canvas>
          </div>

          <div style="display:flex;flex-wrap:wrap;gap:8px;align-items:center;">
            <label class="btn-primary btn-sm" for="studioFileInput">Upload Photo from Computer</label>
            <input id="studioFileInput" type="file" accept="image/*" class="hidden" />
            <button type="button" class="btn-outline btn-sm" id="studioRotateBtn">Rotate 90&deg;</button>
            <button type="button" class="btn-ghost btn-sm" id="studioResetEditBtn">Reset</button>
          </div>

          <div>
            <div class="muted small" style="margin-bottom:6px;">Or choose curated preset artwork:</div>
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

          <div style="display:grid;gap:8px;">
            <label class="small muted">Brightness (<span id="brightVal">${s.brightness}</span>)
              <input id="brightSlider" type="range" min="-35" max="35" value="${s.brightness}" style="width:100%;" />
            </label>
            <label class="small muted">Contrast (<span id="contrastVal">${s.contrast}</span>)
              <input id="contrastSlider" type="range" min="0.7" max="1.4" step="0.05" value="${s.contrast}" style="width:100%;" />
            </label>
            <label class="small muted">Warmth (<span id="warmthVal">${s.warmth}</span>)
              <input id="warmthSlider" type="range" min="-0.3" max="0.45" step="0.05" value="${s.warmth}" style="width:100%;" />
            </label>
          </div>
        </div>

        <div class="card-panel">
          <label class="small muted">Caption (supports #hashtags and @mentions)</label>
          <textarea id="studioCaption" class="textarea" rows="4" placeholder="Write a caption with #hashtags and @creator tags...">${escapeHtml(s.caption)}</textarea>

          <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px;">
            <div>
              <label class="small muted">Location</label>
              <input id="studioLocation" class="input" placeholder="e.g. Tokyo, Lahore" value="${escapeHtml(s.location)}" />
            </div>
            <div>
              <label class="small muted">Category</label>
              <select id="studioCategory" class="select">
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
            <div class="chip-row" style="margin-top:8px;">
              ${TRENDING_HASHTAGS.map((t) => `
                <button type="button" class="chip ${s.hashtags.includes(t) ? 'active' : ''}" data-studio-tag="${t}">#${t}</button>
              `).join('')}
              ${s.hashtags.filter((t) => !TRENDING_HASHTAGS.includes(t)).map((t) => `
                <button type="button" class="chip active" data-studio-tag="${escapeHtml(t)}">#${escapeHtml(t)} &times;</button>
              `).join('')}
            </div>
          </div>

          <div>
            <label class="small muted">Tag Creators</label>
            <div class="chip-row" style="margin-top:6px;">
              ${otherCreators.slice(0, 8).map((c) => `
                <button type="button" class="chip ${s.taggedUsernames.includes(c.username) ? 'active' : ''}" data-tag-creator="${escapeHtml(c.username)}">@${escapeHtml(c.username)}</button>
              `).join('')}
            </div>
          </div>

          <label style="display:flex;align-items:center;gap:8px;font-size:0.9rem;">
            <input id="studioIsReel" type="checkbox" ${s.isReel ? 'checked' : ''} />
            Feature in Momi Clips
          </label>

          <button type="button" class="btn-primary btn-lg" id="publishPostBtn" ${s.publishing ? 'disabled' : ''}>
            ${s.publishing ? 'Publishing to Momi...' : 'Publish Post'}
          </button>
        </div>
      </div>
    </div>
  `;

  const canvas = document.getElementById('studioCanvas');
  const redraw = () => renderEditedCanvas(canvas, activeSrc, s).catch(() => undefined);
  redraw();

  document.getElementById('studioFileInput').onchange = (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    if (s.uploadedSrc?.startsWith('blob:')) URL.revokeObjectURL(s.uploadedSrc);
    s.uploadedSrc = URL.createObjectURL(file);
    renderCreateStudio();
  };

  document.getElementById('studioRotateBtn').onclick = () => {
    s.rotationDeg = (s.rotationDeg + 90) % 360;
    redraw();
  };

  document.getElementById('studioResetEditBtn').onclick = () => {
    Object.assign(s, { filterTag: 'NONE', aspectRatio: '1:1', rotationDeg: 0, brightness: 0, contrast: 1, warmth: 0 });
    renderCreateStudio();
  };

  mainStage.querySelectorAll('[data-preset-key]').forEach((b) => {
    b.onclick = () => { s.uploadedSrc = null; s.imageKey = b.dataset.presetKey; renderCreateStudio(); };
  });
  mainStage.querySelectorAll('[data-aspect-btn]').forEach((b) => {
    b.onclick = () => { s.aspectRatio = b.dataset.aspectBtn; renderCreateStudio(); };
  });
  mainStage.querySelectorAll('[data-filter-tag]').forEach((b) => {
    b.onclick = () => { s.filterTag = b.dataset.filterTag; renderCreateStudio(); };
  });

  document.getElementById('brightSlider').oninput = (e) => { s.brightness = Number(e.target.value); document.getElementById('brightVal').textContent = s.brightness; redraw(); };
  document.getElementById('contrastSlider').oninput = (e) => { s.contrast = Number(e.target.value); document.getElementById('contrastVal').textContent = s.contrast; redraw(); };
  document.getElementById('warmthSlider').oninput = (e) => { s.warmth = Number(e.target.value); document.getElementById('warmthVal').textContent = s.warmth; redraw(); };

  document.getElementById('studioCaption').oninput = (e) => { s.caption = e.target.value; };
  document.getElementById('studioLocation').oninput = (e) => { s.location = e.target.value; };
  document.getElementById('studioCategory').onchange = (e) => { s.category = e.target.value; };
  document.getElementById('studioIsReel').onchange = (e) => { s.isReel = e.target.checked; };

  document.getElementById('addCustomTagBtn').onclick = () => {
    const val = document.getElementById('customTagInput').value.trim().replace(/^#/, '').toLowerCase().replace(/[^a-z0-9_]/g, '');
    if (val && !s.hashtags.includes(val) && s.hashtags.length < 15) {
      s.hashtags.push(val);
      renderCreateStudio();
    }
  };
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
      const hasEdits = Boolean(s.uploadedSrc) || s.filterTag !== 'NONE' || s.aspectRatio !== '1:1' || s.rotationDeg !== 0 || s.brightness !== 0 || s.contrast !== 1 || s.warmth !== 0;
      let imageData = null;
      if (hasEdits) {
        const exportCanvas = document.createElement('canvas');
        await renderEditedCanvas(exportCanvas, activeSrc, s);
        imageData = exportCanvasToBase64Jpeg(exportCanvas, MAX_IMAGE_BASE64_CHARS);
      }
      const postId = makeClientId('post');
      const finalHashtags = extractHashtags(s.caption, s.hashtags);
      const finalTags = extractMentions(s.caption, s.taggedUsernames);
      await setDoc(doc(db, 'posts', postId), {
        userId: state.user.uid,
        authorUsername: state.profile.username,
        authorDisplayName: state.profile.displayName,
        authorAvatarKey: state.profile.avatarKey || 'avatar_aria',
        authorVerified: Boolean(state.profile.isVerified),
        location: s.location.trim().slice(0, 120),
        imageKey: s.imageKey,
        imageData,
        mediaType: 'IMAGE',
        filterTag: s.filterTag,
        aspectRatio: s.aspectRatio,
        taggedUsernames: finalTags,
        hashtags: finalHashtags,
        caption: s.caption.trim().slice(0, 1000),
        likesCount: 0,
        viewsCount: 0,
        commentsCount: 0,
        category: s.category || 'Travel',
        isReel: Boolean(s.isReel),
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp()
      });
      state.studio = {
        ...state.studio,
        uploadedSrc: null,
        filterTag: 'NONE',
        aspectRatio: '1:1',
        rotationDeg: 0,
        brightness: 0,
        contrast: 1,
        warmth: 0,
        caption: '',
        location: '',
        hashtags: [],
        taggedUsernames: [],
        publishing: false
      };
      showToast('Published to Momi across Web & Android!');
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
        <h2>Momi Live Rooms</h2>
        <button type="button" class="btn-primary" id="lobbyGoLiveBtn">Start Desktop Webcam Broadcast</button>
      </div>
      ${
        state.liveStreams.length === 0
          ? `<div class="card-panel" style="text-align:center;padding:36px;">
              <h3 style="margin:0 0 8px;">No creators are live right now</h3>
              <p class="muted" style="margin:0 0 16px;">Go live from your computer webcam — Android Momi users can watch and send gifts in real time.</p>
              <button type="button" class="btn-primary" id="lobbyEmptyLiveBtn">Go Live Now</button>
            </div>`
          : `<div class="rednote-grid">
              ${state.liveStreams.map((s) => `
                <article class="card-panel" style="gap:10px;">
                  <div style="display:flex;align-items:center;gap:10px;">
                    ${avatarHtml(s, 42)}
                    <div>
                      <div class="post-author-name">@${escapeHtml(s.username)}</div>
                      <div class="muted small">${formatCount(s.viewerCount || 0)} watching &middot; ${formatCount(s.giftsCredits || 0)} gifts</div>
                    </div>
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
  mainStage.innerHTML = `
    <div class="stage-wide">
      <div class="section-header">
        <h2>Momi Marketplace</h2>
        <span class="muted small">${state.products.length} listings &middot; ${state.orders.length} your orders</span>
      </div>
      <div class="rednote-grid">
        ${
          state.products.length === 0
            ? '<div class="card-panel"><p class="muted" style="margin:0;">No marketplace listings available yet.</p></div>'
            : state.products.map((prod) => `
              <article class="rednote-card">
                <img class="rednote-thumb" src="${resolveImageSrc({ imageData: prod.imageData, imageKey: prod.imageKey })}" alt="${escapeHtml(prod.title)}" />
                <div class="rednote-meta">
                  <div style="font-weight:700;">${escapeHtml(prod.title)}</div>
                  <div class="muted small">${escapeHtml(prod.storeName)} &middot; ${prod.freeShipping ? 'Free shipping' : 'Standard'}</div>
                  <div class="rednote-footer">
                    <strong style="color:var(--primary);font-size:0.98rem;">$${Number(prod.priceUsd || 0).toFixed(2)}</strong>
                    ${prod.sellerUid !== state.user?.uid ? `<button type="button" class="btn-primary btn-sm" data-buy-product="${escapeHtml(prod.id)}">Order</button>` : '<span class="muted small">Your listing</span>'}
                  </div>
                </div>
              </article>
            `).join('')
        }
      </div>
    </div>
  `;
  mainStage.querySelectorAll('[data-buy-product]').forEach((b) => {
    b.onclick = async () => {
      const prod = state.products.find((p) => p.id === b.dataset.buyProduct);
      if (!prod || !state.profile) return;
      const addr = window.prompt(`Shipping address for "${prod.title}" ($${Number(prod.priceUsd).toFixed(2)}):`);
      if (!addr || !addr.trim()) return;
      await setDoc(doc(db, 'marketOrders', makeClientId('order')), {
        buyerUid: state.user.uid,
        buyerUsername: state.profile.username,
        sellerUid: prod.sellerUid,
        sellerStoreName: prod.storeName,
        productId: prod.id,
        productTitle: prod.title,
        productImageKey: prod.imageKey,
        productImageData: prod.imageData || null,
        priceUsd: Number(prod.priceUsd),
        shippingAddress: addr.trim().slice(0, 300),
        status: 'PLACED',
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp()
      });
      showToast('Order placed!');
    };
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
        <span class="muted small">Real-time sync with Momi Android</span>
      </div>
      <div class="messages-split">
        <div class="threads-pane">
          ${
            state.threads.length === 0
              ? '<p class="muted small" style="padding:18px;">No threads yet. Open any creator profile to send a message.</p>'
              : state.threads.map((t) => {
                const otherName = t.userAId === uid ? t.userBUsername : t.userAUsername;
                const isUnread = t.unreadUid === uid && (t.unreadCount || 0) > 0;
                return `
                  <button type="button" class="thread-row ${t.id === state.activeThreadId ? 'active' : ''}" data-select-thread="${escapeHtml(t.id)}">
                    ${avatarHtml({ username: otherName }, 40)}
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
                  ${avatarHtml({ username: activeThread.userAId === uid ? activeThread.userBUsername : activeThread.userAUsername }, 34)}
                  <strong>@${escapeHtml(activeThread.userAId === uid ? activeThread.userBUsername : activeThread.userAUsername)}</strong>
                </div>
                <div id="chatScrollBox" class="chat-messages">
                  ${state.activeMessages.map((m) => `
                    <div class="bubble ${m.senderUid === uid ? 'mine' : ''}">
                      ${m.imageData ? `<img src="${resolveImageSrc({ imageData: m.imageData })}" alt="Attachment" />` : ''}
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
      const b64 = await compressFileToBase64(file, 900, 620000);
      await sendChatMessage(activeThread, { text: 'Sent a photo', messageType: 'IMAGE', imageData: b64 });
    };
  }
}

function selectThread(threadId) {
  state.activeThreadId = threadId;
  if (state.msgUnsubs) state.msgUnsubs();
  state.msgUnsubs = onSnapshot(
    query(collection(db, 'threads', threadId, 'messages'), orderBy('createdAt', 'asc'), limit(80)),
    (snap) => {
      state.activeMessages = snap.docs.map((d) => ({ ...d.data(), id: d.id }));
      if (state.nav === 'messages') renderMessages();
    }
  );
  const t = state.threads.find((x) => x.id === threadId);
  if (t && t.unreadUid === state.user?.uid && (t.unreadCount || 0) > 0) {
    updateDoc(doc(db, 'threads', threadId), {
      unreadUid: '',
      unreadCount: 0,
      updatedAt: serverTimestamp()
    }).catch(() => undefined);
  }
}

async function sendChatMessage(thread, { text, messageType = 'TEXT', imageData = null }) {
  if (!state.profile) return;
  const uid = state.user.uid;
  const otherUid = thread.userAId === uid ? thread.userBId : thread.userAId;
  const batch = writeBatch(db);
  batch.set(doc(db, 'threads', thread.id, 'messages', makeClientId('msg')), {
    participantUids: thread.participantUids,
    senderUid: uid,
    senderUsername: state.profile.username,
    text: text.slice(0, 1000),
    messageType,
    imageData,
    createdAt: serverTimestamp()
  });
  batch.update(doc(db, 'threads', thread.id), {
    lastMessage: text.slice(0, 500),
    lastSenderUid: uid,
    unreadUid: otherUid,
    unreadCount: (thread.unreadCount || 0) + 1,
    timeLabel: 'Just now',
    updatedAt: serverTimestamp()
  });
  await batch.commit();
}

function renderNotifications() {
  const desktopPerm = 'Notification' in window ? Notification.permission : 'unsupported';
  mainStage.innerHTML = `
    <div class="stage-column">
      <div class="section-header">
        <h2>Activity &amp; Alert Sounds</h2>
        <div class="chip-row">
          <button type="button" class="btn-outline btn-sm" id="testChimeBtn">Test Sound</button>
          ${desktopPerm === 'default' ? '<button type="button" class="btn-primary btn-sm" id="enableDesktopNotifsBtn">Enable Desktop Popups</button>' : ''}
          <button type="button" class="btn-ghost btn-sm" id="markAllReadBtn">Mark All Read</button>
        </div>
      </div>

      ${
        state.notifications.length === 0
          ? '<div class="card-panel"><p class="muted" style="margin:0;">No notifications yet. Likes, comments, follows, and gift alerts appear here with sound chimes.</p></div>'
          : state.notifications.map((n) => `
            <div class="card-panel" style="flex-direction:row;align-items:center;justify-content:space-between;${n.isUnread ? 'border-color:var(--primary);' : ''}">
              <div style="display:flex;align-items:center;gap:12px;">
                ${avatarHtml({ username: n.actorUsername }, 40)}
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
    const unread = state.notifications.filter((n) => n.isUnread);
    for (const n of unread) {
      await updateDoc(doc(db, 'users', state.user.uid, 'notifications', n.id), {
        isUnread: false,
        updatedAt: serverTimestamp()
      }).catch(() => undefined);
    }
    showToast('Marked notifications as read');
  };
  bindUserAndFollowButtons(mainStage);
}

function renderWallet() {
  const w = state.wallet || { balance: 0, totalEarned: 0, totalSpent: 0, totalPurchased: 0, accountTier: 'STANDARD', verificationTier: 'NONE', unlockedPerks: [] };
  mainStage.innerHTML = `
    <div class="stage-column">
      <div class="section-header">
        <h2>Wallet, Gifts &amp; Account Perks</h2>
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
        <div class="small" style="opacity:0.9;margin-top:4px;">Credit purchases are processed securely via Google Play inside the Momi Android app and sync here immediately.</div>
      </div>

      <div class="card-panel">
        <h3 style="margin:0;">Unlock Account Perks with Credits</h3>
        ${PERK_CATALOG.map((perk) => {
          const unlocked = (w.unlockedPerks || []).includes(perk.key);
          return `
            <div style="display:flex;align-items:center;justify-content:space-between;gap:12px;padding:10px 0;border-bottom:1px solid var(--border);">
              <div>
                <div style="font-weight:700;">${escapeHtml(perk.name)}</div>
                <div class="muted small">${escapeHtml(perk.desc)}</div>
              </div>
              <button type="button" class="btn-sm ${unlocked ? 'btn-outline' : 'btn-primary'}" data-unlock-perk="${perk.key}:${perk.cost}" ${unlocked ? 'disabled' : ''}>
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
            : state.transactions.slice(0, 12).map((tx) => `
              <div style="display:flex;justify-content:space-between;padding:8px 0;border-bottom:1px solid var(--border);font-size:0.88rem;">
                <span>${escapeHtml(tx.type)} &middot; @${escapeHtml(tx.senderUsername)} &rarr; @${escapeHtml(tx.recipientUsername)}</span>
                <strong>${tx.creditsAmount} cr</strong>
              </div>
            `).join('')
        }
      </div>
    </div>
  `;

  mainStage.querySelectorAll('[data-unlock-perk]').forEach((b) => {
    b.onclick = async () => {
      const [perkKey, costStr] = b.dataset.unlockPerk.split(':');
      const cost = Number(costStr);
      if ((w.balance || 0) < cost) {
        showToast('Not enough credits yet. Top up credits in the Momi Android app.');
        return;
      }
      const txId = makeClientId('perk');
      const batch = writeBatch(db);
      const nextPerks = [...(w.unlockedPerks || []), perkKey];
      batch.update(doc(db, 'wallets', state.user.uid), {
        balance: w.balance - cost,
        totalSpent: (w.totalSpent || 0) + cost,
        accountTier: perkKey === 'CREATOR_ANALYTICS' ? 'CREATOR_PRO' : w.accountTier,
        verificationTier: perkKey === 'PRIORITY_VERIFICATION' ? 'PENDING_PERK' : w.verificationTier,
        unlockedPerks: nextPerks,
        lastTxId: txId,
        updatedAt: serverTimestamp()
      });
      batch.set(doc(db, 'creditTransactions', txId), {
        txId,
        type: 'PERK_UNLOCK',
        senderUid: state.user.uid,
        senderUsername: state.profile.username,
        recipientUid: state.user.uid,
        recipientUsername: state.profile.username,
        creditsAmount: cost,
        priceUsd: 0,
        paymentMethod: 'WALLET_CREDITS',
        perkKey,
        recipientClaimed: true,
        status: 'COMPLETED',
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp()
      });
      await batch.commit();
      showToast(`Unlocked ${perkKey}!`);
    };
  });
}

function renderProfile() {
  const targetUid = state.viewedProfileUid || state.user?.uid;
  const isOwn = targetUid === state.user?.uid;
  const profile = isOwn ? state.profile : state.creators.find((c) => c.userId === targetUid);
  if (!profile) {
    mainStage.innerHTML = '<div class="stage-column"><div class="card-panel"><p class="muted">Loading profile...</p></div></div>';
    return;
  }

  const userPosts = state.posts.filter((p) => p.userId === profile.userId);
  const savedPosts = isOwn ? state.posts.filter((p) => state.bookmarkIds.has(p.id)) : [];
  const displayed = state.profileTab === 'saved' && isOwn ? savedPosts : userPosts;
  const isFollowing = state.followingUids.has(profile.userId);

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
        <div class="chip-row">
          <button type="button" class="chip ${state.profileTab === 'posts' ? 'active' : ''}" id="profTabPosts">Posts (${userPosts.length})</button>
          <button type="button" class="chip ${state.profileTab === 'saved' ? 'active' : ''}" id="profTabSaved">Saved (${savedPosts.length})</button>
        </div>
      ` : ''}

      <div class="ig-grid-3">
        ${displayed.map((p) => `
          <button type="button" class="ig-tile" data-open-post="${escapeHtml(p.id)}">
            <img src="${resolveImageSrc(p)}" alt="${escapeHtml(p.caption || '')}" style="filter:${filterCssForTag(p.filterTag)};" loading="lazy" />
          </button>
        `).join('')}
      </div>
    </div>
  `;

  const editBtn = document.getElementById('editProfileBtn');
  if (editBtn) editBtn.onclick = () => openEditProfileModal();
  const createBtn = document.getElementById('profileCreateBtn');
  if (createBtn) createBtn.onclick = () => navigateTo('create');
  const msgBtn = document.getElementById('messageCreatorBtn');
  if (msgBtn) msgBtn.onclick = () => openOrCreateDirectThread(profile);
  const tPosts = document.getElementById('profTabPosts');
  if (tPosts) tPosts.onclick = () => { state.profileTab = 'posts'; renderProfile(); };
  const tSaved = document.getElementById('profTabSaved');
  if (tSaved) tSaved.onclick = () => { state.profileTab = 'saved'; renderProfile(); };
  bindFeedInteractions(mainStage);
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

  container.querySelectorAll('[data-open-post]').forEach((el) => {
    el.onclick = () => openPostDetailModal(el.dataset.openPost);
  });
  container.querySelectorAll('[data-open-story]').forEach((el) => {
    el.onclick = () => openStoryModal(el.dataset.openStory);
  });
  container.querySelectorAll('[data-open-live]').forEach((el) => {
    el.onclick = () => openWatchLiveModal(el.dataset.openLive);
  });
  container.querySelectorAll('[data-like-post]').forEach((b) => {
    b.onclick = (e) => { e.stopPropagation(); togglePostLike(b.dataset.likePost); };
  });
  container.querySelectorAll('[data-bookmark-post]').forEach((b) => {
    b.onclick = (e) => { e.stopPropagation(); toggleBookmark(b.dataset.bookmarkPost); };
  });
  container.querySelectorAll('[data-delete-post]').forEach((b) => {
    b.onclick = async (e) => {
      e.stopPropagation();
      if (!window.confirm('Delete this post permanently?')) return;
      await deleteDoc(doc(db, 'posts', b.dataset.deletePost));
      showToast('Post deleted');
    };
  });
  container.querySelectorAll('[data-share-post]').forEach((b) => {
    b.onclick = async (e) => {
      e.stopPropagation();
      const post = state.posts.find((p) => p.id === b.dataset.sharePost);
      const url = `${window.location.origin}${window.location.pathname}#post-${b.dataset.sharePost}`;
      await navigator.clipboard?.writeText(url).catch(() => undefined);
      showToast(`Link copied for @${post?.authorUsername || 'Momi'} post`);
    };
  });
  container.querySelectorAll('[data-slide]').forEach((b) => {
    b.onclick = (e) => {
      e.stopPropagation();
      const [postId, deltaStr] = b.dataset.slide.split(':');
      const post = state.posts.find((p) => p.id === postId);
      const count = 1 + (Array.isArray(post?.extraImageDatas) ? post.extraImageDatas.length : 0);
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
  const markerRef = doc(db, 'users', uid, 'likedPosts', postId);
  const batch = writeBatch(db);
  if (already) {
    batch.delete(markerRef);
    batch.update(doc(db, 'posts', postId), { likesCount: increment(-1), updatedAt: serverTimestamp() });
  } else {
    batch.set(markerRef, { targetId: postId, createdAt: serverTimestamp() });
    batch.update(doc(db, 'posts', postId), { likesCount: increment(1), updatedAt: serverTimestamp() });
    if (post.userId !== uid) {
      batch.set(doc(db, 'users', post.userId, 'notifications', makeClientId('notif')), {
        userId: post.userId,
        actorUid: uid,
        actorUsername: state.profile.username,
        actorAvatarKey: state.profile.avatarKey || 'avatar_aria',
        message: 'liked your post.',
        timeLabel: 'Just now',
        section: 'Today',
        previewImageKey: post.imageKey || 'img_post_coastal',
        isFollowAction: false,
        isFollowingBack: false,
        isUnread: true,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp()
      });
    }
  }
  await batch.commit();
}

async function toggleBookmark(postId) {
  const uid = state.user.uid;
  const ref = doc(db, 'users', uid, 'bookmarks', postId);
  if (state.bookmarkIds.has(postId)) {
    await deleteDoc(ref);
    showToast('Removed from Saved');
  } else {
    await setDoc(ref, { targetId: postId, createdAt: serverTimestamp() });
    showToast('Saved to your profile');
  }
}

async function toggleFollow(targetUid, targetUsername) {
  const uid = state.user.uid;
  if (!state.profile || !targetUid || targetUid === uid) return;
  const followId = `${uid}_${targetUid}`;
  const ref = doc(db, 'follows', followId);
  const isFollowing = state.followingUids.has(targetUid);
  if (isFollowing) {
    await deleteDoc(ref);
  } else {
    const batch = writeBatch(db);
    batch.set(ref, {
      followerUid: uid,
      targetUid,
      followerUsername: state.profile.username,
      targetUsername,
      createdAt: serverTimestamp()
    });
    batch.set(doc(db, 'users', targetUid, 'notifications', makeClientId('notif')), {
      userId: targetUid,
      actorUid: uid,
      actorUsername: state.profile.username,
      actorAvatarKey: state.profile.avatarKey || 'avatar_aria',
      message: 'started following you.',
      timeLabel: 'Just now',
      section: 'Today',
      isFollowAction: true,
      isFollowingBack: false,
      isUnread: true,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp()
    });
    await batch.commit();
  }
}

async function openOrCreateDirectThread(otherProfile) {
  if (!state.profile || !otherProfile || otherProfile.userId === state.user.uid) return;
  const pair = [state.profile, otherProfile].sort((a, b) => a.userId.localeCompare(b.userId));
  const threadId = `${pair[0].userId}_${pair[1].userId}`;
  const threadRef = doc(db, 'threads', threadId);
  const existing = await getDoc(threadRef);
  if (!existing.exists()) {
    await setDoc(threadRef, {
      participantUids: [pair[0].userId, pair[1].userId],
      userAId: pair[0].userId,
      userAUsername: pair[0].username,
      userADisplayName: pair[0].displayName,
      userAAvatarKey: pair[0].avatarKey || 'avatar_aria',
      userBId: pair[1].userId,
      userBUsername: pair[1].username,
      userBDisplayName: pair[1].displayName,
      userBAvatarKey: pair[1].avatarKey || 'avatar_aria',
      lastMessage: '',
      lastSenderUid: '',
      unreadUid: '',
      timeLabel: 'Now',
      unreadCount: 0,
      isOnline: true,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp()
    });
  }
  selectThread(threadId);
  navigateTo('messages');
}

async function resolveVideoForPost(post) {
  if (state.videoCache[post.id]) return state.videoCache[post.id];
  if (post.videoData) {
    const url = await assembleVideoChunksToObjectUrl([post.videoData]);
    state.videoCache[post.id] = url;
    return url;
  }
  if ((post.videoChunksCount || 0) > 0) {
    const snap = await getDocs(query(collection(db, 'posts', post.id, 'videoChunks'), orderBy('index', 'asc')));
    const url = await assembleVideoChunksToObjectUrl(snap.docs.map((d) => d.data().data));
    state.videoCache[post.id] = url;
    return url;
  }
  return null;
}

async function openPostDetailModal(postId) {
  const post = state.posts.find((p) => p.id === postId);
  if (!post) return;
  updateDoc(doc(db, 'posts', postId), { viewsCount: increment(1), updatedAt: serverTimestamp() }).catch(() => undefined);

  let videoUrl = state.videoCache[postId];
  if ((post.mediaType === 'VIDEO' || post.isReel) && !videoUrl) {
    videoUrl = await resolveVideoForPost(post).catch(() => null);
  }

  const commentsSnap = await getDocs(query(collection(db, 'posts', postId, 'comments'), orderBy('createdAt', 'asc'), limit(50)));
  const comments = commentsSnap.docs.map((d) => ({ ...d.data(), id: d.id }));
  const morePosts = state.posts.filter((p) => p.id !== postId).slice(0, 4);

  openModal(`
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:14px;">
      <strong>@${escapeHtml(post.authorUsername)}</strong>
      <button type="button" class="btn-ghost btn-sm" id="closeModalBtn">Close</button>
    </div>
    <div class="post-detail-split">
      <div style="background:#000;border-radius:14px;overflow:hidden;display:grid;place-items:center;">
        ${
          videoUrl
            ? `<video src="${videoUrl}" controls autoplay playsinline style="width:100%;max-height:70vh;"></video>`
            : `<img src="${resolveImageSrc(post)}" alt="${escapeHtml(post.caption || '')}" style="width:100%;max-height:70vh;object-fit:contain;filter:${filterCssForTag(post.filterTag)};" />`
        }
      </div>
      <div style="display:flex;flex-direction:column;gap:12px;">
        <div class="post-caption">${renderRichCaptionHtml(post.caption || '')}</div>
        <div class="muted small">${formatCount(post.likesCount)} likes &middot; ${formatCount(post.commentsCount)} comments &middot; ${formatCount((post.viewsCount || 0) + 1)} views</div>
        <div style="flex:1;max-height:320px;overflow-y:auto;display:flex;flex-direction:column;gap:8px;border-top:1px solid var(--border);padding-top:10px;">
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
        <div class="rednote-grid">
          ${morePosts.map((mp) => `
            <article class="rednote-card" data-open-post="${escapeHtml(mp.id)}">
              <img class="rednote-thumb" src="${resolveImageSrc(mp)}" alt="${escapeHtml(mp.caption || '')}" />
              <div class="rednote-meta"><div class="rednote-title">${escapeHtml(mp.caption || '@' + mp.authorUsername)}</div></div>
            </article>
          `).join('')}
        </div>
      </div>
    ` : ''}
  `);

  document.getElementById('closeModalBtn').onclick = closeModal;
  bindFeedInteractions(modalContent);

  document.getElementById('modalCommentForm').onsubmit = async (e) => {
    e.preventDefault();
    const input = document.getElementById('modalCommentInput');
    const text = input.value.trim();
    if (!text || !state.profile) return;
    input.value = '';
    const batch = writeBatch(db);
    batch.set(doc(db, 'posts', postId, 'comments', makeClientId('cmt')), {
      userId: state.user.uid,
      authorUsername: state.profile.username,
      authorAvatarKey: state.profile.avatarKey || 'avatar_aria',
      text: text.slice(0, 500),
      likesCount: 0,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp()
    });
    batch.update(doc(db, 'posts', postId), { commentsCount: increment(1), updatedAt: serverTimestamp() });
    await batch.commit();
    openPostDetailModal(postId);
  };
}

function openStoryModal(storyId) {
  const story = state.stories.find((s) => s.id === storyId);
  if (!story) return;
  setDoc(doc(db, 'users', state.user.uid, 'seenStories', storyId), {
    targetId: storyId,
    createdAt: serverTimestamp()
  }).catch(() => undefined);

  openModal(`
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;">
      <strong>@${escapeHtml(story.username)} &middot; Story</strong>
      <button type="button" class="btn-ghost btn-sm" id="closeModalBtn">Close</button>
    </div>
    <div style="text-align:center;background:#000;border-radius:16px;overflow:hidden;">
      <img src="${resolveImageSrc(story)}" alt="${escapeHtml(story.caption || '')}" style="max-height:72vh;width:100%;object-fit:contain;" />
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
  let newAvatarData = p.avatarData || null;
  document.getElementById('editAvatarInput').onchange = async (e) => {
    const file = e.target.files?.[0];
    if (file) newAvatarData = await compressFileToBase64(file, 360, 180000);
  };
  document.getElementById('editProfileForm').onsubmit = async (e) => {
    e.preventDefault();
    await updateDoc(doc(db, 'users', state.user.uid), {
      displayName: document.getElementById('editDisplayName').value.trim().slice(0, 80),
      username: document.getElementById('editUsername').value.trim().toLowerCase().replace(/[^a-z0-9._]/g, '').slice(0, 50),
      category: document.getElementById('editCategory').value.trim().slice(0, 80),
      bio: document.getElementById('editBio').value.trim().slice(0, 300),
      website: document.getElementById('editWebsite').value.trim().slice(0, 200),
      avatarData: newAvatarData,
      updatedAt: serverTimestamp()
    });
    closeModal();
    showToast('Profile updated');
  };
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
  openModal(`
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:14px;">
      <h3 style="margin:0;">Go Live from Desktop</h3>
      <button type="button" class="btn-ghost btn-sm" id="closeModalBtn">Close</button>
    </div>
    <div style="display:grid;gap:12px;">
      <video id="livePreviewVideo" autoplay muted playsinline style="width:100%;max-height:420px;background:#000;border-radius:16px;"></video>
      <input id="liveTitleInput" class="input" value="Live from Momi Desktop Studio" placeholder="Stream title..." />
      <div style="display:flex;gap:10px;">
        <button type="button" class="btn-primary" id="startLiveBtn">Start Live Broadcast</button>
        <button type="button" class="btn-danger hidden" id="endLiveBtn">End Broadcast</button>
      </div>
    </div>
  `);
  document.getElementById('closeModalBtn').onclick = async () => {
    await updateDoc(doc(db, 'liveStreams', state.user.uid), { isLive: false, updatedAt: serverTimestamp() }).catch(() => undefined);
    closeModal();
  };

  navigator.mediaDevices?.getUserMedia({ video: true, audio: false }).then((stream) => {
    state.liveStreamMedia = stream;
    const vid = document.getElementById('livePreviewVideo');
    if (vid) vid.srcObject = stream;
  }).catch(() => showToast('Camera permission denied or unavailable.'));

  document.getElementById('startLiveBtn').onclick = async () => {
    const uid = state.user.uid;
    const title = document.getElementById('liveTitleInput').value.trim() || 'Live Studio Session';
    await setDoc(doc(db, 'liveStreams', uid), {
      userId: uid,
      username: state.profile.username,
      displayName: state.profile.displayName,
      avatarKey: state.profile.avatarKey || 'avatar_aria',
      authorVerified: Boolean(state.profile.isVerified),
      title: title.slice(0, 140),
      category: 'Live',
      isLive: true,
      viewerCount: 0,
      likesCount: 0,
      giftsCredits: 0,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp()
    });
    document.getElementById('startLiveBtn').classList.add('hidden');
    document.getElementById('endLiveBtn').classList.remove('hidden');

    let seq = 0;
    const snapCanvas = document.createElement('canvas');
    snapCanvas.width = 480;
    snapCanvas.height = 360;
    const vid = document.getElementById('livePreviewVideo');
    state.liveTimer = setInterval(() => {
      if (!vid || vid.readyState < 2) return;
      snapCanvas.getContext('2d').drawImage(vid, 0, 0, snapCanvas.width, snapCanvas.height);
      const b64 = (snapCanvas.toDataURL('image/jpeg', 0.55).split(',')[1] || '').slice(0, 180000);
      setDoc(doc(db, 'liveStreams', uid, 'liveFrames', 'latest'), {
        userId: uid,
        seq: seq++,
        frameData: b64,
        cameraEnabled: true,
        micMuted: false,
        updatedAt: serverTimestamp()
      }).catch(() => undefined);
    }, 550);
    showToast('You are LIVE on Momi!');
  };

  document.getElementById('endLiveBtn').onclick = async () => {
    await updateDoc(doc(db, 'liveStreams', state.user.uid), {
      isLive: false,
      viewerCount: 0,
      updatedAt: serverTimestamp()
    }).catch(() => undefined);
    closeModal();
    showToast('Live broadcast ended');
  };
}

function openWatchLiveModal(streamId) {
  const stream = state.liveStreams.find((s) => s.userId === streamId);
  if (!stream) return;
  updateDoc(doc(db, 'liveStreams', streamId), { viewerCount: increment(1), updatedAt: serverTimestamp() }).catch(() => undefined);

  openModal(`
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;">
      <div>
        <strong>@${escapeHtml(stream.username)}</strong>
        <span class="live-pill-badge" style="margin-left:6px;">LIVE</span>
        <span class="muted small" style="margin-left:8px;">${escapeHtml(stream.title)}</span>
      </div>
      <button type="button" class="btn-ghost btn-sm" id="closeModalBtn">Leave</button>
    </div>
    <div class="post-detail-split">
      <div>
        <img id="liveRemoteFrameImg" src="${resolveImageSrc({ imageKey: 'img_post_coastal' })}" alt="Live stream" style="width:100%;max-height:60vh;object-fit:cover;border-radius:16px;background:#000;" />
        <div style="display:flex;gap:8px;flex-wrap:wrap;margin-top:10px;">
          <button type="button" class="btn-outline btn-sm" id="liveHeartBtn">&#9829; Heart</button>
          ${GIFT_CATALOG.map((g) => `
            <button type="button" class="btn-primary btn-sm" data-send-gift="${g.key}:${g.credits}:${escapeHtml(g.name)}">${escapeHtml(g.name)} (${g.credits})</button>
          `).join('')}
        </div>
      </div>
      <div style="display:flex;flex-direction:column;gap:10px;">
        <strong>Live Chat</strong>
        <div id="liveChatBox" style="flex:1;max-height:360px;overflow-y:auto;display:flex;flex-direction:column;gap:6px;"></div>
        <form id="liveChatForm" style="display:flex;gap:8px;">
          <input id="liveChatInput" class="input" placeholder="Say hello in live chat..." />
          <button type="submit" class="btn-primary btn-sm">Send</button>
        </form>
      </div>
    </div>
  `);

  document.getElementById('closeModalBtn').onclick = () => {
    updateDoc(doc(db, 'liveStreams', streamId), { viewerCount: increment(-1), updatedAt: serverTimestamp() }).catch(() => undefined);
    closeModal();
  };

  state.liveUnsubs.push(onSnapshot(doc(db, 'liveStreams', streamId, 'liveFrames', 'latest'), (snap) => {
    const img = document.getElementById('liveRemoteFrameImg');
    if (snap.exists() && img && snap.data().frameData) {
      img.src = `data:image/jpeg;base64,${snap.data().frameData}`;
    }
  }));

  state.liveUnsubs.push(onSnapshot(query(collection(db, 'liveStreams', streamId, 'liveComments'), orderBy('createdAt', 'asc'), limit(50)), (snap) => {
    const box = document.getElementById('liveChatBox');
    if (!box) return;
    box.innerHTML = snap.docs.map((d) => {
      const c = d.data();
      return `<div><strong>@${escapeHtml(c.username)}</strong> ${escapeHtml(c.text)}</div>`;
    }).join('');
    box.scrollTop = box.scrollHeight;
  }));

  document.getElementById('liveHeartBtn').onclick = () => {
    updateDoc(doc(db, 'liveStreams', streamId), { likesCount: increment(1), updatedAt: serverTimestamp() }).catch(() => undefined);
  };

  document.getElementById('liveChatForm').onsubmit = async (e) => {
    e.preventDefault();
    const input = document.getElementById('liveChatInput');
    const text = input.value.trim();
    if (!text || !state.profile) return;
    input.value = '';
    await setDoc(doc(db, 'liveStreams', streamId, 'liveComments', makeClientId('lc')), {
      userId: state.user.uid,
      username: state.profile.username,
      avatarKey: state.profile.avatarKey || 'avatar_aria',
      text: text.slice(0, 300),
      createdAt: serverTimestamp()
    });
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
      const batch = writeBatch(db);
      batch.update(doc(db, 'wallets', state.user.uid), {
        balance: w.balance - credits,
        totalSpent: (w.totalSpent || 0) + credits,
        lastTxId: txId,
        updatedAt: serverTimestamp()
      });
      batch.set(doc(db, 'creditTransactions', txId), {
        txId,
        type: 'GIFT',
        senderUid: state.user.uid,
        senderUsername: state.profile.username,
        recipientUid: stream.userId,
        recipientUsername: stream.username,
        creditsAmount: credits,
        priceUsd: 0,
        paymentMethod: 'WALLET_CREDITS',
        giftKey,
        giftName,
        streamId,
        recipientClaimed: false,
        status: 'COMPLETED',
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp()
      });
      batch.update(doc(db, 'liveStreams', streamId), {
        giftsCredits: increment(credits),
        updatedAt: serverTimestamp()
      });
      await batch.commit();
      showToast(`Sent ${giftName} (${credits} credits) to @${stream.username}!`);
    };
  });
}
