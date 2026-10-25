# GAALOP Forge

GAALOP Forge brings the [GAALOP compiler](https://github.com/CallForSanity/Gaalop) to the browser. Write GAALOPScript, generate source code, and inspect geometric objects or quantum-state probabilities.

The project has two workbenches:

| Workbench | Entry | Use |
| --- | --- | --- |
| GAALOPWeb | `/gaalop/` | Geometric algebra compilation and ganja.js previews |
| QuantumGAALOP | `/quantumgaalop/` | QCA, QRA and quantum bit geometric algebra |

Both pages share a Spring Boot API. Each page has its own startup module and browser drafts.

![GAALOPWeb](docs/gaalop-online-editor.png)

## Run with Docker

Version **1.2.1** preserves constant-zero outputs across language backends and clears caller-owned output arrays. It includes the two workbenches, MATLAB symbolic arrays, Python numeric math, and QCA up to five qubits.

```bash
docker pull gagislab/gaalop-forge:1.2.1
docker run -d --name gaalop-forge -p 18080:8080 \
  -e GAALOP_SWAGGER_USERNAME=admin \
  -e GAALOP_SWAGGER_PASSWORD=change-me \
  gagislab/gaalop-forge:1.2.1
```

| Service | URL |
| --- | --- |
| GAALOPWeb | <http://localhost:18080/gaalop/> |
| QuantumGAALOP | <http://localhost:18080/quantumgaalop/> |
| Health | <http://localhost:18080/api/v1/health> |
| Swagger UI | <http://localhost:18080/swagger-ui.html> |

Swagger uses HTTP Basic authentication. Set its username and password through the environment or a Git-ignored `.env` file. The image includes Java 17, Nginx, Maxima and Linux x86_64 QRA libraries.

To build from source:

```bash
git clone https://github.com/GAGIS-Lab/gaalop-forge.git
cd gaalop-forge
cp .env.example .env
docker compose up -d --build
```

[Docker build and release](docs/docker-release.md) covers source builds, artifact assembly and verification.

## Use the workbenches

1. Select the algebra, output language and output mode.
2. Enter GAALOPScript in **Code to Optimize**, or use **Open** to load a `.gs` file. The file name sets the function name.
3. Enter numeric values in **Variable Assignments** and select states or objects in **Multivectors to be Visualized**.
4. Choose **Run**. The lower panels show generated code and visualization results.

**Save** writes to an authorized file when the browser supports native file access; other browsers download a copy. Generated files use the function name and language extension. The file picker controls their destination. **Copy code** copies generated text to the clipboard.

### Quantum modes

| Mode | Qubits | Generated code | Visualization |
| --- | --- | --- | --- |
| QCA | 1–5 | Functions retain free parameters | Basis probabilities and amplitudes |
| QRA | 2–9 | Constants from native numeric evaluation | Basis probabilities and amplitudes |
| quantum bit geometric algebra | Bundled fixed algebra | TBA code generation | Code generation mode |

QuantumGAALOP starts with QCA for a new workspace and restores the algebra of an existing draft. Mark a state with `:psi;`, using the variable name from your script. QCA uses **Variable Assignments** for visualization; QRA uses those values for numeric execution in every output mode. Probability displays include the total probability and projection residual.

The quantum toolbar offers 13 output generators, including MATLAB, Python, Java, C/C++, C#, Rust, Julia and Mathematica. The MATLAB generator supports numeric and symbolic scalar inputs, intermediate arrays and multiple outputs. Python uses standard-library math functions for scalar code and NumPy for array output.

Capacity measurements use specific scripts and single requests. See [QCA expansion](docs/qca-qubit-expansion.md), [QRA nine-qubit measurements](docs/qra-9qubit-memory.md) and [resource-script tests](docs/qra-resource-script-tests.md) for workloads, memory and timings.

### Traditional optimization

GAALOPWeb exposes TBA, CSE and Maxima options. CSE removes repeated expressions; Maxima performs symbolic simplification. For a local Maxima installation, set `GAALOP_MAXIMA_COMMAND` or pass `--gaalop.maxima.command=/path/to/maxima` when starting Java.

## Develop locally

Use JDK 17, Maven 3.9+, Node.js 22 and pnpm 10.25.0.

```bash
mvn -pl gaalop-rest -am package
java -jar gaalop-rest/target/gaalop-rest-1.0.0.jar
```

The standalone service listens on `8080` with context path `/gaalop`. Its health URL is <http://localhost:8080/gaalop/api/v1/health>.

In `frontend/`:

```bash
pnpm install --frozen-lockfile
pnpm dev
```

Open <http://localhost:5173/gaalop/> or <http://localhost:5173/quantumgaalop/>. Vite proxies API requests to the standalone backend. Set `VITE_API_TARGET` to change its host or port.

`pnpm build` creates the combined site in `frontend/dist/`. `pnpm build:sites` creates self-contained sites in `frontend/dist-sites/gaalop/` and `frontend/dist-sites/quantumgaalop/`. See [two-site deployment](docs/two-sites-deployment.md).

The default Maven reactor builds the Web service and shared compiler tests. Use `-Pdesktop` to build legacy desktop entry points and distribution. Shared mathematical tests still use `codegen-visualizer`; the REST JAR excludes its desktop runtime. See [build boundaries](docs/desktop-web-gap-review.md).

### REST example

For the Docker deployment, send this request to `http://localhost:18080/api/v1/compile`. For standalone Java, use `http://localhost:8080/gaalop/api/v1/compile`.

```json
{
  "algebraPlugins": "ALGEBRA_CGA",
  "codegenPlugins": "MATLAB",
  "outputMode": "CODE_AND_VISUALIZATION",
  "optimization": { "cse": false, "maxima": false },
  "script": {
    "functionName": "sphereDemo",
    "optimizeCode": "?S=createPoint(a,b,c)-0.5*r*r*einf;",
    "variableAssignments": "a=0;b=0;c=0;r=0.5;",
    "multivectorsVisualized": ":Blue;\n:S;"
  }
}
```

`optimizeResult` contains generated code; `visualizationCode` contains the geometric preview script. Quantum results include amplitudes, probabilities, total probability and residual. Swagger documents the request and response fields in English, Chinese and German.

## Tests and maintenance

```bash
mvn clean test
cd frontend
node scripts/test-quantum.mjs
node scripts/test-file-access.mjs
node scripts/test-script-file.mjs
node scripts/test-workspace-import.mjs
node scripts/test-workspace-navigation.mjs
pnpm build
```

For QRA integration tests, pass `-Dgaalop.garamon.nativeDir=<native-library-directory>`. [Native maintenance](docs/qra-acceleration-maintenance.md) covers Linux builds and the C ABI. Compiler tests use JUnit 4; the MATLAB and Python folders also contain executable target-language fixtures.

The systemd health probe uses `/gaalop/api/v1/health`, waits through startup and retries transient failures. It reports URL/access errors as configuration issues and uses a normal service restart after repeated connection or server failures. Installation and verification steps are in [server deployment](deploy/ubuntu/README.md).

Compile history is opt-in through `GAALOP_COMPILE_HISTORY_ENABLED=true`. History files contain submitted scripts and generated results.

## Project layout

| Directory | Contents |
| --- | --- |
| `frontend/` | Vue 3, Ant Design Vue and Monaco workbenches |
| `gaalop-rest/` | Spring Boot API |
| `api/`, `clucalc/`, `algebra/` | Compiler model, parser and algebra definitions |
| `tba/`, `gapp/`, `codegen-*/` | Optimization and output generators |
| `native/qra/`, `garamon/` | QRA native libraries and Java bridge |
| `testbenchTbaGapp/` | Compiler regression tests |
| `docker/`, `deploy/` | Image assembly and server deployment |

More: [GAALOPScript rules](GAALOPScript_Rules.md), [language reference](docs/gaalopscript-analysis/02-完整语言参考.md), [MATLAB compatibility](docs/matlab-symbolic-codegen.md), [Python compatibility](docs/python-codegen-compatibility.md), [Open/Save](docs/browser-open-save.md), [1.2.1 changes](docs/releases/1.2.1.md).

## Acknowledgments and license

Wang Jian developed this project at the Nanjing Geometric Algebra Computing Research and Application Center, under the guidance of Yu Zhaoyuan, Luo Wen and Dietmar Hildenbrand.

The project extends GAALOP and uses LGPL 3.0. Component licenses are listed in [LICENSE](LICENSE), [LICENSE_ganjajs](LICENSE_ganjajs), [LICENSE_iamath](LICENSE_iamath) and [NOTICE.md](NOTICE.md).
