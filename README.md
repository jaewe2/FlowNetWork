# Priority-Based Data Preservation with Variable Packet Sizes
**A Size-Aware Maximum Weighted Flow Approach**  
Author: Jason Roe | Advisor: Professor Bin Tang, California State University Dominguez Hills  
Course: CSC 590 — M.S. Project

---

## Overview

This project extends the Maximum Weighted Flow (MWF-U) framework from Rivera & Tang (2024) to support packets of different sizes across data generator nodes. The core contribution is a new class of size-aware algorithms that maximize total preserved data priority in base station-less sensor networks (BSNs) under storage and energy constraints.

In the original paper, all data packets are assumed to be unit-sized, allowing Algorithm 3 (GOA) to be proven optimal. This project removes that assumption and studies what happens when packets from different source nodes have heterogeneous storage requirements.

**Key results:**
- GOA is provably suboptimal when packet sizes vary (counterexample: GOA=20, optimal=42)
- Seven new heuristic algorithms designed, from simple density sorting to per-step interleaved scoring
- Hybrid GOA and PSB-GOA achieve 100% of B&B optimal across all tested networks (10- and 15-node)
- ILP verification via GLPK reveals the dual B&B solver is not a true exact solver — GLPK finds solutions up to 82% better on some instances due to the sequential routing limitation
- Paper Algorithm 1 achieves 99.5% of ILP Max Weight priority and matches ILP Max Flow on packet count (212 vs 213)
- Paper Algorithm 2's all-or-nothing model fails under few storage nodes (62.5% gap) but outperforms Algorithm 1 under severe storage bottlenecks (9.8% vs 27.5% gap from ILP)
- Extended stress tests across four storage-to-demand regimes show no single ordering universally dominates
- 95% confidence interval analysis across 20 independent trials on 10-node and 20-node networks validates statistical reliability of all results
- DDR+-GOA (contention-aware extension) improves on DDR-GOA in sparse and heavily fragmented networks
- Codebase refactored from a 3,500-line monolith into 19 focused Java files

---

## Files

| File | Description |
|------|-------------|
| **Core Flow Network** | |
| `FlowNetwork.java` | BFN state, buildCFN, bfsFAP, augmentPath, computeDelta, snapCap/restoreFlow, computeReachablePerSink |
| `AugmentationEngine.java` | Shared augmentation helpers — augmentBestFit, runPSBTail (used by Hybrid and PSB) |
| **Algorithms** | |
| `GOA.java` | Greedy Optimal Algorithm — sort by priority, BFS augmentation (Algorithm 3 from paper) |
| `DensityGOA.java` | Sort by priority / size (value density), BFS augmentation |
| `HybridGOA.java` | Prefix enumeration (k=2) + density rollout + best-fit storage selection |
| `DDRGOA.java` | Dynamic Density Reordering — re-ranks DGs after each full augmentation step |
| `DDRPlusGOA.java` | Contention-aware DDR — shares reachable storage proportionally using sqrt-contention dampening |
| `PSBGOA.java` | Per-Step Best-path scoring — scores every (DG, path) pair at each step, interleaves packets |
| `ExactSolverNew.java` | Dual Branch & Bound — best-fit pass + BFS pass, shared global best |
| `ILPSolver.java` | Generates CPLEX-format .lp file for ILP optimal via GLPK; includes paper Algorithm 1, Algorithm 2, both ILP baselines, and 95% CI multi-trial support for Mode 2 and Mode 3 |
| `ExactSolver.java` | Legacy exact solver (single-strategy, retained for reference) |
| **Experiment & I/O** | |
| `SimulationRunner.java` | Scaling loop with per-trial result collection, 95% CI computation, CSV output for error bar plots, active-trial tracking, violation warnings, fallback `*` markers, per-algorithm win/loss counts |
| `TraceLogger.java` | Per-packet relay trace output with node type annotations |
| `AlgorithmResult.java` | Value object — total priority + flow edge list |
| `GraphLauncher.java` | Swing graph window launcher for visual runs |
| `Main.java` | Thin I/O shell — reads parameters, delegates to SimulationRunner; supports Mode 1 (simulation), Mode 2 (professor ILP test), Mode 3 (custom professor-style comparison) |
| **Visualization** | |
| `SensorNetworkGraph.java` | Physical BSN graph (Swing) — draggable nodes, flow paths highlighted |
| `BFNGraph.java` | BFN flow network (Swing) — split nodes, draggable/bendable edges, pannable canvas |
| `Axis.java` | Simple x/y coordinate holder for graph nodes |
| **Legacy** | |
| `SensorStuff.java` | Original 3,500-line monolith — retained for reference, not used by Main |

