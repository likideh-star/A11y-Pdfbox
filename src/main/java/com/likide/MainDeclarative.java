package com.likide;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import com.likide.a11y.pdf.A11yPdfDocument;
import com.likide.a11y.pdf.json.JsonParser;
import com.likide.a11y.pdf.model.DeclarativeDocument;

public class MainDeclarative {

    private static final String DEFAULT_JSON_RESOURCE = "examples/declarative-visual.json";
    // private static final String DEFAULT_JSON_RESOURCE = "examples/article-style.json";

    public static void main(String[] args) {
        try {
            Files.createDirectories(Path.of("target"));
            Path output = Path.of("target", "visual-check-declarative.pdf");
            // Path output = Path.of("target", "article-style.pdf");

            DeclarativeDocument doc = loadDeclarativeExample(args);
                byte[] pdf = A11yPdfDocument.fromDeclarative(doc).buildBytes();

            Files.write(output, pdf);

            System.out.println("Declarative visual test PDF generated: " + output.toAbsolutePath());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate declarative visual test PDF", e);
        }
    }

    private static DeclarativeDocument loadDeclarativeExample(String[] args) throws IOException {
        if (args != null && args.length > 0 && args[0] != null && !args[0].isBlank()) {
            return JsonParser.parse(Path.of(args[0]));
        }
        try (InputStream in = MainDeclarative.class.getClassLoader().getResourceAsStream(DEFAULT_JSON_RESOURCE)) {
            if (in != null) {
                return JsonParser.parse(in);
            }
        }
        Path fallback = Path.of("src", "main", "resources", DEFAULT_JSON_RESOURCE);
        if (!Files.exists(fallback)) {
            throw new IllegalStateException(
                    "Missing default declarative JSON resource: " + DEFAULT_JSON_RESOURCE
                            + " and fallback file not found: " + fallback);
        }
        return JsonParser.parse(fallback);
    }
}
