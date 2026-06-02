package com.likide.a11y.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
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

import com.likide.a11y.pdf.model.DeclarativeDocument;
import com.likide.a11y.pdf.model.DocumentModelConverter;
import com.likide.a11y.pdf.model.FluentDocumentSnapshot;
import com.likide.a11y.pdf.model.FluentFigureNode;
import com.likide.a11y.pdf.model.FluentHeadingNode;
import com.likide.a11y.pdf.model.FluentListNode;
import com.likide.a11y.pdf.model.FluentNode;
import com.likide.a11y.pdf.model.FluentParagraphNode;
import com.likide.a11y.pdf.model.IntermediateBoxModel;
import com.likide.a11y.pdf.model.IntermediateDocument;
import com.likide.a11y.pdf.model.IntermediateFigure;
import com.likide.a11y.pdf.model.IntermediateHeading;
import com.likide.a11y.pdf.model.IntermediateList;
import com.likide.a11y.pdf.model.IntermediateNode;
import com.likide.a11y.pdf.model.IntermediateParagraph;
import com.likide.a11y.pdf.model.IntermediateTextStyle;
import com.likide.a11y.pdf.rendering.RenderingException;
import com.likide.a11y.pdf.validation.ValidationException;

/**
 * Minimal fluent API skeleton aligned with the planned adapter names.
 *
 * This is intentionally small but executable so PRD tests can run end-to-end.
 */
public final class A11yPdfDocument {

    private static final float DEFAULT_PAGE_MARGIN = 72.0f;

    private A11yPdfDocument() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder fromDeclarative(DeclarativeDocument document) {
        IntermediateDocument model = DocumentModelConverter.fromDeclarative(document);
        Builder builder = builder()
                .lang(model.lang())
                .title(model.title())
                .displayDocTitle(model.displayDocTitle())
                .columns(model.pageSettings().columns(), model.pageSettings().columnGap())
                .pageSize(model.pageSettings().pageWidth(), model.pageSettings().pageHeight())
                .pageMargins(
                        model.pageSettings().marginTop(),
                        model.pageSettings().marginRight(),
                        model.pageSettings().marginBottom(),
                        model.pageSettings().marginLeft());

        for (IntermediateNode node : model.nodes()) {
            if (node instanceof IntermediateHeading heading) {
                builder.heading(
                        heading.level(),
                        heading.text(),
                        fromIntermediateBoxModel(heading.style().boxModel()),
                        heading.style().lineHeightMultiplier());
            } else if (node instanceof IntermediateParagraph paragraph) {
                builder.paragraph(
                        paragraph.text(),
                        fromIntermediateBoxModel(paragraph.style().boxModel()),
                        paragraph.style().lineHeightMultiplier());
            } else if (node instanceof IntermediateFigure figure) {
                builder.image(figure.pathOrId(), figure.altText(), figure.decorative());
            } else if (node instanceof IntermediateList list) {
                ListBuilder listBuilder = builder.unorderedList();
                for (String item : list.items()) {
                    listBuilder.item(item);
                }
                listBuilder.endList();
            }
        }

        return builder;
    }

    private static BoxModel fromIntermediateBoxModel(IntermediateBoxModel boxModel) {
        return new BoxModel(
                boxModel.marginTop(),
                boxModel.paddingTop(),
                boxModel.paddingRight(),
                boxModel.paddingBottom(),
                boxModel.paddingLeft(),
                boxModel.marginBottom());
    }

