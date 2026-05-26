# 独立前端说明

这个目录是新的独立前端工程，和 `gaalop-rest` 平级存在，不再依赖 Spring Boot 静态资源目录来承载页面。

## 技术栈

- Vue 3
- Vite
- Ant Design Vue
- pnpm

## 启动方式

安装依赖：

```bash
pnpm install
```

开发模式：

```bash
pnpm dev
```

默认开发地址：

```text
http://localhost:5173
```

## 后端联调

- Vite 已经把 `/api` 代理到 `http://localhost:8080`
- 因此前端开发时，只需要保证 `gaalop-rest` 在本地 `8080` 端口启动

## 生产构建

```bash
pnpm build
```

构建产物位于：

```text
frontend/dist
```

## 当前页面范围

当前只实现了 `Online Editing` 主页面，包含：

- 顶部导航
- 左侧功能栏
- 参数配置区
- 三块脚本编辑区
- 编译结果区
- 可视化预览区

页面已移除 `Visualization Plugin` 配置项，并使用与后端一致的 `Output Mode`。
