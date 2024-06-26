/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package jenkins.security.stapler;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.ExtensionList;
import hudson.model.FreeStyleProject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.WebMethod;

@Issue("SECURITY-400")
public class StaticRoutingDecisionProviderTest extends StaplerAbstractTest {
    @TestExtension
    public static class ContentProvider extends AbstractUnprotectedRootAction {
        // simulate side effect
        public static boolean called = false;
        public static boolean called2 = false;

        public FreeStyleProject getJob() {
            called = true;
            return (FreeStyleProject) Jenkins.get().getItem("testProject");
        }

        public String getString() {
            called = true;
            return "a";
        }

        // cannot provide side-effect since the String has no side-effect methods
        public Object getObjectString() {
            called = true;
            return "a";
        }

        public static String OBJECT_CUSTOM_SIGNATURE = "method jenkins.security.stapler.StaticRoutingDecisionProviderTest$ContentProvider getObjectCustom";

        // but it opens wide range of potentially dangerous classes
        public Object getObjectCustom() {
            called = true;
            return new Object() {
                // in order to provide a web entry-point
                public void doIndex() {
                    called2 = true;
                    replyOk();
                }
            };
        }
    }

    @Before
    public void preparation() throws Exception {
        ContentProvider.called = false;
        ContentProvider.called2 = false;
    }

    @Before
    public void resetWhitelist() throws Exception {
        ExtensionList.lookupSingleton(StaticRoutingDecisionProvider.class).resetAndSave();
    }

    @Test
    public void test_job_index() throws Exception {
        j.createFreeStyleProject("testProject");
        assertReachableWithoutOk("contentProvider/job/");
        assertTrue(ContentProvider.called);
    }

    @Test
    public void test_string() throws Exception {
        assertNotReachable("contentProvider/string/");
        assertFalse(ContentProvider.called);
    }

    @Test
    public void test_objectString() throws Exception {
        assertNotReachable("contentProvider/objectString/");
        assertFalse(ContentProvider.called);
    }

    @Test
    public void test_objectCustom() throws Exception {
        assertNotReachable("contentProvider/objectCustom/");
        assertFalse(ContentProvider.called);
    }

    //for more test about the whitelist initial loading, please refer to StaticRoutingDecisionProvider2Test
    @Test
    public void test_objectCustom_withUserControlledSavedWhitelist() throws Throwable {
        String whitelist = ContentProvider.OBJECT_CUSTOM_SIGNATURE + "\n";
        Path whitelistFile = j.jenkins.getRootDir().toPath().resolve("stapler-whitelist.txt");
        Files.writeString(whitelistFile, whitelist, StandardCharsets.UTF_8);
        ExtensionList.lookupSingleton(StaticRoutingDecisionProvider.class).reload();
        try {
            assertNotReachable("contentProvider/objectString/");
            assertFalse(ContentProvider.called);
            assertGetMethodRequestWasBlockedAndResetFlag();
            assertReachable("contentProvider/objectCustom/");
            assertTrue(ContentProvider.called);
        } finally {
            Files.deleteIfExists(whitelistFile);
            ExtensionList.lookupSingleton(StaticRoutingDecisionProvider.class).reload();
        }
    }

    @Test
    public void test_objectCustom_withUserControlledEditedWhitelist() throws Exception {
        try {
            assertNotReachable("contentProvider/objectString/");
            assertFalse(ContentProvider.called);
            assertNotReachable("contentProvider/objectCustom/");
            assertFalse(ContentProvider.called);

            ExtensionList.lookupSingleton(StaticRoutingDecisionProvider.class).add(ContentProvider.OBJECT_CUSTOM_SIGNATURE);

            assertNotReachable("contentProvider/objectString/");
            assertFalse(ContentProvider.called);
            assertFalse(ContentProvider.called2);
            assertGetMethodRequestWasBlockedAndResetFlag();

            assertReachable("contentProvider/objectCustom/");
            assertTrue(ContentProvider.called);
            assertTrue(ContentProvider.called2);

            ContentProvider.called = false;
            ContentProvider.called2 = false;

            ExtensionList.lookupSingleton(StaticRoutingDecisionProvider.class).remove(ContentProvider.OBJECT_CUSTOM_SIGNATURE);

            assertNotReachable("contentProvider/objectString/");
            assertFalse(ContentProvider.called);
            assertNotReachable("contentProvider/objectCustom/");
            assertFalse(ContentProvider.called);
        } finally {
            //TODO check if the file is created per test or in general
            ExtensionList.lookupSingleton(StaticRoutingDecisionProvider.class).reload();
        }
    }

