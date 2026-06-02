package com.likide.a11y.pdf.model;

import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.common.PDRectangle;

import com.likide.a11y.pdf.validation.ValidationException;

/**
 * Conversion layer that normalizes fluent and declarative inputs
 * into the same intermediate model.
 */
public final class DocumentModelConverter {

    private static final String DEFAULT_LANG = "en-US";
    private static final String DEFAULT_TITLE = "Untitled";
    private static final float DEFAULT_MARGIN = 72.0f;

    private DocumentModelConverter() {
    }

    public static IntermediateDocument fromFluent(FluentDocumentSnapshot snapshot) {
        List<IntermediateNode> nodes = new ArrayList<>();
        for (FluentNode node : snapshot.nodes()) {
            if (node instanceof FluentHeadingNode heading) {
                nodes.add(new IntermediateHeading(
                        heading.level(),
                        heading.text(),
                        heading.style(),
                        new SemanticMetadata("H" + heading.level())));
            } else if (node instanceof FluentParagraphNode paragraph) {
                nodes.add(new IntermediateParagraph(
                        paragraph.text(),
                        paragraph.style(),
                        new SemanticMetadata("P")));
            } else if (node instanceof FluentFigureNode figure) {
                nodes.add(new IntermediateFigure(
                        figure.pathOrId(),
                        figure.altText(),
                        figure.decorative(),
                        new SemanticMetadata("Figure")));
            } else if (node instanceof FluentListNode list) {
                nodes.add(new IntermediateList(List.copyOf(list.items()), new SemanticMetadata("L")));
            }
        }
        return new IntermediateDocument(
                snapshot.lang(),
                snapshot.title(),
                snapshot.displayDocTitle(),
                snapshot.pageSettings(),
                List.copyOf(nodes));
    }

    public static IntermediateDocument fromDeclarative(DeclarativeDocument document) {
        if (document == null) {
            throw new ValidationException("declarative document must not be null");
        }

        String lang = isBlank(document.lang) ? DEFAULT_LANG : document.lang;
        String title = isBlank(document.title) ? DEFAULT_TITLE : document.title;
        boolean displayDocTitle = document.displayDocTitle == null || document.displayDocTitle;
        IntermediatePageSettings pageSettings = resolvePageSettings(document.page);

        List<IntermediateNode> nodes = new ArrayList<>();
        for (DeclarativeNode node : document.nodes) {
            if (node == null) {
                continue;
            }
            nodes.add(convertNode(node));
        }

        return new IntermediateDocument(lang, title, displayDocTitle, pageSettings, List.copyOf(nodes));
    }

    private static IntermediatePageSettings resolvePageSettings(DeclarativePageSettings page) {
        if (page == null) {
            return new IntermediatePageSettings(
                    1,
                    0.0f,
                    PDRectangle.A4.getWidth(),
                    PDRectangle.A4.getHeight(),
                    DEFAULT_MARGIN,
                    DEFAULT_MARGIN,
                    DEFAULT_MARGIN,
                    DEFAULT_MARGIN);
        }

        int columns = page.columns == null ? 1 : page.columns;
        float columnGap = page.columnGap == null ? 0.0f : page.columnGap;
        float pageWidth = page.pageWidth == null ? PDRectangle.A4.getWidth() : page.pageWidth;
        float pageHeight = page.pageHeight == null ? PDRectangle.A4.getHeight() : page.pageHeight;
        float marginTop = page.marginTop == null ? DEFAULT_MARGIN : page.marginTop;
        float marginRight = page.marginRight == null ? DEFAULT_MARGIN : page.marginRight;
        float marginBottom = page.marginBottom == null ? DEFAULT_MARGIN : page.marginBottom;
        float marginLeft = page.marginLeft == null ? DEFAULT_MARGIN : page.marginLeft;

        if (columns < 1) {
            throw new ValidationException("columns must be >= 1");
        }
        if (pageWidth <= 0.0f || pageHeight <= 0.0f) {
            throw new ValidationException("page size must be > 0");
        }

        return new IntermediatePageSettings(columns, columnGap, pageWidth, pageHeight, marginTop, marginRight, marginBottom, marginLeft);
    }

    private static IntermediateNode convertNode(DeclarativeNode node) {
        if (node instanceof DeclarativeHeading heading) {
            int level = heading.level == null ? 1 : heading.level;
            if (level < 1 || level > 6) {
                throw new ValidationException("heading level must be between 1 and 6");
            }
            return new IntermediateHeading(
                    level,
                    nullToEmpty(heading.text),
                    resolveStyle(heading.style, heading.boxModel),
                    new SemanticMetadata("H" + level));
        }
        if (node instanceof DeclarativeParagraph paragraph) {
            return new IntermediateParagraph(
                    nullToEmpty(paragraph.text),
                    resolveStyle(paragraph.style, paragraph.boxModel),
                    new SemanticMetadata("P"));
        }
        if (node instanceof DeclarativeFigure figure) {
            boolean decorative = figure.decorative != null && figure.decorative;
            if (!decorative && isBlank(figure.altText)) {
                throw new ValidationException("Image requires altText unless decorative=true");
            }
            return new IntermediateFigure(
                    nullToEmpty(figure.pathOrId),
                    figure.altText,
                    decorative,
                    new SemanticMetadata("Figure"));
        }
        if (node instanceof DeclarativeList list) {
            return new IntermediateList(List.copyOf(list.items), new SemanticMetadata("L"));
        }
        throw new ValidationException("Unsupported declarative node type: " + node.getClass().getName());
    }

    private static IntermediateTextStyle resolveStyle(DeclarativeTextStyle style, DeclarativeBoxModel boxModel) {
        float lineHeight = (style == null || style.lineHeightMultiplier == null) ? 1.2f : style.lineHeightMultiplier;
        if (lineHeight <= 0.0f) {
            throw new ValidationException("lineHeight multiplier must be > 0");
        }

        if (boxModel == null) {
            return new IntermediateTextStyle(lineHeight, new IntermediateBoxModel(0, 0, 0, 0, 0, 0));
        }

        return new IntermediateTextStyle(
                lineHeight,
                new IntermediateBoxModel(
                        nullAsZero(boxModel.marginTop),
                        nullAsZero(boxModel.paddingTop),
                        nullAsZero(boxModel.paddingRight),
                        nullAsZero(boxModel.paddingBottom),
                        nullAsZero(boxModel.paddingLeft),
                        nullAsZero(boxModel.marginBottom)));
    }

    private static float nullAsZero(Float value) {
        return value == null ? 0.0f : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
