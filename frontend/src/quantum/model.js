export const draftKey = "quantum-gaalop-workspace-v1";
export const algebraModes = {
  QRA: { label: "QRA — Quantum Register Algebra", dimensions: [2, 3, 4, 5, 6, 7, 8, 9], numeric: true, visualization: true },
  QCA: { label: "QCA — Quantum Computing Algebra", dimensions: [1, 2, 3], numeric: false, visualization: true },
  QGA: { label: "quantum bit geometric algebra", dimensions: [], numeric: false, visualization: false }
};
export function draftKeyFor(algebra) { return algebra === "QRA" ? draftKey : `${draftKey}-${algebra}`; }
export function initialForm(algebra = "QRA") {
  if (algebra === "QCA") return {
    algebraDimension: 2, codegenPlugins: "JAVA", outputMode: "CODE_AND_VISUALIZATION", functionName: "qca_state",
    optimizeCode: "// QCA: vacuum and computational kets\nj=ei1*ei2;\nId=f1*f1T*f2*f2T;\nket00=Id;\nket11=f1T*f2T*Id;\n?psi=cos(theta/2)*ket00+j*sin(theta/2)*ket11;",
    variableAssignments: "theta=1.5707963267948966;", multivectorsVisualized: ":psi;"
  };
  if (algebra !== "QRA") return {
    algebraDimension: 1, codegenPlugins: "JAVA", outputMode: "CODE_ONLY",
    functionName: "qbit_ga",
    optimizeCode: "// Q bit GA: the original bundled algebra\n?positiveSquare = e1*e1;\n?nullSquare = einfx*einfx;\n?pairing = einfx.e0x;\n?scaled = scale*e1;",
    variableAssignments: "", multivectorsVisualized: ""
  };
  return {
    algebraDimension: 2, codegenPlugins: "JAVA", outputMode: "CODE_AND_VISUALIZATION", functionName: "qra_state",
    optimizeCode: `// Two-qubit state with a variable rotation angle\ni = er1*er2;\nf1 = 0.5*(e1+i*e3);\nf1T = 0.5*(e1-i*e3);\nf2 = 0.5*(e2+i*e4);\nf2T = 0.5*(e2-i*e4);\nId = f1*f1T*f2*f2T;\nket00 = Id;\nket10 = f1T*Id;\n?psi = cos(theta/2)*ket00 + sin(theta/2)*ket10;`,
    variableAssignments: "theta = 1.5707963267948966;", multivectorsVisualized: ":psi;"
  };
}
export function buildRequest(form, algebra = "QRA") {
  const numeric = algebraModes[algebra].numeric;
  const visualization = algebraModes[algebra].visualization;
  return {
    algebraPlugins: `ALGEBRA_${algebra}`, ...(algebra === "QGA" ? {} : { algebraDimension: Number(form.algebraDimension) }),
    codegenPlugins: form.codegenPlugins, outputMode: visualization ? form.outputMode : "CODE_ONLY",
    visualizationEnabled: visualization && form.outputMode !== "CODE_ONLY", optimization: { cse: false, maxima: false },
    script: { functionName: form.functionName.trim(), optimizeCode: form.optimizeCode,
      variableAssignments: numeric || (visualization && form.outputMode !== "CODE_ONLY") ? form.variableAssignments : "", multivectorsVisualized: visualization ? form.multivectorsVisualized : "" }
  };
}
export function validateResults(results, n) {
  if (!results || typeof results !== "object" || Array.isArray(results)) throw new Error("The server did not return quantum results. Check the backend version.");
  for (const state of Object.values(results)) {
    if (!state || state.nqubits !== n || ![state.labels, state.real, state.imaginary, state.probabilities].every(a => Array.isArray(a) && a.length === 2 ** n)
      || ![...state.real, ...state.imaginary, ...state.probabilities, state.totalProbability, state.residualNorm].every(Number.isFinite)
      || state.probabilities.some(p => p < 0) || !state.labels.every(label => typeof label === "string" && new RegExp(`^[01]{${n}}$`).test(label))) {
      throw new Error("The server returned invalid quantum-state data.");
    }
  }
  return results;
}
