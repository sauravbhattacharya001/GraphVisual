package gvisual;

import edu.uci.ics.jung.graph.Graph;

import java.util.*;

/**
 * GraphPrivacyExposureAuditor -- agentic re-identification / privacy-exposure
 * auditor over an (assumed-anonymized) graph release.
 *
 * <p>Given a graph that someone is about to publish, this auditor estimates
 * how easy it would be for an adversary with structural background knowledge
 * (degree, 1- or 2-hop neighborhood fingerprint, triangle density,
 * betweenness, articulation status) to re-identify individual vertices,
 * then recommends concrete pre-publish actions.</p>
 *
 * <p>Per vertex it scores six signals:</p>
 * <ol>
 *   <li><b>Degree uniqueness</b> -- how few other nodes share the same
 *       degree (degree-anonymity).</li>
 *   <li><b>Neighborhood-fingerprint uniqueness</b> -- multiset of neighbor
 *       degrees (textbook 1-neighborhood k-anonymity); optionally extended
 *       to 2 hops.</li>
 *   <li><b>Local clustering coefficient</b> -- distinctive triangle
 *       structure.</li>
 *   <li><b>Betweenness rank percentile</b> -- highly-between nodes are
 *       obviously identifiable bridges.</li>
 *   <li><b>Articulation point</b> -- cut vertices are very identifiable.</li>
 *   <li><b>Sensitive flag</b> -- caller-supplied VIPs get a severity bump.</li>
 * </ol>
 *
 * <p>Signals are combined into a 0-100 {@code exposureScore} and bucketed
 * into MINIMAL / LOW / MODERATE / HIGH / CRITICAL bands with verdicts
 * SAFE / MONITOR / ANONYMIZE_RECOMMENDED / ANONYMIZE_REQUIRED /
 * DO_NOT_PUBLISH.</p>
 *
 * <p>Aggregates surface graph-wide k-anonymity (by degree and by 1-hop
 * fingerprint), counts per band, and a letter grade A-F. A ranked
 * P0-P3 playbook recommends actions such as {@code DO_NOT_PUBLISH_RAW},
 * {@code ANONYMIZE_TOP_N_NODES}, {@code ADD_DEGREE_NOISE},
 * {@code REVIEW_ARTICULATION_POINTS}, {@code MERGE_UNIQUE_FINGERPRINTS},
 * {@code STRIP_SENSITIVE_NODES}, or finally {@code RELEASE_AS_IS}.</p>
 *
 * <p>Risk appetite modulates band cutoffs and k thresholds:
 * CAUTIOUS lowers cutoffs (more flags), AGGRESSIVE raises them.</p>
 *
 * <p>Pure Java 8 + JUNG + JDK. Deterministic. Never mutates the input graph.
 * No I/O.</p>
 *
 * <pre>
 *   GraphPrivacyExposureAuditor auditor =
 *           new GraphPrivacyExposureAuditor(graph)
 *               .withRiskAppetite(RiskAppetite.BALANCED)
 *               .withTopK(10);
 *   GraphPrivacyExposureAuditor.Report r = auditor.analyze();
 *   System.out.println(auditor.toMarkdown(r));
 * </pre>
 *
 * @author sauravbhattacharya001
 */
public final class GraphPrivacyExposureAuditor {

    // -- Public types ------------------------------------------------------

    public enum RiskAppetite { CAUTIOUS, BALANCED, AGGRESSIVE }

    public enum Band { MINIMAL, LOW, MODERATE, HIGH, CRITICAL }

    public enum Verdict {
        SAFE, MONITOR, ANONYMIZE_RECOMMENDED, ANONYMIZE_REQUIRED, DO_NOT_PUBLISH
    }

    public enum Priority { P0, P1, P2, P3 }

    public static final class Finding {
        public final String nodeId;
        public final int degree;
        public final double exposureScore;
        public final Band band;
        public final Verdict verdict;
        public final List<String> reasons;
        public final boolean articulationPoint;
        public final boolean sensitive;
        public final double clustering;
        public final double betweennessPercentile;
        public final int degreeShareCount;
        public final int fingerprintShareCount;

        Finding(String nodeId, int degree, double exposureScore, Band band, Verdict verdict,
                List<String> reasons, boolean articulationPoint, boolean sensitive,
                double clustering, double betweennessPercentile,
                int degreeShareCount, int fingerprintShareCount) {
            this.nodeId = nodeId;
            this.degree = degree;
            this.exposureScore = exposureScore;
            this.band = band;
            this.verdict = verdict;
            this.reasons = Collections.unmodifiableList(new ArrayList<>(reasons));
            this.articulationPoint = articulationPoint;
            this.sensitive = sensitive;
            this.clustering = clustering;
            this.betweennessPercentile = betweennessPercentile;
            this.degreeShareCount = degreeShareCount;
            this.fingerprintShareCount = fingerprintShareCount;
        }
    }

