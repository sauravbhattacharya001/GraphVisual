package gvisual;

import edu.uci.ics.jung.graph.Graph;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.*;

/**
 * Graph Drawing Quality Analyzer — evaluates the aesthetic quality of a
 * graph layout by computing standard readability metrics.
 *
 * <h3>Background</h3>
 * <p>A good graph drawing minimises visual clutter and maximises readability.
 * Several objective metrics from the graph-drawing literature quantify this:</p>
 *
 * <h3>Metrics Computed</h3>
 * <ul>
 *   <li><b>Edge crossings:</b> total number of edge–edge intersections
 *       (fewer is better; zero means planar embedding).</li>
 *   <li><b>Edge-length uniformity:</b> coefficient of variation of edge
 *       lengths (lower → more uniform).</li>
 *   <li><b>Minimum angular resolution:</b> smallest angle between adjacent
 *       edges at any vertex (larger is better; ideal = 2π/degree).</li>
 *   <li><b>Stress:</b> Kamada–Kawai stress ∑ w_ij (‖p_i − p_j‖ − d_ij)²
 *       measuring how well Euclidean distances match graph distances.</li>
 *   <li><b>Neighbourhood preservation:</b> fraction of graph-distance-1
 *       neighbours that are also among the k nearest in the drawing.</li>
 *   <li><b>Node-node overlap ratio:</b> fraction of vertex pairs closer
 *       than a threshold (indicates crowding).</li>
 *   <li><b>Drawing area utilisation:</b> ratio of convex-hull area of
 *       vertices to bounding-box area (higher → better spread).</li>
 *   <li><b>Aspect ratio:</b> width/height of bounding box (ideal ≈ 1).</li>
 *   <li><b>Overall quality score:</b> weighted composite in [0, 100].</li>
 * </ul>
 *
 * <p>Positions are supplied as a Map&lt;String, Point2D&gt;. If a vertex
 * has no position, it is excluded from spatial metrics.</p>
 *
 * @author zalenix
 */
public class GraphDrawingQualityAnalyzer {

    private final Graph<String, Edge> graph;
    private final Map<String, Point2D> positions;
    private boolean computed;

    // ── Results ─────────────────────────────────────────────────────
    private int edgeCrossings;
    private double edgeLengthMean;
    private double edgeLengthStdDev;
    private double edgeLengthCV;         // coefficient of variation
    private double minAngularResolution; // radians
    private double avgAngularResolution;
    private double stress;
    private double neighbourhoodPreservation;
    private double overlapRatio;
    private double areaUtilisation;
    private double aspectRatio;
    private double qualityScore;

    // ── BFS distance cache ──────────────────────────────────────────
    private Map<String, Map<String, Integer>> distCache;

    public GraphDrawingQualityAnalyzer(Graph<String, Edge> graph,
                                       Map<String, Point2D> positions) {
        this.graph = Objects.requireNonNull(graph);
        this.positions = Objects.requireNonNull(positions);
    }

    // ── Public getters (lazy compute) ───────────────────────────────

    public int getEdgeCrossings()           { ensureComputed(); return edgeCrossings; }
    public double getEdgeLengthMean()       { ensureComputed(); return edgeLengthMean; }
    public double getEdgeLengthStdDev()     { ensureComputed(); return edgeLengthStdDev; }
    public double getEdgeLengthCV()         { ensureComputed(); return edgeLengthCV; }
    public double getMinAngularResolution() { ensureComputed(); return minAngularResolution; }
    public double getAvgAngularResolution() { ensureComputed(); return avgAngularResolution; }
    public double getStress()               { ensureComputed(); return stress; }
    public double getNeighbourhoodPreservation() { ensureComputed(); return neighbourhoodPreservation; }
    public double getOverlapRatio()         { ensureComputed(); return overlapRatio; }
    public double getAreaUtilisation()      { ensureComputed(); return areaUtilisation; }
    public double getAspectRatio()          { ensureComputed(); return aspectRatio; }
    public double getQualityScore()         { ensureComputed(); return qualityScore; }

