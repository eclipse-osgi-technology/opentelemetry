package org.eclipse.osgi.technology.opentelemetry.weaver.scr;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.osgi.technology.opentelemetry.weaving.OpenTelemetryProxy;
import org.eclipse.osgi.technology.opentelemetry.weaving.SafeClassWriter;
import org.eclipse.osgi.technology.opentelemetry.weaving.Weaver;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.osgi.framework.Bundle;
import org.osgi.framework.hooks.weaving.WovenClass;

/**
 * Weaver that instruments Declarative Services component lifecycle methods.
 * <p>
 * This weaver parses the {@code Service-Component} manifest header to locate
 * DS XML descriptors and extracts lifecycle method names directly from the
 * component XML attributes, following the DS specification defaults:
 * <ul>
 *   <li>{@code activate} — default {@code "activate"}</li>
 *   <li>{@code deactivate} — default {@code "deactivate"}</li>
 *   <li>{@code modified} — no default (only instrumented when explicitly declared)</li>
 *   <li>{@code init} — default {@code 0} (constructor injection when &gt; 0)</li>
 * </ul>
 * <p>
 * No annotation scanning is performed — the XML descriptors are the single
 * source of truth, as annotations are not mandatory for DS components.
 * <p>
 * The parsed {@link ComponentDescriptor}s are cached per bundle for efficiency.
 */
public class ScrWeaver implements Weaver {

    private static final Logger LOG = Logger.getLogger(ScrWeaver.class.getName());

    private static final Pattern COMPONENT_TAG = Pattern.compile(
            "<(?:scr:)?component\\b([^>]*(?:>|/>))", Pattern.DOTALL);

    private static final Pattern IMPLEMENTATION_CLASS = Pattern.compile(
            "<implementation\\s+class\\s*=\\s*\"([^\"]+)\"");

    private static final Pattern ATTRIBUTE = Pattern.compile(
            "\\b(\\w[\\w-]*)\\s*=\\s*\"([^\"]*)\"");

    private final ConcurrentHashMap<Long, Map<String, ComponentDescriptor>> cache =
            new ConcurrentHashMap<>();

    @Override
    public String name() {
        return "scr";
    }

    @Override
    public boolean canWeave(String className, WovenClass wovenClass) {
        Bundle bundle = wovenClass.getBundleWiring().getBundle();
        Map<String, ComponentDescriptor> descriptors = getComponentDescriptors(bundle);
        return descriptors.containsKey(className);
    }

    @Override
    public void weave(WovenClass wovenClass, OpenTelemetryProxy telemetry) {
        ScrInstrumentationHelper.setProxy(telemetry);
        addDynamicImports(wovenClass);

        Bundle bundle = wovenClass.getBundleWiring().getBundle();
        String className = wovenClass.getClassName();
        ComponentDescriptor descriptor = getComponentDescriptors(bundle).get(className);
        if (descriptor == null) {
            return;
        }

        byte[] original = wovenClass.getBytes();
        ClassReader reader = new ClassReader(original);
        ClassWriter writer = new SafeClassWriter(reader, wovenClass);
        ScrClassVisitor visitor = new ScrClassVisitor(writer, descriptor);
        reader.accept(visitor, ClassReader.EXPAND_FRAMES);

        if (visitor.isTransformed()) {
            wovenClass.setBytes(writer.toByteArray());
            LOG.log(Level.FINE, () -> "Wove SCR lifecycle: " + className);
        }
    }

    private Map<String, ComponentDescriptor> getComponentDescriptors(Bundle bundle) {
        return cache.computeIfAbsent(bundle.getBundleId(), id -> {
            String header = bundle.getHeaders().get("Service-Component");
            if (header == null) {
                return Map.of();
            }
            return parseServiceComponentHeader(bundle, header);
        });
    }

    private Map<String, ComponentDescriptor> parseServiceComponentHeader(
            Bundle bundle, String header) {
        Map<String, ComponentDescriptor> descriptors = new HashMap<>();
        String[] paths = header.split("\\s*,\\s*");
        for (String path : paths) {
            path = path.trim();
            if (path.isEmpty()) {
                continue;
            }
            try {
                if (path.contains("*")) {
                    parseWildcardEntries(bundle, path, descriptors);
                } else {
                    URL url = bundle.getEntry(path);
                    if (url != null) {
                        parseXml(url, descriptors);
                    }
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to parse DS XML: " + path
                        + " in bundle " + bundle.getSymbolicName(), e);
            }
        }
        return Collections.unmodifiableMap(descriptors);
    }

    private void parseWildcardEntries(Bundle bundle, String path,
            Map<String, ComponentDescriptor> descriptors) {
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash < 0) {
            return;
        }
        String dir = path.substring(0, lastSlash);
        String pattern = path.substring(lastSlash + 1);
        Enumeration<URL> entries = bundle.findEntries(dir, pattern, false);
        if (entries != null) {
            while (entries.hasMoreElements()) {
                try {
                    parseXml(entries.nextElement(), descriptors);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Failed to parse DS XML entry", e);
                }
            }
        }
    }

    private void parseXml(URL url, Map<String, ComponentDescriptor> descriptors)
            throws IOException {
        String xml;
        try (InputStream is = url.openStream();
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            xml = sb.toString();
        }

        Matcher implMatcher = IMPLEMENTATION_CLASS.matcher(xml);
        if (!implMatcher.find()) {
            return;
        }
        String implClass = implMatcher.group(1);

        // Extract component element attributes
        String activate = "activate";
        String deactivate = "deactivate";
        String modified = null;
        int init = 0;
        String componentName = implClass;

        Matcher componentMatcher = COMPONENT_TAG.matcher(xml);
        if (componentMatcher.find()) {
            String attrs = componentMatcher.group(1);
            Map<String, String> attrMap = parseAttributes(attrs);
            activate = attrMap.getOrDefault("activate", "activate");
            deactivate = attrMap.getOrDefault("deactivate", "deactivate");
            modified = attrMap.get("modified");
            componentName = attrMap.getOrDefault("name", implClass);
            String initStr = attrMap.get("init");
            if (initStr != null) {
                try {
                    init = Integer.parseInt(initStr);
                } catch (NumberFormatException e) {
                    LOG.log(Level.WARNING, "Invalid init value in DS XML: " + initStr);
                }
            }
        }

        descriptors.put(implClass, new ComponentDescriptor(
                implClass, componentName, activate, deactivate, modified, init));
    }

    private Map<String, String> parseAttributes(String text) {
        Map<String, String> attrs = new HashMap<>();
        Matcher matcher = ATTRIBUTE.matcher(text);
        while (matcher.find()) {
            attrs.put(matcher.group(1), matcher.group(2));
        }
        return attrs;
    }

    private void addDynamicImports(WovenClass wovenClass) {
        List<String> imports = wovenClass.getDynamicImports();
        imports.add("io.opentelemetry.api");
        imports.add("io.opentelemetry.api.trace");
        imports.add("io.opentelemetry.api.metrics");
        imports.add("io.opentelemetry.api.common");
        imports.add("io.opentelemetry.context");
        imports.add("org.eclipse.osgi.technology.opentelemetry.weaving");
        imports.add("org.eclipse.osgi.technology.opentelemetry.weaver.scr");
    }
}
