export function workspaceUrl(workspace, href, preserveImport = false) {
  const url = new URL(href);
  const directory = new URL("./", url);
  const applicationRoot = /\/(gaalop|quantumgaalop)\/$/.test(directory.pathname)
    ? new URL("../", directory)
    : directory;
  url.pathname = applicationRoot.pathname + (workspace === "quantum" ? "quantumgaalop/" : "gaalop/");
  url.searchParams.delete("workspace");
  if (!preserveImport) url.searchParams.delete("workspaceUrl");
  url.hash = "";
  return url.href;
}
