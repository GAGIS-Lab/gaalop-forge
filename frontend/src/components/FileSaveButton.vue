<template>
  <span class="file-save-group" :class="{ compact }">
    <button class="file-save-main" type="button" :title="`Save ${label}`" :disabled="disabled" @click="$emit('save')"><SaveOutlined /><span>{{ busy ? 'Saving…' : 'Save' }}</span></button>
  </span>
</template>

<script setup>
import { SaveOutlined } from "@ant-design/icons-vue";
defineProps({ label: { type: String, required: true }, disabled: Boolean, busy: Boolean, compact: Boolean });
defineEmits(["save"]);
</script>

<style scoped>
.file-save-group { display: inline-flex; flex-shrink: 0; height: 34px; vertical-align: middle; }
.file-save-group button { display: inline-flex; align-items: center; justify-content: center; gap: 4px; padding: 0 9px; border: 1px solid #d9e2f2; background: #fff; color: #425675; font: inherit; font-size: 12px; cursor: pointer; white-space: nowrap; }
.file-save-main { border-radius: 5px; }
.file-save-group button:hover { color: #2455ff; border-color: #8caaff; background: #f5f8ff; }
.file-save-group button:focus-visible { outline: 2px solid #2455ff; outline-offset: 2px; }
.file-save-group button:disabled { opacity: .5; cursor: not-allowed; }
.compact { height: 27px; }
</style>
