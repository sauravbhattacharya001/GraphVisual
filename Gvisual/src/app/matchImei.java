
package app;

import java.sql.*;

/**
 *
 * @author zalenix
 */
public class matchImei {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception {
        // int month = 1;
        // int day = 2;
        // int hour = 13;

        int Thres = -900;
        String rssiThres = Integer.toString(Thres);

        System.out.println("connecting...");
        Connection conn = Util.getAppConnection();
        Statement stmt1 = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
        Statement stmt2 = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
        Statement stmt3 = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);



        System.out.println("fetching device...");
        ResultSet rs_device = stmt1.executeQuery("SELECT DISTINCT * FROM device_1");
        System.out.println("device fetched...");


        String update;
        int numEventsDone;
        System.out.println("fetching event(sndrnode)...");
        
        String sql = " SELECT DISTINCT sndrnode FROM event_3 WHERE "
                + "sndrnode != '' AND rssi >= " + rssiThres;

        ResultSet rs_event = stmt2.executeQuery(sql);
        rs_event.last();
        System.out.println("event(sndrnode) fetched..." + rs_event.getRow() + " entries");

        rs_event.first();

        numEventsDone=0;
        while (rs_event.next()) {
            numEventsDone++;
            System.out.println("Done = "+numEventsDone);
            System.out.println("trying match imei with sndrnode " + rs_event.getString(1));

            rs_device.first();
            while (rs_device.next()) {
                if (rs_device.getString(1).equals(rs_event.getString(1))) {
                    String imei = rs_device.getString(2);
                    System.out.println(imei);
                    System.out.println("matched sndrnode " + rs_event.getString(1) + "\n");

                    update = "UPDATE event_3 SET sndrimei = '" + imei + "' WHERE sndrnode = '" + rs_event.getString(1)
                            + "'";

                    int rows = stmt3.executeUpdate(update);
                    break;
                }
            }
        }
        System.out.println("all sndrnodes matched\n");






        System.out.println("fetching event(srcnode)...");
        //initial init2 file formation with some rssi threshold
        sql = " SELECT DISTINCT srcnode FROM event_3 WHERE "
                + "sndrnode = '' AND rssi >= " + rssiThres;
        rs_event = stmt2.executeQuery(sql);
        rs_event.last();
        System.out.println("event(srcnode) fetched..." + rs_event.getRow() + " entries");

        rs_event.first();


        numEventsDone=0;
        while (rs_event.next()) {
            numEventsDone++;
            System.out.println("Done = "+numEventsDone);
            System.out.println("trying match imei with srcnode " + rs_event.getString(1));
            rs_device.first();
            while (rs_device.next()) {
                if (rs_device.getString(1).equals(rs_event.getString(1))) {
                    String imei = rs_device.getString(2);
                    System.out.println(imei);
                    System.out.println("matched srcnode " + rs_event.getString(1) + "\n");

                    update = "UPDATE event_3 SET sndrimei = '" + imei + "' WHERE srcnode = '" + rs_event.getString(1)
                            + "' AND sndrnode = ''";

                    int rows = stmt3.executeUpdate(update);
                    break;
                }
            }
        }

        System.out.println("all srcnodes matched\n");

        stmt1.close();
        stmt2.close();
        stmt3.close();
        conn.close();
    }
}
