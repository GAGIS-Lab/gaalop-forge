const scriptTypes = [{ description: "GAALOPScript", accept: { "text/plain": [".gs", ".txt"] } }];
const unavailable = error => ["SecurityError", "NotSupportedError"].includes(error?.name);

export function suggestedFileName(functionName, extension) {
  const name = (functionName?.trim() || "script").replace(/[<>:"/\\|?*\u0000-\u001F]/g, "_");
  return `${name}.${extension}`;
}

export function normalizeSaveName(value, extension) {
  let name = value.trim();
  if (!name || /[<>:"/\\|?*\u0000-\u001F]/.test(name) || /[. ]$/.test(name)) {
    throw new Error("Enter a file name without path separators or reserved characters.");
  }
  if (!name.includes(".")) name += `.${extension}`;
  if (/^(con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\.|$)/i.test(name)) throw new Error("This file name is reserved. Choose another name.");
  return name;
}

export function canPickScriptFile(host = window) {
  return host.isSecureContext !== false && typeof host.showOpenFilePicker === "function";
}

export async function pickScriptFile(host = window) {
  if (!canPickScriptFile(host)) return { kind: "fallback" };
  try {
    const [handle] = await host.showOpenFilePicker({ id: "gaalop-script", multiple: false, types: scriptTypes });
    return handle ? { kind: "opened", file: await handle.getFile(), handle } : { kind: "cancelled" };
  } catch (error) {
    if (error.name === "AbortError") return { kind: "cancelled" };
    if (unavailable(error)) return { kind: "fallback" };
    throw error;
  }
}

export function downloadText(text, name, host = window) {
  const url = host.URL.createObjectURL(new Blob([text], { type: "text/plain;charset=utf-8" }));
  const link = host.document.createElement("a");
  link.href = url;
  link.download = name;
  host.document.body.appendChild(link);
  try { link.click(); }
  finally { link.remove(); setTimeout(() => host.URL.revokeObjectURL(url), 1000); }
}

async function writeText(handle, text) {
  const options = { mode: "readwrite" };
  if (handle.queryPermission && await handle.queryPermission(options) !== "granted") {
    if (!handle.requestPermission || await handle.requestPermission(options) !== "granted") {
      throw new Error("Write access was not granted. Use Save as to choose another file.");
    }
  }
  const writable = await handle.createWritable();
  try {
    await writable.write(text);
    await writable.close();
  } catch (error) {
    try { await writable.abort?.(); } catch { /* Keep the original write failure. */ }
    throw error;
  }
}

// Handles belong to the current page session, never browser storage. A binding
// separates script drafts and generated function/language/algebra identities.
export function createTextFileSession({ host = window, pickerId, description, chooseName, download = (text, name) => downloadText(text, name, host) } = {}) {
  let current = null, fileName = "", currentBinding = "", busy = false;
  return {
    attach(handle, name, binding = "script") { current = handle; fileName = name; currentBinding = binding; },
    reset() { current = null; fileName = ""; currentBinding = ""; },
    async save({ text, name, binding = "script", saveAs = false, downloadOnly = false }) {
      if (busy) throw new Error("A file operation is already in progress.");
      busy = true;
      try {
        const same = binding === currentBinding;
        const suggested = same && fileName ? fileName : name;
        const extension = suggested.split(".").pop();
        let handle = !downloadOnly && !saveAs && same ? current : null;
        if (!downloadOnly && !handle && host.isSecureContext !== false && typeof host.showSaveFilePicker === "function") {
          try {
            handle = await host.showSaveFilePicker({
              id: pickerId, suggestedName: suggested,
              types: [{ description, accept: { "text/plain": [`.${extension}`] } }]
            });
          } catch (error) {
            if (error.name === "AbortError") return { kind: "cancelled" };
            if (!unavailable(error)) throw error;
          }
        }
        if (handle) {
          await writeText(handle, text);
          current = handle; fileName = handle.name; currentBinding = binding;
          return { kind: "saved", name: fileName };
        }
        const selected = saveAs && chooseName ? await chooseName(suggested, extension) : suggested;
        if (selected === null) return { kind: "cancelled" };
        const targetName = normalizeSaveName(selected, extension);
        await download(text, targetName);
        // A downloaded Save as must not leave Save pointing at an older file.
        if (!downloadOnly) { current = null; fileName = targetName; currentBinding = binding; }
        return { kind: "downloaded", name: targetName, ...(downloadOnly ? { copy: true } : {}) };
      } finally { busy = false; }
    }
  };
}
