"use strict";
const { HttpsError } = require("firebase-functions/v2/https");
const { FieldValue } = require("firebase-admin/firestore");
const crypto = require("node:crypto");
const p = require("./policy");
const PHRASES = [
  "You showed up. Nice work!",
  "Catch me if you can!",
  "Small steps, strong habits.",
  "Team effort!",
  "Your consistency is showing.",
  "Rest and recover — see you next time.",
];
const hash = (value) =>
  crypto.createHash("sha256").update(value).digest("hex").slice(0, 24);
const pair = (a, b) => hash([a, b].sort().join("\0"));
module.exports = function (db, adminAuth) {
  const user = (uid) => db.doc(`users/${uid}`),
    social = (uid) => db.doc(`publicProfiles/${uid}`);
  function identity(request) {
    if (!request.auth) throw new HttpsError("unauthenticated", "Sign in first");
    if (request.data?.uid !== request.auth.uid)
      throw new HttpsError(
        "permission-denied",
        "Account changed; sign in again",
      );
    if (request.auth.token.email_verified !== true)
      throw new HttpsError("permission-denied", "Verify your email first");
    return request.auth.uid;
  }
  async function active(uid) {
    const [tomb, profile] = await db.getAll(
      db.doc(`accountTombstones/${uid}`),
      user(uid),
    );
    if (tomb.exists || !profile.exists)
      throw new HttpsError(
        "failed-precondition",
        "An active profile is required",
      );
    try {
      return JSON.parse(profile.get("profileJson"));
    } catch (_) {
      throw new HttpsError("failed-precondition", "Save your profile first");
    }
  }
  async function ready(uid) {
    const profile = await active(uid);
    const s = await social(uid).get();
    if (!s.exists)
      throw new HttpsError(
        "failed-precondition",
        "Set up your social alias first",
      );
    return { profile, public: s.data() };
  }
  async function limited(uid, key, seconds = 2) {
    await db.runTransaction(async (tx) => {
      const ref = db.doc(`users/${uid}/socialLimits/${key}`),
        s = await tx.get(ref);
      if (s.exists && Date.now() - s.get("lastAt") < seconds * 1000)
        throw new HttpsError(
          "resource-exhausted",
          "Please wait before trying again",
        );
      tx.set(ref, { lastAt: Date.now() });
    });
  }
  async function edge(uid, other) {
    const s = await db.doc(`friendships/${pair(uid, other)}`).get();
    return s.exists && s.get("status") === "accepted";
  }
  async function blocked(a, b) {
    const s = await db.getAll(
      db.doc(`users/${a}/blocks/${b}`),
      db.doc(`users/${b}/blocks/${a}`),
    );
    return s.some((d) => d.exists);
  }
  async function profiles(uids) {
    if (!uids.length) return new Map();
    const docs = await db.getAll(...uids.map(social));
    return new Map(
      docs
        .filter((d) => d.exists)
        .map((d) => [
          d.id,
          { uid: d.id, handle: d.get("handle"), alias: d.get("alias") },
        ]),
    );
  }
  async function stats(uid) {
    return (
      (await db.doc(`users/${uid}/game/state`).get()).data() ?? {
        xp: 0,
        seconds: 0,
        eligibleSessions: 0,
        earlyBird: false,
        badges: [],
        streak: 0,
        level: 1,
        tier: 0,
      }
    );
  }
  async function refreshDay(uid, day) {
    const now = Date.now(),
      today = Math.floor(now / p.DAY);
    await db.runTransaction(async (tx) => {
      const state = db.doc(`users/${uid}/game/state`),
        dayRef = db.doc(`users/${uid}/gameDays/${day}`);
      const [tomb, profile, oldDay, oldState, sessions, recent, rest] =
        await Promise.all([
          tx.get(db.doc(`accountTombstones/${uid}`)),
          tx.get(social(uid)),
          tx.get(dayRef),
          tx.get(state),
          tx.get(
            user(uid)
              .collection("sessions")
              .where("startedAt", ">=", day * p.DAY)
              .where("startedAt", "<", (day + 1) * p.DAY)
              .orderBy("startedAt")
              .limit(300),
          ),
          tx.get(
            user(uid)
              .collection("gameDays")
              .where("epochDay", ">=", today - 399)
              .orderBy("epochDay"),
          ),
          tx.get(
            user(uid)
              .collection("restDays")
              .where("epochDay", ">=", today - 399),
          ),
        ]);
      if (tomb.exists) return;
      const summary = p.daySummary(sessions.docs.map((d) => d.data())),
        before = oldDay.data() ?? {},
        old = oldState.data() ?? {};
      const history = recent.docs
        .map((d) => d.data())
        .filter((d) => d.epochDay !== day);
      history.push({ ...summary, epochDay: day });
      for (const r of rest.docs) {
        const existing = history.find((d) => d.epochDay === r.get("epochDay"));
        if (existing) existing.rest = true;
        else history.push({ epochDay: r.get("epochDay"), rest: true });
      }
      const streak = p.streak(history, today),
        longest = Math.max(old.longestStreak || 0, streak.days);
      const next = {
        xp: (old.xp || 0) + summary.xp - (before.xp || 0),
        seconds: (old.seconds || 0) + summary.seconds - (before.seconds || 0),
        eligibleSessions:
          (old.eligibleSessions || 0) +
          summary.eligibleSessions -
          (before.eligibleSessions || 0),
        earlyBird: !!(old.earlyBird || summary.earlyBird),
        streak: streak.days,
        streakCapped: streak.capped,
        longestStreak: longest,
        tier: old.tier || 0,
        updatedAt: now,
      };
      next.level = 1 + Math.floor(next.xp / 100);
      next.badges = [
        ...new Set([...(old.badges || []), ...p.badges(next, longest)]),
      ];
      const league = old.leagueId
        ? await tx.get(db.doc(`leagues/${old.leagueId}`))
        : null;
      tx.set(dayRef, { ...summary, epochDay: day });
      tx.set(state, next, { merge: true });
      const week = p.period("weekly", now).start,
        weekDay = Math.floor(week / p.DAY);
      const weekSeconds = history
        .filter((d) => d.epochDay >= weekDay && d.epochDay <= today)
        .reduce((n, d) => n + (d.seconds || 0), 0);
      if (profile.exists && profile.get("campusOptIn"))
        tx.set(
          db.doc(`campusBoards/${profile.get("campusKey")}/members/${uid}`),
          {
            uid,
            handle: profile.get("handle"),
            alias: profile.get("alias"),
            campusKey: profile.get("campusKey"),
            weekStart: week,
            score: weekSeconds / 60,
            level: next.level,
            xp: next.xp,
          },
        );
      if (
        league?.exists &&
        !league.get("finalized") &&
        now <= league.get("end") + p.DAY
      ) {
        const first = Math.floor(league.get("start") / p.DAY),
          score =
            history
              .filter((d) => d.epochDay >= first && d.epochDay < first + 7)
              .reduce((n, d) => n + (d.seconds || 0), 0) / 60;
        tx.update(league.ref, { [`scores.${uid}`]: score });
      }
    });
  }
  async function rebuildRecent(uid) {
    const state = await stats(uid);
    if (!state.backfillDone) {
      let query = user(uid)
        .collection("sessions")
        .orderBy("startedAt")
        .orderBy(require("firebase-admin/firestore").FieldPath.documentId())
        .limit(200);
      if (state.backfillTime != null)
        query = query.startAfter(state.backfillTime, state.backfillId);
      const page = await query.get(),
        days = [],
        consumed = [];
      for (const doc of page.docs) {
        const day = Math.floor(doc.get("startedAt") / p.DAY);
        if (!days.includes(day) && days.length >= 20) break;
        if (!days.includes(day)) days.push(day);
        consumed.push(doc);
      }
      for (const day of days) await refreshDay(uid, day);
      await db.runTransaction(async (tx) => {
        const [tomb, current] = await Promise.all([
          tx.get(db.doc(`accountTombstones/${uid}`)),
          tx.get(db.doc(`users/${uid}/game/state`)),
        ]);
        if (tomb.exists) return;
        if (
          current.get("backfillTime") !== state.backfillTime ||
          current.get("backfillId") !== state.backfillId
        )
          return;
        const last = consumed.at(-1);
        tx.set(
          db.doc(`users/${uid}/game/state`),
          {
            backfillDone: page.size < 200 && consumed.length === page.size,
            ...(last
              ? { backfillTime: last.get("startedAt"), backfillId: last.id }
              : {}),
          },
          { merge: true },
        );
      });
    }
    await refreshDay(uid, Math.floor(Date.now() / p.DAY));
  }
  async function finalizeLeague(id) {
    await db.runTransaction(async (tx) => {
      const ref = db.doc(`leagues/${id}`),
        snap = await tx.get(ref);
      if (
        !snap.exists ||
        snap.get("finalized") ||
        Date.now() < snap.get("end") + p.DAY
      )
        return;
      const data = snap.data(),
        members = await Promise.all(
          data.memberUids.map((uid) =>
            tx.get(db.doc(`accountTombstones/${uid}`)),
          ),
        );
      const eligible = data.memberUids.filter((uid, i) => !members[i].exists),
        rows = p.rank(
          eligible.map((uid) => ({ uid, score: data.scores?.[uid] || 0 })),
        );
      for (const row of rows)
        tx.set(
          db.doc(`users/${row.uid}/game/state`),
          {
            tier: p.leagueTier(data.tier, row.rank, rows.length, row.score),
            lastLeagueRank: row.rank,
            lastLeagueId: id,
          },
          { merge: true },
        );
      tx.update(ref, { finalized: true });
    });
  }
  async function enrollLeague(uid, profile) {
    let state = await stats(uid);
    const now = Date.now(),
      period = p.period("weekly", now);
    if (state.leagueId) {
      const old = await db.doc(`leagues/${state.leagueId}`).get();
      if (old.exists && old.get("start") === period.start)
        return state.leagueId;
      if (old.exists && now < old.get("end") + p.DAY) return state.leagueId;
      await finalizeLeague(state.leagueId);
      state = await stats(uid);
    }
    if (!profile.leagueOptIn) return null;
    const tier = state.tier || 0,
      base = `${profile.campusKey}_${period.start}_${tier}`;
    return db.runTransaction(async (tx) => {
      const counterRef = db.doc(`leagueCounters/${base}`),
        gameRef = db.doc(`users/${uid}/game/state`);
      const [tomb, game, sharing, counter] = await Promise.all([
        tx.get(db.doc(`accountTombstones/${uid}`)),
        tx.get(gameRef),
        tx.get(social(uid)),
        tx.get(counterRef),
      ]);
      if (tomb.exists)
        throw new HttpsError("failed-precondition", "Account deletion pending");
      if (
        !sharing.exists ||
        !sharing.get("leagueOptIn") ||
        sharing.get("campusKey") !== profile.campusKey
      )
        return null;
      if (game.get("leagueWeek") === period.start) return game.get("leagueId");
      let shard = counter.get("shard") || 0,
        id = `${base}_${shard}`,
        cohort = await tx.get(db.doc(`leagues/${id}`));
      while ((cohort.get("memberUids") || []).length >= 30) {
        shard++;
        id = `${base}_${shard}`;
        cohort = await tx.get(db.doc(`leagues/${id}`));
      }
      tx.set(counterRef, { shard });
      tx.set(db.doc(`leagues/${id}`), {
        start: period.start,
        end: period.end,
        tier,
        memberUids: [...new Set([...(cohort.get("memberUids") || []), uid])],
        scores: { ...(cohort.get("scores") || {}), [uid]: 0 },
        finalized: false,
      });
      tx.set(
        gameRef,
        { leagueWeek: period.start, leagueId: id, tier },
        { merge: true },
      );
      return id;
    });
  }
  async function challengeAccess(uid, id) {
    if (typeof id !== "string" || !/^[a-zA-Z0-9_-]{1,100}$/.test(id))
      throw new HttpsError("invalid-argument", "Invalid challenge");
    const doc = await db.doc(`challenges/${id}`).get();
    if (!doc.exists) throw new HttpsError("not-found", "Challenge not found");
    const c = doc.data();
    if (c.visibility === "campus") {
      const s = await social(uid).get();
      if (!s.exists || s.get("campusKey") !== c.campusKey)
        throw new HttpsError(
          "permission-denied",
          "This campus board belongs to another campus",
        );
    } else if (c.groupId) {
      const g = await db.doc(`groups/${c.groupId}`).get();
      if (!g.exists || !g.get("memberUids").includes(uid))
        throw new HttpsError("permission-denied", "Group membership required");
    } else if (c.owner !== uid && !(await edge(uid, c.owner)))
      throw new HttpsError(
        "permission-denied",
        "Accepted friendship with the creator required",
      );
    return { ...c, id: doc.id };
  }
  async function board(uid, id, system = false) {
    let c;
    if (system) {
      const document = await db.doc(`challenges/${id}`).get();
      if (!document.exists) throw new HttpsError("not-found", "Challenge not found");
      c = { id: document.id, ...document.data() };
    } else c = await challengeAccess(uid, id);
    const members = await db.collection(`challenges/${id}/members`).get(), now = Date.now();
    const isMember = members.docs.some((d) => d.id === uid);
    if (p.WEIGHT.has(c.metric) && !isMember && !system)
      return {
        challenge: c,
        rows: [],
        teams: [],
        messages: [],
        joined: false,
        serverTime: now,
      };
    const group = c.groupId ? await db.doc(`groups/${c.groupId}`).get() : null;
    const names = await profiles(members.docs.map((d) => d.id));
    const rows = [];
    for (const member of members.docs) {
      const alias = names.get(member.id);
      if (!alias) continue;
      if (
        c.groupId &&
        (!group?.exists || !group.get("memberUids").includes(member.id))
      )
        continue;
      if (
        c.visibility === "friends" &&
        !c.groupId &&
        member.id !== c.owner &&
        !(await edge(member.id, c.owner))
      )
        continue;
      const tomb = await db.doc(`accountTombstones/${member.id}`).get();
      if (tomb.exists) continue;
      let score = null,
        reason = "";
      if (p.WEIGHT.has(c.metric)) {
        const root = await user(member.id).get();
        let preferences;
        try {
          preferences = JSON.parse(root.get("profileJson"));
        } catch (_) {
          continue;
        }
        const weights = await user(member.id)
          .collection("weighIns")
          .where("epochDay", ">=", Math.floor(c.start / p.DAY) - 1)
          .where("epochDay", "<", Math.ceil(c.end / p.DAY))
          .get();
        const result = p.weightScore(
          c,
          member.data(),
          weights.docs.map((d) => d.data()),
          preferences,
          now,
        );
        score = result.score;
        reason = member.id === uid ? result.reason : "";
      } else {
        const sessions = await user(member.id)
          .collection("sessions")
          .where("startedAt", ">=", c.start)
          .where("startedAt", "<", c.end)
          .orderBy("startedAt")
          .limit(9301)
          .get();
        score = p.activityScore(
          c,
          member.data(),
          sessions.docs.map((d) => d.data()),
          now,
        );
      }
      rows.push({
        uid: member.id,
        handle: alias.handle,
        alias: alias.alias,
        score,
        reason,
        team: member.get("team") || "",
        selfReported: true,
      });
    }
    const messages = await db
      .collection(`challenges/${id}/messages`)
      .orderBy("at", "desc")
      .limit(50)
      .get();
    const cheers = [];
    for (const m of messages.docs) {
      if (!(await blocked(uid, m.get("from"))))
        cheers.push({ id: m.id, ...m.data() });
    }
    const ranked = p.rank(rows),
      visible = [];
    for (const row of ranked)
      if (system || !(await blocked(uid, row.uid))) visible.push(row);
    return {
      challenge: c,
      rows: visible,
      teams: p.teams(rows),
      messages: cheers,
      joined: members.docs.some((d) => d.id === uid),
      serverTime: now,
    };
  }
  async function summary(uid) {
    const { public: me } = await ready(uid);
    await rebuildRecent(uid);
    if (me.leagueOptIn) {
      await enrollLeague(uid, me);
      await refreshDay(uid, Math.floor(Date.now() / p.DAY));
    }
    const [friends, groups, joined, created] = await Promise.all([
      db
        .collection("friendships")
        .where("memberUids", "array-contains", uid)
        .limit(201)
        .get(),
      db
        .collection("groups")
        .where("memberUids", "array-contains", uid)
        .limit(50)
        .get(),
      db
        .collection("challenges")
        .where("memberUids", "array-contains", uid)
        .limit(100)
        .get(),
      db.collection("challenges").where("owner", "==", uid).limit(100).get(),
    ]);
    const friendIds = friends.docs.map((d) =>
        d.get("memberUids").find((id) => id !== uid),
      ),
      aliases = await profiles(friendIds);
    const all = new Map(
      [...joined.docs, ...created.docs].map((d) => [
        d.id,
        { id: d.id, ...d.data() },
      ]),
    );
    const accepted = friends.docs
      .filter((d) => d.get("status") === "accepted")
      .map((d) => d.get("memberUids").find((id) => id !== uid));
    for (let i = 0; i < accepted.length; i += 30) {
      const found = await db
        .collection("challenges")
        .where("owner", "in", accepted.slice(i, i + 30))
        .limit(100)
        .get();
      for (const d of found.docs)
        if (d.get("visibility") === "friends" && !d.get("groupId"))
          all.set(d.id, { id: d.id, ...d.data() });
    }
    for (const group of groups.docs) {
      const found = await db
        .collection("challenges")
        .where("groupId", "==", group.id)
        .limit(100)
        .get();
      for (const d of found.docs) all.set(d.id, { id: d.id, ...d.data() });
    }
    const campus = await db
      .collection("challenges")
      .where("campusKey", "==", me.campusKey)
      .where("visibility", "==", "campus")
      .limit(50)
      .get();
    for (const d of campus.docs) all.set(d.id, { id: d.id, ...d.data() });
    const game = await stats(uid),
      awards = await user(uid).collection("awards").limit(100).get(),
      blocks = await user(uid).collection("blocks").limit(200).get(),
      blockNames = await profiles(blocks.docs.map((d) => d.id));
    return {
      me,
      game,
      blocked: blocks.docs.map((d) => ({
        uid: d.id,
        alias: blockNames.get(d.id)?.alias || "Deleted account",
        handle: blockNames.get(d.id)?.handle || "",
      })),
      awards: awards.docs.map((d) => ({ id: d.id, ...d.data() })),
      friends: friends.docs
        .filter((d) =>
          aliases.has(d.get("memberUids").find((id) => id !== uid)),
        )
        .map((d) => ({
          id: d.id,
          ...d.data(),
          person: {
            uid: d.get("memberUids").find((id) => id !== uid),
            ...aliases.get(d.get("memberUids").find((id) => id !== uid)),
          },
        })),
      groups: groups.docs.map((d) => ({ id: d.id, ...d.data() })),
      challenges: [...all.values()].sort((a, b) => b.start - a.start),
      phrases: PHRASES,
    };
  }
  async function register(uid, data) {
    if (data.directoryConsent !== true)
      throw Error("Confirm social sharing before saving");
    const privateProfile = await active(uid),
      name = p.handle(data.handle),
      alias = p.text(data.alias, 40),
      campusKey = hash(p.campus(privateProfile.university));
    if (data.discoverByPhone) {
      const u = await adminAuth.getUser(uid);
      if (!u.phoneNumber)
        throw new HttpsError(
          "failed-precondition",
          "Link and verify a phone number before enabling phone discovery",
        );
    }
    await db.runTransaction(async (tx) => {
      const [tomb, claimed, old, game] = await Promise.all([
        tx.get(db.doc(`accountTombstones/${uid}`)),
        tx.get(db.doc(`handles/${name}`)),
        tx.get(social(uid)),
        tx.get(db.doc(`users/${uid}/game/state`)),
      ]);
      const league = game.get("leagueId")
        ? await tx.get(db.doc(`leagues/${game.get("leagueId")}`))
        : null;
      if (tomb.exists)
        throw new HttpsError("failed-precondition", "Account deletion pending");
      if (claimed.exists && claimed.get("uid") !== uid)
        throw new HttpsError("already-exists", "That username is taken");
      const value = {
        uid,
        handle: name,
        alias,
        campusKey,
        campusLabel: p.text(privateProfile.university, 120),
        campusOptIn: data.campusOptIn === true,
        leagueOptIn: data.leagueOptIn === true,
        discoverByPhone: data.discoverByPhone === true,
      };
      if (old.exists && old.get("handle") !== name)
        tx.delete(db.doc(`handles/${old.get("handle")}`));
      if (old.exists && old.get("campusKey") !== campusKey)
        tx.delete(
          db.doc(`campusBoards/${old.get("campusKey")}/members/${uid}`),
        );
      if (!value.campusOptIn)
        tx.delete(db.doc(`campusBoards/${campusKey}/members/${uid}`));
      if (
        league?.exists &&
        (!value.leagueOptIn ||
          (old.exists && old.get("campusKey") !== campusKey))
      ) {
        tx.update(league.ref, {
          memberUids: FieldValue.arrayRemove(uid),
          [`scores.${uid}`]: FieldValue.delete(),
        });
        tx.set(
          db.doc(`users/${uid}/game/state`),
          { leagueId: FieldValue.delete(), leagueWeek: FieldValue.delete() },
          { merge: true },
        );
      }
      tx.set(db.doc(`handles/${name}`), { uid });
      tx.set(social(uid), value);
    });
    await rebuildRecent(uid);
    return summary(uid);
  }
  async function createChallenge(uid, data) {
    const { profile, public: me } = await ready(uid),
      metric = data.metric;
    if (!p.METRICS.includes(metric)) throw Error("Invalid ranking metric");
    if (
      p.WEIGHT.has(metric) &&
      (profile.activityOnly ||
        (metric === "kilograms" && profile.percentageRanking))
    )
      throw new HttpsError(
        "failed-precondition",
        "Your privacy settings exclude this weight metric",
      );
    const visibility = data.visibility === "campus" ? "campus" : "friends";
    if (visibility === "campus" && (p.WEIGHT.has(metric) || !me.campusOptIn))
      throw new HttpsError(
        "failed-precondition",
        "Campus boards use opt-in activity metrics only",
      );
    const timing = p.period(
        data.period,
        Date.now(),
        p.WEIGHT.has(metric) || data.next === true,
      ),
      groupId = data.groupId || "";
    if (groupId && !/^[a-zA-Z0-9_-]{1,100}$/.test(groupId))
      throw Error("Invalid group");
    if (groupId && visibility === "campus")
      throw Error("Choose a private group or a campus challenge, not both");
    if (groupId) {
      const g = await db.doc(`groups/${groupId}`).get();
      if (!g.exists || !g.get("memberUids").includes(uid))
        throw new HttpsError("permission-denied", "Join the group first");
    }
    const teams = (data.teams || []).map((t) => p.text(t, 30));
    if (
      teams.length === 1 ||
      teams.length > 8 ||
      new Set(teams.map((t) => t.toLowerCase())).size !== teams.length
    )
      throw Error("Use 2–8 distinct teams, or no teams");
    const id = crypto.randomUUID();
    await db.runTransaction(async (tx) => {
      const tomb = await tx.get(db.doc(`accountTombstones/${uid}`));
      if (tomb.exists)
        throw new HttpsError("failed-precondition", "Account deletion pending");
      tx.create(db.doc(`challenges/${id}`), {
        title: p.text(data.title, 60),
        owner: uid,
        period: data.period,
        metric,
        visibility,
        groupId,
        teams,
        teamMode: teams.length > 0,
        campusKey: me.campusKey,
        start: timing.start,
        end: timing.end,
        memberUids: [],
        revision: 0,
        finalized: false,
        createdAt: Date.now(),
      });
    });
    return { id };
  }
  async function joinChallenge(uid, data) {
    const c = await challengeAccess(uid, data.id),
      { profile, public: me } = await ready(uid),
      now = Date.now();
    if (c.visibility === "campus" && !me.campusOptIn)
      throw new HttpsError(
        "failed-precondition",
        "Enable campus sharing before joining a campus challenge",
      );
    if (now >= c.end)
      throw new HttpsError("failed-precondition", "This challenge has ended");
    if (p.WEIGHT.has(c.metric)) {
      if (now >= c.start)
        throw new HttpsError(
          "failed-precondition",
          "Weight enrollment closes when the period starts",
        );
      if (
        data.weightConsent !== true ||
        profile.activityOnly ||
        (c.metric === "kilograms" && profile.percentageRanking)
      )
        throw new HttpsError(
          "failed-precondition",
          "Explicit consent and matching privacy settings are required",
        );
      if (
        !Number.isInteger(profile.age) ||
        profile.age < 18 ||
        profile.age > 100 ||
        !Number.isFinite(profile.heightCm) ||
        profile.heightCm < 120 ||
        profile.heightCm > 230 ||
        !Number.isFinite(profile.goalKg) ||
        profile.goalKg < 35 ||
        profile.goalKg > 350 ||
        profile.goalKg / Math.pow(profile.heightCm / 100, 2) < 18.5
      )
        throw new HttpsError(
          "failed-precondition",
          "Review your health profile before a weight challenge",
        );
    }
    const team = c.teamMode ? p.text(data.team, 30) : "";
    if (c.teamMode && !c.teams.includes(team))
      throw Error("Choose one of the listed teams");
    await db.runTransaction(async (tx) => {
      const [tomb, snap, member] = await Promise.all([
        tx.get(db.doc(`accountTombstones/${uid}`)),
        tx.get(db.doc(`challenges/${data.id}`)),
        tx.get(db.doc(`challenges/${data.id}/members/${uid}`)),
      ]);
      if (tomb.exists)
        throw new HttpsError("failed-precondition", "Account deletion pending");
      if (member.exists) return;
      const list = snap.get("memberUids");
      if (list.length >= 50)
        throw new HttpsError(
          "resource-exhausted",
          "This challenge is full (50 members)",
        );
      tx.create(db.doc(`challenges/${data.id}/members/${uid}`), {
        joinedAt: now,
        weightConsent: !!data.weightConsent,
        team,
      });
      tx.update(snap.ref, {
        memberUids: FieldValue.arrayUnion(uid),
        revision: FieldValue.increment(1),
      });
    });
    return board(uid, data.id);
  }
  async function leaveChallenge(uid, id) {
    if (typeof id !== "string" || !/^[a-zA-Z0-9_-]{1,100}$/.test(id))
      throw Error("Invalid challenge");
    // A participant can always withdraw, even after losing friendship/group access.
    await db.runTransaction(async (tx) => {
      const [doc, member] = await Promise.all([
        tx.get(db.doc(`challenges/${id}`)),
        tx.get(db.doc(`challenges/${id}/members/${uid}`)),
      ]);
      if (!member.exists) return;
      tx.delete(member.ref);
      if (doc.exists)
        tx.update(doc.ref, {
          memberUids: FieldValue.arrayRemove(uid),
          revision: FieldValue.increment(1),
        });
    });
    return { left: true };
  }
  async function withdraw(uid, predicate) {
    const entries = await db
      .collection("challenges")
      .where("memberUids", "array-contains", uid)
      .get();
    for (const doc of entries.docs)
      if (predicate(doc.data())) await leaveChallenge(uid, doc.id);
  }
  async function campusBoard(uid) {
    const { public: me } = await ready(uid);
    const week = p.period("weekly", Date.now()).start;
    const found = await db
      .collection(`campusBoards/${me.campusKey}/members`)
      .where("weekStart", "==", week)
      .orderBy("score", "desc")
      .limit(100)
      .get();
    const all = p.rank(found.docs.map((d) => d.data())),
      rows = [];
    for (const row of all) if (!(await blocked(uid, row.uid))) rows.push(row);
    return {
      campus: me.campusLabel,
      weekStart: week,
      rows,
      metric: "minutes",
      limit: 100,
    };
  }
  async function league(uid) {
    const { public: me } = await ready(uid),
      id = await enrollLeague(uid, me);
    if (!id) return { optedIn: false };
    const doc = await db.doc(`leagues/${id}`).get(),
      c = doc.data(),
      names = await profiles(c.memberUids);
    const all = p.rank(
        c.memberUids
          .filter((id) => names.has(id))
          .map((id) => ({
            uid: id,
            alias: names.get(id).alias,
            score: c.scores?.[id] || 0,
          })),
      ),
      rows = [];
    for (const row of all) if (!(await blocked(uid, row.uid))) rows.push(row);
    return {
      optedIn: true,
      id,
      ...c,
      rows,
      pendingFinalization: Date.now() >= c.end && !c.finalized,
    };
  }
  async function friendAction(uid, data) {
    await ready(uid);
    const name = p.handle(data.handle),
      lookup = await db.doc(`handles/${name}`).get();
    const other = lookup.get("uid");
    if (!other || other === uid || (await blocked(uid, other)))
      throw new HttpsError("not-found", "This person cannot be added");
    const ref = db.doc(`friendships/${pair(uid, other)}`),
      action = data.operation;
    await db.runTransaction(async (tx) => {
      const [tomb, target, old, count] = await Promise.all([
        tx.get(db.doc(`accountTombstones/${uid}`)),
        tx.get(db.doc(`accountTombstones/${other}`)),
        tx.get(ref),
        tx.get(
          db
            .collection("friendships")
            .where("memberUids", "array-contains", uid)
            .limit(201),
        ),
      ]);
      if (tomb.exists || target.exists)
        throw new HttpsError("failed-precondition", "Account unavailable");
      if (action === "request") {
        if (count.size >= 200)
          throw new HttpsError(
            "resource-exhausted",
            "Friend list limit reached",
          );
        if (!old.exists)
          tx.create(ref, {
            from: uid,
            to: other,
            status: "pending",
            memberUids: [uid, other],
            at: Date.now(),
          });
      } else if (action === "accept") {
        if (
          !old.exists ||
          old.get("to") !== uid ||
          old.get("status") !== "pending"
        )
          throw new HttpsError("permission-denied", "No incoming request");
        tx.update(ref, { status: "accepted" });
        tx.set(db.doc(`users/${uid}/friends/${other}`), { uid: other });
        tx.set(db.doc(`users/${other}/friends/${uid}`), { uid });
      } else if (action === "remove" || action === "decline") {
        tx.delete(ref);
        tx.delete(db.doc(`users/${uid}/friends/${other}`));
        tx.delete(db.doc(`users/${other}/friends/${uid}`));
      } else if (action === "block") {
        tx.set(db.doc(`users/${uid}/blocks/${other}`), { uid: other });
        tx.delete(ref);
        tx.delete(db.doc(`users/${uid}/friends/${other}`));
        tx.delete(db.doc(`users/${other}/friends/${uid}`));
      } else throw Error("Unknown friend action");
    });
    if (["remove", "decline", "block"].includes(action)) {
      await withdraw(
        uid,
        (c) => c.visibility === "friends" && !c.groupId && c.owner === other,
      );
      await withdraw(
        other,
        (c) => c.visibility === "friends" && !c.groupId && c.owner === uid,
      );
    }
    return summary(uid);
  }
  async function phoneLookup(uid, data) {
    await ready(uid);
    await limited(uid, "phone", 10);
    if (typeof data.phone !== "string" || !/^\+[1-9]\d{7,14}$/.test(data.phone))
      throw Error("Use an international +country-code phone number");
    let target;
    try {
      target = await adminAuth.getUserByPhoneNumber(data.phone);
    } catch (_) {
      throw new HttpsError(
        "not-found",
        "No discoverable account for that number",
      );
    }
    const s = await social(target.uid).get();
    if (
      !s.exists ||
      !s.get("discoverByPhone") ||
      (await blocked(uid, target.uid))
    )
      throw new HttpsError(
        "not-found",
        "No discoverable account for that number",
      );
    return { handle: s.get("handle"), alias: s.get("alias") };
  }
  async function closeGroup(g) {
    const challenges = await db
      .collection("challenges")
      .where("groupId", "==", g.id)
      .get();
    for (const d of challenges.docs) await db.recursiveDelete(d.ref);
    await db.doc(`groupCodes/${g.get("code")}`).delete();
    await db.recursiveDelete(g.ref);
  }
  async function groupAction(uid, data) {
    await ready(uid);
    if (data.operation === "create") {
      const name = p.text(data.name, 60),
        kind = data.kind;
      if (!["friends", "hostel", "department", "class"].includes(kind))
        throw Error("Choose a group type");
      const owned = await db
        .collection("groups")
        .where("owner", "==", uid)
        .limit(20)
        .get();
      if (owned.size >= 20)
        throw new HttpsError("resource-exhausted", "Group limit reached");
      const id = crypto.randomUUID(),
        code = crypto.randomBytes(8).toString("hex");
      await db.runTransaction(async (tx) => {
        const tomb = await tx.get(db.doc(`accountTombstones/${uid}`));
        if (tomb.exists)
          throw new HttpsError(
            "failed-precondition",
            "Account deletion pending",
          );
        tx.create(db.doc(`groups/${id}`), {
          name,
          kind,
          owner: uid,
          code,
          memberUids: [uid],
          createdAt: Date.now(),
        });
        tx.create(db.doc(`groupCodes/${code}`), { groupId: id });
      });
    } else if (data.operation === "join") {
      if (typeof data.code !== "string" || !/^[a-f0-9]{16}$/.test(data.code))
        throw Error("Invalid invitation code");
      await db.runTransaction(async (tx) => {
        const [tomb, invite] = await Promise.all([
          tx.get(db.doc(`accountTombstones/${uid}`)),
          tx.get(db.doc(`groupCodes/${data.code}`)),
        ]);
        if (tomb.exists || !invite.exists)
          throw new HttpsError("not-found", "Invitation unavailable");
        const g = await tx.get(db.doc(`groups/${invite.get("groupId")}`));
        if (!g.exists || g.get("memberUids").length >= 50)
          throw new HttpsError(
            "resource-exhausted",
            "Group unavailable or full",
          );
        tx.update(g.ref, { memberUids: FieldValue.arrayUnion(uid) });
      });
    } else if (data.operation === "leave") {
      if (
        typeof data.id !== "string" ||
        !/^[a-zA-Z0-9_-]{1,100}$/.test(data.id)
      )
        throw Error("Invalid group");
      const g = await db.doc(`groups/${data.id}`).get();
      if (!g.exists || !g.get("memberUids").includes(uid))
        throw new HttpsError("permission-denied", "Group membership required");
      if (g.get("owner") === uid)
        throw new HttpsError(
          "failed-precondition",
          "Group owners must close their group instead",
        );
      await g.ref.update({ memberUids: FieldValue.arrayRemove(uid) });
      await withdraw(uid, (c) => c.groupId === data.id);
    } else if (data.operation === "close") {
      if (
        typeof data.id !== "string" ||
        !/^[a-zA-Z0-9_-]{1,100}$/.test(data.id)
      )
        throw Error("Invalid group");
      const g = await db.doc(`groups/${data.id}`).get();
      if (!g.exists || g.get("owner") !== uid)
        throw new HttpsError(
          "permission-denied",
          "Only the group owner may close it",
        );
      await closeGroup(g);
    } else throw Error("Unknown group action");
    return summary(uid);
  }
  async function cheer(uid, data) {
    const c = await challengeAccess(uid, data.id);
    if (!c.memberUids.includes(uid))
      throw new HttpsError("permission-denied", "Join before sending cheers");
    if (!PHRASES.includes(data.phrase))
      throw Error("Choose a supportive phrase");
    if (
      data.target &&
      (!c.memberUids.includes(data.target) || (await blocked(uid, data.target)))
    )
      throw Error("Choose an available participant");
    await limited(uid, "cheer", 10);
    const me = (await ready(uid)).public;
    await db.runTransaction(async (tx) => {
      const tomb = await tx.get(db.doc(`accountTombstones/${uid}`));
      if (tomb.exists)
        throw new HttpsError("failed-precondition", "Account deletion pending");
      const msg = db.collection(`challenges/${data.id}/messages`).doc();
      tx.create(msg, {
        from: uid,
        alias: me.alias,
        target: data.target || "",
        phrase: data.phrase,
        at: Date.now(),
      });
      tx.update(db.doc(`challenges/${data.id}`), {
        revision: FieldValue.increment(1),
      });
    });
    return board(uid, data.id);
  }
  async function rest(uid) {
    await ready(uid);
    const today = Math.floor(Date.now() / p.DAY),
      start = p.period("weekly", Date.now()).start / p.DAY;
    await db.runTransaction(async (tx) => {
      const [block, days] = await Promise.all([
        tx.get(db.doc(`accountTombstones/${uid}`)),
        tx.get(
          user(uid)
            .collection("restDays")
            .where("epochDay", ">=", start)
            .where("epochDay", "<", start + 7),
        ),
      ]);
      if (block.exists) throw Error("Account deletion pending");
      if (days.docs.some((d) => d.get("epochDay") !== today))
        throw new HttpsError(
          "failed-precondition",
          "A rest day is already protected this UTC week; you can still rest without competing",
        );
      tx.set(db.doc(`users/${uid}/restDays/${today}`), { epochDay: today });
    });
    await refreshDay(uid, today);
    return summary(uid);
  }
  async function finalizeChallenge(id) {
    const snap = await db.doc(`challenges/${id}`).get();
    if (
      !snap.exists ||
      snap.get("finalized") ||
      Date.now() < snap.get("end") + p.DAY
    )
      return;
    const c = snap.data();
    const result = await board(c.owner, id, true);
    const winnerIds = new Set(p.winners(c, result.rows));
    const winners = result.rows.filter((r) => winnerIds.has(r.uid));
    await db.runTransaction(async (tx) => {
      const current = await tx.get(snap.ref),
        tombs = await Promise.all(
          winners.map((r) => tx.get(db.doc(`accountTombstones/${r.uid}`))),
        );
      if (current.get("finalized")) return;
      // Weight rankings never award XP or habit trophies; activity wins can earn a trophy.
      if (!p.WEIGHT.has(c.metric))
        winners.forEach((r, i) => {
          if (!tombs[i].exists)
            tx.set(db.doc(`users/${r.uid}/awards/${id}`), {
              title: c.title,
              at: Date.now(),
              kind: c.teamMode ? "activity_team_champion" : "activity_champion",
            });
        });
      tx.update(snap.ref, {
        finalized: true,
        revision: FieldValue.increment(1),
      });
    });
  }
  async function cleanup(uid) {
    await db.doc(`socialSignals/${uid}`).delete();
    const mine = await social(uid).get();
    if (mine.exists) {
      await db.doc(`handles/${mine.get("handle")}`).delete();
      await db
        .doc(`campusBoards/${mine.get("campusKey")}/members/${uid}`)
        .delete();
      await mine.ref.delete();
    }
    const edges = await db
      .collection("friendships")
      .where("memberUids", "array-contains", uid)
      .get();
    for (const d of edges.docs) {
      const other = d.get("memberUids").find((id) => id !== uid);
      await db.doc(`users/${other}/friends/${uid}`).delete();
      await d.ref.delete();
    }
    const challenges = await db
      .collection("challenges")
      .where("memberUids", "array-contains", uid)
      .get();
    for (const d of challenges.docs) {
      await db.doc(`challenges/${d.id}/members/${uid}`).delete();
      await d.ref.update({
        memberUids: FieldValue.arrayRemove(uid),
        revision: FieldValue.increment(1),
      });
    }
    const authored = await db
      .collection("challenges")
      .where("owner", "==", uid)
      .get();
    for (const d of authored.docs) await db.recursiveDelete(d.ref);
    const messages = await db
      .collectionGroup("messages")
      .where("from", "==", uid)
      .get();
    for (const d of messages.docs) await d.ref.delete();
    const groups = await db
      .collection("groups")
      .where("memberUids", "array-contains", uid)
      .get();
    for (const g of groups.docs) {
      if (g.get("owner") === uid) {
        await closeGroup(g);
      } else await g.ref.update({ memberUids: FieldValue.arrayRemove(uid) });
    }
    const leagues = await db
      .collection("leagues")
      .where("memberUids", "array-contains", uid)
      .get();
    for (const d of leagues.docs)
      await d.ref.update({
        memberUids: FieldValue.arrayRemove(uid),
        [`scores.${uid}`]: FieldValue.delete(),
      });
  }
  async function invalidate(uid) {
    await db.runTransaction(async (tx) => {
      const block = await tx.get(db.doc(`accountTombstones/${uid}`));
      if (!block.exists)
        tx.set(
          db.doc(`socialSignals/${uid}`),
          { revision: FieldValue.increment(1) },
          { merge: true },
        );
    });
    const found = await db
      .collection("challenges")
      .where("memberUids", "array-contains", uid)
      .get();
    for (const d of found.docs)
      await d.ref.update({ revision: FieldValue.increment(1) });
  }
  async function action(request) {
    const uid = identity(request);
    await active(uid);
    const data = request.data;
    if (
      ![
        "register",
        "summary",
        "createChallenge",
        "joinChallenge",
        "leaveChallenge",
        "board",
        "campus",
        "league",
        "friend",
        "phoneLookup",
        "group",
        "cheer",
        "rest",
        "unblock",
      ].includes(data.action)
    )
      throw new HttpsError("invalid-argument", "Unknown social action");
    await limited(
      uid,
      `action_${data.action}`,
      ["summary", "board", "campus", "league"].includes(data.action) ? 0.3 : 1,
    );
    try {
      switch (data.action) {
        case "register":
          return await register(uid, data);
        case "summary":
          return await summary(uid);
        case "createChallenge":
          return await createChallenge(uid, data);
        case "joinChallenge":
          return await joinChallenge(uid, data);
        case "leaveChallenge":
          return await leaveChallenge(uid, data.id);
        case "board":
          return await board(uid, data.id);
        case "campus":
          return await campusBoard(uid);
        case "league":
          return await league(uid);
        case "friend":
          return await friendAction(uid, data);
        case "phoneLookup":
          return await phoneLookup(uid, data);
        case "group":
          return await groupAction(uid, data);
        case "cheer":
          return await cheer(uid, data);
        case "rest":
          return await rest(uid);
        case "unblock":
          if (
            typeof data.target !== "string" ||
            !/^[a-zA-Z0-9_-]{1,128}$/.test(data.target)
          )
            throw Error("Invalid account");
          await db.doc(`users/${uid}/blocks/${data.target}`).delete();
          return await summary(uid);
        default:
          throw Error("Unknown social action");
      }
    } catch (error) {
      if (error instanceof HttpsError) throw error;
      throw new HttpsError(
        "invalid-argument",
        error.message || "Request could not be completed",
      );
    }
  }
  return {
    action,
    refreshDay,
    invalidate,
    cleanup,
    finalizeChallenge,
    finalizeLeague,
  };
};
