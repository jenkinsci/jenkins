/*
 * The MIT License
 *
 * Copyright (c) 2011, CloudBees, Inc.
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
package jenkins;

import hudson.Plugin;
import org.kohsuke.MetaInfServices;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementScanner6;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Set;

/**
 * Discovers the subtype of {@link Plugin} and generates service loader index file.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.420
 */
@SupportedSourceVersion(SourceVersion.RELEASE_6)
@SupportedAnnotationTypes("*")
@MetaInfServices(Processor.class)
@SuppressWarnings({"Since15"})
public class PluginSubtypeMarker extends AbstractProcessor {
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        try {
            ElementScanner6<Void,Void> scanner = new ElementScanner6<Void, Void>() {
                @Override
                public Void visitType(TypeElement e, Void aVoid) {
                    if(!e.getModifiers().contains(Modifier.ABSTRACT)) {
                        Element sc = asElement(e.getSuperclass());
                        if (sc!=null && ((TypeElement)sc).getQualifiedName().contentEquals("hudson.Plugin")) {
                            try {
                                write(e);
                            } catch (IOException x) {
                                StringWriter sw = new StringWriter();
                                x.printStackTrace(new PrintWriter(sw));
                                processingEnv.getMessager().printMessage(Kind.ERROR,sw.toString(),e);
                            }
                        }
                    }

                    return super.visitType(e, aVoid);
                }
            };

            for (Element e : roundEnv.getRootElements()) {
                if (e.getKind() == ElementKind.PACKAGE) { // JENKINS-11739
                    continue;
                }
                scanner.scan(e, null);
            }

            return false;
        } catch (RuntimeException e) {
            // javac sucks at reporting errors in annotation processors
            e.printStackTrace();
            throw e;
        } catch (Error e) {
            e.printStackTrace();
            throw e;
        }
    }

    private void write(TypeElement c) throws IOException {
        FileObject f = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT,
                "", "META-INF/services/hudson.Plugin");
        Writer w = new OutputStreamWriter(f.openOutputStream(),"UTF-8");
        try {
            w.write(c.getQualifiedName().toString());
        } finally {
            w.close();
        }
    }

    private Element asElement(TypeMirror m) {
        return processingEnv.getTypeUtils().asElement(m);
    }
}
