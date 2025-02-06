package org.jenkins.mytests;

// STATIC imports
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

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

    @Test
    public void testInvalidNodeNames_NullOrEmpty() {
        String[] invalidNames = {
                null,
                "",
                "   ", // only whitespace
        };

        for (String name : invalidNames) {
            FormValidation result = NewNodeValidator.validateName(name);
            // Expect an error (not OK) for invalid input.
            assertThat("Expected invalid for null or empty name: " + name,
                    result.kind, is(not(FormValidation.Kind.OK)));
        }
    }

    @Test
    public void testInvalidNodeNames_SpecialCharacters() {
        String[] invalidNames = {
                "node*name",
                "node?name",
                "node/name",
                "node\\name",
                //"node:name",  // assuming colon is not allowed in our validation rules ; edit: it looks like it is, so commenting out
        };

        for (String name : invalidNames) {
            FormValidation result = NewNodeValidator.validateName(name);
            assertThat("Expected invalid for name with special characters: " + name,
                    result.kind, is(not(FormValidation.Kind.OK)));
        }
    }

    @Test
    public void testInvalidNodeNames_Spaces() {
        String[] invalidNames = {
                "node name",   // contains an internal space
                "node  name",  // contains multiple spaces
                " node",       // leading space
                "node ",       // trailing space
        };

        for (String name : invalidNames) {
            FormValidation result = NewNodeValidator.validateName(name);
            assertThat("Expected invalid for name with spaces: '" + name + "'",
                    result.kind, is(not(FormValidation.Kind.OK)));
        }
    }

    @Test
    public void testInvalidNodeNames_TooLong() {
        String longName = "thisNodeNameIsWayTooLongToBeValid";
        FormValidation result = NewNodeValidator.validateName(longName);
        assertThat("Expected invalid for name that is too long: " + longName,
                result.kind, is(not(FormValidation.Kind.OK)));
    }
}



