import { createApp } from "vue";
import { Alert, Avatar, Col, Input, Row, Select } from "ant-design-vue";
import editorWorker from "monaco-editor/esm/vs/editor/editor.worker?worker";
import { workspaceUrl } from "./workspaceNavigation.js";
import "ant-design-vue/dist/reset.css";
import "./styles.css";

globalThis.MonacoEnvironment = {
  getWorker() {
    return new editorWorker();
  }
};

export function mountWorkspace(component, workspace, title) {
  const url = new URL(window.location.href);
  if (url.searchParams.has("workspace")) {
    const legacyWorkspace = url.searchParams.get("workspace") === "quantum" ? "quantum" : "traditional";
    window.location.replace(workspaceUrl(legacyWorkspace, url.href, true));
    return;
  }

  document.title = title;
  if (workspace === "quantum") document.body.style.minWidth = "0";
  createApp(component)
    .use(Alert)
    .use(Avatar)
    .use(Col)
    .use(Input)
    .use(Row)
    .use(Select)
    .mount("#app");
}
