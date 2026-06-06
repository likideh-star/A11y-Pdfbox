package com.likide.a11y.pdf.model;

/**
 * Declarative font family configuration for a single named font family.
 *
 * <p>All four paths are optional; omitted variants fall back to the regular variant.
 * Paths can be absolute or relative to the process working directory.
 *
 * <p>Example JSON:
 * <pre>
 * "fonts": {
 *   "default": {
 *     "regular": "fonts/LiberationSans-Regular.ttf",
 *     "bold":    "fonts/LiberationSans-Bold.ttf",
 *     "italic":  "fonts/LiberationSans-Italic.ttf",
 *     "boldItalic": "fonts/LiberationSans-BoldItalic.ttf"
 *   }
 * }
 * </pre>
 */
public final class DeclarativeFontConfig {
    public String regular;
    public String bold;
    public String italic;
    public String boldItalic;
}
