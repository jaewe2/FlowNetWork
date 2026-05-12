import java.util.*;
import java.io.*;
import java.util.regex.*;

// =============================================================================
//  ILPSolver — True ILP Formulation for MWF-S
//
//  Generates two ILP formulations:
//
//  ILP_max_weight (objective 7): max Σ v_i × f_i(s, i')
//    Maximizes total preserved priority.
//
//  ILP_max_flow (objective 1): max Σ f_i(s, i')
//    Maximizes total preserved packets.
//    Algorithm 1 should match or be close to this result.
//
//  Both use the same constraints (2)-(6) under the uniform energy model.
//
//    Constraints:
//      (C1) Source capacity:  f_i(s, i') ≤ dᵢ              ∀i ∈ Vd
//      (C2) Sink capacity:   Σᵢ f_i(j'', t) × szᵢ ≤ mⱼ   ∀j ∈ Vs
//           (MWF-S: each packet from DG i consumes szᵢ storage units)
//      (C3) Flow conservation at DG nodes:
//           f_i(s, i') + Σ_{j''} f_i(j'', i') = f_i(i', i'') = Σ_{j'} f_i(i'', j')
//      (C4) Flow conservation at storage/transshipment nodes:
//           Σ_{j''} f_i(j'', i') = f_i(i', i'') = f_i(i'', t) + Σ_{j'} f_i(i'', j')
//      (C5) Edge capacity (energy):
//           Σᵢ f_i(u, v) ≤ ca(u, v)   ∀(u,v) ∈ E'
//           Under uniform energy model, all DGs cost 1 unit per hop.
//      (C6) Non-negativity and integrality:
//           f_i(u, v) ≥ 0, integer
//
//  APPROACH:
//    1. Generates a .lp file in CPLEX format that any standard ILP solver
//       (GLPK, CPLEX, Gurobi, SCIP, OR-Tools) can read and solve.
//    2. Also provides a self-contained Java solver that enumerates feasible
//       flows via constraint propagation for small instances (≤ 5 DGs).
//
//  USAGE:
//    ILPSolver solver = new ILPSolver(flowNetwork);
//    solver.solve(dgNodes, storageNodes, storageCapacity, adjGraph);
// =============================================================================

public class ILPSolver {

    private final FlowNetwork fn;

    public ILPSolver(FlowNetwork fn) {
        this.fn = fn;
    }

    // ── Generate CPLEX-format .lp file ────────────────────────────────────────
    // This can be fed to glpsol, CPLEX, Gurobi, SCIP, or OR-Tools.

    public void generateLP(List<Integer> dgNodes,
                           List<Integer> storageNodes,
                           int[]         storageCapacity,
                           Object        adjGraph,
                           String        outputPath) throws IOException {
        generateLP(dgNodes, storageNodes, storageCapacity, adjGraph, outputPath, true);
    }

