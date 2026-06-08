# A11y PDFBox Library

## Milestone 1 Baseline

The current baseline establishes a buildable, testable foundation for further milestone work.

### Architecture Boundaries

Core package ownership is now explicit:

1. `com.likide.a11y.pdf.api` for public-facing API and base exception types.
2. `com.likide.a11y.pdf.model` for intermediate document model contracts.
3. `com.likide.a11y.pdf.layout` for pass-1 flow and pagination planning.
4. `com.likide.a11y.pdf.rendering` for pass-2 content stream rendering concerns.
5. `com.likide.a11y.pdf.tagging` for tagged PDF structure mapping.
6. `com.likide.a11y.pdf.metadata` for catalog and XMP compliance metadata.
7. `com.likide.a11y.pdf.validation` for preflight and constraints validation.
8. `com.likide.a11y.pdf.fonts` for font and glyph resolution strategy.
9. `com.likide.a11y.pdf.testing` for test-only helpers.

### Error Model

Library failures use a common runtime hierarchy rooted at `A11yPdfException` with dedicated types for validation, rendering, metadata compliance, and font resolution.

### Local Build and Test Workflow

1. Run all tests with defaults:

```bash
mvn test
```

2. Run tests with an explicit adapter override (optional):

```bash
mvn "-Da11y.pdf.adapter=com.likide.spec.PlannedFluentApiPdfLibraryAdapter" test
```

3. Generate the visual smoke PDF sample:

```bash
mvn -q -DskipTests compile org.codehaus.mojo:exec-maven-plugin:3.3.0:java "-Dexec.mainClass=com.likide.Main"
```

Output path:

`target/visual-check.pdf`

---

# Product Requirements Document (PRD) & Technical Specification