    public static final class Summary {
        public final int nodeCount;
        public final int edgeCount;
        public final int kAnonymityDegree;
        public final int kAnonymityFingerprint;
        public final double meanExposureScore;
        public final int criticalCount;
        public final int highCount;
        public final int moderateCount;
        public final int lowCount;
        public final int minimalCount;

        Summary(int nodeCount, int edgeCount, int kAnonymityDegree, int kAnonymityFingerprint,
                double meanExposureScore, int criticalCount, int highCount,
                int moderateCount, int lowCount, int minimalCount) {
            this.nodeCount = nodeCount;
            this.edgeCount = edgeCount;
            this.kAnonymityDegree = kAnonymityDegree;
            this.kAnonymityFingerprint = kAnonymityFingerprint;
            this.meanExposureScore = meanExposureScore;
            this.criticalCount = criticalCount;
            this.highCount = highCount;
            this.moderateCount = moderateCount;
            this.lowCount = lowCount;
            this.minimalCount = minimalCount;
        }
    }

    public static final class Recommendation {
        public final Priority priority;
        public final String actionCode;
        public final String reason;
        public final List<String> affectedNodes;

        Recommendation(Priority priority, String actionCode, String reason,
                       List<String> affectedNodes) {
            this.priority = priority;
            this.actionCode = actionCode;
            this.reason = reason;
            this.affectedNodes = Collections.unmodifiableList(
                    affectedNodes == null ? Collections.<String>emptyList()
                                          : new ArrayList<>(affectedNodes));
        }
    }

    public static final class Report {
        public final List<Finding> findings;       // sorted by exposureScore desc
        public final Summary summary;
        public final List<Recommendation> playbook;
        public final char grade;
        public final long generatedAt;

        Report(List<Finding> findings, Summary summary, List<Recommendation> playbook,
               char grade, long generatedAt) {
            this.findings = Collections.unmodifiableList(new ArrayList<>(findings));
            this.summary = summary;
            this.playbook = Collections.unmodifiableList(new ArrayList<>(playbook));
            this.grade = grade;
            this.generatedAt = generatedAt;
        }
    }

    // -- Configuration -----------------------------------------------------

    private final Graph<String, Edge> graph;
    private RiskAppetite riskAppetite = RiskAppetite.BALANCED;
    private final Set<String> sensitiveNodes = new HashSet<>();
    private int topK = 10;
    private int neighborhoodRadius = 1;
    private Long fixedClock = null;

    public GraphPrivacyExposureAuditor(Graph<String, Edge> graph) {
        if (graph == null) throw new IllegalArgumentException("graph must not be null");
        this.graph = graph;
    }

    public GraphPrivacyExposureAuditor withRiskAppetite(RiskAppetite r) {
        if (r != null) this.riskAppetite = r;
        return this;
    }

    public GraphPrivacyExposureAuditor withSensitiveNodes(Set<String> ids) {
        sensitiveNodes.clear();
        if (ids != null) sensitiveNodes.addAll(ids);
        return this;
    }

    public GraphPrivacyExposureAuditor withTopK(int k) {
        if (k < 1) k = 1;
        this.topK = k;
        return this;
    }

    public GraphPrivacyExposureAuditor withNeighborhoodRadius(int r) {
        if (r < 1) r = 1;
        if (r > 2) r = 2;
        this.neighborhoodRadius = r;
        return this;
    }

    /** Test hook: pin generatedAt for deterministic JSON comparison. */
    public GraphPrivacyExposureAuditor withFixedClock(long epochMs) {
        this.fixedClock = epochMs;
        return this;
    }

    // -- Core analysis -----------------------------------------------------

