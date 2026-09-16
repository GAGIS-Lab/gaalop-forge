package de.gaalop.rest.service;

import de.gaalop.*;
import de.gaalop.cfg.*;
import de.gaalop.dfg.*;
import de.gaalop.garamon.qra.QraStateProjection;
import java.util.*;

/** QCA ket coordinates in the corrected metric (ei1*ei2)^2=-1.
 * Vacuum P=product(f_k*f_kT); ket_b=(f1T)^b1...(fnT)^bn P.
 * Uses compiled scalar expressions, never executes generated source code.
 */
final class QcaStateEvaluator {
    static Map<String, QraStateProjection.Result> evaluate(int n, String source) throws CompilationException {
        Set<String> selected = new LinkedHashSet<>();
        Map<String, double[]> outputs = compile(n, source, selected);
        Map<String, QraStateProjection.Result> results = new LinkedHashMap<>();
        if (selected.isEmpty()) return results;
        StringBuilder basisSource = new StringBuilder("qcaVacuum=");
        for (int k=1;k<=n;k++) { if(k>1) basisSource.append('*'); basisSource.append('f').append(k).append("*f").append(k).append('T'); }
        basisSource.append(';');
        for(int state=0;state<(1<<n);state++) {
            basisSource.append("?qcaKet").append(state).append('=');
            for(int k=1;k<=n;k++) if((state & (1<<(n-k)))!=0) basisSource.append('f').append(k).append("T*");
            basisSource.append("qcaVacuum; ?qcaImag").append(state).append("=ei1*ei2*qcaKet").append(state).append(';');
        }
        Map<String, double[]> basis = compile(n, basisSource.toString(), new LinkedHashSet<>());
        // Check the disjoint/orthogonal column assumption in the actual compiler basis.
        List<double[]> columns = new ArrayList<>(basis.values());
        for(int i=0;i<columns.size();i++) for(int j=0;j<i;j++)
            if(Math.abs(dot(columns.get(i),columns.get(j)))>1e-12) throw new IllegalStateException("QCA ket columns are not orthogonal");
        for(String name:selected) {
            double[] input = outputs.get(name);
            if(input == null) throw new IllegalArgumentException("No QCA output for selected state " + name);
            double[] residual=input.clone(), re=new double[1<<n], im=new double[1<<n];
            for(int state=0;state<re.length;state++) {
                re[state]=project(input,residual,basis.get("qcaKet"+state));
                im[state]=project(input,residual,basis.get("qcaImag"+state));
            }
            double norm=0; for(double x:residual) norm=Math.hypot(norm,x);
            QraStateProjection.Result result=new QraStateProjection.Result(n,re,im,norm);
            if(!Double.isFinite(result.totalProbability)) throw new IllegalArgumentException("Non-finite QCA probability");
            results.put(name,result);
        }
        return results;
    }

    private static double dot(double[] a,double[] b) { double sum=0; for(int i=0;i<a.length;i++) sum+=a[i]*b[i]; return sum; }
    private static double project(double[] input,double[] residual,double[] basis) {
        double norm=dot(basis,basis);
        if(norm==0) throw new IllegalStateException("Empty QCA ket column");
        double amplitude=dot(input,basis)/norm;
        for(int i=0;i<input.length;i++) residual[i]-=amplitude*basis[i];
        return amplitude;
    }

