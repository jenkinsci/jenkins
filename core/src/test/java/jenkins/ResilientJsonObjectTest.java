package jenkins;

import net.sf.json.JSONObject;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

/**
 * @author Kohsuke Kawaguchi
 */
public class ResilientJsonObjectTest {
    public static class Foo { public int a; }

    /**
     * {@link JSONObject} databinding should be able to ignore non-existent fields.
     */
    @Test
    @Issue("JENKINS-15105")
    public void databindingShouldIgnoreUnrecognizedJsonProperty() {
        JSONObject o = JSONObject.fromObject("{a:1,b:2}");
        Foo f = (Foo)JSONObject.toBean(o,Foo.class);
        assert f.a == 1;
    }
}
