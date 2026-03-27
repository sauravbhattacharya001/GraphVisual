
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
     * Matches event nodes against device records and updates IMEI mappings.
     *
     * <p>For each distinct node in the event result set, scans the device
     * table for a matching node ID and writes the corresponding IMEI back
     * to the event table via the supplied update statement.</p>
     *
     * @param nodeType   human-readable label for logging (e.g. "sndrnode", "srcnode")
     * @param selectStmt prepared SELECT for distinct nodes (parameter 1 = RSSI threshold)
     * @param updateStmt prepared UPDATE to write the matched IMEI
     * @param rsDevice   scrollable device result set (rewound for each event row)
     * @param threshold  minimum RSSI value
     * @throws SQLException if a database operation fails
     */
    private static void matchNodes(String nodeType,
                                   PreparedStatement selectStmt,
                                   PreparedStatement updateStmt,
                                   ResultSet rsDevice,
                                   int threshold) throws SQLException {
        System.out.println("fetching event(" + nodeType + ")...");
        selectStmt.setInt(1, threshold);

        try (ResultSet rsEvent = selectStmt.executeQuery()) {
            rsEvent.last();
            System.out.println("event(" + nodeType + ") fetched..." + rsEvent.getRow() + " entries");
            rsEvent.first();

            int numDone = 0;
            while (rsEvent.next()) {
                numDone++;
                String nodeId = rsEvent.getString(1);
                System.out.println("Done = " + numDone);
                System.out.println("trying match imei with " + nodeType + " " + nodeId);

                rsDevice.first();
                while (rsDevice.next()) {
                    if (rsDevice.getString(1).equals(nodeId)) {
                        String imei = rsDevice.getString(2);
                        System.out.println(imei);
                        System.out.println("matched " + nodeType + " " + nodeId + "\n");

                        updateStmt.setString(1, imei);
                        updateStmt.setString(2, nodeId);
                        updateStmt.executeUpdate();
                        break;
                    }
                }
            }
        }
        System.out.println("all " + nodeType + "s matched\n");
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
            try (ResultSet rs_device = stmt1.executeQuery("SELECT DISTINCT * FROM device_1")) {
                System.out.println("device fetched...");

                matchNodes("sndrnode", selectSndrnode, updateBySndrnode, rs_device, Thres);
                matchNodes("srcnode", selectSrcnode, updateBySrcnode, rs_device, Thres);
            }
        }
    }
}
