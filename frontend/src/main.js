import { createApp } from "vue";
import { Alert, Avatar, Col, Input, Row, Select } from "ant-design-vue";
import editorWorker from "monaco-editor/esm/vs/editor/editor.worker?worker";
import { defineAsyncComponent } from "vue";
import "ant-design-vue/dist/reset.css";
import "./styles.css";

globalThis.MonacoEnvironment = {
  getWorker() {
    return new editorWorker();
  }
};

const quantumWorkspace = new URLSearchParams(window.location.search).get("workspace") === "quantum";
if (quantumWorkspace) document.body.style.minWidth = "0";
const App = defineAsyncComponent(() => quantumWorkspace ? import("./quantum/QuantumWorkspace.vue") : import("./App.vue"));

createApp(App)
  .use(Alert)
  .use(Avatar)
  .use(Col)
  .use(Input)
  .use(Row)
  .use(Select)
  .mount("#app");
