import { defineAsyncComponent } from "vue";
import { mountWorkspace } from "./mountWorkspace.js";

mountWorkspace(defineAsyncComponent(() => import("./App.vue")), "traditional", "GAALOPWeb");
