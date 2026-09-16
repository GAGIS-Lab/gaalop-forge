# QCA integration

This is a focused port from [orat/Gaalop, commit 34d13219d678e67c43b3aab7ff7bee7b5b2c48e4](https://github.com/orat/Gaalop/tree/34d13219d678e67c43b3aab7ff7bee7b5b2c48e4), initially reviewed on 2026-09-12. On 2026-09-14 it was updated with [upstream metric fix 11da0993d2278945a83a98717c4dd5b8325015c0](https://github.com/orat/Gaalop/commit/11da0993d2278945a83a98717c4dd5b8325015c0), included in the fetched upstream master [b09ce5b91312611bc8262be26cfe50d804499700](https://github.com/orat/Gaalop/tree/b09ce5b91312611bc8262be26cfe50d804499700). The source diff between the two reviewed upstream snapshots is only the `ei2` metric sign. This remains a focused port, not a merge of the entire fork.

## Included

- QCA generation in `AlgebraDefinitionFile.createQCA`, retaining upstream basis order, transformations and corrected metric signs; the QCA macros are unchanged between the reviewed upstream revisions.
- Basis prefixes (`f1`, `f1T`, `ei1`, `ei2`) through `BaseVector`, replacement and blade construction. Existing `BaseVector(String)` and `getIndex()` callers remain supported.
- Float coefficients through product computation, accumulation, text tables and TBA expression generation. This preserves QCA's half coefficients.
- Dimension propagation through `CompilerFacade`, the control-flow graph, CLI (`--algebraDimension`) and REST (`algebraDimension`). The original facade constructor defaults to one qubit.
- QCA registration in the shared core and REST API. On 2026-09-15, QCA/QGA entries and qubit controls were removed from the traditional Web frontend, reserving quantum workflows for a future separate QuantumGaalopWeb.

QCA computes products directly. Existing bundled algebras continue to use their existing product tables. The fork's incompatible binary table format, unrelated algebras, build changes and desktop UI redesign are not imported. Legacy compressed table writing rejects fractional coefficients instead of silently discarding them; fractional text tables are supported.

## Upstream signature discrepancy resolved on 2026-09-14

The original `34d13219` revision set `ei2²=-1`, giving `Cl(n+1,n+1,0)` despite a display label of `Cl(n+2,n,0)`. Upstream fix `11da0993` changes `ei2²` to `+1`; `ei1²=+1` and each `eip²=+1`, `eim²=-1` remain unchanged. The generated signature now matches `Cl(n+2,n,0)`: `Cl(3,1,0)`, `Cl(4,2,0)` and `Cl(5,3,0)` for one, two and three qubits.

The upstream commit title mentions `ei1`, but its actual diff changes `baseSquares.put("ei2", ...)`. This integration follows the executable change. `QcaProductTest.generatedSignatureMatchesUpstreamDisplaySignature` checks agreement across dimensions 1–10 without constructing the exponentially large blade tables; product and compilation tests use the supported Web range of 1–3 qubits.

This changes numerical results involving `ei2`: `ei2*ei2` now evaluates to `+1`, and `(ei1*ei2)*(ei1*ei2)` to `-1` (previously `+1`). Regenerate prior QCA output that depends on these products. The `f_i`/`f_iT` nilpotency, half coefficients and anticommutation relations are unchanged. QCA still computes products directly, so no precomputed product table needs updating. The Web UI does not currently display the signature.

## Usage and limits

The traditional Web frontend no longer offers quantum algebra options. QCA remains available through the shared REST API and CLI for future reuse; no separate quantum frontend has been implemented. The web service accepts 1–3 qubits, defaulting to 1 when omitted, with Code Only output. This is a conservative service limit, not a claim that larger examples cannot work. Core/CLI generation retains the upstream 1–10 range; basis construction is exponential and the upper range has not been performance-qualified. One, two and three qubits generate 16, 64 and 256 basis blades respectively.

QCA quantum visualizations and the dedicated QuantumGAALOP interface are follow-up work. Ganja, VIS2D and Visualizer requests are explicitly rejected for QCA. Other code-generation targets continue through the existing pipeline; Java, C++ and Python have focused service tests.

Example request to the existing compile endpoint:

```json
{
  "algebraPlugins": "ALGEBRA_QCA",
  "algebraDimension": 2,
  "codegenPlugins": "JAVA",
  "outputMode": "CODE_ONLY",
  "visualizationEnabled": false,
  "script": {
    "functionName": "qca_example",
    "optimizeCode": "?half=f1.f1T; ?anti=f1*f1T+f1T*f1; ?nil=f2*f2; ?sign=ei2*ei2; ?imaginarySquare=(ei1*ei2)*(ei1*ei2);"
  }
}
```

Expected scalars under the corrected revision: `half=0.5`, `anti=1`, `nil=0`, `sign=1`, `imaginarySquare=-1`. The optimizer may omit identically zero output components.

## Validation

Focused tests cover nilpotency, same-mode and cross-mode anticommutation, half coefficients, idempotence, scaled inputs, basis output, matching signatures, the auxiliary vector and bivector squares, text-table round trips, default/invalid dimensions, unsupported visualizations, multiple code generators and a CGA smoke check.

```text
mvn -pl testbenchTbaGapp,gaalop-rest -am test -Dtest=Qca*Test -Dsurefire.failIfNoSpecifiedTests=false
mvn clean test
cd frontend
pnpm build
```

Historical validation on 2026-09-12 with JDK 17: `mvn clean test` passed all 206 tests (0 failures/errors/skips), including 12 new QCA test methods; service packaging and `pnpm build` passed. Browser verification and a packaged-service HTTP check covered QCA Java/Python output using the old metric. Their `sign=-1` expectation is superseded by the 2026-09-14 correction. No desktop/server performance comparison or upstream quantum algorithm benchmark has been completed.

Validation of the upstream correction on 2026-09-14 with JDK 17: the new signature and auxiliary-square tests failed against the old metric, then `mvn clean test` passed all 217 tests (0 failures/errors/skips), including 13 QCA test methods. Core compilation checks cover one to three qubits with CSE both enabled and disabled; REST tests check named scalar assignments in Java, C++ and Python output. The REST service was repackaged and started locally; HTTP requests for one, two and three qubits generated Java sources that were separated into their original files, compiled with `javac`, and executed. All returned `ei1²=1`, `ei2²=1`, `(ei1*ei2)²=-1`, `f_n.f_nT=0.5` and `f_n*f_nT+f_nT*f_n=1`. The test service was stopped afterward. No frontend behavior changed in this update, and no server deployment was performed.
