package app;

/**
 * Database utility — connection factory.
 *
 * Credentials are read from environment variables so that secrets
 * are never committed to version control.
 *
 *   DB_HOST  — database hostname  (default: localhost)
 *   DB_USER  — database user      (required)
 *   DB_PASS  — database password  (required)
 *
 * @author zalenix
 */
import java.sql.*;

public class Util {

    private static final String DEFAULT_HOST = "localhost";

    private static String envOrDefault(String key, String fallback) {
        String val = System.getenv(key);
        return (val != null && !val.isEmpty()) ? val : fallback;
    }

    private static String requireEnv(String key) {
        String val = System.getenv(key);
        if (val == null || val.isEmpty()) {
            throw new IllegalStateException(
                "Required environment variable " + key + " is not set. "
                + "Set DB_HOST, DB_USER, and DB_PASS before running.");
        }
        return val;
    }

    public static Connection getAppConnection() throws Exception {
        String host = envOrDefault("DB_HOST", DEFAULT_HOST);
        String user = requireEnv("DB_USER");
        String pass = requireEnv("DB_PASS");

        Class.forName("org.postgresql.Driver");
        Connection conn = DriverManager.getConnection(
                "jdbc:postgresql://" + host + "/nic_apps", user, pass);
        System.out.println("Successfully connected to database \"nic_apps\"");
        return conn;
    }

    public static Connection getAzialaConnection() throws Exception {
        String host = envOrDefault("DB_HOST", DEFAULT_HOST);
        String user = requireEnv("DB_USER");
        String pass = requireEnv("DB_PASS");

        Class.forName("org.postgresql.Driver");
        Connection conn = DriverManager.getConnection(
                "jdbc:postgresql://" + host + "/nic_aziala", user, pass);
        System.out.println("Successfully connected to database \"nic_aziala\"");
        return conn;
    }
}
