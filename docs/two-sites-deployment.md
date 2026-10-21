# 双站点部署

## 构建与目录

在 `frontend/` 运行 `pnpm build:sites`，得到两个独立目录：

- `dist-sites/gaalop/`：GAALOPWeb。
- `dist-sites/quantumgaalop/`：QuantumGAALOP。

每个目录包含 `index.html` 和 `assets/`，上传时复制整个目录。现有服务器分别使用 `/var/www/gaalop/` 和 `/var/www/quantumgaalop/`，对应访问路径：

- `/ga-application/gaalop/`
- `/ga-application/quantumgaalop/`

两套入口使用独立 HTML 和启动模块。顶部导航跳转到相邻目录，Hub 使用对应入口并附加 `workspaceUrl` 参数。根地址与旧 `?workspace=quantum` 链接通过兼容入口跳转。

## API 转发

当前独立 Java 服务监听 `127.0.0.1:8080`，context path 为 `/gaalop`。两个网页的 API location 均转发到 `http://127.0.0.1:8080/gaalop/api/`。现有服务器配置见 `deploy/ubuntu/gacrac-site-ga-application.conf`。

集成 Docker 在容器内部使用根上下文，并通过 Nginx 暴露 `/api/`。将外层代理指向集成容器时，使用容器公开端口的 `/api/`。

修改现有 Nginx server 块后执行 `nginx -t`，检查通过再 reload。保留同一服务器上的 Hub 和主站路由。

## 组合镜像

`pnpm build` 在 `dist/` 中生成两个页面及公共 assets。Docker 复制整个 `dist/`；容器内使用 `/gaalop/`、`/quantumgaalop/` 和 `/api/`。

后端启用 Hub 导入时设置 `GAALOP_HUB_BASE_URL` 为其可访问的 Hub 根地址。更新前端和 REST JAR 后，检查两个页面、对应健康接口、CGA 三球、QCA 概率及 Hub 请求。
