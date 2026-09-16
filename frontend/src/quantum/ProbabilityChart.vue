<template>
  <section class="q-state">
    <h4>{{ name }} · Quantum state probability distribution (n={{ state.nqubits }})</h4>
    <p v-if="invalidState" class="q-warning">This output is not a normalized state in the selected algebra’s ket subspace. Values are shown without normalization.</p>
    <div class="q-plot-scroll" tabindex="0" :aria-label="`${name} probability chart, scroll horizontally for all basis states`">
      <svg :viewBox="`0 0 ${width} 290`" :style="{ width: `${width}px`, minWidth: '100%' }" role="img" :aria-label="`${name} basis-state probabilities`">
        <text x="12" y="16" fill="#687993" font-size="11">Probability |α|²</text>
        <g v-for="tick in [0, 1, 2, 3, 4]" :key="tick">
          <line x1="48" :x2="width - 12" :y1="230 - tick * 50" :y2="230 - tick * 50" stroke="#e3e9f2" />
          <text x="42" :y="234 - tick * 50" text-anchor="end" font-size="10" fill="#687993">{{ (maximum * tick / 4).toFixed(2) }}</text>
        </g>
        <g v-for="(p, index) in state.probabilities" :key="index">
          <rect :x="48 + index * step + 5" :y="230 - p / maximum * 200" :width="step - 10" :height="p / maximum * 200" :fill="p > 0 && p >= peak * 0.95 ? '#ef4444' : '#80c7fa'">
            <title>{{ state.labels[index] }}: P={{ p }}; Re(α)={{ state.real[index] }}; Im(α)={{ state.imaginary[index] }}</title>
          </rect>
          <text v-if="state.probabilities.length <= 16" :x="48 + (index + 0.5) * step" :y="224 - p / maximum * 200" text-anchor="middle" font-size="10">{{ p.toFixed(4) }}</text>
          <text :transform="`translate(${48 + (index + 0.5) * step},244) rotate(40)`" font-size="10" fill="#354663">{{ state.labels[index] }}</text>
        </g>
      </svg>
    </div>
    <div class="q-statistics"><span>Total probability = {{ state.totalProbability.toPrecision(7) }}</span><span>Residual norm = {{ state.residualNorm.toExponential(3) }}</span></div>
    <details><summary>Amplitudes and probabilities · {{ state.labels.length }} basis states</summary>
      <div class="q-table-scroll"><table><thead><tr><th>Basis state</th><th>Re(α)</th><th>Im(α)</th><th>|α|²</th></tr></thead><tbody>
        <tr v-for="(label, index) in state.labels" :key="label"><th>{{ label }}</th><td>{{ state.real[index].toPrecision(7) }}</td><td>{{ state.imaginary[index].toPrecision(7) }}</td><td>{{ state.probabilities[index].toPrecision(7) }}</td></tr>
      </tbody></table></div>
    </details>
  </section>
</template>
<script setup>
import { computed } from "vue";
const props = defineProps({ name: String, state: { type: Object, required: true } });
const width = computed(() => Math.max(580, 60 + props.state.labels.length * 34));
const step = computed(() => (width.value - 60) / props.state.labels.length);
const peak = computed(() => Math.max(...props.state.probabilities));
const maximum = computed(() => Math.max(1, peak.value * 1.08));
const invalidState = computed(() => Math.abs(props.state.totalProbability - 1) > 1e-6 || props.state.residualNorm > 1e-8);
</script>
<style scoped>
.q-state { padding: 14px; border-bottom: 1px solid #e4ebf5; }
h4 { margin: 0 0 12px; font-size: 13px; color: #253754; }
.q-plot-scroll { overflow-x: auto; } svg { display: block; height: 290px; }
.q-statistics { display: flex; flex-wrap: wrap; gap: 12px; font: 11px Consolas, monospace; color: #596b87; }
.q-warning { padding: 8px; background: #fff7e6; color: #855600; font-size: 12px; }
details { margin-top: 12px; font-size: 12px; } summary { cursor: pointer; color: #2455ff; }
.q-table-scroll { max-height: 220px; overflow: auto; } table { width: 100%; border-collapse: collapse; } th, td { padding: 6px; text-align: right; border-bottom: 1px solid #e4ebf5; }
</style>
