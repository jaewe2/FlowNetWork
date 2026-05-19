import java.util.*;

// =============================================================================
//  Main — entry point
//
//  Reads configuration from stdin and delegates to SimulationRunner.
//  Mode 1: Regular MWF-S simulation (all algorithms + scaling trials)
//  Mode 2: Professor's ILP test case (20 nodes, fixed structure, LP file output)
//  Mode 3: Same professor-style comparison, but with user-configurable parameters
// =============================================================================

public class Main {

    public static void main(String[] args) {
        Scanner kb = new Scanner(System.in);

        System.out.println("Select mode:");
        System.out.println("  1 — Regular MWF-S simulation");
        System.out.println("  2 — Professor's ILP test (20 nodes, generates .lp file)");
        System.out.println("  3 — Custom professor-style comparison");
        System.out.print("Mode: ");
        int mode = kb.nextInt();

        if (mode == 2) {
            System.out.println("\nChoose algorithms to run for Professor's test:");
            System.out.println("  1 — Algorithm 1 (paper max-flow / size order)");
            System.out.println("  2 — Algorithm 2 (paper approximation / all-or-nothing)");
            System.out.println("  3 — ILP baselines (ILP_max_weight + ILP_max_flow)");
            System.out.println("  A — All required algorithms");
            System.out.print("Algorithms (comma-separated, e.g. 1,2,3 or A): ");
            String professorChoices = kb.next();

            System.out.println();
            ILPSolver.runProfessorTest(professorChoices);
            kb.close();
            return;
        }

        if (mode == 3) {
            System.out.println("\nCustom professor-style comparison setup");
            System.out.println("This uses the same model as Mode 2, but lets you change the network parameters.");

            System.out.print("Width x and length y of sensor network: ");
            int customWidth = kb.nextInt();
            int customHeight = kb.nextInt();

            System.out.print("Transmission range (m): ");
            double customTR = kb.nextDouble();

            System.out.print("Total number of nodes: ");
            int customNodes = Math.max(2, kb.nextInt());

            System.out.print("Number of DG nodes: ");
            int customDGs = Math.max(1, kb.nextInt());

            System.out.print("Number of storage nodes: ");
            int customStorage = Math.max(1, kb.nextInt());

            if (customDGs + customStorage > customNodes) {
                System.out.println("DG nodes + storage nodes cannot exceed total nodes.");
                kb.close();
                return;
            }

            System.out.print("Packets per DG: ");
            int packetsPerDG = Math.max(0, kb.nextInt());

            System.out.print("Min packet size (storage units): ");
            int minSize = kb.nextInt();
            System.out.print("Max packet size (storage units): ");
            int maxSize = kb.nextInt();

            System.out.print("Min storage capacity (storage units): ");
            int minStorage = kb.nextInt();
            System.out.print("Max storage capacity (storage units): ");
            int maxStorage = kb.nextInt();

            System.out.print("Uniform node energy level: ");
            int energy = Math.max(0, kb.nextInt());

            System.out.print("Min packet priority: ");
            int minPriority = kb.nextInt();
            System.out.print("Max packet priority: ");
            int maxPriority = kb.nextInt();

            System.out.print("Number of trials (e.g. 20): ");
            int customTrials = Math.max(1, kb.nextInt());

            System.out.println("\nChoose algorithms to run for custom test:");
            System.out.println("  1 — Algorithm 1 (paper max-flow / size order)");
            System.out.println("  2 — Algorithm 2 (paper approximation / all-or-nothing)");
            System.out.println("  3 — ILP baselines (ILP_max_weight + ILP_max_flow)");
            System.out.println("  A — All required algorithms");
            System.out.print("Algorithms (comma-separated, e.g. 1,2,3 or A): ");
            String customChoices = kb.next();

            System.out.println();
            ILPSolver.runCustomProfessorTest(
                customChoices,
                customNodes,
                customWidth,
                customHeight,
                customTR,
                customDGs,
                customStorage,
                packetsPerDG,
                minSize,
                maxSize,
                minStorage,
                maxStorage,
                energy,
                minPriority,
                maxPriority,
                customTrials);
            kb.close();
            return;
        }

        // ── Mode 1: Regular simulation ────────────────────────────────────────
        SimulationRunner runner = new SimulationRunner();

        System.out.print("Width x and length y of sensor network: ");
        runner.widthX = kb.nextInt();
        runner.lenY   = kb.nextInt();

        System.out.print("Transmission range (m): ");
        runner.TR = kb.nextInt();

        System.out.print("Graph structure - 1: adj-matrix, 2: adj-list: ");
        runner.choice = kb.nextInt();

        System.out.print("Network sizes to test (comma-separated, e.g. 10,50,100): ");
        String[] sizeTokens = kb.next().split(",");
        runner.networkSizes = new int[sizeTokens.length];
        for (int i = 0; i < sizeTokens.length; i++)
            runner.networkSizes[i] = Math.max(2, Integer.parseInt(sizeTokens[i].trim()));

        System.out.print("Number of trials per network size: ");
        runner.trials = kb.nextInt();

        System.out.print("Min node energy level: ");
        runner.minE = kb.nextInt();
        System.out.print("Max node energy level: ");
        runner.maxE = kb.nextInt();

        System.out.print("Min overflow packets per DG: ");
        runner.minPkt = kb.nextInt();
        System.out.print("Max overflow packets per DG: ");
        runner.maxPkt = kb.nextInt();

        System.out.print("Min packet size (storage units): ");
        runner.minSz = kb.nextInt();
        System.out.print("Max packet size (storage units): ");
        runner.maxSz = kb.nextInt();

        System.out.print("Min storage capacity (storage units): ");
        runner.minCap = kb.nextInt();
        System.out.print("Max storage capacity (storage units): ");
        runner.maxCap = kb.nextInt();

        System.out.print("Min packet priority: ");
        runner.minPri = kb.nextInt();
        System.out.print("Max packet priority: ");
        runner.maxPri = kb.nextInt();

        System.out.print("Number of nodes for visual run: ");
        runner.visNodes = Math.max(2, kb.nextInt());

        System.out.println("\nChoose algorithms to run:");
        System.out.println("  1 — GOA");
        System.out.println("  2 — Density GOA");
        System.out.println("  3 — Hybrid GOA");
        System.out.println("  4 — DDR-GOA");
        System.out.println("  5 — DDR+-GOA");
        System.out.println("  6 — PSB-GOA");
        System.out.println("  7 — Java B&B Baseline (not external ILP)");
        System.out.println("  A — All algorithms");
        System.out.print("Algorithms (comma-separated, e.g. 1,2,6 or A): ");
        runner.setSelectedAlgorithms(kb.next());

        System.out.print("Display visualization graphs? (y/n): ");
        runner.showGraphs = kb.next().trim().equalsIgnoreCase("y");

        kb.close();

        runner.runVisual();
        runner.runScaling();
    }
}
