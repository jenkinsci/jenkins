package hudson.tasks.junit;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import jenkins.model.Jenkins;

/**
 * Allow extensions to transform the class/package/method name for JUnit test
 * cases which will be displayed on the test result page.
 *
 * This is useful for alternative JVM languages like Scala that allow
 * identifiers with invalid characters by encoding them: an extension can
 * decode the identifier so it is displayed correctly.
 *
 * @since 1.515
 */

public abstract class TestNameTransformer implements ExtensionPoint {
    /**
     * Transform the class/package/method name.
     *
     * @param name
     *      Class name (may be simple or fully qualified), package name, or
     *      method name from a JUnit test.
     * @return
     *      The transformed name, or the name that was passed in if it doesn't
     *      need to be changed.
     */
    public abstract String transformName(String name);
    
    public static String getTransformedName(String name) {
        String transformedName = name;
        for (TestNameTransformer transformer : all()) {
            transformedName = transformer.transformName(transformedName);
        }
        return transformedName;
    }

    public static ExtensionList<TestNameTransformer> all() {
        return ExtensionList.lookup(TestNameTransformer.class);
    }
}
