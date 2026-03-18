package com.pi.coding.extension;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Extension loader - discovers and loads Java extension modules.
 *
 * <p>Supports loading extensions from:
 * <ul>
 *   <li>Java ServiceLoader (META-INF/services)</li>
 *   <li>Explicit paths to extension JARs</li>
 *   <li>Standard locations (project-local and global directories)</li>
 * </ul>
 *
 * <p><b>Validates: Requirements 5.1, 5.2</b>
 */
public class ExtensionLoader {

    private static final Logger logger = LoggerFactory.getLogger(ExtensionLoader.class);

    private static final String EXTENSIONS_DIR = "extensions";
    private static final String PI_DIR = ".pi";

    /**
     * Discover and load extensions from paths.
     *
     * <p><b>Validates: Requirement 5.1</b>
     *
     * @param paths     explicit extension paths
     * @param cwd       current working directory
     * @param agentDir  agent directory (e.g., ~/.pi)
     * @param runner    the extension runner to load into
     * @return the load result
     */
    public static LoadExtensionsResult discoverAndLoadExtensions(
            List<String> paths, String cwd, String agentDir, ExtensionRunner runner) {

        List<ExtensionFactory> factories = new ArrayList<>();
        List<LoadExtensionsResult.LoadError> discoveryErrors = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // 1. Load from ServiceLoader (Java module system)
        try {
            ServiceLoader<ExtensionFactory> serviceLoader = ServiceLoader.load(ExtensionFactory.class);
            for (ExtensionFactory factory : serviceLoader) {
                String className = factory.getClass().getName();
                if (!seen.contains(className)) {
                    seen.add(className);
                    factories.add(factory);
                    logger.debug("Discovered extension via ServiceLoader: {}", className);
                }
            }
        } catch (Exception e) {
            logger.warn("Error loading extensions via ServiceLoader: {}", e.getMessage());
            discoveryErrors.add(new LoadExtensionsResult.LoadError("ServiceLoader", e.getMessage()));
        }

        // 2. Project-local extensions: cwd/.pi/extensions/
        Path localExtDir = Path.of(cwd, PI_DIR, EXTENSIONS_DIR);
        discoverExtensionsInDir(localExtDir, factories, discoveryErrors, seen);

        // 3. Global extensions: agentDir/extensions/
        if (agentDir != null) {
            Path globalExtDir = Path.of(agentDir, EXTENSIONS_DIR);
            discoverExtensionsInDir(globalExtDir, factories, discoveryErrors, seen);
        }

        // 4. Explicitly configured paths
        for (String path : paths) {
            Path extPath = Path.of(path);
            if (!extPath.isAbsolute()) {
                extPath = Path.of(cwd).resolve(path);
            }
            discoverExtensionAtPath(extPath, factories, discoveryErrors, seen);
        }

        // Load all discovered factories
        LoadExtensionsResult loadResult = runner.loadExtensions(factories);

        // Combine discovery errors with load errors
        List<LoadExtensionsResult.LoadError> allErrors = new ArrayList<>(discoveryErrors);
        allErrors.addAll(loadResult.errors());

        return new LoadExtensionsResult(loadResult.extensions(), allErrors);
    }

    /**
     * Discover extensions in a directory.
     *
     * <p>Looks for:
     * <ul>
     *   <li>JAR files containing ExtensionFactory implementations</li>
     *   <li>Subdirectories with extension modules</li>
     * </ul>
     */
    private static void discoverExtensionsInDir(
            Path dir,
            List<ExtensionFactory> factories,
            List<LoadExtensionsResult.LoadError> errors,
            Set<String> seen) {

        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return;
        }

        try (Stream<Path> entries = Files.list(dir)) {
            entries.forEach(entry -> {
                discoverExtensionAtPath(entry, factories, errors, seen);
            });
        } catch (IOException e) {
            logger.warn("Error listing extensions directory {}: {}", dir, e.getMessage());
            errors.add(new LoadExtensionsResult.LoadError(dir.toString(), e.getMessage()));
        }
    }

    /**
     * Discover extension at a specific path.
     */
    private static void discoverExtensionAtPath(
            Path path,
            List<ExtensionFactory> factories,
            List<LoadExtensionsResult.LoadError> errors,
            Set<String> seen) {

        String pathStr = path.toAbsolutePath().toString();
        if (seen.contains(pathStr)) {
            return;
        }

        if (Files.isRegularFile(path) && path.toString().endsWith(".jar")) {
            // Load JAR file
            try {
                List<ExtensionFactory> jarFactories = loadExtensionsFromJar(path);
                for (ExtensionFactory factory : jarFactories) {
                    String key = pathStr + ":" + factory.getClass().getName();
                    if (!seen.contains(key)) {
                        seen.add(key);
                        factories.add(factory);
                        logger.debug("Discovered extension from JAR: {} -> {}", path, factory.getClass().getName());
                    }
                }
            } catch (Exception e) {
                logger.warn("Error loading extension JAR {}: {}", path, e.getMessage());
                errors.add(new LoadExtensionsResult.LoadError(pathStr, e.getMessage()));
            }
        } else if (Files.isDirectory(path)) {
            // Check for extension module in directory
            discoverExtensionsInDir(path, factories, errors, seen);
        }
    }

    /**
     * Load extension factories from a JAR file.
     *
     * <p><b>Validates: Requirement 5.2</b>
     */
    private static List<ExtensionFactory> loadExtensionsFromJar(Path jarPath) throws Exception {
        List<ExtensionFactory> factories = new ArrayList<>();

        // Use URLClassLoader to load the JAR
        java.net.URL jarUrl = jarPath.toUri().toURL();
        try (java.net.URLClassLoader classLoader = new java.net.URLClassLoader(
                new java.net.URL[]{jarUrl},
                ExtensionLoader.class.getClassLoader())) {

            // Use ServiceLoader with the JAR's classloader
            ServiceLoader<ExtensionFactory> serviceLoader = ServiceLoader.load(ExtensionFactory.class, classLoader);
            for (ExtensionFactory factory : serviceLoader) {
                factories.add(factory);
            }
        }

        return factories;
    }

    /**
     * Load extensions from explicit factory instances.
     *
     * <p>Useful for programmatic extension registration.
     *
     * @param factories the factory instances
     * @param runner    the extension runner
     * @return the load result
     */
    public static LoadExtensionsResult loadExtensions(List<ExtensionFactory> factories, ExtensionRunner runner) {
        return runner.loadExtensions(factories);
    }

    /**
     * Create an extension from an inline factory function.
     *
     * @param factory the factory function
     * @param runner  the extension runner
     * @return the load result
     */
    public static LoadExtensionsResult loadExtensionFromFactory(ExtensionFactory factory, ExtensionRunner runner) {
        return runner.loadExtensions(List.of(factory));
    }
}
