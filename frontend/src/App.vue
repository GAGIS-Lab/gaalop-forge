<template>
  <div ref="pageRoot" class="editor-page">
    <header ref="topbarRef" class="topbar">
      <div class="topbar-brand">Gaalop</div>
      <nav class="topbar-nav">
        <button class="nav-item nav-button" :class="{ active: activeView === 'home' }" type="button" @click="showFuturePage('home')">Home</button>
        <button class="nav-item nav-button" :class="{ active: activeView === 'online' }" type="button" @click="showOnlineEditing">Online Editing</button>
        <button class="nav-item nav-button" :class="{ active: activeView === 'agent' }" type="button" @click="showFuturePage('agent')">GA-CodeAgent</button>
      </nav>
      <div class="topbar-actions">
        <div class="last-run-chip" :class="{ error: runState.kind === 'error' }">
          <CheckOutlined v-if="runState.kind !== 'error'" />
          <CloseCircleOutlined v-else />
          <span>{{ lastRunText }}</span>
        </div>
        <div class="account-chip">
          <a-avatar :size="36" class="account-avatar">
            <template #icon><UserOutlined /></template>
          </a-avatar>
          <span>GACRAC</span>
          <DownOutlined />
        </div>
      </div>
    </header>

    <div ref="pageBodyRef" class="page-body" :class="{ 'sidebar-collapsed': sidebarCollapsed }">
      <aside class="sidebar">
        <button class="sidebar-item" :class="{ active: activeView === 'online' }" type="button" :title="sidebarCollapsed ? 'Code' : undefined" @click="showOnlineEditing">
          <FileTextOutlined />
          <span class="sidebar-label">Code</span>
        </button>
        <button class="sidebar-item" :class="{ active: activeView === 'history' }" type="button" :title="sidebarCollapsed ? 'History' : undefined" @click="showFuturePage('history')">
          <HistoryOutlined />
          <span class="sidebar-label">History</span>
        </button>
        <button class="sidebar-item" :class="{ active: activeView === 'help' }" type="button" :title="sidebarCollapsed ? 'Help' : undefined" @click="showFuturePage('help')">
          <QuestionCircleOutlined />
          <span class="sidebar-label">Help</span>
        </button>

        <button class="collapse-link" type="button" :title="sidebarCollapsed ? 'Expand' : 'Collapse'" @click="toggleSidebar">
          <DoubleLeftOutlined />
          <span class="collapse-label">{{ sidebarCollapsed ? "Expand" : "Collapse" }}</span>
        </button>
      </aside>

      <main v-show="activeView === 'online'" class="workspace">
        <section ref="toolbarPanelRef" class="toolbar-panel">
          <a-row :gutter="[18, 16]" align="middle">
            <a-col :xxl="3" :xl="4" :lg="4" :md="24" :sm="24" :xs="24">
              <div class="run-stack">
                <button class="run-button" type="button" :disabled="running" @click="runCompilation">
                  <LoadingOutlined v-if="running" />
                  <CaretRightFilled v-else />
                  <span>{{ running ? "Running" : "Run" }}</span>
                </button>
                <div class="save-hint">{{ saveHint }}</div>
              </div>
            </a-col>

            <a-col :xxl="21" :xl="20" :lg="20" :md="24" :sm="24" :xs="24">
              <a-row :gutter="[18, 14]">
                <a-col :xxl="5" :xl="6" :lg="6" :md="12" :sm="24" :xs="24">
                  <label class="field-label">Algebra Type</label>
                  <a-select
                    v-model:value="form.algebraPlugins"
                    size="large"
                    class="toolbar-select"
                    :options="algebraOptions"
                    @change="markDirty"
                  />
                </a-col>
                <a-col :xxl="4" :xl="5" :lg="5" :md="12" :sm="24" :xs="24">
                  <label class="field-label">Code Generation</label>
                  <a-select
                    v-model:value="form.codegenPlugins"
                    size="large"
                    class="toolbar-select"
                    :options="codegenOptions"
                    @change="markDirty"
                  />
                </a-col>
                <a-col :xxl="4" :xl="5" :lg="5" :md="12" :sm="24" :xs="24">
                  <label class="field-label">Optimization</label>
                  <a-select
                    v-model:value="form.optimizationPreset"
                    size="large"
                    class="toolbar-select"
                    :options="optimizationOptions"
                    @change="markDirty"
                  />
                </a-col>
                <a-col :xxl="4" :xl="5" :lg="4" :md="12" :sm="24" :xs="24">
                  <label class="field-label">Output Mode</label>
                  <a-select
                    v-model:value="form.outputMode"
                    size="large"
                    class="toolbar-select"
                    :options="outputModeOptions"
                    @change="markDirty"
                  />
                </a-col>
                <a-col :xxl="4" :xl="3" :lg="4" :md="12" :sm="24" :xs="24">
                  <label class="field-label">Function Name</label>
                  <div class="function-input-wrap">
                    <a-input v-model:value="form.functionName" size="large" class="toolbar-input" @input="markDirty" />
                    <CheckOutlined v-if="functionNameValid" class="function-valid-icon" />
                    <CloseCircleOutlined v-else class="function-invalid-icon" />
                  </div>
                </a-col>
              </a-row>
            </a-col>
          </a-row>
        </section>

        <section class="editor-grid">
          <a-row :gutter="[16, 16]" class="editor-top-row">
            <a-col :span="24">
              <a-row :gutter="[16, 16]" class="editor-top-inner">
                <a-col :xxl="12" :xl="12" :lg="14" :md="24" :sm="24" :xs="24">
                  <article class="panel-card editor-card editor-card-large">
                    <div class="panel-header">
                      <div class="panel-title-group">
                        <div class="panel-title-line">
                          <h3>Code to Optimize</h3>
                          <InfoCircleOutlined />
                        </div>
                      </div>
                    </div>
                    <div class="monaco-editor-shell">
                      <div ref="mainEditorContainer" class="monaco-editor-host"></div>
                    </div>
                    <div class="panel-footer">
                      <span>Ln {{ mainCursor.line }}, Col {{ mainCursor.column }}</span>
                      <span>{{ mainLines.length }} lines</span>
                    </div>
                  </article>
                </a-col>

                <a-col :xxl="12" :xl="12" :lg="10" :md="24" :sm="24" :xs="24">
                  <a-row :gutter="[0, 16]" class="editor-right-stack">
                    <a-col :span="24">
                      <article class="panel-card editor-card editor-card-small">
                        <div class="panel-header">
                          <div class="panel-title-group">
                            <div class="panel-title-line">
                              <h3>Variable Assignments</h3>
                              <InfoCircleOutlined />
                            </div>
                          </div>
                        </div>
                        <div class="monaco-editor-shell monaco-editor-shell-compact">
                          <div ref="variableEditorContainer" class="monaco-editor-host"></div>
                        </div>
                        <div class="panel-footer">
                          <span>Ln {{ variableCursor.line }}, Col {{ variableCursor.column }}</span>
                          <span>{{ variableLines.length }} lines</span>
                        </div>
                      </article>
                    </a-col>

                    <a-col :span="24">
                      <article class="panel-card editor-card editor-card-small">
                        <div class="panel-header">
                          <div class="panel-title-group">
                            <div class="panel-title-line">
                              <h3>Multivectors to be Visualized</h3>
                              <InfoCircleOutlined />
                            </div>
                          </div>
                        </div>
                        <div class="monaco-editor-shell monaco-editor-shell-compact">
                          <div ref="multivectorEditorContainer" class="monaco-editor-host"></div>
                        </div>
                        <div class="panel-footer">
                          <span>Ln {{ multivectorCursor.line }}, Col {{ multivectorCursor.column }}</span>
                          <span>{{ multivectorLines.length }} lines</span>
                        </div>
                      </article>
                    </a-col>
                  </a-row>
                </a-col>
              </a-row>
            </a-col>
          </a-row>

          <div class="editor-alert-row">
            <a-alert :message="statusMessage" :type="statusAlertType" show-icon />
          </div>

          <a-row :gutter="[16, 16]" class="result-row">
            <a-col class="result-col" :xxl="12" :xl="12" :lg="12" :md="24" :sm="24" :xs="24">
              <article class="panel-card result-card">
                <div class="panel-header result-header">
                  <div class="result-title-cluster">
                    <div class="panel-title-line">
                      <h3>Generated Code</h3>
                      <InfoCircleOutlined />
                    </div>
                    <span class="success-badge" :class="{ muted: runState.kind !== 'success' }">
                      {{ runState.kind === 'success' ? 'Success' : 'Waiting' }}
                    </span>
                  </div>
                  <div class="result-tools">
                    <button class="icon-button" type="button" @click="copyText(displayedCode)">
                      <CopyOutlined />
                    </button>
                    <button class="icon-button" type="button" @click="downloadCode">
                      <DownloadOutlined />
                    </button>
                  </div>
                </div>
                <div class="code-surface result-surface">
                  <div class="line-gutter">
                    <div v-for="line in generatedLines.length" :key="`generated-${line}`">{{ line }}</div>
                  </div>
                  <pre class="code-block result-block"><code v-html="generatedCodeHtml"></code></pre>
                </div>
              </article>
            </a-col>

            <a-col class="result-col" :xxl="12" :xl="12" :lg="12" :md="24" :sm="24" :xs="24">
              <article class="panel-card preview-card">
                <div class="panel-header result-header">
                  <div class="panel-title-line">
                    <h3>Visualization Preview</h3>
                    <InfoCircleOutlined />
                  </div>
                  <div class="preview-tools">
                    <button class="icon-button" type="button" @click="previewZoomed = !previewZoomed">
                      <FullscreenOutlined />
                    </button>
                    <button class="icon-button" type="button" @click="runCompilation">
                      <ReloadOutlined />
                    </button>
                  </div>
                </div>
                <div class="preview-surface" :class="{ zoomed: previewZoomed }">
                  <iframe
                    v-if="displayedPreview"
                    class="preview-frame"
                    title="Visualization Preview"
                    :srcdoc="displayedPreview"
                  />
                  <div v-else class="preview-empty">
                    <div>No visualization output yet.</div>
                    <span>Run compilation in a visualization-capable mode to populate this area.</span>
                  </div>
                </div>
              </article>
            </a-col>
          </a-row>
        </section>
      </main>

      <main v-show="activeView !== 'online'" class="future-page">
        <section class="future-panel">
          <div class="future-heading">
            <div class="future-icon">
              <InfoCircleOutlined />
            </div>
            <div class="future-copy">
              <h1>{{ futurePage.title }}</h1>
              <p>{{ futurePage.description }}</p>
            </div>
          </div>
          <div class="future-detail">
            <h2>Planned capabilities</h2>
            <ul>
              <li v-for="item in futurePage.items" :key="item">{{ item }}</li>
            </ul>
          </div>
        </section>
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import {
  CaretRightFilled,
  CheckOutlined,
  CloseCircleOutlined,
  CopyOutlined,
  DoubleLeftOutlined,
  DownOutlined,
  DownloadOutlined,
  FileTextOutlined,
  FullscreenOutlined,
  HistoryOutlined,
  InfoCircleOutlined,
  LoadingOutlined,
  QuestionCircleOutlined,
  ReloadOutlined,
  UserOutlined
} from "@ant-design/icons-vue";

