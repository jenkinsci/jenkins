/*
 * Copyright 2004-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package hudson.util.spring;

import groovy.lang.Binding;
import groovy.lang.Closure;
import groovy.lang.GString;
import groovy.lang.GroovyObject;
import groovy.lang.GroovyObjectSupport;
import groovy.lang.GroovyShell;
import groovy.lang.MetaClass;
import groovy.lang.MissingMethodException;
import org.apache.commons.lang.ArrayUtils;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.support.ManagedMap;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;

/**
 * <p>Runtime bean configuration wrapper. Like a Groovy builder, but more of a DSL for
 * Spring configuration. Allows syntax like:</p>
 *
 * <pre>
 * import org.hibernate.SessionFactory
 * import org.apache.commons.dbcp.BasicDataSource
 *
 * BeanBuilder builder = new BeanBuilder()
 * builder.beans {
 *   dataSource(BasicDataSource) {                  // <--- invokeMethod
 *      driverClassName = "org.hsqldb.jdbcDriver"
 *      url = "jdbc:hsqldb:mem:grailsDB"
 *      username = "sa"                            // <-- setProperty
 *      password = ""
 *      settings = [mynew:"setting"]
 *  }
 *  sessionFactory(SessionFactory) {
 *  	   dataSource = dataSource                 // <-- getProperty for retrieving refs
 *  }
 *  myService(MyService) {
 *      nestedBean = { AnotherBean bean->          // <-- setProperty with closure for nested bean
 *      		dataSource = dataSource
 *      }
 *  }
 * }
 * </pre>
 * <p>
 *   You can also use the Spring IO API to load resources containing beans defined as a Groovy
 *   script using either the constructors or the loadBeans(Resource[] resources) method
 * </p>
 *
 * @author Graeme Rocher
 * @since 0.4
 *
 */
public class BeanBuilder extends GroovyObjectSupport {
    private static final String ANONYMOUS_BEAN = "bean";
    private RuntimeSpringConfiguration springConfig = new DefaultRuntimeSpringConfiguration();
    private BeanConfiguration currentBeanConfig;
    private Map<String,DeferredProperty> deferredProperties = new HashMap<String,DeferredProperty>();
    private ApplicationContext parentCtx;
    private Map binding = new HashMap();
    private ClassLoader classLoader = null;


    public BeanBuilder() {
		super();
	}

    public BeanBuilder(ClassLoader classLoader) {
		super();
		this.classLoader = classLoader;
	}

	public BeanBuilder(ApplicationContext parent) {
		super();
		this.parentCtx = parent;
		this.springConfig = new DefaultRuntimeSpringConfiguration(parent);
	}

	public BeanBuilder(ApplicationContext parent,ClassLoader classLoader) {
		super();
		this.parentCtx = parent;
		this.springConfig = new DefaultRuntimeSpringConfiguration(parent);
		this.classLoader = classLoader;
	}

    /**
     * Parses the bean definition groovy script.
     */
    public void parse(InputStream script) {
        parse(script,new Binding());
    }

    /**
     * Parses the bean definition groovy script by first exporting the given {@link Binding}. 
     */
    public void parse(InputStream script, Binding binding) {
        if (script==null)
            throw new IllegalArgumentException("No script is provided");
        setBinding(binding);
        CompilerConfiguration cc = new CompilerConfiguration();
        cc.setScriptBaseClass(ClosureScript.class.getName());
        GroovyShell shell = new GroovyShell(classLoader,binding,cc);

        ClosureScript s = (ClosureScript)shell.parse(new InputStreamReader(script));
        s.setDelegate(this);
        s.run();
    }

    /**
	 * Retrieves the parent ApplicationContext
	 * @return The parent ApplicationContext
	 */
	public ApplicationContext getParentCtx() {
		return parentCtx;
	}

	/**
	 * Retrieves the RuntimeSpringConfiguration instance used the the BeanBuilder
	 * @return The RuntimeSpringConfiguration instance
	 */
	public RuntimeSpringConfiguration getSpringConfig() {
		return springConfig;
	}

