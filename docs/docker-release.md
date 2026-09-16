# Docker 发布构建

目标平台为 Linux x86_64，应用标签为 `gagislab/gaalop-forge:1.1.0`。

## 全源码构建

从根目录执行 `docker build --platform linux/amd64 -t gagislab/gaalop-forge:1.1.0 .`。Dockerfile 分别构建 QRA、Java、前端及 Maxima，再组装运行镜像。QRA 以 Ubuntu 22.04 编译，与运行环境一致。

QRA 构建阶段支持 `--build-arg UBUNTU_MIRROR=<Ubuntu 镜像源地址>`，不设置时使用默认软件源，包签名验证保持开启。

## 复用运行环境构建

当未变化的系统依赖或 Maxima 下载受阻时，可使用 `docker/Dockerfile.artifacts`。它固定引用已发布 1.0.0 的运行镜像摘要，只替换本次验证过的应用产物并加入新编译的 QRA 库。不能复用任意来源的旧 Java 包或前端目录。

1. 使用 JDK 17 从当前源码执行 Maven clean package，并指定原生库目录；确认测试没有跳过。
2. 在 frontend 中执行 `pnpm build`、`node scripts/test-quantum.mjs`。
3. 执行 `docker build --platform linux/amd64 --target qra-build -t gaalop-qra-jammy:1.1.0 .`，完成全部 2–9 qubit 原生库构建和 CTest。
4. 新建一个空的、Git 忽略的 artifact context，仅放这些文件：
   - `gaalop-rest.jar`：来自 gaalop-rest/target/gaalop-rest-1.0.0.jar。
   - `frontend/`：来自 frontend/dist。
   - `start-container.sh`、`nginx.conf`：来自 docker/，保持 shell 脚本为 LF。
   - `licenses/`：Garamon LICENCE.txt 与 Eigen COPYING.*。
5. 使用该目录作为 context，执行 `docker build --platform linux/amd64 -f docker/Dockerfile.artifacts --build-arg SOURCE_REVISION=<当前提交SHA> -t gagislab/gaalop-forge:1.1.0 <artifact-context>`。

打包产物不得包含本地工具规则、设计提示词、凭据、日志或开发配置。QRA 库不能从 Ubuntu 24.04 编译目录直接复制到 Ubuntu 22.04 运行镜像，必须先检查 GLIBC/GLIBCXX 依赖并验证实际加载。

发布前启动独立测试容器，核对健康检查、传统代数、QCA 概率、QGA 代码输出、QRA 2–9 qubit 概率，以及非 root 运行和共享导航静态资源。测试容器保留供维护者清理。

镜像中的 nginx 对外使用 `/api/`，启动脚本显式将 Spring context 设为 `/`；独立运行 Java 服务时的 `/gaalop` 配置不受影响。推送仅使用已登录且具有目标仓库权限的账户，不将令牌写入源码、镜像或日志。
