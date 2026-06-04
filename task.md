# Milestone-Based Implementation Plan

This document converts the PRD into execution milestones with concrete deliverables, scope boundaries, and exit criteria for the accessible PDF library built on PDFBox 3.0.x.

## Current Status (2026-06-02)

1. Milestone 1: Done.
2. Milestone 2: Done.
3. Milestone 3: Done.
4. Milestone 4: Done.
5. Milestone 5: Done.
6. Milestone 6: Done.
7. Milestone 7: Done.
8. Milestone 8: Done.

### Latest Completed Work

1. Milestone 1 baseline completed: package ownership boundaries, exception hierarchy, Maven baseline gates, and README developer workflow are in place.
2. Default `mvn test` now passes without adapter shortcut assumptions, backed by a direct smoke test.
3. Milestone 2 foundation implemented: declarative POJOs, unified intermediate model, and conversion layer from fluent and declarative inputs.
4. Node/support coverage expanded for table, TOC, and custom future-family nodes with richer semantic metadata (`structureTag`, `roleHint`, `nodeFamily`).
5. Missing fluent builder APIs were implemented for table, TOC, and custom nodes, including materialization from declarative input.
6. Conversion tests now cover defaults, invalid input validation, fluent/declarative equivalence, broader node conversion, and positive fluent materialization checks for table/TOC/custom nodes.
7. Pass-1 layout blueprint supports explicit heading/paragraph box-model fields (`marginTop`, paddings, `marginBottom`).
8. Per-element `lineHeight` multipliers are implemented for headings and paragraphs.
9. Pagination flow is verified across column overflow and page rollover transitions for text blueprint blocks.
10. Milestone 8 foundation is implemented: document-level default font family registration, fallback font file registration, runtime variant resolution, and mixed-font chunked rendering.
11. Full font style cascade is implemented (node -> parent -> document default), including style propagation for heading/paragraph/list/table nodes.
12. Integration coverage now validates multilingual mixed-glyph rendering using a real Unicode TTF fallback path.
13. List continuation indentation is now configurable (align-with-bullet, two-space, custom), and long list-item wrapping/pagination is hardened.
14. Declarative visual example now includes an explicit ordered-list sample block for Milestone 10 validation scenarios.
15. Ordered list rendering is implemented end-to-end (declarative parser/model/converter -> renderer), including `start` index handling and numeric marker output.
16. Regression tests now verify ordered-list numeric markers for both fluent-builder and declarative entry paths.

### Next Focus

1. Continue Milestone 10 by implementing nested list semantics and full `L/LI/Lbl/LBody` structural behavior.

## Planning Principles

1. Every milestone must end in a buildable, testable state.
2. Both public APIs must converge on the same intermediate document model and rendering engine.
3. PDF/UA-related validation must be implemented as first-class build rules, not as optional warnings for critical failures.
4. Rendering features should only be added after the structure model and validation rules for that feature are defined.
5. Automated tests should be added alongside each milestone rather than postponed to the end.

## Milestone 1: Core Architecture and Build Baseline

### Goal

Establish the project structure, core modules, stable error model, and a minimal build pipeline that supports further implementation safely.

### Scope

1. Define package structure for API, model, layout, rendering, tagging, metadata, validation, fonts, and testing.
2. Replace the current ad hoc skeleton layout with stable internal boundaries.
3. Add Maven plugins and dependencies for testing and code quality.
4. Create exception types for validation, rendering, font, and compliance errors.
5. Establish developer documentation for local build and test workflow.

### Deliverables

1. Stable package and class layout committed in main source.
2. Base exception hierarchy for the library.
3. Maven build updated for test execution and future quality gates.
4. Repository README section describing implementation status and architecture overview.
5. Smoke tests proving the project builds and emits a minimal tagged PDF candidate.

### Exit Criteria

1. `mvn test` passes without adapter-only shortcuts for the implemented baseline.
2. The codebase has clear ownership boundaries between API, model, layout, and PDF output.

## Milestone 2: Unified Public API and Intermediate Document Model

### Goal

Define the long-term external API shape and the internal document model that both fluent and declarative inputs share.

### Scope

