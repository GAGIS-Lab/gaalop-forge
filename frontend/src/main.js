import { createApp } from "vue";
import { Alert, Avatar, Col, Input, Row, Select } from "ant-design-vue";
import editorWorker from "monaco-editor/esm/vs/editor/editor.worker?worker";
import App from "./App.vue";
import "ant-design-vue/dist/reset.css";
import "./styles.css";

globalThis.MonacoEnvironment = {
  getWorker() {
    return new editorWorker();
  }
};

createApp(App)
  .use(Alert)
  .use(Avatar)
  .use(Col)
  .use(Input)
  .use(Row)
  .use(Select)
  .mount("#app");
