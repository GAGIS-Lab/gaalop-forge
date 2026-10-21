import { defineAsyncComponent } from "vue";
import { mountWorkspace } from "./mountWorkspace.js";

mountWorkspace(defineAsyncComponent(() => import("./quantum/QuantumWorkspace.vue")), "quantum", "QuantumGAALOP");