    @Test
    public void test_objectCustom_withStandardWhitelist() throws Exception {
        assertNotReachable("contentProvider/objectString/");
        assertFalse(ContentProvider.called);
        assertGetMethodRequestWasBlockedAndResetFlag();
        assertNotReachable("contentProvider/objectCustom/");
        assertFalse(ContentProvider.called);

        StaticRoutingDecisionProvider whitelist = ExtensionList.lookupSingleton(StaticRoutingDecisionProvider.class);

        { // add entry in the set loaded from the standard whitelist file and reload
            Method resetMetaClassCache = StaticRoutingDecisionProvider.class.getDeclaredMethod("resetMetaClassCache");
            resetMetaClassCache.setAccessible(true);

            Field field = StaticRoutingDecisionProvider.class.getDeclaredField("whitelistSignaturesFromFixedList");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Set<String> standardWhitelist = (Set<String>) field.get(whitelist);

            standardWhitelist.add(ContentProvider.OBJECT_CUSTOM_SIGNATURE);
            // just call this method to avoid to reload the file and so override our new signature
            resetMetaClassCache.invoke(whitelist);
        }

        assertNotReachable("contentProvider/objectString/");
        assertFalse(ContentProvider.called);
        assertFalse(ContentProvider.called2);
        assertGetMethodRequestWasBlockedAndResetFlag();
        assertReachable("contentProvider/objectCustom/");
        assertTrue(ContentProvider.called);
        assertTrue(ContentProvider.called2);

        { // reset to previous state
            ContentProvider.called = false;
            ContentProvider.called2 = false;

            whitelist.reload();
        }

        assertNotReachable("contentProvider/objectString/");
        assertFalse(ContentProvider.called);
        assertNotReachable("contentProvider/objectCustom/");
        assertFalse(ContentProvider.called);
    }

    @TestExtension
    public static class ActionWithWhitelist extends AbstractUnprotectedRootAction {
        @Override
        public @CheckForNull String getUrlName() {
            return "do-action";
        }

        public static String DO_ACTION_SIGNATURE = "method jenkins.security.stapler.StaticRoutingDecisionProviderTest$ActionWithWhitelist doAction org.kohsuke.stapler.StaplerRequest2";

        public void doAction(StaplerRequest2 request) {
            replyOk();
        }

        public static String DO_ACTION_STAPLER_ROUTABLE_SIGNATURE = "method jenkins.security.stapler.StaticRoutingDecisionProviderTest$ActionWithWhitelist doActionWithStaplerDispatchable org.kohsuke.stapler.StaplerRequest2";

        @StaplerDispatchable
        public void doActionWithStaplerDispatchable(StaplerRequest2 request) {
            replyOk();
        }

        public static String DO_ACTION_STAPLER_NONROUTABLE_SIGNATURE = "method jenkins.security.stapler.StaticRoutingDecisionProviderTest$ActionWithWhitelist doActionWithStaplerNotDispatchable org.kohsuke.stapler.StaplerRequest2";

        @StaplerNotDispatchable
        public void doActionWithStaplerNotDispatchable(StaplerRequest2 request) {
            replyOk();
        }

        public static String DO_ACTION_STAPLER_WEBMETHOD_SIGNATURE = "method jenkins.security.stapler.StaticRoutingDecisionProviderTest$ActionWithWhitelist doActionWithWebMethod org.kohsuke.stapler.StaplerRequest2";

        @WebMethod(name = "actionWithWebMethod")
        public void doActionWithWebMethod(StaplerRequest2 request) {
            replyOk();
        }
    }

