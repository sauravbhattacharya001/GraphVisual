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

    /**
     * Pattern for a safe hostname: alphanumeric, dots, hyphens, and optional
     * port (e.g. "db.example.com", "192.168.1.5:5432").  Rejects any JDBC
     * parameter injection characters (/, ?, &amp;, =).
     */
    private static final java.util.regex.Pattern SAFE_HOST =
        java.util.regex.Pattern.compile("^[a-zA-Z0-9._-]+(:[0-9]{1,5})?$");

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

    /**
     * Validates that a hostname string is safe for use in a JDBC URL.
     * Prevents JDBC connection string injection via characters like
     * /, ?, &amp;, or = that could add arbitrary driver parameters.
     *
     * @param host the hostname to validate
     * @return the validated hostname
     * @throws IllegalStateException if the hostname contains unsafe characters
     */
    private static String validateHost(String host) {
        if (!SAFE_HOST.matcher(host).matches()) {
            throw new IllegalStateException(
                "DB_HOST contains invalid characters: " + host
                + ". Expected hostname[:port] (e.g. localhost, db.example.com:5432).");
        }
        return host;
    }

    public static Connection getAppConnection() throws Exception {
        String host = validateHost(envOrDefault("DB_HOST", DEFAULT_HOST));
        String user = requireEnv("DB_USER");
        String pass = requireEnv("DB_PASS");

        Class.forName("org.postgresql.Driver");
        Connection conn = DriverManager.getConnection(
                "jdbc:postgresql://" + host + "/nic_apps", user, pass);
        System.out.println("Successfully connected to database \"nic_apps\"");
        return conn;
    }

    public static Connection getAzialaConnection() throws Exception {
        String host = validateHost(envOrDefault("DB_HOST", DEFAULT_HOST));
        String user = requireEnv("DB_USER");
        String pass = requireEnv("DB_PASS");

        Class.forName("org.postgresql.Driver");
        Connection conn = DriverManager.getConnection(
                "jdbc:postgresql://" + host + "/nic_aziala", user, pass);
        System.out.println("Successfully connected to database \"nic_aziala\"");
        return conn;
    }
}
