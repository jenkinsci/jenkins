package jenkins.security;


/**
 * Prevents problematic classes from getting de-serialized.
 *
 * @author Kohsuke Kawaguchi
 */
public class RemotingFilterClassLoader extends ClassLoader {
    private final ClassLoader actual;

    public RemotingFilterClassLoader(ClassLoader actual) {
        // intentionally not passing 'actual' as the parent classloader to the super type
        // to prevent accidental bypassing of a filter.
        this.actual = actual;
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        if (isBlacklisted(name))    throw new ClassNotFoundException(name);
        Class<?> c = actual.loadClass(name);
        if (isBlacklisted(c))       throw new ClassNotFoundException(name);
        return c;
    }

    protected boolean isBlacklisted(String name) {
        // these are coming from libraries, so protecting it by name is better as
        // some plugins might be bundling them and choosing to mask ones from core.
        if (name.startsWith("org.codehaus.groovy.runtime."))
            return true;    // ConvertedClosure is named in exploit
        if (name.startsWith("org.apache.commons.collections.functors."))
            return true;    // InvokerTransformer, InstantiateFactory, InstantiateTransformer are particularly scary

        // this package can appear in ordinary xalan.jar or com.sun.org.apache.xalan
        // the target is trax.TemplatesImpl
        if (name.contains("org.apache.xalan"))
            return true;
        return false;
    }

    protected boolean isBlacklisted(Class c) {
        /* Switched to blacklisting by name.

import org.apache.commons.collections.Transformer;
import org.codehaus.groovy.runtime.ConversionHandler;

import javax.xml.transform.Templates;

        if (Transformer.class.isAssignableFrom(c))
            return true;
        if (ConversionHandler.class.isAssignableFrom(c))
            return true;
        if (Templates.class.isAssignableFrom(c))
            return true;
        */

        return false;
    }
}

/*
    Publicized attack payload:

		ObjectInputStream.readObject()
			PriorityQueue.readObject()
				Comparator.compare() (Proxy)
					ConvertedClosure.invoke()
						MethodClosure.call()
							...
						  		Method.invoke()
									Runtime.exec()


		ObjectInputStream.readObject()
			AnnotationInvocationHandler.readObject()
				Map(Proxy).entrySet()
					AnnotationInvocationHandler.invoke()
						LazyMap.get()
							ChainedTransformer.transform()
								ConstantTransformer.transform()
								InvokerTransformer.transform()
									Method.invoke()
										Class.getMethod()
								InvokerTransformer.transform()
									Method.invoke()
										Runtime.getRuntime()
								InvokerTransformer.transform()
									Method.invoke()
										Runtime.exec()


		ObjectInputStream.readObject()
			PriorityQueue.readObject()
				...
					TransformingComparator.compare()
						InvokerTransformer.transform()
							Method.invoke()
								Runtime.exec()


		ObjectInputStream.readObject()
			SerializableTypeWrapper.MethodInvokeTypeProvider.readObject()
				SerializableTypeWrapper.TypeProvider(Proxy).getType()
					AnnotationInvocationHandler.invoke()
						HashMap.get()
				ReflectionUtils.findMethod()
				SerializableTypeWrapper.TypeProvider(Proxy).getType()
					AnnotationInvocationHandler.invoke()
						HashMap.get()
				ReflectionUtils.invokeMethod()
					Method.invoke()
						Templates(Proxy).newTransformer()
							AutowireUtils.ObjectFactoryDelegatingInvocationHandler.invoke()
								ObjectFactory(Proxy).getObject()
									AnnotationInvocationHandler.invoke()
										HashMap.get()
								Method.invoke()
									TemplatesImpl.newTransformer()
										TemplatesImpl.getTransletInstance()
											TemplatesImpl.defineTransletClasses()
												TemplatesImpl.TransletClassLoader.defineClass()
													Pwner*(Javassist-generated).<static init>
														Runtime.exec()

 */