    // ── Full report ─────────────────────────────────────────────────

    /**
     * Generates a formatted text report of all drawing quality metrics.
     */
    public String generateReport() {
        ensureComputed();
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════════════════\n");
        sb.append("       GRAPH DRAWING QUALITY REPORT\n");
        sb.append("═══════════════════════════════════════════════════\n\n");

        sb.append(String.format("Vertices with positions: %d / %d%n",
                positions.size(), graph.getVertexCount()));
        sb.append(String.format("Edges: %d%n%n", graph.getEdgeCount()));

        sb.append("─── Edge Crossings ────────────────────────────────\n");
        sb.append(String.format("  Total crossings:    %d%n", edgeCrossings));
        int maxCrossings = graph.getEdgeCount() * (graph.getEdgeCount() - 1) / 2;
        if (maxCrossings > 0) {
            sb.append(String.format("  Crossing density:   %.4f  (crossings / max possible)%n",
                    (double) edgeCrossings / maxCrossings));
        }

        sb.append("\n─── Edge Lengths ──────────────────────────────────\n");
        sb.append(String.format("  Mean length:        %.2f%n", edgeLengthMean));
        sb.append(String.format("  Std deviation:      %.2f%n", edgeLengthStdDev));
        sb.append(String.format("  CV (uniformity):    %.4f  (lower = more uniform)%n", edgeLengthCV));

        sb.append("\n─── Angular Resolution ────────────────────────────\n");
        sb.append(String.format("  Minimum angle:      %.2f°%n", Math.toDegrees(minAngularResolution)));
        sb.append(String.format("  Average angle:      %.2f°%n", Math.toDegrees(avgAngularResolution)));

        sb.append("\n─── Stress (Kamada-Kawai) ─────────────────────────\n");
        sb.append(String.format("  Normalised stress:  %.6f%n", stress));

        sb.append("\n─── Neighbourhood Preservation ────────────────────\n");
        sb.append(String.format("  Preservation rate:  %.2f%%  (graph neighbours among drawing neighbours)%n",
                neighbourhoodPreservation * 100));

        sb.append("\n─── Spatial Distribution ──────────────────────────\n");
        sb.append(String.format("  Node overlap ratio: %.4f  (pairs too close)%n", overlapRatio));
        sb.append(String.format("  Area utilisation:   %.2f%%%n", areaUtilisation * 100));
        sb.append(String.format("  Aspect ratio:       %.3f  (ideal ≈ 1.0)%n", aspectRatio));

        sb.append("\n═══════════════════════════════════════════════════\n");
        sb.append(String.format("  OVERALL QUALITY SCORE:  %.1f / 100%n", qualityScore));
        sb.append(qualityGrade());
        sb.append("═══════════════════════════════════════════════════\n");

        return sb.toString();
    }

    // ── Computation ─────────────────────────────────────────────────

    private synchronized void ensureComputed() {
        if (computed) return;
        computeEdgeCrossings();
        computeEdgeLengths();
        computeAngularResolution();
        computeStress();
        computeNeighbourhoodPreservation();
        computeOverlapRatio();
        computeSpatialDistribution();
        computeQualityScore();
        computed = true;
    }

    // ── Edge crossings ──────────────────────────────────────────────

