import React, { useState, useEffect, useMemo } from 'react';
import { api } from '../api/client';
import { useToast } from '../context/ToastContext';
import MapComponent from '../components/MapComponent';
import {
  Building2, Plus, Zap, AlertCircle, CheckCircle2, ChevronRight,
  MapPin, IndianRupee, Layers, Check, X, RefreshCw, BarChart3,
  Activity, ArrowUpRight
} from 'lucide-react';

export default function OwnerDashboard() {
  const toast = useToast();
  const [stations, setStations] = useState([]);
  const [allBookings, setAllBookings] = useState([]);
  const [analytics, setAnalytics] = useState(null);
  const [hoveredHour, setHoveredHour] = useState(null);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(false);

  // Station creation form state
  const [showStationModal, setShowStationModal] = useState(false);
  const [stationName, setStationName] = useState('');
  const [stationAddress, setStationAddress] = useState('');
  const [stationCity, setStationCity] = useState('Vadodara');
  const [stationLat, setStationLat] = useState('22.307200');
  const [stationLng, setStationLng] = useState('73.181200');

  // Charger creation modal state
  const [selectedStation, setSelectedStation] = useState(null);
  const [showChargerModal, setShowChargerModal] = useState(false);
  const [chargerName, setChargerName] = useState('Fast DC Bay 2');
  const [powerKw, setPowerKw] = useState('60.00');
  const [connectorType, setConnectorType] = useState('CCS2');
  const [pricePerKwh, setPricePerKwh] = useState('18.50');

  // Load owned stations, chargers, and analytics
  const loadData = async () => {
    try {
      setLoading(true);
      const [stRes, bRes, analyticsRes] = await Promise.all([
        api.listOwnerStations(),
        api.listBookings().catch(() => ({ content: [] })),
        api.getOwnerAnalytics().catch((err) => {
          console.warn('Owner analytics error:', err);
          return null;
        }),
      ]);

      const stationList = stRes?.content || [];
      const populated = await Promise.all(
        stationList.map(async (st) => {
          try {
            const chargers = await api.listOwnerChargers(st.id);
            return { ...st, chargers: chargers || [] };
          } catch {
            return { ...st, chargers: [] };
          }
        })
      );
      setStations(populated);
      setAllBookings(bRes?.content || []);
      setAnalytics(analyticsRes);
    } catch (err) {
      toast.error(err.message || 'Failed to load station operations');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  // Compute KPI Metrics combining real analytics and local status
  const kpis = useMemo(() => {
    const totalStations = analytics?.overall?.totalStations ?? stations.length;
    let totalChargers = 0;
    let availableChargers = 0;
    let occupiedChargers = 0;
    let offlineChargers = 0;

    stations.forEach((st) => {
      (st.chargers || []).forEach((ch) => {
        totalChargers++;
        if (ch.status === 'AVAILABLE') availableChargers++;
        else if (ch.status === 'OCCUPIED') occupiedChargers++;
        else offlineChargers++;
      });
    });

    const totalRevenue = analytics?.overall?.totalRevenue
      ? Number(analytics.overall.totalRevenue)
      : allBookings
          .filter((b) => b.status === 'COMPLETED' || b.status === 'ACTIVE' || b.status === 'CONFIRMED')
          .reduce((sum, b) => sum + Number(b.totalAmount || b.baseAmount || 0), 0);

    const energyRevenue = Number(analytics?.overall?.totalEnergyRevenue ?? totalRevenue);
    const penaltyRevenue = Number(analytics?.overall?.totalPenaltyRevenue ?? 0);
    const energyPct = totalRevenue > 0 ? Math.round((energyRevenue / totalRevenue) * 100) : 100;
    const penaltyPct = totalRevenue > 0 ? Math.max(0, 100 - energyPct) : 0;

    return {
      totalStations,
      totalChargers: analytics?.overall?.totalChargers ?? totalChargers,
      availableChargers,
      occupiedChargers,
      offlineChargers,
      totalCompletedSessions: analytics?.overall?.totalCompletedSessions ?? allBookings.length,
      totalActiveSessions: analytics?.overall?.totalActiveSessions ?? 0,
      totalRevenue: totalRevenue.toFixed(2),
      energyRevenue: energyRevenue.toFixed(2),
      penaltyRevenue: penaltyRevenue.toFixed(2),
      energyPct,
      penaltyPct,
      avgUtilization: analytics?.overall?.averageChargerUtilizationPercent ?? 0,
      busiestHourLabel: analytics?.overall?.busiestHourLabel ?? 'No sessions yet',
    };
  }, [stations, allBookings, analytics]);

  // Handle station coordinate pick from mini-map
  const handleMapCoordinatePick = (pickLat, pickLng) => {
    setStationLat(pickLat.toFixed(6));
    setStationLng(pickLng.toFixed(6));
    toast.info(`Coordinates updated: (${pickLat.toFixed(4)}, ${pickLng.toFixed(4)})`);
  };

  const handleCreateStation = async (e) => {
    e.preventDefault();
    setActionLoading(true);
    try {
      await api.createStation({
        name: stationName,
        address: stationAddress,
        city: stationCity,
        latitude: parseFloat(stationLat),
        longitude: parseFloat(stationLng),
      });
      toast.success(`Station "${stationName}" created`);
      setShowStationModal(false);
      setStationName('');
      setStationAddress('');
      await loadData();
    } catch (err) {
      toast.error(err.message || 'Failed to create station');
    } finally {
      setActionLoading(false);
    }
  };

  const handleToggleStationStatus = async (station) => {
    setActionLoading(true);
    try {
      const newStatus = station.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
      await api.changeStationStatus(station.id, newStatus);
      toast.success(`Station "${station.name}" marked as ${newStatus}`);
      await loadData();
    } catch (err) {
      toast.error(err.message || 'Failed to update status');
    } finally {
      setActionLoading(false);
    }
  };

  const handleCreateCharger = async (e) => {
    e.preventDefault();
    if (!selectedStation) return;
    setActionLoading(true);
    try {
      await api.createCharger(selectedStation.id, {
        name: chargerName,
        powerKw: parseFloat(powerKw),
        connectorType,
        pricePerKwh: parseFloat(pricePerKwh),
      });
      toast.success(`Charger "${chargerName}" installed in ${selectedStation.name}`);
      setShowChargerModal(false);
      await loadData();
    } catch (err) {
      toast.error(err.message || 'Failed to install charger');
    } finally {
      setActionLoading(false);
    }
  };

  const handleToggleChargerStatus = async (stationId, charger, newStatus) => {
    setActionLoading(true);
    try {
      await api.changeChargerStatus(stationId, charger.id, newStatus);
      toast.success(`Charger status set to ${newStatus}`);
      await loadData();
    } catch (err) {
      toast.error(err.message || 'Failed to update charger status');
    } finally {
      setActionLoading(false);
    }
  };

  return (
    <div className="min-h-[calc(100vh-3.5rem)] bg-[#0B1120] text-slate-100 px-4 sm:px-6 lg:px-8 py-8 space-y-7">
      {/* Top Banner Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 p-6 sm:p-7 rounded-2xl bg-[#1E293B] border border-slate-700/70 shadow-sm">
        <div>
          <div className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-blue-50 text-blue-700 border border-blue-200 text-xs font-semibold mb-2">
            <Building2 className="w-3.5 h-3.5" />
            Station Operations
          </div>
          <h1 className="text-2xl sm:text-3xl font-bold text-slate-100 tracking-tight">
            Charging Infrastructure
          </h1>
          <p className="text-xs sm:text-sm text-slate-400 mt-1">
            Monitor real-time network utilization, deploy high-power bays, and control operational status.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={loadData}
            disabled={loading}
            className="p-2.5 rounded-xl border border-slate-700/70 bg-[#1E293B] text-slate-400 hover:bg-[#111827] hover:text-slate-100 shadow-sm transition-colors"
            title="Refresh network data"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
          </button>
          <button
            onClick={() => setShowStationModal(true)}
            className="flex items-center gap-2 px-4 py-2.5 rounded-xl font-semibold text-xs bg-blue-600 hover:bg-blue-700 text-white transition-all shadow-sm"
          >
            <Plus className="w-4 h-4" />
            <span>Deploy New Station</span>
          </button>
        </div>
      </div>

      {/* Top KPI Metric Cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Total Stations */}
        <div className="p-5 rounded-2xl bg-[#1E293B] border border-slate-700/70 shadow-sm space-y-1">
          <div className="flex items-center justify-between text-slate-400 text-xs font-semibold uppercase tracking-wider">
            <span>Charging Stations</span>
            <Building2 className="w-4 h-4 text-blue-600" />
          </div>
          <div className="text-3xl font-extrabold text-slate-100 font-mono tabular-nums">{kpis.totalStations}</div>
          <div className="text-xs text-slate-400">Live operational hubs</div>
        </div>

        {/* Chargers Status Split */}
        <div className="p-5 rounded-2xl bg-[#1E293B] border border-slate-700/70 shadow-sm space-y-1">
          <div className="flex items-center justify-between text-slate-400 text-xs font-semibold uppercase tracking-wider">
            <span>Charger Bays</span>
            <Zap className="w-4 h-4 text-emerald-600" />
          </div>
          <div className="text-3xl font-extrabold text-slate-100 font-mono tabular-nums">{kpis.totalChargers}</div>
          <div className="flex items-center gap-2 text-xs font-medium pt-0.5">
            <span className="text-emerald-700">⚡ {kpis.availableChargers} Available</span>
            <span className="text-slate-300">•</span>
            <span className="text-amber-700">⏳ {kpis.occupiedChargers} Busy</span>
          </div>
        </div>

        {/* Total Completed Sessions */}
        <div className="p-5 rounded-2xl bg-[#1E293B] border border-slate-700/70 shadow-sm space-y-1">
          <div className="flex items-center justify-between text-slate-400 text-xs font-semibold uppercase tracking-wider">
            <span>Completed Sessions</span>
            <Activity className="w-4 h-4 text-blue-600" />
          </div>
          <div className="text-3xl font-extrabold text-slate-100 font-mono tabular-nums">{kpis.totalCompletedSessions}</div>
          <div className="text-xs text-blue-700 font-medium">
            {kpis.totalActiveSessions > 0 ? `+${kpis.totalActiveSessions} live active` : 'All sessions settled'}
          </div>
        </div>

        {/* Average Charger Utilization */}
        <div className="p-5 rounded-2xl bg-[#1E293B] border border-slate-700/70 shadow-sm space-y-1">
          <div className="flex items-center justify-between text-slate-400 text-xs font-semibold uppercase tracking-wider">
            <span>Charger Utilization</span>
            <BarChart3 className="w-4 h-4 text-amber-600" />
          </div>
          <div className="text-3xl font-extrabold text-slate-100 font-mono tabular-nums">{kpis.avgUtilization}%</div>
          <div className="text-xs text-slate-400">30-day capacity baseline</div>
        </div>
      </div>

      {/* Real Revenue vs Penalty Breakdown & Network Load Analytics */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-stretch">
        {/* Left: Revenue vs Penalty Composition Card */}
        <div className="lg:col-span-6 p-6 sm:p-7 rounded-2xl bg-[#1E293B] border border-slate-700/70 shadow-sm space-y-6 flex flex-col justify-between">
          <div className="space-y-4">
            <div className="flex items-center justify-between border-b border-slate-800 pb-3">
              <div className="flex items-center gap-2">
                <IndianRupee className="w-5 h-5 text-emerald-600" />
                <h3 className="text-base font-bold text-slate-100">Revenue & Penalty Composition</h3>
              </div>
              <span className="text-[11px] font-semibold px-2.5 py-0.5 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200">
                Live Ledger Settle
              </span>
            </div>

            {/* Total Revenue Big Metric */}
            <div className="space-y-1">
              <span className="text-xs text-slate-400 font-semibold uppercase tracking-wider">Gross Platform Earnings</span>
              <div className="text-3xl sm:text-4xl font-extrabold text-slate-100 font-mono tabular-nums">
                ₹{Number(kpis.totalRevenue).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
              </div>
            </div>

            {/* Visual Dual-Color Stacked Revenue Split Bar */}
            <div className="space-y-2 pt-2">
              <div className="flex flex-wrap justify-between items-center gap-2 text-xs font-medium">
                <span className="text-blue-700 flex items-center gap-1.5">
                  <span className="w-2.5 h-2.5 rounded-full bg-blue-600 flex-shrink-0" />
                  Energy Sales: ₹{Number(kpis.energyRevenue).toLocaleString('en-IN', { minimumFractionDigits: 2 })} ({kpis.energyPct}%)
                </span>
                <span className="text-amber-700 flex items-center gap-1.5">
                  <span className="w-2.5 h-2.5 rounded-full bg-amber-500 flex-shrink-0" />
                  Overstay Penalties: ₹{Number(kpis.penaltyRevenue).toLocaleString('en-IN', { minimumFractionDigits: 2 })} ({kpis.penaltyPct}%)
                </span>
              </div>

              <div className="w-full h-3.5 rounded-full bg-[#0F172A] overflow-hidden flex border border-slate-700/70 shadow-inner">
                <div
                  style={{ width: `${kpis.energyPct}%` }}
                  className="bg-blue-600 transition-all duration-500"
                  title={`Energy: ₹${kpis.energyRevenue} (${kpis.energyPct}%)`}
                />
                <div
                  style={{ width: `${kpis.penaltyPct}%` }}
                  className="bg-amber-500 transition-all duration-500"
                  title={`Overstay Penalties: ₹${kpis.penaltyRevenue} (${kpis.penaltyPct}%)`}
                />
              </div>

              <div className="text-xs text-slate-400 flex items-center justify-between pt-1">
                <span>⚡ Base kWh Tariff + Time Hold</span>
                <span>⏱️ Accrued at ₹10/min after 10m grace</span>
              </div>
            </div>
          </div>

          {/* Quick Metrics Grid */}
          <div className="grid grid-cols-3 gap-3 pt-4 border-t border-slate-800">
            <div className="p-3 rounded-xl bg-[#111827] border border-slate-700/70 text-center">
              <span className="text-[10px] text-slate-400 uppercase font-semibold block">Energy Sales</span>
              <span className="text-base font-bold text-blue-700 font-mono tabular-nums">₹{Number(kpis.energyRevenue).toFixed(0)}</span>
              <span className="text-[10px] text-slate-400 font-medium block">{kpis.energyPct}% of gross</span>
            </div>

            <div className="p-3 rounded-xl bg-[#111827] border border-slate-700/70 text-center">
              <span className="text-[10px] text-slate-400 uppercase font-semibold block">Overstay Income</span>
              <span className="text-base font-bold text-amber-700 font-mono tabular-nums">₹{Number(kpis.penaltyRevenue).toFixed(0)}</span>
              <span className="text-[10px] text-slate-400 font-medium block">{kpis.penaltyPct}% of gross</span>
            </div>

            <div className="p-3 rounded-xl bg-[#111827] border border-slate-700/70 text-center">
              <span className="text-[10px] text-slate-400 uppercase font-semibold block">Peak Traffic</span>
              <span className="text-xs font-bold text-slate-800 truncate block mt-0.5" title={kpis.busiestHourLabel}>
                {kpis.busiestHourLabel.split(' ')[0]}
              </span>
              <span className="text-[10px] text-slate-400 font-medium block">busiest hour</span>
            </div>
          </div>
        </div>

        {/* Right: 24-Hour Busiest Hours Network Load Chart */}
        <div className="lg:col-span-6 p-6 sm:p-7 rounded-2xl bg-[#1E293B] border border-slate-700/70 shadow-sm space-y-4 flex flex-col justify-between">
          <div className="space-y-2">
            <div className="flex items-center justify-between border-b border-slate-800 pb-3">
              <div className="flex items-center gap-2">
                <BarChart3 className="w-5 h-5 text-blue-600" />
                <h3 className="text-base font-bold text-slate-100">24-Hour Network Load & Busiest Hours</h3>
              </div>
              <span className="text-xs text-slate-400 font-mono">
                Busiest: {kpis.busiestHourLabel.split('(')[0]}
              </span>
            </div>

            <p className="text-xs text-slate-400">
              Hourly driver session traffic and revenue generation across all your charging hubs.
            </p>

            {/* 24-Bar Hourly Chart */}
            <div className="h-36 flex items-end justify-between gap-1 px-2 bg-[#111827] rounded-xl p-3 border border-slate-700/70 mt-2">
              {(analytics?.overall?.hourlyActivity || Array.from({ length: 24 }, (_, i) => ({ hour: i, sessionCount: 0, revenue: 0 }))).map((slot) => {
                const maxCount = Math.max(1, ...((analytics?.overall?.hourlyActivity || []).map(s => s.sessionCount)));
                const heightPct = slot.sessionCount > 0 ? Math.max(18, Math.round((slot.sessionCount / maxCount) * 100)) : 8;
                const isPeak = slot.hour === analytics?.overall?.busiestHour && slot.sessionCount > 0;

                return (
                  <div
                    key={slot.hour}
                    className="flex-1 flex flex-col items-center h-full justify-end cursor-pointer group relative"
                    onMouseEnter={() => setHoveredHour(slot)}
                    onMouseLeave={() => setHoveredHour(null)}
                  >
                    <div
                      style={{ height: `${heightPct}%` }}
                      className={`w-full rounded-t-md transition-all duration-200 ${
                        isPeak
                          ? 'bg-amber-500 shadow-sm'
                          : slot.sessionCount > 0
                          ? 'bg-blue-600 group-hover:bg-blue-700'
                          : 'bg-slate-200 group-hover:bg-slate-300'
                      }`}
                    />
                    <span className="text-[9px] text-slate-400 font-mono mt-1 opacity-70 group-hover:opacity-100">
                      {slot.hour % 6 === 0 ? `${slot.hour}h` : ''}
                    </span>
                  </div>
                );
              })}
            </div>

            {/* Hover Tooltip / Detail Box */}
            <div className="p-2.5 rounded-xl bg-[#111827] border border-slate-700/70 text-xs flex items-center justify-between text-slate-300">
              {hoveredHour ? (
                <>
                  <span className="font-semibold text-slate-100">Hour {hoveredHour.hour}:00 - {(hoveredHour.hour + 1) % 24}:00</span>
                  <span className="text-blue-700 font-bold font-mono">{hoveredHour.sessionCount} Sessions</span>
                  <span className="text-emerald-700 font-bold font-mono">₹{Number(hoveredHour.revenue || 0).toFixed(2)}</span>
                </>
              ) : (
                <>
                  <span className="text-slate-400">Hover over any hourly bar to inspect session traffic</span>
                  <span className="text-amber-700 font-semibold">★ Peak: {kpis.busiestHourLabel}</span>
                </>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* Stations List */}
      <div className="space-y-6">
        <h3 className="text-lg font-bold text-slate-100 flex items-center gap-2">
          <Building2 className="w-5 h-5 text-blue-600" />
          Your Managed Charging Hubs ({stations.length})
        </h3>

        {loading && stations.length === 0 ? (
          <div className="text-center py-16">
            <div className="w-8 h-8 border-3 border-blue-600 border-t-transparent rounded-full animate-spin mx-auto mb-3" />
            <p className="text-xs text-slate-400 font-medium">Loading charging hubs...</p>
          </div>
        ) : stations.length === 0 ? (
          <div className="text-center py-16 bg-[#1E293B] border border-dashed border-slate-700 rounded-2xl p-8">
            <Building2 className="w-12 h-12 text-slate-400 mx-auto mb-3" />
            <h4 className="text-base font-bold text-slate-100">No Stations Created</h4>
            <p className="text-xs text-slate-400 max-w-sm mx-auto mt-1 mb-4">
              Deploy your first public EV charging station to start accepting reservations from drivers.
            </p>
            <button
              onClick={() => setShowStationModal(true)}
              className="px-4 py-2 rounded-xl text-xs font-semibold bg-blue-600 text-white hover:bg-blue-700 transition-colors shadow-sm"
            >
              Deploy First Station
            </button>
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-6">
            {stations.map((st) => (
              <div
                key={st.id}
                className="p-6 sm:p-7 rounded-2xl bg-[#1E293B] border border-slate-700/70 shadow-sm space-y-6"
              >
                {/* Station Card Header */}
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-slate-800 pb-5">
                  <div className="space-y-1">
                    <div className="flex items-center gap-3">
                      <h3 className="text-lg font-bold text-slate-100">{st.name}</h3>
                      <span
                        className={`text-[10px] font-bold uppercase tracking-wider px-2.5 py-0.5 rounded-full border ${
                          st.status === 'ACTIVE'
                            ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                            : 'bg-[#0F172A] text-slate-400 border-slate-700/70'
                        }`}
                      >
                        {st.status}
                      </span>
                    </div>
                    <div className="flex flex-wrap items-center gap-4 text-xs text-slate-400">
                      <span className="flex items-center gap-1">
                        <MapPin className="w-3.5 h-3.5 text-slate-400" />
                        {st.address}, {st.city}
                      </span>
                      <span className="text-slate-400 font-mono">
                        ({Number(st.latitude).toFixed(4)}, {Number(st.longitude).toFixed(4)})
                      </span>
                    </div>
                  </div>

                  {/* Actions */}
                  <div className="flex items-center gap-3">
                    {/* Active/Inactive Toggle Switch */}
                    <button
                      type="button"
                      onClick={() => handleToggleStationStatus(st)}
                      disabled={actionLoading}
                      className={`px-3 py-1.5 rounded-xl text-xs font-medium border transition-all ${
                        st.status === 'ACTIVE'
                          ? 'border-slate-700/70 text-slate-400 hover:bg-[#111827]'
                          : 'border-emerald-300 text-emerald-700 hover:bg-emerald-50'
                      }`}
                    >
                      {st.status === 'ACTIVE' ? 'Set Offline' : 'Set Active'}
                    </button>

                    <button
                      type="button"
                      onClick={() => {
                        setSelectedStation(st);
                        setShowChargerModal(true);
                      }}
                      className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-semibold bg-blue-50 text-blue-700 hover:bg-blue-100 border border-blue-200 transition-all"
                    >
                      <Plus className="w-3.5 h-3.5" />
                      <span>Add Charger</span>
                    </button>
                  </div>
                </div>

                {/* Station Performance Ribbon */}
                {(() => {
                  const stAnalytics = analytics?.stations?.find((s) => s.stationId === st.id);
                  const stEnergy = Number(stAnalytics?.energyRevenue ?? 0);
                  const stPenalty = Number(stAnalytics?.penaltyRevenue ?? 0);
                  const stTotal = Number(stAnalytics?.totalRevenue ?? (stEnergy + stPenalty));
                  const stEnergyPct = stTotal > 0 ? Math.round((stEnergy / stTotal) * 100) : 100;
                  const stPenaltyPct = stTotal > 0 ? Math.max(0, 100 - stEnergyPct) : 0;

                  return (
                    <div className="p-4 rounded-xl bg-[#111827] border border-slate-700/70 space-y-3">
                      <div className="flex flex-wrap items-center justify-between gap-2 text-xs">
                        <span className="font-semibold text-slate-300 uppercase tracking-wider text-[11px] flex items-center gap-1.5">
                          <BarChart3 className="w-3.5 h-3.5 text-blue-600" />
                          Hub Revenue & Performance
                        </span>
                        <div className="flex items-center gap-2">
                          <span className="text-slate-400">
                            Utilization: <strong className="text-slate-800 font-mono">{stAnalytics?.utilizationPercent ?? 0}%</strong>
                          </span>
                          <span className="text-slate-300">•</span>
                          <span className="text-slate-400">
                            Sessions: <strong className="text-slate-800 font-mono">{stAnalytics?.completedSessions ?? 0}</strong> completed
                          </span>
                          <span className="text-slate-300">•</span>
                          <span className="text-amber-700 font-medium">
                            Peak: {stAnalytics?.busiestHourLabel ? stAnalytics.busiestHourLabel.split('(')[0] : 'N/A'}
                          </span>
                        </div>
                      </div>

                      {/* Station Financial Breakdown */}
                      <div className="grid grid-cols-1 sm:grid-cols-3 gap-2.5 pt-1">
                        <div className="p-2.5 rounded-lg bg-[#1E293B] border border-slate-700/70 flex justify-between items-center text-xs">
                          <span className="text-slate-400 text-[11px]">Energy Sales</span>
                          <span className="font-bold text-blue-700 font-mono tabular-nums">₹{stEnergy.toFixed(2)}</span>
                        </div>
                        <div className="p-2.5 rounded-lg bg-[#1E293B] border border-slate-700/70 flex justify-between items-center text-xs">
                          <span className="text-slate-400 text-[11px]">Overstay Penalties</span>
                          <span className="font-bold text-amber-700 font-mono tabular-nums">₹{stPenalty.toFixed(2)}</span>
                        </div>
                        <div className="p-2.5 rounded-lg bg-[#1E293B] border border-slate-700/70 flex justify-between items-center text-xs">
                          <span className="text-slate-400 text-[11px]">Station Total</span>
                          <span className="font-bold text-slate-100 font-mono tabular-nums">₹{stTotal.toFixed(2)}</span>
                        </div>
                      </div>

                      {/* Mini Dual-Color Revenue Bar */}
                      {stTotal > 0 && (
                        <div className="space-y-1">
                          <div className="w-full h-2 rounded-full bg-slate-200 overflow-hidden flex">
                            <div style={{ width: `${stEnergyPct}%` }} className="bg-blue-600" title={`Energy: ${stEnergyPct}%`} />
                            <div style={{ width: `${stPenaltyPct}%` }} className="bg-amber-500" title={`Overstay: ${stPenaltyPct}%`} />
                          </div>
                          <div className="flex justify-between text-[10px] text-slate-400">
                            <span>Energy: {stEnergyPct}%</span>
                            <span>Overstay: {stPenaltyPct}%</span>
                          </div>
                        </div>
                      )}
                    </div>
                  );
                })()}

                {/* Chargers Inside Station */}
                <div>
                  <div className="flex items-center justify-between mb-3 text-xs font-semibold uppercase tracking-wider text-slate-400">
                    <span className="flex items-center gap-1.5">
                      <Zap className="w-3.5 h-3.5 text-blue-600" />
                      Station Charger Bays ({st.chargers?.length || 0})
                    </span>
                  </div>

                  {(!st.chargers || st.chargers.length === 0) ? (
                    <div className="p-4 rounded-xl bg-[#111827] border border-slate-700/70 text-center text-xs text-slate-400">
                      No chargers configured for this station yet. Click "Add Charger" above to install one.
                    </div>
                  ) : (
                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                      {st.chargers.map((ch) => (
                        <div
                          key={ch.id}
                          className="p-4 rounded-xl bg-[#111827] border border-slate-700/70 flex flex-col justify-between space-y-3"
                        >
                          <div className="flex items-start justify-between">
                            <div>
                              <div className="font-semibold text-sm text-slate-100">{ch.name}</div>
                              <span className="text-xs font-mono font-semibold text-blue-600">
                                {ch.connectorType}
                              </span>
                            </div>
                            <span
                              className={`text-[10px] font-bold uppercase px-2 py-0.5 rounded-full border ${
                                ch.status === 'AVAILABLE'
                                  ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                                  : ch.status === 'OCCUPIED'
                                  ? 'bg-amber-50 text-amber-800 border-amber-200'
                                  : 'bg-[#0F172A] text-slate-400 border-slate-700/70'
                              }`}
                            >
                              {ch.status}
                            </span>
                          </div>

                          <div className="grid grid-cols-2 gap-2 text-xs pt-2 border-t border-slate-700/70">
                            <div>
                              <span className="text-[10px] text-slate-400 block">Rated Power</span>
                              <span className="font-bold text-slate-100 font-mono tabular-nums">{ch.powerKw} kW</span>
                            </div>
                            <div>
                              <span className="text-[10px] text-slate-400 block">Tariff</span>
                              <span className="font-bold text-emerald-700 font-mono tabular-nums">₹{ch.pricePerKwh}/kWh</span>
                            </div>
                          </div>

                          {/* Quick 1-Click Status Controls */}
                          <div className="flex items-center gap-1.5 pt-1">
                            <button
                              onClick={() => handleToggleChargerStatus(st.id, ch, 'AVAILABLE')}
                              className={`flex-1 py-1 text-[11px] font-semibold rounded-lg transition-all ${
                                ch.status === 'AVAILABLE'
                                  ? 'bg-emerald-600 text-white shadow-sm'
                                  : 'bg-[#1E293B] text-slate-400 border border-slate-700/70 hover:text-slate-100'
                              }`}
                            >
                              Available
                            </button>
                            <button
                              onClick={() => handleToggleChargerStatus(st.id, ch, 'OCCUPIED')}
                              className={`flex-1 py-1 text-[11px] font-semibold rounded-lg transition-all ${
                                ch.status === 'OCCUPIED'
                                  ? 'bg-amber-600 text-white shadow-sm'
                                  : 'bg-[#1E293B] text-slate-400 border border-slate-700/70 hover:text-slate-100'
                              }`}
                            >
                              Occupied
                            </button>
                            <button
                              onClick={() => handleToggleChargerStatus(st.id, ch, 'OUT_OF_SERVICE')}
                              className={`flex-1 py-1 text-[11px] font-semibold rounded-lg transition-all ${
                                ch.status === 'OUT_OF_SERVICE'
                                  ? 'bg-slate-700 text-white shadow-sm'
                                  : 'bg-[#1E293B] text-slate-400 border border-slate-700/70 hover:text-slate-100'
                              }`}
                            >
                              Offline
                            </button>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Modal: Deploy Station with Interactive Mini-Map */}
      {showStationModal && (
        <div className="fixed inset-0 z-[9990] flex items-center justify-center p-4 bg-slate-950/75 backdrop-blur-sm">
          <div className="w-full max-w-2xl bg-[#1E293B] border border-slate-700/70 rounded-2xl p-6 sm:p-7 shadow-2xl space-y-5 max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between border-b border-slate-800 pb-3">
              <h3 className="text-base font-bold text-slate-100 flex items-center gap-2">
                <Building2 className="w-5 h-5 text-blue-600" />
                Deploy Charging Station
              </h3>
              <button onClick={() => setShowStationModal(false)} className="text-slate-400 hover:text-slate-400">
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleCreateStation} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">Station Name</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Alkapuri Supercharge Hub"
                  value={stationName}
                  onChange={(e) => setStationName(e.target.value)}
                  className="w-full px-3.5 py-2.5 bg-[#1E293B] border border-slate-700/70 rounded-xl text-xs text-slate-100 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-600/20 focus:border-blue-600"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">Street Address</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Sayajigunj, Station Road"
                  value={stationAddress}
                  onChange={(e) => setStationAddress(e.target.value)}
                  className="w-full px-3.5 py-2.5 bg-[#1E293B] border border-slate-700/70 rounded-xl text-xs text-slate-100 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-600/20 focus:border-blue-600"
                />
              </div>

              <div className="grid grid-cols-3 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-slate-300 mb-1">City</label>
                  <input
                    type="text"
                    required
                    value={stationCity}
                    onChange={(e) => setStationCity(e.target.value)}
                    className="w-full px-3 py-2 bg-[#1E293B] border border-slate-700/70 rounded-xl text-xs text-slate-100"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-slate-300 mb-1">Latitude</label>
                  <input
                    type="number"
                    step="0.000001"
                    required
                    value={stationLat}
                    onChange={(e) => setStationLat(e.target.value)}
                    className="w-full px-3 py-2 bg-[#1E293B] border border-slate-700/70 rounded-xl text-xs text-slate-100 font-mono"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-slate-300 mb-1">Longitude</label>
                  <input
                    type="number"
                    step="0.000001"
                    required
                    value={stationLng}
                    onChange={(e) => setStationLng(e.target.value)}
                    className="w-full px-3 py-2 bg-[#1E293B] border border-slate-700/70 rounded-xl text-xs text-slate-100 font-mono"
                  />
                </div>
              </div>

              {/* Interactive Mini-Map for Pinpointing Location */}
              <div className="space-y-1.5">
                <div className="flex items-center justify-between text-xs text-slate-400">
                  <span className="font-semibold flex items-center gap-1 text-slate-300">
                    <MapPin className="w-3.5 h-3.5 text-blue-600" />
                    Interactive Coordinate Picker (Click anywhere on map)
                  </span>
                  <span>Presets:</span>
                </div>

                {/* Preset City buttons */}
                <div className="flex gap-2 mb-2">
                  {[
                    { name: 'Vadodara', lat: '22.307200', lng: '73.181200' },
                    { name: 'Ahmedabad', lat: '23.022500', lng: '72.571400' },
                    { name: 'Pune', lat: '18.520400', lng: '73.856700' },
                    { name: 'Mumbai', lat: '19.076000', lng: '72.877700' },
                  ].map((city) => (
                    <button
                      key={city.name}
                      type="button"
                      onClick={() => {
                        setStationCity(city.name);
                        setStationLat(city.lat);
                        setStationLng(city.lng);
                      }}
                      className="px-2.5 py-1 text-xs rounded-lg bg-[#0F172A] text-slate-300 hover:bg-slate-200 border border-slate-700/70 transition-colors"
                    >
                      {city.name}
                    </button>
                  ))}
                </div>

                <MapComponent
                  userLat={stationLat}
                  userLng={stationLng}
                  onMapClick={handleMapCoordinatePick}
                  height="220px"
                  zoom={12}
                />
              </div>

              <div className="flex justify-end gap-3 pt-3 border-t border-slate-800">
                <button
                  type="button"
                  onClick={() => setShowStationModal(false)}
                  className="px-4 py-2 text-xs font-semibold text-slate-400 hover:text-slate-100"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={actionLoading}
                  className="px-5 py-2.5 rounded-xl text-xs font-semibold bg-blue-600 hover:bg-blue-700 text-white transition-all shadow-sm"
                >
                  {actionLoading ? 'Deploying...' : 'Deploy Station'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal: Add Charger */}
      {showChargerModal && selectedStation && (
        <div className="fixed inset-0 z-[9990] flex items-center justify-center p-4 bg-slate-950/75 backdrop-blur-sm">
          <div className="w-full max-w-md bg-[#1E293B] border border-slate-700/70 rounded-2xl p-6 shadow-2xl space-y-4">
            <div className="flex items-center justify-between border-b border-slate-800 pb-3">
              <div>
                <h3 className="text-base font-bold text-slate-100 flex items-center gap-2">
                  <Zap className="w-4 h-4 text-blue-600" />
                  Install Charger Bay
                </h3>
                <span className="text-xs text-slate-400">{selectedStation.name}</span>
              </div>
              <button onClick={() => setShowChargerModal(false)} className="text-slate-400 hover:text-slate-400">
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleCreateCharger} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">Bay / Charger Name</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Fast DC Bay 2"
                  value={chargerName}
                  onChange={(e) => setChargerName(e.target.value)}
                  className="w-full px-3.5 py-2.5 bg-[#1E293B] border border-slate-700/70 rounded-xl text-xs text-slate-100 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-600/20 focus:border-blue-600"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-slate-300 mb-1">Rated Power (kW)</label>
                  <input
                    type="number"
                    step="0.01"
                    min="1"
                    max="500"
                    required
                    value={powerKw}
                    onChange={(e) => setPowerKw(e.target.value)}
                    className="w-full px-3 py-2 bg-[#1E293B] border border-slate-700/70 rounded-xl text-xs text-slate-100 font-mono"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-slate-300 mb-1">Connector Type</label>
                  <select
                    value={connectorType}
                    onChange={(e) => setConnectorType(e.target.value)}
                    className="w-full px-3 py-2 bg-[#1E293B] border border-slate-700/70 rounded-xl text-xs text-slate-100"
                  >
                    <option value="CCS2">CCS2 (DC Fast)</option>
                    <option value="TYPE2">TYPE2 (AC Standard)</option>
                    <option value="CHADEMO">CHADEMO</option>
                    <option value="GB_T">GB_T</option>
                  </select>
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">Tariff Rate (₹/kWh)</label>
                <input
                  type="number"
                  step="0.01"
                  min="0.01"
                  required
                  value={pricePerKwh}
                  onChange={(e) => setPricePerKwh(e.target.value)}
                  className="w-full px-3 py-2 bg-[#1E293B] border border-slate-700/70 rounded-xl text-xs text-slate-100 font-mono"
                />
              </div>

              <div className="flex justify-end gap-3 pt-3 border-t border-slate-800">
                <button
                  type="button"
                  onClick={() => setShowChargerModal(false)}
                  className="px-4 py-2 text-xs font-semibold text-slate-400 hover:text-slate-100"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={actionLoading}
                  className="px-5 py-2.5 rounded-xl text-xs font-semibold bg-blue-600 hover:bg-blue-700 text-white transition-all shadow-sm"
                >
                  {actionLoading ? 'Installing...' : 'Install Bay'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
