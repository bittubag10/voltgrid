import React, { useState, useEffect } from 'react';
import { api } from '../api/client';
import { useToast } from '../context/ToastContext';
import MapComponent from '../components/MapComponent';
import {
  ShieldAlert, Users, Building2, BatteryCharging, IndianRupee,
  RefreshCw, CheckCircle2, AlertTriangle, Search, Filter,
  Power, ShieldCheck, MapPin, Zap, Clock, UserCheck, UserX
} from 'lucide-react';

export default function AdminDashboard() {
  const toast = useToast();
  const [overview, setOverview] = useState(null);
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(false);

  // User filter & search
  const [roleFilter, setRoleFilter] = useState('ALL'); // 'ALL' | 'DRIVER' | 'OWNER' | 'ADMIN'
  const [searchTerm, setSearchTerm] = useState('');

  // Active section tab
  const [activeTab, setActiveTab] = useState('overview'); // 'overview' | 'users' | 'bookings'

  const loadData = async () => {
    try {
      setLoading(true);
      const [ovRes, uRes] = await Promise.all([
        api.getAdminOverview(),
        api.listAdminUsers(0, 100),
      ]);
      setOverview(ovRes);
      setUsers(uRes?.content || []);
    } catch (err) {
      toast.error(err.message || 'Failed to load Network Administration data');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleToggleUserStatus = async (user) => {
    setActionLoading(true);
    try {
      const newStatus = !user.enabled;
      await api.toggleAdminUserStatus(user.id, newStatus);
      toast.success(`User ${user.email} marked as ${newStatus ? 'ENABLED' : 'DISABLED'}`);
      const uRes = await api.listAdminUsers(0, 100);
      setUsers(uRes?.content || []);
    } catch (err) {
      toast.error(err.message || 'Failed to toggle user status');
    } finally {
      setActionLoading(false);
    }
  };

  // Filtered users list
  const filteredUsers = users.filter((u) => {
    const matchesRole = roleFilter === 'ALL' || u.role === roleFilter;
    const matchesSearch =
      !searchTerm ||
      u.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
      u.email.toLowerCase().includes(searchTerm.toLowerCase());
    return matchesRole && matchesSearch;
  });

  return (
    <div className="min-h-[calc(100vh-3.5rem)] bg-[#0B1120] text-slate-100 px-4 sm:px-6 lg:px-8 py-8 space-y-7">
      {/* Network Administration Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 p-6 sm:p-7 rounded-2xl bg-[#1E293B] border border-slate-700/70 shadow-sm">
        <div>
          <div className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-blue-50 text-blue-700 border border-blue-200 text-xs font-semibold mb-2">
            <ShieldCheck className="w-3.5 h-3.5" />
            Network Administration
          </div>
          <h1 className="text-2xl sm:text-3xl font-bold text-slate-100 tracking-tight">
            Platform Operations
          </h1>
          <p className="text-xs sm:text-sm text-slate-400 mt-1">
            Global monitoring of stations, live charging sessions, financial telemetry, and user access controls.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={loadData}
            disabled={loading}
            className="p-2.5 rounded-xl border border-slate-700/70 bg-[#1E293B] text-slate-400 hover:bg-[#111827] hover:text-slate-100 shadow-sm transition-colors"
            title="Refresh network metrics"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
          </button>

          {/* Navigation Tabs */}
          <div className="flex p-1 rounded-xl bg-[#0F172A] border border-slate-700/70">
            <button
              onClick={() => setActiveTab('overview')}
              className={`px-3.5 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                activeTab === 'overview'
                  ? 'bg-[#1E293B] text-slate-100 shadow-sm'
                  : 'text-slate-400 hover:text-slate-100'
              }`}
            >
              Overview
            </button>
            <button
              onClick={() => setActiveTab('users')}
              className={`px-3.5 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                activeTab === 'users'
                  ? 'bg-[#1E293B] text-slate-100 shadow-sm'
                  : 'text-slate-400 hover:text-slate-100'
              }`}
            >
              Users ({users.length})
            </button>
            <button
              onClick={() => setActiveTab('bookings')}
              className={`px-3.5 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                activeTab === 'bookings'
                  ? 'bg-[#1E293B] text-slate-100 shadow-sm'
                  : 'text-slate-400 hover:text-slate-100'
              }`}
            >
              Live Bookings
            </button>
          </div>
        </div>
      </div>

      {/* Top Network KPI Metrics */}
      {overview && (
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          {/* Total Users */}
          <div className="p-5 rounded-2xl bg-[#1E293B] border border-slate-700/70 shadow-sm space-y-1">
            <div className="flex items-center justify-between text-slate-400 text-xs font-semibold uppercase tracking-wider">
              <span>Platform Users</span>
              <Users className="w-4 h-4 text-blue-600" />
            </div>
            <div className="text-3xl font-extrabold text-slate-100 font-mono tabular-nums">{overview.totalUsers}</div>
            <div className="text-xs text-slate-400 flex items-center gap-1.5">
              <span className="text-slate-300 font-medium">{overview.totalDrivers} Drivers</span>
              <span>•</span>
              <span className="text-slate-300 font-medium">{overview.totalOwners} Owners</span>
            </div>
          </div>

          {/* Charging Infrastructure */}
          <div className="p-5 rounded-2xl bg-[#1E293B] border border-slate-700/70 shadow-sm space-y-1">
            <div className="flex items-center justify-between text-slate-400 text-xs font-semibold uppercase tracking-wider">
              <span>Active Stations</span>
              <Building2 className="w-4 h-4 text-blue-600" />
            </div>
            <div className="text-3xl font-extrabold text-slate-100 font-mono tabular-nums">
              {overview.activeStations} <span className="text-sm text-slate-400 font-normal">/ {overview.totalStations}</span>
            </div>
            <div className="text-xs text-slate-400">
              {overview.totalChargers} total bays installed
            </div>
          </div>

          {/* Active Charging Sessions */}
          <div className="p-5 rounded-2xl bg-[#1E293B] border border-slate-700/70 shadow-sm space-y-1">
            <div className="flex items-center justify-between text-slate-400 text-xs font-semibold uppercase tracking-wider">
              <span>Live Charging</span>
              <BatteryCharging className="w-4 h-4 text-emerald-600" />
            </div>
            <div className="text-3xl font-extrabold text-emerald-700 font-mono tabular-nums">{overview.activeSessions}</div>
            <div className="text-xs text-slate-400">
              {overview.totalBookings} lifetime reservations
            </div>
          </div>

          {/* Revenue & Penalties */}
          <div className="p-5 rounded-2xl bg-[#1E293B] border border-slate-700/70 shadow-sm space-y-1">
            <div className="flex items-center justify-between text-slate-400 text-xs font-semibold uppercase tracking-wider">
              <span>Network Revenue</span>
              <IndianRupee className="w-4 h-4 text-blue-600" />
            </div>
            <div className="text-3xl font-extrabold text-slate-100 font-mono tabular-nums">₹{overview.totalRevenue}</div>
            <div className="text-xs text-amber-700 font-medium">
              ₹{overview.totalOverstayPenalties} overstay penalties
            </div>
          </div>
        </div>
      )}

      {/* TAB 1: OVERVIEW & MAP */}
      {activeTab === 'overview' && overview && (
        <div className="space-y-6">
          {/* Global Stations Map */}
          <div className="p-6 rounded-2xl bg-[#1E293B] border border-slate-700/70 shadow-sm space-y-4">
            <div className="flex items-center justify-between border-b border-slate-800 pb-3">
              <div className="flex items-center gap-2 text-slate-100 font-bold text-base">
                <MapPin className="w-5 h-5 text-blue-600" />
                <span>Pan-India Supercharging Hubs ({overview.stations.length})</span>
              </div>
              <span className="text-xs text-slate-400">Live operational nodes across key corridors</span>
            </div>

            <MapComponent
              userLat="22.307200"
              userLng="73.181200"
              stations={overview.stations}
              height="460px"
              zoom={7}
            />
          </div>

          {/* Quick Hub Inventory */}
          <div className="p-6 rounded-2xl bg-[#1E293B] border border-slate-700/70 shadow-sm space-y-4">
            <h3 className="text-base font-bold text-slate-100 flex items-center gap-2">
              <Building2 className="w-4 h-4 text-blue-600" />
              Infrastructure Hub Registry
            </h3>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {overview.stations.map((st) => (
                <div
                  key={st.id}
                  className="p-4 rounded-xl bg-[#111827] border border-slate-700/70 flex items-start justify-between gap-3"
                >
                  <div>
                    <div className="font-semibold text-sm text-slate-100">{st.name}</div>
                    <div className="text-xs text-slate-400 flex items-center gap-1 mt-0.5">
                      <MapPin className="w-3 h-3 text-slate-400" />
                      <span>{st.city} • {st.address}</span>
                    </div>
                    <div className="text-xs text-slate-400 font-mono mt-1">
                      ({Number(st.latitude).toFixed(4)}, {Number(st.longitude).toFixed(4)})
                    </div>
                  </div>
                  <span
                    className={`text-[10px] font-bold uppercase px-2 py-0.5 rounded-full border ${
                      st.status === 'ACTIVE'
                        ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                        : 'bg-[#0F172A] text-slate-400 border-slate-700/70'
                    }`}
                  >
                    {st.status}
                  </span>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* TAB 2: USER MANAGEMENT TABLE */}
      {activeTab === 'users' && (
        <div className="p-6 sm:p-7 rounded-2xl bg-[#1E293B] border border-slate-700/70 shadow-sm space-y-5">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-slate-800 pb-4">
            <div>
              <h3 className="text-base font-bold text-slate-100 flex items-center gap-2">
                <Users className="w-5 h-5 text-blue-600" />
                User Access & Role Management
              </h3>
              <p className="text-xs text-slate-400">View user credentials, filter by roles, and control account status.</p>
            </div>

            {/* Role Filter & Search */}
            <div className="flex flex-wrap items-center gap-3">
              <div className="relative">
                <Search className="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
                <input
                  type="text"
                  placeholder="Search user or email..."
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  className="pl-9 pr-3 py-1.5 bg-[#1E293B] border border-slate-700/70 rounded-xl text-xs text-slate-100 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-600/20 focus:border-blue-600"
                />
              </div>

              <div className="flex rounded-xl bg-[#0F172A] p-1 border border-slate-700/70 text-xs">
                {['ALL', 'DRIVER', 'OWNER', 'ADMIN'].map((r) => (
                  <button
                    key={r}
                    onClick={() => setRoleFilter(r)}
                    className={`px-3 py-1 rounded-lg font-semibold transition-all ${
                      roleFilter === r
                        ? 'bg-[#1E293B] text-slate-100 shadow-sm'
                        : 'text-slate-400 hover:text-slate-100'
                    }`}
                  >
                    {r}
                  </button>
                ))}
              </div>
            </div>
          </div>

          {/* Table */}
          <div className="overflow-x-auto border border-slate-700/70 rounded-xl">
            <table className="w-full text-left text-xs">
              <thead className="text-[11px] font-semibold uppercase tracking-wider text-slate-400 bg-[#111827] border-b border-slate-700/70">
                <tr>
                  <th className="py-3 px-4">User</th>
                  <th className="py-3 px-4">Role</th>
                  <th className="py-3 px-4">Registered</th>
                  <th className="py-3 px-4">Status</th>
                  <th className="py-3 px-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {filteredUsers.map((u) => (
                  <tr key={u.id} className="hover:bg-[#111827]/80 transition-colors">
                    <td className="py-3.5 px-4">
                      <div className="font-semibold text-slate-100 text-sm">{u.name}</div>
                      <div className="text-slate-400 text-xs">{u.email}</div>
                    </td>
                    <td className="py-3.5 px-4">
                      <span
                        className={`text-[10px] font-bold uppercase px-2.5 py-0.5 rounded-full border ${
                          u.role === 'ADMIN'
                            ? 'bg-purple-50 text-purple-700 border-purple-200'
                            : u.role === 'OWNER'
                            ? 'bg-[#0F172A] text-slate-800 border-slate-700'
                            : 'bg-blue-50 text-blue-700 border-blue-200'
                        }`}
                      >
                        {u.role}
                      </span>
                    </td>
                    <td className="py-3.5 px-4 text-slate-400">
                      {u.createdAt ? new Date(u.createdAt).toLocaleDateString() : 'N/A'}
                    </td>
                    <td className="py-3.5 px-4">
                      <span
                        className={`text-[10px] font-semibold px-2 py-0.5 rounded-full border ${
                          u.enabled !== false
                            ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                            : 'bg-rose-50 text-rose-700 border-rose-200'
                        }`}
                      >
                        {u.enabled !== false ? 'Active' : 'Disabled'}
                      </span>
                    </td>
                    <td className="py-3.5 px-4 text-right">
                      {u.role !== 'ADMIN' && (
                        <button
                          onClick={() => handleToggleUserStatus(u)}
                          disabled={actionLoading}
                          className={`px-3 py-1.5 rounded-lg font-semibold text-xs border transition-all ${
                            u.enabled !== false
                              ? 'border-slate-700/70 text-slate-400 hover:bg-[#111827]'
                              : 'border-emerald-300 text-emerald-700 hover:bg-emerald-50'
                          }`}
                        >
                          {u.enabled !== false ? 'Disable' : 'Enable'}
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* TAB 3: GLOBAL BOOKINGS MONITOR */}
      {activeTab === 'bookings' && overview && (
        <div className="p-6 sm:p-7 rounded-2xl bg-[#1E293B] border border-slate-700/70 shadow-sm space-y-4">
          <div className="border-b border-slate-800 pb-3">
            <h3 className="text-base font-bold text-slate-100 flex items-center gap-2">
              <Clock className="w-5 h-5 text-blue-600" />
              Global Reservation Stream
            </h3>
            <p className="text-xs text-slate-400">Live booking queue and financial status across all nodes.</p>
          </div>

          <div className="overflow-x-auto border border-slate-700/70 rounded-xl">
            <table className="w-full text-left text-xs">
              <thead className="text-[11px] font-semibold uppercase tracking-wider text-slate-400 bg-[#111827] border-b border-slate-700/70">
                <tr>
                  <th className="py-3 px-4">ID</th>
                  <th className="py-3 px-4">Bay & Station</th>
                  <th className="py-3 px-4">Vehicle</th>
                  <th className="py-3 px-4">Schedule</th>
                  <th className="py-3 px-4">Status</th>
                  <th className="py-3 px-4 text-right">Settled Amount</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {overview.recentBookings.map((b) => (
                  <tr key={b.id} className="hover:bg-[#111827]/80 transition-colors">
                    <td className="py-3 px-4 font-mono font-semibold text-slate-400">#{b.id}</td>
                    <td className="py-3 px-4">
                      <div className="font-semibold text-slate-100">{b.chargerName}</div>
                      <div className="text-slate-400 text-xs">{b.stationName}</div>
                    </td>
                    <td className="py-3 px-4 text-slate-300 font-medium">{b.vehicleModel}</td>
                    <td className="py-3 px-4 text-slate-400 font-mono">
                      {new Date(b.startTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                    </td>
                    <td className="py-3 px-4">
                      <span className="text-[10px] font-semibold uppercase px-2 py-0.5 rounded-full bg-[#0F172A] text-slate-300 border border-slate-700/70">
                        {b.status}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-right font-bold text-slate-100 font-mono tabular-nums text-sm">
                      ₹{b.totalAmount || b.baseAmount || '0.00'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
