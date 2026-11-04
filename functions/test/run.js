const { spawnSync } = require("node:child_process");
for (const file of ["test/policy.test.js", "test/social.test.js", "test/notifications.test.js"]) {
  const result = spawnSync(process.execPath, [file], {
    stdio: "inherit",
    env: process.env,
  });
  if (result.error) throw result.error;
  if (result.status !== 0) process.exit(result.status ?? 1);
}
