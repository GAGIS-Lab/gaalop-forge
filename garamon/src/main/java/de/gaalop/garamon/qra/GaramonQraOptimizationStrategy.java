package de.gaalop.garamon.qra;

import de.gaalop.LoggingListener;
import de.gaalop.LoggingListenerGroup;
import de.gaalop.OptimizationException;
import de.gaalop.OptimizationStrategy;
import de.gaalop.cfg.AssignmentNode;
import de.gaalop.cfg.ControlFlowGraph;
import de.gaalop.cfg.SequentialNode;
import de.gaalop.cfg.StoreResultNode;
import de.gaalop.dfg.FloatConstant;
import de.gaalop.dfg.MultivectorComponent;
import de.gaalop.dfg.Variable;
import de.gaalop.garamon.Plugin;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GaramonQraOptimizationStrategy implements OptimizationStrategy {
    private final Plugin plugin;
    private final LoggingListenerGroup listeners = new LoggingListenerGroup();
    private final Map<String, QraStateProjection.Result> quantumResults = new LinkedHashMap<String, QraStateProjection.Result>();
    private boolean projectStates;
    private java.util.Set<String> projectedVariables;

    public void setProjectStates(boolean enabled) { projectStates = enabled; }
    public void setProjectedVariables(java.util.Set<String> names) {
        projectedVariables = new java.util.HashSet<String>(names);
    }
    public Map<String, QraStateProjection.Result> getQuantumResults() {
        return java.util.Collections.unmodifiableMap(quantumResults);
    }

    public GaramonQraOptimizationStrategy(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void transform(ControlFlowGraph graph) throws OptimizationException {
        quantumResults.clear();
        QraAlgebraId qra = QraAlgebraId.detect(graph.algebraName);
        if (qra == null) {
            runTbaFallback(graph);
            return;
        }

        try {
            listeners.logNote("Loading Garamon native backend for " + qra.id, 0.05d);

            QraNativeLibrary nativeLibrary = QraNativeLoader.load(qra);
            QraBladeOrderMapper bladeOrderMapper =
                    new QraBladeOrderMapper(nativeLibrary.dimension());

            listeners.logNote("Evaluating QRA graph with Garamon", 0.25d);

            GaramonQraExpressionEvaluator evaluator =
                    new GaramonQraExpressionEvaluator(graph, nativeLibrary, bladeOrderMapper);

            Map<String, double[]> garamonOutputs = evaluator.evaluateOutputs();
            if (projectStates) {
                for (Map.Entry<String, double[]> output : garamonOutputs.entrySet()) {
                    if (projectedVariables != null && !projectedVariables.contains(output.getKey())) continue;
                    quantumResults.put(output.getKey(), QraStateProjection.solve(qra.nqubits, output.getValue()));
                }
            }

            listeners.logNote("Rewriting graph with Garamon constants", 0.75d);

            Map<String, double[]> gaalopOutputs = new LinkedHashMap<String, double[]>();
            for (Map.Entry<String, double[]> entry : garamonOutputs.entrySet()) {
                gaalopOutputs.put(entry.getKey(), bladeOrderMapper.garamonToGaalop(entry.getValue()));
            }

            rewriteGraphAsConstantOutputs(graph, gaalopOutputs, nativeLibrary.coefficientCount());

            graph.tbaOptimized = true;
            listeners.logNote("Garamon QRA optimization finished", 1.0d);
        } catch (OptimizationException ex) {
            throw ex;
        } catch (Throwable ex) {
            throw new OptimizationException("Garamon QRA backend failed: " + ex.getMessage(), ex, graph);
        }
    }

    private void runTbaFallback(ControlFlowGraph graph) throws OptimizationException {
        de.gaalop.tba.Plugin tbaPlugin = new de.gaalop.tba.Plugin();
        OptimizationStrategy tba = tbaPlugin.createOptimizationStrategy();

        for (LoggingListener listener : listeners) {
            tba.addProgressListener(listener);
        }

        tba.transform(graph);
    }

    private void rewriteGraphAsConstantOutputs(
            ControlFlowGraph graph,
            Map<String, double[]> outputs,
            int coeffCount) throws OptimizationException {

        if (outputs.isEmpty()) {
            throw new OptimizationException("Garamon QRA produced no output variables.", graph);
        }

        clearVariableSets(graph);
        clearSequentialBody(graph);

        graph.getPragmaOutputVariables().clear();

        SequentialNode cursor = graph.getStartNode();

        for (Map.Entry<String, double[]> entry : outputs.entrySet()) {
            String outputName = entry.getKey();
            double[] dense = entry.getValue();

            if (dense.length != coeffCount) {
                throw new OptimizationException(
                        "Output " + outputName + " has " + dense.length
                                + " coefficients, expected " + coeffCount + ".",
                        graph);
            }

            graph.addPragmaOutputVariable(outputName);

            boolean wroteAnyComponent = false;
            for (int blade = 0; blade < coeffCount; blade++) {
                double value = clean(dense[blade]);
                if (!plugin.emitZeroComponents && value == 0.0d) {
                    continue;
                }

                AssignmentNode assignment = new AssignmentNode(
                        graph,
                        new MultivectorComponent(outputName, blade),
                        new FloatConstant(value));

                cursor.insertAfter(assignment);
                cursor = assignment;
                wroteAnyComponent = true;
            }

            if (!wroteAnyComponent) {
                AssignmentNode zeroScalar = new AssignmentNode(
                        graph,
                        new MultivectorComponent(outputName, 0),
                        new FloatConstant(0.0d));

                cursor.insertAfter(zeroScalar);
                cursor = zeroScalar;
            }

            StoreResultNode store = new StoreResultNode(graph, new Variable(outputName));
            cursor.insertAfter(store);
            cursor = store;
        }
    }

    private void clearSequentialBody(ControlFlowGraph graph) {
        while (graph.getStartNode().getSuccessor() instanceof SequentialNode) {
            SequentialNode next = (SequentialNode) graph.getStartNode().getSuccessor();
            graph.removeNode(next);
        }
    }

    private void clearVariableSets(ControlFlowGraph graph) {
        for (Variable input : new ArrayList<Variable>(graph.getInputVariables())) {
            graph.removeInputVariable(input);
        }

        for (Variable local : new ArrayList<Variable>(graph.getLocalVariables())) {
            graph.removeLocalVariable(local);
        }

        graph.getScalarVariables().clear();
        graph.getPragmaOnlyEvaluateVariables().clear();
        graph.getOnlyEvaluateNodes().clear();
    }

    private double clean(double value) {
        return Math.abs(value) < plugin.zeroEpsilon ? 0.0d : value;
    }

    @Override
    public void addProgressListener(LoggingListener progressListener) {
        listeners.add(progressListener);
    }
}
