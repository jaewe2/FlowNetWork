import java.util.*;
import java.io.*;

// =============================================================================
//  SimulationRunner — visual run + scaling trial loop
//
//  Extracted from SensorStuff.main() so that the entry point contains only
//  I/O and orchestration logic, with no algorithm or graph logic mixed in.
//
//  Usage:
//    SimulationRunner runner = new SimulationRunner(config);
//    runner.runVisual();
//    runner.runScaling();
// =============================================================================

public class SimulationRunner {

    // ── Configuration (set from main() via Scanner) ───────────────────────────

    public int     widthX, lenY, TR, choice;
    public int[]   networkSizes;
    public int     trials;
    public int     minE, maxE;
    public int     minPkt, maxPkt;
    public int     minSz, maxSz;
    public int     minCap, maxCap;
    public int     minPri, maxPri;
    public int     visNodes;
    public Random  rand = new Random();

    // ── Visualisation control ────────────────────────────────────────────────

    public boolean showGraphs = true;

    // ── Algorithm selection ──────────────────────────────────────────────────

    public static final String ALG_GOA     = "GOA";
    public static final String ALG_DENSITY = "DENSITY";
    public static final String ALG_HYBRID  = "HYBRID";
    public static final String ALG_DDR     = "DDR";
    public static final String ALG_DDRPLUS = "DDRPLUS";
    public static final String ALG_PSB     = "PSB";
    public static final String ALG_EXACT   = "EXACT";

    public Set<String> selectedAlgorithms = new LinkedHashSet<>();

    public void setSelectedAlgorithms(String input) {
        selectedAlgorithms.clear();
        if (input == null || input.trim().isEmpty() || input.trim().equalsIgnoreCase("A")) {
            selectAllAlgorithms();
            return;
        }

        for (String token : input.split(",")) {
            switch (token.trim().toUpperCase()) {
                case "1": case ALG_GOA:     selectedAlgorithms.add(ALG_GOA);     break;
                case "2": case ALG_DENSITY: selectedAlgorithms.add(ALG_DENSITY); break;
                case "3": case ALG_HYBRID:  selectedAlgorithms.add(ALG_HYBRID);  break;
                case "4": case ALG_DDR:     selectedAlgorithms.add(ALG_DDR);     break;
                case "5": case ALG_DDRPLUS: selectedAlgorithms.add(ALG_DDRPLUS); break;
                case "6": case ALG_PSB:     selectedAlgorithms.add(ALG_PSB);     break;
                case "7": case ALG_EXACT:   selectedAlgorithms.add(ALG_EXACT);   break;
                case "A": case "ALL":       selectAllAlgorithms();              break;
                default:
                    System.out.println("  Ignoring invalid algorithm choice: " + token.trim());
            }
        }

        if (selectedAlgorithms.isEmpty()) {
            System.out.println("  No valid algorithms selected — defaulting to all algorithms.");
            selectAllAlgorithms();
        }
    }

    private void selectAllAlgorithms() {
        selectedAlgorithms.add(ALG_GOA);
        selectedAlgorithms.add(ALG_DENSITY);
        selectedAlgorithms.add(ALG_HYBRID);
        selectedAlgorithms.add(ALG_DDR);
        selectedAlgorithms.add(ALG_DDRPLUS);
        selectedAlgorithms.add(ALG_PSB);
        selectedAlgorithms.add(ALG_EXACT);
    }

    private boolean use(String algorithm) {
        if (selectedAlgorithms.isEmpty()) selectAllAlgorithms();
        return selectedAlgorithms.contains(algorithm);
    }

    // ── Visual run ────────────────────────────────────────────────────────────

