package jenkins.model;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import hudson.ExtensionList;
import hudson.cli.CLICommand;
import hudson.cli.CLICommandInvoker;
import hudson.cli.CreateNodeCommand;
import hudson.cli.DeleteNodeCommand;
import hudson.cli.GetNodeCommand;
import hudson.cli.UpdateNodeCommand;
import hudson.model.Node;
import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

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
        Node agent = j.createAgent();
        String xml = cli(new GetNodeCommand()).invokeWithArgs(agent.getNodeName()).stdout();
        cli(new UpdateNodeCommand()).withStdin(new ByteArrayInputStream(xml.getBytes(Charset.defaultCharset()))).invokeWithArgs(agent.getNodeName());
        cli(new DeleteNodeCommand()).invokeWithArgs(agent.getNodeName());

        cli(new CreateNodeCommand()).withStdin(new ByteArrayInputStream(xml.getBytes(Charset.defaultCharset()))).invokeWithArgs("replica");
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
