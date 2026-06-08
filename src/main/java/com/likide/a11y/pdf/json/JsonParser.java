package com.likide.a11y.pdf.json;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.likide.a11y.pdf.model.DeclarativeBoxModel;
import com.likide.a11y.pdf.model.DeclarativeChromeImage;
import com.likide.a11y.pdf.model.DeclarativeChromeLink;
import com.likide.a11y.pdf.model.DeclarativeChromeText;
import com.likide.a11y.pdf.model.DeclarativeCustomNode;
import com.likide.a11y.pdf.model.DeclarativeDocument;
import com.likide.a11y.pdf.model.DeclarativeFigure;
import com.likide.a11y.pdf.model.DeclarativeFontConfig;
import com.likide.a11y.pdf.model.DeclarativeHeading;
import com.likide.a11y.pdf.model.DeclarativeList;
import com.likide.a11y.pdf.model.DeclarativeListItem;
import com.likide.a11y.pdf.model.DeclarativeNode;
import com.likide.a11y.pdf.model.DeclarativePageChrome;
import com.likide.a11y.pdf.model.DeclarativePageNumber;
import com.likide.a11y.pdf.model.DeclarativePageSettings;
import com.likide.a11y.pdf.model.DeclarativeParagraph;
import com.likide.a11y.pdf.model.DeclarativeSection;
import com.likide.a11y.pdf.model.DeclarativeSemanticMetadata;
import com.likide.a11y.pdf.model.DeclarativeTable;
import com.likide.a11y.pdf.model.DeclarativeTableRow;
import com.likide.a11y.pdf.model.DeclarativeTextStyle;
import com.likide.a11y.pdf.model.DeclarativeToc;

/**
 * Parses a JSON file into a {@link DeclarativeDocument}.
 *
 * <p>Recognised node discriminators (checked in order):
 * <ol>
 *   <li>{@code "level"} present → {@link DeclarativeHeading}</li>
 *   <li>{@code "pathOrId"}, {@code "altText"}, or {@code "decorative"} present → {@link DeclarativeFigure}</li>
 *   <li>{@code "items"} present → {@link DeclarativeList}</li>
 *   <li>{@code "headerCells"} or {@code "rows"} present → {@link DeclarativeTable}</li>
 *   <li>{@code "family"} or {@code "type"} present → {@link DeclarativeCustomNode}</li>
 *   <li>{@code "maxDepth"} present, or {@code "title"} present without {@code "text"} → {@link DeclarativeToc}</li>
 *   <li>{@code "text"} present → {@link DeclarativeParagraph}</li>
 * </ol>
 */
