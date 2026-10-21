import { algebraModes } from "./quantum/model.js";

export const MAX_WORKSPACE_BYTES = 1024 * 1024;
const traditional = ["2D", "3D", "2D_PGA", "3D_PGA", "CRA", "STA", "CGA", "GAC", "DCGA", "CCGA"];
const fields = ["optimizeCode", "variableAssignments", "multivectorsVisualized"];
const fail = message => { throw new Error(message); };

export function validateWorkspaceUrl(value) {
  let url;
  try { url = new URL(value); } catch { fail("Hub 导出地址无效。"); }
  if (!["http:", "https:"].includes(url.protocol) || url.username || url.password || url.hash) fail("Hub 导出地址必须是 HTTP(S) 地址，且不能包含登录信息或 fragment。");
  const match = url.pathname.match(/^\/api\/v1\/examples\/([A-Za-z0-9_-]{1,128})\/workspace$/);
  if (!match || [...url.searchParams.keys()].some(key => key !== "revision")
      || url.searchParams.getAll("revision").length > 1) fail("必须使用 Hub 示例的工作区导出地址。");
  const revision = url.searchParams.get("revision");
  if (revision !== null && !/^[1-9]\d{0,17}$/.test(revision)) fail("Hub 修订号无效。");
  return { url, id: match[1], revision };
}

export function validateWorkspace(data, requested) {
  if (!data || data.schemaVersion !== 1) fail("不支持此工作区协议版本（需要 schemaVersion 1）。");
  for (const key of ["id", "revision", "title", "algebra", "algebraPlugins"]) {
    if (typeof data[key] !== "string" || !data[key].trim() || data[key].length > 300) fail(`工作区字段 ${key} 无效。`);
  }
  if (data.id !== requested.id || (requested.revision !== null && data.revision !== requested.revision)) {
    fail("示例 ID 或修订号不一致，请返回 Hub 重新打开；不会自动切换版本。");
  }
  const mode = Object.hasOwn(algebraModes, data.algebra) ? algebraModes[data.algebra] : null;
  if ((!mode && !traditional.includes(data.algebra)) || data.algebraPlugins !== `ALGEBRA_${data.algebra}`) fail("工作区代数空间不受支持或标识不一致。");
  if (data.workspace !== (mode ? "quantum" : "traditional")) fail("代数空间与工作台类型不一致。");
  if (mode?.dimensions.length) {
    if (!Number.isInteger(data.qubits) || !mode.dimensions.includes(data.qubits)) fail(`${data.algebra} 不支持该 qubit 数。`);
  } else if (data.qubits !== undefined) fail("此代数不接受 qubits 参数。");
  if (!data.script || fields.some(key => typeof data.script[key] !== "string")) fail("三个编辑区必须全部提供字符串，包括空字符串。");
  if (!data.script.optimizeCode.trim()) fail("主脚本不能为空。");
  if (fields.reduce((total, key) => total + new TextEncoder().encode(data.script[key]).length, 0) > MAX_WORKSPACE_BYTES) fail("工作区脚本超过 1 MiB 限制。");
  if (data.algebra === "QGA" && (data.script.variableAssignments.trim() || data.script.multivectorsVisualized.trim())) {
    fail("quantum bit geometric algebra 仅支持主脚本代码生成，不能导入赋值或可视化脚本。");
  }
  return {
    schemaVersion: 1, id: data.id, revision: data.revision, title: data.title,
    workspace: data.workspace, algebra: data.algebra, algebraPlugins: data.algebraPlugins,
    ...(data.qubits === undefined ? {} : { qubits: data.qubits }),
    script: Object.fromEntries(fields.map(key => [key, data.script[key]])),
    sourceUrl: requested.url.href
  };
}

export async function fetchWorkspace(value, { signal, fetcher = fetch, pageUrl = globalThis.location?.href || "http://localhost/" } = {}) {
  const requested = validateWorkspaceUrl(value);
  const proxy = new URL("api/hub/examples/" + encodeURIComponent(requested.id) + "/workspace", new URL("./", pageUrl));
  if (requested.revision !== null) proxy.searchParams.set("revision", requested.revision);
  const response = await fetcher(proxy.href, {
    credentials: "same-origin", redirect: "error", cache: "no-store", referrerPolicy: "no-referrer",
    headers: { Accept: "application/json" }, signal
  });
  const errors = {
    404: "示例不存在、未发布或已下架，请返回 Hub。",
    409: "示例版本冲突或空间需要维护者确认，请返回 Hub 重新打开；不会自动替换版本。",
    422: "Hub 请求参数不合法，请检查导出链接。",
    502: "Hub 代理无法读取有效响应，请检查服务端 Hub 连接。",
    503: "Hub 代理未配置或配置无效，请设置 GAALOP_HUB_BASE_URL。",
    504: "Hub 代理请求超时，请稍后重试。"
  };
  if (response.status !== 200) fail(errors[response.status] || `Hub 服务异常（HTTP ${response.status}）。`);
  if (!response.headers.get("content-type")?.includes("application/json")) fail("Hub 未返回 JSON 工作区。");
  if (Number(response.headers.get("content-length")) > MAX_WORKSPACE_BYTES) fail("工作区响应超过 1 MiB 限制。");
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let bytes = 0, text = "";
  try {
    while (true) {
      const chunk = await reader.read();
      if (chunk.done) break;
      bytes += chunk.value.byteLength;
      if (bytes > MAX_WORKSPACE_BYTES) { await reader.cancel(); fail("工作区响应超过 1 MiB 限制。"); }
      text += decoder.decode(chunk.value, { stream: true });
    }
    text += decoder.decode();
  } finally { reader.releaseLock(); }
  let data;
  try { data = JSON.parse(text); } catch { fail("Hub 工作区 JSON 无法解析。"); }
  return validateWorkspace(data, requested);
}
