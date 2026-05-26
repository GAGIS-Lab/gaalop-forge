# GAALOPScript 已实现语法规则

本文档整理当前项目中已经真实实现的 GAALOPScript 规则，适合作为“自然语言 -> GAALOPScript -> 多语言代码生成”的中间表示参考。

依据代码：

- `clucalc/src/main/antlr4/de/gaalop/clucalc/input/CluCalc.g4`
- `clucalc/src/main/java/de/gaalop/clucalc/input/CluVisitor.java`
- `clucalc/src/main/java/de/gaalop/clucalc/input/CluCalcCodeParser.java`
- `clucalc/src/main/java/de/gaalop/clucalc/input/GraphBuilder.java`

注意：本文档只记录当前解析器和 visitor 已经实现的功能。仅在 lexer 中定义、但 parser rule 未使用或 visitor 未实现语义的 token，不作为可用 GAALOPScript 规则记录。

## 1. 脚本结构与基础语句

| 操作名称 | GAALOPScript 里面的操作符定义 | 解释说明 | 示例 |
|---|---|---|---|
| 脚本根规则 | `statement* EOF` | 一个 GAALOPScript 文件由 0 条或多条语句组成。语句之间不需要额外分隔符，普通语句以分号结束。 | `a = b + c;`<br>`?a;` |
| 空语句 | `;` | 空语句，不产生实际计算。 | `;` |
| 普通赋值 | `variable = expression;` | 将表达式赋值给变量，生成普通赋值节点。 | `a = b + c;` |
| 输出变量 | `?variable;` | 将已有变量标记为输出结果。这里的 `?` 只能用于变量或赋值语句，不能直接输出任意裸表达式。 | `?p;` |
| 输出赋值 | `?variable = expression;` | 先执行赋值，再将左侧变量标记为输出结果。 | `?p = x * e1 + y * e2;` |
| 可视化变量 | `:variable;` | 将变量加入可视化表达式列表，供 visualizer/codegen 使用。 | `:circle;` |
| 可视化赋值 | `:variable = expression;` | 先执行赋值，再将左侧变量加入可视化表达式列表。 | `:circle = p1 ^ p2 ^ p3;` |
| 仅求值赋值 | `!variable = expression;` | 将赋值加入 only-evaluate 节点列表，用于表示该赋值只需要求值。 | `!tmp = a * b;` |

## 2. 表达式规则

| 操作名称 | GAALOPScript 里面的操作符定义 | 解释说明 | 示例 |
|---|---|---|---|
| 括号表达式 | `(expression)` | 用括号组合表达式，改变表达式结构。 | `a = (b + c) * d;` |
| 加法 | `left + right` | 构造加法表达式。 | `a = b + c;` |
| 减法 | `left - right` | 构造减法表达式。 | `a = b - c;` |
| 乘法 / 几何积 | `left * right` | 构造乘法表达式。在几何代数场景中，后续代数阶段通常将其作为几何积处理。 | `m = a * b;` |
| 除法 | `left / right` | 构造除法表达式。 | `x = numerator / denominator;` |
| 外积 | `left ^ right` | 构造外积表达式。 | `line = p1 ^ p2;` |
| 内积 | `left . right` | 构造内积表达式。 | `s = a . b;` |
| Reverse | `~expression` | 构造 reverse 表达式。 | `r_reverse = ~r;` |
| Dual | `*expression` | 一元 `*` 表示 dual。当前实现会将其转成名称为 `*` 的宏调用。 | `dual_sphere = *sphere;` |
| 一元取负 | `-primary` | 对变量、常量、宏参数等 primary expression 取负。 | `a = -b;` |
| 括号表达式取负 | `-(expression)` | 对括号中的完整表达式取负。 | `a = -(b + c);` |
| 函数 / 宏调用取负 | `-name(args)` | 对函数或宏调用结果取负。 | `a = -sin(t);` |
| 函数 / 宏调用 | `name()`<br>`name(arg1, arg2, ...)` | 调用函数或宏。解析阶段统一生成 `MacroCall`，后续阶段再决定如何展开或处理。 | `p = createPoint(x, y, z);`<br>`n = Normalize(v);` |
| 宏参数引用 | `_P(index)` | 在宏定义中引用第 `index` 个参数。`index` 是十进制整数。 | `_P(1)` |
| 变量引用 | `IDENTIFIER` | 引用变量。未在左侧定义过但被表达式使用的变量会在后续处理中被识别为输入变量。 | `a = x + y;` |
| 整数常量 | `DECIMAL_LITERAL` | 十进制整数常量。内部会构造浮点常量节点。 | `a = 1;` |
| 浮点常量 | `FLOATING_POINT_LITERAL` | 浮点常量，支持小数、科学计数法、`f`/`d` 后缀。 | `a = 1.0;`<br>`b = .5;`<br>`c = 1e-3;`<br>`d = 2.0f;` |