1. Finalize fluent builder entry points and nested builders.
2. Define the declarative Java model for serialization-based input.
3. Create the internal node model for document, heading, paragraph, list, table, image, TOC, and page settings.
4. Add style and semantic metadata containers.
5. Add conversion from public API input to the internal model.

### Deliverables

1. Stable fluent builder interfaces and builders for document composition.
2. Declarative POJOs matching the fluent API capability set.
3. Intermediate document model with structural and style fields.
4. Conversion layer from fluent and declarative inputs to the same model.
5. Unit tests covering default values, invalid input, and model conversion.

### Exit Criteria

1. The fluent and declarative APIs produce equivalent internal document trees.
2. Model nodes carry enough information to support layout and tagging without leaking PDFBox-specific details.

## Milestone 3: Preflight Validation and Compliance Rules

### Goal

Implement the hard validation rules required before PDF generation starts.

### Scope

1. Validate required document metadata such as language and title.
2. Enforce heading hierarchy rules.
3. Enforce image alt-text requirements for non-decorative figures.
4. Validate list and table semantic composition.
5. Validate font resources for Unicode mapping readiness.
6. Validate TOC references where applicable.

### Deliverables

1. Central validation pipeline executed before layout begins.
2. Structured validation error reporting with node context.
3. Tests for all critical failure paths described in the PRD.
4. Clear split between fatal validation failures and non-fatal warnings.

### Exit Criteria

1. Invalid input fails deterministically before rendering begins.
2. Validation errors identify both the failing rule and the failing node.

## Milestone 4: Metadata, Catalog Setup, and Tagged PDF Skeleton

### Goal

Produce a minimally compliant tagged PDF shell with correct catalog metadata and structure root setup.

### Scope

1. Set document language and title metadata.
2. Enable `DisplayDocTitle` in viewer preferences.
3. Apply `/Tabs /S` to all pages.
4. Embed XMP metadata containing `pdfUAid:part=1`.
5. Create the structure tree root and basic structure element mapping support.

### Deliverables

1. Metadata service for document catalog and XMP generation.
2. Page initialization code that applies tab ordering consistently.
3. Structure tree root initialization for generated documents.
4. Compliance tests that inspect saved PDFs for metadata and structure presence.

### Exit Criteria

1. A minimal document saves with required catalog metadata and structure root.
2. Existing PRD metadata tests pass without test-specific workarounds.

## Milestone 5: Pass 1 Layout Engine for Text Flow

### Goal

Implement the first-pass layout analyzer for headings and paragraphs, including box-model resolution and pagination decisions.

### Scope

1. Create the layout context and resolved layout boxes.
2. Compute content widths based on margins, padding, and active column width.
3. Implement line-height calculation and text wrapping.
4. Implement paragraph height calculation.
5. Add keep-with-next handling for headings.
6. Emit a reusable layout blueprint for rendering.

### Deliverables

1. Pass 1 layout analyzer for paragraphs and headings.
2. Layout blueprint model that records resolved positions and page or column assignments.
3. Tests for box model, line height, wrapping, and heading keep-with-next behavior.

### Exit Criteria

1. Layout decisions can be reproduced without re-measuring during rendering.
2. Headings and paragraphs can flow across pages safely.

## Milestone 6: Multi-Column Layout Engine

### Goal

Add multi-column flow behavior to the layout engine while preserving logical reading order.

### Scope

1. Implement column width and cursor calculations.
2. Move overflow from one column to the next.
3. Move overflow from the last column on a page to the first column on the next page.
4. Preserve logical structure ordering independent of visual positioning.
5. Validate cursor resets and page transitions.

### Deliverables

1. Multi-column cursor and overflow logic in pass 1.
2. Tests covering paragraphs and headings across columns and pages.
3. Debug-friendly layout diagnostics for column transitions.

### Exit Criteria

1. The PRD column advance formula is implemented and tested.
2. Text flow behaves correctly across both columns and pages.

## Milestone 7: Pass 2 Renderer and MCID Infrastructure

### Goal

Implement the second-pass renderer that replays the layout blueprint, writes visible content, and creates marked-content mappings.

### Scope