    public void generateLP(List<Integer> dgNodes,
                           List<Integer> storageNodes,
                           int[]         storageCapacity,
                           Object        adjGraph,
                           String        outputPath,
                           boolean       maxWeight) throws IOException {

        fn.buildCFN(dgNodes, storageNodes, storageCapacity, adjGraph);

        int n     = fn.nodeLoc.length;
        int S     = 0;
        int T     = fn.superSink();
        int nCFN  = fn.cfnNodes;
        int nDG   = dgNodes.size();

        // Collect all edges with positive capacity
        List<int[]> edges = new ArrayList<>();
        for (int u = 0; u < nCFN; u++)
            for (int v = 0; v < nCFN; v++)
                if (fn.cap[u][v] > 0) edges.add(new int[]{u, v});

        PrintWriter pw = new PrintWriter(new FileWriter(outputPath));

        // ── Objective ─────────────────────────────────────────────────────
        if (maxWeight) {
            pw.println("\\  MWF-S ILP_max_weight — objective (7): maximize total preserved priority");
        } else {
            pw.println("\\  MWF-S ILP_max_flow — objective (1): maximize total preserved packets");
        }
        pw.printf("\\  %d DGs, %d storage nodes, %d transshipment nodes%n",
                  nDG, storageNodes.size(), n - nDG - storageNodes.size());
        pw.println();
        pw.println("Maximize");
        pw.print("  obj: ");
        boolean first = true;
        for (int idx = 0; idx < nDG; idx++) {
            int dg = dgNodes.get(idx);
            int vi = fn.packetPriority[dg];
            int sEdge_u = S;
            int sEdge_v = fn.inNode(dg);
            String var = varName(idx, sEdge_u, sEdge_v);
            if (!first) pw.print(" + ");
            if (maxWeight) {
                pw.printf("%d %s", vi, var);   // objective (7): v_i * f_i
            } else {
                pw.print(var);                  // objective (1): f_i (coefficient = 1)
            }
            first = false;
        }
        pw.println();
        pw.println();

        // ── Constraints ───────────────────────────────────────────────────
        pw.println("Subject To");
        int cNum = 0;

        // (C1) Source capacity: f_i(s, i') ≤ dᵢ  for each DG
        for (int idx = 0; idx < nDG; idx++) {
            int dg = dgNodes.get(idx);
            int di = fn.packetsPerNode[dg];
            String var = varName(idx, S, fn.inNode(dg));
            pw.printf("  c%d: %s <= %d%n", ++cNum, var, di);
        }

        // (C2) Sink capacity with sizes: Σᵢ f_i(j'', t) × szᵢ ≤ mⱼ
        for (int jIdx = 0; jIdx < storageNodes.size(); jIdx++) {
            int st = storageNodes.get(jIdx);
            int mj = storageCapacity[jIdx];
            pw.printf("  c%d: ", ++cNum);
            first = true;
            for (int idx = 0; idx < nDG; idx++) {
                int dg  = dgNodes.get(idx);
                int szI = fn.packetSize[dg];
                String var = varName(idx, fn.outNode(st), T);
                if (fn.cap[fn.outNode(st)][T] > 0) {
                    if (!first) pw.print(" + ");
                    pw.printf("%d %s", szI, var);
                    first = false;
                }
            }
            if (first) pw.print("0");  // no DG can reach this storage
            pw.printf(" <= %d%n", mj);
        }

        // (C3/C4) Flow conservation at every internal node for each DG
        //   For each DG i and each physical node v:
        //     inflow to v' = flow through v'→v'' = outflow from v''
        for (int idx = 0; idx < nDG; idx++) {
            for (int v = 0; v < n; v++) {
                int vIn  = fn.inNode(v);
                int vOut = fn.outNode(v);

                // Conservation at in-node v':
                //   Σ_u f_i(u, v') = f_i(v', v'')
                // i.e.,  Σ_u f_i(u, v') - f_i(v', v'') = 0
                StringBuilder sb = new StringBuilder();
                boolean hasTerms = false;
                for (int u = 0; u < nCFN; u++) {
                    if (fn.cap[u][vIn] > 0 && u != vIn) {
                        String var = varName(idx, u, vIn);
                        if (hasTerms) sb.append(" + ");
                        sb.append(var);
                        hasTerms = true;
                    }
                }
                if (hasTerms) {
                    String throughVar = varName(idx, vIn, vOut);
                    sb.append(" - ").append(throughVar);
                    pw.printf("  c%d: %s = 0%n", ++cNum, sb.toString());
                }

                // Conservation at out-node v'':
                //   f_i(v', v'') = Σ_w f_i(v'', w)
                // i.e.,  f_i(v', v'') - Σ_w f_i(v'', w) = 0
                StringBuilder sb2 = new StringBuilder();
                boolean hasOut = false;
                String throughVar2 = varName(idx, vIn, vOut);
                sb2.append(throughVar2);
                for (int w = 0; w < nCFN; w++) {
                    if (fn.cap[vOut][w] > 0 && w != vOut) {
                        sb2.append(" - ").append(varName(idx, vOut, w));
                        hasOut = true;
                    }
                }
                if (hasOut) {
                    pw.printf("  c%d: %s = 0%n", ++cNum, sb2.toString());
                }
            }
        }

        // (C5) Uniform edge/energy capacity for the professor's model:
        //   Σᵢ f_i(u,v) ≤ ca(u,v) for finite non-source, non-sink edges.
        // Packet size cᵢ does NOT multiply energy use here. In this test,
        // sending, relaying, and receiving one packet each costs 1 energy unit
        // regardless of packet size. Packet size is enforced only by C2.
        int INF_THRESH = Integer.MAX_VALUE / 4;
        for (int[] e : edges) {
            int u = e[0], v = e[1];
            if (fn.cap[u][v] >= INF_THRESH) continue;  // skip ∞ routing edges
            if (u == S) continue;  // source edges handled by C1/bounds
            if (v == T) continue;  // sink storage handled by C2

            pw.printf("  c%d: ", ++cNum);
            first = true;
            for (int idx = 0; idx < nDG; idx++) {
                String var = varName(idx, u, v);
                if (!first) pw.print(" + ");
                pw.print(var);
                first = false;
            }
            pw.printf(" <= %d%n", fn.cap[u][v]);
        }

        // ── Bounds & Integrality ──────────────────────────────────────────
        // Source edges are commodity-specific: DG i can only originate from
        // its own s→i' edge. Energy/internal edges use raw packet capacity.
        // Sink edges use floor(storage / packet size) as an individual bound,
        // while C2 enforces the shared storage capacity across all DGs.
        pw.println();
        pw.println("Bounds");
        int totalPacketsAllDGs = 0;
        for (int dg0 : dgNodes) totalPacketsAllDGs += fn.packetsPerNode[dg0];
        for (int idx = 0; idx < nDG; idx++) {
            int dg  = dgNodes.get(idx);
            int szI = fn.packetSize[dg];
            for (int[] e : edges) {
                int u = e[0], v = e[1];
                String var = varName(idx, u, v);
                int rawCap = fn.cap[u][v];
                int ub;

                if (u == S) {
                    // A commodity may only start at its own DG source edge.
                    ub = (v == fn.inNode(dg)) ? fn.packetsPerNode[dg] : 0;
                } else if (v == T) {
                    // Storage units converted into packets for this DG.
                    ub = rawCap / szI;
                } else if (rawCap >= INF_THRESH) {
                    ub = totalPacketsAllDGs;
                } else {
                    // Uniform energy model: 1 packet consumes 1 energy unit.
                    ub = rawCap;
                }
                pw.printf("  0 <= %s <= %d%n", var, ub);
            }
        }

        pw.println();
        pw.println("General");
        pw.print(" ");
        for (int idx = 0; idx < nDG; idx++) {
            for (int[] e : edges) {
                pw.print(" " + varName(idx, e[0], e[1]));
            }
        }
        pw.println();

        pw.println();
        pw.println("End");
        pw.close();

        System.out.printf("  ILP written to %s (%d variables, %d constraints)%n",
                          outputPath, nDG * edges.size(), cNum);
    }

    // ── Variable naming ───────────────────────────────────────────────────────
    // f_<dgIndex>_<fromNode>_<toNode>

    private String varName(int dgIdx, int from, int to) {
        return String.format("f_%d_%d_%d", dgIdx, from, to);
    }

    // ── Java fallback / LP export helper ─────────────────────────────────────
    // Exports the LP file and then runs the Java B&B-style baseline.
    // Important: this Java baseline is useful for quick checks, but the
    // external GLPK/ILP solution should be treated as the true optimum.

