const { spawnSync } = require("node:child_process");
for (const file of [
  "test/emulator/tracking.test.js",
  "test/emulator/social.test.js",
  "test/emulator/notifications.test.js",
]) {
  const result = spawnSync(process.execPath, [file], {
    stdio: "inherit",
    env: process.env,
  });
  if (result.error) throw result.error;
  if (result.status !== 0) process.exit(result.status ?? 1);
}
