import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import { AuthProvider } from './context/AuthContext';
import { initialiseIconFontDetection } from './utils/icons';
import './index.css';

const container = document.getElementById('root');
if (!container) {
  throw new Error('Root container #root was not found in index.html');
}

createRoot(container).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <App />
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
);

// Decide whether the Material Symbols webfont is actually usable so the fallback CSS
// can substitute a neutral placeholder instead of rendering the ligature names as
// text when the font is unreachable. Deliberately not awaited: it must never delay
// first paint, and the page is fully usable either way.
void initialiseIconFontDetection();