---

## Requirements & Setup

### Java Version
Java SE 11 or higher. Visualization is built with Java Swing, included in the standard JDK — no additional libraries required.

### Installing Java (JDK)

**macOS:**
```bash
# Download JDK 21 from https://www.oracle.com/java/technologies/downloads/
# After installing the .dmg, verify with:
java -version
javac -version
```

**Windows:**  
Download the JDK 21 MSI installer from https://www.oracle.com/java/technologies/downloads/, run it, then verify in Command Prompt:
```cmd
java -version
javac -version
```

**Linux (Ubuntu/Debian):**
```bash
sudo apt update
sudo apt install openjdk-21-jdk
java -version
```

### ILP Verification (Optional but Recommended)
To verify results against the true ILP optimal, install GLPK:
```bash
# macOS
brew install glpk

# Ubuntu/Debian
sudo apt install glpk-utils

# Windows — download from https://sourceforge.net/projects/winglpk/
# Extract and add the w64 (or w32) folder to your system PATH

# Verify installation
glpsol --version

# After running the program, verify generated LP files:
glpsol --lp mwf_s_custom_ilp_weight.lp -o custom_solution_weight.txt
glpsol --lp mwf_s_custom_ilp_flow.lp -o custom_solution_flow.txt
```

When GLPK is on your PATH, Mode 2 and Mode 3 automatically call it during trials. Without GLPK, the code falls back to the Java B&B solver, which can be very slow on larger networks.

---

## Compile & Run

```bash
# Compile all files
javac *.java

# Run
java Main
```

### Modes

**Mode 1 — Regular MWF-S simulation** (heuristic algorithms + scaling trials with 95% CI)  
Runs all seven heuristic algorithms across multiple network sizes. Each data point averages over 20 trials with 95% confidence intervals. Outputs `scaling_results_ci.csv` for plotting with error bars.

**Mode 2 — Professor's ILP test** (fixed 20-node BSN, 20 trials with 95% CI)  
Runs paper algorithms and ILP baselines on the professor's fixed network parameters. Each algorithm is tested on 20 independent random networks. Choose algorithms: `1` (Algorithm 1), `2` (Algorithm 2), `3` (ILP baselines), or `A` (all). Outputs per-trial results and a CI summary table.

**Mode 3 — Custom professor-style comparison** (user-configurable parameters, configurable trial count with 95% CI)  
Same model as Mode 2 but with full control over network size, DG count, storage count, packet sizes, priorities, and number of trials. Outputs a CSV file for plotting with error bars.

---

## Confidence Interval Support

All three modes compute 95% confidence intervals using the formula:

```
CI = 1.96 * stdev / sqrt(n)
```

This is equivalent to Excel's `CONFIDENCE(0.05, stdev, n)` function. The 1.96 is the z-score for a 95% confidence level from the standard normal distribution. Each trial generates a fresh random network with the same parameters, and per-trial results are collected into lists for computing mean, standard deviation, and CI per algorithm.

### Console output
```
[4] Confidence Interval Summary (95% CI, 20 trials)
--------------------------------------------------
Algorithm              Mean       StdDev       CI (+/-)   CI Range        N
--------------------------------------------------
Algorithm 1          923.00       310.06       135.89 [  787.11,  1058.89]      20
Algorithm 2          845.50       303.77       133.13 [  712.37,   978.63]      20
ILP_max_weight      1034.80       263.40       115.44 [  919.36,  1150.24]      20
ILP_max_flow         961.10       271.19       118.85 [  842.25,  1079.95]      20
```

### CSV output
Mode 1 writes `scaling_results_ci.csv`, Mode 2/3 writes `mwf_s_custom_ilp_ci_results.csv`:
```
Nodes,Algorithm,Mean,StdDev,CI95,CI_Low,CI_High,N
```

### Plotting error bars in Excel
1. Open the CSV file
2. Create a line chart with the x-axis as network size (or algorithm name) and y-axis as Mean
3. Click on a data series, then Add Error Bars, then Custom
4. Set both positive and negative error values to the CI95 column
5. The error bar extends from CI_Low to CI_High, centered on Mean

### CI Results

**10-Node Network** (50x50 field, TR=25, 3 DGs, 3 storage, 5 pkts/DG, sizes [1,3], priorities [1,10], energy=10, 20 trials)

| Algorithm | Mean | Std Dev | CI | 95% CI Range | N |
|-----------|------|---------|-----|--------------|---|
| Algorithm 1 | 60.55 | 27.95 | 12.25 | [48.30, 72.80] | 20 |
| Algorithm 2 | 55.25 | 26.58 | 11.65 | [43.60, 66.90] | 20 |
| ILP_max_weight | 71.00 | 21.88 | 9.59 | [61.41, 80.59] | 20 |
| ILP_max_flow | 63.75 | 26.22 | 11.49 | [52.26, 75.24] | 20 |

