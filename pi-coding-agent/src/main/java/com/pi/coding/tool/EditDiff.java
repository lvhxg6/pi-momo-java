package com.pi.coding.tool;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Diff computation utilities for the edit tool.
 * Provides fuzzy matching and unified diff generation.
 */
public final class EditDiff {

    private EditDiff() {
        // Utility class
    }

    /**
     * Detect the line ending style used in content.
     *
     * @param content Content to analyze
     * @return "\r\n" for Windows, "\n" for Unix
     */
    public static String detectLineEnding(String content) {
        int crlfIdx = content.indexOf("\r\n");
        int lfIdx = content.indexOf("\n");
        if (lfIdx == -1) return "\n";
        if (crlfIdx == -1) return "\n";
        return crlfIdx < lfIdx ? "\r\n" : "\n";
    }

    /**
     * Normalize all line endings to LF.
     *
     * @param text Text to normalize
     * @return Text with LF line endings
     */
    public static String normalizeToLF(String text) {
        return text.replace("\r\n", "\n").replace("\r", "\n");
    }

    /**
     * Restore line endings to the specified style.
     *
     * @param text Text to process
     * @param ending Target line ending style
     * @return Text with restored line endings
     */
    public static String restoreLineEndings(String text, String ending) {
        return "\r\n".equals(ending) ? text.replace("\n", "\r\n") : text;
    }

    // Patterns for Unicode normalization
    private static final Pattern SMART_SINGLE_QUOTES = Pattern.compile("[\u2018\u2019\u201A\u201B]");
    private static final Pattern SMART_DOUBLE_QUOTES = Pattern.compile("[\u201C\u201D\u201E\u201F]");
    private static final Pattern DASHES = Pattern.compile("[\u2010\u2011\u2012\u2013\u2014\u2015\u2212]");
    private static final Pattern SPECIAL_SPACES = Pattern.compile("[\u00A0\u2002-\u200A\u202F\u205F\u3000]");

    /**
     * Normalize text for fuzzy matching.
     * Applies progressive transformations:
     * - Strip trailing whitespace from each line
     * - Normalize smart quotes to ASCII equivalents
     * - Normalize Unicode dashes/hyphens to ASCII hyphen
     * - Normalize special Unicode spaces to regular space
     *
     * @param text Text to normalize
     * @return Normalized text
     */
    public static String normalizeForFuzzyMatch(String text) {
        // NFKC normalization
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC);

