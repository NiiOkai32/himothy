const { test, before, after } = require("node:test");
const assert = require("node:assert/strict");
const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} = require("@firebase/rules-unit-testing");
const { doc, getDoc, setDoc } = require("firebase/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, Timestamp } = require("firebase-admin/firestore");
const { getAuth } = require("firebase-admin/auth");
const fs = require("node:fs");
const path = require("node:path");
let env, db, auth;
const people = [];
const projectId = "demo-campusfit";
before(async () => {
  assert.ok(
    process.env.FIRESTORE_EMULATOR_HOST &&
      process.env.FIREBASE_AUTH_EMULATOR_HOST,
    "Local emulators are required",
  );
  const app = initializeApp({ projectId });
  db = getFirestore(app);
  auth = getAuth(app);
  env = await initializeTestEnvironment({
    projectId,
    firestore: {
      rules: fs.readFileSync(
        path.join(__dirname, "../../../firestore.rules"),
        "utf8",
      ),
    },
  });
  for (let i = 0; i < 3; i++) {
    const u = await auth.createUser({
      email: `social-${Date.now()}-${i}@example.test`,
      password: "Test-only-12345",
      emailVerified: true,
    });
    const response = await fetch(
      `http://${process.env.FIREBASE_AUTH_EMULATOR_HOST}/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=demo-key`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          email: u.email,
          password: "Test-only-12345",
          returnSecureToken: true,
        }),
      },
    );
    const token = (await response.json()).idToken;
    assert.ok(token);
    const profile = {
      uid: u.uid,
      name: `Student ${i}`,
      age: 21,
      heightCm: 170,
      weightKg: 70,
      goalKg: 68,
      level: "Beginner",
      minutes: 10,
      university: "Test University",
      group: "",
      activityOnly: false,
      percentageRanking: true,
      healthAcknowledged: true,
    };
    await db
      .doc(`users/${u.uid}`)
      .set({ schemaVersion: 1, profileJson: JSON.stringify(profile) });
    people.push({
      uid: u.uid,
      token,
      handle: `student_${Date.now()}_${i}`.slice(-20).replace(/^[^a-z]/, "s"),
    });
  }
});
after(async () => {
  await env?.cleanup();
});
async function request(person, action, fields = {}, name = "socialAction") {
  for (let attempt = 0; attempt < 3; attempt++) {
    const response = await fetch(
      `http://127.0.0.1:5001/${projectId}/us-central1/${name}`,
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${person.token}`,
        },
        body: JSON.stringify({ data: { uid: person.uid, action, ...fields } }),
      },
    );
    const data = await response.json();
    if (response.status === 429 && attempt < 2) {
      await new Promise((resolve) => setTimeout(resolve, 1100));
      continue;
    }
    return {
      status: response.status,
      result: data.result ?? data.data,
      error: data.error,
    };
  }
}
async function ok(person, action, fields = {}, name) {
  const result = await request(person, action, fields, name);
  assert.equal(result.status, 200, JSON.stringify(result.error));
  return result.result;
}
test("server-owned social enrollment, rank ties, private consent, groups and erasure", async () => {
  const [owner, peer, observer] = people;
  for (const person of people)
    await ok(person, "register", {
      handle: person.handle,
      alias: person.handle,
      campusOptIn: true,
      leagueOptIn: false,
      discoverByPhone: false,
      directoryConsent: true,
    });
  const ownerClient = env
    .authenticatedContext(owner.uid, { email_verified: true })
    .firestore();
  await assertFails(
    setDoc(doc(ownerClient, `publicProfiles/${owner.uid}`), {
      alias: "forged",
      xp: 999,
    }),
  );
  await ok(owner, "friend", { handle: peer.handle, operation: "request" });
  await ok(peer, "friend", { handle: owner.handle, operation: "accept" });
  const c = await ok(owner, "createChallenge", {
    title: "Study break",
    period: "weekly",
    metric: "minutes",
    visibility: "friends",
    groupId: "",
    teams: [],
    next: false,
  });
  await ok(owner, "joinChallenge", {
    id: c.id,
    team: "",
    weightConsent: false,
  });
  await ok(peer, "joinChallenge", { id: c.id, team: "", weightConsent: false });
  for (const person of [owner, peer])
    await db
      .doc(`users/${person.uid}/sessions/${person.uid}`)
      .set({
        id: person.uid,
        startedAt: Date.now(),
        durationSeconds: 120,
        calories: 8,
        source: "guided",
        distanceMeters: 0,
        reps: 0,
        receivedAt: Timestamp.now(),
      });
  await new Promise((resolve) => setTimeout(resolve, 100));
  const board = await ok(owner, "board", { id: c.id });
  assert.equal(board.rows.length, 2);
  assert.deepEqual(
    board.rows.map((r) => r.rank),
    [1, 1],
  );
  assert.equal(board.rows[0].score, 2);
  await assertFails(
    setDoc(doc(ownerClient, `challenges/${c.id}`), { score: 10000 }),
  );
  assert.notEqual((await request(observer, "board", { id: c.id })).status, 200);
  await ok(owner, "friend", { handle: observer.handle, operation: "request" });
  await ok(observer, "friend", { handle: owner.handle, operation: "accept" });
  const w = await ok(owner, "createChallenge", {
    title: "Gentle future habits",
    period: "daily",
    metric: "percent",
    visibility: "friends",
    groupId: "",
    teams: [],
    next: true,
  });
  await ok(owner, "joinChallenge", { id: w.id, team: "", weightConsent: true });
  const preview = await ok(observer, "board", { id: w.id });
  assert.equal(
    preview.rows.length,
    0,
    "nonparticipants never see weight standings",
  );
  assert.notEqual(
    (
      await request(owner, "createChallenge", {
        title: "Private kilograms",
        period: "weekly",
        metric: "kilograms",
        visibility: "friends",
        teams: [],
        next: true,
      })
    ).status,
    200,
  );
  await ok(peer, "friend", { handle: owner.handle, operation: "remove" });
  await ok(peer, "leaveChallenge", { id: c.id });
  assert.equal(
    (await db.doc(`challenges/${c.id}/members/${peer.uid}`).get()).exists,
    false,
    "leaving remains possible after friendship access is lost",
  );
  const created = await ok(owner, "group", {
    operation: "create",
    name: "Hall one",
    kind: "hostel",
  });
  const group = created.groups.find((g) => g.name === "Hall one");
  assert.ok(group);
  const peerClient = env
    .authenticatedContext(peer.uid, { email_verified: true })
    .firestore();
  await assertFails(getDoc(doc(peerClient, `groups/${group.id}`)));
  await ok(peer, "group", { operation: "join", code: group.code });
  await assertSucceeds(getDoc(doc(peerClient, `groups/${group.id}`)));
  const g = await ok(owner, "createChallenge", {
    title: "Hall teams",
    period: "weekly",
    metric: "minutes",
    visibility: "friends",
    groupId: group.id,
    teams: ["A", "B"],
    next: true,
  });
  await ok(peer, "joinChallenge", {
    id: g.id,
    team: "B",
    weightConsent: false,
  });
  await ok(owner, "group", { operation: "close", id: group.id });
  assert.equal((await db.doc(`challenges/${g.id}`).get()).exists, false);
  const erased = await ok(owner, undefined, {}, "deleteCampusFitAccount");
  assert.equal(erased.removed, true);
  assert.equal(
    (await db.doc(`publicProfiles/${owner.uid}`).get()).exists,
    false,
  );
  assert.equal((await db.doc(`handles/${owner.handle}`).get()).exists, false);
  assert.equal((await db.doc(`challenges/${c.id}`).get()).exists, false);
  assert.equal(
    (await db.doc(`socialSignals/${owner.uid}`).get()).exists,
    false,
  );
  await assert.rejects(
    auth.getUser(owner.uid),
    (e) => e.code === "auth/user-not-found",
  );
});
