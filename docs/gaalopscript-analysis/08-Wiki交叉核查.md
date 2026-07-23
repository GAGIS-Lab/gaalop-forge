# GitHub Wiki 交叉核查与修补清单

核查对象：[CallForSanity/Gaalop Wiki](https://github.com/CallForSanity/Gaalop/wiki)，Wiki Git revision `4e0a5f4feefa43bddebcf7c8842cf3c4316ca9b2`（最后提交时间 2025-08-19）。本轮完整读取 8 个 Markdown 页面：Home、Builtin Functions、Compiling GAALOP、Compiling GAALOP Precompiler、CSE、GAALOP Structure、Plugin configurations、Pragmas。

## 需要修补的本地报告结论

| 原结论 | Wiki 触发的复核 | 修正后结论 |
|---|---|---|
| pragma 共 11 个 | Wiki 没有 unroll/count 页面；回看 wrapper `:95-98` | 9 个有效 pragma；unroll/count 仅 pass-through，不写 CFG |
| pragma normalize 只写作 metadata | Wiki 描述“下一输出向量归一化”；回看 parser `:179-197` | 它是源码重写：把下一条 `?v=expr;` 改成临时量与 `/abs(temp)`，且实现是一次开启后持续作用，不会自动 reset |
| insert/return 泛称 backend dependent | Wiki 明确 return 仅 C#/Python 且 `useArrays=false` | return 的实际公共实现位于 `NonarrayCodeGeneratorVisitor`；insert 位于 `DefaultCodeGeneratorVisitor`，但各后端继承关系仍决定最终支持 |
| range/segments/triangles 泛称 visualizer dependent | Wiki 指定 Ganja code generator | 当前消费者证据为 `codegen-ganja/GanjaVisitor.java:227-245`（segments/triangles）；range 同属 Ganja 交互元数据 |

## Wiki 本身与当前源码不一致

| Wiki 页面/位置 | 文档说法 | 当前源码证据 | 建议 |
|---|---|---|---|
| `Pragmas.md:39` | `!c = a + b` | `CluCalc.g4:241` 要求 `SEMICOLON` | 改为 `!c = a + b;` |
| `Pragmas.md:245-247` | normalize 示例 assignment 无 `;` | grammar `:237` 要求 `;`；normalize rewrite `CluCalcCodeParser:191-194` 也依赖原表达式携带分号 | 补分号并说明是源码重写 |
| `Pragmas.md:265-267` | return 示例 assignment 无 `;` | grammar `:237` | 补分号 |
| `Pragmas.md:239-249` | “normalize the next output vector” | `usingNormalizePragma` 在 `:168-205` 从不 reset | 文档或代码二选一修正；当前行为是后续所有匹配输出 assignment 都归一化 |
| `CSE.md:8` | CLI `--opt-gcse` | `cli/Main.java:29-72` 无此 option | 改为当前 `--specific` 配置方式，或恢复兼容 alias |
| `Compiling GAALOP.md:12` | 输出目录 `distribution-x.y.z-bin` | 当前项目版本固定 `1.0.0`，assembly 产物名应以实际 build 为准 | 改为通配说明并在 CI 校验 |
| `Home.md:4` | “should use Netbeans” | 当前同时含 Maven/IDEA/Eclipse 元数据，构建不依赖 NetBeans | 改成 IDE 可选、Maven 为权威构建入口 |
| `Home.md:11` | `Buitin Functions` | 拼写错误 | 改为 `Builtin Functions` |
| 多个页面链接 | `(wiki/Compiling-GAALOP)` 等 | Wiki 内相对链接通常应为页面 slug | 改成 `(Compiling-GAALOP)` 或完整 URL，避免路径重复 |

## Wiki 确认且应纳入参考手册的细节

- `coefficient(m,e2^e1)` 会保留 blade 次序符号，Wiki 示例期望 `-3`；这与 inliner 的 blade 处理方向一致，但应增加专项回归测试。
- `?` 表示输出并倾向计算全部输出分量，`!` 是 only-evaluate optimization boundary；Wiki 对两者性能语义的解释比原报告更具体。
- `in2out` 不只是排序提示：`ControlFlowGraph.java:574-627` 对遗漏/非法 input/output 会抛异常。
- `#pragma output {p} e1 e2^e0` 的花括号变量组由 `AlStrategy.getVariables:205-218` 支持。
- tuple/non-array return 与 C#/Python 的 `useArrays=false` 绑定；`optimizeOnSave` 是 GUI/插件保存行为，不属于 GAALOPScript。

## 建议新增测试

1. `normalize` 连续两个输出，确认当前“持续生效”是否是 bug。
2. `#pragma normalize` 后带注释、空行、普通 assignment，再到 output 的边界。
3. `//#pragma unroll`、`#pragma unroll`、count 两种载体，确认无效/错误恢复行为。
4. Wiki 三个缺分号示例作为 negative/positive pair。
5. `coefficient(m,e2^e1)` 的符号回归。
6. `--specific de.gaalop.tba.Plugin:optGCSE=true` 的 CLI smoke test，并据结果更新 Wiki CSE 命令。

## 总体判断

Wiki 对 pragma、`coefficient` 和插件配置提供了有价值的用户语义，但它不是当前语言规范：部分页面陈旧、示例缺分号，且 normalize 的文字语义与实现状态机不一致。源码报告应保留 Wiki 作为辅助证据，并始终把 grammar、wrapper 和实际测试置于更高优先级。