1. Create rendering abstractions around `PDPageContentStream`.
2. Render headings and paragraphs from the layout blueprint.
3. Add marked content blocks and MCID sequencing.
4. Connect layout nodes to structure elements.
5. Centralize coordinate and page-content stream management.

### Deliverables

1. Pass 2 renderer for text content.
2. MCID allocator and structure binding support.
3. Tests that inspect structure tree and marked-content relationships.

### Exit Criteria

1. Rendered text is visible and semantically mapped through structure tags and MCIDs.
2. The same layout blueprint is sufficient for deterministic rendering.

## Milestone 8: Font Families, Cascading Resolution, and Fallback

### Goal

Implement robust font handling required for accessible and multilingual text rendering.

### Scope

1. Implement `A11yFontFamily` with regular, bold, italic, and bold-italic variants.
2. Add document-level default font registration.
3. Implement cascading font resolution from element to parent to document default.
4. Add fallback behavior for missing style variants.
5. Add fallback font search for missing glyphs using `PDFont.hasGlyph(int)`.
6. Implement mixed-font chunking without breaking semantic continuity.

### Deliverables

1. Font family registry and resolution service.
2. Variant fallback and warning reporting.
3. Glyph fallback chunker with width-aware segmentation.
4. Tests for multilingual strings, symbols, and missing variants.

### Exit Criteria

1. Mixed-glyph text can render without glyph loss where fallback fonts are available.
2. Semantically contiguous text remains under one logical structure block.

## Milestone 9: Headings and Paragraphs Production-Ready

### Goal

Finish the first fully supported content types end to end.

### Scope

1. Complete rendering polish for headings and paragraphs.
2. Honor styling fields such as padding, margins, line height, and typography.
3. Ensure structure tags H1-H6 and P are emitted correctly.
4. Verify multi-page and multi-column continuity.

### Deliverables

1. Production-ready heading and paragraph pipeline.
2. End-to-end examples for article-style documents.
3. Regression tests for text layout, tagging, and validation.

### Exit Criteria

1. Headings and paragraphs can be considered stable library features.
2. The library can generate a structured text document that passes internal compliance checks.

## Milestone 10: Lists and Nested List Semantics

### Goal

Implement accessible list structures with correct visual and semantic behavior.

### Scope

1. Support unordered and ordered lists.
2. Implement separate `Lbl` and `LBody` structural content within `LI`.
3. Support nested lists recursively.
4. Support bullet styles `DISC`, `CIRCLE`, `SQUARE`, `DASH`, and `CUSTOM`.
5. Implement indentation and bullet width calculations.

### Deliverables

1. List model and renderer.
2. Nested list layout support.
3. Structure tree mapping for `L`, `LI`, `Lbl`, and `LBody`.
4. Tests for bullet style, indentation, nesting, and overflow.

### Exit Criteria

1. Lists render correctly and expose the right semantic tree.
2. Nested lists survive pagination and column transitions.

## Milestone 11: Tables with Semantic Headers and Splitting

### Goal

Implement accessible tables that can split across columns and pages while preserving semantics.

### Scope

1. Create table, head, body, row, header-cell, and data-cell model nodes.
2. Implement width distribution and scaling to fit column boundaries.
3. Implement dynamic cell height based on wrapped content.
4. Implement `THead`, `TBody`, `TR`, `TH`, and `TD` tags.
5. Implement row continuation and repeated visual headers.

### Deliverables

1. Table layout engine support.
2. Table renderer with semantic tagging.
3. Header repetition logic for split tables.
4. Tests for structure, wrapping, width resolution, and split behavior.

### Exit Criteria

1. Tables behave predictably across page and column boundaries.
2. Header semantics remain intact in the structure tree.

## Milestone 12: Figures and Image Flow Modes

### Goal

Implement image rendering with accessibility validation and both PRD flow behaviors.

### Scope

1. Support figure nodes with alt text and decorative mode.
2. Implement inline image flow constrained to the active column.
3. Implement proportional scaling when images exceed column width.
4. Defer oversized images to the next column or page if they do not fit vertically.
5. Implement span-all-columns image flow.

### Deliverables

