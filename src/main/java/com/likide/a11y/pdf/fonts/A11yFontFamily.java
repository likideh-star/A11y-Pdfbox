package com.likide.a11y.pdf.fonts;

import java.nio.file.Path;

import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Defines font sources for regular/bold/italic/bold-italic document text variants.
 */
public final class A11yFontFamily {

    public record FontSource(Standard14Fonts.FontName standard14, Path path) {

        public FontSource {
            if (standard14 == null && path == null) {
                throw new IllegalArgumentException("FontSource requires either Standard14 name or font file path");
            }
            if (standard14 != null && path != null) {
                throw new IllegalArgumentException("FontSource must use either Standard14 name or font file path, not both");
            }
        }

        public static FontSource standard14(Standard14Fonts.FontName fontName) {
            return new FontSource(fontName, null);
        }

        public static FontSource file(Path path) {
            return new FontSource(null, path);
        }

        public boolean isStandard14() {
            return standard14 != null;
        }
    }

    private final FontSource regular;
    private final FontSource bold;
    private final FontSource italic;
    private final FontSource boldItalic;

    public A11yFontFamily(FontSource regular, FontSource bold, FontSource italic, FontSource boldItalic) {
        if (regular == null) {
            throw new IllegalArgumentException("Regular variant is required");
        }
        this.regular = regular;
        this.bold = bold;
        this.italic = italic;
        this.boldItalic = boldItalic;
    }

    public FontSource regular() {
        return regular;
    }

    public FontSource bold() {
        return bold;
    }

    public FontSource italic() {
        return italic;
    }

    public FontSource boldItalic() {
        return boldItalic;
    }

    public static A11yFontFamily helvetica() {
        return new A11yFontFamily(
                FontSource.standard14(Standard14Fonts.FontName.HELVETICA),
                FontSource.standard14(Standard14Fonts.FontName.HELVETICA_BOLD),
                FontSource.standard14(Standard14Fonts.FontName.HELVETICA_OBLIQUE),
                FontSource.standard14(Standard14Fonts.FontName.HELVETICA_BOLD_OBLIQUE));
    }
}
