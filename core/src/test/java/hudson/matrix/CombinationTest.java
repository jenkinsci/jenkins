package hudson.matrix;

import junit.framework.TestCase;

import java.util.Map;
import java.util.HashMap;

/**
 * @author Kohsuke Kawaguchi
 */
public class CombinationTest extends TestCase {
    AxisList axes = new AxisList(
            new Axis("a","X","x"),
            new Axis("b","Y","y"));

    @SuppressWarnings({"RedundantStringConstructorCall"})
    public void testEval() {
        Map<String,String> r = new HashMap<String, String>();
        r.put("a","X");
        r.put("b",new String("Y")); // make sure this 'Y' is not the same object as literal "Y".
        Combination c = new Combination(r);

        r.put("a","x");
        Combination d = new Combination(r);

        assertTrue(eval(c, null));
        assertTrue(eval(c,"    "));
        assertTrue(eval(c,"true"));
        assertTrue(eval(c,"a=='X'"));
        assertTrue(eval(c,"b=='Y'"));
        assertTrue(eval(c,"(a=='something').implies(b=='other')"));
        assertTrue(eval(c,"index%2==0")^eval(d,"index%2==0"));
        assertTrue(eval(c,"index%2==1")^eval(d,"index%2==1"));
    }

    private boolean eval(Combination c, String exp) {
        return c.evalGroovyExpression(axes, exp);
    }
}
