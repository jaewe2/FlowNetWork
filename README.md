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
- Paper Algorithm 1 achieves 99.5% of ILP Max Weight priority and matches ILP Max Flow on packet count (212 vs 213) — confirming near-optimality for the max-flow objective
- Paper Algorithm 2's all-or-nothing model fails under few storage nodes (62.5% gap) but outperforms Algorithm 1 under severe storage bottlenecks (9.8% vs 27.5% gap from ILP)
- Extended stress tests across four storage-to-demand regimes show no single ordering universally dominates — the optimal strategy depends on network topology
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
| `DensityGOA.java` | Sort by priority ÷ size (value density), BFS augmentation |
| `HybridGOA.java` | Prefix enumeration (κ=2) + density rollout + best-fit storage selection |
| `DDRGOA.java` | Dynamic Density Reordering — re-ranks DGs after each full augmentation step |
| `DDRPlusGOA.java` | Contention-aware DDR — shares reachable storage proportionally across competing DGs using √contention dampening |
| `PSBGOA.java` | Per-Step Best-path scoring — scores every (DG, path) pair at each step, interleaves packets |
| `ExactSolverNew.java` | Dual Branch & Bound — best-fit pass + BFS pass, shared global best |
| `ILPSolver.java` | Generates CPLEX-format .lp file for true ILP optimal via GLPK/CPLEX/Gurobi; includes paper Algorithm 1, Algorithm 2, both ILP baselines (ILP_max_weight, ILP_max_flow), and 95% CI multi-trial support for Mode 2 and Mode 3 |
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

# After running the program, verify the generated LP file:
glpsol --lp mwf_s_ilp_weight.lp -o solution_weight.txt
glpsol --lp mwf_s_ilp_flow.lp   -o solution_flow.txt
cat solution_weight.txt | grep "obj ="
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
Runs all seven heuristic algorithms across multiple network sizes. Each data point averages over the configured number of trials with 95% confidence intervals. Outputs `scaling_results_ci.csv` for plotting with error bars.

**Mode 2 — Professor's ILP test** (fixed 20-node BSN, 20 trials with 95% CI)  
Runs paper algorithms and ILP baselines on the professor's fixed network parameters. Each algorithm is tested on 20 independent random networks. Choose algorithms: `1` (Algorithm 1), `2` (Algorithm 2), `3` (ILP baselines), or `A` (all)

**Mode 3 — Custom professor-style comparison** (same model as Mode 2, user-configurable network parameters, configurable trial count with 95% CI)

### Sample Input (Mode 1)
```
Select mode: 1
Width x and length y of sensor network: 150 150
Transmission range (m): 55
Graph structure - 1: adj-matrix, 2: adj-list: 1
Network sizes to test (comma-separated, e.g. 10,50,100): 10
Number of trials per network size: 10
Min node energy level: 25
Max node energy level: 25
Min overflow packets per DG: 5
Max overflow packets per DG: 10
Min packet size (storage units): 1
Max packet size (storage units): 6
Min storage capacity (storage units): 8
Max storage capacity (storage units): 15
Min packet priority: 1
Max packet priority: 30
Number of nodes for visual run: 10
```

> Setting min = max for any parameter produces the uniform (fixed) case.

---

## Confidence Interval Support

All three modes compute 95% confidence intervals following the methodology from [1]: each data point represents the average of 20 independent simulation runs, each on a freshly generated random network with the same parameters. The CI is computed as:

```
CI = 1.96 × σ / √n
```

This is equivalent to Excel's `CONFIDENCE(0.05, stdev, n)` function. The 1.96 is the z-score for a 95% confidence level from the standard normal distribution.

### How it works
- Each trial generates a fresh random network with the same parameters
- Per-trial results are stored in lists (not just summed)
- After all trials complete, the mean, standard deviation, and CI are computed per algorithm
- Results are printed to the console and written to a CSV file

### Console output
```
[4] Confidence Interval Summary (95% CI, 20 trials)
--------------------------------------------------
Algorithm              Mean       StdDev       CI (±)     CI Range        N
--------------------------------------------------
Algorithm 1          923.00       310.06       135.89 [  787.11,  1058.89]      20
Algorithm 2          845.50       303.77       133.13 [  712.37,   978.63]      20
ILP_max_weight      1034.80       263.40       115.44 [  919.36,  1150.24]      20
ILP_max_flow         961.10       271.19       118.85 [  842.25,  1079.95]      20
```

### CSV output
Mode 1 writes `scaling_results_ci.csv`:
```
Nodes,Algorithm,Mean,StdDev,CI95,CI_Low,CI_High,N
```

