package hudson.model;

import com.google.common.base.Throwables;
import org.apache.commons.io.FileUtils;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.recipes.LocalData;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static java.lang.System.currentTimeMillis;
import static java.util.Collections.emptyMap;
import static org.apache.commons.io.FileUtils.writeStringToFile;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertNotNull;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Kohsuke Kawaguchi
 */
public class UserPropertyTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("JENKINS-9062")
    public void test() throws Exception {
        User u = User.get("foo");
        u.addProperty(new UserProperty1());
        j.configRoundtrip(u);
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
    public void nestedUserReference() throws Exception {
        // first time it loads from FS into object
        User user = User.get("nestedUserReference", false, emptyMap());
        assertThat("nested reference should be updated after jenkins start", user, nestedUserSet());

        SetUserUserProperty property = user.getProperty(SetUserUserProperty.class);
        File testFile = property.getInnerUserClass().userFile;
        List<String> fileLines = FileUtils.readLines(testFile);
        assertThat(fileLines, hasSize(1));

        j.configRoundtrip(user);

        user = User.get("nestedUserReference", false, Collections.emptyMap());
        assertThat("nested reference should exist after user configuration change", user, nestedUserSet());

        fileLines = FileUtils.readLines(testFile);
        assertThat(fileLines, hasSize(1));
    }

    public static Matcher<User> nestedUserSet() {
        return new BaseMatcher<User>() {
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
    public static class InnerUserClass extends AbstractDescribableImpl<InnerUserClass> {
        private transient User user;

        private transient File userFile;

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
                writeStringToFile(userFile, String.valueOf(currentTimeMillis()), true);
            } catch (IOException e) {
                Throwables.propagate(e);
            }
        }

        private File getUserFile() throws IOException {
            userFile =  File.createTempFile("user", ".txt");
            userFile.deleteOnExit();
            if (!userFile.exists()) {
                userFile.createNewFile();
            }
            return userFile;
        }

        @Override
        public DescriptorImpl getDescriptor() {
            return (DescriptorImpl) super.getDescriptor();
        }

        @TestExtension
        public static class DescriptorImpl extends Descriptor<InnerUserClass> {
        }
    }

}
