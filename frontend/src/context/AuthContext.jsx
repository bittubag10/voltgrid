import React, { createContext, useContext, useState, useEffect } from 'react';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => localStorage.getItem('voltgrid_token'));
  const [user, setUser] = useState(() => {
    const saved = localStorage.getItem('voltgrid_user');
    try {
      return saved ? JSON.parse(saved) : null;
    } catch {
      return null;
    }
  });

  useEffect(() => {
    const handleAuthChange = () => {
      const savedToken = localStorage.getItem('voltgrid_token');
      const savedUser = localStorage.getItem('voltgrid_user');
      setToken(savedToken);
      try {
        setUser(savedUser ? JSON.parse(savedUser) : null);
      } catch {
        setUser(null);
      }
    };

    window.addEventListener('voltgrid_auth_change', handleAuthChange);
    return () => window.removeEventListener('voltgrid_auth_change', handleAuthChange);
  }, []);

  const login = (authToken, authUser) => {
    localStorage.setItem('voltgrid_token', authToken);
    localStorage.setItem('voltgrid_user', JSON.stringify(authUser));
    setToken(authToken);
    setUser(authUser);
  };

  const logout = () => {
    localStorage.removeItem('voltgrid_token');
    localStorage.removeItem('voltgrid_user');
    setToken(null);
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ token, user, isAuthenticated: !!token, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
