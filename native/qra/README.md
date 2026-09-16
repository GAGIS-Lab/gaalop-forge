# QRA native libraries (2–9 qubits)

The bridge template is adapted from the supplied `Qra7Project/Qra7Bridge/qra7_java_bridge.cpp`.
Garamon generator sources/templates and Eigen headers are bundled in `vendor/` with
their original licenses and a SHA-256 manifest. This is the default source input;
`QRA_RESOURCE_ROOT` can override it with another compatible source snapshot.
No author-specific absolute paths, binary DLLs, or generated headers are committed here.

Garamon generates Cl(2n+2,0). Internal C++ accessor names use b1,b2,... to avoid
the e12/e1^e2 collision; numeric XOR ordering and GAALOP basis names are unchanged.
The precomputed-product/accessor threshold is 32; larger grades use Garamon's
recursive implementation to limit generated C++ size and compiler memory use.

## Build

Use CMake 3.20+, a C++14 compiler and an x86_64 target. On Windows, run from a
Visual Studio developer environment, or use MinGW with its bin directory on PATH.

```text
cmake -S native/qra -B native/qra/target/build -DCMAKE_BUILD_TYPE=Release
cmake --build native/qra/target/build --config Release --parallel 2
ctest --test-dir native/qra/target/build -C Release --output-on-failure
cmake --install native/qra/target/build --config Release --prefix /path/to/nativeLibraries/garamon
```

On Windows Visual Studio add `-G "Visual Studio 17 2022" -A x64` to configure.
To build a subset, pass `-DQRA_QUBITS=2;3;4` as one quoted argument.
Changing the generator/configuration requires a fresh build directory; incomplete
upstream generation is deliberately not silently reused.

Installation creates `windows-x86_64/qraN/QraNBridge.dll` or
`linux-x86_64/qraN/libQraNBridge.so`. Set JVM property
`-Dgaalop.garamon.nativeDir=/path/to/nativeLibraries/garamon`.
MinGW builds statically link compiler runtimes, including winpthreads, so the DLLs
and smoke executables do not require MinGW on PATH at runtime. Windows binaries
cannot be used on Linux. Run Windows tests via `pwsh.exe -NoProfile -File
native/qra/test-windows.ps1 -BuildDirectory native/qra/target/build` to report loader
errors without modal desktop dialogs.

Sparse products with at most 1,048,576 nonzero coefficient pairs use the equivalent
positive-metric signed-XOR kernel. Denser products retain the Garamon implementation.
The generated dual-permutation initializer is rewritten to heap construction to
avoid a Windows DLL initialization stack overflow at 9 qubits.

The CTest suite checks all vector squares, anticommutation and random higher-grade
geometric/inner/outer products against an independent bit-parity oracle.
Passing these tests does not establish an algorithm throughput or concurrency limit.

## Java and service checks

```text
mvn -pl gaalop-rest -am test -Dtest=QraStateProjectionTest,QraServiceTest -Dsurefire.failIfNoSpecifiedTests=false -Dgaalop.garamon.nativeDir=/path/to/nativeLibraries/garamon
```

Without the nativeDir property, native integration tests skip explicitly; projection
tests still run and compare every supplied 2–6 qubit ket coefficient and 2–9 qubit
complex superpositions. Runtime projection generates sparse ket columns instead of
allocating a dense basis matrix. It returns total probability and residual without
silently normalizing or discarding out-of-subspace components.

The REST QRA path uses `ALGEBRA_QRA` and an explicit `algebraDimension` (2–9).
It numerically evaluates assigned GAALOPScript inputs. JAVA/CPP/PYTHON output is
constant output code, not a symbolic parameterized function. Visualization returns
`quantumResults` keyed by output variable; no generated-source regex parsing is used.
Unexpanded macros, unassigned variables and unsupported control flow produce errors.
The separate QuantumGaalopWeb frontend uses this backend through the REST API.

For source provenance, the generation pipeline, extension points and Docker commands,
see [the maintenance guide](../../docs/qra-acceleration-maintenance.md).