    public void runVisual() {
        System.out.println("\n==== Visual Run (graphs for one trial) ====");

        // retry until we find a trial that actually has a bottleneck
        SensorStuff visNet = null;
        List<Integer> visDG = null, visST = null;
        int[] visCap = null;
        int attempts = 0;

        while (true) {
            attempts++;
            visNet = new SensorStuff(visNodes, choice);
            visNet.buildConnectedGraph(visNodes, widthX, lenY, TR, rand, false);
            visNet.randomNodeEnergies(minE, maxE);
            visNet.randomDataPackets(minPkt, maxPkt);
            visNet.randomPacketSizes(minSz, maxSz);
            visNet.randomPacketPriorities(minPri, maxPri);

            boolean[] visVisited = new boolean[visNodes];
            visNet.components = new ArrayList<>();
            for (int i = 0; i < visNodes; i++)
                if (!visVisited[i])
                    visNet.components.add(FlowNetwork.buildBFS(
                        visNet.adjM != null ? visNet.adjM.getAdjM() : visNet.adjList,
                        i, visVisited));

            visDG = new ArrayList<>();
            visST = new ArrayList<>();
            List<Integer> visAll = new ArrayList<>();
            for (int i = 0; i < visNodes; i++) visAll.add(i);
            Collections.shuffle(visAll, rand);
            visDG.add(visAll.get(0)); visST.add(visAll.get(1));
            double visDgRatio = 0.2 + rand.nextDouble() * 0.5;
            for (int i = 2; i < visNodes; i++) {
                if (rand.nextDouble() < visDgRatio) visDG.add(visAll.get(i));
                else                               visST.add(visAll.get(i));
            }

            visCap = new int[visST.size()];
            for (int j = 0; j < visCap.length; j++)
                visCap[j] = (minCap == maxCap) ? minCap
                          : rand.nextInt(maxCap - minCap + 1) + minCap;

            if (visNet.needsMWF(visDG, visST, visCap)) break;

            if (attempts >= 50) {
                System.out.println(
                    "  Warning: could not find a bottleneck instance after 50 attempts.\n" +
                    "  Try reducing storage capacity or increasing packets per DG.\n" +
                    "  Skipping visual run.");
                return;
            }
            System.out.printf("  (attempt %d: no bottleneck, retrying...)%n", attempts);
        }

        System.out.printf("  Visual run DG ratio: %.0f%% (%d DGs, %d storage) — found in %d attempt(s)%n",
                          100.0 * visDG.size() / visNodes, visDG.size(), visST.size(), attempts);
        System.out.println("\n-- Visual Run DG Setup --");
        for (int dg : visDG)
            System.out.printf("  DG %d: packets=%d, size=%d, priority=%d, energy=%d%n",
                dg, visNet.packetsPerNode[dg], visNet.packetSize[dg],
                visNet.packetPriority[dg], visNet.nodeEnergy[dg]);
        System.out.println("-- Visual Run Storage Setup --");
        for (int j = 0; j < visST.size(); j++)
            System.out.printf("  Storage %d: capacity=%d, energy=%d%n",
                visST.get(j), visCap[j], visNet.nodeEnergy[visST.get(j)]);

        visNet.printAdjacency(visDG, visST);

        Object adjGraph = visNet.adjM != null ? visNet.adjM.getAdjM() : visNet.adjList;
        GraphLauncher launcher = new GraphLauncher(visNet);
        TraceLogger verbose = new TraceLogger(true);

        // each algorithm gets its own FlowNetwork so shared cap/flow state
        // is never corrupted between algorithm calls
        AlgorithmResult resGOA    = null;
        AlgorithmResult resDens   = null;
        AlgorithmResult resHybrid = null;
        AlgorithmResult resDDR    = null;
        AlgorithmResult resDDRP   = null;
        AlgorithmResult resPSB    = null;
        double visExact = -1.0;
        double visExternalILP = -1.0;

        if (use(ALG_GOA)) {
            System.out.println("\n-- Running GOA (visual) --");
            resGOA = new GOA(newFN(visNet), verbose)
                .run(visDG, visST, visCap, adjGraph);
            if (showGraphs) {
                launcher.launchGraph(visDG, visST, resGOA.flowEdges, visCap, "GOA - Sort by Priority (v)");
                launcher.launchBFN(visDG, visST, visCap, resGOA.flowEdges, "GOA - Sort by Priority (v)");
            }
        }

        if (use(ALG_DENSITY)) {
            System.out.println("\n-- Running Density GOA (visual) --");
            resDens = new DensityGOA(newFN(visNet), verbose)
                .run(visDG, visST, visCap, adjGraph);
            if (showGraphs) {
                launcher.launchGraph(visDG, visST, resDens.flowEdges, visCap, "Density GOA");
                launcher.launchBFN(visDG, visST, visCap, resDens.flowEdges, "Density GOA");
            }
        }

        if (use(ALG_HYBRID)) {
            System.out.println("\n-- Running Hybrid GOA (visual) --");
            resHybrid = new HybridGOA(newFN(visNet), verbose)
                .run(visDG, visST, visCap, adjGraph);
            if (showGraphs) {
                launcher.launchGraph(visDG, visST, resHybrid.flowEdges, visCap, "Hybrid GOA");
                launcher.launchBFN(visDG, visST, visCap, resHybrid.flowEdges, "Hybrid GOA");
            }
        }

        if (use(ALG_DDR)) {
            System.out.println("\n-- Running DDR-GOA (visual) --");
            resDDR = new DDRGOA(newFN(visNet), verbose)
                .run(visDG, visST, visCap, adjGraph);
            if (showGraphs) {
                launcher.launchGraph(visDG, visST, resDDR.flowEdges, visCap, "DDR-GOA");
                launcher.launchBFN(visDG, visST, visCap, resDDR.flowEdges, "DDR-GOA");
            }
        }

        if (use(ALG_DDRPLUS)) {
            System.out.println("\n-- Running DDR+-GOA (visual) --");
            resDDRP = new DDRPlusGOA(newFN(visNet), verbose)
                .run(visDG, visST, visCap, adjGraph);
            if (showGraphs) {
                launcher.launchGraph(visDG, visST, resDDRP.flowEdges, visCap, "DDR+-GOA");
                launcher.launchBFN(visDG, visST, visCap, resDDRP.flowEdges, "DDR+-GOA");
            }
        }

        if (use(ALG_PSB)) {
            System.out.println("\n-- Running PSB-GOA (visual) --");
            resPSB = new PSBGOA(newFN(visNet), verbose)
                .run(visDG, visST, visCap, adjGraph);
            if (showGraphs) {
                launcher.launchGraph(visDG, visST, resPSB.flowEdges, visCap, "PSB-GOA");
                launcher.launchBFN(visDG, visST, visCap, resPSB.flowEdges, "PSB-GOA");
            }
        }

        if (use(ALG_EXACT)) {
            System.out.println("\n-- Running Java B&B Baseline (visual) --");
            visExact = new ExactSolverNew(newFN(visNet))
                .solve(visDG, visST, visCap, adjGraph);
        }

        // Generate and solve the external ILP file for this visual run instance.
        // This is independent from option 7: option 7 is only the Java B&B baseline.
        System.out.println("\n-- Generating external ILP file (visual run) --");
        try {
            new ILPSolver(newFN(visNet))
                .generateLP(visDG, visST, visCap, adjGraph, "mwf_s_ilp.lp");
            System.out.println("  LP file generated: mwf_s_ilp.lp");
            System.out.println("  Running GLPK: glpsol --lp mwf_s_ilp.lp -o solution.txt");
            Double obj = runGLPKAndParse("mwf_s_ilp.lp", "solution.txt");
            if (obj != null) {
                visExternalILP = obj;
                System.out.printf("  External ILP objective from solution.txt: %.1f%n", visExternalILP);
            } else {
                System.out.println("  GLPK was not available or did not return an objective.");
                System.out.println("  Manual command: glpsol --lp mwf_s_ilp.lp -o solution.txt");
            }
        } catch (Exception e) {
            System.err.println("  Could not write/solve LP file: " + e.getMessage());
        }

        System.out.printf("%n===== VISUAL RUN SUMMARY =====%n");
        if (visExact >= 0)
            System.out.printf("  Java B&B Baseline:          %.1f%n", visExact);
        else
            System.out.println("  Java B&B Baseline:          N/A (option 7 not selected or skipped)");
        if (visExternalILP >= 0)
            System.out.printf("  External ILP (GLPK):        %.1f  (solution.txt)%n", visExternalILP);
        else
            System.out.println("  External ILP (GLPK):        N/A (see mwf_s_ilp.lp)");
        boolean visFull = visDG.size() <= HybridGOA.MAX_HYBRID_DGS;
        double visualPctBaseline = (visExternalILP >= 0) ? visExternalILP : visExact;
        if (resGOA    != null) pct("GOA",         resGOA.totalPriority,    visualPctBaseline);
        if (resDens   != null) pct("Density GOA", resDens.totalPriority,   visualPctBaseline);
        if (resHybrid != null) pct(visFull ? "Hybrid GOA" : "Hybrid GOA*", resHybrid.totalPriority, visualPctBaseline);
        if (resDDR    != null) pct("DDR-GOA",     resDDR.totalPriority,    visualPctBaseline);
        if (resDDRP   != null) pct("DDR+-GOA",    resDDRP.totalPriority,   visualPctBaseline);
        if (resPSB    != null) pct(visFull ? "PSB-GOA" : "PSB-GOA*",       resPSB.totalPriority,    visualPctBaseline);
        if (!visFull && (resHybrid != null || resPSB != null))
            System.out.printf("  [*] k=%d > MAX_HYBRID_DGS (%d): Hybrid/PSB used DensityGOA%n",
                              visDG.size(), HybridGOA.MAX_HYBRID_DGS);

        // Post-hoc sanity check on visual run. Prefer the external GLPK ILP if available.
        double checkBaseline = (visExternalILP >= 0) ? visExternalILP : visExact;
        if (checkBaseline >= 0) {
            double bestVis = bestOf(resGOA, resDens, resHybrid, resDDR, resDDRP, resPSB);
            if (bestVis > checkBaseline + 0.5)
                System.out.printf("  [WARNING] Heuristic (%.1f) > baseline (%.1f) " +
                                  "— check model consistency.%n", bestVis, checkBaseline);
        }

        if (showGraphs)
            System.out.println("\n-- Graphs launched. Starting scaling runs... --\n");
        else
            System.out.println("\n-- Graph display disabled. Starting scaling runs... --\n");
    }

