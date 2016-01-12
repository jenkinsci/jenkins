package hudson.node_monitors;

import org.junit.Test;

public class InodesUsageGetterTest {
	@Test
	public void get_percentage() throws Exception {
		new InodesUsageGetter().getUsedInodesPercentage();
	}
}