	/**
	 * Retrieves a BeanDefinition for the given name
	 * @param name The bean definition
	 * @return The BeanDefinition instance
	 */
	public BeanDefinition getBeanDefinition(String name) {
		if(!getSpringConfig().containsBean(name))
			return null;
		return getSpringConfig().getBeanConfig(name).getBeanDefinition();
	}

    /**
     * Retrieves all BeanDefinitions for this BeanBuilder
     *
     * @return A map of BeanDefinition instances with the bean id as the key
     */
    public Map<String,BeanDefinition> getBeanDefinitions() {

        Map<String,BeanDefinition> beanDefinitions = new HashMap<String,BeanDefinition>();
        for (String beanName : getSpringConfig().getBeanNames()) {
            BeanDefinition bd = getSpringConfig()
                    .getBeanConfig(beanName)
                    .getBeanDefinition();
            beanDefinitions.put(beanName, bd);
        }
        return beanDefinitions;
    }

    /**
	 * Sets the runtime Spring configuration instance to use. This is not necessary to set
	 * and is configured to default value if not, but is useful for integrating with other
	 * spring configuration mechanisms @see org.codehaus.groovy.grails.commons.spring.GrailsRuntimeConfigurator
	 *
	 * @param springConfig The spring config
	 */
	public void setSpringConfig(RuntimeSpringConfiguration springConfig) {
		this.springConfig = springConfig;
	}



	/**
	 * This class is used to defer the adding of a property to a bean definition until later
	 * This is for a case where you assign a property to a list that may not contain bean references at
	 * that point of asignment, but may later hence it would need to be managed
	 *
	 * @author Graeme Rocher
	 */
	private static class DeferredProperty {
		private BeanConfiguration config;
		private String name;
		private Object value;

		DeferredProperty(BeanConfiguration config, String name, Object value) {
			this.config = config;
			this.name = name;
			this.value = value;
		}

		public void setInBeanConfig() {
			this.config.addProperty(name, value);
		}
	}

	/**
	 * A RuntimeBeanReference that takes care of adding new properties to runtime references
	 *
	 * @author Graeme Rocher
	 * @since 0.4
	 *
	 */
	private class ConfigurableRuntimeBeanReference extends RuntimeBeanReference implements GroovyObject {

		private MetaClass metaClass;
		private BeanConfiguration beanConfig;

		public ConfigurableRuntimeBeanReference(String beanName, BeanConfiguration beanConfig) {
			this(beanName, beanConfig, false);
		}

		public ConfigurableRuntimeBeanReference(String beanName, BeanConfiguration beanConfig, boolean toParent) {
			super(beanName, toParent);
			this.beanConfig = beanConfig;
			if(beanConfig == null)
				throw new IllegalArgumentException("Argument [beanConfig] cannot be null");
			this.metaClass = InvokerHelper.getMetaClass(this);
		}

		public MetaClass getMetaClass() {
			return this.metaClass;
		}

		public Object getProperty(String property) {
			if(property.equals("beanName"))
				return getBeanName();
			else if(property.equals("source"))
				return getSource();
			else if(this.beanConfig != null) {
				return new WrappedPropertyValue(property,beanConfig.getPropertyValue(property));
			}
			else
				return this.metaClass.getProperty(this, property);
		}



        /**
		 * Wraps a BeanConfiguration property an ensures that any RuntimeReference additions to it are
		 * deferred for resolution later
		 *
		 * @author Graeme Rocher
		 * @since 0.4
		 *
		 */
		private class WrappedPropertyValue extends GroovyObjectSupport {
			private Object propertyValue;
			private String propertyName;
			public WrappedPropertyValue(String propertyName, Object propertyValue) {
				this.propertyValue = propertyValue;
				this.propertyName = propertyName;
			}

			public void leftShift(Object value) {
				InvokerHelper.invokeMethod(propertyValue, "leftShift", value);
				if(value instanceof RuntimeBeanReference) {
					deferredProperties.put(beanConfig.getName(), new DeferredProperty(beanConfig, propertyName, propertyValue));
				}
			}
		}
		public Object invokeMethod(String name, Object args) {
			return this.metaClass.invokeMethod(this, name, args);
		}

		public void setMetaClass(MetaClass metaClass) {
			this.metaClass = metaClass;
		}

