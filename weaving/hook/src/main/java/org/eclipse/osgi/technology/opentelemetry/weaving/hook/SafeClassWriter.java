package org.eclipse.osgi.technology.opentelemetry.weaving.hook;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.osgi.framework.hooks.weaving.WovenClass;

/**
 * A {@link ClassWriter} that uses the target bundle's classloader for stack
 * frame computation instead of the weaving bundle's classloader.
 * <p>
 * In OSGi, the default {@code ClassWriter.getCommonSuperClass()} fails because
 * the weaving bundle cannot load classes from the target bundle. This class
 * overrides the classloader to use the target bundle's classloader, falling
 * back to {@code java/lang/Object} for types that cannot be resolved.
 */
public class SafeClassWriter extends ClassWriter {

    private final ClassLoader targetClassLoader;

    /**
     * Creates a new SafeClassWriter with COMPUTE_FRAMES mode.
     *
     * @param classReader the class reader used for optimization (frame copying)
     * @param wovenClass the woven class being transformed, used to obtain the
     *        target bundle's classloader for type resolution
     */
    public SafeClassWriter(ClassReader classReader, WovenClass wovenClass) {
        super(classReader, ClassWriter.COMPUTE_FRAMES);
        this.targetClassLoader = wovenClass.getBundleWiring().getClassLoader();
    }

    @Override
    protected ClassLoader getClassLoader() {
        return targetClassLoader;
    }

    @Override
    protected String getCommonSuperClass(String type1, String type2) {
        try {
            return super.getCommonSuperClass(type1, type2);
        } catch (Exception e) {
            return "java/lang/Object";
        }
    }
}
