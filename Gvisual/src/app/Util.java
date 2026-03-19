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

    /**
     * Opens a JDBC connection to the specified PostgreSQL database.
     *
     * <p>Credentials and host are read from environment variables
     * ({@code DB_HOST}, {@code DB_USER}, {@code DB_PASS}).
     *
     * @param database the database name to connect to
     * @return an open {@link Connection}
     * @throws Exception if the driver cannot be loaded or the connection fails
     */
    private static Connection getConnection(String database) throws Exception {
        if (database == null || database.isEmpty()) {
            throw new IllegalArgumentException("database name must not be null or empty");
        }
        // Reject database names that could inject JDBC parameters
        if (!database.matches("[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException(
                "database name contains invalid characters: " + database
                + ". Only alphanumerics and underscores are allowed.");
        }

        String host = validateHost(envOrDefault("DB_HOST", DEFAULT_HOST));
        String user = requireEnv("DB_USER");
        String pass = requireEnv("DB_PASS");

        Class.forName("org.postgresql.Driver");
        Connection conn = DriverManager.getConnection(
                "jdbc:postgresql://" + host + "/" + database, user, pass);
        System.out.println("Successfully connected to database \"" + database + "\"");
        return conn;
    }

    public static Connection getAppConnection() throws Exception {
        return getConnection("nic_apps");
    }

    public static Connection getAzialaConnection() throws Exception {
        return getConnection("nic_aziala");
    }
}
