<template>
  <div class="quantum-workspace">
    <WorkspaceHeader quantum :active-view="activeView" :status-text="running ? 'Running' : stale ? 'Inputs changed' : status === 'error' ? 'Run failed' : 'Ready to compile'" :error="status === 'error'" @navigate="activeView = $event" />
    <main v-show="activeView === 'online'">
      <section class="q-toolbar" aria-label="Compilation settings">
        <div class="q-run"><button class="q-primary" :disabled="running" @click="run">{{ running ? 'Running…' : '▶ Run' }}</button></div>
        <label>Algebra Type<a-select v-model:value="algebra" :disabled="running" :options="algebras" /></label>
        <label v-if="algebra !== 'QGA'">Qubits<a-select v-model:value="form.algebraDimension" :options="qubits" /></label>
        <label v-else>Algebra<span class="q-fixed">Cl(6,3)</span></label>
        <label>Code Generation<a-select v-model:value="form.codegenPlugins" :options="languages" /></label>
        <label>Optimization<span class="q-fixed">{{ numeric ? 'Native numeric evaluation' : 'Table-Based Approach' }}</span></label>
        <label>Output Mode<a-select v-model:value="form.outputMode" :options="availableModes" /></label>
        <label>Function Name<a-input v-model:value="form.functionName" :status="validName ? '' : 'error'" /></label>
      </section>
      <section class="q-input-grid">
        <article class="q-panel q-main-editor"><h3>Code to Optimize</h3><ScriptEditor v-model="form.optimizeCode" label="Code to Optimize" /></article>
        <div class="q-right-editors">
          <article class="q-panel"><h3>Variable Assignments</h3><p v-if="!numeric" class="q-caption">{{ visualization ? 'These values are used for visualization only; generated code keeps free parameters.' : 'Code generation preserves free parameters. Put fixed assignments in Code to Optimize.' }}</p><ScriptEditor :key="algebra + '-assignments'" v-model="form.variableAssignments" label="Variable Assignments" :read-only="!visualization" /></article>
          <article class="q-panel"><h3>Multivectors to be Visualized</h3><p v-if="!visualization" class="q-caption">Visualization is not supported for {{ modeName }}.</p><ScriptEditor :key="algebra + '-visualization'" v-model="form.multivectorsVisualized" label="Multivectors to be Visualized" :read-only="!visualization" /></article>
        </div>
      </section>
      <div class="q-message" :class="{ 'q-error': status === 'error', 'q-stale': stale }" role="status" aria-live="polite">
        {{ message }}<span v-if="stale"> Inputs have changed; displayed results belong to the previous submission.</span>
      </div>
      <section class="q-output-grid">
        <article class="q-panel q-code-output">
          <div class="q-panel-heading"><h3>Generated Code <small v-if="result">{{ result.language }} · {{ result.algebra === 'QRA' ? 'evaluated values' : 'compiled expressions' }}</small></h3><div><button :disabled="!result?.code" @click="copyCode">{{ copied ? 'Copied' : 'Copy' }}</button><button :disabled="!result?.code" @click="downloadCode">Download</button></div></div>
          <ScriptEditor :model-value="result?.code || ''" label="Generated Code" read-only />
          <p class="q-caption">{{ numeric ? 'Generated code contains this run’s evaluated values.' : 'Generated code preserves free input parameters from the computation script.' }}</p>
        </article>
        <article ref="preview" class="q-panel q-preview" :class="{ 'q-expanded': expanded }">
          <div class="q-panel-heading"><h3>Visualization Preview <small v-if="!visualization">Not supported</small></h3><button v-if="visualization" @click="expanded = !expanded">{{ expanded ? 'Close expanded view' : 'Expand' }}</button></div>
          <div v-if="expanded && stale" class="q-message q-stale">Inputs changed. These results belong to the previous submission.</div>
          <div v-if="result && Object.keys(result.states).length" class="q-charts"><ProbabilityChart v-for="(state, name) in result.states" :key="name" :name="name" :state="state" /></div>
          <div v-else class="q-empty">{{ emptyMessage }}</div>
        </article>
      </section>
    </main>
    <main v-if="activeView !== 'online'" class="q-future">
      <h1>{{ activeView === 'home' ? 'Home' : 'GA-CodeAgent' }}</h1>
      <p>{{ activeView === 'home' ? '量子工作台概览页尚未开放，请进入 Online Editing 编写和运行脚本。' : '智能代码助手尚未接入，请进入 Online Editing 编写和运行脚本。' }}</p>
      <button @click="activeView = 'online'">Online Editing</button>
    </main>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import WorkspaceHeader from "../components/WorkspaceHeader.vue";
