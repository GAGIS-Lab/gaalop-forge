import assert from "node:assert/strict";
import { fetchWorkspace, validateWorkspace, validateWorkspaceUrl, MAX_WORKSPACE_BYTES } from "../src/workspaceImport.js";
import { buildRequest, initialForm } from "../src/quantum/model.js";

const url = `http://127.0.0.1:5174/api/v1/examples/qra-grover-3/workspace?revision=1`;
const requested = validateWorkspaceUrl(url);
for (const origin of ["http://127.0.0.1:9999", "https://hub.example.org", "https://another-hub.example.org:8443"]) {
  assert.equal(validateWorkspaceUrl(`${origin}/api/v1/examples/qra-grover-3/workspace?revision=1`).url.origin, origin);
}
const example = { schemaVersion: 1, id: "qra-grover-3", revision: "1", title: "Grover", workspace: "quantum", algebra: "QRA", algebraPlugins: "ALGEBRA_QRA", qubits: 3, script: { optimizeCode: "?res=1;", variableAssignments: "", multivectorsVisualized: ":res;" } };
const parsed = validateWorkspace(example, requested);
assert.equal(parsed.script.variableAssignments, "");
assert.equal(parsed.sourceUrl, url);
for (const invalid of [url.replace("/workspace", "/other"), url + "&token=secret", url + "&revision=2", url + "#fragment", url.replace("http://", "http://user:pass@"), "file:///tmp/test", url.replace("revision=1", "revision=0")]) {
  assert.throws(() => validateWorkspaceUrl(invalid));
}
for (const patch of [{schemaVersion:2}, {id:"other"}, {revision:"2"}, {qubits:10}, {qubits:3.5}, {algebra:"__proto__"}, {algebraPlugins:"ALGEBRA_CGA"}, {workspace:"traditional"}, {script:{optimizeCode:"x"}}, {script:{...example.script,variableAssignments:{}}}, {script:{...example.script,optimizeCode:"x".repeat(MAX_WORKSPACE_BYTES+1)}}]) {
  assert.throws(() => validateWorkspace({...example,...patch},requested));
}
const cga = {...example, workspace:"traditional", algebra:"CGA", algebraPlugins:"ALGEBRA_CGA", script:{...example.script,multivectorsVisualized:""}};
delete cga.qubits;
assert.equal(validateWorkspace(cga,requested).script.multivectorsVisualized, "");
assert.throws(() => validateWorkspace({...cga,qubits:3},requested));
const qca = {...example,algebra:"QCA",algebraPlugins:"ALGEBRA_QCA"};
assert.equal(validateWorkspace(qca,requested).qubits,3);
for (const qubits of [4,5]) assert.equal(validateWorkspace({...qca,qubits},requested).qubits,qubits);
assert.throws(() => validateWorkspace({...qca,qubits:6},requested));
const qga = {...cga,workspace:"quantum",algebra:"QGA",algebraPlugins:"ALGEBRA_QGA"};
assert.equal(validateWorkspace(qga,requested).algebra,"QGA");
assert.throws(() => validateWorkspace({...qga,script:{...qga.script,multivectorsVisualized:":res;"}},requested));
for (const status of [404,409,422,500,502,503,504]) {
  await assert.rejects(fetchWorkspace(url,{fetcher:async()=>new Response("{}",{status})}));
}
let calls = 0;
const fetched = await fetchWorkspace(url,{fetcher:async(input,options)=>{
  calls++;
  assert.equal(input,"http://localhost/api/hub/examples/qra-grover-3/workspace?revision=1");assert.notEqual(input,url);assert.equal(options.credentials,"same-origin");assert.equal(options.redirect,"error");assert.equal(options.cache,"no-store");
  return new Response(JSON.stringify(example),{headers:{"content-type":"application/json"}});
}});
assert.equal(calls,1);
assert.deepEqual(fetched,parsed);
await assert.rejects(fetchWorkspace(url,{fetcher:async()=>new Response("not JSON",{headers:{"content-type":"application/json"}})}));
await assert.rejects(fetchWorkspace(url,{fetcher:async()=>new Response("x".repeat(MAX_WORKSPACE_BYTES+1),{headers:{"content-type":"application/json"}})}));
const form=initialForm("QRA");
Object.assign(form,parsed.script,{algebraDimension:parsed.qubits});
const request=buildRequest(form,"QRA");
assert.equal(request.script.multivectorsVisualized,":res;");
assert.equal(request.script.variableAssignments,"");
assert.equal(request.algebraDimension,3);
assert.equal("schemaVersion" in request,false);
assert.equal("sourceUrl" in request,false);
console.log("Workspace URL, schema, capability, revision, response limits and compile conversion checks passed.");

await fetchWorkspace(url.replace("127.0.0.1:5174", "unrelated.example"), {
  pageUrl: "https://ga.example/quantumgaalop/",
  fetcher: async (input, options) => {
    assert.equal(input, "https://ga.example/quantumgaalop/api/hub/examples/qra-grover-3/workspace?revision=1");
    assert.equal(options.credentials, "same-origin");
    return new Response(JSON.stringify(example), {headers:{"content-type":"application/json"}});
  }
});
assert.throws(() => validateWorkspaceUrl(url.replace("revision=1", "revision=" + "1".repeat(19))));
assert.throws(() => validateWorkspaceUrl(url.replace("qra-grover-3", "x".repeat(129))));