public final class JsonParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonParser() {
    }

    /**
     * Parse the JSON file at the given path into a {@link DeclarativeDocument}.
     *
     * @param path path to a UTF-8 JSON file
     * @return the parsed document
     * @throws IOException if the file cannot be read or the JSON is malformed
     */
    public static DeclarativeDocument parse(Path path) throws IOException {
        return fromNode(MAPPER.readTree(path.toFile()));
    }

    /**
     * Parse a JSON document from the given input stream into a {@link DeclarativeDocument}.
     * The stream is not closed by this method.
     *
     * @param in the input stream containing UTF-8 JSON
     * @return the parsed document
     * @throws IOException if the stream cannot be read or the JSON is malformed
     */
    public static DeclarativeDocument parse(InputStream in) throws IOException {
        return fromNode(MAPPER.readTree(in));
    }

    // -------------------------------------------------------------------------
    // Document
    // -------------------------------------------------------------------------

    private static DeclarativeDocument fromNode(JsonNode root) {
        DeclarativeDocument doc = new DeclarativeDocument();
        doc.lang = text(root, "lang");
        doc.title = text(root, "title");
        doc.displayDocTitle = bool(root, "displayDocTitle");

        JsonNode pageNode = root.path("page");
        if (pageNode.isObject()) {
            doc.page = parsePageSettings(pageNode);
        }

        JsonNode fontsNode = root.path("fonts");
        if (fontsNode.isObject()) {
            fontsNode.fieldNames().forEachRemaining(key -> {
                JsonNode familyNode = fontsNode.path(key);
                if (familyNode.isObject()) {
                    DeclarativeFontConfig cfg = new DeclarativeFontConfig();
                    cfg.regular = text(familyNode, "regular");
                    cfg.bold = text(familyNode, "bold");
                    cfg.italic = text(familyNode, "italic");
                    cfg.boldItalic = text(familyNode, "boldItalic");
                    doc.fonts.put(key, cfg);
                }
            });
        }

        JsonNode pageChromeNode = root.path("pageChrome");
        if (pageChromeNode.isObject()) {
            doc.pageChrome = parsePageChrome(pageChromeNode);
        }

        JsonNode nodes = root.path("nodes");
        if (!nodes.isArray()) {
            throw new JsonParseException("JSON must contain an array field 'nodes'");
        }
        for (JsonNode node : nodes) {
            DeclarativeNode parsed = parseNode(node);
            if (parsed instanceof DeclarativeToc toc
                    && toc.titleLevel != null
                    && toc.titleLevel > 0
                    && toc.title != null
                    && !toc.title.isBlank()) {
                DeclarativeHeading titleHeading = new DeclarativeHeading();
                titleHeading.level = toc.titleLevel;
                titleHeading.text = toc.title;
                titleHeading.semantic = new DeclarativeSemanticMetadata();
                titleHeading.semantic.roleHint = "toc-title";
                titleHeading.semantic.nodeFamily = "toc";
                doc.nodes.add(titleHeading);
                toc.title = "";
            }
            doc.nodes.add(parsed);
        }

        return doc;
    }

    // -------------------------------------------------------------------------
    // Page settings
    // -------------------------------------------------------------------------

    private static DeclarativePageSettings parsePageSettings(JsonNode node) {
        DeclarativePageSettings page = new DeclarativePageSettings();
        page.columns = integer(node, "columns");
        page.columnGap = floating(node, "columnGap");
        page.pageWidth = floating(node, "pageWidth");
        page.pageHeight = floating(node, "pageHeight");
        page.marginTop = floating(node, "marginTop");
        page.marginRight = floating(node, "marginRight");
        page.marginBottom = floating(node, "marginBottom");
        page.marginLeft = floating(node, "marginLeft");
        page.contentPaddingTop = floating(node, "contentPaddingTop");
        page.contentPaddingRight = floating(node, "contentPaddingRight");
        page.contentPaddingBottom = floating(node, "contentPaddingBottom");
        page.contentPaddingLeft = floating(node, "contentPaddingLeft");
        return page;
    }

    // -------------------------------------------------------------------------
    // Nodes — discriminated by field presence
    // -------------------------------------------------------------------------

    private static DeclarativeNode parseNode(JsonNode node) {
        if (node.has("level")) {
            return parseHeading(node);
        }
        if (node.has("pathOrId") || node.has("altText") || node.has("decorative")) {
            return parseFigure(node);
        }
        if (node.has("items")) {
            return parseList(node);
        }
        if (node.has("headerCells") || node.has("rows")) {
            return parseTable(node);
        }
        if (node.has("family") || node.has("type")) {
            return parseCustomNode(node);
        }
        if (node.has("section")) {
            return parseSection(node.path("section"));
        }
        if (node.has("maxDepth") || (node.has("title") && !node.has("text"))) {
            return parseToc(node);
        }
        if (node.has("text")) {
            return parseParagraph(node);
        }
        throw new JsonParseException("Cannot identify declarative node type from JSON: " + node);
    }

    private static DeclarativeHeading parseHeading(JsonNode node) {
        DeclarativeHeading heading = new DeclarativeHeading();
        heading.level = integer(node, "level");
        heading.text = text(node, "text");
        heading.style = parseTextStyle(node.path("style"));
        heading.boxModel = parseBoxModel(node.path("boxModel"));
        heading.semantic = parseSemanticMetadata(node.path("semantic"));
        return heading;
    }

    private static DeclarativeParagraph parseParagraph(JsonNode node) {
        DeclarativeParagraph paragraph = new DeclarativeParagraph();
        paragraph.text = text(node, "text");
        paragraph.style = parseTextStyle(node.path("style"));
        paragraph.boxModel = parseBoxModel(node.path("boxModel"));
        paragraph.semantic = parseSemanticMetadata(node.path("semantic"));
        return paragraph;
    }

    private static DeclarativeFigure parseFigure(JsonNode node) {
        DeclarativeFigure figure = new DeclarativeFigure();
        figure.pathOrId = text(node, "pathOrId");
        figure.altText = text(node, "altText");
        figure.decorative = bool(node, "decorative");
        figure.flowMode = text(node, "flowMode");
        figure.semantic = parseSemanticMetadata(node.path("semantic"));
        return figure;
    }

    private static DeclarativeList parseList(JsonNode node) {
        DeclarativeList list = new DeclarativeList();
        JsonNode items = node.path("items");
        if (items.isArray()) {
            for (JsonNode item : items) {
                if (item.isObject()) {
                    DeclarativeListItem listItem = new DeclarativeListItem();
                    listItem.text = text(item, "text");
                    if (item.has("list")) {
                        listItem.nestedList = parseList(item.path("list"));
                    }
                    list.itemNodes.add(listItem);
                    list.items.add(listItem.text == null ? "" : listItem.text);
                } else {
                    String text = item.asText("");
                    list.items.add(text);
                    DeclarativeListItem listItem = new DeclarativeListItem();
                    listItem.text = text;
                    list.itemNodes.add(listItem);
                }
            }
        }
        list.ordered = bool(node, "ordered");
        list.start = integer(node, "start");
        list.bulletStyle = text(node, "bulletStyle");
        list.customMarker = text(node, "customMarker");
        list.style = parseTextStyle(node.path("style"));
        list.indentStyle = text(node, "indentStyle");
        list.customIndentPt = floating(node, "customIndentPt");
        list.boxModel = parseBoxModel(node.path("boxModel"));
        list.semantic = parseSemanticMetadata(node.path("semantic"));
        return list;
    }

    private static DeclarativeTable parseTable(JsonNode node) {
        DeclarativeTable table = new DeclarativeTable();
        JsonNode headerCells = node.path("headerCells");
        if (headerCells.isArray()) {
            for (JsonNode cell : headerCells) {
                table.headerCells.add(cell.asText(""));
            }
        }
        JsonNode rows = node.path("rows");
        if (rows.isArray()) {
            for (JsonNode rowNode : rows) {
                DeclarativeTableRow row = new DeclarativeTableRow();
                JsonNode cells = rowNode.path("cells");
                if (cells.isArray()) {
                    for (JsonNode cell : cells) {
                        row.cells.add(cell.asText(""));
                    }
                }
                table.rows.add(row);
            }
        }
        table.style = parseTextStyle(node.path("style"));
        table.boxModel = parseBoxModel(node.path("boxModel"));
        table.semantic = parseSemanticMetadata(node.path("semantic"));
        return table;
    }

    private static DeclarativeToc parseToc(JsonNode node) {
        DeclarativeToc toc = new DeclarativeToc();
        toc.title = text(node, "title");
        toc.titleLevel = integer(node, "titleLevel");
        toc.maxDepth = integer(node, "maxDepth");
        toc.itemMode = text(node, "itemMode");
        toc.showPageNumbers = bool(node, "showPageNumbers");
        toc.semantic = parseSemanticMetadata(node.path("semantic"));
        return toc;
    }

    private static DeclarativeCustomNode parseCustomNode(JsonNode node) {
        DeclarativeCustomNode custom = new DeclarativeCustomNode();
        custom.family = text(node, "family");
        custom.type = text(node, "type");
        JsonNode attributes = node.path("attributes");
        if (attributes.isObject()) {
            attributes.fieldNames().forEachRemaining(
                    key -> custom.attributes.put(key, attributes.path(key).asText("")));
        }
        custom.semantic = parseSemanticMetadata(node.path("semantic"));
        return custom;
    }

    private static DeclarativeSection parseSection(JsonNode node) {
        if (!node.isObject()) {
            throw new JsonParseException("Node field 'section' must be an object");
        }
        DeclarativeSection section = new DeclarativeSection();
        section.columns = integer(node, "columns");
        section.columnGap = floating(node, "columnGap");
        return section;
    }

    private static DeclarativePageChrome parsePageChrome(JsonNode node) {
        DeclarativePageChrome chrome = new DeclarativePageChrome();
        chrome.headerText = parseChromeText(node.path("headerText"));
        chrome.headerLink = parseChromeLink(node.path("headerLink"));
        chrome.headerImage = parseChromeImage(node.path("headerImage"));
        chrome.footerText = parseChromeText(node.path("footerText"));
        chrome.footerLink = parseChromeLink(node.path("footerLink"));
        chrome.footerImage = parseChromeImage(node.path("footerImage"));
        chrome.pageNumber = parsePageNumber(node.path("pageNumber"));
        return chrome;
    }

    private static DeclarativeChromeText parseChromeText(JsonNode node) {
        if (!node.isObject()) {
            return null;
        }
        DeclarativeChromeText chrome = new DeclarativeChromeText();
        chrome.text = text(node, "text");
        chrome.alignment = text(node, "alignment");
        chrome.fontSize = floating(node, "fontSize");
        return chrome;
    }

    private static DeclarativeChromeLink parseChromeLink(JsonNode node) {
        if (!node.isObject()) {
            return null;
        }
        DeclarativeChromeLink chrome = new DeclarativeChromeLink();
        chrome.text = text(node, "text");
        chrome.url = text(node, "url");
        chrome.alignment = text(node, "alignment");
        chrome.fontSize = floating(node, "fontSize");
        return chrome;
    }

    private static DeclarativeChromeImage parseChromeImage(JsonNode node) {
        if (!node.isObject()) {
            return null;
        }
        DeclarativeChromeImage chrome = new DeclarativeChromeImage();
        chrome.pathOrId = text(node, "pathOrId");
        chrome.widthPt = floating(node, "widthPt");
        chrome.heightPt = floating(node, "heightPt");
        chrome.alignment = text(node, "alignment");
        chrome.linkUrl = text(node, "linkUrl");
        return chrome;
    }

    private static DeclarativePageNumber parsePageNumber(JsonNode node) {
        if (!node.isObject()) {
            return null;
        }
        DeclarativePageNumber chrome = new DeclarativePageNumber();
        chrome.pattern = text(node, "pattern");
        chrome.alignment = text(node, "alignment");
        return chrome;
    }

    // -------------------------------------------------------------------------
    // Shared sub-objects
    // -------------------------------------------------------------------------

    private static DeclarativeTextStyle parseTextStyle(JsonNode node) {
        if (!node.isObject()) {
            return null;
        }
        DeclarativeTextStyle style = new DeclarativeTextStyle();
        style.lineHeightMultiplier = floating(node, "lineHeightMultiplier");
        style.fontFamily = text(node, "fontFamily");
        style.fontVariant = text(node, "fontVariant");
        style.alignment = text(node, "alignment");
        if (style.alignment == null) {
            style.alignment = text(node, "textAlign");
        }
        return style;
    }

    private static DeclarativeBoxModel parseBoxModel(JsonNode node) {
        if (!node.isObject()) {
            return null;
        }
        DeclarativeBoxModel box = new DeclarativeBoxModel();
        box.marginTop = floating(node, "marginTop");
        box.paddingTop = floating(node, "paddingTop");
        box.paddingRight = floating(node, "paddingRight");
        box.paddingBottom = floating(node, "paddingBottom");
        box.paddingLeft = floating(node, "paddingLeft");
        box.marginBottom = floating(node, "marginBottom");
        return box;
    }

    private static DeclarativeSemanticMetadata parseSemanticMetadata(JsonNode node) {
        if (!node.isObject()) {
            return null;
        }
        DeclarativeSemanticMetadata meta = new DeclarativeSemanticMetadata();
        meta.structureTag = text(node, "structureTag");
        meta.roleHint = text(node, "roleHint");
        meta.nodeFamily = text(node, "nodeFamily");
        return meta;
    }

    // -------------------------------------------------------------------------
    // Primitive helpers
    // -------------------------------------------------------------------------

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static Integer integer(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asInt();
    }

    private static Float floating(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : (float) value.asDouble();
    }

    private static Boolean bool(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asBoolean();
    }
}