import ScriptEditor from "./ScriptEditor.vue";
import ProbabilityChart from "./ProbabilityChart.vue";
import { algebraModes, buildRequest, draftKeyFor, initialForm, validateResults } from "./model.js";
const algebras = Object.entries(algebraModes).map(([value, mode]) => ({ value, label: mode.label }));
const algebra = ref("QRA");
const numeric = computed(() => algebraModes[algebra.value].numeric);
const visualization = computed(() => algebraModes[algebra.value].visualization);
const modeName = computed(() => algebraModes[algebra.value].label);
const qubits = computed(() => algebraModes[algebra.value].dimensions.map(value => ({ value, label: String(value) })));
const languages = ["JAVA", "CPP", "PYTHON"].map(value => ({ value, label: { JAVA: "Java", CPP: "C++", PYTHON: "Python" }[value] }));
const modes = [{ value: "CODE_AND_VISUALIZATION", label: "Code and Visualization" }, { value: "CODE_ONLY", label: "Code Only" }, { value: "VISUALIZATION_ONLY", label: "Visualization Only" }];
const availableModes = computed(() => visualization.value ? modes : modes.filter(mode => mode.value === 'CODE_ONLY'));
const drafts = reactive(Object.fromEntries(Object.keys(algebraModes).map(key => [key, initialForm(key)])));
const form = computed(() => drafts[algebra.value]);
const activeView = ref("online");
for (const key of Object.keys(algebraModes)) {
  const draft = drafts[key], defaults = initialForm(key);
  try {
    const saved = JSON.parse(localStorage.getItem(draftKeyFor(key)) || "null");
    if (saved) for (const field of Object.keys(draft)) if (typeof saved[field] === typeof draft[field]) draft[field] = saved[field];
    if (!algebraModes[key].dimensions.includes(draft.algebraDimension)) draft.algebraDimension = defaults.algebraDimension;
    if (!languages.some(x => x.value === draft.codegenPlugins)) draft.codegenPlugins = defaults.codegenPlugins;
    if (!algebraModes[key].visualization || !modes.some(x => x.value === draft.outputMode)) draft.outputMode = defaults.outputMode;
  } catch { /* Keep the default draft when local storage is unavailable. */ }
}
const running = ref(false), status = ref("idle"), result = ref(null), expanded = ref(false), copied = ref(false);
const message = ref("Set parameters in Variable Assignments and mark a state with :psi; in the visualization script, then Run.");
const validName = computed(() => /^[A-Za-z_][A-Za-z0-9_]*$/.test(form.value.functionName.trim()));
const fingerprint = () => JSON.stringify({ algebra: algebra.value, ...form.value });
const stale = computed(() => result.value !== null && result.value.fingerprint !== fingerprint());
const emptyMessage = computed(() => !visualization.value ? `Visualization is not supported for ${modeName.value}. Code generation is available.` : status.value === "error" ? "No result from this run. Check the error above." : result.value?.mode === "CODE_ONLY" ? "Choose a visualization output mode to plot a quantum state." : result.value ? "No states selected. Add :psi; (using your state variable name) to the visualization script." : "Run a quantum script to display basis-state probabilities.");
const base = new URL("./", window.location.href);
const apiBase = import.meta.env.VITE_API_BASE_URL;
const compileUrl = apiBase ? `${apiBase.replace(/\/+$/, "")}/api/v1/compile` : new URL("api/v1/compile", base).href;
let controller, timer;
watch(form, () => {
  copied.value = false;
  try { localStorage.setItem(draftKeyFor(algebra.value), JSON.stringify(form.value)); }
  catch { /* Editing remains available without local persistence. */ }
}, { deep: true });
watch(algebra, () => {
  result.value = null; status.value = 'idle'; expanded.value = false;
  message.value = visualization.value ? 'Set parameters and mark states in the visualization script, then Run.' : `${modeName.value}: compile GAALOPScript with free parameters. Visualization is not supported.`;
});
async function run() {
  if (running.value) return;
  if (!validName.value || !form.value.optimizeCode.trim()) { status.value = "error"; message.value = "Enter a valid function name and a computation script."; return; }
  const submittedAlgebra = algebra.value;
  const request = buildRequest(form.value, submittedAlgebra), submitted = fingerprint();
  running.value = true; status.value = "running"; result.value = null; copied.value = false;
  message.value = numeric.value ? "Evaluating the submitted script…" : "Compiling the submitted script…";
  controller = new AbortController();
  timer = setTimeout(() => controller.abort(), 120000);
  try {
    const response = await fetch(compileUrl, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(request), signal: controller.signal });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok || String(payload.statusCode) !== "200") throw new Error(payload.message || `Request failed (HTTP ${response.status}). Check that the backend is running.`);
    const states = request.visualizationEnabled ? validateResults(payload.quantumResults, request.algebraDimension) : {};
    result.value = { code: payload.optimizeResult || "", states, fingerprint: submitted, language: request.codegenPlugins, mode: request.outputMode, name: request.script.functionName, algebra: submittedAlgebra };
    status.value = "success"; message.value = `Run completed · ${modeName.value}${request.algebraDimension ? ` · ${request.algebraDimension} qubits` : ' · fixed algebra'}.`;
  } catch (error) {
    status.value = "error"; message.value = error.name === "AbortError" ? "The request timed out. The server may still be computing; wait before retrying." : error.message;
  } finally { clearTimeout(timer); running.value = false; }
}
async function copyCode() {
  try { await navigator.clipboard.writeText(result.value.code); copied.value = true; }
  catch { message.value = "Clipboard unavailable. Select and copy text from Generated Code."; }
}
function downloadCode() {
  const extension = { JAVA: "java", CPP: "cpp", PYTHON: "py" }[result.value.language];
  const url = URL.createObjectURL(new Blob([result.value.code], { type: "text/plain;charset=utf-8" }));
  const link = document.createElement("a"); link.href = url; link.download = `${result.value.name}.${extension}`; link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000);
}
function escape(event) { if (event.key === "Escape") expanded.value = false; }
onMounted(() => { document.title = "QuantumGaalopWeb"; window.addEventListener("keydown", escape); });
onBeforeUnmount(() => { controller?.abort(); clearTimeout(timer); window.removeEventListener("keydown", escape); });
</script>