    public double solve(List<Integer> dgNodes,
                        List<Integer> storageNodes,
                        int[]         storageCapacity,
                        Object        adjGraph) {

        int nDG = dgNodes.size();

        // Step 1: Generate the LP file for external verification
        String lpFile = "mwf_s_ilp.lp";
        try {
            generateLP(dgNodes, storageNodes, storageCapacity, adjGraph, lpFile);
        } catch (IOException e) {
            System.err.println("  Warning: could not write LP file: " + e.getMessage());
        }

        // Step 2: Run the Java B&B-style baseline as an internal fallback.
        // The external LP/GLPK result is the official ILP optimum.
        System.out.println("\n-- ILP Solver: Running Java B&B fallback baseline --");
        ExactSolverNew bb = new ExactSolverNew(
            new FlowNetwork(fn.packetSize, fn.packetPriority,
                            fn.nodeEnergy, fn.packetsPerNode, fn.nodeLoc));
        double bbResult = bb.solve(dgNodes, storageNodes, storageCapacity, adjGraph);

        System.out.printf("  Java B&B fallback result: %.0f%n", bbResult);
        System.out.printf("  LP file written: %s (feed to glpsol/CPLEX/Gurobi for verification)%n", lpFile);
        System.out.println("  To verify: glpsol --lp mwf_s_ilp.lp -o solution.txt");

        return bbResult;
    }


    // ── Professor comparison helpers ─────────────────────────────────────────

    private static class ProfessorResult {
        String name;
        double priority;
        int packets;
        int storageUsed;
        long runtimeMs;
        String note;

        ProfessorResult(String name, double priority, int packets,
                        int storageUsed, long runtimeMs, String note) {
            this.name = name;
            this.priority = priority;
            this.packets = packets;
            this.storageUsed = storageUsed;
            this.runtimeMs = runtimeMs;
            this.note = note;
        }
    }

    private static class GLPKSolutionStats {
        double objective;
        int packets;
        int storageUsed;

        GLPKSolutionStats(double objective, int packets, int storageUsed) {
            this.objective = objective;
            this.packets = packets;
            this.storageUsed = storageUsed;
        }
    }

    private static ProfessorResult runOrderedGreedy(String name,
                                                    FlowNetwork fn,
                                                    List<Integer> dgNodes,
                                                    List<Integer> storageNodes,
                                                    int[] storageCapacity,
                                                    Object adjGraph,
                                                    Comparator<Integer> orderComparator,
                                                    String note) {
        long start = System.nanoTime();
        fn.buildCFN(dgNodes, storageNodes, storageCapacity, adjGraph);

        int nDG = dgNodes.size();
        int[] remaining = new int[nDG];
        for (int i = 0; i < nDG; i++) {
            remaining[i] = fn.packetsPerNode[dgNodes.get(i)];
        }
        int[] sinkCap = fn.makeSinkCap(storageNodes, storageCapacity);

        Integer[] order = new Integer[nDG];
        for (int i = 0; i < nDG; i++) order[i] = i;
        Arrays.sort(order, orderComparator);

        double totalPriority = 0.0;
        int totalPackets = 0;
        int maxIter = fn.cfnNodes * fn.cfnNodes;

        for (int idx : order) {
            int dg = dgNodes.get(idx);
            int cfnSrc = fn.inNode(dg);
            int szI = fn.packetSize[dg];
            int vi = fn.packetPriority[dg];
            int iters = 0;

            while (remaining[idx] > 0 && iters++ < maxIter) {
                int[] parent = fn.bfsFAP(cfnSrc, szI, sinkCap);
                if (parent == null) break;

                int before = remaining[idx];
                int gained = fn.augmentPath(parent, idx, remaining, sinkCap,
                                            szI, vi, null);
                if (gained <= 0) break;

                int delta = before - remaining[idx];
                totalPackets += delta;
                totalPriority += gained;
            }
        }

        int storageLeft = 0;
        for (int st : storageNodes) storageLeft += sinkCap[fn.outNode(st)];
        int storageTotal = 0;
        for (int cap : storageCapacity) storageTotal += cap;
        int storageUsed = storageTotal - storageLeft;

        long runtimeMs = (System.nanoTime() - start) / 1_000_000L;
        return new ProfessorResult(name, totalPriority, totalPackets,
                                   storageUsed, runtimeMs, note);
    }