    public Report analyze() {
        List<String> nodes = new ArrayList<>(graph.getVertices());
        Collections.sort(nodes);
        int n = nodes.size();

        // Empty graph -> grade A with RELEASE_AS_IS only.
        if (n == 0) {
            Summary emptySummary = new Summary(0, 0, 0, 0, 0.0, 0, 0, 0, 0, 0);
            List<Recommendation> pb = new ArrayList<>();
            pb.add(new Recommendation(Priority.P3, "RELEASE_AS_IS",
                    "Empty graph: no privacy exposure.", Collections.<String>emptyList()));
            return new Report(Collections.<Finding>emptyList(), emptySummary, pb, 'A', now());
        }

        // Degrees.
        Map<String, Integer> degree = new HashMap<>();
        for (String v : nodes) degree.put(v, graph.degree(v));

        // Degree histogram (for uniqueness count).
        Map<Integer, Integer> degHistogram = new HashMap<>();
        for (int d : degree.values()) {
            Integer c = degHistogram.get(d);
            degHistogram.put(d, c == null ? 1 : c + 1);
        }

        // 1-hop fingerprint = sorted multiset of neighbor degrees, as canonical string.
        // Optionally also 2-hop ring (degrees of nodes at distance 2).
        Map<String, String> fingerprint = new HashMap<>();
        for (String v : nodes) {
            fingerprint.put(v, buildFingerprint(v, degree, neighborhoodRadius));
        }
        Map<String, Integer> fpHistogram = new HashMap<>();
        for (String fp : fingerprint.values()) {
            Integer c = fpHistogram.get(fp);
            fpHistogram.put(fp, c == null ? 1 : c + 1);
        }

        // Clustering coefficient.
        Map<String, Double> clustering = new HashMap<>();
        for (String v : nodes) clustering.put(v, localClustering(v));

        // Betweenness (Brandes, undirected, unweighted).
        Map<String, Double> betweenness = brandesBetweenness(nodes);

        // Rank betweenness -> percentile (0..1, higher = more central).
        Map<String, Double> btwPercentile = percentiles(betweenness);

        // Articulation points.
        Set<String> articulation = findArticulationPoints(nodes);

        // K-anonymity aggregates.
        int kDeg = Integer.MAX_VALUE;
        for (int c : degHistogram.values()) if (c < kDeg) kDeg = c;
        int kFp = Integer.MAX_VALUE;
        for (int c : fpHistogram.values()) if (c < kFp) kFp = c;
        if (n == 0) { kDeg = 0; kFp = 0; }

        // Score each node.
        List<Finding> findings = new ArrayList<>(n);
        int crit = 0, high = 0, mod = 0, low = 0, min = 0;
        double sumScore = 0.0;

        int[] cutoffs = bandCutoffs();  // {lowMin, modMin, highMin, critMin}

        for (String v : nodes) {
            int d = degree.get(v);
            int degShare = degHistogram.get(d);
            int fpShare = fpHistogram.get(fingerprint.get(v));
            double clust = clustering.get(v);
            double btw = btwPercentile.getOrDefault(v, 0.0);
            boolean art = articulation.contains(v);
            boolean sens = sensitiveNodes.contains(v);

            // Sub-scores in [0,1].
            double sUniqDeg = uniquenessScore(degShare, n);
            double sUniqFp  = uniquenessScore(fpShare, n);
            double sBtw     = btw;                          // already percentile
            double sClust   = triangleOutlierScore(clust);  // 0..1, distance from median
            double sArt     = art ? 1.0 : 0.0;
            double sSens    = sens ? 1.0 : 0.0;

            // Weighted blend, weights sum to 1.0 before sensitive bump.
            double raw =
                    0.30 * sUniqFp
                  + 0.22 * sUniqDeg
                  + 0.22 * sBtw
                  + 0.10 * sClust
                  + 0.16 * sArt;
            // Sensitive nodes get an explicit additive bump (severity).
            raw += 0.15 * sSens;
            if (raw > 1.0) raw = 1.0;
            double score = round1(raw * 100.0);
            sumScore += score;

            Band band = bandFor(score, cutoffs);
            Verdict verdict = verdictFor(band);

            List<String> reasons = new ArrayList<>();
            if (degShare == 1) reasons.add("UNIQUE_DEGREE");
            else if (degShare <= 2) reasons.add("RARE_DEGREE");
            if (fpShare == 1) reasons.add("UNIQUE_NEIGHBORHOOD_FINGERPRINT");
            else if (fpShare <= 2) reasons.add("RARE_NEIGHBORHOOD_FINGERPRINT");
            if (btw >= 0.85) reasons.add("HIGH_BETWEENNESS");
            if (art) reasons.add("ARTICULATION_POINT");
            if (sens) reasons.add("SENSITIVE_NODE");
            if (sClust >= 0.7) reasons.add("TRIANGLE_OUTLIER");
            if (reasons.isEmpty()) reasons.add("LOW_RISK_BASELINE");

            findings.add(new Finding(v, d, score, band, verdict, reasons,
                    art, sens, round3(clust), round3(btw), degShare, fpShare));

            switch (band) {
                case CRITICAL: crit++; break;
                case HIGH:     high++; break;
                case MODERATE: mod++;  break;
                case LOW:      low++;  break;
                case MINIMAL:  min++;  break;
            }
        }

        // Sort findings: exposureScore desc, then nodeId asc (deterministic).
        Collections.sort(findings, new Comparator<Finding>() {
            @Override public int compare(Finding a, Finding b) {
                int c = Double.compare(b.exposureScore, a.exposureScore);
                if (c != 0) return c;
                return a.nodeId.compareTo(b.nodeId);
            }
        });

        double meanScore = round1(sumScore / n);

        Summary summary = new Summary(
                n,
                graph.getEdgeCount(),
                kDeg == Integer.MAX_VALUE ? 0 : kDeg,
                kFp  == Integer.MAX_VALUE ? 0 : kFp,
                meanScore, crit, high, mod, low, min);

        List<Recommendation> playbook = buildPlaybook(summary, findings);
        char grade = computeGrade(summary);

        return new Report(findings, summary, playbook, grade, now());
    }