    // ── Scaling loop ──────────────────────────────────────────────────────────

    public void runScaling() {
        for (int numNodes : networkSizes) {
            System.out.printf("%n==== Network Size: %d nodes, %d trials ====%n",
                              numNodes, trials);

            // Per-trial result lists for CI computation
            List<Double> goaResults     = new ArrayList<>();
            List<Double> densityResults = new ArrayList<>();
            List<Double> hybridResults  = new ArrayList<>();
            List<Double> ddrResults     = new ArrayList<>();
            List<Double> ddrpResults    = new ArrayList<>();
            List<Double> psbResults     = new ArrayList<>();
            List<Double> exactResults   = new ArrayList<>();

            int densityBeatGOA = 0, goaBeatDensity = 0, tied = 0;
            int ddrpBeatDDR = 0, ddrBeatDDRP = 0, ddrpTied = 0;
            int activeTrials = 0, violations = 0;
            boolean anyFallback = false;

            for (int t = 1; t <= trials; t++) {
                // Delay between trials to avoid resource contention
                if (t > 1) {
                    try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                }

                SensorStuff net = new SensorStuff(numNodes, choice);
                int trialTR = net.buildConnectedGraph(
                    numNodes, widthX, lenY, TR, rand, true);

                net.randomNodeEnergies(minE, maxE);
                net.randomDataPackets(minPkt, maxPkt);
                net.randomPacketSizes(minSz, maxSz);
                net.randomPacketPriorities(minPri, maxPri);

                boolean[] visited = new boolean[numNodes];
                net.components = new ArrayList<>();
                for (int i = 0; i < numNodes; i++)
                    if (!visited[i])
                        net.components.add(FlowNetwork.buildBFS(
                            net.adjM != null ? net.adjM.getAdjM() : net.adjList,
                            i, visited));

                List<Integer> dgNodes = new ArrayList<>(), storageNodes = new ArrayList<>();
                List<Integer> allNodes = new ArrayList<>();
                for (int i = 0; i < numNodes; i++) allNodes.add(i);
                Collections.shuffle(allNodes, rand);
                dgNodes.add(allNodes.get(0)); storageNodes.add(allNodes.get(1));
                double dgRatio = 0.2 + rand.nextDouble() * 0.5;
                for (int i = 2; i < numNodes; i++) {
                    if (rand.nextDouble() < dgRatio) dgNodes.add(allNodes.get(i));
                    else                             storageNodes.add(allNodes.get(i));
                }

                int[] storageCap = new int[storageNodes.size()];
                for (int j = 0; j < storageCap.length; j++)
                    storageCap[j] = (minCap == maxCap) ? minCap
                        : rand.nextInt(maxCap - minCap + 1) + minCap;

                if (!net.needsMWF(dgNodes, storageNodes, storageCap)) {
                    System.out.printf("  Trial %2d [TR=%d, DGs=%d, STs=%d]: " +
                                      "No bottleneck — skipped%n",
                                      t, trialTR, dgNodes.size(), storageNodes.size());
                    continue;
                }

                activeTrials++;
                Object adjGraph = net.adjM != null ? net.adjM.getAdjM() : net.adjList;

                Double goaResult = null, densityResult = null, hybridResult = null,
                       ddrResult = null, ddrpResult = null, psbResult = null, exactResult = null;

                if (use(ALG_GOA)) {
                    goaResult = new GOA(newFN(net), TraceLogger.SILENT)
                        .runSilent(dgNodes, storageNodes, storageCap, adjGraph);
                    goaResults.add(goaResult);
                }

                if (use(ALG_DENSITY)) {
                    densityResult = new DensityGOA(newFN(net), TraceLogger.SILENT)
                        .runSilent(dgNodes, storageNodes, storageCap, adjGraph);
                    densityResults.add(densityResult);
                }

                if (use(ALG_HYBRID)) {
                    hybridResult = new HybridGOA(newFN(net), TraceLogger.SILENT)
                        .runSilent(dgNodes, storageNodes, storageCap, adjGraph);
                    hybridResults.add(hybridResult);
                }

                if (use(ALG_DDR)) {
                    ddrResult = new DDRGOA(newFN(net), TraceLogger.SILENT)
                        .runSilent(dgNodes, storageNodes, storageCap, adjGraph);
                    ddrResults.add(ddrResult);
                }

                if (use(ALG_DDRPLUS)) {
                    ddrpResult = new DDRPlusGOA(newFN(net), TraceLogger.SILENT)
                        .runSilent(dgNodes, storageNodes, storageCap, adjGraph);
                    ddrpResults.add(ddrpResult);
                }

                if (use(ALG_PSB)) {
                    psbResult = new PSBGOA(newFN(net), TraceLogger.SILENT)
                        .runSilent(dgNodes, storageNodes, storageCap, adjGraph);
                    psbResults.add(psbResult);
                }

                if (use(ALG_EXACT)) {
                    exactResult = new ExactSolverNew(newFN(net))
                        .runSilent(dgNodes, storageNodes, storageCap, adjGraph);
                    if (exactResult >= 0) { exactResults.add(exactResult); }
                }

                boolean exactRan   = exactResult != null && exactResult >= 0;
                boolean hybridFull = dgNodes.size() <= HybridGOA.MAX_HYBRID_DGS;
                if (!hybridFull && (hybridResult != null || psbResult != null)) anyFallback = true;

                // post-hoc sanity check: no heuristic should exceed exact optimal
                if (exactRan) {
                    double bestHeuristic = bestOf(goaResult, densityResult, hybridResult,
                                                  ddrResult, ddrpResult, psbResult);
                    if (bestHeuristic > exactResult + 0.5) {
                        violations++;
                        System.out.printf(
                            "  [WARNING] Trial %2d: heuristic (%.1f) > exact (%.1f) " +
                            "— constraint violation!%n",
                            t, bestHeuristic, exactResult);
                    }
                }

                System.out.printf(
                    "  Trial %2d [TR=%d, DGs=%d, STs=%d]: %s%n",
                    t, trialTR, dgNodes.size(), storageNodes.size(),
                    trialLine(goaResult, densityResult, hybridResult, ddrResult,
                              ddrpResult, psbResult, exactResult, hybridFull));

                if (goaResult != null && densityResult != null) {
                    if (densityResult > goaResult)      densityBeatGOA++;
                    else if (goaResult > densityResult) goaBeatDensity++;
                    else                                tied++;
                }

                if (ddrpResult != null && ddrResult != null) {
                    if (ddrpResult > ddrResult)      ddrpBeatDDR++;
                    else if (ddrResult > ddrpResult) ddrBeatDDRP++;
                    else                             ddrpTied++;
                }
            }

            // use activeTrials as denominator for averages and win/loss counts
            System.out.printf("%n-- Results over %d active trials (%d skipped, %d total) --%n",
                              activeTrials, trials - activeTrials, trials);
            double avgExact = !exactResults.isEmpty() ? mean(exactResults) : -1;
            if (use(ALG_EXACT))
                System.out.printf("  Java B&B baseline:    %s%n",
                                  !exactResults.isEmpty() ? fmtCI(exactResults) : "N/A");
            else
                System.out.println("  Java B&B baseline:    N/A (option 7 not selected)");

            if (!goaResults.isEmpty())
                System.out.printf("  GOA:                  %s%s%n",
                    fmtCI(goaResults), gap(mean(goaResults), avgExact));
            if (!densityResults.isEmpty())
                System.out.printf("  Density GOA:          %s%s%n",
                    fmtCI(densityResults), gap(mean(densityResults), avgExact));
            if (!hybridResults.isEmpty())
                System.out.printf("  Hybrid GOA:           %s%s%n",
                    fmtCI(hybridResults), gap(mean(hybridResults), avgExact));
            if (!ddrResults.isEmpty())
                System.out.printf("  DDR-GOA:              %s%s%n",
                    fmtCI(ddrResults), gap(mean(ddrResults), avgExact));
            if (!ddrpResults.isEmpty())
                System.out.printf("  DDR+-GOA:             %s%s%n",
                    fmtCI(ddrpResults), gap(mean(ddrpResults), avgExact));
            if (!psbResults.isEmpty())
                System.out.printf("  PSB-GOA:              %s%s%n",
                    fmtCI(psbResults), gap(mean(psbResults), avgExact));

            if (!goaResults.isEmpty() && !densityResults.isEmpty()) {
                System.out.printf("  Density beat GOA:     %d/%d active trials%n", densityBeatGOA, activeTrials);
                System.out.printf("  GOA beat Density:     %d/%d active trials%n", goaBeatDensity, activeTrials);
                System.out.printf("  Tied:                 %d/%d active trials%n", tied, activeTrials);
            }
            if (!ddrResults.isEmpty() && !ddrpResults.isEmpty()) {
                System.out.printf("  DDR+ beat DDR:        %d/%d active trials%n", ddrpBeatDDR, activeTrials);
                System.out.printf("  DDR beat DDR+:        %d/%d active trials%n", ddrBeatDDRP, activeTrials);
                System.out.printf("  DDR+/DDR tied:        %d/%d active trials%n", ddrpTied, activeTrials);
            }
            if (violations > 0)
                System.out.printf("  [!] Constraint violations: %d (heuristic > exact)%n", violations);
            if (anyFallback)
                System.out.printf("  [*] Hybrid/PSB marked with * fell back to DensityGOA " +
                                  "(k > MAX_HYBRID_DGS=%d)%n", HybridGOA.MAX_HYBRID_DGS);

            // ── Write CSV row for this network size (for plotting with error bars) ──
            appendCSV(numNodes, "GOA",         goaResults);
            appendCSV(numNodes, "DensityGOA",  densityResults);
            appendCSV(numNodes, "HybridGOA",   hybridResults);
            appendCSV(numNodes, "DDR-GOA",     ddrResults);
            appendCSV(numNodes, "DDR+-GOA",    ddrpResults);
            appendCSV(numNodes, "PSB-GOA",     psbResults);
            appendCSV(numNodes, "Exact",       exactResults);
        }
        System.out.println("\n  CSV data written to: scaling_results_ci.csv");
        System.out.println("  Use this file to plot error bars in Excel or Python.");
    }

