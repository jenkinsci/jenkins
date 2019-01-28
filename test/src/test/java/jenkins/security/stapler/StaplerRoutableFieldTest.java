package jenkins.security.stapler;

import org.junit.Test;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.TestExtension;

@Issue("SECURITY-595")
@For({StaplerDispatchable.class, StaplerNotDispatchable.class, TypedFilter.class})
public class StaplerRoutableFieldTest extends StaplerAbstractTest {
    @TestExtension
    public static class TestRootAction extends AbstractUnprotectedRootAction {
        @Override
        public String getUrlName() {
            return "test";
        }
        
        public Renderable renderableNotAnnotated = new Renderable();
        
        public ParentRenderable parentRenderableNotAnnotated = new ParentRenderable();
        
        public Object objectNotAnnotated = new Renderable();
        
        @StaplerDispatchable
        public Renderable renderableAnnotatedOk = new Renderable();
        
        @StaplerDispatchable
        public ParentRenderable parentRenderableAnnotatedOk = new ParentRenderable();
        
        @StaplerDispatchable
        public Object objectAnnotatedOk = new Renderable();
        
        @StaplerNotDispatchable
        public Renderable renderableAnnotatedKo = new Renderable();
        
        @StaplerNotDispatchable
        public Object objectAnnotatedKo = new Renderable();
        
        @StaplerDispatchable
        @StaplerNotDispatchable
        public Renderable renderableDoubleAnnotated = new Renderable();
        
        @StaplerDispatchable
        @StaplerNotDispatchable
        public Object objectDoubleAnnotated = new Renderable();
        
        public static Renderable staticRenderableNotAnnotated = new Renderable();
        
        public static Object staticObjectNotAnnotated = new Renderable();
        
        @StaplerDispatchable
        public static Renderable staticRenderableAnnotatedOk = new Renderable();
        
        @StaplerDispatchable
        public static Object staticObjectAnnotatedOk = new Renderable();
    }
    
    @Test
    public void testFieldNotAnnotated() throws Exception {
        assertReachable("test/renderableNotAnnotated/");
        assertReachable("test/renderableNotAnnotated/valid/");
        
        assertNotReachable("test/parentRenderableNotAnnotated/");
        assertNotReachable("test/parentRenderableNotAnnotated/renderable/");
        assertNotReachable("test/parentRenderableNotAnnotated/renderable/valid/");
        
        assertNotReachable("test/objectNotAnnotated/");
        assertNotReachable("test/objectNotAnnotated/valid/");
    }
    
    @Test
    public void testFieldNotAnnotated_escapeHatch() throws Exception {
        boolean currentValue = TypedFilter.SKIP_TYPE_CHECK;
        try {
            TypedFilter.SKIP_TYPE_CHECK = true;
            // to apply the new configuration
            webApp.clearMetaClassCache();
            
            assertReachable("test/renderableNotAnnotated/");
            assertReachable("test/renderableNotAnnotated/valid/");
    
            assertNotReachable("test/parentRenderableNotAnnotated/");
            assertReachable("test/parentRenderableNotAnnotated/renderable/");
            assertReachable("test/parentRenderableNotAnnotated/renderable/valid/");
        } finally {
            TypedFilter.SKIP_TYPE_CHECK = currentValue;
            // to reset the configuration
            webApp.clearMetaClassCache();
        }
    }
    
    @Test
    public void testFieldAnnotatedOk() throws Exception {
        assertReachable("test/renderableAnnotatedOk/");
        assertReachable("test/renderableAnnotatedOk/valid/");
        
        assertReachable("test/objectAnnotatedOk/");
        assertReachable("test/objectAnnotatedOk/valid/");
    }
    
    @Test
    public void testFieldAnnotatedKo() throws Exception {
        assertNotReachable("test/renderableAnnotatedKo/");
        assertNotReachable("test/renderableAnnotatedKo/valid/");
        
        assertNotReachable("test/objectAnnotatedKo/");
        assertNotReachable("test/objectAnnotatedKo/valid/");
    }
    
    @Test
    public void testFieldDoubleAnnotated() throws Exception {
        assertNotReachable("test/renderableDoubleAnnotated/");
        assertNotReachable("test/renderableDoubleAnnotated/valid/");
        
        assertNotReachable("test/objectDoubleAnnotated/");
        assertNotReachable("test/objectDoubleAnnotated/valid/");
    }
    
    @Test
    public void testStaticFieldNotAnnotated() throws Exception {
        assertNotReachable("test/staticRenderableNotAnnotated/");
        assertNotReachable("test/staticRenderableNotAnnotated/valid/");
        
        assertNotReachable("test/staticObjectNotAnnotated/");
        assertNotReachable("test/staticObjectNotAnnotated/valid/");
    }
    
    @Test
    public void testStaticFieldNotAnnotated_escapeHatch() throws Exception {
        boolean currentValue = TypedFilter.PROHIBIT_STATIC_ACCESS;
        try {
            TypedFilter.PROHIBIT_STATIC_ACCESS = false;
            // to apply the new configuration
            webApp.clearMetaClassCache();
            
            assertReachable("test/staticRenderableNotAnnotated/");
            assertReachable("test/staticRenderableNotAnnotated/valid/");
            
            assertNotReachable("test/staticObjectNotAnnotated/");
            assertNotReachable("test/staticObjectNotAnnotated/valid/");
        } finally {
            TypedFilter.PROHIBIT_STATIC_ACCESS = currentValue;
            // to reset the configuration
            webApp.clearMetaClassCache();
        }
    }
    
    @Test
    public void testStaticFieldAnnotatedOk() throws Exception {
        assertReachable("test/staticRenderableAnnotatedOk/");
        assertReachable("test/staticRenderableAnnotatedOk/valid/");
        
        assertReachable("test/staticObjectAnnotatedOk/");
        assertReachable("test/staticObjectAnnotatedOk/valid/");
    }
}
