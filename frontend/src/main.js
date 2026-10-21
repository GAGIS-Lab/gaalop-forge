import { workspaceUrl } from "./workspaceNavigation.js";

// Compatibility entry for bookmarks created before the two independent pages.
const url = new URL(window.location.href);
const workspace = url.searchParams.get("workspace") === "quantum" ? "quantum" : "traditional";
window.location.replace(workspaceUrl(workspace, url.href, true));
