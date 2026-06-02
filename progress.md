# Milestone Progress Tracker

Last updated: 2026-06-02
Reference source: [task.md](task.md)

## Status Legend

- Done: exit criteria mostly satisfied based on current implementation/tests.
- Partially Done: foundational pieces exist, but key scope/deliverables are missing.
- Not Done: milestone scope is not implemented yet.

## Overall Snapshot

- Done: 2
- Partially Done: 9
- Not Done: 5

## Detailed Milestone Status

| Milestone | Title                                                 | Status         | Notes                                                                                                                                                                                                                                                                                                                |
| --------- | ----------------------------------------------------- | -------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1         | Core Architecture and Build Baseline                  | Done           | Package ownership boundaries are explicitly defined, base exception hierarchy is implemented, Maven toolchain gates were added, README now documents architecture and local workflow, and default `mvn test` passes with a direct smoke test (no adapter shortcut required).                                         |
| 2         | Unified Public API and Intermediate Document Model    | Partially Done | Declarative POJOs, shared intermediate model records, and fluent/declarative conversion are implemented for heading, paragraph, list, figure, table, TOC, and custom future-family nodes, including richer semantic metadata (tag, role hint, family); full fluent materialization for table/TOC/custom remains pending. |
| 3         | Preflight Validation and Compliance Rules             | Partially Done | Heading hierarchy and image alt-text validation are implemented; broader validation pipeline is missing.                                                                                                                                                                                                             |
| 4         | Metadata, Catalog Setup, and Tagged PDF Skeleton      | Done           | /Lang, title, /DisplayDocTitle, /Tabs /S, XMP pdfUA marker, and structure root are implemented and test-covered.                                                                                                                                                                                                     |
| 5         | Pass 1 Layout Engine for Text Flow                    | Partially Done | First-pass blueprint now includes explicit heading/paragraph box-model fields (margin-top, paddings, margin-bottom), per-element lineHeight multipliers, content-box resolution, deterministic wrapping, keep-with-next handling, and verified pagination transitions; pass-2 rendering integration remains pending. |
| 6         | Multi-Column Layout Engine                            | Partially Done | Column width, cursor progression, and overflow from column-to-column then page rollover are implemented and test-covered for heading/paragraph blueprint flow; broader component coverage and diagnostics remain pending.                                                                                            |
| 7         | Pass 2 Renderer and MCID Infrastructure               | Not Done       | No pass-2 renderer replaying layout blueprint and no MCID mapping infrastructure yet.                                                                                                                                                                                                                                |
| 8         | Font Families, Cascading Resolution, and Fallback     | Not Done       | No font family registry/cascade, variant fallback policy, or glyph chunking implementation yet.                                                                                                                                                                                                                      |
| 9         | Headings and Paragraphs Production-Ready              | Not Done       | Basic structure tagging exists, but production-grade layout/rendering behavior is not complete.                                                                                                                                                                                                                      |
| 10        | Lists and Nested List Semantics                       | Partially Done | L/LI/Lbl/LBody tags are emitted; nested semantics, style variants, and robust layout behavior are missing.                                                                                                                                                                                                           |
| 11        | Tables with Semantic Headers and Splitting            | Not Done       | No table model/layout/rendering/tagging pipeline implemented yet.                                                                                                                                                                                                                                                    |
| 12        | Figures and Image Flow Modes                          | Partially Done | Figure tagging + alt-text validation exist; inline/span-all-columns image flow/scaling rules are missing.                                                                                                                                                                                                            |
| 13        | TOC and Structural References                         | Not Done       | TOC generation and structural referencing are not implemented yet.                                                                                                                                                                                                                                                   |
| 14        | Artifact Handling and Page Chrome                     | Partially Done | Artifact marked-content marker exists; full header/footer/page-number rendering support is not complete.                                                                                                                                                                                                             |
| 15        | External Compliance Verification and Regression Suite | Partially Done | Internal JUnit checks exist; external validator integration (e.g., veraPDF) and broader regression corpus are missing.                                                                                                                                                                                               |
| 16        | Performance, Hardening, and Documentation             | Not Done       | No benchmarking/stress suite/contributor-focused hardening artifacts yet.                                                                                                                                                                                                                                            |

## Verification Notes

- Default test run (`mvn test`) succeeds without adapter wiring shortcuts.
- Adapter-backed run also succeeds explicitly using:
  - `mvn "-Da11y.pdf.adapter=com.likide.spec.PlannedFluentApiPdfLibraryAdapter" test`

## Next Recommended Milestone

- Continue Milestone 2 and 6 in parallel: add fluent builder materialization for table/TOC/custom nodes while hardening multi-column edge cases before pass-2 renderer wiring.
