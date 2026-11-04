"use strict";
const DAY = 86400000;
const METRICS = ["minutes", "calories", "percent", "kilograms"];
const WEIGHT = new Set(["percent", "kilograms"]);
function handle(value) {
  const result = typeof value === "string" ? value.trim().toLowerCase() : "";
  if (!/^[a-z][a-z0-9_]{2,19}$/.test(result))
    throw Error(
      "Use 3–20 letters, numbers or underscores, starting with a letter",
    );
  return result;
}
function text(value, max = 60) {
  if (
    typeof value !== "string" ||
    !value.trim() ||
    value.trim().length > max ||
    /[\u0000-\u001f]/.test(value)
  )
    throw Error("Invalid text");
  return value.trim();
}
function campus(value) {
  return text(value, 120).normalize("NFKC").toLowerCase().replace(/\s+/g, " ");
}
function period(kind, now, next = false) {
  const date = new Date(Math.floor(now / DAY) * DAY);
  if (kind === "weekly")
    date.setUTCDate(date.getUTCDate() - ((date.getUTCDay() + 6) % 7));
  else if (kind === "monthly") date.setUTCDate(1);
  else if (kind !== "daily") throw Error("Choose daily, weekly or monthly");
  if (next) {
    if (kind === "daily") date.setUTCDate(date.getUTCDate() + 1);
    else if (kind === "weekly") date.setUTCDate(date.getUTCDate() + 7);
    else date.setUTCMonth(date.getUTCMonth() + 1);
  }
  const start = date.getTime();
  if (kind === "daily") date.setUTCDate(date.getUTCDate() + 1);
  else if (kind === "weekly") date.setUTCDate(date.getUTCDate() + 7);
  else date.setUTCMonth(date.getUTCMonth() + 1);
  return { start, end: date.getTime() };
}
function daySummary(sessions) {
  const ordered = [...sessions].sort(
    (a, b) =>
      a.startedAt - b.startedAt || String(a.id).localeCompare(String(b.id)),
  );
  let seconds = 0,
    calories = 0,
    eligibleSessions = 0,
    earlyBird = false;
  const ids = new Set();
  for (const s of ordered) {
    if (ids.has(s.id)) continue;
    ids.add(s.id);
    if (!Number.isFinite(s.durationSeconds) || s.durationSeconds <= 0) continue;
    const applied = Math.min(3600 - seconds, s.durationSeconds);
    if (applied <= 0) break;
    calories +=
      (Math.min(Math.max(0, s.calories || 0), (s.durationSeconds * 100) / 60) *
        applied) /
      s.durationSeconds;
    seconds += applied;
    if (s.durationSeconds >= 120) {
      eligibleSessions++;
      const hour = new Date(s.startedAt).getUTCHours();
      if (hour >= 5 && hour < 8) earlyBird = true;
    }
  }
  const minutes = Math.floor(seconds / 60);
  return {
    seconds,
    minutes,
    calories,
    eligibleSessions,
    earlyBird,
    xp: minutes * 2 + (seconds >= 120 ? 10 : 0),
  };
}
function streak(days, today) {
  const map = new Map(days.map((d) => [d.epochDay, d]));
  let cursor = today,
    usedRest = 0,
    count = 0;
  const active = (day) => map.get(day)?.seconds >= 120;
  if (!active(cursor) && !map.get(cursor)?.rest) cursor--;
  for (let i = 0; i < 400; i++, cursor--) {
    const d = map.get(cursor);
    if (!d) break;
    if (d.seconds >= 120) {
      count++;
      continue;
    }
    if (d.rest) {
      const week = period("weekly", cursor * DAY).start;
      if (usedRest !== week + 1) {
        usedRest = week + 1;
        count++;
        continue;
      }
    }
    break;
  }
  return { days: count, capped: count === 400 };
}
function activityScore(challenge, member, sessions, now) {
  const start = Math.max(challenge.start, member.joinedAt),
    end = Math.min(challenge.end, now);
  const days = new Map();
  for (const s of sessions) {
    const received =
      s.receivedAt?.toMillis?.() ?? s.receivedAtMillis ?? Infinity;
    if (
      s.startedAt < start ||
      s.startedAt >= end ||
      received > challenge.end + DAY
    )
      continue;
    const day = Math.floor(s.startedAt / DAY);
    if (!days.has(day)) days.set(day, []);
    days.get(day).push(s);
  }
  return [...days.values()].reduce((sum, items) => {
    const d = daySummary(items);
    return sum + (challenge.metric === "minutes" ? d.seconds / 60 : d.calories);
  }, 0);
}
function weightScore(challenge, member, weights, preferences, now) {
  if (
    preferences.activityOnly ||
    (challenge.metric === "kilograms" && preferences.percentageRanking)
  )
    return {
      score: null,
      reason: "Your privacy settings exclude this metric.",
    };
  if (!member.weightConsent || member.joinedAt >= challenge.start)
    return {
      score: null,
      reason: "Weight enrollment closed before this period began.",
    };
  const baseDay = Math.floor(challenge.start / DAY) - 1,
    endDay = Math.floor((Math.min(now, challenge.end) - 1) / DAY);
  const received = (w) =>
    w.receivedAt?.toMillis?.() ?? w.receivedAtMillis ?? Infinity;
  const baseline = weights.find(
    (w) => w.epochDay === baseDay && received(w) < challenge.start,
  );
  if (!baseline)
    return {
      score: null,
      reason:
        "A baseline from the previous UTC day must sync before the start.",
    };
  const periodWeights = weights
    .filter(
      (w) =>
        w.epochDay > baseDay &&
        w.epochDay <= endDay &&
        received(w) <= challenge.end + DAY,
    )
    .sort((a, b) => a.epochDay - b.epochDay);
  if (!periodWeights.length)
    return { score: null, reason: "No eligible weigh-in in this period yet." };
  if (periodWeights.some((w) => w.flagged))
    return {
      score: null,
      reason:
        "An unusual change was flagged; no weight-loss reward is awarded.",
    };
  if (!Number.isFinite(preferences.heightCm) || preferences.heightCm < 120 || preferences.heightCm > 230 || !Number.isFinite(preferences.goalKg) || preferences.goalKg / Math.pow(preferences.heightCm / 100, 2) < 18.5) return { score: null, reason: "Review your health profile; activity-only competition is always available." };
  const heightSquared = Math.pow(preferences.heightCm / 100, 2);
  if (baseline.weightKg / heightSquared < 18.5 || periodWeights.at(-1).weightKg / heightSquared < 18.5) return { score: null, reason: "Choose activity-only competition for this profile. Weight-loss ranking is unavailable below the app's BMI threshold." };
  const latest = periodWeights.at(-1),
    loss = baseline.weightKg - latest.weightKg,
    days = latest.epochDay - baseDay;
  if (!Number.isFinite(loss) || baseline.weightKg <= 0 || days <= 0)
    return { score: null, reason: "Invalid baseline." };
  if (loss > days / 7 + 1e-8)
    return {
      score: null,
      reason:
        "Change exceeds the suggested ceiling of 1 kg per week; focus on healthy habits.",
    };
  return {
    score: Math.max(
      0,
      challenge.metric === "percent" ? (loss / baseline.weightKg) * 100 : loss,
    ),
    reason: loss < 0 ? "Maintaining healthy habits still counts." : "",
  };
}
function rank(rows) {
  const sorted = rows
    .map((r) => ({ ...r, score: Number.isFinite(r.score) ? r.score : null }))
    .sort(
      (a, b) =>
        (b.score == null ? -Infinity : Math.round(b.score * 1e6)) -
          (a.score == null ? -Infinity : Math.round(a.score * 1e6)) ||
        a.uid.localeCompare(b.uid),
    );
  let last = null,
    rank = 0;
  return sorted.map((row, index) => {
    if (row.score == null) return { ...row, rank: null };
    const rounded = Math.round(row.score * 1e6) / 1e6;
    if (last !== rounded) {
      rank = index + 1;
      last = rounded;
    }
    return { ...row, rank };
  });
}
function teams(rows) {
  const by = new Map();
  for (const row of rows) {
    if (!row.team) continue;
    if (!by.has(row.team)) by.set(row.team, []);
    by.get(row.team).push(row);
  }
  return rank(
    [...by].map(([name, members]) => ({
      uid: name,
      alias: name,
      team: name,
      count: members.length,
      score: members.some((r) => r.score != null)
        ? members.reduce((n, r) => n + (r.score ?? 0), 0) / members.length
        : null,
    })),
  );
}
function leagueTier(tier, position, count, score) {
  if (count < 5 || !position) return tier;
  const move = Math.max(1, Math.floor(count * 0.2));
  if (position <= move && score > 0) return Math.min(3, tier + 1);
  if (position > count - move) return Math.max(0, tier - 1);
  return tier;
}
function badges(stats, currentStreak) {
  return [
    stats.seconds >= 120 && "first_move",
    stats.eligibleSessions >= 10 && "ten_sessions",
    stats.seconds >= 6000 && "hundred_minutes",
    stats.earlyBird && "early_bird",
    currentStreak >= 7 && "seven_day_streak",
  ].filter(Boolean);
}
function winners(challenge, rows) {
  if (WEIGHT.has(challenge.metric)) return [];
  if (challenge.teamMode) {
    const winningTeams=new Set(teams(rows).filter(r=>r.rank===1&&r.score>0).map(r=>r.uid));
    return rows.filter(r=>r.score>0&&winningTeams.has(r.team)).map(r=>r.uid);
  }
  return rank(rows).filter(r=>r.rank===1&&r.score>0).map(r=>r.uid);
}
module.exports = {
  DAY,
  METRICS,
  WEIGHT,
  handle,
  text,
  campus,
  period,
  daySummary,
  streak,
  activityScore,
  weightScore,
  rank,
  teams,
  leagueTier,
  badges,
  winners,
};