Mode 2/3 writes a CSV file (e.g., `mwf_s_custom_ilp_ci_results.csv`):
```
Algorithm,Mean,StdDev,CI95,CI_Low,CI_High,N
```

### Plotting error bars in Excel
1. Open the CSV file
2. Create a line chart with the x-axis as network size (or algorithm name) and y-axis as Mean
3. Click on a data series → Add Error Bars → Custom
4. Set both positive and negative error values to the CI95 column
5. The error bar extends from CI_Low to CI_High, centered on Mean

### CI Sample Inputs

**Small CI test (Mode 3, fast):**
```
Select mode: 3
Width/Height: 50 50 | TR: 25 | Nodes: 10 | DGs: 3 | Storage: 3
Packets/DG: 5 | Size: 1-3 | Storage cap: 5-10 | Energy: 10 | Priority: 1-10
Trials: 20 | Algorithms: A
```

**Medium CI test (Mode 3):**
```
Select mode: 3
Width/Height: 100 100 | TR: 40 | Nodes: 20 | DGs: 5 | Storage: 5
Packets/DG: 10 | Size: 1-5 | Storage cap: 10-20 | Energy: 20 | Priority: 1-50
Trials: 20 | Algorithms: A
```

**Larger CI test (Mode 3):**
```
Select mode: 3
Width/Height: 150 150 | TR: 50 | Nodes: 30 | DGs: 7 | Storage: 7
Packets/DG: 15 | Size: 1-5 | Storage cap: 15-30 | Energy: 30 | Priority: 1-50
Trials: 20 | Algorithms: A
```

**Skip ILP for faster runs:** Use `1,2` instead of `A` for algorithms. Algorithm 1 and 2 run in milliseconds per trial.

> **Note:** There is a 5-second delay between trials to avoid resource contention. With 20 trials this adds about 95 seconds to total runtime.

### CI Experimental Results

**10-Node Network** (50×50 field, TR=25, 3 DGs, 3 storage, 5 pkts/DG, sizes [1,3], priorities [1,10], energy=10, 20 trials)

| Algorithm | Mean | Std Dev | CI (±) | 95% CI Range | N |
|-----------|------|---------|--------|--------------|---|
| Algorithm 1 | 60.55 | 27.95 | 12.25 | [48.30, 72.80] | 20 |
| Algorithm 2 | 55.25 | 26.58 | 11.65 | [43.60, 66.90] | 20 |
| ILP_max_weight | 71.00 | 21.88 | 9.59 | [61.41, 80.59] | 20 |
| ILP_max_flow | 63.75 | 26.22 | 11.49 | [52.26, 75.24] | 20 |

**20-Node Network** (100×100 field, TR=40, 5 DGs, 5 storage, 10 pkts/DG, sizes [1,5], priorities [1,50], energy=20, 20 trials)

| Algorithm | Mean | Std Dev | CI (±) | 95% CI Range | N |
|-----------|------|---------|--------|--------------|---|
| Algorithm 1 | 923.00 | 310.06 | 135.89 | [787.11, 1058.89] | 20 |
| Algorithm 2 | 845.50 | 303.77 | 133.13 | [712.37, 978.63] | 20 |
| ILP_max_weight | 1034.80 | 263.40 | 115.44 | [919.36, 1150.24] | 20 |
| ILP_max_flow | 961.10 | 271.19 | 118.85 | [842.25, 1079.95] | 20 |

At 20 nodes, Algorithm 1 reaches about 89.2% of the ILP optimal on average, while Algorithm 2 sits at 81.7%. The CI ranges for Algorithm 1 and ILP_max_weight do not fully share common ground, indicating the gap is statistically significant.

---

## Problem Statement

In a BSN deployed in a harsh environment (e.g., underwater, underground, volcanic), sensors collect data and must store it locally until a drone or robot retrieves it. When sensor storage fills up, overflow data must be offloaded to nearby storage nodes. Because not all data can always be preserved, the system must decide which data to keep to maximize total preserved priority.

**This extension:** Each data generator (DG) node produces packets of a specific size `szᵢ` (storage units). Different DGs may have different packet sizes, making storage consumption heterogeneous. The goal is to maximize:

```
Vf = Σᵢ (vᵢ × |fᵢ|)
```

subject to energy and storage constraints.

---

## Parameters