    private static ProfessorResult runOrderedAllOrNothing(String name,
                                                          FlowNetwork fn,
                                                          List<Integer> dgNodes,
                                                          List<Integer> storageNodes,
                                                          int[] storageCapacity,
                                                          Object adjGraph,
                                                          Comparator<Integer> orderComparator,
                                                          String note) {
        long start = System.nanoTime();
        fn.buildCFN(dgNodes, storageNodes, storageCapacity, adjGraph);

        int nDG = dgNodes.size();
        int[] remaining = new int[nDG];
        for (int i = 0; i < nDG; i++) {
            remaining[i] = fn.packetsPerNode[dgNodes.get(i)];
        }
        int[] sinkCap = fn.makeSinkCap(storageNodes, storageCapacity);

        Integer[] order = new Integer[nDG];
        for (int i = 0; i < nDG; i++) order[i] = i;
        Arrays.sort(order, orderComparator);

        double totalPriority = 0.0;
        int totalPackets = 0;
        int maxIter = fn.cfnNodes * fn.cfnNodes;

        for (int idx : order) {
            int originalRemaining = remaining[idx];
            if (originalRemaining <= 0) continue;

            // Algorithm 2 is all-or-nothing: if this DG cannot send all d_i
            // packets in the current residual graph, discard its partial trial
            // and stop this ordering.
            int[][] capSnap  = fn.snapCap();
            int[][] flowSnap = fn.snapFlow();
            int[] remSnap    = remaining.clone();
            int[] sinkSnap   = sinkCap.clone();

            int dg = dgNodes.get(idx);
            int cfnSrc = fn.inNode(dg);
            int szI = fn.packetSize[dg];
            int vi = fn.packetPriority[dg];
            int sentForThisDG = 0;
            int gainedForThisDG = 0;
            int iters = 0;

            while (remaining[idx] > 0 && iters++ < maxIter) {
                int[] parent = fn.bfsFAP(cfnSrc, szI, sinkCap);
                if (parent == null) break;

                int before = remaining[idx];
                int gained = fn.augmentPath(parent, idx, remaining, sinkCap,
                                            szI, vi, null);
                if (gained <= 0) break;

                int delta = before - remaining[idx];
                sentForThisDG += delta;
                gainedForThisDG += gained;
            }

            if (sentForThisDG < originalRemaining) {
                fn.restoreCap(capSnap);
                fn.restoreFlow(flowSnap);
                remaining = remSnap;
                sinkCap = sinkSnap;
                note = note + " | stopped at DG " + dg
                     + " (sent " + sentForThisDG + "/" + originalRemaining + ")";
                break;
            }

            totalPackets += sentForThisDG;
            totalPriority += gainedForThisDG;
        }

        int storageLeft = 0;
        for (int st : storageNodes) storageLeft += sinkCap[fn.outNode(st)];
        int storageTotal = 0;
        for (int cap : storageCapacity) storageTotal += cap;
        int storageUsed = storageTotal - storageLeft;

        long runtimeMs = (System.nanoTime() - start) / 1_000_000L;
        return new ProfessorResult(name, totalPriority, totalPackets,
                                   storageUsed, runtimeMs, note);
    }

    private static ProfessorResult runAlgorithm1(FlowNetwork fn,
                                                 List<Integer> dgNodes,
                                                 List<Integer> storageNodes,
                                                 int[] storageCapacity,
                                                 Object adjGraph) {
        // Paper Algorithm 1: process DGs by nondecreasing packet size/cost c_i.
        Comparator<Integer> bySizeAsc = (a, b) -> {
            int dgA = dgNodes.get(a), dgB = dgNodes.get(b);
            int cmp = Integer.compare(fn.packetSize[dgA], fn.packetSize[dgB]);
            if (cmp != 0) return cmp;
            return Integer.compare(dgA, dgB);
        };
        return runOrderedGreedy("Algorithm 1", fn, dgNodes, storageNodes,
                                storageCapacity, adjGraph, bySizeAsc,
                                "max-flow baseline; smaller packets first");
    }

    private static ProfessorResult runAlgorithm2(FlowNetwork fnA,
                                                 FlowNetwork fnB,
                                                 List<Integer> dgNodes,
                                                 List<Integer> storageNodes,
                                                 int[] storageCapacity,
                                                 Object adjGraph) {
        // Paper Algorithm 2: take the better of priority order and density order.
        Comparator<Integer> byPriorityDesc = (a, b) -> {
            int dgA = dgNodes.get(a), dgB = dgNodes.get(b);
            int cmp = Integer.compare(fnA.packetPriority[dgB], fnA.packetPriority[dgA]);
            if (cmp != 0) return cmp;
            return Integer.compare(dgA, dgB);
        };
        Comparator<Integer> byDensityDesc = (a, b) -> {
            int dgA = dgNodes.get(a), dgB = dgNodes.get(b);
            double dA = (double) fnB.packetPriority[dgA] / fnB.packetSize[dgA];
            double dB = (double) fnB.packetPriority[dgB] / fnB.packetSize[dgB];
            int cmp = Double.compare(dB, dA);
            if (cmp != 0) return cmp;
            return Integer.compare(dgA, dgB);
        };

        ProfessorResult priorityRun = runOrderedAllOrNothing("Algorithm 2A", fnA,
            dgNodes, storageNodes, storageCapacity, adjGraph,
            byPriorityDesc, "all-or-nothing; priority order");
        ProfessorResult densityRun = runOrderedAllOrNothing("Algorithm 2B", fnB,
            dgNodes, storageNodes, storageCapacity, adjGraph,
            byDensityDesc, "all-or-nothing; density order");

        ProfessorResult best = (priorityRun.priority >= densityRun.priority)
            ? priorityRun : densityRun;
        best.name = "Algorithm 2";
        best.note = best.note + "; best of A/B (A="
                  + String.format("%.0f", priorityRun.priority)
                  + ", B=" + String.format("%.0f", densityRun.priority) + ")";
        return best;
    }

    private static double gapPercent(double value, double optimal) {
        if (optimal <= 0) return 0.0;
        return 100.0 * (optimal - value) / optimal;
    }

    private static Set<String> parseProfessorAlgorithms(String input) {
        Set<String> selected = new LinkedHashSet<>();
        if (input == null || input.trim().isEmpty() || input.trim().equalsIgnoreCase("A")) {
            selected.add("ALG1");
            selected.add("ALG2");
            selected.add("ILP");
            return selected;
        }

        for (String token : input.split(",")) {
            switch (token.trim().toUpperCase()) {
                case "1": case "ALG1": case "ALGORITHM1": case "ALGORITHM_1":
                    selected.add("ALG1");
                    break;
                case "2": case "ALG2": case "ALGORITHM2": case "ALGORITHM_2":
                    selected.add("ALG2");
                    break;
                case "3": case "ILP": case "OPT": case "OPTIMAL":
                    selected.add("ILP");
                    break;
                case "A": case "ALL":
                    selected.add("ALG1");
                    selected.add("ALG2");
                    selected.add("ILP");
                    break;
                default:
                    System.out.println("  Ignoring invalid professor-test choice: " + token.trim());
            }
        }

        if (selected.isEmpty()) {
            System.out.println("  No valid professor-test choices selected — defaulting to all required algorithms.");
            selected.add("ALG1");
            selected.add("ALG2");
            selected.add("ILP");
        }
        return selected;
    }

