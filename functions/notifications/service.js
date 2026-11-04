"use strict";
const { HttpsError } = require("firebase-functions/v2/https");
const { Timestamp } = require("firebase-admin/firestore");
const p = require("./policy");
module.exports = function notificationService(db, messaging) {
  async function action(request) {
    const uid = request.auth?.uid, data = request.data;
    if (!uid) throw new HttpsError("unauthenticated", "Sign in first");
    if (data?.uid !== uid || request.auth.token.email_verified !== true)
      throw new HttpsError("permission-denied", "Verify the current account first");
    if (!["register", "unregister"].includes(data.action)) throw new HttpsError("invalid-argument", "Unknown device action");
    let key, value;
    try {
      if (data.action === "register") { value = p.token(data.token); key = p.key(value); }
      else { key = data.key; if (typeof key !== "string" || !/^[a-f0-9]{64}$/.test(key)) throw Error(); }
    } catch (_) { throw new HttpsError("invalid-argument", "Invalid device registration"); }
    await db.runTransaction(async tx => {
      const owner = db.doc(`notificationDeviceOwners/${key}`), device = db.doc(`users/${uid}/devices/${key}`);
      const [tomb, profile, binding, devices] = await Promise.all([
        tx.get(db.doc(`accountTombstones/${uid}`)), tx.get(db.doc(`users/${uid}`)), tx.get(owner),
        tx.get(db.collection(`users/${uid}/devices`).limit(11)),
      ]);
      if (tomb.exists || !profile.exists) throw new HttpsError("failed-precondition", "Active profile required");
      if (data.action === "register") {
        if (devices.size >= 10 && !devices.docs.some(d => d.id === key)) throw new HttpsError("resource-exhausted", "Remove an old device before adding another");
        const oldUid = binding.get("uid");
        if (oldUid && oldUid !== uid) tx.delete(db.doc(`users/${oldUid}/devices/${key}`));
        tx.set(owner, { uid, expiresAt: Timestamp.fromMillis(Date.now() + 30 * p.DAY) });
        tx.set(device, { token: value, expiresAt: Timestamp.fromMillis(Date.now() + 30 * p.DAY) });
      } else {
        tx.delete(device);
        if (binding.get("uid") === uid) tx.delete(owner);
      }
    });
    return { key, registered: data.action === "register" };
  }
  async function emit(uid, eventId, kind, target = "") {
    const id = p.key(`${kind}:${eventId}`), now = Date.now(), entry = p.entry(kind, target, now);
    await db.runTransaction(async tx => {
      const ref = db.doc(`users/${uid}/notifications/${id}`);
      const [tomb, profile, old] = await Promise.all([tx.get(db.doc(`accountTombstones/${uid}`)), tx.get(db.doc(`users/${uid}`)), tx.get(ref)]);
      if (!tomb.exists && profile.exists && !old.exists) tx.create(ref, { ...entry, expiresAt: Timestamp.fromMillis(now + 30 * p.DAY) });
    });
  }
  async function remove(uid, key) {
    await db.runTransaction(async tx => {
      const ref = db.doc(`notificationDeviceOwners/${key}`), owner = await tx.get(ref);
      tx.delete(db.doc(`users/${uid}/devices/${key}`));
      if (owner.get("uid") === uid) tx.delete(ref);
    });
  }
  async function dispatch(uid, id) {
    const [tomb, note, devices] = await Promise.all([
      db.doc(`accountTombstones/${uid}`).get(), db.doc(`users/${uid}/notifications/${id}`).get(), db.collection(`users/${uid}/devices`).limit(10).get(),
    ]);
    if (tomb.exists || !note.exists || note.get("read") || Date.now() - note.get("createdAt") > p.DAY) return;
    const candidates = [];
    for (const d of devices.docs) {
      const binding = await db.doc(`notificationDeviceOwners/${d.id}`).get();
      if (binding.get("uid") === uid && d.get("expiresAt")?.toMillis() > Date.now()) candidates.push(d);
      else await remove(uid, d.id);
    }
    if (!candidates.length) return;
    const result = await messaging.sendEachForMulticast({ ...p.payload(uid, id, note.get("kind")), tokens: candidates.map(d => d.get("token")) });
    for (let i = 0; i < result.responses.length; i++) {
      const code = result.responses[i].error?.code;
      if (["messaging/registration-token-not-registered", "messaging/invalid-registration-token"].includes(code)) await remove(uid, candidates[i].id);
      else if (code) throw new Error("Push delivery temporarily failed"); // No tokens or service response details in logs.
    }
  }
  async function available(from, to) {
    const docs = await Promise.all([db.doc(`users/${from}/blocks/${to}`).get(), db.doc(`users/${to}/blocks/${from}`).get(), db.doc(`accountTombstones/${from}`).get(), db.doc(`accountTombstones/${to}`).get()]);
    return docs.every(d => !d.exists);
  }
  async function friendship(id, before, after) {
    if (!after || !(await available(after.from, after.to))) return;
    if (!before && after.status === "pending") await emit(after.to, `${id}:${after.at}`, "friend_request");
    if (before?.status === "pending" && after.status === "accepted") await emit(after.from, `${id}:${after.at}`, "friend_accept");
  }
  async function cheer(challengeId, id, message) {
    const challenge = await db.doc(`challenges/${challengeId}`).get();
    const members = challenge.get("memberUids") || [];
    if (!members.includes(message.from)) return;
    const targets = message.target ? members.filter(uid => uid === message.target) : members;
    for (const uid of targets.slice(0, 50)) if (uid !== message.from && await available(message.from, uid)) await emit(uid, `${challengeId}:${id}`, "cheer", challengeId);
  }
  async function joined(challengeId, uid, eventId) {
    const c = await db.doc(`challenges/${challengeId}`).get(), owner = c.get("owner");
    if (owner && uid !== owner && (c.get("memberUids") || []).includes(uid) && await available(uid, owner)) await emit(owner, eventId, "challenge_join", challengeId);
  }
  async function league(id, before, after) {
    if (!after?.finalized || before?.finalized) return;
    for (const uid of (after.memberUids || []).slice(0, 50)) await emit(uid, id, "league");
  }
  async function cleanup(uid) {
    const devices = await db.collection(`users/${uid}/devices`).get();
    for (const device of devices.docs) await remove(uid, device.id);
  }
  return { action, emit, dispatch, friendship, cheer, joined, league, cleanup };
};
