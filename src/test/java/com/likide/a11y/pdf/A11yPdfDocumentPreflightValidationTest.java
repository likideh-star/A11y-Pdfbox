package com.likide.a11y.pdf;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.likide.a11y.pdf.validation.ValidationException;

class A11yPdfDocumentPreflightValidationTest {

    @Test
    void buildBytes_shouldFailWhenTitleIsBlank() {
        ValidationException error = assertThrows(
                ValidationException.class,
                () -> A11yPdfDocument.builder()
                        .lang("en-US")
                        .title(" ")
                        .paragraph("content")
                        .buildBytes());

        assertTrue(error.getMessage().contains("DOC_TITLE_REQUIRED"));
    }

    @Test
    void buildBytes_shouldFailForEmptyList() {
        ValidationException error = assertThrows(
                ValidationException.class,
                () -> A11yPdfDocument.builder()
                        .unorderedList()
                        .endList()
                        .buildBytes());

        assertTrue(error.getMessage().contains("LIST_EMPTY"));
    }

    @Test
    void buildBytes_shouldFailForTableRowColumnMismatch() {
        ValidationException error = assertThrows(
                ValidationException.class,
                () -> A11yPdfDocument.builder()
                        .table()
                        .headerCell("A")
                        .headerCell("B")
                        .row().cell("x").endRow()
                        .endTable()
                        .buildBytes());

        assertTrue(error.getMessage().contains("TABLE_ROW_COLUMN_MISMATCH"));
    }

    @Test
    void buildBytes_shouldFailWhenTocHasNoHeadingReferences() {
        ValidationException error = assertThrows(
                ValidationException.class,
                () -> A11yPdfDocument.builder()
                        .tableOfContents("Contents", 3)
                        .paragraph("Only body text")
                        .buildBytes());

        assertTrue(error.getMessage().contains("TOC_NO_REFERENCES"));
    }

    @Test
    void buildBytes_shouldFailWhenUnicodeCannotBeMappedToDefaultFont() {
        ValidationException error = assertThrows(
                ValidationException.class,
                () -> A11yPdfDocument.builder()
                        .heading(1, "English heading")
                        .paragraph("Unicode sample: 你好")
                        .buildBytes());

        assertTrue(error.getMessage().contains("FONT_UNICODE_UNSUPPORTED"));
    }

    @Test
    void buildBytes_shouldCollectNonFatalWarnings() {
        A11yPdfDocument.Builder builder = A11yPdfDocument.builder()
                .table()
                .row().cell("a").endRow()
                .endTable();

        byte[] pdf = builder.buildBytes();

        assertTrue(pdf.length > 0);
        assertFalse(builder.preflightWarnings().isEmpty());
        assertTrue(builder.preflightWarnings().get(0).contains("TABLE_HEADER_MISSING"));
    }
}