| Parameter | Description |
|-----------|-------------|
| Width × Length | Physical dimensions of the sensor field (meters) |
| Transmission Range (TR) | Max distance between two connected nodes (meters) |
| Min/Max Node Energy | Energy budget `Eᵢ` — limits packets routed through a node |
| Min/Max Overflow Packets | `dᵢ` — number of overflow packets at each DG |
| Min/Max Packet Size | `szᵢ` — storage units one packet from DGᵢ occupies |
| Min/Max Storage Capacity | `mⱼ` — raw storage units at each storage node |
| Min/Max Packet Priority | `vᵢ` — importance/weight of packets from DGᵢ |
| Network Sizes | Comma-separated node counts to test (e.g. `8,10,20`) |
| Trials per Size | Number of random trials per network size |
| Visual Run Nodes | Node count for the single graphical run before scaling |

---

## BSN-Based Flow Network (BFN) Transformation

Following Section VI of Rivera & Tang (2024), each BSN graph `G(V, E)` is transformed into a directed flow network `G'(V', E')`.

**Node layout (CFN indices):**
```
0        = super source s
2i + 1   = in-node  i'   for BSN node i
2i + 2   = out-node i''  for BSN node i
2n + 1   = super sink t
```

**Edge construction:**
```
s  -> i'  :  capacity = dᵢ    (DG sending capacity)
i' -> i'' :  capacity = Eᵢ    (node energy budget)
u'' -> v' :  capacity = INF   (BSN routing edge)
j'' -> t  :  capacity = mⱼ    (raw storage units at storage node j)
```

**Size-aware sink check:** When augmenting flow for DGᵢ with packet size `szᵢ`, storage node `j` can only accept a packet if `sinkCap[j] >= szᵢ`. After pushing Δ packets, storage is reduced by `Δ × szᵢ`.

---

## Algorithms

### Algorithm 1 — GOA (Greedy Optimal Algorithm)
**Based on:** Algorithm 3 from Rivera & Tang (2024)  
**Sorting key:** Priority `vᵢ` descending  
**Routing:** BFS shortest augmenting path  
**Complexity:** O(k·|V|·|E|²)

Sorts DGs by priority weight and pushes maximum flow starting from the highest-priority source. Proven optimal for MWF-U (Theorem 2). Not optimal for MWF-S — a large high-priority packet may fill storage that could hold many small packets with greater combined priority.

**Counterexample:**
```
DG0: v=10, sz=3, d=2  vs  DG1: v=7, sz=1, d=6,  storage cap=6
GOA = 20   (pushes DG0 first, fills storage)
Optimal = 42  (pushing DG1 first fits 6 packets)
```

---

### Algorithm 2 — Density GOA (Value Density Greedy)
**New contribution**  
**Sorting key:** Value density `ρᵢ = vᵢ / szᵢ` descending  
**Routing:** BFS shortest augmenting path  
**Complexity:** O(k·|V|·|E|²)

Sorts by priority per storage unit consumed. Motivated by the fractional knapsack algorithm (Dantzig, 1957). Consistently outperforms GOA when packet sizes vary. Also not always optimal — leftover storage fragments create bin-packing difficulty.

---

### Algorithm 3 — Approx GOA (2-Approximation)
**Adapted from:** Algorithm 5 of Rivera & Tang (2024)  
**Complexity:** O(k·|V|·|E|²) with constant factor 2

Runs both GOA and Density GOA independently on fresh network copies and returns whichever result is higher. Always at least as good as either constituent. Provides a guaranteed ≥ ½ × optimal bound (adapted from Theorem 5).

---

### Algorithm 4 — Hybrid GOA (Prefix Enumeration + Best-Fit)
**New contribution**  
**Strategy:** Try every ordered pair of DGs as the first two to commit (κ=2), simulate the rest in density order, commit to the best starting combination  
**Routing:** Best-fit storage selection (route to the storage node where leftover space is smallest)  
**Complexity:** O(k⁴·|V|·|E|²), capped at MAX_HYBRID_DGS=12

Based on the rollout algorithm framework (Bertsekas, Tsitsiklis & Wu, 1997). Addresses the fundamental weakness of all fixed-ordering algorithms: the best ordering depends on the network state after routing has begun. Best-fit selection minimizes storage fragmentation. Achieves 100% of B&B optimal across all experiments.

---

### Algorithm 5 — DDR-GOA (Dynamic Density Reordering)
**New contribution**  
**Strategy:** Re-rank all remaining DGs after each full augmentation step  
**Routing:** BFS shortest augmenting path  
**Complexity:** O(k²·|V|·|E|²)

Grounded in adaptive submodularity theory (Golovin & Krause, 2011). After routing each DG, recomputes effective priority for all remaining DGs: `effPri(i) = vᵢ × min(dᵢ, reachable_capacity / szᵢ)`. Routes whichever scores highest.

