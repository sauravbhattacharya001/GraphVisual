package app;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

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
        Statement stmt1 = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
        Statement stmt2 = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
        Statement stmt3 = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
        Statement stmt4 = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
        Statement stmt5 = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
        Statement stmt6 = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);


        ResultSet rs_friend;
        ResultSet rs_cmate;
        ResultSet rs_stranger;
        ResultSet rs_famstranger;
        ResultSet rs_studyg;
        ResultSet rs_device;


        String imei1, imei2;
        int times;
        float duration;
        double weight;

        String query = " SELECT x.id , y.id , C , d  "
                + " FROM ( SELECT imei1 , imei2, count(*) as C,avg(duration) as d"
                + "       FROM ( SELECT imei1, imei2, duration"
                + "              FROM meeting"
                + "              WHERE month = '" + Month + "' AND date = '" + Date + "' AND location= 'public' AND duration >" + dThresF + ") as b"
                + "       GROUP BY imei1, imei2) as a, deviceID as x, deviceID as y"
                + " WHERE C >= " + CThresF + " AND a.imei1= x.imei AND a.imei2 = y.imei";
        //System.out.println(query);
        rs_friend = stmt1.executeQuery(query);

        rs_studyg = stmt5.executeQuery(" SELECT x.id , y.id , C , d   "
                + " FROM ( SELECT imei1, imei2, count(*) as C,avg(duration) as d"
                + "       FROM ( SELECT imei1, imei2, duration"
                + "              FROM meeting"
                + "              WHERE month = '" + Month + "' AND date = '" + Date + "' AND location= 'class' AND duration >" + dThresSg + ") as b"
                + "       GROUP BY imei1, imei2) as a, deviceID as x, deviceID as y"
                + " WHERE C <= " + CThresSg + " AND a.imei1= x.imei AND a.imei2 = y.imei");

        rs_cmate = stmt2.executeQuery(" SELECT x.id , y.id, C , d  "
                + " FROM ( SELECT imei1, imei2, count(*) as C, avg(duration) as d"
                + "       FROM ( SELECT imei1, imei2, duration"
                + "              FROM meeting"
                + "              WHERE month = '" + Month + "' AND date = '" + Date + "' AND location= 'class' AND duration >" + dThresC + ") as b"
                + "       GROUP BY imei1, imei2) as a, deviceID as x, deviceID as y"
                + " WHERE C >= " + CThresC + " AND a.imei1= x.imei AND a.imei2 = y.imei");


        rs_stranger = stmt3.executeQuery(" SELECT x.id , y.id , C , d "
                + " FROM ( SELECT imei1, imei2, count(*) as C,avg(duration) as d"
                + "       FROM ( SELECT imei1, imei2, duration"
                + "              FROM meeting"
                + "              WHERE month = '" + Month + "' AND date = '" + Date + "' AND location!= 'class' AND duration <" + dThresS + ") as b"
                + "       GROUP BY imei1, imei2) as a, deviceID as x, deviceID as y"
                + " WHERE C < " + CThresS + " AND a.imei1= x.imei AND a.imei2 = y.imei");

        rs_famstranger = stmt4.executeQuery(" SELECT x.id , y.id , C , d  "
                + " FROM ( SELECT imei1, imei2, count(*) as C, avg(duration) as d"
                + "       FROM ( SELECT imei1, imei2, duration"
                + "              FROM meeting"
                + "              WHERE month = '" + Month + "' AND date = '" + Date + "' AND location!= 'class' AND duration <" + dThresFS + ") as b"
                + "       GROUP BY imei1, imei2) as a , deviceID as x, deviceID as y"
                + " WHERE C > " + CThresFS + " AND a.imei1= x.imei AND a.imei2 = y.imei");


        rs_device = stmt6.executeQuery("SELECT DISTINCT imei FROM device_1");



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
    }
}
