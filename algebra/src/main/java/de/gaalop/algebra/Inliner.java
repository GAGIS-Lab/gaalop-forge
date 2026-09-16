package de.gaalop.algebra;

import de.gaalop.OptimizationException;
import de.gaalop.cfg.*;
import de.gaalop.dfg.Expression;
import de.gaalop.dfg.FloatConstant;
import de.gaalop.dfg.MacroCall;
import de.gaalop.dfg.MathFunction;
import de.gaalop.dfg.MathFunctionCall;
import de.gaalop.dfg.Relation;
import de.gaalop.dfg.Variable;
import de.gaalop.visitors.DFGTraversalVisitor;
import de.gaalop.visitors.ReplaceVisitor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

/**
 * Inlines the macros in a ControlFlowGraph
 * @author Christian Steinmetz
 */
public class Inliner extends EmptyControlFlowVisitor {

    private int count = 0;
    private HashMap<StringIntContainer, Macro> macros;
    private ControlFlowGraph graph;
    
    private ColorNode currentColorNode = null;

    private boolean containsMacroCall(Expression e) {
        final LinkedList<MacroCall> calls = new LinkedList<MacroCall>();
        e.accept(new DFGTraversalVisitor() {
            @Override
            public void visit(MacroCall node) {
                calls.add(node);
            }
        });
        return !calls.isEmpty();
    }

    private Inliner(HashMap<StringIntContainer, Macro> macros, ControlFlowGraph graph) {   //private constructor for making the usage of the static methods mandatory
        this.macros = macros;
        this.graph = graph;
    }

    public static void inline(ControlFlowGraph graph, HashMap<StringIntContainer, Macro> macros) throws OptimizationException {
        Inliner inliner = new Inliner(macros, graph);
        while (inliner.error == null && MacroCallCounter.countMacroCallsInGraph(graph)>0)
            graph.accept(inliner);
        if (inliner.error != null)
            throw inliner.error;
    }

    @Override
    public void visit(ColorNode node) {
        curNode = node;
        //make all non-variable arguments to variables
        Expression c = node.getR();
        if (!((c instanceof Variable) || (c instanceof FloatConstant))) {
            Variable newVariable = createNewVariable("color", "r");
            AssignmentNode assignmentNode = new AssignmentNode(graph, newVariable, c);
            curNode.insertBefore(assignmentNode); 
            StoreResultNode storeNode = new StoreResultNode(graph, newVariable);
            curNode.insertBefore(storeNode);
            node.setR(newVariable);
        } 
        
        c = node.getG();
        if (!((c instanceof Variable) || (c instanceof FloatConstant))) {
            Variable newVariable = createNewVariable("color", "g");
            AssignmentNode assignmentNode = new AssignmentNode(graph, newVariable, c);
            curNode.insertBefore(assignmentNode); 
            StoreResultNode storeNode = new StoreResultNode(graph, newVariable);
            curNode.insertBefore(storeNode);
            node.setG(newVariable);
        } 

        c = node.getB();
        if (!((c instanceof Variable) || (c instanceof FloatConstant))) {
            Variable newVariable = createNewVariable("color", "b");
            AssignmentNode assignmentNode = new AssignmentNode(graph, newVariable, c);
            curNode.insertBefore(assignmentNode); 
            StoreResultNode storeNode = new StoreResultNode(graph, newVariable);
            curNode.insertBefore(storeNode);
            node.setB(newVariable);
        } 
        
        c = node.getAlpha();
        if (!((c instanceof Variable) || (c instanceof FloatConstant))) {
            Variable newVariable = createNewVariable("color", "a");
            AssignmentNode assignmentNode = new AssignmentNode(graph, newVariable, c);
            curNode.insertBefore(assignmentNode); 
            StoreResultNode storeNode = new StoreResultNode(graph, newVariable);
            curNode.insertBefore(storeNode);
            node.setAlpha(newVariable);
        } 
        
        currentColorNode = node;
        super.visit(node);
    }

    private SequentialNode curNode;

    private OptimizationException error;

    @Override
    public void visit(AssignmentNode node) {
        curNode = node;
        
        while (containsMacroCall(node.getValue()) && error == null) {
            replacer.result = null;
            node.getValue().accept(replacer);
            if (replacer.result != null) 
                node.setValue(replacer.result);
        }
        super.visit(node);
    }

    @Override
    public void visit(ExpressionStatement node) {
        curNode = node;
        replacer.result = null;
        delete = false;
        node.getExpression().accept(replacer);
        boolean del = delete;
        Expression result = replacer.result;
        if (result != null)
            node.setExpression(result);

        super.visit(node);
        
        if (del)
            graph.removeNode(node);
    }

    private boolean delete;

