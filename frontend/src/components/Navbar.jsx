import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import { Zap, LogOut, Compass, Route, BatteryCharging, Car } from 'lucide-react';
import { api } from '../api/client';

const DRIVER_TABS = [
  { id: 'finder',   label: 'Nearby Finder',   icon: Compass,         activeColor: 'text-blue-400'    },
  { id: 'trips',    label: 'Highway Planner', icon: Route,           activeColor: 'text-blue-400'    },
  { id: 'bookings', label: 'My Sessions',     icon: BatteryCharging, activeColor: 'text-emerald-400' },
  { id: 'vehicles', label: 'My Garage',       icon: Car,             activeColor: 'text-slate-300'   },
];

export default function Navbar({ activeTab, setActiveTab, bookingCount = 0 }) {
  const { user, logout } = useAuth();
  const [backendOnline, setBackendOnline] = useState(true);

  const isDriver = user && (user.role === 'DRIVER' || user.role === 'ROLE_DRIVER');

  // Ping backend to show live health status
  useEffect(() => {
    const checkHealth = async () => {
      try {
        await api.listPublicStations();
        setBackendOnline(true);
      } catch {
        setBackendOnline(false);
      }
    };
    checkHealth();
    const interval = setInterval(checkHealth, 30000);
    return () => clearInterval(interval);
  }, []);

  return (
    <header className="sticky top-0 z-50 bg-[#0B1120]/95 backdrop-blur-md border-b border-slate-800/80">
      <div className="max-w-[1600px] mx-auto px-4 sm:px-6">
        <div className="flex items-center justify-between h-14 gap-4">

          {/* Logo Brand */}
          <div className="flex items-center gap-2.5 shrink-0">
            <div className="w-8 h-8 rounded-xl bg-blue-600 flex items-center justify-center shadow-md shadow-blue-900/30">
              <Zap className="w-4 h-4 text-white fill-white" />
            </div>
            <span className="text-base font-bold tracking-tight text-slate-100">
              VoltGrid
            </span>
          </div>

          {/* Driver Navigation Tabs — centered */}
          {isDriver && setActiveTab && (
            <nav className="flex-1 flex items-center justify-center">
              <div className="flex items-center gap-1 p-1 rounded-xl bg-[#111827] border border-slate-800">
                {DRIVER_TABS.map(({ id, label, icon: Icon, activeColor }) => {
                  const isActive = activeTab === id;
                  return (
                    <button
                      key={id}
                      type="button"
                      onClick={() => setActiveTab(id)}
                      className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium transition-all cursor-pointer whitespace-nowrap ${
                        isActive
                          ? 'bg-[#1E293B] text-slate-100 font-semibold border border-slate-700/80 shadow-xs'
                          : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/40 border border-transparent'
                      }`}
                    >
                      <Icon className={`w-3.5 h-3.5 ${isActive ? activeColor : 'text-slate-500'}`} />
                      <span className="hidden sm:inline">{label}</span>
                      {id === 'bookings' && bookingCount > 0 && (
                        <span className="ml-1 px-1.5 py-0.2 rounded-full text-[9px] bg-emerald-500 text-slate-950 font-bold leading-none">
                          {bookingCount}
                        </span>
                      )}
                    </button>
                  );
                })}
              </div>
            </nav>
          )}

          {/* Right Section: Backend Status, User Badge & Logout */}
          <div className="flex items-center gap-2 sm:gap-3 shrink-0">
            {/* Live Backend Indicator */}
            <div
              className={`hidden md:flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium border transition-colors ${
                backendOnline
                  ? 'bg-emerald-950/40 text-emerald-400 border-emerald-800/50'
                  : 'bg-rose-950/40 text-rose-400 border-rose-800/50'
              }`}
              title={backendOnline ? 'Spring Boot API Connected' : 'API Unreachable'}
            >
              <span className={`w-1.5 h-1.5 rounded-full ${backendOnline ? 'bg-emerald-400 animate-pulse' : 'bg-rose-500'}`} />
              <span className="text-[11px] font-mono">{backendOnline ? 'Online' : 'Offline'}</span>
            </div>

            {user && (
              <>
                {/* User Profile Pill */}
                <div className="flex items-center gap-2 px-2.5 py-1.5 rounded-xl bg-[#1E293B] border border-slate-800 shadow-xs">
                  <div className="w-5 h-5 rounded-md bg-blue-600/20 text-blue-400 flex items-center justify-center font-bold text-[10px] border border-blue-500/30">
                    {user.name ? user.name[0].toUpperCase() : 'U'}
                  </div>
                  <div className="hidden sm:block text-left">
                    <div className="text-xs font-medium text-slate-200 leading-tight">{user.name}</div>
                  </div>
                  <span
                    className={`text-[9px] font-bold uppercase tracking-wider px-1.5 py-0.5 rounded-md border ${
                      user.role === 'OWNER'
                        ? 'bg-slate-800 text-slate-300 border-slate-700'
                        : user.role === 'ADMIN'
                        ? 'bg-purple-950/50 text-purple-300 border-purple-800/60'
                        : 'bg-blue-950/50 text-blue-300 border-blue-800/60'
                    }`}
                  >
                    {user.role}
                  </span>
                </div>

                {/* Logout Button */}
                <button
                  onClick={logout}
                  className="flex items-center gap-1.5 px-2.5 py-1.5 text-xs font-medium text-slate-400 hover:text-slate-100 hover:bg-slate-800/80 rounded-xl transition-colors border border-transparent hover:border-slate-700"
                  title="Sign out"
                >
                  <LogOut className="w-3.5 h-3.5" />
                  <span className="hidden sm:inline">Logout</span>
                </button>
              </>
            )}
          </div>
        </div>
      </div>
    </header>
  );
}
