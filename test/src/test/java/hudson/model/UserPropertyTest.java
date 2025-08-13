package hudson.model;

import static java.lang.System.currentTimeMillis;
import static java.util.Collections.emptyMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.htmlunit.html.HtmlFormUtil.submit;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author Kohsuke Kawaguchi
 */
@WithJenkins
class UserPropertyTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    private User configRoundtrip(User u) throws Exception {
        submit(j.createWebClient().goTo(u.getUrl() + "/account/").getFormByName("config"));
        return u;
    }

    @Test
    @Issue("JENKINS-9062")
    void test() throws Exception {
        User u = User.get("foo");
        u.addProperty(new UserProperty1());
        configRoundtrip(u);
        for (UserProperty p : u.getAllProperties())
            assertNotNull(p);
    }

    public static class UserProperty1 extends UserProperty {
        @TestExtension
        public static class DescriptorImpl extends UserPropertyDescriptor {
            @Override
            public UserProperty newInstance(User user) {
                return new UserProperty1();
            }
        }
    }

    public static class UserProperty2 extends UserProperty {
        @TestExtension
        public static class DescriptorImpl extends UserPropertyDescriptor {
            @Override
            public boolean isEnabled() {
                return false;
            }

            @Override
            public UserProperty newInstance(User user) {
                return new UserProperty1();
            }
        }
    }

    @Test
    @LocalData
    void nestedUserReference() throws Exception {
        // first time it loads from FS into object
        User user = User.get("nestedUserReference", false, emptyMap());
        assertThat("nested reference should be updated after jenkins start", user, nestedUserSet());

        SetUserUserProperty property = user.getProperty(SetUserUserProperty.class);
        File testFile = property.getInnerUserClass().userFile;
        List<String> fileLines = Files.readAllLines(testFile.toPath(), StandardCharsets.US_ASCII);
        assertThat(fileLines, hasSize(1));

        configRoundtrip(user);

        user = User.get("nestedUserReference", false, Collections.emptyMap());
        assertThat("nested reference should exist after user configuration change", user, nestedUserSet());

        fileLines = Files.readAllLines(testFile.toPath(), StandardCharsets.US_ASCII);
        assertThat(fileLines, hasSize(1));
    }

    private static Matcher<User> nestedUserSet() {
        return new BaseMatcher<>() {
            @Override
            public boolean matches(Object item) {
                User user = (User) item;
                assertThat(user, notNullValue());
                final SetUserUserProperty prop = user.getProperty(SetUserUserProperty.class);
                assertThat(prop, notNullValue());
                assertThat(prop.getOwner(), notNullValue());
                assertThat(prop.getOwner(), is(user));

                final InnerUserClass innerUserClass = prop.getInnerUserClass();
                assertThat(innerUserClass, notNullValue());
                final User innerUser = innerUserClass.getUser();
                assertThat(innerUser, notNullValue());
                assertThat(innerUser, is(user));
                return true;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("User object should contain initialised inner fields");
            }
        };
    }

    /**
     * User property that need update User object reference for InnerUserClass.
     */
    public static class SetUserUserProperty extends UserProperty {
        private InnerUserClass innerUserClass = new InnerUserClass();

        @SuppressWarnings("checkstyle:redundantmodifier")
        @DataBoundConstructor
        public SetUserUserProperty() {
        }

        public InnerUserClass getInnerUserClass() {
            return innerUserClass;
        }

        public User getOwner() {
            return user;
        }

        @Override
        protected void setUser(User u) {
            super.setUser(u);
            innerUserClass.setUser(u);
        }

        public Object readResolve() {
            if (innerUserClass == null) {
                innerUserClass = new InnerUserClass();
            }
            return this;
        }

        @TestExtension
        public static class DescriptorImpl extends UserPropertyDescriptor {
            @Override
            public UserProperty newInstance(User user) {
                if (user.getId().equals("nesteduserreference")) {
                    return new SetUserUserProperty();
                }
                return null;
            }
        }
    }

    /**
     * Class that should get setUser(User) object reference update.
     */
    public static class InnerUserClass implements Describable<InnerUserClass> {
        private transient User user;

        private transient File userFile;

        @SuppressWarnings("checkstyle:redundantmodifier")
        @DataBoundConstructor
        public InnerUserClass() {
        }

        public User getUser() {
            return user;
        }

        /**
         * Should be initialised separately.
         */
        public void setUser(User user) {
            this.user = user;
            try {
                File userFile = getUserFile();
                Files.writeString(userFile.toPath(), String.valueOf(currentTimeMillis()), StandardCharsets.US_ASCII, StandardOpenOption.APPEND);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private File getUserFile() throws IOException {
            userFile =  Files.createTempFile("user", ".txt").toFile();
            userFile.deleteOnExit();
            if (!userFile.exists()) {
                userFile.createNewFile();
            }
            return userFile;
        }

        @Override
        public DescriptorImpl getDescriptor() {
            return (DescriptorImpl) Describable.super.getDescriptor();
        }

        @TestExtension
        public static class DescriptorImpl extends Descriptor<InnerUserClass> {
        }
    }

}
