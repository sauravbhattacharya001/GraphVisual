package app;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 *
 * @author zalenix
 */
public class findMeetings {

    /**
     * defines the time window for which no interaction defines the end of a meeting
     */
    private static double WINDOW_SIZE = 5.00;

    public static float getTimeDifference(String endTime, String startTime) {
        String[] endTimeArr = endTime.split(":");
        String[] startTimeArr = startTime.split(":");


        String[] endTimeArr1 = endTimeArr[0].split("\\.");
        String[] endTimeArr2 = endTimeArr[1].split("\\.");

        String[] startTimeArr1 = startTimeArr[0].split("\\.");
        String[] startTimeArr2 = startTimeArr[0].split("\\.");


        float numMin = (Float.parseFloat(endTimeArr1[0]) - Float.parseFloat(startTimeArr1[0])) * 60 + (Float.parseFloat(endTimeArr1[1]) - Float.parseFloat(startTimeArr1[1])) + (Float.parseFloat(endTimeArr2[0]) - Float.parseFloat(startTimeArr2[0])) / 60;
        return numMin;
    }

    public static String getTimeStamp(String month, String date, String time) {
        String[] timeArr = time.split(":");
        String[] timeArr1 = timeArr[0].split("\\.");
        String[] timeArr2 = timeArr[1].split("\\.");


        String result = "2011-" + month + "-" + date + " " + timeArr1[0] + ":" + timeArr1[1] + ":" + timeArr2[0] + "." + timeArr2[1];
        return result;
    }

    public static void addMeeting(String devicePair, String startTime, String endTime, String month, String date) throws Exception {
        String[] imeiArr = devicePair.split("#");
        String imei1, imei2;
        if (imeiArr[0].compareTo(imeiArr[1]) > 0) {
            imei1 = imeiArr[1];
            imei2 = imeiArr[0];
        } else {
            imei2 = imeiArr[1];
            imei1 = imeiArr[0];
        }
        int ap = 0;
        String query;
        /*Connection azialaConn = Util.getAzialaConnection();
        java.sql.Statement azialaStmt = azialaConn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);

        String startTimeStamp = getTimeStamp(month, date, startTime);
        String endTimeStamp = getTimeStamp(month, date, endTime);

        query = "select * from (select DISTINCT ap,ssi from event as a,trace as b where a.trace = b.id and imei = '" + imei1 + "' and timestamp >= '" + startTimeStamp + "' and timestamp <= '" + endTimeStamp
        + "' intersect "
        + "select DISTINCT ap,ssi from event as a,trace as b where a.trace = b.id and imei = '" + imei2 + "' and timestamp >= '" + startTimeStamp + "' and timestamp <= '" + endTimeStamp+"') as d order by ssi desc limit 1";

        System.out.println(query);
        ResultSet rs = azialaStmt.executeQuery(query);

        if(rs.next()){
        ap = rs.getInt("ap");
        }*/
        String apType = "";


        /*switch (ap) {
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
        }*/



        Connection appConn = Util.getAppConnection();
        java.sql.Statement appStmt = appConn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);

        query = "insert into meeting (imei1,imei2,starttime,endtime,location,month,date,duration) values('" + imei1 + "','" + imei2 + "','" + startTime + "','" + endTime + "','" + apType + "','" + month + "','" + date + "'," + (int) getTimeDifference(endTime, startTime) + ")";
        appStmt.executeUpdate(query);

        appStmt.close();
        appConn.close();
        //azialaStmt.close();
        //azialaConn.close();
    }

    public static void main(String[] args) throws Exception {
        System.out.println("connecting...");

        Connection appConn = Util.getAppConnection();
        java.sql.Statement appStmt = appConn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);




        ResultSet rs;

        int Thres = -60;
        int monthInt = 3;
        int dateInt;

        for (; monthInt <= 5; monthInt++) {
            for (dateInt = 1; dateInt <= 31; dateInt++) {

                String month = Integer.toString(monthInt);
                month = "0" + month;
                String date = Integer.toString(dateInt);
                if (dateInt < 10) {
                    date = "0" + date;
                }


                String query = "SELECT DISTINCT rcvrimei, sndrimei, time  FROM event_3 WHERE month = '" + month + "' AND date = '"
                        + date + "' AND sndrimei != '' AND rssi >= " + Thres;
                //System.out.println(query);
                rs = appStmt.executeQuery(query);


                Map<String, SortedSet<String>> deviceInteraction = new HashMap<String, SortedSet<String>>();

                rs.last();
                System.out.println("fetched " + rs.getRow() + " number of entries....still working...");
                rs.first();
                while (rs.next()) {
                    if (rs.getRow() % 1000 == 0) {
                        System.out.println("added " + rs.getRow() + " number of entries to map");
                    }

                    String imei1, imei2;
                    if (rs.getString(1).compareTo(rs.getString(2)) > 0) {
                        imei1 = rs.getString(2);
                        imei2 = rs.getString(1);
                    } else {
                        imei1 = rs.getString(1);
                        imei2 = rs.getString(2);
                    }

                    String curTime = rs.getString(3);
                    if (curTime.length() == 11) {
                        curTime = "0" + curTime;
                    }

                    String devicePair = imei1 + "#" + imei2;

                    if (deviceInteraction.containsKey(devicePair)) {
                        SortedSet<String> newList = deviceInteraction.get(devicePair);
                        newList.add(curTime);

                        deviceInteraction.remove(devicePair);
                        deviceInteraction.put(devicePair, newList);
                    } else {
                        SortedSet<String> newList = new TreeSet<String>();
                        newList.add(curTime);
                        deviceInteraction.put(devicePair, newList);
                    }
                }

                System.out.println("Hash Map created");

                System.out.println("Total different pairs detected = " + deviceInteraction.size());

                for (String x : deviceInteraction.keySet()) {
                    SortedSet<String> curSet = deviceInteraction.get(x);

                    String meetingStartTime = null;
                    String lastTime = null;

                    for (String y : curSet) {
                        if (lastTime == null) {
                            lastTime = y;
                            meetingStartTime = y;
                        } else if (getTimeDifference(y, lastTime) > WINDOW_SIZE) {
                            addMeeting(x, meetingStartTime, lastTime, month, date);
                            System.out.println("found a meeting and inserted successfully");
                            lastTime = y;
                            meetingStartTime = y;
                        } else {
                            lastTime = y;
                        }
                    }
                }
            }
        }

        appStmt.close();
        appConn.close();
    }
}
