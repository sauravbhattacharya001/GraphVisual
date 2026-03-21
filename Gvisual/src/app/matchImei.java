
package app;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps Bluetooth node identifiers to IMEI device identifiers in trace data.
 *
 * <p>Processes the {@code event_3} table to resolve sender/source node IDs
 * to their corresponding IMEI numbers using a two-pass approach: first
 * matching by {@code sndrnode}, then by {@code srcnode} for records where
 * the sender node is empty. Only considers records with RSSI above a
 * configurable threshold to filter out noise from distant devices.</p>
 *
 * <p><b>Performance:</b> Device lookups use an in-memory {@link HashMap}
 * (O(1) per lookup) instead of the previous nested ResultSet scan
 * (O(devices) per event row), reducing overall complexity from
 * O(events × devices) to O(events + devices).</p>
 *
 * @author zalenix
 */
public class matchImei {

    // SQL for updating sndrimei by sndrnode
    private static final String UPDATE_BY_SNDRNODE_SQL =
            "UPDATE event_3 SET sndrimei = ? WHERE sndrnode = ?";

    // SQL for updating sndrimei by srcnode (only where sndrnode is empty)
    private static final String UPDATE_BY_SRCNODE_SQL =
            "UPDATE event_3 SET sndrimei = ? WHERE srcnode = ? AND sndrnode = ''";

    // SQL for selecting distinct sndrnodes above RSSI threshold
    private static final String SELECT_SNDRNODE_SQL =
            "SELECT DISTINCT sndrnode FROM event_3 WHERE sndrnode != '' AND rssi >= ?";

    // SQL for selecting distinct srcnodes above RSSI threshold (where sndrnode is empty)
    private static final String SELECT_SRCNODE_SQL =
            "SELECT DISTINCT srcnode FROM event_3 WHERE sndrnode = '' AND rssi >= ?";

    /**
     * Loads the device_1 table into a HashMap for O(1) node→IMEI lookups.
     *
     * @param conn database connection
     * @return map from node identifier to IMEI string
     */
    private static Map<String, String> loadDeviceMap(Connection conn) throws SQLException {
        Map<String, String> deviceMap = new HashMap<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT DISTINCT * FROM device_1")) {
            while (rs.next()) {
                String node = rs.getString(1);
                String imei = rs.getString(2);
                if (node != null && imei != null) {
                    deviceMap.put(node, imei);
                }
            }
        }
        System.out.println("device map loaded: " + deviceMap.size() + " entries");
        return deviceMap;
    }

    /**
     * Matches event nodes to IMEIs and batch-updates the database.
     *
     * @param deviceMap  node→IMEI lookup map
     * @param selectStmt prepared statement to fetch distinct nodes
     * @param updateStmt prepared statement to update sndrimei
     * @param nodeType   label for logging ("sndrnode" or "srcnode")
     * @param batchSize  number of updates to accumulate before flushing
     */
    private static void matchAndUpdate(Map<String, String> deviceMap,
                                       PreparedStatement selectStmt,
                                       PreparedStatement updateStmt,
                                       String nodeType,
                                       int batchSize) throws SQLException {
        try (ResultSet rs = selectStmt.executeQuery()) {
            int matched = 0;
            int unmatched = 0;
            int batchCount = 0;

            while (rs.next()) {
                String node = rs.getString(1);
                String imei = deviceMap.get(node);

                if (imei != null) {
                    matched++;
                    updateStmt.setString(1, imei);
                    updateStmt.setString(2, node);
                    updateStmt.addBatch();
                    batchCount++;

                    if (batchCount >= batchSize) {
                        updateStmt.executeBatch();
                        System.out.println("  flushed batch (" + matched + " matched so far)");
                        batchCount = 0;
                    }
                } else {
                    unmatched++;
                }
            }

            // Flush remaining
            if (batchCount > 0) {
                updateStmt.executeBatch();
            }

            System.out.println("all " + nodeType + " matched: "
                    + matched + " resolved, " + unmatched + " unmatched");
        }
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception {
        int Thres = -900;
        int BATCH_SIZE = 500;

        System.out.println("connecting...");

        try (Connection conn = Util.getAppConnection()) {
            // Disable auto-commit for batch performance
            boolean origAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try {
                // Load device table into memory once — O(devices)
                Map<String, String> deviceMap = loadDeviceMap(conn);

                try (PreparedStatement updateBySndrnode = conn.prepareStatement(UPDATE_BY_SNDRNODE_SQL);
                     PreparedStatement updateBySrcnode = conn.prepareStatement(UPDATE_BY_SRCNODE_SQL);
                     PreparedStatement selectSndrnode = conn.prepareStatement(SELECT_SNDRNODE_SQL);
                     PreparedStatement selectSrcnode = conn.prepareStatement(SELECT_SRCNODE_SQL)) {

                    // --- Match by sndrnode ---
                    System.out.println("matching by sndrnode...");
                    selectSndrnode.setInt(1, Thres);
                    matchAndUpdate(deviceMap, selectSndrnode, updateBySndrnode, "sndrnode", BATCH_SIZE);

                    // --- Match by srcnode ---
                    System.out.println("matching by srcnode...");
                    selectSrcnode.setInt(1, Thres);
                    matchAndUpdate(deviceMap, selectSrcnode, updateBySrcnode, "srcnode", BATCH_SIZE);
                }

                conn.commit();
                System.out.println("all updates committed successfully");
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(origAutoCommit);
            }
        }
    }
}
