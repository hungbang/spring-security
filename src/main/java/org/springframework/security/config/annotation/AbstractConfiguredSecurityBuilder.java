/*
 * Copyright 2002-2013 the original author or authors.
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
package org.springframework.security.config.annotation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;

import org.springframework.security.config.annotation.web.WebSecurityBuilder;
import org.springframework.web.filter.DelegatingFilterProxy;

/**
 * <p>A base {@link SecurityBuilder} that allows {@link SecurityConfigurator} to be
 * applied to it. This makes modifying the {@link SecurityBuilder} a strategy
 * that can be customized and broken up into a number of
 * {@link SecurityConfigurator} objects that have more specific goals than that
 * of the {@link SecurityBuilder}.</p>
 *
 * <p>For example, a {@link SecurityBuilder} may build an
 * {@link DelegatingFilterProxy}, but a {@link SecurityConfigurator} might
 * populate the {@link SecurityBuilder} with the filters necessary for session
 * management, form based login, authorization, etc.</p>
 *
 * @see WebSecurityBuilder
 *
 * @author Rob Winch
 *
 * @param <T>
 *            The object that this builder returns
 * @param <B>
 *            The type of this builder (that is returned by the base class)
 */
public abstract class AbstractConfiguredSecurityBuilder<T, B extends SecurityBuilder<T>> extends AbstractSecurityBuilder<T> {

    private final LinkedHashMap<Class<? extends SecurityConfigurator<T, B>>, SecurityConfigurator<T, B>> configurators =
            new LinkedHashMap<Class<? extends SecurityConfigurator<T, B>>, SecurityConfigurator<T, B>>();

    private BuildState buildState = BuildState.UNBUILT;

    /**
     * Applies a {@link SecurityConfiguratorAdapter} to this
     * {@link SecurityBuilder} and invokes
     * {@link SecurityConfiguratorAdapter#setBuilder(SecurityBuilder)}.
     *
     * @param configurer
     * @return
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    public <C extends SecurityConfiguratorAdapter<T, B>> C apply(C configurer)
            throws Exception {
        add(configurer);
        configurer.setBuilder((B) this);
        return configurer;
    }

    /**
     * Applies a {@link SecurityConfigurator} to this {@link SecurityBuilder}
     * overriding any {@link SecurityConfigurator} of the exact same class. Note
     * that object hierarchies are not considered.
     *
     * @param configurer
     * @return
     * @throws Exception
     */
    public <C extends SecurityConfigurator<T, B>> C apply(C configurer)
            throws Exception {
        add(configurer);
        return configurer;
    }

    /**
     * Adds {@link SecurityConfigurator} ensuring that it is allowed and
     * invoking {@link SecurityConfigurator#init(SecurityBuilder)} immediately
     * if necessary.
     *
     * @param configurer the {@link SecurityConfigurator} to add
     * @throws Exception if an error occurs
     */
    @SuppressWarnings("unchecked")
    private <C extends SecurityConfigurator<T, B>> void add(C configurer) throws Exception {
        Class<? extends SecurityConfigurator<T, B>> clazz = (Class<? extends SecurityConfigurator<T, B>>) configurer
                .getClass();
        synchronized(configurators) {
            if(buildState.isConfigured()) {
                throw new IllegalStateException("Cannot apply "+configurer+" to already built object");
            }
            this.configurators.put(clazz, configurer);
            if(buildState.isInitializing()) {
                configurer.init((B)this);
            }
        }
    }

    /**
     * Gets the {@link SecurityConfigurator} by its class name or
     * <code>null</code> if not found. Note that object hierarchies are not
     * considered.
     *
     * @param clazz
     * @return
     */
    @SuppressWarnings("unchecked")
    protected <C extends SecurityConfigurator<T, B>> C getConfigurator(
            Class<C> clazz) {
        return (C) configurators.get(clazz);
    }

