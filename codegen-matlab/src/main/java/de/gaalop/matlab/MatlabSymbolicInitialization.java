package de.gaalop.matlab;

import de.gaalop.UsedVariablesVisitor;
import de.gaalop.cfg.AssignmentNode;
import de.gaalop.cfg.ControlFlowGraph;
import de.gaalop.cfg.EmptyControlFlowVisitor;
import de.gaalop.dfg.MultivectorComponent;
import de.gaalop.dfg.Variable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Initializes only arrays that depend on symbolic inputs. Numeric execution
 * keeps the original assignments, including their MATLAB class and shape.
 */
final class MatlabSymbolicInitialization {
    private MatlabSymbolicInitialization() { }

    static void append(ControlFlowGraph graph, StringBuilder code, int indentation) {
        final Map<Variable, Set<Variable>> references = new LinkedHashMap<>();
        final Map<String, Set<String>> arrays = new LinkedHashMap<>();
        final Set<String> usedNames = new HashSet<>();
        final List<String> inputs = graph.getInputs();
        usedNames.addAll(inputs);
        usedNames.addAll(graph.getLocals());
        usedNames.addAll(graph.getScalars());
        usedNames.addAll(graph.getOutputs());
        graph.accept(new EmptyControlFlowVisitor() {
            @Override
            public void visit(AssignmentNode node) {
                Variable target = node.getVariable();
                UsedVariablesVisitor used = new UsedVariablesVisitor();
                node.getValue().accept(used);
                references.computeIfAbsent(target, key -> new LinkedHashSet<>()).addAll(used.getVariables());
                usedNames.add(target.getName());
                for (Variable variable : used.getVariables()) usedNames.add(variable.getName());
                if (target instanceof MultivectorComponent && !inputs.contains(target.getName())) {
                    arrays.computeIfAbsent(target.getName(), key -> new LinkedHashSet<>());
                }
                super.visit(node);
            }
        });

        // A symbolic array gives even its constant components class sym.
        // Propagate that type through scalar CSE temporaries and local arrays.
        Map<String, Set<String>> dependencies = new HashMap<>();
        for (Variable target : references.keySet()) dependencies.put(target.getName(), new LinkedHashSet<>());
        boolean changed;
        do {
            changed = false;
            for (Map.Entry<Variable, Set<Variable>> entry : references.entrySet()) {
                Set<String> target = dependencies.get(entry.getKey().getName());
                for (Variable reference : entry.getValue()) {
                    if (inputs.contains(reference.getName())) changed |= target.add(reference.getName());
                    Set<String> inherited = dependencies.get(reference.getName());
                    if (inherited != null) changed |= target.addAll(inherited);
                }
            }
        } while (changed);
        for (Map.Entry<String, Set<String>> entry : dependencies.entrySet()) {
            Set<String> array = arrays.get(entry.getKey());
            if (array != null) array.addAll(entry.getValue());
        }
        if (arrays.values().stream().allMatch(Set::isEmpty)) return;

        // A handle avoids shadowing isa by a user input; reserve its name too.
        String isSymbolic = "gaalopIsSym";
        for (int suffix = 1; usedNames.contains(isSymbolic); suffix++) isSymbolic = "gaalopIsSym_" + suffix;
        line(code, indentation, "% Preserve symbolic coefficients; keep numeric assignment types unchanged.");
        line(code, indentation, isSymbolic + " = @isa;");
        for (Map.Entry<String, Set<String>> array : arrays.entrySet()) {
            boolean first = true;
            for (String input : inputs) {
                if (!array.getValue().contains(input)) continue;
                line(code, indentation, (first ? "if " : "elseif ") + isSymbolic + "(" + input + ", 'sym')");
                // Empty indexing preserves the symbolic class, without calling
                // sym/zeros (which can themselves be valid input names).
                line(code, indentation + 1, array.getKey() + " = " + input + "([]);");
                first = false;
            }
            if (!first) line(code, indentation, "end");
        }
        code.append('\n');
    }

    private static void line(StringBuilder code, int indentation, String text) {
        for (int i = 0; i < indentation; i++) code.append('\t');
        code.append(text).append('\n');
    }
}
