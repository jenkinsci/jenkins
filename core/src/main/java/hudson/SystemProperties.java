/*
 * The MIT License
 *
 * Copyright 2015 Johannes Ernst, http://upon2020.com/.
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
package hudson;

import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletContext;

/**
 * Centralizes calls to System.getProperty() and related calls.
 * This allows us to get values not just from environment variables but also from
 * the ServletContext, so things like hudson.DNSMultiCast.disabled can be set
 * in context.xml and the app server's boot script does not have to be changed.
 * 
 * While it looks like it on first glamce, this cannot be mapped to EnvVars.java
 * because EnvVars.java is only for build variables, not Jenkins itself variables. 
 * 
 * This should be invoked for hudson parameters (e.g. hudson.DNSMultiCast.disabled),
 * but not for system parameters (e.g. os.name).
 * 
 * @author Johannes Ernst
 */
public class SystemProperties {
    /**
     * The ServletContext to get the init parameters from.
     */
    private static ServletContext theContext;

    /**
     * Logger.
     */
    private static final Logger LOGGER = Logger.getLogger(SystemProperties.class.getName());

    /**
     * Gets the system property indicated by the specified key.
     * This behaves just like System.getProperty(String), except that it
     * also consults the ServletContext's init parameters.
     * 
     * @param      key   the name of the system property.
     * @return     the string value of the system property,
     *             or <code>null</code> if there is no property with that key.
     *
     * @exception  SecurityException  if a security manager exists and its
     *             <code>checkPropertyAccess</code> method doesn't allow
     *              access to the specified system property.
     * @exception  NullPointerException if <code>key</code> is
     *             <code>null</code>.
     * @exception  IllegalArgumentException if <code>key</code> is empty.
     */
    public static String getProperty(String key) {
        String value = System.getProperty(key); // keep passing on any exceptions
        if( value != null ) {
            if( LOGGER.isLoggable(Level.INFO )) {
                LOGGER.log(Level.INFO, "Property (system): {0} => {1}", new Object[]{ key, value });
            }
        } else if( theContext != null ) {
            value = theContext.getInitParameter(key);
            if( value != null ) {
                if( LOGGER.isLoggable(Level.INFO )) {
                    LOGGER.log(Level.INFO, "Property (context): {0} => {1}", new Object[]{ key, value });
                }
            }
        } else {
            if( LOGGER.isLoggable(Level.INFO )) {
                LOGGER.log(Level.INFO, "Property (not found): {0} => {1}", new Object[]{ key, value });
            }
        }

        return value;
    }

    /**
     * Gets the system property indicated by the specified key.
     * This behaves just like System.getProperty(String), except that it
     * also consults the ServletContext's init parameters.
     * 
     * @param      key   the name of the system property.
     * @param      def   a default value.
     * @return     the string value of the system property,
     *             or <code>null</code> if there is no property with that key.
     *
     * @exception  SecurityException  if a security manager exists and its
     *             <code>checkPropertyAccess</code> method doesn't allow
     *              access to the specified system property.
     * @exception  NullPointerException if <code>key</code> is
     *             <code>null</code>.
     * @exception  IllegalArgumentException if <code>key</code> is empty.
     */
    public static String getProperty(String key, String def) {
        String value = System.getProperty(key); // keep passing on any exceptions
        if( value != null ) {
            if( LOGGER.isLoggable(Level.INFO )) {
                LOGGER.log(Level.INFO, "Property (system): {0} => {1}", new Object[]{ key, value });
            }
        } else if( theContext != null ) {
            value = theContext.getInitParameter(key);
            if( value != null ) {
                if( LOGGER.isLoggable(Level.INFO )) {
                    LOGGER.log(Level.INFO, "Property (context): {0} => {1}", new Object[]{ key, value });
                }
            }
        }
        if( value == null ) {
            value = def;
            if( LOGGER.isLoggable(Level.INFO )) {
                LOGGER.log(Level.INFO, "Property (default): {0} => {1}", new Object[]{ key, value });
            }
        }
        return value;        
    }

    /**
      * Returns {@code true} if the system property
      * named by the argument exists and is equal to the string
      * {@code "true"}. If the system property does not exist, return
      * {@code "true"} if a property by this name exists in the servletcontext
      * and is equal to the string {@code "true"}.
      * 
      * This behaves just like Boolean.getBoolean(String), except that it
      * also consults the ServletContext's init parameters.
      * 
      * @param   name   the system property name.
      * @return  the {@code boolean} value of the system property.
      */  
    public static boolean getBoolean(String name) {
        return getBoolean(name, false);
    }

    /**
      * Returns {@code true} if the system property
      * named by the argument exists and is equal to the string
      * {@code "true"}. If the system property does not exist, return
      * {@code "true"} if a property by this name exists in the servletcontext
      * and is equal to the string {@code "true"}. If that property does not
      * exist either, return the default value.
      * 
      * This behaves just like Boolean.getBoolean(String), except that it
      * also consults the ServletContext's init parameters.
      * 
      * @param   name   the system property name.
      * @param   def   a default value.
      * @return  the {@code boolean} value of the system property.
      */
    public static boolean getBoolean(String name, boolean def) {
        String v = getProperty(name);
       
        if (v != null) {
            return Boolean.parseBoolean(v);
        }
        return def;
    }
    
    /**
      * Determines the integer value of the system property with the
      * specified name.
      * 
      * This behaves just like Integer.getInteger(String), except that it
      * also consults the ServletContext's init parameters.
      * 
      * @param   name property name.
      * @return  the {@code Integer} value of the property.
      */
    public static Integer getInteger(String name) {
        return getInteger( name, null );
    }

    /**
      * Determines the integer value of the system property with the
      * specified name.
      * 
      * This behaves just like Integer.getInteger(String), except that it
      * also consults the ServletContext's init parameters. If neither exist,
      * return the default value.
      * 
      * @param   name property name.
      * @param   def   a default value.
      * @return  the {@code Integer} value of the property.
      */

    public static Integer getInteger(String name, Integer def) {
        String v = getProperty(name);
       
        if (v != null) {
            try {
                return Integer.decode(v);
            } catch (NumberFormatException e) {
            }
        }
        return def;
    }
    /**
     * Invoked by WebAppMain, tells us where to get the init parameters from.
     * 
     * @param context the ServletContext obtained from contextInitialized
     */
    public static void initialize(ServletContext context) {
        theContext = context;
    }
}
