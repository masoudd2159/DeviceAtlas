# Phase 1 implementation and behavioral test plan

Retain `:app` and the pure-computation Rust crate. Establish package boundaries,
not empty feature modules. Existing `NativeMath`, error mapping, dispatcher and
JNI semantics already satisfy the Rust-first direction and remain intact.

1. Architecture: preserve all gateway/ViewModel numerical and cancellation tests.
   Native verification is instantiated only when Diagnostics is visited.
2. Design system: extend existing tokens and theme; use semantic surfaces, status
   labels, headings, information cards/rows and full-row selection targets.
   Verify fallback light/dark rendering and representative large fonts.
3. Localization: complete English/Persian resource sets; AppCompatActivity with
   AppCompat auto-storage below API 33 and framework locales above it. Test live
   switching, RTL, recreation, system mode and resource-key/format parity.
4. Navigation: Navigation 3 serializable keys, saved back stacks, saveable entry
   state and entry-scoped ViewModels. Test repeated selection, top-level return to
   Overview, nested About/Language/Theme Back, and saved-state restoration.
5. Shell: Overview, Device, Sensors, Diagnostics, Settings plus real subsections.
   Hardware pages state their unavailability. Preserve the known-input Rust test.
6. Adaptation: official Material navigation suite, readable bounded content,
   scrollable screens, start/end layout, targeted LTR technical values. Verify
   compact/expanded windows and font scale, heading/selection/live-region semantics.
7. Review/verification: JVM, connected, lint, debug/release/AAB, offline/cache,
   Rust format/Clippy/host and Android tests, four ABIs, ELF/ZIP alignment,
   merged-manifest permissions, exporter regression, graph refresh, Git hygiene.

Visual direction: an inspection workbench with a blue (`#315DA8`) identity,
slate text (`#191C20`), cool white surfaces (`#F9F9FF`), and semantic teal success
and amber caution. System sans supports both scripts; proportional UI text and
monospace only for actual technical values. The Overview introduction carries
identity; grouped action rows give the rest of the interface a quiet hierarchy.
No decorative charts, invented metrics or repeated dashboard-card grid.

Statuses and exact execution evidence belong in `docs/phase-1-report.md` after
verification. Phase 0 reports remain historical records.
