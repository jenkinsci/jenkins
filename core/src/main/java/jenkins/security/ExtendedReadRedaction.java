package jenkins.security;

import hudson.ExtensionList;
import hudson.ExtensionPoint;

/**
 * Redact {@code config.xml} contents for users with ExtendedRead permission
 * while lacking the required Configure permission to see the full unredacted
 * configuration.
 *
 * @see <a href="https://issues.jenkins.io/browse/SECURITY-266">SECURITY-266</a>
 * @see <a href="https://www.jenkins.io/security/advisory/2016-05-11/">Jenkins Security Advisory 2016-05-11</a>
 * @since 2.479
 */
public interface ExtendedReadRedaction extends ExtensionPoint {
    /**
     * Redacts sensitive information from the provided {@code config.xml} file content.
     * Input may already have redactions applied; output may be passed through further redactions.
     * These methods are expected to retain the basic structure of the XML document contained in input/output strings.
     *
     * @param configDotXml String representation of (potentially already redacted) config.xml file
     * @return Redacted config.xml file content
     */
    String apply(String configDotXml);

    static ExtensionList<ExtendedReadRedaction> all() {
        return ExtensionList.lookup(ExtendedReadRedaction.class);
    }

    static String applyAll(String configDotXml) {
        for (ExtendedReadRedaction redaction : all()) {
            configDotXml = redaction.apply(configDotXml);
        }
        return configDotXml;
    }
}
