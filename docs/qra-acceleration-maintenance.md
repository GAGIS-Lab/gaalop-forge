# QRA 加速库生成与维护指南

本文是 QRA 原生计算链路的维护入口。当前范围为 Windows/Linux x86_64、2–9 qubit；不是 GPU 加速，也不涉及 ARM。传统 GaalopWeb 与 QuantumGaalopWeb 共用编译工程，但能力与前端入口分别管理。

## 1. 项目内保存了什么

| 位置 | 职责 |
|---|---|
| `native/qra/vendor/garamon/src` | Garamon C++ 代数代码生成器源码 |
| `native/qra/vendor/garamon/data` | 生成器读取的 C++ 模板及许可证 |
| `native/qra/vendor/eigen-3.4.1` | 构建必需的 Eigen 头文件与许可证 |
| `native/qra/vendor/SHA256.json` | 导入源码逐文件校验值 |
| `native/qra/CMakeLists.txt`、`qra.conf.in` | 为每个 qubit 数生成维度、度规及库目标 |
| `native/qra/generate.cmake` | 调用生成器、检查配置一致性、修补大初始化列表 |
| `native/qra/bridge.cpp.in` | 导出 C 接口、稀疏乘积快速路径与 Garamon 回退 |
| `native/qra/smoke.cpp.in` | 基向量和随机高阶乘积的独立校验 |
| `garamon/src/main/java/de/gaalop/garamon/qra` | JNA 加载、基底映射、数值求值与概率投影 |
| `algebra/src/main/resources/de/gaalop/algebra/algebra/qra2` … `qra9` | GAALOP 代数定义及宏 |
| `algebra/src/main/java/de/gaalop/algebra/QraBladeCatalog.java` | 分级基底目录，复用表达式后缀降低内存 |
| `gaalop-rest/src/main/java/de/gaalop/rest/service/GaalopCompileService.java` | 请求检查、编译及可视化结果返回 |
| `frontend/src/quantum/model.js` | 前端 qubit 范围、能力标志及请求组装 |
| `examples/qra` | 已收录的原作者 2–6 qubit Grover 脚本 |

第三方最小源码快照已放入项目，默认构建不再依赖开发者机器上的资源目录。来源与许可见 [vendor 说明](../native/qra/vendor/README.md)。Java 模块源自用户提供的 Gaalop_QRA，桥接模板改编自用户提供的 Qra7Project；这里没有伪造这些来源的上游版本号。

## 2. 加速库如何生成

1. CMake 用 Eigen 头文件编译 Garamon 生成器 `qra_generator`。
2. 遍历 `QRA_QUBITS`，对 n qubit 设置代数维度 `d=2n+2`、单位度规矩阵，生成 `conf/qraN.conf`。
3. 在构建目录复制生成模板。生成器运行时从工作目录相对位置 `../data` 读取模板，输出 `generator/build/output/garamon_qraN`。
4. `generate.cmake` 检查生成目录和输入配置，修补 `Constants.hpp` 的 `dualPermutations` 初始化方式。
5. `bridge.cpp.in` 实例化为各维度的桥接源文件，包含生成的 `qraN/Mvec.hpp`，编译成独立 DLL 或 SO。
6. CTest 检查乘积；安装到 `windows-x86_64/qraN/QraNBridge.dll` 或 `linux-x86_64/qraN/libQraNBridge.so`。

这一步生成的是专用代数运算库。运行网页脚本时不会重新调用 C++ 编译器。Windows 的 DLL 与 Linux 的 SO 必须分别编译，不能因为 CPU 同为 x86_64 就混用。

## 3. 运行时调用与数据约定

GAALOPScript → 解析与宏展开 → `GaramonQraOptimizationStrategy` → `GaramonQraExpressionEvaluator` → `QraNativeLibrary`（JNA）→ C 桥接 → 乘积结果。之后生成常量结果代码，按需要用 `QraStateProjection` 返回复振幅、概率、总概率和残差。

