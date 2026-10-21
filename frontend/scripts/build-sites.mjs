import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { resolve } from "node:path";

const root = fileURLToPath(new URL("../", import.meta.url));
for (const [workspace, directory] of [["traditional", "gaalop"], ["quantum", "quantumgaalop"]]) {
  const result = spawnSync(process.execPath, [resolve(root, "node_modules/vite/bin/vite.js"), "build", "--outDir", resolve(root, `dist-sites/${directory}`), "--emptyOutDir"], {
    cwd: root,
    stdio: "inherit",
    env: { ...process.env, VITE_WORKSPACE_ENTRY: workspace, VITE_API_BASE_URL: "" }
  });
  if (result.status !== 0) process.exit(result.status || 1);
}
