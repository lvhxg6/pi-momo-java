package com.pi.ai.core.types;

/**
 * Mutable token usage accumulator for streaming scenarios.
 *
 * <p>During streaming, token counts arrive incrementally. This class
 * collects them and converts to an immutable {@link Usage} record
 * via {@link #toUsage()} once the stream completes.
 */
public final class MutableUsage {

    private int input;
    private int output;
    private int cacheRead;
    private int cacheWrite;
    private Usage.Cost cost;

    public MutableUsage() { }

    // --- Accumulation methods ---

    public void addInput(int tokens) {
        this.input += tokens;
    }

    public void addOutput(int tokens) {
        this.output += tokens;
    }

    public void addCacheRead(int tokens) {
        this.cacheRead += tokens;
    }

    public void addCacheWrite(int tokens) {
        this.cacheWrite += tokens;
    }

    public void setCost(Usage.Cost cost) {
        this.cost = cost;
    }

    /**
     * Computes total tokens as the sum of all token fields.
     */
    public int computeTotalTokens() {
        return input + output + cacheRead + cacheWrite;
    }

    /**
     * Converts this mutable accumulator to an immutable {@link Usage} record.
     */
    public Usage toUsage() {
        return new Usage(input, output, cacheRead, cacheWrite, computeTotalTokens(), cost);
    }

    // --- Getters ---

    public int getInput() {
        return input;
    }

    public int getOutput() {
        return output;
    }

    public int getCacheRead() {
        return cacheRead;
    }

    public int getCacheWrite() {
        return cacheWrite;
    }

    public Usage.Cost getCost() {
        return cost;
    }
}
