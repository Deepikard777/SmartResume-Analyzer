---
name: SmartResume Analyzer Design System
colors:
  surface: '#faf8ff'
  surface-dim: '#d2d9f4'
  surface-bright: '#faf8ff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f2f3ff'
  surface-container: '#eaedff'
  surface-container-high: '#e2e7ff'
  surface-container-highest: '#dae2fd'
  on-surface: '#131b2e'
  on-surface-variant: '#464555'
  inverse-surface: '#283044'
  inverse-on-surface: '#eef0ff'
  outline: '#777587'
  outline-variant: '#c7c4d8'
  surface-tint: '#4d44e3'
  primary: '#3525cd'
  on-primary: '#ffffff'
  primary-container: '#4f46e5'
  on-primary-container: '#dad7ff'
  inverse-primary: '#c3c0ff'
  secondary: '#006c49'
  on-secondary: '#ffffff'
  secondary-container: '#6cf8bb'
  on-secondary-container: '#00714d'
  tertiary: '#95002b'
  on-tertiary: '#ffffff'
  tertiary-container: '#bf0f3c'
  on-tertiary-container: '#ffd0d2'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#e2dfff'
  primary-fixed-dim: '#c3c0ff'
  on-primary-fixed: '#0f0069'
  on-primary-fixed-variant: '#3323cc'
  secondary-fixed: '#6ffbbe'
  secondary-fixed-dim: '#4edea3'
  on-secondary-fixed: '#002113'
  on-secondary-fixed-variant: '#005236'
  tertiary-fixed: '#ffdadb'
  tertiary-fixed-dim: '#ffb2b7'
  on-tertiary-fixed: '#40000d'
  on-tertiary-fixed-variant: '#92002a'
  background: '#faf8ff'
  on-background: '#131b2e'
  surface-variant: '#dae2fd'
typography:
  display:
    fontFamily: Plus Jakarta Sans
    fontSize: 3.5rem
    fontWeight: '700'
    lineHeight: 4rem
    letterSpacing: -0.03em
  headline-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 2.25rem
    fontWeight: '600'
    lineHeight: 2.75rem
    letterSpacing: -0.025em
  headline-lg-mobile:
    fontFamily: Plus Jakarta Sans
    fontSize: 1.75rem
    fontWeight: '600'
    lineHeight: 2.25rem
    letterSpacing: -0.02em
  headline-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 1.5rem
    fontWeight: '600'
    lineHeight: 2rem
    letterSpacing: -0.02em
  headline-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 1.125rem
    fontWeight: '600'
    lineHeight: 1.5rem
    letterSpacing: -0.01em
  body-lg:
    fontFamily: Inter
    fontSize: 1rem
    fontWeight: '400'
    lineHeight: 1.625rem
    letterSpacing: -0.01em
  body-md:
    fontFamily: Inter
    fontSize: 0.875rem
    fontWeight: '400'
    lineHeight: 1.375rem
    letterSpacing: 0em
  body-sm:
    fontFamily: Inter
    fontSize: 0.75rem
    fontWeight: '400'
    lineHeight: 1.125rem
    letterSpacing: 0.01em
  label-code:
    fontFamily: JetBrains Mono
    fontSize: 0.75rem
    fontWeight: '500'
    lineHeight: 1rem
    letterSpacing: -0.01em
  label-badge:
    fontFamily: JetBrains Mono
    fontSize: 0.6875rem
    fontWeight: '500'
    lineHeight: 0.875rem
    letterSpacing: 0.03em
rounded:
  sm: 0.125rem
  DEFAULT: 0.25rem
  md: 0.375rem
  lg: 0.5rem
  xl: 0.75rem
  full: 9999px
spacing:
  gutter: 1.5rem
  gutter-mobile: 1rem
  margin: 2rem
  margin-mobile: 1rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2.5rem
---

## Brand & Style

The design system establishes a high-precision, diagnostic aesthetic tailored for students and early-career professionals seeking institutional-grade feedback on their resumes. The visual language merges the technical rigor of developer tooling (Vercel, Linear) with the polished clarity of modern financial infrastructure (Stripe). 

The emotional tone balances analytical authority with encouraging precision: the interface must never feel like an intimidating exam score, but rather a sophisticated telemetry dashboard that systematically deconstructs a resume into actionable optimizations. 

### Visual Paradigm
- **Movement:** Modern Analytical SaaS / Precision Minimalism.
- **Surface Strategy:** Razor-sharp layout boundaries, pure neutral substrates, and high-density information displays.
- **Atmosphere:** Controlled, data-centric, and distraction-free. Visual weight is managed strictly through crisp 1px borders, typographic scale, and localized status hues rather than heavy decorative treatments or illustrative distractions.

## Colors

The palette uses a crisp, high-contrast light foundation engineered for scanning dense text, syntax-highlighted resumes, and real-time score readouts.

### Functional Roles
- **Primary (`#4F46E5` / `#6366F1`):** Indigo represents intelligence and structural actions. Used exclusively for primary interactive triggers, active tabs, AI-processing states, and high-impact focal points.
- **Success / Match (`#10B981`):** Emerald denotes passed ATS checks, optimal phrasing, and high-probability score thresholds (85–100%).
- **Warning / Keyword Saturation (`#F59E0B`):** Amber highlights low-frequency concerns, questionable metrics, or keyword-stuffing flags.
- **Critical / Skill Void (`#F43F5E`):** Rose targets missing qualifications, critical syntax errors, or unparseable blocks.
- **Neutral Core (`#0F172A` to `#F8FAFC`):**
  - Text Primary: `slate-900` (`#0F172A`) for deep, uncompromised legibility.
  - Text Secondary: `slate-500` (`#64748B`) for metadata and analytical labels.
  - Borders: `slate-200` (`#E2E8F0`) defining clean structural compartments.
  - Canvas: `#F8FAFC` for page substrates; pure `#FFFFFF` for analytical cards and sheet viewports.