const algebraOptions = [
  { value: "ALGEBRA_2D", label: "2D geometric algebra" },
  { value: "ALGEBRA_3D", label: "3D geometric algebra" },
  { value: "ALGEBRA_2D_PGA", label: "2D projective geometric algebra" },
  { value: "ALGEBRA_3D_PGA", label: "3D projective geometric algebra" },
  { value: "ALGEBRA_CRA", label: "Conformal restricted algebra" },
  { value: "ALGEBRA_STA", label: "Spacetime algebra" },
  { value: "ALGEBRA_CGA", label: "Conformal geometric algebra" },
  { value: "ALGEBRA_GAC", label: "Geometric algebra for conics" },
  { value: "ALGEBRA_DCGA", label: "Double conformal geometric algebra" },
  { value: "ALGEBRA_CCGA", label: "Conformal conic geometric algebra" },
  { value: "ALGEBRA_QGA", label: "Quadric geometric algebra" }
];

const codegenOptions = [
  { value: "CLUCALC", label: "CLUCalc" },
  { value: "JULIA", label: "Julia" },
  { value: "VERILOG", label: "Verilog" },
  { value: "GAPP_DEBUGGER", label: "GAPP Debugger" },
  { value: "CSHARP", label: "C#" },
  { value: "RUST", label: "Rust" },
  { value: "JAVA", label: "Java" },
  { value: "VIS2D", label: "VIS2D" },
  { value: "GAALET_OUTPUT", label: "Gaalet Output" },
  { value: "GAPP", label: "GAPP" },
  { value: "COMPRESSED", label: "Compressed" },
  { value: "VISUALIZER", label: "Visualizer" },
  { value: "GANJA", label: "Ganja" },
  { value: "GAPP_OPENCL", label: "GAPP OpenCL" },
  { value: "PYTHON", label: "Python" },
  { value: "MATLAB", label: "MATLAB" },
  { value: "DOT", label: "DOT" },
  { value: "MATHematica", label: "Mathematica" },
  { value: "CPP", label: "C++" },
  { value: "LATEX", label: "LaTeX" }
];

