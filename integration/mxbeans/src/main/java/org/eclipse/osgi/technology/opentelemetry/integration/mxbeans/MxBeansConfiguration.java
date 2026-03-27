package org.eclipse.osgi.technology.opentelemetry.integration.mxbeans;

/**
 * Configuration for the MXBeans metrics integration.
 * <p>
 * Allows enabling or disabling individual MXBean metric groups via OSGi Configuration Admin.
 * All groups are enabled by default.
 */
public @interface MxBeansConfiguration {

    boolean memoryEnabled() default true;

    boolean cpuEnabled() default true;

    boolean threadsEnabled() default true;

    boolean gcEnabled() default true;

    boolean classLoadingEnabled() default true;

    boolean bufferPoolsEnabled() default true;

    boolean memoryPoolsEnabled() default true;
}