    private void computeEdgeCrossings() {
        List<Edge> edges = new ArrayList<>(graph.getEdges());
        edgeCrossings = 0;
        for (int i = 0; i < edges.size(); i++) {
            Edge e1 = edges.get(i);
            String u1 = graph.getEndpoints(e1).getFirst();
            String v1 = graph.getEndpoints(e1).getSecond();
            Point2D p1 = positions.get(u1), p2 = positions.get(v1);
            if (p1 == null || p2 == null) continue;

            for (int j = i + 1; j < edges.size(); j++) {
                Edge e2 = edges.get(j);
                String u2 = graph.getEndpoints(e2).getFirst();
                String v2 = graph.getEndpoints(e2).getSecond();
                // skip edges sharing a vertex
                if (u2.equals(u1) || u2.equals(v1) || v2.equals(u1) || v2.equals(v1))
                    continue;
                Point2D p3 = positions.get(u2), p4 = positions.get(v2);
                if (p3 == null || p4 == null) continue;

                if (Line2D.linesIntersect(
                        p1.getX(), p1.getY(), p2.getX(), p2.getY(),
                        p3.getX(), p3.getY(), p4.getX(), p4.getY())) {
                    // exclude collinear touching at endpoints (already filtered)
                    edgeCrossings++;
                }
            }
        }
    }

    // ── Edge lengths ────────────────────────────────────────────────

    private void computeEdgeLengths() {
        List<Double> lengths = new ArrayList<>();
        for (Edge e : graph.getEdges()) {
            String u = graph.getEndpoints(e).getFirst();
            String v = graph.getEndpoints(e).getSecond();
            Point2D pu = positions.get(u), pv = positions.get(v);
            if (pu != null && pv != null) {
                lengths.add(pu.distance(pv));
            }
        }
        if (lengths.isEmpty()) {
            edgeLengthMean = edgeLengthStdDev = edgeLengthCV = 0;
            return;
        }
        double sum = 0;
        for (double l : lengths) sum += l;
        edgeLengthMean = sum / lengths.size();

        double ssq = 0;
        for (double l : lengths) ssq += (l - edgeLengthMean) * (l - edgeLengthMean);
        edgeLengthStdDev = Math.sqrt(ssq / lengths.size());
        edgeLengthCV = edgeLengthMean > 0 ? edgeLengthStdDev / edgeLengthMean : 0;
    }

    // ── Angular resolution ──────────────────────────────────────────

    private void computeAngularResolution() {
        double globalMin = Double.MAX_VALUE;
        double totalAngle = 0;
        int angleCount = 0;

        for (String v : graph.getVertices()) {
            Point2D pv = positions.get(v);
            if (pv == null) continue;

            Collection<String> nbrs = graph.getNeighbors(v);
            if (nbrs == null || nbrs.size() < 2) continue;

            List<Double> angles = new ArrayList<>();
            for (String n : nbrs) {
                Point2D pn = positions.get(n);
                if (pn == null) continue;
                angles.add(Math.atan2(pn.getY() - pv.getY(), pn.getX() - pv.getX()));
            }
            if (angles.size() < 2) continue;

            Collections.sort(angles);
            double minLocal = Double.MAX_VALUE;
            for (int i = 1; i < angles.size(); i++) {
                double diff = angles.get(i) - angles.get(i - 1);
                minLocal = Math.min(minLocal, diff);
                totalAngle += diff;
                angleCount++;
            }
            double wrap = (2 * Math.PI) - (angles.get(angles.size() - 1) - angles.get(0));
            minLocal = Math.min(minLocal, wrap);
            totalAngle += wrap;
            angleCount++;

            globalMin = Math.min(globalMin, minLocal);
        }

        minAngularResolution = globalMin == Double.MAX_VALUE ? 0 : globalMin;
        avgAngularResolution = angleCount > 0 ? totalAngle / angleCount : 0;
    }

    // ── Stress (Kamada-Kawai) ───────────────────────────────────────

    private void computeStress() {
        List<String> verts = new ArrayList<>();
        for (String v : graph.getVertices()) {
            if (positions.containsKey(v)) verts.add(v);
        }
        if (verts.size() < 2) { stress = 0; return; }

        ensureDistCache(verts);

        double totalStress = 0;
        double normaliser = 0;
        for (int i = 0; i < verts.size(); i++) {
            for (int j = i + 1; j < verts.size(); j++) {
                String u = verts.get(i), v = verts.get(j);
                Integer dij = distCache.getOrDefault(u, Collections.emptyMap()).get(v);
                if (dij == null || dij == 0) continue;

                double drawDist = positions.get(u).distance(positions.get(v));
                double ideal = dij * edgeLengthMean; // scale graph distance by mean edge length
                double w = 1.0 / (dij * dij);
                totalStress += w * (drawDist - ideal) * (drawDist - ideal);
                normaliser += w * ideal * ideal;
            }
        }
        stress = normaliser > 0 ? totalStress / normaliser : 0;
    }

