/**
 * Icon-font availability detection.
 *
 * The UI uses the Material Symbols ligature font. When it cannot be loaded, the
 * ligature names ("dashboard", "check_circle") would be painted as ordinary text and
 * would destroy every layout that relies on a square icon box. `index.css` contains
 * the matching fallback rules; this module decides which state to put the document in.
 *
 * The detection FAILS OPEN: if the Font Loading API is unavailable or misbehaves we
 * report "available" and hide nothing, because wrongly hiding every icon would be a
 * worse outcome than showing a placeholder.
 */

export const ICON_FONT_FAMILY = 'Material Symbols Outlined';

/** Attribute set on <html> that the fallback CSS keys off. */
export const ICON_STATE_ATTRIBUTE = 'data-icons';

export type IconFontState = 'available' | 'unavailable';

/** The slice of the Font Loading API this module needs. */
export interface FontAvailabilityProbe {
  check(font: string, text?: string): boolean;
}

/** Minimal surface of the element we set the attribute on. */
export interface AttributeSink {
  setAttribute(name: string, value: string): void;
}

/**
 * Decide whether the icon font can actually be drawn.
 * Pure: takes the probe, returns the state, touches no globals.
 */
export function resolveIconFontState(
  probe: FontAvailabilityProbe | null | undefined,
  family: string = ICON_FONT_FAMILY,
): IconFontState {
  if (!probe || typeof probe.check !== 'function') {
    return 'available';
  }
  try {
    if (probe.check(`24px "${family}"`)) return 'available';
    if (probe.check(`1em "${family}"`)) return 'available';
    return 'unavailable';
  } catch {
    return 'available';
  }
}

export function applyIconFontState(state: IconFontState, target: AttributeSink): IconFontState {
  target.setAttribute(ICON_STATE_ATTRIBUTE, state);
  return state;
}

/**
 * Wait (briefly) for the webfont to settle, then record the result on <html>.
 * Never throws and never blocks rendering — the application is already mounted by the
 * time this resolves. If the font is still not resolved after `timeoutMs` the check
 * runs anyway, so a stalled network cannot leave the page in an undecided state.
 */
export async function initialiseIconFontDetection(options?: {
  timeoutMs?: number;
}): Promise<IconFontState> {
  const timeoutMs = options?.timeoutMs ?? 3000;

  if (typeof document === 'undefined') {
    return 'available';
  }

  const fonts = (document as Document & { fonts?: FontFaceSet }).fonts;
  if (!fonts || typeof fonts.load !== 'function') {
    // No Font Loading API: fail open rather than hiding icons we cannot judge.
    return applyIconFontState('available', document.documentElement);
  }

  await Promise.race([
    (async () => {
      try {
        await fonts.load(`24px "${ICON_FONT_FAMILY}"`);
      } catch {
        // A rejection here means the face could not be loaded — handled by check().
      }
      try {
        await fonts.ready;
      } catch {
        // Ignore.
      }
    })(),
    new Promise<void>((resolve) => {
      setTimeout(resolve, timeoutMs);
    }),
  ]);

  return applyIconFontState(resolveIconFontState(fonts), document.documentElement);
}
