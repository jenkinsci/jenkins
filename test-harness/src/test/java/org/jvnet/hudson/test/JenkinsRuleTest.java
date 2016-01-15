package org.jvnet.hudson.test;

import org.junit.Rule;
import org.junit.Test;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Collections;
import java.util.List;

public class JenkinsRuleTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    @Test
    public void assertEqualDataBoundBeansForNullLists() throws Exception {
        j.assertEqualDataBoundBeans(new SomeClass(null), new SomeClass(null));
    }

    @Test(expected = AssertionError.class)
    public void givenOneNullListAndOneNonnullListAssertShouldFail() throws Exception {
        j.assertEqualDataBoundBeans(new SomeClass(Collections.<String>emptyList()), new SomeClass(null));
    }

    public static class SomeClass {
        private List<String> someList;

        @DataBoundConstructor
        public SomeClass(List<String> someList) {
            this.someList = someList;
        }

        public List<String> getSomeList() {
            return someList;
        }
    }
}
