# Milestone Progress Tracker

Last updated: 2026-06-04
Reference source: [task.md](task.md)

## Status Legend

- Done: exit criteria mostly satisfied based on current implementation/tests.
- Partially Done: foundational pieces exist, but key scope/deliverables are missing.
- Not Done: milestone scope is not implemented yet.

## Overall Snapshot

- Done: 7
- Partially Done: 5
- Not Done: 3

## Detailed Milestone Status

| Milestone | Title                                                 | Status         | Notes                                                                                                                                                                                                                                                                                                     |
| --------- | ----------------------------------------------------- | -------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1         | Core Architecture and Build Baseline                  | Done           | Package ownership boundaries are explicitly defined, base exception hierarchy is implemented, Maven toolchain gates were added, README now documents architecture and local workflow, and default `mvn test` passes with a direct smoke test (no adapter shortcut required).                              |
| 2         | Unified Public API and Intermediate Document Model    | Done           | Fluent builder parity is now complete for table/TOC/custom node families, declarative/fluent conversion converges on the same intermediate model for all currently supported nodes, and equivalence/materialization tests pass in the default `mvn test` run.                                             |
| 3         | Preflight Validation and Compliance Rules             | Done           | Central preflight validation now executes before rendering with structured rule codes and node context; fatal vs warning behavior is explicit, and failure-path tests are in place for metadata, heading hierarchy, image alt-text, list/table composition, Unicode readiness, and TOC reference checks.  |
| 4         | Metadata, Catalog Setup, and Tagged PDF Skeleton      | Done           | /Lang, title, /DisplayDocTitle, /Tabs /S, XMP pdfUA marker, and structure root are implemented and test-covered.                                                                                                                                                                                          |
| 5         | Pass 1 Layout Engine for Text Flow                    | Done           | Pass-1 analyzer for heading/paragraph flow is implemented with box-model resolution, line-height wrapping, paragraph height, keep-with-next, and deterministic `LayoutBlueprint` output; text-only rendering now reuses blueprint decisions without re-measure, and tests cover the full pass-1 contract. |
| 6         | Multi-Column Layout Engine                            | Done           | Multi-column cursor progression and overflow across columns/pages are implemented with direct test coverage, and `LayoutBlueprint` now provides diagnostics traces for placement/advance events to support edge-case debugging and verification of transition behavior.                                   |
| 7         | Pass 2 Renderer and MCID Infrastructure               | Done           | Pass-2 text replay from `LayoutBlueprint` is implemented and covered, with MCID allocation and marked-content bindings validated by dedicated tests.                                                                                                                                                      |
| 8         | Font Families, Cascading Resolution, and Fallback     | Done           | Full style cascade is implemented (node -> parent -> document default), with expanded declarative/intermediate style fields, registered family resolution, variant fallback, and end-to-end multilingual fallback coverage using a real Unicode TTF path in integration tests.                            |
| 9         | Headings and Paragraphs Production-Ready              | Not Done       | Basic structure tagging exists, but production-grade layout/rendering behavior is not complete.                                                                                                                                                                                                           |
| 10        | Lists and Nested List Semantics                       | Partially Done | List rendering now supports unordered and ordered markers (including declarative `ordered` + `start`), configurable continuation indentation modes (`ALIGN_WITH_BULLET`, `TWO_SPACE`, `CUSTOM`), and long-item wrapping/pagination hardening; nested `L/LI/Lbl/LBody` structures are still pending.      |
| 11        | Tables with Semantic Headers and Splitting            | Partially Done | Declarative/fluent table model conversion, visual rendering with borders, row pagination across pages, repeated headers on continued pages, and basic table tagging are implemented; deeper semantic header association and full production hardening are still pending.                                  |
| 12        | Figures and Image Flow Modes                          | Partially Done | Figure tagging + alt-text validation exist; inline/span-all-columns image flow/scaling rules are missing.                                                                                                                                                                                                 |
| 13        | TOC and Structural References                         | Not Done       | TOC generation and structural referencing are not implemented yet.                                                                                                                                                                                                                                        |
| 14        | Artifact Handling and Page Chrome                     | Partially Done | Artifact marked-content marker exists; full header/footer/page-number rendering support is not complete.                                                                                                                                                                                                  |
| 15        | External Compliance Verification and Regression Suite | Partially Done | Internal JUnit checks exist; external validator integration (e.g., veraPDF) and broader regression corpus are missing.                                                                                                                                                                                    |
| 16        | Performance, Hardening, and Documentation             | Not Done       | No benchmarking/stress suite/contributor-focused hardening artifacts yet.                                                                                                                                                                                                                                 |

## Verification Notes

- Default test run (`mvn test`) succeeds without adapter wiring shortcuts.
- Adapter-backed run also succeeds explicitly using:
  - `mvn "-Da11y.pdf.adapter=com.likide.spec.PlannedFluentApiPdfLibraryAdapter" test`
