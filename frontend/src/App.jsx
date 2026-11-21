import React, { useState } from 'react';
import { AuthProvider, useAuth } from './context/AuthContext';
import { ToastProvider } from './context/ToastContext';
import Navbar from './components/Navbar';
import AuthPage from './pages/AuthPage';
import DriverDashboard from './pages/DriverDashboard';
import OwnerDashboard from './pages/OwnerDashboard';
import AdminDashboard from './pages/AdminDashboard';

function AppContent() {
  const { user, isAuthenticated } = useAuth();

  // Lifted tab state so Navbar and DriverDashboard share it
  const [driverTab, setDriverTab] = useState('finder');
  const [bookingCount, setBookingCount] = useState(0);

  if (!isAuthenticated || !user) {
    return (
      <div className="min-h-screen bg-[#0B1120] text-slate-100 flex flex-col">
        <Navbar />
        <main className="flex-1">
          <AuthPage />
        </main>
      </div>
    );
  }

  const role = (user.role || '').toUpperCase();
  const isAdmin = role === 'ADMIN' || role === 'ROLE_ADMIN';
  const isOwner = role === 'OWNER' || role === 'ROLE_OWNER';
  const isDriver = !isAdmin && !isOwner;

  return (
    <div className="min-h-screen bg-[#0B1120] text-slate-100 flex flex-col">
      <Navbar
        activeTab={isDriver ? driverTab : undefined}
        setActiveTab={isDriver ? setDriverTab : undefined}
        bookingCount={bookingCount}
      />
      <main className="flex-1 flex flex-col">
        {isAdmin ? (
          <AdminDashboard />
        ) : isOwner ? (
          <OwnerDashboard />
        ) : (
          <DriverDashboard
            activeTab={driverTab}
            setActiveTab={setDriverTab}
            onBookingCountChange={setBookingCount}
          />
        )}
      </main>
    </div>
  );
}

export default function App() {
  return (
    <ToastProvider>
      <AuthProvider>
        <AppContent />
      </AuthProvider>
    </ToastProvider>
  );
}
