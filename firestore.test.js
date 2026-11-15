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
const CHARLIE_UID = "charlie_789";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function serverTimestamp() {
  return new Date(Date.now() - 2000);
}

function validProfilePayload(uid, username = "aria.studio", displayName = "Aria Vance") {
  return {
    userId: uid,
    username,
    displayName,
    category: "Visual Creator",
    bio: "Chasing silk blue skies & deep maroon shadows.",
    website: `momi.photo/${username}`,
    avatarKey: "avatar_aria",
    followersCount: 0,
    followingCount: 0,
    isVerified: true,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

function validPostPayload(uid, username = "aria.studio") {
  return {
    userId: uid,
    authorUsername: username,
    authorDisplayName: "Aria Vance",
    authorAvatarKey: "avatar_aria",
    authorVerified: true,
    location: "Positano, Amalfi Coast",
    imageKey: "img_post_coastal",
    localImagePath: null,
    imageData: null,
    filterTag: "NONE",
    caption: "Golden hour on the coast.",
    likesCount: 0,
    commentsCount: 0,
    category: "Travel",
    isReel: false,
    audioTitle: "",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

function validCommentPayload(uid, username = "bob.lens") {
  return {
    userId: uid,
    authorUsername: username,
    authorAvatarKey: "avatar_kenji",
    text: "Unreal tones!",
    likesCount: 0,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

function validStoryPayload(uid, username = "aria.studio") {
  return {
    userId: uid,
    username,
    avatarKey: "avatar_aria",
    imageKey: "img_post_coastal",
    localImagePath: null,
    imageData: null,
    caption: "Morning ferry",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

function validNotificationPayload(targetUid, actorUid) {
  return {
    userId: targetUid,
    actorUid,
    actorUsername: "bob.lens",
    actorAvatarKey: "avatar_kenji",
    message: "liked your photo.",
    timeLabel: "now",
    section: "Today",
    previewImageKey: "img_post_coastal",
    isFollowAction: false,
    isFollowingBack: false,
    isUnread: true,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

function validThreadPayload(uidA, uidB) {
  return {
    participantUids: [uidA, uidB],
    userAId: uidA,
    userAUsername: "aria.studio",
    userADisplayName: "Aria Vance",
    userAAvatarKey: "avatar_aria",
    userBId: uidB,
    userBUsername: "bob.lens",
    userBDisplayName: "Bob Lens",
    userBAvatarKey: "avatar_kenji",
    lastMessage: "Hey!",
    lastSenderUid: uidA,
    unreadUid: uidB,
    timeLabel: "now",
    unreadCount: 1,
    isOnline: true,
    noteText: null,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

function validChatMessagePayload(participantUids, senderUid, senderUsername = "aria.studio") {
  return {
    participantUids,
    senderUid,
    senderUsername,
    text: "Hello from Momi!",
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

test("Unauthenticated user: cannot read or write any shared or private Momi collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").get());
  await assertFails(unauthDb.collection("posts").get());
  await assertFails(unauthDb.collection("stories").get());
  await assertFails(unauthDb.collection("follows").get());
  await assertFails(unauthDb.collection("threads").get());
  await assertFails(unauthDb.collection("posts").doc("p1").set(validPostPayload(ALICE_UID)));
});

// --- 2. Public Creator Profiles + Private Subcollections ---

test("Users: signed-in users can read creator profiles, only owner can update; private subcollections isolated", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID)));
  await assertSucceeds(bobDb.collection("users").doc(ALICE_UID).get());
  await assertSucceeds(bobDb.collection("users").get());

  // Bob cannot edit or delete Alice's profile
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({ displayName: "Hacked", updatedAt: serverTimestamp() })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).delete());

  // Alice's private bookmarks & likedPosts are strictly private
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("bookmarks").doc("post_1").set({
      targetId: "post_1",
      createdAt: serverTimestamp(),
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("bookmarks").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("likedPosts").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("seenStories").get());
});

// --- 3. Multi-user Shared Feed Posts & Counter Deltas ---

test("Posts: Bob can view, like (+1/-1), and comment on Alice's post, but cannot edit caption, delete, or tamper count", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(aliceDb.collection("posts").doc("post_1").set(validPostPayload(ALICE_UID)));

  // Bob reads shared feed and post
  await assertSucceeds(bobDb.collection("posts").get());
  await assertSucceeds(bobDb.collection("posts").doc("post_1").get());

  // Bob likes Alice's post (+1) and tracks own likedPosts marker
  await assertSucceeds(
    bobDb.collection("posts").doc("post_1").update({ likesCount: 1, updatedAt: serverTimestamp() })
  );
  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).collection("likedPosts").doc("post_1").set({
      targetId: "post_1",
      createdAt: serverTimestamp(),
    })
  );

  // Reject counter jump (+99) or negative count (-1)
  await assertFails(
    bobDb.collection("posts").doc("post_1").update({ likesCount: 100, updatedAt: serverTimestamp() })
  );
  await assertFails(
    bobDb.collection("posts").doc("post_1").update({ likesCount: -1, updatedAt: serverTimestamp() })
  );

  // Reject Bob editing Alice's caption or deleting Alice's post
  await assertFails(
    bobDb.collection("posts").doc("post_1").update({ caption: "Tampered", updatedAt: serverTimestamp() })
  );
  await assertFails(bobDb.collection("posts").doc("post_1").delete());

  // Bob comments on Alice's post and increments commentsCount by +1
  await assertSucceeds(
    bobDb.collection("posts").doc("post_1").collection("comments").doc("c1").set(validCommentPayload(BOB_UID))
  );
  await assertSucceeds(
    bobDb.collection("posts").doc("post_1").update({ commentsCount: 1, updatedAt: serverTimestamp() })
  );

  // Alice (post owner) can read Bob's comment and moderate/delete it or delete her own post
  await assertSucceeds(aliceDb.collection("posts").doc("post_1").collection("comments").get());
  await assertSucceeds(aliceDb.collection("posts").doc("post_1").collection("comments").doc("c1").delete());
  await assertSucceeds(aliceDb.collection("posts").doc("post_1").delete());
});

// --- 4. ImageData payload bounds ---

test("Posts & Stories: allow compressed imageData under 700KB, reject oversized strings", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const okPayload = {
    ...validPostPayload(ALICE_UID),
    imageData: "A".repeat(1024),
  };
  await assertSucceeds(aliceDb.collection("posts").doc("post_img_ok").set(okPayload));

  const badPayload = {
    ...validPostPayload(ALICE_UID),
    imageData: "A".repeat(700001),
  };
  await assertFails(aliceDb.collection("posts").doc("post_img_bad").set(badPayload));

  await assertSucceeds(
    aliceDb.collection("stories").doc("story_1").set(validStoryPayload(ALICE_UID))
  );
  await assertFails(
    testEnv.authenticatedContext(BOB_UID).firestore().collection("stories").doc("story_1").delete()
  );
});

// --- 5. Follows & Cross-user Notifications ---

test("Follows & Notifications: deterministic follow edges and verified actorUid notifications", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  const followId = `${BOB_UID}_${ALICE_UID}`;
  await assertSucceeds(
    bobDb.collection("follows").doc(followId).set({
      followerUid: BOB_UID,
      targetUid: ALICE_UID,
      followerUsername: "bob.lens",
      targetUsername: "aria.studio",
      createdAt: serverTimestamp(),
    })
  );

  // Cannot spoof someone else's follow or follow self
  await assertFails(
    charlieDb.collection("follows").doc(`${BOB_UID}_${CHARLIE_UID}`).set({
      followerUid: BOB_UID,
      targetUid: CHARLIE_UID,
      followerUsername: "bob.lens",
      targetUsername: "charlie",
      createdAt: serverTimestamp(),
    })
  );
  await assertFails(
    bobDb.collection("follows").doc(`${BOB_UID}_${BOB_UID}`).set({
      followerUid: BOB_UID,
      targetUid: BOB_UID,
      followerUsername: "bob.lens",
      targetUsername: "bob.lens",
      createdAt: serverTimestamp(),
    })
  );

  // Bob can send Alice a notification with actorUid = BOB_UID
  await assertSucceeds(
    bobDb.collection("users").doc(ALICE_UID).collection("notifications").doc("n1").set(
      validNotificationPayload(ALICE_UID, BOB_UID)
    )
  );

  // Charlie cannot spoof actorUid = BOB_UID in notification to Alice
  await assertFails(
    charlieDb.collection("users").doc(ALICE_UID).collection("notifications").doc("n2").set(
      validNotificationPayload(ALICE_UID, BOB_UID)
    )
  );

  // Bob cannot read Alice's notifications, Alice can read & mark read
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("notifications").get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("notifications").get());
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("notifications").doc("n1").update({
      isUnread: false,
      updatedAt: serverTimestamp(),
    })
  );
});

