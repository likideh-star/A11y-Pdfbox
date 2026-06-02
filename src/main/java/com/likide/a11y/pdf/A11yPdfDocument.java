package com.likide.a11y.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot;
import org.apache.pdfbox.pdmodel.documentinterchange.taggedpdf.StandardStructureTypes;
import org.apache.pdfbox.pdmodel.interactive.viewerpreferences.PDViewerPreferences;

/**
 * Minimal fluent API skeleton aligned with the planned adapter names.
 *
 * This is intentionally small but executable so PRD tests can run end-to-end.
 */
public final class A11yPdfDocument {

    private A11yPdfDocument() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String lang = "en-US";
        private String title = "Untitled";
        private boolean displayDocTitle = true;
        private int columns = 1;
        private float columnGap = 0.0f;
        private String artifactHeaderFooterPattern;

        private final List<Element> elements = new ArrayList<>();
        private int lastHeadingLevel = 0;

        public Builder lang(String value) {
            this.lang = value;
            return this;
        }

        public Builder title(String value) {
            this.title = value;
            return this;
        }

        public Builder displayDocTitle(boolean value) {
            this.displayDocTitle = value;
            return this;
        }

        public Builder columns(int count, float gapPt) {
            if (count < 1) {
                throw new IllegalArgumentException("columns must be >= 1");
            }
            this.columns = count;
            this.columnGap = gapPt;
            return this;
        }

        public Builder paragraph(String text) {
            elements.add(new Paragraph(text));
            return this;
        }

        public Builder heading(int level, String text) {
            if (level < 1 || level > 6) {
                throw new IllegalArgumentException("heading level must be between 1 and 6");
            }
            if (lastHeadingLevel > 0 && level > lastHeadingLevel + 1) {
                throw new IllegalStateException("Heading hierarchy skip detected: H" + lastHeadingLevel + " -> H" + level);
            }
            lastHeadingLevel = level;
            elements.add(new Heading(level, text));
            return this;
        }

        public Builder image(String pathOrId, String altText, boolean decorative) {
            if (!decorative && (altText == null || altText.isBlank())) {
                throw new IllegalStateException("Image requires altText unless decorative=true");
            }
            elements.add(new Figure(pathOrId, altText, decorative));
            return this;
        }

        public ListBuilder unorderedList() {
            ListBlock block = new ListBlock();
            elements.add(block);
            return new ListBuilder(this, block);
        }

        public Builder artifactHeaderFooter(String pageTextPattern) {
            this.artifactHeaderFooterPattern = pageTextPattern;
            return this;
        }

