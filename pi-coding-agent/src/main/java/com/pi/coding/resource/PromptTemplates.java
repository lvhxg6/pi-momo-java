package com.pi.coding.resource;

import com.pi.coding.util.Frontmatter;
import com.pi.coding.util.FrontmatterResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Prompt templates system for loading and expanding prompt templates.
 * 
 * <p>Templates are markdown files that can contain argument placeholders:
 * <ul>
 *   <li>$1, $2, ... for positional arguments</li>
 *   <li>$@ and $ARGUMENTS for all arguments</li>
 *   <li>${@:N} for arguments from Nth onwards</li>
 *   <li>${@:N:L} for L arguments starting from Nth</li>
 * </ul>
 */
public final class PromptTemplates {
    
    private PromptTemplates() {
        // Utility class
    }
    
    /**
     * Load prompt templates from all configured locations.
     * 
     * @param options Load options
     * @return List of loaded templates
     */
    public static List<PromptTemplate> loadPromptTemplates(LoadPromptTemplatesOptions options) {
        List<PromptTemplate> templates = new ArrayList<>();
        
        if (options.includeDefaults()) {
            // Load global templates
            Path globalPromptsDir = Paths.get(options.agentDir(), "prompts");
            templates.addAll(loadTemplatesFromDir(globalPromptsDir, "user", "(user)"));
            
            // Load project templates
            Path projectPromptsDir = Paths.get(options.cwd(), ".kiro", "prompts");
            templates.addAll(loadTemplatesFromDir(projectPromptsDir, "project", "(project)"));
        }
        
        // Load from explicit paths
        Path userPromptsDir = Paths.get(options.agentDir(), "prompts");
        Path projectPromptsDir = Paths.get(options.cwd(), ".kiro", "prompts");
        
        for (String rawPath : options.promptPaths()) {
            Path resolvedPath = resolvePromptPath(rawPath, options.cwd());
            
            if (!Files.exists(resolvedPath)) {
                continue;
            }
            
            try {
                SourceInfo sourceInfo = determineSourceInfo(
                    resolvedPath, userPromptsDir, projectPromptsDir, options.includeDefaults()
                );
                
                if (Files.isDirectory(resolvedPath)) {
                    templates.addAll(loadTemplatesFromDir(
                        resolvedPath, sourceInfo.source(), sourceInfo.label()
                    ));
                } else if (Files.isRegularFile(resolvedPath) && 
                          resolvedPath.toString().endsWith(".md")) {
                    PromptTemplate template = loadTemplateFromFile(
                        resolvedPath, sourceInfo.source(), sourceInfo.label()
                    );
                    if (template != null) {
                        templates.add(template);
                    }
                }
            } catch (Exception e) {
                // Ignore read failures
            }
        }
        
        return templates;
    }
    
    /**
     * Expand a prompt template if text matches a template name.
     * 
     * @param text Text to expand (should start with /)
     * @param templates Available templates
     * @return Expanded content or original text if not a template
     */
    public static String expandPromptTemplate(String text, List<PromptTemplate> templates) {
        if (!text.startsWith("/")) {
            return text;
        }
        
        int spaceIndex = text.indexOf(' ');
        String templateName = spaceIndex == -1 ? text.substring(1) : text.substring(1, spaceIndex);
        String argsString = spaceIndex == -1 ? "" : text.substring(spaceIndex + 1);
        
        PromptTemplate template = templates.stream()
            .filter(t -> t.name().equals(templateName))
            .findFirst()
            .orElse(null);
        
        if (template != null) {
            List<String> args = parseCommandArgs(argsString);
            return substituteArgs(template.content(), args);
        }
        
        return text;
    }
    
