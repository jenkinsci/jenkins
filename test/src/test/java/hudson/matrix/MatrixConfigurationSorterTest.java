package hudson.matrix;

import hudson.model.Item;
import hudson.util.FormValidation;

import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author Kohsuke Kawaguchi
 */
public class MatrixConfigurationSorterTest extends HudsonTestCase {
    public void testConfigRoundtrip() throws Exception {
        MatrixProject p = createMatrixProject();
        configRoundtrip((Item)p);
        assertEqualDataBoundBeans(new NoopMatrixConfigurationSorter(),p.getSorter());

        SorterImpl before = new SorterImpl();
        p.setSorter(before);
        p.setRunSequentially(true);
        configRoundtrip((Item)p);
        Object after = p.getSorter();
        assertNotSame(before,after);
        assertSame(before.getClass(),after.getClass());
    }

    public static class SorterImpl extends MatrixConfigurationSorter {
        @DataBoundConstructor
        public SorterImpl() {}

        @Override
        public void validate(MatrixProject p) throws FormValidation {
        }

        public int compare(MatrixConfiguration o1, MatrixConfiguration o2) {
            return o1.getDisplayName().compareTo(o2.getDisplayName());
        }

        @TestExtension
        public static class DescriptorImpl extends MatrixConfigurationSorterDescriptor {
            @Override
            public String getDisplayName() {
                return "Test Sorter";
            }
        }
    }
}
