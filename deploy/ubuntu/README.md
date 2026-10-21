# Linux 服务器部署

## 运行方式

集成镜像在容器内运行 Nginx、Java 和 Maxima，公开 `/gaalop/`、`/quantumgaalop/` 和根 `/api/`。使用 `docker compose up -d` 启动已发布的 `gagislab/gaalop-forge:1.2.0`。

现有服务器采用独立 Java 服务：`/home/GAALOP/gaalop-rest-1.0.0.jar`，端口 8080，上下文 `/gaalop`。Nginx 分别从 `/var/www/gaalop/` 和 `/var/www/quantumgaalop/` 提供两个网站，转发到 `/gaalop/api/`。Hub 使用独立服务与路由。

`gaalop-rest.service` 是新安装模板，默认用户及目录为 GACRAC，堆设置为 `-Xms128m -Xmx768m`。已有服务沿用其用户、目录、环境变量、原生库和经过评估的 JVM 参数。

## 更新独立服务

1. 将 JAR、`frontend/dist-sites/`、匹配的 Linux 原生库和健康检查脚本上传到版本化暂存目录，核对 SHA-256。
2. 保存当前 JAR、网站目录及健康检查配置作为回滚版本。
3. 停止健康检查 timer 和正在运行的探测，替换脚本及对应 service 文件。
4. 停止 Java 服务，安装新 JAR 和网站目录，再启动 Java；Nginx 路径保持现有配置。
5. 检查 `http://127.0.0.1:8080/gaalop/api/v1/health`、两个页面及实际编译请求。
6. 执行 `systemctl daemon-reload`，启用健康检查 timer，观察至少两个探测周期的 PID、服务状态及日志。

## 健康检查

将 `gaalop-rest-healthcheck.sh` 安装到 `/usr/local/bin/`，将对应 `.service`、`.timer` 安装到 `/etc/systemd/system/`。脚本默认访问 `/gaalop/api/v1/health`；`/etc/default/gaalop-rest-healthcheck` 可通过 `HEALTH_URL` 指定其他地址。

脚本等待启动后的 90 秒，探测最多重试三次。2xx 表示成功；3xx/4xx 写入配置诊断；连续连接失败或 5xx 触发 `systemctl try-restart`。重启前复核 systemd activation identity，保留管理员停止服务的状态。systemd 负责进程退出后的恢复。

```bash
systemctl daemon-reload
systemctl enable --now gaalop-rest-healthcheck.timer
systemctl start gaalop-rest-healthcheck.service
systemctl show gaalop-rest.service -p MainPID -p ActiveState -p ActiveEnterTimestamp
journalctl -u gaalop-rest-healthcheck.service -n 20 --no-pager
```

本地回归脚本位于 `tests/healthcheck-test.sh`，使用模拟 systemctl/curl 验证正常响应、错误地址、重试、启动等待和服务状态竞争。
