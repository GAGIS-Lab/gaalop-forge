// QraAlgebraId.java
package de.gaalop.garamon.qra;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class QraAlgebraId {
    private static final Pattern PATTERN = Pattern.compile("^qra([2-9])$", Pattern.CASE_INSENSITIVE);

    public final int nqubits;
    public final String id;
    public final String prefix;
    public final String dllBaseName;

    private QraAlgebraId(int nqubits) {
        this.nqubits = nqubits;
        this.id = "qra" + nqubits;
        this.prefix = this.id + "_";
        this.dllBaseName = "Qra" + nqubits + "Bridge";
    }

    public static QraAlgebraId detect(String algebraName) {
        if (algebraName == null) return null;
        Matcher m = PATTERN.matcher(algebraName.trim());
        return m.matches() ? new QraAlgebraId(Integer.parseInt(m.group(1))) : null;
    }
}
