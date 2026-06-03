package com.likide.a11y.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot;
import org.apache.pdfbox.pdmodel.documentinterchange.taggedpdf.StandardStructureTypes;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.viewerpreferences.PDViewerPreferences;

import com.likide.a11y.pdf.model.DeclarativeDocument;
import com.likide.a11y.pdf.model.DocumentModelConverter;
import com.likide.a11y.pdf.model.FluentCustomNode;
import com.likide.a11y.pdf.model.FluentDocumentSnapshot;
import com.likide.a11y.pdf.model.FluentFigureNode;
import com.likide.a11y.pdf.model.FluentHeadingNode;
import com.likide.a11y.pdf.model.FluentListNode;
import com.likide.a11y.pdf.model.FluentNode;
import com.likide.a11y.pdf.model.FluentParagraphNode;
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
                ListBuilder listBuilder = builder.unorderedList(fromIntermediateBoxModel(list.boxModel()));
                for (String item : list.items()) {
                    listBuilder.item(item);
                }
                listBuilder.endList();
            } else if (node instanceof IntermediateTable table) {
                TableBuilder tableBuilder = builder.table(fromIntermediateBoxModel(table.boxModel()));
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
            return unorderedList(BoxModel.none());
        }

        public ListBuilder unorderedList(BoxModel boxModel) {
            ListBlock block = new ListBlock(boxModel);
            elements.add(block);
            return new ListBuilder(this, block);
        }

        public TableBuilder table() {
            return table(BoxModel.none());
        }

        public TableBuilder table(BoxModel boxModel) {
            TableBlock block = new TableBlock(boxModel);
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
                setupCatalogMetadata(doc);
                renderToDocument(doc);
                doc.save(out);
                return out.toByteArray();
            } catch (IOException e) {
                throw new RenderingException("Failed to build PDF bytes", e);
            }
        }

        private void renderToDocument(PDDocument doc) throws IOException {
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            float contentWidth = pageWidth - marginLeft - marginRight;

            PDPage page = addStructuredPage(doc);
            float y = pageHeight - marginTop;

            for (Element element : elements) {
                if (element instanceof ListBlock listBlock) {
                    RenderCursor cursor = renderListAcrossPages(
                            doc,
                            page,
                            y,
                            marginLeft,
                            listBlock,
                            regular);
                    page = cursor.page();
                    y = cursor.y();
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
                            bold,
                            regular);
                    page = cursor.page();
                    y = cursor.y();
                    continue;
                }

                float needed = estimateHeight(element, contentWidth);
                if (y - needed < marginBottom) {
                    page = addStructuredPage(doc);
                    y = pageHeight - marginTop;
                }
                y = renderElement(doc, page, bold, regular, element, marginLeft, y, contentWidth);
            }

            buildStructureTree(doc);
            maybeWriteArtifactMarker(doc, doc.getPage(0));
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
                return wrapText(heading.text, contentWidth, fontSize * 0.55f).size() * leading
                        + heading.boxModel.marginTop() + heading.boxModel.marginBottom() + 8.0f;
            }
            if (element instanceof Paragraph paragraph) {
                float fontSize = 12.0f;
                float leading = fontSize * paragraph.lineHeightMultiplier;
                return wrapText(paragraph.text, contentWidth, fontSize * 0.5f).size() * leading
                        + paragraph.boxModel.marginTop() + paragraph.boxModel.marginBottom();
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

        private float renderElement(PDDocument doc, PDPage page, PDType1Font bold, PDType1Font regular,
                Element element, float x, float y, float contentWidth) throws IOException {
            try (PDPageContentStream cs = new PDPageContentStream(
                    doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {

                if (element instanceof Heading heading) {
                    float fontSize = 22.0f - (heading.level - 1) * 2.0f;
                    float leading = fontSize * heading.lineHeightMultiplier;
                    y -= heading.boxModel.marginTop() + 6.0f;
                    for (String line : wrapText(heading.text, contentWidth, fontSize * 0.55f)) {
                        cs.beginText();
                        cs.setFont(bold, fontSize);
                        cs.newLineAtOffset(x, y);
                        cs.showText(line);
                        cs.endText();
                        y -= leading;
                    }
                    y -= heading.boxModel.marginBottom();

                } else if (element instanceof Paragraph paragraph) {
                    float fontSize = 12.0f;
                    float leading = fontSize * paragraph.lineHeightMultiplier;
                    y -= paragraph.boxModel.marginTop();
                    for (String line : wrapText(paragraph.text, contentWidth, fontSize * 0.5f)) {
                        cs.beginText();
                        cs.setFont(regular, fontSize);
                        cs.newLineAtOffset(x, y);
                        cs.showText(line);
                        cs.endText();
                        y -= leading;
                    }
                    y -= paragraph.boxModel.marginBottom();

                } else if (element instanceof ListBlock listBlock) {
                    float leading = 14.4f;
                    for (String item : listBlock.items) {
                        cs.beginText();
                        cs.setFont(regular, 12.0f);
                        cs.newLineAtOffset(x + 12.0f, y);
                        cs.showText("- " + item);
                        cs.endText();
                        y -= leading;
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
                        cs.beginText();
                        cs.setFont(bold, headerFontSize);
                        cs.newLineAtOffset(x + i * colWidth + 4.0f, tableTop - headerBaselineOffset);
                        cs.showText(tableBlock.headerCells.get(i));
                        cs.endText();
                    }
                    float bodyBaselineOffset = (rowHeight - bodyFontSize) / 2.0f + (bodyFontSize * 0.8f);
                    int rowIndex = 1;
                    for (List<String> row : tableBlock.rows) {
                        float rowBaseline = tableTop - (rowIndex * rowHeight) - bodyBaselineOffset;
                        for (int i = 0; i < row.size(); i++) {
                            cs.beginText();
                            cs.setFont(regular, bodyFontSize);
                            cs.newLineAtOffset(x + i * colWidth + 4.0f, rowBaseline);
                            cs.showText(row.get(i));
                            cs.endText();
                        }
                        rowIndex++;
                    }
                    y = tableBottom - 8.0f;

                } else if (element instanceof TocBlock tocBlock) {
                    String label = tocBlock.title == null || tocBlock.title.isBlank()
                            ? "Table of Contents" : tocBlock.title;
                    cs.beginText();
                    cs.setFont(bold, 12.0f);
                    cs.newLineAtOffset(x, y);
                    cs.showText(label);
                    cs.endText();
                    y -= 20.0f;

                } else if (element instanceof Figure figure) {
                    String label = figure.decorative ? "[Figure - decorative]"
                            : "[Figure: " + (figure.altText != null && !figure.altText.isBlank()
                                    ? figure.altText : figure.pathOrId) + "]";
                    cs.beginText();
                    cs.setFont(regular, 11.0f);
                    cs.newLineAtOffset(x, y);
                    cs.showText(label);
                    cs.endText();
                    y -= 20.0f;

                } else if (element instanceof CustomBlock customBlock) {
                    cs.beginText();
                    cs.setFont(regular, 11.0f);
                    cs.newLineAtOffset(x, y);
                    cs.showText("[" + customBlock.family + " / " + customBlock.type + "]");
                    cs.endText();
                    y -= 20.0f;
                }
            }
            return y;
        }

        private RenderCursor renderListAcrossPages(
                PDDocument doc,
                PDPage startPage,
                float startY,
                float x,
                ListBlock listBlock,
                PDType1Font regular) throws IOException {
            float fontSize = 12.0f;
            float leading = 14.4f;
            BoxModel boxModel = listBlock.boxModel;
            float contentX = x + boxModel.paddingLeft();

            if (listBlock.items.isEmpty()) {
                return new RenderCursor(
                        startPage,
                        startY - boxModel.marginTop() - boxModel.paddingTop() - boxModel.paddingBottom() - boxModel.marginBottom() - 8.0f);
            }

            PDPage page = startPage;
            float y = startY - boxModel.marginTop() - boxModel.paddingTop();
            int itemStart = 0;

            while (true) {
                int maxItemsThisPage = (int) Math.floor((y - marginBottom) / leading);
                if (maxItemsThisPage <= 0) {
                    page = addStructuredPage(doc);
                    y = pageHeight - marginTop;
                    continue;
                }

                int remaining = listBlock.items.size() - itemStart;
                int itemsThisPage = Math.min(maxItemsThisPage, remaining);
                float lineY = y;

                try (PDPageContentStream cs = new PDPageContentStream(
                        doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    for (int i = 0; i < itemsThisPage; i++) {
                        cs.beginText();
                        cs.setFont(regular, fontSize);
                        cs.newLineAtOffset(contentX + 12.0f, lineY);
                        cs.showText("- " + listBlock.items.get(itemStart + i));
                        cs.endText();
                        lineY -= leading;
                    }
                }

                itemStart += itemsThisPage;
                if (itemStart >= listBlock.items.size()) {
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
                PDType1Font bold,
                PDType1Font regular) throws IOException {
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
                        cs.beginText();
                        cs.setFont(bold, headerFontSize);
                        cs.newLineAtOffset(tableX + i * colWidth + 4.0f, tableTop - headerBaselineOffset);
                        cs.showText(tableBlock.headerCells.get(i));
                        cs.endText();
                    }

                    float bodyBaselineOffset = (rowHeight - bodyFontSize) / 2.0f + (bodyFontSize * 0.8f);
                    for (int r = 0; r < rowsThisPage; r++) {
                        List<String> row = tableBlock.rows.get(rowStart + r);
                        float rowBaseline = tableTop - ((r + 1) * rowHeight) - bodyBaselineOffset;
                        int cells = Math.min(row.size(), colCount);
                        for (int c = 0; c < cells; c++) {
                            cs.beginText();
                            cs.setFont(regular, bodyFontSize);
                            cs.newLineAtOffset(tableX + c * colWidth + 4.0f, rowBaseline);
                            cs.showText(row.get(c));
                            cs.endText();
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

    private sealed interface Element permits Heading, Paragraph, Figure, ListBlock, TableBlock, TocBlock, CustomBlock {
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
        private final BoxModel boxModel;
        private final List<String> items = new ArrayList<>();

        private ListBlock(BoxModel boxModel) {
            this.boxModel = boxModel;
        }
    }

    private static final class TableBlock implements Element {
        private final BoxModel boxModel;
        private final List<String> headerCells = new ArrayList<>();
        private final List<List<String>> rows = new ArrayList<>();

        private TableBlock(BoxModel boxModel) {
            this.boxModel = boxModel;
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

    private record RenderCursor(PDPage page, float y) {
    }
}
