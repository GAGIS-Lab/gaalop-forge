package de.gaalop.garamon.qra;

import de.gaalop.OptimizationException;
import de.gaalop.cfg.*;
import de.gaalop.dfg.*;
import de.gaalop.garamon.GaramonNativeEngine;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GaramonQraExpressionEvaluator extends EmptyControlFlowVisitor implements ExpressionVisitor {
    private static final double ZERO_EPS = 1.0e-12;

    private final ControlFlowGraph graph;
    private final GaramonNativeEngine engine;
    private final QraBladeOrderMapper mapper;
    private final int coeffCount;

    private final Map<String, double[]> variables = new LinkedHashMap<String, double[]>();
    private final Map<String, double[]> outputs = new LinkedHashMap<String, double[]>();

    private double[] result;

    public GaramonQraExpressionEvaluator(ControlFlowGraph graph, GaramonNativeEngine engine, QraBladeOrderMapper mapper) {
        this.graph = graph;
        this.engine = engine;
        this.mapper = mapper;
        this.coeffCount = engine.coefficientCount();
    }

    public Map<String, double[]> evaluateOutputs() throws OptimizationException {
        try {
            graph.accept(this);
            return outputs;
        } catch (EvaluationFailure ex) {
            throw new OptimizationException(ex.getMessage(), ex, graph);
        } catch (Throwable ex) {
            throw new OptimizationException("Garamon QRA expression evaluation failed: " + ex.getMessage(), ex, graph);
        }
    }

    @Override
    public void visit(AssignmentNode node) {
        double[] value = evaluate(node.getValue());

        if (node.getVariable() instanceof MultivectorComponent) {
            MultivectorComponent component = (MultivectorComponent) node.getVariable();
            double[] target = variables.get(component.getName());
            if (target == null) {
                target = zero();
            } else {
                target = copy(target);
            }

            target[gaalopToGaramon(component.getBladeIndex())] = requireScalar(value, "assignment to " + component);
            variables.put(component.getName(), target);
        } else {
            variables.put(node.getVariable().getName(), value);
        }

        node.getSuccessor().accept(this);
    }

    @Override
    public void visit(StoreResultNode node) {
        Variable value = node.getValue();

        if (value instanceof MultivectorComponent) {
            MultivectorComponent component = (MultivectorComponent) value;
            double[] mv = requireVariable(component.getName());
            double[] out = zero();
            out[0] = mv[gaalopToGaramon(component.getBladeIndex())];
            outputs.put(component.getName(), out);
        } else {
            outputs.put(value.getName(), copy(requireVariable(value.getName())));
        }

        node.getSuccessor().accept(this);
    }

    @Override
    public void visit(IfThenElseNode node) {
        fail("Garamon QRA backend does not support control flow yet: if/else");
    }

    @Override
    public void visit(LoopNode node) {
        fail("Garamon QRA backend does not support control flow yet: loop");
    }

    @Override
    public void visit(BreakNode node) {
        fail("Garamon QRA backend does not support control flow yet: break");
    }

    @Override
    public void visit(Macro node) {
        fail("Garamon QRA backend saw an unexpanded macro: " + node.getName());
    }

    @Override
    public void visit(ExpressionStatement node) {
        node.getSuccessor().accept(this);
    }

    @Override
    public void visit(ColorNode node) {
        node.getSuccessor().accept(this);
    }

    @Override
    public void visit(Addition node) {
        result = add(evaluate(node.getLeft()), evaluate(node.getRight()));
    }

    @Override
    public void visit(Subtraction node) {
        result = sub(evaluate(node.getLeft()), evaluate(node.getRight()));
    }

    @Override
    public void visit(Negation node) {
        result = scale(-1.0d, evaluate(node.getOperand()));
    }

    @Override
    public void visit(Multiplication node) {
        result = engine.geometric(evaluate(node.getLeft()), evaluate(node.getRight()));
    }

    @Override
    public void visit(OuterProduct node) {
        result = engine.outer(evaluate(node.getLeft()), evaluate(node.getRight()));
    }

    @Override
    public void visit(InnerProduct node) {
        result = engine.inner(evaluate(node.getLeft()), evaluate(node.getRight()));
    }

    @Override
    public void visit(Division node) {
        double[] left = evaluate(node.getLeft());
        double denom = requireScalar(evaluate(node.getRight()), "division denominator");
        if (denom == 0.0d) {
            fail("Division by zero in Garamon QRA evaluator.");
        }
        result = scale(1.0d / denom, left);
    }

    @Override
    public void visit(Reverse node) {
        double[] in = evaluate(node.getOperand());
        double[] out = zero();

        for (int garamonIndex = 0; garamonIndex < coeffCount; garamonIndex++) {
            int grade = Integer.bitCount(garamonIndex);
            boolean negative = ((grade * (grade - 1) / 2) & 1) != 0;
            out[garamonIndex] = negative ? -in[garamonIndex] : in[garamonIndex];
        }

        result = out;
    }

    @Override
    public void visit(FloatConstant node) {
        result = scalar(node.getValue());
    }

    @Override
    public void visit(BaseVector node) {
        int gaalopIndex = graph.getAlgebraDefinitionFile().getIndex(node.toString());
        if (gaalopIndex < 0) {
            fail("Unknown QRA base vector: " + node);
        }

        double[] mv = zero();
        mv[gaalopToGaramon(gaalopIndex)] = 1.0d;
        result = mv;
    }

    @Override
    public void visit(Variable node) {
        result = copy(requireVariable(node.getName()));
    }

    @Override
    public void visit(MultivectorComponent node) {
        double[] mv = requireVariable(node.getName());
        result = scalar(mv[gaalopToGaramon(node.getBladeIndex())]);
    }

    @Override
    public void visit(MathFunctionCall node) {
        double v = requireScalar(evaluate(node.getOperand()), node.getFunction() + " operand");

        switch (node.getFunction()) {
            case ABS:
                result = scalar(Math.abs(v));
                break;
            case ACOS:
                result = scalar(Math.acos(v));
                break;
            case ASIN:
                result = scalar(Math.asin(v));
                break;
            case ATAN:
                result = scalar(Math.atan(v));
                break;
            case CEIL:
                result = scalar(Math.ceil(v));
                break;
            case COS:
                result = scalar(Math.cos(v));
                break;
            case EXP:
                result = scalar(Math.exp(v));
                break;
            case FACT:
                result = scalar(factorial(v));
                break;
            case FLOOR:
                result = scalar(Math.floor(v));
                break;
            case LOG:
                result = scalar(Math.log(v));
                break;
            case SIN:
                result = scalar(Math.sin(v));
                break;
            case SQRT:
                result = scalar(Math.sqrt(v));
                break;
            case TAN:
                result = scalar(Math.tan(v));
                break;
            case INVERT:
                result = scalar(1.0d / v);
                break;
            default:
                fail("Unsupported math function: " + node.getFunction());
        }
    }

    @Override
    public void visit(Exponentiation node) {
        double left = requireScalar(evaluate(node.getLeft()), "exponentiation base");
        double right = requireScalar(evaluate(node.getRight()), "exponentiation exponent");
        result = scalar(Math.pow(left, right));
    }

    @Override
    public void visit(Relation node) {
        double[] left = evaluate(node.getLeft());
        double[] right = evaluate(node.getRight());

        if (node.getType() == Relation.Type.COEFFICIENT) {
            double sum = 0.0d;
            for (int i = 0; i < coeffCount; i++) {
                sum += left[i] * right[i];
            }
            result = scalar(sum);
            return;
        }

        double l = requireScalar(left, "relation left operand");
        double r = requireScalar(right, "relation right operand");
        boolean value;

        switch (node.getType()) {
            case LESS:
                value = l < r;
                break;
            case LESS_OR_EQUAL:
                value = l <= r;
                break;
            case GREATER:
                value = l > r;
                break;
            case GREATER_OR_EQUAL:
                value = l >= r;
                break;
            default:
                fail("Unsupported relation: " + node.getType());
                return;
        }

        result = scalar(value ? 1.0d : 0.0d);
    }

    @Override
    public void visit(Equality node) {
        result = scalar(requireScalar(evaluate(node.getLeft()), "equality left operand")
                == requireScalar(evaluate(node.getRight()), "equality right operand") ? 1.0d : 0.0d);
    }

    @Override
    public void visit(Inequality node) {
        result = scalar(requireScalar(evaluate(node.getLeft()), "inequality left operand")
                != requireScalar(evaluate(node.getRight()), "inequality right operand") ? 1.0d : 0.0d);
    }

    @Override
    public void visit(LogicalOr node) {
        result = scalar((truth(evaluate(node.getLeft())) || truth(evaluate(node.getRight()))) ? 1.0d : 0.0d);
    }

    @Override
    public void visit(LogicalAnd node) {
        result = scalar((truth(evaluate(node.getLeft())) && truth(evaluate(node.getRight()))) ? 1.0d : 0.0d);
    }

    @Override
    public void visit(LogicalNegation node) {
        result = scalar(!truth(evaluate(node.getOperand())) ? 1.0d : 0.0d);
    }

    @Override
    public void visit(FunctionArgument node) {
        fail("Unexpected FunctionArgument. Macros should have been inlined before Garamon QRA evaluation.");
    }

    @Override
    public void visit(MacroCall node) {
        fail("Unexpected MacroCall. Macros should have been inlined before Garamon QRA evaluation: " + node.getName());
    }

    private double[] evaluate(Expression expression) {
        expression.accept(this);
        return copy(result);
    }

    private double[] requireVariable(String name) {
        double[] value = variables.get(name);
        if (value == null) {
            fail("Unknown variable '" + name + "'. First Garamon QRA version only supports values assigned in the script.");
        }
        return value;
    }

    private int gaalopToGaramon(int gaalopIndex) {
        int garamonIndex = mapper.gaalopToGaramonIndex(gaalopIndex);
        if (garamonIndex < 0 || garamonIndex >= coeffCount) {
            fail("Cannot map Gaalop blade index " + gaalopIndex + " to Garamon blade order.");
        }
        return garamonIndex;
    }

    private double requireScalar(double[] mv, String context) {
        for (int i = 1; i < mv.length; i++) {
            if (Math.abs(mv[i]) > ZERO_EPS) {
                fail("Expected scalar for " + context + ", but found non-zero coefficient at Garamon index " + i + ".");
            }
        }
        return mv[0];
    }

    private boolean truth(double[] mv) {
        return requireScalar(mv, "logical operand") != 0.0d;
    }

    private double factorial(double value) {
        int n = (int) Math.floor(value);
        if (n < 0) {
            fail("Factorial is undefined for negative values: " + value);
        }

        double result = 1.0d;
        for (int i = 2; i <= n; i++) {
            result *= i;
        }
        return result;
    }

    private double[] zero() {
        return new double[coeffCount];
    }

    private double[] scalar(double value) {
        double[] mv = zero();
        mv[0] = value;
        return mv;
    }

    private double[] copy(double[] value) {
        return Arrays.copyOf(value, value.length);
    }

    private double[] add(double[] a, double[] b) {
        double[] out = zero();
        for (int i = 0; i < coeffCount; i++) {
            out[i] = a[i] + b[i];
        }
        return out;
    }

    private double[] sub(double[] a, double[] b) {
        double[] out = zero();
        for (int i = 0; i < coeffCount; i++) {
            out[i] = a[i] - b[i];
        }
        return out;
    }

    private double[] scale(double factor, double[] value) {
        double[] out = zero();
        for (int i = 0; i < coeffCount; i++) {
            out[i] = factor * value[i];
        }
        return out;
    }

    private void fail(String message) {
        throw new EvaluationFailure(message);
    }

    private static final class EvaluationFailure extends RuntimeException {
        EvaluationFailure(String message) {
            super(message);
        }
    }
}