"use strict";
const DAY = 86400000;
const finite = (n, min, max) => typeof n === "number" && Number.isFinite(n) && n >= min && n <= max;
function weight(data, now) {
  if (!data || !Number.isInteger(data.epochDay) || data.epochDay > Math.floor(now / DAY) || data.epochDay < 10957 ||
      !finite(data.weightKg, 35, 350) || !finite(data.createdAt, data.epochDay * DAY, Math.min((data.epochDay + 1) * DAY - 1, now + 60000))) throw new Error("Invalid weigh-in or outside the supported calendar range");
  return {epochDay: data.epochDay, weightKg: data.weightKg, createdAt: data.createdAt};
}
function session(data, now) {
  if (!data || typeof data.id !== "string" || !/^[a-zA-Z0-9-]{1,80}$/.test(data.id) ||
      !finite(data.startedAt, Date.UTC(2000,0,1), now) || !Number.isInteger(data.durationSeconds) || !finite(data.durationSeconds, 1, 1200) ||
      !finite(data.calories, 0, data.durationSeconds * 100 / 60) || !["guided", "gps-walk", "gps-run", "camera"].includes(data.source) ||
      !finite(data.distanceMeters, 0, data.durationSeconds * 12) || !Number.isInteger(data.reps) || !finite(data.reps, 0, data.durationSeconds * 4)) throw new Error("Invalid workout session");
  if (!data.source.startsWith("gps-") && data.distanceMeters !== 0) throw new Error("Only GPS sessions have distance");
  if (data.source !== "camera" && data.reps !== 0) throw new Error("Only camera sessions have reps");
  return {id: data.id, startedAt: data.startedAt, durationSeconds: data.durationSeconds, calories: data.calories,
    source: data.source, distanceMeters: data.distanceMeters, reps: data.reps, verification: "self_reported"};
}
function unusual(previous, current) {
  if (!previous || current.epochDay <= previous.epochDay) return false;
  return (1 - Math.pow(current.weightKg / previous.weightKg, 1 / (current.epochDay - previous.epochDay))) * 100 > 2 + 1e-9;
}
module.exports = {DAY, weight, session, unusual};
