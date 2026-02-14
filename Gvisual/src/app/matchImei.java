
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

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception {
        int Thres = -900;
        String rssiThres = Integer.toString(Thres);

        System.out.println("connecting...");
        Connection conn = Util.getAppConnection();
        Statement stmt1 = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
        Statement stmt2 = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);

        // Prepared statements for parameterized updates (fixes SQL injection — issue #7)
        PreparedStatement updateBySndrnode = conn.prepareStatement(UPDATE_BY_SNDRNODE_SQL);
        PreparedStatement updateBySrcnode = conn.prepareStatement(UPDATE_BY_SRCNODE_SQL);

        System.out.println("fetching device...");
        ResultSet rs_device = stmt1.executeQuery("SELECT DISTINCT * FROM device_1");
        System.out.println("device fetched...");

        int numEventsDone;
        System.out.println("fetching event(sndrnode)...");

        String sql = " SELECT DISTINCT sndrnode FROM event_3 WHERE "
                + "sndrnode != '' AND rssi >= " + rssiThres;

        ResultSet rs_event = stmt2.executeQuery(sql);
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
        System.out.println("all sndrnodes matched\n");

        System.out.println("fetching event(srcnode)...");
        sql = " SELECT DISTINCT srcnode FROM event_3 WHERE "
                + "sndrnode = '' AND rssi >= " + rssiThres;
        rs_event = stmt2.executeQuery(sql);
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

        System.out.println("all srcnodes matched\n");

        updateBySndrnode.close();
        updateBySrcnode.close();
        stmt1.close();
        stmt2.close();
        conn.close();
    }
}
