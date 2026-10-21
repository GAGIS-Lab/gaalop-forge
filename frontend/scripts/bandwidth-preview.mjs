// Local QA only: serve the production build with a shared 256 KiB/s budget.
// pnpm exec node scripts/bandwidth-preview.mjs
// Optional: PORT=5188 EDITOR_DELAY_MS=15000 FAIL_EDITOR_ONCE=1
import { createServer, request } from "node:http";
import { readFile } from "node:fs/promises";
import { resolve, extname, sep } from "node:path";
import { fileURLToPath } from "node:url";
import { gzipSync } from "node:zlib";

const root = fileURLToPath(new URL("../dist/", import.meta.url));
const types = { ".html": "text/html", ".js": "application/javascript", ".css": "text/css", ".png": "image/png" };
const queue = [];
let failEditor = process.env.FAIL_EDITOR_ONCE === "1";
setInterval(() => {
  let budget = Math.floor(256 * 1024 / 20);
  while (budget > 0 && queue.length) {
    const item = queue[0];
    if (item.res.destroyed) { queue.shift(); continue; }
    const count = Math.min(budget, item.data.length - item.offset);
    item.res.write(item.data.subarray(item.offset, item.offset + count));
    item.offset += count;
    budget -= count;
    if (item.offset === item.data.length) { item.res.end(); queue.shift(); }
  }
}, 50).unref();

createServer(async (req, res) => {
  const path = new URL(req.url, "http://localhost").pathname;
  const apiPath = path.match(/^\/(?:gaalop|quantumgaalop)(\/api\/.*)$/);
  if (apiPath) {
    const { origin, ...headers } = req.headers;
    const proxy = request({ hostname: "127.0.0.1", port: 18089, path: `/gaalop${apiPath[1]}${new URL(req.url, "http://localhost").search}`, method: req.method,
      headers }, (upstream) => {
      res.writeHead(upstream.statusCode, upstream.headers);
      upstream.pipe(res);
    });
    proxy.on("error", () => { res.writeHead(502); res.end(); });
    req.pipe(proxy);
    return;
  }
  if (["/gaalop", "/quantumgaalop"].includes(path)) {
    res.writeHead(301, { Location: `${path}/${new URL(req.url, "http://localhost").search}` });
    res.end(); return;
  }
  if (path !== "/" && !/^\/(gaalop|quantumgaalop|assets)\//.test(path)) { res.writeHead(404); res.end(); return; }
  try {
    const relative = decodeURIComponent(path.slice(1));
    const file = resolve(root, relative + (path.endsWith("/") ? "index.html" : ""));
    if (!file.startsWith(resolve(root) + sep)) { res.writeHead(403); res.end(); return; }
    if (/editor\.api-.*\.js$/.test(file)) {
      if (failEditor) { failEditor = false; res.writeHead(503); res.end(); return; }
      await new Promise((done) => setTimeout(done, Number(process.env.EDITOR_DELAY_MS || 0)));
    }
    const raw = await readFile(file);
    const zipped = /gzip/.test(req.headers["accept-encoding"] || "") && /\.(js|css|html)$/.test(file);
    const data = zipped ? gzipSync(raw, { level: 6 }) : raw;
    res.writeHead(200, { "Content-Type": types[extname(file)] || "application/octet-stream",
      "Content-Length": data.length, "Cache-Control": "no-store",
      ...(zipped ? { "Content-Encoding": "gzip", Vary: "Accept-Encoding" } : {}) });
    queue.push({ res, data, offset: 0 });
    console.log(`${path}: ${data.length} bytes${zipped ? " (gzip)" : ""}`);
  } catch { res.writeHead(404); res.end(); }
}).listen(Number(process.env.PORT || 5188), "127.0.0.1", () => {
  console.log(`Bandwidth preview: http://127.0.0.1:${process.env.PORT || 5188}/gaalop/`);
});