1. Figure rendering support.
2. Image scaling and placement rules.
3. Figure semantic tagging and validation.
4. Tests for alt text enforcement, scaling, inline flow, and span-all-columns behavior.

### Exit Criteria

1. Image rendering obeys PRD flow rules.
2. Figures are tagged correctly and invalid figures fail preflight validation.

## Milestone 13: TOC and Structural References

### Goal

Generate a document table of contents with structural references to target content.

### Scope

1. Build TOC generation from heading nodes.
2. Implement `TOC` and `TOCI` structure elements.
3. Link TOC items to heading targets through destinations or structure references.
4. Ensure page tab ordering remains consistent.

### Deliverables

1. TOC model and generator.
2. Structural reference support for TOC items.
3. Tests for TOC content, references, and page navigation configuration.

### Exit Criteria

1. TOC items resolve correctly to their targets.
2. Generated TOC content participates in the logical structure as required.

## Milestone 14: Artifact Handling and Page Chrome

### Goal

Implement non-structural rendering for headers, footers, page numbers, and background page decorations.

### Scope

1. Create abstractions for page-level decorative content.
2. Wrap all such output in Artifact marked-content blocks.
3. Prevent artifact content from leaking into the structure tree.
4. Support page numbering patterns such as `Page X of Y`.

### Deliverables

1. Artifact rendering service.
2. Header, footer, and page-number support.
3. Tests inspecting decoded page streams for artifact markers.

### Exit Criteria

1. Decorative content is excluded from assistive reading order.
2. Page chrome works without corrupting structure tagging.

## Milestone 15: External Compliance Verification and Regression Suite

### Goal

Move from internal structural checks to repeatable compliance-oriented validation and broader regression coverage.

### Scope

1. Expand JUnit coverage across all supported components.
2. Add regression tests for every fatal validation rule.
3. Integrate veraPDF or an equivalent validator into CI.
4. Add stable sample documents for compliance inspection.
5. Create a manual Acrobat accessibility verification checklist.
6. Generate a release-ready compliance summary that captures test evidence, validator outcomes, known limitations, and open risks.

### Deliverables

1. Full PRD-oriented automated test suite.
2. External validation step integrated into CI or release workflow.
3. Compliance sample corpus.
4. Manual QA checklist for non-automated checks.
5. Compliance summary document for each release candidate.

### Exit Criteria

1. The library has repeatable automated evidence for core PDF/UA expectations.
2. Regression failures identify whether the problem is metadata, layout, tagging, or rendering.
3. A summary report is generated and attached for each release candidate, with pass/fail status and known gaps.

## Milestone 16: Performance, Hardening, and Documentation

### Goal

Stabilize the library for broader usage with performance work, robust examples, and contributor guidance.

### Scope

1. Profile large and complex documents.
2. Reduce repeated font loading and structure allocation overhead.
3. Add stress tests for deep nesting, large tables, and long text.
4. Write usage examples for fluent and declarative APIs.
5. Document limitations, guarantees, and extension points.

### Deliverables

1. Performance benchmark or profiling notes.
2. Stress-test suite.
3. Example documents and sample code.
4. Contributor documentation for safely adding new content types.

### Exit Criteria

1. The library performs acceptably on realistic multi-page documents.
2. New contributors can understand the rendering and tagging architecture without reverse engineering it.

## Recommended Delivery Order

1. Milestone 1
2. Milestone 2
3. Milestone 3
4. Milestone 4
5. Milestone 5
6. Milestone 6
7. Milestone 7
8. Milestone 8
9. Milestone 9
10. Milestone 10
11. Milestone 11
12. Milestone 12
13. Milestone 13
14. Milestone 14
15. Milestone 15
16. Milestone 16

## Suggested MVP Cut

If you need a first usable release before full PRD coverage, the MVP should include:

1. Milestone 1 through Milestone 9 in full.
2. Milestone 10 limited to unordered lists without deep nesting.
3. Milestone 12 limited to inline figures only.
4. Milestone 14 limited to artifact-tagged page numbering.
5. Milestone 15 limited to internal tests plus one external validation path.

This MVP would deliver semantically tagged text documents with metadata, headings, paragraphs, basic lists, inline figures, multi-column flow, and a credible validation baseline.
