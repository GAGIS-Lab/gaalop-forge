# GACRAC GAALOPScript Vue + Spring Compiler

[![Language](https://img.shields.io/badge/Language-English-green)](./README.md)
[![语言](https://img.shields.io/badge/语言-中文-blue)](./README.zh-CN.md)

GACRAC GAALOPScript Vue + Spring Compiler is an integrated web application for editing, compiling, and visualizing GAALOPScript. It combines a Vue 3 + Vite + Ant Design Vue frontend with a Spring Boot 3 backend built on GAALOP, the Geometric Algebra Algorithms Optimizer.

The application is designed as a browser-based geometric algebra workflow:

```text
GAALOPScript editing -> Spring Boot compile API -> target-language code + ganja.js visualization preview
```

## Highlights

- Modern Vue 3 online editing UI with Ant Design Vue.
- Monaco-based GAALOPScript editor with syntax highlighting and completion hints.
- Spring Boot REST backend powered by GAALOP compiler modules.
- Single public compile endpoint: `POST /api/v1/compile`.
- Supports multiple algebra spaces and code generators.
- Returns generated target code and visualization core script separately.
- Frontend wraps the visualization script into a customizable ganja.js preview.
- Swagger UI supports English, Chinese, and German documentation groups.
- Swagger access is protected by HTTP Basic authentication.
- Docker image packages frontend, backend, Nginx, Java runtime, and Maxima together.

## Quick Start With Docker

Build and start the integrated container:

```bash
docker compose up -d --build
```

Open the web app:

```text
http://localhost:18080/
```

Useful container commands:

```bash
docker compose ps
docker logs -f gaalop-vue-spring-compiler
docker compose down
```

The Docker image name is:

```text
gacrac/gaalop-vue-spring-compiler:1.0
```

## Swagger Docs

Swagger UI:

```text
http://localhost:18080/swagger-ui.html
```

Swagger requires login:

```text
Username: GACRAC
Password: GAGIS
```

The Swagger definition selector includes:

```text
English
中文
Deutsch
```

Direct OpenAPI JSON endpoints:

```text
http://localhost:18080/v3/api-docs/english
http://localhost:18080/v3/api-docs/chinese
http://localhost:18080/v3/api-docs/german
```

## REST API

### Compile GAALOPScript

```http
POST /api/v1/compile
Content-Type: application/json
```

Example request:

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

Response fields:

| Field | Description |
|---|---|
| `statusCode` | Business status code. `200` means success. |
| `message` | Human-readable response message. |
| `optimizeResult` | Generated target-language source code. |
| `visualizationCode` | Core ganja.js visualization script. The frontend wraps it into HTML. |

## Supported Values

### Algebra Spaces

Request field: `algebraPlugins`

| Enum | GAALOP internal id | Description |
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

### Code Generators

Request field: `codegenPlugins`

| Enum | Plugin name |
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

### Output Modes

Request field: `outputMode`

| Enum | Description |
|---|---|
| `CODE_ONLY` | Return generated target code only. |
| `CODE_AND_VISUALIZATION` | Return generated code and visualization script. |
| `VISUALIZATION_ONLY` | Return visualization script only. |

## Local Development

Run backend:

```bash
mvn -pl gaalop-rest -am -DskipTests package
java -jar gaalop-rest/target/gaalop-rest-1.0.0.jar --server.port=18080
```

Run frontend:

```bash
cd frontend
pnpm install
pnpm dev
```

Frontend dev server:

```text
http://localhost:5173/
```

The Vite dev server proxies `/api` to `http://localhost:18080`.

## Project Structure

```text
frontend/                     Vue 3 + Vite + Ant Design Vue web UI
gaalop-rest/                  Spring Boot 3 compile API
docker/                       Nginx and container startup scripts
clucalc/                      GAALOPScript parser and visitor implementation
algebra/                      Built-in geometric algebra definitions
tba/                          Default optimization pipeline
gapp/                         GAPP optimization pipeline
codegen-*/                    Target language code generator plugins
visualCodeInserter/           Traditional visualization code inserter
ganjaVisualCodeInserter/      Ganja visualization code inserter
GAALOPScript_Rules.md         Implemented GAALOPScript syntax reference
Dockerfile                    Integrated frontend + backend image definition
docker-compose.yml            Docker Compose startup configuration
```

## License

The original GAALOP project is licensed under LGPL 3.0. Please comply with the license terms when redistributing or modifying this project.