const optimizationOptions = [
  { value: "TBA", label: "Table-Based Approach" },
  { value: "CSE", label: "Table-Based + CSE" },
  { value: "MAXIMA", label: "Table-Based + Maxima" },
  { value: "CSE_MAXIMA", label: "Table-Based + CSE + Maxima" }
];

const outputModeOptions = [
  { value: "CODE_AND_VISUALIZATION", label: "Code and Visualization" },
  { value: "CODE_ONLY", label: "Code Only" },
  { value: "VISUALIZATION_ONLY", label: "Visualization Only" }
];

const codegenFileExtensions = {
  CLUCALC: "clu",
  JULIA: "jl",
  VERILOG: "v",
  GAPP_DEBUGGER: "gapp",
  CSHARP: "cs",
  RUST: "rs",
  JAVA: "java",
  VIS2D: "vis2d",
  GAALET_OUTPUT: "cpp",
  GAPP: "gapp",
  COMPRESSED: "txt",
  VISUALIZER: "html",
  GANJA: "js",
  GAPP_OPENCL: "cl",
  PYTHON: "py",
  MATLAB: "m",
  DOT: "dot",
  MATHematica: "wl",
  CPP: "cpp",
  LATEX: "tex"
};

const gaalopNamedColors = ["Black", "Blue", "Cyan", "Green", "Magenta", "Orange", "Red", "White", "Yellow"];
const gaalopBasisIdentifiers = ["e0", "e1", "e2", "e3", "einf"];
const gaalopBuiltinCalls = ["createPoint", "Color", "Normalize", "abs", "cos", "sin", "sqrt"];
const gaalopReservedIdentifiers = new Set([
  ...gaalopBuiltinCalls,
  ...gaalopNamedColors,
  ...gaalopBasisIdentifiers,
  "TIME",
  "_BGColor",
  "_P"
]);
const gaalopOperatorHints = [
  { label: "^ outer product", insertText: "${1:left} ^ ${2:right}", detail: "Outer product / wedge product.", type: "operator", trigger: "^" },
  { label: ". inner product", insertText: "${1:left} . ${2:right}", detail: "Inner product / dot product.", type: "operator", trigger: "." },
  { label: "* geometric product", insertText: "${1:left} * ${2:right}", detail: "Geometric product / multiplication.", type: "operator", trigger: "*" },
  { label: "/ divide", insertText: "${1:left} / ${2:right}", detail: "Division operator.", type: "operator", trigger: "/" },
  { label: "~ reverse", insertText: "~${1:value}", detail: "Reverse operator.", type: "operator", trigger: "~" },
  { label: "* dual", insertText: "*${1:value}", detail: "Unary dual operator.", type: "operator", trigger: "*" }
];
const gaalopBasisHints = gaalopBasisIdentifiers.map((basis) => ({
  label: basis,
  insertText: basis,
  detail: "CGA basis identifier."
}));

const GAALOP_MAIN_LANGUAGE = "gaalopscript-main";
const GAALOP_VARIABLE_LANGUAGE = "gaalopscript-variable";
const GAALOP_VISUALIZATION_LANGUAGE = "gaalopscript-visualization";
const gaalopLanguageIds = [GAALOP_MAIN_LANGUAGE, GAALOP_VARIABLE_LANGUAGE, GAALOP_VISUALIZATION_LANGUAGE];

