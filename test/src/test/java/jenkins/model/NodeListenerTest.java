package jenkins.model;

import hudson.ExtensionList;
import hudson.cli.CLICommand;
import hudson.cli.CLICommandInvoker;
import hudson.cli.CreateNodeCommand;
import hudson.cli.DeleteNodeCommand;
import hudson.cli.GetNodeCommand;
import hudson.cli.UpdateNodeCommand;
import hudson.model.Node;
import hudson.slaves.DumbSlave;
import org.apache.tools.ant.filters.StringInputStream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.mockito.Mockito.*;

public class NodeListenerTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    private NodeListener mock;

    @Before
    public void setUp() {
        mock = mock(NodeListener.class);
        ExtensionList.lookup(NodeListener.class).add(mock);
    }

    @Test
    public void crud() throws Exception {
        DumbSlave slave = j.createSlave();
        String xml = cli(new GetNodeCommand()).invokeWithArgs(slave.getNodeName()).stdout();
        cli(new UpdateNodeCommand()).withStdin(new StringInputStream(xml)).invokeWithArgs(slave.getNodeName());
        cli(new DeleteNodeCommand()).invokeWithArgs(slave.getNodeName());

        cli(new CreateNodeCommand()).withStdin(new StringInputStream(xml)).invokeWithArgs("replica");
        j.jenkins.getComputer("replica").doDoDelete();

        verify(mock, times(2)).onCreated(any(Node.class));
        verify(mock, times(1)).onUpdated(any(Node.class), any(Node.class));
        verify(mock, times(2)).onDeleted(any(Node.class));
        verifyNoMoreInteractions(mock);
    }

    private CLICommandInvoker cli(CLICommand cmd) {
        return new CLICommandInvoker(j, cmd);
    }
}