    public static final class Builder {
        private String lang = "en-US";
        private String title = "Untitled";
        private boolean displayDocTitle = true;
        private int columns = 1;
        private float columnGap = 0.0f;
        private float pageWidth = PDRectangle.A4.getWidth();
        private float pageHeight = PDRectangle.A4.getHeight();
        private float marginTop = DEFAULT_PAGE_MARGIN;
        private float marginRight = DEFAULT_PAGE_MARGIN;
        private float marginBottom = DEFAULT_PAGE_MARGIN;
        private float marginLeft = DEFAULT_PAGE_MARGIN;
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
                throw new ValidationException("columns must be >= 1");
            }
            this.columns = count;
            this.columnGap = gapPt;
            return this;
        }

        public Builder pageSize(float width, float height) {
            if (width <= 0.0f || height <= 0.0f) {
                throw new ValidationException("page size must be > 0");
            }
            this.pageWidth = width;
            this.pageHeight = height;
            return this;
        }

        public Builder pageMargins(float top, float right, float bottom, float left) {
            if (top < 0.0f || right < 0.0f || bottom < 0.0f || left < 0.0f) {
                throw new ValidationException("page margins must be >= 0");
            }
            this.marginTop = top;
            this.marginRight = right;
            this.marginBottom = bottom;
            this.marginLeft = left;
            return this;
        }

        public Builder pageMargin(float value) {
            return pageMargins(value, value, value, value);
        }

        public Builder paragraph(String text) {
            return paragraph(text, BoxModel.none(), 1.2f);
        }

        public Builder paragraph(String text, float lineHeightMultiplier) {
            return paragraph(text, BoxModel.none(), lineHeightMultiplier);
        }

        public Builder paragraph(String text, BoxModel boxModel) {
            return paragraph(text, boxModel, 1.2f);
        }

        public Builder paragraph(String text, BoxModel boxModel, float lineHeightMultiplier) {
            elements.add(new Paragraph(text, boxModel, validateLineHeight(lineHeightMultiplier)));
            return this;
        }

        public Builder heading(int level, String text) {
            return heading(level, text, BoxModel.none(), 1.2f);
        }

        public Builder heading(int level, String text, float lineHeightMultiplier) {
            return heading(level, text, BoxModel.none(), lineHeightMultiplier);
        }

        public Builder heading(int level, String text, BoxModel boxModel) {
            return heading(level, text, boxModel, 1.2f);
        }

        public Builder heading(int level, String text, BoxModel boxModel, float lineHeightMultiplier) {
            if (level < 1 || level > 6) {
                throw new ValidationException("heading level must be between 1 and 6");
            }
            if (lastHeadingLevel > 0 && level > lastHeadingLevel + 1) {
                throw new ValidationException("Heading hierarchy skip detected: H" + lastHeadingLevel + " -> H" + level);
            }
            lastHeadingLevel = level;
            elements.add(new Heading(level, text, boxModel, validateLineHeight(lineHeightMultiplier)));
            return this;
        }

        public Builder image(String pathOrId, String altText, boolean decorative) {
            if (!decorative && (altText == null || altText.isBlank())) {
                throw new ValidationException("Image requires altText unless decorative=true");
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

        public LayoutBlueprint layoutBlueprint() {
            return analyzeLayout();
        }

        public IntermediateDocument toIntermediateModel() {
            return DocumentModelConverter.fromFluent(toFluentSnapshot());
        }

        private FluentDocumentSnapshot toFluentSnapshot() {
            List<FluentNode> nodes = new ArrayList<>();
            for (Element element : elements) {
                if (element instanceof Heading heading) {
                    nodes.add(new FluentHeadingNode(
                            heading.level,
                            heading.text,
                            new IntermediateTextStyle(
                                    heading.lineHeightMultiplier,
                                    new IntermediateBoxModel(
                                            heading.boxModel.marginTop(),
                                            heading.boxModel.paddingTop(),
                                            heading.boxModel.paddingRight(),
                                            heading.boxModel.paddingBottom(),
                                            heading.boxModel.paddingLeft(),
                                            heading.boxModel.marginBottom()))));
                } else if (element instanceof Paragraph paragraph) {
                    nodes.add(new FluentParagraphNode(
                            paragraph.text,
                            new IntermediateTextStyle(
                                    paragraph.lineHeightMultiplier,
                                    new IntermediateBoxModel(
                                            paragraph.boxModel.marginTop(),
                                            paragraph.boxModel.paddingTop(),
                                            paragraph.boxModel.paddingRight(),
                                            paragraph.boxModel.paddingBottom(),
                                            paragraph.boxModel.paddingLeft(),
                                            paragraph.boxModel.marginBottom()))));
                } else if (element instanceof Figure figure) {
                    nodes.add(new FluentFigureNode(figure.pathOrId, figure.altText, figure.decorative));
                } else if (element instanceof ListBlock listBlock) {
                    nodes.add(new FluentListNode(List.copyOf(listBlock.items)));
                }
            }

            return new FluentDocumentSnapshot(
                    lang,
                    title,
                    displayDocTitle,
                    new com.likide.a11y.pdf.model.IntermediatePageSettings(
                            columns,
                            columnGap,
                            pageWidth,
                            pageHeight,
                            marginTop,
                            marginRight,
                            marginBottom,
                            marginLeft),
                    List.copyOf(nodes));
        }

        public byte[] buildBytes() {
            try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                PDPage page = new PDPage(new PDRectangle(pageWidth, pageHeight));
                page.getCOSObject().setItem(COSName.getPDFName("Tabs"), COSName.S);
                doc.addPage(page);

                setupCatalogMetadata(doc);
                buildStructureTree(doc);
                maybeWriteArtifactMarker(doc, page);

                doc.save(out);
                return out.toByteArray();
            } catch (IOException e) {
                throw new RenderingException("Failed to build PDF bytes", e);
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

        private LayoutBlueprint analyzeLayout() {
            PageSettings settings = new PageSettings(
                    pageWidth,
                    pageHeight,
                    marginTop,
                    marginRight,
                    marginBottom,
                    marginLeft);

            float columnWidth = settings.columnWidth(columns, columnGap);
            float usableHeight = settings.usableHeight();
            if (columnWidth <= 0.0f) {
                throw new ValidationException("Page width and margins leave no room for content");
            }
            if (usableHeight <= 0.0f) {
                throw new ValidationException("Page height and margins leave no room for content");
            }

            List<LayoutBlock> blocks = new ArrayList<>();
            int pageIndex = 0;
            int columnIndex = 0;
            float currentY = settings.topMargin();

            for (int elementIndex = 0; elementIndex < elements.size(); elementIndex++) {
                Element element = elements.get(elementIndex);
                if (!(element instanceof Heading) && !(element instanceof Paragraph)) {
                    continue;
                }

                MeasuredBlock measuredBlock = measureBlock(element, columnWidth);
                float requiredHeight = measuredBlock.height();
                if (measuredBlock.keepWithNext() && elementIndex + 1 < elements.size()) {
                    Element nextElement = elements.get(elementIndex + 1);
                    if (nextElement instanceof Paragraph) {
                        MeasuredBlock nextMeasured = measureBlock(nextElement, columnWidth);
                        requiredHeight += Math.min(nextMeasured.height(), nextMeasured.lineHeight());
                    }
                }

                if (currentY + requiredHeight > settings.topMargin() + usableHeight) {
                    columnIndex++;
                    if (columnIndex >= columns) {
                        columnIndex = 0;
                        pageIndex++;
                    }
                    currentY = settings.topMargin();
                }

                float x = settings.columnX(columnIndex, columns, columnGap);
                float contentX = x + measuredBlock.boxModel().paddingLeft();
                float contentY = currentY + measuredBlock.boxModel().marginTop() + measuredBlock.boxModel().paddingTop();
                blocks.add(new LayoutBlock(
                        measuredBlock.role(),
                        pageIndex,
                        columnIndex,
                        x,
                        currentY,
                        columnWidth,
                        measuredBlock.height(),
                        contentX,
                        contentY,
                        measuredBlock.contentWidth(),
                        measuredBlock.contentHeight(),
                        measuredBlock.boxModel(),
                        measuredBlock.lineHeight(),
                        measuredBlock.lineHeightMultiplier(),
                        measuredBlock.fontSize(),
                        measuredBlock.keepWithNext(),
                        measuredBlock.lines()));
                currentY += measuredBlock.height();
            }

            int pageCount = blocks.isEmpty() ? 1 : blocks.get(blocks.size() - 1).pageIndex() + 1;
            return new LayoutBlueprint(List.copyOf(blocks), pageCount, columnWidth, columns, columnGap, settings);
        }

        private MeasuredBlock measureBlock(Element element, float availableWidth) {
            if (element instanceof Heading heading) {
                float fontSize = 22.0f - ((heading.level - 1) * 2.0f);
                float lineHeight = fontSize * heading.lineHeightMultiplier;
                float contentWidth = resolveContentWidth(availableWidth, heading.boxModel);
                List<String> lines = wrapText(heading.text, contentWidth, fontSize * 0.55f);
                float textHeight = lines.size() * lineHeight;
                return new MeasuredBlock(
                        mapHeadingType(heading.level),
                        List.copyOf(lines),
                        lineHeight,
                        heading.lineHeightMultiplier,
                        fontSize,
                        heading.boxModel,
                        textHeight + heading.boxModel.marginTop() + heading.boxModel.verticalPadding() + heading.boxModel.marginBottom(),
                        true,
                        contentWidth,
                        textHeight);
            }

            Paragraph paragraph = (Paragraph) element;
            float fontSize = 12.0f;
            float lineHeight = fontSize * paragraph.lineHeightMultiplier;
            float contentWidth = resolveContentWidth(availableWidth, paragraph.boxModel);
            List<String> lines = wrapText(paragraph.text, contentWidth, fontSize * 0.5f);
            float textHeight = lines.size() * lineHeight;
            return new MeasuredBlock(
                    StandardStructureTypes.P,
                    List.copyOf(lines),
                    lineHeight,
                    paragraph.lineHeightMultiplier,
                    fontSize,
                    paragraph.boxModel,
                    textHeight + paragraph.boxModel.marginTop() + paragraph.boxModel.verticalPadding() + paragraph.boxModel.marginBottom(),
                    false,
                    contentWidth,
                    textHeight);
        }

        private float validateLineHeight(float lineHeightMultiplier) {
            if (lineHeightMultiplier <= 0.0f) {
                throw new ValidationException("lineHeight multiplier must be > 0");
            }
            return lineHeightMultiplier;
        }

        private float resolveContentWidth(float availableWidth, BoxModel boxModel) {
            float contentWidth = availableWidth - boxModel.horizontalPadding();
            if (contentWidth <= 0.0f) {
                throw new ValidationException("Element box model leaves no room for content");
            }
            return contentWidth;
        }

        private List<String> wrapText(String text, float availableWidth, float averageCharWidth) {
            if (text == null || text.isBlank()) {
                return Collections.singletonList("");
            }

            int maxCharsPerLine = Math.max(1, (int) Math.floor(availableWidth / averageCharWidth));
            String[] words = text.trim().split("\\s+");
            List<String> lines = new ArrayList<>();
            StringBuilder currentLine = new StringBuilder();

            for (String word : words) {
                if (word.length() > maxCharsPerLine) {
                    if (!currentLine.isEmpty()) {
                        lines.add(currentLine.toString());
                        currentLine.setLength(0);
                    }
                    lines.addAll(splitLongWord(word, maxCharsPerLine));
                    continue;
                }

                if (currentLine.isEmpty()) {
                    currentLine.append(word);
                    continue;
                }

                if (currentLine.length() + 1 + word.length() <= maxCharsPerLine) {
                    currentLine.append(' ').append(word);
                    continue;
                }

                lines.add(currentLine.toString());
                currentLine.setLength(0);
                currentLine.append(word);
            }

            if (!currentLine.isEmpty()) {
                lines.add(currentLine.toString());
            }

            return lines.isEmpty() ? Collections.singletonList("") : lines;
        }

        private List<String> splitLongWord(String word, int maxCharsPerLine) {
            List<String> parts = new ArrayList<>();
            int start = 0;
            while (start < word.length()) {
                int end = Math.min(word.length(), start + maxCharsPerLine);
                parts.add(word.substring(start, end));
                start = end;
            }
            return parts;
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
                default -> throw new ValidationException("Unsupported heading level: " + level);
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
        private final String text;
        private final BoxModel boxModel;
        private final float lineHeightMultiplier;

        private Heading(int level, String text, BoxModel boxModel, float lineHeightMultiplier) {
            this.level = level;
            this.text = text;
            this.boxModel = boxModel;
            this.lineHeightMultiplier = lineHeightMultiplier;
        }
    }

    private static final class Paragraph implements Element {
        private final String text;
        private final BoxModel boxModel;
        private final float lineHeightMultiplier;

        private Paragraph(String text, BoxModel boxModel, float lineHeightMultiplier) {
            this.text = text;
            this.boxModel = boxModel;
            this.lineHeightMultiplier = lineHeightMultiplier;
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

    private record MeasuredBlock(
            String role,
            List<String> lines,
            float lineHeight,
            float lineHeightMultiplier,
            float fontSize,
            BoxModel boxModel,
            float height,
            boolean keepWithNext,
            float contentWidth,
            float contentHeight) {
    }

    public record LayoutBlueprint(
            List<LayoutBlock> blocks,
            int pageCount,
            float columnWidth,
            int columnCount,
            float columnGap,
            PageSettings pageSettings) {
    }

    public record LayoutBlock(
            String role,
            int pageIndex,
            int columnIndex,
            float x,
            float y,
            float width,
            float height,
            float contentX,
            float contentY,
            float contentWidth,
            float contentHeight,
            BoxModel boxModel,
            float lineHeight,
            float lineHeightMultiplier,
            float fontSize,
            boolean keepWithNext,
            List<String> lines) {
    }

    public record BoxModel(
            float marginTop,
            float paddingTop,
            float paddingRight,
            float paddingBottom,
            float paddingLeft,
            float marginBottom) {

        public BoxModel {
            if (marginTop < 0.0f || paddingTop < 0.0f || paddingRight < 0.0f || paddingBottom < 0.0f || paddingLeft < 0.0f || marginBottom < 0.0f) {
                throw new ValidationException("Box model values must be >= 0");
            }
        }

        public static BoxModel none() {
            return new BoxModel(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
        }

        float horizontalPadding() {
            return paddingLeft + paddingRight;
        }

        float verticalPadding() {
            return paddingTop + paddingBottom;
        }
    }

    public record PageSettings(
            float pageWidth,
            float pageHeight,
            float topMargin,
            float rightMargin,
            float bottomMargin,
            float leftMargin) {

        float usableWidth() {
            return pageWidth - leftMargin - rightMargin;
        }

        float usableHeight() {
            return pageHeight - topMargin - bottomMargin;
        }

        float columnWidth(int columnCount, float columnGap) {
            return (usableWidth() - ((columnCount - 1) * columnGap)) / columnCount;
        }

        float columnX(int columnIndex, int columnCount, float columnGap) {
            return leftMargin + (columnIndex * columnWidth(columnCount, columnGap)) + (columnIndex * columnGap);
        }
    }
}