		public void setProperty(String property, Object newValue) {
            if(!addToDeferred(beanConfig,property, newValue)) {
                beanConfig.setPropertyValue(property, newValue);
            }
		}
	}
	/**
	 * Takes a resource pattern as (@see org.springframework.core.io.support.PathMatchingResourcePatternResolver)
	 * This allows you load multiple bean resources in this single builder
	 *
	 * eg loadBeans("classpath:*Beans.groovy")
	 *
	 * @param resourcePattern
	 * @throws IOException When the path cannot be matched
	 */
	public void loadBeans(String resourcePattern) throws IOException {
		loadBeans(new PathMatchingResourcePatternResolver().getResources(resourcePattern));
	}

	/**
	 * Loads a single Resource into the bean builder
	 *
	 * @param resource The resource to load
	 * @throws IOException When an error occurs
	 */
	public void loadBeans(Resource resource) throws IOException {
		loadBeans(new Resource[]{resource});
	}

	/**
	 * Loads a set of given beans
	 * @param resources The resources to load
	 * @throws IOException
	 */
	public void loadBeans(Resource[] resources) throws IOException {
		Closure beans = new Closure(this){
			@Override
			public Object call(Object[] args) {
				return beans((Closure)args[0]);
			}
		};
		Binding b = new Binding();
		b.setVariable("beans", beans);

		GroovyShell shell = classLoader != null ? new GroovyShell(classLoader,b) : new GroovyShell(b);
        for (Resource resource : resources) {
            shell.evaluate(new InputStreamReader(resource.getInputStream()));
        }
	}

    public void registerBeans(StaticApplicationContext ctx) {
        finalizeDeferredProperties();
        springConfig.registerBeansWithContext(ctx);
    }

    public RuntimeBeanReference ref(String refName) {
        return ref(refName,false);
    }

    public RuntimeBeanReference parentRef(String refName) {
        return ref(refName,true);
    }

    public RuntimeBeanReference ref(String refName, boolean parentRef) {
        return new RuntimeBeanReference(refName, parentRef);
    }

    /**
	 * This method is invoked by Groovy when a method that's not defined in Java is invoked.
     * We use that as a syntax for bean definition. 
	 */
	public Object methodMissing(String name, Object arg) {
        Object[] args = (Object[])arg;

        if(args.length == 0)
			throw new MissingMethodException(name,getClass(),args);

		if(args[0] instanceof Closure) {
            // abstract bean definition
            return invokeBeanDefiningMethod(name, args);
		}
		else if(args[0] instanceof Class || args[0] instanceof RuntimeBeanReference || args[0] instanceof Map) {
			return invokeBeanDefiningMethod(name, args);
		}
		else if (args.length > 1 && args[args.length -1] instanceof Closure) {
			return invokeBeanDefiningMethod(name, args);
		}
        WebApplicationContext ctx = springConfig.getUnrefreshedApplicationContext();
        MetaClass mc = DefaultGroovyMethods.getMetaClass(ctx);
        if(!mc.respondsTo(ctx, name, args).isEmpty()){
            return mc.invokeMethod(ctx,name, args);
        }
        return this;
	}

    public WebApplicationContext createApplicationContext() {
        finalizeDeferredProperties();
        return springConfig.getApplicationContext();
    }

    private void finalizeDeferredProperties() {
        for (DeferredProperty dp : deferredProperties.values()) {
            if (dp.value instanceof List) {
                dp.value = manageListIfNecessary((List)dp.value);
            } else if (dp.value instanceof Map) {
                dp.value = manageMapIfNecessary((Map)dp.value);
            }
            dp.setInBeanConfig();
        }
		deferredProperties.clear();
	}

