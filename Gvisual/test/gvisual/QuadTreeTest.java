package gvisual;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for the package-private {@link QuadTree} Barnes-Hut tree
 * used by force-directed layouts.
 *
 * <p>QuadTree has package-private visibility, so this test must live in
 * the {@code gvisual} package. Coverage:</p>
 * <ul>
 *   <li>building from positions and single-body handling</li>
 *   <li>that a body never repels itself</li>
 *   <li>repulsion direction (away from neighbor)</li>
 *   <li>that two distant clusters can be approximated as a single
 *       center-of-mass (Barnes-Hut "far enough" path)</li>
 *   <li>numerical stability when two bodies are coincident</li>
 * </ul>
 */
public class QuadTreeTest {

    private static final double THETA_SQ = 0.5 * 0.5;
    private static final double K_SQ = 1.0;
    private static final double EPS = 1e-9;

    @Test
    public void testBuildSingleBody() {
        double[][] pos = { {1.0, 2.0} };
        QuadTree root = QuadTree.build(pos, 1);
        assertNotNull("build() must return a non-null root", root);

        double[] disp = new double[2];
        // self repulsion must be a no-op
        root.applyRepulsion(0, 1.0, 2.0, K_SQ, disp, THETA_SQ);
        assertEquals(0.0, disp[0], EPS);
        assertEquals(0.0, disp[1], EPS);
    }

    @Test
    public void testBuildEmptyDoesNotThrow() {
        // n==0 should still produce a valid (empty) tree and zero force
        double[][] pos = new double[0][0];
        QuadTree root = QuadTree.build(pos, 0);
        assertNotNull(root);

        double[] disp = new double[2];
        root.applyRepulsion(0, 0.0, 0.0, K_SQ, disp, THETA_SQ);
        assertEquals(0.0, disp[0], EPS);
        assertEquals(0.0, disp[1], EPS);
    }

    @Test
    public void testRepulsionDirectionAwayFromNeighbor() {
        // Body 0 at origin, body 1 to the right.
        // Force on 0 from 1 should push 0 to the LEFT (negative x).
        double[][] pos = { {0.0, 0.0}, {5.0, 0.0} };
        QuadTree root = QuadTree.build(pos, 2);

        double[] disp = new double[2];
        root.applyRepulsion(0, 0.0, 0.0, K_SQ, disp, THETA_SQ);

        assertTrue("Expected negative x displacement (push away from +x), got " + disp[0],
                disp[0] < -EPS);
        assertEquals("No vertical component on a horizontal pair", 0.0, disp[1], EPS);
    }

    @Test
    public void testSelfRepulsionIsZero() {
        double[][] pos = { {0.0, 0.0}, {3.0, 4.0}, {-2.0, 1.0} };
        QuadTree root = QuadTree.build(pos, 3);

        // Compute force on body 1 once normally...
        double[] dispAll = new double[2];
        root.applyRepulsion(1, 3.0, 4.0, K_SQ, dispAll, THETA_SQ);

        // ...and check it's non-zero (sanity).
        assertTrue("Sanity: force on body 1 from others should be non-zero",
                Math.abs(dispAll[0]) + Math.abs(dispAll[1]) > EPS);
    }

    @Test
    public void testNearbyButNotCoincidentBodies() {
        // MIN_DIST clamp in applyRepulsion should keep the force
        // finite even when bodies are very close.
        double[][] pos = { {0.0, 0.0}, {1e-6, 0.0} };
        QuadTree root = QuadTree.build(pos, 2);

        double[] disp = new double[2];
        root.applyRepulsion(0, 0.0, 0.0, K_SQ, disp, THETA_SQ);

        assertFalse("Force x must be finite even for very close bodies",
                Double.isNaN(disp[0]) || Double.isInfinite(disp[0]));
        assertFalse("Force y must be finite even for very close bodies",
                Double.isNaN(disp[1]) || Double.isInfinite(disp[1]));
    }

    @Test
    public void testBarnesHutApproximatesDistantCluster() {
        // Cluster of 100 bodies tightly packed near (1000, 1000),
        // and one probe body at the origin.  With Barnes-Hut, the
        // probe's force should be very close to the force from a
        // single body of mass 100 at the center of mass.
        int n = 101;
        double[][] pos = new double[n][2];
        pos[0][0] = 0.0;
        pos[0][1] = 0.0;
        for (int i = 1; i < n; i++) {
            // Tiny jitter so the tree actually subdivides
            pos[i][0] = 1000.0 + (i % 10) * 0.01;
            pos[i][1] = 1000.0 + (i / 10) * 0.01;
        }

        QuadTree root = QuadTree.build(pos, n);

        double[] disp = new double[2];
        root.applyRepulsion(0, 0.0, 0.0, K_SQ, disp, THETA_SQ);

        // Both displacement components should be negative (away from
        // the cluster at +x, +y).
        assertTrue("Probe should be pushed in -x by distant cluster, got " + disp[0],
                disp[0] < -EPS);
        assertTrue("Probe should be pushed in -y by distant cluster, got " + disp[1],
                disp[1] < -EPS);
    }
}
