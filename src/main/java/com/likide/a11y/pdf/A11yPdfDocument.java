package com.likide.a11y.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDMarkedContentReference;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot;
import org.apache.pdfbox.pdmodel.documentinterchange.markedcontent.PDPropertyList;
import org.apache.pdfbox.pdmodel.documentinterchange.taggedpdf.StandardStructureTypes;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.interactive.viewerpreferences.PDViewerPreferences;

import com.likide.a11y.pdf.fonts.A11yFontFamily;
import com.likide.a11y.pdf.fonts.FontResolutionException;
import com.likide.a11y.pdf.fonts.FontRuntime;
import com.likide.a11y.pdf.fonts.FontVariant;
import com.likide.a11y.pdf.model.DeclarativeDocument;
import com.likide.a11y.pdf.model.DocumentModelConverter;
import com.likide.a11y.pdf.model.FluentCustomNode;
import com.likide.a11y.pdf.model.FluentDocumentSnapshot;
import com.likide.a11y.pdf.model.FluentFigureNode;
import com.likide.a11y.pdf.model.FluentHeadingNode;
import com.likide.a11y.pdf.model.FluentListNode;
import com.likide.a11y.pdf.model.FluentNode;
import com.likide.a11y.pdf.model.FluentParagraphNode;
import com.likide.a11y.pdf.model.FluentSectionNode;
import com.likide.a11y.pdf.model.FluentTableNode;
import com.likide.a11y.pdf.model.FluentTocNode;
import com.likide.a11y.pdf.model.IntermediateBoxModel;
import com.likide.a11y.pdf.model.IntermediateCustomNode;
import com.likide.a11y.pdf.model.IntermediateDocument;
import com.likide.a11y.pdf.model.IntermediateFigure;
import com.likide.a11y.pdf.model.IntermediateHeading;
import com.likide.a11y.pdf.model.IntermediateList;
import com.likide.a11y.pdf.model.IntermediateNode;
import com.likide.a11y.pdf.model.IntermediateParagraph;
import com.likide.a11y.pdf.model.IntermediateSection;
import com.likide.a11y.pdf.model.IntermediateTable;
import com.likide.a11y.pdf.model.IntermediateTableRow;
import com.likide.a11y.pdf.model.IntermediateTextStyle;
import com.likide.a11y.pdf.model.IntermediateToc;
import com.likide.a11y.pdf.rendering.RenderingException;
import com.likide.a11y.pdf.validation.ValidationException;

/**
 * Minimal fluent API skeleton aligned with the planned adapter names.
 *
 * This is intentionally small but executable so PRD tests can run end-to-end.
 */
public final class A11yPdfDocument {