const mainScriptHints = [
  { label: "createPoint", insertText: "createPoint(${1:x}, ${2:y}, ${3:z})", detail: "Example macro call from current GAALOPScript rules." },
  { label: "Normalize", insertText: "Normalize(${1:value})", detail: "Macro/function call pattern used by existing GAALOPScript examples." },
  { label: "abs", insertText: "abs(${1:value})", detail: "Used in normalize-style expressions and macro bodies." },
  { label: "cos", insertText: "cos(${1:value})", detail: "Function / macro call supported by expression grammar." },
  { label: "sin", insertText: "sin(${1:value})", detail: "Function / macro call supported by expression grammar." },
  { label: "sqrt", insertText: "sqrt(${1:value})", detail: "Square-root function used in GAALOPScript expressions." },
  { label: "_P", insertText: "_P(${1:index})", detail: "Macro parameter reference." },
  { label: "?output", insertText: "?${1:result} = ${2:expression};", detail: "Output assignment." },
  { label: "!evaluate", insertText: "!${1:tmp} = ${2:expression};", detail: "Only-evaluate assignment." },
  { label: "#pragma in2out", insertText: "#pragma in2out ${1:input1},${2:input2} -> ${3:output};", detail: "Input/output signature pragma." },
  { label: "#pragma range", insertText: "#pragma range ${1:0} <= ${2:t} <= ${3:1}", detail: "Input variable range pragma." },
  { label: "#pragma onlyEvaluate", insertText: "#pragma onlyEvaluate ${1:a} ${2:b}", detail: "Mark variables as only-evaluate." },
  { label: "#pragma output", insertText: "#pragma output ${1:p} ${2:e1} ${3:e2}", detail: "Output blade selection pragma." },
  { label: "#pragma insert", insertText: "#pragma insert ${1:// generated helper}", detail: "Insert raw text for downstream codegen." },
  { label: "#pragma return", insertText: "#pragma return ${1:p5},${2:p6} typed ${3:Vector2} as ${4:Vector2(x, y)}", detail: "Return object pragma." },
  { label: "#pragma normalize", insertText: "#pragma normalize", detail: "Enable normalized output rewriting." },
  { label: "macro template", insertText: "${1:MacroName} = {\\n    ${2:tmp} = _P(1);\\n    ${3:_P(1)}\\n}", detail: "Global-scope macro definition template." },
  ...gaalopBasisHints,
  ...gaalopOperatorHints,
  { label: "TIME", insertText: "TIME", detail: "Runtime animation parameter used in existing scripts." }
];

const variableAssignmentHints = [
  { label: "assignment", insertText: "${1:name} = ${2:value}", detail: "Variable assignment for runtime/sample values." },
  { label: "TIME", insertText: "TIME", detail: "Runtime animation parameter used in existing scripts." },
  { label: "Pi", insertText: "Pi", detail: "Common constant used by GAALOPScript examples." },
  { label: "abs", insertText: "abs(${1:value})", detail: "Math expression helper." },
  { label: "cos", insertText: "cos(${1:value})", detail: "Math expression helper." },
  { label: "sin", insertText: "sin(${1:value})", detail: "Math expression helper." },
  { label: "sqrt", insertText: "sqrt(${1:value})", detail: "Math expression helper." },
  ...gaalopOperatorHints
];

const visualizationHints = [
  { label: ":visualize", insertText: ":${1:variable};", detail: "Visualize a variable from Code to Optimize." },
  { label: ":assign visual", insertText: ":${1:name} = ${2:expression};", detail: "Define a visualization helper expression." },
  { label: "Color", insertText: "Color(${1:r}, ${2:g}, ${3:b})", detail: "Foreground or background color helper." },
  { label: "_BGColor", insertText: "_BGColor = Color(${1:r}, ${2:g}, ${3:b});", detail: "Background color assignment." },
  { label: "#pragma segments", insertText: "#pragma segments ${1:p1} ${2:p2}, ${3:p3} ${4:p4}", detail: "Visualization segment pragma." },
  { label: "#pragma triangles", insertText: "#pragma triangles ${1:p1} ${2:p2} ${3:p3}", detail: "Visualization triangle pragma." },
  ...gaalopNamedColors.map((color) => ({
    label: `:${color}`,
    insertText: `:${color};`,
    detail: "Named visualization color."
  })),
  ...gaalopOperatorHints
];

const form = reactive({
  algebraPlugins: "ALGEBRA_CGA",
  codegenPlugins: "JAVA",
  optimizationPreset: "TBA",
  outputMode: "CODE_AND_VISUALIZATION",
  functionName: "threespheres",
  script: {
    optimizeCode: `//creating the CGA points;
?x1=createPoint(a1,a2,a3); 
x2=createPoint(b1,b2,b3);
x3=createPoint(c1,c2,c3);

// creating the spheres;

?S1=x1-0.5*(d14*d14)*einf;
?S2=x2-0.5*(d24*d24)*einf;
?S3=x3-0.5*(d34*d34)*einf;

// The PointPair in the intersection;

?PP4=S1^S2^S3;
?DualPP4=*PP4;

// Extraction of the two points;

?x4a=-(-sqrt(DualPP4.DualPP4)+DualPP4)/(einf.DualPP4);
?x4b=-(sqrt(DualPP4.DualPP4)+DualPP4)/(einf.DualPP4);`,
    variableAssignments: `a1=0; a2=0; a3=0;
b1=0; b2=0.4; b3=0;
c1=0; c2=0.45; c3=0.2;
d14=0.5; d24=0.4; d34=0.3;`,
    multivectorsVisualized: `:Blue;
:S1;
:Red;
:S2;
:Green;
:S3;`
  }
});

const running = ref(false);
const dirty = ref(false);
const previewZoomed = ref(false);
const activeView = ref("online");
const sidebarCollapsed = ref(false);
const multivectorText = ref(form.script.multivectorsVisualized);
const pageRoot = ref(null);
const topbarRef = ref(null);
const pageBodyRef = ref(null);
const toolbarPanelRef = ref(null);
const mainEditorContainer = ref(null);
const variableEditorContainer = ref(null);
const multivectorEditorContainer = ref(null);
const result = reactive({
  optimizeResult: "",
  visualizationCode: "",
  lastSuccessAt: null
});

const runState = reactive({
  kind: "idle",
  message: ""
});
const mainCursor = reactive({ line: 1, column: 1 });
const variableCursor = reactive({ line: 1, column: 1 });
const multivectorCursor = reactive({ line: 1, column: 1 });
let mainEditor = null;
let variableEditor = null;
let multivectorEditor = null;
let completionProviders = [];
let layoutRaf = 0;
let monaco = null;
let monacoSetupPromise = null;
let disposed = false;

