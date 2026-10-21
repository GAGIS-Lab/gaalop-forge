export function functionNameFromFile(fileName) {
  return fileName.split(/[\\/]/).pop().replace(/\.(gs|txt)$/i, "");
}

export function isValidFunctionName(name) {
  return /^[A-Za-z_][A-Za-z0-9_]*$/.test(name.trim());
}

export const scriptSections = [
  ["optimizeCode", "// **Code to optimize:**"],
  ["variableAssignments", "// **Variable assignments:**"],
  ["multivectorsVisualized", "// **Multivectors to be visualized:**"]
];

export function parseScriptFile(text) {
  text = text.replace(/^\uFEFF/, "").replace(/\r\n?/g, "\n");
  const lines = text.split("\n");
  const markers = lines.flatMap((line, index) => {
    const section = scriptSections.findIndex(([, marker]) => line.trim() === marker);
    return section < 0 ? [] : [{ section, index }];
  });
  if (!markers.length) return { optimizeCode: text, variableAssignments: "", multivectorsVisualized: "" };
  if (markers.length !== 3 || markers.some((m, i) => m.section !== i)
      || lines.slice(0, markers[0].index).some(line => line.trim())) {
    throw new Error("文件分段格式错误：请按顺序保留三条分隔注释，每条只出现一次。");
  }
  return Object.fromEntries(markers.map((m, i) => [scriptSections[i][0],
    lines.slice(m.index + 1, markers[i + 1]?.index ?? lines.length).join("\n")]));
}

export function serializeScriptFile(script) {
  return scriptSections.map(([key, marker]) => {
    const text = script[key].replace(/\r\n?/g, "\n");
    if (text.split("\n").some(line => scriptSections.some(([, reserved]) => line.trim() === reserved))) {
      throw new Error("正文中包含分隔注释，请移除重复的分隔注释后再保存。");
    }
    return marker + "\n" + text;
  }).join("\n");
}
