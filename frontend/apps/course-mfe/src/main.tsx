import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { Container, CssBaseline } from '@mui/material';
import { App } from './App';
import { routerFuture } from './routerFuture';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <CssBaseline />
    <BrowserRouter future={routerFuture}>
      <Container maxWidth="md" sx={{ py: 4 }}>
        <App />
      </Container>
    </BrowserRouter>
  </StrictMode>,
);
