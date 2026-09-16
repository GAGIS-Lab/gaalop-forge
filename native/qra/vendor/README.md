# QRA build source snapshot

Imported on 2026-09-16 from the user-supplied `QRA_resource` directory.
These files are unmodified copies of the supplied source, not a claim of a
verified upstream release/commit. No upstream Git revision was established.

- `garamon/src`: C++ algebra generator.
- `garamon/data`: generator templates (including the generated-library license).
- `garamon/LICENCE.txt`: original MIT license and author attribution.
- `eigen-3.4.1/Eigen`: required Eigen headers; version label follows the supplied
  directory. `COPYING.*` and the upstream README are retained. Per-file notices apply.
- `SHA256.json`: hashes of every imported source/license file, with paths relative
  to this directory. The manifest and this README are not included in the manifest.

Excluded: external build/output directories, binaries, Python bindings built by
the author, Eigen tests/benchmarks and `.git` metadata. Our bridge and generated
header fix live outside this snapshot in `../bridge.cpp.in` and `../generate.cmake`.

When updating dependencies, record the new source revision if available, preserve
licenses, regenerate the manifest, use a fresh build directory, and rerun product,
Java mapping and probability checks on both supported platforms. Do not copy
generated output back into this directory.
