package com.likide.a11y.pdf.fonts;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

/**
 * Runtime font resolver used during rendering for variant fallback and glyph fallback chunking.
 */
public final class FontRuntime {

    public record FontChunk(PDFont font, String text) {
    }

    private final PDFont regular;
    private final PDFont bold;
    private final PDFont italic;
    private final PDFont boldItalic;
    private final List<PDFont> fallbackFonts;

    private FontRuntime(PDFont regular, PDFont bold, PDFont italic, PDFont boldItalic, List<PDFont> fallbackFonts) {
        this.regular = regular;
        this.bold = bold;
        this.italic = italic;
        this.boldItalic = boldItalic;
        this.fallbackFonts = fallbackFonts;
    }

    public static FontRuntime load(PDDocument doc, A11yFontFamily family, List<Path> fallbackFiles) {
        try {
            PDFont regular = loadFont(doc, family.regular());
            PDFont bold = family.bold() == null ? regular : loadFont(doc, family.bold());
            PDFont italic = family.italic() == null ? regular : loadFont(doc, family.italic());
            PDFont boldItalic = family.boldItalic() == null ? bold : loadFont(doc, family.boldItalic());

            List<PDFont> fallback = new ArrayList<>();
            for (Path file : fallbackFiles) {
                fallback.add(PDType0Font.load(doc, file.toFile()));
            }

            return new FontRuntime(regular, bold, italic, boldItalic, List.copyOf(fallback));
        } catch (IOException e) {
            throw new FontResolutionException("Failed to load configured font resources: " + e.getMessage());
        }
    }

    public boolean hasFallbackFonts() {
        return !fallbackFonts.isEmpty();
    }

    public List<FontChunk> chunkText(String text, FontVariant variant) {
        if (text == null || text.isEmpty()) {
            return List.of(new FontChunk(resolveVariant(variant), ""));
        }

        List<FontChunk> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        PDFont currentFont = null;

        int index = 0;
        while (index < text.length()) {
            int codePoint = text.codePointAt(index);
            String token = new String(Character.toChars(codePoint));
            PDFont selected = resolveFontForCodePoint(codePoint, variant);

            if (currentFont == null || currentFont != selected) {
                if (current.length() > 0) {
                    chunks.add(new FontChunk(currentFont, current.toString()));
                    current.setLength(0);
                }
                currentFont = selected;
            }

            current.append(token);
            index += Character.charCount(codePoint);
        }

        if (current.length() > 0) {
            chunks.add(new FontChunk(currentFont, current.toString()));
        }

        return chunks;
    }

    public PDFont resolveVariant(FontVariant variant) {
        return switch (variant) {
            case REGULAR -> regular;
            case BOLD -> bold;
            case ITALIC -> italic;
            case BOLD_ITALIC -> boldItalic;
        };
    }

    private PDFont resolveFontForCodePoint(int codePoint, FontVariant variant) {
        PDFont preferred = resolveVariant(variant);
        if (supports(preferred, codePoint)) {
            return preferred;
        }

        if (variant != FontVariant.REGULAR && supports(regular, codePoint)) {
            return regular;
        }

        for (PDFont fallback : fallbackFonts) {
            if (supports(fallback, codePoint)) {
                return fallback;
            }
        }

        return preferred;
    }

    private static PDFont loadFont(PDDocument doc, A11yFontFamily.FontSource source) throws IOException {
        if (source.isStandard14()) {
            return new PDType1Font(source.standard14());
        }
        return PDType0Font.load(doc, source.path().toFile());
    }

    private boolean supports(PDFont font, int codePoint) {
        try {
            font.getStringWidth(new String(Character.toChars(codePoint)));
            return true;
        } catch (IOException | IllegalArgumentException ignored) {
            return false;
        }
    }
}
