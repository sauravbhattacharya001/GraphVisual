package app;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 *
 *
 * @author zalenix
 */
public class Network {

    /**
     *
     * Connects to database and writes out the edge-list from the meeting DB table, forming edges of kind:
     * friends, classmates, study-groups, strangers and familiar strangers (depending upon parameters).
     *
     * @param path
     * @param Month
     * @param Date
     * @param dThresF
     * @param CThresF
     * @param dThresFS
     * @param CThresFS
     * @param dThresC
     * @param CThresC
     * @param dThresS
     * @param CThresS
     * @param dThresSg
     * @param CThresSg
     * @throws Exception
     */
    public static void generateFile(String path, String Month, String Date, int dThresF, int CThresF, int dThresFS, int CThresFS, int dThresC, int CThresC, int dThresS, int CThresS, int dThresSg, int CThresSg) throws Exception {

        System.out.println("connecting...");

        Connection conn = Util.getAppConnection();

        // Parameterized query template for location-based meeting queries.
        // Parameters: month, date, location, duration threshold, count threshold (with >= or < or <=).
        // The integer thresholds are safe from injection but parameterized for consistency.

        // --- Friends query ---
        String friendSql = " SELECT x.id , y.id , C , d  "
                + " FROM ( SELECT imei1 , imei2, count(*) as C,avg(duration) as d"
                + "       FROM ( SELECT imei1, imei2, duration"
                + "              FROM meeting"
                + "              WHERE month = ? AND date = ? AND location= 'public' AND duration > ?) as b"
                + "       GROUP BY imei1, imei2) as a, deviceID as x, deviceID as y"
                + " WHERE C >= ? AND a.imei1= x.imei AND a.imei2 = y.imei";

        PreparedStatement psFriend = conn.prepareStatement(friendSql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
        psFriend.setString(1, Month);
        psFriend.setString(2, Date);
        psFriend.setInt(3, dThresF);
        psFriend.setInt(4, CThresF);
        ResultSet rs_friend = psFriend.executeQuery();

        // --- Study groups query ---
        String studygSql = " SELECT x.id , y.id , C , d   "
                + " FROM ( SELECT imei1, imei2, count(*) as C,avg(duration) as d"
                + "       FROM ( SELECT imei1, imei2, duration"
                + "              FROM meeting"
                + "              WHERE month = ? AND date = ? AND location= 'class' AND duration > ?) as b"
                + "       GROUP BY imei1, imei2) as a, deviceID as x, deviceID as y"
                + " WHERE C <= ? AND a.imei1= x.imei AND a.imei2 = y.imei";

        PreparedStatement psStudyg = conn.prepareStatement(studygSql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
        psStudyg.setString(1, Month);
        psStudyg.setString(2, Date);
        psStudyg.setInt(3, dThresSg);
        psStudyg.setInt(4, CThresSg);
        ResultSet rs_studyg = psStudyg.executeQuery();

        // --- Classmates query ---
        String cmateSql = " SELECT x.id , y.id, C , d  "
                + " FROM ( SELECT imei1, imei2, count(*) as C, avg(duration) as d"
                + "       FROM ( SELECT imei1, imei2, duration"
                + "              FROM meeting"
                + "              WHERE month = ? AND date = ? AND location= 'class' AND duration > ?) as b"
                + "       GROUP BY imei1, imei2) as a, deviceID as x, deviceID as y"
                + " WHERE C >= ? AND a.imei1= x.imei AND a.imei2 = y.imei";

        PreparedStatement psCmate = conn.prepareStatement(cmateSql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
        psCmate.setString(1, Month);
        psCmate.setString(2, Date);
        psCmate.setInt(3, dThresC);
        psCmate.setInt(4, CThresC);
        ResultSet rs_cmate = psCmate.executeQuery();

        // --- Strangers query ---
        String strangerSql = " SELECT x.id , y.id , C , d "
                + " FROM ( SELECT imei1, imei2, count(*) as C,avg(duration) as d"
                + "       FROM ( SELECT imei1, imei2, duration"
                + "              FROM meeting"
                + "              WHERE month = ? AND date = ? AND location != 'class' AND duration < ?) as b"
                + "       GROUP BY imei1, imei2) as a, deviceID as x, deviceID as y"
                + " WHERE C < ? AND a.imei1= x.imei AND a.imei2 = y.imei";

        PreparedStatement psStranger = conn.prepareStatement(strangerSql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
        psStranger.setString(1, Month);
        psStranger.setString(2, Date);
        psStranger.setInt(3, dThresS);
        psStranger.setInt(4, CThresS);
        ResultSet rs_stranger = psStranger.executeQuery();

        // --- Familiar strangers query ---
        String famstrangerSql = " SELECT x.id , y.id , C , d  "
                + " FROM ( SELECT imei1, imei2, count(*) as C, avg(duration) as d"
                + "       FROM ( SELECT imei1, imei2, duration"
                + "              FROM meeting"
                + "              WHERE month = ? AND date = ? AND location != 'class' AND duration < ?) as b"
                + "       GROUP BY imei1, imei2) as a , deviceID as x, deviceID as y"
                + " WHERE C > ? AND a.imei1= x.imei AND a.imei2 = y.imei";

        PreparedStatement psFamstranger = conn.prepareStatement(famstrangerSql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
        psFamstranger.setString(1, Month);
        psFamstranger.setString(2, Date);
        psFamstranger.setInt(3, dThresFS);
        psFamstranger.setInt(4, CThresFS);
        ResultSet rs_famstranger = psFamstranger.executeQuery();

        // --- Device query (no user input, safe as-is) ---
        PreparedStatement psDevice = conn.prepareStatement("SELECT DISTINCT imei FROM device_1", ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
        ResultSet rs_device = psDevice.executeQuery();


        String imei1, imei2;
        int times;
        float duration;
        double weight;

        File f = new File(path);
        if (f.exists()) {
            f.delete();
        }

        f = new File(path);


        /**
         * Nodes
         */
        BufferedWriter out = new BufferedWriter(new FileWriter(f, true));
        String str = "edges";



        /*
         * Friend
         */
        rs_friend.first();
        while (rs_friend.next()) {

            imei1 = rs_friend.getString(1);
            imei2 = rs_friend.getString(2);
            times = rs_friend.getInt(3);
            duration = rs_friend.getFloat(4);
            weight=times*duration;

            str = str + "\nf " + imei1 + " " + imei2 +" "+ weight;

        }


        /*
         * Classmates
         */
        rs_cmate.first();
        while (rs_cmate.next()) {

            imei1 = rs_cmate.getString(1);
            imei2 = rs_cmate.getString(2);
            times = rs_cmate.getInt(3);
            duration = rs_cmate.getFloat(4);
            weight=times*duration;

            str = str + "\nc " + imei1 + " " + imei2 +" "+ weight;

        }

        /*
         * Familiar Stranger
         */
        rs_famstranger.first();
        while (rs_famstranger.next()) {

            imei1 = rs_famstranger.getString(1);
            imei2 = rs_famstranger.getString(2);
            times = rs_famstranger.getInt(3);
            duration = rs_famstranger.getFloat(4);
            weight=times*duration;

            str = str + "\nfs " + imei1 + " " + imei2 +" "+ weight;

        }


        /*
         *
         * Study Groups
         *
         *
         */
        rs_studyg.first();
        while (rs_studyg.next()) {

            imei1 = rs_studyg.getString(1);
            imei2 = rs_studyg.getString(2);
            times = rs_studyg.getInt(3);
            duration = rs_studyg.getFloat(4);
            weight=times*duration;

            str = str + "\nsg " + imei1 + " " + imei2 +" "+ weight;

        }


        /*
         *
         * Stranger
         *
         */
        rs_stranger.first();
        while (rs_stranger.next()) {

            imei1 = rs_stranger.getString(1);
            imei2 = rs_stranger.getString(2);
            times = rs_stranger.getInt(3);
            duration = rs_stranger.getFloat(4);
            weight=times*duration;

            str = str + "\ns " + imei1 + " " + imei2 +" "+ weight;

        }

        out.write(str);
        out.flush();
        out.close();

        // Close all prepared statements
        psFriend.close();
        psStudyg.close();
        psCmate.close();
        psStranger.close();
        psFamstranger.close();
        psDevice.close();
    }
}
