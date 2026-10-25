# Docker 发布构建

应用镜像为 `gagislab/gaalop-forge:1.2.1`，目标平台 Linux x86_64。

## 源码构建

```bash
docker build --platform linux/amd64 -t gagislab/gaalop-forge:1.2.1 .
```

Dockerfile 分别构建 QRA、Java、前端和 Maxima，再组装运行环境。QRA 使用 Ubuntu 22.04，与运行环境的 ABI 对齐。`UBUNTU_MIRROR` 构建参数可指定 Ubuntu 镜像源，apt 保留包签名验证。

## 产物组装

`docker/Dockerfile.artifacts` 从固定摘要的 1.0.0 运行环境组装应用，复用系统依赖和 Maxima。Java、前端及原生库使用当前源码对应的构建产物。

1. 运行 Maven 编译与回归，原生测试通过 `gaalop.garamon.nativeDir` 加载匹配库。
2. 在 frontend 运行前端测试和 `pnpm build`。
3. QRA 使用 `docker build --target qra-build -t gaalop-qra-jammy:1.2.0 .` 构建并运行 CTest。源码逐文件 SHA-256 一致时，也可复用已验证的 Ubuntu 22.04 构建，再执行 CTest。
4. 新建空的产物 context，放入 `gaalop-rest.jar`、`frontend/`、`start-container.sh`、`nginx.conf` 和 `licenses/`。文件分别来自当前 Maven/Vite 构建、docker 目录以及 Garamon/Eigen 许可。
5. 用 `docker/Dockerfile.artifacts` 构建，将 `SOURCE_REVISION` 设为本次源码提交，保留脚本 LF 行尾。

context 只包含上述运行文件。原生库通过源码指纹、系统 ABI 及实际加载核对。镜像清理基础层中的旧静态目录后复制当前前端。

## 运行验证与推送

启动独立测试容器，使用 `--memory 2g --memory-swap 2g` 和回环地址端口。检查健康、两个页面、静态资源、CGA、QCA、QRA 2–9 qubit、生成代码及 Maxima，并记录内存峰值和 OOM 计数。按测试脚本的负载解释容量数据。

```bash
docker push gagislab/gaalop-forge:1.2.1
```

推送后核对仓库摘要。源码提交使用同一版本标签，镜像的 revision 标签指向对应提交。

容器内部 Java 使用根上下文，Nginx 对外提供 `/api/`。独立 Java 使用 `/gaalop` 上下文。Swagger 凭据通过环境注入，构建产物与发布日志保留校验信息。
