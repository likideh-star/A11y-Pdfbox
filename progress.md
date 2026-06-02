# Milestone Progress Tracker

Last updated: 2026-06-02
Reference source: [task.md](task.md)

## Status Legend

- Done: exit criteria mostly satisfied based on current implementation/tests.
- Partially Done: foundational pieces exist, but key scope/deliverables are missing.
- Not Done: milestone scope is not implemented yet.

## Overall Snapshot

- Done: 1
- Partially Done: 8
- Not Done: 7

## Detailed Milestone Status

| Milestone | Title                                                 | Status         | Notes                                                                                                                   |
| --------- | ----------------------------------------------------- | -------------- | ----------------------------------------------------------------------------------------------------------------------- |
| 1         | Core Architecture and Build Baseline                  | Partially Done | Maven/JUnit baseline and smoke-like checks exist, but full package boundaries and exception hierarchy are not complete. |
| 2         | Unified Public API and Intermediate Document Model    | Partially Done | Minimal fluent API exists; declarative model + unified intermediate tree conversion are missing.                        |
| 3         | Preflight Validation and Compliance Rules             | Partially Done | Heading hierarchy and image alt-text validation are implemented; broader validation pipeline is missing.                |
| 4         | Metadata, Catalog Setup, and Tagged PDF Skeleton      | Done           | /Lang, title, /DisplayDocTitle, /Tabs /S, XMP pdfUA marker, and structure root are implemented and test-covered.        |
| 5         | Pass 1 Layout Engine for Text Flow                    | Not Done       | No pass-1 measurement/wrapping/blueprint layout engine implemented yet.                                                 |
| 6         | Multi-Column Layout Engine                            | Not Done       | Column configuration fields exist, but no multi-column overflow/cursor flow logic yet.                                  |
| 7         | Pass 2 Renderer and MCID Infrastructure               | Not Done       | No pass-2 renderer replaying layout blueprint and no MCID mapping infrastructure yet.                                   |
| 8         | Font Families, Cascading Resolution, and Fallback     | Not Done       | No font family registry/cascade, variant fallback policy, or glyph chunking implementation yet.                         |
| 9         | Headings and Paragraphs Production-Ready              | Not Done       | Basic structure tagging exists, but production-grade layout/rendering behavior is not complete.                         |
| 10        | Lists and Nested List Semantics                       | Partially Done | L/LI/Lbl/LBody tags are emitted; nested semantics, style variants, and robust layout behavior are missing.              |
| 11        | Tables with Semantic Headers and Splitting            | Not Done       | No table model/layout/rendering/tagging pipeline implemented yet.                                                       |
| 12        | Figures and Image Flow Modes                          | Partially Done | Figure tagging + alt-text validation exist; inline/span-all-columns image flow/scaling rules are missing.               |
| 13        | TOC and Structural References                         | Not Done       | TOC generation and structural referencing are not implemented yet.                                                      |
| 14        | Artifact Handling and Page Chrome                     | Partially Done | Artifact marked-content marker exists; full header/footer/page-number rendering support is not complete.                |
| 15        | External Compliance Verification and Regression Suite | Partially Done | Internal JUnit checks exist; external validator integration (e.g., veraPDF) and broader regression corpus are missing.  |
| 16        | Performance, Hardening, and Documentation             | Not Done       | No benchmarking/stress suite/contributor-focused hardening artifacts yet.                                               |

## Verification Notes

- Default test run (`mvn test`) currently skips adapter-dependent tests because the default adapter is intentionally unimplemented.
- Full adapter-backed run succeeds using:
  - `mvn "-Da11y.pdf.adapter=com.likide.spec.PlannedFluentApiPdfLibraryAdapter" test`

## Next Recommended Milestone

- Milestone 5: Implement pass-1 text layout blueprint for headings/paragraphs (wrapping, line-height, keep-with-next, pagination decisions), then expose deterministic data for a future pass-2 renderer.
