package com.pi.coding.extension;

/**
 * Flag definition for registerFlag().
 *
 * <p>Defines a CLI flag that can be passed to the agent.
 *
 * @param name         flag name (without leading dashes)
 * @param description  flag description (may be null)
 * @param type         flag type ("boolean" or "string")
 * @param defaultValue default value (may be null)
 */
public record FlagDefinition(
    String name,
    String description,
    FlagType type,
    Object defaultValue
) {

    /**
     * Flag type enumeration.
     */
    public enum FlagType {
        BOOLEAN,
        STRING
    }

    /**
     * Builder for creating FlagDefinition instances.
     */
    public static class Builder {
        private String name;
        private String description;
        private FlagType type = FlagType.BOOLEAN;
        private Object defaultValue;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder type(FlagType type) {
            this.type = type;
            return this;
        }

        public Builder defaultValue(Object defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        public FlagDefinition build() {
            return new FlagDefinition(name, description, type, defaultValue);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