    private static final float DEFAULT_PAGE_MARGIN = 72.0f;
    private static final float DEFAULT_CUSTOM_LIST_INDENT_PT = 12.0f;

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
                        heading.style().lineHeightMultiplier(),
                        fromIntermediateStyle(heading.style(), FontVariant.BOLD));
            } else if (node instanceof IntermediateParagraph paragraph) {
                builder.paragraph(
                        paragraph.text(),
                        fromIntermediateBoxModel(paragraph.style().boxModel()),
                        paragraph.style().lineHeightMultiplier(),
                        fromIntermediateStyle(paragraph.style(), FontVariant.REGULAR));
            } else if (node instanceof IntermediateFigure figure) {
                builder.image(figure.pathOrId(), figure.altText(), figure.decorative());
            } else if (node instanceof IntermediateList list) {
                ListBuilder listBuilder = (list.ordered() != null && list.ordered())
                    ? builder.orderedList(
                        list.start() == null ? 1 : list.start(),
                        fromIntermediateBoxModel(list.boxModel()),
                        fromIntermediateStyle(list.style(), FontVariant.REGULAR),
                        parseListIndentStyle(list.indentStyle()),
                        list.customIndentPt() == null ? DEFAULT_CUSTOM_LIST_INDENT_PT : list.customIndentPt())
                    : builder.unorderedList(
                        fromIntermediateBoxModel(list.boxModel()),
                        fromIntermediateStyle(list.style(), FontVariant.REGULAR),
                        parseListIndentStyle(list.indentStyle()),
                        list.customIndentPt() == null ? DEFAULT_CUSTOM_LIST_INDENT_PT : list.customIndentPt());
                for (String item : list.items()) {
                    listBuilder.item(item);
                }
                listBuilder.endList();
            } else if (node instanceof IntermediateTable table) {
                TableBuilder tableBuilder = builder.table(
                        fromIntermediateBoxModel(table.boxModel()),
                        fromIntermediateStyle(table.style(), FontVariant.REGULAR));
                for (String headerCell : table.headerCells()) {
                    tableBuilder.headerCell(headerCell);
                }
                for (IntermediateTableRow row : table.rows()) {
                    TableRowBuilder rowBuilder = tableBuilder.row();
                    for (String cell : row.cells()) {
                        rowBuilder.cell(cell);
                    }
                    rowBuilder.endRow();
                }
                tableBuilder.endTable();
            } else if (node instanceof IntermediateToc toc) {
                builder.tableOfContents(toc.title(), toc.maxDepth());
            } else if (node instanceof IntermediateCustomNode custom) {
                CustomNodeBuilder customNodeBuilder = builder.customNode(custom.family(), custom.type());
                for (Map.Entry<String, String> entry : custom.attributes().entrySet()) {
                    customNodeBuilder.attribute(entry.getKey(), entry.getValue());
                }
                customNodeBuilder.endCustomNode();
            } else if (node instanceof IntermediateSection section) {
                builder.sectionColumns(section.columns(), section.columnGap());
            } else {
                throw new ValidationException(
                        "fromDeclarative(...) cannot materialize intermediate node yet: "
                                + node.getClass().getSimpleName());
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

    private static TextStyle fromIntermediateStyle(IntermediateTextStyle style, FontVariant defaultVariant) {
        if (style == null) {
            return TextStyle.of(null, defaultVariant);
        }
        return TextStyle.of(style.fontFamily(), parseFontVariant(style.fontVariant(), defaultVariant));
    }

    private static FontVariant parseFontVariant(String rawValue, FontVariant fallback) {
        if (rawValue == null || rawValue.isBlank()) {
            return fallback;
        }
        String normalized = rawValue.trim().replace('-', '_').replace(' ', '_').toUpperCase();
        try {
            return FontVariant.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static ListIndentStyle parseListIndentStyle(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return ListIndentStyle.TWO_SPACE;
        }
        String normalized = rawValue.trim().replace('-', '_').replace(' ', '_').toUpperCase();
        try {
            return ListIndentStyle.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return ListIndentStyle.TWO_SPACE;
        }
    }

    public enum ListIndentStyle {
        ALIGN_WITH_BULLET,
        TWO_SPACE,
        CUSTOM
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
        private final List<String> preflightWarnings = new ArrayList<>();
        private A11yFontFamily documentFontFamily = A11yFontFamily.helvetica();
        private final Map<String, A11yFontFamily> fontFamilies = new LinkedHashMap<>();
        private final List<Path> fallbackFontFiles = new ArrayList<>();

        private final List<Element> elements = new ArrayList<>();
        private int lastHeadingLevel = 0;

        private Builder() {
            fontFamilies.put("default", documentFontFamily);
        }

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
            elements.add(new Paragraph(text, boxModel, validateLineHeight(lineHeightMultiplier), TextStyle.of(null, FontVariant.REGULAR)));
            return this;
        }

        public Builder paragraph(String text, BoxModel boxModel, float lineHeightMultiplier, TextStyle style) {
            elements.add(new Paragraph(text, boxModel, validateLineHeight(lineHeightMultiplier), normalizeStyle(style, FontVariant.REGULAR)));
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
            elements.add(new Heading(level, text, boxModel, validateLineHeight(lineHeightMultiplier), TextStyle.of(null, FontVariant.BOLD)));
            return this;
        }

        public Builder heading(int level, String text, BoxModel boxModel, float lineHeightMultiplier, TextStyle style) {
            if (level < 1 || level > 6) {
                throw new ValidationException("heading level must be between 1 and 6");
            }
            if (lastHeadingLevel > 0 && level > lastHeadingLevel + 1) {
                throw new ValidationException("Heading hierarchy skip detected: H" + lastHeadingLevel + " -> H" + level);
            }
            lastHeadingLevel = level;
            elements.add(new Heading(level, text, boxModel, validateLineHeight(lineHeightMultiplier), normalizeStyle(style, FontVariant.BOLD)));
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
            return unorderedList(BoxModel.none());
        }

        public ListBuilder unorderedList(BoxModel boxModel) {
            return list(false, 1, boxModel, TextStyle.of(null, FontVariant.REGULAR), ListIndentStyle.TWO_SPACE, DEFAULT_CUSTOM_LIST_INDENT_PT);
        }

        public ListBuilder unorderedList(BoxModel boxModel, TextStyle style) {
            return unorderedList(boxModel, style, ListIndentStyle.TWO_SPACE, DEFAULT_CUSTOM_LIST_INDENT_PT);
        }

        public ListBuilder unorderedList(BoxModel boxModel, TextStyle style, ListIndentStyle indentStyle, float customIndentPt) {
            return list(false, 1, boxModel, style, indentStyle, customIndentPt);
        }

        public ListBuilder orderedList(int start, BoxModel boxModel, TextStyle style, ListIndentStyle indentStyle, float customIndentPt) {
            return list(true, start, boxModel, style, indentStyle, customIndentPt);
        }

        private ListBuilder list(boolean ordered, int start, BoxModel boxModel, TextStyle style, ListIndentStyle indentStyle, float customIndentPt) {
            if (indentStyle == null) {
                throw new ValidationException("List indent style must not be null");
            }
            if (customIndentPt < 0.0f) {
                throw new ValidationException("List custom indent must be >= 0");
            }
            if (ordered && start < 1) {
                throw new ValidationException("Ordered list start must be >= 1");
            }
            ListBlock block = new ListBlock(
                    boxModel,
                    normalizeStyle(style, FontVariant.REGULAR),
                    indentStyle,
                    customIndentPt,
                    ordered,
                    start);
            elements.add(block);
            return new ListBuilder(this, block);
        }

        public TableBuilder table() {
            return table(BoxModel.none());
        }

        public TableBuilder table(BoxModel boxModel) {
            TableBlock block = new TableBlock(boxModel, TextStyle.of(null, FontVariant.REGULAR));
            elements.add(block);
            return new TableBuilder(this, block);
        }

        public TableBuilder table(BoxModel boxModel, TextStyle style) {
            TableBlock block = new TableBlock(boxModel, normalizeStyle(style, FontVariant.REGULAR));
            elements.add(block);
            return new TableBuilder(this, block);
        }

        public Builder tableOfContents(String title) {
            return tableOfContents(title, 6);
        }

        public Builder tableOfContents(String title, int maxDepth) {
            if (maxDepth < 1) {
                throw new ValidationException("TOC maxDepth must be >= 1");
            }
            elements.add(new TocBlock(title == null ? "" : title, maxDepth));
            return this;
        }

        public Builder sectionColumns(int count, float gapPt) {
            if (count < 1) {
                throw new ValidationException("section columns must be >= 1");
            }
            if (gapPt < 0.0f) {
                throw new ValidationException("section column gap must be >= 0");
            }
            elements.add(new SectionOverride(count, gapPt));
            return this;
        }

        public CustomNodeBuilder customNode(String family, String type) {
            if (family == null || family.isBlank()) {
                throw new ValidationException("Custom node family must not be blank");
            }
            if (type == null || type.isBlank()) {
                throw new ValidationException("Custom node type must not be blank");
            }
            CustomBlock block = new CustomBlock(family, type);
            elements.add(block);
            return new CustomNodeBuilder(this, block);
        }

        public Builder artifactHeaderFooter(String pageTextPattern) {
            this.artifactHeaderFooterPattern = pageTextPattern;
            return this;
        }

        public Builder defaultFontFamily(A11yFontFamily fontFamily) {
            if (fontFamily == null) {
                throw new FontResolutionException("Document default font family must not be null");
            }
            this.documentFontFamily = fontFamily;
            this.fontFamilies.put("default", fontFamily);
            return this;
        }

        public Builder registerFontFamily(String key, A11yFontFamily fontFamily) {
            if (key == null || key.isBlank()) {
                throw new FontResolutionException("Font family key must not be blank");
            }
            if (fontFamily == null) {
                throw new FontResolutionException("Font family must not be null");
            }
            this.fontFamilies.put(key.trim(), fontFamily);
            return this;
        }

        public Builder addFallbackFont(Path fontFile) {
            if (fontFile == null) {
                throw new FontResolutionException("Fallback font path must not be null");
            }
            this.fallbackFontFiles.add(fontFile);
            return this;
        }

        public List<String> preflightWarnings() {
            return List.copyOf(preflightWarnings);
        }

        public LayoutBlueprint layoutBlueprint() {
            return analyzeLayout();
        }

        public IntermediateDocument toIntermediateModel() {
            return DocumentModelConverter.fromFluent(toFluentSnapshot());
        }

        private TextStyle normalizeStyle(TextStyle style, FontVariant fallbackVariant) {
            if (style == null) {
                return TextStyle.of(null, fallbackVariant);
            }
            return TextStyle.of(style.fontFamilyKey(), style.variant() == null ? fallbackVariant : style.variant());
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
                                    heading.boxModel.marginBottom()),
                                heading.style.fontFamilyKey,
                                heading.style.variant.name())));
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
                                    paragraph.boxModel.marginBottom()),
                                paragraph.style.fontFamilyKey,
                                paragraph.style.variant.name())));
                } else if (element instanceof Figure figure) {
                    nodes.add(new FluentFigureNode(figure.pathOrId, figure.altText, figure.decorative));
                } else if (element instanceof ListBlock listBlock) {
                    nodes.add(new FluentListNode(List.copyOf(listBlock.items), listBlock.ordered, listBlock.start));
                } else if (element instanceof TableBlock tableBlock) {
                    List<IntermediateTableRow> rows = new ArrayList<>();
                    for (List<String> row : tableBlock.rows) {
                        rows.add(new IntermediateTableRow(List.copyOf(row)));
                    }
                    nodes.add(new FluentTableNode(List.copyOf(tableBlock.headerCells), List.copyOf(rows)));
                } else if (element instanceof TocBlock tocBlock) {
                    nodes.add(new FluentTocNode(tocBlock.title, tocBlock.maxDepth));
                } else if (element instanceof CustomBlock customBlock) {
                    nodes.add(new FluentCustomNode(customBlock.family, customBlock.type, Map.copyOf(customBlock.attributes)));
                } else if (element instanceof SectionOverride sectionOverride) {
                    nodes.add(new FluentSectionNode(sectionOverride.columns, sectionOverride.columnGap));
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
                runPreflightValidation();
                setupCatalogMetadata(doc);
                renderToDocument(doc);
                doc.save(out);
                return out.toByteArray();
            } catch (IOException e) {
                throw new RenderingException("Failed to build PDF bytes", e);
            }
        }

        private void runPreflightValidation() {
            List<ValidationIssue> fatals = new ArrayList<>();
            List<ValidationIssue> warnings = new ArrayList<>();

            validateDocumentMetadata(fatals);
            validateNodeComposition(fatals, warnings);

            preflightWarnings.clear();
            for (ValidationIssue warning : warnings) {
                preflightWarnings.add(warning.format());
            }

            if (!fatals.isEmpty()) {
                StringBuilder message = new StringBuilder("Preflight validation failed:\n");
                for (ValidationIssue issue : fatals) {
                    message.append(" - ").append(issue.format()).append('\n');
                }
                throw new ValidationException(message.toString().trim());
            }
        }

        private void validateDocumentMetadata(List<ValidationIssue> fatals) {
            if (lang == null || lang.isBlank()) {
                fatals.add(ValidationIssue.fatal("DOC_LANG_REQUIRED", null, null,
                        "Document language (lang) must not be blank"));
            }
            if (title == null || title.isBlank()) {
                fatals.add(ValidationIssue.fatal("DOC_TITLE_REQUIRED", null, null,
                        "Document title must not be blank"));
            }
        }

        private void validateNodeComposition(List<ValidationIssue> fatals, List<ValidationIssue> warnings) {
            int lastHeadingLevel = 0;
            int shallowestHeadingLevel = Integer.MAX_VALUE;
            List<TocReferenceCheck> tocChecks = new ArrayList<>();

            for (int i = 0; i < elements.size(); i++) {
                Element element = elements.get(i);
                String nodeType = element.getClass().getSimpleName();

                if (element instanceof Heading heading) {
                    if (lastHeadingLevel > 0 && heading.level > lastHeadingLevel + 1) {
                        fatals.add(ValidationIssue.fatal("HEADING_HIERARCHY_SKIP", i, nodeType,
                                "Heading hierarchy skip detected: H" + lastHeadingLevel + " -> H" + heading.level));
                    }
                    lastHeadingLevel = heading.level;
                    shallowestHeadingLevel = Math.min(shallowestHeadingLevel, heading.level);
                    validateUnicodeCoverage(i, nodeType, heading.text, fatals);
                } else if (element instanceof Paragraph paragraph) {
                    validateUnicodeCoverage(i, nodeType, paragraph.text, fatals);
                } else if (element instanceof Figure figure) {
                    if (!figure.decorative && (figure.altText == null || figure.altText.isBlank())) {
                        fatals.add(ValidationIssue.fatal("IMAGE_ALTTEXT_REQUIRED", i, nodeType,
                                "Non-decorative image must provide altText"));
                    }
                    validateUnicodeCoverage(i, nodeType, figure.altText, fatals);
                } else if (element instanceof ListBlock listBlock) {
                    if (listBlock.items.isEmpty()) {
                        fatals.add(ValidationIssue.fatal("LIST_EMPTY", i, nodeType,
                                "List must contain at least one item"));
                    }
                    for (int itemIndex = 0; itemIndex < listBlock.items.size(); itemIndex++) {
                        String item = listBlock.items.get(itemIndex);
                        if (item == null || item.isBlank()) {
                            fatals.add(ValidationIssue.fatal("LIST_ITEM_BLANK", i, nodeType,
                                    "List item at index " + itemIndex + " must not be blank"));
                        }
                        validateUnicodeCoverage(i, nodeType, item, fatals);
                    }
                } else if (element instanceof TableBlock tableBlock) {
                    if (tableBlock.headerCells.isEmpty() && tableBlock.rows.isEmpty()) {
                        fatals.add(ValidationIssue.fatal("TABLE_EMPTY", i, nodeType,
                                "Table must contain header cells or body rows"));
                    }
                    if (tableBlock.headerCells.isEmpty() && !tableBlock.rows.isEmpty()) {
                        warnings.add(ValidationIssue.warning("TABLE_HEADER_MISSING", i, nodeType,
                                "Table has body rows without header cells"));
                    }
                    int expectedColumns = resolveExpectedColumns(tableBlock);
                    if (expectedColumns <= 0) {
                        fatals.add(ValidationIssue.fatal("TABLE_NO_COLUMNS", i, nodeType,
                                "Table must resolve to at least one column"));
                    }
                    for (int rowIndex = 0; rowIndex < tableBlock.rows.size(); rowIndex++) {
                        List<String> row = tableBlock.rows.get(rowIndex);
                        if (row.size() != expectedColumns) {
                            fatals.add(ValidationIssue.fatal("TABLE_ROW_COLUMN_MISMATCH", i, nodeType,
                                    "Row " + rowIndex + " has " + row.size()
                                            + " cells but expected " + expectedColumns));
                        }
                        for (String cell : row) {
                            validateUnicodeCoverage(i, nodeType, cell, fatals);
                        }
                    }
                    for (String header : tableBlock.headerCells) {
                        validateUnicodeCoverage(i, nodeType, header, fatals);
                    }
                } else if (element instanceof TocBlock tocBlock) {
                    tocChecks.add(new TocReferenceCheck(i, Math.max(1, tocBlock.maxDepth)));
                    validateUnicodeCoverage(i, nodeType, tocBlock.title, fatals);
                } else if (element instanceof CustomBlock customBlock) {
                    validateUnicodeCoverage(i, nodeType, customBlock.family, fatals);
                    validateUnicodeCoverage(i, nodeType, customBlock.type, fatals);
                    for (Map.Entry<String, String> entry : customBlock.attributes.entrySet()) {
                        validateUnicodeCoverage(i, nodeType, entry.getKey(), fatals);
                        validateUnicodeCoverage(i, nodeType, entry.getValue(), fatals);
                    }
                }
            }

            for (TocReferenceCheck check : tocChecks) {
                if (shallowestHeadingLevel > check.maxDepth()) {
                    fatals.add(ValidationIssue.fatal("TOC_NO_REFERENCES", check.nodeIndex(), "TocBlock",
                            "TOC requires at least one heading at or above maxDepth=" + check.maxDepth()));
                }
            }
        }

        private int resolveExpectedColumns(TableBlock tableBlock) {
            if (!tableBlock.headerCells.isEmpty()) {
                return tableBlock.headerCells.size();
            }
            if (!tableBlock.rows.isEmpty()) {
                return tableBlock.rows.get(0).size();
            }
            return 0;
        }

        private void validateUnicodeCoverage(Integer nodeIndex, String nodeType, String value, List<ValidationIssue> fatals) {
            if (value == null || value.isBlank()) {
                return;
            }
            if (containsPotentiallyUnsupportedUnicode(value)) {
                if (hasPotentialUnicodeFallback()) {
                    return;
                }
                fatals.add(ValidationIssue.fatal("FONT_UNICODE_UNSUPPORTED", nodeIndex, nodeType,
                        "Text contains characters outside WinAnsi coverage; configure fallback fonts via addFallbackFont(...) or a Unicode-capable default font family"));
            }
        }

        private boolean hasPotentialUnicodeFallback() {
            if (!fallbackFontFiles.isEmpty()) {
                return true;
            }
            for (A11yFontFamily family : fontFamilies.values()) {
                if (family != null
                        && family.regular() != null
                        && !family.regular().isStandard14()) {
                    return true;
                }
            }
            return false;
        }

        private boolean containsPotentiallyUnsupportedUnicode(String value) {
            for (int i = 0; i < value.length(); i++) {
                if (value.charAt(i) > 255) {
                    return true;
                }
            }
            return false;
        }

        private void renderToDocument(PDDocument doc) throws IOException {
            Map<String, FontRuntime> fontRuntimes = loadFontRuntimes(doc);

            if (isTextOnlyFlow()) {
                renderTextOnlyFromLayoutBlueprint(doc, fontRuntimes);
                maybeWriteArtifactMarker(doc, doc.getPage(0));
                return;
            }

            float contentWidth = pageWidth - marginLeft - marginRight;

            PDPage page = addStructuredPage(doc);
            float y = pageHeight - marginTop;
            int activeColumns = columns;
            float activeColumnGap = columnGap;
            int activeColumnIndex = 0;

            for (Element element : elements) {
                if (element instanceof SectionOverride sectionOverride) {
                    activeColumns = sectionOverride.columns;
                    activeColumnGap = sectionOverride.columnGap;
                    activeColumnIndex = 0;
                    page = addStructuredPage(doc);
                    y = pageHeight - marginTop;
                    continue;
                }

                if (element instanceof ListBlock listBlock) {
                    RenderCursor cursor = renderListAcrossPages(
                            doc,
                            page,
                            y,
                            marginLeft,
                            listBlock,
                            fontRuntimes);
                    page = cursor.page();
                    y = cursor.y();
                            activeColumnIndex = 0;
                    continue;
                }

                if (element instanceof TableBlock tableBlock) {
                    RenderCursor cursor = renderTableAcrossPages(
                            doc,
                            page,
                            y,
                            marginLeft,
                            contentWidth,
                            tableBlock,
                            fontRuntimes);
                    page = cursor.page();
                    y = cursor.y();
                    activeColumnIndex = 0;
                    continue;
                }

                float activeColumnWidth = activeColumns <= 1
                        ? contentWidth
                        : resolveColumnWidth(activeColumns, activeColumnGap);

                if (element instanceof Heading heading) {
                    FlowCursor cursor = renderHeadingAcrossFlow(
                        doc,
                        page,
                        fontRuntimes,
                        heading,
                        activeColumnIndex,
                        activeColumns,
                        activeColumnGap,
                        y,
                        activeColumnWidth);
                    page = cursor.page();
                    activeColumnIndex = cursor.columnIndex();
                    y = cursor.y();
                    continue;
                }

                if (element instanceof Paragraph paragraph) {
                    FlowCursor cursor = renderParagraphAcrossFlow(
                        doc,
                        page,
                        fontRuntimes,
                        paragraph,
                        activeColumnIndex,
                        activeColumns,
                        activeColumnGap,
                        y,
                        activeColumnWidth);
                    page = cursor.page();
                    activeColumnIndex = cursor.columnIndex();
                    y = cursor.y();
                    continue;
                }

                float needed = estimateHeight(element, activeColumnWidth);
                if (y - needed < marginBottom) {
                    if (activeColumns > 1 && activeColumnIndex + 1 < activeColumns) {
                        activeColumnIndex++;
                        y = pageHeight - marginTop;
                    } else {
                        page = addStructuredPage(doc);
                        y = pageHeight - marginTop;
                        activeColumnIndex = 0;
                    }
                }

                float activeX = activeColumns <= 1
                        ? marginLeft
                        : resolveColumnX(activeColumnIndex, activeColumns, activeColumnGap);

                y = renderElement(doc, page, fontRuntimes, element, activeX, y, activeColumnWidth);
            }

            buildStructureTree(doc);
            maybeWriteArtifactMarker(doc, doc.getPage(0));
        }

        private boolean isTextOnlyFlow() {
            float usableHeight = pageHeight - marginTop - marginBottom;
            int activeColumns = columns;
            float activeColumnGap = columnGap;

            for (Element element : elements) {
                if (element instanceof SectionOverride sectionOverride) {
                    activeColumns = sectionOverride.columns;
                    activeColumnGap = sectionOverride.columnGap;
                    continue;
                }

                if (!(element instanceof Heading) && !(element instanceof Paragraph) && !(element instanceof SectionOverride)) {
                    return false;
                }

                if (element instanceof Heading || element instanceof Paragraph) {
                    float activeColumnWidth = activeColumns <= 1
                            ? pageWidth - marginLeft - marginRight
                            : resolveColumnWidth(activeColumns, activeColumnGap);
                    MeasuredBlock measured = measureBlock(element, activeColumnWidth);
                    // Oversized text blocks need line-level continuation across pages/columns.
                    if (measured.height() > usableHeight) {
                        return false;
                    }
                }
            }
            return !elements.isEmpty();
        }

        private void renderTextOnlyFromLayoutBlueprint(PDDocument doc, Map<String, FontRuntime> fontRuntimes) throws IOException {
            LayoutBlueprint blueprint = analyzeLayout();
            List<PDPage> pages = new ArrayList<>();
            for (int i = 0; i < blueprint.pageCount(); i++) {
                pages.add(addStructuredPage(doc));
            }

            PDDocumentCatalog catalog = doc.getDocumentCatalog();
            PDStructureTreeRoot structureRoot = new PDStructureTreeRoot();
            catalog.setStructureTreeRoot(structureRoot);

            int mcid = 0;

            for (LayoutBlock block : blueprint.blocks()) {
                PDPage page = pages.get(block.pageIndex());
                float lineY = pageHeight - block.contentY();

                PDStructureElement structureElement = new PDStructureElement(block.role(), structureRoot);
                structureRoot.appendKid(structureElement);

                COSDictionary markedContentProps = new COSDictionary();
                markedContentProps.setInt(COSName.MCID, mcid);

                try (PDPageContentStream cs = new PDPageContentStream(
                        doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    cs.beginMarkedContent(COSName.getPDFName(block.role()), PDPropertyList.create(markedContentProps));
                    for (String line : block.lines()) {
                        drawChunkedLine(
                                cs,
                                fontRuntimes,
                                block.style(),
                                null,
                                block.role().startsWith("H") ? FontVariant.BOLD : FontVariant.REGULAR,
                                block.fontSize(),
                                block.contentX(),
                                lineY,
                                line);
                        lineY -= block.lineHeight();
                    }
                    cs.endMarkedContent();
                }

                PDMarkedContentReference mcr = new PDMarkedContentReference();
                mcr.setPage(page);
                mcr.setMCID(mcid);
                structureElement.appendKid(mcr);
                mcid++;
            }
        }

        private PDPage addStructuredPage(PDDocument doc) {
            PDPage page = new PDPage(new PDRectangle(pageWidth, pageHeight));
            page.getCOSObject().setItem(COSName.getPDFName("Tabs"), COSName.S);
            doc.addPage(page);
            return page;
        }

        private float estimateHeight(Element element, float contentWidth) {
            if (element instanceof Heading heading) {
                float fontSize = 22.0f - (heading.level - 1) * 2.0f;
                float leading = fontSize * heading.lineHeightMultiplier;
            float resolvedContentWidth = resolveContentWidth(contentWidth, heading.boxModel);
            return wrapText(heading.text, resolvedContentWidth, fontSize * 0.55f).size() * leading
                + heading.boxModel.marginTop() + heading.boxModel.verticalPadding() + heading.boxModel.marginBottom() + 8.0f;
            }
            if (element instanceof Paragraph paragraph) {
                float fontSize = 12.0f;
                float leading = fontSize * paragraph.lineHeightMultiplier;
            float resolvedContentWidth = resolveContentWidth(contentWidth, paragraph.boxModel);
            return wrapText(paragraph.text, resolvedContentWidth, fontSize * 0.5f).size() * leading
                + paragraph.boxModel.marginTop() + paragraph.boxModel.verticalPadding() + paragraph.boxModel.marginBottom();
            }
            if (element instanceof ListBlock listBlock) {
                return listBlock.items.size() * 14.4f
                        + listBlock.boxModel.marginTop()
                        + listBlock.boxModel.paddingTop()
                        + listBlock.boxModel.paddingBottom()
                        + listBlock.boxModel.marginBottom()
                        + 8.0f;
            }
            if (element instanceof TableBlock tableBlock) {
                return (tableBlock.rows.size() + 1) * 18.0f
                        + tableBlock.boxModel.marginTop()
                        + tableBlock.boxModel.paddingTop()
                        + tableBlock.boxModel.paddingBottom()
                        + tableBlock.boxModel.marginBottom()
                        + 8.0f;
            }
            return 24.0f;
        }

        private float renderElement(PDDocument doc, PDPage page, Map<String, FontRuntime> fontRuntimes,
                Element element, float x, float y, float contentWidth) throws IOException {
            try (PDPageContentStream cs = new PDPageContentStream(
                    doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {

                if (element instanceof Heading heading) {
                    float fontSize = 22.0f - (heading.level - 1) * 2.0f;
                    float leading = fontSize * heading.lineHeightMultiplier;
                    float resolvedContentWidth = resolveContentWidth(contentWidth, heading.boxModel);
                    float textX = x + heading.boxModel.paddingLeft();
                    y -= heading.boxModel.marginTop() + heading.boxModel.paddingTop() + 6.0f;
                    for (String line : wrapText(heading.text, resolvedContentWidth, fontSize * 0.55f)) {
                        drawChunkedLine(cs, fontRuntimes, heading.style, null, FontVariant.BOLD, fontSize, textX, y, line);
                        y -= leading;
                    }
                    y -= heading.boxModel.paddingBottom() + heading.boxModel.marginBottom();

                } else if (element instanceof Paragraph paragraph) {
                    float fontSize = 12.0f;
                    float leading = fontSize * paragraph.lineHeightMultiplier;
                    float resolvedContentWidth = resolveContentWidth(contentWidth, paragraph.boxModel);
                    float textX = x + paragraph.boxModel.paddingLeft();
                    y -= paragraph.boxModel.marginTop() + paragraph.boxModel.paddingTop();
                    for (String line : wrapText(paragraph.text, resolvedContentWidth, fontSize * 0.5f)) {
                        drawChunkedLine(cs, fontRuntimes, paragraph.style, null, FontVariant.REGULAR, fontSize, textX, y, line);
                        y -= leading;
                    }
                    y -= paragraph.boxModel.paddingBottom() + paragraph.boxModel.marginBottom();

                } else if (element instanceof ListBlock listBlock) {
                    float leading = 14.4f;
                    float bulletX = x + 12.0f;
                    float availableTextWidth = contentWidth - 12.0f;
                    float averageCharWidth = 12.0f * 0.5f;
                    float firstLineWidth = Math.max(1.0f, availableTextWidth - maxListPrefixWidth(listBlock));
                    float continuationWidth = switch (listBlock.indentStyle) {
                        case ALIGN_WITH_BULLET -> availableTextWidth;
                        case TWO_SPACE -> Math.max(1.0f, availableTextWidth - (2.0f * averageCharWidth));
                        case CUSTOM -> Math.max(1.0f, availableTextWidth - listBlock.customIndentPt);
                    };
                    float wrapWidth = Math.max(1.0f, Math.min(firstLineWidth, continuationWidth));
                    for (int itemIndex = 0; itemIndex < listBlock.items.size(); itemIndex++) {
                        String item = listBlock.items.get(itemIndex);
                        List<String> lines = wrapText(item, wrapWidth, averageCharWidth);
                        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
                            String line = lines.get(lineIndex);
                            float lineX = bulletX;
                            if (lineIndex == 0) {
                                line = listItemPrefix(listBlock, itemIndex) + line;
                            } else if (listBlock.indentStyle == ListIndentStyle.TWO_SPACE) {
                                line = "  " + line;
                            } else if (listBlock.indentStyle == ListIndentStyle.CUSTOM) {
                                lineX += listBlock.customIndentPt;
                            }
                            drawChunkedLine(cs, fontRuntimes, null, listBlock.style, FontVariant.REGULAR, 12.0f, lineX, y, line);
                            y -= leading;
                        }
                    }
                    y -= 8.0f;

                } else if (element instanceof TableBlock tableBlock) {
                    float rowHeight = 18.0f;
                    float headerFontSize = 10.0f;
                    float bodyFontSize = 10.0f;
                    int colCount = tableBlock.headerCells.isEmpty()
                            ? (tableBlock.rows.isEmpty() ? 1 : tableBlock.rows.get(0).size())
                            : tableBlock.headerCells.size();
                    float colWidth = colCount > 0 ? contentWidth / colCount : contentWidth;
                    int totalRows = tableBlock.rows.size() + 1;
                    float tableTop = y + 4.0f;
                    float tableHeight = totalRows * rowHeight;
                    float tableBottom = tableTop - tableHeight;

                    // Draw outer border and grid lines.
                    cs.addRect(x, tableBottom, contentWidth, tableHeight);
                    for (int i = 1; i < colCount; i++) {
                        float lineX = x + i * colWidth;
                        cs.moveTo(lineX, tableTop);
                        cs.lineTo(lineX, tableBottom);
                    }
                    for (int i = 1; i < totalRows; i++) {
                        float lineY = tableTop - i * rowHeight;
                        cs.moveTo(x, lineY);
                        cs.lineTo(x + contentWidth, lineY);
                    }
                    cs.stroke();

                    float headerBaselineOffset = (rowHeight - headerFontSize) / 2.0f + (headerFontSize * 0.8f);
                    for (int i = 0; i < tableBlock.headerCells.size(); i++) {
                        drawChunkedLine(
                                cs,
                                fontRuntimes,
                                TextStyle.of(tableBlock.style.fontFamilyKey, FontVariant.BOLD),
                                tableBlock.style,
                                FontVariant.BOLD,
                                headerFontSize,
                                x + i * colWidth + 4.0f,
                                tableTop - headerBaselineOffset,
                                tableBlock.headerCells.get(i));
                    }
                    float bodyBaselineOffset = (rowHeight - bodyFontSize) / 2.0f + (bodyFontSize * 0.8f);
                    int rowIndex = 1;
                    for (List<String> row : tableBlock.rows) {
                        float rowBaseline = tableTop - (rowIndex * rowHeight) - bodyBaselineOffset;
                        for (int i = 0; i < row.size(); i++) {
                            drawChunkedLine(
                                    cs,
                                    fontRuntimes,
                                    null,
                                    tableBlock.style,
                                    FontVariant.REGULAR,
                                    bodyFontSize,
                                    x + i * colWidth + 4.0f,
                                    rowBaseline,
                                    row.get(i));
                        }
                        rowIndex++;
                    }
                    y = tableBottom - 8.0f;

                } else if (element instanceof TocBlock tocBlock) {
                    String label = tocBlock.title == null || tocBlock.title.isBlank()
                            ? "Table of Contents" : tocBlock.title;
                    drawChunkedLine(cs, fontRuntimes, null, null, FontVariant.BOLD, 12.0f, x, y, label);
                    y -= 20.0f;

                } else if (element instanceof Figure figure) {
                    String label = figure.decorative ? "[Figure - decorative]"
                            : "[Figure: " + (figure.altText != null && !figure.altText.isBlank()
                                    ? figure.altText : figure.pathOrId) + "]";
                        drawChunkedLine(cs, fontRuntimes, null, null, FontVariant.REGULAR, 11.0f, x, y, label);
                    y -= 20.0f;

                } else if (element instanceof CustomBlock customBlock) {
                    drawChunkedLine(cs, fontRuntimes, null, null, FontVariant.REGULAR, 11.0f, x, y,
                            "[" + customBlock.family + " / " + customBlock.type + "]");
                    y -= 20.0f;
                }
            }
            return y;
        }

        private FlowCursor renderHeadingAcrossFlow(
                PDDocument doc,
                PDPage startPage,
                Map<String, FontRuntime> fontRuntimes,
                Heading heading,
                int startColumnIndex,
                int activeColumns,
                float activeColumnGap,
                float startY,
                float activeColumnWidth) throws IOException {
            float fontSize = 22.0f - (heading.level - 1) * 2.0f;
            float leading = fontSize * heading.lineHeightMultiplier;
            float resolvedContentWidth = resolveContentWidth(activeColumnWidth, heading.boxModel);
            List<String> lines = wrapText(heading.text, resolvedContentWidth, fontSize * 0.55f);

            PDPage page = startPage;
            int columnIndex = startColumnIndex;
            float y = startY - heading.boxModel.marginTop() - heading.boxModel.paddingTop() - 6.0f;

            for (String line : lines) {
                if (y - leading < marginBottom) {
                    FlowCursor next = advanceTextFlow(doc, page, columnIndex, activeColumns);
                    page = next.page();
                    columnIndex = next.columnIndex();
                    y = next.y();
                }

                float x = activeColumns <= 1
                        ? marginLeft
                        : resolveColumnX(columnIndex, activeColumns, activeColumnGap);
                float textX = x + heading.boxModel.paddingLeft();

                try (PDPageContentStream cs = new PDPageContentStream(
                        doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    drawChunkedLine(cs, fontRuntimes, heading.style, null, FontVariant.BOLD, fontSize, textX, y, line);
                }
                y -= leading;
            }

            y -= heading.boxModel.paddingBottom() + heading.boxModel.marginBottom();
            return new FlowCursor(page, columnIndex, y);
        }

        private FlowCursor renderParagraphAcrossFlow(
                PDDocument doc,
                PDPage startPage,
                Map<String, FontRuntime> fontRuntimes,
                Paragraph paragraph,
                int startColumnIndex,
                int activeColumns,
                float activeColumnGap,
                float startY,
                float activeColumnWidth) throws IOException {
            float fontSize = 12.0f;
            float leading = fontSize * paragraph.lineHeightMultiplier;
            float resolvedContentWidth = resolveContentWidth(activeColumnWidth, paragraph.boxModel);
            List<String> lines = wrapText(paragraph.text, resolvedContentWidth, fontSize * 0.5f);

            PDPage page = startPage;
            int columnIndex = startColumnIndex;
            float y = startY - paragraph.boxModel.marginTop() - paragraph.boxModel.paddingTop();

            for (String line : lines) {
                if (y - leading < marginBottom) {
                    FlowCursor next = advanceTextFlow(doc, page, columnIndex, activeColumns);
                    page = next.page();
                    columnIndex = next.columnIndex();
                    y = next.y();
                }

                float x = activeColumns <= 1
                        ? marginLeft
                        : resolveColumnX(columnIndex, activeColumns, activeColumnGap);
                float textX = x + paragraph.boxModel.paddingLeft();

                try (PDPageContentStream cs = new PDPageContentStream(
                        doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    drawChunkedLine(cs, fontRuntimes, paragraph.style, null, FontVariant.REGULAR, fontSize, textX, y, line);
                }
                y -= leading;
            }

            y -= paragraph.boxModel.paddingBottom() + paragraph.boxModel.marginBottom();
            return new FlowCursor(page, columnIndex, y);
        }

        private FlowCursor advanceTextFlow(PDDocument doc, PDPage page, int columnIndex, int activeColumns) {
            if (activeColumns > 1 && columnIndex + 1 < activeColumns) {
                return new FlowCursor(page, columnIndex + 1, pageHeight - marginTop);
            }
            PDPage nextPage = addStructuredPage(doc);
            return new FlowCursor(nextPage, 0, pageHeight - marginTop);
        }

        private RenderCursor renderListAcrossPages(
                PDDocument doc,
                PDPage startPage,
                float startY,
                float x,
                ListBlock listBlock,
            Map<String, FontRuntime> fontRuntimes) throws IOException {
            float fontSize = 12.0f;
            float leading = 14.4f;
            BoxModel boxModel = listBlock.boxModel;
            float contentX = x + boxModel.paddingLeft();
            float bulletX = contentX + 12.0f;
            float availableTextWidth = pageWidth - bulletX - marginRight - boxModel.paddingRight();
            if (availableTextWidth <= 0.0f) {
                throw new ValidationException("List box model leaves no room for content");
            }

            float averageCharWidth = fontSize * 0.5f;
            float firstLineWidth = Math.max(1.0f, availableTextWidth - maxListPrefixWidth(listBlock));
            float continuationWidth = switch (listBlock.indentStyle) {
                case ALIGN_WITH_BULLET -> availableTextWidth;
                case TWO_SPACE -> Math.max(1.0f, availableTextWidth - (2.0f * averageCharWidth));
                case CUSTOM -> Math.max(1.0f, availableTextWidth - listBlock.customIndentPt);
            };
            float wrapWidth = Math.max(1.0f, Math.min(firstLineWidth, continuationWidth));

            if (listBlock.items.isEmpty()) {
                return new RenderCursor(
                        startPage,
                        startY - boxModel.marginTop() - boxModel.paddingTop() - boxModel.paddingBottom() - boxModel.marginBottom() - 8.0f);
            }

            List<List<String>> wrappedItems = new ArrayList<>();
            for (String item : listBlock.items) {
                wrappedItems.add(wrapText(item, wrapWidth, averageCharWidth));
            }

            PDPage page = startPage;
            float y = startY - boxModel.marginTop() - boxModel.paddingTop();
            int itemStart = 0;
            int lineIndex = 0;

            while (true) {
                int maxLinesThisPage = (int) Math.floor((y - marginBottom) / leading);
                if (maxLinesThisPage <= 0) {
                    page = addStructuredPage(doc);
                    y = pageHeight - marginTop;
                    continue;
                }
                float lineY = y;
                int linesDrawn = 0;

                try (PDPageContentStream cs = new PDPageContentStream(
                        doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    while (linesDrawn < maxLinesThisPage && itemStart < wrappedItems.size()) {
                        List<String> itemLines = wrappedItems.get(itemStart);
                        String textLine = itemLines.get(lineIndex);

                        float lineX = bulletX;
                        if (lineIndex == 0) {
                            textLine = listItemPrefix(listBlock, itemStart) + textLine;
                        } else if (listBlock.indentStyle == ListIndentStyle.TWO_SPACE) {
                            textLine = "  " + textLine;
                        } else if (listBlock.indentStyle == ListIndentStyle.CUSTOM) {
                            lineX += listBlock.customIndentPt;
                        }

                        drawChunkedLine(
                                cs,
                            fontRuntimes,
                            null,
                            listBlock.style,
                            FontVariant.REGULAR,
                                fontSize,
                                lineX,
                                lineY,
                                textLine);

                        lineY -= leading;
                        linesDrawn++;
                        lineIndex++;

                        if (lineIndex >= itemLines.size()) {
                            itemStart++;
                            lineIndex = 0;
                        }
                    }
                }

                if (itemStart >= wrappedItems.size()) {
                    return new RenderCursor(page, lineY - boxModel.paddingBottom() - boxModel.marginBottom() - 8.0f);
                }

                page = addStructuredPage(doc);
                y = pageHeight - marginTop;
            }
        }

        private RenderCursor renderTableAcrossPages(
                PDDocument doc,
                PDPage startPage,
                float startY,
                float x,
                float contentWidth,
                TableBlock tableBlock,
            Map<String, FontRuntime> fontRuntimes) throws IOException {
            float rowHeight = 18.0f;
            float headerFontSize = 10.0f;
            float bodyFontSize = 10.0f;
            BoxModel boxModel = tableBlock.boxModel;
            float tableX = x + boxModel.paddingLeft();
            float tableWidth = contentWidth - boxModel.horizontalPadding();
            if (tableWidth <= 0.0f) {
                throw new ValidationException("Table box model leaves no room for content");
            }

            int colCount = tableBlock.headerCells.isEmpty()
                    ? (tableBlock.rows.isEmpty() ? 1 : tableBlock.rows.get(0).size())
                    : tableBlock.headerCells.size();
            float colWidth = colCount > 0 ? tableWidth / colCount : tableWidth;

            PDPage page = startPage;
            float y = startY - boxModel.marginTop() - boxModel.paddingTop();
            int rowStart = 0;

            while (true) {
                int maxBodyRowsThisPage = (int) Math.floor((y + 4.0f - marginBottom) / rowHeight) - 1;
                if (maxBodyRowsThisPage < 0) {
                    maxBodyRowsThisPage = 0;
                }

                if (maxBodyRowsThisPage == 0 && !tableBlock.rows.isEmpty()) {
                    page = addStructuredPage(doc);
                    y = pageHeight - marginTop;
                    continue;
                }

                int remaining = tableBlock.rows.size() - rowStart;
                int rowsThisPage = Math.min(maxBodyRowsThisPage, remaining);
                int totalRowsThisPage = rowsThisPage + 1; // header + body slice

                float tableTop = y + 4.0f;
                float tableHeight = totalRowsThisPage * rowHeight;
                float tableBottom = tableTop - tableHeight;

                try (PDPageContentStream cs = new PDPageContentStream(
                        doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    cs.addRect(tableX, tableBottom, tableWidth, tableHeight);
                    for (int i = 1; i < colCount; i++) {
                        float lineX = tableX + i * colWidth;
                        cs.moveTo(lineX, tableTop);
                        cs.lineTo(lineX, tableBottom);
                    }
                    for (int i = 1; i < totalRowsThisPage; i++) {
                        float lineY = tableTop - i * rowHeight;
                        cs.moveTo(tableX, lineY);
                        cs.lineTo(tableX + tableWidth, lineY);
                    }
                    cs.stroke();

                    float headerBaselineOffset = (rowHeight - headerFontSize) / 2.0f + (headerFontSize * 0.8f);
                    for (int i = 0; i < tableBlock.headerCells.size() && i < colCount; i++) {
                        drawChunkedLine(
                                cs,
                            fontRuntimes,
                            TextStyle.of(tableBlock.style.fontFamilyKey, FontVariant.BOLD),
                            tableBlock.style,
                            FontVariant.BOLD,
                                headerFontSize,
                                tableX + i * colWidth + 4.0f,
                                tableTop - headerBaselineOffset,
                                tableBlock.headerCells.get(i));
                    }

                    float bodyBaselineOffset = (rowHeight - bodyFontSize) / 2.0f + (bodyFontSize * 0.8f);
                    for (int r = 0; r < rowsThisPage; r++) {
                        List<String> row = tableBlock.rows.get(rowStart + r);
                        float rowBaseline = tableTop - ((r + 1) * rowHeight) - bodyBaselineOffset;
                        int cells = Math.min(row.size(), colCount);
                        for (int c = 0; c < cells; c++) {
                            drawChunkedLine(
                                    cs,
                                    fontRuntimes,
                                    null,
                                    tableBlock.style,
                                    FontVariant.REGULAR,
                                    bodyFontSize,
                                    tableX + c * colWidth + 4.0f,
                                    rowBaseline,
                                    row.get(c));
                        }
                    }
                }

                rowStart += rowsThisPage;
                if (rowStart >= tableBlock.rows.size()) {
                    return new RenderCursor(page, tableBottom - boxModel.paddingBottom() - boxModel.marginBottom() - 8.0f);
                }

                page = addStructuredPage(doc);
                y = pageHeight - marginTop;
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

            float usableHeight = settings.usableHeight();
            float initialColumnWidth = settings.columnWidth(columns, columnGap);
            if (initialColumnWidth <= 0.0f) {
                throw new ValidationException("Page width and margins leave no room for content");
            }
            if (usableHeight <= 0.0f) {
                throw new ValidationException("Page height and margins leave no room for content");
            }

            List<LayoutBlock> blocks = new ArrayList<>();
            List<String> diagnostics = new ArrayList<>();
            int pageIndex = 0;
            int columnIndex = 0;
            float currentY = settings.topMargin();
            int activeColumns = columns;
            float activeColumnGap = columnGap;

            for (int elementIndex = 0; elementIndex < elements.size(); elementIndex++) {
                Element element = elements.get(elementIndex);
                if (element instanceof SectionOverride sectionOverride) {
                    activeColumns = sectionOverride.columns;
                    activeColumnGap = sectionOverride.columnGap;
                    pageIndex++;
                    columnIndex = 0;
                    currentY = settings.topMargin();
                    diagnostics.add("section columns=" + activeColumns + " gap=" + activeColumnGap + " -> page=" + pageIndex);
                    continue;
                }

                if (!(element instanceof Heading) && !(element instanceof Paragraph)) {
                    continue;
                }

                float activeColumnWidth = settings.columnWidth(activeColumns, activeColumnGap);
                if (activeColumnWidth <= 0.0f) {
                    throw new ValidationException("Section column settings leave no room for content");
                }

                MeasuredBlock measuredBlock = measureBlock(element, activeColumnWidth);
                float requiredHeight = measuredBlock.height();
                if (measuredBlock.keepWithNext() && elementIndex + 1 < elements.size()) {
                    Element nextElement = elements.get(elementIndex + 1);
                    if (nextElement instanceof Paragraph) {
                        MeasuredBlock nextMeasured = measureBlock(nextElement, activeColumnWidth);
                        requiredHeight += Math.min(nextMeasured.height(), nextMeasured.lineHeight());
                    }
                }

                if (currentY + requiredHeight > settings.topMargin() + usableHeight) {
                    LayoutCursor cursor = advanceLayoutCursor(
                            settings,
                            pageIndex,
                            columnIndex,
                            activeColumns,
                            diagnostics,
                            measuredBlock.role(),
                            requiredHeight);
                    pageIndex = cursor.pageIndex();
                    columnIndex = cursor.columnIndex();
                    currentY = cursor.currentY();
                }

                diagnostics.add(
                        "place " + measuredBlock.role() + " page=" + pageIndex +
                                " column=" + columnIndex + " y=" + currentY +
                                " height=" + measuredBlock.height());

                if (currentY + measuredBlock.height() > settings.topMargin() + usableHeight) {
                    LayoutCursor cursor = advanceLayoutCursor(
                            settings,
                            pageIndex,
                            columnIndex,
                            activeColumns,
                            diagnostics,
                            measuredBlock.role(),
                            measuredBlock.height());
                    pageIndex = cursor.pageIndex();
                    columnIndex = cursor.columnIndex();
                    currentY = cursor.currentY();
                    diagnostics.add(
                            "place-after-advance " + measuredBlock.role() + " page=" + pageIndex +
                                    " column=" + columnIndex + " y=" + currentY);
                }

                float x = settings.columnX(columnIndex, activeColumns, activeColumnGap);
                float contentX = x + measuredBlock.boxModel().paddingLeft();
                float contentY = currentY + measuredBlock.boxModel().marginTop() + measuredBlock.boxModel().paddingTop();
                blocks.add(new LayoutBlock(
                        measuredBlock.role(),
                        pageIndex,
                        columnIndex,
                        activeColumns,
                        activeColumnGap,
                        x,
                        currentY,
                        activeColumnWidth,
                        measuredBlock.height(),
                        contentX,
                        contentY,
                        measuredBlock.contentWidth(),
                        measuredBlock.contentHeight(),
                        measuredBlock.boxModel(),
                        measuredBlock.lineHeight(),
                        measuredBlock.lineHeightMultiplier(),
                        measuredBlock.fontSize(),
                        measuredBlock.style(),
                        measuredBlock.keepWithNext(),
                        measuredBlock.lines()));
                currentY += measuredBlock.height();
            }

            int pageCount = blocks.isEmpty() ? 1 : blocks.get(blocks.size() - 1).pageIndex() + 1;
            return new LayoutBlueprint(List.copyOf(blocks), pageCount, initialColumnWidth, columns, columnGap, settings, List.copyOf(diagnostics));
        }

        private LayoutCursor advanceLayoutCursor(
                PageSettings settings,
                int pageIndex,
                int columnIndex,
                int activeColumns,
                List<String> diagnostics,
                String role,
                float requiredHeight) {
            int nextColumn = columnIndex + 1;
            int nextPage = pageIndex;
            if (nextColumn >= activeColumns) {
                nextColumn = 0;
                nextPage++;
                diagnostics.add(
                        "advance page=" + pageIndex + "->" + nextPage +
                                " column=" + columnIndex + "->" + nextColumn +
                                " role=" + role + " requiredHeight=" + requiredHeight);
            } else {
                diagnostics.add(
                        "advance column=" + columnIndex + "->" + nextColumn +
                                " page=" + pageIndex +
                                " role=" + role + " requiredHeight=" + requiredHeight);
            }
            return new LayoutCursor(nextPage, nextColumn, settings.topMargin());
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
                    heading.style,
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
                    paragraph.style,
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

        private float resolveColumnWidth(int columnCount, float gap) {
            float usableWidth = pageWidth - marginLeft - marginRight;
            float width = (usableWidth - ((columnCount - 1) * gap)) / columnCount;
            if (width <= 0.0f) {
                throw new ValidationException("Section column settings leave no room for content");
            }
            return width;
        }

        private float resolveColumnX(int columnIndex, int columnCount, float gap) {
            float columnWidth = resolveColumnWidth(columnCount, gap);
            return marginLeft + (columnIndex * (columnWidth + gap));
        }

        private Map<String, FontRuntime> loadFontRuntimes(PDDocument doc) {
            Map<String, FontRuntime> runtimes = new LinkedHashMap<>();
            for (Map.Entry<String, A11yFontFamily> entry : fontFamilies.entrySet()) {
                runtimes.put(entry.getKey(), FontRuntime.load(doc, entry.getValue(), List.copyOf(fallbackFontFiles)));
            }
            if (!runtimes.containsKey("default")) {
                runtimes.put("default", FontRuntime.load(doc, documentFontFamily, List.copyOf(fallbackFontFiles)));
            }
            return runtimes;
        }

        private FontSelection resolveFontSelection(
                Map<String, FontRuntime> fontRuntimes,
                TextStyle nodeStyle,
                TextStyle parentStyle,
                FontVariant fallbackVariant) {
            String familyKey = resolveFontFamilyKey(nodeStyle, parentStyle);
            FontRuntime runtime = fontRuntimes.getOrDefault(familyKey, fontRuntimes.get("default"));
            FontVariant variant = resolveFontVariant(nodeStyle, parentStyle, fallbackVariant);
            return new FontSelection(runtime, variant);
        }

        private String resolveFontFamilyKey(TextStyle nodeStyle, TextStyle parentStyle) {
            if (nodeStyle != null && nodeStyle.fontFamilyKey != null && !nodeStyle.fontFamilyKey.isBlank()) {
                return nodeStyle.fontFamilyKey;
            }
            if (parentStyle != null && parentStyle.fontFamilyKey != null && !parentStyle.fontFamilyKey.isBlank()) {
                return parentStyle.fontFamilyKey;
            }
            return "default";
        }

        private FontVariant resolveFontVariant(TextStyle nodeStyle, TextStyle parentStyle, FontVariant fallbackVariant) {
            if (nodeStyle != null && nodeStyle.variant != null) {
                return nodeStyle.variant;
            }
            if (parentStyle != null && parentStyle.variant != null) {
                return parentStyle.variant;
            }
            return fallbackVariant;
        }

        private void drawChunkedLine(
                PDPageContentStream cs,
                Map<String, FontRuntime> fontRuntimes,
                TextStyle nodeStyle,
                TextStyle parentStyle,
                FontVariant fallbackVariant,
                float fontSize,
                float x,
                float y,
                String text) throws IOException {
            FontSelection selection = resolveFontSelection(fontRuntimes, nodeStyle, parentStyle, fallbackVariant);
            float cursorX = x;
            for (FontRuntime.FontChunk chunk : selection.runtime().chunkText(text, selection.variant())) {
                if (chunk.text().isEmpty()) {
                    continue;
                }
                PDFont font = chunk.font();
                cs.beginText();
                cs.setFont(font, fontSize);
                cs.newLineAtOffset(cursorX, y);
                cs.showText(chunk.text());
                cs.endText();
                cursorX += font.getStringWidth(chunk.text()) / 1000.0f * fontSize;
            }
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

        private String listItemPrefix(ListBlock listBlock, int itemIndex) {
            if (!listBlock.ordered) {
                return "- ";
            }
            return (listBlock.start + itemIndex) + ". ";
        }

        private float maxListPrefixWidth(ListBlock listBlock) {
            float averageCharWidth = 12.0f * 0.5f;
            if (!listBlock.ordered) {
                return 2.0f * averageCharWidth;
            }
            int maxNumber = listBlock.start + Math.max(0, listBlock.items.size() - 1);
            int markerChars = String.valueOf(maxNumber).length() + 2;
            return markerChars * averageCharWidth;
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
                } else if (element instanceof TableBlock) {
                    root.appendKid(new PDStructureElement(StandardStructureTypes.TABLE, root));
                } else if (element instanceof TocBlock) {
                    root.appendKid(new PDStructureElement(StandardStructureTypes.TOC, root));
                } else if (element instanceof CustomBlock) {
                    root.appendKid(new PDStructureElement("Sect", root));
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

        private record ValidationIssue(
                Severity severity,
                String code,
                Integer nodeIndex,
                String nodeType,
                String message) {

            static ValidationIssue fatal(String code, Integer nodeIndex, String nodeType, String message) {
                return new ValidationIssue(Severity.FATAL, code, nodeIndex, nodeType, message);
            }

            static ValidationIssue warning(String code, Integer nodeIndex, String nodeType, String message) {
                return new ValidationIssue(Severity.WARNING, code, nodeIndex, nodeType, message);
            }

            String format() {
                String nodeContext = nodeIndex == null
                        ? "document"
                        : "node[" + nodeIndex + "]" + (nodeType == null ? "" : "(" + nodeType + ")");
                return code + " @ " + nodeContext + ": " + message;
            }
        }

        private enum Severity {
            FATAL,
            WARNING
        }

        private record TocReferenceCheck(int nodeIndex, int maxDepth) {
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

        public ListBuilder alignWithBulletIndent() {
            block.indentStyle = ListIndentStyle.ALIGN_WITH_BULLET;
            return this;
        }

        public ListBuilder twoSpaceIndent() {
            block.indentStyle = ListIndentStyle.TWO_SPACE;
            return this;
        }

        public ListBuilder customIndent(float indentPt) {
            if (indentPt < 0.0f) {
                throw new ValidationException("List custom indent must be >= 0");
            }
            block.indentStyle = ListIndentStyle.CUSTOM;
            block.customIndentPt = indentPt;
            return this;
        }

        public Builder endList() {
            return parent;
        }
    }

    public static final class TableBuilder {
        private final Builder parent;
        private final TableBlock block;

        private TableBuilder(Builder parent, TableBlock block) {
            this.parent = parent;
            this.block = block;
        }

        public TableBuilder headerCell(String text) {
            block.headerCells.add(text == null ? "" : text);
            return this;
        }

        public TableRowBuilder row() {
            List<String> row = new ArrayList<>();
            block.rows.add(row);
            return new TableRowBuilder(this, row);
        }

        public Builder endTable() {
            return parent;
        }
    }

    public static final class TableRowBuilder {
        private final TableBuilder parent;
        private final List<String> row;

        private TableRowBuilder(TableBuilder parent, List<String> row) {
            this.parent = parent;
            this.row = row;
        }

        public TableRowBuilder cell(String text) {
            row.add(text == null ? "" : text);
            return this;
        }

        public TableBuilder endRow() {
            return parent;
        }
    }

    public static final class CustomNodeBuilder {
        private final Builder parent;
        private final CustomBlock block;

        private CustomNodeBuilder(Builder parent, CustomBlock block) {
            this.parent = parent;
            this.block = block;
        }

        public CustomNodeBuilder attribute(String key, String value) {
            if (key == null || key.isBlank()) {
                throw new ValidationException("Custom attribute key must not be blank");
            }
            block.attributes.put(key, value == null ? "" : value);
            return this;
        }

        public Builder endCustomNode() {
            return parent;
        }
    }

    private sealed interface Element permits Heading, Paragraph, Figure, ListBlock, TableBlock, TocBlock, CustomBlock, SectionOverride {
    }

    private static final class SectionOverride implements Element {
        private final int columns;
        private final float columnGap;

        private SectionOverride(int columns, float columnGap) {
            this.columns = columns;
            this.columnGap = columnGap;
        }
    }

    private static final class Heading implements Element {
        private final int level;
        private final String text;
        private final BoxModel boxModel;
        private final float lineHeightMultiplier;
        private final TextStyle style;

        private Heading(int level, String text, BoxModel boxModel, float lineHeightMultiplier, TextStyle style) {
            this.level = level;
            this.text = text;
            this.boxModel = boxModel;
            this.lineHeightMultiplier = lineHeightMultiplier;
            this.style = style;
        }
    }

    private static final class Paragraph implements Element {
        private final String text;
        private final BoxModel boxModel;
        private final float lineHeightMultiplier;
        private final TextStyle style;

        private Paragraph(String text, BoxModel boxModel, float lineHeightMultiplier, TextStyle style) {
            this.text = text;
            this.boxModel = boxModel;
            this.lineHeightMultiplier = lineHeightMultiplier;
            this.style = style;
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
        private final BoxModel boxModel;
        private final TextStyle style;
        private ListIndentStyle indentStyle;
        private float customIndentPt;
        private final boolean ordered;
        private final int start;
        private final List<String> items = new ArrayList<>();

        private ListBlock(
                BoxModel boxModel,
                TextStyle style,
                ListIndentStyle indentStyle,
                float customIndentPt,
                boolean ordered,
                int start) {
            this.boxModel = boxModel;
            this.style = style;
            this.indentStyle = indentStyle;
            this.customIndentPt = customIndentPt;
            this.ordered = ordered;
            this.start = start;
        }
    }

    private static final class TableBlock implements Element {
        private final BoxModel boxModel;
        private final TextStyle style;
        private final List<String> headerCells = new ArrayList<>();
        private final List<List<String>> rows = new ArrayList<>();

        private TableBlock(BoxModel boxModel, TextStyle style) {
            this.boxModel = boxModel;
            this.style = style;
        }
    }

    private static final class TocBlock implements Element {
        private final String title;
        private final int maxDepth;

        private TocBlock(String title, int maxDepth) {
            this.title = title;
            this.maxDepth = maxDepth;
        }
    }

    private static final class CustomBlock implements Element {
        private final String family;
        private final String type;
        private final Map<String, String> attributes = new LinkedHashMap<>();

        private CustomBlock(String family, String type) {
            this.family = family;
            this.type = type;
        }
    }

    public static final class TextStyle {
        private final String fontFamilyKey;
        private final FontVariant variant;

        private TextStyle(String fontFamilyKey, FontVariant variant) {
            this.fontFamilyKey = fontFamilyKey;
            this.variant = variant;
        }

        public static TextStyle of(String fontFamilyKey, FontVariant variant) {
            return new TextStyle(fontFamilyKey, variant);
        }

        public static TextStyle none() {
            return new TextStyle(null, null);
        }

        public String fontFamilyKey() {
            return fontFamilyKey;
        }

        public FontVariant variant() {
            return variant;
        }
    }

    private record FontSelection(FontRuntime runtime, FontVariant variant) {
    }

    private record MeasuredBlock(
            String role,
            List<String> lines,
            float lineHeight,
            float lineHeightMultiplier,
            float fontSize,
            TextStyle style,
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
            PageSettings pageSettings,
            List<String> diagnostics) {
    }

    public record LayoutBlock(
            String role,
            int pageIndex,
            int columnIndex,
            int activeColumnCount,
            float activeColumnGap,
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
                TextStyle style,
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

    private record LayoutCursor(int pageIndex, int columnIndex, float currentY) {
    }

    private record FlowCursor(PDPage page, int columnIndex, float y) {
    }

    private record RenderCursor(PDPage page, float y) {
    }
}
