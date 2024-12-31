package hudson.agents;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import hudson.DescriptorExtensionList;
import hudson.model.Descriptor;
import java.util.ArrayList;
import jenkins.model.Jenkins;
import org.junit.Test;
import org.mockito.MockedStatic;

/**
 * @author peppelan
 */
public class DelegatingComputerLauncherTest {

    public static class DummyOne extends DelegatingComputerLauncher {

        public DummyOne() {
            super(null);
        }

        public static class DummyOneDescriptor extends DescriptorImpl {
        }
    }


    public static class DummyTwo extends DelegatingComputerLauncher {

        public DummyTwo() {
            super(null);
        }

        public static class DummyTwoDescriptor extends DescriptorImpl {
        }
    }

    // Ensure that by default a DelegatingComputerLauncher subclass doesn't advertise the option to delegate another
    // DelegatingComputerLauncher
    @Test
    public void testRecursionAvoidance() {
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

            assertTrue("DelegatingComputerLauncher should filter out other DelegatingComputerLauncher instances " +
                            "from its descriptor's getApplicableDescriptors() method",
                    new DummyTwo.DummyTwoDescriptor().applicableDescriptors(null, new DumbAgent.DescriptorImpl()).isEmpty());
        }
    }

}