const futurePages = {
  home: {
    title: "Home",
    description: "未来增加项目概览页，用来集中展示 GAALOP Online 的入口、示例和服务状态。",
    items: [
      "展示常用几何代数模板和推荐示例",
      "提供最近更新、服务状态和使用入口",
      "帮助新用户快速进入 Online Editing 工作流"
    ]
  },
  agent: {
    title: "GA-CodeAgent",
    description: "未来增加智能代码助手，用来辅助编写、解释和优化 GAALOPScript 与生成代码。",
    items: [
      "根据自然语言生成 GAALOPScript 初稿",
      "解释当前脚本中的变量、宏和几何意义",
      "给出优化建议并辅助定位编译错误"
    ]
  },
  history: {
    title: "History",
    description: "未来增加历史记录，用来保存编译任务、参数配置和生成结果，方便回溯与复用。",
    items: [
      "查看历史编译请求和结果",
      "恢复某一次的输入脚本与插件配置",
      "对比不同优化策略或代码生成目标的输出"
    ]
  },
  help: {
    title: "Help",
    description: "未来增加帮助中心，用来说明 GAALOPScript 语法、插件选项和常见问题。",
    items: [
      "提供 GAALOPScript 语法和 pragma 说明",
      "解释 Algebra Type、Code Generation 和 Optimization 选项",
      "整理常见编译错误和可视化问题的处理方式"
    ]
  }
};

const functionNameValid = computed(() => /^[A-Za-z_$-][A-Za-z0-9_$-]*$/.test(form.functionName.trim()));
const saveHint = computed(() => {
  if (running.value) {
    return "Compiling with backend API...";
  }
  if (dirty.value) {
    return "You have unpublished changes";
  }
  return "All changes saved";
});

const statusAlertType = computed(() => {
  if (running.value) {
    return "info";
  }
  if (runState.kind === "success") {
    return "success";
  }
  if (runState.kind === "error") {
    return "error";
  }
  return "info";
});

const statusMessage = computed(() => {
  if (running.value) {
    return "Compilation in progress. The system is processing your current script.";
  }
  if (runState.kind === "success") {
    return "Compilation completed successfully. Generated code and visualization output are ready.";
  }
  if (runState.kind === "error") {
    return runState.message || "Compilation failed.";
  }
  return "System status: ready to compile. Configure options and click Run to start.";
});
const futurePage = computed(() => futurePages[activeView.value] || {
  title: "Future Feature",
  description: "未来增加。",
  items: ["This module is reserved for future expansion."]
});

const lastRunText = computed(() => {
  if (runState.kind === "error") {
    return "Last run failed";
  }
  if (!result.lastSuccessAt) {
    return "Ready to compile";
  }
  return `Last run: ${formatRelative(result.lastSuccessAt)}`;
});

const mainLines = computed(() => form.script.optimizeCode.split("\n"));
const variableLines = computed(() => form.script.variableAssignments.split("\n"));
const multivectorLines = computed(() => multivectorText.value.split("\n"));
const displayedCode = computed(() => result.optimizeResult || "// No generated code returned.");
const generatedLines = computed(() => displayedCode.value.split("\n"));
const displayedPreview = computed(() => buildVisualizationDocument(result.visualizationCode));
const generatedCodeHtml = computed(() => highlight(displayedCode.value));

function markDirty() {
  dirty.value = true;
}

function showOnlineEditing() {
  activeView.value = "online";
  updateLayoutMetrics();
}

function showFuturePage(view) {
  activeView.value = view;
}

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value;
  updateLayoutMetrics();
}

function handleMultivectorInput() {
  form.script.multivectorsVisualized = multivectorText.value;
  markDirty();
}

const optimizeScriptSymbols = computed(() => extractScriptSymbols(form.script.optimizeCode));
const optimizeScriptMacros = computed(() => extractScriptMacros(form.script.optimizeCode));
const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || "http://gacrac.gagis.cn:8080";

function buildRequestBody() {
  return {
    algebraPlugins: form.algebraPlugins,
    codegenPlugins: form.codegenPlugins,
    outputMode: form.outputMode,
    visualizationEnabled: form.outputMode !== "CODE_ONLY" || form.codegenPlugins === "GANJA",
    optimization: buildOptimizationFlags(form.optimizationPreset),
    script: {
      functionName: form.functionName.trim(),
      optimizeCode: form.script.optimizeCode,
      variableAssignments: form.script.variableAssignments,
      multivectorsVisualized: form.script.multivectorsVisualized
    }
  };
}

function buildOptimizationFlags(preset) {
  return {
    cse: preset === "CSE" || preset === "CSE_MAXIMA",
    maxima: preset === "MAXIMA" || preset === "CSE_MAXIMA"
  };
}

async function runCompilation() {
  if (!functionNameValid.value || running.value) {
    runState.kind = "error";
    runState.message = "Function name is invalid.";
    return;
  }

  running.value = true;
  runState.kind = "idle";
  runState.message = "";

  try {
    const response = await fetch(`${apiBaseUrl}/api/v1/compile`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify(buildRequestBody())
    });

    const payload = await response.json().catch(() => ({}));
    if (!response.ok || payload.statusCode !== "200") {
      throw new Error(payload.message || `Compile request failed with status ${response.status}.`);
    }

    result.optimizeResult = payload.optimizeResult || "";
    result.visualizationCode = payload.visualizationCode || "";
    result.lastSuccessAt = Date.now();
    dirty.value = false;
    runState.kind = "success";
  } catch (error) {
    runState.kind = "error";
    runState.message = error.message || "Compilation failed.";
  } finally {
    running.value = false;
  }
}

function highlight(value) {
  return escapeHtml(value)
    .replace(/createPoint/g, '<span class="token-function">createPoint</span>')
    .replace(/\b(public|static|void|float)\b/g, '<span class="token-keyword">$1</span>');
}