    @Test
    public void doAction_regular() throws Exception {
        assertReachable("do-action/action/");

        ExtensionList.lookupSingleton(StaticRoutingDecisionProvider.class).add(ActionWithWhitelist.DO_ACTION_SIGNATURE);

        assertReachable("do-action/action/");

        ExtensionList.lookupSingleton(StaticRoutingDecisionProvider.class).remove(ActionWithWhitelist.DO_ACTION_SIGNATURE);

        assertReachable("do-action/action/");

        ExtensionList.lookupSingleton(StaticRoutingDecisionProvider.class).addBlacklistSignature(ActionWithWhitelist.DO_ACTION_SIGNATURE);

        assertNotReachable("do-action/action/");
        assertDoActionRequestWasBlockedAndResetFlag();

        ExtensionList.lookupSingleton(StaticRoutingDecisionProvider.class).removeBlacklistSignature(ActionWithWhitelist.DO_ACTION_SIGNATURE);

        assertReachable("do-action/action/");
    }

    @Test
    public void doAction_actionWithStaplerDispatchable() throws Exception {
        assertReachable("do-action/actionWithStaplerDispatchable/");

        ExtensionList.lookupSingleton(StaticRoutingDecisionProvider.class).addBlacklistSignature(ActionWithWhitelist.DO_ACTION_STAPLER_ROUTABLE_SIGNATURE);

        assertReachable("do-action/actionWithStaplerDispatchable/");
    }

    @Test
    public void doAction_actionWithWebMethod() throws Exception {
        assertReachable("do-action/actionWithWebMethod/");

        ExtensionList.lookupSingleton(StaticRoutingDecisionProvider.class).addBlacklistSignature(ActionWithWhitelist.DO_ACTION_STAPLER_WEBMETHOD_SIGNATURE);

        assertNotReachable("do-action/actionWithWebMethod/");
        assertDoActionRequestWasBlockedAndResetFlag();
    }

    @TestExtension
    public static class GetterWithWhitelist extends AbstractUnprotectedRootAction {
        @Override
        public @CheckForNull String getUrlName() {
            return "getter";
        }

        public static String GET_ITEM_SIGNATURE = "method jenkins.security.stapler.StaticRoutingDecisionProviderTest$GetterWithWhitelist getItem";

        public Renderable getItem() {
            return new Renderable();
        }

        public static String GET_ITEM_STAPLER_ROUTABLE_SIGNATURE = "method jenkins.security.stapler.StaticRoutingDecisionProviderTest$GetterWithWhitelist getItemWithStaplerDispatchable";

        @StaplerDispatchable
        public Renderable getItemWithStaplerDispatchable() {
            return new Renderable();
        }

        public static String GET_ITEM_STAPLER_NONROUTABLE_SIGNATURE = "method jenkins.security.stapler.StaticRoutingDecisionProviderTest$GetterWithWhitelist getItemWithStaplerNotDispatchable";

        @StaplerNotDispatchable
        public Renderable getItemWithStaplerNotDispatchable() {
            return new Renderable();
        }
    }

    @Test
    public void getItem_regular() throws Exception {
        assertReachable("getter/item/");
        assertReachable("getter/item/valid");

        ExtensionList.lookupSingleton(StaticRoutingDecisionProvider.class).addBlacklistSignature(GetterWithWhitelist.GET_ITEM_SIGNATURE);

        assertNotReachable("getter/item/");
        assertGetMethodRequestWasBlockedAndResetFlag();
        assertNotReachable("getter/item/valid");
        assertGetMethodRequestWasBlockedAndResetFlag();
    }

    @Test
    public void getItem_getterWithStaplerDispatchable() throws Exception {
        assertReachable("getter/itemWithStaplerDispatchable/");
        assertReachable("getter/itemWithStaplerDispatchable/valid");

        ExtensionList.lookupSingleton(StaticRoutingDecisionProvider.class).addBlacklistSignature(GetterWithWhitelist.GET_ITEM_STAPLER_ROUTABLE_SIGNATURE);

        // Annotation overrides whitelist/blacklist
        assertReachable("getter/itemWithStaplerDispatchable/");
        assertReachable("getter/itemWithStaplerDispatchable/valid");
    }