    public static void runProfessorTest() {
        runProfessorTest("A");
    }


    // ── Professor's test case ─────────────────────────────────────────────────
    // Builds the specific 20-node network the professor requested:
    //   5 DGs (100 packets each, size random [1,10], priority random [1,100])
    //   5 storage nodes (capacity random [50,150])
    //   10 transshipment nodes (no data, no storage)
    //   All energy = 50, TR = 30, area = 100×100

    public static void runProfessorTest(String algorithmInput) {
        runConfiguredProfessorTest(
            algorithmInput,
            "PROFESSOR TEST CASE — 20-NODE MWF-S COMPARISON",
            20, 100, 100, 30.0,
            5, 5,
            100,
            1, 10,
            50, 150,
            50,
            1, 100,
            "mwf_s_ilp.lp",
            "solution.txt");
    }

    public static void runCustomProfessorTest(String algorithmInput,
                                              int totalNodes,
                                              int width,
                                              int height,
                                              double transmissionRange,
                                              int numDGs,
                                              int numStorage,
                                              int packetsPerDG,
                                              int minPacketSize,
                                              int maxPacketSize,
                                              int minStorageCapacity,
                                              int maxStorageCapacity,
                                              int nodeEnergyLevel,
                                              int minPriority,
                                              int maxPriority) {
        runConfiguredProfessorTest(
            algorithmInput,
            "CUSTOM PROFESSOR-STYLE MWF-S COMPARISON",
            totalNodes, width, height, transmissionRange,
            numDGs, numStorage,
            packetsPerDG,
            minPacketSize, maxPacketSize,
            minStorageCapacity, maxStorageCapacity,
            nodeEnergyLevel,
            minPriority, maxPriority,
            "mwf_s_custom_ilp.lp",
            "custom_solution.txt");
    }

