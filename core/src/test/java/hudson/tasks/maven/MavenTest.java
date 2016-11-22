package hudson.tasks.maven;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import hudson.tasks.Maven;

import org.junit.Test;

public class MavenTest {

	@Test
	public void testMavenTaskWithoutPropertiesOrBuildVariables() {
		Maven mvn = config();

		System.out.println("hello World!");
	}


	private Maven config() {
		return new Maven("clean deploy",
			"mavenInstName",
			null,
			null, // properties
			null,
			false,
			null,
			null,
			false);
	}
}
