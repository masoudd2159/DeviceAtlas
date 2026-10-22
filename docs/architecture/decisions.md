# Phase 1 architecture decisions

## ADR-001: package boundaries before Gradle modularization

Accepted. One UI module and one existing Rust crate are sufficient. Feature
modules would add build/plugin surfaces without an independent build or API
consumer. Pure contracts stay in `foundation`, adapters in `data`, UI in
`presentation`. Revisit modules when a real reuse/build-isolation need appears.

## ADR-002: Rust computational authority, Android presentation ownership

Accepted. Preserve the real mean and JNI protocol. Kotlin acquires future Android
inputs and presents typed results. No speculative computational APIs, duplicated
algorithms or generic repository hierarchy. Native verification is entry-scoped.

## ADR-003: official per-app locales

Accepted. Migrate ComponentActivity to AppCompatActivity and use AppCompat
auto-storage below API 33, framework locale management above it. This handles
older Android correctly and avoids competing locale preferences. Existing backup
exclusions remain. AppCompat's small synchronous locale storage is the official
compatibility tradeoff; no custom wrapper is introduced.

## ADR-004: Navigation 3 and official adaptive navigation

Accepted. Stable 1.2.0 and existing Lifecycle 2.11.0 are compatible with the pinned
toolchain. Serializable keys, per-tab official back stacks, entry saveable/VM
decorators and a small event policy supply restoration and exit-through-Overview.
Material's navigation suite owns width/posture decisions. No Navigation 2 or
custom navigation engine is needed.

## ADR-005: small theme preference adapter

Accepted. One private SharedPreferences enum key is sufficient; no database or
additional persistence framework is justified. Reads and synchronous durable
writes run on injected IO, serialized by the Activity-scoped SettingsViewModel.
Failed writes retain the prior UI theme and expose retry. No sensitive data is
stored, and all existing backup exclusions cover the preference.
