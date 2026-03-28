
package app;

import java.sql.*;

/**
 * Maps Bluetooth node identifiers to IMEI device identifiers in trace data.
 *
 * <p>Processes the {@code event_3} table to resolve sender/source node IDs
 * to their corresponding IMEI numbers using a two-pass approach: first
 * matching by {@code sndrnode}, then by {@code srcnode} for records where
 * the sender node is empty. Only considers records with RSSI above a
 * configurable threshold to filter out noise from distant devices.</p>
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
     * Matches event nodes against a pre-built device lookup map and updates
     * IMEI mappings via batch UPDATE.
     *
     * <p>Previous implementation re-scanned the device ResultSet (O(D)) for
     * every event row, giving O(N×D) total work. This version uses a
     * HashMap for O(1) lookups per event row, reducing the match phase to
     * O(N+D). Updates are batched for reduced round-trip overhead.</p>
     *
     * @param nodeType   human-readable label for logging (e.g. "sndrnode", "srcnode")
     * @param selectStmt prepared SELECT for distinct nodes (parameter 1 = RSSI threshold)
     * @param updateStmt prepared UPDATE to write the matched IMEI
     * @param deviceMap  pre-built map from node ID to IMEI
     * @param threshold  minimum RSSI value
     * @throws SQLException if a database operation fails
     */
    private static void matchNodes(String nodeType,
                                   PreparedStatement selectStmt,
                                   PreparedStatement updateStmt,
                                   java.util.Map<String, String> deviceMap,
                                   int threshold) throws SQLException {
        System.out.println("fetching event(" + nodeType + ")...");
        selectStmt.setInt(1, threshold);

        try (ResultSet rsEvent = selectStmt.executeQuery()) {
            int numDone = 0;
            int matched = 0;
            while (rsEvent.next()) {
                numDone++;
                String nodeId = rsEvent.getString(1);
                String imei = deviceMap.get(nodeId);

                if (imei != null) {
                    matched++;
                    System.out.println("matched " + nodeType + " " + nodeId + " -> " + imei);
                    updateStmt.setString(1, imei);
                    updateStmt.setString(2, nodeId);
                    updateStmt.addBatch();
                }
            }
            if (matched > 0) {
                updateStmt.executeBatch();
            }
            System.out.println(nodeType + ": " + matched + "/" + numDone + " matched");
        }
        System.out.println("all " + nodeType + "s matched\n");
    }

    /**
     * Builds a node-ID to IMEI lookup map from a device ResultSet.
     *
     * @param rsDevice scrollable device result set with columns (nodeId, imei)
     * @return map from node ID to IMEI
     * @throws SQLException if a database operation fails
     */
    private static java.util.Map<String, String> buildDeviceMap(
            ResultSet rsDevice) throws SQLException {
        java.util.Map<String, String> map = new java.util.HashMap<>();
        while (rsDevice.next()) {
            String nodeId = rsDevice.getString(1);
            String imei = rsDevice.getString(2);
            map.put(nodeId, imei);
        }
        System.out.println("device map built: " + map.size() + " entries");
        return map;
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception {
        int Thres = -900;

        System.out.println("connecting...");

        try (Connection conn = Util.getAppConnection();
             Statement stmt1 = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
             PreparedStatement updateBySndrnode = conn.prepareStatement(UPDATE_BY_SNDRNODE_SQL);
             PreparedStatement updateBySrcnode = conn.prepareStatement(UPDATE_BY_SRCNODE_SQL);
             PreparedStatement selectSndrnode = conn.prepareStatement(SELECT_SNDRNODE_SQL,
                     ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
             PreparedStatement selectSrcnode = conn.prepareStatement(SELECT_SRCNODE_SQL,
                     ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY)) {

            System.out.println("fetching device...");
            java.util.Map<String, String> deviceMap;
            try (ResultSet rs_device = stmt1.executeQuery("SELECT DISTINCT * FROM device_1")) {
                deviceMap = buildDeviceMap(rs_device);
            }

            matchNodes("sndrnode", selectSndrnode, updateBySndrnode, deviceMap, Thres);
            matchNodes("srcnode", selectSrcnode, updateBySrcnode, deviceMap, Thres);
        }
    }
}
