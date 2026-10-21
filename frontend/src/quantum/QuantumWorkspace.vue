<template>
  <div class="quantum-workspace">
    <WorkspaceHeader quantum :active-view="activeView" :status-text="running ? 'Running' : stale ? 'Inputs changed' : status === 'error' ? 'Run failed' : 'Ready to compile'" :error="status === 'error'" @navigate="activeView = $event" />
    <main v-show="activeView === 'online'">
      <WorkspaceImport workspace="quantum" :busy="running || fileLoading || codeSaving" @import="importWorkspace" />
      <section class="q-toolbar" aria-label="Compilation settings">
        <div class="q-run"><button class="q-primary" :disabled="running || fileLoading || codeSaving" @click="run">{{ running ? 'Running…' : '▶ Run' }}</button><div class="script-file-actions"><FileOpenButton :disabled="running || fileLoading || codeSaving" @open="openScript" /><FileSaveButton label="script" :disabled="running || fileLoading || codeSaving" :busy="scriptSaving" @save="saveScript()" /><input ref="scriptFileInput" type="file" accept=".gs,.txt,text/plain" hidden @change="uploadScript" /></div>
          <a-popover title="Computation modes" trigger="click" placement="bottomLeft">
            <template #content><div class="q-mode-help">
              <p><strong>QCA</strong> compiles with the Table-Based Approach (TBA), preserving free parameters. Variable Assignments are used separately for probability visualization.</p>
              <p><strong>QRA</strong> evaluates numerically using the native backend. Assign every input, including in Code Only mode; generated code contains the evaluated values.</p>
              <p><strong>quantum bit geometric algebra</strong> uses TBA to generate code with free parameters.</p>
              <p><strong>Copy code</strong> copies the generated text to your clipboard. Open loads a script file. Save writes to an authorized file, or asks you to choose a file on first save. Browsers without file access save a downloaded copy. Generated code uses the run's function name and language extension; you choose its location.</p>
            </div></template>
            <button class="q-help-button" type="button" title="How computation modes and code actions work"><QuestionCircleOutlined /> Help</button>
          </a-popover>
        </div>
        <label>Algebra Type<a-select v-model:value="algebra" :disabled="running || fileLoading || codeSaving" :options="algebras" /></label>
        <label v-if="algebra !== 'QGA'">Qubits<a-select v-model:value="form.algebraDimension" :options="qubits" /></label>
        <label v-else>Algebra<span class="q-fixed">Cl(6,3)</span></label>
        <label>Code Generation<a-select v-model:value="form.codegenPlugins" :options="languages" /></label>
        <label>Output Mode<a-select v-model:value="form.outputMode" :options="availableModes" /></label>
        <label>Function Name<a-input v-model:value="form.functionName" :status="validName ? '' : 'error'" :aria-invalid="!validName" :aria-describedby="validName ? undefined : 'quantum-name-error'" /><span v-if="!validName" id="quantum-name-error" class="q-name-error">Use letters, digits and underscores; start with a letter or underscore.</span></label>
      </section>
      <section class="q-input-grid">
        <article class="q-panel q-main-editor"><h3>Code to Optimize</h3><ScriptEditor v-model="form.optimizeCode" label="Code to Optimize" /></article>
        <div class="q-right-editors">
          <article class="q-panel"><h3>Variable Assignments</h3><p v-if="!numeric" class="q-caption">{{ visualization ? 'These values are used for visualization only; generated code keeps free parameters.' : 'Code generation preserves free parameters. Put fixed assignments in Code to Optimize.' }}</p><ScriptEditor :key="algebra + '-assignments'" v-model="form.variableAssignments" label="Variable Assignments" :read-only="!visualization" /></article>
          <article class="q-panel"><h3>Multivectors to be Visualized</h3><p v-if="!visualization" class="q-caption">{{ modeName }} uses code generation mode.</p><ScriptEditor :key="algebra + '-visualization'" v-model="form.multivectorsVisualized" label="Multivectors to be Visualized" :read-only="!visualization" /></article>
        </div>
      </section>
      <div class="q-message" :class="{ 'q-error': status === 'error', 'q-stale': stale }" role="status" aria-live="polite">
        {{ message }}<span v-if="stale"> Inputs have changed; displayed results belong to the previous submission.</span>
      </div>
      <section class="q-output-grid">
        <article class="q-panel q-code-output">
          <div class="q-panel-heading"><h3>Generated Code <small v-if="result">{{ result.language }} · {{ result.algebra === 'QRA' ? 'evaluated values' : 'compiled expressions' }}</small></h3><div><button :disabled="!result?.code" title="Copy generated code to the clipboard" @click="copyCode">{{ copied ? 'Copied' : 'Copy code' }}</button><FileSaveButton label="generated code" compact :disabled="!result?.code || codeSaving || fileLoading" :busy="codeSaving" @save="saveCode()" /></div></div>
          <ScriptEditor :model-value="result?.code || ''" label="Generated Code" read-only />
          <p class="q-caption">{{ numeric ? 'Generated code contains this run’s evaluated values.' : 'Generated code preserves free input parameters from the computation script.' }}</p>
        </article>
        <article ref="preview" class="q-panel q-preview" :class="{ 'q-expanded': expanded }">
          <div class="q-panel-heading"><h3>Visualization Preview <small v-if="!visualization">Code generation mode</small></h3><button v-if="visualization" @click="expanded = !expanded">{{ expanded ? 'Close expanded view' : 'Expand' }}</button></div>
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
import { QuestionCircleOutlined } from "@ant-design/icons-vue";
import { message as fileMessage, Popover as APopover } from "ant-design-vue";
import { functionNameFromFile, isValidFunctionName, parseScriptFile, serializeScriptFile } from "../scriptFile.js";
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import WorkspaceHeader from "../components/WorkspaceHeader.vue";
import FileSaveButton from "../components/FileSaveButton.vue";
import FileOpenButton from "../components/FileOpenButton.vue";
import { canPickScriptFile, createTextFileSession, pickScriptFile, suggestedFileName } from "../fileAccess.js";
import { chooseSaveName, reportFileSave } from "../fileDialogs.js";
import WorkspaceImport from "../components/WorkspaceImport.vue";
import ScriptEditor from "./ScriptEditor.vue";
import ProbabilityChart from "./ProbabilityChart.vue";
import { quantumLanguages } from "./languages.js";
import { algebraModes, algebraSelectionKey, buildRequest, defaultAlgebra, draftKeyFor, initialAlgebra, initialForm, validateResults } from "./model.js";
const algebras = Object.entries(algebraModes).map(([value, mode]) => ({ value, label: mode.label }));
let preferredAlgebra = defaultAlgebra;
try { preferredAlgebra = initialAlgebra(localStorage); } catch { /* Use QCA without browser storage. */ }
const algebra = ref(preferredAlgebra);
const numeric = computed(() => algebraModes[algebra.value].numeric);
const visualization = computed(() => algebraModes[algebra.value].visualization);
const modeName = computed(() => algebraModes[algebra.value].label);
const qubits = computed(() => algebraModes[algebra.value].dimensions.map(value => ({ value, label: String(value) })));
const languages = quantumLanguages;
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
const validName = computed(() => isValidFunctionName(form.value.functionName));
const fingerprint = () => JSON.stringify({ algebra: algebra.value, ...form.value });
const stale = computed(() => result.value !== null && result.value.fingerprint !== fingerprint());
const emptyMessage = computed(() => !visualization.value ? `Generate code with ${modeName.value}. Choose QCA or QRA to plot basis-state probabilities.` : status.value === "error" ? "No result from this run. Check the error above." : result.value?.mode === "CODE_ONLY" ? "Choose a visualization output mode to plot a quantum state." : result.value ? "No states selected. Add :psi; (using your state variable name) to the visualization script." : "Run a quantum script to display basis-state probabilities.");
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
  try { localStorage.setItem(algebraSelectionKey, algebra.value); } catch { /* Keep the selection in memory. */ }
  result.value = null; status.value = 'idle'; expanded.value = false;
  message.value = visualization.value ? 'Set parameters and mark states in the visualization script, then Run.' : `${modeName.value}: compile GAALOPScript with free parameters.`;
}, { flush: "sync" });
const scriptFileInput = ref(null), fileLoading = ref(false), scriptSaving = ref(false), codeSaving = ref(false);
const scriptFiles = Object.fromEntries(Object.keys(algebraModes).map(key => [key,
  createTextFileSession({ pickerId: "gaalop-script", description: "GAALOPScript", chooseName: chooseSaveName })]));