- `QraNativeLoader` 根据平台、代数编号和 `gaalop.garamon.nativeDir` 找库；加载后检查维度及系数数目。
- C ABI 导出 `qraN_dimension`、`qraN_coeff_count` 和 `qraN_{geometric,outer,inner}_dense`。乘积参数为三个独立的 double 缓冲区；调用者必须提供正确长度，不应让输出缓冲区与输入重叠。
- 缓冲区长度为 `2^(2n+2)`，按 XOR 位掩码索引。GAALOP 使用分级顺序，两者通过 `QraBladeOrderMapper` 转换，不能直接混用索引。
- GAALOP 基向量为 `e1…e(2n),er1,er2`；生成 C++ 使用 `b1…b(2n),r1,r2`，防止 `e12` 与 `e1^e2` 的访问器命名歧义。位序不变。
- 桥接成功返回 0，空指针返回 -2，捕获的异常返回 -1；不应跨 C ABI 传播 C++ 异常。
- 当前仅对全正对角度规使用符号 XOR 快速路径。非零系数对数不超过 1,048,576 时走该路径，否则回退 Garamon。内积中标量参与时结果为零，遵循现有 Garamon 约定。
- 概率按稀疏 ket 列投影，不再构造稠密 SVD 矩阵。不要静默归一化或忽略子空间外残差。

QRA 当前是**数值执行**：Variable Assignments 提供参数，生成代码包含本次计算的常量结果，不保留自由参数。可视化编辑区用 `:变量名;`，不限定 `res`。当前不支持的控制流、未赋值参数和未展开宏会报错；CSE/Maxima 不适用于该路径。

## 4. 从源码构建

要求 CMake ≥3.20、C++14 编译器。默认使用项目内 vendor；需要对照其他源码时显式设置 `-DQRA_RESOURCE_ROOT=<目录>`，目录中须包含 `garamon/src`、`garamon/data`、`eigen-3.4.1/Eigen`。

构建前可运行 `pwsh.exe -NoLogo -NoProfile -File native/qra/verify-vendor.ps1`，校验 391 个导入源码及许可文件。更新依赖时有意识地重新生成清单；不要在校验失败时直接改哈希来掩盖非预期变更。

### Windows x86_64

在已配置 x64 MSVC 或 MinGW 的 PowerShell 7 环境中，从仓库根目录执行：

```powershell
cmake -S native/qra -B native/qra/target/windows -DCMAKE_BUILD_TYPE=Release
cmake --build native/qra/target/windows --config Release --parallel 2
pwsh.exe -NoLogo -NoProfile -File native/qra/test-windows.ps1 -BuildDirectory native/qra/target/windows
cmake --install native/qra/target/windows --config Release --prefix native/qra/target/install
```

MinGW 可在首次配置时加 `-G 'MinGW Makefiles'`，MSVC 加 `-A x64`。单独验证可加 `'-DQRA_QUBITS=2'`；多个维度如 `'-DQRA_QUBITS=2;3;9'` 必须整体引用。工具链、vendor 或配置变更后用新的构建目录，不能复用旧生成结果。

MinGW 目标静态链接编译器运行库，避免用户遇到缺少 `libgcc_s_seh-1.dll`。9 qubit 的大型 dual 初始化改为堆构造，避免 Windows DLL 初始化栈溢出（0xc0000142）。这两项修复都应保留在构建/生成层，而不是手改某次生成结果。

### Linux x86_64：Docker

从仓库根目录在 PowerShell 7 中执行（容器名称已存在时换一个名称）：

```powershell
docker build -t gaalop-qra-build -f native/qra/Dockerfile.test native/qra
$nativeSource = (Resolve-Path native/qra).Path
$nativeOutput = Join-Path $nativeSource 'target/linux-rebuild'
New-Item -ItemType Directory -Force $nativeOutput | Out-Null
docker run --name gaalop-qra-rebuild --mount "type=bind,source=$nativeSource,target=/src,readonly" --mount "type=bind,source=$nativeOutput,target=/work" gaalop-qra-build
```

默认构建并测试 2–9 qubit，产物在 `/work/install`。这里不使用 WSL，不自动删除容器。Docker 镜像工具安装需要可访问 apt；源码生成本身使用仓库快照。构建阶段不承诺能在 2GB 机器内完成，之前的 2 GiB 限制测试针对运行阶段。

