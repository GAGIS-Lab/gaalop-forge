# Web 与桌面构建范围

项目默认构建 Web 服务、无窗口的编译工具和共享数学测试。桌面源码通过 Maven 的 `desktop` profile 保留。

## 默认构建

```bash
mvn clean test
mvn -pl gaalop-rest -am package
```

REST 使用解析、代数展开、TBA/GAPP 优化和目标语言生成模块。浏览器中的几何预览由 Ganja 实现；量子概率展示使用 QCA 投影和 QRA 原生数值接口。

REST JAR 已移除 `codegen-verilog`、`gappDebugger`、`codegen-vis2d`、`codegen-visualizer` 及其 LWJGL/JInput 依赖。四种桌面输出请求返回 HTTP 400，并说明对应插件需要桌面界面。

`codegen-visualizer` 继续参与默认 reactor，因为 `testbenchTbaGapp` 使用其中的微分、求值和求根算法。共享 API 中的图标和颜色类型也保留兼容。

## 桌面维护

```bash
mvn -Pdesktop test
mvn -Pdesktop package
```

此 profile 加入 `gui`、`starter`、`distribution`、`visualCodeInserter2d`、`codegen-verilog`、`codegen-vis2d` 和 `gappDebugger`。REST 的依赖范围保持一致。CLI 作为无窗口辅助入口参与默认构建。

## 验证记录

2026-10-21，默认 `mvn clean test` 与 `mvn -Pdesktop test` 均通过：275 项 JUnit 测试，0 失败、0 错误、0 跳过。测试覆盖 MATLAB/Python 生成、CGA/QCA、QRA 原生调用和共享数学算法。桌面 profile 的验证覆盖 Maven 编译和测试阶段。

REST 类路径测试检查桌面入口、四种桌面插件和 LWJGL 类的缺席情况，并保留 Java 生成及 Ganja 预览的正向用例。打包后的 JAR 为 33,527,161 字节，包含 76 个运行时依赖；本地服务的 20 项实际 HTTP 与数值检查通过。

## 后续工程工作

Java 等生成器会返回多个源文件。REST 当前将其拼接为生成文本，后续可增加按文件保存和打包下载。共享通知容器、生成错误传播和细分输出参数也适合按请求逐步整理。
