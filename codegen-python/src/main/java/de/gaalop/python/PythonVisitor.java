package de.gaalop.python;

import de.gaalop.NonarrayCodeGeneratorVisitor;
import de.gaalop.StringList;
import de.gaalop.cfg.*;
import de.gaalop.dfg.*;
import java.util.*;


/**
 * Generates Python from the optimized control and data flow graphs.
 */
public class PythonVisitor extends NonarrayCodeGeneratorVisitor {

    protected String variableType = "float";

    protected String filename;

    private PythonRuntimeSupport runtime;
    private final Set<String> initializedArrays = new HashSet<>();
    private final Set<String> coefficientNames = new HashSet<>();

    public PythonVisitor(boolean useDouble, String filename, boolean useArrays) {
        this.filename = filename;
        if (useDouble) {
            variableType = "double";
        }
        this.useArrays = useArrays;
    }

    public PythonVisitor(String variableType) {
        this.variableType = variableType;
    }

    @Override
    protected Boolean useNamedTuples() {
        return false;
    }

    @Override
    public void visit(StartNode node) {
        // At each start, this set need to be cleared
        declaredVariableNames.clear();
        coefficientNames.clear();

        graph = node.getGraph();
        libraries.clear();
        initializedArrays.clear();
        runtime = new PythonRuntimeSupport(graph, filename.toLowerCase(Locale.ROOT), useArrays);

        addLine();

        appendIndentation();
        addCode("def " + filename.toLowerCase(Locale.ROOT) + "(");

        // Print parameters
        StringList inputs = graph.getInputs();
        addCode(inputs.join());

        addCode("):\n");
        indentation++;

        node.getSuccessor().accept(this);
    }

    @Override
    public void visit(AssignmentNode node) {

        Variable target = node.getVariable();
        String name = getNewName(target);
        // Keep the original set (and its iteration order) for tuple compatibility.
        declaredVariableNames.add(name);
        if (target instanceof MultivectorComponent) coefficientNames.add(name);
        boolean arrayComponent = useArrays && target instanceof MultivectorComponent;
        if (arrayComponent) {
            String array = target.getName();
            if (initializedArrays.add(array)) {
                addLine(array + " = " + runtime.numpy() + ".zeros(" + graph.getAlgebraDefinitionFile().blades2.length + ")");
            }
            appendIndentation();
            addCode(array + " = " + runtime.setComponent() + "(" + array + ", "
                    + ((MultivectorComponent) target).getBladeIndex() + ", ");
            node.getValue().accept(this);
            addCode(") # " + graph.getBladeString((MultivectorComponent) target));
        } else {
            appendIndentation();
            target.accept(this);
            addCode(" = ");
            node.getValue().accept(this);
        }

        addCode('\n');
        node.getSuccessor().accept(this);
    }

    @Override
    public void visit(ExpressionStatement node) {
        node.getSuccessor().accept(this);
    }

    @Override
    public void visit(StoreResultNode node) {
        node.getSuccessor().accept(this);
    }

    @Override
    public void visit(EndNode node) {
        if (graph.getOutputs().isEmpty()) {
            for (String text : graph.getInsertionTexts()) code.insert(0, text + newline);
            addLine("return None");
        } else {
            super.visit(node);
            if (useArrays) addLine("return " + graph.getOutputs().join());
        }

        List<String> imports = new ArrayList<>(libraries);
        Collections.sort(imports);
        StringBuilder preamble = new StringBuilder();
        for (String library : imports) preamble.append(library).append('\n');
        preamble.append(runtime.render());
        code.insert(0, preamble);
    }

    @Override
    public void visit(ColorNode node) {
        node.getSuccessor().accept(this);
    }


    @Override
    public void visit(MathFunctionCall mathFunctionCall) {
        if (mathFunctionCall.getFunction() == MathFunction.INVERT) {
            // Inversion reaches this scalar backend only after GA lowering.
            addCode("(1.0 / (");
            mathFunctionCall.getOperand().accept(this);
            addCode("))");
            return;
        }
        addCode(runtime.math(mathFunctionCall.getFunction()));
        addCode('(');
        mathFunctionCall.getOperand().accept(this);
        addCode(')');
    }

    @Override
    public void visit(Variable variable) {
        // usually there are no variables
        addCode(getNewName(variable));
    }

    @Override
    public void visit(MultivectorComponent component) {
        String name = component.getNewName(graph, useArrays);
        addCode(name);
    }

    @Override
    public void visit(Exponentiation exponentiation) {
        if (isSquare(exponentiation)) {
            Multiplication m = new Multiplication(exponentiation.getLeft(), exponentiation.getLeft());
            m.accept(this);
        } else {
            addCode("(");
            exponentiation.getLeft().accept(this);
            addCode(") ** (");
            exponentiation.getRight().accept(this);
            addCode(')');
        }
    }

    @Override
    public void visit(FloatConstant floatConstant) {
        addCode(Double.toString(floatConstant.getValue()));
    }

    @Override
    public void visit(Negation negation) {
        addCode('(');
        addCode('-');
        addChild(negation, negation.getOperand());
        addCode(')');
    }

    @Override
    protected String GetVariableNameFromBladeVariable(String variable) {
        // Scalar CSE temporaries need not contain a blade-name separator.
        return coefficientNames.contains(variable) ? super.GetVariableNameFromBladeVariable(variable) : "";
    }

    @Override
    protected void addReturnType(List<String> returnTypes) {
        if (returnTypes.size() != 1 || returnTypes.stream().anyMatch(type -> type.contains("Tuple["))) {
            libraries.add("from typing import Tuple");
        }

        // Add brackets if tuples are used
        String typeString = String.join(", ", returnTypes);
        String totalReturnType = returnTypes.size() == 1 ? typeString : "Tuple[" + typeString + "]";
        replaceInCode("):", ") -> " + totalReturnType + ":");
    }
}
