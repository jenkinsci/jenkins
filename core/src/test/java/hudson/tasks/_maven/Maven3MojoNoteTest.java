package hudson.tasks._maven;

import static org.junit.Assert.assertEquals;
import hudson.MarkupText;

import org.junit.Test;

public class Maven3MojoNoteTest {

	@Test
	public void testAnnotateMavenPlugin() {
		assertEquals("[INFO] <b class=maven-mojo>--- maven-clean-plugin:2.4.1:clean (default-clean) @ jobConfigHistory ---</b>", annotate("[INFO] --- maven-clean-plugin:2.4.1:clean (default-clean) @ jobConfigHistory ---"));
	}

	@Test
	public void testAnnotateCodehausPlugin() {
		assertEquals("[INFO] <b class=maven-mojo>--- cobertura-maven-plugin:2.4:instrument (report:cobertura) @ sardine ---</b>", annotate("[INFO] --- cobertura-maven-plugin:2.4:instrument (report:cobertura) @ sardine ---"));
	}

	@Test
	public void testAnnotateOtherPlugin() {
		assertEquals("[INFO] <b class=maven-mojo>--- gmaven-plugin:1.0-rc-5:generateTestStubs (test-in-groovy) @ jobConfigHistory ---</b>", annotate("[INFO] --- gmaven-plugin:1.0-rc-5:generateTestStubs (test-in-groovy) @ jobConfigHistory ---"));
	}

    private String annotate(String text) {
        MarkupText markupText = new MarkupText(text);
        new Maven3MojoNote().annotate(new Object(), markupText, 0);
        return markupText.toString(true);
    }

}