function escapeHtml(value) {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}

function buildVisualizationDocument(coreScript) {
  if (!coreScript || !coreScript.trim()) {
    return "";
  }

  if (/^\s*<!doctype html|^\s*<html/i.test(coreScript)) {
    return coreScript;
  }

  return `<!doctype html>
<html>
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <script src="https://gaalopweb.esa.informatik.tu-darmstadt.de/gaalopweb/lib/ganja/ganja.js"><\/script>
    <style>
      :root {
        color-scheme: light;
        --panel-bg: #ffffff;
        --panel-border: #dbe7ff;
        --axis: #10264d;
        --muted: #6b7da5;
      }

      * {
        box-sizing: border-box;
      }

      html,
      body {
        width: 100%;
        height: 100%;
        margin: 0;
        overflow: hidden;
        background:
          radial-gradient(circle at 18% 16%, rgba(54, 104, 255, 0.10), transparent 34%),
          linear-gradient(135deg, #f8fbff 0%, #ffffff 48%, #eef6ff 100%);
        color: var(--axis);
        font-family: "Segoe UI", "Helvetica Neue", Arial, sans-serif;
      }

      body {
        display: flex;
        flex-direction: column;
        gap: 10px;
        padding: 12px;
      }

      .gaalop-preview-shell {
        display: grid;
        grid-template-columns: minmax(0, 1fr) minmax(180px, 260px);
        gap: 12px;
        width: 100%;
        height: 100%;
        min-height: 0;
      }

      .preview-stage,
      #sliderpanel {
        min-height: 0;
        border: 1px solid var(--panel-border);
        border-radius: 14px;
        background: rgba(255, 255, 255, 0.86);
        box-shadow: 0 18px 40px rgba(27, 69, 156, 0.08);
      }

      .preview-stage {
        position: relative;
        overflow: hidden;
      }

      #ganjacanvas {
        position: absolute;
        inset: 0;
        display: flex;
        align-items: stretch;
        justify-content: stretch;
      }

      #ganjacanvas canvas,
      #ganjacanvas > * {
        width: 100% !important;
        height: 100% !important;
        max-width: none !important;
        max-height: none !important;
      }

      #sliderpanel {
        display: flex;
        flex-direction: column;
        gap: 8px;
        overflow: auto;
        padding: 12px;
      }

      #sliderpanel:empty {
        display: none;
      }

      #sliderpanel:empty + .preview-stage,
      .gaalop-preview-shell:has(#sliderpanel:empty) {
        grid-template-columns: minmax(0, 1fr);
      }

      .slideritem {
        display: grid;
        grid-template-columns: auto minmax(90px, 1fr) auto auto;
        align-items: center;
        gap: 8px;
        color: var(--muted);
        font-size: 12px;
      }

      input[type="range"] {
        accent-color: #2458ff;
      }

      @media (max-width: 760px) {
        .gaalop-preview-shell {
          grid-template-columns: minmax(0, 1fr);
          grid-template-rows: minmax(0, 1fr) auto;
        }
      }
    </style>
  </head>
  <body>
    <div class="gaalop-preview-shell">
      <div class="preview-stage">
        <div id="ganjacanvas"></div>
      </div>
      <div id="sliderpanel"></div>
    </div>
    <script>
${coreScript}
    <\/script>
  </body>
</html>`;
}

async function copyText(text) {
  if (!text) {
    return;
  }
  try {
    await navigator.clipboard.writeText(text);
  } catch {
    return;
  }
}