    // -- Helpers: signals --------------------------------------------------

    private String buildFingerprint(String v, Map<String, Integer> degree, int radius) {
        // Hop-1 neighbor degree multiset (sorted asc).
        List<Integer> hop1 = new ArrayList<>();
        Collection<String> n1 = graph.getNeighbors(v);
        if (n1 != null) for (String u : n1) hop1.add(degree.get(u));
        Collections.sort(hop1);

        StringBuilder sb = new StringBuilder();
        sb.append('h').append(1).append(':').append(hop1.toString());

        if (radius >= 2) {
            // Distance-exactly-2 multiset.
            Set<String> hop2set = new HashSet<>();
            if (n1 != null) {
                for (String u : n1) {
                    Collection<String> nu = graph.getNeighbors(u);
                    if (nu != null) for (String w : nu) {
                        if (w.equals(v)) continue;
                        if (n1.contains(w)) continue;
                        hop2set.add(w);
                    }
                }
            }
            List<Integer> hop2 = new ArrayList<>();
            for (String w : hop2set) hop2.add(degree.get(w));
            Collections.sort(hop2);
            sb.append("|h2:").append(hop2.toString());
        }
        return sb.toString();
    }

    private double localClustering(String v) {
        Collection<String> nbrs = graph.getNeighbors(v);
        if (nbrs == null) return 0.0;
        int k = nbrs.size();
        if (k < 2) return 0.0;
        List<String> list = new ArrayList<>(nbrs);
        int links = 0;
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                if (graph.isNeighbor(list.get(i), list.get(j))) links++;
            }
        }
        double denom = (double) k * (k - 1) / 2.0;
        return links / denom;
    }

    /** Brandes betweenness for undirected unweighted graph. */
    private Map<String, Double> brandesBetweenness(List<String> nodes) {
        Map<String, Double> bc = new HashMap<>();
        for (String v : nodes) bc.put(v, 0.0);

        for (String s : nodes) {
            Deque<String> stack = new ArrayDeque<>();
            Map<String, List<String>> preds = new HashMap<>();
            Map<String, Integer> sigma = new HashMap<>();
            Map<String, Integer> dist = new HashMap<>();
            for (String v : nodes) {
                preds.put(v, new ArrayList<String>());
                sigma.put(v, 0);
                dist.put(v, -1);
            }
            sigma.put(s, 1);
            dist.put(s, 0);

            Deque<String> queue = new ArrayDeque<>();
            queue.add(s);
            while (!queue.isEmpty()) {
                String v = queue.poll();
                stack.push(v);
                Collection<String> nbrs = graph.getNeighbors(v);
                if (nbrs == null) continue;
                for (String w : nbrs) {
                    if (dist.get(w) < 0) {
                        dist.put(w, dist.get(v) + 1);
                        queue.add(w);
                    }
                    if (dist.get(w) == dist.get(v) + 1) {
                        sigma.put(w, sigma.get(w) + sigma.get(v));
                        preds.get(w).add(v);
                    }
                }
            }

            Map<String, Double> delta = new HashMap<>();
            for (String v : nodes) delta.put(v, 0.0);
            while (!stack.isEmpty()) {
                String w = stack.pop();
                for (String v : preds.get(w)) {
                    double share = ((double) sigma.get(v) / sigma.get(w)) * (1.0 + delta.get(w));
                    delta.put(v, delta.get(v) + share);
                }
                if (!w.equals(s)) bc.put(w, bc.get(w) + delta.get(w));
            }
        }
        // Halve because we summed each pair twice on undirected graph.
        for (Map.Entry<String, Double> e : bc.entrySet()) {
            e.setValue(e.getValue() / 2.0);
        }
        return bc;
    }

    private Map<String, Double> percentiles(Map<String, Double> values) {
        Map<String, Double> out = new HashMap<>();
        int n = values.size();
        if (n == 0) return out;
        // Sort ids by value ascending. Ties get average rank.
        List<Map.Entry<String, Double>> list = new ArrayList<>(values.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<String, Double>>() {
            @Override public int compare(Map.Entry<String, Double> a, Map.Entry<String, Double> b) {
                int c = Double.compare(a.getValue(), b.getValue());
                if (c != 0) return c;
                return a.getKey().compareTo(b.getKey());
            }
        });
        if (n == 1) {
            out.put(list.get(0).getKey(), 0.0);
            return out;
        }
        // If every node has the exact same value, percentile is undefined; treat as 0.
        double first = list.get(0).getValue();
        double last = list.get(list.size() - 1).getValue();
        if (first == last) {
            for (Map.Entry<String, Double> e : list) out.put(e.getKey(), 0.0);
            return out;
        }
        // Tie-aware fractional rank.
        int i = 0;
        while (i < list.size()) {
            int j = i;
            while (j + 1 < list.size()
                    && list.get(j + 1).getValue().doubleValue() == list.get(i).getValue().doubleValue()) {
                j++;
            }
            double avgRank = (i + j) / 2.0;          // 0-indexed
            double pct = (n == 1) ? 0.0 : (avgRank / (n - 1));
            for (int k = i; k <= j; k++) out.put(list.get(k).getKey(), pct);
            i = j + 1;
        }
        return out;
    }

    /** Treat clustering values far from the network median as outliers. */
    private double triangleOutlierScore(double clust) {
        // Distance from 0.5 baseline, mapped to [0,1].
        double d = Math.abs(clust - 0.5) * 2.0;
        if (d < 0) d = 0;
        if (d > 1) d = 1;
        return d;
    }

    private double uniquenessScore(int shareCount, int totalNodes) {
        if (totalNodes <= 1) return 0.0;
        if (shareCount <= 1) return 1.0;
        if (shareCount == 2) return 0.75;
        if (shareCount == 3) return 0.55;
        if (shareCount == 4) return 0.4;
        if (shareCount == 5) return 0.3;
        // Smooth tail.
        double frac = (double) shareCount / totalNodes;
        return Math.max(0.0, 0.25 - frac);
    }

    private Set<String> findArticulationPoints(List<String> nodes) {
        Set<String> art = new LinkedHashSet<>();
        if (nodes.isEmpty()) return art;
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < nodes.size(); i++) idx.put(nodes.get(i), i);
        int n = nodes.size();
        int[] disc = new int[n];
        int[] low = new int[n];
        int[] parent = new int[n];
        boolean[] visited = new boolean[n];
        boolean[] isArt = new boolean[n];
        Arrays.fill(disc, -1);
        Arrays.fill(parent, -1);

        int[] timer = new int[]{0};
        for (int u = 0; u < n; u++) {
            if (!visited[u]) apDfsIterative(u, visited, disc, low, parent, isArt, timer, nodes, idx);
        }
        for (int i = 0; i < n; i++) if (isArt[i]) art.add(nodes.get(i));
        return art;
    }

    private void apDfsIterative(int start, boolean[] visited, int[] disc, int[] low,
                                int[] parent, boolean[] isArt, int[] timer,
                                List<String> nodes, Map<String, Integer> idx) {
        // Iterative DFS with neighbor iterators on a stack.
        Deque<int[]> stack = new ArrayDeque<>();
        List<List<Integer>> adj = new ArrayList<>(nodes.size());
        for (int i = 0; i < nodes.size(); i++) adj.add(null);
        // Lazy fill on demand.
        Map<Integer, List<Integer>> cache = new HashMap<>();

        visited[start] = true;
        disc[start] = low[start] = timer[0]++;
        stack.push(new int[]{start, 0});
        int rootChildren = 0;

        while (!stack.isEmpty()) {
            int[] frame = stack.peek();
            int u = frame[0];
            int next = frame[1];
            List<Integer> nb = cache.get(u);
            if (nb == null) {
                nb = new ArrayList<>();
                Collection<String> nbrs = graph.getNeighbors(nodes.get(u));
                if (nbrs != null) {
                    List<String> sorted = new ArrayList<>(nbrs);
                    Collections.sort(sorted);
                    for (String s : sorted) {
                        Integer vi = idx.get(s);
                        if (vi != null) nb.add(vi);
                    }
                }
                cache.put(u, nb);
            }
            if (next < nb.size()) {
                int v = nb.get(next);
                frame[1] = next + 1;
                if (!visited[v]) {
                    parent[v] = u;
                    visited[v] = true;
                    disc[v] = low[v] = timer[0]++;
                    if (u == start) rootChildren++;
                    stack.push(new int[]{v, 0});
                } else if (v != parent[u]) {
                    low[u] = Math.min(low[u], disc[v]);
                }
            } else {
                // Done with u: propagate low to parent.
                stack.pop();
                int p = parent[u];
                if (p != -1) {
                    low[p] = Math.min(low[p], low[u]);
                    if (low[u] >= disc[p] && p != start) isArt[p] = true;
                }
            }
        }
        if (rootChildren > 1) isArt[start] = true;
    }

    // -- Helpers: scoring config ------------------------------------------

    /** Returns {lowMin, modMin, highMin, critMin}. */
    private int[] bandCutoffs() {
        int shift;
        switch (riskAppetite) {
            case CAUTIOUS:   shift = -5; break;
            case AGGRESSIVE: shift =  5; break;
            default:         shift =  0; break;
        }
        return new int[]{20 + shift, 40 + shift, 60 + shift, 80 + shift};
    }

    private Band bandFor(double score, int[] cutoffs) {
        if (score >= cutoffs[3]) return Band.CRITICAL;
        if (score >= cutoffs[2]) return Band.HIGH;
        if (score >= cutoffs[1]) return Band.MODERATE;
        if (score >= cutoffs[0]) return Band.LOW;
        return Band.MINIMAL;
    }

    private Verdict verdictFor(Band b) {
        switch (b) {
            case CRITICAL: return Verdict.DO_NOT_PUBLISH;
            case HIGH:     return Verdict.ANONYMIZE_REQUIRED;
            case MODERATE: return Verdict.ANONYMIZE_RECOMMENDED;
            case LOW:      return Verdict.MONITOR;
            default:       return Verdict.SAFE;
        }
    }

    private List<Recommendation> buildPlaybook(Summary s, List<Finding> findings) {
        // k thresholds for ADD_DEGREE_NOISE / MERGE_UNIQUE_FINGERPRINTS.
        int kDegThreshold, kFpThreshold;
        switch (riskAppetite) {
            case CAUTIOUS:   kDegThreshold = 5; kFpThreshold = 4; break;
            case AGGRESSIVE: kDegThreshold = 2; kFpThreshold = 2; break;
            default:         kDegThreshold = 3; kFpThreshold = 3; break;
        }

        List<Recommendation> pb = new ArrayList<>();

        // P0: do not publish raw.
        if (s.criticalCount > 0) {
            List<String> critIds = new ArrayList<>();
            for (Finding f : findings) if (f.band == Band.CRITICAL) critIds.add(f.nodeId);
            pb.add(new Recommendation(Priority.P0, "DO_NOT_PUBLISH_RAW",
                    s.criticalCount + " CRITICAL exposure node(s) detected; publish would enable trivial re-identification.",
                    critIds));
        }

        // P0: anonymize top-N highest exposure nodes (>= HIGH).
        List<String> topAnon = new ArrayList<>();
        for (Finding f : findings) {
            if (f.band == Band.HIGH || f.band == Band.CRITICAL) topAnon.add(f.nodeId);
            if (topAnon.size() >= topK) break;
        }
        if (!topAnon.isEmpty()) {
            pb.add(new Recommendation(Priority.P0, "ANONYMIZE_TOP_N_NODES",
                    "Anonymize (perturb degree, swap neighbors, or suppress labels) the top "
                    + topAnon.size() + " highest-exposure node(s) before release.",
                    topAnon));
        }

        // P1: add degree noise.
        if (s.kAnonymityDegree > 0 && s.kAnonymityDegree < kDegThreshold) {
            pb.add(new Recommendation(Priority.P1, "ADD_DEGREE_NOISE",
                    "Degree-anonymity k=" + s.kAnonymityDegree
                    + " is below target " + kDegThreshold
                    + "; add/remove edges to ensure every degree class has at least "
                    + kDegThreshold + " nodes.",
                    Collections.<String>emptyList()));
        }

        // P1: review articulation points that are HIGH+.
        List<String> dangerousArticulations = new ArrayList<>();
        for (Finding f : findings) {
            if (f.articulationPoint && (f.band == Band.HIGH || f.band == Band.CRITICAL)) {
                dangerousArticulations.add(f.nodeId);
            }
        }
        if (!dangerousArticulations.isEmpty()) {
            pb.add(new Recommendation(Priority.P1, "REVIEW_ARTICULATION_POINTS",
                    dangerousArticulations.size()
                    + " articulation point(s) carry HIGH+ exposure; consider edge additions to remove the cut.",
                    dangerousArticulations));
        }

        // P2: merge unique fingerprints.
        if (s.kAnonymityFingerprint > 0 && s.kAnonymityFingerprint < kFpThreshold) {
            pb.add(new Recommendation(Priority.P2, "MERGE_UNIQUE_FINGERPRINTS",
                    "1-neighborhood k-anonymity is " + s.kAnonymityFingerprint
                    + " (target >= " + kFpThreshold
                    + "). Merge or perturb unique fingerprints via neighbor swaps.",
                    Collections.<String>emptyList()));
        }

        // P2: strip sensitive nodes that are HIGH+.
        List<String> dangerousSensitive = new ArrayList<>();
        for (Finding f : findings) {
            if (f.sensitive && (f.band == Band.HIGH || f.band == Band.CRITICAL)) {
                dangerousSensitive.add(f.nodeId);
            }
        }
        if (!dangerousSensitive.isEmpty()) {
            pb.add(new Recommendation(Priority.P2, "STRIP_SENSITIVE_NODES",
                    "Sensitive node(s) sit in HIGH+ bands; consider removing them from the release.",
                    dangerousSensitive));
        }

        // P3: release as is, only when nothing above fired.
        if (pb.isEmpty()) {
            pb.add(new Recommendation(Priority.P3, "RELEASE_AS_IS",
                    "No meaningful exposure detected; graph appears safe to release.",
                    Collections.<String>emptyList()));
        }

        // Sort by priority (P0 < P1 < P2 < P3), preserve insertion order within.
        Collections.sort(pb, new Comparator<Recommendation>() {
            @Override public int compare(Recommendation a, Recommendation b) {
                return a.priority.ordinal() - b.priority.ordinal();
            }
        });
        return pb;
    }

    private char computeGrade(Summary s) {
        int n = s.nodeCount;
        if (n == 0) return 'A';
        double critPct = (double) s.criticalCount / n;
        if (s.kAnonymityFingerprint <= 1 && critPct >= 0.05) return 'F';
        if (s.criticalCount > 0) return 'D';
        if (s.kAnonymityFingerprint < 2 || s.highCount > 0) return 'C';
        if (s.kAnonymityFingerprint < 5 || s.moderateCount > 0) return 'B';
        return 'A';
    }

    private long now() {
        return fixedClock != null ? fixedClock.longValue() : System.currentTimeMillis();
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    // -- Renderers: text / markdown / json --------------------------------

    public String render(Report r) {
        StringBuilder sb = new StringBuilder();
        sb.append("GraphPrivacyExposureAuditor Report\n");
        sb.append("==================================\n");
        sb.append("Risk appetite        : ").append(riskAppetite).append('\n');
        sb.append("Nodes / edges        : ").append(r.summary.nodeCount)
          .append(" / ").append(r.summary.edgeCount).append('\n');
        sb.append("k-anonymity (degree) : ").append(r.summary.kAnonymityDegree).append('\n');
        sb.append("k-anonymity (fprint) : ").append(r.summary.kAnonymityFingerprint).append('\n');
        sb.append("Mean exposure score  : ").append(String.format(Locale.ROOT, "%.1f", r.summary.meanExposureScore)).append('\n');
        sb.append("Band counts          : CRIT=").append(r.summary.criticalCount)
          .append(" HIGH=").append(r.summary.highCount)
          .append(" MOD=").append(r.summary.moderateCount)
          .append(" LOW=").append(r.summary.lowCount)
          .append(" MIN=").append(r.summary.minimalCount).append('\n');
        sb.append("Grade                : ").append(r.grade).append('\n');
        sb.append('\n');

        sb.append("Top ").append(Math.min(topK, r.findings.size())).append(" exposure findings\n");
        sb.append("------------------------------\n");
        int shown = 0;
        for (Finding f : r.findings) {
            if (shown >= topK) break;
            sb.append(String.format(Locale.ROOT,
                    "  %-12s score=%5.1f  band=%-8s  verdict=%-22s  deg=%d  reasons=%s%n",
                    f.nodeId, f.exposureScore, f.band, f.verdict, f.degree, f.reasons));
            shown++;
        }
        sb.append('\n');

        sb.append("Playbook\n");
        sb.append("--------\n");
        for (Recommendation rec : r.playbook) {
            sb.append("  [").append(rec.priority).append("] ").append(rec.actionCode)
              .append(" -- ").append(rec.reason).append('\n');
            if (!rec.affectedNodes.isEmpty()) {
                sb.append("       affected: ").append(rec.affectedNodes).append('\n');
            }
        }
        return sb.toString();
    }

    public String toMarkdown(Report r) {
        StringBuilder sb = new StringBuilder();
        sb.append("# GraphPrivacyExposureAuditor Report\n\n");
        sb.append("## Summary\n\n");
        sb.append("- Risk appetite: `").append(riskAppetite).append("`\n");
        sb.append("- Nodes: ").append(r.summary.nodeCount).append('\n');
        sb.append("- Edges: ").append(r.summary.edgeCount).append('\n');
        sb.append("- k-anonymity (degree): ").append(r.summary.kAnonymityDegree).append('\n');
        sb.append("- k-anonymity (1-neighborhood fingerprint): ").append(r.summary.kAnonymityFingerprint).append('\n');
        sb.append("- Mean exposure score: ")
          .append(String.format(Locale.ROOT, "%.1f", r.summary.meanExposureScore)).append('\n');
        sb.append("- Band counts: CRITICAL=").append(r.summary.criticalCount)
          .append(", HIGH=").append(r.summary.highCount)
          .append(", MODERATE=").append(r.summary.moderateCount)
          .append(", LOW=").append(r.summary.lowCount)
          .append(", MINIMAL=").append(r.summary.minimalCount).append('\n');
        sb.append("- Grade: **").append(r.grade).append("**\n\n");

        sb.append("## Top ").append(Math.min(topK, r.findings.size())).append(" exposure findings\n\n");
        sb.append("| node | score | band | verdict | degree | reasons |\n");
        sb.append("|------|------:|------|---------|------:|---------|\n");
        int shown = 0;
        for (Finding f : r.findings) {
            if (shown >= topK) break;
            sb.append("| ").append(f.nodeId)
              .append(" | ").append(String.format(Locale.ROOT, "%.1f", f.exposureScore))
              .append(" | ").append(f.band)
              .append(" | ").append(f.verdict)
              .append(" | ").append(f.degree)
              .append(" | ").append(String.join(", ", f.reasons))
              .append(" |\n");
            shown++;
        }
        sb.append('\n');

        sb.append("## Playbook\n\n");
        for (Recommendation rec : r.playbook) {
            sb.append("- **[").append(rec.priority).append("] ").append(rec.actionCode).append("** -- ")
              .append(rec.reason);
            if (!rec.affectedNodes.isEmpty()) {
                sb.append(" _(affects: ").append(String.join(", ", rec.affectedNodes)).append(")_");
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    public String toJson(Report r) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"generated_at\":").append(r.generatedAt).append(',');
        sb.append("\"grade\":\"").append(r.grade).append("\",");
        sb.append("\"risk_appetite\":\"").append(riskAppetite).append("\",");
        sb.append("\"summary\":{")
          .append("\"critical_count\":").append(r.summary.criticalCount).append(',')
          .append("\"edge_count\":").append(r.summary.edgeCount).append(',')
          .append("\"high_count\":").append(r.summary.highCount).append(',')
          .append("\"k_anonymity_degree\":").append(r.summary.kAnonymityDegree).append(',')
          .append("\"k_anonymity_fingerprint\":").append(r.summary.kAnonymityFingerprint).append(',')
          .append("\"low_count\":").append(r.summary.lowCount).append(',')
          .append("\"mean_exposure_score\":").append(num(r.summary.meanExposureScore)).append(',')
          .append("\"minimal_count\":").append(r.summary.minimalCount).append(',')
          .append("\"moderate_count\":").append(r.summary.moderateCount).append(',')
          .append("\"node_count\":").append(r.summary.nodeCount)
          .append("},");

        sb.append("\"findings\":[");
        for (int i = 0; i < r.findings.size(); i++) {
            if (i > 0) sb.append(',');
            Finding f = r.findings.get(i);
            sb.append('{')
              .append("\"articulation_point\":").append(f.articulationPoint).append(',')
              .append("\"band\":\"").append(f.band).append("\",")
              .append("\"betweenness_percentile\":").append(num(f.betweennessPercentile)).append(',')
              .append("\"clustering\":").append(num(f.clustering)).append(',')
              .append("\"degree\":").append(f.degree).append(',')
              .append("\"degree_share_count\":").append(f.degreeShareCount).append(',')
              .append("\"exposure_score\":").append(num(f.exposureScore)).append(',')
              .append("\"fingerprint_share_count\":").append(f.fingerprintShareCount).append(',')
              .append("\"node\":").append(jsonString(f.nodeId)).append(',')
              .append("\"reasons\":").append(jsonStringArray(f.reasons)).append(',')
              .append("\"sensitive\":").append(f.sensitive).append(',')
              .append("\"verdict\":\"").append(f.verdict).append("\"")
              .append('}');
        }
        sb.append("],");

        sb.append("\"playbook\":[");
        for (int i = 0; i < r.playbook.size(); i++) {
            if (i > 0) sb.append(',');
            Recommendation rec = r.playbook.get(i);
            sb.append('{')
              .append("\"action_code\":\"").append(rec.actionCode).append("\",")
              .append("\"affected_nodes\":").append(jsonStringArray(rec.affectedNodes)).append(',')
              .append("\"priority\":\"").append(rec.priority).append("\",")
              .append("\"reason\":").append(jsonString(rec.reason))
              .append('}');
        }
        sb.append(']');

        sb.append('}');
        return sb.toString();
    }

    private static String num(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) return "0";
        return String.format(Locale.ROOT, "%.4f", d);
    }

    private static String jsonStringArray(List<String> xs) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < xs.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(jsonString(xs.get(i)));
        }
        sb.append(']');
        return sb.toString();
    }

    private static String jsonString(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b");  break;
                case '\f': sb.append("\\f");  break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
