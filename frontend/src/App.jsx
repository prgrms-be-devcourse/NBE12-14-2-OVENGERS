import { BrowserRouter } from 'react-router-dom';
import { AuthProvider } from './context/AuthProvider';
import AppRoutes from './router/routes';

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <AppRoutes />
      </AuthProvider>
    </BrowserRouter>
  );
}