    private static void runConfiguredProfessorTest(String algorithmInput,
                                                   String title,
                                                   int N,
                                                   int WIDTH,
                                                   int HEIGHT,
                                                   double TR,
                                                   int nDG,
                                                   int nST,
                                                   int packetsEachDG,
                                                   int minSz,
                                                   int maxSz,
                                                   int minCap,
                                                   int maxCap,
                                                   int energyLevel,
                                                   int minPri,
                                                   int maxPri,
                                                   String lpFile,
                                                   String solFile) {
        Set<String> selectedAlgorithms = parseProfessorAlgorithms(algorithmInput);
        Random rng = new Random();
        int nTR = N - nDG - nST;

        if (N < 2 || nDG < 1 || nST < 1 || nTR < 0) {
            System.out.println("Invalid setup. Make sure total nodes >= DGs + storage nodes, with at least 1 DG and 1 storage node.");
            return;
        }
        if (minSz > maxSz || minCap > maxCap || minPri > maxPri) {
            System.out.println("Invalid ranges. Minimum values must be <= maximum values.");
            return;
        }
        if (packetsEachDG < 0 || energyLevel < 0 || TR <= 0 || WIDTH <= 0 || HEIGHT <= 0) {
            System.out.println("Invalid numeric setup. Packets/energy must be nonnegative, and area/TR must be positive.");
            return;
        }

        double[][] nodeLoc;
        int[][] adjMatrix;
        int reachable;
        int attempts = 0;

        // Regenerate positions until the random geometric graph is connected.
        do {
            attempts++;
            nodeLoc = new double[N][2];
            for (int i = 0; i < N; i++) {
                nodeLoc[i][0] = rng.nextDouble() * WIDTH;
                nodeLoc[i][1] = rng.nextDouble() * HEIGHT;
            }

            adjMatrix = new int[N][N];
            for (int u = 0; u < N; u++) {
                for (int v = u + 1; v < N; v++) {
                    double dist = Math.sqrt(
                        Math.pow(nodeLoc[u][0] - nodeLoc[v][0], 2) +
                        Math.pow(nodeLoc[u][1] - nodeLoc[v][1], 2));
                    if (dist <= TR) {
                        adjMatrix[u][v] = 1;
                        adjMatrix[v][u] = 1;
                    }
                }
            }
            reachable = countReachable(adjMatrix, 0);
        } while (reachable < N && attempts < 1000);

        if (reachable < N) {
            System.out.printf("Could not generate a connected %d-node graph after 1000 attempts. Try increasing TR or shrinking the area.%n", N);
            return;
        }

        // Assign roles: first nDG = DG, next nST = storage, remaining = transshipment.
        List<Integer> dgNodes = new ArrayList<>();
        List<Integer> storageNodes = new ArrayList<>();
        for (int i = 0; i < nDG; i++) dgNodes.add(i);
        for (int i = nDG; i < nDG + nST; i++) storageNodes.add(i);

        int[] packetSize = new int[N];
        int[] packetPriority = new int[N];
        int[] nodeEnergy = new int[N];
        int[] packetsPerNode = new int[N];
        int[] storageCapacity = new int[nST];

        Arrays.fill(nodeEnergy, energyLevel);

        for (int dg : dgNodes) {
            packetsPerNode[dg] = packetsEachDG;
            packetSize[dg] = minSz + rng.nextInt(maxSz - minSz + 1);
            packetPriority[dg] = minPri + rng.nextInt(maxPri - minPri + 1);
        }
        for (int j = 0; j < nST; j++) {
            storageCapacity[j] = minCap + rng.nextInt(maxCap - minCap + 1);
        }

        int totalPackets = 0, totalStorageNeed = 0, totalStorageCap = 0;
        for (int dg : dgNodes) {
            totalPackets += packetsPerNode[dg];
            totalStorageNeed += packetsPerNode[dg] * packetSize[dg];
        }
        for (int c : storageCapacity) totalStorageCap += c;

        printBar('=');
        System.out.println(title);
        printBar('=');

        System.out.println("\n[1] Network Parameters");
        System.out.printf("  %-24s %dx%d%n", "Area", WIDTH, HEIGHT);
        System.out.printf("  %-24s %.1f%n", "Transmission range", TR);
        System.out.printf("  %-24s %d  (%d DG, %d storage, %d transshipment)%n",
                          "Total nodes", N, nDG, nST, nTR);
        System.out.printf("  %-24s YES  (%d/%d reachable, attempts=%d)%n",
                          "Connected", reachable, N, attempts);
        System.out.printf("  %-24s %s%n", "Energy model", "uniform: 1 energy per packet per node");
        System.out.printf("  %-24s %d%n", "Energy per node", energyLevel);
        System.out.printf("  %-24s %d%n", "Packets per DG", packetsEachDG);

        System.out.println("\n[2] Capacity Summary");
        System.out.printf("  %-24s %d%n", "Total DG packets", totalPackets);
        System.out.printf("  %-24s %d%n", "Total storage demand", totalStorageNeed);
        System.out.printf("  %-24s %d%n", "Total storage capacity", totalStorageCap);
        System.out.printf("  %-24s %.1f%%%n", "Capacity / demand",
                          totalStorageNeed == 0 ? 0.0 : 100.0 * totalStorageCap / totalStorageNeed);

        System.out.println("\n[3] Random Ranges Used");
        System.out.printf("  %-24s [%d, %d]%n", "Packet size", minSz, maxSz);
        System.out.printf("  %-24s [%d, %d]%n", "Storage capacity", minCap, maxCap);
        System.out.printf("  %-24s [%d, %d]%n", "DG priority", minPri, maxPri);

        System.out.println("\n[4] DG Setup");
        System.out.printf("  %-6s %10s %8s %10s %10s%n",
                          "DG", "Packets", "Size", "Priority", "v/size");
        System.out.println("  ------------------------------------------------");
        for (int dg : dgNodes) {
            System.out.printf("  %-6s %10d %8d %10d %10.2f%n",
                              "DG " + dg, packetsPerNode[dg], packetSize[dg],
                              packetPriority[dg],
                              (double) packetPriority[dg] / packetSize[dg]);
        }

        System.out.println("\n[5] Storage Setup");
        System.out.printf("  %-10s %12s %10s%n", "Storage", "Capacity", "Energy");
        System.out.println("  ------------------------------------");
        for (int j = 0; j < nST; j++) {
            int st = storageNodes.get(j);
            System.out.printf("  %-10s %12d %10d%n",
                              "Node " + st, storageCapacity[j], nodeEnergy[st]);
        }
        System.out.printf("\n[6] Transshipment Nodes%n");
        if (nTR > 0) {
            System.out.printf("  Nodes %d-%d: no data, no storage, energy=%d each%n",
                              nDG + nST, N - 1, energyLevel);
        } else {
            System.out.println("  None");
        }

        ProfessorResult alg1 = null;
        ProfessorResult alg2 = null;
        ProfessorResult ilpWeight = null;
        ProfessorResult ilpFlow = null;

        if (selectedAlgorithms.contains("ALG1")) {
            alg1 = runAlgorithm1(
                new FlowNetwork(packetSize, packetPriority, nodeEnergy, packetsPerNode, nodeLoc),
                dgNodes, storageNodes, storageCapacity, adjMatrix);
        }

        if (selectedAlgorithms.contains("ALG2")) {
            alg2 = runAlgorithm2(
                new FlowNetwork(packetSize, packetPriority, nodeEnergy, packetsPerNode, nodeLoc),
                new FlowNetwork(packetSize, packetPriority, nodeEnergy, packetsPerNode, nodeLoc),
                dgNodes, storageNodes, storageCapacity, adjMatrix);
        }

        if (selectedAlgorithms.contains("ILP")) {
            // Derive file names for both ILPs
            String lpFileWeight = lpFile.replace(".lp", "_weight.lp");
            String solFileWeight = solFile.replace(".txt", "_weight.txt");
            String lpFileFlow = lpFile.replace(".lp", "_flow.lp");
            String solFileFlow = solFile.replace(".txt", "_flow.txt");

            ilpWeight = runILPBaseline(
                packetSize, packetPriority, nodeEnergy, packetsPerNode, nodeLoc,
                dgNodes, storageNodes, storageCapacity, adjMatrix,
                lpFileWeight, solFileWeight, true);

            ilpFlow = runILPBaseline(
                packetSize, packetPriority, nodeEnergy, packetsPerNode, nodeLoc,
                dgNodes, storageNodes, storageCapacity, adjMatrix,
                lpFileFlow, solFileFlow, false);

            // For ILP_max_flow, GLPK objective = packet count, not priority.
            // Compute the priority those packets achieved from the solution file.
            if (ilpFlow != null && ilpFlow.name.equals("ILP_max_flow")) {
                double flowPriority = computePriorityFromSolution(
                    solFileFlow, dgNodes, packetPriority, nodeLoc.length);
                if (flowPriority >= 0) {
                    ilpFlow.priority = flowPriority;
                    ilpFlow.note += "; priority computed from source flows";
                }
            }
        }

        double exactPriority = (ilpWeight != null && ilpWeight.name.equals("ILP_max_weight"))
                               ? ilpWeight.priority : -1.0;

        System.out.println("\n[7] Results");
        printBar('-');
        System.out.printf("%-18s %12s %10s %13s %10s %12s  %s%n",
                          "Algorithm", "Priority", "Packets", "Storage",
                          "Time(ms)", "Gap vs ILP", "Meaning");
        printBar('-');
        if (alg1 != null)      printProfessorRow(alg1, exactPriority);
        if (alg2 != null)      printProfessorRow(alg2, exactPriority);
        if (ilpWeight != null)  printProfessorRow(ilpWeight, exactPriority);
        if (ilpFlow != null)    printProfessorRow(ilpFlow, exactPriority);
        printBar('-');

        System.out.println("\n[8] How to Read This");
        System.out.println("  Algorithm 1 is the paper's max-flow baseline: it sorts by packet size c_i ascending to maximize packet count, then reports the priority achieved.");
        System.out.printf("  Algorithm 2 is all-or-nothing: if a DG cannot send all %d packets, that ordering stops.%n", packetsEachDG);
        System.out.println("  ILP_max_weight uses objective (7): max Σ v_i × f_i — maximizes total preserved priority.");
        System.out.println("  ILP_max_flow uses objective (1): max Σ f_i — maximizes total preserved packets. Algorithm 1 should match or be close to this.");
        System.out.println("  Both ILPs use the same constraints (2)-(6). Uniform energy model: packet size affects storage only.");

        System.out.println("\n[9] Files");
        if (ilpWeight != null) {
            String lpw = lpFile.replace(".lp", "_weight.lp");
            String slw = solFile.replace(".txt", "_weight.txt");
            System.out.println("  ILP_max_weight LP: " + lpw + "  |  Solution: " + slw);
        }
        if (ilpFlow != null) {
            String lpf = lpFile.replace(".lp", "_flow.lp");
            String slf = solFile.replace(".txt", "_flow.txt");
            System.out.println("  ILP_max_flow LP:   " + lpf + "  |  Solution: " + slf);
        }
        System.out.println("  Verify: glpsol --lp <file>.lp -o <file>.txt");
    }

