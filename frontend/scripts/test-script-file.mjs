import assert from 'node:assert/strict';
import { functionNameFromFile, isValidFunctionName, parseScriptFile, serializeScriptFile, scriptSections } from '../src/scriptFile.js';
for (const script of [
  {optimizeCode:'?x=1;',variableAssignments:'',multivectorsVisualized:':x;'},
  {optimizeCode:'// 中文\n?x=a;\n',variableAssignments:'a=2;\n\n',multivectorsVisualized:''},
  {optimizeCode:'',variableAssignments:'',multivectorsVisualized:''}
]) assert.deepEqual(parseScriptFile(serializeScriptFile(script)), script);
assert.deepEqual(parseScriptFile('\uFEFF?x=1;\r\n'),{optimizeCode:'?x=1;\n',variableAssignments:'',multivectorsVisualized:''});
assert.throws(()=>parseScriptFile(scriptSections[0][1]+'\n?x=1;'));
assert.throws(()=>parseScriptFile(scriptSections.map(x=>x[1]).reverse().join('\n')));
assert.throws(()=>parseScriptFile(scriptSections.map(x=>x[1]).join('\n')+'\n'+scriptSections[2][1]));
assert.equal(functionNameFromFile('Bell_state.GS'), 'Bell_state');
assert.equal(functionNameFromFile('game.txt'), 'game');
assert.equal(functionNameFromFile('C:\\examples\\game.gs'), 'game');
for (const name of ['game', 'Bell_state2', '_state']) assert.ok(isValidFunctionName(name));
for (const file of ['2game.gs', 'game-v2.gs', 'game.v2.gs', 'my game.gs', '.gs']) {
  assert.equal(isValidFunctionName(functionNameFromFile(file)), false);
}
console.log('Script round-trip, sections, file-derived function names and invalid name checks passed.');
