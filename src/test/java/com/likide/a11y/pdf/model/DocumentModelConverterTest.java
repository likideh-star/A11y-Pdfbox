package com.likide.a11y.pdf.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

        IntermediateDocument converted = A11yPdfDocument.fromDeclarative(declarative).toIntermediateModel();

        assertEquals(fluent, converted);
    }
}
