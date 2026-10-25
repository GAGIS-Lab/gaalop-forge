package de.gaalop;

import de.gaalop.cfg.*;
import de.gaalop.dfg.FloatConstant;
import de.gaalop.dfg.MultivectorComponent;
import de.gaalop.dfg.Variable;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Restores the output contract after sparse optimization, at code generation
 * (and before GAPP decoration). Internal zero coefficients remain eliminated. */
public final class ZeroOutputRestorer {
    private static final Pattern PLACEHOLDER = Pattern.compile("<([^<>]+)>");

    private ZeroOutputRestorer() { }

    public static void restore(final ControlFlowGraph graph) {
        final Set<String> outputs = new HashSet<>(graph.getOutputs());
        final Map<String, SortedSet<Integer>> required = new HashMap<>();
        for (String selection : graph.getPragmaOutputVariables()) {
            int separator = selection.lastIndexOf('$');
            if (separator >= 0 && outputs.contains(selection.substring(0, separator))) {
                required.computeIfAbsent(selection.substring(0, separator), key -> new TreeSet<>())
                        .add(Integer.parseInt(selection.substring(separator + 1)));
            }
        }
        for (ReturnDefinition definition : graph.getReturnDefinitions()) {
            for (String output : definition.variableNames) {
                if (!outputs.contains(output)) continue;
                Matcher matcher = PLACEHOLDER.matcher(definition.returnText);
                while (matcher.find()) {
                    required.computeIfAbsent(output, key -> new TreeSet<>()).add(indexOf(graph, matcher.group(1)));
                }
            }
        }
        graph.accept(new EmptyControlFlowVisitor() {
            private final Map<String, Set<Integer>> assigned = new HashMap<>();

            @Override
            public void visit(AssignmentNode node) {
                if (node.getVariable() instanceof MultivectorComponent) {
                    MultivectorComponent component = (MultivectorComponent) node.getVariable();
                    assigned.computeIfAbsent(component.getName(), key -> new HashSet<>()).add(component.getBladeIndex());
                } else {
                    assigned.computeIfAbsent(node.getVariable().getName(), key -> new HashSet<>()).add(-1);
                }
                super.visit(node);
            }

            @Override
            public void visit(StoreResultNode node) {
                String output = node.getValue().getName();
                Set<Integer> present = assigned.computeIfAbsent(output, key -> new HashSet<>());
                if (present.contains(-1)) {
                    super.visit(node);
                    return; // Unlowered scalar/output assignments are already defined.
                }
                Set<Integer> selected = required.get(output);
                if (selected == null) {
                    // Without grade/return metadata, scalar zero is the canonical
                    // sparse representation of an entirely eliminated multivector.
                    selected = present.isEmpty() ? Collections.singleton(0) : Collections.emptySet();
                }
                for (int index : selected) {
                    if (present.add(index)) {
                        node.insertBefore(new AssignmentNode(graph, new MultivectorComponent(output, index), new FloatConstant(0.0)));
                        graph.addLocalVariable(new Variable(output));
                    }
                }
                super.visit(node);
            }
        });
    }

    private static int indexOf(ControlFlowGraph graph, String placeholder) {
        for (int index = 0; index < graph.getAlgebraDefinitionFile().getBladeCount(); index++) {
            String blade = graph.getBladeString(new MultivectorComponent("", index))
                    .replace(".0", "").replaceAll("[ ()^]", "");
            if (blade.equals(placeholder)) return index;
        }
        throw new IllegalArgumentException("Unknown return blade <" + placeholder + ">");
    }
}
