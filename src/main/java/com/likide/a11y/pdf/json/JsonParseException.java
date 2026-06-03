package com.likide.a11y.pdf.json;

/**
 * Thrown when a JSON document cannot be mapped to a {@link com.likide.a11y.pdf.model.DeclarativeDocument}.
 */
public final class JsonParseException extends RuntimeException {

    public JsonParseException(String message) {
        super(message);
    }

    public JsonParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