        // Strip trailing whitespace per line
        String[] lines = normalized.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) sb.append("\n");
            sb.append(stripTrailingWhitespace(lines[i]));
        }
        normalized = sb.toString();

        // Smart single quotes → '
        normalized = SMART_SINGLE_QUOTES.matcher(normalized).replaceAll("'");
        // Smart double quotes → "
        normalized = SMART_DOUBLE_QUOTES.matcher(normalized).replaceAll("\"");
        // Various dashes/hyphens → -
        normalized = DASHES.matcher(normalized).replaceAll("-");
        // Special spaces → regular space
        normalized = SPECIAL_SPACES.matcher(normalized).replaceAll(" ");

        return normalized;
    }

    private static String stripTrailingWhitespace(String line) {
        int end = line.length();
        while (end > 0 && Character.isWhitespace(line.charAt(end - 1))) {
            end--;
        }
        return line.substring(0, end);
    }

    /**
     * Result of fuzzy text matching.
     */
    public record FuzzyMatchResult(
        boolean found,
        int index,
        int matchLength,
        boolean usedFuzzyMatch,
        String contentForReplacement
    ) {
        public static FuzzyMatchResult notFound(String content) {
            return new FuzzyMatchResult(false, -1, 0, false, content);
        }

        public static FuzzyMatchResult exactMatch(int index, int length, String content) {
            return new FuzzyMatchResult(true, index, length, false, content);
        }

        public static FuzzyMatchResult fuzzyMatch(int index, int length, String normalizedContent) {
            return new FuzzyMatchResult(true, index, length, true, normalizedContent);
        }
    }

    /**
     * Find oldText in content, trying exact match first, then fuzzy match.
     *
     * @param content Content to search in
     * @param oldText Text to find
     * @return Match result
     */
    public static FuzzyMatchResult fuzzyFindText(String content, String oldText) {
        // Try exact match first
        int exactIndex = content.indexOf(oldText);
        if (exactIndex != -1) {
            return FuzzyMatchResult.exactMatch(exactIndex, oldText.length(), content);
        }

        // Try fuzzy match
        String fuzzyContent = normalizeForFuzzyMatch(content);
        String fuzzyOldText = normalizeForFuzzyMatch(oldText);
        int fuzzyIndex = fuzzyContent.indexOf(fuzzyOldText);

        if (fuzzyIndex == -1) {
            return FuzzyMatchResult.notFound(content);
        }

        return FuzzyMatchResult.fuzzyMatch(fuzzyIndex, fuzzyOldText.length(), fuzzyContent);
    }

    /**
     * Strip UTF-8 BOM if present.
     *
     * @param content Content to process
     * @return BOM and text without it
     */
    public static BomResult stripBom(String content) {
        if (content.startsWith("\uFEFF")) {
            return new BomResult("\uFEFF", content.substring(1));
        }
        return new BomResult("", content);
    }

    /**
     * Result of BOM stripping.
     */
    public record BomResult(String bom, String text) {}

    /**
     * Generate a unified diff string with line numbers and context.
     *
     * @param oldContent Original content
     * @param newContent New content
     * @return Diff result with diff string and first changed line
     */
    public static DiffResult generateDiffString(String oldContent, String newContent) {
        return generateDiffString(oldContent, newContent, 4);
    }

    /**
     * Generate a unified diff string with line numbers and context.
     *
     * @param oldContent Original content
     * @param newContent New content
     * @param contextLines Number of context lines to show
     * @return Diff result with diff string and first changed line
     */
    public static DiffResult generateDiffString(String oldContent, String newContent, int contextLines) {
        String[] oldLines = oldContent.split("\n", -1);
        String[] newLines = newContent.split("\n", -1);

        // Simple line-by-line diff
        List<DiffPart> parts = computeLineDiff(oldLines, newLines);

        List<String> output = new ArrayList<>();
        int maxLineNum = Math.max(oldLines.length, newLines.length);
        int lineNumWidth = String.valueOf(maxLineNum).length();

        int oldLineNum = 1;
        int newLineNum = 1;
        boolean lastWasChange = false;
        Integer firstChangedLine = null;

        for (int i = 0; i < parts.size(); i++) {
            DiffPart part = parts.get(i);
            String[] lines = part.lines();

            if (part.added() || part.removed()) {
                // Capture the first changed line
                if (firstChangedLine == null) {
                    firstChangedLine = newLineNum;
                }

                for (String line : lines) {
                    if (part.added()) {
                        String lineNum = padLeft(String.valueOf(newLineNum), lineNumWidth);
                        output.add("+" + lineNum + " " + line);
                        newLineNum++;
                    } else {
                        String lineNum = padLeft(String.valueOf(oldLineNum), lineNumWidth);
                        output.add("-" + lineNum + " " + line);
                        oldLineNum++;
                    }
                }
                lastWasChange = true;
            } else {
                // Context lines
                boolean nextPartIsChange = i < parts.size() - 1 && 
                    (parts.get(i + 1).added() || parts.get(i + 1).removed());

                if (lastWasChange || nextPartIsChange) {
                    String[] linesToShow = lines;
                    int skipStart = 0;
                    int skipEnd = 0;

                    if (!lastWasChange) {
                        skipStart = Math.max(0, lines.length - contextLines);
                        linesToShow = copyOfRange(lines, skipStart, lines.length);
                    }

                    if (!nextPartIsChange && linesToShow.length > contextLines) {
                        skipEnd = linesToShow.length - contextLines;
                        linesToShow = copyOfRange(linesToShow, 0, contextLines);
                    }

                    if (skipStart > 0) {
                        output.add(" " + padLeft("", lineNumWidth) + " ...");
                        oldLineNum += skipStart;
                        newLineNum += skipStart;
                    }

                    for (String line : linesToShow) {
                        String lineNum = padLeft(String.valueOf(oldLineNum), lineNumWidth);
                        output.add(" " + lineNum + " " + line);
                        oldLineNum++;
                        newLineNum++;
                    }

                    if (skipEnd > 0) {
                        output.add(" " + padLeft("", lineNumWidth) + " ...");
                        oldLineNum += skipEnd;
                        newLineNum += skipEnd;
                    }
                } else {
                    oldLineNum += lines.length;
                    newLineNum += lines.length;
                }

                lastWasChange = false;
            }
        }

        return new DiffResult(String.join("\n", output), firstChangedLine != null ? firstChangedLine : 1);
    }

    /**
     * Result of diff generation.
     */
    public record DiffResult(String diff, int firstChangedLine) {}

    /**
     * A part of a diff (added, removed, or unchanged lines).
     */
    private record DiffPart(String[] lines, boolean added, boolean removed) {}

    /**
     * Compute a simple line-by-line diff using LCS algorithm.
     */
    private static List<DiffPart> computeLineDiff(String[] oldLines, String[] newLines) {
        // Use a simple LCS-based diff
        int[][] lcs = computeLCS(oldLines, newLines);
        List<DiffPart> parts = new ArrayList<>();

        int i = oldLines.length;
        int j = newLines.length;
        List<String> currentRemoved = new ArrayList<>();
        List<String> currentAdded = new ArrayList<>();
        List<String> currentUnchanged = new ArrayList<>();

        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && oldLines[i - 1].equals(newLines[j - 1])) {
                // Flush any pending changes
                flushChanges(parts, currentRemoved, currentAdded, currentUnchanged);
                currentUnchanged.add(0, oldLines[i - 1]);
                i--;
                j--;
            } else if (j > 0 && (i == 0 || lcs[i][j - 1] >= lcs[i - 1][j])) {
                // Flush unchanged
                if (!currentUnchanged.isEmpty()) {
                    parts.add(0, new DiffPart(currentUnchanged.toArray(new String[0]), false, false));
                    currentUnchanged.clear();
                }
                currentAdded.add(0, newLines[j - 1]);
                j--;
            } else if (i > 0) {
                // Flush unchanged
                if (!currentUnchanged.isEmpty()) {
                    parts.add(0, new DiffPart(currentUnchanged.toArray(new String[0]), false, false));
                    currentUnchanged.clear();
                }
                currentRemoved.add(0, oldLines[i - 1]);
                i--;
            }
        }

        flushChanges(parts, currentRemoved, currentAdded, currentUnchanged);

        return parts;
    }

    private static void flushChanges(List<DiffPart> parts, 
            List<String> removed, List<String> added, List<String> unchanged) {
        if (!unchanged.isEmpty()) {
            parts.add(0, new DiffPart(unchanged.toArray(new String[0]), false, false));
            unchanged.clear();
        }
        if (!added.isEmpty()) {
            parts.add(0, new DiffPart(added.toArray(new String[0]), true, false));
            added.clear();
        }
        if (!removed.isEmpty()) {
            parts.add(0, new DiffPart(removed.toArray(new String[0]), false, true));
            removed.clear();
        }
    }

    private static int[][] computeLCS(String[] a, String[] b) {
        int m = a.length;
        int n = b.length;
        int[][] dp = new int[m + 1][n + 1];

        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (a[i - 1].equals(b[j - 1])) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }

        return dp;
    }

    private static String padLeft(String s, int width) {
        if (s.length() >= width) return s;
        return " ".repeat(width - s.length()) + s;
    }

    private static String[] copyOfRange(String[] arr, int from, int to) {
        String[] result = new String[to - from];
        System.arraycopy(arr, from, result, 0, to - from);
        return result;
    }
}