    private static ProfessorResult runILPBaseline(int[] packetSize,
                                                  int[] packetPriority,
                                                  int[] nodeEnergy,
                                                  int[] packetsPerNode,
                                                  double[][] nodeLoc,
                                                  List<Integer> dgNodes,
                                                  List<Integer> storageNodes,
                                                  int[] storageCapacity,
                                                  Object adjGraph,
                                                  String lpFile,
                                                  String solFile,
                                                  boolean maxWeight) {
        long start = System.nanoTime();
        String label = maxWeight ? "ILP_max_weight" : "ILP_max_flow";
        String note  = maxWeight
            ? "objective (7): max total priority"
            : "objective (1): max total packets";

        FlowNetwork lpFN = new FlowNetwork(packetSize, packetPriority,
                                           nodeEnergy, packetsPerNode, nodeLoc);
        try {
            new ILPSolver(lpFN).generateLP(dgNodes, storageNodes, storageCapacity,
                                           adjGraph, lpFile, maxWeight);
        } catch (IOException e) {
            System.err.println("  Could not write LP file: " + e.getMessage());
        }

        GLPKSolutionStats glpk = tryRunGLPK(lpFile, solFile,
            dgNodes, storageNodes, packetSize, nodeLoc.length, maxWeight);
        long runtimeMs = (System.nanoTime() - start) / 1_000_000L;
        if (glpk != null) {
            return new ProfessorResult(label, glpk.objective,
                                       glpk.packets, glpk.storageUsed,
                                       runtimeMs, "GLPK " + note + " from " + solFile);
        }

        // Fallback if GLPK is not installed.
        if (maxWeight) {
            long fbStart = System.nanoTime();
            double fallback = new ExactSolverNew(new FlowNetwork(packetSize, packetPriority,
                                             nodeEnergy, packetsPerNode, nodeLoc))
                .runSilent(dgNodes, storageNodes, storageCapacity, adjGraph);
            runtimeMs = (System.nanoTime() - fbStart) / 1_000_000L;
            return new ProfessorResult(label + " (fallback)", fallback, -1, -1, runtimeMs,
                                       "GLPK not found; Java B&B fallback; LP exported");
        } else {
            return new ProfessorResult(label + " (fallback)", -1, -1, -1, 0,
                                       "GLPK not found; LP exported as " + lpFile);
        }
    }