    @Test
    public void getItem_getterWithStaplerNotDispatchable() throws Exception {
        assertNotReachable("getter/itemWithStaplerNotDispatchable/");
        assertGetMethodRequestWasBlockedAndResetFlag();
        assertNotReachable("getter/itemWithStaplerNotDispatchable/valid");
        assertGetMethodRequestWasBlockedAndResetFlag();

        ExtensionList.lookupSingleton(StaticRoutingDecisionProvider.class).add(GetterWithWhitelist.GET_ITEM_STAPLER_NONROUTABLE_SIGNATURE);

        // Annotation overrides whitelist/blacklist
        assertNotReachable("getter/itemWithStaplerNotDispatchable/");
        assertGetMethodRequestWasBlockedAndResetFlag();
        assertNotReachable("getter/itemWithStaplerNotDispatchable/valid");
        assertGetMethodRequestWasBlockedAndResetFlag();
    }

    @TestExtension
    public static class FieldWithWhitelist extends AbstractUnprotectedRootAction {
        @Override
        public @CheckForNull String getUrlName() {
            return "field";
        }

        public static String FIELD_SIGNATURE = "field jenkins.security.stapler.StaticRoutingDecisionProviderTest$FieldWithWhitelist renderable";

        public Renderable renderable = new Renderable();

        public static String FIELD_STAPLER_ROUTABLE_SIGNATURE = "field jenkins.security.stapler.StaticRoutingDecisionProviderTest$FieldWithWhitelist renderableWithStaplerDispatchable";

        @StaplerDispatchable
        public Renderable renderableWithStaplerDispatchable = new Renderable();

        public static String FIELD_STAPLER_NONROUTABLE_SIGNATURE = "field jenkins.security.stapler.StaticRoutingDecisionProviderTest$FieldWithWhitelist renderableWithStaplerNotDispatchable";

        @StaplerNotDispatchable
        public Renderable renderableWithStaplerNotDispatchable = new Renderable();

        public static String FIELD_STATIC_SIGNATURE = "staticField jenkins.security.stapler.StaticRoutingDecisionProviderTest$FieldWithWhitelist staticRenderable";

        public static Renderable staticRenderable = new Renderable();

        public static String FIELD_STATIC_STAPLER_ROUTABLE_SIGNATURE = "staticField jenkins.security.stapler.StaticRoutingDecisionProviderTest$FieldWithWhitelist staticRenderableWithStaplerDispatchable";

        @StaplerDispatchable
        public static Renderable staticRenderableWithStaplerDispatchable = new Renderable();

        public static String FIELD_STATIC_STAPLER_NONROUTABLE_SIGNATURE = "staticField jenkins.security.stapler.StaticRoutingDecisionProviderTest$FieldWithWhitelist staticRenderableWithStaplerNotDispatchable";

        @StaplerNotDispatchable
        public static Renderable staticRenderableWithStaplerNotDispatchable = new Renderable();
    }

    @Test
    public void field_regular() throws Exception {
        assertReachable("field/renderable/");
        assertReachable("field/renderable/valid");

        ExtensionList.lookupSingleton(StaticRoutingDecisionProvider.class).addBlacklistSignature(FieldWithWhitelist.FIELD_SIGNATURE);

        assertNotReachable("field/renderable/");
        assertFieldRequestWasBlockedAndResetFlag();
        assertNotReachable("field/renderable/valid");
        assertFieldRequestWasBlockedAndResetFlag();
    }

    @Test
    public void field_regular_returnType() throws Exception {
        assertReachable("field/renderable/");
        assertReachable("field/renderable/valid");

        ExtensionList.lookupSingleton(StaticRoutingDecisionProvider.class).addBlacklistSignature(RENDERABLE_CLASS_SIGNATURE);

        assertNotReachable("field/renderable/");
        assertFieldRequestWasBlockedAndResetFlag();
        assertNotReachable("field/renderable/valid");
        assertFieldRequestWasBlockedAndResetFlag();

        ExtensionList.lookupSingleton(StaticRoutingDecisionProvider.class).removeBlacklistSignature(RENDERABLE_CLASS_SIGNATURE);

        assertReachable("field/renderable/");
        assertReachable("field/renderable/valid");

        ExtensionList.lookupSingleton(StaticRoutingDecisionProvider.class).add(RENDERABLE_CLASS_SIGNATURE);
        // method is checked first as it's more specific
        ExtensionList.lookupSingleton(StaticRoutingDecisionProvider.class).addBlacklistSignature(FieldWithWhitelist.FIELD_SIGNATURE);

        assertNotReachable("field/renderable/");
        assertFieldRequestWasBlockedAndResetFlag();
        assertNotReachable("field/renderable/valid");
        assertFieldRequestWasBlockedAndResetFlag();

        // reverse, now we blacklist the type but whitelist the method => it's ok
        ExtensionList.lookupSingleton(StaticRoutingDecisionProvider.class).remove(RENDERABLE_CLASS_SIGNATURE);
        ExtensionList.lookupSingleton(StaticRoutingDecisionProvider.class).removeBlacklistSignature(FieldWithWhitelist.FIELD_SIGNATURE);

        ExtensionList.lookupSingleton(StaticRoutingDecisionProvider.class).addBlacklistSignature(RENDERABLE_CLASS_SIGNATURE);
        ExtensionList.lookupSingleton(StaticRoutingDecisionProvider.class).add(FieldWithWhitelist.FIELD_SIGNATURE);

        assertReachable("field/renderable/");
        assertReachable("field/renderable/valid");
    }

