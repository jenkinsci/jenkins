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

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.web.context.ServletContextAware;
import org.springframework.web.context.WebApplicationContext;

import javax.servlet.ServletContext;
import java.util.Collection;
import java.util.List;

/**
 * A programmable runtime Spring configuration that allows a spring ApplicationContext
 * to be constructed at runtime
 *
 * Credit must go to Solomon Duskis and the
 * article: http://jroller.com/page/Solomon?entry=programmatic_configuration_in_spring
 *
 * @author Graeme
 * @since 0.3
 *
 */
interface RuntimeSpringConfiguration extends ServletContextAware {

    /**
     * Adds a singleton bean definition
     *
     * @param name The name of the bean
     * @param clazz The class of the bean
     * @return A BeanConfiguration instance
     */
    BeanConfiguration addSingletonBean(String name, Class clazz);

    WebApplicationContext getUnrefreshedApplicationContext();
    /**
     * Adds a prototype bean definition
     *
     * @param name The name of the bean
     * @param clazz The class of the bean
     * @return A BeanConfiguration instance
     */
    BeanConfiguration addPrototypeBean(String name, Class clazz);

    /**
     * Retrieves the application context from the current state
     *
     * @return The ApplicationContext instance
     */
    WebApplicationContext getApplicationContext();

    /**
     * Adds an empty singleton bean configuration
     * @param name The name of the singleton bean
     *
     * @return A BeanConfiguration instance
     */
    BeanConfiguration addSingletonBean(String name);

    /**
     * Adds an empty prototype bean configuration
     *
     * @param name The name of the prototype bean
     * @return A BeanConfiguration instance
     */
    BeanConfiguration addPrototypeBean(String name);

    /**
     * Creates a singleton bean configuration. Differs from addSingletonBean in that
     * it doesn't add the bean to the list of bean references. Hence should be used for
     * creating nested beans
     *
     * @param clazz
     * @return A BeanConfiguration instance
     */
    BeanConfiguration createSingletonBean(Class clazz);

    /**
     * Creates a new singleton bean and adds it to the list of bean references
     *
     * @param name The name of the bean
     * @param clazz The class of the bean
     * @param args The constructor arguments of the bean
     * @return A BeanConfiguration instance
     */
    BeanConfiguration addSingletonBean(String name, Class clazz, Collection args);

    /**
     * Creates a singleton bean configuration. Differs from addSingletonBean in that
     * it doesn't add the bean to the list of bean references. Hence should be used for
     * creating nested beans
     *
     * @param clazz The bean class
     * @param constructorArguments The constructor arguments
     * @return A BeanConfiguration instance
     */
    BeanConfiguration createSingletonBean(Class clazz, Collection constructorArguments);

    /**
     * Sets the servlet context
     *
     * @param context The servlet Context
     */
    void setServletContext(ServletContext context);

    /**
     * Creates a new prototype bean configuration. Differs from addPrototypeBean in that
     * it doesn't add the bean to the list of bean references to be created via the getApplicationContext()
     * method, hence can be used for creating nested beans
     *
     * @param name The bean name
     * @return A BeanConfiguration instance
     *
     */
    BeanConfiguration createPrototypeBean(String name);

    /**
     * Creates a new singleton bean configuration. Differs from addSingletonBean in that
     * it doesn't add the bean to the list of bean references to be created via the getApplicationContext()
     * method, hence can be used for creating nested beans
     *
     * @param name The bean name
     * @return A BeanConfiguration instance
     *
     */
    BeanConfiguration createSingletonBean(String name);

    /**
     * Adds a bean configuration to the list of beans to be created
     *
     * @param beanName The name of the bean in the context
     * @param beanConfiguration The BeanConfiguration instance
     */
    void addBeanConfiguration(String beanName, BeanConfiguration beanConfiguration);
    /**
     * Adds a Spring BeanDefinition. Differs from BeanConfiguration which is a factory class
     * for creating BeanDefinition instances
     * @param name The name of the bean
     * @param bd The BeanDefinition instance
     */
    void addBeanDefinition(String name, BeanDefinition bd);

    /**
     * Returns whether the runtime spring config contains the specified bean
     *
     * @param name The bean name
     * @return True if it does
     */
    boolean containsBean(String name);
    /**
     * Returns the BeanConfiguration for the specified name
     * @param name The name of the bean configuration
     * @return The BeanConfiguration
     */
    BeanConfiguration getBeanConfig(String name);

    /**
     * Creates and returns the BeanDefinition that is regsitered within the given name or returns null
     *
     * @param name The name of the bean definition
     * @return A BeanDefinition
     */
    AbstractBeanDefinition createBeanDefinition(String name);

    /**
     * Registers a bean factory post processor with the context
     *
     * @param processor The BeanFactoryPostProcessor instance
     */
    void registerPostProcessor(BeanFactoryPostProcessor processor);

    List<String> getBeanNames();

    /**
     * Registers the beans held within this RuntimeSpringConfiguration instance with the given ApplicationContext
     *
     * @param applicationContext The ApplicationContext instance
     */
    void registerBeansWithContext(StaticApplicationContext applicationContext);

    /**
     * Adds an abstract bean definition to the bean factory and returns the BeanConfiguration object
     *
     * @param name The name of the bean
     * @return The BeanConfiguration object
     */
    BeanConfiguration addAbstractBean(String name);
}
