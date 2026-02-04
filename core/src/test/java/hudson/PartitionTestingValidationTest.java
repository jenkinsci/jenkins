
package hudson;

import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.util.FormValidation;
import hudson.util.FormValidation.Kind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Partition Testing for Jenkins Validation Methods
 *
 * This test class demonstrates equivalence partitioning and boundary value testing
 * for two Jenkins input validation features:
 *
 * Feature 1: Port Number Validation (valid range: 0-65535)
 * Feature 2: Thread Count Validation (valid range: 5-100)
 */
class PartitionTestingValidationTest {

    // FEATURE 1: PORT NUMBER VALIDATION
    //
    // Tests the doCheckPort() method in ProxyConfiguration
    // Valid port range: 0 to 65535
    //
    // Partitions:
    //   P1: Empty/null input     -> Valid (OK)
    //   P2: Non-numeric string   -> Invalid (Error)
    //   P3: Negative numbers     -> Invalid (Error)
    //   P4: Valid range 0-65535  -> Valid (OK)
    //   P5: Above 65535          -> Invalid (Error)

    private final ProxyConfiguration.DescriptorImpl portValidator =
            new ProxyConfiguration.DescriptorImpl();

    //Partition P1: Empty/Null Input (Valid)

    @Test
    @DisplayName("Port P1: Empty string is valid")
    void port_emptyString_isValid() {
        FormValidation result = portValidator.doCheckPort("");
        assertEquals(Kind.OK, result.kind);
    }

    @Test
    @DisplayName("Port P1: Null is valid")
    void port_null_isValid() {
        FormValidation result = portValidator.doCheckPort(null);
        assertEquals(Kind.OK, result.kind);
    }

    //  Partition P2: Non-Numeric String (Invalid) 

    @Test
    @DisplayName("Port P2: Alphabetic string 'abc' is invalid")
    void port_alphabeticString_isInvalid() {
        FormValidation result = portValidator.doCheckPort("abc");
        assertEquals(Kind.ERROR, result.kind);
    }

    @Test
    @DisplayName("Port P2: Decimal '80.5' is invalid")
    void port_decimalNumber_isInvalid() {
        FormValidation result = portValidator.doCheckPort("80.5");
        assertEquals(Kind.ERROR, result.kind);
    }

    //  Partition P3: Negative Numbers (Invalid) 

    @Test
    @DisplayName("Port P3: Negative number '-1' is invalid (boundary)")
    void port_negativeOne_isInvalid() {
        FormValidation result = portValidator.doCheckPort("-1");
        assertEquals(Kind.ERROR, result.kind);
    }

    @Test
    @DisplayName("Port P3: Negative number '-100' is invalid")
    void port_negativeHundred_isInvalid() {
        FormValidation result = portValidator.doCheckPort("-100");
        assertEquals(Kind.ERROR, result.kind);
    }

    //  Partition P4: Valid Range 0-65535 (Valid) 

    @Test
    @DisplayName("Port P4: Port '0' is valid (lower boundary)")
    void port_zero_isValid() {
        FormValidation result = portValidator.doCheckPort("0");
        assertEquals(Kind.OK, result.kind);
    }

    @Test
    @DisplayName("Port P4: Port '1' is valid (just above lower boundary)")
    void port_one_isValid() {
        FormValidation result = portValidator.doCheckPort("1");
        assertEquals(Kind.OK, result.kind);
    }

    @Test
    @DisplayName("Port P4: Port '8080' is valid (typical value)")
    void port_8080_isValid() {
        FormValidation result = portValidator.doCheckPort("8080");
        assertEquals(Kind.OK, result.kind);
    }

    @Test
    @DisplayName("Port P4: Port '65534' is valid (just below upper boundary)")
    void port_65534_isValid() {
        FormValidation result = portValidator.doCheckPort("65534");
        assertEquals(Kind.OK, result.kind);
    }

    @Test
    @DisplayName("Port P4: Port '65535' is valid (upper boundary)")
    void port_65535_isValid() {
        FormValidation result = portValidator.doCheckPort("65535");
        assertEquals(Kind.OK, result.kind);
    }

    //  Partition P5: Above Maximum (Invalid) 

