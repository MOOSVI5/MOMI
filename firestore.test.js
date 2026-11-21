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
    isVerified: false,
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

test("Users: signed-in users can read creator profiles, only owner updates profile/avatarData, only admin verifies; private subcollections isolated", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true }).firestore();
  const adminDb = testEnv.authenticatedContext("admin_momi", {
    email: "syali6511@gmail.com",
    email_verified: true,
  }).firestore();

  // Non-admin cannot self-create as verified
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      ...validProfilePayload(ALICE_UID),
      isVerified: true,
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID)));
  await assertSucceeds(bobDb.collection("users").doc(ALICE_UID).get());
  await assertSucceeds(bobDb.collection("users").get());

  // Owner can upload custom compressed avatarData and update own profile, but cannot self-verify
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      avatarData: "Q1VTVE9NX0FWQVRBUg==",
      updatedAt: serverTimestamp(),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isVerified: true,
      updatedAt: serverTimestamp(),
    })
  );

  // Admin (syali6511@gmail.com) can grant & revoke verified badge, but cannot alter Alice's displayName
  await assertSucceeds(
    adminDb.collection("users").doc(ALICE_UID).update({
      isVerified: true,
      updatedAt: serverTimestamp(),
    })
  );
  await assertSucceeds(
    adminDb.collection("users").doc(ALICE_UID).update({
      isVerified: false,
      updatedAt: serverTimestamp(),
    })
  );
  await assertFails(
    adminDb.collection("users").doc(ALICE_UID).update({
      displayName: "Admin Edited",
      updatedAt: serverTimestamp(),
    })
  );

  // Bob cannot edit, verify, or delete Alice's profile
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({ displayName: "Hacked", updatedAt: serverTimestamp() })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({ isVerified: true, updatedAt: serverTimestamp() })
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

