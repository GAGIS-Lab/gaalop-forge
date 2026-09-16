# QRA 网页接入

2026-09-16。独立量子入口为前端根地址加 `?workspace=quantum`，默认根地址仍加载传统 GaalopWeb。组件按入口延迟加载，传统 App.vue 本次未修改。

## 使用

1. 选择 QRA、与脚本对应的 qubit 数（2–9）、输出语言及输出模式。
2. Code to Optimize 中填写完整 GAALOPScript。默认示例为 2 qubit 参数化态，其他维度需填写对应基底定义；切换 qubit 数不会改写脚本。
3. Variable Assignments 中给本次求值参数赋定值，例如 `theta=0;`。
4. Multivectors to be Visualized 中用 `:psi;` 标记状态。可先写计算语句，再标记结果；多行 `:变量;` 产生多个图。
5. Run 后查看基态概率柱状图、振幅表、总概率和残差。非归一化或子空间外结果会提示，不自动归一化。

保留五面板、三个带行号与语法高亮的编辑器。支持图表放大、代码复制/下载、独立本地草稿、输入变更后的旧结果提示、请求失败清除旧输出。图表不执行生成代码，也不使用模拟数据。

QRA 仍走原生数值执行，生成代码包含本次定值结果，并非保留自由参数的函数。

## QCA 与 Q bit GA 接入

同日后续更新：模式选择现在可运行 QRA、QCA、Q bit GA（接口标识 ALGEBRA_QGA）。三种模式分别保存本地草稿，保留旧版 QRA 草稿键。切换模式清除旧输出，运行中暂不可切换模式。

| 模式 | 参数 | 编译方式 | 可视化 |
| --- | --- | --- | --- |
| QRA | 2–9 qubit | 原生数值求值 | 概率图、振幅表 |
| QCA | 1–3 qubit | TBA，保留自由参数；可视化单独定值求值 | 概率图、振幅表 |
| Q bit GA | 项目自带固定 Cl(6,3)，不设置 qubit 数 | TBA，保留自由参数 | 尚未接通 |

三种模式均提供 Java、C++、Python 输出。Q bit GA 只开放 Code Only，变量赋值和可视化面板为只读。QCA 的 Variable Assignments 只用于可视化，生成代码仍保留自由参数。若要生成带定值的代码，将赋值写入主脚本。每种模式提供与实际基底对应的初始脚本。

QCA 新增独立的状态解码接口，参见 [QCA 可视化说明](qca-visualization.md)。Q bit GA 使用已有 qga/definition.csv、宏和乘积表，没有修改其数学定义；其资源与公开 Quantum Bit GA 定义的对应关系尚待核对。

验证新增 Q bit GA 三语言输出、基向量平方、零向量配对和自由参数编译检查；前端请求测试覆盖三种模式的能力约束与草稿键隔离。浏览器工具当前连接失败，未完成本轮交互与截图检查。

## 后端变化

QRA 的 `quantumResults` 现在只包含通过冒号语句标记的变量。`?变量` 继续参与代码输出，但不会自动成为概率图。未标记任何状态时返回空对象。为兼容 GAALOPScript，也识别主脚本中的冒号标记；不通过正则截取可视化变量。

仍使用 `/api/v1/compile`；开发服务器代理至后端的 `/gaalop/api/v1/compile`。后端需要配置 `-Dgaalop.garamon.nativeDir=<原生库目录>`。前端 API 地址可通过既有 `VITE_API_BASE_URL` 配置；开发代理通过 `VITE_API_TARGET` 配置。

## 本次验证

- `pnpm build`，以及 `node scripts/test-quantum.mjs`。
- 全 reactor `mvn -o clean test -Dgaalop.garamon.nativeDir=F:/IDEA_project/Gaalop/native/qra/target/install`：231 项，0 失败、0 错误、0 跳过。日志 `native/qra/target/web-reactor-test.log`。
- 新增后端测试：参数改变概率、只投影选中状态、可视化区计算语句、多状态、空选择、缺少参数报错。
- 浏览器连接真实本地 Java/JNA 后端：默认 theta=π/2 时 00/10 概率各 0.5；改为 0 后 00 概率为 1；验证旧结果标记、未知变量错误清图、多状态和图表放大。
- 传统页面 CGA 三球示例编译成功，生成代码和可视化响应正常。

没有部署到服务器，也没有完成 2GB Docker 内存上限测试。界面提供 2–9 维度选项不代表 2GB 服务器可运行任意规模脚本。
