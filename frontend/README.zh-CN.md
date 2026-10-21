# 前端工作台

Vue 3、Vite、Ant Design Vue 和 Monaco 提供两个工作台：`/gaalop/` 对应 GAALOPWeb，`/quantumgaalop/` 对应 QuantumGAALOP。两者共用后端、导航和文件操作组件，各自保存草稿。

## 开发

```bash
pnpm install --frozen-lockfile
pnpm dev
```

默认端口为 5173。Vite 将两个页面下的 `/api/` 及根 `/api/` 代理到 `http://localhost:8080/gaalop/api/`。用 `VITE_API_TARGET` 设置后端主机或端口。

## 构建

- `pnpm build`：组合站点，输出 `dist/`，两个入口共享 `dist/assets/`。
- `pnpm build:sites`：分别输出 `dist-sites/gaalop/`、`dist-sites/quantumgaalop/`，各自包含 HTML 与 assets。

旧根地址和 `workspace` 查询参数转到对应页面。导航采用相邻目录地址，支持 `/ga-application/` 下的部署。Hub 导入参数在跳转时保留。配置见[双站点部署](../docs/two-sites-deployment.md)。

## 工作流

三个脚本编辑区对应源码、赋值和可视化选择，两个结果区显示代码和图形。Open 打开脚本并更新函数名，Save 写入授权文件或下载副本，Copy code 复制生成文本。量子默认选择 QCA；模式能力与后端保持一致。

前端检查脚本位于 `scripts/test-*.mjs`，覆盖文件流程、量子请求、Hub 导入和地址转换。
