# Pro controls — what is real, what is next

## Real in this build

**Exposure triangle** — ISO, shutter (log-scale 1/8000s–30s), WB Kelvin
2000–10000K, EV ±3, manual focus distance, all applied through
Camera2Interop on FULL/LEVEL_3 devices. Every row has an AUTO reset.
**AF/AE lock** — long-press the preview (or the PRO-panel chip) meters
AF+AE+AWB together and holds until released; chip shows lock state.
**True AEB** — HDR brackets 3/5/7 frames at ±1/±2/±3 EV by stepping
`setExposureCompensationIndex` (clamped to the device range) between
frames, restoring EV 0 after. Night/Portrait stack N identical frames.
**Burst rate** — 5/10/20 shots per burst, wired through to capture.
**Timelapse** — TIMELAPSE mode shoots N stills (6/12/30) spaced by the
configured interval; progress shows in the toast.
**Live telemetry** — a throttled Y-plane analyzer feeds real 64-bin luma
histograms, clip-fraction zebras, and a variance-of-Laplacian sharpness
meter (peaking shows for manual focus only).
**Composition** — thirds / golden-ratio / center grids, 1:1–2.39:1
letterbox masks (capture stays full-frame), gyro horizon level.
**Ergonomics** — volume-down shutter, one-tap clean viewfinder, haptic
shutter, named presets (save/recall/delete, persisted).
**Scan looks** — Mono/Warm/Cool looks generated as 3D LUTs plus Adobe
`.cube` import, applied on-device before PDF export (OCR always runs
on the ungraded warp).

## Honest limitations (roadmap)

- **RAW/DNG**: the flag is plumbed but capture still writes JPEG/HEIF.
  True DNG needs a dedicated Camera2 RAW_SENSOR session (CameraX must
  unbind first) and device-by-device validation — not shipped untested.
- **Focus stacking / anamorphic / green-magenta tint / false color**:
  not implemented. Peaking is a sharpness meter, not per-pixel edges.
- **HDR merge** keeps the middle frame (no Debevec/Mertens fusion yet).
- LUT looks apply to scans/exports, not the live viewfinder (GL
  pipeline not attached to PreviewView yet).
- ML features (OCR/barcode/face) need Google Play Services (thin clients).
