package gvisual;

/**
 * Barnes-Hut quadtree for O(V log V) repulsive force approximation
 * in force-directed graph layouts.
 *
 * <p>Divides 2D space into quadrants. Each internal node stores the
 * center of mass and total mass of its children. When computing
 * repulsive force on a body, if a quadrant is "far enough" (its
 * width / distance &lt; theta), the entire quadrant is treated as a
 * single body at its center of mass.</p>
 *
 * <p>Previously an inner class of {@link ForceDirectedLayout}. Extracted
 * to its own file for reusability across other layout algorithms
 * (e.g. spectral, hierarchical) and to improve testability.</p>
 *
 * @author zalenix
 */
final class QuadTree {

    private static final double MIN_DIST = 0.01;
    private static final double MIN_DIST_SQ = MIN_DIST * MIN_DIST;

    private double cx, cy;       // center of mass
    private int mass;            // number of bodies
    private int bodyIndex = -1;  // leaf: index of single body
    private double x, y, size;   // bounding region
    private QuadTree nw, ne, sw, se;

    QuadTree(double x, double y, double size) {
        this.x = x;
        this.y = y;
        this.size = size;
    }

    /**
     * Builds a quadtree from the given positions array.
     *
     * @param pos array of [x, y] positions
     * @param n   number of bodies (must be &le; pos.length)
     * @return root of the quadtree
     */
    static QuadTree build(double[][] pos, int n) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            if (pos[i][0] < minX) minX = pos[i][0];
            if (pos[i][0] > maxX) maxX = pos[i][0];
            if (pos[i][1] < minY) minY = pos[i][1];
            if (pos[i][1] > maxY) maxY = pos[i][1];
        }
        double sz = Math.max(maxX - minX, maxY - minY) + 1.0;
        QuadTree root = new QuadTree(minX - 0.5, minY - 0.5, sz + 1.0);

        for (int i = 0; i < n; i++) {
            root.insert(i, pos[i][0], pos[i][1]);
        }
        return root;
    }

    void insert(int idx, double px, double py) {
        if (mass == 0) {
            bodyIndex = idx;
            cx = px;
            cy = py;
            mass = 1;
            return;
        }

        if (bodyIndex >= 0) {
            int existing = bodyIndex;
            double ex = cx, ey = cy;
            bodyIndex = -1;
            putInChild(existing, ex, ey);
        }

        putInChild(idx, px, py);

        cx = (cx * mass + px) / (mass + 1);
        cy = (cy * mass + py) / (mass + 1);
        mass++;
    }

    private void putInChild(int idx, double px, double py) {
        double half = size / 2.0;
        double midX = x + half;
        double midY = y + half;

        if (px <= midX) {
            if (py <= midY) {
                if (nw == null) nw = new QuadTree(x, y, half);
                nw.insert(idx, px, py);
            } else {
                if (sw == null) sw = new QuadTree(x, midY, half);
                sw.insert(idx, px, py);
            }
        } else {
            if (py <= midY) {
                if (ne == null) ne = new QuadTree(midX, y, half);
                ne.insert(idx, px, py);
            } else {
                if (se == null) se = new QuadTree(midX, midY, half);
                se.insert(idx, px, py);
            }
        }
    }

    /**
     * Computes repulsive force on body {@code i} at (px, py) from this
     * quadtree node, accumulating into disp[0] (dx) and disp[1] (dy).
     *
     * @param i       index of the body (skip self)
     * @param px      x-position of body i
     * @param py      y-position of body i
     * @param kSq     pre-computed k² (optimal distance squared)
     * @param disp    displacement array to accumulate into [dx, dy]
     * @param thetaSq pre-computed theta² for the Barnes-Hut opening angle
     */
    void applyRepulsion(int i, double px, double py,
                        double kSq, double[] disp, double thetaSq) {
        if (mass == 0) return;

        double dx = px - cx;
        double dy = py - cy;
        double distSq = dx * dx + dy * dy;

        if (mass == 1 && bodyIndex >= 0) {
            if (bodyIndex == i) return;
            if (distSq < MIN_DIST_SQ) distSq = MIN_DIST_SQ;
            // force = k² / dist; fx = (dx/dist)*force = dx * k² / dist²
            double f = kSq / distSq;
            disp[0] += dx * f;
            disp[1] += dy * f;
            return;
        }

        // Barnes-Hut check: size/dist < theta  ⟺  size²/distSq < theta²
        // Avoids Math.sqrt in the common "far enough" case.
        double sizeSq = size * size;
        if (sizeSq < thetaSq * distSq) {
            if (distSq < MIN_DIST_SQ) distSq = MIN_DIST_SQ;
            double f = kSq * mass / distSq;
            disp[0] += dx * f;
            disp[1] += dy * f;
            return;
        }

        if (nw != null) nw.applyRepulsion(i, px, py, kSq, disp, thetaSq);
        if (ne != null) ne.applyRepulsion(i, px, py, kSq, disp, thetaSq);
        if (sw != null) sw.applyRepulsion(i, px, py, kSq, disp, thetaSq);
        if (se != null) se.applyRepulsion(i, px, py, kSq, disp, thetaSq);
    }
}
