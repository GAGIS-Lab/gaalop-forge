# QRA 9 qubit 内存实测（2026-09-16）

使用当前已编译的 GAALOP REST 服务，通过 HTTP POST `/gaalop/api/v1/compile` 执行完整脚本、生成 Java 常量结果代码并返回 512 项概率数据。没有修改计算实现。

## 环境与测量口径

- Docker Linux x86_64，Java 17，2 CPU。
- 容器 `--memory 2g --memory-swap 2g`：上限 2 GiB，额外 swap 为 0。
- JVM `-Xmx768m`，使用现有 Linux QRA 原生库。
- Java 进程峰值取 `/proc/<pid>/status` 的 `VmHWM`，包含堆、原生内存等驻留页。
- 容器峰值取 cgroup v2 `memory.peak`，还包含测量进程及计入容器的文件缓存等。
- 三个请求在同一服务进程中顺序运行；下表峰值为**截至该请求完成时的累计峰值**，不是各请求的独立峰值。首次请求包含冷加载，后两次是热服务。
- 容器启动时安装了 Python 测量工具，因此容器指标覆盖该准备阶段；准备阶段峰值低于后续计算峰值。

## 结果

| 9 qubit 脚本 | Java 进程累计峰值 | 容器累计峰值 | 请求耗时 |
|---|---:|---:|---:|
| 全零基态 | 975.1 MiB | 1123.6 MiB | 6.77 s |
| GHZ 态：全零与全一等幅叠加 | 1007.7 MiB | 1160.9 MiB | 1.85 s |
| 全部 512 个基态均匀叠加 | 1036.8 MiB | 1185.8 MiB | 1.97 s |

首次计算前 Java RSS 为 171.0 MiB，容器使用量约 314.7 MiB。最终容器累计峰值为 1.158 GiB，Java 进程累计峰值为 1.013 GiB。

概率与独立预期逐项比较：基态概率误差为 0；GHZ 最大误差约 6.33e-15；均匀叠加最大误差约 2.47e-17。三项总概率均约为 1，残差不超过 3.13e-16。容器 OOM 与 OOM kill 计数均为 0，正常退出（ExitCode 0，OOMKilled false）。日志中的 GET 方法错误来自启动探测；实际计算使用 POST 并成功。

## 对 2GB 服务器的含义

这三种单请求任务在 2 GiB 容器限制下成功，证明当前实现可以完成这些 9 qubit 计算与概率输出。容器限制不包含宿主操作系统、Docker 守护进程及其他服务，因此不能据此保证整台 2GB 服务器上的任意脚本或并发请求都能运行。未测试长量子门序列、9 qubit Grover 和并发；建议先限制为单计算请求，并在目标服务器复测。此测试不代表精确的最低内存要求。

## 可复核材料

- 测量脚本：`native/qra/benchmark-memory.py`，容器内需要 `/repo` 项目只读挂载、`/maven` Maven 缓存只读挂载及 `/work` 可写测量目录；后者包含 `classpath-linux.txt` 和 `install/` 原生库。
- 原始结果：`native/qra/target/docker/memory-results.json`。
- 完整请求与结果：同目录 `memory-{basis,ghz,uniform}-{request,response}.json`。
- 服务日志：同目录 `memory-api.log`。
- 完成的测试容器：`gaalop-qra9-memory-2gb-run-20260916`，已停止，保留供用户删除。
- 首次尝试的容器 `gaalop-qra9-memory-2gb-20260916` 因镜像缺少 Python 未能启动，亦保留；本地测试镜像为 `gaalop-qra-memory-tools:20260916`。
