package hudson.node_monitors;

import static org.junit.Assert.*;

import java.text.ParseException;

import org.junit.Test;

public class InodesMonitorTest {
	@Test(expected = ParseException.class)
	public void check_threshold_exception() throws Exception {
		new InodesMonitor("110%");
	}

	@Test(expected = ParseException.class)
	public void check_threshold_exception2() throws Exception {
		new InodesMonitor(",5%");
	}

	@Test
	public void threshold_ok() throws Exception {
		new InodesMonitor("5%");
		new InodesMonitor("50%");
		new InodesMonitor("99%");
	}

	@Test
	public void parse() throws Exception {
		assertEquals(8, InodesMonitor.parse("8%"));
	}
}
