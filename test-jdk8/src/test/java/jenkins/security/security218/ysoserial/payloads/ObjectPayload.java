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

import java.lang.reflect.Modifier;
import java.util.Iterator;
import java.util.Set;

import org.reflections.Reflections;

import jenkins.security.security218.ysoserial.GeneratePayload;

public interface ObjectPayload<T> {
    /*
     * return armed payload object to be serialized that will execute specified 
     * command on deserialization
     */
    public T getObject(String command) throws Exception;

    public static class Utils {

        // get payload classes by classpath scanning
        public static Set<Class<? extends ObjectPayload>> getPayloadClasses () {
            final Reflections reflections = new Reflections(ObjectPayload.class.getPackage().getName());
            final Set<Class<? extends ObjectPayload>> payloadTypes = reflections.getSubTypesOf(ObjectPayload.class);
            for ( Iterator<Class<? extends ObjectPayload>> iterator = payloadTypes.iterator(); iterator.hasNext(); ) {
                Class<? extends ObjectPayload> pc = iterator.next();
                if ( pc.isInterface() || Modifier.isAbstract(pc.getModifiers()) ) {
                    iterator.remove();
                }
            }
            return payloadTypes;
        }
     @SuppressWarnings ( "unchecked" )
        public static Class<? extends ObjectPayload> getPayloadClass ( final String className ) {
            Class<? extends ObjectPayload> clazz = null;
            try {
                clazz = (Class<? extends ObjectPayload>) Class.forName(className);
            }
            catch ( Exception e1 ) {}
            if ( clazz == null ) {
                try {
                    return clazz = (Class<? extends ObjectPayload>) Class
                            .forName(GeneratePayload.class.getPackage().getName() + ".payloads." + className);
                }
                catch ( Exception e2 ) {}
            }
            if ( clazz != null && !ObjectPayload.class.isAssignableFrom(clazz) ) {
                clazz = null;
            }
            return clazz;
        }


        public static Object makePayloadObject ( String payloadType, String payloadArg ) {
            final Class<? extends ObjectPayload> payloadClass = getPayloadClass(payloadType);
            if ( payloadClass == null || !ObjectPayload.class.isAssignableFrom(payloadClass) ) {
                throw new IllegalArgumentException("Invalid payload type '" + payloadType + "'");

            }

            final Object payloadObject;
            try {
                final ObjectPayload payload = payloadClass.newInstance();
                payloadObject = payload.getObject(payloadArg);
            }
            catch ( Exception e ) {
                throw new IllegalArgumentException("Failed to construct payload", e);
            }
            return payloadObject;
        }


        @SuppressWarnings ( "unchecked" )
        public static void releasePayload ( ObjectPayload payload, Object object ) throws Exception {
            if ( payload instanceof ReleaseableObjectPayload ) {
                ( (ReleaseableObjectPayload) payload ).release(object);
            }
        }


        public static void releasePayload ( String payloadType, Object payloadObject ) {
            final Class<? extends ObjectPayload> payloadClass = getPayloadClass(payloadType);
            if ( payloadClass == null || !ObjectPayload.class.isAssignableFrom(payloadClass) ) {
                throw new IllegalArgumentException("Invalid payload type '" + payloadType + "'");

            }

            try {
                final ObjectPayload payload = payloadClass.newInstance();
                releasePayload(payload, payloadObject);
            }
            catch ( Exception e ) {
                e.printStackTrace();
            }

        }
    }
}
