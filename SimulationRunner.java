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

            double sumGOA = 0, sumDensity = 0,
                   sumHybrid = 0, sumDDR = 0, sumDDRP = 0, sumPSB = 0, sumExact = 0;
            int goaTrials = 0, densityTrials = 0, hybridTrials = 0,
                ddrTrials = 0, ddrpTrials = 0, psbTrials = 0;
            int densityBeatGOA = 0, goaBeatDensity = 0, tied = 0;
            int ddrpBeatDDR = 0, ddrBeatDDRP = 0, ddrpTied = 0;
            int exactTrials = 0, activeTrials = 0, violations = 0;
            boolean anyFallback = false;

            for (int t = 1; t <= trials; t++) {
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
                    sumGOA += goaResult; goaTrials++;
                }

                if (use(ALG_DENSITY)) {
                    densityResult = new DensityGOA(newFN(net), TraceLogger.SILENT)
                        .runSilent(dgNodes, storageNodes, storageCap, adjGraph);
                    sumDensity += densityResult; densityTrials++;
                }

                if (use(ALG_HYBRID)) {
                    hybridResult = new HybridGOA(newFN(net), TraceLogger.SILENT)
                        .runSilent(dgNodes, storageNodes, storageCap, adjGraph);
                    sumHybrid += hybridResult; hybridTrials++;
                }

                if (use(ALG_DDR)) {
                    ddrResult = new DDRGOA(newFN(net), TraceLogger.SILENT)
                        .runSilent(dgNodes, storageNodes, storageCap, adjGraph);
                    sumDDR += ddrResult; ddrTrials++;
                }

                if (use(ALG_DDRPLUS)) {
                    ddrpResult = new DDRPlusGOA(newFN(net), TraceLogger.SILENT)
                        .runSilent(dgNodes, storageNodes, storageCap, adjGraph);
                    sumDDRP += ddrpResult; ddrpTrials++;
                }

                if (use(ALG_PSB)) {
                    psbResult = new PSBGOA(newFN(net), TraceLogger.SILENT)
                        .runSilent(dgNodes, storageNodes, storageCap, adjGraph);
                    sumPSB += psbResult; psbTrials++;
                }

                if (use(ALG_EXACT)) {
                    exactResult = new ExactSolverNew(newFN(net))
                        .runSilent(dgNodes, storageNodes, storageCap, adjGraph);
                    if (exactResult >= 0) { sumExact += exactResult; exactTrials++; }
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
            double avgExact = exactTrials > 0 ? sumExact / exactTrials : -1;
            if (use(ALG_EXACT))
                System.out.printf("  Java B&B baseline avg:       %.2f  (%d/%d active trials)%n",
                                  avgExact >= 0 ? avgExact : 0, exactTrials, activeTrials);
            else
                System.out.println("  Java B&B baseline avg:       N/A (option 7 not selected)");

            if (goaTrials > 0)
                System.out.printf("  GOA avg:              %.2f%s%n",
                    sumGOA/goaTrials, gap(sumGOA/goaTrials, avgExact));
            if (densityTrials > 0)
                System.out.printf("  Density GOA avg:      %.2f%s%n",
                    sumDensity/densityTrials, gap(sumDensity/densityTrials, avgExact));
            if (hybridTrials > 0)
                System.out.printf("  Hybrid GOA avg:       %.2f%s%n",
                    sumHybrid/hybridTrials, gap(sumHybrid/hybridTrials, avgExact));
            if (ddrTrials > 0)
                System.out.printf("  DDR-GOA avg:          %.2f%s%n",
                    sumDDR/ddrTrials, gap(sumDDR/ddrTrials, avgExact));
            if (ddrpTrials > 0)
                System.out.printf("  DDR+-GOA avg:         %.2f%s%n",
                    sumDDRP/ddrpTrials, gap(sumDDRP/ddrpTrials, avgExact));
            if (psbTrials > 0)
                System.out.printf("  PSB-GOA avg:          %.2f%s%n",
                    sumPSB/psbTrials, gap(sumPSB/psbTrials, avgExact));

            if (goaTrials > 0 && densityTrials > 0) {
                System.out.printf("  Density beat GOA:     %d/%d active trials%n", densityBeatGOA, activeTrials);
                System.out.printf("  GOA beat Density:     %d/%d active trials%n", goaBeatDensity, activeTrials);
                System.out.printf("  Tied:                 %d/%d active trials%n", tied, activeTrials);
            }
            if (ddrTrials > 0 && ddrpTrials > 0) {
                System.out.printf("  DDR+ beat DDR:        %d/%d active trials%n", ddrpBeatDDR, activeTrials);
                System.out.printf("  DDR beat DDR+:        %d/%d active trials%n", ddrBeatDDRP, activeTrials);
                System.out.printf("  DDR+/DDR tied:        %d/%d active trials%n", ddrpTied, activeTrials);
            }
            if (violations > 0)
                System.out.printf("  [!] Constraint violations: %d (heuristic > exact)%n", violations);
            if (anyFallback)
                System.out.printf("  [*] Hybrid/PSB marked with * fell back to DensityGOA " +
                                  "(k > MAX_HYBRID_DGS=%d)%n", HybridGOA.MAX_HYBRID_DGS);
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