    // ── Neighbourhood preservation ──────────────────────────────────

    private void computeNeighbourhoodPreservation() {
        int totalNeighbours = 0;
        int preserved = 0;

        List<String> verts = new ArrayList<>();
        for (String v : graph.getVertices()) {
            if (positions.containsKey(v)) verts.add(v);
        }

        for (String v : verts) {
            Collection<String> nbrs = graph.getNeighbors(v);
            if (nbrs == null || nbrs.isEmpty()) continue;

            int k = 0;
            for (String n : nbrs) {
                if (positions.containsKey(n)) k++;
            }
            if (k == 0) continue;

            // find k nearest in drawing
            Point2D pv = positions.get(v);
            List<Map.Entry<String, Double>> dists = new ArrayList<>();
            for (String u : verts) {
                if (u.equals(v)) continue;
                dists.add(new AbstractMap.SimpleEntry<>(u, pv.distance(positions.get(u))));
            }
            dists.sort(Comparator.comparingDouble(Map.Entry::getValue));

            Set<String> kNearest = new HashSet<>();
            for (int i = 0; i < Math.min(k, dists.size()); i++) {
                kNearest.add(dists.get(i).getKey());
            }

            for (String n : nbrs) {
                if (positions.containsKey(n)) {
                    totalNeighbours++;
                    if (kNearest.contains(n)) preserved++;
                }
            }
        }

        neighbourhoodPreservation = totalNeighbours > 0
                ? (double) preserved / totalNeighbours : 1.0;
    }

    // ── Overlap ratio ───────────────────────────────────────────────

    private void computeOverlapRatio() {
        List<String> verts = new ArrayList<>();
        for (String v : graph.getVertices()) {
            if (positions.containsKey(v)) verts.add(v);
        }
        if (verts.size() < 2) { overlapRatio = 0; return; }

        // threshold: 5% of average edge length or 10 pixels, whichever is larger
        double threshold = Math.max(edgeLengthMean * 0.05, 10.0);
        int overlaps = 0;
        int pairs = 0;

        for (int i = 0; i < verts.size(); i++) {
            for (int j = i + 1; j < verts.size(); j++) {
                pairs++;
                if (positions.get(verts.get(i)).distance(positions.get(verts.get(j))) < threshold) {
                    overlaps++;
                }
            }
        }

        overlapRatio = pairs > 0 ? (double) overlaps / pairs : 0;
    }

    // ── Spatial distribution ────────────────────────────────────────

    private void computeSpatialDistribution() {
        if (positions.isEmpty()) {
            areaUtilisation = 0;
            aspectRatio = 1;
            return;
        }

        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;

        for (Point2D p : positions.values()) {
            minX = Math.min(minX, p.getX());
            maxX = Math.max(maxX, p.getX());
            minY = Math.min(minY, p.getY());
            maxY = Math.max(maxY, p.getY());
        }

        double width = maxX - minX;
        double height = maxY - minY;

        if (width <= 0 || height <= 0) {
            areaUtilisation = 0;
            aspectRatio = 1;
            return;
        }

        aspectRatio = Math.min(width, height) / Math.max(width, height);

        // convex hull area via gift-wrapping
        List<Point2D> pts = new ArrayList<>(positions.values());
        double hullArea = convexHullArea(pts);
        double boxArea = width * height;
        areaUtilisation = boxArea > 0 ? hullArea / boxArea : 0;
    }

    // ── Quality score ───────────────────────────────────────────────

