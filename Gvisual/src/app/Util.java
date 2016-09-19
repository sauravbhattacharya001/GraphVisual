package app;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author zalenix
 */
import java.sql.*;

public class Util {

    public static Connection getAppConnection() throws Exception {
        Class.forName("org.postgresql.Driver");
        Connection conn = DriverManager.getConnection("jdbc:postgresql:"
                + "//lausanne.nokiaresearch.com/nic_apps", "saurav", "Xee5AiV7");
        System.out.println("Succesfully connected to database \"nic_apps\"");
        return conn;
    }

    public static Connection getAzialaConnection() throws Exception {
        Class.forName("org.postgresql.Driver");
        Connection conn = DriverManager.getConnection("jdbc:postgresql:"
                + "//lausanne.nokiaresearch.com/nic_aziala", "saurav", "Xee5AiV7");
        System.out.println("Succesfully connected to database \"nic_aziala\"");
        return conn;
    }
}