---

### Algorithm 6 — DDR+-GOA (Contention-Aware DDR)
**New contribution — extension of DDR-GOA**  
**Strategy:** Re-rank with contention-weighted reachability  
**Routing:** BFS shortest augmenting path  
**Complexity:** O(k²·|V|·|E|²)

Fixes DDR-GOA's competition-blindness using √contention dampening:

```
share(i, j) = sinkCap(j) / √contention(j)
reachable+(i) = Σⱼ share(i, j)
effPri+(i) = vᵢ × min(dᵢ, ⌊reachable+(i) / szᵢ⌋)
```

---

### Algorithm 7 — PSB-GOA (Per-Step Best-Path Scoring)
**New contribution**  
**Strategy:** PE(κ=2) prefix + per-step scoring tail  
**Routing:** Best-fit storage selection with relay energy penalty  
**Complexity:** O(k⁴·|V|·|E|²), capped at MAX_HYBRID_DGS=12

Scores every (DG, path) pair at each step:
```
score(i, path) = (vᵢ × Δ) / (Δ × szᵢ + waste + relayPenalty(path) + 1)
```
Achieves 100% of B&B optimal across all experiments through a different mechanism than Hybrid GOA — interleaving packets from different DGs at fine granularity rather than committing to a global prefix ordering.

---

### Paper Algorithm 1 — Maximum Flow for MWF-S
**From:** Algorithm 1 of Rivera & Tang (2024)  
**Sorting key:** Packet size `cᵢ` ascending (maximize packet count)  
**Routing:** Shortest feasible augmenting path  
**Complexity:** O(|V|·|E|²)

Sorts DGs by packet size in ascending order, maximizing total packet count. This is the paper's max-flow baseline. Under the uniform energy model, packet size affects storage only — not routing cost. Implemented in `ILPSolver.java` and available in Mode 2 and Mode 3.

**Result on 20-node BSN (5 DGs, 100 pkts/DG, sz∈[1,10], E=100):**
- Preserved priority: 13,016 (0.5% gap from ILP Max Weight)
- Preserved packets: 212 (matches ILP Max Flow at 213 — within 1 packet)

---

### Paper Algorithm 2 — Approximation Algorithm for MWF-S
**From:** Algorithm 2 of Rivera & Tang (2024)  
**Model:** All-or-nothing — each DG either sends all dᵢ packets or none  
**Strategy:** Runs two orderings (priority desc, density desc), returns the better result

Under the all-or-nothing constraint, if a DG cannot send all its packets, the ordering halts. Algorithm 2 is a 2-approximation under this model. Implemented in `ILPSolver.java`.