	private boolean addToDeferred(BeanConfiguration beanConfig,String property, Object newValue) {
		if(newValue instanceof List) {
			deferredProperties.put(currentBeanConfig.getName()+property,new DeferredProperty(currentBeanConfig, property, newValue));
			return true;
		}
		else if(newValue instanceof Map) {
			deferredProperties.put(currentBeanConfig.getName()+property,new DeferredProperty(currentBeanConfig, property, newValue));
			return true;
		}
		return false;
	}
	/**
	 * This method is called when a bean definition node is called
	 *
	 * @param name The name of the bean to define
	 * @param args The arguments to the bean. The first argument is the class name, the last argument is sometimes a closure. All
	 * the arguments in between are constructor arguments
	 * @return The bean configuration instance
	 */
	private BeanConfiguration invokeBeanDefiningMethod(String name, Object[] args) {
        BeanConfiguration old = currentBeanConfig;
        try {
            if(args[0] instanceof Class) {
                Class beanClass = args[0] instanceof Class ? (Class)args[0] : args[0].getClass();

                if(args.length >= 1) {
                    if(args[args.length-1] instanceof Closure) {
                        if(args.length-1 != 1) {
                            Object[] constructorArgs = ArrayUtils.subarray(args, 1, args.length-1);
                            filterGStringReferences(constructorArgs);
                            if(name.equals(ANONYMOUS_BEAN))
                                currentBeanConfig = springConfig.createSingletonBean(beanClass,Arrays.asList(constructorArgs));
                            else
                                currentBeanConfig = springConfig.addSingletonBean(name, beanClass, Arrays.asList(constructorArgs));
                        }
                        else {
                            if(name.equals(ANONYMOUS_BEAN))
                                currentBeanConfig = springConfig.createSingletonBean(beanClass);
                            else
                                currentBeanConfig = springConfig.addSingletonBean(name, beanClass);
                        }
                    }
                    else  {
                        Object[] constructorArgs = ArrayUtils.subarray(args, 1, args.length);
                        filterGStringReferences(constructorArgs);
                        if(name.equals(ANONYMOUS_BEAN))
                            currentBeanConfig = springConfig.createSingletonBean(beanClass,Arrays.asList(constructorArgs));
                        else
                            currentBeanConfig = springConfig.addSingletonBean(name, beanClass, Arrays.asList(constructorArgs));
                    }

                }
            }
            else if(args[0] instanceof RuntimeBeanReference) {
                currentBeanConfig = springConfig.addSingletonBean(name);
                currentBeanConfig.setFactoryBean(((RuntimeBeanReference)args[0]).getBeanName());
            }
            else if(args[0] instanceof Map) {
                currentBeanConfig = springConfig.addSingletonBean(name);
                Map.Entry factoryBeanEntry = (Map.Entry)((Map)args[0]).entrySet().iterator().next();
                currentBeanConfig.setFactoryBean(factoryBeanEntry.getKey().toString());
                currentBeanConfig.setFactoryMethod(factoryBeanEntry.getValue().toString());
            }
            else if(args[0] instanceof Closure) {
                currentBeanConfig = springConfig.addAbstractBean(name);
            }
            else {
                Object[] constructorArgs;
                if(args[args.length-1] instanceof Closure) {
                    constructorArgs= ArrayUtils.subarray(args, 0, args.length-1);
                }
                else {
                    constructorArgs= ArrayUtils.subarray(args, 0, args.length);
                }
                filterGStringReferences(constructorArgs);
                currentBeanConfig = new DefaultBeanConfiguration(name, null, Arrays.asList(constructorArgs));
                springConfig.addBeanConfiguration(name,currentBeanConfig);
            }
            if(args[args.length-1] instanceof Closure) {
                Closure callable = (Closure)args[args.length-1];
                callable.setDelegate(this);
                callable.setResolveStrategy(Closure.DELEGATE_FIRST);
                callable.call(new Object[]{currentBeanConfig});

            }

            return currentBeanConfig;
        } finally {
            currentBeanConfig = old;
        }
    }

    private void filterGStringReferences(Object[] constructorArgs) {
        for (int i = 0; i < constructorArgs.length; i++) {
            Object constructorArg = constructorArgs[i];
            if(constructorArg instanceof GString) constructorArgs[i] = constructorArg.toString();
        }
    }

    /**
	 * When an methods argument is only a closure it is a set of bean definitions
	 *
	 * @param callable The closure argument
	 */
	public BeanBuilder beans(Closure callable) {
		callable.setDelegate(this);
  //      callable.setResolveStrategy(Closure.DELEGATE_FIRST);
        callable.call();
		finalizeDeferredProperties();

        return this;
    }