    private static Map<String,double[]> compile(int n,String source,Set<String> selected) throws CompilationException {
        Map<String,double[]> outputs=new LinkedHashMap<>();
        de.gaalop.tba.Plugin tba=new de.gaalop.tba.Plugin(); tba.setOptGCSE(false);
        de.gaalop.algebra.Plugin algebra=new de.gaalop.algebra.Plugin();
        CompilerFacade compiler=new CompilerFacade(new de.gaalop.clucalc.input.Plugin().createCodeParser(),
                new de.gaalop.globalSettings.Plugin().createGlobalSettingsStrategy(), graph -> {
                    Set<String> assigned = new HashSet<>();
                    Node statementNode = graph.getStartNode().getSuccessor();
                    while (!(statementNode instanceof EndNode)) {
                        if (statementNode instanceof AssignmentNode) assigned.add(((AssignmentNode) statementNode).getVariable().getName());
                        if (!(statementNode instanceof SequentialNode) || statementNode instanceof IfThenElseNode || statementNode instanceof LoopNode)
                            throw new IllegalArgumentException("QCA visualization supports straight-line scripts only.");
                        statementNode = ((SequentialNode) statementNode).getSuccessor();
                    }
                    for(ExpressionStatement statement:graph.visualizerExpressions) {
                        if(!(statement.getExpression() instanceof Variable)) throw new IllegalArgumentException("Assign a visualization expression to a variable before marking it for output.");
                        Variable variable=(Variable)statement.getExpression();
                        if (!assigned.contains(variable.getName())) throw new IllegalArgumentException("Unknown QCA state '" + variable.getName() + "'. Assign it before visualizing.");
                        selected.add(variable.getName());
                        statement.insertAfter(new StoreResultNode(graph,variable));
                    }
                }, algebra.createAlgebraStrategy(),tba.createOptimizationStrategy(),graph -> {
                    Map<String,Double> values=new HashMap<>();
                    Node node=graph.getStartNode().getSuccessor();
                    int count=graph.getAlgebraDefinitionFile().blades.length;
                    while(!(node instanceof EndNode)) {
                        if(node instanceof AssignmentNode) {
                            AssignmentNode assignment=(AssignmentNode)node;
                            double value=value(assignment.getValue(),values);
                            if(!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite QCA value for " + assignment.getVariable().getName());
                            values.put(key(assignment.getVariable()),value);
                        } else if(node instanceof StoreResultNode) {
                            String name=((StoreResultNode)node).getValue().getName();
                            double[] coefficients=new double[count];
                            for(int i=0;i<count;i++) coefficients[i]=values.getOrDefault(name+"#"+i,0.0);
                            outputs.put(name,coefficients);
                        } else if(!(node instanceof ExpressionStatement) && !(node instanceof ColorNode)) {
                            throw new IllegalArgumentException("QCA visualization does not support " + node.getClass().getSimpleName());
                        }
                        node=((SequentialNode)node).getSuccessor();
                    }
                    return Collections.emptySet();
                },"qca",n,true,algebra.getAdditionalBaseDirectory());
        compiler.compile(new InputFile("qca_visualization.clu",source));
        return outputs;
    }

    private static String key(Variable variable) {
        return variable.getName()+"#"+(variable instanceof MultivectorComponent ? ((MultivectorComponent)variable).getBladeIndex() : 0);
    }
    private static double value(Expression expression,Map<String,Double> values) {
        if(expression instanceof FloatConstant) return ((FloatConstant)expression).getValue();
        if(expression instanceof Variable) {
            Double result=values.get(key((Variable)expression));
            if(result==null) throw new IllegalArgumentException("Assign a value to '"+((Variable)expression).getName()+"' in Variable Assignments before visualizing.");
            return result;
        }
        if(expression instanceof Negation) return -value(((Negation)expression).getOperand(),values);
        if(expression instanceof MathFunctionCall) {
            MathFunctionCall call=(MathFunctionCall)expression; double x=value(call.getOperand(),values);
            switch(call.getFunction()) {
                case ABS:return Math.abs(x); case ACOS:return Math.acos(x); case ASIN:return Math.asin(x);
                case ATAN:return Math.atan(x); case CEIL:return Math.ceil(x); case COS:return Math.cos(x);
                case EXP:return Math.exp(x); case FLOOR:return Math.floor(x); case LOG:return Math.log(x);
                case SIN:return Math.sin(x); case SQRT:return Math.sqrt(x); case TAN:return Math.tan(x);
                default:throw new IllegalArgumentException("Unsupported QCA visualization function: "+call.getFunction());
            }
        }
        if(expression instanceof BinaryOperation) {
            BinaryOperation binary=(BinaryOperation)expression;
            double left=value(binary.getLeft(),values),right=value(binary.getRight(),values);
            if(expression instanceof Addition) return left+right;
            if(expression instanceof Subtraction) return left-right;
            if(expression instanceof Multiplication) return left*right;
            if(expression instanceof Division) return left/right;
            if(expression instanceof Exponentiation) return Math.pow(left,right);
        }
        throw new IllegalArgumentException("Unsupported QCA visualization expression: "+expression.getClass().getSimpleName());
    }
}