test("Posts & Stories: allow compressed imageData/videoData under 700KB, reject oversized strings or invalid mediaType", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const okPayload = {
    ...validPostPayload(ALICE_UID),
    imageData: "A".repeat(1024),
  };
  await assertSucceeds(aliceDb.collection("posts").doc("post_img_ok").set(okPayload));

  const okVideoPayload = {
    ...validPostPayload(ALICE_UID),
    mediaType: "VIDEO",
    localVideoPath: "/data/user/0/app/files/luma_media/clip_1.mp4",
    videoData: "B".repeat(2048),
    videoDurationMs: 6500,
    audioTitle: "Golden Hour Lo-Fi",
    localAudioPath: "/data/user/0/app/cache/momi_music/golden_hour.wav",
    isReel: true,
  };
  await assertSucceeds(aliceDb.collection("posts").doc("post_vid_ok").set(okVideoPayload));

  await assertSucceeds(
    aliceDb.collection("posts").doc("post_vid_ok").collection("videoChunks").doc("chunk_000").set({
      index: 0,
      data: "B".repeat(2048),
      createdAt: serverTimestamp(),
    })
  );
  await assertSucceeds(
    testEnv.authenticatedContext(BOB_UID).firestore().collection("posts").doc("post_vid_ok").collection("videoChunks").get()
  );
  await assertFails(
    testEnv.authenticatedContext(BOB_UID).firestore().collection("posts").doc("post_vid_ok").collection("videoChunks").doc("chunk_001").set({
      index: 1,
      data: "C".repeat(128),
      createdAt: serverTimestamp(),
    })
  );
  await assertFails(
    testEnv.authenticatedContext(BOB_UID).firestore().collection("posts").doc("post_vid_ok").collection("videoChunks").doc("chunk_000").delete()
  );
  await assertFails(
    aliceDb.collection("posts").doc("post_vid_ok").collection("videoChunks").doc("chunk_025").set({
      index: 25,
      data: "B".repeat(128),
      createdAt: serverTimestamp(),
    })
  );
  await assertFails(
    aliceDb.collection("posts").doc("post_vid_ok").collection("videoChunks").doc("chunk_big").set({
      index: 1,
      data: "B".repeat(700001),
      createdAt: serverTimestamp(),
    })
  );

  const badPayload = {
    ...validPostPayload(ALICE_UID),
    imageData: "A".repeat(700001),
  };
  await assertFails(aliceDb.collection("posts").doc("post_img_bad").set(badPayload));

  const badVideoPayload = {
    ...validPostPayload(ALICE_UID),
    mediaType: "VIDEO",
    videoData: "B".repeat(700001),
  };
  await assertFails(aliceDb.collection("posts").doc("post_vid_bad").set(badVideoPayload));

  const badMediaType = {
    ...validPostPayload(ALICE_UID),
    mediaType: "EXE",
  };
  await assertFails(aliceDb.collection("posts").doc("post_type_bad").set(badMediaType));

  await assertSucceeds(
    aliceDb.collection("stories").doc("story_1").set({
      ...validStoryPayload(ALICE_UID),
      mediaType: "VIDEO",
      videoData: "C".repeat(1024),
      videoChunksCount: 1,
      videoDurationMs: 4200,
      audioTitle: "Silk Breeze",
      localAudioPath: "/data/user/0/app/cache/momi_music/silk_breeze.wav",
    })
  );
  await assertSucceeds(
    aliceDb.collection("stories").doc("story_1").collection("videoChunks").doc("chunk_000").set({
      index: 0,
      data: "C".repeat(1024),
      createdAt: serverTimestamp(),
    })
  );
  await assertSucceeds(
    testEnv.authenticatedContext(BOB_UID).firestore().collection("stories").doc("story_1").collection("videoChunks").get()
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

// --- 7. Post Views Count & Live Streams Rules ---

test("Posts viewsCount: allows +1 view increment on new and legacy posts, blocks tampering", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Legacy post without viewsCount
  await assertSucceeds(aliceDb.collection("posts").doc("legacy_post").set(validPostPayload(ALICE_UID)));

  // First view on legacy doc initializes viewsCount to 1
  await assertSucceeds(
    bobDb.collection("posts").doc("legacy_post").update({
      viewsCount: 1,
      updatedAt: serverTimestamp(),
    })
  );

  // Subsequent view increments +1
  await assertSucceeds(
    aliceDb.collection("posts").doc("legacy_post").update({
      viewsCount: 2,
      updatedAt: serverTimestamp(),
    })
  );

  // Reject skipping (+5) or negative
  await assertFails(
    bobDb.collection("posts").doc("legacy_post").update({
      viewsCount: 7,
      updatedAt: serverTimestamp(),
    })
  );
  await assertFails(
    bobDb.collection("posts").doc("legacy_post").update({
      viewsCount: -1,
      updatedAt: serverTimestamp(),
    })
  );
});

test("LiveStreams & LiveComments: host broadcasts, viewers join/leave/heart/comment, spoofers denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  const streamPayload = {
    userId: ALICE_UID,
    username: "aria.studio",
    displayName: "Aria Vance",
    avatarKey: "avatar_aria",
    authorVerified: false,
    title: "Evening studio session",
    category: "Live",
    isLive: true,
    viewerCount: 0,
    likesCount: 0,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };

  // Bob cannot hijack Alice's live stream document
  await assertFails(bobDb.collection("liveStreams").doc(ALICE_UID).set(streamPayload));

  // Alice starts her own live stream
  await assertSucceeds(aliceDb.collection("liveStreams").doc(ALICE_UID).set(streamPayload));

  // Alice publishes rolling LiveFrames ('latest') and toggles camera/mic; viewers read
  await assertSucceeds(
    aliceDb.collection("liveStreams").doc(ALICE_UID).collection("liveFrames").doc("latest").set({
      userId: ALICE_UID,
      seq: 1,
      frameData: "JPEG_FRAME_B64",
      cameraEnabled: true,
      micMuted: false,
      updatedAt: serverTimestamp(),
    })
  );
  await assertSucceeds(
    bobDb.collection("liveStreams").doc(ALICE_UID).collection("liveFrames").doc("latest").get()
  );

  // Reject wrong docId, non-host frame overwrite, or oversized frameData
  await assertFails(
    aliceDb.collection("liveStreams").doc(ALICE_UID).collection("liveFrames").doc("frame_2").set({
      userId: ALICE_UID,
      seq: 2,
      frameData: "JPEG_FRAME_B64",
      cameraEnabled: true,
      micMuted: false,
      updatedAt: serverTimestamp(),
    })
  );
  await assertFails(
    bobDb.collection("liveStreams").doc(ALICE_UID).collection("liveFrames").doc("latest").set({
      userId: ALICE_UID,
      seq: 2,
      frameData: "HIJACKED",
      cameraEnabled: true,
      micMuted: false,
      updatedAt: serverTimestamp(),
    })
  );
  await assertFails(
    aliceDb.collection("liveStreams").doc(ALICE_UID).collection("liveFrames").doc("latest").set({
      userId: ALICE_UID,
      seq: 3,
      frameData: "A".repeat(220001),
      cameraEnabled: true,
      micMuted: false,
      updatedAt: serverTimestamp(),
    })
  );

  // Bob joins (+1 viewerCount), sends heart (+1 likesCount), increments giftsCredits (+50), and comments
  await assertSucceeds(
    bobDb.collection("liveStreams").doc(ALICE_UID).update({
      viewerCount: 1,
      updatedAt: serverTimestamp(),
    })
  );
  await assertSucceeds(
    bobDb.collection("liveStreams").doc(ALICE_UID).update({
      likesCount: 1,
      updatedAt: serverTimestamp(),
    })
  );
  await assertSucceeds(
    bobDb.collection("liveStreams").doc(ALICE_UID).update({
      giftsCredits: 50,
      updatedAt: serverTimestamp(),
    })
  );
  await assertFails(
    bobDb.collection("liveStreams").doc(ALICE_UID).update({
      giftsCredits: 5051,
      updatedAt: serverTimestamp(),
    })
  );
  await assertSucceeds(
    bobDb.collection("liveStreams").doc(ALICE_UID).collection("liveComments").doc("lc1").set({
      userId: BOB_UID,
      username: "bob.lens",
      avatarKey: "avatar_kenji",
      text: "Hello from the stream!",
      createdAt: serverTimestamp(),
    })
  );

  // Charlie cannot spoof Bob's comment userId, inflate viewerCount by +50, or end Alice's stream
  await assertFails(
    charlieDb.collection("liveStreams").doc(ALICE_UID).collection("liveComments").doc("lc2").set({
      userId: BOB_UID,
      username: "bob.lens",
      avatarKey: "avatar_kenji",
      text: "Spoofed!",
      createdAt: serverTimestamp(),
    })
  );
  await assertFails(
    charlieDb.collection("liveStreams").doc(ALICE_UID).update({
      viewerCount: 51,
      updatedAt: serverTimestamp(),
    })
  );
  await assertFails(
    charlieDb.collection("liveStreams").doc(ALICE_UID).update({
      isLive: false,
      updatedAt: serverTimestamp(),
    })
  );

  // Alice ends her own stream
  await assertSucceeds(
    aliceDb.collection("liveStreams").doc(ALICE_UID).update({
      isLive: false,
      viewerCount: 0,
      updatedAt: serverTimestamp(),
    })
  );
});

// --- 8. Multi-Image Carousel Posts, AudioData, and Rich DM Messages ---

