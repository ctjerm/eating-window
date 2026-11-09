# Variable fonts in Compose 1.7 (ui-text 1.7.5)

`Font(resId, weight, variationSettings = …)` can silently render a variable font at its default
instance. Verified on the API 36 emulator on 2026-10-11.

**Mechanism.** `TypefaceCompatApi26.setFontVariationSettings` reuses one thread-local `Paint`: it
sets the new typeface, then calls `Paint.setFontVariationSettings(string)`. Android returns early
from that call when the string equals the previous one, so the new typeface keeps its default axes.

**Seen as.** Fraunces Normal and Manrope Normal both produce `'wght' 400, 'ital' 0`. Whichever loads
second stays at its default instance. For Manrope that is wght 200, so body text rendered thin while
the Medium and SemiBold weights were correct.

**Fixed upstream.** ui-text 1.10.2 calls `setFontVariationSettings(null)` before setting the
typeface (seen with `javap`).

**What the app does.** It bundles static instances cut with `fonttools varLib.instancer`, so no
variation settings are involved. A Compose upgrade would allow going back to the variable files.

Also, the plain `Font(resId, weight)` overload applies no variation at all. The `weight` there only
drives font matching.