This is the comprehensive, unified Product Requirements Document (PRD) and Technical Feature Specification for your Java-based, PDF/UA-compliant accessible PDF generation library built on Apache PDFBox 3.0.x.
(beispiel: https://github.com/martinlovell/accessible-pdfbox-example/tree/main)

## 1. Project Overview & Guiding Principle

The objective of this library is to provide a semantically driven layout engine on top of Apache PDFBox 3.0.x. Because PDFBox treats a document as a purely visual, absolute-positioned canvas, this library abstracts the low-level coordinate calculations and handles both visual layout flow and the simultaneous injection of the Tagged PDF logical structure tree (PDStructureTreeRoot). The final output must pass automated machine accessibility checkers (e.g., veraPDF Engine, Adobe Acrobat Pro Accessibility Check) and comply with strict PDF/UA-1 (ISO 14289-1) criteria.

## 2. Core Architectural & Engine Requirements

### 2.1 Dual-Pass Execution Model

To manage dynamic sizing, column breaks, and structural tag mapping without breaking PDFBox sequential writing streams, the layout engine must implement a strict dual-pass lifecycle:

- Pass 1 (Sizing, Flow & Constraints Analysis): Traverses the input Document Model tree, computes text wrapping based on active boundary widths, evaluates padding/margins, tracks column/page overflows, resolves heights, and constructs the structural node topology in memory.
- Pass 2 (Binary Generation & Structural Stitching): Iterates over the structured layout blueprint, draws text, background shapes, or imagery into the PDPageContentStream, wraps layout elements in Marked Content blocks (BDC/EMC operators), and maps them back to the semantic structure tree via incremental Marked Content IDs (MCID).

### 2.2 API Paradigms

The engine must support two public-facing input interfaces concurrently, feeding into the exact same intermediate object model representation:

- Fluent Builder API: A type-safe, readable Java builder interface using lambda configuration blocks for document orchestration.
- Declarative Data Model: A clean, un-opinionated Java Object Model fully compatible with standard serialization frameworks (Jackson, Gson) for generating PDFs from JSON or YAML inputs.

---

### 3. Layout Model Requirements## 3.1 Advanced CSS-Style Box Model

Every block-level element must support a subset of the standard box model framework to establish structural whitespace and visual containers:

- Padding Matrix: Elements accept explicit paddingTop, paddingRight, paddingBottom, and paddingLeft variables. Padding reduces the internal space available for text wrapping or child rendering, and defines the outer fill bounds for backgrounds/borders.
- Margin-Bottom: Elements accept a marginBottom property in typographic points. This defines the minimum dead space before the next element's boundary starts.
- Line-Height: Text blocks accept a custom lineHeight multiplier (default 1.2f), which scales the vertical distance between lines mathematically: $\text{Spacing} = \text{Font Size} \times \text{lineHeight}$.

### 3.2 Multi-Column Layout Architecture

- Column Grid Metrics: Pages must accept configuration inputs for an arbitrary column count ($N$) and a specific column gap size in points.
- Tracking Cursors: The layout engine tracks execution through a contextual status keeping tabs on currentColumnIndex, currentColumnX, and currentY.
- Overflow Break Handling: When content exceeds the bottom page margin bounds:

1. The engine advances the cursor to the top margin of the next logical column:

$X_{\text{next}} = \text{Left Margin} + \text{Column Index} \times (\text{Column Width} + \text{Column Gap})$.

2. If all columns on the active page are filled, the engine generates a new PDPage instance, registers structural properties, enforces tab ordering, and resets the cursor to column index 0.

---

## 4. Typography & Font Management Requirements## 4.1 Cascading Font Resolution Hierarchy

To allow localized typographic styling, font assignments cascade down the DOM hierarchy:

1.  Element Overrides: Check if a custom font family is assigned to the current active element. If null, check recursively up through its parent elements (e.g., Cell $\rightarrow$ Row $\rightarrow$ Table).
2.  Global Base Fallback: If no component has an explicit font mapping, adopt the document's globally configured primary font.

### 4.2 Font Family Wrapper & Styling Variants

To guarantee valid PDF/UA text-to-glyph mappings, procedural transformation matrix overrides (such as faux-bold or faux-italic font rendering via code stretching) are strictly forbidden.

- The library must introduce an A11yFontFamily asset container that maps to four discrete, embedded TrueType or OpenType font files: REGULAR, BOLD, ITALIC, and BOLD_ITALIC (PDType0Font).
- Elements toggle these via boolean flags (.bold(true) / .italic(true)), forcing the engine to resolve the exact physical font file required. If a specific variant file is missing, the engine gracefully falls back to the REGULAR font file and flags a system warning.

### 4.3 Automated Font Fallback & Glyph Chunking Engine

To fulfill PDF/UA Unicode mapping specifications when using localized text or symbols missing from primary fonts:

- Before writing text strings to a content stream, the layout processor must check character codes against the resolved active font via PDFont.hasGlyph(int codePoint).
- If code points are missing, the engine triggers an automatic search loop across an ordered, globally registered array of fallback fonts.
- Dynamic Slicing: The engine must segment mixed strings into individual, single-font character blocks on the fly, tracking widths precisely to prevent glyph overlapping.
- Unified Semantic Tagging: To keep screen-reader audio playback smooth, text strings fragmented across multiple font files due to missing glyphs must still be wrapped within a single semantic MCID block rather than breaking into separate structural elements.

---

## 5. Component Feature & Tag Matrix Specification

The engine must translate structural definitions into the following explicit PDF/UA Standard Structure Elements:

| Component Type      | High-Level Element Tag | PDFBox StandardStructureTypes Mapping | Structural Layout & Multi-Column Rules                                                                                                  |
| ------------------- | ---------------------- | ------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------- |
| Headings            | H1 to H6               | H1 through H6                         | Applies "Keep-with-next" validation constraints. Headings are prohibited from orphaning at the base of columns or pages.                |
| Paragraphs          | Paragraph              | P                                     | Breaks text across columns and pages using standard line wrap logic based on bounding box width.                                        |
| Tables              | Table                  | TABLE                                 | Scales cells proportionally to fit column boundaries. Breaks rows across columns/pages cleanly.                                         |
| Table Head          | THead                  | THEAD                                 | Outer container wrapping header rows. Automatically clones headers visually to the top of subsequent columns/pages if the table splits. |
| Table Body          | TBody                  | TBODY                                 | Structural grid parent wrapping content entries.                                                                                        |
| Table Row           | TR                     | TR                                    | Container for dynamic table cell groupings.                                                                                             |
| Table Header / Data | TH / TD                | TH / TD                               | Content container wrapping layout elements. Cell heights expand dynamically to fit their contents.                                      |
| List Group Root     | UL / OL                | L                                     | Manages list structures. Increments structural left-indentation parameters dynamically.                                                 |
| List Container      | LI                     | LI                                    | Structural composite block. Accepts plain text runs or recursive child elements—including deeply nested sub-lists.                      |
| List Label          | Lbl                    | LBL                                   | Visual indicator block holding bullet markers or auto-number strings.                                                                   |
| List Content Body   | LBody                  | LBODY                                 | Structural text area or the parent block hosting a nested sub-list element.                                                             |
| Images / Figures    | Figure                 | FIGURE                                | Structural wrapper containing graphical assets. Requires mandatory alternate description metadata.                                      |

---

## 6. Layout-Specific Structural Mechanics## 6.1 Unordered List Bullet Variations

The library must support distinct visual bullet markers for the /Lbl element block based on style configurations, using specific Unicode mappings:

- DISC $\rightarrow$ Solid Circle (\u2022 / •)
- CIRCLE $\rightarrow$ Hollow Circle (\u25CB / ○)
- SQUARE $\rightarrow$ Solid Square (\u25A0 / ■)
- DASH $\rightarrow$ Em Dash (\u2014 / —)
- CUSTOM $\rightarrow$ Developer-supplied glyph parameters (e.g., ✔, →).

The bullet marker resides within the /Lbl structure block, while the list text resides in the /LBody block. Both are contained within the parent /LI node. The body content is shifted horizontally within the column by:

$\text{Content Position } X = \text{Current } X + \text{paddingLeft} + \text{bulletWidth}$

### 6.2 Image Grid Flow Interactions

Images (Figure elements) within multi-column grids must support two explicit structural behaviors:

1.  Inline Column Containment (ImageFlow.INLINE): The image behaves like a standard block-level element locked inside the active column width. If the asset width ($W_{\text{img}}$) exceeds the column width ($W_{\text{col}}$), it scales down proportionally:

$\text{Scale Ratio} = \frac{W_{\text{col}}}{W_{\text{img}}}$

If the scaled height exceeds the remaining column depth, the entire image block moves to the top of the next column or page. 2. Full Page Width Span (ImageFlow.SPAN_ALL_COLUMNS): The image breaks out of the column system to span the entire page width (minus page margins). The layout engine pauses content rendering in all active columns, balances them horizontally, embeds the full-width image, and then resets the multi-column cursor below the image to resume column flow.

### 6.3 Automated Pagination & Document Summaries

- Artifact Isolation: All structural background geometry, page boundaries, header/footer elements, and dynamic page number strings ("Page X of Y") must be explicitly tagged as /Artifact content blocks. This ensures screen readers ignore them during reading sequences.
- Table of Contents (TOC): Document summaries must map to a parent TOC structure containing structural TOCI (TOC Item) nodes. These items must link back to their target components using structural references (/Ref), and the pages must enforce proper tab ordering (/Tabs /S).

---

## 7. Global Configuration & Metadata Requirements

To ensure complete compliance with PDF/UA machine parsing constraints, the .build() lifecycle must validate and inject the following properties before saving:

- Natural Document Language: Must set the language code configuration (e.g., en-US, de-DE) directly into the catalog root metadata array (/Lang).
- Explicit Title Preference: Must establish a document title property inside the PDF metadata catalog, setting the viewer preference parameter /DisplayDocTitle true.
- Document Tab Ordering: Must programmatically force a structural tab routing command (/Tabs /S) onto every newly instantiated PDPage object to align physical keyboard navigation with the document structure tree.
- PDF/UA Schema Identification Flag: Must embed a compliant, valid XMP metadata stream containing the official PDF/UA designation identifier: pdfUAid:part="1".
- Compilation Fault Safeguards: The .build() pipeline must throw an explicit exception and halt compilation if:
- An image lack alternative text (altText) and is not explicitly set as decorative.
  - Heading levels skip hierarchically (e.g., an H3 is added directly beneath an H1 without an intermediate H2).
  - A custom font lack valid embedded Unicode translation mapping tables (/ToUnicode CMap tables).

---

## Proposing Next Steps

To begin developing this architecture, would you like to check a concrete code implementation blueprint for a core layout coordinator showing how a two-column grid measures text and creates structural element nodes under these box-model rules?
