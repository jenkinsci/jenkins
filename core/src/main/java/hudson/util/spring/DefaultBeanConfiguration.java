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

import groovy.lang.GroovyObjectSupport;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.ChildBeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;

import java.util.*;

/**
 * Default implementation of the BeanConfiguration interface
 *
 * Credit must go to Solomon Duskis and the
 * article: http://jroller.com/page/Solomon?entry=programmatic_configuration_in_spring
 *
 * @author Graeme
 * @since 0.3
 *
 */
class DefaultBeanConfiguration extends GroovyObjectSupport implements BeanConfiguration {

	private static final String AUTOWIRE = "autowire";
	private static final String CONSTRUCTOR_ARGS = "constructorArgs";
	private static final String DESTROY_METHOD = "destroyMethod";
	private static final String FACTORY_BEAN = "factoryBean";
	private static final String FACTORY_METHOD = "factoryMethod";
	private static final String INIT_METHOD = "initMethod";
	private static final String BY_NAME = "byName";
    private static final String PARENT = "parent";
    private static final String BY_TYPE = "byType";
    private static final String BY_CONSTRUCTOR = "constructor";
    private static final Set<String> DYNAMIC_PROPS = new HashSet<String>(Arrays.asList(AUTOWIRE, CONSTRUCTOR_ARGS, DESTROY_METHOD, FACTORY_BEAN, FACTORY_METHOD, INIT_METHOD, BY_NAME, BY_TYPE, BY_CONSTRUCTOR));
    private String parentName;

    @Override
    public Object getProperty(String property) {
		getBeanDefinition();
		if(wrapper.isReadableProperty(property)) {
			return wrapper.getPropertyValue(property);
		}
		else if(DYNAMIC_PROPS.contains(property)) {
			return null;
		}
		return super.getProperty(property);
	}

    @Override
    public void setProperty(String property, Object newValue) {
        if(PARENT.equals(property)) {
            setParent(newValue);
        }
        else {
            AbstractBeanDefinition bd = getBeanDefinition();
            if(AUTOWIRE.equals(property)) {
                if(BY_NAME.equals(newValue)) {
                    bd.setAutowireMode(AutowireCapableBeanFactory.AUTOWIRE_BY_NAME);
                }
                else if(BY_TYPE.equals(newValue)) {
                    bd.setAutowireMode(AutowireCapableBeanFactory.AUTOWIRE_BY_TYPE);
                }
                else if(Boolean.TRUE.equals(newValue)) {
                    bd.setAutowireMode(AutowireCapableBeanFactory.AUTOWIRE_BY_NAME);
                }
                else if(BY_CONSTRUCTOR.equals(newValue)) {
                    bd.setAutowireMode(AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR);
                }
            }
            // constructorArgs
            else if(CONSTRUCTOR_ARGS.equals(property) && newValue instanceof List) {
                ConstructorArgumentValues cav = new ConstructorArgumentValues();
                List args = (List)newValue;
                for (Object e : args) {
                    cav.addGenericArgumentValue(e);
                }
                bd.setConstructorArgumentValues(cav);
            }
            // destroyMethod
            else if(DESTROY_METHOD.equals(property)) {
                if(newValue != null)
                    bd.setDestroyMethodName(newValue.toString());
            }
            // factoryBean
            else if(FACTORY_BEAN.equals(property)) {
                if(newValue != null)
                    bd.setFactoryBeanName(newValue.toString());
            }
            // factoryMethod
            else if(FACTORY_METHOD.equals(property)) {
                if(newValue != null)
                    bd.setFactoryMethodName(newValue.toString());
            }
            // initMethod
            else if(INIT_METHOD.equals(property)) {
                if(newValue != null)
                    bd.setInitMethodName(newValue.toString());
            }
            else if(wrapper.isWritableProperty(property)) {

                wrapper.setPropertyValue(property, newValue);
            }
            // autowire
            else {
                super.setProperty(property, newValue);
            }
        }
	}

	private Class clazz;
	private String name;
	private boolean singleton = true;
	private AbstractBeanDefinition definition;
	private Collection constructorArgs = Collections.EMPTY_LIST;
	private BeanWrapper wrapper;

	public DefaultBeanConfiguration(String name, Class clazz) {
		this.name = name;
		this.clazz = clazz;
	}

