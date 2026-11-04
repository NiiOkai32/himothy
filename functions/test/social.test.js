const { test } = require("node:test");
const assert = require("node:assert/strict");
const p = require("../social/policy");
const now = Date.UTC(2026, 10, 3, 12),
  day = Math.floor(now / p.DAY);
const challenge = {
  start: day * p.DAY,
  end: (day + 1) * p.DAY,
  metric: "minutes",
};
const member = { joinedAt: challenge.start, weightConsent: true };
const claim = (id, seconds = 120, startedAt = challenge.start + 1000) => ({
  id,
  durationSeconds: seconds,
  calories: (seconds / 60) * 4,
  startedAt,
  receivedAtMillis: now,
});
test("usernames canonicalize without accepting URLs or whitespace", () => {
  assert.equal(p.handle("  Ama_23 "), "ama_23");
  for (const value of ["ab", "a b", "a/b", "https://example.com", null])
    assert.throws(() => p.handle(value));
});
test("UTC daily period is exact across local DST changes", () => {
  const r = p.period("daily", Date.UTC(2026, 10, 1, 18));
  assert.equal(r.end - r.start, p.DAY);
  assert.equal(new Date(r.start).getUTCHours(), 0);
});
test("weeks start Monday UTC and next week is consecutive", () => {
  const r = p.period("weekly", now),
    n = p.period("weekly", now, true);
  assert.equal(new Date(r.start).getUTCDay(), 1);
  assert.equal(n.start, r.end);
  assert.equal(r.end - r.start, 7 * p.DAY);
});
test("months use real calendar boundaries including leap February", () => {
  const r = p.period("monthly", Date.UTC(2028, 1, 15));
  assert.equal(r.end - r.start, 29 * p.DAY);
  assert.equal(new Date(r.end).getUTCDate(), 1);
});
test("duplicate sessions never double XP or rank credit", () => {
  assert.equal(p.daySummary([claim("a"), claim("a")]).seconds, 120);
  assert.equal(p.daySummary([claim("a"), claim("a")]).xp, 14);
});
test("daily activity and XP caps do not reward longer extreme sessions", () => {
  const s = p.daySummary([claim("a", 2400), claim("b", 2400)]);
  assert.equal(s.seconds, 3600);
  assert.equal(s.xp, 130);
  assert.equal(s.calories, 240);
});
test("activity ignores pre-join, out-of-period and late uploads", () => {
  const m = { joinedAt: challenge.start + 5000 };
  assert.equal(
    p.activityScore(
      challenge,
      m,
      [claim("a", 120, challenge.start + 1000)],
      now,
    ),
    0,
  );
  assert.equal(
    p.activityScore(
      challenge,
      member,
      [{ ...claim("a"), receivedAtMillis: challenge.end + p.DAY + 1 }],
      challenge.end,
    ),
    0,
  );
});
test("minutes/calorie scores prorate the last credited session at the cap", () => {
  assert.equal(
    p.activityScore(
      challenge,
      member,
      [claim("a", 2400), claim("b", 2400)],
      now,
    ),
    60,
  );
  assert.equal(
    p.activityScore(
      { ...challenge, metric: "calories" },
      member,
      [claim("a", 2400), claim("b", 2400)],
      now,
    ),
    240,
  );
});
const weights = [
  { epochDay: day - 1, weightKg: 70, receivedAtMillis: challenge.start - 1000 },
  { epochDay: day, weightKg: 69.9, receivedAtMillis: now, flagged: false },
];
const prefs = { activityOnly: false, percentageRanking: true, heightCm: 170, goalKg: 68 };
const enrolled = { joinedAt: challenge.start - 2000, weightConsent: true };
test("percentage loss is fair across starting body sizes", () => {
  const c = { ...challenge, metric: "percent" };
  const a = p.weightScore(c, enrolled, weights, prefs, now).score;
  const scaled = weights.map((w) => ({ ...w, weightKg: w.weightKg * 0.8 }));
  assert.ok(
    Math.abs(a - p.weightScore(c, enrolled, scaled, prefs, now).score) < 1e-8,
  );
});
test("raw kilograms require a matching privacy preference", () => {
  assert.equal(
    p.weightScore(
      { ...challenge, metric: "kilograms" },
      enrolled,
      weights,
      prefs,
      now,
    ).score,
    null,
  );
  assert.ok(
    p.weightScore(
      { ...challenge, metric: "kilograms" },
      enrolled,
      weights,
      { ...prefs, percentageRanking: false },
      now,
    ).score > 0,
  );
});
test("activity-only preference withdraws weight participation immediately", () =>
  assert.equal(
    p.weightScore(
      { ...challenge, metric: "percent" },
      enrolled,
      weights,
      { ...prefs, activityOnly: true },
      now,
    ).score,
    null,
  ));
