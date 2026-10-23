# Phase 2 implementation and test design

Extend the existing single Android module and Rust crate, keeping navigation,
settings and the mean ABI intact. Order: contracts, native metrics, bounded JNI,
public platform collectors, shared foreground ViewModel, UI/localization,
regression checks, review, documentation and local commits.

Tests target numeric relationships and boundaries (including i64 maximum,
non-finite real inputs, invalid scale and signed current), exact JNI lengths and
version/status failures, per-field isolation and missing metadata, coroutine
cancellation and refresh deduplication. Instrumentation compares framework sources
with collected values rather than OEM constants; existing Phase 1 navigation,
locale, theme and native tests remain. Verify all four ABIs, lint, minification,
APK/AAB alignment and explicit API 26/API 33 runtimes. Treat unsupported OEM data
as unavailable; report test environment gaps separately from failures.
