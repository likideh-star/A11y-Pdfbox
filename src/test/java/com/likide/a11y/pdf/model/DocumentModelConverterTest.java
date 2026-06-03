package com.likide.a11y.pdf.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.likide.a11y.pdf.A11yPdfDocument;
import com.likide.a11y.pdf.validation.ValidationException;

class DocumentModelConverterTest {

    @Test
    void declarativeConversion_shouldApplyDocumentDefaults() {
        DeclarativeDocument input = new DeclarativeDocument();
        DeclarativeParagraph paragraph = new DeclarativeParagraph();
        paragraph.text = "hello";
        input.nodes.add(paragraph);

        IntermediateDocument model = DocumentModelConverter.fromDeclarative(input);

        assertEquals("en-US", model.lang());
        assertEquals("Untitled", model.title());
        assertEquals(true, model.displayDocTitle());
        assertEquals(1, model.pageSettings().columns());
        assertEquals(1, model.nodes().size());
        IntermediateParagraph converted = (IntermediateParagraph) model.nodes().get(0);
        assertEquals(1.2f, converted.style().lineHeightMultiplier(), 0.0001f);
    }

    @Test
    void declarativeConversion_shouldRejectInvalidHeadingLevel() {
        DeclarativeDocument input = new DeclarativeDocument();
        DeclarativeHeading heading = new DeclarativeHeading();
        heading.level = 7;
        heading.text = "bad";
        input.nodes.add(heading);

        assertThrows(ValidationException.class, () -> DocumentModelConverter.fromDeclarative(input));
    }

    @Test
    void fluentAndDeclarative_shouldProduceEquivalentIntermediateModel() {
        A11yPdfDocument.BoxModel box = new A11yPdfDocument.BoxModel(1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f);

        IntermediateDocument fluent = A11yPdfDocument.builder()
                .lang("de-DE")
                .title("Equivalent")
                .displayDocTitle(false)
                .columns(2, 20.0f)
                .pageSize(500.0f, 700.0f)
                .pageMargins(10.0f, 11.0f, 12.0f, 13.0f)
                .heading(2, "Heading", box, 1.5f)
                .paragraph("Paragraph", box, 1.4f)
                .image("figure-id", "alt", false)
                .unorderedList()
                .item("one")
                .item("two")
                .endList()
                .table()
                .headerCell("Name")
                .headerCell("Value")
                .row()
                .cell("A")
                .cell("1")
                .endRow()
                .endTable()
                .tableOfContents("Overview", 3)
                .customNode("future.analytics", "KpiWidget")
                .attribute("metric", "views")
                .endCustomNode()
                .toIntermediateModel();

        DeclarativeDocument declarative = new DeclarativeDocument();
        declarative.lang = "de-DE";
        declarative.title = "Equivalent";
        declarative.displayDocTitle = false;
        declarative.page = new DeclarativePageSettings();
        declarative.page.columns = 2;
        declarative.page.columnGap = 20.0f;
        declarative.page.pageWidth = 500.0f;
        declarative.page.pageHeight = 700.0f;
        declarative.page.marginTop = 10.0f;
        declarative.page.marginRight = 11.0f;
        declarative.page.marginBottom = 12.0f;
        declarative.page.marginLeft = 13.0f;

        DeclarativeHeading heading = new DeclarativeHeading();
        heading.level = 2;
        heading.text = "Heading";
        heading.style = new DeclarativeTextStyle();
        heading.style.lineHeightMultiplier = 1.5f;
        heading.boxModel = new DeclarativeBoxModel();
        heading.boxModel.marginTop = 1.0f;
        heading.boxModel.paddingTop = 2.0f;
        heading.boxModel.paddingRight = 3.0f;
        heading.boxModel.paddingBottom = 4.0f;
        heading.boxModel.paddingLeft = 5.0f;
        heading.boxModel.marginBottom = 6.0f;
        declarative.nodes.add(heading);

        DeclarativeParagraph paragraph = new DeclarativeParagraph();
        paragraph.text = "Paragraph";
        paragraph.style = new DeclarativeTextStyle();
        paragraph.style.lineHeightMultiplier = 1.4f;
        paragraph.boxModel = new DeclarativeBoxModel();
        paragraph.boxModel.marginTop = 1.0f;
        paragraph.boxModel.paddingTop = 2.0f;
        paragraph.boxModel.paddingRight = 3.0f;
        paragraph.boxModel.paddingBottom = 4.0f;
        paragraph.boxModel.paddingLeft = 5.0f;
        paragraph.boxModel.marginBottom = 6.0f;
        declarative.nodes.add(paragraph);

        DeclarativeFigure figure = new DeclarativeFigure();
        figure.pathOrId = "figure-id";
        figure.altText = "alt";
        figure.decorative = false;
        declarative.nodes.add(figure);

        DeclarativeList list = new DeclarativeList();
        list.items.add("one");
        list.items.add("two");
        declarative.nodes.add(list);

        DeclarativeTable table = new DeclarativeTable();
        table.headerCells.add("Name");
        table.headerCells.add("Value");
        DeclarativeTableRow row = new DeclarativeTableRow();
        row.cells.add("A");
        row.cells.add("1");
        table.rows.add(row);
        declarative.nodes.add(table);

        DeclarativeToc toc = new DeclarativeToc();
        toc.title = "Overview";
        toc.maxDepth = 3;
        declarative.nodes.add(toc);

        DeclarativeCustomNode custom = new DeclarativeCustomNode();
        custom.family = "future.analytics";
        custom.type = "KpiWidget";
        custom.attributes.put("metric", "views");
        declarative.nodes.add(custom);

        IntermediateDocument converted = A11yPdfDocument.fromDeclarative(declarative).toIntermediateModel();

        assertEquals(fluent, converted);
    }

