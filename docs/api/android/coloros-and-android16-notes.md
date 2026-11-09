# Android platform behaviour this app depends on

Observed on a OnePlus CPH2663, Android 16 (API 36.0), ColorOS 16.0.5, September 2026.

## Alarms: ColorOS stretches exact alarms

- `setExact*` / `setExactAndAllowWhileIdle` alarms arrived about 45 s late in a short-delay test.
  `dumpsys alarm` shows `OplusAlarmAdjustMap` widening the window by up to 75 % of the delay.
- `setAlarmClock` alarms are left alone and fired within 1 ms. The cost: the system alarm-clock icon
  shows in the status bar while any of our alarms is pending, and the lock screen / clock app may list
  ours as the "next alarm".
- The daily start nudge uses `setAlarmClock` too. It is armed about a day ahead, so a 75 % stretch
  could make it hours late; the trade is that the alarm icon is shown all day while a daily start
  time is set. Revisit if `setExactAndAllowWhileIdle` proves punctual for long delays on this phone
  (untested: only short delays were measured). On the phone the nudge fired 91 ms after its time
  (2-minute delay) and re-armed for the next day.
- The fast-complete alarm (16 h after the last bite) is pending from the moment a window starts until
  the fast is done, so even without a daily start time the alarm icon shows nearly all the time. The
  same revisit applies: it is armed up to 24 hours ahead, so a 75 % stretch could make it most of a
  day late. Verified on the emulator only (fired within the 5 s polling interval).
- Force-stopping the app wipes its alarms, as on stock Android; `MainActivity` re-arms on open.

## Live Updates (promoted ongoing notifications)

- The API 36.0 SDK has `Notification.FLAG_PROMOTED_ONGOING` and
  `NotificationManager.canPostPromotedNotifications()`, but no constant for the request extra and no
  `POST_PROMOTED_NOTIFICATIONS` permission constant. Both arrived with Android 16 QPR2 (API 36.1).
- We set the extra `"android.requestPromotedOngoing"` and declare the permission by name. On the 36.0
  test phone the countdown is posted as a normal ongoing notification (no `PROMOTED_ONGOING` flag).
  Promotion is **unverified**; check on a 36.1+ device with `dumpsys notification`.
- Chronometer countdown (`setUsesChronometer` + `setChronometerCountDown` + `setWhen(closesAt)`)
  renders in the ColorOS shade header.

## Quick Settings tiles on ColorOS

- `cmd statusbar add-tile` / `remove-tile` work, but ColorOS keeps its own tile order: the tile does
  not appear in `settings get secure sysui_qs_tiles`.
- `cmd statusbar click-tile` does nothing for a tile that has not been added.
- Custom tiles run from the lock screen without unlocking; ColorOS shows its own
  "<label> on" toast when the tile turns active. Tile subtitles are not shown in the grid.
