package hudson.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.StringReader;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
public class RobustReflectionConverterSecurity4032Test {

    private JenkinsRule r;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        r = rule;
    }

    public static class TypeWithNotDeserializableField {
        @jenkins.security.XStreamNotDeserializable
        private transient String identity;
        private transient String migratable;
        private String normal;

        TypeWithNotDeserializableField(String identity, String normal) {
            this.identity = identity;
            this.normal = normal;
        }

        public String getIdentity() {
            return identity;
        }

        public String getMigratable() {
            return migratable;
        }

        public String getNormal() {
            return normal;
        }
    }

    @Test
    void xStreamNotDeserializableFieldIsSkipped() {
        XStream2 xs = new XStream2();
        xs.alias("test", TypeWithNotDeserializableField.class);

        String xml = """
                <test>
                  <identity>injected</identity>
                  <migratable>migrated-value</migratable>
                  <normal>updated</normal>
                </test>""";

        TypeWithNotDeserializableField existing = new TypeWithNotDeserializableField("original", "old-normal");
        xs.unmarshal(XStream2.getDefaultDriver().createReader(new StringReader(xml)), existing, null, true);

        assertEquals("original", existing.getIdentity(),
                "@XStreamNotDeserializable field must not be overwritten by XML");
        assertEquals("migrated-value", existing.getMigratable(),
                "unannotated transient field must still be writable (migration support)");
        assertEquals("updated", existing.getNormal(),
                "normal (non-transient) field must be updated from XML");
    }

    @Test
    void xStreamNotDeserializableFieldIsSkippedOnFreshInstance() {
        XStream2 xs = new XStream2();
        xs.alias("test", TypeWithNotDeserializableField.class);

        String xml = """
                <test>
                  <identity>injected</identity>
                  <migratable>migrated-value</migratable>
                  <normal>from-xml</normal>
                </test>""";

        TypeWithNotDeserializableField result = (TypeWithNotDeserializableField) xs.fromXML(xml);

        assertNull(result.getIdentity(),
                "@XStreamNotDeserializable field must not be set even on a fresh instance");
        assertEquals("migrated-value", result.getMigratable(),
                "unannotated transient field must be populated on fresh instance");
        assertEquals("from-xml", result.getNormal(),
                "normal field must be populated from XML");
    }

    public static class TypeWithDeserializableField {
        @jenkins.security.XStreamDeserializable
        private transient String migration;
        private transient String unannotated;
        private String normal;

        TypeWithDeserializableField() {}

        TypeWithDeserializableField(String migration, String unannotated, String normal) {
            this.migration = migration;
            this.unannotated = unannotated;
            this.normal = normal;
        }

        public String getMigration() {
            return migration;
        }

        public String getUnannotated() {
            return unannotated;
        }

        public String getNormal() {
            return normal;
        }
    }

    @Test
    void xStreamDeserializableFieldIsPopulated() {
        XStream2 xs = new XStream2();
        xs.alias("test", TypeWithDeserializableField.class);

        String xml = """
                <test>
                  <migration>old-value</migration>
                  <unannotated>also-populated</unannotated>
                  <normal>updated</normal>
                </test>""";

        TypeWithDeserializableField existing = new TypeWithDeserializableField(null, null, "old-normal");
        xs.unmarshal(XStream2.getDefaultDriver().createReader(new StringReader(xml)), existing, null, true);

        assertEquals("old-value", existing.getMigration(),
                "@XStreamDeserializable transient field must be populated from XML");
        assertEquals("also-populated", existing.getUnannotated(),
                "unannotated transient field must also be populated (current permissive default)");
        assertEquals("updated", existing.getNormal(),
                "normal field must be updated from XML");
    }

    @Test
    void xStreamDeserializableFieldIsPopulatedOnFreshInstance() {
        XStream2 xs = new XStream2();
        xs.alias("test", TypeWithDeserializableField.class);

        String xml = """
                <test>
                  <migration>old-value</migration>
                  <unannotated>also-populated</unannotated>
                  <normal>from-xml</normal>
                </test>""";

        TypeWithDeserializableField result = (TypeWithDeserializableField) xs.fromXML(xml);

        assertEquals("old-value", result.getMigration(),
                "@XStreamDeserializable transient field must be populated on fresh instance");
        assertEquals("also-populated", result.getUnannotated(),
                "unannotated transient field must also be populated on fresh instance");
        assertEquals("from-xml", result.getNormal(),
                "normal field must be populated from XML");
    }

    /**
     * Test-scoped annotation with the same simple name as
     * {@link jenkins.security.XStreamNotDeserializable}.
     * Verifies that RobustReflectionConverter matches by simple name, allowing plugins
     * targeting older cores to declare their own annotation without a core dependency.
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface XStreamNotDeserializable {
    }

    /**
     * Test-scoped annotation with the same simple name as
     * {@link jenkins.security.XStreamDeserializable}.
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface XStreamDeserializable {
    }

    public static class TypeWithTestScopedAnnotations {
        @XStreamNotDeserializable
        private transient String blocked;
        @XStreamDeserializable
        private transient String allowed;
        private String normal;

        TypeWithTestScopedAnnotations() {}

        TypeWithTestScopedAnnotations(String blocked, String allowed, String normal) {
            this.blocked = blocked;
            this.allowed = allowed;
            this.normal = normal;
        }

        public String getBlocked() {
            return blocked;
        }

        public String getAllowed() {
            return allowed;
        }

        public String getNormal() {
            return normal;
        }
    }

    @Test
    void testScopedNotDeserializableAnnotationIsHonoredBySimpleName() {
        XStream2 xs = new XStream2();
        xs.alias("test", TypeWithTestScopedAnnotations.class);

        String xml = """
                <test>
                  <blocked>injected</blocked>
                  <allowed>migrated</allowed>
                  <normal>updated</normal>
                </test>""";

        TypeWithTestScopedAnnotations existing = new TypeWithTestScopedAnnotations("original", null, "old");
        xs.unmarshal(XStream2.getDefaultDriver().createReader(new StringReader(xml)), existing, null, true);

        assertEquals("original", existing.getBlocked(),
                "test-scoped @XStreamNotDeserializable must be matched by simple name and block the write");
        assertEquals("migrated", existing.getAllowed(),
                "test-scoped @XStreamDeserializable field must still be populated (no-op annotation)");
        assertEquals("updated", existing.getNormal(),
                "normal field must be updated");
    }

    @Test
    void testScopedNotDeserializableAnnotationIsHonoredOnFreshInstance() {
        XStream2 xs = new XStream2();
        xs.alias("test", TypeWithTestScopedAnnotations.class);

        String xml = """
                <test>
                  <blocked>injected</blocked>
                  <allowed>migrated</allowed>
                  <normal>from-xml</normal>
                </test>""";

        TypeWithTestScopedAnnotations result = (TypeWithTestScopedAnnotations) xs.fromXML(xml);

        assertNull(result.getBlocked(),
                "test-scoped @XStreamNotDeserializable must block the write on fresh instance too");
        assertEquals("migrated", result.getAllowed(),
                "test-scoped @XStreamDeserializable field must be populated on fresh instance");
        assertEquals("from-xml", result.getNormal(),
                "normal field must be populated from XML");
    }

    @Test
    void escapeHatchDisablesNotDeserializableCheck() {
        XStream2 xs = new XStream2();
        xs.alias("test", TypeWithNotDeserializableField.class);

        String xml = """
                <test>
                  <identity>injected</identity>
                  <migratable>migrated-value</migratable>
                  <normal>updated</normal>
                </test>""";

        boolean original = RobustReflectionConverter.DISABLE_XSTREAM_NOT_DESERIALIZABLE_CHECK;
        try {
            RobustReflectionConverter.DISABLE_XSTREAM_NOT_DESERIALIZABLE_CHECK = true;

            TypeWithNotDeserializableField existing = new TypeWithNotDeserializableField("original", "old-normal");
            xs.unmarshal(XStream2.getDefaultDriver().createReader(new StringReader(xml)), existing, null, true);

            assertEquals("injected", existing.getIdentity(),
                    "escape hatch must allow @XStreamNotDeserializable field to be overwritten");
            assertEquals("migrated-value", existing.getMigratable(),
                    "unannotated transient field must still be writable");
            assertEquals("updated", existing.getNormal(),
                    "normal field must be updated from XML");
        } finally {
            RobustReflectionConverter.DISABLE_XSTREAM_NOT_DESERIALIZABLE_CHECK = original;
        }
    }

    @Test
    void strictModeBlocksUnannotatedTransientFields() {
        XStream2 xs = new XStream2();
        xs.alias("test", TypeWithDeserializableField.class);

        String xml = """
                <test>
                  <migration>old-value</migration>
                  <unannotated>should-be-blocked</unannotated>
                  <normal>updated</normal>
                </test>""";

        boolean original = RobustReflectionConverter.TRANSIENT_FIELD_STRICT_MODE;
        try {
            RobustReflectionConverter.TRANSIENT_FIELD_STRICT_MODE = true;

            TypeWithDeserializableField existing = new TypeWithDeserializableField("orig-migration", "orig-unannotated", "old-normal");
            xs.unmarshal(XStream2.getDefaultDriver().createReader(new StringReader(xml)), existing, null, true);

            assertEquals("old-value", existing.getMigration(),
                    "@XStreamDeserializable field must still be populated in strict mode");
            assertEquals("orig-unannotated", existing.getUnannotated(),
                    "unannotated transient field must be blocked in strict mode");
            assertEquals("updated", existing.getNormal(),
                    "normal (non-transient) field must still be updated in strict mode");
        } finally {
            RobustReflectionConverter.TRANSIENT_FIELD_STRICT_MODE = original;
        }
    }

    @Test
    void strictModeStillRespectsNotDeserializable() {
        XStream2 xs = new XStream2();
        xs.alias("test", TypeWithNotDeserializableField.class);

        String xml = """
                <test>
                  <identity>injected</identity>
                  <migratable>should-be-blocked</migratable>
                  <normal>updated</normal>
                </test>""";

        boolean original = RobustReflectionConverter.TRANSIENT_FIELD_STRICT_MODE;
        try {
            RobustReflectionConverter.TRANSIENT_FIELD_STRICT_MODE = true;

            TypeWithNotDeserializableField existing = new TypeWithNotDeserializableField("original", "old-normal");
            xs.unmarshal(XStream2.getDefaultDriver().createReader(new StringReader(xml)), existing, null, true);

            assertEquals("original", existing.getIdentity(),
                    "@XStreamNotDeserializable field must still be blocked in strict mode");
            assertNull(existing.getMigratable(),
                    "unannotated transient field must also be blocked in strict mode");
            assertEquals("updated", existing.getNormal(),
                    "normal field must still be updated in strict mode");
        } finally {
            RobustReflectionConverter.TRANSIENT_FIELD_STRICT_MODE = original;
        }
    }

    public static class TypeWithAnnotationOnNonTransientField {
        @jenkins.security.XStreamNotDeserializable
        private String shouldIgnoreAnnotation;
        private String normal;

        TypeWithAnnotationOnNonTransientField() {}

        TypeWithAnnotationOnNonTransientField(String shouldIgnoreAnnotation, String normal) {
            this.shouldIgnoreAnnotation = shouldIgnoreAnnotation;
            this.normal = normal;
        }

        public String getShouldIgnoreAnnotation() {
            return shouldIgnoreAnnotation;
        }

        public String getNormal() {
            return normal;
        }
    }

    @Test
    void annotationsOnNonTransientFieldsAreIgnored() {
        XStream2 xs = new XStream2();
        xs.alias("test", TypeWithAnnotationOnNonTransientField.class);

        String xml = """
                <test>
                  <shouldIgnoreAnnotation>from-xml</shouldIgnoreAnnotation>
                  <normal>updated</normal>
                </test>""";

        TypeWithAnnotationOnNonTransientField existing = new TypeWithAnnotationOnNonTransientField("original", "old");
        xs.unmarshal(XStream2.getDefaultDriver().createReader(new StringReader(xml)), existing, null, true);

        assertEquals("from-xml", existing.getShouldIgnoreAnnotation(),
                "@XStreamNotDeserializable on a non-transient field must be ignored");
        assertEquals("updated", existing.getNormal(),
                "normal field must be updated from XML");
    }

    @Test
    void annotationsOnNonTransientFieldsAreIgnoredInStrictMode() {
        XStream2 xs = new XStream2();
        xs.alias("test", TypeWithAnnotationOnNonTransientField.class);

        String xml = """
                <test>
                  <shouldIgnoreAnnotation>from-xml</shouldIgnoreAnnotation>
                  <normal>updated</normal>
                </test>""";

        boolean original = RobustReflectionConverter.TRANSIENT_FIELD_STRICT_MODE;
        try {
            RobustReflectionConverter.TRANSIENT_FIELD_STRICT_MODE = true;

            TypeWithAnnotationOnNonTransientField existing = new TypeWithAnnotationOnNonTransientField("original", "old");
            xs.unmarshal(XStream2.getDefaultDriver().createReader(new StringReader(xml)), existing, null, true);

            assertEquals("from-xml", existing.getShouldIgnoreAnnotation(),
                    "@XStreamNotDeserializable on a non-transient field must be ignored even in strict mode");
            assertEquals("updated", existing.getNormal(),
                    "normal field must be updated from XML");
        } finally {
            RobustReflectionConverter.TRANSIENT_FIELD_STRICT_MODE = original;
        }
    }

}
