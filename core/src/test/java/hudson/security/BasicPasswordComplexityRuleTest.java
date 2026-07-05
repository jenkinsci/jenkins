package hudson.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class BasicPasswordComplexityRuleTest {

    @Test
    void validationWithAllRulesEnabled() {
        BasicPasswordComplexityRule rule = new BasicPasswordComplexityRule(8, true, true, true, true);

        PasswordComplexityException e = assertThrows(PasswordComplexityException.class, () -> rule.validate("bad"));
        assertThat(e.getMessage(), containsString("Password must be at least 8 characters long"));

        assertDoesNotThrow(() -> rule.validate("GoodPass1!"));
    }

    @Test
    void minimumLengthEnforced() {
        BasicPasswordComplexityRule rule = new BasicPasswordComplexityRule(8, false, false, false, false);

        PasswordComplexityException e = assertThrows(PasswordComplexityException.class, () -> rule.validate("short"));
        assertThat(e.getMessage(), containsString("Password must be at least 8 characters long"));

        assertDoesNotThrow(() -> rule.validate("longenough"));
    }

    @Test
    void uppercaseRequired() {
        BasicPasswordComplexityRule rule = new BasicPasswordComplexityRule(0, true, false, false, false);

        PasswordComplexityException e = assertThrows(PasswordComplexityException.class, () -> rule.validate("alllowercase"));
        assertThat(e.getMessage(), containsString("uppercase letter"));

        assertDoesNotThrow(() -> rule.validate("hasUppercase"));
    }

    @Test
    void lowercaseRequired() {
        BasicPasswordComplexityRule rule = new BasicPasswordComplexityRule(0, false, true, false, false);

        PasswordComplexityException e = assertThrows(PasswordComplexityException.class, () -> rule.validate("ALLUPPERCASE"));
        assertThat(e.getMessage(), containsString("lowercase letter"));

        assertDoesNotThrow(() -> rule.validate("HASLowercase"));
    }

    @Test
    void digitRequired() {
        BasicPasswordComplexityRule rule = new BasicPasswordComplexityRule(0, false, false, true, false);

        PasswordComplexityException e = assertThrows(PasswordComplexityException.class, () -> rule.validate("NoDigitsHere"));
        assertThat(e.getMessage(), containsString("digit"));

        assertDoesNotThrow(() -> rule.validate("Has1Digit"));
    }

    @Test
    void specialCharacterRequired() {
        BasicPasswordComplexityRule rule = new BasicPasswordComplexityRule(0, false, false, false, true);

        PasswordComplexityException e = assertThrows(PasswordComplexityException.class, () -> rule.validate("NoSpecial123"));
        assertThat(e.getMessage(), containsString("special character"));

        assertDoesNotThrow(() -> rule.validate("HasSpecial!"));
    }

    @Test
    void multipleViolationsReportedTogether() {
        BasicPasswordComplexityRule rule = new BasicPasswordComplexityRule(8, true, true, true, true);

        PasswordComplexityException e = assertThrows(PasswordComplexityException.class, () -> rule.validate("bad"));
        assertThat(e.getMessage(), allOf(
                containsString("at least 8 characters"),
                containsString("uppercase letter"),
                containsString("digit"),
                containsString("special character")
        ));
    }

    @Test
    void noneRuleAcceptsAnyPassword() {
        NonePasswordComplexityRule rule = new NonePasswordComplexityRule();
        assertDoesNotThrow(() -> rule.validate("a"));
        assertDoesNotThrow(() -> rule.validate(""));
    }

}
