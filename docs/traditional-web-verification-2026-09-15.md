# 传统 GaalopWeb 核对与量子入口移除

## 核对结论

本记录对应 2026-09-15 的传统 GAALOPWeb：包含 QCA 入口、qubit 输入、编辑器加载和接口修复。当前独立量子页面见 QuantumGAALOP 工作台文档。

本次从 `frontend/src/App.vue` 移除 QCA、旧 QGA 两个选项、qubit 输入、量子输出模式筛选和请求中的量子维度字段，并从 `frontend/src/main.js` 移除不再使用的 InputNumber 注册。传统网页保留 10 种代数和既有代码生成、几何可视化流程。

量子代数定义、宏、共享 REST API、CLI 及已有测试保留，以便后续独立 QuantumGaalopWeb 复用。本次没有新增量子前端，没有修改编译核心或部署线上服务。网页入口移除不是后端量子 API 下线。

## 本地验证

- `pnpm --dir frontend build`：通过。保留既有 Monaco 大体积 chunk 提示。
- `mvn -o clean package`：完整 reactor 测试与打包通过，222 项测试，0 失败、0 错误、0 跳过。第一次在线构建因受限环境不能写工作区外 Maven 仓库的元数据失败，随后使用现有本地缓存离线完成，未修改 Maven 配置。
- 启动新构建的 REST jar（Java 17，无图形环境）和项目已有的生产静态文件代理，通过同源 `/gaalop/api/v1/compile` 完成以下 8 项检查，全部通过。

| 检查 | 结果 |
| --- | --- |
| 生产 index 与实际入口 JS | HTTP 200；入口 JS 无 QCA/QGA 选项和 qubit 控件标识 |
| 从 App.vue 提取的默认 CGA 三球示例：Code Only | HTTP 200，返回生成代码 |
| 同一示例：Code and Visualization | HTTP 200，返回代码和可视化脚本 |
| 同一示例：Visualization Only | HTTP 200，返回可视化脚本 |
| 2D：`e1*e1`，C++ + CSE | HTTP 200，标量结果 1 |
| 3D：`e1*e1`，C++ + CSE | HTTP 200，标量结果 1 |
| 2D PGA：`e1*e1`，C++ + CSE | HTTP 200，标量结果 1 |
| 3D PGA：`e1*e1`，C++ + CSE | HTTP 200，标量结果 1 |

临时测试进程在脚本 finally 中关闭。复现脚本和原始响应保存在 `gaalop-rest/target/traditional-web-smoke.ps1`、`traditional-web-smoke-results.json`、`traditional-*.json`；这些构建目录文件会在下次 clean 时清除。

## 验证边界

本记录的可视化检查采用实际编译接口返回的脚本。浏览器渲染与交互验证见后续工作台记录；桌面插件的 Web 依赖清理见构建范围文档。
