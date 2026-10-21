# QuantumGAALOP 布局与交互

本页记录用户确认的布局，作为前端维护基线。保留浅色背景、白色面板、蓝色强调和紧凑工具栏。

## 参考

- [原生界面参考](design/quantum-workspace-native-reference.png)
- [量子工作台布局稿](design/quantum-workspace-v6.png)

布局图用于说明面板比例，实际图表由运行结果生成。

## 页面结构

| 位置 | 内容 | 交互 |
| --- | --- | --- |
| 顶部 | Run、Open、Save、Help、Algebra Type、Code Generation、Output Mode、Function Name | 选择 QCA、QRA 或 quantum bit geometric algebra，按模式显示参数 |
| 上层左侧，约 60% | Code to Optimize | 完整 GAALOPScript 编辑器 |
| 上层右上 | Variable Assignments | 本次参数赋值脚本 |
| 上层右下 | Multivectors to be Visualized | 使用冒号语句选择对象或状态 |
| 两层之间 | 运行状态 | 运行中、成功、失败和旧结果提示 |
| 下层左侧，约 50% | Generated Code | Copy code 复制到剪贴板，Save 使用函数名和语言扩展名 |
| 下层右侧 | Visualization Preview | 概率与振幅展示，支持放大 |

三个输入区使用带行号和语法高亮的脚本编辑器。顶部导航共用 WorkspaceHeader，品牌与跳转位置固定；量子工作区使用整页宽度。Help 介绍各模式计算方式。

## 状态与能力

QCA 默认 1–5 qubit，QRA 为 2–9 qubit 原生数值路径；两者展示基态概率、总概率和投影残差。quantum bit geometric algebra 使用代码生成模式。各代数保留独立草稿。

运行结果绑定本次提交的脚本和配置。输入变化后标记旧结果，运行失败后清空结果。概率及残差采用后端数据。

## 验收

在传统与量子页面分别使用完整脚本检查编辑、编译、保存及对应的可视化路径。核对三个输入区、两个结果区、独立地址、导航、空结果和错误反馈。