test("Posts & Stories: support multi-image extraImageDatas/localExtraImagePaths and audioData within limits", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("posts").doc("post_carousel_audio").set({
      ...validPostPayload(ALICE_UID),
      imageData: "A".repeat(512),
      localExtraImagePaths: ["/tmp/img2.jpg", "/tmp/img3.jpg"],
      extraImageDatas: ["B".repeat(256), "C".repeat(256)],
      audioTitle: "Midnight Lo-Fi · Momi Studio",
      audioData: "D".repeat(1024),
    })
  );

  // Reject > 4 extra images
  await assertFails(
    aliceDb.collection("posts").doc("post_carousel_too_many").set({
      ...validPostPayload(ALICE_UID),
      extraImageDatas: ["A", "B", "C", "D", "E"],
    })
  );

  // Reject oversized audioData
  await assertFails(
    aliceDb.collection("stories").doc("story_audio_too_big").set({
      ...validStoryPayload(ALICE_UID),
      audioTitle: "Long Track",
      audioData: "A".repeat(660001),
    })
  );

  await assertSucceeds(
    aliceDb.collection("stories").doc("story_audio_ok").set({
      ...validStoryPayload(ALICE_UID),
      audioTitle: "Sunset Acoustic",
      audioData: "E".repeat(1024),
    })
  );
});

