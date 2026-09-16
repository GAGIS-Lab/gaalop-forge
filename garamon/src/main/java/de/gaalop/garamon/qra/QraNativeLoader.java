// QraNativeLoader.java
package de.gaalop.garamon.qra;

import java.io.File;

public final class QraNativeLoader {
    private QraNativeLoader() {}

    public static QraNativeLibrary load(QraAlgebraId id) {
        if (id == null) throw new IllegalArgumentException("Expected qra2 through qra9");
        String arch = System.getProperty("os.arch", "");
        if (!arch.equals("amd64") && !arch.equals("x86_64")) {
            throw new IllegalStateException("QRA native libraries require x86_64");
        }
        boolean windows = System.getProperty("os.name", "").startsWith("Windows");
        boolean linux = System.getProperty("os.name", "").equals("Linux");
        if (!windows && !linux) throw new IllegalStateException("QRA native libraries support Windows and Linux");
        String platform = windows ? "windows-x86_64" : "linux-x86_64";
        String fileName = windows ? id.dllBaseName + ".dll" : "lib" + id.dllBaseName + ".so";
        String[] roots = {
                System.getProperty("gaalop.garamon.nativeDir", ""),
                "nativeLibraries/garamon",
                "distribution/src/main/resources/nativeLibraries/garamon"
        };

        for (String root : roots) {
            if (root == null || root.trim().isEmpty()) continue;
            File dll = new File(new File(new File(root, platform), id.id), fileName);
            if (dll.isFile()) return new QraNativeLibrary(id, dll);
        }

        throw new IllegalStateException("Missing Garamon native library for " + id.id + " on " + platform + ": " + fileName);
    }
}
