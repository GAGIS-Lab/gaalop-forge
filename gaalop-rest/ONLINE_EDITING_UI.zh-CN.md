# GAALOP 在线编辑页操作逻辑说明

这个页面是基于 `gaalop-rest` API 的单页在线编辑原型，目标是把“编译参数配置 + 脚本编辑 + 结果查看”放进一个更现代的工作台里。

## 页面结构

- 顶部导航：
  `Home`、`Online Editing` 和用户信息。
- 左侧功能栏：
  `Code`、`History`、`Help`。
- 顶部参数条：
  `Run`、`Algebra Type`、`Code Generation`、`Optimization`、`Output Mode`、`Function Name`。
- 中部编辑区：
  左侧为主编辑器 `Code to Optimize`；
  右侧上方为 `Variable Assignments`；
  右侧下方为 `Multivectors to be Visualized`。
- 底部结果区：
  左侧 `Generated Code`；
  右侧 `Visualization Preview`。

## 操作逻辑

### 1. 初始加载

- 页面启动后请求 `GET /api/v1/options`。
- 成功时，用后端返回的枚举填充：
  `Algebra Type`、`Code Generation`、`Output Mode`。
- 如果接口暂时不可用，页面会回退到内置默认选项，保证设计稿和原型仍然可以演示。

### 2. 参数与草稿状态

- 用户修改任一配置项或编辑器内容后，页面进入“草稿已修改”状态。
- 状态文案会提示用户重新运行以刷新结果。
- `Function Name` 会做即时校验：
  为空或格式非法时，输入框进入错误态，并禁止点击 `Run`。

### 3. 运行编译

- 点击 `Run Compilation` 后，页面向 `POST /api/v1/compile` 发送请求。
- 请求体映射关系如下：
  - `Algebra Type` -> `algebraPlugins`
  - `Code Generation` -> `codegenPlugins`
  - `Output Mode` -> `outputMode`
  - `Optimization` -> `optimization.cse` 与 `optimization.maxima`
  - `Function Name` -> `script.functionName`
  - `Code to Optimize` -> `script.optimizeCode`
  - `Variable Assignments` -> `script.variableAssignments`
  - `Multivectors to be Visualized` -> `script.multivectorsVisualized`
- 在运行过程中：
  - `Run` 按钮进入 loading 状态
  - 顶部状态栏显示“正在编译”

### 4. 成功结果

- 编译成功后：
  - 左侧 `Generated Code` 显示 `optimizeResult`
  - 右侧 `Visualization Preview` 显示 `visualizationCode`
- 如果 `visualizationCode` 是 HTML，会直接放进 iframe 预览。
- 成功状态会清除“草稿已修改”标记。

### 5. 失败结果

- 编译失败时：
  - 页面显示错误提示条
  - 编辑区内容全部保留
  - 如果之前有成功结果，页面继续保留上一次成功输出，不会被失败结果清空

### 6. Output Mode 的界面逻辑

- `CODE_ONLY`：
  左侧代码结果区高亮，右侧预览区弱化。
- `VISUALIZATION_ONLY`：
  右侧预览区高亮，左侧代码结果区弱化。
- `CODE_AND_VISUALIZATION`：
  左右两个结果区都高亮显示。

## 当前实现边界

- 本轮只实现 `Online Editing` 主页面。
- `History` 和 `Help` 保留导航入口，但不展开完整业务页面。
- `Visualization Plugin` 已从页面移除，因为后端 API 没有这个配置入口。
- 页面重点是“高保真可运行原型”，后续如果要继续产品化，可以再补：
  - 历史记录面板
  - 真正的 Home 页面
  - Monaco 编辑器
  - 中英文切换
  - 更完整的运行日志和错误定位