test("Threads & Messages: support IMAGE, VIDEO, and AUDIO chat messages; reject invalid messageType", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  const participants = [ALICE_UID, BOB_UID];
  const threadId = `${ALICE_UID}_${BOB_UID}`;
  await assertSucceeds(aliceDb.collection("threads").doc(threadId).set(validThreadPayload(ALICE_UID, BOB_UID)));

  await assertSucceeds(
    aliceDb.collection("threads").doc(threadId).collection("messages").doc("m_img").set({
      participantUids: participants,
      senderUid: ALICE_UID,
      senderUsername: "aria.studio",
      text: "Photo",
      messageType: "IMAGE",
      imageData: "IMG_B64",
      createdAt: serverTimestamp(),
    })
  );

  await assertSucceeds(
    bobDb.collection("threads").doc(threadId).collection("messages").doc("m_vid").set({
      participantUids: participants,
      senderUid: BOB_UID,
      senderUsername: "bob.lens",
      text: "Video clip",
      messageType: "VIDEO",
      videoData: "VID_B64",
      videoDurationMs: 4200,
      createdAt: serverTimestamp(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("threads").doc(threadId).collection("messages").doc("m_aud").set({
      participantUids: participants,
      senderUid: ALICE_UID,
      senderUsername: "aria.studio",
      text: "Voice message",
      messageType: "AUDIO",
      audioData: "AUD_B64",
      audioDurationMs: 3100,
      createdAt: serverTimestamp(),
    })
  );

  await assertFails(
    aliceDb.collection("threads").doc(threadId).collection("messages").doc("m_bad").set({
      participantUids: participants,
      senderUid: ALICE_UID,
      senderUsername: "aria.studio",
      text: "Bad file",
      messageType: "APK",
      createdAt: serverTimestamp(),
    })
  );
});

// --- 9. Momi Marketplace: Merchants, Products, VideoChunks & Orders ---

test("Marketplace: admin-only merchant verification, seller product ownership, and private buyer/seller orders", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true }).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID, { email: "charlie@example.com", email_verified: true }).firestore();
  const adminDb = testEnv.authenticatedContext("admin_momi", {
    email: "syali6511@gmail.com",
    email_verified: true,
  }).firestore();

  await assertFails(unauthDb.collection("merchants").get());
  await assertFails(unauthDb.collection("marketProducts").get());
  await assertFails(unauthDb.collection("marketOrders").get());

  const merchantBase = {
    userId: ALICE_UID,
    ownerUsername: "aria.studio",
    storeName: "Aria Vintage",
    tagline: "Curated analog cameras & collectibles",
    category: "Collectibles",
    location: "Karachi, PK",
    avatarKey: "avatar_aria",
    isVerified: false,
    totalSales: 0,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };

  // Alice cannot self-create as already verified
  await assertFails(
    aliceDb.collection("merchants").doc(ALICE_UID).set({ ...merchantBase, isVerified: true })
  );

  // Alice creates unverified storefront
  await assertSucceeds(aliceDb.collection("merchants").doc(ALICE_UID).set(merchantBase));

  // Alice can edit storefront tagline, cannot self-verify
  await assertSucceeds(
    aliceDb.collection("merchants").doc(ALICE_UID).update({
      tagline: "Updated curated finds",
      updatedAt: serverTimestamp(),
    })
  );
  await assertFails(
    aliceDb.collection("merchants").doc(ALICE_UID).update({
      isVerified: true,
      updatedAt: serverTimestamp(),
    })
  );

  // Bob (non-admin) cannot verify Alice's merchant storefront
  await assertFails(
    bobDb.collection("merchants").doc(ALICE_UID).update({
      isVerified: true,
      verifiedByEmail: "bob@example.com",
      updatedAt: serverTimestamp(),
    })
  );

  // Admin verifies and can also unverify (verifiedByEmail = null)
  await assertSucceeds(
    adminDb.collection("merchants").doc(ALICE_UID).update({
      isVerified: true,
      verifiedByEmail: "syali6511@gmail.com",
      updatedAt: serverTimestamp(),
    })
  );
  await assertSucceeds(
    adminDb.collection("merchants").doc(ALICE_UID).update({
      isVerified: false,
      verifiedByEmail: null,
      updatedAt: serverTimestamp(),
    })
  );

  // Alice publishes a product with extra images & chunked product video
  const productPayload = {
    sellerUid: ALICE_UID,
    sellerUsername: "aria.studio",
    storeName: "Aria Vintage",
    title: "Casio Vintage Watch F-91W",
    description: "Classic retro watch with free worldwide shipping.",
    category: "Fashion",
    priceUsd: 26.29,
    originalPriceUsd: 29.99,
    discountPercent: 12,
    freeShipping: true,
    imageKey: "img_post_coastal",
    imageData: "IMG1",
    extraImageDatas: ["IMG2", "IMG3"],
    videoChunksCount: 1,
    videoDurationMs: 5200,
    stockCount: 8,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };

  // Bob cannot spoof Alice's sellerUid
  await assertFails(bobDb.collection("marketProducts").doc("prod_1").set(productPayload));
  await assertSucceeds(aliceDb.collection("marketProducts").doc("prod_1").set(productPayload));
  await assertSucceeds(
    aliceDb.collection("marketProducts").doc("prod_1").collection("videoChunks").doc("chunk_000").set({
      index: 0,
      data: "CHUNK_B64",
      createdAt: serverTimestamp(),
    })
  );
  await assertSucceeds(bobDb.collection("marketProducts").get());
  await assertSucceeds(bobDb.collection("marketProducts").doc("prod_1").collection("videoChunks").get());

  // Bob cannot delete Alice's product or inject videoChunks
  await assertFails(bobDb.collection("marketProducts").doc("prod_1").delete());
  await assertFails(
    bobDb.collection("marketProducts").doc("prod_1").collection("videoChunks").doc("chunk_001").set({
      index: 1,
      data: "INJECTED",
      createdAt: serverTimestamp(),
    })
  );

  // Orders: Bob buys from Alice
  const orderPayload = {
    buyerUid: BOB_UID,
    buyerUsername: "bob.lens",
    sellerUid: ALICE_UID,
    sellerStoreName: "Aria Vintage",
    productId: "prod_1",
    productTitle: "Casio Vintage Watch F-91W",
    productImageKey: "img_post_coastal",
    priceUsd: 26.29,
    shippingAddress: "44000 Islamabad, PK",
    status: "PLACED",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };

  // Cannot buy own product or create already SHIPPED
  await assertFails(
    aliceDb.collection("marketOrders").doc("ord_self").set({
      ...orderPayload,
      buyerUid: ALICE_UID,
    })
  );
  await assertFails(
    bobDb.collection("marketOrders").doc("ord_shipped").set({
      ...orderPayload,
      status: "SHIPPED",
    })
  );

  await assertSucceeds(bobDb.collection("marketOrders").doc("ord_1").set(orderPayload));

  // Buyer & seller queries work; Charlie cannot read or list orders
  await assertSucceeds(bobDb.collection("marketOrders").where("buyerUid", "==", BOB_UID).get());
  await assertSucceeds(aliceDb.collection("marketOrders").where("sellerUid", "==", ALICE_UID).get());
  await assertFails(charlieDb.collection("marketOrders").doc("ord_1").get());
  await assertFails(charlieDb.collection("marketOrders").where("buyerUid", "==", BOB_UID).get());

  // Seller updates order status; buyer and Charlie cannot
  await assertFails(
    bobDb.collection("marketOrders").doc("ord_1").update({
      status: "SHIPPED",
      updatedAt: serverTimestamp(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("marketOrders").doc("ord_1").update({
      status: "SHIPPED",
      updatedAt: serverTimestamp(),
    })
  );
  await assertFails(aliceDb.collection("marketOrders").doc("ord_1").delete());
});

// --- 10. Team Roles, Scoped Surveillance & Content Moderation ---

test("Team RBAC & Content Moderation: admin appoints scoped moderators/editors, non-admin cannot self-escalate, moderators handle only enrolled scopes", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true }).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID, { email: "charlie@example.com", email_verified: true }).firestore();
  const adminDb = testEnv.authenticatedContext("admin_momi", {
    email: "syali6511@gmail.com",
    email_verified: true,
  }).firestore();

  // Users cannot create profiles with pre-assigned MODERATOR or EDITOR roles
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      ...validProfilePayload(ALICE_UID),
      role: "MODERATOR",
      moderationScopes: ["VIOLENCE_GRAPHIC"],
    })
  );

  // Create regular profiles
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set({
    ...validProfilePayload(ALICE_UID),
    role: "USER",
    moderationScopes: [],
  }));
  await assertSucceeds(bobDb.collection("users").doc(BOB_UID).set({
    ...validProfilePayload(BOB_UID, "bob.lens", "Bob Lens"),
    role: "USER",
    moderationScopes: [],
  }));
  await assertSucceeds(charlieDb.collection("users").doc(CHARLIE_UID).set({
    ...validProfilePayload(CHARLIE_UID, "charlie.art", "Charlie Art"),
    role: "USER",
    moderationScopes: [],
  }));

  // Bob cannot self-promote to MODERATOR or appoint Alice
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).update({
      role: "MODERATOR",
      moderationScopes: ["VIOLENCE_GRAPHIC", "SEXUAL_NUDITY"],
      updatedAt: serverTimestamp(),
    })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      role: "EDITOR",
      updatedAt: serverTimestamp(),
    })
  );

  // Admin appoints Alice as MODERATOR enrolled strictly in VIOLENCE_GRAPHIC & SEXUAL_NUDITY
  await assertSucceeds(
    adminDb.collection("users").doc(ALICE_UID).update({
      role: "MODERATOR",
      moderationScopes: ["VIOLENCE_GRAPHIC", "SEXUAL_NUDITY"],
      assignedByEmail: "syali6511@gmail.com",
      updatedAt: serverTimestamp(),
    })
  );

  // Admin appoints Charlie as EDITOR enrolled strictly in EDITORIAL_CURATION
  await assertSucceeds(
    adminDb.collection("users").doc(CHARLIE_UID).update({
      role: "EDITOR",
      moderationScopes: ["EDITORIAL_CURATION"],
      assignedByEmail: "syali6511@gmail.com",
      updatedAt: serverTimestamp(),
    })
  );

  // Alice (Moderator) does NOT have full authority: cannot verify Bob or appoint Bob as staff
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).update({
      isVerified: true,
      updatedAt: serverTimestamp(),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).update({
      role: "MODERATOR",
      updatedAt: serverTimestamp(),
    })
  );

  // Bob publishes a violating post and a marketplace product
  await assertSucceeds(bobDb.collection("posts").doc("bad_post_1").set(validPostPayload(BOB_UID, "bob.lens")));
  await assertSucceeds(
    bobDb.collection("marketProducts").doc("prod_curate").set({
      sellerUid: BOB_UID,
      sellerUsername: "bob.lens",
      storeName: "Bob Goods",
      title: "Vintage Tripod",
      description: "Brass tripod stand",
      category: "Collectibles",
      priceUsd: 49.0,
      originalPriceUsd: 65.0,
      discountPercent: 24,
      freeShipping: true,
      imageKey: "img_post_coastal",
      videoDurationMs: 0,
      stockCount: 3,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  // Regular user Bob reports violent/graphic post AND a marketplace safety item
  const violentReport = {
    reporterUid: BOB_UID,
    reporterUsername: "bob.lens",
    targetType: "POST",
    targetId: "bad_post_1",
    targetAuthorUid: BOB_UID,
    targetAuthorUsername: "bob.lens",
    targetPreviewText: "Reported graphic content",
    category: "VIOLENCE_GRAPHIC",
    reasonDetail: "Contains graphic violence",
    status: "OPEN",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
  const marketReport = {
    ...violentReport,
    targetType: "PRODUCT",
    targetId: "prod_curate",
    category: "MARKETPLACE_SAFETY",
    reasonDetail: "Suspicious listing",
  };

  await assertSucceeds(bobDb.collection("contentReports").doc("rep_graphic").set(violentReport));
  await assertSucceeds(bobDb.collection("contentReports").doc("rep_market").set(marketReport));

  // Regular user Bob can query own submitted reports, CANNOT list all reports
  await assertSucceeds(bobDb.collection("contentReports").where("reporterUid", "==", BOB_UID).get());
  await assertFails(bobDb.collection("contentReports").get());

  // Alice (Moderator with VIOLENCE_GRAPHIC) can list reports and resolve rep_graphic + delete violating post
  await assertSucceeds(aliceDb.collection("contentReports").get());
  await assertSucceeds(
    aliceDb.collection("contentReports").doc("rep_graphic").update({
      status: "REMOVED",
      reviewedByUid: ALICE_UID,
      reviewedByUsername: "aria.studio",
      reviewedByRole: "MODERATOR",
      resolutionNote: "Removed graphic post",
      updatedAt: serverTimestamp(),
    })
  );
  await assertSucceeds(aliceDb.collection("posts").doc("bad_post_1").delete());

  // Alice is NOT enrolled in MARKETPLACE_SAFETY -> denied resolving rep_market, deleting product, or featuring product
  await assertFails(
    aliceDb.collection("contentReports").doc("rep_market").update({
      status: "DISMISSED",
      reviewedByUid: ALICE_UID,
      reviewedByUsername: "aria.studio",
      reviewedByRole: "MODERATOR",
      resolutionNote: "Out of scope",
      updatedAt: serverTimestamp(),
    })
  );
  await assertFails(aliceDb.collection("marketProducts").doc("prod_curate").delete());
  await assertFails(
    aliceDb.collection("marketProducts").doc("prod_curate").update({
      isFeatured: true,
      updatedAt: serverTimestamp(),
    })
  );

  // Charlie (Editor with EDITORIAL_CURATION) CAN feature product, CANNOT delete Bob's product or fake ADMIN role
  await assertSucceeds(
    charlieDb.collection("marketProducts").doc("prod_curate").update({
      isFeatured: true,
      updatedAt: serverTimestamp(),
    })
  );
  await assertFails(charlieDb.collection("marketProducts").doc("prod_curate").delete());
  await assertFails(
    charlieDb.collection("contentReports").doc("rep_market").update({
      status: "DISMISSED",
      reviewedByUid: CHARLIE_UID,
      reviewedByUsername: "charlie.art",
      reviewedByRole: "ADMIN",
      updatedAt: serverTimestamp(),
    })
  );

  // Admin can resolve any report and delete reports
  await assertSucceeds(adminDb.collection("contentReports").get());
  await assertSucceeds(
    adminDb.collection("contentReports").doc("rep_market").update({
      status: "DISMISSED",
      reviewedByUid: "admin_momi",
      reviewedByUsername: "syali6511",
      reviewedByRole: "ADMIN",
      resolutionNote: "Checked by owner",
      updatedAt: serverTimestamp(),
    })
  );
});

// --- 11. Momi Wallets & Immutable Credit Transactions Ledger ---

function zeroWalletPayload(uid) {
  return {
    userId: uid,
    balance: 0,
    totalEarned: 0,
    totalSpent: 0,
    totalPurchased: 0,
    accountTier: "STANDARD",
    verificationTier: "NONE",
    unlockedPerks: [],
    lastTxId: null,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

test("Wallets & CreditTransactions: owner isolation, zero-init, atomic top-up, gift send+claim, perk unlock, and anti-replay", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true }).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID, { email: "charlie@example.com", email_verified: true }).firestore();

  // Cannot initialize wallet with free non-zero balance or hijack someone else's wallet
  await assertFails(
    aliceDb.collection("wallets").doc(ALICE_UID).set({
      ...zeroWalletPayload(ALICE_UID),
      balance: 500,
    })
  );
  await assertFails(bobDb.collection("wallets").doc(ALICE_UID).set(zeroWalletPayload(ALICE_UID)));

  // Both Alice & Bob initialize wallets at zero balance
  await assertSucceeds(aliceDb.collection("wallets").doc(ALICE_UID).set(zeroWalletPayload(ALICE_UID)));
  await assertSucceeds(bobDb.collection("wallets").doc(BOB_UID).set(zeroWalletPayload(BOB_UID)));

  // Wallets are strictly owner-private
  await assertSucceeds(aliceDb.collection("wallets").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("wallets").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("wallets").get());

  // Arbitrary wallet balance inflation without matching CreditTransaction is denied
  await assertFails(
    aliceDb.collection("wallets").doc(ALICE_UID).update({
      balance: 200,
      totalPurchased: 200,
      lastTxId: "tx_fake_missing",
      updatedAt: serverTimestamp(),
    })
  );

  // Atomic TOP_UP of 500 credits for Alice
  const topUpBatch = aliceDb.batch();
  topUpBatch.set(aliceDb.collection("creditTransactions").doc("tx_topup_1"), {
    txId: "tx_topup_1",
    type: "TOP_UP",
    senderUid: ALICE_UID,
    senderUsername: "aria.studio",
    recipientUid: ALICE_UID,
    recipientUsername: "aria.studio",
    creditsAmount: 500,
    priceUsd: 6.99,
    paymentMethod: "GOOGLE_PLAY",
    paymentRef: "GPA.3312-4411-9087",
    recipientClaimed: true,
    status: "COMPLETED",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  });
  topUpBatch.update(aliceDb.collection("wallets").doc(ALICE_UID), {
    balance: 500,
    totalPurchased: 500,
    accountTier: "SUPPORTER",
    lastTxId: "tx_topup_1",
    updatedAt: serverTimestamp(),
  });
  await assertSucceeds(topUpBatch.commit());

  // Replaying the same tx_topup_1 fails (!exists(txPath) blocks replay)
  const replayBatch = aliceDb.batch();
  replayBatch.set(aliceDb.collection("creditTransactions").doc("tx_topup_1"), {
    txId: "tx_topup_1",
    type: "TOP_UP",
    senderUid: ALICE_UID,
    senderUsername: "aria.studio",
    recipientUid: ALICE_UID,
    recipientUsername: "aria.studio",
    creditsAmount: 500,
    priceUsd: 6.99,
    paymentMethod: "GOOGLE_PLAY",
    paymentRef: "GPA.3312-4411-9087",
    recipientClaimed: true,
    status: "COMPLETED",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  });
  replayBatch.update(aliceDb.collection("wallets").doc(ALICE_UID), {
    balance: 1000,
    totalPurchased: 1000,
    accountTier: "SUPPORTER",
    lastTxId: "tx_topup_1",
    updatedAt: serverTimestamp(),
  });
  await assertFails(replayBatch.commit());

  // Bob starts a live stream and Alice sends a 50-credit gift atomically
  await assertSucceeds(
    bobDb.collection("liveStreams").doc(BOB_UID).set({
      userId: BOB_UID,
      username: "bob.lens",
      displayName: "Bob Lens",
      avatarKey: "avatar_kenji",
      authorVerified: false,
      title: "Night Studio",
      category: "Live",
      isLive: true,
      viewerCount: 1,
      likesCount: 0,
      giftsCredits: 0,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  // Sending gift pre-marked recipientClaimed: true on create must fail
  const spoofGiftBatch = aliceDb.batch();
  spoofGiftBatch.set(aliceDb.collection("creditTransactions").doc("tx_gift_bad"), {
    txId: "tx_gift_bad",
    type: "GIFT",
    senderUid: ALICE_UID,
    senderUsername: "aria.studio",
    recipientUid: BOB_UID,
    recipientUsername: "bob.lens",
    creditsAmount: 50,
    priceUsd: 0.0,
    paymentMethod: "WALLET_CREDITS",
    giftKey: "spotlight",
    giftName: "Spotlight",
    streamId: BOB_UID,
    recipientClaimed: true,
    status: "COMPLETED",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  });
  spoofGiftBatch.update(aliceDb.collection("wallets").doc(ALICE_UID), {
    balance: 450,
    totalSpent: 50,
    lastTxId: "tx_gift_bad",
    updatedAt: serverTimestamp(),
  });
  await assertFails(spoofGiftBatch.commit());

  // Valid gift send (50 credits): Alice debited, tx created with recipientClaimed=false, liveStream.giftsCredits incremented
  const giftBatch = aliceDb.batch();
  giftBatch.set(aliceDb.collection("creditTransactions").doc("tx_gift_1"), {
    txId: "tx_gift_1",
    type: "GIFT",
    senderUid: ALICE_UID,
    senderUsername: "aria.studio",
    recipientUid: BOB_UID,
    recipientUsername: "bob.lens",
    creditsAmount: 50,
    priceUsd: 0.0,
    paymentMethod: "WALLET_CREDITS",
    giftKey: "spotlight",
    giftName: "Spotlight",
    streamId: BOB_UID,
    recipientClaimed: false,
    status: "COMPLETED",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  });
  giftBatch.update(aliceDb.collection("wallets").doc(ALICE_UID), {
    balance: 450,
    totalSpent: 50,
    lastTxId: "tx_gift_1",
    updatedAt: serverTimestamp(),
  });
  giftBatch.update(aliceDb.collection("liveStreams").doc(BOB_UID), {
    giftsCredits: 50,
    updatedAt: serverTimestamp(),
  });
  await assertSucceeds(giftBatch.commit());

  // Charlie cannot read, hijack, or claim Bob's gift
  await assertFails(charlieDb.collection("creditTransactions").doc("tx_gift_1").get());
  await assertFails(charlieDb.collection("creditTransactions").get());
  await assertSucceeds(bobDb.collection("creditTransactions").where("recipientUid", "==", BOB_UID).where("recipientClaimed", "==", false).get());
  await assertSucceeds(aliceDb.collection("creditTransactions").where("senderUid", "==", ALICE_UID).get());

  // Bob claims the 50-credit gift into his own wallet atomically
  const claimBatch = bobDb.batch();
  claimBatch.update(bobDb.collection("creditTransactions").doc("tx_gift_1"), {
    recipientClaimed: true,
    updatedAt: serverTimestamp(),
  });
  claimBatch.update(bobDb.collection("wallets").doc(BOB_UID), {
    balance: 50,
    totalEarned: 50,
    lastTxId: "tx_gift_1",
    updatedAt: serverTimestamp(),
  });
  await assertSucceeds(claimBatch.commit());

  // Double-claiming the same gift tx_gift_1 fails
  const doubleClaimBatch = bobDb.batch();
  doubleClaimBatch.update(bobDb.collection("creditTransactions").doc("tx_gift_1"), {
    recipientClaimed: true,
    updatedAt: serverTimestamp(),
  });
  doubleClaimBatch.update(bobDb.collection("wallets").doc(BOB_UID), {
    balance: 100,
    totalEarned: 100,
    lastTxId: "tx_gift_1",
    updatedAt: serverTimestamp(),
  });
  await assertFails(doubleClaimBatch.commit());

  // Alice unlocks Supporter Frame perk (100 credits) from remaining 450 credits -> 350 balance
  const perkBatch = aliceDb.batch();
  perkBatch.set(aliceDb.collection("creditTransactions").doc("tx_perk_1"), {
    txId: "tx_perk_1",
    type: "PERK_UNLOCK",
    senderUid: ALICE_UID,
    senderUsername: "aria.studio",
    recipientUid: ALICE_UID,
    recipientUsername: "aria.studio",
    creditsAmount: 100,
    priceUsd: 0.0,
    paymentMethod: "WALLET_CREDITS",
    perkKey: "SUPPORTER_FRAME",
    recipientClaimed: true,
    status: "COMPLETED",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  });
  perkBatch.update(aliceDb.collection("wallets").doc(ALICE_UID), {
    balance: 350,
    totalSpent: 150,
    accountTier: "SUPPORTER",
    verificationTier: "NONE",
    unlockedPerks: ["SUPPORTER_FRAME"],
    lastTxId: "tx_perk_1",
    updatedAt: serverTimestamp(),
  });
  await assertSucceeds(perkBatch.commit());

  // Deleting wallets or credit transactions is strictly forbidden
  await assertFails(aliceDb.collection("wallets").doc(ALICE_UID).delete());
  await assertFails(aliceDb.collection("creditTransactions").doc("tx_topup_1").delete());
});

// --- 12. Expanded Post Filters, Aspect Ratio, Hashtags & Creator Tagging ---

test("Posts: allows expanded color filters, aspectRatio, taggedUsernames, and hashtags while enforcing bounds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("posts").doc("post_styled_1").set({
      ...validPostPayload(ALICE_UID),
      filterTag: "CINEMA",
      aspectRatio: "4:5",
      taggedUsernames: ["bob.lens", "charlie.art"],
      hashtags: ["momi", "streetstyle", "night"],
    })
  );

  // Reject unsupported filterTag or invalid aspectRatio
  await assertFails(
    aliceDb.collection("posts").doc("post_bad_filter").set({
      ...validPostPayload(ALICE_UID),
      filterTag: "UNKNOWN_FILTER",
    })
  );
  await assertFails(
    aliceDb.collection("posts").doc("post_bad_ratio").set({
      ...validPostPayload(ALICE_UID),
      aspectRatio: "99:1",
    })
  );

  // Reject > 10 taggedUsernames or > 15 hashtags
  await assertFails(
    aliceDb.collection("posts").doc("post_too_many_tags").set({
      ...validPostPayload(ALICE_UID),
      taggedUsernames: Array.from({ length: 11 }, (_, i) => `user${i}`),
    })
  );
  await assertFails(
    aliceDb.collection("posts").doc("post_too_many_hashtags").set({
      ...validPostPayload(ALICE_UID),
      hashtags: Array.from({ length: 16 }, (_, i) => `tag${i}`),
    })
  );
});