## 3. 标识符规则

| 操作名称 | GAALOPScript 里面的操作符定义 | 解释说明 | 示例 |
|---|---|---|---|
| 普通标识符 | `LETTER (LETTER \| DIGIT)*` | 标识符必须以 `A-Z`、`a-z`、`_` 或 `$` 开头，后续可以包含字母或数字。 | `x`<br>`point1`<br>`_tmp`<br>`$value` |
| 全局访问标识符 | `::LETTER (LETTER \| DIGIT)*` | 标识符可以带 `::` 前缀。`Variable` 类会记录这是 global access，并去掉变量名中的 `::` 前缀。 | `::globalValue` |
| 非法变量名限制 | `length` | `length` 被 `GraphBuilder` 标为非法变量名，因为它在 Maxima 中受保护。 | 不要写：`length = 3;` |

## 4. 宏规则

| 操作名称 | GAALOPScript 里面的操作符定义 | 解释说明 | 示例 |
|---|---|---|---|
| 宏定义 | `IDENTIFIER = { statement* return_expression? }` | 定义一个宏。宏体可以包含多条语句，末尾可以直接放一个表达式作为返回值。 | `Normalize = { _P(1) / abs(_P(1)) }` |
| 宏体赋值 | `variable = expression;` | 宏内部可以包含普通赋值。宏体中的赋值会被收集到宏定义节点中。 | `Rotor = { half = _P(1) / 2; cos(half) + sin(half) * _P(2) }` |
| 宏返回表达式 | `expression` | 宏定义的 `}` 前可以放一个不带分号的表达式，作为宏返回值。 | `Dual = { *(_P(1)) }` |
| 宏参数 | `_P(1)`、`_P(2)`、... | 宏使用 `_P(n)` 引用调用时传入的第 n 个参数。 | `Scale = { _P(1) * _P(2) }` |
| 宏调用 | `MacroName(args)` | 调用已定义宏。解析阶段只生成宏调用表达式，是否存在对应宏由后续处理阶段检查。 | `p = createPoint(x, y, z);` |
| 宏定义作用域 | 只能在全局作用域定义 | 当前实现不允许在宏内部再定义宏。嵌套宏会报 parser error。 | 合法：`M = { _P(1) }` |

## 5. 颜色与可视化辅助规则

| 操作名称 | GAALOPScript 里面的操作符定义 | 解释说明 | 示例 |
|---|---|---|---|
| RGB 前景色 | `:Color(r, g, b);` | 添加颜色节点。`r`、`g`、`b` 是表达式，通常使用 `0` 到 `1` 的数值。 | `:Color(1, 0, 0);` |
| RGBA 前景色 | `:Color(r, g, b, a);` | 添加带 alpha 通道的颜色节点。 | `:Color(1, 0, 0, 0.5);` |
| 命名前景色 | `:ColorName;` | 使用内置命名颜色。颜色名大小写固定。 | `:Red;` |
| 支持的命名颜色 | `Black`、`Blue`、`Cyan`、`Green`、`Magenta`、`Orange`、`Red`、`White`、`Yellow` | 当前实现中可用的命名颜色集合。 | `:Yellow;` |
| RGB 背景色 | `_BGColor = Color(r, g, b);` | 设置背景色。 | `_BGColor = Color(1, 1, 1);` |
| RGBA 背景色 | `_BGColor = Color(r, g, b, a);` | 设置带 alpha 通道的背景色。 | `_BGColor = Color(0, 0, 0, 0.8);` |

## 6. 注释规则

