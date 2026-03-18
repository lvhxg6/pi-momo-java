package com.pi.coding.settings;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Token budgets for different thinking levels.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ThinkingBudgets(
    @JsonProperty("low") Integer low,
    @JsonProperty("medium") Integer medium,
    @JsonProperty("high") Integer high,
    @JsonProperty("xhigh") Integer xhigh
) {
    public static final ThinkingBudgets DEFAULT = new ThinkingBudgets(2000, 8000, 16000, 32000);

    public int getLow() { return low != null ? low : 2000; }
    public int getMedium() { return medium != null ? medium : 8000; }
    public int getHigh() { return high != null ? high : 16000; }
    public int getXhigh() { return xhigh != null ? xhigh : 32000; }
}
