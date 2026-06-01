package hudson.slaves;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import hudson.DescriptorExtensionList;
import hudson.model.Descriptor;
import java.util.ArrayList;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * @author peppelan
 */
class DelegatingComputerLauncherTest {

    public static class DummyOne extends DelegatingComputerLauncher {

        @SuppressWarnings("checkstyle:redundantmodifier")
        public DummyOne() {
            super(null);
        }

        public static class DummyOneDescriptor extends DescriptorImpl {
        }
    }


    public static class DummyTwo extends DelegatingComputerLauncher {

        @SuppressWarnings("checkstyle:redundantmodifier")
        public DummyTwo() {
            super(null);
        }

        public static class DummyTwoDescriptor extends DescriptorImpl {
        }
    }

    // Ensure that by default a DelegatingComputerLauncher subclass doesn't advertise the option to delegate another
    // DelegatingComputerLauncher
    @Test
    void testRecursionAvoidance() {
        Jenkins jenkins = mock(Jenkins.class);
        try (MockedStatic<Jenkins> mocked = mockStatic(Jenkins.class)) {
            mocked.when(Jenkins::get).thenReturn(jenkins);

            DescriptorExtensionList<ComputerLauncher, Descriptor<ComputerLauncher>> mockList =
                    mock(DescriptorExtensionList.class);
            doReturn(mockList).when(jenkins).getDescriptorList(eq(ComputerLauncher.class));
            ArrayList<Descriptor<ComputerLauncher>> returnedList = new ArrayList<>();

            returnedList.add(new DummyOne.DummyOneDescriptor());
            returnedList.add(new DummyTwo.DummyTwoDescriptor());

            when(mockList.iterator()).thenReturn(returnedList.iterator());

            assertTrue(new DummyTwo.DummyTwoDescriptor().applicableDescriptors(null, new DumbSlave.DescriptorImpl()).isEmpty(),
                    "DelegatingComputerLauncher should filter out other DelegatingComputerLauncher instances " +
                            "from its descriptor's getApplicableDescriptors() method");
        }
    }

}
