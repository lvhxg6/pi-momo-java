package com.pi.coding.extension;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Autocomplete item for command argument completion.
 *
 * @param value       the completion value
 * @param label       display label (may be null, defaults to value)
 * @param description description of the completion (may be null)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AutocompleteItem(
    @JsonProperty("value") String value,
    @JsonProperty("label") String label,
    @JsonProperty("description") String description
) {

    /**
     * Create an autocomplete item with just a value.
     *
     * @param value the completion value
     */
    public AutocompleteItem(String value) {
        this(value, null, null);
    }

    /**
     * Create an autocomplete item with value and label.
     *
     * @param value the completion value
     * @param label display label
     */
    public AutocompleteItem(String value, String label) {
        this(value, label, null);
    }
}
