import assert from "node:assert/strict";
import { workspaceUrl } from "../src/workspaceNavigation.js";

for (const [source, base] of [
  ["http://localhost:5174/", "http://localhost:5174/"],
  ["http://localhost:5174/index.html?workspace=quantum", "http://localhost:5174/"],
  ["http://localhost:5174/gaalop/", "http://localhost:5174/"],
  ["http://localhost:5174/quantumgaalop/", "http://localhost:5174/"],
  ["http://localhost:5174/gaalop", "http://localhost:5174/"],
  ["http://localhost:5174/quantumgaalop", "http://localhost:5174/"],
  ["https://site/ga-application/gaalop/index.html", "https://site/ga-application/"],
  ["https://site/ga-application/quantumgaalop/", "https://site/ga-application/"]
]) {
  assert.equal(workspaceUrl("traditional", source), base + "gaalop/");
  assert.equal(workspaceUrl("quantum", source), base + "quantumgaalop/");
}

const imported = new URL("https://site/ga-application/gaalop/?workspace=quantum&theme=light#old");
const sourceUrl = "https://hub.example/api/v1/examples/game/workspace?revision=3";
imported.searchParams.set("workspaceUrl", sourceUrl);
const automatic = new URL(workspaceUrl("quantum", imported.href, true));
assert.equal(automatic.pathname, "/ga-application/quantumgaalop/");
assert.equal(automatic.searchParams.get("workspaceUrl"), sourceUrl);
assert.equal(automatic.searchParams.get("theme"), "light");
assert.equal(automatic.searchParams.has("workspace"), false);
assert.equal(automatic.hash, "");
const manual = new URL(workspaceUrl("traditional", automatic.href));
assert.equal(manual.pathname, "/ga-application/gaalop/");
assert.equal(manual.searchParams.has("workspaceUrl"), false);
assert.equal(manual.searchParams.get("theme"), "light");
console.log("Independent page URLs, legacy links, nested deployment paths and Hub import navigation passed.");
