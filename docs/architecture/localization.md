# Localization

All visible Phase 1 text comes from `res/values/strings.xml` and
`res/values-fa/strings.xml`. Technical constants such as the app version are not
translations. `LocalizationTest` compares all keys, duplicates, nonempty values,
format arguments and Persian kaf/yeh forms. Run with `:app:testDebugUnitTest`.

## One locale owner

`MainActivity` extends Hilt-enabled `AppCompatActivity`, retaining Compose
`setContent`, lifecycle collection and edge-to-edge. Its window theme is
`Theme.AppCompat.DayNight.NoActionBar` in both day/night resources, satisfying
AppCompat's activity requirement. The Compose theme remains Material 3.

Language actions use `AppCompatDelegate.setApplicationLocales` on Main. On API
26–32, AppCompat auto-storage persists the selected locale and wraps the Activity
context. On API 33+, it delegates to framework per-app locale settings and syncs
with the system language picker. `locales_config.xml` advertises `en` and `fa`.
The metadata service is disabled and non-exported, exactly as the
[Android locale guidance](https://developer.android.com/guide/topics/resources/app-languages)
requires. No separate language key or custom context wrapper exists.

Empty locale tags mean Follow system. English/Persian tags mean explicit
selection. Activity recreation applies the resources, and saved navigation
restores the current Language page. Unsupported system languages use the English
default resources. Regional English/Persian variants use their language fallback.

AppCompat's official opt-in auto-storage may perform small locale disk reads on
the main thread below API 33; this is its documented tradeoff. Theme disk IO is
separately off-main. Locale files and theme preferences remain covered by the
existing no-backup/cloud/device-transfer exclusions. Android framework/OEM locale
persistence follows the platform policy; the app adds no sync or telemetry.

## Direction and adding a language

Persian inherits RTL from localized Activity resources and Compose configuration.
Layout uses logical start/end, Material's directional navigation, and mirrored
Back/forward vectors. Only technical text values set LTR. Localized numerical
status formatting uses `stringResource` with the current resource locale;
fixed test inputs are explicitly translated. Persian copy uses Persian letters,
punctuation and half-spaces; version strings preserve technical Latin digits.

To add a third language: create its complete `values-<tag>/strings.xml`, add the
locale to `locales_config.xml` and `AppLanguage`/label mapping, extend completeness
and runtime direction/switching tests, and verify native names/mixed text. Never
put translations in Rust. Update tests rather than relying on fallback to conceal
missing supported-language resources.