    @Test
    public void field_withStaplerDispatchable() throws Exception {
        assertReachable("field/renderableWithStaplerDispatchable/");
        assertReachable("field/renderableWithStaplerDispatchable/valid");

        ExtensionList.lookupSingleton(StaticRoutingDecisionProvider.class).addBlacklistSignature(FieldWithWhitelist.FIELD_STAPLER_ROUTABLE_SIGNATURE);

        assertReachable("field/renderableWithStaplerDispatchable/");
    }

    @Test
    public void field_withStaplerNotDispatchable() throws Exception {
        assertNotReachable("field/renderableWithStaplerNotDispatchable/");
        assertFieldRequestWasBlockedAndResetFlag();
        assertNotReachable("field/renderableWithStaplerNotDispatchable/valid");
        assertFieldRequestWasBlockedAndResetFlag();

        ExtensionList.lookupSingleton(StaticRoutingDecisionProvider.class).add(FieldWithWhitelist.FIELD_STAPLER_NONROUTABLE_SIGNATURE);

        assertNotReachable("field/renderableWithStaplerNotDispatchable/");
        assertFieldRequestWasBlockedAndResetFlag();
        assertNotReachable("field/renderableWithStaplerNotDispatchable/valid");
        assertFieldRequestWasBlockedAndResetFlag();
    }

    @Test
    public void fieldStatic_regular() throws Exception {
        assertNotReachable("field/staticRenderable/");
        assertFieldRequestWasBlockedAndResetFlag();
        assertNotReachable("field/staticRenderable/valid");
        assertFieldRequestWasBlockedAndResetFlag();

        ExtensionList.lookupSingleton(StaticRoutingDecisionProvider.class).add(FieldWithWhitelist.FIELD_STATIC_SIGNATURE);

        assertReachable("field/staticRenderable/");
        assertReachable("field/staticRenderable/valid");
    }

    @Test
    public void fieldStatic_withStaplerDispatchable() throws Exception {
        assertReachable("field/staticRenderableWithStaplerDispatchable/");
        assertReachable("field/staticRenderableWithStaplerDispatchable/valid");

        // doesn't do anything
        ExtensionList.lookupSingleton(StaticRoutingDecisionProvider.class).addBlacklistSignature(FieldWithWhitelist.FIELD_STATIC_STAPLER_ROUTABLE_SIGNATURE);

        assertReachable("field/staticRenderableWithStaplerDispatchable/");
    }

    @Test
    public void fieldStatic_withStaplerNotDispatchable() throws Exception {
        assertNotReachable("field/staticRenderableWithStaplerNotDispatchable/");
        assertFieldRequestWasBlockedAndResetFlag();
        assertNotReachable("field/staticRenderableWithStaplerNotDispatchable/valid");
        assertFieldRequestWasBlockedAndResetFlag();

        ExtensionList.lookupSingleton(StaticRoutingDecisionProvider.class).add(FieldWithWhitelist.FIELD_STATIC_STAPLER_NONROUTABLE_SIGNATURE);

        assertNotReachable("field/staticRenderableWithStaplerNotDispatchable/");
        assertFieldRequestWasBlockedAndResetFlag();
        assertNotReachable("field/staticRenderableWithStaplerNotDispatchable/valid");
        assertFieldRequestWasBlockedAndResetFlag();
    }
}
