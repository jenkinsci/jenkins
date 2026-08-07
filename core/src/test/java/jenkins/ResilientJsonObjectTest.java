package jenkins;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.sf.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;

/**
 * @author Kohsuke Kawaguchi
 */
class ResilientJsonObjectTest {
    public static class Foo { public int a; }

    /**
     * {@link JSONObject} databinding should be able to ignore non-existent fields.
     */
    @Test
    @Issue("JENKINS-15105")
    void databindingShouldIgnoreUnrecognizedJsonProperty() {
        JSONObject o = JSONObject.fromObject("{a:1,b:2}");
        Foo f = (Foo) JSONObject.toBean(o, Foo.class);
        assertEquals(1, f.a);
    }
}
