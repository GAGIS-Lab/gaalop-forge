<template></template>

<script setup>
import { onMounted, onBeforeUnmount, watch } from "vue";
import { message } from "ant-design-vue";
import { workspaceUrl } from "../workspaceNavigation.js";
import { fetchWorkspace } from "../workspaceImport.js";
const props = defineProps({ workspace: String, busy: Boolean });
const emit = defineEmits(["import"]);
let controller, timeout, pending;
function apply() {
  if (!pending || props.busy) return;
  const entry = pending;
  pending = null;
  const url = new URL(window.location.href);
  if (entry.workspace !== props.workspace) {
    window.location.replace(workspaceUrl(entry.workspace, url.href, true));
    return;
  }
  emit("import", entry);
  url.searchParams.delete("workspaceUrl");
  window.history.replaceState(window.history.state, "", url.href);
}
watch(() => props.busy, apply);
onMounted(async () => {
  const values = new URL(window.location.href).searchParams.getAll("workspaceUrl");
  if (!values.length) return;
  controller = new AbortController();
  timeout = setTimeout(() => controller.abort(), 15000);
  try {
    if (values.length !== 1) throw new Error("workspaceUrl 只能提供一次。");
    pending = await fetchWorkspace(values[0], { signal: controller.signal });
    apply();
  } catch (error) {
    message.error(error.name === "AbortError" ? "Hub 请求超时，当前编辑内容未改变。" : error.message + " 当前编辑内容未改变。");
  } finally { clearTimeout(timeout); }
});
onBeforeUnmount(() => { controller?.abort(); clearTimeout(timeout); });
</script>
