# GACRAC GAALOPScript Vue + Spring 编译器

[![Language](https://img.shields.io/badge/Language-English-green)](./README.md)
[![语言](https://img.shields.io/badge/语言-中文-blue)](./README.zh-CN.md)

GACRAC GAALOPScript Vue + Spring 编译器是一个用于编辑、编译和可视化 GAALOPScript 的一体化 Web 应用。它由 Vue 3 + Vite + Ant Design Vue 前端和基于 GAALOP 的 Spring Boot 3 后端组成。

整体工作流如下：

```text
编辑 GAALOPScript -> Spring Boot 编译接口 -> 目标语言代码 + ganja.js 可视化预览
```

## 功能概览

- 现代化 Vue 3 在线编辑页面，使用 Ant Design Vue。
- 基于 Monaco 的 GAALOPScript 编辑器，支持语法高亮和智能提示。
- Spring Boot REST 后端，底层调用 GAALOP 编译模块。
- 只保留一个业务编译接口：`POST /api/v1/compile`。
- 支持多种几何代数空间和代码生成器。
- 代码结果和可视化核心脚本分离返回。
- 前端负责把可视化核心脚本拼接成可定制的 ganja.js 预览页面。
- Swagger UI 支持英文、中文、德文三个文档分组。
- Swagger 访问已启用 HTTP Basic 用户名密码保护。
- Docker 镜像内置前端、后端、Nginx、Java 运行时和 Maxima。

## Docker 快速启动

构建并启动一体化容器：

```bash
docker compose up -d --build
```

访问 Web 应用：

```text
http://localhost:18080/
```

常用容器命令：

```bash
docker compose ps
docker logs -f gaalop-vue-spring-compiler
docker compose down
```

Docker 镜像名：

```text
gacrac/gaalop-vue-spring-compiler:1.0
```

## Swagger 文档

Swagger UI：

```text
http://localhost:18080/swagger-ui.html
```

Swagger 登录账号：

```text
用户名：GACRAC
密码：GAGIS
```

Swagger 页面顶部可以切换：

```text
English
中文
Deutsch
```

OpenAPI JSON 地址：

```text
http://localhost:18080/v3/api-docs/english
http://localhost:18080/v3/api-docs/chinese
http://localhost:18080/v3/api-docs/german
```

## REST API

### 编译 GAALOPScript

```http
POST /api/v1/compile
Content-Type: application/json
```

请求示例：

```json
{
  "algebraPlugins": "ALGEBRA_CGA",
  "codegenPlugins": "JAVA",
  "outputMode": "CODE_AND_VISUALIZATION",
  "visualizationEnabled": true,
  "optimization": {
    "cse": false,
    "maxima": false
  },
  "script": {
    "functionName": "threespheres",
    "optimizeCode": "?x1=createPoint(a1,a2,a3);\n?S1=x1-0.5*(d14*d14)*einf;",
    "variableAssignments": "a1=0; a2=0; a3=0; d14=0.5;",
    "multivectorsVisualized": ":Blue;\n:S1;"
  }
}
```

响应字段：

| 字段 | 说明 |
|---|---|
| `statusCode` | 业务状态码，`200` 表示成功。 |
| `message` | 响应说明。 |
| `optimizeResult` | 生成的目标语言代码。 |
| `visualizationCode` | 核心 ganja.js 可视化脚本，前端负责拼接成 HTML。 |

## 支持的枚举

### 几何代数空间

请求字段：`algebraPlugins`

| 枚举值 | GAALOP 内部 id | 说明 |
|---|---|---|
| `ALGEBRA_2D` | `2d` | imaginary numbers |
| `ALGEBRA_3D` | `3d` | euclidean geometric algebra |
| `ALGEBRA_2D_PGA` | `2dpga` | 2D projective geometric algebra |
| `ALGEBRA_3D_PGA` | `3dpga` | 3D projective geometric algebra |
| `ALGEBRA_CRA` | `cra` | compass ruler algebra |
| `ALGEBRA_STA` | `sta` | space-time algebra |
| `ALGEBRA_CGA` | `cga` | conformal geometric algebra |
| `ALGEBRA_GAC` | `gac` | geometric algebra for conics |
| `ALGEBRA_DCGA` | `dcga` | double conformal geometric algebra |
| `ALGEBRA_CCGA` | `ccga` | cubic CGA |
| `ALGEBRA_QGA` | `qga` | quantum bit geometric algebra |

### 代码生成器

请求字段：`codegenPlugins`

| 枚举值 | 插件名称 |
|---|---|
| `CLUCALC` | GAALOPScript |
| `JULIA` | Julia |
| `VERILOG` | Verilog |
| `GAPP_DEBUGGER` | Gapp Debugger |
| `CSHARP` | C# |
| `RUST` | Rust |
| `JAVA` | Java |
| `VIS2D` | Vis2d |
| `GAALET_OUTPUT` | C/C++ (gaalet) |
| `GAPP` | GAPP CodeGenerator |
| `COMPRESSED` | compressed C/C++ |
| `VISUALIZER` | Visualizer |
| `GANJA` | Ganja |
| `GAPP_OPENCL` | GAPP OpenCL |
| `PYTHON` | Python |
| `MATLAB` | MATLAB |
| `DOT` | Graphviz DOT |
| `MATHEMATICA` | Mathematica |
| `CPP` | C/C++ |
| `LATEX` | LaTeX |

### 输出模式

请求字段：`outputMode`

| 枚举值 | 说明 |
|---|---|
| `CODE_ONLY` | 只返回生成的目标代码。 |
| `CODE_AND_VISUALIZATION` | 同时返回生成代码和可视化脚本。 |
| `VISUALIZATION_ONLY` | 只返回可视化脚本。 |

## 本地开发

启动后端：

```bash
mvn -pl gaalop-rest -am -DskipTests package
java -jar gaalop-rest/target/gaalop-rest-1.0.0.jar --server.port=18080
```

启动前端：

```bash
cd frontend
pnpm install
pnpm dev
```

前端开发地址：

```text
http://localhost:5173/
```

Vite 开发服务器会把 `/api` 代理到 `http://localhost:18080`。

## 项目结构

```text
frontend/                     Vue 3 + Vite + Ant Design Vue 前端
gaalop-rest/                  Spring Boot 3 编译接口
docker/                       Nginx 和容器启动脚本
clucalc/                      GAALOPScript parser 和 visitor 实现
algebra/                      内置几何代数空间定义
tba/                          默认优化流程
gapp/                         GAPP 优化流程
codegen-*/                    各目标语言代码生成插件
visualCodeInserter/           传统可视化代码插入器
ganjaVisualCodeInserter/      Ganja 可视化代码插入器
GAALOPScript_Rules.md         已实现 GAALOPScript 语法规则文档
Dockerfile                    前后端一体化 Docker 镜像构建文件
docker-compose.yml            Docker Compose 启动配置
```

## License

原 GAALOP 项目采用 LGPL 3.0。请在分发和修改时遵守对应许可证要求。