	public DefaultBeanConfiguration(String name, Class clazz, boolean prototype) {
		this(name,clazz,Collections.EMPTY_LIST);
		this.singleton = !prototype;
	}

	public DefaultBeanConfiguration(String name) {
		this(name,null,Collections.EMPTY_LIST);
	}

	public DefaultBeanConfiguration(Class clazz2) {
		this.clazz = clazz2;
	}

	public DefaultBeanConfiguration(String name2, Class clazz2, Collection args) {
		this.name = name2;
		this.clazz = clazz2;
		this.constructorArgs = args;
	}

	public DefaultBeanConfiguration(String name2, boolean prototype) {
		this(name2,null,Collections.EMPTY_LIST);
		this.singleton = !prototype;
	}

	public DefaultBeanConfiguration(Class clazz2, Collection constructorArguments) {
		this.clazz = clazz2;
		this.constructorArgs = constructorArguments;
	}

	public String getName() {
		return this.name;
	}

	public boolean isSingleton() {
		return this.singleton ;
	}

	public AbstractBeanDefinition getBeanDefinition() {
		if (definition == null)
			definition = createBeanDefinition();
		return definition;
	}

	protected AbstractBeanDefinition createBeanDefinition() {
		AbstractBeanDefinition bd;
		if(constructorArgs.size() > 0) {
			ConstructorArgumentValues cav = new ConstructorArgumentValues();
            for (Object constructorArg : constructorArgs) {
                cav.addGenericArgumentValue(constructorArg);
            }
            if(StringUtils.isBlank(parentName)) {
                bd = new RootBeanDefinition(clazz,cav,null);
            }
            else {
                bd = new ChildBeanDefinition(parentName,clazz,cav, null);
            }
            bd.setSingleton(singleton);
		}
		else {
            if(StringUtils.isBlank(parentName)) {
                bd = new RootBeanDefinition(clazz,singleton);
            }
            else {
                bd = new ChildBeanDefinition(parentName,clazz, null,null);
                bd.setSingleton(singleton);
            }

		}
		wrapper = new BeanWrapperImpl(bd);
		return bd;
	}

	public BeanConfiguration addProperty(String propertyName, Object propertyValue) {
		if(propertyValue instanceof BeanConfiguration) {
			propertyValue = ((BeanConfiguration)propertyValue).getBeanDefinition();
		}
		getBeanDefinition()
			.getPropertyValues()
			.addPropertyValue(propertyName,propertyValue);

		return this;
	}

	public BeanConfiguration setDestroyMethod(String methodName) {
		getBeanDefinition().setDestroyMethodName(methodName);
		return this;
	}

	public BeanConfiguration setDependsOn(String[] dependsOn) {
		getBeanDefinition().setDependsOn(dependsOn);
		return this;
	}

	public BeanConfiguration setFactoryBean(String beanName) {
		getBeanDefinition().setFactoryBeanName(beanName);

		return this;
	}

	public BeanConfiguration setFactoryMethod(String methodName) {
		getBeanDefinition().setFactoryMethodName(methodName);
		return this;
	}

	public BeanConfiguration setAutowire(String type) {
		if("byName".equals(type)) {
			getBeanDefinition().setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_NAME);
		}
		else if("byType".equals(type)){
			getBeanDefinition().setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);
		}
		return this;
	}

    public void setName(String beanName) {
        this.name = beanName;
    }

	public Object getPropertyValue(String name) {
		return getBeanDefinition()
					.getPropertyValues()
					.getPropertyValue(name)
					.getValue();
	}

	public boolean hasProperty(String name) {
		return getBeanDefinition().getPropertyValues().contains(name);
	}

	public void setPropertyValue(String property, Object newValue) {
		getBeanDefinition().getPropertyValues().addPropertyValue(property, newValue);
	}

    public BeanConfiguration setAbstract(boolean isAbstract) {
        getBeanDefinition().setAbstract(isAbstract);
        return this;
    }

    public void setParent(Object obj) {
        if(obj == null) throw new IllegalArgumentException("Parent bean cannot be set to a null runtime bean reference!");
        if(obj instanceof String)
            this.parentName = (String)obj;
        else if(obj instanceof RuntimeBeanReference) {
            this.parentName = ((RuntimeBeanReference)obj).getBeanName();
        }
        else if(obj instanceof BeanConfiguration) {
            this.parentName = ((BeanConfiguration)obj).getName();
        }
    }


}