test("weight enrollment and explicit consent must predate the start", () => {
  assert.equal(
    p.weightScore(
      { ...challenge, metric: "percent" },
      member,
      weights,
      prefs,
      now,
    ).score,
    null,
  );
  assert.equal(
    p.weightScore(
      { ...challenge, metric: "percent" },
      { ...enrolled, weightConsent: false },
      weights,
      prefs,
      now,
    ).score,
    null,
  );
});
test("a baseline uploaded after the start cannot be backdated into eligibility", () => {
  const late = weights.map((w, i) =>
    i === 0 ? { ...w, receivedAtMillis: now } : w,
  );
  assert.equal(
    p.weightScore(
      { ...challenge, metric: "percent" },
      enrolled,
      late,
      prefs,
      now,
    ).score,
    null,
  );
});
test("daily loss above 1/7 kg is excluded instead of clipped into a reward", () => {
  const fast = weights.map((w, i) => (i ? { ...w, weightKg: 69.5 } : w));
  assert.equal(
    p.weightScore(
      { ...challenge, metric: "percent" },
      enrolled,
      fast,
      prefs,
      now,
    ).score,
    null,
  );
});
test("any flagged period weight excludes weight-loss ranking", () =>
  assert.equal(
    p.weightScore(
      { ...challenge, metric: "percent" },
      enrolled,
      weights.map((w, i) => (i ? { ...w, flagged: true } : w)),
      prefs,
      now,
    ).score,
    null,
  ));
test("weight gains yield zero without an unhealthy-loss reward", () => {
  const gain = weights.map((w, i) => (i ? { ...w, weightKg: 71 } : w));
  assert.equal(
    p.weightScore(
      { ...challenge, metric: "percent" },
      enrolled,
      gain,
      prefs,
      now,
    ).score,
    0,
  );
});
test("rank ties share positions and next place uses competition numbering", () => {
  const rows = p.rank([
    { uid: "b", score: 5 },
    { uid: "a", score: 5 },
    { uid: "c", score: 4 },
    { uid: "d", score: null },
  ]);
  assert.deepEqual(
    rows.map((r) => [r.uid, r.rank]),
    [
      ["a", 1],
      ["b", 1],
      ["c", 3],
      ["d", null],
    ],
  );
});
test("near-equal floats tie at six decimals and non-finite claims are unranked", () => {
  const r = p.rank([
    { uid: "b", score: 1.0000004 },
    { uid: "a", score: 1.0000003 },
    { uid: "c", score: NaN },
  ]);
  assert.deepEqual(
    r.map((x) => [x.uid, x.rank]),
    [
      ["a", 1],
      ["b", 1],
      ["c", null],
    ],
  );
});
test("team scores average all enrolled participants rather than reward larger teams", () => {
  const rows = p.teams([
    { uid: "a", team: "A", score: 10 },
    { uid: "b", team: "A", score: 0 },
    { uid: "c", team: "B", score: 6 },
  ]);
  assert.equal(rows[0].uid, "B");
  assert.equal(rows.find((r) => r.uid === "A").score, 5);
});
test("teams with no eligible scores stay unranked", () =>
  assert.equal(p.teams([{ uid: "a", team: "A", score: null }])[0].rank, null));