**20-Node Network** (100x100 field, TR=40, 5 DGs, 5 storage, 10 pkts/DG, sizes [1,5], priorities [1,50], energy=20, 20 trials)

| Algorithm | Mean | Std Dev | CI | 95% CI Range | N |
|-----------|------|---------|-----|--------------|---|
| Algorithm 1 | 923.00 | 310.06 | 135.89 | [787.11, 1058.89] | 20 |
| Algorithm 2 | 845.50 | 303.77 | 133.13 | [712.37, 978.63] | 20 |
| ILP_max_weight | 1034.80 | 263.40 | 115.44 | [919.36, 1150.24] | 20 |
| ILP_max_flow | 961.10 | 271.19 | 118.85 | [842.25, 1079.95] | 20 |

Algorithm 1 reaches about 89.2% of the ILP optimal on average, while Algorithm 2 sits at 81.7%. The CI ranges for Algorithm 1 and ILP_max_weight do not fully share common ground, indicating the gap is statistically significant.

---

## Sample Inputs

### Mode 1 — Regular simulation with CI
```
Select mode: 1
Width x and length y: 200 200
Transmission range (m): 60
Graph structure - 1: adj-matrix, 2: adj-list: 2
Network sizes to test: 10,20,30,50,100
Number of trials per network size: 20
Min/Max node energy: 20 50
Min/Max overflow packets per DG: 4 10
Min/Max packet size: 1 8
Min/Max storage capacity: 4 16
Min/Max packet priority: 1 50
Number of nodes for visual run: 10
Algorithms: A
Display visualization graphs? n
```

### Mode 2 — Professor's test (20 trials automatic)
```
Select mode: 2
Algorithms: A
```
To skip ILP and run faster, use `1,2` instead of `A`.

### Mode 3 — Small CI test (fast)
```
Select mode: 3
Width/Height: 50 50
TR: 25 | Nodes: 10 | DGs: 3 | Storage: 3
Packets/DG: 5 | Size: 1-3 | Storage cap: 5-10
Energy: 10 | Priority: 1-10 | Trials: 20
Algorithms: A
```

### Mode 3 — Medium CI test
```
Select mode: 3
Width/Height: 100 100
TR: 40 | Nodes: 20 | DGs: 5 | Storage: 5
Packets/DG: 10 | Size: 1-5 | Storage cap: 10-20
Energy: 20 | Priority: 1-50 | Trials: 20
Algorithms: A
```

### Mode 3 — Larger CI test
```
Select mode: 3
Width/Height: 150 150
TR: 50 | Nodes: 30 | DGs: 7 | Storage: 7
Packets/DG: 15 | Size: 1-5 | Storage cap: 15-30
Energy: 30 | Priority: 1-50 | Trials: 20
Algorithms: A
```

### Mode 3 — Skip ILP (much faster)
```
Algorithms: 1,2
```

> **Note:** There is a 5-second delay between trials to avoid resource contention. With 20 trials this adds about 95 seconds to total runtime.

> Setting min = max for any parameter produces the uniform (fixed) case.

---

## Problem Statement

In a BSN deployed in a harsh environment (e.g., underwater, underground, volcanic), sensors collect data and must store it locally until a drone or robot retrieves it. When sensor storage fills up, overflow data must be offloaded to nearby storage nodes. Because not all data can always be preserved, the system must decide which data to keep to maximize total preserved priority.

**This extension:** Each data generator (DG) node produces packets of a specific size `sz_i` (storage units). Different DGs may have different packet sizes, making storage consumption heterogeneous. The goal is to maximize:

```
Vf = sum_i (v_i * |f_i|)
```

subject to energy and storage constraints.

---

## Parameters

| Parameter | Description |
|-----------|-------------|
| Width x Length | Physical dimensions of the sensor field (meters) |
| Transmission Range (TR) | Max distance between two connected nodes (meters) |
| Min/Max Node Energy | Energy budget E_i — limits packets routed through a node |
| Min/Max Overflow Packets | d_i — number of overflow packets at each DG |
| Min/Max Packet Size | sz_i — storage units one packet from DG_i occupies |
| Min/Max Storage Capacity | m_j — raw storage units at each storage node |
| Min/Max Packet Priority | v_i — importance/weight of packets from DG_i |
| Network Sizes | Comma-separated node counts to test (e.g. 8,10,20) |
| Trials per Size | Number of random trials per network size |
| Visual Run Nodes | Node count for the single graphical run before scaling |

