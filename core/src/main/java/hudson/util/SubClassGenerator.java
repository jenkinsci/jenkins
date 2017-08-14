/*
 * The MIT License
 *
 * Copyright (c) 2010, InfraDNA, Inc.
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
package hudson.util;

import hudson.PluginManager.UberClassLoader;
import jenkins.model.Jenkins;
import org.kohsuke.asm5.ClassWriter;
import org.kohsuke.asm5.MethodVisitor;
import org.kohsuke.asm5.Type;

import java.lang.reflect.Constructor;

import static org.kohsuke.asm5.Opcodes.*;

/**
 * Generates a new class that just defines constructors into the super types.
 *
 * @author Kohsuke Kawaguchi
 */
public class SubClassGenerator extends ClassLoader {
    public SubClassGenerator(ClassLoader parent) {
        super(parent);
    }

    public <T> Class<? extends T> generate(Class<T> base, String name) {
        ClassWriter cw = new ClassWriter(0);//?
        cw.visit(49, ACC_PUBLIC, name.replace('.', '/'), null,
                Type.getInternalName(base),null);

        for (Constructor c : base.getDeclaredConstructors()) {
            Class[] et = c.getExceptionTypes();
            String[] exceptions = new String[et.length];
            for (int i = 0; i < et.length; i++)
                exceptions[i] = Type.getInternalName(et[i]);

            String methodDescriptor = getMethodDescriptor(c);
            MethodVisitor m = cw.visitMethod(c.getModifiers(), "<init>", methodDescriptor, null, exceptions);
            m.visitCode();

            int index=1;
            m.visitVarInsn(ALOAD,0);
            for (Class param : c.getParameterTypes()) {
                Type t = Type.getType(param);
                m.visitVarInsn(t.getOpcode(ILOAD), index);
                index += t.getSize();
            }
            m.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(base), "<init>", methodDescriptor);
            m.visitInsn(RETURN);
            m.visitMaxs(index,index);
            m.visitEnd();
        }

        cw.visitEnd();
        byte[] image = cw.toByteArray();

        Class<? extends T> c = defineClass(name, image, 0, image.length).asSubclass(base);

        Jenkins h = Jenkins.getInstanceOrNull();
        if (h!=null)    // null only during tests.
            ((UberClassLoader)h.pluginManager.uberClassLoader).addNamedClass(name,c); // can't change the field type as it breaks binary compatibility
        
        return c;
    }

    private String getMethodDescriptor(Constructor c) {
        StringBuilder buf = new StringBuilder();
        buf.append('(');
        for (Class p : c.getParameterTypes())
            buf.append(Type.getDescriptor(p));

        buf.append(")V");
        return buf.toString();
    }
}