    private ReplaceVisitor replacer = new ReplaceVisitor() {

        @Override
        public void visit(MacroCall macroExpr) {
            if (error != null)
                return;
            String macroCallName = macroExpr.getName();
            //check if this macro call is a builtin function
            if ("coefficient".equals(macroCallName.toLowerCase())) {
                result = new Relation(macroExpr.getArguments().get(0), macroExpr.getArguments().get(1), Relation.Type.COEFFICIENT);
                return;
            }

            //check if this macro call is a MathFunction
            for (MathFunction f: MathFunction.values()) {
                if (f.name().toLowerCase().equals(macroCallName.toLowerCase())) { 
                    // it is a MathFunction
                    result = new MathFunctionCall(macroExpr.getArguments().get(0), f);
                    return;
                }
            }

            StringIntContainer container = new StringIntContainer(macroCallName, macroExpr.getArguments().size());
            if (!macros.containsKey(container)) {
                result = null;
                // Only standalone, unknown function calls can be handed to a renderer.
                // A macro used as a value must be defined before later compiler stages run.
                boolean rendererCall = curNode instanceof ExpressionStatement
                        && ((ExpressionStatement) curNode).getExpression() == macroExpr
                        && !"*".equals(macroCallName);
                for (StringIntContainer knownMacro : macros.keySet()) {
                    if (knownMacro.getName().equals(macroCallName))
                        rendererCall = false;
                }
                if (!rendererCall) {
                    String message = "*".equals(macroCallName)
                            ? "The unary dual operator '*' is not defined for algebra '" + graph.algebraName
                                    + "'. Define a one-argument Dual macro or use an algebra that provides one."
                            : "Macro '" + macroCallName + "' with " + macroExpr.getArguments().size()
                                    + " arguments is not defined for algebra '" + graph.algebraName + "'.";
                    error = new OptimizationException(message, graph);
                    return;
                }
                delete = true;
                graph.unknownMacros.add(new UnknownMacroCall(macroExpr, currentColorNode));
                //make all non-variable arguments to variables
                ArrayList<Expression> newArgs = new ArrayList<Expression>(macroExpr.getArguments().size());
                for (Expression arg: macroExpr.getArguments()) {
                    if (!((arg instanceof Variable) || (arg instanceof FloatConstant))) {
                        Variable newVariable = createNewVariable(macroCallName, "arg");
                        AssignmentNode assignmentNode = new AssignmentNode(graph, newVariable, arg);
                        curNode.insertBefore(assignmentNode); 
                        StoreResultNode storeNode = new StoreResultNode(graph, newVariable);
                        curNode.insertBefore(storeNode);
                        newArgs.add(newVariable);
                    } else 
                        newArgs.add(arg);
                }
                macroExpr.setArgs(newArgs);
                return;
            }
            Macro macro = macros.get(container);

            HashMap<String, Expression> replaceMap = new HashMap<String, Expression>();
            //fill replaceMap with macro call arguments
            int index = 1;
            for (Expression e: macroExpr.getArguments()) {
                replaceMap.put("_P("+index+")", e);
                index++;
            }

            //add replacements
            for (SequentialNode sNode: macro.getBody()) {
                if (sNode instanceof AssignmentNode) {
                    AssignmentNode assignmentNode = (AssignmentNode) sNode;
                    String name = assignmentNode.getVariable().getName();

                    if (!replaceMap.containsKey(name)) {
                        Variable newVar = createNewVariable(macroCallName, name);
                        replaceMap.put(name, newVar);
                        if (graph.getOnlyEvaluateNodes().contains(assignmentNode))
                            graph.addPragmaOnlyEvaluateVariable(newVar.getName()); 
                    }
                }
            }

            for (SequentialNode sNode: macro.getBody()) {
                SequentialNode copySNode = sNode.copy(graph);
                //replace variables in copySNode
                MacroVariablesCFGReplacer r = new MacroVariablesCFGReplacer(replaceMap);
                copySNode.accept(r);
                //
                curNode.insertBefore(copySNode);
            }
            
            Expression copiedReturnVal = macro.getReturnValue().copy();
            //replace variables in copiedReturnVal
            MacroVariablesDFGReplacer d = new MacroVariablesDFGReplacer(replaceMap);
            result = d.replace(copiedReturnVal);
        }
    };

    private Variable createNewVariable(String macroName, String originVarName) {
        VariableCollector collector = new VariableCollector();
        graph.accept(collector);

        String m = "macro_"+macroName+"_"+originVarName;
        if (collector.getVariables().contains(m)) {
            count = 1;
            while (collector.getVariables().contains(m+count))
                count++;

            return new Variable(m+count);
        } else {
            return new Variable(m);
        }
    }

}
