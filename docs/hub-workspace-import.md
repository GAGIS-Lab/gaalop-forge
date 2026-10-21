# Hub 工作区导入

两个工作台均接收浏览器 URL 的 `workspaceUrl` 查询参数。本项目不使用 Hash/Vue Router 路由；参数放在普通 query 中。

```text
/gaalop/?workspaceUrl=<URL编码的导出地址>
/quantumgaalop/?workspaceUrl=<URL编码的导出地址>
```

Hub 按示例类型选择工作台，并把绝对导出 URL 放入参数。使用 `URL.searchParams.set('workspaceUrl', exportUrl.href)`，不要先手动编码再传给 searchParams。示例导出路径必须为 `/api/v1/examples/{id}/workspace`，可附 `revision`。当前接收 ID 为 ASCII 字母、数字、下划线及连字符。

## 同源后端代理与部署

浏览器从 workspaceUrl 提取 ID 和 revision，只请求当前页面目录下的 api/hub/examples/{id}/workspace，不访问链接中的 Hub 域名，也不提供直连降级。

Spring Controller 路径为 /api/hub/examples/{id}/workspace；独立后端默认上下文 /gaalop，因此完整路径为 /gaalop/api/hub/examples/{id}/workspace。前端部署于 /gaalop/ 时自动保留此前缀。

启动 GaalopWeb 后端前设置环境变量：

```powershell
$env:GAALOP_HUB_BASE_URL = 'http://127.0.0.1:8011'
```

地址必须是 Hub 服务根地址（HTTP(S)，不含 API 路径、查询参数或凭据）。后端自行追加导出路径。缺失/无效配置返回 503，不回退到链接地址。浏览器不需要 Hub CORS，且不用 VITE_HUB_WORKSPACE_ORIGINS。

Docker Compose 已传入 GAALOP_HUB_BASE_URL；容器部署应设置可访问的 Hub 服务名（例如 http://hub:8011）或宿主机地址，不能把容器内 127.0.0.1 当作宿主机。环境变量变更需要重启后端，不需要重建前端。

Vite 的 /api、/gaalop/api、/quantumgaalop/api 代理均转发至后端 /gaalop/api；docker/nginx.conf 的对应三组 API 规则转发到使用根上下文的容器后端，均覆盖新接口。其他生产反向代理也必须转发整个 /api/（或 /gaalop/api/），不能仅转发 compile。无需改动现有认证规则；浏览器使用 same-origin 凭据，上游请求不转发 Cookie/Authorization。

后端连接超时 3 秒、读取超时 10 秒，禁止重定向，流式读取最多 1 MiB，并检查 JSON 对象及尾随内容。响应 Cache-Control: no-store。404/409/422 保留合法错误 JSON；参数错误 400，配置缺失 503，超时 504，其余上游异常 502。前端保留 15 秒超时和 1 MiB 限制。ID 最多 128 位，revision 为 1–18 位正整数且不能以 0 开头；重复参数不接受。

## 导入规则

- 仅接受 schemaVersion 1；id、revision、title、workspace、algebra、algebraPlugins 和三个脚本字符串必需。ID/指定 revision 必须与请求一致，不尝试读取替代版本。
- 按当前界面验证空间：传统 10 种代数；QCA 1–3、QRA 2–9；QGA 不接受 qubits。QGA 只支持主脚本，所以非空赋值/可视化内容会明确拒绝，避免导入后被静默忽略。
- 读取、校验不改变工作台。校验通过后直接填充代数、qubit 和三个编辑区，空字符串也覆盖；清除旧代码与图表，不自动运行。
- 保留工作台自己的输出语言、函数名和可用输出模式。若正在运行，等待运行结束再加载。
- 工作台不匹配时自动跳转到对应入口并加载。
- 不新增导入面板或确认步骤，不改变原有页面布局。加载完成后移除 workspaceUrl，避免刷新重复导入。
- 404、409、422 分别提示；网络/代理/超时、JSON 或校验失败均保留原有内容。

## 编译字段说明：修正 Hub 文档中的 Object 推断

Hub JSON 是编辑器导入协议，不直接发送至编译接口。两个工作台继续调用自己的请求组装函数。

当前 `ScriptInput.multivectorsVisualized` 的 Java 声明是 Object，但 `GaalopCompileService` 会解析字符串脚本，现有前端和测试也发送字符串。**`:res;` 应保持字符串，不应依据 Swagger 的 object 显示转换成 JSON 对象。** 本次未修改后端编译协议。

## 验证

在 frontend 中运行 `node scripts/test-workspace-import.mjs`、`node scripts/test-quantum.mjs` 和 `pnpm build`。浏览器需确认：两个工作台直接加载、失败保留、空字符串覆盖、运行状态保护及导入后无自动编译。Hub 按钮接线属于独立 Hub 项目，本仓库不修改该项目。

本次本地验证：后端设置 GAALOP_HUB_BASE_URL=http://127.0.0.1:8011，前端 18081 同源代理成功导入四点球与三量子位 Grover，浏览器手动运行均成功；Grover 010 概率为 0.9453125，总概率 1.000000。旧 revision 的代理响应为 409，当前脚本未被导入替换。已通过全量 mvn -o clean test（带 nativeDir）、代理 HTTP 层测试、前端导入/量子测试及 pnpm build。未修改 Hub 按钮，使用等价跳转 URL 验证；未部署生产 Nginx/容器，生产端到端访问未实测。
