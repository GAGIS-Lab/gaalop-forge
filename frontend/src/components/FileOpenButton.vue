<template>
  <span class="file-open-group">
    <button class="file-open-main" type="button" title="Open .gs or .txt" :disabled="disabled" @click="$emit('open')"><FolderOpenOutlined /><span>Open</span></button>
  </span>
</template>

<script setup>
import { FolderOpenOutlined } from "@ant-design/icons-vue";
defineProps({ disabled: Boolean });
defineEmits(["open"]);
</script>

<style scoped>
.file-open-group { display: inline-flex; flex-shrink: 0; height: 34px; vertical-align: middle; }
.file-open-group button { display: inline-flex; align-items: center; justify-content: center; gap: 4px; padding: 0 9px; border: 1px solid #d9e2f2; background: #fff; color: #425675; font: inherit; font-size: 12px; cursor: pointer; white-space: nowrap; }
.file-open-main { border-radius: 5px; }
.file-open-group button:hover { color: #2455ff; border-color: #8caaff; background: #f5f8ff; }
.file-open-group button:focus-visible { outline: 2px solid #2455ff; outline-offset: 2px; }
.file-open-group button:disabled { opacity: .5; cursor: not-allowed; }
</style>