// --- 6. Shared 1:1 DM Threads (Strict Participant Isolation) ---

test("Threads & Messages: Alice and Bob share a 1:1 thread and exchange messages; Charlie is denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  const participants = [ALICE_UID, BOB_UID];
  const threadId = `${ALICE_UID}_${BOB_UID}`;
  const threadRefAlice = aliceDb.collection("threads").doc(threadId);
  const threadRefBob = bobDb.collection("threads").doc(threadId);

  // Before creation: participants can check existence (returns empty snap); Charlie cannot
  await assertSucceeds(threadRefAlice.get());
  await assertFails(charlieDb.collection("threads").doc(threadId).get());

  await assertSucceeds(threadRefAlice.set(validThreadPayload(ALICE_UID, BOB_UID)));

  // Client queries threads using where("participantUids", "array-contains", uid)
  await assertSucceeds(
    aliceDb.collection("threads").where("participantUids", "array-contains", ALICE_UID).get()
  );
  await assertSucceeds(
    bobDb.collection("threads").where("participantUids", "array-contains", BOB_UID).get()
  );

  // Charlie cannot read or query Alice-Bob thread
  await assertFails(charlieDb.collection("threads").doc(threadId).get());
  await assertFails(
    charlieDb.collection("threads").where("participantUids", "array-contains", ALICE_UID).get()
  );

  // Alice and Bob exchange messages
  await assertSucceeds(
    threadRefAlice.collection("messages").doc("m1").set(validChatMessagePayload(participants, ALICE_UID))
  );
  await assertSucceeds(
    threadRefBob.collection("messages").doc("m2").set(validChatMessagePayload(participants, BOB_UID, "bob.lens"))
  );
  await assertSucceeds(
    threadRefBob.update({
      lastMessage: "Hey back!",
      lastSenderUid: BOB_UID,
      timeLabel: "now",
      unreadUid: ALICE_UID,
      unreadCount: 1,
      updatedAt: serverTimestamp(),
    })
  );

  // Charlie cannot read m1, list messages unfiltered, query Alice's messages, or inject messages into Alice-Bob thread
  await assertFails(
    charlieDb.collection("threads").doc(threadId).collection("messages").doc("m1").get()
  );
  await assertFails(
    charlieDb.collection("threads").doc(threadId).collection("messages").get()
  );
  await assertFails(
    charlieDb.collection("threads").doc(threadId).collection("messages")
      .where("participantUids", "array-contains", ALICE_UID).get()
  );
  await assertFails(
    charlieDb.collection("threads").doc(threadId).collection("messages").doc("m3").set(
      validChatMessagePayload(participants, CHARLIE_UID, "charlie")
    )
  );
});
