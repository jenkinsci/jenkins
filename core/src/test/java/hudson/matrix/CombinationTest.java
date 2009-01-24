package hudson.matrix;

import junit.framework.TestCase;

import java.util.Map;
import java.util.HashMap;

/**
 * @author Kohsuke Kawaguchi
 */
public class CombinationTest extends TestCase {
    @SuppressWarnings({"RedundantStringConstructorCall"})
    public void testEval() {
        Map<String,String> r = new HashMap<String, String>();
        r.put("a","X");
        r.put("b",new String("Y")); // make sure this 'Y' is not the same object as literal "Y".
        Combination c = new Combination(r);
        assertTrue(c.evalGroovyExpression(null));
        assertTrue(c.evalGroovyExpression("    "));
        assertTrue(c.evalGroovyExpression("true"));
        assertTrue(c.evalGroovyExpression("a=='X'"));
        assertTrue(c.evalGroovyExpression("b=='Y'"));
        assertTrue(c.evalGroovyExpression("(a=='something').implies(b=='other')"));

    }
}