    /**
     * Removes and returns the {@link SecurityConfigurator} by its class name or
     * <code>null</code> if not found. Note that object hierarchies are not
     * considered.
     *
     * @param clazz
     * @return
     */
    @SuppressWarnings("unchecked")
    public <C extends SecurityConfigurator<T,B>> C removeConfigurator(Class<C> clazz) {
        return (C) configurators.remove(clazz);
    }

    /**
     * Executes the build using the {@link SecurityConfigurator}'s that have been applied using the following steps:
     *
     * <ul>
     * <li>Invokes {@link #beforeInit()} for any subclass to hook into</li>
     * <li>Invokes {@link SecurityConfigurator#init(SecurityBuilder)} for any {@link SecurityConfigurator} that was applied to this builder.</li>
     * <li>Invokes {@link #beforeConfigure()} for any subclass to hook into</li>
     * <li>Invokes {@link #performBuild()} which actually builds the Object</li>
     * </ul>
     */
    @Override
    protected final T doBuild() throws Exception {
        synchronized(configurators) {
            buildState = BuildState.INITIALIZING;

            beforeInit();
            init();

            buildState = BuildState.CONFIGURING;

            beforeConfigure();
            configure();

            buildState = BuildState.BUILDING;

            T result = performBuild();

            buildState = BuildState.BUILT;

            return result;
        }
    }

    /**
     * Invoked prior to invoking each
     * {@link SecurityConfigurator#init(SecurityBuilder)} method. Subclasses may
     * override this method to hook into the lifecycle without using a
     * {@link SecurityConfigurator}.
     */
    protected void beforeInit() throws Exception {
    }

    /**
     * Invoked prior to invoking each
     * {@link SecurityConfigurator#configure(SecurityBuilder)} method.
     * Subclasses may override this method to hook into the lifecycle without
     * using a {@link SecurityConfigurator}.
     */
    protected void beforeConfigure() throws Exception {
    }

    /**
     * Subclasses must implement this method to build the object that is being returned.
     *
     * @return
     */
    protected abstract T performBuild() throws Exception;

    @SuppressWarnings("unchecked")
    private void init() throws Exception {
        Collection<SecurityConfigurator<T,B>> configurators = getConfigurators();

        for(SecurityConfigurator<T,B> configurer : configurators ) {
            configurer.init((B) this);
        }
    }

    @SuppressWarnings("unchecked")
    private void configure() throws Exception {
        Collection<SecurityConfigurator<T,B>> configurators = getConfigurators();

        for(SecurityConfigurator<T,B> configurer : configurators ) {
            configurer.configure((B) this);
        }
    }

    private Collection<SecurityConfigurator<T, B>> getConfigurators() {
        return new ArrayList<SecurityConfigurator<T,B>>(this.configurators.values());
    }

    /**
     * The build state for the application
     *
     * @author Rob Winch
     * @since 3.2
     */
    private static enum BuildState {
        /**
         * This is the state before the {@link Builder#build()} is invoked
         */
        UNBUILT(0),

        /**
         * The state from when {@link Builder#build()} is first invoked until
         * all the {@link SecurityConfigurator#init(SecurityBuilder)} methods
         * have been invoked.
         */
        INITIALIZING(1),

        /**
         * The state from after all
         * {@link SecurityConfigurator#init(SecurityBuilder)} have been invoked
         * until after all the
         * {@link SecurityConfigurator#configure(SecurityBuilder)} methods have
         * been invoked.
         */
        CONFIGURING(2),

        /**
         * From the point after all the
         * {@link SecurityConfigurator#configure(SecurityBuilder)} have
         * completed to just after
         * {@link AbstractConfiguredSecurityBuilder#performBuild()}.
         */
        BUILDING(3),

        /**
         * After the object has been completely built.
         */
        BUILT(4);

        private final int order;

        BuildState(int order) {
            this.order = order;
        }

        public boolean isInitializing() {
            return INITIALIZING.order == order;
        }

        /**
         * Determines if the state is CONFIGURING or later
         * @return
         */
        public boolean isConfigured() {
            return order >= CONFIGURING.order;
        }
    }
}