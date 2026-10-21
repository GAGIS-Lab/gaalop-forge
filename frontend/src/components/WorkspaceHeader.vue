<template>
  <header class="topbar workspace-header">
    <div class="topbar-brand">{{ quantum ? 'QuantumGAALOP' : 'GAALOPWeb' }}</div>
    <nav class="topbar-nav" aria-label="Main navigation">
      <button class="nav-item nav-button" :class="{ active: activeView === 'home' }" :aria-current="activeView === 'home' ? 'page' : undefined" type="button" @click="$emit('navigate', 'home')">Home</button>
      <button class="nav-item nav-button" :class="{ active: activeView === 'online' }" :aria-current="activeView === 'online' ? 'page' : undefined" type="button" @click="$emit('navigate', 'online')">Online Editing</button>
      <a class="nav-item workspace-switch" :href="switchUrl">{{ quantum ? 'GAALOPWeb' : 'QuantumGAALOP' }}</a>
      <button class="nav-item nav-button" :class="{ active: activeView === 'agent' }" :aria-current="activeView === 'agent' ? 'page' : undefined" type="button" @click="$emit('navigate', 'agent')">GA-CodeAgent</button>
    </nav>
    <div class="topbar-actions">
      <div class="last-run-chip" :class="{ error }" role="status">
        <CloseCircleOutlined v-if="error" /><CheckOutlined v-else />
        <span>{{ statusText }}</span>
      </div>
      <div class="account-chip">
        <a-avatar :size="36" class="account-avatar"><template #icon><UserOutlined /></template></a-avatar>
        <span>GACRAC</span><DownOutlined />
      </div>
    </div>
  </header>
</template>

<script setup>
import { workspaceUrl } from "../workspaceNavigation.js";
import { computed } from "vue";
import { CheckOutlined, CloseCircleOutlined, DownOutlined, UserOutlined } from "@ant-design/icons-vue";
const props = defineProps({ quantum: Boolean, activeView: { type: String, default: "online" }, statusText: { type: String, default: "Ready to compile" }, error: Boolean });
defineEmits(["navigate"]);
const switchUrl = computed(() => workspaceUrl(props.quantum ? "traditional" : "quantum", window.location.href));
</script>

<style scoped>
.workspace-header { flex-shrink: 0; font-family: "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif; }
/* Reserve the longer workspace name so switching keeps every navigation item aligned. */
.topbar-brand { flex: 0 0 9.5em; }
.topbar-nav { flex-shrink: 0; }
.workspace-switch { flex: 0 0 10.5em; text-align: center; }
.topbar-brand, .nav-item, .last-run-chip, .account-chip { white-space: nowrap; }
@media (max-width: 1200px) {
  .workspace-header { gap: 12px; }
  .topbar-brand { font-size: 23px; }
  .topbar-nav { margin-left: 8px; gap: 0; }
  .nav-item { padding-left: 10px; padding-right: 10px; font-size: 13px; }
  .topbar-actions { gap: 8px; }
  .last-run-chip { font-size: 11px; padding: 5px 8px; }
}
@media (max-width: 760px) {
  .workspace-header { height: auto; min-height: var(--topbar-h); flex-wrap: wrap; padding: 10px 12px; }
  .topbar-nav { order: 3; width: 100%; margin: 0; overflow-x: auto; }
  .topbar-actions { margin-left: auto; }
  .account-chip { font-size: 12px; gap: 5px; }
  .last-run-chip { display: none; }
}
</style>
