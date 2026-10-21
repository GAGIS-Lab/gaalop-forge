# MATLAB 数值与符号数组兼容修复

2026-10-20。修复当前 GAALOP MATLAB 生成器先写数值常量、再向同一数组写入 `sym` 时的类型错误。修改位于 `codegen-matlab`，对中间数组和输出数组均生效；网页和桌面调用共享该生成器。

## 原因与实现

旧输出例如：

```matlab
mix(1) = 1.0;
mix(4) = theta;
```

第一句将 mix 建为 double，传入符号 theta 后第二句不能把 sym 写入 double。修复从优化后的赋值图收集符号输入依赖，经固定点传播跟踪多级中间数组与标量 CSE 临时变量。数组成为 sym 后，其常数分量也属于 sym，因此按整个数组的类型依赖传播，不能只看表达式是否含自由参数。

新输出采用条件初始化：

```matlab
gaalopIsSym = @isa;
if gaalopIsSym(theta, 'sym')
    mix = theta([]);
end
mix(1) = 1.0;
mix(4) = theta;
```

`theta([])` 创建与符号输入同类的空容器，后续保持原来的线性索引和数组增长方式。仅相关输入是 sym 时执行初始化；数值调用继续使用原赋值语句，不预分配 double、不改变 single 或复数输入的数值结果、输出类型与形状。不依赖符号输入的独立数组不被强制转成 sym。

类型检测通过函数句柄调用，生成的辅助变量名避开输入、输出和局部变量；初始化使用输入原型，不调用可能被用户输入名遮蔽的 sym/zeros 构造函数。覆盖了输入名 isa、sym、zeros、gaalopIsSym 的实际 MATLAB 运行。

实现入口：`MatlabVisitor.visit(StartNode)`；类型依赖与初始化代码：`MatlabSymbolicInitialization`。

## 回归范围

- 模块级 7 项 JUnit：常数先写入、标量 CSE 临时变量、多级中间数组、读取符号数组的常数分量、多输入输出、独立输出、辅助名冲突、符号分量先写、纯常数与空函数。旧生成器运行同组测试出现 4 项失败，修复后全部通过。
- REST 2 项 JUnit：QCA/CGA/QGA 的真实编译链、多输出独立常数及 CSE 依赖。
- 全 reactor `mvn clean test` 共 253 项通过，0 失败、0 错误、0 跳过；设置 QRA 原生库目录，包含真实原生测试。REST jar 打包通过。
- 实际 MATLAB R2025b（25.2.0.2998904）和 Symbolic Math Toolbox：分别运行修复前与修复后生成的函数，比较值、class、size；覆盖零、正负实数、复数、single、single/double 混合输入。
- 符号测试逐个输入置为 sym，再测试全符号输入；核对代入后的数值结果、零与复数代入、输出形状。中间数组用独立解析公式检查。
- 实际服务生成案例包括 QCA 混合系数、多输入多输出、启用 CSE、5 位状态，传统 CGA，多参数 QGA/混合系数，以及 QRA 数值输出。七组原数学赋值代码逐字相同（排除新初始化块和函数名测试后缀）。

实际共验证 14 组生成函数：直接生成器样本 7 组、真实 REST 导出样本 7 组。合计 69 次数值调用组合比较、51 次符号调用及代入比较，全部通过；原生成器在其中 24 次符号调用中出现类型转换错误。数值对比逐项检查值、class、size；七组服务输出的原始赋值代码也保持一致。证据：direct-runtime.json、service-runtime.json、full-tests.log、focused-tests.log 和 before-tests.log。

## 复核方法

Windows 使用 PowerShell 7、JDK 17。全工程：

```powershell
mvn clean test "-Dgaalop.garamon.nativeDir=native/qra/target/install" "-DargLine=-Xmx1024m"
```

模块级测试可导出实际生成的 .m 样本，供 MATLAB 执行：

```powershell
mvn -pl codegen-matlab -am test "-Dtest=MatlabSymbolicInitializationTest" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dgaalop.matlab.fixtureDir=C:/path/to/after" "-Dgaalop.matlab.fixtureSuffix=_after"
```

运行修复前生成器时，以 `_before` 后缀导出相同测试图；旧代码的符号初始化断言会失败，这是预期现象，但样本会先写出。保留真正的修复前样本，勿用修改后的生成器冒充基线。MATLAB 中：

```matlab
addpath('codegen-matlab/src/test/matlab');
report = run_symbolic_regression('C:/path/to/before', 'C:/path/to/after', 'C:/path/to/report.json');
```

测试脚本自动配对 `*_before.m` / `*_after.m`，核对函数签名，比较原数值计算，再验证符号调用与数值代入。需要已安装的 MATLAB 和 Symbolic Math Toolbox；Maven/JUnit 本身不要求 MATLAB 安装。

本机完整证据在 Codex 可视化任务目录的 `matlab-generator-fix/`：修复前后 .m、REST 请求案例清单、源码对比脚本、Maven 日志及 MATLAB JSON 结果。运行输出未写入源码目录。

## 边界

修复针对现有标量参数与多向量系数数组，不新增参数向量化或 symmatrix/symfun 支持，不改变数学表达式与系数顺序。符号调用需要 Symbolic Math Toolbox；纯数值生成函数没有新增 Symbolic Math Toolbox 构造函数调用。几何虚数与 MATLAB 复数的映射、Jaroslav 量子博弈整体验证、QRA 自由参数符号编译仍是另外的任务。