---

## Algorithms

### GOA (Greedy Optimal Algorithm)
Sort by priority descending, BFS routing. O(k*|V|*|E|^2). Optimal for MWF-U, not for MWF-S.

### Density GOA
Sort by value density (v_i / sz_i) descending, BFS routing. O(k*|V|*|E|^2).

### Approx GOA
Runs both GOA and Density GOA, keeps the better result. Guarantees >= 1/2 optimal. O(k*|V|*|E|^2).

### Hybrid GOA
Prefix enumeration (k=2) + density rollout + best-fit storage. O(k^4*|V|*|E|^2), capped at 12 DGs. Achieves 100% of B&B optimal in all experiments.

### DDR-GOA
Dynamic re-ranking after each DG is fully routed. O(k^2*|V|*|E|^2).

### DDR+-GOA
Contention-aware DDR with sqrt-contention dampening. O(k^2*|V|*|E|^2).

### PSB-GOA
Per-step scoring of every (DG, path) pair. O(k^4*|V|*|E|^2), capped at 12 DGs. Achieves 100% of B&B optimal in all experiments.

### Paper Algorithm 1 (MaxFlow_Greedy)
Sort by packet size ascending to maximize packet count. O(|V|*|E|^2). Available in Mode 2 and Mode 3.

### Paper Algorithm 2 (MaxWeightedFlow_Approximation)
All-or-nothing model, tries two orderings, returns the better result. O(k*|V|*|E|^2). Available in Mode 2 and Mode 3.

### ILP Baselines
ILP_max_weight (maximize priority) and ILP_max_flow (maximize packets). Solved via GLPK. Available in Mode 2 and Mode 3.

### Dual Branch & Bound
Exhaustive DG ordering search with pruning. O(k!*|V|*|E|^2), capped at 15 DGs. Exact only within sequential routing strategies.

---

## Visualization

Each visual run opens graph windows per algorithm:

**Physical BSN Graph** — Purple nodes = DGs, Green nodes = Storage, Orange edges = flow paths. Nodes are draggable.

**BFN Flow Network** — Split node pairs (i'/i''), super source/sink, edge labels showing capacities. Orange glow on active flow edges. Drag edges to bend, drag background to pan.

---

## References

1. G. Rivera and B. Tang, "Priority-Based Data Preservation in Challenging Environments: A Maximum Weighted Flow Approach," California State University Dominguez Hills, 2024.
2. L. R. Ford and D. R. Fulkerson, "Maximal Flow through a Network," *Canadian Journal of Mathematics*, vol. 8, pp. 399-404, 1956.
3. J. Edmonds and R. M. Karp, "Theoretical Improvements in Algorithmic Efficiency for Network Flow Problems," *Journal of the ACM*, vol. 19, no. 2, pp. 248-264, 1972.
4. R. K. Ahuja, T. L. Magnanti, and J. B. Orlin, *Network Flows: Theory, Algorithms, and Applications*. Prentice-Hall, 1993.
5. H. Kellerer, U. Pferschy, and D. Pisinger, *Knapsack Problems*. Springer, 2004.
6. D. S. Johnson et al., "Worst-Case Performance Bounds for Simple One-Dimensional Packing Algorithms," *SIAM Journal on Computing*, vol. 3, no. 4, pp. 299-325, 1974.
7. D. P. Bertsekas, J. N. Tsitsiklis, and C. Wu, "Rollout Algorithms for Combinatorial Optimization," *Journal of Heuristics*, vol. 3, no. 3, pp. 245-262, 1997.
8. D. Golovin and A. Krause, "Adaptive Submodularity," *JAIR*, vol. 42, pp. 427-486, 2011.
9. C. P. Gomes and B. Selman, "Algorithm Portfolios," *Artificial Intelligence*, vol. 126, no. 1-2, pp. 43-62, 2001.
10. B. Tang, N. Yao, and B. Choi, "Maximizing Data Preservation in Intermittently Connected Sensor Networks," in *Proc. of IEEE MASS*, 2012.
11. G. Rivera and B. Tang, "Data Preservation in Intermittently Connected Sensor Networks with Data Priority," in *Proc. of IEEE MASS*, 2013.
12. B. Tang and C. S. Raghavendra, "Truthful and Optimal Data Preservation in Base Station-less Sensor Networks," *ACM Transactions on Sensor Networks*, 2023.
13. B. Tang, N. Jaggi, H. Wu, and R. Kurkal, "Energy-Efficient Data Redistribution in Sensor Networks," *ACM Transactions on Sensor Networks*, vol. 9, no. 2, 2013.
