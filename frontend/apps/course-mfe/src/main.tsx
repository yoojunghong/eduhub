import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { CssBaseline } from '@mui/material';
import { App } from './App';
import { Layout } from './Layout';
import { routerFuture } from './routerFuture';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <CssBaseline />
    <BrowserRouter future={routerFuture}>
      <Layout>
        <App />
      </Layout>
    </BrowserRouter>
  </StrictMode>,
);