        public byte[] buildBytes() {
            try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                PDPage page = new PDPage(PDRectangle.LETTER);
                page.getCOSObject().setItem(COSName.getPDFName("Tabs"), COSName.S);
                doc.addPage(page);

                setupCatalogMetadata(doc);
                buildStructureTree(doc);
                maybeWriteArtifactMarker(doc, page);

                doc.save(out);
                return out.toByteArray();
            } catch (IOException e) {
                throw new IllegalStateException("Failed to build PDF bytes", e);
            }
        }

        private void setupCatalogMetadata(PDDocument doc) throws IOException {
            PDDocumentCatalog catalog = doc.getDocumentCatalog();
            catalog.setLanguage(lang);

            PDDocumentInformation info = doc.getDocumentInformation();
            info.setTitle(title);

            PDViewerPreferences prefs = new PDViewerPreferences(new COSDictionary());
            prefs.setDisplayDocTitle(displayDocTitle);
            catalog.setViewerPreferences(prefs);

            String xmp = buildPdfUaXmp(title, lang);
            PDMetadata metadata = new PDMetadata(doc);
            metadata.importXMPMetadata(xmp.getBytes(StandardCharsets.UTF_8));
            catalog.setMetadata(metadata);

            catalog.setNames(new PDDocumentNameDictionary(catalog));
        }

        private void buildStructureTree(PDDocument doc) {
            PDDocumentCatalog catalog = doc.getDocumentCatalog();
            PDStructureTreeRoot root = new PDStructureTreeRoot();
            catalog.setStructureTreeRoot(root);

            for (Element element : elements) {
                if (element instanceof Heading heading) {
                    root.appendKid(new PDStructureElement(mapHeadingType(heading.level), root));
                } else if (element instanceof Paragraph) {
                    root.appendKid(new PDStructureElement(StandardStructureTypes.P, root));
                } else if (element instanceof Figure) {
                    root.appendKid(new PDStructureElement(StandardStructureTypes.Figure, root));
                } else if (element instanceof ListBlock listBlock) {
                    PDStructureElement list = new PDStructureElement(StandardStructureTypes.L, root);
                    root.appendKid(list);
                    for (String ignored : listBlock.items) {
                        PDStructureElement li = new PDStructureElement(StandardStructureTypes.LI, list);
                        list.appendKid(li);
                        li.appendKid(new PDStructureElement("Lbl", li));
                        li.appendKid(new PDStructureElement("LBody", li));
                    }
                }
            }
        }

        private void maybeWriteArtifactMarker(PDDocument doc, PDPage page) throws IOException {
            if (artifactHeaderFooterPattern == null) {
                return;
            }
            try (PDPageContentStream contentStream = new PDPageContentStream(
                    doc,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true)) {
                contentStream.beginMarkedContent(COSName.getPDFName("Artifact"));
                contentStream.endMarkedContent();
            }
        }

        private String mapHeadingType(int level) {
            return switch (level) {
                case 1 -> StandardStructureTypes.H1;
                case 2 -> StandardStructureTypes.H2;
                case 3 -> StandardStructureTypes.H3;
                case 4 -> StandardStructureTypes.H4;
                case 5 -> StandardStructureTypes.H5;
                case 6 -> StandardStructureTypes.H6;
                default -> throw new IllegalArgumentException("Unsupported heading level: " + level);
            };
        }

        private String buildPdfUaXmp(String documentTitle, String language) {
            String safeTitle = documentTitle == null ? "Untitled" : documentTitle;
            String safeLang = language == null ? "en-US" : language;
            return "<?xpacket begin=\"\uFEFF\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>"
                    + "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">"
                    + "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">"
                    + "<rdf:Description rdf:about=\"\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:pdfuaid=\"http://www.aiim.org/pdfua/ns/id/\">"
                    + "<dc:title><rdf:Alt><rdf:li xml:lang=\"x-default\">" + escapeXml(safeTitle) + "</rdf:li></rdf:Alt></dc:title>"
                    + "<dc:language><rdf:Bag><rdf:li>" + escapeXml(safeLang) + "</rdf:li></rdf:Bag></dc:language>"
                    + "<pdfuaid:part>1</pdfuaid:part>"
                    + "</rdf:Description>"
                    + "</rdf:RDF>"
                    + "</x:xmpmeta>"
                    + "<?xpacket end=\"w\"?>";
        }

        private String escapeXml(String value) {
            return value
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&apos;");
        }
    }

    public static final class ListBuilder {
        private final Builder parent;
        private final ListBlock block;

        private ListBuilder(Builder parent, ListBlock block) {
            this.parent = parent;
            this.block = block;
        }

        public ListBuilder item(String text) {
            block.items.add(text);
            return this;
        }

        public Builder endList() {
            return parent;
        }
    }

    private sealed interface Element permits Heading, Paragraph, Figure, ListBlock {
    }

    private static final class Heading implements Element {
        private final int level;
        @SuppressWarnings("unused")
        private final String text;

        private Heading(int level, String text) {
            this.level = level;
            this.text = text;
        }
    }

    private static final class Paragraph implements Element {
        @SuppressWarnings("unused")
        private final String text;

        private Paragraph(String text) {
            this.text = text;
        }
    }

    private static final class Figure implements Element {
        @SuppressWarnings("unused")
        private final String pathOrId;
        @SuppressWarnings("unused")
        private final String altText;
        @SuppressWarnings("unused")
        private final boolean decorative;

        private Figure(String pathOrId, String altText, boolean decorative) {
            this.pathOrId = pathOrId;
            this.altText = altText;
            this.decorative = decorative;
        }
    }

    private static final class ListBlock implements Element {
        private final List<String> items = new ArrayList<>();
    }
}
