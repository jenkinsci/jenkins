package jenkins;

import hudson.ExtensionComponent;
import hudson.console.ConsoleAnnotatorFactory;
import hudson.model.PageDecorator;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.TestExtension;

/**
 * @author Kohsuke Kawaguchi
 */
public class ExtensionFilterTest extends HudsonTestCase {
    public void testFilter() {
        assertTrue(PageDecorator.all().isEmpty());
        assertTrue(ConsoleAnnotatorFactory.all().isEmpty());
    }

    @TestExtension("testFilter")
    public static class Impl extends ExtensionFilter {
        @Override
        public <T> boolean allows(Class<T> type, ExtensionComponent<T> component) {
            if (type==ConsoleAnnotatorFactory.class)
                return false;
            if (component.isDescriptorOf(PageDecorator.class))
                return false;
            return true;
        }
    }
}
