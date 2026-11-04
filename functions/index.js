"use strict";
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { initializeApp } = require("firebase-admin/app");
const {
  getFirestore,
  FieldValue,
  Timestamp,
} = require("firebase-admin/firestore");
const { getAuth } = require("firebase-admin/auth");
const policy = require("./policy");
const {
  onDocumentCreated,
  onDocumentUpdated,
  onDocumentWritten,
} = require("firebase-functions/v2/firestore");
const { onSchedule } = require("firebase-functions/v2/scheduler");
initializeApp();
const db = getFirestore();
const notificationService = require("./notifications/service")(db, require("firebase-admin/messaging").getMessaging());
const socialService = require("./social/service")(db, getAuth());
const options = {
  region: "us-central1",
  maxInstances: 10,
  timeoutSeconds: 120,
};
function identity(request, verified = true) {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in first");
  if (request.data?.uid !== request.auth.uid)
    throw new HttpsError(
      "permission-denied",
      "Account changed; retry after signing in",
    );
  if (verified && request.auth.token.email_verified !== true)
    throw new HttpsError("permission-denied", "Verify your email first");
  return request.auth.uid;
}
exports.syncTracking = onCall(options, async (request) => {
  const uid = identity(request);
  const now = Date.now();
  let data;
  try {
    data =
      request.data?.kind === "weight"
        ? policy.weight(request.data.record, now)
        : request.data?.kind === "session"
          ? policy.session(request.data.record, now)
          : null;
  } catch (_) {
    throw new HttpsError(
      "invalid-argument",
      "Record rejected; local copy can be kept",
    );
  }
  if (!data) throw new HttpsError("invalid-argument", "Unknown record type");
  const user = db.doc(`users/${uid}`);
  const tombstone = db.doc(`accountTombstones/${uid}`);
  return db.runTransaction(async (tx) => {
    // The same tombstone read participates in deletion/upload concurrency control.
    const [deleted, profile] = await Promise.all([
      tx.get(tombstone),
      tx.get(user),
    ]);
    if (deleted.exists)
      throw new HttpsError("failed-precondition", "Account deletion pending");
    if (!profile.exists)
      throw new HttpsError("failed-precondition", "Profile has not synced yet");
    if (request.data.kind === "session") {
      const ref = user.collection("sessions").doc(data.id);
      const existing = await tx.get(ref);
      if (!existing.exists) {
        const day = Math.floor(data.startedAt / policy.DAY),
          quota = user.collection("uploadDays").doc(String(day)),
          count = await tx.get(quota);
        if ((count.get("sessions") || 0) >= 300)
          throw new HttpsError(
            "resource-exhausted",
            "Daily session upload limit reached; device copy is kept",
          );
        tx.create(ref, { ...data, receivedAt: FieldValue.serverTimestamp() });
        tx.set(quota, { sessions: (count.get("sessions") || 0) + 1 });
      }
      // First writer wins: retries never alter a recorded session or award duplicate credit.
      return { record: existing.exists ? existing.data() : data };
    }
    const collection = user.collection("weighIns");
    const ref = collection.doc(String(data.epochDay));
    const existing = await tx.get(ref);
    if (existing.exists)
      return {
        record: existing.data(),
        collision: existing.data().weightKg !== data.weightKg,
      };
    const before = await tx.get(
      collection
        .where("epochDay", "<", data.epochDay)
        .orderBy("epochDay", "desc")
        .limit(1),
    );
    const after = await tx.get(
      collection
        .where("epochDay", ">", data.epochDay)
        .orderBy("epochDay")
        .limit(1),
    );
    const canonical = {
      ...data,
      flagged: policy.unusual(before.docs[0]?.data(), data),
    };
    tx.create(ref, { ...canonical, receivedAt: FieldValue.serverTimestamp() });
    // Offline uploads can arrive out of order; update the next day's safety classification.
    if (!after.empty)
      tx.update(after.docs[0].ref, {
        flagged: policy.unusual(canonical, after.docs[0].data()),
      });
    return { record: canonical, collision: false };
  });
});
exports.deleteCampusFitAccount = onCall(
  { ...options, timeoutSeconds: 540 },
  async (request) => {
    const uid = identity(request, false);
    if (
      !Number.isFinite(request.auth.token.auth_time) ||
      Date.now() / 1000 - request.auth.token.auth_time > 300
    )
      throw new HttpsError(
        "failed-precondition",
        "Sign in again before deleting your account",
      );
    const block = db.doc(`accountTombstones/${uid}`);
    // Kept separately so a still-valid pre-deletion ID token cannot recreate private data.
    await db.runTransaction(async (tx) => {
      const existing = await tx.get(block);
      if (!existing.exists)
        tx.create(block, {
          requestedAt: FieldValue.serverTimestamp(),
          expiresAt: Timestamp.fromMillis(Date.now() + policy.DAY),
        });
    });
    await notificationService.cleanup(uid);
    await socialService.cleanup(uid);
    await db.recursiveDelete(db.doc(`users/${uid}`));
    try {
      await getAuth().deleteUser(uid);
    } catch (error) {
      if (error.code !== "auth/user-not-found")
        throw new HttpsError("internal", "Deletion incomplete; retry");
    }
    return { removed: true };
  },
);

