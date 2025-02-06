package org.jenkins.mytests;

// STATIC imports
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import hudson.util.FormValidation; // SPECIAL_IMPORTS group ? //it says needs to have an empty line above this but not above the next one?
import org.junit.Test; // THIRD_PARTY_PACKAGE imports ?

public class PartitionTest {
    public static class NewNodeValidator {
        public static FormValidation validateName(String name) {
            if (name == null || name.trim().isEmpty()) {
                return FormValidation.error("Name cannot be empty");
            }
            // Disallow spaces anywhere in the name
            if (name.contains(" ")) {
                return FormValidation.error("Name cannot contain spaces");
            }
            // Disallow special characters: '*', '/', '?', or '\' (backslash)
            if (name.matches(".*[*/?\\\\].*")) {
                return FormValidation.error("Name contains invalid characters");
            }
            // For demonstration, enforce a maximum length of 20 characters
            if (name.length() > 20) {
                return FormValidation.error("Name is too long");
            }
            return FormValidation.ok();
        }
    }

    @Test //worked by clicking play debugg button on left
    public void testValidNodeNames() {
        String[] validNames = {
                "node1",
                "Node-2",
                "node_3",
                "NODE4",
                "node-123",
                "node.name",
                "n",
        };

        for (String name : validNames) {
            FormValidation result = NewNodeValidator.validateName(name);
            // Expect a successful (OK) validation for valid input.
            assertThat("Expected valid for name: " + name,
                    result.kind, equalTo(FormValidation.Kind.OK));
        }
    }
}