    private void computeQualityScore() {
        // Weighted composite: each sub-score in [0, 1]
        int maxCrossings = Math.max(1, graph.getEdgeCount() * (graph.getEdgeCount() - 1) / 2);
        double crossingScore = 1.0 - Math.min(1.0, (double) edgeCrossings / maxCrossings);
        double uniformityScore = 1.0 - Math.min(1.0, edgeLengthCV);
        double angleScore = graph.getVertexCount() > 1
                ? Math.min(1.0, minAngularResolution / (Math.PI / 4)) : 1.0;
        double stressScore = 1.0 - Math.min(1.0, stress);
        double neighbourScore = neighbourhoodPreservation;
        double overlapScore = 1.0 - overlapRatio;
        double areaScore = areaUtilisation;
        double aspectScore = aspectRatio; // already in [0, 1]

        // Weights (summing to 1)
        qualityScore = 100.0 * (
                0.25 * crossingScore +
                0.15 * uniformityScore +
                0.10 * angleScore +
                0.15 * stressScore +
                0.10 * neighbourScore +
                0.10 * overlapScore +
                0.10 * areaScore +
                0.05 * aspectScore
        );
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private void ensureDistCache(List<String> verts) {
        if (distCache != null) return;
        distCache = new HashMap<>();
        for (String s : verts) {
            distCache.put(s, bfs(s));
        }
    }

    private Map<String, Integer> bfs(String source) {
        Map<String, Integer> dist = new HashMap<>();
        dist.put(source, 0);
        Queue<String> queue = new ArrayDeque<>();
        queue.add(source);
        while (!queue.isEmpty()) {
            String u = queue.poll();
            int d = dist.get(u);
            for (String nbr : graph.getNeighbors(u)) {
                if (!dist.containsKey(nbr)) {
                    dist.put(nbr, d + 1);
                    queue.add(nbr);
                }
            }
        }
        return dist;
    }

    private static double convexHullArea(List<Point2D> points) {
        if (points.size() < 3) return 0;

        // find convex hull (Andrew's monotone chain)
        List<Point2D> sorted = new ArrayList<>(points);
        sorted.sort((a, b) -> a.getX() != b.getX()
                ? Double.compare(a.getX(), b.getX())
                : Double.compare(a.getY(), b.getY()));

        List<Point2D> hull = new ArrayList<>();
        // lower hull
        for (Point2D p : sorted) {
            while (hull.size() >= 2 && cross(hull.get(hull.size() - 2), hull.get(hull.size() - 1), p) <= 0)
                hull.remove(hull.size() - 1);
            hull.add(p);
        }
        // upper hull
        int lower = hull.size() + 1;
        for (int i = sorted.size() - 2; i >= 0; i--) {
            Point2D p = sorted.get(i);
            while (hull.size() >= lower && cross(hull.get(hull.size() - 2), hull.get(hull.size() - 1), p) <= 0)
                hull.remove(hull.size() - 1);
            hull.add(p);
        }
        hull.remove(hull.size() - 1); // remove duplicate last point

        // shoelace formula
        double area = 0;
        for (int i = 0; i < hull.size(); i++) {
            Point2D a = hull.get(i), b = hull.get((i + 1) % hull.size());
            area += a.getX() * b.getY() - b.getX() * a.getY();
        }
        return Math.abs(area) / 2.0;
    }

    private static double cross(Point2D o, Point2D a, Point2D b) {
        return (a.getX() - o.getX()) * (b.getY() - o.getY())
             - (a.getY() - o.getY()) * (b.getX() - o.getX());
    }

    private String qualityGrade() {
        if (qualityScore >= 90) return "  Grade: ★★★★★  Excellent\n";
        if (qualityScore >= 75) return "  Grade: ★★★★☆  Good\n";
        if (qualityScore >= 60) return "  Grade: ★★★☆☆  Adequate\n";
        if (qualityScore >= 40) return "  Grade: ★★☆☆☆  Poor\n";
        return "  Grade: ★☆☆☆☆  Very Poor\n";
    }
}
