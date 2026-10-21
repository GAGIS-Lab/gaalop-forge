import assert from "node:assert/strict";
import { mkdtemp, readFile, writeFile, unlink, rmdir } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { createTextFileSession, normalizeSaveName, pickScriptFile, suggestedFileName } from "../src/fileAccess.js";
import { parseScriptFile, serializeScriptFile } from "../src/scriptFile.js";

const namedError = name => Object.assign(new Error(name), { name });
function file(name, initial = "", options = {}) {
  const stats = { writes: 0, closes: 0, aborts: 0, requests: 0, text: initial };
  return {
    name, stats,
    async getFile() { return { name, size: new TextEncoder().encode(stats.text).length, text: async () => stats.text }; },
    async queryPermission() { return options.permission || "granted"; },
    async requestPermission() { stats.requests++; return options.request || "granted"; },
    async createWritable() {
      let next;
      return {
        async write(text) { stats.writes++; if (options.failWrite) throw namedError("NotReadableError"); next = text; },
        async close() { stats.closes++; if (options.failClose) throw namedError("NotAllowedError"); stats.text = next; await options.commit?.(next); },
        async abort() { stats.aborts++; }
      };
    }
  };
}
let checks = 0;
async function check(name, test) { await test(); checks++; console.log("PASS", name); }
const session = (host, extra = {}) => createTextFileSession({ host, pickerId: "gaalop-script", description: "GAALOPScript", ...extra });

await check("Open reads the selected handle; Save writes UTF-8 sections back without another picker", async () => {
  const directory = await mkdtemp(path.join(tmpdir(), "gaalop-open-save-"));
  assert.ok(path.resolve(directory).startsWith(path.resolve(tmpdir()) + path.sep));
  const target = path.join(directory, "game.gs");
  try {
    await writeFile(target, "?x=1;", "utf8");
    const handle = file("game.gs", await readFile(target, "utf8"), { permission: "prompt", commit: text => writeFile(target, text, "utf8") });
    let opens = 0;
    const host = { showOpenFilePicker: async options => { opens++; assert.equal(options.multiple, false); return [handle]; }, showSaveFilePicker: () => assert.fail("Save must use the opened handle") };
    const selected = await pickScriptFile(host);
    const script = parseScriptFile(await selected.file.text());
    script.optimizeCode = "// 中文\n?x=cos(theta);";
    script.variableAssignments = "theta=0.5;";
    const current = session(host);
    current.attach(selected.handle, selected.file.name);
    assert.deepEqual(await current.save({ text: serializeScriptFile(script), name: "game.gs" }), { kind: "saved", name: "game.gs" });
    assert.deepEqual(parseScriptFile(await readFile(target, "utf8")), script);
    assert.equal(handle.stats.requests, 1);
    assert.equal(opens, 1);
  } finally { await unlink(target).catch(() => {}); await rmdir(directory); }
});

await check("First Save chooses a name; later Save reuses it; Save as selects a separate target", async () => {
  const first = file("one.gs"), second = file("two.gs");
  const picked = [];
  const current = session({ showSaveFilePicker: async options => { picked.push(options); return picked.length === 1 ? first : second; } });
  await current.save({ text: "1", name: "one.gs" });
  await current.save({ text: "2", name: "renamed_function.gs" });
  assert.equal(picked.length, 1);
  assert.equal(first.stats.text, "2");
  await current.save({ text: "3", name: "renamed_function.gs", saveAs: true });
  assert.equal(picked[1].suggestedName, "one.gs");
  assert.equal(first.stats.text, "2");
  assert.equal(second.stats.text, "3");
});

await check("Cancelling Open never requests fallback or a new file", async () => {
  assert.deepEqual(await pickScriptFile({ showOpenFilePicker: async () => { throw namedError("AbortError"); } }), { kind: "cancelled" });
});

await check("Cancelling Save as preserves the current handle and its content", async () => {
  const original = file("game.gs", "original");
  const current = session({ showSaveFilePicker: async () => { throw namedError("AbortError"); } }, { download: () => assert.fail("No download on cancellation") });
  current.attach(original, original.name);
  assert.deepEqual(await current.save({ text: "changed", name: "game.gs", saveAs: true }), { kind: "cancelled" });
  assert.equal(original.stats.text, "original");
  await current.save({ text: "accepted", name: "game.gs" });
  assert.equal(original.stats.text, "accepted");
});

await check("Permission denial does not silently download, write, or claim success", async () => {
  const original = file("game.gs", "original", { permission: "denied", request: "denied" });
  const current = session({}, { download: () => assert.fail("Permission denial is not download fallback") });
  current.attach(original, original.name);
  await assert.rejects(current.save({ text: "changed", name: "game.gs" }), /Write access/);
  assert.equal(original.stats.text, "original");
  assert.equal(original.stats.writes, 0);
});

await check("Write and close failures abort the stream and preserve a previous handle", async () => {
  for (const failure of ["failWrite", "failClose"]) {
    const original = file("original.gs", "original");
    const failed = file("failed.gs", "unchanged", { [failure]: true });
    const current = session({ showSaveFilePicker: async () => failed });
    current.attach(original, original.name);
    await assert.rejects(current.save({ text: "changed", name: "original.gs", saveAs: true }));
    assert.equal(failed.stats.text, "unchanged");
    assert.equal(failed.stats.aborts, 1);
    await current.save({ text: "still original target", name: "original.gs" });
    assert.equal(original.stats.text, "still original target");
  }
});

