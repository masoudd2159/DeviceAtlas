# Design system

`presentation/theme` extends the Phase 0 theme rather than replacing it.
Material 3 light/dark semantic schemes retain the blue identity and define
secondary, surface containers and content colors. API 31+ Dynamic Color uses the
system palette; earlier APIs and previews use deterministic fallback schemes.
Semantic information, success, warning, error and unavailable tokens provide
on-container pairs. Status always includes text rather than relying on color.

Material typography supplies the full hierarchy with system sans for both
English and Persian; headline/body roles retain explicit Phase 0 sizing. Actual
technical values alone use monospace and targeted LTR text direction. No
downloaded fonts or network assets are needed.

`FoundationSpacing` contains the spacing scale (4/8/12/16/24/32 dp), 48 dp touch
minimum, icon sizes, bounded readable content width, and card elevation. Existing
8/16/24 dp shape tokens distinguish controls, groups and the Overview introduction.
Screen code consumes these tokens rather than scattering dimensions.

Shared components in `presentation/components` are used by the screens:

- `AtlasTopBar`, with localized Back and automatic vector mirroring.
- `ResponsiveContent`, with bounded width and saveable scroll state.
- `SectionHeader`, exposing accessibility heading semantics.
- `InformationCard` and `InformationRow`, including technical values.
- `StatusIndicator` and `AvailabilityMessage` for honest future-feature states.
- `ActionRow` for navigation and settings with full-row touch targets.
- `SelectionGroup` / `SettingsChoice`, with radio roles and selected semantics.
- `PreferenceError`, with live-region text and retry.

Native status also uses a polite live region. Decorative icons have null content
descriptions because visible row/navigation labels supply their names. Settings
choices are full-row radio controls with one accessibility action. Rows stack
label/value vertically to tolerate long translations and large fonts. Screens
scroll rather than use fixed content heights.

Previews cover English/Persian components, Overview phone/tablet, dark Settings,
and doubled font size. Real theme choice is read/written off-main by
`AndroidAppPreferences`; writes publish only after successful persistence. A failed
load/write exposes a recoverable error. Activity state survives configuration
changes; process restart reloads storage. System bars follow the selected theme
while preserving edge-to-edge content/insets.

## Review closure: theme effects and manual QA

The Activity's `SideEffect` is retained. It publishes the resolved dark/light
appearance after a successful composition; it is not an animation-frame loop.
Navigation state is read in the child shell, so changing a tab does not inherently
require an Activity-root recomposition. The platform calls are idempotent and no
measured or correctness defect justified a different effect lifecycle. Tests now
verify status/navigation bar icon contrast for Light/Dark/System and recreation
on API 26 and 33. Dynamic palette selection and surface/content contrast are
checked against the available platform palette (and the API 26 fallback), not
claimed for every possible wallpaper.

Preference retry now repeats the latest failed write instead of reloading the
previous theme. Pending choices, reads, writes and retries share the existing
mutex; cancelled work does not become an error. Read failures release startup
into a recoverable UI. No persistence framework or locale store was added.

The following manual checks remain **NOT RUN** and should be performed on the
corresponding real input/device environments:

| Check | Reproducible procedure |
| --- | --- |
| Spoken TalkBack traversal | In English and Persian, traverse headings, all navigation items, Settings rows and radio choices. Confirm one useful label/action, selected-state speech and logical order. Visit Diagnostics and check the polite status announcement. |
| Hardware keyboard | Tab/Shift-Tab through navigation, Back and Settings choices; activate with Enter/Space and confirm visible focus, selection and no focus traps. |
| Real predictive gesture | On a gesture-enabled supported device, partially swipe Back in a Settings child and cancel; confirm the child remains. Complete the gesture, then Back through Settings/Overview. |
| Physical foldable/tablet posture | Resize/fold/unfold with a nested Settings destination selected, in both languages and at 200% font. Confirm one navigation control, retained selection, readable scrollable content and correct insets. |
