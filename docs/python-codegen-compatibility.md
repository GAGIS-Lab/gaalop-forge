# Python 数值代码生成器：函数映射与按需导入

2026-10-21。按用户澄清，符号输入支持限定在 MATLAB；Python 移除此前额外加入的 SymPy 分派及依赖，保留数值生成器修复。MATLAB 的符号支持见 [MATLAB 兼容性说明](matlab-symbolic-codegen.md)。

Python 仍可生成带 theta 等自由参数的函数，调用时传入数值；这不要求使用符号数学库。网页仍采用原来的标量系数／元组返回模式，通过插件启用的 NumPy 数组模式也纳入数值回归。

## 当前实现

根据实际访问的数学节点生成调用和导入，移除原来的裸函数名及 sin/cos 字符串搜索补丁：

| GAALOP 函数 | Python 数值调用 |
| --- | --- |
| sin/cos/tan、asin/acos/atan、exp/log/sqrt、ceil/floor | math 同名函数 |
| fact | math.factorial，兼容整数值浮点输入 |
| abs | Python 内建 abs |
| invert | 几何代数展开后的标量倒数 |

例如 cos 输出直接调用 `math.cos(theta)`（模块别名会避开用户变量名）。不生成 SymPy 导入、类型检测或符号函数分派。普通 math 函数继续使用原有实数定义域；`fact` 不截断非整数，负整数仍报错。标量 invert 不代替编译器的多向量求逆。

依赖与数组处理：

- 标量／元组输出不导入 NumPy。没有数学函数时不导入 math；普通单标量返回不导入 typing.Tuple。
- SymPy、mpmath 已从 Python 回归项目的 pyproject.toml、uv.lock 和 uv 环境移除；只有测试数组所需的 NumPy。
- 数组仅初始化一次，修复写每个分量前清零、导致之前分量丢失的问题。标量 CSE 临时变量直接赋值。
- 数值实数组保持默认 float64；复数写入时按 NumPy result_type 提升 dtype，保留已写分量。dtype 不变时不重复复制。
- 保留多输出元组顺序和自定义 Tuple 注解。返回值发现不会误处理标量 CSE 临时变量；生成失败向上传递异常，不能返回截断的“成功”程序。空函数返回 None。
- 支持代码中的模块别名、内建函数别名避开用户变量和主函数名；幂运算避免被名为 pow 的参数遮蔽。

参考：[Python math](https://docs.python.org/3/library/math.html)、[NumPy zeros](https://numpy.org/doc/stable/reference/generated/numpy.zeros.html)。

## 实际验证

环境：Windows x86_64、JDK 17、Python 3.12.14、NumPy 2.5.4；测试环境未安装 SymPy。Python 依赖由 `codegen-python/src/test/python/pyproject.toml` 和 `uv.lock` 管理。

- 模块级 JUnit 12 项、REST 编译链 JUnit 3 项。全 reactor `mvn clean test` 共 268 项，0 失败、0 错误、0 跳过，包含已配置的 QRA 原生库测试；REST JAR 打包成功。
- 直接生成器 30 组程序、139 次数值调用；网页接口实际导出 20 组程序、102 次数值调用。覆盖数学函数、中间数组、标量 CSE、多输入多输出、独立输出、辅助名冲突、空函数及自定义返回注解。
- 合计 50 组程序、241 次数值调用，与移除 SymPy 前的版本逐项严格比较值、类型和形状，全部一致；另按参考公式核验。覆盖零、正负实数、float32、float64、混合类型；纯代数及数组运算还覆盖复数。
- REST 案例包括 QCA（含 5 位状态）、CGA、QGA、CSE、多输出及 QRA 已求值常量；20 组系数赋值 AST 在归一化函数绑定及幂写法后保持一致。
- 214 次数值调用在禁止 NumPy/SymPy 导入的保护器下通过；45 个标量程序还在 `python -S` 的纯标准库环境中实际执行。非法阶乘的负数、非整数输入仍被拒绝。

此前原版数组清零错误的输出不作为正确性标准；数值修复保留。本次严格比较基线是移除额外符号支持之前已经修复的版本。

## 复核方法

项目根目录使用 PowerShell 7、JDK 17。本地进程直接运行 target 内 JAR 时，需先停止本项目开发后端解除 Windows 文件锁，构建后恢复服务。

```powershell
mvn clean test "-Dgaalop.garamon.nativeDir=native/qra/target/install" "-DargLine=-Xmx1024m"
mvn -pl gaalop-rest -am package -DskipTests
```

专项 JUnit 导出实际 .py 样本：

```powershell
mvn -pl codegen-python -am test "-Dtest=PythonCodeGenerationTest" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dgaalop.python.fixtureDir=C:/path/to/after"
uv sync --locked --project codegen-python/src/test/python
uv run --locked --project codegen-python/src/test/python codegen-python/src/test/python/run_regression.py --before C:/path/to/before --after C:/path/to/after --report C:/path/to/direct-runtime.json --strict-baseline
```

before 应由待比较版本生成。仅在比较尚未修复数组清零缺陷的旧版时省略 `--strict-baseline`，使脚本对新版核对完整参考公式，同时单独记录旧版缺陷。

本地服务采集与回归：

```powershell
uv run --locked --project codegen-python/src/test/python codegen-python/src/test/python/capture_service.py C:/path/to/service-after
uv run --locked --project codegen-python/src/test/python codegen-python/src/test/python/run_regression.py --before C:/path/to/service-before --after C:/path/to/service-after --report C:/path/to/service-runtime.json --service --strict-baseline
```

`capture_service.py --url` 可指定编译接口，默认 `http://127.0.0.1:5174/api/v1/compile`。`stdlib_only.py` 用 Python `-S` 执行，核验所有标量输出在无第三方包的环境下可运行。

本次数值范围调整的证据位于 Codex 任务可视化目录 `python-numeric-scope/`，包含生成文件、真实 REST 导出、运行 JSON 及 Maven 日志。原始数值修复的历史证据仍在 `python-generator-fix/`。

## 边界

Python 当前支持数值标量系数及多向量系数数组，不新增符号数学、参数向量化或 cmath 复数数学函数分派。MATLAB 的数值／符号兼容实现保持不变。QRA 仍先进行原生数值求值再导出常量。