## Typography

The type system blends structural geometric display faces with high-legibility interface text and technical monospaced metadata.

- **Plus Jakarta Sans** delivers sharp geometric titles that project modern tech fluency without corporate stuffiness.
- **Inter** handles narrative critiques, resume line-item breakdowns, and recommendations, preserving optimal optical rhythm at 14px and 16px.
- **JetBrains Mono** surfaces parse engines, match metrics, semantic tokens, and keyword tags, grounding the feedback in clear analytical rigor.

## Layout & Spacing

The layout is structured around an ultra-compact analytical grid. Content conforms to an asymmetric workspace model: a persistent 64px macro navigation bar, a contextual split-pane viewport (resume inspection on the left, telemetry and feedback feed on the right), and modular data cards.

- **Desktop (1280px+):** Asymmetric split view. Resume canvas locks to a rigid aspect-ratio container with contextual review cards aligned to a 12-column sub-grid with 24px (`space-lg`) gutters.
- **Tablet (768px - 1023px):** Collapsible lateral sheets; tabbed toggle between "Document View" and "Metric Breakdown".
- **Mobile (<768px):** Linear vertical stack; resume preview collapses to an inspection-first accordion stack with edge margins pinned to 16px (`gutter-mobile`).

## Elevation & Depth

Visual hierarchy rejects exaggerated drop shadows in favor of low-contrast hairline outlines and subtle tonal layering.

- **Surface Tiers:**
  - `Base`: Background canvas `#F8FAFC`.
  - `Surface-1`: Surface card `#FFFFFF` framed by a 1px solid border in `#E2E8F0`.
  - `Surface-2`: Sub-inspector panels, data tables, and input reservoirs `#F1F5F9`.
- **Shadow Profile:**
  - Ambient elevation is reserved only for floating utility toolbars and interactive popovers: `0 1px 3px 0 rgba(15, 23, 42, 0.05), 0 1px 2px -1px rgba(15, 23, 42, 0.05)`.
  - Focused interactive cards elevate using a subtle layered ambient wash: `0 10px 15px -3px rgba(15, 23, 42, 0.04), 0 4px 6px -4px rgba(15, 23, 42, 0.02)`.
- **Active State Highlights:** Selected cards swap their border treatment from `#E2E8F0` to a dual-layer transition: 1px `#4F46E5` accompanied by an inner ring offset `0 0 0 1px #4F46E5`.

## Shapes

The geometric architecture embraces controlled, engineered contours (`0.25rem` base, with maximum card rounding capping at `0.5rem`). This restraint reflects precision engineering and preserves pixel efficiency across complex data grids, high-density metric rows, and syntax-highlighted resume snippets. Rounding never exceeds `0.5rem` (`rounded-lg`), barring circular status dots and pills.

## Components

### Buttons
- **Primary:** Solid `#4F46E5` background, text `#FFFFFF`, 1px border `#4338CA`. Subtle inset top highlight (`box-shadow: inset 0 1px 0 rgba(255,255,255,0.15)`). Hover transition to `#4338CA`.
- **Secondary:** Surface `#FFFFFF`, text `#0F172A`, 1px border `#E2E8F0`. Hover transition to `#F8FAFC` and border `#CBD5E1`.
- **Ghost:** Text `#64748B`, background transparent. Hover transition to `#F1F5F9`, text `#0F172A`.

### Status Badges & Keyword Tags
- Engineered as monospace tags using `JetBrains Mono` (`label-badge`).
- **Syntax Verified (Emerald):** Background `#ECFDF5`, text `#065F46`, border 1px solid `#A7F3D0`.
- **Missing / Gap (Rose):** Background `#FFF1F2`, text `#9F1239`, border 1px solid `#FECDD3`.
- **Warning (Amber):** Background `#FFFBEB`, text `#92400E`, border 1px solid `#FDE68A`.
- **Neutral Metric:** Background `#F8FAFC`, text `#334155`, border 1px solid `#E2E8F0`.

### Cards & Telemetry Blocks
- Pure white surfaces wrapped with a 1px border `#E2E8F0`.
- Header areas feature an integrated sub-grid separator border.
- Hover states on interactive cards shift the border to `#CBD5E1` with a micro-translation of `-1px` on the Y-axis.

### Input Fields & Search Bars
- Background `#FFFFFF`, 1px border `#E2E8F0`, interior text `#0F172A`, placeholder `#94A3B8`.
- Focus state triggers an immediate border swap to `#4F46E5` accompanied by a distinct 3px outer glow ring of `rgba(99, 102, 241, 0.15)`.

### Checkboxes & Segmented Controls
- Checkboxes: 16px square, radius `0.25rem`, border 1px `#CBD5E1`. Checked state fills `#4F46E5` with a crisp `#FFFFFF` check icon.
- Segmented switches: Enclosed inside a `#F1F5F9` structural tray with a 1px `#E2E8F0` border; active selection renders as a clean `#FFFFFF` tile with a subtle 1px border `#CBD5E1`.

### Specialized Platform Components
- **ATS Score Dial:** Concentric radial progress indicator utilizing `#4F46E5` for overall baseline metrics, matched with an inner `#10B981` delta ring. The numeric readout features tabular numerals set in `Plus Jakarta Sans` bold.
- **Diff / Replacement Strip:** Inline resume optimization preview showing targeted deletions in soft muted rose (`#FFE4E6`, strike-through) alongside suggested AI action phrases in structured emerald highlights (`#D1FAE5`, font-weight 500).