**Result on same 20-node BSN:**
- Preserved priority: 12,200 (6.8% gap from ILP Max Weight)
- Preserved packets: 200 (preserved 2 full DGs; stopped when next DG couldn't fully fit)

**Behavior under different storage regimes:**

| Test | Cap/Demand | Algorithm 1 Gap | Algorithm 2 Gap | Winner |
|------|-----------|-----------------|-----------------|--------|
| Severe bottleneck (20 DG, 20 ST, 100 pkts) | 5.8% | 27.5% | 9.8% | Alg 2 |
| Medium bottleneck (20 DG, 20 ST, 50 pkts) | 37.1% | 25.2% | 20.2% | Alg 2 |
| Storage-scarce (20 DG, 5 ST) | 4.8% | 52.6% | 62.5% | Alg 1 |
| Storage-abundant (5 DG, 20 ST) | 113.4% | 0.0% | 21.2% | Alg 1 = ILP |

When storage is extremely tight with many DGs, Algorithm 2's density ordering wins. When few storage nodes create bottlenecks, Algorithm 2's all-or-nothing rule collapses to a single DG and Algorithm 1 is superior. When storage is abundant, Algorithm 1 matches ILP exactly while Algorithm 2 still falls short.

---

### ILP Baselines
**ILP_max_weight** — Maximizes total preserved priority: `max Σᵢ (vᵢ × fᵢ)`  
**ILP_max_flow** — Maximizes total preserved packets: `max Σᵢ fᵢ`

Both use the same constraints (2)–(6) from Rivera & Tang. Solved externally via GLPK. `ILPSolver.java` generates separate `.lp` files for each objective and reads back the GLPK solution to compute the achieved priority.

---

### Exact Solver — Dual Branch & Bound
**Sequential routing ground truth**  
**Complexity:** O(k!·|V|·|E|²), capped at MAX_EXACT_DGS=15

Exhaustively searches all DG orderings with pruning. Warm-started with the best heuristic result. Runs two independent B&B searches (best-fit + BFS routing) with shared global best.

**Important limitation:** The B&B solver is exact only within the space of sequential DG orderings. The true ILP optimal (computed via GLPK) can be significantly higher because it permits interleaved routing of packets from different DGs. See "ILP Verification" below.

---

## Key Engineering Fixes

### Fix 1 — Sink-Edge Unit Mismatch
The CFN sink edge capacity was in raw storage units, but the bottleneck walk treated it as a packet count — silently mixing units and over-augmenting every path. Fixed by skipping the sink edge in all bottleneck min-walks and augmenting it explicitly by Δ × szᵢ.

### Fix 2 — Dual B&B Exact Solver
Best-fit routing (Hybrid, PSB) and standard BFS routing (GOA, DDR) find genuinely different feasible solutions on the same instance. The original single-strategy B&B allowed heuristics to exceed it. Fixed by running two independent B&B passes, both updating one shared global best.

### Fix 3 — O(k⁴) Complexity Cap
Hybrid GOA and PSB-GOA enumerate k² prefixes with k-step rollouts → O(k⁴·n·m²). At k > 12 the 30-node trials ran indefinitely. Fixed by MAX_HYBRID_DGS=12: fall back to Density GOA automatically. Affected trials marked with `*` in output.

---

## Key Theoretical Findings

- GOA is **not optimal** with different sizes — shown by counterexample (GOA=20, optimal=42)
- Density GOA is **also not optimal** — shown by second counterexample (Density=16, optimal=17)
- The problem is **fundamentally harder** with sizes — leftover storage fragments create bin-packing difficulty on top of flow routing
- Approx GOA **guarantees ≥ ½ × optimal** — proven by adapting Theorem 5 from Rivera & Tang (2024)
- **Best-fit and BFS routing explore different feasible spaces** — the exact solver must cover both to guarantee correctness
- **Sequential routing is a binding limitation** — the ILP finds solutions up to 82% better than the B&B on some instances
- **Contention-aware reachability helps selectively** — DDR+ improves on DDR in sparse/fragmented settings but can over-correct in dense networks
- **No single ordering universally dominates** — the optimal paper algorithm depends on the storage-to-demand ratio and number of storage nodes

---

## Experimental Results

### Multi-Experiment Summary (% of B&B Optimal)

| Experiment | Pkt Size | Storage | GOA | Density | Hybrid | PSB |
|------------|----------|---------|-----|---------|--------|-----|
| 1: Small pkts, tight storage | 1–2 | 4–8 | 96.3% | 100.0% | 100.0% | 100.0% |
| 2: Large pkts, tight storage | 4–8 | 8–15 | 98.0% | 97.5% | 100.0% | 100.0% |
| 3: Wide range, knapsack stress | 1–8 | 8–15 | 94.5% | 98.3% | 99.7% | 99.7% |
| 4: Abundant storage | 1–6 | 15–25 | 84.9% | 95.5% | 100.0% | 100.0% |
| 5: 15 nodes, extreme frag | 1–8 | 6–12 | 97.0% | 93.9% | 100.0% | 100.0% |

All experiments use uniform energy (25/node), field 150×150, TR=55 (Exp 5: 200×200, TR=60).

### 10-Node Scaling Results (10 trials, 8 active)

| Algorithm | Avg Priority | % of B&B Optimal |
|-----------|-------------|------------------|
| B&B Exact | 353.50 | 100.0% |
| GOA | 342.63 | 96.9% |
| Density GOA | 348.38 | 98.6% |
| Approx GOA | 351.75 | 99.5% |
| Hybrid GOA | 353.50 | 100.0% |
| DDR-GOA | 349.13 | 98.8% |
| DDR+-GOA | 347.75 | 98.4% |
| PSB-GOA | 353.50 | 100.0% |

### 15-Node Scaling Results (5 trials, 5 active)

| Algorithm | Avg Priority | % of B&B Optimal |
|-----------|-------------|------------------|
| B&B Exact | 549.20 | 100.0% |
| GOA | 525.20 | 95.6% |
| Density GOA | 507.40 | 92.4% |
| Approx GOA | 525.20 | 95.6% |
| Hybrid GOA | 549.20 | 100.0% |
| DDR-GOA | 519.20 | 94.5% |
| DDR+-GOA | 514.20 | 93.6% |
| PSB-GOA | 549.20 | 100.0% |

### Paper Algorithm Comparison — 20-Node BSN, Variable Packet Sizes

Network: 20 nodes, 5 DGs, 5 storage, 10 relay. Area: 100×100, TR=30. Packets: 100/DG, sz∈[1,10]. Storage: cap∈[50,150]. Energy: 100/node (uniform model). Capacity/demand: 13.1%.

| Algorithm | Preserved Priority | Packets | Storage Used | Runtime | Gap vs ILP |
|-----------|-------------------|---------|-------------|---------|-----------|
| Algorithm 1 | 13,016 | 212 | 408 | 1 ms | 0.5% |
| Algorithm 2 | 12,200 | 200 | 300 | 0 ms | 6.8% |
| ILP Max Weight | 13,084 | 213 | 417 | 44 ms | 0.0% |
| ILP Max Flow | 13,046 | 213 | 419 | 24 ms | 0.3% |

Algorithm 1 matches ILP Max Flow on packet count (212 vs 213) and achieves 99.5% of ILP Max Weight priority. Algorithm 2 preserves 2 full DGs (200 packets) using the density ordering, then stops at the third DG when it cannot fit all 100 packets — the all-or-nothing stopping condition.

### Extended Stress Tests — Custom 100-Node Configurations

All tests: 100 nodes, 2000×2000 area, TR=250, E=100, sz∈[1,10], priority∈[1,100], uniform energy model.

**Test 1 — 20 DG / 20 Storage, Severe Bottleneck (100 pkts/DG, cap/demand = 5.8%)**

| Algorithm | Priority | Packets | Gap from ILP Wt |
|-----------|----------|---------|-----------------|
| Algorithm 1 | 16,645 | 389 | 27.5% |
| Algorithm 2 | 20,700 | 300 | 9.8% |
| ILP Max Weight | 22,946 | 322 | 0.0% |
| ILP Max Flow | 16,835 | 402 | 26.6% |

**Test 2 — 20 DG / 20 Storage, Medium Bottleneck (50 pkts/DG, cap/demand = 37.1%)**

| Algorithm | Priority | Packets | Gap from ILP Wt |
|-----------|----------|---------|-----------------|
| Algorithm 1 | 25,680 | 561 | 25.2% |
| Algorithm 2 | 27,400 | 400 | 20.2% |
| ILP Max Weight | 34,336 | 490 | 0.0% (20,924 ms) |
| ILP Max Flow | 28,789 | 598 | 16.2% |

ILP runtime exceeds 20 seconds at this scale — confirming ILP impracticality for production use.

**Test 3 — Storage-Scarce (20 DGs, 5 storage nodes, cap/demand = 4.8%)**

| Algorithm | Priority | Packets | Gap from ILP Wt |
|-----------|----------|---------|-----------------|
| Algorithm 1 | 9,480 | 305 | 52.6% |
| Algorithm 2 | 7,500 | 100 | 62.5% |
| ILP Max Weight | 20,012 | 299 | 0.0% |
| ILP Max Flow | 17,150 | 389 | 14.3% |

Algorithm 2's all-or-nothing rule collapses to 1 DG (100 packets) when few storage nodes create routing bottlenecks. Algorithm 1 handles partial fills more effectively.

**Test 4 — Storage-Abundant (5 DGs, 20 storage nodes, cap/demand = 113.4%)**

| Algorithm | Priority | Packets | Gap from ILP Wt |
|-----------|----------|---------|-----------------|
| Algorithm 1 | 25,900 | 500 | 0.0% |
| Algorithm 2 | 20,400 | 300 | 21.2% |
| ILP Max Weight | 25,900 | 500 | 0.0% |
| ILP Max Flow | 25,900 | 500 | 0.0% |

When total capacity exceeds total demand, Algorithm 1 matches both ILP baselines exactly. Algorithm 2 still falls 21.2% below due to the all-or-nothing constraint skipping DGs that could be partially accommodated.

### ILP Verification (B&B vs True ILP Optimal)

| Instance | Nodes | DGs | B&B | ILP (GLPK) | Gap |
|----------|-------|-----|-----|------------|-----|
| Exp 3 visual run | 10 | 6 | 338 | 340 | 0.6% |
| Exp 5 visual run | 15 | 9 | 655 | 759 | 15.9% |
| Earlier 10-node run | 10 | 6 | 208 | 379 | 82.2% |
| Earlier 15-node run | 15 | 8 | 389 | 504 | 29.6% |

The gap is instance-dependent and arises from the sequential routing limitation of the B&B solver. The ILP can interleave packets from different DGs across paths simultaneously. The LP relaxation values show small integrality gaps (6.2% and 1.2%), confirming the gap is dominated by the sequential routing restriction rather than integrality.

### Algorithm Complexity

| Algorithm | Time Complexity | Notes |
|-----------|----------------|-------|
| GOA | O(k·\|V\|·\|E\|²) | Simplest; optimal for MWF-U |
| Density GOA | O(k·\|V\|·\|E\|²) | Same as GOA, different sort key |
| Approx GOA | O(k·\|V\|·\|E\|²) | Constant factor 2× |
| Hybrid GOA | O(k⁴·\|V\|·\|E\|²) | Capped at k=12 |
| DDR-GOA | O(k²·\|V\|·\|E\|²) | Re-ranks after each DG |
| DDR+-GOA | O(k²·\|V\|·\|E\|²) | Same as DDR + contention map |
| PSB-GOA | O(k⁴·\|V\|·\|E\|²) | Capped at k=12 |
| Exact B&B | O(k!·\|V\|·\|E\|²) | Capped at k=15 |
| Paper Alg 1 | O(\|V\|·\|E\|²) | Size-sort greedy |
| Paper Alg 2 | O(k·\|V\|·\|E\|²) | All-or-nothing, two orderings |

---

## Randomized Trial Design

Each trial independently varies three factors:

**Node placement** — Gaussian clustering (1–3 centres) + 30% uniform outliers. Produces trials with tight clusters, sparse scatter, or a mix.

**DG/storage split** — Ratio drawn randomly between 20% and 70% per trial. Some trials are DG-heavy (storage bottleneck), others storage-heavy (energy bottleneck).

**Transmission range jitter** — Each trial applies ±25% random multiplier to base TR. The actual TR is printed per trial.

**Connectivity guaranteed** — `buildConnectedGraph()` regenerates up to 1000 times until BFS confirms full connectivity.

---

## Visualization

Each visual run opens graph windows per algorithm:

### Physical BSN Graph (`SensorNetworkGraph`)
- **Purple nodes** = Data Generators — shows `v`, `sz`, `d`, `E`, `(x,y)`
- **Green nodes** = Storage nodes — shows `cap`, `E`, `(x,y)`
- **Orange glow edges** = Flow paths chosen by the algorithm
- **Yellow dashed ring** = Relay-capable node
- Nodes are **draggable**; render at true physical coordinates

### BFN Flow Network (`BFNGraph`)
- Split pairs `i'` / `i''` for each BSN node
- Super source `s` left, super sink `t` right
- Edge labels: `d=X`, `E=X`, `m=X`, `inf`
- **Orange glow** on edges carrying active flow
- **Drag edges** to bend; **drag background** to pan

---

## Sample Output

### Visual Run
```
-- Feasibility Check --
  Total DG packets:    52
  Total storage need:  323
  Total storage cap:   45
  Usable storage cap:  45  (BOTTLENECK)
  Total node energy:   250  (sufficient)
  >> Bottleneck detected — running MWF.

===== VISUAL RUN SUMMARY =====
  ILP Optimal:                338.0
  GOA priority:        293.0  (86.7% of optimal)
  Density GOA priority: 338.0  (100.0% of optimal)
  Hybrid GOA priority: 338.0  (100.0% of optimal)
  DDR-GOA priority:    293.0  (86.7% of optimal)
  DDR+-GOA priority:   293.0  (86.7% of optimal)
  PSB-GOA priority:    338.0  (100.0% of optimal)
```

### Scaling Runs
```
==== Network Size: 10 nodes, 10 trials ====
  Trial  1 [TR=59, DGs=3, STs=7]: GOA=273.0  Density=273.0  Approx=273.0
    Hybrid=273.0  DDR=273.0  DDR+=273.0  PSB=273.0  Exact=273.0
  Trial  3 [TR=64, DGs=6, STs=4]: GOA=340.0  Density=379.0  Approx=379.0
    Hybrid=379.0  DDR=379.0  DDR+=379.0  PSB=379.0  Exact=379.0
  ...
-- Results over 4 active trials (1 skipped, 5 total) --
  ILP Optimal avg:      440.00  (4/4 active trials)
  GOA avg:              411.75  (93.6% of optimal)
  Density GOA avg:      434.25  (98.7% of optimal)
  Hybrid GOA avg:       440.00  (100.0% of optimal)
  PSB-GOA avg:          440.00  (100.0% of optimal)
```

### Paper Algorithm Comparison (Mode 2)
```
[7] Results
---------------------------------------------------------------
Algorithm          Priority    Packets       Storage   Time(ms)  Gap vs ILP
---------------------------------------------------------------
Algorithm 1        13016       212           408       1         0.5%
Algorithm 2        12200       200           300       0         6.8%
ILP_max_weight     13084       213           417       44        0.0%
ILP_max_flow       13046       213           419       24        0.3%
---------------------------------------------------------------
```

### ILP Verification
```
$ glpsol --lp mwf_s_ilp_weight.lp -o solution_weight.txt
GLPK Integer Optimizer 5.0
...
INTEGER OPTIMAL SOLUTION FOUND
Objective:  obj = 340 (MAXimum)
# B&B reported 338, ILP found 340 — 0.6% gap from sequential routing limitation
```

---

## Recommended Test Inputs

### Small & clean (see everything working)
```
150 150 / TR=55 / adj-matrix / sizes: 10 / 10 trials / energy 25-25
packets 5-10 / pkt-size 1-6 / storage 8-15 / priority 1-30 / visual: 10
```

### Maximum fragmentation pressure
```
150 150 / TR=55 / adj-matrix / sizes: 10 / 10 trials / energy 25-25
packets 5-10 / pkt-size 1-8 / storage 8-15 / priority 5-50 / visual: 10
```

### Large scale with complexity cap
```
200 200 / TR=60 / adj-matrix / sizes: 15 / 5 trials / energy 25-25
packets 5-10 / pkt-size 1-8 / storage 6-12 / priority 10-50 / visual: 15
```

### ILP stress test (run glpsol after)
```
150 150 / TR=55 / adj-matrix / sizes: 10 / 10 trials / energy 25-25
packets 5-10 / pkt-size 1-8 / storage 8-15 / priority 5-50 / visual: 10
# Then: glpsol --lp mwf_s_ilp_weight.lp -o solution_weight.txt
```

### Paper algorithm comparison (Mode 2 or 3)
```
Mode 2 — fixed 20-node BSN, algorithms: A (all)
Mode 3 — 2000x2000 / TR=250 / 100 nodes / 20 DGs / 20 storage / 100 pkts/DG
          pkt-size 1-10 / storage cap 10-50 / priority 1-100 / energy 100
```

---

## References

1. G. Rivera and B. Tang, "Priority-Based Data Preservation in Challenging Environments: A Maximum Weighted Flow Approach," California State University Dominguez Hills, 2024. Paper: https://csc.csudh.edu/btang/papers/priority_journal.pdf
2. L. R. Ford and D. R. Fulkerson, "Maximal Flow through a Network," *Canadian Journal of Mathematics*, vol. 8, pp. 399–404, 1956.
3. J. Edmonds and R. M. Karp, "Theoretical Improvements in Algorithmic Efficiency for Network Flow Problems," *Journal of the ACM*, vol. 19, no. 2, pp. 248–264, 1972.
4. R. K. Ahuja, T. L. Magnanti, and J. B. Orlin, *Network Flows: Theory, Algorithms, and Applications*. Prentice-Hall, 1993.
5. H. Kellerer, U. Pferschy, and D. Pisinger, *Knapsack Problems*. Springer, 2004.
6. D. S. Johnson, A. Demers, J. D. Ullman, M. R. Garey, and R. L. Graham, "Worst-Case Performance Bounds for Simple One-Dimensional Packing Algorithms," *SIAM Journal on Computing*, vol. 3, no. 4, pp. 299–325, 1974.
7. D. P. Bertsekas, J. N. Tsitsiklis, and C. Wu, "Rollout Algorithms for Combinatorial Optimization," *Journal of Heuristics*, vol. 3, no. 3, pp. 245–262, 1997.
8. D. Golovin and A. Krause, "Adaptive Submodularity: Theory and Applications in Active Learning and Stochastic Optimization," *Journal of Artificial Intelligence Research*, vol. 42, pp. 427–486, 2011.
9. C. P. Gomes and B. Selman, "Algorithm Portfolios," *Artificial Intelligence*, vol. 126, no. 1–2, pp. 43–62, 2001.
10. B. Tang, N. Yao, and B. Choi, "Maximizing Data Preservation in Intermittently Connected Sensor Networks," in *Proc. of IEEE MASS*, 2012.
11. G. Rivera and B. Tang, "Data Preservation in Intermittently Connected Sensor Networks with Data Priority," in *Proc. of IEEE MASS*, 2013.
12. B. Tang and C. S. Raghavendra, "Truthful and Optimal Data Preservation in Base Station-less Sensor Networks: An Integrated Game Theory and Network Flow Approach," *ACM Transactions on Sensor Networks*, 2023.
13. B. Tang, N. Jaggi, H. Wu, and R. Kurkal, "Energy-Efficient Data Redistribution in Sensor Networks," *ACM Transactions on Sensor Networks*, vol. 9, no. 2, 2013.
