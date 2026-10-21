# QuantumGAALOP 工作台

量子入口为 `/quantumgaalop/`，传统入口为 `/gaalop/`。两个 HTML 页面使用各自启动模块，共享导航和后端。新草稿默认选择 QCA，已有草稿恢复代数与编辑内容。

## 三种模式

| 模式 | Qubit | 计算与代码 | 可视化 |
| --- | --- | --- | --- |
| QCA | 1–5 | TBA 代码生成保留自由参数 | 基态概率和振幅 |
| QRA | 2–9 | 原生数值执行后生成常量代码 | 基态概率和振幅 |
| quantum bit geometric algebra | 内置固定 Cl(6,3) | TBA 代码生成 | 代码生成模式 |

工具栏提供 13 种输出生成器，见[输出语言](quantum-output-formats.md)。切换代数时保存各自草稿，并清除上一模式的结果。

## 编译与查看状态

1. 选择代数、qubit 数、语言及输出模式。
2. 在 Code to Optimize 中编写该维度的完整脚本。切换维度后，根据脚本构造对应的真空和态。
3. 在 Variable Assignments 中填写本次求值参数，如 `theta=0;`。QCA 将这些值用于可视化；QRA 在所有输出模式中都执行数值赋值。
4. 在 Multivectors to be Visualized 中用 `:psi;` 标记状态，也可先写计算语句，再用多行冒号语句选择多个结果。
5. Run 后查看基态概率、振幅、总概率和投影残差。修改输入后，界面标记上一运行结果；失败后清除输出。

`?变量` 选择代码输出，`:变量;` 选择概率展示。QRA 的 `quantumResults` 采用冒号语句中的变量名；空选择返回空结果对象。QCA 的投影语义见 [QCA 可视化](qca-visualization.md)。

## 运行配置

独立 Java 服务使用 `/gaalop/api/v1/compile`，集成 Docker 通过 Nginx 暴露 `/api/v1/compile`。QRA 使用 `-Dgaalop.garamon.nativeDir=<原生库目录>` 指定原生库。前端通过 `VITE_API_BASE_URL` 设置 API 路径，Vite 通过 `VITE_API_TARGET` 设置代理目标。

## 验证与容量

前端脚本测试覆盖请求、代数草稿、参数和可视化能力。后端测试覆盖参数改变概率、多状态投影、可视化计算语句和输入错误。浏览器验证包括 QCA 5 qubit、QRA 参数变化、旧结果提示、失败清图、放大视图及传统三球预览。

[QCA 扩展记录](qca-qubit-expansion.md)和 [QRA 内存测量](qra-9qubit-memory.md)列出具体脚本及资源结果；[资源脚本测试](qra-resource-script-tests.md)覆盖原始 2–6 qubit Grover 脚本。容量评估以相应负载和并发条件为准。