exports.socialAction = onCall(options, (request) =>
  socialService.action(request),
);
exports.trackingGameSession = onDocumentCreated(
  {
    document: "users/{uid}/sessions/{id}",
    region: "us-central1",
    maxInstances: 10,
  },
  async (event) => {
    if (!event.data) return;
    await socialService.refreshDay(
      event.params.uid,
      Math.floor(event.data.get("startedAt") / policy.DAY),
    );
    await socialService.invalidate(event.params.uid);
  },
);
exports.trackingGameWeight = onDocumentCreated(
  {
    document: "users/{uid}/weighIns/{id}",
    region: "us-central1",
    maxInstances: 10,
  },
  (event) => socialService.invalidate(event.params.uid),
);
exports.trackingGamePrivacy = onDocumentUpdated(
  { document: "users/{uid}", region: "us-central1", maxInstances: 10 },
  (event) => socialService.invalidate(event.params.uid),
);
exports.finalizeGames = onSchedule(
  {
    schedule: "every 60 minutes",
    timeZone: "Etc/UTC",
    region: "us-central1",
    maxInstances: 1,
  },
  async () => {
    const now = Date.now();
    for (const name of ["challenges", "leagues"]) {
      let query = db
        .collection(name)
        .where("finalized", "==", false)
        .where("end", "<=", now - policy.DAY)
        .orderBy("end")
        .limit(100);
      while (true) {
        const page = await query.get();
        for (const d of page.docs) {
          if (name === "challenges")
            await socialService.finalizeChallenge(d.id);
          else await socialService.finalizeLeague(d.id);
        }
        if (page.size < 100) break;
        query = query.startAfter(page.docs.at(-1));
      }
    }
  },
);

// All event records and tokens stay private; push carries only an opaque inbox reference.
exports.notificationAction = onCall(options, request => notificationService.action(request));
const eventOptions = document => ({ document, region: "us-central1", maxInstances: 10, retry: true });
exports.deliverNotification = onDocumentCreated(eventOptions("users/{uid}/notifications/{id}"), event => process.env.FUNCTIONS_EMULATOR === "true" ? Promise.resolve() : notificationService.dispatch(event.params.uid, event.params.id));
exports.notifyFriendship = onDocumentWritten(eventOptions("friendships/{id}"), event => notificationService.friendship(event.params.id, event.data?.before.data(), event.data?.after.data()));
exports.notifyCheer = onDocumentCreated(eventOptions("challenges/{challengeId}/messages/{id}"), event => event.data && notificationService.cheer(event.params.challengeId, event.params.id, event.data.data()));
exports.notifyChallengeJoin = onDocumentCreated(eventOptions("challenges/{challengeId}/members/{uid}"), event => notificationService.joined(event.params.challengeId, event.params.uid, event.id));
exports.notifyTrophy = onDocumentCreated(eventOptions("users/{uid}/awards/{id}"), event => notificationService.emit(event.params.uid, event.params.id, "trophy"));
exports.notifyLeague = onDocumentUpdated(eventOptions("leagues/{id}"), event => notificationService.league(event.params.id, event.data?.before.data(), event.data?.after.data()));