    /**
	 * This method overrides property setting in the scope of the BeanBuilder to set
	 * properties on the current BeanConfiguration
	 */
	@Override
	public void setProperty(String name, Object value) {
		if(currentBeanConfig != null) {
			if(value instanceof GString)value = value.toString();
			if(addToDeferred(currentBeanConfig, name, value)) {
				return;
			}
			else if(value instanceof Closure) {
				BeanConfiguration current = currentBeanConfig;
				try {
					Closure callable = (Closure)value;

					Class parameterType = callable.getParameterTypes()[0];
					if(parameterType.equals(Object.class)) {
						currentBeanConfig = springConfig.createSingletonBean("");
						callable.call(new Object[]{currentBeanConfig});
					}
					else {
						currentBeanConfig = springConfig.createSingletonBean(parameterType);
						callable.call(null);
					}

					value = currentBeanConfig.getBeanDefinition();
				}
				finally {
					currentBeanConfig = current;
				}
			}
            currentBeanConfig.addProperty(name, value);
		} else {
            binding.put(name,value);
        }
	}

	/**
	 * Checks whether there are any runtime refs inside a Map and converts
	 * it to a ManagedMap if necessary
	 *
	 * @param value The current map
	 * @return A ManagedMap or a normal map
	 */
	private Object manageMapIfNecessary(Map<Object, Object> value) {
		boolean containsRuntimeRefs = false;
        for (Entry<Object, Object> e : value.entrySet()) {
            Object v = e.getValue();
            if (v instanceof RuntimeBeanReference) {
                containsRuntimeRefs = true;
            }
            if (v instanceof BeanConfiguration) {
                BeanConfiguration c = (BeanConfiguration) v;
                e.setValue(c.getBeanDefinition());
                containsRuntimeRefs = true;
            }
        }
		if(containsRuntimeRefs) {
//			return new ManagedMap(map);
            ManagedMap m = new ManagedMap();
            m.putAll(value);
            return m;
        }
		return value;
	}

	/**
	 * Checks whether there are any runtime refs inside the list and
	 * converts it to a ManagedList if necessary
	 *
	 * @param value The object that represents the list
	 * @return Either a new list or a managed one
	 */
	private Object manageListIfNecessary(List<Object> value) {
		boolean containsRuntimeRefs = false;
		for (ListIterator<Object> i = value.listIterator(); i.hasNext();) {
			Object e = i.next();
			if(e instanceof RuntimeBeanReference) {
				containsRuntimeRefs = true;
			}
            if (e instanceof BeanConfiguration) {
                BeanConfiguration c = (BeanConfiguration) e;
                i.set(c.getBeanDefinition());
                containsRuntimeRefs = true;
            }
        }
		if(containsRuntimeRefs) {
			List tmp = new ManagedList();
			tmp.addAll(value);
			value = tmp;
		}
		return value;
	}

	/**
	 * This method overrides property retrieval in the scope of the BeanBuilder to either:
	 *
	 * a) Retrieve a variable from the bean builder's binding if it exits
	 * b) Retrieve a RuntimeBeanReference for a specific bean if it exists
	 * c) Otherwise just delegate to super.getProperty which will resolve properties from the BeanBuilder itself
	 */
	@Override
	public Object getProperty(String name) {
		if(binding.containsKey(name)) {
			return binding.get(name);
		}
		else {
			if(springConfig.containsBean(name)) {
				BeanConfiguration beanConfig = springConfig.getBeanConfig(name);
				if(beanConfig != null) {
					return new ConfigurableRuntimeBeanReference(name, springConfig.getBeanConfig(name) ,false);
				}
				else
					return new RuntimeBeanReference(name,false);
			}
			// this is to deal with the case where the property setter is the last
			// statement in a closure (hence the return value)
			else if(currentBeanConfig != null) {
				if(currentBeanConfig.hasProperty(name))
					return currentBeanConfig.getPropertyValue(name);
				else {
					DeferredProperty dp = deferredProperties.get(currentBeanConfig.getName()+name);
					if(dp!=null) {
						return dp.value;
					}
					else {
						return super.getProperty(name);
					}
				}
			}
			else {
				return super.getProperty(name);
			}
		}
	}

	/**
	 * Sets the binding (the variables available in the scope of the BeanBuilder)
	 * @param b The Binding instance
	 */
	public void setBinding(Binding b) {
		this.binding = b.getVariables();
	}

}
