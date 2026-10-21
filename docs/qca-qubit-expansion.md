# QCA 4–5 qubit 扩展验证

2026-10-20。QuantumGAALOP 的 QCA 网页与 REST 范围由 1–3 扩展为 **1–5 qubit**。6 位及以上在 REST 入口拒绝；底层定义生成器接受到 10，不代表已验证其执行能力。QRA、固定 Cl(6,3) 的 quantum bit geometric algebra 均未改变。

## 实现与正确性

QCA 原本就按维度生成 Cl(n+2,n) 定义，沿用当前修正后的 `ei1²=ei2²=+1`、`j=ei1*ei2`、`j²=-1`。4、5 位分别有 1024、4096 个基刃，不依赖 QRA 原生库或新增预计算表。本次同步 REST 维度检查、前端选项、Hub 导入校验、英德 OpenAPI 描述与测试。

`QcaExpansionTest` 对 4、5 位分别检查：

- 全部 16/32 个计算基态、位串顺序和共同复相位；逐项核对实振幅、虚振幅、概率、总概率和投影残差。
- Hadamard、带 Jordan–Wigner 符号的受控 X 链、相位门得到 `(ket_0 + j*ket_all1)/sqrt(2)`，与独立复状态向量比较。
- MATLAB 输出保留自由参数 `theta`；改变可视化赋值不改变生成代码；Code Only 不要求参数定值。
- 原有 1–3 位与非归一化/子空间残差测试保留，6 位请求拒绝。

这不等于已验证所有生成语言的目标运行环境，也不等于 Jaroslav 的量子博弈案例已可直接运行。

## 2GB 容器测量

环境：Windows 11、Docker Linux x86_64、JDK 17；宿主机约 127 GiB RAM、32 逻辑 CPU。测试容器使用缓存的 `gagislab/gaalop-forge:1.1.0` 提供 Java 运行时，挂载当前编译产物运行探针，显式设置 `--memory 2g --memory-swap 2g`，即容器总额度 2 GiB、不额外配置 swap；JVM `-Xmx768m`。每个探针为独立进程，单请求、无并发。测试容器保留供复核。

| 工作负载 | 用时（秒） | 进程峰值 RSS（KiB，VmHWM） | 容器 memory.peak（bytes） |
| --- | ---: | ---: | ---: |
| 4 位，全部 16 个带复相位的基态，一次投影 | 1.606 | 167052 | 216707072 |
| 4 位，参数化首尾双态叠加 | 1.448 | 172820 | 161382400 |
| 5 位，全部 32 个带复相位的基态，一次投影 | 3.298 | 710324 | 713539584 |
| 5 位，参数化首尾双态叠加 | 2.114 | 414352 | 408788992 |

探针执行真实 TBA 编译及 QCA 概率解码，不含 Spring 服务启动。RSS 与 cgroup 分别统计进程映射和容器内存记账，不能互换。单次测量不用于宣称稳定吞吐量；大规模门序列、任意 5 位算法、多用户并发仍需针对实际脚本测量。构建时内存不包含在以上运行时数据中。

### 打包 REST 服务验证

使用重新构建的 `gaalop-rest-1.0.0.jar`，在独立容器 `gaalop-qca5-rest-20261020-v2` 内以相同 2 GiB/no extra swap、`-Xmx768m` 条件启动 Spring 服务。通过 `/gaalop/api/v1/compile` 顺序请求，输出语言为 MATLAB，每次请求均核对振幅、概率、总概率与残差；并发度为 1。下表峰值是同一服务进程的**累计峰值**，不是各请求独立的峰值。

| 请求 | HTTP 耗时（秒） | 累计进程峰值 RSS（KiB） | 累计容器 memory.peak（bytes） |
| --- | ---: | ---: | ---: |
| 4 位全部基态，代码与可视化 | 0.809 | 336928 | 412672000 |
| 4 位受控门 GHZ 与相位门，代码与可视化 | 0.696 | 602600 | 684589056 |
| 5 位全部基态，代码与可视化 | 2.186 | 684472 | 768655360 |
| 5 位受控门 GHZ 与相位门，代码与可视化 | 17.396 | 684472 | 768655360 |
| 5 位参数叠加，theta=0 | 0.680 | 698392 | 774983680 |
| 5 位参数叠加，theta=pi/2 | 0.690 | 711004 | 796037120 |
| 相同 5 位脚本，无定值，Code Only | 0.100 | 711704 | 797372416 |

整组容器峰值约 760.4 MiB，无 OOM；Docker 检查 `Memory=MemorySwap=2147483648`。改变 theta 不改变 MATLAB 生成文本，Code Only 同样保留 theta；6 位请求返回 HTTP 400。测试完成后停止并保留容器，未操作生产容器。测量覆盖表中脚本的顺序请求。复杂 5 位程序、并发请求及 Nginx/Maxima 并发负载需要分别评估。

Windows 全 reactor `mvn clean test`：**244 项通过、0 失败、0 错误、0 跳过**，包含设置原生库路径的 QRA 测试。四项前端脚本检查、`pnpm build`、服务打包通过。实际网页选择 QCA/5/MATLAB 运行参数化首尾叠加态，显示 32 个基态，00000 与 11111 概率各 0.5，总概率 1、残差 0；MATLAB 函数签名保留 theta。

## 复核

Windows PowerShell 7，JDK 17，项目根目录：

```powershell
mvn clean test "-Dgaalop.garamon.nativeDir=native/qra/target/install" "-DargLine=-Xmx1024m"
# 迭代时可仅运行扩展检查；完整验证仍运行上面的全 reactor 测试。
mvn -pl gaalop-rest -am test "-Dtest=QcaExpansionTest,QcaServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" "-DargLine=-Xmx1024m"
cd frontend
node scripts/test-quantum.mjs
node scripts/test-workspace-import.mjs
pnpm build
```

可视化输入仍遵守现有 TBA 的单赋值限制，参数值写在 Variable Assignments，选择状态使用 `:psi;`。总概率和残差原样返回，不静默归一化。切换 qubit 数不会自动扩写原来的脚本；主脚本必须构造相应维度的真空和态。

容器复测时，使用已构建的服务 jar，在缓存镜像中覆盖入口并加上 `--memory 2g --memory-swap 2g`、`-Xmx768m`，只向 localhost 发布独立端口。编译请求使用 `QcaExpansionTest` 中的相同源码，MATLAB、`CODE_AND_VISUALIZATION`；分别读取 `/proc/1/status` 的 VmHWM 与 `/sys/fs/cgroup/memory.peak`。PowerShell 中 Java 的 `-D...` 参数必须整体加引号。复测使用本次构建的 JAR。
