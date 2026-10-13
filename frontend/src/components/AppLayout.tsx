import { useEffect, useState, type ReactNode } from 'react';
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

/**
 * Application chrome ported from the Stitch deliverable: fixed 64px header
 * (logo + nav tray + primary CTA + user chip) and the shared footer.
 *
 * One deliberate addition: Stitch hides its nav below `lg` with no replacement,
 * which would leave mobile users without navigation. A mobile toggle is added
 * using the same tokens and markup conventions as the desktop tray.
 */

const NAV_ITEMS = [
  { to: '/', label: 'Dashboard', end: true },
  { to: '/analysis/new', label: 'New Analysis', end: false },
  { to: '/analysis/history', label: 'Analysis History', end: false },
  { to: '/settings', label: 'Settings', end: false },
];

function navLinkClass(isActive: boolean): string {
  const base = 'px-space-md py-1.5 rounded-lg transition-colors whitespace-nowrap';
  return isActive
    ? `${base} bg-surface-container text-primary font-semibold font-body-md text-body-md`
    : `${base} text-on-surface-variant font-body-md text-body-md hover:text-on-surface hover:bg-surface-container-high`;
}

export function AppLayout({ children }: { children: ReactNode }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [mobileNavOpen, setMobileNavOpen] = useState(false);

  useEffect(() => {
    setMobileNavOpen(false);
  }, [location.pathname]);

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  return (
    <div className="bg-surface font-body-md text-on-surface antialiased min-h-screen flex flex-col">
      <header className="fixed top-0 left-0 right-0 z-50 bg-surface-container-lowest/90 backdrop-blur-md border-b border-outline-variant/40">
        <div className="h-16 w-full px-gutter-mobile lg:px-gutter flex items-center justify-between gap-space-md">
          <div className="flex items-center gap-space-lg min-w-0">
            <Link to="/" className="flex items-center gap-space-sm shrink-0">
              <img alt="SmartResume Analyzer Logo" className="h-8 w-auto object-contain" src="/logo.svg" />
              <span className="font-headline-sm text-headline-sm text-on-surface tracking-tight font-semibold hidden sm:inline">
                SmartResume Analyzer
              </span>
              <span className="hidden xl:inline px-space-xs py-0.5 rounded-DEFAULT bg-secondary-container text-on-secondary-container font-label-badge text-label-badge uppercase tracking-wider font-medium border border-secondary/20">
                Engine v2.4
              </span>
            </Link>
            <nav
              aria-label="Primary"
              className="hidden lg:flex items-center gap-space-xs p-1 bg-surface-container-low rounded-xl border border-outline-variant/30"
            >
              {NAV_ITEMS.map((item) => (
                <NavLink key={item.to} to={item.to} end={item.end} className={({ isActive }) => navLinkClass(isActive)}>
                  {item.label}
                </NavLink>
              ))}
            </nav>
          </div>

          <div className="flex items-center gap-space-sm lg:gap-space-md shrink-0">
            <Link
              to="/analysis/new"
              className="hidden sm:inline-flex items-center gap-space-xs px-space-md py-1.5 rounded-lg bg-primary-container text-on-primary font-body-md text-body-md font-medium hover:bg-primary transition-colors shadow-sm"
            >
              <span className="material-symbols-outlined text-[18px]">document_scanner</span>
              <span>Analyze Resume</span>
            </Link>

            {user && (
              <>
                <div className="h-6 w-px bg-outline-variant/50 hidden sm:block" />
                <div className="hidden sm:flex items-center gap-space-sm pl-space-xs">
                  <div className="text-right leading-tight max-w-[16rem]">
                    <p className="font-body-md text-body-md font-semibold text-on-surface truncate">{user.name}</p>
                    <p className="font-label-badge text-label-badge text-on-surface-variant truncate">{user.email}</p>
                  </div>
                  <div className="w-8 h-8 rounded-full bg-primary flex items-center justify-center shrink-0">
                    <span className="material-symbols-outlined text-on-primary text-[18px]">person</span>
                  </div>
                </div>
                <button
                  type="button"
                  onClick={handleLogout}
                  title="Sign out"
                  className="p-2 rounded-lg text-on-surface-variant hover:text-error hover:bg-error-container/30 transition-colors"
                >
                  <span className="material-symbols-outlined text-[20px]">logout</span>
                </button>
              </>
            )}

            <button
              type="button"
              aria-label="Toggle navigation"
              aria-expanded={mobileNavOpen}
              onClick={() => setMobileNavOpen((open) => !open)}
              className="lg:hidden p-2 rounded-lg text-on-surface-variant hover:text-on-surface hover:bg-surface-container-high transition-colors"
            >
              <span className="material-symbols-outlined text-[20px]">
                {mobileNavOpen ? 'close' : 'menu'}
              </span>
            </button>
          </div>
        </div>

        {mobileNavOpen && (
          <nav
            aria-label="Primary mobile"
            className="lg:hidden px-gutter-mobile pb-space-sm flex flex-col gap-space-xs"
          >
            {NAV_ITEMS.map((item) => (
              <NavLink key={item.to} to={item.to} end={item.end} className={({ isActive }) => navLinkClass(isActive)}>
                {item.label}
              </NavLink>
            ))}
            <Link
              to="/analysis/new"
              className="sm:hidden mt-space-xs inline-flex items-center gap-space-xs px-space-md py-2 rounded-lg bg-primary-container text-on-primary font-body-md text-body-md font-medium justify-center"
            >
              <span className="material-symbols-outlined text-[18px]">document_scanner</span>
              <span>Analyze Resume</span>
            </Link>
          </nav>
        )}
      </header>

      <main className="w-full pt-16 flex-1 bg-surface">
        <div className="flex flex-col w-full">{children}</div>
      </main>

      <footer className="w-full bg-surface-container-lowest border-t border-outline-variant/30 py-space-md px-gutter">
        <div className="w-full flex flex-col sm:flex-row items-center justify-between gap-space-sm text-on-surface-variant font-body-sm text-body-sm">
          <div className="flex items-center gap-space-sm font-label-code text-label-code">
            <span>SmartResume Core Engine v2.4</span>
            <span className="text-outline-variant">•</span>
            <span className="text-secondary font-medium">ATS Diagnostic Ready</span>
          </div>
          <p>© 2025 SmartResume Analyzer Inc. Institutional Grade Telemetry.</p>
        </div>
      </footer>
    </div>
  );
}