    @Test
    @DisplayName("Port P5: Port '65536' is invalid (just above upper boundary)")
    void port_65536_isInvalid() {
        FormValidation result = portValidator.doCheckPort("65536");
        assertEquals(Kind.ERROR, result.kind);
    }

    @Test
    @DisplayName("Port P5: Port '100000' is invalid (well above maximum)")
    void port_100000_isInvalid() {
        FormValidation result = portValidator.doCheckPort("100000");
        assertEquals(Kind.ERROR, result.kind);
    }

    // FEATURE 2: THREAD COUNT VALIDATION
    //
    // Tests the validateIntegerInRange() method used by SCMTrigger
    // Valid thread count range: 5 to 100
    //
    // Partitions:
    //   P1: Non-numeric string   -> Invalid (Error)
    //   P2: Below minimum (<5)   -> Invalid (Error)
    //   P3: Valid range 5-100    -> Valid (OK)
    //   P4: Above maximum (>100) -> Invalid (Error)

    private static final int MIN_THREADS = 5;
    private static final int MAX_THREADS = 100;

    private FormValidation validateThreadCount(String value) {
        return FormValidation.validateIntegerInRange(value, MIN_THREADS, MAX_THREADS);
    }

    //  Partition P1: Non-Numeric String (Invalid) 

    @Test
    @DisplayName("Threads P1: Empty string is invalid")
    void threads_emptyString_isInvalid() {
        FormValidation result = validateThreadCount("");
        assertEquals(Kind.ERROR, result.kind);
    }

    @Test
    @DisplayName("Threads P1: Alphabetic string 'abc' is invalid")
    void threads_alphabeticString_isInvalid() {
        FormValidation result = validateThreadCount("abc");
        assertEquals(Kind.ERROR, result.kind);
    }

    //  Partition P2: Below Minimum (Invalid) 

    @Test
    @DisplayName("Threads P2: Value '4' is invalid (just below minimum boundary)")
    void threads_four_isInvalid() {
        FormValidation result = validateThreadCount("4");
        assertEquals(Kind.ERROR, result.kind);
    }

    @Test
    @DisplayName("Threads P2: Value '0' is invalid")
    void threads_zero_isInvalid() {
        FormValidation result = validateThreadCount("0");
        assertEquals(Kind.ERROR, result.kind);
    }

    @Test
    @DisplayName("Threads P2: Value '-1' is invalid (negative)")
    void threads_negativeOne_isInvalid() {
        FormValidation result = validateThreadCount("-1");
        assertEquals(Kind.ERROR, result.kind);
    }

    //  Partition P3: Valid Range 5-100 (Valid) 

    @Test
    @DisplayName("Threads P3: Value '5' is valid (lower boundary)")
    void threads_five_isValid() {
        FormValidation result = validateThreadCount("5");
        assertEquals(Kind.OK, result.kind);
    }

    @Test
    @DisplayName("Threads P3: Value '6' is valid (just above lower boundary)")
    void threads_six_isValid() {
        FormValidation result = validateThreadCount("6");
        assertEquals(Kind.OK, result.kind);
    }

    @Test
    @DisplayName("Threads P3: Value '50' is valid (middle of range)")
    void threads_fifty_isValid() {
        FormValidation result = validateThreadCount("50");
        assertEquals(Kind.OK, result.kind);
    }

    @Test
    @DisplayName("Threads P3: Value '99' is valid (just below upper boundary)")
    void threads_ninetyNine_isValid() {
        FormValidation result = validateThreadCount("99");
        assertEquals(Kind.OK, result.kind);
    }

    @Test
    @DisplayName("Threads P3: Value '100' is valid (upper boundary)")
    void threads_hundred_isValid() {
        FormValidation result = validateThreadCount("100");
        assertEquals(Kind.OK, result.kind);
    }

    // Partition P4: Above Maximum (Invalid) 

    @Test
    @DisplayName("Threads P4: Value '101' is invalid (just above upper boundary)")
    void threads_101_isInvalid() {
        FormValidation result = validateThreadCount("101");
        assertEquals(Kind.ERROR, result.kind);
    }

    @Test
    @DisplayName("Threads P4: Value '500' is invalid (well above maximum)")
    void threads_500_isInvalid() {
        FormValidation result = validateThreadCount("500");
        assertEquals(Kind.ERROR, result.kind);
    }
}
