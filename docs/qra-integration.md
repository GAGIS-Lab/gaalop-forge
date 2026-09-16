# QRA 2–9 qubit 后端扩展

2026-09-16。本文记录 QRA 代数资源、原生计算、REST 数值执行和概率数据。QuantumGaalopWeb 已另行接入，见 [网页说明](quantum-qra-web.md)；未改造桌面端。后续扩展首先阅读 [加速库生成与维护指南](qra-acceleration-maintenance.md)。

## 来源与改动

- 来源：本机 `F:/IDEA_project/Gaalop_QRA` 的 garamon Java 模块，以及 `F:/IDEA_project/QRA_resource` 中的代数资源、Grover 脚本、Garamon/Eigen 和 Qra7Project 桥接源码。
- 新增 Maven garamon 模块，供 REST 使用，不依赖 gui。
- 保留 QRA2–6 原始定义和宏，按相同全正度规与基底规则补齐 QRA7–9；不搬入预计算乘积表。QRA 的代数维度为 2n+2。
- 原生库识别 2–9，按 Windows/Linux x86_64 加载，并检查库返回的维度和系数数量。
- 统一 CMake 构建和桥接模板，重新生成各维度 Garamon 库。C++ 基向量名称使用无歧义标识，保持位序及 GAALOP 名称不变。
- 稀疏输入使用全正度规下的符号 XOR 乘积；超过阈值后仍走 Garamon 运算。内积遵循 Garamon 约定：标量与任意多向量的内积为零。
- 修复 Windows MinGW 运行库依赖及 9 qubit 生成代码的大初始化列表栈溢出；测试脚本将加载错误作为失败报告，避免系统弹窗。
- QRA 基底目录共享表达式后缀，减少高维初始化对象数量；保持原有分级基底顺序。
- 概率解释采用逐列稀疏 ket 投影，不再构造 8 GiB 稠密矩阵及 SVD。保留总概率与投影残差，不自动归一化。
- 直接从已求值系数得到结果，不再从生成代码正则提取固定的 res 变量。

## REST 使用

编译请求使用 `ALGEBRA_QRA`，`algebraDimension` 必须明确指定 2–9；当前接入 JAVA、CPP、PYTHON 输出。

三个脚本区仍是 GAALOPScript：变量赋值在源码前执行，可视化脚本在后执行。数值路径在 Code Only 模式也使用变量赋值。支持通过 `?变量;` 输出，通过可视化区 `:变量;` 标记输出，不要求输出必须命名为 res。

这是一条数值执行路径，生成代码包含本次赋值后的常量结果，不是保留自由参数的符号编译。尚未支持的控制流、未展开宏及未赋值输入会返回错误；不能把“完整脚本编辑”理解为支持语言的所有控制结构。

该路径不启用 CSE/Maxima；请求这两项优化时明确报错，不静默忽略。

请求可视化时，响应新增 `quantumResults`，以输出变量名为键，每个结果包含 nqubits、labels、real、imaginary、probabilities、totalProbability、residualNorm。传统响应不增加该字段；传统 visualizationCode 路径不变。前端集成时应检查残差及总概率，不能将任意多向量投影默认为有效归一化量子态。

## 构建与验证

参见 [原生库构建说明](../native/qra/README.md)。构建默认使用 `native/qra/vendor` 中整理的 Garamon 生成器、模板及 Eigen 头文件，可通过 QRA_RESOURCE_ROOT 覆盖。保留原始许可证和 SHA256 清单；不收录第三方构建目录和编译产物。

测试覆盖：

1. Windows/Linux 每个 2–9 qubit 库的基向量平方、反交换及随机高阶几何积、外积、内积。
2. 逐项对照作者提供的 2–6 qubit 概率基底系数，检验分级索引到 XOR 索引映射。
3. 2–9 qubit 复振幅叠加态，以及非归一化、子空间外分量的残差。
4. 2–6 qubit 原始 Grover 脚本及独立振幅放大概率公式。
5. 7–9 qubit 完整 GAALOPScript 构造复基态、计算并返回概率。
6. 全 Maven reactor 回归，包含已有 QCA 和传统代数测试。

这些检查证明相应计算路径的正确性，不代表已完成 7–9 qubit Grover 性能基准或 Web 并发压测。高维稠密运算仍可能很慢，暂不据此扩大生产并发容量。

本次构建与测试输出保存在 `native/qra/target`（Git 忽略），全量清理或重新克隆后需重新构建。

Windows 与 Linux 原生库各通过 8 项 CTest；Docker Java/JNA 链路通过 8 项 JUnit 检查。
`mvn -o clean test -Dgaalop.garamon.nativeDir=F:/IDEA_project/Gaalop/native/qra/target/install` 全工程回归通过：230 项测试，0 失败、0 错误、0 跳过。
完整构建日志与测试日志分别位于 `native/qra/target/windows-test.log`、`native/qra/target/docker/test.log`、`native/qra/target/docker/java-test.log` 和 `native/qra/target/full-reactor-test.log`。

双平台库包：`native/qra/target/qra-native-x86_64.zip`，内附 SHA256.json。解压到运行环境的原生库目录后，以 JVM 参数 `-Dgaalop.garamon.nativeDir=<解压目录>` 指定位置。库包没有自动部署到现有服务。

## Docker 测试环境

按用户要求，Linux 的最终验证在 Docker 中执行；此前启动的 WSL 构建已停止。

本次保留的容器供用户自行删除：

- `gaalop-qra-x86-test-20260916`：初始工具准备容器。
- `gaalop-qra-linux-validation-20260916`：Linux 原生库构建和 CTest。
- `gaalop-qra-linux-api-20260916`：Java/JNA 到 Linux 库的完整脚本验证。

临时工具镜像：`gaalop-qra-test-tools:20260916`。没有自动删除容器或镜像。
后续可用 `native/qra/Dockerfile.test` 与 `test-linux.sh` 在单个容器中重复完整流程；将 native/qra 只读挂载至 /src，测试输出目录挂载至 /work，默认使用 /src/vendor。若使用外部快照，另挂载资源目录并设置 QRA_RESOURCE_ROOT。若运行 Java 测试，还需只读挂载工程与 Maven 依赖，并提供对应 Linux 路径的 classpath-linux.txt。
