package hudson.slaves;

import hudson.DescriptorExtensionList;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;

import static org.junit.Assert.*;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * @author peppelan
 */
@RunWith(PowerMockRunner.class)
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
    @PrepareForTest(Jenkins.class)
    public void testRecursionAvoidance() {
        PowerMockito.mockStatic(Jenkins.class);
        Jenkins mockJenkins = mock(Jenkins.class);
        PowerMockito.when(Jenkins.getInstance()).thenReturn(mockJenkins);

        DescriptorExtensionList<ComputerLauncher, Descriptor<ComputerLauncher>> mockList =
                mock(DescriptorExtensionList.class);
        doReturn(mockList).when(mockJenkins).getDescriptorList(eq(ComputerLauncher.class));
        ArrayList<Descriptor<ComputerLauncher>> returnedList = new ArrayList<>();

        returnedList.add(new DummyOne.DummyOneDescriptor());
        returnedList.add(new DummyTwo.DummyTwoDescriptor());

        when(mockList.iterator()).thenReturn(returnedList.iterator());

        assertTrue("DelegatingComputerLauncher should filter out other DelegatingComputerLauncher instances " +
                   "from its descriptor's getApplicableDescriptors() method",
                new DummyTwo.DummyTwoDescriptor().applicableDescriptors(null, new DumbSlave.DescriptorImpl()).isEmpty());
    }

}
