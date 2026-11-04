const { test } = require("node:test");
const assert = require("node:assert/strict");
const p = require("../notifications/policy");
const service = require("../notifications/service")(
  { doc() { throw Error("Unexpected Firestore RPC"); } },
  { sendEachForMulticast() { throw Error("Unexpected external push"); } },
);
test("device keys are deterministic hashes without the raw token", () => {
  const token = "abc_123:mock-token-for-tests";
  assert.equal(p.key(token).length, 64);
  assert.equal(p.key(token), p.key(token));
  assert.notEqual(p.key(token), p.key(token + "2"));
  assert.equal(p.token(token), token);
});
test("device registration rejects oversized or malformed tokens", () => {
  for (const token of [null, "short", "a".repeat(4097), "token with spaces".repeat(4), "https://fake/token/1234567890"])
    assert.throws(() => p.token(token));
});
test("canonical inbox records contain no health or message content", () => {
  const row = p.entry("cheer", "challenge_12", 123);
  assert.deepEqual(row, { kind: "cheer", target: "challenge_12", createdAt: 123, read: false });
  assert.throws(() => p.entry("body_weight", "x", 123));
  assert.throws(() => p.entry("cheer", "https://untrusted/", 123));
  assert.throws(() => p.entry("cheer", "x", NaN));
});
test("FCM messages are data only with bounded lifetime and canonical inbox identity", () => {
  const message = p.payload("user", p.key("event"), "trophy");
  assert.deepEqual(Object.keys(message.data).sort(), ["id", "kind", "uid"]);
  assert.equal(message.notification, undefined);
  assert.equal(message.android.notification, undefined);
  assert.equal(message.android.ttl, p.DAY);
  assert.equal(message.android.priority, "normal");
});
test("push rejects unknown kinds and caller-provided URLs", () => {
  assert.throws(() => p.payload("user", "https://example.test", "cheer"));
  assert.throws(() => p.payload("user", p.key("event"), "arbitrary"));
});
test("notification IDs deduplicate event retries and separate event kinds", () => {
  assert.equal(p.key("cheer:event-1"), p.key("cheer:event-1"));
  assert.notEqual(p.key("cheer:event-1"), p.key("trophy:event-1"));
});
for (const [name, request, code] of [
  ["anonymous", { data: { uid: "u", action: "register" } }, "unauthenticated"],
  ["changed account", { auth: { uid: "u", token: { email_verified: true } }, data: { uid: "v", action: "register" } }, "permission-denied"],
  ["unverified", { auth: { uid: "u", token: {} }, data: { uid: "u", action: "register" } }, "permission-denied"],
  ["unknown action", { auth: { uid: "u", token: { email_verified: true } }, data: { uid: "u", action: "send" } }, "invalid-argument"],
  ["invalid token", { auth: { uid: "u", token: { email_verified: true } }, data: { uid: "u", action: "register", token: "short" } }, "invalid-argument"],
  ["invalid unregister key", { auth: { uid: "u", token: { email_verified: true } }, data: { uid: "u", action: "unregister", key: "../other" } }, "invalid-argument"],
]) test(`${name} fails before any database or messaging RPC`, async () => {
  await assert.rejects(service.action(request), e => e.code === code);
});