| 操作名称 | GAALOPScript 里面的操作符定义 | 解释说明 | 示例 |
|---|---|---|---|
| 单行注释 | `// text` | 从 `//` 到行尾的内容会被忽略。 | `// create a point` |
| 块注释 | `/* text */` | 块注释内容会被忽略，可以跨越多行。 | `/* temporary calculation */` |

## 7. Pragma 规则

`pragma` 在进入 ANTLR parser 之前由 `CluCalcCodeParser` 逐行预处理。当前支持两种前缀：

```gaalop
//#pragma ...
#pragma ...
```

| 操作名称 | GAALOPScript 里面的操作符定义 | 解释说明 | 示例 |
|---|---|---|---|
| 输出分量声明 | `#pragma output variable blade1 blade2 ...` | 将指定变量的若干 blade 标记为输出。代码中会把第一个字段作为变量名，后续字段逐个加入输出集合。 | `#pragma output p e1 e2 e3` |
| 仅求值变量声明 | `#pragma onlyEvaluate variable1 variable2 ...` | 将变量名加入 only-evaluate 变量集合。 | `#pragma onlyEvaluate a b c` |
| 输入输出签名 | `#pragma in2out input1,input2 -> output1,output2` | 指定脚本的输入输出签名。必须包含且只包含一个 `->`。左右两侧用逗号分隔变量。 | `#pragma in2out x,y,z -> p` |
| 可视化线段 | `#pragma segments A B, C D, ...` | 声明可视化线段。每组必须包含两个变量名。 | `#pragma segments p1 p2, p3 p4` |
| 可视化三角形 | `#pragma triangles A B C, D E F, ...` | 声明可视化三角形。每组必须包含三个变量名。 | `#pragma triangles p1 p2 p3` |
| 输入变量范围 | `#pragma range min <= variable <= max` | 为变量设置最小值和最大值。格式必须是三段式 `min <= variable <= max`。 | `#pragma range 0 <= t <= 1` |
| 插入文本 | `#pragma insert text` | 将 `text` 加入 graph 的 insertionTexts，供后续 codegen 使用。 | `#pragma insert // generated helper` |
| 返回对象定义 | `#pragma return vars typed Type as expressionText` | 定义返回对象的变量列表、目标类型和返回表达式文本。 | `#pragma return p5,p6 typed Vector2 as Vector2(x, y)` |
| 输出归一化开关 | `#pragma normalize` | 开启后，后续匹配 `?x = expression` 的行会被改写为先计算 `x_unnormalized = expression`，再输出 `x = x_unnormalized / abs(x_unnormalized)`。 | `#pragma normalize` |

## 8. 常用生成模式示例

### 8.1 点生成

```gaalop
#pragma in2out x,y,z -> p

createPoint = {
    _P(1) * e1 + _P(2) * e2 + _P(3) * e3
}

?p = createPoint(x, y, z);
```

### 8.2 归一化输出

```gaalop
#pragma normalize

?n = a + b;
```

预处理后等价于：

```gaalop
n_unnormalized = a + b;
?n = n_unnormalized / abs(n_unnormalized);
```

### 8.3 可视化对象

```gaalop
:Red;
:line = p1 ^ p2;

_BGColor = Color(1, 1, 1);
```

### 8.4 宏作为中间表示

```gaalop
ScaleAndAdd = {
    scaled = _P(1) * _P(2);
    scaled + _P(3)
}

?result = ScaleAndAdd(v, s, offset);
```

## 9. 当前不纳入可用规则表的内容

以下内容在 lexer 或其他旧代码中可以看到痕迹，但当前 GAALOPScript parser/visitor 没有形成完整可用实现，因此不建议作为自然语言代码生成器的目标规则：

- `%`
- `[]`
- `!!`
- `||`
- `&&`
- `==`
- `!=`
- `<`
- `>`
- `<=` 和 `>=` 作为普通表达式比较
- 字符串字面量
- `Slider`
- `range` 关键字形式
- `unroll`
- `count`
- `if` / `else`
- `loop` / `break`
- `:IPNS` / `:OPNS` 的实际语义处理

其中 `<=`、`->` 等符号可能在特定 pragma 文本中出现，例如 `#pragma range` 和 `#pragma in2out`，但它们不是普通表达式语法的一部分。