function downloadCode() {
  const blob = new Blob([displayedCode.value], { type: "text/plain;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = buildDownloadFilename();
  anchor.click();
  URL.revokeObjectURL(url);
}

function buildDownloadFilename() {
  const baseName = (form.functionName.trim() || "gaalop").replace(/[<>:"/\\|?*\u0000-\u001F]/g, "_");
  const extension = codegenFileExtensions[form.codegenPlugins] || "txt";
  return `${baseName}.${extension}`;
}

function formatRelative(timestamp) {
  const minutes = Math.max(0, Math.floor((Date.now() - timestamp) / 60000));
  if (minutes < 1) {
    return "just now";
  }
  if (minutes === 1) {
    return "1 minute ago";
  }
  return `${minutes} minutes ago`;
}

onMounted(() => {
  multivectorText.value = form.script.multivectorsVisualized;
  updateLayoutMetrics();
  window.addEventListener("resize", handleWindowResize);
  void setupMonaco();
});

onBeforeUnmount(() => {
  disposed = true;
  window.removeEventListener("resize", handleWindowResize);
  cancelAnimationFrame(layoutRaf);
  completionProviders.forEach((provider) => provider?.dispose?.());
  mainEditor?.dispose?.();
  variableEditor?.dispose?.();
  multivectorEditor?.dispose?.();
});

function handleWindowResize() {
  updateLayoutMetrics();
}

function updateLayoutMetrics() {
  cancelAnimationFrame(layoutRaf);
  layoutRaf = requestAnimationFrame(() => {
    if (!pageRoot.value || !pageBodyRef.value) {
      return;
    }

    const topbarHeight = topbarRef.value?.offsetHeight ?? 0;
    const toolbarHeight = toolbarPanelRef.value?.offsetHeight ?? 0;
    const pageBodyStyles = window.getComputedStyle(pageBodyRef.value);
    const padTop = Number.parseFloat(pageBodyStyles.paddingTop) || 0;
    const padBottom = Number.parseFloat(pageBodyStyles.paddingBottom) || 0;
    const pageRootStyles = window.getComputedStyle(pageRoot.value);
    const workspaceGap = Number.parseFloat(pageRootStyles.getPropertyValue("--workspace-gap")) || 10;
    const availableHeight = window.innerHeight - topbarHeight - padTop - padBottom - toolbarHeight - workspaceGap;

    pageRoot.value.style.setProperty("--editor-grid-height", `${Math.max(420, availableHeight)}px`);
    mainEditor?.layout();
    variableEditor?.layout();
    multivectorEditor?.layout();
  });
}

async function setupMonaco() {
  if (monacoSetupPromise) {
    return monacoSetupPromise;
  }

  monacoSetupPromise = setupMonacoEditor();
  return monacoSetupPromise;
}

async function setupMonacoEditor() {
  if (!mainEditorContainer.value || !variableEditorContainer.value || !multivectorEditorContainer.value) {
    return;
  }

  monaco = await import("monaco-editor/esm/vs/editor/editor.api");
  if (disposed) {
    return;
  }

  for (const languageId of gaalopLanguageIds) {
    if (!monaco.languages.getLanguages().some((item) => item.id === languageId)) {
      monaco.languages.register({ id: languageId });
    }

    monaco.languages.setMonarchTokensProvider(languageId, {
      tokenizer: {
        root: [
          [/\/\/#pragma.*$/, "predefined"],
          [/\/\/.*$/, "comment"],
          [/\/\*/, "comment", "@comment"],
          [/#pragma\b/, "predefined"],
          [/\b\d+(\.\d+)?([eE][+-]?\d+)?[fFdD]?\b/, "number"],
          [/\b(TIME|_BGColor)\b/, "keyword"],
          [/\b(_P)\b/, "type.identifier"],
          [new RegExp(`\\b(${gaalopBuiltinCalls.join("|")})\\b`), "function"],
          [/\b[A-Za-z_$][A-Za-z0-9_$-]*(?=\s*\()/, "function"],
          [new RegExp(`:(Color|${gaalopNamedColors.join("|")})\\b`), "keyword"],
          [/[?:!~^*./+\-=]/, "operator"]
        ],
        comment: [
          [/[^\/*]+/, "comment"],
          [/\*\//, "comment", "@pop"],
          [/[\/*]/, "comment"]
        ]
      }
    });
  }

  monaco.editor.defineTheme("gaalop-light", {
    base: "vs",
    inherit: true,
    rules: [
      { token: "comment", foreground: "7f8cab" },
      { token: "number", foreground: "9a5b00" },
      { token: "keyword", foreground: "2455ff", fontStyle: "bold" },
      { token: "function", foreground: "2455ff", fontStyle: "bold" },
      { token: "predefined", foreground: "7c3aed", fontStyle: "bold" },
      { token: "type.identifier", foreground: "0f766e", fontStyle: "bold" },
      { token: "operator", foreground: "445473", fontStyle: "bold" }
    ],
    colors: {
      "editor.background": "#ffffff",
      "editorLineNumber.foreground": "#637392",
      "editorLineNumber.activeForeground": "#2455ff",
      "editor.selectionBackground": "#dbe8ff",
      "editor.inactiveSelectionBackground": "#edf3ff"
    }
  });

  completionProviders.forEach((provider) => provider?.dispose?.());
  completionProviders = [
    registerGaalopCompletionProvider(GAALOP_MAIN_LANGUAGE, () => buildCompletionSuggestions(mainScriptHints, buildSymbolHints("main"))),
    registerGaalopCompletionProvider(GAALOP_VARIABLE_LANGUAGE, () => buildCompletionSuggestions(variableAssignmentHints, buildSymbolHints("variable"), "Identifier referenced in Code to Optimize.")),
    registerGaalopCompletionProvider(GAALOP_VISUALIZATION_LANGUAGE, () => buildCompletionSuggestions(visualizationHints, buildSymbolHints("visualization"), "Identifier referenced in Code to Optimize."))
  ];

  mainEditor = createEditor(mainEditorContainer.value, form.script.optimizeCode, GAALOP_MAIN_LANGUAGE);
  variableEditor = createEditor(variableEditorContainer.value, form.script.variableAssignments, GAALOP_VARIABLE_LANGUAGE, { lineNumbersMinChars: 2 });
  multivectorEditor = createEditor(multivectorEditorContainer.value, form.script.multivectorsVisualized, GAALOP_VISUALIZATION_LANGUAGE, { lineNumbersMinChars: 2 });

  mainEditor.onDidChangeModelContent(() => {
    form.script.optimizeCode = mainEditor.getValue();
    dirty.value = true;
  });

  mainEditor.onDidChangeCursorPosition((event) => {
    mainCursor.line = event.position.lineNumber;
    mainCursor.column = event.position.column;
  });

  const initial = mainEditor.getPosition();
  if (initial) {
    mainCursor.line = initial.lineNumber;
    mainCursor.column = initial.column;
  }

  variableEditor.onDidChangeModelContent(() => {
    form.script.variableAssignments = variableEditor.getValue();
    dirty.value = true;
  });

  variableEditor.onDidChangeCursorPosition((event) => {
    variableCursor.line = event.position.lineNumber;
    variableCursor.column = event.position.column;
  });

  const variableInitial = variableEditor.getPosition();
  if (variableInitial) {
    variableCursor.line = variableInitial.lineNumber;
    variableCursor.column = variableInitial.column;
  }

  multivectorEditor.onDidChangeModelContent(() => {
    multivectorText.value = multivectorEditor.getValue();
    form.script.multivectorsVisualized = multivectorText.value;
    dirty.value = true;
  });

  multivectorEditor.onDidChangeCursorPosition((event) => {
    multivectorCursor.line = event.position.lineNumber;
    multivectorCursor.column = event.position.column;
  });

  const multivectorInitial = multivectorEditor.getPosition();
  if (multivectorInitial) {
    multivectorCursor.line = multivectorInitial.lineNumber;
    multivectorCursor.column = multivectorInitial.column;
  }

  updateLayoutMetrics();
}

function registerGaalopCompletionProvider(languageId, buildHints) {
  return monaco.languages.registerCompletionItemProvider(languageId, {
    triggerCharacters: ["#", ":", "_", "?", "!", "^", ".", "*", "/", "~", "+", "-", "c", "a", "s", "T", "P", "p"],
    provideCompletionItems(model, position) {
      const word = model.getWordUntilPosition(position);
      const linePrefix = model.getValueInRange({
        startLineNumber: position.lineNumber,
        startColumn: Math.max(1, position.column - 1),
        endLineNumber: position.lineNumber,
        endColumn: position.column
      });
      const baseRange = {
        startLineNumber: position.lineNumber,
        endLineNumber: position.lineNumber,
        startColumn: word.startColumn,
        endColumn: word.endColumn
      };

      const suggestions = buildHints().map((item, index) => ({
        label: item.label,
        kind: getCompletionKind(item),
        detail: item.detail,
        insertText: item.insertText || item.label,
        insertTextRules: (item.insertText || item.label).includes("${")
          ? monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet
          : undefined,
        range: getCompletionRange(item, baseRange, linePrefix),
        sortText: `${item.sortGroup ?? "1"}-${String(index).padStart(3, "0")}-${item.label}`
      }));

      return { suggestions };
    }
  });
}

function getCompletionRange(item, baseRange, linePrefix) {
  const insertText = item.insertText || item.label;
  const shouldReplaceTrigger =
    (linePrefix === ":" && insertText.startsWith(":")) ||
    (linePrefix === "#" && insertText.startsWith("#")) ||
    (linePrefix === "?" && insertText.startsWith("?")) ||
    (linePrefix === "!" && insertText.startsWith("!")) ||
    (item.type === "operator" && item.trigger === linePrefix);

  if (!shouldReplaceTrigger) {
    return baseRange;
  }

  return {
    ...baseRange,
    startColumn: Math.max(1, baseRange.startColumn - 1)
  };
}

function buildCompletionSuggestions(baseHints, symbolHints = []) {
  return [
    ...baseHints.map((hint) => ({ ...hint, sortGroup: "0" })),
    ...symbolHints.map((hint) => ({ ...hint, sortGroup: "1" }))
  ];
}

function buildSymbolHints(context) {
  const macroSymbols = optimizeScriptMacros.value.map((name) => ({
    label: name,
    insertText: context === "main" ? `${name}(\${1:value})` : name,
    detail: "Macro defined in Code to Optimize.",
    type: "macro"
  }));
  const baseSymbols = optimizeScriptSymbols.value
    .filter((name) => !optimizeScriptMacros.value.includes(name))
    .map((name) => ({
    label: name,
    insertText: name,
    detail: "Identifier referenced in Code to Optimize.",
    type: "variable"
  }));

  if (context !== "visualization") {
    return [...macroSymbols, ...baseSymbols];
  }

  return [
    ...macroSymbols,
    ...baseSymbols,
    ...optimizeScriptSymbols.value.map((name) => ({
      label: `:${name};`,
      insertText: `:${name};`,
      detail: "Visualize this Code to Optimize identifier.",
      type: "color"
    }))
  ];
}

function getCompletionKind(item) {
  if (item.type === "variable") {
    return monaco.languages.CompletionItemKind.Variable;
  }

  if (item.type === "macro") {
    return monaco.languages.CompletionItemKind.Function;
  }

  if (item.label.startsWith("#pragma")) {
    return monaco.languages.CompletionItemKind.Keyword;
  }

  if (item.label.startsWith(":")) {
    return monaco.languages.CompletionItemKind.Color;
  }

  if (item.label === "macro template" || (item.insertText || "").includes("${")) {
    return monaco.languages.CompletionItemKind.Snippet;
  }

  if (item.type === "operator") {
    return monaco.languages.CompletionItemKind.Operator;
  }

  return monaco.languages.CompletionItemKind.Function;
}

function createEditor(container, value, language, extraOptions = {}) {
  return monaco.editor.create(container, {
    value,
    language,
    theme: "gaalop-light",
    automaticLayout: true,
    minimap: { enabled: false },
    scrollBeyondLastLine: false,
    wordWrap: "off",
    lineNumbers: "on",
    lineNumbersMinChars: 3,
    roundedSelection: false,
    glyphMargin: false,
    folding: false,
    fontSize: 11,
    lineHeight: 18,
    fontFamily: "Consolas, 'Cascadia Mono', monospace",
    overviewRulerBorder: false,
    hideCursorInOverviewRuler: true,
    scrollbar: {
      verticalScrollbarSize: 8,
      horizontalScrollbarSize: 8
    },
    ...extraOptions
  });
}

function extractScriptSymbols(script) {
  const names = new Set();
  const stripped = script
    .replace(/\/\*[\s\S]*?\*\//g, " ")
    .replace(/\/\/#pragma.*$/gm, " ")
    .replace(/#pragma.*$/gm, " ")
    .replace(/\/\/.*$/gm, " ");

  for (const match of stripped.matchAll(/\b(::)?([A-Za-z_$][A-Za-z0-9_$-]*)\b/g)) {
    const name = match[2];
    const full = match[0];
    const nextChar = stripped[match.index + full.length] || "";

    if (gaalopReservedIdentifiers.has(name)) {
      continue;
    }

    if (/^\d/.test(name)) {
      continue;
    }

    if (nextChar === "(") {
      continue;
    }

    names.add(name);
  }

  return [...names].sort((left, right) => left.localeCompare(right));
}

function extractScriptMacros(script) {
  const names = new Set();
  const stripped = script
    .replace(/\/\*[\s\S]*?\*\//g, " ")
    .replace(/\/\/.*$/gm, " ");

  for (const match of stripped.matchAll(/^\s*([A-Za-z_$][A-Za-z0-9_$-]*)\s*=\s*\{/gm)) {
    const name = match[1];
    if (!gaalopReservedIdentifiers.has(name)) {
      names.add(name);
    }
  }

  return [...names].sort((left, right) => left.localeCompare(right));
}
</script>