// --- 13. Multi-Monetizer Config, Partner Consent, Post Boosts & Owner Grants ---

test("AppConfig, Partner Consent, Post Boosts & Owner Grants: zero-trust enforcement", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true }).firestore();
  const adminDb = testEnv.authenticatedContext("admin_momi", {
    email: "syali6511@gmail.com",
    email_verified: true,
  }).firestore();

  const monetizationConfig = {
    admobEnabled: true,
    admobBannerUnitId: "ca-app-pub-3940256099942544/6300978111",
    admobMrecUnitId: "ca-app-pub-3940256099942544/6300978111",
    admobInterstitialUnitId: "ca-app-pub-3940256099942544/1033173712",
    startIoEnabled: true,
    monetagEnabled: true,
    monetagDirectLinkUrl: "https://otieu.com/4/1234567",
    monetagWebZoneId: "1234567",
    monetagWebMetaTag: "monetag-verify-token",
    partnerPromoTitle: "Featured Partner Drop",
    partnerPromoSubtitle: "Support Momi creators and browse curated partner deals.",
    partnerPromoCta: "Visit Partner",
    updatedByEmail: "syali6511@gmail.com",
    updatedAt: serverTimestamp(),
  };

  // Only owner admin can write appConfig/monetization; signed-in users can read; unauth denied
  await assertFails(unauthDb.collection("appConfig").doc("monetization").get());
  await assertFails(aliceDb.collection("appConfig").doc("monetization").set(monetizationConfig));
  await assertSucceeds(adminDb.collection("appConfig").doc("monetization").set(monetizationConfig));
  await assertSucceeds(aliceDb.collection("appConfig").doc("monetization").get());

  // Reject http:// (insecure) monetagDirectLinkUrl
  await assertFails(
    adminDb.collection("appConfig").doc("monetization").set({
      ...monetizationConfig,
      monetagDirectLinkUrl: "http://insecure.example.com",
    })
  );

  // Alice creates profile, opts into partnerConsent, and creates a post
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID)));
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      partnerConsent: true,
      consentUpdatedAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );
  // Bob cannot toggle Alice's consent
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      partnerConsent: false,
      consentUpdatedAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  await assertSucceeds(aliceDb.collection("posts").doc("p_boost").set(validPostPayload(ALICE_UID)));

  // Alice cannot self-boost without spending Wallet credits
  await assertFails(
    aliceDb.collection("posts").doc("p_boost").update({
      isBoosted: true,
      boostTier: "SPOTLIGHT",
      recommendedLabel: "Boosted",
      boostedByUid: ALICE_UID,
      boostedAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  // Owner grants Alice +200 credits + CREATOR_BOOST perk (even before Alice opens Wallet)
  const grantBatch = adminDb.batch();
  grantBatch.set(adminDb.collection("creditTransactions").doc("tx_owner_grant_1"), {
    txId: "tx_owner_grant_1",
    type: "OWNER_GRANT",
    senderUid: "admin_momi",
    senderUsername: "syali6511",
    recipientUid: ALICE_UID,
    recipientUsername: "aria.studio",
    creditsAmount: 200,
    priceUsd: 0.0,
    paymentMethod: "OWNER_ADMIN",
    perkKey: "CREATOR_BOOST",
    recipientClaimed: true,
    status: "COMPLETED",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  });
  grantBatch.set(adminDb.collection("wallets").doc(ALICE_UID), {
    userId: ALICE_UID,
    balance: 200,
    totalEarned: 200,
    totalSpent: 0,
    totalPurchased: 0,
    accountTier: "CREATOR_PRO",
    verificationTier: "CREATOR_PRO",
    unlockedPerks: ["CREATOR_BOOST"],
    lastTxId: "tx_owner_grant_1",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  });
  await assertSucceeds(grantBatch.commit());

  // Non-admin Bob cannot forge OWNER_GRANT
  const forgedGrantBatch = bobDb.batch();
  forgedGrantBatch.set(bobDb.collection("creditTransactions").doc("tx_forged_grant"), {
    txId: "tx_forged_grant",
    type: "OWNER_GRANT",
    senderUid: BOB_UID,
    senderUsername: "bob.lens",
    recipientUid: BOB_UID,
    recipientUsername: "bob.lens",
    creditsAmount: 500,
    priceUsd: 0.0,
    paymentMethod: "OWNER_ADMIN",
    recipientClaimed: true,
    status: "COMPLETED",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  });
  forgedGrantBatch.set(bobDb.collection("wallets").doc(BOB_UID), {
    ...zeroWalletPayload(BOB_UID),
    balance: 500,
    totalEarned: 500,
    lastTxId: "tx_forged_grant",
  });
  await assertFails(forgedGrantBatch.commit());

  // Alice atomically spends 100 credits to boost her own post
  const boostBatch = aliceDb.batch();
  boostBatch.set(aliceDb.collection("creditTransactions").doc("tx_boost_1"), {
    txId: "tx_boost_1",
    type: "POST_BOOST",
    senderUid: ALICE_UID,
    senderUsername: "aria.studio",
    recipientUid: ALICE_UID,
    recipientUsername: "aria.studio",
    creditsAmount: 100,
    priceUsd: 0.0,
    paymentMethod: "WALLET_CREDITS",
    postId: "p_boost",
    recipientClaimed: true,
    status: "COMPLETED",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  });
  boostBatch.update(aliceDb.collection("wallets").doc(ALICE_UID), {
    balance: 100,
    totalSpent: 100,
    lastTxId: "tx_boost_1",
    updatedAt: serverTimestamp(),
  });
  boostBatch.update(aliceDb.collection("posts").doc("p_boost"), {
    isBoosted: true,
    boostTier: "SPOTLIGHT",
    recommendedLabel: "Boosted Spotlight",
    boostedByUid: ALICE_UID,
    boostedAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  });
  await assertSucceeds(boostBatch.commit());

  // Admin can also recommend any post directly
  await assertSucceeds(
    adminDb.collection("posts").doc("p_boost").update({
      isBoosted: true,
      boostTier: "OWNER_RECOMMENDED",
      recommendedLabel: "Recommended by Momi",
      boostedByUid: "admin_momi",
      boostedAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );
  // Regular user Bob cannot mark Alice's post as recommended
  await assertFails(
    bobDb.collection("posts").doc("p_boost").update({
      isBoosted: true,
      boostTier: "OWNER_RECOMMENDED",
      recommendedLabel: "Hacked",
      updatedAt: serverTimestamp(),
    })
  );
});



