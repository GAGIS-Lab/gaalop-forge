# 256 KB/s 带宽部署与验证

部署方式：宿主机 Nginx 提供 `/var/www/gaalop/`，Java 17 直接运行后端 JAR，接口为 `/gaalop/api/v1/...`。

## 本次优化

- 页面加载后先提供三个普通文本编辑区，立即支持编辑和提交编译。
- Monaco 的语法高亮与补全在后台加载。输入框有焦点时暂缓切换，移开焦点后使用最新输入初始化 Monaco，并保留光标位置。
- Monaco 下载失败时继续提供普通文本编辑，不强制刷新或丢弃输入。
- 构建采用 Terser 压缩；没有新增依赖，也没有删减 Monaco 功能。
- Nginx 对 GAALOP 静态文件启用 Gzip；带内容哈希的 `assets/` 文件缓存一年。HTML 和 `ganja.js` 每次校验，避免更新后仍使用旧入口或脚本。

## 构建产物测量（2026-09-07）

以下按单个用户独占 256 KiB/s（262144 字节/秒）估算，仅表示传输耗时，不含往返延迟、浏览器解析、渲染和后端计算。

| 阶段 | 传输量 | 理论传输时间 |
| --- | ---: | ---: |
| 旧包完整编辑器，无 Gzip，JS + CSS | 3,123,008 字节 | 11.91 秒 |
| 新包首屏基础编辑，Gzip，JS + CSS | 150,159 字节 | 0.57 秒 |
| 新包完整编辑器，Gzip，JS + CSS 合计 | 812,580 字节 | 3.10 秒 |
| 如触发编辑器 Worker，额外 Gzip 传输 | 75,257 字节 | 0.29 秒 |

HTML 约 843 字节，另计；运行可视化时才请求 `ganja.js`。本地编辑器初始加载没有观察到 Worker 请求，因此它单独列出。

主要收益来自 HTTP 压缩和提前提供基础编辑。代码本身体积仅小幅减少；如果服务器原本已经开启 Gzip，不能期待再获得同样幅度的传输节省。ZIP 的压缩率不代表浏览器传输量，部署时必须同时应用 Nginx 配置。

若 256 KB/s 是服务器总出口，多人同时访问会共享带宽，上述单用户耗时不能直接用于并发访问。

## 上传与配置

1. 将新版静态 ZIP 中的所有内容解压到 `/var/www/gaalop/`，确保 `index.html` 直接位于该目录下。
2. 用同目录的 `gaalop-nginx.conf` 更新域名现有 `server` 中的 GAALOP 配置。它是 `server` 内部的片段，不能直接替换主 `nginx.conf`，也不要和旧的同名 location 重复粘贴。
3. 保持后端运行在本机 8080，执行 `sudo nginx -t && sudo systemctl reload nginx`。
4. 初次部署后用无痕窗口测试 `/gaalop/`。以后重复访问应复用 `assets/` 缓存。

检查实际响应（将 `index-实际哈希.js` 替换为上传后的文件名）：

```bash
curl -sS -D - -o /dev/null -H 'Accept-Encoding: gzip' \
  http://gacrac.gagis.cn/gaalop/assets/index-实际哈希.js
curl -I http://gacrac.gagis.cn/gaalop/
curl http://gacrac.gagis.cn/gaalop/api/v1/health
```

JS 应返回 `Content-Encoding: gzip`、`Vary: Accept-Encoding` 和一年缓存；HTML 应返回 `Cache-Control: no-cache`（允许缓存但必须校验）。Nginx 压缩需要主配置正常包含 `mime.types`。

## 本地验证

- `pnpm build` 和 `git diff --check` 通过。
- 使用真实 Nginx 进行配置检查，并验证子目录资源、Gzip、缓存头和健康检查代理。
- 使用生产构建、共享 256 KiB/s 限速服务，并额外延迟 Monaco 请求，验证加载期间可以编辑。
- 人为让 Monaco 首次请求返回 503，验证普通文本模式仍能调用实际 Java 后端并成功生成代码。
- 测试工具 `frontend/scripts/bandwidth-preview.mjs` 仅供本地 QA，不会进入部署 ZIP。它将 API 代理到本机测试端口 18089。

PowerShell 7 复现命令（在 `frontend` 下运行；后端另行启动在 18089）：

```powershell
$env:EDITOR_DELAY_MS = '10000'
$env:FAIL_EDITOR_ONCE = '1'
node scripts/bandwidth-preview.mjs
```

访问 `http://127.0.0.1:5188/gaalop/`。该工具故意使用 `no-store` 来测试冷加载，不能用它衡量生产缓存效果。

公网检查时 `http://gacrac.gagis.cn/gaalop/` 返回 404，因此本报告不是服务器线上实测的加载时间保证。

参考：[Nginx Gzip 官方文档](https://nginx.org/en/docs/http/ngx_http_gzip_module.html)、[Vite 生产构建文档](https://vite.dev/guide/build)。
