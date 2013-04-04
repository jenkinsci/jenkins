package hudson.tasks.junit;

import hudson.Extension;

import org.jvnet.hudson.test.HudsonTestCase;

public class TestNameTransformerTest extends HudsonTestCase {
	
	private static final String UniqueNameForTest = "unique-name-to-test-name-transformer";
	
	@Extension
	public static class TestTransformer extends TestNameTransformer {
		@Override
		public String transformName(String name) {
			if (UniqueNameForTest.equals(name)) {
				return name + "-transformed";
			}
			return name;
		}
	}
	
	public void testNameIsTransformed() {
		assertEquals(UniqueNameForTest + "-transformed", TestNameTransformer.getTransformedName(UniqueNameForTest));
	}
	
}

