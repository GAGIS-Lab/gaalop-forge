import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import { fileURLToPath } from "node:url";
import { resolve } from "node:path";

const projectRoot = fileURLToPath(new URL("./", import.meta.url));
const apiTarget = process.env.VITE_API_TARGET || "http://localhost:8080";
const standalone = process.env.VITE_WORKSPACE_ENTRY;
if (standalone && !["traditional", "quantum"].includes(standalone)) {
  throw new Error("VITE_WORKSPACE_ENTRY must be traditional or quantum.");
}
const siteRoot = standalone
  ? resolve(projectRoot, standalone === "quantum" ? "quantumgaalop" : "gaalop")
  : projectRoot;
const apiProxy = Object.fromEntries(["", "/gaalop", "/quantumgaalop"].map(prefix => [
  `${prefix}/api`, {
    target: apiTarget,
    changeOrigin: true,
    rewrite: path => `/gaalop${path.slice(prefix.length)}`,
    configure(proxy) {
      proxy.on("proxyReq", proxyReq => proxyReq.removeHeader("origin"));
    }
  }
]));

function canonicalDirectories() {
  const install = server => {
    server.middlewares.use((req, res, next) => {
      const url = new URL(req.url, "http://localhost");
      if (["/gaalop", "/quantumgaalop"].includes(url.pathname)) {
        res.writeHead(308, { Location: `${url.pathname}/${url.search}` });
        res.end();
        return;
      }
      next();
    });
  };
  return { name: "workspace-directories", configureServer: install, configurePreviewServer: install };
}

export default defineConfig({
  root: siteRoot,
  base: "./",
  build: {
    outDir: resolve(projectRoot, "dist"),
    rollupOptions: {
      input: standalone ? resolve(siteRoot, "index.html") : {
        index: resolve(projectRoot, "index.html"),
        gaalop: resolve(projectRoot, "gaalop/index.html"),
        quantumgaalop: resolve(projectRoot, "quantumgaalop/index.html")
      }
    },
    minify: "terser",
    terserOptions: { compress: { passes: 2 } }
  },
  plugins: [vue(), canonicalDirectories()],
  publicDir: resolve(projectRoot, "../codegen-ganja/src/main/resources/de/gaalop/ganja"),
  server: {
    host: "0.0.0.0",
    port: 5173,
    allowedHosts: true,
    proxy: apiProxy
  },
  preview: { proxy: apiProxy }
});