await check("Unsupported and insecure Open requests use the file input fallback", async () => {
  assert.equal((await pickScriptFile({})).kind, "fallback");
  assert.equal((await pickScriptFile({ isSecureContext: false, showOpenFilePicker: () => assert.fail() })).kind, "fallback");
  for (const name of ["NotSupportedError", "SecurityError"]) assert.equal((await pickScriptFile({ showOpenFilePicker: async () => { throw namedError(name); } })).kind, "fallback");
});

await check("Save downloads current content; fallback Save as chooses and retains the new file name", async () => {
  const downloads = [];
  const current = session({}, { chooseName: async (name, ext) => { assert.equal(name, "game.gs"); assert.equal(ext, "gs"); return "新脚本"; }, download: (text, name) => downloads.push({ text, name }) });
  current.attach(null, "game.gs");
  await current.save({ text: "one", name: "game.gs" });
  await current.save({ text: "two", name: "game.gs", saveAs: true });
  await current.save({ text: "three", name: "game.gs" });
  assert.deepEqual(downloads, [{ text: "one", name: "game.gs" }, { text: "two", name: "新脚本.gs" }, { text: "three", name: "新脚本.gs" }]);
});

await check("Cancelling the fallback name dialog produces no download", async () => {
  const current = session({}, { chooseName: async () => null, download: () => assert.fail() });
  assert.deepEqual(await current.save({ text: "content", name: "game.gs", saveAs: true }), { kind: "cancelled" });
});

await check("Save as fallback releases the old handle", async () => {
  const original = file("old.gs", "original");
  const names = [];
  const current = session({ showSaveFilePicker: async () => { throw namedError("SecurityError"); } }, { chooseName: async () => "new.gs", download: (text, name) => names.push(name) });
  current.attach(original, original.name);
  await current.save({ text: "new copy", name: "old.gs", saveAs: true });
  await current.save({ text: "next copy", name: "old.gs" });
  assert.equal(original.stats.text, "original");
  assert.deepEqual(names, ["new.gs", "new.gs"]);
});

await check("A new function, language or algebra binding never reuses an unrelated generated file", async () => {
  const handles = [file("game.py"), file("game.m"), file("other.m"), file("other.m")];
  const options = [];
  const current = createTextFileSession({ host: { showSaveFilePicker: async value => { options.push(value); return handles[options.length - 1]; } }, pickerId: "gaalop-output", description: "Generated code" });
  for (const [name, binding] of [["game.py", "QCA:game.py"], ["game.m", "QCA:game.m"], ["other.m", "QCA:other.m"], ["other.m", "QRA:other.m"]]) await current.save({ text: binding, name, binding });
  assert.equal(options.length, 4);
  for (const option of options) { assert.equal(option.id, "gaalop-output"); assert.equal(Object.hasOwn(option, "startIn"), false); }
  assert.equal(handles[0].stats.text, "QCA:game.py");
});

await check("Reset after another imported script forgets the old write target", async () => {
  const original = file("old.gs", "original"), next = file("next.gs");
  const current = session({ showSaveFilePicker: async () => next });
  current.attach(original, original.name);
  current.reset();
  await current.save({ text: "new script", name: "next.gs" });
  assert.equal(original.stats.text, "original");
  assert.equal(next.stats.text, "new script");
});

await check("Save never overlaps another write on the same session", async () => {
  let resolve;
  const waiting = new Promise(done => { resolve = done; });
  const current = session({ showSaveFilePicker: async () => { await waiting; return file("game.gs"); } });
  const first = current.save({ text: "one", name: "game.gs" });
  await assert.rejects(current.save({ text: "two", name: "game.gs" }), /already in progress/);
  resolve();
  await first;
});

await check("Default names retain the function and language extension; invalid paths are rejected", async () => {
  assert.equal(suggestedFileName(" quantum_game ", "m"), "quantum_game.m");
  assert.equal(suggestedFileName("game", "py"), "game.py");
  assert.equal(normalizeSaveName("game", "gs"), "game.gs");
  for (const name of ["", "../game.gs", "C:\\game.gs", "game/other.gs", "game.", "CON.gs"]) assert.throws(() => normalizeSaveName(name, "gs"));
});

await check("Saving a downloaded copy preserves the authorized write target", async () => {
  const original = file("game.gs", "original");
  const downloads = [];
  const current = session({ showSaveFilePicker: () => assert.fail("Copy must bypass native file access") }, {
    chooseName: async () => "copy.gs", download: (text, name) => downloads.push({ text, name })
  });
  current.attach(original, original.name);
  assert.deepEqual(await current.save({ text: "copy content", name: "game.gs", saveAs: true, downloadOnly: true }), { kind: "downloaded", name: "copy.gs", copy: true });
  assert.equal(original.stats.text, "original");
  await current.save({ text: "saved original", name: "game.gs" });
  assert.equal(original.stats.text, "saved original");
  assert.deepEqual(downloads, [{ text: "copy content", name: "copy.gs" }]);
});

console.log(`${checks} Open/Save/Save as file lifecycle scenarios passed.`);
