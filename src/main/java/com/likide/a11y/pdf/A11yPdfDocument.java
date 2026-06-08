package com.likide.a11y.pdf;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IllegalFormatException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNull;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDMarkedContentReference;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDObjectReference;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot;
import org.apache.pdfbox.pdmodel.documentinterchange.markedcontent.PDPropertyList;
import org.apache.pdfbox.pdmodel.documentinterchange.taggedpdf.StandardStructureTypes;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageXYZDestination;
import org.apache.pdfbox.pdmodel.interactive.viewerpreferences.PDViewerPreferences;

import com.likide.a11y.pdf.fonts.A11yFontFamily;
import com.likide.a11y.pdf.fonts.FontResolutionException;
import com.likide.a11y.pdf.fonts.FontRuntime;
import com.likide.a11y.pdf.fonts.FontVariant;
import com.likide.a11y.pdf.model.DeclarativeChromeImage;
import com.likide.a11y.pdf.model.DeclarativeChromeLink;
import com.likide.a11y.pdf.model.DeclarativeChromeText;
import com.likide.a11y.pdf.model.DeclarativeDocument;
import com.likide.a11y.pdf.model.DeclarativeFontConfig;
import com.likide.a11y.pdf.model.DeclarativePageChrome;
import com.likide.a11y.pdf.model.DocumentModelConverter;
import com.likide.a11y.pdf.model.FluentCustomNode;
import com.likide.a11y.pdf.model.FluentDocumentSnapshot;
import com.likide.a11y.pdf.model.FluentFigureNode;
import com.likide.a11y.pdf.model.FluentHeadingNode;
import com.likide.a11y.pdf.model.FluentListItemNode;
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
import com.likide.a11y.pdf.model.IntermediateListItem;
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

        // Apply font family registrations from JSON
        for (Map.Entry<String, DeclarativeFontConfig> entry : document.fonts.entrySet()) {
            DeclarativeFontConfig cfg = entry.getValue();
            A11yFontFamily family = buildFontFamilyFromConfig(cfg);
            if (family != null) {
                if ("default".equals(entry.getKey())) {
                    builder.defaultFontFamily(family);
                } else {
                    builder.registerFontFamily(entry.getKey(), family);
                }
            }
        }

        applyDeclarativePageChrome(document.pageChrome, builder);

        for (IntermediateNode node : model.nodes()) {
            if (node instanceof IntermediateHeading heading) {
                builder.addHeading(
                        heading.level(),
                        heading.text(),
                        fromIntermediateBoxModel(heading.style().boxModel()),
                        heading.style().lineHeightMultiplier(),
                        fromIntermediateStyle(heading.style(), FontVariant.BOLD),
                        !"toc-title".equals(heading.semantic().roleHint()));
            } else if (node instanceof IntermediateParagraph paragraph) {
                builder.paragraph(
                        paragraph.text(),
                        fromIntermediateBoxModel(paragraph.style().boxModel()),
                        paragraph.style().lineHeightMultiplier(),
                        fromIntermediateStyle(paragraph.style(), FontVariant.REGULAR));
            } else if (node instanceof IntermediateFigure figure) {
                builder.image(
                        figure.pathOrId(),
                        figure.altText(),
                        figure.decorative(),
                        parseFigureFlowMode(figure.flowMode()));
            } else if (node instanceof IntermediateList list) {
                ListBuilder listBuilder = (list.ordered() != null && list.ordered())
                    ? builder.orderedList(
                        list.start() == null ? 1 : list.start(),
                        fromIntermediateBoxModel(list.boxModel()),
                        fromIntermediateStyle(list.style(), FontVariant.REGULAR),
                        parseListIndentStyle(list.indentStyle()),
                                list.customIndentPt() == null ? DEFAULT_CUSTOM_LIST_INDENT_PT : list.customIndentPt(),
                                parseListBulletStyle(list.bulletStyle()),
                                list.customMarker())
                    : builder.unorderedList(
                        fromIntermediateBoxModel(list.boxModel()),
                        fromIntermediateStyle(list.style(), FontVariant.REGULAR),
                        parseListIndentStyle(list.indentStyle()),
                                list.customIndentPt() == null ? DEFAULT_CUSTOM_LIST_INDENT_PT : list.customIndentPt(),
                                parseListBulletStyle(list.bulletStyle()),
                                list.customMarker());
                addIntermediateListItems(listBuilder, list.itemNodes());
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
                builder.tableOfContents(toc.title(), toc.maxDepth(), parseTocItemMode(toc.itemMode()), toc.showPageNumbers());
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

    private static void applyDeclarativePageChrome(DeclarativePageChrome pageChrome, Builder builder) {
        if (pageChrome == null) {
            return;
        }

        if (pageChrome.headerText != null) {
            applyChromeText(pageChrome.headerText, true, builder);
        }
        if (pageChrome.headerLink != null) {
            applyChromeLink(pageChrome.headerLink, true, builder);
        }
        if (pageChrome.headerImage != null) {
            applyChromeImage(pageChrome.headerImage, true, builder);
        }
        if (pageChrome.footerText != null) {
            applyChromeText(pageChrome.footerText, false, builder);
        }
        if (pageChrome.footerLink != null) {
            applyChromeLink(pageChrome.footerLink, false, builder);
        }
        if (pageChrome.footerImage != null) {
            applyChromeImage(pageChrome.footerImage, false, builder);
        }
        if (pageChrome.pageNumber != null) {
            PageNumberAlignment alignment = parsePageNumberAlignment(pageChrome.pageNumber.alignment);
            if (pageChrome.pageNumber.pattern != null && !pageChrome.pageNumber.pattern.isBlank()) {
                builder.artifactPageNumber(pageChrome.pageNumber.pattern, alignment);
            }
        }
    }

    private static void applyChromeText(DeclarativeChromeText chromeText, boolean header, Builder builder) {
        String text = chromeText.text == null ? "" : chromeText.text;
        ChromeAlignment alignment = parseChromeAlignment(chromeText.alignment);
        if (header) {
            builder.artifactHeaderText(text, alignment);
        } else {
            builder.artifactFooterText(text, alignment);
        }
    }

    private static void applyChromeLink(DeclarativeChromeLink chromeLink, boolean header, Builder builder) {
        String text = chromeLink.text == null ? "" : chromeLink.text;
        String url = chromeLink.url == null ? "" : chromeLink.url;
        ChromeAlignment alignment = parseChromeAlignment(chromeLink.alignment);
        if (header) {
            builder.artifactHeaderLink(text, url, alignment);
        } else {
            builder.artifactFooterLink(text, url, alignment);
        }
    }

    private static void applyChromeImage(DeclarativeChromeImage chromeImage, boolean header, Builder builder) {
        String pathOrId = chromeImage.pathOrId == null ? "" : chromeImage.pathOrId;
        float widthPt = chromeImage.widthPt == null ? 36.0f : chromeImage.widthPt.floatValue();
        float heightPt = chromeImage.heightPt == null ? 18.0f : chromeImage.heightPt.floatValue();
        ChromeAlignment alignment = parseChromeAlignment(chromeImage.alignment);
        if (header) {
            builder.artifactHeaderImage(pathOrId, widthPt, heightPt, alignment);
        } else {
            builder.artifactFooterImage(pathOrId, widthPt, heightPt, alignment);
        }
    }

    private static ChromeAlignment parseChromeAlignment(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return ChromeAlignment.CENTER;
        }
        try {
            return ChromeAlignment.valueOf(rawValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return ChromeAlignment.CENTER;
        }
    }

    private static PageNumberAlignment parsePageNumberAlignment(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return PageNumberAlignment.CENTER;
        }
        try {
            return PageNumberAlignment.valueOf(rawValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return PageNumberAlignment.CENTER;
        }
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
            return TextStyle.of(null, defaultVariant, TextAlignment.LEFT);
        }
        return TextStyle.of(
                style.fontFamily(),
                parseFontVariant(style.fontVariant(), defaultVariant),
                parseTextAlignment(style.textAlignment(), TextAlignment.LEFT));
    }

    private static A11yFontFamily buildFontFamilyFromConfig(DeclarativeFontConfig cfg) {
        if (cfg == null || (cfg.regular == null && cfg.bold == null)) {
            return null;
        }
        A11yFontFamily.FontSource regular = fontSourceFromPath(cfg.regular);
        if (regular == null) {
            return null;
        }
        A11yFontFamily.FontSource bold = cfg.bold != null ? fontSourceFromPath(cfg.bold) : regular;
        A11yFontFamily.FontSource italic = cfg.italic != null ? fontSourceFromPath(cfg.italic) : regular;
        A11yFontFamily.FontSource boldItalic = cfg.boldItalic != null ? fontSourceFromPath(cfg.boldItalic) : bold;
        return new A11yFontFamily(regular, bold, italic, boldItalic);
    }

    private static A11yFontFamily.FontSource fontSourceFromPath(String pathString) {
        if (pathString == null || pathString.isBlank()) {
            return null;
        }
        return A11yFontFamily.FontSource.file(Path.of(pathString));
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

    private static TextAlignment parseTextAlignment(String rawValue, TextAlignment fallback) {
        if (rawValue == null || rawValue.isBlank()) {
            return fallback;
        }
        String normalized = rawValue.trim().replace('-', '_').replace(' ', '_').toUpperCase();
        try {
            return TextAlignment.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static TocItemMode parseTocItemMode(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return TocItemMode.TEXT;
        }
        String normalized = rawValue.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
        try {
            return TocItemMode.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return TocItemMode.TEXT;
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

    private static ListBulletStyle parseListBulletStyle(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return ListBulletStyle.DASH;
        }
        String normalized = rawValue.trim().replace('-', '_').replace(' ', '_').toUpperCase();
        try {
            return ListBulletStyle.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return ListBulletStyle.DASH;
        }
    }

    private static FigureFlowMode parseFigureFlowMode(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return FigureFlowMode.INLINE;
        }
        String normalized = rawValue.trim().replace('-', '_').replace(' ', '_').toUpperCase();
        try {
            return FigureFlowMode.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return FigureFlowMode.INLINE;
        }
    }

    private static void addIntermediateListItems(ListBuilder listBuilder, List<IntermediateListItem> items) {
        for (IntermediateListItem item : items) {
            if (item == null) {
                continue;
            }
            ListBuilder itemBuilder = listBuilder.item(item.text());
            if (item.nestedList() != null) {
                IntermediateList nested = item.nestedList();
                ListBuilder nestedBuilder = (nested.ordered() != null && nested.ordered())
                        ? itemBuilder.beginNestedOrderedList(
                                nested.start() == null ? 1 : nested.start(),
                                fromIntermediateBoxModel(nested.boxModel()),
                                fromIntermediateStyle(nested.style(), FontVariant.REGULAR),
                                parseListIndentStyle(nested.indentStyle()),
                                nested.customIndentPt() == null ? DEFAULT_CUSTOM_LIST_INDENT_PT : nested.customIndentPt(),
                                parseListBulletStyle(nested.bulletStyle()),
                                nested.customMarker())
                        : itemBuilder.beginNestedUnorderedList(
                                fromIntermediateBoxModel(nested.boxModel()),
                                fromIntermediateStyle(nested.style(), FontVariant.REGULAR),
                                parseListIndentStyle(nested.indentStyle()),
                                nested.customIndentPt() == null ? DEFAULT_CUSTOM_LIST_INDENT_PT : nested.customIndentPt(),
                                parseListBulletStyle(nested.bulletStyle()),
                                nested.customMarker());
                addIntermediateListItems(nestedBuilder, nested.itemNodes());
                nestedBuilder.endList();
            }
        }
    }

    public enum ListIndentStyle {
        ALIGN_WITH_BULLET,
        TWO_SPACE,
        CUSTOM
    }

    public enum ListBulletStyle {
        DISC,
        CIRCLE,
        SQUARE,
        DASH,
        CUSTOM
    }

    public enum FigureFlowMode {
        INLINE,
        SPAN_ALL_COLUMNS
    }

    public enum ChromeAlignment {
        LEFT,
        CENTER,
        RIGHT
    }

    public enum PageNumberAlignment {
        CENTER,
        RIGHT,
        ALTERNATE
    }

    public enum TextAlignment {
        LEFT,
        RIGHT,
        CENTER,
        JUSTIFY
    }

    public enum TocItemMode {
        TEXT,
        LINK
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
        private ArtifactText headerTextArtifact;
        private ArtifactText footerTextArtifact;
        private ArtifactLink headerLinkArtifact;
        private ArtifactLink footerLinkArtifact;
        private ArtifactImage headerImageArtifact;
        private ArtifactImage footerImageArtifact;
        private String artifactFooterPattern;
        private PageNumberAlignment pageNumberAlignment = PageNumberAlignment.CENTER;
        private final List<String> preflightWarnings = new ArrayList<>();
        private int currentElementIndex = -1;
        private PDPage currentRenderPage = null;
        private final Map<PDPage, Integer> pageLocalMcidCounter = new LinkedHashMap<>();
        private final List<MarkedContentRecord> markedContentRecords = new ArrayList<>();
        private final Map<PDPage, Map<Integer, PDStructureElement>> mcidToStructElem = new LinkedHashMap<>();
        private final Map<Integer, float[]> figureBBoxes = new LinkedHashMap<>();
        private final Map<Integer, TableSlotPlan> tableSlotPlans = new LinkedHashMap<>();
        private final Map<Integer, TocSlotPlan> tocSlotPlans = new LinkedHashMap<>();
        private final List<TocLinkAnnotationPlan> tocLinkAnnotationPlans = new ArrayList<>();
        private final Map<TocLinkSlotKey, List<PDAnnotationLink>> tocLinkAnnotationsBySlot = new LinkedHashMap<>();
        private final Map<Integer, PDStructureElement> objectParentTreeEntries = new LinkedHashMap<>();
        private int nextAnnotationStructParent = 100000;
        private final List<TocPageSpan> tocPageSpans = new ArrayList<>();
        private Map<Integer, Integer> resolvedTocHeadingPages = Map.of();
        private final Map<Integer, Integer> collectedHeadingPageNumbers = new LinkedHashMap<>();
        private boolean collectHeadingPageNumbers = false;
        private boolean suppressTocLinkAnnotations = false;
        private final Map<Integer, List<ListItemSlotPlan>> listItemSlotPlans = new LinkedHashMap<>();
        private int currentItemSlot = -1;
        private int nextItemSlot = 0;
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
            if (this.headerTextArtifact == null || this.headerTextArtifact.text().isBlank()) {
                this.headerTextArtifact = new ArtifactText(value, ChromeAlignment.CENTER, 9.0f);
            }
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
            return addHeading(
                    level,
                    text,
                    boxModel,
                    lineHeightMultiplier,
                    TextStyle.of(null, FontVariant.BOLD),
                    true);
        }

        public Builder heading(int level, String text, BoxModel boxModel, float lineHeightMultiplier, TextStyle style) {
            return addHeading(
                    level,
                    text,
                    boxModel,
                    lineHeightMultiplier,
                    normalizeStyle(style, FontVariant.BOLD),
                    true);
        }

        private Builder addHeading(int level, String text, BoxModel boxModel, float lineHeightMultiplier, TextStyle style, boolean includeInToc) {
            if (level < 1 || level > 6) {
                throw new ValidationException("heading level must be between 1 and 6");
            }
            if (lastHeadingLevel > 0 && level > lastHeadingLevel + 1) {
                throw new ValidationException("Heading hierarchy skip detected: H" + lastHeadingLevel + " -> H" + level);
            }
            lastHeadingLevel = level;
            elements.add(new Heading(level, text, boxModel, validateLineHeight(lineHeightMultiplier), style, includeInToc));
            return this;
        }

        public Builder image(String pathOrId, String altText, boolean decorative) {
            return image(pathOrId, altText, decorative, FigureFlowMode.INLINE);
        }

        public Builder image(String pathOrId, String altText, boolean decorative, FigureFlowMode flowMode) {
            if (!decorative && (altText == null || altText.isBlank())) {
                throw new ValidationException("Image requires altText unless decorative=true");
            }
            elements.add(new Figure(pathOrId, altText, decorative, flowMode == null ? FigureFlowMode.INLINE : flowMode));
            return this;
        }

        public ListBuilder unorderedList() {
            return unorderedList(BoxModel.none());
        }

        public ListBuilder unorderedList(BoxModel boxModel) {
            return list(false, 1, boxModel, TextStyle.of(null, FontVariant.REGULAR), ListIndentStyle.TWO_SPACE, DEFAULT_CUSTOM_LIST_INDENT_PT, ListBulletStyle.DASH, null);
        }

        public ListBuilder unorderedList(BoxModel boxModel, TextStyle style) {
            return unorderedList(boxModel, style, ListIndentStyle.TWO_SPACE, DEFAULT_CUSTOM_LIST_INDENT_PT);
        }

        public ListBuilder unorderedList(BoxModel boxModel, TextStyle style, ListIndentStyle indentStyle, float customIndentPt) {
            return unorderedList(boxModel, style, indentStyle, customIndentPt, ListBulletStyle.DASH, null);
        }

        public ListBuilder unorderedList(
                BoxModel boxModel,
                TextStyle style,
                ListIndentStyle indentStyle,
                float customIndentPt,
                ListBulletStyle bulletStyle,
                String customMarker) {
            return list(false, 1, boxModel, style, indentStyle, customIndentPt, bulletStyle, customMarker);
        }

        public ListBuilder orderedList(int start, BoxModel boxModel, TextStyle style, ListIndentStyle indentStyle, float customIndentPt) {
            return orderedList(start, boxModel, style, indentStyle, customIndentPt, ListBulletStyle.DASH, null);
        }

        public ListBuilder orderedList(
                int start,
                BoxModel boxModel,
                TextStyle style,
                ListIndentStyle indentStyle,
                float customIndentPt,
                ListBulletStyle bulletStyle,
                String customMarker) {
            return list(true, start, boxModel, style, indentStyle, customIndentPt, bulletStyle, customMarker);
        }

        private ListBuilder list(
                boolean ordered,
                int start,
                BoxModel boxModel,
                TextStyle style,
                ListIndentStyle indentStyle,
                float customIndentPt,
                ListBulletStyle bulletStyle,
                String customMarker) {
            if (indentStyle == null) {
                throw new ValidationException("List indent style must not be null");
            }
            if (customIndentPt < 0.0f) {
                throw new ValidationException("List custom indent must be >= 0");
            }
            if (ordered && start < 1) {
                throw new ValidationException("Ordered list start must be >= 1");
            }
            if (bulletStyle == null) {
                throw new ValidationException("List bullet style must not be null");
            }
            if (bulletStyle == ListBulletStyle.CUSTOM && (customMarker == null || customMarker.isBlank())) {
                throw new ValidationException("Custom bullet style requires a non-blank custom marker");
            }
            ListBlock block = new ListBlock(
                    boxModel,
                    normalizeStyle(style, FontVariant.REGULAR),
                    indentStyle,
                    customIndentPt,
                    ordered,
                    start,
                    bulletStyle,
                    customMarker);
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
            return tableOfContents(title, maxDepth, TocItemMode.TEXT, true);
        }

        public Builder tableOfContents(String title, int maxDepth, TocItemMode itemMode, boolean showPageNumbers) {
            if (maxDepth < 1) {
                throw new ValidationException("TOC maxDepth must be >= 1");
            }
            elements.add(new TocBlock(title, maxDepth, itemMode == null ? TocItemMode.TEXT : itemMode, showPageNumbers));
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
            this.headerTextArtifact = new ArtifactText(title, ChromeAlignment.CENTER, 9.0f);
            this.artifactFooterPattern = pageTextPattern;
            return this;
        }

        public Builder artifactHeader(String headerText) {
            this.headerTextArtifact = new ArtifactText(headerText, ChromeAlignment.CENTER, 9.0f);
            return this;
        }

        public Builder artifactFooter(String pageTextPattern) {
            this.artifactFooterPattern = pageTextPattern;
            return this;
        }

        public Builder artifactHeaderText(String text, ChromeAlignment alignment) {
            this.headerTextArtifact = new ArtifactText(text, defaultAlignment(alignment), 9.0f);
            return this;
        }

        public Builder artifactFooterText(String text, ChromeAlignment alignment) {
            this.footerTextArtifact = new ArtifactText(text, defaultAlignment(alignment), 9.0f);
            return this;
        }

        public Builder artifactHeaderLink(String text, String url, ChromeAlignment alignment) {
            this.headerLinkArtifact = new ArtifactLink(text, url, defaultAlignment(alignment), 9.0f);
            return this;
        }

        public Builder artifactFooterLink(String text, String url, ChromeAlignment alignment) {
            this.footerLinkArtifact = new ArtifactLink(text, url, defaultAlignment(alignment), 9.0f);
            return this;
        }

        public Builder artifactHeaderImage(String pathOrId, float widthPt, float heightPt, ChromeAlignment alignment) {
            this.headerImageArtifact = new ArtifactImage(pathOrId, defaultAlignment(alignment), widthPt, heightPt, null);
            return this;
        }

        public Builder artifactFooterImage(String pathOrId, float widthPt, float heightPt, ChromeAlignment alignment) {
            this.footerImageArtifact = new ArtifactImage(pathOrId, defaultAlignment(alignment), widthPt, heightPt, null);
            return this;
        }

        public Builder artifactPageNumber(String pattern, PageNumberAlignment alignment) {
            this.artifactFooterPattern = pattern;
            this.pageNumberAlignment = alignment == null ? PageNumberAlignment.CENTER : alignment;
            return this;
        }

        private ChromeAlignment defaultAlignment(ChromeAlignment alignment) {
            return alignment == null ? ChromeAlignment.CENTER : alignment;
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
                return TextStyle.of(null, fallbackVariant, TextAlignment.LEFT);
            }
            return TextStyle.of(
                    style.fontFamilyKey(),
                    style.variant() == null ? fallbackVariant : style.variant(),
                    style.textAlignment() == null ? TextAlignment.LEFT : style.textAlignment());
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
                                            heading.style.variant.name(),
                                            heading.style.textAlignment.name())));
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
                                paragraph.style.variant.name(),
                                paragraph.style.textAlignment.name())));
                } else if (element instanceof Figure figure) {
                    nodes.add(new FluentFigureNode(figure.pathOrId, figure.altText, figure.decorative, figure.flowMode.name()));
                } else if (element instanceof ListBlock listBlock) {
                    nodes.add(new FluentListNode(
                            toFluentListItems(listBlock.items),
                            listBlock.ordered,
                            listBlock.start,
                            listBlock.bulletStyle.name(),
                            listBlock.customMarker));
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

        private List<FluentListItemNode> toFluentListItems(List<ListItem> items) {
            List<FluentListItemNode> nodes = new ArrayList<>();
            for (ListItem item : items) {
                FluentListNode nested = null;
                if (item.nestedList != null) {
                    nested = new FluentListNode(
                            toFluentListItems(item.nestedList.items),
                            item.nestedList.ordered,
                            item.nestedList.start,
                            item.nestedList.bulletStyle.name(),
                            item.nestedList.customMarker);
                }
                nodes.add(new FluentListItemNode(item.text, nested));
            }
            return List.copyOf(nodes);
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
                    validateListItems(i, nodeType, listBlock.items, fatals);
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

        private void validateListItems(Integer nodeIndex, String nodeType, List<ListItem> items, List<ValidationIssue> fatals) {
            for (int itemIndex = 0; itemIndex < items.size(); itemIndex++) {
                ListItem item = items.get(itemIndex);
                if (item.text == null || item.text.isBlank()) {
                    fatals.add(ValidationIssue.fatal("LIST_ITEM_BLANK", nodeIndex, nodeType,
                            "List item at index " + itemIndex + " must not be blank"));
                }
                validateUnicodeCoverage(nodeIndex, nodeType, item.text, fatals);
                if (item.nestedList != null) {
                    if (item.nestedList.items.isEmpty()) {
                        fatals.add(ValidationIssue.fatal("LIST_EMPTY", nodeIndex, nodeType,
                                "Nested list at index " + itemIndex + " must contain at least one item"));
                    } else {
                        validateListItems(nodeIndex, nodeType, item.nestedList.items, fatals);
                    }
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
            renderToDocument(doc, true);
        }

        private void renderToDocument(PDDocument doc, boolean resolveTocTargets) throws IOException {
            if (resolveTocTargets) {
                if (!isTextOnlyFlow() && containsTocBlock()) {
                    resolvedTocHeadingPages = resolveTocHeadingPages();
                } else {
                    resolvedTocHeadingPages = Map.of();
                }
            }

            markedContentRecords.clear();
            mcidToStructElem.clear();
            pageLocalMcidCounter.clear();
            figureBBoxes.clear();
            tableSlotPlans.clear();
            tocSlotPlans.clear();
            tocLinkAnnotationPlans.clear();
            tocLinkAnnotationsBySlot.clear();
            objectParentTreeEntries.clear();
            nextAnnotationStructParent = 100000;
            tocPageSpans.clear();
            collectedHeadingPageNumbers.clear();
            listItemSlotPlans.clear();
            currentElementIndex = -1;
            currentRenderPage = null;
            currentItemSlot = -1;
            nextItemSlot = 0;

            LayoutBlueprint layoutBlueprint = analyzeLayout();

            Map<String, FontRuntime> fontRuntimes = loadFontRuntimes(doc);

            if (isTextOnlyFlow()) {
                renderTextOnlyFromLayoutBlueprint(doc, fontRuntimes);
                renderArtifactPageChrome(doc, fontRuntimes);
                applyTocLinkAnnotations(doc);
                return;
            }

            float contentWidth = pageWidth - marginLeft - marginRight;

            PDPage page = addStructuredPage(doc);
            float y = pageHeight - marginTop;
            int activeColumns = columns;
            float activeColumnGap = columnGap;
            int activeColumnIndex = 0;

            for (int elemIdx = 0; elemIdx < elements.size(); elemIdx++) {
                Element element = elements.get(elemIdx);
                currentElementIndex = elemIdx;
                currentItemSlot = -1;

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
                        elemIdx,
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

                if (element instanceof TocBlock tocBlock) {
                    FlowCursor cursor = renderTocAcrossFlow(
                            doc,
                            page,
                            fontRuntimes,
                            tocBlock,
                            elemIdx,
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

                if (element instanceof Figure figure) {
                    FlowCursor cursor = renderFigureAcrossFlow(
                            doc,
                            page,
                            fontRuntimes,
                            figure,
                            activeColumnIndex,
                            activeColumns,
                            activeColumnGap,
                            y,
                            activeColumnWidth,
                            contentWidth);
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

            applyTocLinkAnnotations(doc);
            buildStructureTree(doc);
            renderArtifactPageChrome(doc, fontRuntimes);
        }

        private boolean containsTocBlock() {
            for (Element element : elements) {
                if (element instanceof TocBlock) {
                    return true;
                }
            }
            return false;
        }

        private Map<Integer, Integer> resolveTocHeadingPages() throws IOException {
            Map<Integer, Integer> guess = Map.of();
            Map<Integer, Integer> previousResolved = resolvedTocHeadingPages;
            boolean previousCollect = collectHeadingPageNumbers;
            boolean previousSuppressLinks = suppressTocLinkAnnotations;
            try {
                for (int pass = 0; pass < 3; pass++) {
                    resolvedTocHeadingPages = guess;
                    collectHeadingPageNumbers = true;
                    suppressTocLinkAnnotations = true;
                    try (PDDocument scratch = new PDDocument()) {
                        renderToDocument(scratch, false);
                    }
                    Map<Integer, Integer> computed = Map.copyOf(collectedHeadingPageNumbers);
                    if (computed.equals(guess)) {
                        return computed;
                    }
                    guess = computed;
                }
                return guess;
            } finally {
                resolvedTocHeadingPages = previousResolved;
                collectHeadingPageNumbers = previousCollect;
                suppressTocLinkAnnotations = previousSuppressLinks;
                collectedHeadingPageNumbers.clear();
            }
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
            currentRenderPage = page;
            pageLocalMcidCounter.put(page, 0);
            return page;
        }

        private int allocateMcid(PDPage page) {
            int mcid = pageLocalMcidCounter.getOrDefault(page, 0);
            pageLocalMcidCounter.put(page, mcid + 1);
            return mcid;
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
                return countRenderedListItems(listBlock) * 14.4f
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
            if (element instanceof TocBlock tocBlock) {
                float content = 14.4f;
                String title = tocBlock.title == null ? "Table of Contents" : tocBlock.title;
                if (!title.isBlank()) {
                    content += wrapText(title, Math.max(1.0f, contentWidth), 12.0f * 0.55f).size() * 14.4f;
                }
                LayoutBlueprint layoutBlueprint = analyzeLayout();
                for (TocEntry entry : buildTocEntries(layoutBlueprint, tocBlock.maxDepth)) {
                    String line = "  ".repeat(Math.max(0, entry.level() - 1)) + entry.text();
                    content += wrapText(line, Math.max(1.0f, contentWidth), 11.0f * 0.5f).size() * 13.2f;
                }
                return content + 8.0f;
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
                        drawTaggedChunkedLine(cs, mapHeadingType(heading.level), fontRuntimes, heading.style, null, FontVariant.BOLD, fontSize, textX, y, line);
                        y -= leading;
                    }
                    y -= heading.boxModel.paddingBottom() + heading.boxModel.marginBottom();

                } else if (element instanceof Paragraph paragraph) {
                    float fontSize = 12.0f;
                    float leading = fontSize * paragraph.lineHeightMultiplier;
                    float resolvedContentWidth = resolveContentWidth(contentWidth, paragraph.boxModel);
                    float textX = x + paragraph.boxModel.paddingLeft();
                    y -= paragraph.boxModel.marginTop() + paragraph.boxModel.paddingTop();
                    FontSelection selection = resolveFontSelection(fontRuntimes, paragraph.style, null, FontVariant.REGULAR);
                    TextAlignment alignment = paragraph.style.textAlignment == null ? TextAlignment.LEFT : paragraph.style.textAlignment;
                    List<String> lines = wrapText(paragraph.text, resolvedContentWidth, fontSize * 0.5f);
                    beginTaggedMarkedContent(cs, StandardStructureTypes.P);
                    try {
                        for (int i = 0; i < lines.size(); i++) {
                            String line = lines.get(i);
                            boolean justifyLine = alignment == TextAlignment.JUSTIFY && i < lines.size() - 1;
                            float alignedX = resolveParagraphLineX(textX, resolvedContentWidth, selection, fontSize, line, alignment, justifyLine);
                            float wordSpacing = resolveParagraphWordSpacing(resolvedContentWidth, selection, fontSize, line, justifyLine);
                            drawChunkedLine(cs, selection, fontSize, alignedX, y, line, wordSpacing);
                            y -= leading;
                        }
                    } finally {
                        cs.endMarkedContent();
                    }
                    y -= paragraph.boxModel.paddingBottom() + paragraph.boxModel.marginBottom();

                } else if (element instanceof ListBlock listBlock) {
                    float leading = 14.4f;
                    y = renderListWithoutPaging(cs, fontRuntimes, listBlock, x, y, leading);
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

                    // Draw outer border and grid lines wrapped as Artifact.
                    cs.beginMarkedContent(COSName.getPDFName("Artifact"));
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
                    cs.endMarkedContent();

                    float headerBaselineOffset = (rowHeight - headerFontSize) / 2.0f + (headerFontSize * 0.8f);
                    for (int i = 0; i < tableBlock.headerCells.size(); i++) {
                        drawTaggedChunkedLine(
                                cs,
                            StandardStructureTypes.TH,
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
                            drawTaggedChunkedLine(
                                    cs,
                                    StandardStructureTypes.TD,
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
                    String title = tocBlock.title == null ? "Table of Contents" : tocBlock.title;
                    if (!title.isBlank()) {
                        drawTaggedChunkedLine(cs, StandardStructureTypes.TOC, fontRuntimes, null, null, FontVariant.BOLD, 12.0f, x, y, title);
                        y -= 14.4f;
                    }
                    List<TocEntry> tocEntries = buildTocEntries(analyzeLayout(), tocBlock.maxDepth);
                    int tocPageCount = estimateTocPageCount(tocBlock, tocEntries, contentWidth, 1, 0.0f, y);
                    int currentTocElementIndex = currentElementIndex;
                    for (TocEntry entry : tocEntries) {
                        String line = "  ".repeat(Math.max(0, entry.level() - 1)) + entry.text();
                        int targetPageNumber = resolveTocTargetPageNumber(entry, tocPageCount, currentTocElementIndex);
                        String displayLine = tocBlock.showPageNumbers ? line + " " + targetPageNumber : line;
                        renderTocEntryLine(doc, page, fontRuntimes, tocBlock, displayLine, targetPageNumber, x, y, contentWidth);
                        y -= 13.2f;
                    }
                    if (currentTocElementIndex >= 0) {
                        tocPageSpans.add(new TocPageSpan(currentTocElementIndex, tocPageCount));
                    }
                    y -= 8.0f;

                } else if (element instanceof Figure figure) {
                    String label = figure.decorative ? "[Figure - decorative]"
                            : "[Figure: " + (figure.altText != null && !figure.altText.isBlank()
                                    ? figure.altText : figure.pathOrId) + "]";
                        drawTaggedChunkedLine(cs, StandardStructureTypes.Figure, fontRuntimes, null, null, FontVariant.REGULAR, 11.0f, x, y, label);
                    y -= 20.0f;

                } else if (element instanceof CustomBlock customBlock) {
                    drawTaggedChunkedLine(cs, "Sect", fontRuntimes, null, null, FontVariant.REGULAR, 11.0f, x, y,
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
            boolean capturedPage = false;

            for (String line : lines) {
                if (y - leading < marginBottom) {
                    FlowCursor next = advanceTextFlow(doc, page, columnIndex, activeColumns);
                    page = next.page();
                    columnIndex = next.columnIndex();
                    y = next.y();
                }

                if (!capturedPage && collectHeadingPageNumbers && heading.includeInToc && currentElementIndex >= 0) {
                    collectedHeadingPageNumbers.putIfAbsent(currentElementIndex, resolveRenderedPageNumber(doc, page));
                    capturedPage = true;
                }

                float x = activeColumns <= 1
                        ? marginLeft
                        : resolveColumnX(columnIndex, activeColumns, activeColumnGap);
                float textX = x + heading.boxModel.paddingLeft();

                try (PDPageContentStream cs = new PDPageContentStream(
                        doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    drawTaggedChunkedLine(cs, mapHeadingType(heading.level), fontRuntimes, heading.style, null, FontVariant.BOLD, fontSize, textX, y, line);
                }
                y -= leading;
            }

            y -= heading.boxModel.paddingBottom() + heading.boxModel.marginBottom();
            return new FlowCursor(page, columnIndex, y);
        }

        private int resolveRenderedPageNumber(PDDocument doc, PDPage targetPage) {
            int index = 0;
            for (PDPage page : doc.getPages()) {
                if (page == targetPage) {
                    return index + 1;
                }
                index++;
            }
            return Math.max(1, doc.getNumberOfPages());
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
            FontSelection selection = resolveFontSelection(fontRuntimes, paragraph.style, null, FontVariant.REGULAR);
            TextAlignment alignment = paragraph.style.textAlignment == null ? TextAlignment.LEFT : paragraph.style.textAlignment;

            PDPage page = startPage;
            int columnIndex = startColumnIndex;
            float y = startY - paragraph.boxModel.marginTop() - paragraph.boxModel.paddingTop();

            int lineIndex = 0;
            while (lineIndex < lines.size()) {
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
                    beginTaggedMarkedContent(cs, StandardStructureTypes.P);
                    try {
                        while (lineIndex < lines.size()) {
                            if (y - leading < marginBottom) {
                                break;
                            }
                            String line = lines.get(lineIndex++);
                            boolean justifyLine = alignment == TextAlignment.JUSTIFY && lineIndex < lines.size();
                            float alignedX = resolveParagraphLineX(textX, resolvedContentWidth, selection, fontSize, line, alignment, justifyLine);
                            float wordSpacing = resolveParagraphWordSpacing(resolvedContentWidth, selection, fontSize, line, justifyLine);
                            drawChunkedLine(cs, selection, fontSize, alignedX, y, line, wordSpacing);
                            y -= leading;
                        }
                    } finally {
                        cs.endMarkedContent();
                    }
                }
            }

            y -= paragraph.boxModel.paddingBottom() + paragraph.boxModel.marginBottom();
            return new FlowCursor(page, columnIndex, y);
        }

        private FlowCursor renderFigureAcrossFlow(
                PDDocument doc,
                PDPage startPage,
                Map<String, FontRuntime> fontRuntimes,
                Figure figure,
                int startColumnIndex,
                int activeColumns,
                float activeColumnGap,
                float startY,
                float activeColumnWidth,
                float fullContentWidth) throws IOException {
            PDPage page = startPage;
            int columnIndex = startColumnIndex;
            float y = startY;

            boolean spanAllColumns = figure.flowMode == FigureFlowMode.SPAN_ALL_COLUMNS && activeColumns > 1;
            if (spanAllColumns && columnIndex != 0) {
                FlowCursor next = advanceTextFlow(doc, page, activeColumns - 1, activeColumns);
                page = next.page();
                columnIndex = 0;
                y = next.y();
            }

            float availableWidth = spanAllColumns ? fullContentWidth : activeColumnWidth;
            float x = spanAllColumns
                    ? marginLeft
                    : (activeColumns <= 1 ? marginLeft : resolveColumnX(columnIndex, activeColumns, activeColumnGap));

            FigureRenderPlan plan = buildFigureRenderPlan(doc, figure, availableWidth);
            if (y - plan.totalHeight() < marginBottom) {
                if (spanAllColumns) {
                    FlowCursor next = advanceTextFlow(doc, page, activeColumns - 1, activeColumns);
                    page = next.page();
                    columnIndex = 0;
                    y = next.y();
                } else {
                    FlowCursor next = advanceTextFlow(doc, page, columnIndex, activeColumns);
                    page = next.page();
                    columnIndex = next.columnIndex();
                    y = next.y();
                    x = activeColumns <= 1 ? marginLeft : resolveColumnX(columnIndex, activeColumns, activeColumnGap);
                }
            }

            float imageTop = y - 4.0f;
            float imageBottom = imageTop - plan.imageHeight();
            float imageX = x + (availableWidth - plan.imageWidth()) / 2.0f;

            // Record bounding box for /BBox attribute on Figure structure element
            if (currentElementIndex >= 0) {
                figureBBoxes.put(currentElementIndex, new float[]{imageX, imageBottom, plan.imageWidth(), plan.imageHeight()});
            }

            try (PDPageContentStream cs = new PDPageContentStream(
                    doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                if (plan.image() != null) {
                    int imgMcid = allocateMcid(page);
                    if (currentElementIndex >= 0) {
                        markedContentRecords.add(new MarkedContentRecord(currentElementIndex, -1, page, imgMcid));
                    }
                    COSDictionary imgProps = new COSDictionary();
                    imgProps.setInt(COSName.MCID, imgMcid);
                    cs.beginMarkedContent(COSName.getPDFName(StandardStructureTypes.Figure), PDPropertyList.create(imgProps));
                    cs.drawImage(plan.image(), imageX, imageBottom, plan.imageWidth(), plan.imageHeight());
                    cs.endMarkedContent();
                } else {
                    cs.beginMarkedContent(COSName.getPDFName("Artifact"));
                    cs.addRect(imageX, imageBottom, plan.imageWidth(), plan.imageHeight());
                    cs.stroke();
                    cs.endMarkedContent();
                    String missingText = "[Figure source not found: " + (figure.pathOrId == null ? "" : figure.pathOrId) + "]";
                    drawTaggedChunkedLine(cs, StandardStructureTypes.Figure, fontRuntimes, null, null, FontVariant.REGULAR, 9.0f, imageX + 4.0f, imageTop - 12.0f, missingText);
                }

                float captionY = imageBottom - 8.0f;
                for (String line : wrapText(plan.label(), availableWidth, 10.0f * 0.5f)) {
                    drawTaggedChunkedLine(cs, StandardStructureTypes.Figure, fontRuntimes, null, null, FontVariant.REGULAR, 10.0f, x, captionY, line);
                    captionY -= 12.0f;
                }
            }

            float nextY = imageBottom - 8.0f - plan.captionHeight();
            if (spanAllColumns) {
                columnIndex = 0;
            }
            return new FlowCursor(page, columnIndex, nextY);
        }

        private FlowCursor renderTocAcrossFlow(
                PDDocument doc,
                PDPage startPage,
                Map<String, FontRuntime> fontRuntimes,
                TocBlock tocBlock,
                int elementIndex,
                int startColumnIndex,
                int activeColumns,
                float activeColumnGap,
                float startY,
                float activeColumnWidth) throws IOException {
            PDPage page = startPage;
            int columnIndex = startColumnIndex;
            float y = startY;
            LayoutBlueprint layoutBlueprint = analyzeLayout();
            List<TocEntry> entries = buildTocEntries(layoutBlueprint, tocBlock.maxDepth);
            int tocPageCount = estimateTocPageCount(tocBlock, entries, activeColumnWidth, activeColumns, activeColumnGap, y);
            List<Integer> referenceSlots = new ArrayList<>();
            for (int i = 0; i < entries.size(); i++) {
                referenceSlots.add(nextItemSlot++);
            }
            tocSlotPlans.put(elementIndex, new TocSlotPlan(List.copyOf(referenceSlots)));

            String title = tocBlock.title == null ? "Table of Contents" : tocBlock.title;
            if (!title.isBlank()) {
                List<String> titleLines = wrapText(title, Math.max(1.0f, activeColumnWidth), 12.0f * 0.55f);

                for (String line : titleLines) {
                    if (y - 14.4f < marginBottom) {
                        FlowCursor next = advanceTextFlow(doc, page, columnIndex, activeColumns);
                        page = next.page();
                        columnIndex = next.columnIndex();
                        y = next.y();
                    }
                    float x = activeColumns <= 1
                            ? marginLeft
                            : resolveColumnX(columnIndex, activeColumns, activeColumnGap);
                    try (PDPageContentStream cs = new PDPageContentStream(
                            doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                        drawTaggedChunkedLine(cs, StandardStructureTypes.TOC, fontRuntimes, null, null, FontVariant.BOLD, 12.0f, x, y, line);
                    }
                    y -= 14.4f;
                }
            }

            for (int entryIndex = 0; entryIndex < entries.size(); entryIndex++) {
                TocEntry entry = entries.get(entryIndex);
                currentItemSlot = referenceSlots.get(entryIndex);
                String entryText = "  ".repeat(Math.max(0, entry.level() - 1)) + entry.text();
                List<String> lines = wrapText(entryText, Math.max(1.0f, activeColumnWidth), 11.0f * 0.5f);
                int targetPageNumber = resolveTocTargetPageNumber(entry, tocPageCount, elementIndex);
                for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
                    String line = lines.get(lineIndex);
                    if (y - 13.2f < marginBottom) {
                        FlowCursor next = advanceTextFlow(doc, page, columnIndex, activeColumns);
                        page = next.page();
                        columnIndex = next.columnIndex();
                        y = next.y();
                    }
                    float x = activeColumns <= 1
                            ? marginLeft
                            : resolveColumnX(columnIndex, activeColumns, activeColumnGap);
                        boolean lastLine = lineIndex == lines.size() - 1;
                        String displayLine = tocBlock.showPageNumbers && lastLine
                            ? line + " " + targetPageNumber
                            : line;
                        renderTocEntryLine(doc, page, fontRuntimes, tocBlock, displayLine, targetPageNumber, x, y, activeColumnWidth);
                    y -= 13.2f;
                }
                currentItemSlot = -1;
            }

                    tocPageSpans.add(new TocPageSpan(elementIndex, tocPageCount));

            return new FlowCursor(page, columnIndex, y - 8.0f);
        }

        private List<TocEntry> buildTocEntries(LayoutBlueprint layoutBlueprint, int maxDepth) {
            List<TocEntry> entries = new ArrayList<>();
            int depth = Math.max(1, maxDepth);
            List<LayoutBlock> headingBlocks = layoutBlueprint.blocks().stream()
                    .filter(block -> block.role() != null && block.role().startsWith("H"))
                    .toList();
            int headingCursor = 0;
            for (int elementIndex = 0; elementIndex < elements.size(); elementIndex++) {
                Element element = elements.get(elementIndex);
                if (element instanceof Heading heading && heading.includeInToc && heading.level <= depth) {
                    if (headingCursor >= headingBlocks.size()) {
                        break;
                    }
                    LayoutBlock headingBlock = headingBlocks.get(headingCursor++);
                    entries.add(new TocEntry(heading.level, heading.text, elementIndex, headingBlock.pageIndex()));
                }
            }
            return entries;
        }

        private int estimateTocPageCount(TocBlock tocBlock, List<TocEntry> entries, float activeColumnWidth, int activeColumns, float activeColumnGap, float startY) {
            String title = tocBlock.title == null ? "Table of Contents" : tocBlock.title;
            float y = startY;
            int pages = 1;
            int columnIndex = 0;

            if (!title.isBlank()) {
                for (String ignored : wrapText(title, Math.max(1.0f, activeColumnWidth), 12.0f * 0.55f)) {
                    if (y - 14.4f < marginBottom) {
                        if (activeColumns > 1 && columnIndex + 1 < activeColumns) {
                            columnIndex++;
                        } else {
                            pages++;
                            columnIndex = 0;
                        }
                        y = pageHeight - marginTop;
                    }
                    y -= 14.4f;
                }
            }

            for (TocEntry entry : entries) {
                String line = "  ".repeat(Math.max(0, entry.level() - 1)) + entry.text();
                for (String ignored : wrapText(line, Math.max(1.0f, activeColumnWidth), 11.0f * 0.5f)) {
                    if (y - 13.2f < marginBottom) {
                        if (activeColumns > 1 && columnIndex + 1 < activeColumns) {
                            columnIndex++;
                        } else {
                            pages++;
                            columnIndex = 0;
                        }
                        y = pageHeight - marginTop;
                    }
                    y -= 13.2f;
                }
            }

            return pages;
        }

        private int advanceTocColumn(int columnIndex, int activeColumns) {
            if (activeColumns <= 1) {
                return 0;
            }
            int nextColumn = columnIndex + 1;
            if (nextColumn >= activeColumns) {
                return 0;
            }
            return nextColumn;
        }

        private int tocPageShiftFor(int sourceElementIndex, int currentTocPageCount, int currentTocElementIndex) {
            int shift = 0;
            for (TocPageSpan span : tocPageSpans) {
                if (span.elementIndex() < sourceElementIndex) {
                    shift += span.pageCount();
                }
            }
            if (currentTocElementIndex >= 0 && currentTocElementIndex < sourceElementIndex) {
                shift += currentTocPageCount;
            }
            return shift;
        }

        private int resolveTocTargetPageNumber(TocEntry entry, int currentTocPageCount, int currentTocElementIndex) {
            Integer resolved = resolvedTocHeadingPages.get(entry.sourceElementIndex());
            if (resolved != null && resolved > 0) {
                return resolved;
            }
            int pageShift = tocPageShiftFor(entry.sourceElementIndex(), currentTocPageCount, currentTocElementIndex);
            return entry.pageIndex() + 1 + pageShift;
        }

        private void renderTocEntryLine(
                PDDocument doc,
                PDPage page,
                Map<String, FontRuntime> fontRuntimes,
                TocBlock tocBlock,
                String displayLine,
                int targetPageNumber,
                float x,
                float y,
                float contentWidth) throws IOException {
            String structureTag = tocBlock.itemMode == TocItemMode.LINK ? StandardStructureTypes.LINK : "Reference";
            try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                drawTaggedChunkedLine(cs, structureTag, fontRuntimes, null, null, FontVariant.REGULAR, 11.0f, x, y, displayLine);
            }
            if (!suppressTocLinkAnnotations && tocBlock.itemMode == TocItemMode.LINK) {
                tocLinkAnnotationPlans.add(new TocLinkAnnotationPlan(
                        page,
                        x,
                        y - 2.0f,
                        contentWidth,
                        13.2f,
                        targetPageNumber,
                        currentElementIndex,
                        currentItemSlot));
            }
        }

        private void applyTocLinkAnnotations(PDDocument doc) throws IOException {
            if (tocLinkAnnotationPlans.isEmpty()) {
                return;
            }
            int maxPageIndex = Math.max(0, doc.getNumberOfPages() - 1);
            for (TocLinkAnnotationPlan plan : tocLinkAnnotationPlans) {
                int targetIndex = Math.max(0, Math.min(maxPageIndex, plan.targetPageNumber() - 1));
                PDAnnotationLink link = new PDAnnotationLink();
                PDRectangle rect = new PDRectangle();
                rect.setLowerLeftX(plan.x());
                rect.setLowerLeftY(plan.y());
                rect.setUpperRightX(plan.x() + Math.max(1.0f, plan.width()));
                rect.setUpperRightY(plan.y() + Math.max(1.0f, plan.height()));
                link.setRectangle(rect);
                PDBorderStyleDictionary border = new PDBorderStyleDictionary();
                border.setWidth(0);
                link.setBorderStyle(border);
                PDActionGoTo action = new PDActionGoTo();
                PDPageXYZDestination destination = new PDPageXYZDestination();
                destination.setPage(doc.getPage(targetIndex));
                destination.setTop(Math.round(pageHeight - marginTop));
                destination.setLeft(Math.round(marginLeft));
                action.setDestination(destination);
                link.setAction(action);
                link.setPage(plan.page());
                plan.page().getAnnotations().add(link);
                if (plan.elementIndex() >= 0 && plan.referenceSlot() >= 0) {
                    tocLinkAnnotationsBySlot
                            .computeIfAbsent(new TocLinkSlotKey(plan.elementIndex(), plan.referenceSlot()), key -> new ArrayList<>())
                            .add(link);
                }
            }
        }

        private FigureRenderPlan buildFigureRenderPlan(PDDocument doc, Figure figure, float availableWidth) {
            final float maxImageHeight = 220.0f;
            final float placeholderWidth = Math.max(80.0f, Math.min(availableWidth, 180.0f));
            final float placeholderHeight = Math.max(60.0f, Math.min(maxImageHeight, placeholderWidth * 0.6f));

            PDImageXObject image = loadFigureImage(doc, figure.pathOrId);
            float targetWidth = placeholderWidth;
            float targetHeight = placeholderHeight;

            if (image != null) {
                float imageWidth = Math.max(1.0f, image.getWidth());
                float imageHeight = Math.max(1.0f, image.getHeight());
                float widthScale = availableWidth / imageWidth;
                float heightScale = maxImageHeight / imageHeight;
                float scale = Math.min(1.0f, Math.min(widthScale, heightScale));
                targetWidth = Math.max(1.0f, imageWidth * scale);
                targetHeight = Math.max(1.0f, imageHeight * scale);
            }

            String label = figureLabel(figure);
            float captionHeight = Math.max(12.0f, wrapText(label, availableWidth, 10.0f * 0.5f).size() * 12.0f);
            float totalHeight = 4.0f + targetHeight + 8.0f + captionHeight + 8.0f;
            return new FigureRenderPlan(image, targetWidth, targetHeight, label, captionHeight, totalHeight);
        }

        private String figureLabel(Figure figure) {
            if (figure.decorative) {
                return "[Figure - decorative]";
            }
            String alt = figure.altText == null ? "" : figure.altText.trim();
            if (!alt.isBlank()) {
                return "[Figure: " + alt + "]";
            }
            String source = figure.pathOrId == null ? "" : figure.pathOrId;
            return "[Figure: " + source + "]";
        }

        private PDImageXObject loadFigureImage(PDDocument doc, String pathOrId) {
            if (pathOrId == null || pathOrId.isBlank()) {
                return null;
            }

            try {
                Path direct = Path.of(pathOrId);
                if (Files.exists(direct)) {
                    return PDImageXObject.createFromFileByContent(direct.toFile(), doc);
                }
            } catch (IOException | IllegalArgumentException ignored) {
                return null;
            }

            try {
                Path examplePath = Path.of("src", "main", "resources", "examples", pathOrId);
                if (Files.exists(examplePath)) {
                    return PDImageXObject.createFromFileByContent(examplePath.toFile(), doc);
                }
            } catch (IOException | IllegalArgumentException ignored) {
                return null;
            }

            try (InputStream in = openFigureResource(pathOrId)) {
                if (in == null) {
                    return null;
                }
                BufferedImage bufferedImage = ImageIO.read(in);
                if (bufferedImage == null) {
                    return null;
                }
                return LosslessFactory.createFromImage(doc, bufferedImage);
            } catch (IOException ignored) {
                return null;
            }
        }

        private InputStream openFigureResource(String pathOrId) {
            ClassLoader classLoader = getClass().getClassLoader();
            InputStream in = classLoader.getResourceAsStream(pathOrId);
            if (in != null) {
                return in;
            }
            return classLoader.getResourceAsStream("examples/" + pathOrId);
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
            ListFlowState state = new ListFlowState(startPage, startY);
            state = renderListWithPaging(doc, state, x, listBlock, fontRuntimes);
            return new RenderCursor(state.page(), state.y() - 8.0f);
        }

        private RenderCursor renderTableAcrossPages(
                PDDocument doc,
                PDPage startPage,
                float startY,
                float x,
                float contentWidth,
                TableBlock tableBlock,
            int elementIndex,
            Map<String, FontRuntime> fontRuntimes) throws IOException {
            float headerFontSize = 10.0f;
            float bodyFontSize = 10.0f;
            float headerLeading = headerFontSize * 1.2f;
            float bodyLeading = bodyFontSize * 1.2f;
            float cellPadding = 4.0f;
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
            float wrapWidth = Math.max(1.0f, colWidth - (2.0f * cellPadding));

            List<List<String>> wrappedHeader = new ArrayList<>();
            int headerLines = 1;
            for (int c = 0; c < colCount; c++) {
                String headerText = c < tableBlock.headerCells.size() ? tableBlock.headerCells.get(c) : "";
                List<String> lines = wrapText(headerText, wrapWidth, headerFontSize * 0.5f);
                wrappedHeader.add(lines);
                headerLines = Math.max(headerLines, lines.size());
            }
            float headerHeight = Math.max(18.0f, headerLines * headerLeading + (2.0f * cellPadding));

            List<List<List<String>>> wrappedRows = new ArrayList<>();
            List<Float> rowHeights = new ArrayList<>();
            float maxBodyHeightPerPage = Math.max(
                    bodyLeading + (2.0f * cellPadding),
                    pageHeight - marginTop - marginBottom - headerHeight + 4.0f);
            int maxLinesPerChunk = Math.max(1, (int) Math.floor((maxBodyHeightPerPage - (2.0f * cellPadding)) / bodyLeading));
            for (List<String> row : tableBlock.rows) {
                List<List<String>> wrappedCells = new ArrayList<>();
                int maxLines = 1;
                for (int c = 0; c < colCount; c++) {
                    String cellText = c < row.size() ? row.get(c) : "";
                    List<String> lines = wrapText(cellText, wrapWidth, bodyFontSize * 0.5f);
                    wrappedCells.add(lines);
                    maxLines = Math.max(maxLines, lines.size());
                }
                for (int lineStart = 0; lineStart < maxLines; lineStart += maxLinesPerChunk) {
                    int lineEnd = Math.min(maxLines, lineStart + maxLinesPerChunk);
                    List<List<String>> chunkCells = new ArrayList<>();
                    int chunkMaxLines = 1;
                    for (List<String> cellLines : wrappedCells) {
                        int from = Math.min(lineStart, cellLines.size());
                        int to = Math.min(lineEnd, cellLines.size());
                        List<String> chunk = new ArrayList<>();
                        if (from < to) {
                            chunk.addAll(cellLines.subList(from, to));
                        }
                        if (chunk.isEmpty()) {
                            chunk.add("");
                        }
                        chunkCells.add(chunk);
                        chunkMaxLines = Math.max(chunkMaxLines, chunk.size());
                    }
                    wrappedRows.add(chunkCells);
                    rowHeights.add(Math.max(18.0f, chunkMaxLines * bodyLeading + (2.0f * cellPadding)));
                }
            }

            List<Integer> headerSlots = new ArrayList<>();
            for (int c = 0; c < colCount; c++) {
                headerSlots.add(nextItemSlot++);
            }
            List<List<Integer>> bodyRowSlots = new ArrayList<>();
            for (int r = 0; r < wrappedRows.size(); r++) {
                List<Integer> rowSlots = new ArrayList<>();
                for (int c = 0; c < colCount; c++) {
                    rowSlots.add(nextItemSlot++);
                }
                bodyRowSlots.add(rowSlots);
            }
            tableSlotPlans.put(elementIndex, new TableSlotPlan(List.copyOf(headerSlots), List.copyOf(bodyRowSlots)));

            PDPage page = startPage;
            float y = startY - boxModel.marginTop() - boxModel.paddingTop();
            int rowStart = 0;

            while (true) {
                float availableHeight = y + 4.0f - marginBottom;
                if (availableHeight < headerHeight) {
                    page = addStructuredPage(doc);
                    y = pageHeight - marginTop;
                    continue;
                }

                float remainingHeight = availableHeight - headerHeight;
                int rowsThisPage = 0;
                float bodyHeightThisPage = 0.0f;
                while (rowStart + rowsThisPage < rowHeights.size()) {
                    float nextRowHeight = rowHeights.get(rowStart + rowsThisPage);
                    if (bodyHeightThisPage + nextRowHeight > remainingHeight) {
                        break;
                    }
                    bodyHeightThisPage += nextRowHeight;
                    rowsThisPage++;
                }

                if (rowsThisPage == 0 && !rowHeights.isEmpty()) {
                    page = addStructuredPage(doc);
                    y = pageHeight - marginTop;
                    continue;
                }

                float tableTop = y + 4.0f;
                float tableHeight = headerHeight + bodyHeightThisPage;
                float tableBottom = tableTop - tableHeight;

                try (PDPageContentStream cs = new PDPageContentStream(
                        doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    cs.beginMarkedContent(COSName.getPDFName("Artifact"));
                    cs.addRect(tableX, tableBottom, tableWidth, tableHeight);
                    for (int i = 1; i < colCount; i++) {
                        float lineX = tableX + i * colWidth;
                        cs.moveTo(lineX, tableTop);
                        cs.lineTo(lineX, tableBottom);
                    }
                    float gridY = tableTop - headerHeight;
                    cs.moveTo(tableX, gridY);
                    cs.lineTo(tableX + tableWidth, gridY);
                    float runningBodyY = gridY;
                    for (int i = 0; i < rowsThisPage - 1; i++) {
                        runningBodyY -= rowHeights.get(rowStart + i);
                        float lineY = runningBodyY;
                        cs.moveTo(tableX, lineY);
                        cs.lineTo(tableX + tableWidth, lineY);
                    }
                    cs.stroke();
                    cs.endMarkedContent();

                    for (int i = 0; i < colCount; i++) {
                        List<String> headerLinesWrapped = wrappedHeader.get(i);
                        currentItemSlot = headerSlots.get(i);
                        float headerLineY = tableTop - cellPadding - headerFontSize;
                        for (String line : headerLinesWrapped) {
                            drawTaggedChunkedLine(
                                    cs,
                                StandardStructureTypes.TH,
                                    fontRuntimes,
                                    TextStyle.of(tableBlock.style.fontFamilyKey, FontVariant.BOLD),
                                    tableBlock.style,
                                    FontVariant.BOLD,
                                    headerFontSize,
                                    tableX + i * colWidth + cellPadding,
                                    headerLineY,
                                    line);
                            headerLineY -= headerLeading;
                        }
                        currentItemSlot = -1;
                    }

                    float rowTop = tableTop - headerHeight;
                    for (int r = 0; r < rowsThisPage; r++) {
                        float rowHeight = rowHeights.get(rowStart + r);
                        List<List<String>> rowCells = wrappedRows.get(rowStart + r);
                        for (int c = 0; c < colCount; c++) {
                            currentItemSlot = bodyRowSlots.get(rowStart + r).get(c);
                            List<String> cellLines = rowCells.get(c);
                            float cellLineY = rowTop - cellPadding - bodyFontSize;
                            for (String line : cellLines) {
                                drawTaggedChunkedLine(
                                        cs,
                                    StandardStructureTypes.TD,
                                        fontRuntimes,
                                        null,
                                        tableBlock.style,
                                        FontVariant.REGULAR,
                                        bodyFontSize,
                                        tableX + c * colWidth + cellPadding,
                                        cellLineY,
                                        line);
                                cellLineY -= bodyLeading;
                            }
                            currentItemSlot = -1;
                        }
                        rowTop -= rowHeight;
                    }
                }

                rowStart += rowsThisPage;
                if (rowStart >= rowHeights.size()) {
                    return new RenderCursor(page, tableBottom - boxModel.paddingBottom() - boxModel.marginBottom() - 8.0f);
                }

                page = addStructuredPage(doc);
                y = pageHeight - marginTop;
            }
        }

        private void setupCatalogMetadata(PDDocument doc) throws IOException {
            PDDocumentCatalog catalog = doc.getDocumentCatalog();
            catalog.setLanguage(lang);

            // Mark the document as tagged (required by PDF/UA and checked by PAC)
            COSDictionary markInfo = new COSDictionary();
            markInfo.setBoolean(COSName.getPDFName("Marked"), true);
            catalog.getCOSObject().setItem(COSName.getPDFName("MarkInfo"), markInfo);

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
            drawChunkedLine(cs, selection, fontSize, x, y, text, 0.0f);
        }

        private void drawChunkedLine(
                PDPageContentStream cs,
                FontSelection selection,
                float fontSize,
                float x,
                float y,
                String text,
                float wordSpacing) throws IOException {
            if (wordSpacing > 0.0f && text != null && text.indexOf(' ') >= 0) {
                drawChunkedLineWithManualWordSpacing(cs, selection, fontSize, x, y, text, wordSpacing);
                return;
            }

            drawChunkedLineNoSpacing(cs, selection, fontSize, x, y, text);
        }

        private void drawChunkedLineWithManualWordSpacing(
                PDPageContentStream cs,
                FontSelection selection,
                float fontSize,
                float x,
                float y,
                String text,
                float wordSpacing) throws IOException {
            float cursorX = x;
            String[] tokens = text.split(" ", -1);
            float baseSpaceWidth = measureChunkedTextWidth(selection.runtime(), selection.variant(), fontSize, " ");

            for (int i = 0; i < tokens.length; i++) {
                String token = tokens[i];
                if (!token.isEmpty()) {
                    drawChunkedLineNoSpacing(cs, selection, fontSize, cursorX, y, token);
                    cursorX += measureChunkedTextWidth(selection.runtime(), selection.variant(), fontSize, token);
                }

                if (i < tokens.length - 1) {
                    cursorX += baseSpaceWidth + wordSpacing;
                }
            }
        }

        private void drawChunkedLineNoSpacing(
                PDPageContentStream cs,
                FontSelection selection,
                float fontSize,
                float x,
                float y,
                String text) throws IOException {
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

        private float resolveParagraphLineX(
                float baseX,
                float availableWidth,
                FontSelection selection,
                float fontSize,
                String line,
                TextAlignment alignment,
                boolean justifyLine) throws IOException {
            if (justifyLine || alignment == null || alignment == TextAlignment.LEFT || line == null || line.isBlank()) {
                return baseX;
            }
            float textWidth = measureChunkedTextWidth(selection.runtime(), selection.variant(), fontSize, line);
            return switch (alignment) {
                case RIGHT -> baseX + Math.max(0.0f, availableWidth - textWidth);
                case CENTER -> baseX + Math.max(0.0f, (availableWidth - textWidth) / 2.0f);
                default -> baseX;
            };
        }

        private float resolveParagraphWordSpacing(
                float availableWidth,
                FontSelection selection,
                float fontSize,
                String line,
                boolean justifyLine) throws IOException {
            if (!justifyLine || line == null || line.isBlank()) {
                return 0.0f;
            }
            int spaces = countJustifiableSpaces(line);
            if (spaces <= 0) {
                return 0.0f;
            }
            float textWidth = measureChunkedTextWidth(selection.runtime(), selection.variant(), fontSize, line);
            float delta = availableWidth - textWidth;
            if (delta <= 0.0f) {
                return 0.0f;
            }
            return delta / spaces;
        }

        private int countJustifiableSpaces(String line) {
            int spaces = 0;
            for (int i = 0; i < line.length(); i++) {
                if (line.charAt(i) == ' ') {
                    spaces++;
                }
            }
            return spaces;
        }

        private void drawTaggedChunkedLine(
                PDPageContentStream cs,
                String structureTag,
                Map<String, FontRuntime> fontRuntimes,
                TextStyle nodeStyle,
                TextStyle parentStyle,
                FontVariant fallbackVariant,
                float fontSize,
                float x,
                float y,
                String text) throws IOException {
            beginTaggedMarkedContent(cs, structureTag);
            try {
                drawChunkedLine(cs, fontRuntimes, nodeStyle, parentStyle, fallbackVariant, fontSize, x, y, text);
            } finally {
                cs.endMarkedContent();
            }
        }

        private void beginTaggedMarkedContent(PDPageContentStream cs, String structureTag) throws IOException {
            String tag = (structureTag == null || structureTag.isBlank()) ? StandardStructureTypes.P : structureTag;
            PDPage recordPage = currentRenderPage;
            int mcid = allocateMcid(recordPage != null ? recordPage : new PDPage());
            if (recordPage != null && currentElementIndex >= 0) {
                markedContentRecords.add(new MarkedContentRecord(currentElementIndex, currentItemSlot, recordPage, mcid));
            }
            COSDictionary markedContentProps = new COSDictionary();
            markedContentProps.setInt(COSName.MCID, mcid);
            cs.beginMarkedContent(COSName.getPDFName(tag), PDPropertyList.create(markedContentProps));
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

        private float renderListWithoutPaging(
                PDPageContentStream cs,
                Map<String, FontRuntime> fontRuntimes,
                ListBlock listBlock,
                float x,
                float startY,
                float leading) throws IOException {
            float y = startY - listBlock.boxModel.marginTop() - listBlock.boxModel.paddingTop();
            float contentX = x + listBlock.boxModel.paddingLeft();
            float bulletX = contentX + 12.0f;
            float averageCharWidth = 12.0f * 0.5f;
            float availableTextWidth = pageWidth - bulletX - marginRight - listBlock.boxModel.paddingRight();
            if (availableTextWidth <= 0.0f) {
                throw new ValidationException("List box model leaves no room for content");
            }
            float firstLineWidth = Math.max(1.0f, availableTextWidth - maxListPrefixWidth(listBlock));
            float continuationWidth = switch (listBlock.indentStyle) {
                case ALIGN_WITH_BULLET -> availableTextWidth;
                case TWO_SPACE -> Math.max(1.0f, availableTextWidth - (2.0f * averageCharWidth));
                case CUSTOM -> Math.max(1.0f, availableTextWidth - listBlock.customIndentPt);
            };
            float wrapWidth = Math.max(1.0f, Math.min(firstLineWidth, continuationWidth));

            for (int itemIndex = 0; itemIndex < listBlock.items.size(); itemIndex++) {
                ListItem item = listBlock.items.get(itemIndex);
                int labelSlot = nextItemSlot++;
                int bodySlot = nextItemSlot++;
                listItemSlotPlans
                        .computeIfAbsent(currentElementIndex, k -> new ArrayList<>())
                        .add(new ListItemSlotPlan(labelSlot, bodySlot));
                List<String> lines = wrapText(item.text, wrapWidth, averageCharWidth);
                String marker = listItemPrefix(listBlock, itemIndex);
                currentItemSlot = labelSlot;
                drawTaggedChunkedLine(cs, "LBody", fontRuntimes, null, listBlock.style, FontVariant.REGULAR, 12.0f, bulletX, y, marker);

                currentItemSlot = bodySlot;
                beginTaggedMarkedContent(cs, "LBody");
                try {
                    for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
                        String line = lines.get(lineIndex);
                        float lineX = bulletX;
                        if (lineIndex == 0) {
                            lineX += marker.length() * averageCharWidth;
                        } else if (listBlock.indentStyle == ListIndentStyle.TWO_SPACE) {
                            line = "  " + line;
                        } else if (listBlock.indentStyle == ListIndentStyle.CUSTOM) {
                            lineX += listBlock.customIndentPt;
                        }
                        drawChunkedLine(cs, fontRuntimes, null, listBlock.style, FontVariant.REGULAR, 12.0f, lineX, y, line);
                        y -= leading;
                    }
                } finally {
                    cs.endMarkedContent();
                }
                currentItemSlot = -1;
                if (item.nestedList != null) {
                    y = renderListWithoutPaging(cs, fontRuntimes, item.nestedList, x + 18.0f, y, leading);
                }
            }

            y -= listBlock.boxModel.paddingBottom() + listBlock.boxModel.marginBottom();
            return y;
        }

        private ListFlowState renderListWithPaging(
                PDDocument doc,
                ListFlowState start,
                float x,
                ListBlock listBlock,
                Map<String, FontRuntime> fontRuntimes) throws IOException {
            float y = start.y() - listBlock.boxModel.marginTop() - listBlock.boxModel.paddingTop();
            PDPage page = start.page();
            if (y <= marginBottom) {
                page = addStructuredPage(doc);
                y = pageHeight - marginTop;
            }

            float contentX = x + listBlock.boxModel.paddingLeft();
            float bulletX = contentX + 12.0f;
            float averageCharWidth = 12.0f * 0.5f;
            float availableTextWidth = pageWidth - bulletX - marginRight - listBlock.boxModel.paddingRight();
            if (availableTextWidth <= 0.0f) {
                throw new ValidationException("List box model leaves no room for content");
            }
            float firstLineWidth = Math.max(1.0f, availableTextWidth - maxListPrefixWidth(listBlock));
            float continuationWidth = switch (listBlock.indentStyle) {
                case ALIGN_WITH_BULLET -> availableTextWidth;
                case TWO_SPACE -> Math.max(1.0f, availableTextWidth - (2.0f * averageCharWidth));
                case CUSTOM -> Math.max(1.0f, availableTextWidth - listBlock.customIndentPt);
            };
            float wrapWidth = Math.max(1.0f, Math.min(firstLineWidth, continuationWidth));

            for (int itemIndex = 0; itemIndex < listBlock.items.size(); itemIndex++) {
                ListItem item = listBlock.items.get(itemIndex);
                int labelSlot = nextItemSlot++;
                int bodySlot = nextItemSlot++;
                listItemSlotPlans
                        .computeIfAbsent(currentElementIndex, k -> new ArrayList<>())
                        .add(new ListItemSlotPlan(labelSlot, bodySlot));
                List<String> lines = wrapText(item.text, wrapWidth, averageCharWidth);
                String marker = listItemPrefix(listBlock, itemIndex);
                if (y - 14.4f < marginBottom) {
                    page = addStructuredPage(doc);
                    y = pageHeight - marginTop;
                }
                currentItemSlot = labelSlot;
                try (PDPageContentStream cs = new PDPageContentStream(
                        doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    drawTaggedChunkedLine(cs, "LBody", fontRuntimes, null, listBlock.style, FontVariant.REGULAR, 12.0f, bulletX, y, marker);
                }

                int lineIndex = 0;
                currentItemSlot = bodySlot;
                while (lineIndex < lines.size()) {
                    if (y - 14.4f < marginBottom) {
                        page = addStructuredPage(doc);
                        y = pageHeight - marginTop;
                    }
                    try (PDPageContentStream cs = new PDPageContentStream(
                            doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                        beginTaggedMarkedContent(cs, "LBody");
                        try {
                            while (lineIndex < lines.size()) {
                                if (y - 14.4f < marginBottom) {
                                    break;
                                }
                                String line = lines.get(lineIndex);
                                float lineX = bulletX;
                                if (lineIndex == 0) {
                                    lineX += marker.length() * averageCharWidth;
                                } else if (listBlock.indentStyle == ListIndentStyle.TWO_SPACE) {
                                    line = "  " + line;
                                } else if (listBlock.indentStyle == ListIndentStyle.CUSTOM) {
                                    lineX += listBlock.customIndentPt;
                                }
                                drawChunkedLine(cs, fontRuntimes, null, listBlock.style, FontVariant.REGULAR, 12.0f, lineX, y, line);
                                y -= 14.4f;
                                lineIndex++;
                            }
                        } finally {
                            cs.endMarkedContent();
                        }
                    }
                }
                currentItemSlot = -1;

                if (item.nestedList != null) {
                    ListFlowState nestedState = renderListWithPaging(
                            doc,
                            new ListFlowState(page, y),
                            x + 18.0f,
                            item.nestedList,
                            fontRuntimes);
                    page = nestedState.page();
                    y = nestedState.y();
                }
            }

            y -= listBlock.boxModel.paddingBottom() + listBlock.boxModel.marginBottom();
            return new ListFlowState(page, y);
        }

        private String listItemPrefix(ListBlock listBlock, int itemIndex) {
            if (listBlock.ordered) {
                return (listBlock.start + itemIndex) + ". ";
            }
            return switch (listBlock.bulletStyle) {
                case DISC -> "* ";
                case CIRCLE -> "o ";
                case SQUARE -> "# ";
                case DASH -> "- ";
                case CUSTOM -> listBlock.customMarker + " ";
            };
        }

        private float maxListPrefixWidth(ListBlock listBlock) {
            float averageCharWidth = 12.0f * 0.5f;
            if (listBlock.ordered) {
                int maxNumber = listBlock.start + Math.max(0, listBlock.items.size() - 1);
                int markerChars = String.valueOf(maxNumber).length() + 2;
                return markerChars * averageCharWidth;
            }
            int markerChars = switch (listBlock.bulletStyle) {
                case DISC, CIRCLE, SQUARE, DASH -> 2;
                case CUSTOM -> (listBlock.customMarker == null ? 1 : listBlock.customMarker.length()) + 1;
            };
            return markerChars * averageCharWidth;
        }

        private int countRenderedListItems(ListBlock listBlock) {
            int count = listBlock.items.size();
            for (ListItem item : listBlock.items) {
                if (item.nestedList != null) {
                    count += countRenderedListItems(item.nestedList);
                }
            }
            return count;
        }

        private void buildStructureTree(PDDocument doc) {
            PDDocumentCatalog catalog = doc.getDocumentCatalog();
            PDStructureTreeRoot root = new PDStructureTreeRoot();
            catalog.setStructureTreeRoot(root);

            PDStructureElement document = new PDStructureElement(StandardStructureTypes.DOCUMENT, root);
            root.appendKid(document);

            for (int elemIdx = 0; elemIdx < elements.size(); elemIdx++) {
                Element element = elements.get(elemIdx);
                if (element instanceof Heading heading) {
                    PDStructureElement e = new PDStructureElement(mapHeadingType(heading.level), document);
                    document.appendKid(e);
                    attachMCRs(e, elemIdx);
                } else if (element instanceof Paragraph) {
                    PDStructureElement e = new PDStructureElement(StandardStructureTypes.P, document);
                    document.appendKid(e);
                    attachMCRs(e, elemIdx);
                } else if (element instanceof Figure figure) {
                    PDStructureElement figureParent = document;
                    if (figure.flowMode == FigureFlowMode.INLINE) {
                        PDStructureElement paragraph = new PDStructureElement(StandardStructureTypes.P, document);
                        document.appendKid(paragraph);
                        figureParent = paragraph;
                    }

                    PDStructureElement e = new PDStructureElement(StandardStructureTypes.Figure, figureParent);
                    String altValue = figure.decorative ? "" : (figure.altText != null && !figure.altText.isBlank() ? figure.altText : figure.pathOrId);
                    e.getCOSObject().setString(COSName.ALT, altValue != null ? altValue : "");
                    float[] bbox = figureBBoxes.get(elemIdx);
                    if (bbox != null) {
                        COSArray bboxArray = new COSArray();
                        bboxArray.add(COSInteger.get((int) bbox[0]));
                        bboxArray.add(COSInteger.get((int) bbox[1]));
                        bboxArray.add(COSInteger.get((int) (bbox[0] + bbox[2])));
                        bboxArray.add(COSInteger.get((int) (bbox[1] + bbox[3])));
                        COSDictionary attrDict = new COSDictionary();
                        attrDict.setName(COSName.O, "Layout");
                        attrDict.setName(COSName.getPDFName("Placement"),
                                figure.flowMode == FigureFlowMode.SPAN_ALL_COLUMNS ? "Block" : "Inline");
                        attrDict.setItem(COSName.getPDFName("BBox"), bboxArray);
                        e.getCOSObject().setItem(COSName.getPDFName("A"), attrDict);
                    }
                    figureParent.appendKid(e);
                    attachMCRs(e, elemIdx);
                } else if (element instanceof ListBlock listBlock) {
                    appendListStructure(document, listBlock, elemIdx);
                    // MCRs are attached per-item to LBody in appendListItemsStructure; do not attach to L
                } else if (element instanceof TableBlock tableBlock) {
                    appendTableStructure(document, tableBlock, elemIdx);
                } else if (element instanceof TocBlock tocBlock) {
                    PDStructureElement e = appendTocStructure(document, tocBlock, elemIdx);
                    attachMCRs(e, elemIdx);
                } else if (element instanceof CustomBlock) {
                    PDStructureElement e = new PDStructureElement("Sect", document);
                    document.appendKid(e);
                    attachMCRs(e, elemIdx);
                }
            }

            buildParentTree(root);
        }

        private void buildParentTree(PDStructureTreeRoot structRoot) {
            // Collect distinct pages in encounter order
            Map<PDPage, Integer> pageToKey = new LinkedHashMap<>();
            for (MarkedContentRecord rec : markedContentRecords) {
                if (rec.page() != null) {
                    pageToKey.putIfAbsent(rec.page(), pageToKey.size());
                }
            }
            if (pageToKey.isEmpty()) {
                return;
            }

            // Assign StructParents key to each page and build the Nums array
            COSArray nums = new COSArray();
            for (Map.Entry<PDPage, Integer> entry : pageToKey.entrySet()) {
                PDPage page = entry.getKey();
                int structParentsKey = entry.getValue();

                page.getCOSObject().setInt(COSName.getPDFName("StructParents"), structParentsKey);

                Map<Integer, PDStructureElement> pageMap = mcidToStructElem.getOrDefault(page, Map.of());
                // Array must be indexed 0..maxMcid on this page; each slot = owning struct element
                int maxMcid = pageMap.isEmpty() ? -1 : pageMap.keySet().stream().mapToInt(Integer::intValue).max().getAsInt();
                COSArray pageArray = new COSArray();
                for (int i = 0; i <= maxMcid; i++) {
                    PDStructureElement se = pageMap.get(i);
                    pageArray.add(se != null ? se.getCOSObject() : COSNull.NULL);
                }

                nums.add(COSInteger.get(structParentsKey));
                nums.add(pageArray);
            }

            for (Map.Entry<Integer, PDStructureElement> entry : objectParentTreeEntries.entrySet()) {
                nums.add(COSInteger.get(entry.getKey()));
                nums.add(entry.getValue().getCOSObject());
            }

            COSDictionary parentTreeDict = new COSDictionary();
            parentTreeDict.setItem(COSName.NUMS, nums);
            structRoot.getCOSObject().setItem(COSName.getPDFName("ParentTree"), parentTreeDict);
            int maxKey = -1;
            for (Integer value : pageToKey.values()) {
                maxKey = Math.max(maxKey, value);
            }
            for (Integer key : objectParentTreeEntries.keySet()) {
                maxKey = Math.max(maxKey, key);
            }
            structRoot.getCOSObject().setInt(COSName.getPDFName("ParentTreeNextKey"), maxKey + 1);
        }

        private void attachMCRs(PDStructureElement elem, int elementIndex) {
            for (MarkedContentRecord record : markedContentRecords) {
                if (record.elementIndex() == elementIndex && record.itemSlot() == -1) {
                    PDMarkedContentReference mcr = new PDMarkedContentReference();
                    mcr.setPage(record.page());
                    mcr.setMCID(record.mcid());
                    elem.appendKid(mcr);
                    mcidToStructElem
                            .computeIfAbsent(record.page(), p -> new LinkedHashMap<>())
                            .put(record.mcid(), elem);
                }
            }
        }

        private void attachListItemMCRs(PDStructureElement elem, int elementIndex, int itemSlot) {
            for (MarkedContentRecord record : markedContentRecords) {
                if (record.elementIndex() == elementIndex && record.itemSlot() == itemSlot) {
                    PDMarkedContentReference mcr = new PDMarkedContentReference();
                    mcr.setPage(record.page());
                    mcr.setMCID(record.mcid());
                    elem.appendKid(mcr);
                    mcidToStructElem
                            .computeIfAbsent(record.page(), p -> new LinkedHashMap<>())
                            .put(record.mcid(), elem);
                }
            }
        }

        private void attachTableCellMCRs(PDStructureElement elem, int elementIndex, int cellSlot) {
            attachListItemMCRs(elem, elementIndex, cellSlot);
        }

        private void attachTocReferenceMCRs(PDStructureElement elem, int elementIndex, int referenceSlot) {
            attachListItemMCRs(elem, elementIndex, referenceSlot);
        }

        private PDStructureElement appendListStructure(PDStructureTreeRoot parent, ListBlock listBlock, int elemIdx) {
            PDStructureElement list = new PDStructureElement(StandardStructureTypes.L, parent);
            parent.appendKid(list);
            List<ListItemSlotPlan> itemSlots = listItemSlotPlans.getOrDefault(elemIdx, List.of());
            int[] slotCursor = new int[]{0};
            appendListItemsStructure(list, listBlock, elemIdx, itemSlots, slotCursor);
            return list;
        }

        private void appendListStructure(PDStructureElement parent, ListBlock listBlock, int elemIdx) {
            List<ListItemSlotPlan> itemSlots = listItemSlotPlans.getOrDefault(elemIdx, List.of());
            int[] slotCursor = new int[]{0};
            appendListStructure(parent, listBlock, elemIdx, itemSlots, slotCursor);
        }

        private void appendListStructure(
                PDStructureElement parent,
                ListBlock listBlock,
                int elemIdx,
                List<ListItemSlotPlan> itemSlots,
                int[] slotCursor) {
            PDStructureElement list = new PDStructureElement(StandardStructureTypes.L, parent);
            parent.appendKid(list);
            appendListItemsStructure(list, listBlock, elemIdx, itemSlots, slotCursor);
        }

        private void appendListItemsStructure(
                PDStructureElement list,
                ListBlock listBlock,
                int elemIdx,
                List<ListItemSlotPlan> itemSlots,
                int[] slotCursor) {
            for (ListItem item : listBlock.items) {
                PDStructureElement li = new PDStructureElement(StandardStructureTypes.LI, list);
                list.appendKid(li);
                PDStructureElement label = new PDStructureElement("Lbl", li);
                PDStructureElement body = new PDStructureElement("LBody", li);
                PDStructureElement paragraph = new PDStructureElement(StandardStructureTypes.P, body);
                li.appendKid(label);
                li.appendKid(body);
                body.appendKid(paragraph);

                if (slotCursor[0] < itemSlots.size()) {
                    ListItemSlotPlan plan = itemSlots.get(slotCursor[0]++);
                    attachListItemMCRs(label, elemIdx, plan.labelSlot());
                    attachListItemMCRs(paragraph, elemIdx, plan.bodySlot());
                }

                if (item.nestedList != null) {
                    appendListStructure(body, item.nestedList, elemIdx, itemSlots, slotCursor);
                }
            }
        }

        private PDStructureElement appendTableStructure(PDStructureElement parent, TableBlock tableBlock, int elemIdx) {
            PDStructureElement table = new PDStructureElement(StandardStructureTypes.TABLE, parent);
            parent.appendKid(table);

            TableSlotPlan slotPlan = tableSlotPlans.get(elemIdx);
            List<PDStructureElement> thElements = new ArrayList<>();
            if (!tableBlock.headerCells.isEmpty()) {
                PDStructureElement tHead = new PDStructureElement("THead", table);
                table.appendKid(tHead);
                PDStructureElement headerRow = new PDStructureElement(StandardStructureTypes.TR, tHead);
                tHead.appendKid(headerRow);
                for (int c = 0; c < tableBlock.headerCells.size(); c++) {
                    PDStructureElement th = new PDStructureElement(StandardStructureTypes.TH, headerRow);
                    COSDictionary thAttr = new COSDictionary();
                    thAttr.setName(COSName.O, "Table");
                    thAttr.setName(COSName.getPDFName("Scope"), "Column");
                    th.getCOSObject().setItem(COSName.getPDFName("A"), thAttr);
                    headerRow.appendKid(th);
                    thElements.add(th);
                    if (slotPlan != null && c < slotPlan.headerCellSlots().size()) {
                        attachTableCellMCRs(th, elemIdx, slotPlan.headerCellSlots().get(c));
                    }
                }
            }

            PDStructureElement tBody = new PDStructureElement("TBody", table);
            table.appendKid(tBody);
            List<List<Integer>> bodySlots = slotPlan == null ? List.of() : slotPlan.bodyRowCellSlots();
            int bodyRowCount = slotPlan == null ? tableBlock.rows.size() : bodySlots.size();
            for (int rowIdx = 0; rowIdx < bodyRowCount; rowIdx++) {
                PDStructureElement tr = new PDStructureElement(StandardStructureTypes.TR, tBody);
                tBody.appendKid(tr);
                int cells;
                if (slotPlan == null) {
                    List<String> row = rowIdx < tableBlock.rows.size() ? tableBlock.rows.get(rowIdx) : List.of();
                    cells = tableBlock.headerCells.isEmpty() ? row.size() : tableBlock.headerCells.size();
                } else {
                    cells = bodySlots.get(rowIdx).size();
                }
                for (int c = 0; c < cells; c++) {
                    PDStructureElement td = new PDStructureElement(StandardStructureTypes.TD, tr);
                    // Link TD to its column TH via /Headers attribute
                    if (c < thElements.size()) {
                        COSDictionary tdAttr = new COSDictionary();
                        tdAttr.setName(COSName.O, "Table");
                        COSArray headersArray = new COSArray();
                        headersArray.add(thElements.get(c).getCOSObject());
                        tdAttr.setItem(COSName.getPDFName("Headers"), headersArray);
                        td.getCOSObject().setItem(COSName.getPDFName("A"), tdAttr);
                    }
                    tr.appendKid(td);
                    if (slotPlan != null && rowIdx < bodySlots.size() && c < bodySlots.get(rowIdx).size()) {
                        attachTableCellMCRs(td, elemIdx, bodySlots.get(rowIdx).get(c));
                    }
                }
            }
            return table;
        }

        private PDStructureElement appendTocStructure(PDStructureElement parent, TocBlock tocBlock, int elemIdx) {
            PDStructureElement toc = new PDStructureElement(StandardStructureTypes.TOC, parent);
            parent.appendKid(toc);

            TocSlotPlan slotPlan = tocSlotPlans.get(elemIdx);
            LayoutBlueprint layoutBlueprint = analyzeLayout();
            List<TocEntry> entries = buildTocEntries(layoutBlueprint, tocBlock.maxDepth);
            for (int i = 0; i < entries.size(); i++) {
                PDStructureElement toci = new PDStructureElement("TOCI", toc);
                toc.appendKid(toci);
                if (slotPlan == null || i >= slotPlan.referenceSlots().size()) {
                    continue;
                }

                int referenceSlot = slotPlan.referenceSlots().get(i);
                if (tocBlock.itemMode == TocItemMode.LINK) {
                    PDStructureElement linkElem = new PDStructureElement(StandardStructureTypes.LINK, toci);
                    toci.appendKid(linkElem);

                    List<PDAnnotationLink> annotations = tocLinkAnnotationsBySlot.getOrDefault(
                            new TocLinkSlotKey(elemIdx, referenceSlot),
                            List.of());
                    for (PDAnnotationLink annotation : annotations) {
                        int structParent = nextAnnotationStructParent++;
                        annotation.getCOSObject().setInt(COSName.STRUCT_PARENT, structParent);
                        objectParentTreeEntries.put(structParent, linkElem);

                        COSDictionary objrDict = new COSDictionary();
                        objrDict.setItem(COSName.TYPE, COSName.getPDFName("OBJR"));
                        objrDict.setItem(COSName.OBJ, annotation.getCOSObject()); // Link to physical annotation
                        objrDict.setItem(COSName.PG, annotation.getPage().getCOSObject());             // Link to parent page

                        PDObjectReference objRef = new PDObjectReference(objrDict);
                        linkElem.appendKid(objRef);
                    }

                    attachTocReferenceMCRs(linkElem, elemIdx, referenceSlot);
                } else {
                    PDStructureElement reference = new PDStructureElement("Reference", toci);
                    toci.appendKid(reference);
                    attachTocReferenceMCRs(reference, elemIdx, referenceSlot);
                }
            }
            return toc;
        }

        private void renderArtifactPageChrome(PDDocument doc, Map<String, FontRuntime> fontRuntimes) throws IOException {
            if (headerTextArtifact == null
                    && footerTextArtifact == null
                    && headerLinkArtifact == null
                    && footerLinkArtifact == null
                    && headerImageArtifact == null
                    && footerImageArtifact == null
                    && (artifactFooterPattern == null || artifactFooterPattern.isBlank())) {
                return;
            }

            FontRuntime artifactRuntime = resolveArtifactFontRuntime(fontRuntimes);
            if (artifactRuntime == null) {
                return;
            }

            int totalPages = doc.getNumberOfPages();
            for (int pageIndex = 0; pageIndex < totalPages; pageIndex++) {
                PDPage page = doc.getPage(pageIndex);
                try (PDPageContentStream contentStream = new PDPageContentStream(
                        doc,
                        page,
                        PDPageContentStream.AppendMode.APPEND,
                        true,
                        true)) {
                    contentStream.beginMarkedContent(COSName.getPDFName("Artifact"));
                    drawArtifactDecorations(contentStream);

                    if (headerImageArtifact != null) {
                        drawArtifactImage(doc, page, contentStream, headerImageArtifact, true);
                    }

                    if (headerTextArtifact != null && headerTextArtifact.text() != null && !headerTextArtifact.text().isBlank()) {
                        float headerY = pageHeight - Math.max(10.0f, marginTop * 0.55f);
                        drawArtifactAlignedText(contentStream, artifactRuntime, headerTextArtifact.text(), headerTextArtifact.fontSize(), headerY, headerTextArtifact.alignment());
                    }

                    if (headerLinkArtifact != null && headerLinkArtifact.text() != null && !headerLinkArtifact.text().isBlank()) {
                        float headerLinkY = pageHeight - Math.max(20.0f, marginTop * 0.75f);
                        drawArtifactLink(doc, page, contentStream, artifactRuntime, headerLinkArtifact, headerLinkY);
                    }

                    if (footerImageArtifact != null) {
                        drawArtifactImage(doc, page, contentStream, footerImageArtifact, false);
                    }

                    if (footerTextArtifact != null && footerTextArtifact.text() != null && !footerTextArtifact.text().isBlank()) {
                        float footerTextY = Math.max(16.0f, marginBottom * 0.72f);
                        drawArtifactAlignedText(contentStream, artifactRuntime, footerTextArtifact.text(), footerTextArtifact.fontSize(), footerTextY, footerTextArtifact.alignment());
                    }

                    if (footerLinkArtifact != null && footerLinkArtifact.text() != null && !footerLinkArtifact.text().isBlank()) {
                        float footerLinkY = Math.max(26.0f, marginBottom * 0.96f);
                        drawArtifactLink(doc, page, contentStream, artifactRuntime, footerLinkArtifact, footerLinkY);
                    }

                    if (artifactFooterPattern != null && !artifactFooterPattern.isBlank()) {
                        String footerText = formatArtifactFooter(artifactFooterPattern, pageIndex + 1, totalPages);
                        float footerY = Math.max(8.0f, marginBottom * 0.45f);
                        drawArtifactAlignedText(
                                contentStream,
                                artifactRuntime,
                                footerText,
                                9.0f,
                                footerY,
                                resolvePageNumberAlignment(pageIndex + 1));
                    }

                    contentStream.endMarkedContent();
                }
            }
        }

        private FontRuntime resolveArtifactFontRuntime(Map<String, FontRuntime> fontRuntimes) {
            FontRuntime defaultRuntime = fontRuntimes.getOrDefault("default", null);
            A11yFontFamily defaultFamily = fontFamilies.get("default");
            if (defaultRuntime != null && defaultFamily != null && !defaultFamily.regular().isStandard14()) {
                return defaultRuntime;
            }

            for (Map.Entry<String, A11yFontFamily> entry : fontFamilies.entrySet()) {
                A11yFontFamily family = entry.getValue();
                if (family == null || family.regular().isStandard14()) {
                    continue;
                }
                FontRuntime runtime = fontRuntimes.get(entry.getKey());
                if (runtime != null) {
                    return runtime;
                }
            }

            return defaultRuntime;
        }

        private void drawArtifactDecorations(PDPageContentStream contentStream) throws IOException {
            contentStream.setLineWidth(0.6f);
            contentStream.setStrokingColor(170.0f / 255.0f, 170.0f / 255.0f, 170.0f / 255.0f);
            float topY = pageHeight - Math.max(14.0f, marginTop * 0.9f);
            float bottomY = Math.max(14.0f, marginBottom * 0.9f);
            contentStream.moveTo(marginLeft, topY);
            contentStream.lineTo(pageWidth - marginRight, topY);
            contentStream.moveTo(marginLeft, bottomY);
            contentStream.lineTo(pageWidth - marginRight, bottomY);
            contentStream.stroke();
            contentStream.setStrokingColor(0.0f, 0.0f, 0.0f);
        }

        private ChromeAlignment resolvePageNumberAlignment(int pageNumber) {
            return switch (pageNumberAlignment) {
                case CENTER -> ChromeAlignment.CENTER;
                case RIGHT -> ChromeAlignment.RIGHT;
                case ALTERNATE -> (pageNumber % 2 == 0) ? ChromeAlignment.LEFT : ChromeAlignment.RIGHT;
            };
        }

        private void drawArtifactAlignedText(
                PDPageContentStream contentStream,
                FontRuntime fontRuntime,
                String text,
                float fontSize,
                float y,
                ChromeAlignment alignment) throws IOException {
            if (text == null || text.isBlank()) {
                return;
            }
            float textWidth = measureChunkedTextWidth(fontRuntime, FontVariant.REGULAR, fontSize, text);
            float x = resolveAlignedX(textWidth, alignment);
            float cursorX = x;
            for (FontRuntime.FontChunk chunk : fontRuntime.chunkText(text, FontVariant.REGULAR)) {
                if (chunk.text().isEmpty()) {
                    continue;
                }
                PDFont font = chunk.font();
                contentStream.beginText();
                contentStream.setFont(font, fontSize);
                contentStream.newLineAtOffset(cursorX, y);
                contentStream.showText(chunk.text());
                contentStream.endText();
                cursorX += font.getStringWidth(chunk.text()) / 1000.0f * fontSize;
            }
        }

        private void drawArtifactLink(
                PDDocument doc,
                PDPage page,
                PDPageContentStream contentStream,
                FontRuntime fontRuntime,
                ArtifactLink link,
                float y) throws IOException {
            float textWidth = measureChunkedTextWidth(fontRuntime, FontVariant.REGULAR, link.fontSize(), link.text());
            float x = resolveAlignedX(textWidth, link.alignment());
            drawArtifactAlignedText(contentStream, fontRuntime, link.text(), link.fontSize(), y, link.alignment());
            if (link.url() != null && !link.url().isBlank()) {
                addLinkAnnotation(page, x, y - 2.0f, textWidth, link.fontSize() + 4.0f, link.url());
            }
        }

        private void drawArtifactImage(
                PDDocument doc,
                PDPage page,
                PDPageContentStream contentStream,
                ArtifactImage image,
                boolean headerRegion) throws IOException {
            if (image.pathOrId() == null || image.pathOrId().isBlank()) {
                return;
            }
            PDImageXObject imageObject = loadFigureImage(doc, image.pathOrId());
            if (imageObject == null) {
                return;
            }
            float imageWidth = Math.max(1.0f, image.widthPt());
            float imageHeight = Math.max(1.0f, image.heightPt());
            float x = resolveAlignedX(imageWidth, image.alignment());
            float y = headerRegion
                    ? pageHeight - Math.max(24.0f, marginTop) - imageHeight
                    : Math.max(24.0f, marginBottom * 0.9f);
            contentStream.drawImage(imageObject, x, y, imageWidth, imageHeight);
            if (image.linkUrl() != null && !image.linkUrl().isBlank()) {
                addLinkAnnotation(page, x, y, imageWidth, imageHeight, image.linkUrl());
            }
        }

        private void addLinkAnnotation(PDPage page, float x, float y, float width, float height, String url) throws IOException {
            PDAnnotationLink link = new PDAnnotationLink();
            PDRectangle rect = new PDRectangle();
            rect.setLowerLeftX(x);
            rect.setLowerLeftY(y);
            rect.setUpperRightX(x + Math.max(1.0f, width));
            rect.setUpperRightY(y + Math.max(1.0f, height));
            link.setRectangle(rect);

            PDBorderStyleDictionary border = new PDBorderStyleDictionary();
            border.setWidth(0);
            link.setBorderStyle(border);

            PDActionURI action = new PDActionURI();
            action.setURI(url);
            link.setAction(action);
            page.getAnnotations().add(link);
        }

        private float resolveAlignedX(float elementWidth, ChromeAlignment alignment) {
            return switch (alignment == null ? ChromeAlignment.CENTER : alignment) {
                case LEFT -> marginLeft;
                case CENTER -> (pageWidth - elementWidth) / 2.0f;
                case RIGHT -> pageWidth - marginRight - elementWidth;
            };
        }

        private float measureChunkedTextWidth(FontRuntime fontRuntime, FontVariant variant, float fontSize, String text) throws IOException {
            float width = 0.0f;
            for (FontRuntime.FontChunk chunk : fontRuntime.chunkText(text, variant)) {
                if (chunk.text().isEmpty()) {
                    continue;
                }
                width += chunk.font().getStringWidth(chunk.text()) / 1000.0f * fontSize;
            }
            return width;
        }

        private String formatArtifactFooter(String pattern, int pageNumber, int totalPages) {
            try {
                return String.format(Locale.ROOT, pattern, pageNumber, totalPages);
            } catch (IllegalFormatException ex) {
                return pattern;
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

        private record TocEntry(int level, String text, int sourceElementIndex, int pageIndex) {
        }
    }

    public static final class ListBuilder {
        private final Builder parent;
        private final ListBlock block;
        private ListItem lastItem;

        private ListBuilder(Builder parent, ListBlock block) {
            this.parent = parent;
            this.block = block;
        }

        public ListBuilder item(String text) {
            ListItem item = new ListItem(text == null ? "" : text);
            block.items.add(item);
            lastItem = item;
            return this;
        }

        public ListBuilder beginNestedUnorderedList(
                BoxModel boxModel,
                TextStyle style,
                ListIndentStyle indentStyle,
                float customIndentPt,
                ListBulletStyle bulletStyle,
                String customMarker) {
            ensureLastItemForNesting();
            ListBuilder nestedBuilder = parent.list(false, 1, boxModel, style, indentStyle, customIndentPt, bulletStyle, customMarker);
            parent.elements.remove(parent.elements.size() - 1);
            lastItem.nestedList = nestedBuilder.block;
            return nestedBuilder;
        }

        public ListBuilder beginNestedOrderedList(
                int start,
                BoxModel boxModel,
                TextStyle style,
                ListIndentStyle indentStyle,
                float customIndentPt,
                ListBulletStyle bulletStyle,
                String customMarker) {
            ensureLastItemForNesting();
            ListBuilder nestedBuilder = parent.list(true, start, boxModel, style, indentStyle, customIndentPt, bulletStyle, customMarker);
            parent.elements.remove(parent.elements.size() - 1);
            lastItem.nestedList = nestedBuilder.block;
            return nestedBuilder;
        }

        private void ensureLastItemForNesting() {
            if (lastItem == null) {
                throw new ValidationException("Nested lists require at least one parent list item");
            }
            if (lastItem.nestedList != null) {
                throw new ValidationException("Parent list item already contains a nested list");
            }
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
        private final boolean includeInToc;

        private Heading(int level, String text, BoxModel boxModel, float lineHeightMultiplier, TextStyle style, boolean includeInToc) {
            this.level = level;
            this.text = text;
            this.boxModel = boxModel;
            this.lineHeightMultiplier = lineHeightMultiplier;
            this.style = style;
            this.includeInToc = includeInToc;
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
        private final String pathOrId;
        private final String altText;
        private final boolean decorative;
        private final FigureFlowMode flowMode;

        private Figure(String pathOrId, String altText, boolean decorative, FigureFlowMode flowMode) {
            this.pathOrId = pathOrId;
            this.altText = altText;
            this.decorative = decorative;
            this.flowMode = flowMode;
        }
    }

    private static final class ListBlock implements Element {
        private final BoxModel boxModel;
        private final TextStyle style;
        private ListIndentStyle indentStyle;
        private float customIndentPt;
        private final boolean ordered;
        private final int start;
        private final ListBulletStyle bulletStyle;
        private final String customMarker;
        private final List<ListItem> items = new ArrayList<>();

        private ListBlock(
                BoxModel boxModel,
                TextStyle style,
                ListIndentStyle indentStyle,
                float customIndentPt,
                boolean ordered,
                int start,
                ListBulletStyle bulletStyle,
                String customMarker) {
            this.boxModel = boxModel;
            this.style = style;
            this.indentStyle = indentStyle;
            this.customIndentPt = customIndentPt;
            this.ordered = ordered;
            this.start = start;
            this.bulletStyle = bulletStyle;
            this.customMarker = customMarker;
        }
    }

    private static final class ListItem {
        private final String text;
        private ListBlock nestedList;

        private ListItem(String text) {
            this.text = text;
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
        private final TocItemMode itemMode;
        private final boolean showPageNumbers;

        private TocBlock(String title, int maxDepth, TocItemMode itemMode, boolean showPageNumbers) {
            this.title = title;
            this.maxDepth = maxDepth;
            this.itemMode = itemMode == null ? TocItemMode.TEXT : itemMode;
            this.showPageNumbers = showPageNumbers;
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
        private final TextAlignment textAlignment;

        private TextStyle(String fontFamilyKey, FontVariant variant, TextAlignment textAlignment) {
            this.fontFamilyKey = fontFamilyKey;
            this.variant = variant;
            this.textAlignment = textAlignment == null ? TextAlignment.LEFT : textAlignment;
        }

        public static TextStyle of(String fontFamilyKey, FontVariant variant) {
            return new TextStyle(fontFamilyKey, variant, TextAlignment.LEFT);
        }

        public static TextStyle of(String fontFamilyKey, FontVariant variant, TextAlignment textAlignment) {
            return new TextStyle(fontFamilyKey, variant, textAlignment);
        }

        public static TextStyle none() {
            return new TextStyle(null, null, TextAlignment.LEFT);
        }

        public String fontFamilyKey() {
            return fontFamilyKey;
        }

        public FontVariant variant() {
            return variant;
        }

        public TextAlignment textAlignment() {
            return textAlignment;
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

    private record ListFlowState(PDPage page, float y) {
    }

    private record RenderCursor(PDPage page, float y) {
    }

    private record MarkedContentRecord(int elementIndex, int itemSlot, PDPage page, int mcid) {
    }

    private record TableSlotPlan(List<Integer> headerCellSlots, List<List<Integer>> bodyRowCellSlots) {
    }

    private record TocSlotPlan(List<Integer> referenceSlots) {
    }

    private record TocPageSpan(int elementIndex, int pageCount) {
    }

        private record TocLinkAnnotationPlan(
            PDPage page,
            float x,
            float y,
            float width,
            float height,
            int targetPageNumber,
            int elementIndex,
            int referenceSlot) {
        }

        private record TocLinkSlotKey(int elementIndex, int referenceSlot) {
    }

    private record ListItemSlotPlan(int labelSlot, int bodySlot) {
    }

    private record ArtifactText(String text, ChromeAlignment alignment, float fontSize) {
    }

    private record ArtifactLink(String text, String url, ChromeAlignment alignment, float fontSize) {
    }

    private record ArtifactImage(String pathOrId, ChromeAlignment alignment, float widthPt, float heightPt, String linkUrl) {
    }

        private record FigureRenderPlan(
            PDImageXObject image,
            float imageWidth,
            float imageHeight,
            String label,
            float captionHeight,
            float totalHeight) {
        }
}
