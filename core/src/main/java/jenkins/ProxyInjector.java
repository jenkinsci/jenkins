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

import com.google.inject.Binding;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.MembersInjector;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Scope;
import com.google.inject.TypeLiteral;
import com.google.inject.spi.TypeConverterBinding;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link Injector} that delegates to another one.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class ProxyInjector implements Injector {
    protected abstract Injector resolve();

    public void injectMembers(Object instance) {
        resolve().injectMembers(instance);
    }

    public <T> MembersInjector<T> getMembersInjector(TypeLiteral<T> typeLiteral) {
        return resolve().getMembersInjector(typeLiteral);
    }

    public <T> MembersInjector<T> getMembersInjector(Class<T> type) {
        return resolve().getMembersInjector(type);
    }

    public Map<Key<?>, Binding<?>> getBindings() {
        return resolve().getBindings();
    }

    public Map<Key<?>, Binding<?>> getAllBindings() {
        return resolve().getAllBindings();
    }

    public <T> Binding<T> getBinding(Key<T> key) {
        return resolve().getBinding(key);
    }

    public <T> Binding<T> getBinding(Class<T> type) {
        return resolve().getBinding(type);
    }

    public <T> Binding<T> getExistingBinding(Key<T> key) {
        return resolve().getExistingBinding(key);
    }

    public <T> List<Binding<T>> findBindingsByType(TypeLiteral<T> type) {
        return resolve().findBindingsByType(type);
    }

    public <T> Provider<T> getProvider(Key<T> key) {
        return resolve().getProvider(key);
    }

    public <T> Provider<T> getProvider(Class<T> type) {
        return resolve().getProvider(type);
    }

    public <T> T getInstance(Key<T> key) {
        return resolve().getInstance(key);
    }

    public <T> T getInstance(Class<T> type) {
        return resolve().getInstance(type);
    }

    public Injector getParent() {
        return resolve().getParent();
    }

    public Injector createChildInjector(Iterable<? extends Module> modules) {
        return resolve().createChildInjector(modules);
    }

    public Injector createChildInjector(Module... modules) {
        return resolve().createChildInjector(modules);
    }

    public Map<Class<? extends Annotation>, Scope> getScopeBindings() {
        return resolve().getScopeBindings();
    }

    public Set<TypeConverterBinding> getTypeConverterBindings() {
        return resolve().getTypeConverterBindings();
    }
}