    // ── Confidence Interval Helpers ──────────────────────────────────────────

    /** Compute the mean of a list of values. */
    private static double mean(List<Double> vals) {
        double sum = 0;
        for (double v : vals) sum += v;
        return vals.isEmpty() ? 0 : sum / vals.size();
    }

    /** Compute the sample standard deviation of a list of values. */
    private static double stdev(List<Double> vals) {
        if (vals.size() < 2) return 0;
        double avg = mean(vals);
        double sumSq = 0;
        for (double v : vals) sumSq += (v - avg) * (v - avg);
        return Math.sqrt(sumSq / (vals.size() - 1));
    }

    /**
     * Compute the 95% confidence interval half-width.
     * This mirrors Excel's CONFIDENCE(alpha, stdev, n) function:
     *   CI = z_{alpha/2} * stdev / sqrt(n)
     * For alpha=0.05 (95% CI), z = 1.96.
     */
    private static double confidenceInterval95(List<Double> vals) {
        if (vals.size() < 2) return 0;
        double sd = stdev(vals);
        return 1.96 * sd / Math.sqrt(vals.size());
    }

    /** Format mean ± CI for console output. */
    private static String fmtCI(List<Double> vals) {
        if (vals.isEmpty()) return "N/A";
        double avg = mean(vals);
        double ci  = confidenceInterval95(vals);
        return String.format("%.2f ± %.2f  (95%% CI: [%.2f, %.2f], n=%d)",
                             avg, ci, avg - ci, avg + ci, vals.size());
    }

