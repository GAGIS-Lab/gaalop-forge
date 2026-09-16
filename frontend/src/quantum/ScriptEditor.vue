<template>
  <div class="q-script">
    <textarea v-if="!ready" :value="modelValue" :aria-label="label" :readonly="readOnly" spellcheck="false" @input="$emit('update:modelValue', $event.target.value)"></textarea>
    <div v-show="ready" ref="host" class="q-script-host"></div>
    <small>{{ failed ? 'Plain text editor' : readOnly ? 'Generated output' : 'GAALOPScript' }} · {{ modelValue.split('\n').length }} lines</small>
  </div>
</template>

<script setup>
import { onMounted, onBeforeUnmount, ref, watch } from "vue";
const props = defineProps({ modelValue: { type: String, default: "" }, label: String, readOnly: Boolean });
const emit = defineEmits(["update:modelValue"]);
const host = ref(null), ready = ref(false), failed = ref(false);
let editor, subscription, disposed = false;
onMounted(async () => {
  try {
    const monaco = await import("monaco-editor/esm/vs/editor/editor.api");
    if (disposed) return;
    if (!monaco.languages.getLanguages().some(({ id }) => id === "quantum-gaalop")) {
      monaco.languages.register({ id: "quantum-gaalop" });
      monaco.languages.setMonarchTokensProvider("quantum-gaalop", { tokenizer: { root: [
        [/\/\/.*$/, "comment"], [/\b(?:e\d+|er1|er2)\b/, "type"],
        [/\b(?:sin|cos|sqrt|exp|log|abs)\b/, "keyword"],
        [/\d+(?:\.\d+)?(?:[eE][+-]?\d+)?/, "number"], [/[?~=+*^:;-]/, "operator"]
      ] } });
    }
    // Preserve any typing performed while the editor bundle was loading.
    editor = monaco.editor.create(host.value, {
      value: props.modelValue, language: props.readOnly ? "plaintext" : "quantum-gaalop",
      readOnly: props.readOnly, ariaLabel: props.label, automaticLayout: true,
      editContext: false,
      minimap: { enabled: false }, fontSize: 12, lineNumbersMinChars: 3,
      scrollBeyondLastLine: false, tabSize: 4, padding: { top: 8 },
      fixedOverflowWidgets: true, overviewRulerLanes: 0
    });
    subscription = editor.onDidChangeModelContent(() => emit("update:modelValue", editor.getValue()));
    ready.value = true;
  } catch { failed.value = true; }
});
watch(() => props.modelValue, value => { if (editor && editor.getValue() !== value) editor.setValue(value); });
onBeforeUnmount(() => { disposed = true; subscription?.dispose(); const model = editor?.getModel(); editor?.dispose(); model?.dispose(); });
</script>

<style scoped>
.q-script { display: flex; flex-direction: column; flex: 1; min-height: 0; }
.q-script-host, textarea { flex: 1; min-height: 100px; width: 100%; }
textarea { border: 0; padding: 12px; resize: none; font: 12px/1.6 Consolas, monospace; color: #253754; background: white; }
small { text-align: right; color: #7886a1; font-size: 10px; padding: 4px 10px; }
</style>