test("league movement is bounded, needs five members and never promotes zero activity", () => {
  assert.equal(p.leagueTier(0, 1, 30, 10), 1);
  assert.equal(p.leagueTier(3, 1, 30, 10), 3);
  assert.equal(p.leagueTier(2, 30, 30, 0), 1);
  assert.equal(p.leagueTier(2, 1, 4, 10), 2);
  assert.equal(p.leagueTier(0, 1, 30, 0), 0);
});
test("protected rest bridges once per UTC week without earning XP", () => {
  const s = p.streak(
    [
      { epochDay: day, seconds: 120 },
      { epochDay: day - 1, rest: true },
      { epochDay: day - 2, seconds: 120 },
    ],
    day,
  );
  assert.equal(s.days, 3);
  assert.equal(p.daySummary([]).xp, 0);
});
test("a second rest day in a week does not invent a continuing streak", () => {
  const monday = Math.floor(p.period("weekly", now).start / p.DAY);
  const s = p.streak(
    [
      { epochDay: monday + 4, seconds: 120 },
      { epochDay: monday + 3, rest: true },
      { epochDay: monday + 2, rest: true },
      { epochDay: monday + 1, seconds: 120 },
    ],
    monday + 4,
  );
  assert.equal(s.days, 2);
});
test("habit badges depend on activity, never weight change", () => {
  const base = { seconds: 6000, eligibleSessions: 10, earlyBird: true };
  assert.deepEqual(p.badges(base, 7), [
    "first_move",
    "ten_sessions",
    "hundred_minutes",
    "early_bird",
    "seven_day_streak",
  ]);
  assert.deepEqual(p.badges({ seconds: 0 }, 0), []);
});
process.env.GCLOUD_PROJECT = "demo-campusfit";
process.env.FIRESTORE_EMULATOR_HOST = "127.0.0.1:1";
const fn = require("../index");
test("social callable rejects missing identity before any database access", async () =>
  assert.rejects(
    fn.socialAction.run({ data: { uid: "a", action: "summary" } }),
    (e) => e.code === "unauthenticated",
  ));
test("social callable binds target UID and rejects unverified identities", async () => {
  await assert.rejects(
    fn.socialAction.run({
      auth: { uid: "a", token: { email_verified: true } },
      data: { uid: "b", action: "summary" },
    }),
    (e) => e.code === "permission-denied",
  );
  await assert.rejects(
    fn.socialAction.run({
      auth: { uid: "a", token: { email_verified: false } },
      data: { uid: "a", action: "summary" },
    }),
    (e) => e.code === "permission-denied",
  );
});

test("team trophies follow team averages, not the highest individual",()=>{assert.deepEqual(p.winners({metric:"minutes",teamMode:true},[{uid:"a",team:"A",score:10},{uid:"b",team:"A",score:0},{uid:"c",team:"B",score:6}]),["c"]);});
test("tied activity winners share trophies but weight standings never award them",()=>{assert.deepEqual(p.winners({metric:"minutes",teamMode:false},[{uid:"a",score:5},{uid:"b",score:5},{uid:"c",score:0}]),["a","b"]);assert.deepEqual(p.winners({metric:"percent",teamMode:false},[{uid:"a",score:1}]),[]);});

test("underweight baselines or targets never receive weight-loss ranking",()=>{assert.equal(p.weightScore({...challenge,metric:"percent"},enrolled,weights,{...prefs,heightCm:200,goalKg:80},now).score,null);assert.equal(p.weightScore({...challenge,metric:"percent"},enrolled,weights,{...prefs,goalKg:40},now).score,null);});

test("crossing below the BMI threshold is excluded even at a gradual loss rate",()=>{const near=weights.map((w,i)=>({...w,weightKg:i?73.95:74.05}));assert.equal(p.weightScore({...challenge,metric:"percent"},enrolled,near,{...prefs,heightCm:200,goalKg:80},now).score,null);});
