# Navigation and adaptive shell

Navigation 3 runtime/UI **1.2.0** is the stable release verified against the
[official release notes](https://developer.android.com/jetpack/androidx/releases/navigation3).
Lifecycle navigation integration uses the existing **2.11.0** pin; AndroidX Hilt
Compose **1.4.0** provides entry-scoped injection without Navigation 2. Dagger
Hilt remains **2.60.1**. All resolve through the existing Google/Maven repositories.

`Destination` is a serializable `NavKey` enum. The serialization compiler plugin
matches Kotlin **2.4.21**. `rememberNavBackStack` owns saved stacks for Overview,
Device, Sensors, Diagnostics and Settings. Language, Appearance and About are
Settings children. No deep links or external route strings are introduced.

`AppNavigationState` centralizes selection and Back policy. Selecting the current
tab is idempotent. Every tab preserves its own stack; switching away from a
Settings subsection and back returns to it. A subsection pops to Settings; a
top-level Back selects Overview; Back from Overview exits normally. `NavDisplay`
owns system/predictive Back. Its active entries include Overview followed by the
selected stack, following the official
[multiple-stack recipe](https://github.com/android/nav3-recipes/tree/main/app/src/main/java/com/example/nav3recipes/multiplestacks).

All stacks are decorated while composed, even when inactive. Each uses the
official saveable-state-holder and ViewModel-store decorators. Scroll state,
serialized stack keys and selected tab survive saved-state recreation. Native
verification belongs to the Diagnostics entry, not the Activity. There is no JNI
effect keyed to recomposition or duplicate navigation collector.

Material 3 `NavigationSuiteScaffold` (BOM-managed) selects navigation bar/rail from
current window information, including posture and compact height. There is one
primary navigation control at a time. Compact-height landscape/tabletop can use
the bar per the library's official policy. Content is centered with an 840 dp
maximum width; screens scroll and use start/end layout. No orientation or model
heuristics exist. Navigation 3's default transitions respect system animation
duration settings; no separate nonessential animation is added.

To add a feature: introduce a typed key and title/icon resources, add content to
the centralized entry provider, establish an injected capability only if needed,
and extend destination/Back/restoration tests. Never add fabricated metrics to
structural screens.
