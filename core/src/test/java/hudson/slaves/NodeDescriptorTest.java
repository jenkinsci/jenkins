package hudson.slaves;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.util.FormValidation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NodeDescriptorTest {

    private final NodeDescriptor descriptor = new NodeDescriptor(hudson.model.Node.class) {};
    private boolean originalAllowOperators;

    @BeforeEach
    void setUp() {
        originalAllowOperators = NodeDescriptor.ALLOW_OPERATOR_CHARACTERS_IN_LABELS;
        NodeDescriptor.ALLOW_OPERATOR_CHARACTERS_IN_LABELS = false;
    }

    @AfterEach
    void tearDown() {
        NodeDescriptor.ALLOW_OPERATOR_CHARACTERS_IN_LABELS = originalAllowOperators;
    }

    @Test
    void doCheckLabelString_EmptyOrNull() {
        assertEquals(FormValidation.Kind.OK, descriptor.doCheckLabelString(null).kind);
        assertEquals(FormValidation.Kind.OK, descriptor.doCheckLabelString("").kind);
        assertEquals(FormValidation.Kind.OK, descriptor.doCheckLabelString("   \t\n").kind);
    }

    @Test
    void doCheckLabelString_ValidLabels() {
        assertEquals(FormValidation.Kind.OK, descriptor.doCheckLabelString("linux x64 arm64").kind);
        assertEquals(FormValidation.Kind.OK, descriptor.doCheckLabelString("\"linux 64\" test_node").kind);
        assertEquals(FormValidation.Kind.OK, descriptor.doCheckLabelString("build-123.4").kind);
    }

    @Test
    void doCheckLabelString_OperatorCharacters_ErrorByDefault() {
        FormValidation v = descriptor.doCheckLabelString("a&&b c");
        assertEquals(FormValidation.Kind.ERROR, v.kind);
        assertThat(v.getMessage(), containsString("a&amp;&amp;b"));

        v = descriptor.doCheckLabelString("a && b");
        assertEquals(FormValidation.Kind.ERROR, v.kind);
        assertThat(v.getMessage(), containsString("&amp;&amp;"));

        v = descriptor.doCheckLabelString("a||b");
        assertEquals(FormValidation.Kind.ERROR, v.kind);
        assertThat(v.getMessage(), containsString("a||b"));

        v = descriptor.doCheckLabelString("!test");
        assertEquals(FormValidation.Kind.ERROR, v.kind);
        assertThat(v.getMessage(), containsString("!test"));

        v = descriptor.doCheckLabelString("(group)");
        assertEquals(FormValidation.Kind.ERROR, v.kind);
        assertThat(v.getMessage(), containsString("(group)"));

        v = descriptor.doCheckLabelString("\"x && y\" valid");
        assertEquals(FormValidation.Kind.ERROR, v.kind);
        assertThat(v.getMessage(), containsString("x &amp;&amp; y"));
    }

    @Test
    void doCheckLabelString_MultipleProblematicLabels() {
        FormValidation v = descriptor.doCheckLabelString("a&&b ok (c) !d");
        assertEquals(FormValidation.Kind.ERROR, v.kind);
        assertThat(v.getMessage(), containsString("a&amp;&amp;b"));
        assertThat(v.getMessage(), containsString("(c)"));
        assertThat(v.getMessage(), containsString("!d"));
        assertThat(v.getMessage(), not(containsString("ok")));
    }

    @Test
    void doCheckLabelString_WarningWhenAllowedBySystemProperty() {
        NodeDescriptor.ALLOW_OPERATOR_CHARACTERS_IN_LABELS = true;

        FormValidation v = descriptor.doCheckLabelString("a&&b c");
        assertEquals(FormValidation.Kind.WARNING, v.kind);
        assertThat(v.getMessage(), containsString("a&amp;&amp;b"));

        v = descriptor.doCheckLabelString("linux x64");
        assertEquals(FormValidation.Kind.OK, v.kind);
    }
}
