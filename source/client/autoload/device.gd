class_name DeviceInfo
extends Node

## Device — touch/handheld capability probe and the client's safe-area source.
##
## Metin3 targets phones first, where two things hold that nothing in the
## desktop-shaped client accounted for:
##
## [b]Cutouts.[/b] Notches, punch-holes and the Android gesture bar sit ON TOP
## of the window. Android 15 (targetSdk 35) draws every app edge-to-edge and
## no longer honours the opt-out, so a corner-anchored HUD rail lands under
## the status bar unless the UI is inset by hand. [member safe_area_insets]
## is that inset, already converted out of screen pixels into the canvas-item
## coordinates the UI is laid out in.
##
## [b]Reach.[/b] A HUD authored against a 960x540 viewport renders around half
## a centimetre tall on a 6" panel. [member suggested_ui_scale] is the
## multiplier that would restore a thumb-sized touch target, derived from the
## panel's real DPI.
##
## Autoloaded as [code]Device[/code]. Desktop and web report zero insets and a
## scale of 1.0, so callers never need a platform branch.


## Emitted whenever [member safe_area_insets] changes — rotation, a fold/unfold,
## or the window being resized. UI that insets itself should connect to this
## rather than sampling once in [method Node._ready]: on Android the first
## frame can report the pre-layout full-screen rect.
signal safe_area_changed(insets: Vector4)

## Nominal side of a HUD touch target in canvas units, as the upstream theme
## authors them (the rail buttons in hud.tscn). [member suggested_ui_scale]
## measures against this.
const BASE_TOUCH_TARGET: float = 40.0

## Android's minimum comfortable touch target, in density-independent pixels.
## Both Material and the iOS HIG land within a couple of dp of this.
const MIN_TOUCH_TARGET_DP: float = 48.0

## dp is defined as 1px at 160 dpi.
const DP_BASELINE_DPI: float = 160.0

## Upper bound on [member suggested_ui_scale]. Past this the HUD eats the
## playfield, which on a phone costs more than it buys.
const MAX_UI_SCALE: float = 2.0


## Cutout inset in canvas-item coordinates, as (left, top, right, bottom).
## [constant Vector4.ZERO] on desktop, on web, and on any handheld with no
## intruding system chrome.
## Written only by [method _refresh_safe_area]; treat it as read-only.
var safe_area_insets: Vector4 = Vector4.ZERO

## [code]true[/code] on Android and iOS exports. Note this is about the
## [i]platform[/i], not the input device — a phone with a bluetooth pad still
## reports [code]true[/code]. For "should I draw the touch sticks", ask
## [member has_touchscreen] instead.
var is_handheld: bool:
	get: return OS.has_feature("mobile")

## [code]true[/code] when the display can report touch. True for handhelds and
## also for touch-capable laptops and the web export on a phone browser.
var has_touchscreen: bool:
	get: return DisplayServer.is_touchscreen_available()

## Multiplier that would bring a [constant BASE_TOUCH_TARGET] HUD control up to
## [constant MIN_TOUCH_TARGET_DP] on this panel. 1.0 on desktop and whenever
## the controls are already large enough.
##
## [b]Not applied automatically.[/b] Scaling the canvas in [code]canvas_items[/code]
## stretch mode magnifies the world alongside the UI, which narrows the visible
## playfield — a balance decision, not a layout one. Wiring this into the HUD
## theme is tracked in MOBILE.md.
var suggested_ui_scale: float:
	get: return _compute_ui_scale()


func _ready() -> void:
	# Insets are only meaningful once the window has its final size, and they
	# change again on every rotation. size_changed covers both.
	get_tree().root.size_changed.connect(_refresh_safe_area)
	# Android reports the full-screen rect on the first frame, before the
	# system bars have been laid out. Sample after one frame, not during it.
	_refresh_safe_area.call_deferred()


## Recomputes [member safe_area_insets] and emits [signal safe_area_changed] if
## it moved. Called for you on resize; call it directly only after changing
## the window mode yourself.
func _refresh_safe_area() -> void:
	var insets: Vector4 = _compute_safe_area_insets()
	if insets.is_equal_approx(safe_area_insets):
		return
	safe_area_insets = insets
	safe_area_changed.emit(insets)


func _compute_safe_area_insets() -> Vector4:
	if not is_handheld or not is_inside_tree():
		return Vector4.ZERO

	var window_size: Vector2i = DisplayServer.window_get_size()
	if window_size.x <= 0 or window_size.y <= 0:
		return Vector4.ZERO

	# Screen pixels. On a fullscreen handheld the window covers the screen, so
	# this rect is directly comparable to the window rect.
	var safe: Rect2i = DisplayServer.get_display_safe_area()
	if safe.size.x <= 0 or safe.size.y <= 0:
		return Vector4.ZERO

	# A safe area larger than the window means the two are measured against
	# different origins (some OEM skins, and the editor's device preview).
	# Insetting on that reading would push the HUD off-screen, so decline.
	if safe.size.x > window_size.x or safe.size.y > window_size.y:
		return Vector4.ZERO

	var left: float = maxf(0.0, float(safe.position.x))
	var top: float = maxf(0.0, float(safe.position.y))
	var right: float = maxf(0.0, float(window_size.x - safe.end.x))
	var bottom: float = maxf(0.0, float(window_size.y - safe.end.y))

	# Screen px -> canvas units. Under canvas_items stretch the viewport keeps
	# the design resolution while the window grows, so the ratio between them
	# is the factor the UI is drawn at.
	var viewport_size: Vector2 = get_tree().root.get_visible_rect().size
	var to_canvas: Vector2 = viewport_size / Vector2(window_size)

	return Vector4(
		left * to_canvas.x,
		top * to_canvas.y,
		right * to_canvas.x,
		bottom * to_canvas.y
	)


func _compute_ui_scale() -> float:
	# The getter is public, so it can be read before the autoload enters the
	# tree (another autoload's _init). Fall back rather than crash.
	if not is_handheld or not is_inside_tree():
		return 1.0

	var dpi: int = DisplayServer.screen_get_dpi()
	# screen_get_dpi returns 72 as its "don't know" answer on several drivers.
	# Guessing from a fallback value is worse than not scaling.
	if dpi <= 72:
		return 1.0

	var window_size: Vector2i = DisplayServer.window_get_size()
	if window_size.y <= 0:
		return 1.0
	var viewport_size: Vector2 = get_tree().root.get_visible_rect().size
	if viewport_size.y <= 0.0:
		return 1.0

	# How many screen pixels one canvas unit occupies.
	var px_per_canvas_unit: float = float(window_size.y) / viewport_size.y
	var min_target_px: float = MIN_TOUCH_TARGET_DP * (float(dpi) / DP_BASELINE_DPI)
	var min_target_canvas: float = min_target_px / px_per_canvas_unit

	return clampf(min_target_canvas / BASE_TOUCH_TARGET, 1.0, MAX_UI_SCALE)
