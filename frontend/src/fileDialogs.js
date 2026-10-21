import { h } from "vue";
import { Input, Modal, message } from "ant-design-vue";
import { normalizeSaveName } from "./fileAccess.js";

export function chooseSaveName(suggestedName, extension) {
  return new Promise(resolve => {
    let name = suggestedName;
    Modal.confirm({
      title: "Save as",
      icon: null,
      content: h("div", [
        h("p", "Choose a file name for the downloaded copy. Your browser controls where it is saved."),
        h(Input, { defaultValue: suggestedName, "aria-label": "File name", onChange: event => { name = event.target.value; } })
      ]),
      okText: "Save", cancelText: "Cancel",
      onOk() {
        try { resolve(normalizeSaveName(name, extension)); }
        catch (error) { message.error(error.message); return Promise.reject(error); }
      },
      onCancel() { resolve(null); }
    });
  });
}

export function reportFileSave(result) {
  if (result.kind === "saved") message.success(`Saved ${result.name}.`);
  if (result.kind === "downloaded") message.info(result.copy ? `Download started: ${result.name}. The current save target is unchanged.` : `Download started: ${result.name}. This browser saves a copy instead of writing back to the opened file.`);
}