<style scoped>
.quantum-workspace { min-height: 100vh; background: #f4f7fc; color: #253754; font-family: Arial, sans-serif; }
.q-future { align-items: flex-start; margin: 30px; }
main { padding: 14px; display: flex; flex-direction: column; gap: 12px; }
.q-toolbar { display: grid; grid-template-columns: 105px minmax(180px, 1.4fr) 70px 1fr 1.25fr 1.3fr 1fr; gap: 16px; align-items: center; background: white; padding: 12px 16px; border: 1px solid #e3ebf6; border-radius: 9px; }
.q-toolbar label { min-width: 0; display: flex; flex-direction: column; gap: 5px; font-size: 11px; }
.q-fixed { min-height: 32px; display: flex; align-items: center; font-size: 12px; color: #687993; }
button { border: 1px solid #dbe5f5; border-radius: 5px; padding: 5px 9px; color: #2455ff; background: white; cursor: pointer; font-size: 12px; }button:disabled { opacity: .5; cursor: not-allowed; }button:focus-visible, a:focus-visible { outline: 2px solid #2455ff; outline-offset: 3px; }
.q-primary { background: #2455ff; color: white; padding: 9px 20px; border: 0; }
.q-input-grid { display: grid; grid-template-columns: 3fr 2fr; gap: 12px; min-height: 420px; height: 44vh; }.q-right-editors { display: grid; grid-template-rows: 1fr 1fr; gap: 12px; min-height: 0; }
.q-panel { min-width: 0; min-height: 0; background: white; border: 1px solid #e3ebf6; border-radius: 9px; overflow: hidden; display: flex; flex-direction: column; box-shadow: 0 1px 3px #233a6210; }
h3 { margin: 0; padding: 10px 12px; font-size: 12px; font-weight: 600; } .q-panel-heading { display: flex; justify-content: space-between; align-items: center; padding-right: 10px; gap: 8px; }.q-panel-heading div { display: flex; gap: 6px; }h3 small { font-size: 10px; color: #7886a1; font-weight: 400; margin-left: 8px; }
.q-message { border: 1px solid #b4d6ff; border-radius: 5px; background: #eaf4ff; padding: 8px 12px; font-size: 12px; white-space: pre-wrap; overflow-wrap: anywhere; }.q-error { color: #a92c35; background: #fff0f0; border-color: #f1bbc0; }.q-stale { background: #fff8e9; border-color: #ecd099; color: #775613; }
.q-output-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; min-height: 370px; }.q-code-output { min-height: 370px; }.q-charts { overflow: auto; max-height: 65vh; }.q-caption { font-size: 10px; color: #7886a1; padding: 0 12px; }.q-empty { min-height: 310px; display: grid; place-content: center; padding: 30px; color: #7886a1; font-size: 12px; text-align: center; }
.q-expanded { position: fixed; inset: 16px; z-index: 1000; box-shadow: 0 0 0 30px #17243c99; }.q-expanded .q-charts { max-height: none; flex: 1; }
@media (max-width: 1200px) { .q-toolbar { grid-template-columns: repeat(4, minmax(0, 1fr)); } }
@media (max-width: 760px) { .q-toolbar { grid-template-columns: repeat(2, minmax(0, 1fr)); }.q-input-grid, .q-output-grid { grid-template-columns: 1fr; height: auto; }.q-main-editor { height: 350px; }.q-right-editors { grid-template-rows: 220px 220px; }.q-expanded { inset: 6px; } }
</style>
