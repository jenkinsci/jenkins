package jenkins;

import org.junit.Test;
import static org.junit.Assert.assertNotNull;


public class BreadCrumbTest {

    @Test
    public void testNonNullBreadCrumbItemList() {
        //The breadcrumb Items list  would at least have the home root breadcrumb
        assertNotNull(new BreadCrumb().generateBreadCrumbs());
    }

}