    @Test
    void declarativeConversion_shouldSupportTableTocAndCustomFamilies() {
        DeclarativeDocument input = new DeclarativeDocument();

        DeclarativeTable table = new DeclarativeTable();
        table.headerCells.add("Name");
        table.headerCells.add("Value");
        DeclarativeTableRow row = new DeclarativeTableRow();
        row.cells.add("A");
        row.cells.add("1");
        table.rows.add(row);
        table.semantic = new DeclarativeSemanticMetadata();
        table.semantic.roleHint = "data-grid";
        input.nodes.add(table);

        DeclarativeToc toc = new DeclarativeToc();
        toc.title = "Overview";
        toc.maxDepth = 3;
        input.nodes.add(toc);

        DeclarativeCustomNode custom = new DeclarativeCustomNode();
        custom.family = "future.analytics";
        custom.type = "KpiWidget";
        custom.attributes.put("metric", "views");
        custom.semantic = new DeclarativeSemanticMetadata();
        custom.semantic.structureTag = "Sect";
        input.nodes.add(custom);

        IntermediateDocument converted = DocumentModelConverter.fromDeclarative(input);

        assertEquals(3, converted.nodes().size());

        IntermediateTable convertedTable = (IntermediateTable) converted.nodes().get(0);
        assertEquals("Table", convertedTable.semantic().structureTag());
        assertEquals("data-grid", convertedTable.semantic().roleHint());
        assertEquals("table", convertedTable.semantic().nodeFamily());
        assertEquals(1, convertedTable.rows().size());

        IntermediateToc convertedToc = (IntermediateToc) converted.nodes().get(1);
        assertEquals(3, convertedToc.maxDepth());
        assertEquals("toc", convertedToc.semantic().nodeFamily());

        IntermediateCustomNode convertedCustom = (IntermediateCustomNode) converted.nodes().get(2);
        assertEquals("future.analytics", convertedCustom.family());
        assertEquals("KpiWidget", convertedCustom.type());
        assertEquals("Sect", convertedCustom.semantic().structureTag());
        assertEquals("future.analytics", convertedCustom.semantic().nodeFamily());
        assertEquals("views", convertedCustom.attributes().get("metric"));
    }

    @Test
    void fromDeclarativeBuilder_shouldMaterializeTableTocAndCustomNodes() {
        DeclarativeDocument input = new DeclarativeDocument();

        DeclarativeTable table = new DeclarativeTable();
        table.headerCells.add("Name");
        DeclarativeTableRow row = new DeclarativeTableRow();
        row.cells.add("A");
        table.rows.add(row);
        input.nodes.add(table);

        DeclarativeToc toc = new DeclarativeToc();
        toc.title = "Overview";
        toc.maxDepth = 2;
        input.nodes.add(toc);

        DeclarativeCustomNode custom = new DeclarativeCustomNode();
        custom.family = "future.analytics";
        custom.type = "KpiWidget";
        custom.attributes.put("metric", "views");
        input.nodes.add(custom);

        IntermediateDocument converted = A11yPdfDocument.fromDeclarative(input).toIntermediateModel();
        assertEquals(3, converted.nodes().size());
        assertTrue(converted.nodes().get(0) instanceof IntermediateTable);
        assertTrue(converted.nodes().get(1) instanceof IntermediateToc);
        assertTrue(converted.nodes().get(2) instanceof IntermediateCustomNode);
    }
}