    /**
     * Parse command arguments respecting quoted strings (bash-style).
     * 
     * @param argsString Arguments string
     * @return List of parsed arguments
     */
    public static List<String> parseCommandArgs(String argsString) {
        List<String> args = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        Character inQuote = null;
        
        for (int i = 0; i < argsString.length(); i++) {
            char ch = argsString.charAt(i);
            
            if (inQuote != null) {
                if (ch == inQuote) {
                    inQuote = null;
                } else {
                    current.append(ch);
                }
            } else if (ch == '"' || ch == '\'') {
                inQuote = ch;
            } else if (ch == ' ' || ch == '\t') {
                if (current.length() > 0) {
                    args.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(ch);
            }
        }
        
        if (current.length() > 0) {
            args.add(current.toString());
        }
        
        return args;
    }
    
    /**
     * Substitute argument placeholders in template content.
     * 
     * <p>Supports:
     * <ul>
     *   <li>$1, $2, ... for positional args</li>
     *   <li>$@ and $ARGUMENTS for all args</li>
     *   <li>${@:N} for args from Nth onwards (bash-style slicing)</li>
     *   <li>${@:N:L} for L args starting from Nth</li>
     * </ul>
     * 
     * @param content Template content
     * @param args Arguments to substitute
     * @return Content with substituted arguments
     */
    public static String substituteArgs(String content, List<String> args) {
        String result = content;
        
        // Replace $1, $2, etc. with positional args FIRST
        Pattern positionalPattern = Pattern.compile("\\$(\\d+)");
        Matcher positionalMatcher = positionalPattern.matcher(result);
        StringBuffer sb = new StringBuffer();
        while (positionalMatcher.find()) {
            int index = Integer.parseInt(positionalMatcher.group(1)) - 1;
            String replacement = index >= 0 && index < args.size() ? args.get(index) : "";
            positionalMatcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        positionalMatcher.appendTail(sb);
        result = sb.toString();
        
        // Replace ${@:start} or ${@:start:length} with sliced args
        Pattern slicePattern = Pattern.compile("\\$\\{@:(\\d+)(?::(\\d+))?\\}");
        Matcher sliceMatcher = slicePattern.matcher(result);
        sb = new StringBuffer();
        while (sliceMatcher.find()) {
            int start = Integer.parseInt(sliceMatcher.group(1)) - 1; // Convert to 0-indexed
            if (start < 0) start = 0;
            
            String replacement;
            if (sliceMatcher.group(2) != null) {
                int length = Integer.parseInt(sliceMatcher.group(2));
                int end = Math.min(start + length, args.size());
                replacement = String.join(" ", args.subList(start, end));
            } else {
                replacement = String.join(" ", args.subList(start, args.size()));
            }
            sliceMatcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        sliceMatcher.appendTail(sb);
        result = sb.toString();
        
        // Pre-compute all args joined
        String allArgs = String.join(" ", args);
        
        // Replace $ARGUMENTS with all args
        result = result.replace("$ARGUMENTS", allArgs);
        
        // Replace $@ with all args
        result = result.replace("$@", allArgs);
        
        return result;
    }
    
    // Internal methods
    
    private static List<PromptTemplate> loadTemplatesFromDir(
        Path dir,
        String source,
        String sourceLabel
    ) {
        List<PromptTemplate> templates = new ArrayList<>();
        
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return templates;
        }
        
        try (Stream<Path> entries = Files.list(dir)) {
            entries.forEach(entry -> {
                if (Files.isRegularFile(entry) && entry.toString().endsWith(".md")) {
                    PromptTemplate template = loadTemplateFromFile(entry, source, sourceLabel);
                    if (template != null) {
                        templates.add(template);
                    }
                }
            });
        } catch (IOException e) {
            // Ignore directory read errors
        }
        
        return templates;
    }
    
    private static PromptTemplate loadTemplateFromFile(
        Path filePath,
        String source,
        String sourceLabel
    ) {
        try {
            String rawContent = Files.readString(filePath);
            FrontmatterResult frontmatter = Frontmatter.parseFrontmatter(rawContent);
            
            String fileName = filePath.getFileName().toString();
            String name = fileName.endsWith(".md") ? 
                fileName.substring(0, fileName.length() - 3) : fileName;
            
            // Get description from frontmatter or first non-empty line
            String description = frontmatter.getString("description");
            if (description == null || description.isEmpty()) {
                String[] lines = frontmatter.content().split("\\n");
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        description = trimmed.length() > 60 ? 
                            trimmed.substring(0, 60) + "..." : trimmed;
                        break;
                    }
                }
                if (description == null) {
                    description = "";
                }
            }
            
            // Append source to description
            description = description.isEmpty() ? sourceLabel : description + " " + sourceLabel;
            
            return new PromptTemplate(
                name,
                description,
                frontmatter.content(),
                source,
                filePath.toString()
            );
            
        } catch (Exception e) {
            return null;
        }
    }
    
    private static Path resolvePromptPath(String rawPath, String cwd) {
        String normalized = normalizePath(rawPath);
        Path path = Paths.get(normalized);
        return path.isAbsolute() ? path : Paths.get(cwd).resolve(normalized);
    }
    
    private static String normalizePath(String input) {
        String trimmed = input.trim();
        String home = System.getProperty("user.home");
        
        if (trimmed.equals("~")) {
            return home;
        }
        if (trimmed.startsWith("~/") || trimmed.startsWith("~\\")) {
            return Paths.get(home, trimmed.substring(2)).toString();
        }
        if (trimmed.startsWith("~")) {
            return Paths.get(home, trimmed.substring(1)).toString();
        }
        
        return trimmed;
    }
    
    private static SourceInfo determineSourceInfo(
        Path resolvedPath,
        Path userPromptsDir,
        Path projectPromptsDir,
        boolean includeDefaults
    ) {
        if (!includeDefaults) {
            if (isUnderPath(resolvedPath, userPromptsDir)) {
                return new SourceInfo("user", "(user)");
            }
            if (isUnderPath(resolvedPath, projectPromptsDir)) {
                return new SourceInfo("project", "(project)");
            }
        }
        
        String fileName = resolvedPath.getFileName().toString();
        String base = fileName.endsWith(".md") ? 
            fileName.substring(0, fileName.length() - 3) : fileName;
        if (base.isEmpty()) {
            base = "path";
        }
        return new SourceInfo("path", "(path:" + base + ")");
    }
    
    private static boolean isUnderPath(Path target, Path root) {
        try {
            Path normalizedRoot = root.toAbsolutePath().normalize();
            Path normalizedTarget = target.toAbsolutePath().normalize();
            return normalizedTarget.startsWith(normalizedRoot);
        } catch (Exception e) {
            return false;
        }
    }
    
    private record SourceInfo(String source, String label) {}
}