## 5. 扩展时修改哪里

### 现有 2–9 范围内增加库

设置 `QRA_QUBITS` 构建所需维度即可。每个维度是独立库，已有其他维度无需因新增一个库而重编译；修改桥接、生成模板或运算语义后，则需重建受影响的所有库。

### 超过 9 qubit

不能只放宽前端下拉框。逐项检查：

1. `CMakeLists.txt` 的范围和生成器能力；重新生成对应维度的 DLL/SO。
2. `QraAlgebraId` 的编号匹配范围、位移和系数长度；`QraBladeCatalog`、投影类及索引映射的范围检查。
3. 补充对应 `qraN/definition.csv` 与宏，核对基底位序和度规。
4. REST `validateQraRequest`、前端 `algebraModes.QRA.dimensions` 与请求校验保持一致。
5. 新维度的 CTest、Java/JNA、概率与完整脚本对照测试，最后做内存与时间评估。

每增加一个 qubit，稠密系数数组长度增加四倍。9 qubit 单个 double 数组为 8 MiB；10 qubit 为 32 MiB。变量、中间结果、JNA 缓冲、基底目录还会叠加。当前不承诺 10 qubit 或 9 qubit 复杂脚本可在 2GB 服务器运行。

### 新增量子门或修改代数

若量子门能用现有乘积表达，可添加宏/示例并做独立振幅对照，不必因增加脚本而重新编译原生库。若改变基底、度规或运算约定，则必须同时更新生成配置、桥接快速路径、索引映射及概率解释，并重新构建验证。

## 6. 验证与故障排查

```powershell
mvn -pl gaalop-rest -am test '-Dtest=QraServiceTest,QraStateProjectionTest,QraBladeCatalogTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dgaalop.garamon.nativeDir=<安装目录>'
mvn clean test '-Dgaalop.garamon.nativeDir=<安装目录>'
```

使用 JDK 17；离线依赖已齐备时可加 `-o`。不设置 nativeDir 会让依赖原生库的测试显式跳过，不能将这种成功当作 JNA 链路已验证。前端改动还需在 frontend 中执行 `node scripts/test-quantum.mjs`、`pnpm build` 并手动验证相关流程。

- 找不到库：核对平台子目录、库名、位数及 nativeDir，不能传到单个 qraN 子目录。
- 维度/系数不一致：通常是错误库或旧生成目录，不应绕过加载校验。
- 配置变更或生成中断：新建构建目录，不把残留目录当作成功结果。
- 概率异常：先核对索引映射、复结构、ket 顺序、总概率和残差，再判断是否为图表问题。

测试脚本：[9 qubit 内存测试](../native/qra/benchmark-memory.py)、[原始 Grover 脚本测试](../native/qra/benchmark-resource-scripts.py)。它们当前使用容器内 `/repo`、`/maven`、`/work` 路径，Grover 测试还需 `/resources`；`/work/classpath-linux.txt` 应由当前 Maven 测试依赖路径转换得到，不能直接使用 Windows 分号分隔的 classpath。`/work/install` 放 Linux 原生库。脚本是手动基准工具，不属于 Maven 自动测试。

实测报告：[9 qubit](qra-9qubit-memory.md)、[2–6 qubit 原始 Grover](qra-resource-script-tests.md)。这些是特定脚本的运行记录，不能替代并发或任意高维脚本的容量测试。

所有库、生成头文件、日志及基准输出放 `target/`，不要提交。提交源码模板、vendor 校验清单、示例和测试；分发二进制时附平台、构建工具版本及 SHA256，独立于源码管理。

## 7. 本次源码整理验证记录

2026-09-16：391 个导入文件通过 SHA256 校验。在不挂载外部 QRA_resource 的 Docker 容器中，以默认 vendor 输入重新构建 QRA2，完成生成器编译、代数生成、桥接编译、CTest（1/1 通过）和安装。日志为 `native/qra/target/docker/bundled-verify.log`，容器为 `gaalop-qra-bundled-verify-20260916`，保留供用户清理。本次验证针对依赖收录与默认构建路径，未重新执行所有维度或 Windows 全量构建；各维度此前测试见相关报告。
