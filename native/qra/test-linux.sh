#!/bin/sh
set -eu
cmake -S /src -B /work/build -DQRA_RESOURCE_ROOT="${QRA_RESOURCE_ROOT:-/src/vendor}" -DCMAKE_BUILD_TYPE=Release
cmake --build /work/build --parallel 2
ctest --test-dir /work/build --output-on-failure
cmake --install /work/build --prefix /work/install
# Optional integration against Java classes built by the Maven reactor.
if [ -f /work/classpath-linux.txt ]; then
    cd /repo/gaalop-rest
    java -Xmx2g -Dgaalop.garamon.nativeDir=/work/install \
        -cp "$(cat /work/classpath-linux.txt)" org.junit.runner.JUnitCore \
        de.gaalop.rest.service.QraServiceTest \
        de.gaalop.garamon.qra.QraStateProjectionTest \
        de.gaalop.algebra.QraBladeCatalogTest
fi
