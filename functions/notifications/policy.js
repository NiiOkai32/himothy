"use strict";
const { createHash } = require("node:crypto");
const DAY = 86400000;
const KINDS = new Set(["friend_request", "friend_accept", "cheer", "trophy", "league", "challenge_join"]);
function key(value) { return createHash("sha256").update(value).digest("hex"); }
function token(value) {
  if (typeof value !== "string" || !/^[A-Za-z0-9_:.-]{20,4096}$/.test(value)) throw Error("Invalid device token");
  return value;
}
function target(value) {
  if (typeof value !== "string" || !/^[A-Za-z0-9_-]{0,100}$/.test(value)) throw Error("Invalid notification target");
  return value;
}
function entry(kind, route, now) {
  if (!KINDS.has(kind) || !Number.isSafeInteger(now) || now < 0) throw Error("Invalid notification");
  return { kind, target: target(route), createdAt: now, read: false };
}
function payload(uid, id, kind) {
  if (!uid || !/^[a-f0-9]{64}$/.test(id) || !KINDS.has(kind)) throw Error("Invalid notification payload");
  // Data only: Android verifies the canonical private inbox record before displaying it.
  return { data: { uid, id, kind }, android: { priority: "normal", ttl: DAY, collapseKey: id } };
}
module.exports = { DAY, KINDS, key, token, target, entry, payload };
