package hudson.node_monitors;

import org.junit.Test;

public class DfRunnerTest {
	@Test
	public void get_percentage() throws Exception {
		new DfRunner().getUsedInodesPercentage();
	}
}
