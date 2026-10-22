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