const codeFile = createTextFileSession({ pickerId: "gaalop-output", description: "Generated code", chooseName: chooseSaveName });
async function applyScriptFile(file, handle, mode) {
  if (file.size > 1024 * 1024) throw new Error("脚本文件不能超过 1 MiB。");
  const script = parseScriptFile(await file.text());
  if (running.value || mode !== algebra.value) throw new Error("工作区已变更，请重新打开脚本。");
  Object.assign(form.value, script, { functionName: functionNameFromFile(file.name) });
  scriptFiles[mode].attach(handle, file.name);
  codeFile.reset();
  result.value = null; status.value = "idle"; expanded.value = false; copied.value = false;
  message.value = validName.value ? "Script opened. Check the settings, then Run." : "Script opened. The file name is not a valid function name; correct Function Name before running.";
}
async function openScript() {
  if (running.value || fileLoading.value || codeSaving.value) return;
  // Keep the ordinary input picker directly in the user's click gesture.
  if (!canPickScriptFile()) { scriptFileInput.value.click(); return; }
  fileLoading.value = true;
  const mode = algebra.value;
  try {
    const selected = await pickScriptFile();
    if (selected.kind === "fallback") scriptFileInput.value.click();
    if (selected.kind === "opened") await applyScriptFile(selected.file, selected.handle, mode);
  } catch (error) { fileMessage.error(error.message); }
  finally { fileLoading.value = false; }
}
async function uploadScript(event) {
  const file = event.target.files?.[0];
  event.target.value = "";
  if (!file || running.value || fileLoading.value) return;
  fileLoading.value = true;
  try { await applyScriptFile(file, null, algebra.value); }
  catch (error) { fileMessage.error(error.message); }
  finally { fileLoading.value = false; }
}
async function saveScript(saveAs = false, downloadOnly = false) {
  if (running.value || fileLoading.value || codeSaving.value) return;
  fileLoading.value = true; scriptSaving.value = true;
  try {
    reportFileSave(await scriptFiles[algebra.value].save({
      text: serializeScriptFile(form.value), name: suggestedFileName(form.value.functionName, "gs"), saveAs, downloadOnly
    }));
  } catch (error) { fileMessage.error(error.message); }
  finally { fileLoading.value = false; scriptSaving.value = false; }
}
function importWorkspace(entry) {
  if (running.value || fileLoading.value || codeSaving.value) return;
  algebra.value = entry.algebra;
  scriptFiles[algebra.value].reset();
  codeFile.reset();
  Object.assign(form.value, entry.script, entry.qubits === undefined ? {} : { algebraDimension: entry.qubits });
  result.value = null;
  status.value = "idle";
  expanded.value = false;
  copied.value = false;
  activeView.value = "online";
  message.value = `已导入 ${entry.title}，请检查配置后手动 Run。`;
}
async function run() {
  if (running.value || fileLoading.value || codeSaving.value) return;
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
async function saveCode(saveAs = false, downloadOnly = false) {
  if (!result.value?.code || codeSaving.value || fileLoading.value) return;
  codeSaving.value = true;
  const output = result.value;
  const extension = quantumLanguages.find(item => item.value === output.language)?.extension || "txt";
  const name = suggestedFileName(output.name, extension);
  try {
    reportFileSave(await codeFile.save({ text: output.code, name, binding: `${output.algebra}:${name}`, saveAs, downloadOnly }));
  } catch (error) { fileMessage.error(error.message); }
  finally { codeSaving.value = false; }
}
function escape(event) { if (event.key === "Escape") expanded.value = false; }
onMounted(() => { document.title = "QuantumGAALOP"; window.addEventListener("keydown", escape); });
onBeforeUnmount(() => { controller?.abort(); clearTimeout(timer); window.removeEventListener("keydown", escape); });
</script>

<style scoped>
.quantum-workspace { min-height: 100vh; background: #f4f7fc; color: #253754; font-family: Arial, sans-serif; }
.q-future { align-items: flex-start; margin: 30px; }
main { padding: 14px; display: flex; flex-direction: column; gap: 12px; }
.q-toolbar { display: grid; grid-template-columns: 180px minmax(180px, 1.4fr) 70px 1fr 1.25fr 1.3fr 1fr; gap: 16px; align-items: center; background: white; padding: 12px 16px; border: 1px solid #e3ebf6; border-radius: 9px; }
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

<style scoped>
.script-file-actions { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 6px; }
.script-file-button { display: inline-flex; align-items: center; justify-content: center; gap: 4px; padding: 5px 7px; border: 1px solid #d9e2f2; border-radius: 5px; background: #fff; color: #425675; font-family: inherit; font-size: 12px; line-height: 16px; cursor: pointer; white-space: nowrap; }
.script-file-button:hover { color: #2455ff; border-color: #8caaff; background: #f5f8ff; }
.script-file-button:focus-visible { outline: 2px solid #2455ff; outline-offset: 2px; }
.script-file-button:disabled { opacity: .5; cursor: not-allowed; }
</style>

<style scoped>
.q-toolbar { grid-template-columns: 332px minmax(170px,1.3fr) 66px minmax(110px,1fr) minmax(150px,1.3fr) minmax(150px,1fr); align-items: end; gap: 12px; }
.q-run { display: flex; align-items: center; gap: 10px; }
.q-help-button { height: 34px; white-space: nowrap; }
.q-mode-help { max-width: 360px; font-size: 12px; line-height: 1.6; }
.q-mode-help p { margin: 0 0 8px; }
.q-mode-help p:last-child { margin-bottom: 0; }
.q-name-error { color: #a92c35; font-size: 10px; line-height: 1.4; }
.q-run .q-primary { height: 34px; padding: 0 16px; white-space: nowrap; }
.script-file-actions { margin-top: 0; flex-wrap: nowrap; }
.script-file-button { height: 34px; padding: 0 9px; }
@media (max-width: 1400px) { .q-toolbar { grid-template-columns: repeat(3,minmax(0,1fr)); } .q-run { grid-column: 1 / -1; } }
@media (max-width: 760px) { .q-toolbar { grid-template-columns: repeat(2,minmax(0,1fr)); } }
</style>

<style scoped>
.quantum-workspace { height: 100dvh; min-height: 700px; display: flex; flex-direction: column; }
.quantum-workspace > main { flex: 1; min-height: 0; display: grid; grid-template-rows: auto minmax(260px, 1.15fr) auto minmax(220px, 1fr); }
.q-input-grid, .q-output-grid { height: auto; min-height: 0; }
.q-code-output { min-height: 0; }
.q-charts { flex: 1; min-height: 0; max-height: none; }
.q-empty { flex: 1; min-height: 0; }
.q-toolbar, .q-message { min-width: 0; }
@media (max-width: 760px) {
 .quantum-workspace { height: auto; min-height: 100dvh; }
 .quantum-workspace > main { display: flex; }
 .q-code-output { min-height: 320px; }
 .q-empty { min-height: 220px; }
}
</style>
