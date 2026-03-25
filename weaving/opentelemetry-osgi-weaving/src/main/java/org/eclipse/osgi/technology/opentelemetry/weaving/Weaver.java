package org.eclipse.osgi.technology.opentelemetry.weaving;

import org.osgi.framework.hooks.weaving.WovenClass;

/**
 * Service Provider Interface for weaver implementations.
 * <p>
 * Weavers are discovered via {@link java.util.ServiceLoader} from fragment
 * bundles attached to the weaving host bundle.
 * Each weaver decides which classes it wants to instrument and performs the
 * bytecode transformation using ASM.
 */
public interface Weaver {

    /**
     * Returns a human-readable name for this weaver, used in logging.
     */
    String name();

    /**
     * Determines whether this weaver should instrument the given class.
     *
     * @param className the fully qualified class name (dot-separated)
     * @param wovenClass the woven class context
     * @return {@code true} if this weaver wants to transform the class
     */
    boolean canWeave(String className, WovenClass wovenClass);

    /**
     * Transforms the class bytecode.
     * <p>
     * The implementation should read the bytes from {@code wovenClass.getBytes()},
     * transform them using ASM, and write back via {@code wovenClass.setBytes(...)}.
     * It may also add dynamic imports via {@code wovenClass.getDynamicImports()}.
     *
     * @param wovenClass the woven class to transform
     * @param telemetry the OpenTelemetry proxy providing tracers and meters
     */
    void weave(WovenClass wovenClass, OpenTelemetryProxy telemetry);
}
