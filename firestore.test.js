const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function serverTimestamp() {
  return new Date(Date.now() - 2000);
}

function validProfilePayload(uid) {
  return {
    userId: uid,
    username: "aria.studio",
    displayName: "Aria Vance",
    category: "Visual Creator",
    bio: "Chasing cobalt blue skies & deep maroon shadows.",
    website: "momi.photo/aria.studio",
    avatarKey: "avatar_aria",
    followersCount: 12840,
    followingCount: 418,
    isVerified: true,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

function validPostPayload(uid) {
  return {
    userId: uid,
    authorUsername: "aria.studio",
    authorDisplayName: "Aria Vance",
    authorAvatarKey: "avatar_aria",
    authorVerified: true,
    location: "Positano, Amalfi Coast",
    imageKey: "img_post_coastal",
    localImagePath: null,
    filterTag: "NONE",
    caption: "Golden hour on the coast.",
    likesCount: 120,
    isLiked: false,
    isBookmarked: false,
    isFollowingAuthor: true,
    commentsCount: 0,
    category: "Travel",
    isReel: false,
    audioTitle: "",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

function validCommentPayload(uid) {
  return {
    userId: uid,
    authorUsername: "aria.studio",
    authorAvatarKey: "avatar_aria",
    text: "Unreal tones!",
    likesCount: 0,
    isLiked: false,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

function validStoryPayload(uid) {
  return {
    userId: uid,
    username: "aria.studio",
    avatarKey: "avatar_aria",
    imageKey: "img_post_coastal",
    localImagePath: null,
    caption: "Morning ferry",
    isSeen: false,
    isOwnStory: true,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

function validNotificationPayload(uid) {
  return {
    userId: uid,
    actorUsername: "kenji.lens",
    actorAvatarKey: "avatar_kenji",
    message: "liked your photo.",
    timeLabel: "12m",
    section: "Today",
    previewImageKey: "img_post_coastal",
    isFollowAction: true,
    isFollowingBack: false,
    isUnread: true,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

function validThreadPayload(uid) {
  return {
    userId: uid,
    peerUsername: "elena.maris",
    peerDisplayName: "Elena Maris",
    peerAvatarKey: "avatar_elena",
    lastMessage: "Sent the LUTs!",
    timeLabel: "14m",
    unreadCount: 1,
    isOnline: true,
    noteText: "Amalfi golden hour",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

function validChatMessagePayload(uid) {
  return {
    userId: uid,
    senderUsername: "aria.studio",
    text: "Hello!",
    isFromMe: true,
    createdAt: serverTimestamp(),
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

// --- 1. Unauthenticated bounds ---

test("Unauthenticated user: cannot read or write any Momi collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("posts").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID))
  );
});

// --- 2. Cross-user isolation ---

test("Cross-user isolation: Bob cannot read or write Alice's profile, posts, stories, notifications, or threads", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID)));
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("posts").doc("post_1").set(validPostPayload(ALICE_UID))
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("posts").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("posts").doc("post_1").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("posts").doc("post_1").update({
      isLiked: true,
      likesCount: 121,
      updatedAt: serverTimestamp(),
    })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("posts").doc("post_2").set(validPostPayload(ALICE_UID))
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("stories").doc("story_1").set(validStoryPayload(ALICE_UID))
  );
});

// --- 3. UserProfile CRUD & validation ---

test("Owner: can create, read, and update allowed fields on UserProfile, but rejects immutable createdAt or ghost fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(profileRef.set(validProfilePayload(ALICE_UID)));
  await assertSucceeds(profileRef.get());

  await assertSucceeds(
    profileRef.update({
      displayName: "Aria Updated",
      bio: "New bio",
      updatedAt: serverTimestamp(),
    })
  );

  // Reject ghost field on create
  await assertFails(
    aliceDb.collection("users").doc("bob_spoof").set({
      ...validProfilePayload("bob_spoof"),
      admin: true,
    })
  );

  // Reject modifying createdAt or isVerified on update
  await assertFails(
    profileRef.update({
      isVerified: false,
      updatedAt: serverTimestamp(),
    })
  );
  await assertFails(
    profileRef.update({
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );
});

// --- 4. Post CRUD, action updates, and schema bounds ---

test("Owner: can create, list, action-update, and delete Posts; rejects invalid filterTag or negative likesCount", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const postsCol = aliceDb.collection("users").doc(ALICE_UID).collection("posts");
  const postRef = postsCol.doc("post_1");

  await assertSucceeds(postRef.set(validPostPayload(ALICE_UID)));
  await assertSucceeds(postsCol.get());

  await assertSucceeds(
    postRef.update({ isLiked: true, likesCount: 121, updatedAt: serverTimestamp() })
  );
  await assertSucceeds(
    postRef.update({ isBookmarked: true, updatedAt: serverTimestamp() })
  );
  await assertSucceeds(
    postRef.update({ isFollowingAuthor: false, updatedAt: serverTimestamp() })
  );
  await assertSucceeds(
    postRef.update({ commentsCount: 1, updatedAt: serverTimestamp() })
  );

  // Invalid filterTag
  await assertFails(
    postsCol.doc("post_bad_filter").set({
      ...validPostPayload(ALICE_UID),
      filterTag: "SEPIA",
    })
  );

  // Negative likesCount
  await assertFails(
    postRef.update({ isLiked: false, likesCount: -1, updatedAt: serverTimestamp() })
  );

  await assertSucceeds(postRef.delete());
});

// --- 5. Comments parent-exists & update ---

test("Comments: require parent Post to exist and allow owner like toggle", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const postRef = aliceDb.collection("users").doc(ALICE_UID).collection("posts").doc("post_1");
  const commentOnMissingPost = postRef.collection("comments").doc("c_1");

  await assertFails(commentOnMissingPost.set(validCommentPayload(ALICE_UID)));

  await assertSucceeds(postRef.set(validPostPayload(ALICE_UID)));
  await assertSucceeds(commentOnMissingPost.set(validCommentPayload(ALICE_UID)));
  await assertSucceeds(
    commentOnMissingPost.update({
      isLiked: true,
      likesCount: 1,
      updatedAt: serverTimestamp(),
    })
  );
  await assertSucceeds(commentOnMissingPost.delete());
});

// --- 6. Stories, Notifications, Threads, and append-only ChatMessages ---

test("Stories, Notifications, Threads, and ChatMessages enforce owner permissions and parent thread existence", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const userDoc = aliceDb.collection("users").doc(ALICE_UID);

  // Story
  const storyRef = userDoc.collection("stories").doc("story_1");
  await assertSucceeds(storyRef.set(validStoryPayload(ALICE_UID)));
  await assertSucceeds(storyRef.update({ isSeen: true, updatedAt: serverTimestamp() }));

  // Notification
  const notifRef = userDoc.collection("notifications").doc("notif_1");
  await assertSucceeds(notifRef.set(validNotificationPayload(ALICE_UID)));
  await assertSucceeds(notifRef.update({ isFollowingBack: true, updatedAt: serverTimestamp() }));
  await assertSucceeds(notifRef.update({ isUnread: false, updatedAt: serverTimestamp() }));

  // Thread & ChatMessage
  const threadRef = userDoc.collection("threads").doc("thread_1");
  const msgRef = threadRef.collection("messages").doc("msg_1");

  // Message fails when parent thread doesn't exist
  await assertFails(msgRef.set(validChatMessagePayload(ALICE_UID)));

  await assertSucceeds(threadRef.set(validThreadPayload(ALICE_UID)));
  await assertSucceeds(msgRef.set(validChatMessagePayload(ALICE_UID)));

  // ChatMessage is append-only (update denied)
  await assertFails(msgRef.update({ text: "Tampered" }));

  // Thread unread clear & lastMessage update succeed
  await assertSucceeds(
    threadRef.update({
      lastMessage: "Hello!",
      timeLabel: "now",
      unreadCount: 0,
      updatedAt: serverTimestamp(),
    })
  );
});

// --- 7. isValidId bounds ---

test("isValidId rejects invalid document IDs", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("posts").doc("invalid.id").set(validPostPayload(ALICE_UID))
  );
});
