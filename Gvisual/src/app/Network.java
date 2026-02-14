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

        // Parameterized query template for location-based meeting queries.
        // Parameters: month, date, location, duration threshold, count threshold.

        // --- Friends query ---
        String friendSql = " SELECT x.id , y.id , C , d  "
                + " FROM ( SELECT imei1 , imei2, count(*) as C,avg(duration) as d"
                + "       FROM ( SELECT imei1, imei2, duration"
                + "              FROM meeting"
                + "              WHERE month = ? AND date = ? AND location= 'public' AND duration > ?) as b"
                + "       GROUP BY imei1, imei2) as a, deviceID as x, deviceID as y"
                + " WHERE C >= ? AND a.imei1= x.imei AND a.imei2 = y.imei";

        // --- Study groups query ---
        String studygSql = " SELECT x.id , y.id , C , d   "
                + " FROM ( SELECT imei1, imei2, count(*) as C,avg(duration) as d"
                + "       FROM ( SELECT imei1, imei2, duration"
                + "              FROM meeting"
                + "              WHERE month = ? AND date = ? AND location= 'class' AND duration > ?) as b"
                + "       GROUP BY imei1, imei2) as a, deviceID as x, deviceID as y"
                + " WHERE C <= ? AND a.imei1= x.imei AND a.imei2 = y.imei";

        // --- Classmates query ---
        String cmateSql = " SELECT x.id , y.id, C , d  "
                + " FROM ( SELECT imei1, imei2, count(*) as C, avg(duration) as d"
                + "       FROM ( SELECT imei1, imei2, duration"
                + "              FROM meeting"
                + "              WHERE month = ? AND date = ? AND location= 'class' AND duration > ?) as b"
                + "       GROUP BY imei1, imei2) as a, deviceID as x, deviceID as y"
                + " WHERE C >= ? AND a.imei1= x.imei AND a.imei2 = y.imei";

        // --- Strangers query ---
        String strangerSql = " SELECT x.id , y.id , C , d "
                + " FROM ( SELECT imei1, imei2, count(*) as C,avg(duration) as d"
                + "       FROM ( SELECT imei1, imei2, duration"
                + "              FROM meeting"
                + "              WHERE month = ? AND date = ? AND location != 'class' AND duration < ?) as b"
                + "       GROUP BY imei1, imei2) as a, deviceID as x, deviceID as y"
                + " WHERE C < ? AND a.imei1= x.imei AND a.imei2 = y.imei";

        // --- Familiar strangers query ---
        String famstrangerSql = " SELECT x.id , y.id , C , d  "
                + " FROM ( SELECT imei1, imei2, count(*) as C, avg(duration) as d"
                + "       FROM ( SELECT imei1, imei2, duration"
                + "              FROM meeting"
                + "              WHERE month = ? AND date = ? AND location != 'class' AND duration < ?) as b"
                + "       GROUP BY imei1, imei2) as a , deviceID as x, deviceID as y"
                + " WHERE C > ? AND a.imei1= x.imei AND a.imei2 = y.imei";

        try (Connection conn = Util.getAppConnection()) {

            // Use StringBuilder instead of String concatenation for performance
            StringBuilder sb = new StringBuilder("edges");

            // --- Friends ---
            try (PreparedStatement psFriend = conn.prepareStatement(friendSql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY)) {
                psFriend.setString(1, Month);
                psFriend.setString(2, Date);
                psFriend.setInt(3, dThresF);
                psFriend.setInt(4, CThresF);
                try (ResultSet rs = psFriend.executeQuery()) {
                    while (rs.next()) {
                        double weight = rs.getInt(3) * (double) rs.getFloat(4);
                        sb.append("\nf ").append(rs.getString(1)).append(" ")
                          .append(rs.getString(2)).append(" ").append(weight);
                    }
                }
            }

            // --- Study groups ---
            try (PreparedStatement psStudyg = conn.prepareStatement(studygSql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY)) {
                psStudyg.setString(1, Month);
                psStudyg.setString(2, Date);
                psStudyg.setInt(3, dThresSg);
                psStudyg.setInt(4, CThresSg);
                try (ResultSet rs = psStudyg.executeQuery()) {
                    while (rs.next()) {
                        double weight = rs.getInt(3) * (double) rs.getFloat(4);
                        sb.append("\nsg ").append(rs.getString(1)).append(" ")
                          .append(rs.getString(2)).append(" ").append(weight);
                    }
                }
            }

            // --- Classmates ---
            try (PreparedStatement psCmate = conn.prepareStatement(cmateSql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY)) {
                psCmate.setString(1, Month);
                psCmate.setString(2, Date);
                psCmate.setInt(3, dThresC);
                psCmate.setInt(4, CThresC);
                try (ResultSet rs = psCmate.executeQuery()) {
                    while (rs.next()) {
                        double weight = rs.getInt(3) * (double) rs.getFloat(4);
                        sb.append("\nc ").append(rs.getString(1)).append(" ")
                          .append(rs.getString(2)).append(" ").append(weight);
                    }
                }
            }

            // --- Strangers ---
            try (PreparedStatement psStranger = conn.prepareStatement(strangerSql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY)) {
                psStranger.setString(1, Month);
                psStranger.setString(2, Date);
                psStranger.setInt(3, dThresS);
                psStranger.setInt(4, CThresS);
                try (ResultSet rs = psStranger.executeQuery()) {
                    while (rs.next()) {
                        double weight = rs.getInt(3) * (double) rs.getFloat(4);
                        sb.append("\ns ").append(rs.getString(1)).append(" ")
                          .append(rs.getString(2)).append(" ").append(weight);
                    }
                }
            }

            // --- Familiar strangers ---
            try (PreparedStatement psFamstranger = conn.prepareStatement(famstrangerSql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY)) {
                psFamstranger.setString(1, Month);
                psFamstranger.setString(2, Date);
                psFamstranger.setInt(3, dThresFS);
                psFamstranger.setInt(4, CThresFS);
                try (ResultSet rs = psFamstranger.executeQuery()) {
                    while (rs.next()) {
                        double weight = rs.getInt(3) * (double) rs.getFloat(4);
                        sb.append("\nfs ").append(rs.getString(1)).append(" ")
                          .append(rs.getString(2)).append(" ").append(weight);
                    }
                }
            }

            // Write output file
            File f = new File(path);
            if (f.exists()) {
                f.delete();
            }
            try (BufferedWriter out = new BufferedWriter(new FileWriter(f))) {
                out.write(sb.toString());
            }
        }
    }
}
