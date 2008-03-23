package hudson.model;

import junit.framework.TestCase;

import java.util.GregorianCalendar;
import java.util.List;

/**
 * @author Kohsuke Kawaguchi
 */
public class RunTest extends TestCase {
    private List<Run<?,?>.Artifact> createArtifactList(String... paths) {
        Run<?,?> r = new Run(null,new GregorianCalendar()) {

			public int compareTo(Object arg0) {
				return 0;
			}
        };
        Run<?,?>.ArtifactList list = r.new ArtifactList();
        for (String p : paths) {
            list.add(r.new Artifact(p));
        }
        list.computeDisplayName();
        return list;
    }
    
    public void testArtifactListDisambiguation1() {
        List<Run<?, ?>.Artifact> a = createArtifactList("a/b/c.xml", "d/f/g.xml", "h/i/j.xml");
        assertEquals(a.get(0).getDisplayPath(),"c.xml");
        assertEquals(a.get(1).getDisplayPath(),"g.xml");
        assertEquals(a.get(2).getDisplayPath(),"j.xml");
    }

    public void testArtifactListDisambiguation2() {
        List<Run<?, ?>.Artifact> a = createArtifactList("a/b/c.xml", "d/f/g.xml", "h/i/g.xml");
        assertEquals(a.get(0).getDisplayPath(),"c.xml");
        assertEquals(a.get(1).getDisplayPath(),"f/g.xml");
        assertEquals(a.get(2).getDisplayPath(),"i/g.xml");
    }

    public void testArtifactListDisambiguation3() {
        List<Run<?, ?>.Artifact> a = createArtifactList("a.xml","a/a.xml");
        assertEquals(a.get(0).getDisplayPath(),"a.xml");
        assertEquals(a.get(1).getDisplayPath(),"a/a.xml");
    }
}