    private static GLPKSolutionStats tryRunGLPK(String lpFile,
                                                 String solFile,
                                                 List<Integer> dgNodes,
                                                 List<Integer> storageNodes,
                                                 int[] packetSize,
                                                 int totalNodes,
                                                 boolean maxWeight) {
        try {
            ProcessBuilder pb = new ProcessBuilder("glpsol", "--lp", lpFile, "-o", solFile);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) out.append(line).append('\n');
            }
            int code = proc.waitFor();
            if (code != 0) {
                System.out.println("  GLPK did not finish successfully; using fallback baseline.");
                return null;
            }
            GLPKSolutionStats stats = parseGLPKSolutionStats(
                solFile, dgNodes, storageNodes, packetSize, totalNodes, maxWeight);
            if (stats == null) {
                System.out.println("  Could not parse GLPK solution statistics; using fallback baseline.");
            }
            return stats;
        } catch (Exception e) {
            System.out.println("  GLPK unavailable; using fallback baseline. Reason: " + e.getMessage());
            return null;
        }
    }

    private static GLPKSolutionStats parseGLPKSolutionStats(String solFile,
                                                            List<Integer> dgNodes,
                                                            List<Integer> storageNodes,
                                                            int[] packetSize,
                                                            int totalNodes,
                                                            boolean maxWeight) {
        Double objective = null;
        int packets = 0;
        int storageUsed = 0;
        double computedPriority = 0;  // for max_flow: compute Σ v_i * packets_i
        int T = 2 * totalNodes + 1;
        Set<Integer> storageOutNodes = new HashSet<>();
        for (int st : storageNodes) storageOutNodes.add(2 * st + 2);

        // Also need to track per-DG source flows for priority computation
        int S = 0;
        int[] perDGPackets = new int[dgNodes.size()];

        Pattern varPattern = Pattern.compile("^f_(\\d+)_(\\d+)_(\\d+)$");

        try (BufferedReader br = new BufferedReader(new FileReader(solFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                String trimmed = line.trim();

                if (trimmed.startsWith("Objective:")) {
                    String[] parts = trimmed.split("=");
                    if (parts.length >= 2) {
                        String num = parts[1].trim().split("\\s+")[0];
                        objective = Double.parseDouble(num);
                    }
                    continue;
                }

                String[] tokens = trimmed.split("\\s+");
                for (int tIdx = 0; tIdx < tokens.length; tIdx++) {
                    Matcher m = varPattern.matcher(tokens[tIdx]);
                    if (!m.matches()) continue;

                    int dgIdx = Integer.parseInt(m.group(1));
                    int from = Integer.parseInt(m.group(2));
                    int to = Integer.parseInt(m.group(3));

                    if (dgIdx < 0 || dgIdx >= dgNodes.size()) break;

                    Double value = nextNumericToken(tokens, tIdx + 1);
                    if (value == null || Math.abs(value) < 1e-9) break;
                    int flowVal = (int) Math.round(value);

                    // Sink edges: count packets and storage
                    if (to == T && storageOutNodes.contains(from)) {
                        int dg = dgNodes.get(dgIdx);
                        packets += flowVal;
                        storageUsed += flowVal * packetSize[dg];
                    }

                    // Source edges: track per-DG packets for priority computation
                    int dgInNode = 2 * dgNodes.get(dgIdx) + 1;
                    if (from == S && to == dgInNode) {
                        perDGPackets[dgIdx] += flowVal;
                    }

                    break;
                }
            }
        } catch (Exception ignored) {
            return null;
        }

        if (objective == null) return null;

        if (maxWeight) {
            // objective IS the priority
            return new GLPKSolutionStats(objective, packets, storageUsed);
        } else {
            // objective is packet count; compute priority separately
            // Read packetPriority from the LP file isn't possible here,
            // so we store perDGPackets and return objective as the packet count.
            // The caller will need priority — store it in objective field as
            // a negative sentinel and let the caller compute it.
            // Actually, we can return packets as objective since that's what
            // max_flow optimizes, and let the caller handle priority.
            return new GLPKSolutionStats(objective, packets, storageUsed);
        }
    }

    private static Double nextNumericToken(String[] tokens, int start) {
        for (int i = start; i < tokens.length; i++) {
            if (tokens[i].equals("*")) continue;
            try {
                return Double.parseDouble(tokens[i]);
            } catch (NumberFormatException ignored) {
                // keep looking
            }
        }
        return null;
    }

    private static int countReachable(int[][] adjMatrix, int start) {
        int n = adjMatrix.length;
        boolean[] visited = new boolean[n];
        Queue<Integer> q = new LinkedList<>();
        q.add(start);
        visited[start] = true;
        int count = 1;
        while (!q.isEmpty()) {
            int u = q.poll();
            for (int v = 0; v < n; v++) {
                if (!visited[v] && adjMatrix[u][v] == 1) {
                    visited[v] = true;
                    q.add(v);
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * For ILP_max_flow, the GLPK objective is packet count, not priority.
     * This method parses source edges f_i(s, i') from solution.txt
     * to compute the actual priority: Σ v_i × packets_from_DG_i.
     */
    private static double computePriorityFromSolution(String solFile,
                                                       List<Integer> dgNodes,
                                                       int[] packetPriority,
                                                       int totalNodes) {
        int S = 0;
        double totalPriority = 0;
        Pattern varPattern = Pattern.compile("^f_(\\d+)_(\\d+)_(\\d+)$");

        try (BufferedReader br = new BufferedReader(new FileReader(solFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                String trimmed = line.trim();
                String[] tokens = trimmed.split("\\s+");
                for (int tIdx = 0; tIdx < tokens.length; tIdx++) {
                    Matcher m = varPattern.matcher(tokens[tIdx]);
                    if (!m.matches()) continue;

                    int dgIdx = Integer.parseInt(m.group(1));
                    int from  = Integer.parseInt(m.group(2));
                    int to    = Integer.parseInt(m.group(3));

                    if (dgIdx < 0 || dgIdx >= dgNodes.size()) break;

                    // Source edge: s → i'
                    int dgInNode = 2 * dgNodes.get(dgIdx) + 1;
                    if (from == S && to == dgInNode) {
                        Double value = nextNumericToken(tokens, tIdx + 1);
                        if (value != null && Math.abs(value) > 1e-9) {
                            int dg = dgNodes.get(dgIdx);
                            totalPriority += Math.round(value) * packetPriority[dg];
                        }
                    }
                    break;
                }
            }
        } catch (Exception ignored) {
            return -1;
        }
        return totalPriority;
    }

    private static void printProfessorRow(ProfessorResult r, double exactPriority) {
        String packets = r.packets >= 0 ? String.valueOf(r.packets) : "N/A";
        String storage = r.storageUsed >= 0 ? String.valueOf(r.storageUsed) : "N/A";
        String gap;
        if (exactPriority <= 0) {
            gap = "N/A";
        } else if (r.name.equals("ILP_max_weight")) {
            gap = "0.0%";
        } else {
            gap = String.format("%.1f%%", gapPercent(r.priority, exactPriority));
        }
        System.out.printf("%-18s %12.0f %10s %13s %10d %12s  %s%n",
                          r.name, r.priority, packets, storage, r.runtimeMs, gap, r.note);
    }

    private static void printBar(char ch) {
        for (int i = 0; i < 110; i++) System.out.print(ch);
        System.out.println();
    }

}
