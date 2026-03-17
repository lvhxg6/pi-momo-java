package com.pi.ai.core.types;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Image content block. Appears in user messages and tool result messages.
 *
 * @param type     always {@code "image"}
 * @param data     Base64-encoded image data
 * @param mimeType the image MIME type (e.g. {@code "image/jpeg"}, {@code "image/png"})
 */
public record ImageContent(
    @JsonProperty("type") String type,
    @JsonProperty("data") String data,
    @JsonProperty("mimeType") String mimeType
) implements UserContentBlock {

    /**
     * Convenience constructor that sets type automatically.
     */
    public ImageContent(String data, String mimeType) {
        this("image", data, mimeType);
    }
}