    /** CSV file for plotting: written once with header, then appended per network size. */
    private static boolean csvHeaderWritten = false;

    private static void appendCSV(int numNodes, String algorithm, List<Double> vals) {
        if (vals.isEmpty()) return;
        try (PrintWriter pw = new PrintWriter(new FileWriter("scaling_results_ci.csv", true))) {
            if (!csvHeaderWritten) {
                pw.println("Nodes,Algorithm,Mean,StdDev,CI95,CI_Low,CI_High,N");
                csvHeaderWritten = true;
            }
            double avg = mean(vals);
            double sd  = stdev(vals);
            double ci  = confidenceInterval95(vals);
            pw.printf("%d,%s,%.4f,%.4f,%.4f,%.4f,%.4f,%d%n",
                      numNodes, algorithm, avg, sd, ci, avg - ci, avg + ci, vals.size());
        } catch (IOException e) {
            System.err.println("  Could not write CSV: " + e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    // Creates a fresh FlowNetwork from a SensorStuff instance.
    // Called once per algorithm per trial so no algorithm can corrupt
    // another's cap/flow state through the shared reference.
    static FlowNetwork newFN(SensorStuff net) {
        return new FlowNetwork(net.packetSize, net.packetPriority,
                               net.nodeEnergy, net.packetsPerNode,
                               net.nodeLoc);
    }

    private static void pct(String name, double val, double opt) {
        if (opt > 0) {
            System.out.printf("  %-20s %.1f  (%.1f%% of selected baseline)%n",
                              name + " priority:", val, 100.0 * val / opt);
        } else {
            System.out.printf("  %-20s %.1f%n", name + " priority:", val);
        }
    }

    private static double bestOf(AlgorithmResult... results) {
        double best = 0.0;
        for (AlgorithmResult r : results)
            if (r != null) best = Math.max(best, r.totalPriority);
        return best;
    }

    private static double bestOf(Double... values) {
        double best = 0.0;
        for (Double v : values)
            if (v != null) best = Math.max(best, v);
        return best;
    }

    private static String trialLine(Double goaResult, Double densityResult,
                                    Double hybridResult, Double ddrResult,
                                    Double ddrpResult, Double psbResult,
                                    Double exactResult, boolean hybridFull) {
        List<String> parts = new ArrayList<>();
        if (goaResult     != null) parts.add("GOA="     + fmt(goaResult, false));
        if (densityResult != null) parts.add("Density=" + fmt(densityResult, false));
        if (hybridResult  != null) parts.add("Hybrid="  + fmt(hybridResult, !hybridFull));
        if (ddrResult     != null) parts.add("DDR="     + fmt(ddrResult, false));
        if (ddrpResult    != null) parts.add("DDR+="    + fmt(ddrpResult, false));
        if (psbResult     != null) parts.add("PSB="     + fmt(psbResult, !hybridFull));
        if (exactResult   != null) parts.add("B&B="     + (exactResult >= 0 ? fmt(exactResult, false) : "N/A"));
        return String.join("  ", parts);
    }

    private static String fmt(double value, boolean fallback) {
        return fallback ? String.format("%.1f*", value) : String.format("%.1f", value);
    }

    private static String gap(double algoAvg, double exactAvg) {
        if (exactAvg <= 0) return "";
        return String.format("  (%.1f%% of Java B&B)", 100.0 * algoAvg / exactAvg);
    }

    private static Double runGLPKAndParse(String lpFile, String solFile) {
        try {
            ProcessBuilder pb = new ProcessBuilder("glpsol", "--lp", lpFile, "-o", solFile);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                while (br.readLine() != null) {
                    // Consume output so GLPK cannot block on a full buffer.
                }
            }
            int code = proc.waitFor();
            if (code != 0) return null;
            return parseGLPKObjective(solFile);
        } catch (Exception e) {
            return null;
        }
    }

    private static Double parseGLPKObjective(String solFile) {
        try (BufferedReader br = new BufferedReader(new FileReader(solFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("Objective:")) {
                    String[] parts = line.split("=");
                    if (parts.length >= 2) {
                        String num = parts[1].trim().split("\\s+")[0];
                        return Double.parseDouble(num);
                    }
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

}
