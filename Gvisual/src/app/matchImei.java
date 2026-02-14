
package app;

import java.sql.*;

/**
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

                // --- Match by sndrnode ---
                int numEventsDone;
                System.out.println("fetching event(sndrnode)...");

                selectSndrnode.setInt(1, Thres);
                try (ResultSet rs_event = selectSndrnode.executeQuery()) {
                    rs_event.last();
                    System.out.println("event(sndrnode) fetched..." + rs_event.getRow() + " entries");
                    rs_event.first();

                    numEventsDone = 0;
                    while (rs_event.next()) {
                        numEventsDone++;
                        System.out.println("Done = " + numEventsDone);
                        System.out.println("trying match imei with sndrnode " + rs_event.getString(1));

                        rs_device.first();
                        while (rs_device.next()) {
                            if (rs_device.getString(1).equals(rs_event.getString(1))) {
                                String imei = rs_device.getString(2);
                                String sndrnode = rs_event.getString(1);
                                System.out.println(imei);
                                System.out.println("matched sndrnode " + sndrnode + "\n");

                                updateBySndrnode.setString(1, imei);
                                updateBySndrnode.setString(2, sndrnode);
                                updateBySndrnode.executeUpdate();
                                break;
                            }
                        }
                    }
                }
                System.out.println("all sndrnodes matched\n");

                // --- Match by srcnode ---
                System.out.println("fetching event(srcnode)...");

                selectSrcnode.setInt(1, Thres);
                try (ResultSet rs_event = selectSrcnode.executeQuery()) {
                    rs_event.last();
                    System.out.println("event(srcnode) fetched..." + rs_event.getRow() + " entries");
                    rs_event.first();

                    numEventsDone = 0;
                    while (rs_event.next()) {
                        numEventsDone++;
                        System.out.println("Done = " + numEventsDone);
                        System.out.println("trying match imei with srcnode " + rs_event.getString(1));

                        rs_device.first();
                        while (rs_device.next()) {
                            if (rs_device.getString(1).equals(rs_event.getString(1))) {
                                String imei = rs_device.getString(2);
                                String srcnode = rs_event.getString(1);
                                System.out.println(imei);
                                System.out.println("matched srcnode " + srcnode + "\n");

                                updateBySrcnode.setString(1, imei);
                                updateBySrcnode.setString(2, srcnode);
                                updateBySrcnode.executeUpdate();
                                break;
                            }
                        }
                    }
                }
                System.out.println("all srcnodes matched\n");
            }
        }
    }
}
