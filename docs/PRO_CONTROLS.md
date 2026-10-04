# Pro controls — what is real, what is next

## Real in this build

**Exposure triangle** — ISO and shutter (log-scale, limited to the
device's real range, capped at 1s), white balance 2500–7500K (mapped to
the nearest Camera2 AWB preset), EV across the device's compensation
range, manual focus distance. Applied live to preview AND capture via
`Camera2CameraControl`; controls the bound camera doesn't support are
hidden with a note instead of silently doing nothing. Every row has an
AUTO reset, and all values persist across launches.
**AF/AE lock** — long-press the preview (or the PRO-panel chip) meters
AF+AE+AWB together and holds until released; chip shows lock state.
**True AEB** — HDR brackets 3/5/7 frames at ±1/±2/±3 EV by stepping
`setExposureCompensationIndex` (clamped to the device range) between
frames, restoring your EV after. Frames are aligned and exposure-fused
(per-pixel well-exposedness weights). Night takes 5 frames, aligns them,
and mean-stacks with outlier rejection so moving subjects don't ghost.
Only the merged result is saved to the gallery.
**Burst rate** — 5/10/20 shots per burst, wired through to capture.
**Timelapse** — TIMELAPSE mode shoots N stills (6/12/30) spaced by the
configured interval; progress shows in the toast.
**Live telemetry** — a throttled Y-plane analyzer feeds real 64-bin luma
histograms, clip-fraction zebras, and a variance-of-Laplacian sharpness
meter (peaking shows for manual focus only).
**Composition** — thirds / golden-ratio / center grids, 1:1–2.39:1
letterbox masks (capture stays full-frame), gyro horizon level.
**Looks** — Vivid / Warm / Cool / Fade / Sepia / Mono / Noir colour
matrices graded live on the viewfinder (GPU layer paint) and baked into
saved photos.
**Ergonomics** — volume-down shutter, self-timer (3s/10s), tap-to-focus
with drag-for-exposure, pinch zoom, haptic shutter, named presets
(save/recall/delete, persisted).
**Scan looks** — Mono/Warm/Cool looks generated as 3D LUTs plus Adobe
`.cube` import, applied on-device before PDF export (OCR always runs
on the ungraded warp).

## Honest limitations (roadmap)

- **RAW/DNG**: the flag is plumbed but capture still writes JPEG/HEIF.
  True DNG needs a dedicated Camera2 RAW_SENSOR session (CameraX must
  unbind first) and device-by-device validation — not shipped untested.
- **Focus stacking / anamorphic / green-magenta tint / false color**:
  not implemented. Peaking is a sharpness meter, not per-pixel edges.
- **HDR merge** is single-scale exposure fusion without a Laplacian
  pyramid, so extreme scenes can show faint halos; alignment is global
  translation only (no rotation / local motion).
- `.cube` LUT looks apply to scans/exports; the live viewfinder uses the
  colour-matrix looks above.
- **Portrait/bokeh, slow-motion, astro and dual-camera** modes exist in
  the enum but are not offered in the UI until implemented.
- ML features (OCR/barcode/face) need Google Play Services (thin clients).
