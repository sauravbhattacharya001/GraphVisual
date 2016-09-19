/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package app;

import java.sql.Connection;
import java.sql.ResultSet;

/**
 *
 * @author user
 */
public class addLocation {

    public static String getTimeStamp(String month, String date, String time) {
        String[] timeArr = time.split(":");
        String[] timeArr1 = timeArr[0].split("\\.");
        String[] timeArr2 = timeArr[1].split("\\.");


        String result = "2011-" + month + "-" + date + " " + timeArr1[0] + ":" + timeArr1[1] + ":" + timeArr2[0] + "." + timeArr2[1];
        return result;
    }

    
    public static void main(String[] argv) throws Exception {
        Connection azialaConn = Util.getAzialaConnection();
        java.sql.Statement azialaStmt = azialaConn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);

        Connection appConn = Util.getAppConnection();
        java.sql.Statement appStmt = appConn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
        java.sql.Statement appStmt1 = appConn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);


        String query;
        query = "select * from meeting";
        ResultSet meetings = appStmt.executeQuery(query);

        int count = 0;
        while (meetings.next()) {
            count++;
            System.out.println("finding location for meeting # "+count);
            String imei1 = meetings.getString("imei1");
            String imei2 = meetings.getString("imei2");

            String startTime = meetings.getString("starttime");
            String endTime = meetings.getString("endtime");
            String startTimeStamp = getTimeStamp(meetings.getString("month"), meetings.getString("date"), meetings.getString("starttime"));
            String endTimeStamp = getTimeStamp(meetings.getString("month"), meetings.getString("date"), meetings.getString("endtime"));
        
            query = "select * from (select DISTINCT ap,ssi from event as a,trace as b where a.trace = b.id and imei = '" + imei1 + "' and timestamp >= '" + startTimeStamp + "' and timestamp <= '" + endTimeStamp
                    + "' intersect "
                    + "select DISTINCT ap,ssi from event as a,trace as b where a.trace = b.id and imei = '" + imei2 + "' and timestamp >= '" + startTimeStamp + "' and timestamp <= '" + endTimeStamp + "') as d order by ssi desc limit 1";

            //System.out.println(query);
            ResultSet rs = azialaStmt.executeQuery(query);
            int ap = 0;
            while(rs.next()) ap = rs.getInt("ap");

            if(ap == 0)
            {
                query = "select * from (select DISTINCT ap,ssi from event as a,trace as b where a.trace = b.id and imei = '" + imei1 + "' and timestamp >= '" + startTimeStamp + "' and timestamp <= '" + endTimeStamp+"') as d order by ssi desc limit 1";
                rs = azialaStmt.executeQuery(query);
                while(rs.next()) ap = rs.getInt("ap");
            }
            if(ap == 0)
            {
                query = "select * from (select DISTINCT ap,ssi from event as a,trace as b where a.trace = b.id and imei = '" + imei2 + "' and timestamp >= '" + startTimeStamp + "' and timestamp <= '" + endTimeStamp+"') as d order by ssi desc limit 1";
                rs = azialaStmt.executeQuery(query);
                while(rs.next()) ap = rs.getInt("ap");
            }
            String apType;

            System.out.println("common access point # "+ap);
            switch (ap) {
                case 0:
                    apType = "";
                case 7:
                case 16:
                case 20:
                case 35:
                case 38:
                case 39:
                    apType = "public";
                    break;
                case 29:
                case 30:
                case 31:
                case 32:
                case 33:
                case 34:
                case 36:
                    apType = "class";
                    break;
                default: // all else
                    apType = "path";
                    break;
            }

            query = "update meeting set location = '"+apType+"' where imei1 = '" + imei1 + "' and imei2 = '" + imei2 + "' and starttime = '" + startTime + "' and endtime = '" + endTime + "' and month = '" + meetings.getString("month") + "' and date = '" + meetings.getString("date") + "'";
            appStmt1.executeUpdate(query);
        }

        appStmt.close();
        appStmt1.close();
        appConn.close();
        azialaStmt.close();
        azialaConn.close();
    }
}
