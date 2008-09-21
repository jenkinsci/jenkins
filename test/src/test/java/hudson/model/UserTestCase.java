package hudson.model;

import org.jvnet.hudson.test.HudsonTestCase;

import com.gargoylesoftware.htmlunit.WebAssert;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

public class UserTestCase extends HudsonTestCase {

    public static class UserPropertyImpl extends UserProperty {

        private final String testString;
        private UserPropertyDescriptor descriptorImpl = new UserPropertyDescriptorImpl();
        
        public UserPropertyImpl(String testString) {
            this.testString = testString;
        }
        
        public String getTestString() {
            return testString;
        }

        @Override
        public UserPropertyDescriptor getDescriptor() {
            return descriptorImpl;
        }
        
        public static class UserPropertyDescriptorImpl extends UserPropertyDescriptor {

            protected UserPropertyDescriptorImpl() {
                super(UserPropertyImpl.class);
            }

            @Override
            public UserProperty newInstance(User user) {
                return null;
            }

            @Override
            public String getDisplayName() {
                return "Property";
            }
        }
    }

    /**
     * Asserts that bug# is fixed.
     */
    public void testUserPropertySummaryIsShownInUserPage() throws Exception {
        
        UserProperty property = new UserPropertyImpl("NeedleInPage");
        UserProperties.LIST.add(property.getDescriptor());
        
        User user = User.get("user-test-case");
        user.addProperty(property);
        
        HtmlPage page = new WebClient().goTo("user/user-test-case");
        WebAssert.assertTextPresent(page, "NeedleInPage");
    }
}
