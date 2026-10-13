// Tailwind configuration ported 1:1 from the Stitch deliverable's inline
// tailwind.config block (dashboard_smartresume_analyzer/code.html) so that the
// existing utility class names resolve to identical values.
//
// Fallback stacks: the design's webfonts are loaded from Google Fonts. If that
// request fails (offline, blocked, slow network) the browser would otherwise fall
// back to its own default face, which changes the look and — for the monospace
// labels — the metrics. Every family below therefore names cross-platform system
// fallbacks so the UI stays readable and keeps its intended character without any
// network access. Class names and the primary face are unchanged.
const SANS_FALLBACKS = [
  'system-ui',
  '-apple-system',
  'Segoe UI',
  'Roboto',
  'Helvetica Neue',
  'Arial',
  'sans-serif',
];
const MONO_FALLBACKS = [
  'ui-monospace',
  'SFMono-Regular',
  'Menlo',
  'Consolas',
  'Liberation Mono',
  'monospace',
];

export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        'inverse-primary': '#c3c0ff',
        'surface-tint': '#4d44e3',
        'primary-fixed': '#e2dfff',
        background: '#faf8ff',
        'primary-container': '#4f46e5',
        'on-surface': '#131b2e',
        primary: '#3525cd',
        tertiary: '#95002b',
        outline: '#777587',
        error: '#ba1a1a',
        'tertiary-fixed': '#ffdadb',
        'surface-container-high': '#e2e7ff',
        'on-tertiary-container': '#ffd0d2',
        'on-primary-container': '#dad7ff',
        'on-secondary-fixed-variant': '#005236',
        'primary-fixed-dim': '#c3c0ff',
        'on-secondary': '#ffffff',
        'on-error-container': '#93000a',
        'tertiary-fixed-dim': '#ffb2b7',
        'on-primary-fixed-variant': '#3323cc',
        'inverse-surface': '#283044',
        'surface-dim': '#d2d9f4',
        'secondary-container': '#6cf8bb',
        'on-secondary-fixed': '#002113',
        'on-secondary-container': '#00714d',
        'tertiary-container': '#bf0f3c',
        'error-container': '#ffdad6',
        'on-tertiary-fixed-variant': '#92002a',
        'secondary-fixed-dim': '#4edea3',
        'on-surface-variant': '#464555',
        'on-error': '#ffffff',
        'on-primary': '#ffffff',
        'on-background': '#131b2e',
        secondary: '#006c49',
        'surface-container-lowest': '#ffffff',
        surface: '#faf8ff',
        'surface-variant': '#dae2fd',
        'surface-bright': '#faf8ff',
        'surface-container': '#eaedff',
        'on-tertiary-fixed': '#40000d',
        'secondary-fixed': '#6ffbbe',
        'outline-variant': '#c7c4d8',
        'surface-container-highest': '#dae2fd',
        'on-tertiary': '#ffffff',
        'surface-container-low': '#f2f3ff',
        'inverse-on-surface': '#eef0ff',
        'on-primary-fixed': '#0f0069',
      },
      borderRadius: {
        DEFAULT: '0.125rem',
        lg: '0.25rem',
        xl: '0.5rem',
        full: '0.75rem',
      },
      spacing: {
        'space-md': '1rem',
        gutter: '1.5rem',
        'space-xs': '0.25rem',
        margin: '2rem',
        'space-sm': '0.5rem',
        'space-lg': '1.5rem',
        'space-xl': '2.5rem',
        'gutter-mobile': '1rem',
        'margin-mobile': '1rem',
      },
      fontFamily: {
        'headline-lg-mobile': ['Plus Jakarta Sans', ...SANS_FALLBACKS],
        'body-sm': ['Inter', ...SANS_FALLBACKS],
        'headline-lg': ['Plus Jakarta Sans', ...SANS_FALLBACKS],
        'label-badge': ['JetBrains Mono', ...MONO_FALLBACKS],
        'body-md': ['Inter', ...SANS_FALLBACKS],
        'headline-md': ['Plus Jakarta Sans', ...SANS_FALLBACKS],
        'headline-sm': ['Plus Jakarta Sans', ...SANS_FALLBACKS],
        display: ['Plus Jakarta Sans', ...SANS_FALLBACKS],
        'label-code': ['JetBrains Mono', ...MONO_FALLBACKS],
        'body-lg': ['Inter', ...SANS_FALLBACKS],
        // Used by the body element, so an unstyled node still gets a sane sans face.
        sans: ['Inter', ...SANS_FALLBACKS],
        mono: ['JetBrains Mono', ...MONO_FALLBACKS],
      },
      fontSize: {
        'headline-lg-mobile': [
          '1.75rem',
          { lineHeight: '2.25rem', letterSpacing: '-0.02em', fontWeight: '600' },
        ],
        'body-sm': ['0.75rem', { lineHeight: '1.125rem', letterSpacing: '0.01em', fontWeight: '400' }],
        'headline-lg': [
          '2.25rem',
          { lineHeight: '2.75rem', letterSpacing: '-0.025em', fontWeight: '600' },
        ],
        'label-badge': [
          '0.6875rem',
          { lineHeight: '0.875rem', letterSpacing: '0.03em', fontWeight: '500' },
        ],
        'body-md': ['0.875rem', { lineHeight: '1.375rem', letterSpacing: '0em', fontWeight: '400' }],
        'headline-md': ['1.5rem', { lineHeight: '2rem', letterSpacing: '-0.02em', fontWeight: '600' }],
        'headline-sm': [
          '1.125rem',
          { lineHeight: '1.5rem', letterSpacing: '-0.01em', fontWeight: '600' },
        ],
        display: ['3.5rem', { lineHeight: '4rem', letterSpacing: '-0.03em', fontWeight: '700' }],
        'label-code': ['0.75rem', { lineHeight: '1rem', letterSpacing: '-0.01em', fontWeight: '500' }],
        'body-lg': ['1rem', { lineHeight: '1.625rem', letterSpacing: '-0.01em', fontWeight: '400' }],
      },
    },
  },
  plugins: [],
};
