import React, { useState, useEffect, useMemo } from 'react';
import { api } from '../api/client';
import { useToast } from '../context/ToastContext';
import MapComponent from '../components/MapComponent';
import SlotPickerModal from '../components/SlotPickerModal';
import {
  Car, Zap, Navigation, Clock, CreditCard, Play, CheckCircle2,
  AlertTriangle, Plus, RefreshCw, X, IndianRupee, BatteryCharging,
  PowerOff, Sparkles, MapPin, Sliders, Compass, Battery, Mountain,
  FastForward, Route, ArrowRight
} from 'lucide-react';

const RUPEE = '\u20B9';
const ARROW = '\u2192';
const BULLET = '\u2022';

export default function DriverDashboard({
  activeTab = 'finder',
  setActiveTab = () => {},
  onBookingCountChange = () => {}
}) {
  const toast = useToast();

  // Data states
  const [vehicles, setVehicles] = useState([]);
  const [publicStations, setPublicStations] = useState([]);
  const [bookings, setBookings] = useState([]);
  const [recommendations, setRecommendations] = useState(null);

  // Loading states
  const [loading, setLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);

  // Add Vehicle Modal state
  const [showVehicleModal, setShowVehicleModal] = useState(false);
  const [vModel, setVModel] = useState('Tesla Model 3');
  const [vBattery, setVBattery] = useState('60.00');
  const [vConsumption, setVConsumption] = useState('0.150');
  const [vMaxPower, setVMaxPower] = useState('150.00');
  const [vConnector, setVConnector] = useState('CCS2');

  // Smart Finder Form state
  const [selectedVehicleId, setSelectedVehicleId] = useState('');
  const [lat, setLat] = useState('22.307200');
  const [lng, setLng] = useState('73.181200');
  const [cityPreset, setCityPreset] = useState('Vadodara');
  const [batteryPercent, setBatteryPercent] = useState('25.00');
  const [targetPercent, setTargetPercent] = useState('80.00');
  const [acOn, setAcOn] = useState(true);
  const [speedKmph, setSpeedKmph] = useState(65);
  const [terrain, setTerrain] = useState('FLAT'); // 'FLAT' | 'HILLY' | 'HIGHWAY'

  // Connector Filter Pill state
  const [connectorFilter, setConnectorFilter] = useState('ALL'); // 'ALL' | 'CCS2' | 'TYPE2' | 'CHADEMO'

  // Route destination on map (for glowing polyline)
  const [selectedRouteDest, setSelectedRouteDest] = useState(null);

  // Slot Booking Modal state
  const [selectedRecForBooking, setSelectedRecForBooking] = useState(null);

  // Highway Trip Planner (Dijkstra) states
  const [tripPreset, setTripPreset] = useState('vadodara-mumbai');
  const [tripStartCity, setTripStartCity] = useState('Vadodara');
  const [tripStartLat, setTripStartLat] = useState('22.307200');
  const [tripStartLng, setTripStartLng] = useState('73.181200');
  const [tripDestCity, setTripDestCity] = useState('Mumbai');
  const [tripDestLat, setTripDestLat] = useState('19.076000');
  const [tripDestLng, setTripDestLng] = useState('72.877700');
  const [tripBatteryPercent, setTripBatteryPercent] = useState('30.00');
  const [tripSpeedKmph, setTripSpeedKmph] = useState(85);
  const [tripAcOn, setTripAcOn] = useState(true);
  const [tripTerrain, setTripTerrain] = useState('HIGHWAY');
  const [tripPlanResult, setTripPlanResult] = useState(null);
  const [tripLoading, setTripLoading] = useState(false);
  const [tripError, setTripError] = useState(null);

  // Grounded AI Trip Co-Pilot states
  const [aiPrompt, setAiPrompt] = useState('');
  const [aiLoading, setAiLoading] = useState(false);
  const [aiLastResult, setAiLastResult] = useState(null);

  const AI_PROMPT_SUGGESTIONS = [
    {
      label: `Vadodara ${ARROW} Mumbai (22% SOC, AC on)`,
      prompt: 'I am in Vadodara with 22% battery, AC on, driving to Mumbai at 80 km/h',
    },
    {
      label: `Vadodara ${ARROW} Ahmedabad (35% SOC, 90 km/h)`,
      prompt: 'Drive from Vadodara to Ahmedabad with 35% battery, AC on, at 90 km/h',
    },
    {
      label: 'Find Chargers in Vadodara (15% SOC)',
      prompt: 'Find chargers in Vadodara with 15% battery and AC off',
    },
    {
      label: `Pune ${ARROW} Mumbai (40% SOC, Hilly)`,
      prompt: 'From Pune heading to Mumbai hilly terrain at 85 km/h with 40% battery',
    },
  ];

  const CORRIDOR_PRESETS = [
    {
      id: 'vadodara-mumbai',
      label: `Vadodara ${ARROW} Mumbai`,
      distance: '~410 km',
      startCity: 'Vadodara',
      startLat: '22.307200',
      startLng: '73.181200',
      destCity: 'Mumbai',
      destLat: '19.076000',
      destLng: '72.877700',
    },
    {
      id: 'vadodara-ahmedabad',
      label: `Vadodara ${ARROW} Ahmedabad`,
      distance: '~110 km',
      startCity: 'Vadodara',
      startLat: '22.307200',
      startLng: '73.181200',
      destCity: 'Ahmedabad',
      destLat: '23.022500',
      destLng: '72.571400',
    },
    {
      id: 'pune-mumbai',
      label: `Pune ${ARROW} Mumbai`,
      distance: '~150 km',
      startCity: 'Pune',
      startLat: '18.520400',
      startLng: '73.856700',
      destCity: 'Mumbai',
      destLat: '19.076000',
      destLng: '72.877700',
    },
    {
      id: 'pune-ahmedabad',
      label: `Pune ${ARROW} Ahmedabad`,
      distance: '~660 km',
      startCity: 'Pune',
      startLat: '18.520400',
      startLng: '73.856700',
      destCity: 'Ahmedabad',
      destLat: '23.022500',
      destLng: '72.571400',
    },
    {
      id: 'unreachable-demo',
      label: `Vadodara ${ARROW} Leh (Unreachable Gap Demo)`,
      distance: '~1,700 km',
      startCity: 'Vadodara',
      startLat: '22.307200',
      startLng: '73.181200',
      destCity: 'Leh Ladakh',
      destLat: '34.152600',
      destLng: '77.577100',
    },
  ];

  const handleSelectCorridor = (preset) => {
    setTripPreset(preset.id);
    setTripStartCity(preset.startCity);
    setTripStartLat(preset.startLat);
    setTripStartLng(preset.startLng);
    setTripDestCity(preset.destCity);
    setTripDestLat(preset.destLat);
    setTripDestLng(preset.destLng);
    setTripPlanResult(null);
    setTripError(null);
  };

  // Live Timer ticker for Active Sessions
  const [currentTimestamp, setCurrentTimestamp] = useState(Date.now());
  useEffect(() => {
    const timer = setInterval(() => setCurrentTimestamp(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);

  // Initial load
  const loadInitialData = async () => {
    try {
      setLoading(true);
      const [vRes, bRes, stRes] = await Promise.all([
        api.listVehicles(),
        api.listBookings(),
        api.listPublicStations(),
      ]);
      const vList = vRes?.content || [];
      setVehicles(vList);
      if (vList.length > 0 && !selectedVehicleId) {
        setSelectedVehicleId(vList[0].id.toString());
      }
      const bList = bRes?.content || [];
      setBookings(bList);
      onBookingCountChange(bList.length);
      setPublicStations(stRes?.content || []);
    } catch (err) {
      toast.error(err.message || 'Failed to initialize driver hub');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadInitialData();
  }, []);

  const refreshBookings = async () => {
    try {
      const bRes = await api.listBookings();
      const bList = bRes?.content || [];
      setBookings(bList);
      onBookingCountChange(bList.length);
    } catch (err) {
      console.error(err);
    }
  };

  // Preset City Quick-Jump
  const setCity = (name, cLat, cLng) => {
    setCityPreset(name);
    setLat(cLat);
    setLng(cLng);
    if (activeTab === 'trips') {
      setTripStartCity(name);
      setTripStartLat(cLat);
      setTripStartLng(cLng);
    }
    setSelectedRouteDest(null);
    toast.info(`Map centered on ${name}`);
  };

  // Map Click Handler: Update Driver Pin
  const handleMapClick = (newLat, newLng) => {
    setLat(newLat.toFixed(6));
    setLng(newLng.toFixed(6));
    setCityPreset('Custom Pin');
  };

  // Add Vehicle Handler
  const handleAddVehicle = async (e) => {
    e.preventDefault();
    setActionLoading(true);
    try {
      const newV = await api.createVehicle({
        model: vModel,
        usableBatteryKwh: parseFloat(vBattery),
        baseConsumptionKwhPerKm: parseFloat(vConsumption),
        maxChargeKw: parseFloat(vMaxPower),
        connectorType: vConnector,
      });
      toast.success(`EV "${newV.model}" added to garage!`);
      setShowVehicleModal(false);
      const vRes = await api.listVehicles();
      const vList = vRes?.content || [];
      setVehicles(vList);
      setSelectedVehicleId(newV.id.toString());
    } catch (err) {
      toast.error(err.message || 'Failed to register EV');
    } finally {
      setActionLoading(false);
    }
  };

  // Dynamic Safe-Range Calculation
  const currentVehicle = vehicles.find((v) => v.id.toString() === selectedVehicleId);
  const safeRangeKm = useMemo(() => {
    if (!currentVehicle) return 120;
    const terrainMultiplier = terrain === 'HILLY' ? 1.25 : terrain === 'HIGHWAY' ? 1.15 : 1.0;
    const acMultiplier = acOn ? 1.15 : 1.0;
    const speedMultiplier = speedKmph > 75 ? 1.15 : speedKmph > 60 ? 1.05 : 0.95;
    const effConsumption = currentVehicle.baseConsumptionKwhPerKm * terrainMultiplier * acMultiplier * speedMultiplier;
    const usableEnergy = currentVehicle.usableBatteryKwh * (parseFloat(batteryPercent) / 100);
    return Math.max(5, usableEnergy / effConsumption);
  }, [currentVehicle, terrain, acOn, speedKmph, batteryPercent]);

  // Smart Recommendations Query
  const handleFindChargers = async (e) => {
    e?.preventDefault();
    if (!selectedVehicleId) {
      toast.error('Please add or select an EV first.');
      return;
    }
    setActionLoading(true);
    try {
      const result = await api.getRecommendations({
        vehicleId: parseInt(selectedVehicleId),
        lat: parseFloat(lat),
        lng: parseFloat(lng),
        batteryPercent: parseFloat(batteryPercent),
        targetPercent: parseFloat(targetPercent),
        acOn: Boolean(acOn),
        speedKmph: parseInt(speedKmph),
        topK: 6,
      });
      setRecommendations(result);
      if (!result.recommendations || result.recommendations.length === 0) {
        toast.info(result.message || 'No compatible chargers found in range.');
      } else {
        toast.success(`Found ${result.recommendations.length} ranked chargers!`);
        if (result.recommendations[0]) {
          setSelectedRouteDest([result.recommendations[0].latitude, result.recommendations[0].longitude]);
        }
      }
    } catch (err) {
      toast.error(err.message || 'Failed to fetch recommendations');
    } finally {
      setActionLoading(false);
    }
  };

  // Filter recommendations by connector
  const filteredRecommendations = useMemo(() => {
    if (!recommendations?.recommendations) return [];
    if (connectorFilter === 'ALL') return recommendations.recommendations;
    return recommendations.recommendations.filter((r) => r.connectorType === connectorFilter);
  }, [recommendations, connectorFilter]);

  // Find min cost among recommendations for badge highlighting
  const minCostAmongRecs = useMemo(() => {
    if (!filteredRecommendations.length) return 0;
    return Math.min(...filteredRecommendations.map((r) => Number(r.estimatedCost || 0)));
  }, [filteredRecommendations]);

  // Handle Slot Booking Confirmation
  const handleConfirmReservation = async (startTime, endTime) => {
    if (!selectedRecForBooking || !selectedVehicleId) return;
    setActionLoading(true);
    try {
      const booking = await api.createBooking({
        chargerId: selectedRecForBooking.chargerId,
        vehicleId: parseInt(selectedVehicleId),
        startTime,
        endTime,
      });
      toast.success(
        `Bay reserved! Booking #${booking.id} is held for 5 minutes under PENDING_PAYMENT.`
      );
      setSelectedRecForBooking(null);
      await refreshBookings();
      setActiveTab('bookings');
    } catch (err) {
      toast.error(err.message || 'Failed to reserve slot');
    } finally {
      setActionLoading(false);
    }
  };

  // Session simulator action handlers
  const handleConfirmPayment = async (bookingId) => {
    setActionLoading(true);
    try {
      await api.confirmPayment(bookingId);
      toast.success(`Payment pre-authorized! Booking #${bookingId} is now CONFIRMED.`);
      await refreshBookings();
    } catch (err) {
      toast.error(err.message || 'Payment confirmation failed');
    } finally {
      setActionLoading(false);
    }
  };

  const handleStartSession = async (bookingId) => {
    setActionLoading(true);
    try {
      await api.startSession(bookingId);
      toast.success(`Plugged in! Session ACTIVE for Booking #${bookingId}.`);
      await refreshBookings();
    } catch (err) {
      toast.error(err.message || 'Failed to start charging');
    } finally {
      setActionLoading(false);
    }
  };

  const handleCompleteCharging = async (bookingId) => {
    setActionLoading(true);
    try {
      await api.completeCharging(bookingId);
      toast.info(`Charge completed for #${bookingId}. 10-minute overstay grace period active!`);
      await refreshBookings();
    } catch (err) {
      toast.error(err.message || 'Failed to complete charging');
    } finally {
      setActionLoading(false);
    }
  };

  const handleSimulateOverstay = async (bookingId) => {
    setActionLoading(true);
    try {
      const updated = await api.simulateOverstay(bookingId, 18);
      toast.info(`Time-warped +18m! Grace period expired; penalty accrued: ${RUPEE}${updated.overstayPenalty}`);
      await refreshBookings();
    } catch (err) {
      toast.error(err.message || 'Simulation helper failed');
    } finally {
      setActionLoading(false);
    }
  };

  const handlePlugOut = async (bookingId) => {
    setActionLoading(true);
    try {
      await api.plugOut(bookingId);
      toast.success(`Plugged out! Booking #${bookingId} COMPLETED and settled.`);
      await refreshBookings();
    } catch (err) {
      toast.error(err.message || 'Failed to plug out');
    } finally {
      setActionLoading(false);
    }
  };

  // Highway Trip Planner Action (Dijkstra)
  const handlePlanHighwayTrip = async (e) => {
    e?.preventDefault();
    if (!selectedVehicleId) {
      toast.error('Please add or select an EV first.');
      return;
    }
    setTripLoading(true);
    setTripError(null);
    try {
      const result = await api.planTrip({
        vehicleId: parseInt(selectedVehicleId),
        startLat: parseFloat(tripStartLat),
        startLng: parseFloat(tripStartLng),
        destLat: parseFloat(tripDestLat),
        destLng: parseFloat(tripDestLng),
        currentBatteryPercent: parseFloat(tripBatteryPercent),
        acOn: Boolean(tripAcOn),
        speedKmh: parseInt(tripSpeedKmph),
        terrain: tripTerrain,
      });
      setTripPlanResult(result);
      toast.success(
        `Optimized highway route found: ${result.totalStops} stop${result.totalStops === 1 ? '' : 's'} (${Math.round(result.totalTripMinutes)} min total)!`
      );
    } catch (err) {
      const msg = err.message || 'Highway route planning failed';
      setTripError(msg);
      toast.error(msg);
    } finally {
      setTripLoading(false);
    }
  };

  const handleBookTripStop = (stop) => {
    setSelectedRecForBooking({
      chargerId: stop.recommendedChargerId,
      chargerName: stop.chargerName,
      stationName: stop.stationName,
      city: stop.city,
      connectorType: stop.connectorType,
      powerKw: stop.powerKw,
      pricePerKwh: 18,
    });
  };

  const handleAskCoPilot = async (customPrompt) => {
    const textToSubmit = (typeof customPrompt === 'string' ? customPrompt : aiPrompt).trim();
    if (!textToSubmit) {
      toast.error('Please enter a travel prompt for Co-Pilot.');
      return;
    }

    setAiLoading(true);
    try {
      const vId = selectedVehicleId ? parseInt(selectedVehicleId, 10) : null;
      const response = await api.parseTripPrompt(textToSubmit, vId, true);
      setAiLastResult(response);
      toast.success(response.summary || 'Co-Pilot parsed your travel parameters!');

      if (response.intent === 'HIGHWAY_TRIP') {
        setActiveTab('trips');
        if (response.originCity) setTripStartCity(response.originCity);
        if (response.startLat) setTripStartLat(response.startLat.toString());
        if (response.startLng) setTripStartLng(response.startLng.toString());
        if (response.destinationCity) setTripDestCity(response.destinationCity);
        if (response.destLat) setTripDestLat(response.destLat.toString());
        if (response.destLng) setTripDestLng(response.destLng.toString());
        if (response.currentBatteryPercent) setTripBatteryPercent(response.currentBatteryPercent.toString());
        if (response.speedKmh) setTripSpeedKmph(response.speedKmh);
        if (response.acOn !== undefined) setTripAcOn(response.acOn);
        if (response.terrain) setTripTerrain(response.terrain);

        if (response.plannedTrip) {
          setTripPlanResult(response.plannedTrip);
          setTripError(null);
        } else {
          const effectiveVId = vId || (vehicles.length > 0 ? vehicles[0].id : null);
          if (effectiveVId) {
            try {
              const directPlan = await api.planTrip({
                vehicleId: effectiveVId,
                startLat: parseFloat(response.startLat?.toString() || tripStartLat),
                startLng: parseFloat(response.startLng?.toString() || tripStartLng),
                destLat: parseFloat(response.destLat?.toString() || tripDestLat),
                destLng: parseFloat(response.destLng?.toString() || tripDestLng),
                currentBatteryPercent: parseFloat(response.currentBatteryPercent?.toString() || tripBatteryPercent),
                acOn: response.acOn !== undefined ? Boolean(response.acOn) : Boolean(tripAcOn),
                speedKmh: response.speedKmh || parseInt(tripSpeedKmph),
                terrain: response.terrain || tripTerrain,
              });
              setTripPlanResult(directPlan);
              setTripError(null);
            } catch (pErr) {
              setTripError(pErr.message || 'Route planning failed');
            }
          }
        }
      } else {
        // LOCAL_CHARGER_SEARCH
        setActiveTab('finder');
        const qLat = response.startLat ? response.startLat.toString() : lat;
        const qLng = response.startLng ? response.startLng.toString() : lng;
        const qBat = response.currentBatteryPercent ? response.currentBatteryPercent.toString() : batteryPercent;
        const qAc = response.acOn !== undefined ? response.acOn : acOn;

        if (response.originCity) {
          setCity(response.originCity, qLat, qLng);
        } else {
          setLat(qLat);
          setLng(qLng);
        }
        setBatteryPercent(qBat);
        setAcOn(qAc);

        const effectiveVId = vId || (vehicles.length > 0 ? vehicles[0].id : null);
        if (effectiveVId) {
          try {
            const result = await api.getRecommendations({
              vehicleId: effectiveVId,
              lat: parseFloat(qLat),
              lng: parseFloat(qLng),
              batteryPercent: parseFloat(qBat),
              targetPercent: parseFloat(targetPercent),
              acOn: Boolean(qAc),
              speedKmph: parseInt(speedKmph),
              topK: 6,
            });
            setRecommendations(result);
            if (result?.recommendations?.[0]) {
              setSelectedRouteDest([result.recommendations[0].latitude, result.recommendations[0].longitude]);
            }
          } catch (recErr) {
            console.error('Auto local search error:', recErr);
          }
        }
      }
    } catch (err) {
      toast.error(err.message || 'AI Co-Pilot could not process prompt');
    } finally {
      setAiLoading(false);
    }
  };

  return (
    <div className="flex h-[calc(100vh-3.5rem)] overflow-hidden bg-[#0B1120]">

      {/* LEFT CONTROL PANEL (420px) — Rivian / Polestar Inspired Dark Cockpit */}
      <div className="w-[420px] flex-shrink-0 flex flex-col border-r border-slate-800 bg-[#111827] text-slate-200 overflow-y-auto">

        {/* AI Co-Pilot Compact Bar */}
        <div className="p-4 border-b border-slate-800 bg-[#0F172A] shrink-0">
          <div className="flex items-center gap-2 mb-2">
            <Sparkles className="w-4 h-4 text-blue-400 shrink-0" />
            <span className="text-xs font-bold text-slate-100 tracking-wide uppercase">Trip Co-Pilot</span>
          </div>
          <form
            onSubmit={(e) => { e.preventDefault(); handleAskCoPilot(); }}
            className="flex items-center gap-2"
          >
            <input
              type="text"
              value={aiPrompt}
              onChange={(e) => setAiPrompt(e.target.value)}
              placeholder='e.g. "Vadodara to Mumbai with 22% battery, AC on"'
              className="flex-1 min-w-0 bg-[#1E293B] border border-slate-700 focus:border-blue-500 rounded-xl px-3 py-2 text-xs text-slate-100 placeholder-slate-500 focus:outline-none focus:ring-1 focus:ring-blue-500 transition-all font-sans"
            />
            <button
              type="submit"
              disabled={aiLoading || !aiPrompt.trim()}
              className="flex items-center gap-1.5 px-3 py-2 rounded-xl bg-blue-600 hover:bg-blue-500 text-white font-semibold text-xs shadow-md shadow-blue-900/30 disabled:opacity-40 transition-all shrink-0 cursor-pointer"
            >
              {aiLoading ? <RefreshCw className="w-3.5 h-3.5 animate-spin" /> : <Sparkles className="w-3.5 h-3.5" />}
              <span className="hidden sm:inline">{aiLoading ? '...' : 'Ask'}</span>
            </button>
          </form>

          {/* Quick prompt chips */}
          <div className="mt-2.5 flex flex-wrap gap-1.5">
            {AI_PROMPT_SUGGESTIONS.map((item, idx) => (
              <button
                key={idx}
                type="button"
                onClick={() => { setAiPrompt(item.prompt); handleAskCoPilot(item.prompt); }}
                className="px-2.5 py-1 rounded-lg bg-[#1E293B] hover:bg-slate-700/70 text-slate-300 hover:text-slate-100 border border-slate-700/60 text-[10px] transition-colors cursor-pointer"
              >
                {item.label}
              </button>
            ))}
          </div>

          {/* Co-Pilot parsed result */}
          {aiLastResult && (
            <div className="mt-2.5 p-2 rounded-lg bg-emerald-950/40 border border-emerald-800/60 text-xs flex items-center gap-2">
              <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
              <span className="text-emerald-300 text-[11px] line-clamp-2">{aiLastResult.summary}</span>
            </div>
          )}
        </div>

        {/* TAB 1: NEARBY FINDER — EV Telemetry Controls + Ranked Charger Cards */}
        {activeTab === 'finder' && (
          <div className="flex flex-col flex-1 overflow-y-auto">

            {/* Telemetry Controls */}
            <div className="p-4 border-b border-slate-800 space-y-3.5 shrink-0">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold text-slate-100 flex items-center gap-1.5 tracking-wide">
                  <Sliders className="w-3.5 h-3.5 text-blue-400" /> VEHICLE TELEMETRY
                </span>
                <span className="text-[10px] text-slate-400 font-mono">LIVE INPUTS</span>
              </div>

              {/* Vehicle Selector */}
              {vehicles.length === 0 ? (
                <button
                  type="button"
                  onClick={() => setShowVehicleModal(true)}
                  className="w-full py-2 px-3 text-xs font-semibold rounded-xl border border-dashed border-blue-500/50 text-blue-400 hover:bg-blue-600/10 flex items-center justify-center gap-2 cursor-pointer transition-colors"
                >
                  <Plus className="w-3.5 h-3.5" /> Register an EV First
                </button>
              ) : (
                <select
                  value={selectedVehicleId}
                  onChange={(e) => setSelectedVehicleId(e.target.value)}
                  className="w-full px-3 py-2 bg-[#1E293B] border border-slate-700 rounded-xl text-xs font-semibold text-slate-100 focus:outline-none focus:border-blue-500"
                >
                  {vehicles.map((v) => (
                    <option key={v.id} value={v.id} className="bg-slate-900 text-slate-100">
                      {v.model} ({v.connectorType} {BULLET} {v.usableBatteryKwh} kWh)
                    </option>
                  ))}
                </select>
              )}

              {/* Battery Slider */}
              <div className="p-3.5 rounded-xl bg-[#1E293B] border border-slate-700/70 space-y-2">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-semibold text-slate-300 flex items-center gap-1.5">
                    <Battery className="w-3.5 h-3.5 text-blue-400" /> Battery SOC
                  </span>
                  <span className={`text-sm font-bold font-mono tabular-nums ${
                    parseFloat(batteryPercent) < 20
                      ? 'text-rose-400'
                      : parseFloat(batteryPercent) < 50
                      ? 'text-amber-400'
                      : 'text-emerald-400'
                  }`}>
                    {batteryPercent}%
                  </span>
                </div>
                <input
                  type="range" min="5" max="95" value={batteryPercent}
                  onChange={(e) => setBatteryPercent(e.target.value)}
                  className="w-full accent-blue-500 h-2 bg-slate-700 rounded-lg cursor-pointer"
                />
                <div className="flex justify-between text-[11px] text-slate-400 pt-0.5">
                  <span>Computed Safe Range:</span>
                  <span className="font-bold text-emerald-400 font-mono tabular-nums">{Math.round(safeRangeKm)} km</span>
                </div>
              </div>

              {/* Terrain Selector */}
              <div className="grid grid-cols-3 gap-1.5">
                {[
                  { id: 'FLAT', label: 'Flat (1.0x)' },
                  { id: 'HILLY', label: 'Hilly (+25%)' },
                  { id: 'HIGHWAY', label: 'Highway (+15%)' }
                ].map((t) => (
                  <button
                    key={t.id}
                    type="button"
                    onClick={() => setTerrain(t.id)}
                    className={`py-1.5 text-[11px] font-medium rounded-lg border transition-all cursor-pointer ${
                      terrain === t.id
                        ? 'border-blue-500 bg-blue-600/20 text-blue-300 font-semibold'
                        : 'border-slate-700 bg-[#1E293B] text-slate-400 hover:border-slate-600 hover:text-slate-200'
                    }`}
                  >
                    {t.label}
                  </button>
                ))}
              </div>

              {/* Speed + AC */}
              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="block text-[11px] font-semibold text-slate-300 mb-1">
                    Speed: <span className="font-mono text-slate-100">{speedKmph}</span> km/h
                  </label>
                  <input
                    type="range" min="30" max="120" value={speedKmph}
                    onChange={(e) => setSpeedKmph(parseInt(e.target.value))}
                    className="w-full accent-blue-500 h-2 bg-slate-700 rounded-lg cursor-pointer"
                  />
                </div>
                <div>
                  <label className="block text-[11px] font-semibold text-slate-300 mb-1">Climate AC</label>
                  <button
                    type="button"
                    onClick={() => setAcOn(!acOn)}
                    className={`w-full py-1.5 px-2 rounded-lg text-[11px] font-medium border transition-all cursor-pointer ${
                      acOn
                        ? 'border-blue-500 bg-blue-600/20 text-blue-300 font-semibold'
                        : 'border-slate-700 bg-[#1E293B] text-slate-400 hover:text-slate-200'
                    }`}
                  >
                    {acOn ? 'AC On (+15%)' : 'AC Off'}
                  </button>
                </div>
              </div>

              {/* Search CTA */}
              <button
                type="button"
                onClick={handleFindChargers}
                disabled={actionLoading || vehicles.length === 0}
                className="w-full py-2.5 rounded-xl font-semibold text-xs bg-blue-600 hover:bg-blue-500 text-white transition-all shadow-md shadow-blue-900/30 disabled:opacity-40 flex items-center justify-center gap-2 cursor-pointer"
              >
                <Sparkles className="w-4 h-4" />
                <span>{actionLoading ? 'Analyzing Corridor...' : 'Search Chargers'}</span>
              </button>
            </div>

            {/* Charger Recommendation Cards */}
            {recommendations && (
              <div className="flex-1 overflow-y-auto p-3 space-y-2.5">
                {/* Connector Filter */}
                <div className="flex items-center gap-1 p-0.5 rounded-lg bg-[#0F172A] border border-slate-800">
                  {['ALL', 'CCS2', 'TYPE2', 'CHADEMO'].map((cf) => (
                    <button
                      key={cf}
                      onClick={() => setConnectorFilter(cf)}
                      className={`flex-1 py-1 rounded-md text-[10px] font-semibold transition-all cursor-pointer ${
                        connectorFilter === cf
                          ? 'bg-[#1E293B] text-slate-100 border border-slate-700 shadow-xs'
                          : 'text-slate-400 hover:text-slate-200'
                      }`}
                    >
                      {cf}
                    </button>
                  ))}
                </div>

                {filteredRecommendations.length === 0 ? (
                  <div className="text-center py-8 bg-[#1E293B]/50 border border-dashed border-slate-700 rounded-xl">
                    <AlertTriangle className="w-6 h-6 text-amber-400 mx-auto mb-1.5" />
                    <div className="text-xs font-semibold text-slate-200">No chargers for this filter</div>
                    <div className="text-[11px] text-slate-500 mt-0.5">Try switching to ALL connectors</div>
                  </div>
                ) : (
                  filteredRecommendations.map((rec) => {
                    const totalM = rec.totalMinutes || 1;
                    const drivePct = Math.round((rec.driveMinutes / totalM) * 100);
                    const waitPct = Math.round((rec.waitMinutes / totalM) * 100);
                    const chargePct = Math.max(0, 100 - drivePct - waitPct);
                    const isVehicleCapped = currentVehicle && currentVehicle.maxChargeKw < rec.powerKw;
                    const isLowestCost = Number(rec.estimatedCost) === minCostAmongRecs && minCostAmongRecs > 0;
                    const isFastest = rec.rank === 1;

                    return (
                      <div
                        key={rec.chargerId}
                        onMouseEnter={() => setSelectedRouteDest([rec.latitude, rec.longitude])}
                        className={`p-4 rounded-xl border bg-[#1E293B] shadow-sm space-y-3 transition-all cursor-default ${
                          isFastest
                            ? 'border-blue-500 ring-1 ring-blue-500/30'
                            : 'border-slate-700/70 hover:border-slate-600'
                        }`}
                      >
                        {/* Card header */}
                        <div className="flex items-start justify-between gap-2">
                          <div className="flex-1 min-w-0">
                            <div className="flex items-center gap-1.5 mb-1">
                              <span className="text-[10px] font-bold text-slate-400 font-mono">#{rec.rank}</span>
                              <span className="text-[9px] font-bold px-1.5 py-0.5 rounded bg-slate-800 text-slate-300 font-mono border border-slate-700">
                                {rec.connectorType}
                              </span>
                              {isFastest && (
                                <span className="text-[9px] font-bold px-2 py-0.5 rounded-full bg-blue-500/20 text-blue-400 border border-blue-500/30">
                                  FASTEST
                                </span>
                              )}
                              {!isFastest && isLowestCost && (
                                <span className="text-[9px] font-bold px-2 py-0.5 rounded-full bg-amber-500/20 text-amber-400 border border-amber-500/30">
                                  BEST VALUE
                                </span>
                              )}
                            </div>
                            <h4 className="text-sm font-bold text-slate-100 truncate">{rec.chargerName}</h4>
                            <div className="text-[11px] text-slate-400 flex items-center gap-1 truncate mt-0.5">
                              <MapPin className="w-3 h-3 text-slate-500 flex-shrink-0" />
                              <span className="truncate">{rec.stationName}, {rec.city}</span>
                            </div>
                          </div>
                          <div className="text-right shrink-0">
                            <div className="text-sm font-bold text-slate-100 font-mono tabular-nums">
                              {Math.round(rec.totalMinutes)}m
                            </div>
                            <div className="text-[11px] text-slate-400 font-mono">
                              {RUPEE}{rec.estimatedCost}
                            </div>
                          </div>
                        </div>

                        {/* Time breakdown bar */}
                        <div className="space-y-1">
                          <div className="w-full h-2 rounded-full bg-slate-700/60 overflow-hidden flex">
                            <div style={{ width: `${drivePct}%` }} className="bg-blue-500" title={`Drive ${Math.round(rec.driveMinutes)}m`} />
                            <div style={{ width: `${waitPct}%` }} className="bg-amber-500" title={`Wait ${Math.round(rec.waitMinutes)}m`} />
                            <div style={{ width: `${chargePct}%` }} className="bg-emerald-500" title={`Charge ${Math.round(rec.chargeMinutes)}m`} />
                          </div>
                          <div className="flex justify-between text-[9px] text-slate-400">
                            <span className="flex items-center gap-1">
                              <span className="w-1.5 h-1.5 rounded-full bg-blue-500" />
                              Drive {Math.round(rec.driveMinutes)}m
                            </span>
                            <span className="flex items-center gap-1">
                              <span className="w-1.5 h-1.5 rounded-full bg-amber-500" />
                              Wait {Math.round(rec.waitMinutes)}m
                            </span>
                            <span className="flex items-center gap-1">
                              <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
                              Charge {Math.round(rec.chargeMinutes)}m
                            </span>
                          </div>
                        </div>

                        {/* Power + cap indicator */}
                        <div className="flex items-center justify-between text-[11px] p-2 rounded-lg bg-[#111827] border border-slate-800">
                          <span className="text-slate-400 font-mono">{rec.powerKw} kW bay</span>
                          {isVehicleCapped ? (
                            <span className="px-1.5 py-0.5 rounded bg-amber-950/40 text-amber-400 border border-amber-800/50 text-[10px] font-semibold">
                              Capped by EV: {rec.effectivePowerKw} kW
                            </span>
                          ) : (
                            <span className="px-1.5 py-0.5 rounded bg-emerald-950/40 text-emerald-400 border border-emerald-800/50 text-[10px] font-semibold">
                              Full {rec.effectivePowerKw} kW Rate
                            </span>
                          )}
                        </div>

                        {/* Reserve CTA */}
                        <button
                          type="button"
                          onClick={() => setSelectedRecForBooking(rec)}
                          className="w-full py-2 rounded-lg font-semibold text-xs bg-blue-600 hover:bg-blue-500 text-white transition-all shadow-md shadow-blue-900/30 flex items-center justify-center gap-1.5 cursor-pointer"
                        >
                          <Clock className="w-3.5 h-3.5" /> Reserve 15-Min Slot
                        </button>
                      </div>
                    );
                  })
                )}
              </div>
            )}

            {!recommendations && (
              <div className="flex-1 flex flex-col items-center justify-center text-center px-6 py-10 text-slate-500">
                <Compass className="w-10 h-10 mb-3 opacity-30 text-blue-400" />
                <div className="text-sm font-semibold text-slate-300">Ready to Locate Chargers</div>
                <div className="text-xs text-slate-500 mt-1 max-w-xs">
                  Configure battery SOC and tap "Search Chargers" to discover optimal bays along your route.
                </div>
              </div>
            )}
          </div>
        )}

        {/* TAB 2: HIGHWAY PLANNER (DIJKSTRA) */}
        {activeTab === 'trips' && (
          <div className="flex-1 overflow-y-auto p-4 space-y-4">
            <div className="space-y-1">
              <h3 className="text-sm font-bold text-slate-100 flex items-center gap-2">
                <Route className="w-4 h-4 text-blue-400" /> Highway Trip Planner
              </h3>
              <p className="text-[11px] text-slate-400">Dijkstra graph optimizer with 5% battery safety reserve.</p>
            </div>

            {/* Corridor Presets */}
            <div className="grid grid-cols-2 gap-2">
              {CORRIDOR_PRESETS.map((p) => (
                <button
                  key={p.id}
                  type="button"
                  onClick={() => handleSelectCorridor(p)}
                  className={`p-2.5 rounded-xl border text-left transition-all cursor-pointer ${
                    tripPreset === p.id
                      ? 'bg-blue-600/20 border-blue-500 text-slate-100'
                      : 'bg-[#1E293B] border-slate-700/70 text-slate-400 hover:border-slate-600 hover:text-slate-200'
                  }`}
                >
                  <div className="text-[9px] uppercase font-bold text-blue-400 tracking-wider font-mono">{p.distance}</div>
                  <div className="text-[11px] font-bold mt-0.5 text-slate-200 leading-snug">{p.label}</div>
                </button>
              ))}
            </div>

            {/* Trip Telemetry Inputs */}
            <div className="space-y-3 p-3.5 rounded-xl bg-[#1E293B] border border-slate-700/70">
              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="block text-[11px] font-semibold text-slate-300 mb-1">
                    Origin ({tripStartCity})
                  </label>
                  <div className="grid grid-cols-2 gap-1.5">
                    <input
                      type="text" value={tripStartLat} onChange={(e) => setTripStartLat(e.target.value)} placeholder="Lat"
                      className="w-full px-2 py-1.5 bg-[#111827] border border-slate-700 rounded-lg text-[11px] font-mono text-slate-100"
                    />
                    <input
                      type="text" value={tripStartLng} onChange={(e) => setTripStartLng(e.target.value)} placeholder="Lng"
                      className="w-full px-2 py-1.5 bg-[#111827] border border-slate-700 rounded-lg text-[11px] font-mono text-slate-100"
                    />
                  </div>
                </div>
                <div>
                  <label className="block text-[11px] font-semibold text-slate-300 mb-1">
                    Destination ({tripDestCity})
                  </label>
                  <div className="grid grid-cols-2 gap-1.5">
                    <input
                      type="text" value={tripDestLat} onChange={(e) => setTripDestLat(e.target.value)} placeholder="Lat"
                      className="w-full px-2 py-1.5 bg-[#111827] border border-slate-700 rounded-lg text-[11px] font-mono text-slate-100"
                    />
                    <input
                      type="text" value={tripDestLng} onChange={(e) => setTripDestLng(e.target.value)} placeholder="Lng"
                      className="w-full px-2 py-1.5 bg-[#111827] border border-slate-700 rounded-lg text-[11px] font-mono text-slate-100"
                    />
                  </div>
                </div>
              </div>

              <select
                value={selectedVehicleId}
                onChange={(e) => setSelectedVehicleId(e.target.value)}
                className="w-full px-3 py-2 bg-[#111827] border border-slate-700 rounded-xl text-xs font-semibold text-slate-100 focus:outline-none focus:border-blue-500"
              >
                {vehicles.map((v) => (
                  <option key={v.id} value={v.id} className="bg-slate-900 text-slate-100">
                    {v.model} ({v.connectorType})
                  </option>
                ))}
              </select>

              <div>
                <div className="flex justify-between text-[11px] mb-1">
                  <span className="font-semibold text-slate-300">Departure Battery SOC</span>
                  <span className={`font-mono font-bold ${parseFloat(tripBatteryPercent) < 25 ? 'text-rose-400' : 'text-blue-400'}`}>
                    {tripBatteryPercent}%
                  </span>
                </div>
                <input
                  type="range" min="10" max="100" value={tripBatteryPercent}
                  onChange={(e) => setTripBatteryPercent(e.target.value)}
                  className="w-full accent-blue-500 h-2 bg-slate-700 rounded-lg cursor-pointer"
                />
              </div>

              <div className="grid grid-cols-3 gap-2">
                <div>
                  <label className="block text-[11px] font-semibold text-slate-300 mb-1">
                    Speed: <span className="font-mono text-slate-100">{tripSpeedKmph}</span> km/h
                  </label>
                  <input
                    type="range" min="60" max="120" value={tripSpeedKmph}
                    onChange={(e) => setTripSpeedKmph(parseInt(e.target.value))}
                    className="w-full accent-blue-500 h-2 bg-slate-700 rounded-lg cursor-pointer"
                  />
                </div>
                <div>
                  <label className="block text-[11px] font-semibold text-slate-300 mb-1">Climate</label>
                  <button
                    type="button"
                    onClick={() => setTripAcOn(!tripAcOn)}
                    className={`w-full py-1.5 px-2 rounded-lg text-[11px] font-medium border transition-all cursor-pointer ${
                      tripAcOn
                        ? 'bg-blue-600/20 text-blue-300 border-blue-500'
                        : 'bg-[#111827] text-slate-400 border-slate-700'
                    }`}
                  >
                    {tripAcOn ? 'AC On' : 'AC Off'}
                  </button>
                </div>
                <div>
                  <label className="block text-[11px] font-semibold text-slate-300 mb-1">Terrain</label>
                  <select
                    value={tripTerrain}
                    onChange={(e) => setTripTerrain(e.target.value)}
                    className="w-full px-2 py-1.5 bg-[#111827] border border-slate-700 rounded-lg text-[11px] font-semibold text-slate-100"
                  >
                    <option value="HIGHWAY">Highway</option>
                    <option value="FLAT">Flat</option>
                    <option value="HILLY">Hilly</option>
                  </select>
                </div>
              </div>

              <button
                type="button"
                disabled={tripLoading}
                onClick={handlePlanHighwayTrip}
                className="w-full py-2.5 rounded-xl font-semibold text-xs bg-blue-600 hover:bg-blue-500 text-white transition-all shadow-md shadow-blue-900/30 flex items-center justify-center gap-2 disabled:opacity-40 cursor-pointer"
              >
                <Route className="w-4 h-4" />
                <span>{tripLoading ? 'Computing Dijkstra Route...' : 'Plan Optimal Route'}</span>
              </button>
            </div>

            {/* Trip Error */}
            {tripError && (
              <div className="p-3.5 rounded-xl bg-rose-950/40 border border-rose-800/60 text-rose-300 flex items-start gap-2 text-xs">
                <AlertTriangle className="w-4 h-4 text-rose-400 flex-shrink-0 mt-0.5" />
                <div>
                  <div className="font-bold mb-0.5">Route Infeasible</div>
                  <div>{tripError}</div>
                </div>
              </div>
            )}

            {/* Trip Results Summary */}
            {tripPlanResult && (
              <div className="space-y-3">
                <div className="grid grid-cols-3 gap-2">
                  <div className="p-2.5 rounded-xl bg-[#1E293B] border border-slate-700/70 text-center">
                    <div className="text-[9px] text-slate-400 uppercase font-semibold">Distance</div>
                    <div className="text-sm font-bold text-slate-100 font-mono">{Math.round(tripPlanResult.totalDistanceKm)} km</div>
                  </div>
                  <div className="p-2.5 rounded-xl bg-[#1E293B] border border-slate-700/70 text-center">
                    <div className="text-[9px] text-slate-400 uppercase font-semibold">Stops</div>
                    <div className="text-sm font-bold text-slate-100 font-mono">{tripPlanResult.totalStops}</div>
                  </div>
                  <div className="p-2.5 rounded-xl bg-[#1E293B] border border-slate-700/70 text-center">
                    <div className="text-[9px] text-slate-400 uppercase font-semibold">Cost</div>
                    <div className="text-sm font-bold text-emerald-400 font-mono">{RUPEE}{Math.round(tripPlanResult.totalCost)}</div>
                  </div>
                </div>
                <div className="grid grid-cols-2 gap-2">
                  <div className="p-2.5 rounded-xl bg-[#1E293B] border border-slate-700/70 text-center">
                    <div className="text-[9px] text-slate-400 uppercase font-semibold">Drive Time</div>
                    <div className="text-sm font-bold text-slate-100 font-mono">
                      {Math.floor(tripPlanResult.totalDriveMinutes / 60)}h {Math.round(tripPlanResult.totalDriveMinutes % 60)}m
                    </div>
                  </div>
                  <div className="p-2.5 rounded-xl bg-[#1E293B] border border-slate-700/70 text-center">
                    <div className="text-[9px] text-slate-400 uppercase font-semibold">Total Journey</div>
                    <div className="text-sm font-bold text-blue-400 font-mono">
                      {Math.floor(tripPlanResult.totalTripMinutes / 60)}h {Math.round(tripPlanResult.totalTripMinutes % 60)}m
                    </div>
                  </div>
                </div>

                {tripPlanResult.totalStops === 0 ? (
                  <div className="p-3 rounded-xl bg-emerald-950/40 border border-emerald-800/60 text-xs text-emerald-300 font-semibold flex items-center gap-2">
                    <CheckCircle2 className="w-4 h-4 text-emerald-400" /> Non-Stop Direct Route Feasible!
                  </div>
                ) : (
                  tripPlanResult.stops.map((stop) => (
                    <div key={stop.stopNumber} className="p-3.5 rounded-xl bg-[#1E293B] border border-slate-700/70 shadow-sm space-y-2">
                      <div className="flex items-center gap-2">
                        <span className="w-6 h-6 rounded-full bg-blue-600 text-white font-bold text-xs flex items-center justify-center shrink-0">
                          {stop.stopNumber}
                        </span>
                        <div className="flex-1 min-w-0">
                          <h5 className="text-xs font-bold text-slate-100 truncate">{stop.stationName}</h5>
                          <span className="text-[10px] text-slate-400">{stop.city}</span>
                        </div>
                        <div className="text-right shrink-0">
                          <div className="text-[11px] font-semibold text-blue-400 font-mono">
                            Charge {Math.round(stop.chargeDurationMinutes)}m
                          </div>
                          <div className="text-[10px] text-emerald-400 font-mono">
                            {RUPEE}{Math.round(stop.estimatedCost)}
                          </div>
                        </div>
                      </div>
                      <div className="text-[10px] text-slate-400 flex justify-between font-mono p-1.5 rounded-lg bg-[#111827]">
                        <span>Arrival: {Math.round(stop.arrivalBatteryPercent)}%</span>
                        <span>Target: {Math.round(stop.targetBatteryPercent)}%</span>
                        <span>+{Math.round(stop.energyChargedKwh)} kWh</span>
                      </div>
                      <button
                        type="button"
                        onClick={() => handleBookTripStop(stop)}
                        className="w-full py-1.5 rounded-lg text-[11px] font-semibold bg-blue-600 hover:bg-blue-500 text-white flex items-center justify-center gap-1.5 cursor-pointer transition-colors"
                      >
                        <Zap className="w-3 h-3" /> Reserve Stop #{stop.stopNumber}
                      </button>
                    </div>
                  ))
                )}
              </div>
            )}
          </div>
        )}

        {/* TAB 3: ACTIVE SESSIONS & SIMULATOR */}
        {activeTab === 'bookings' && (
          <div className="flex-1 overflow-y-auto p-4 space-y-3">
            <div className="flex items-center justify-between shrink-0">
              <h3 className="text-sm font-bold text-slate-100 flex items-center gap-2">
                <BatteryCharging className="w-4 h-4 text-emerald-400" /> Active Sessions
              </h3>
              <button
                onClick={refreshBookings}
                className="p-1.5 rounded-lg bg-[#1E293B] border border-slate-700 text-slate-400 hover:text-slate-100 transition-colors cursor-pointer"
              >
                <RefreshCw className="w-3.5 h-3.5" />
              </button>
            </div>

            {bookings.length === 0 ? (
              <div className="text-center py-10 bg-[#1E293B]/40 border border-dashed border-slate-700 rounded-xl">
                <Clock className="w-8 h-8 text-slate-500 mx-auto mb-2" />
                <div className="text-xs font-semibold text-slate-200">No Active Reservations</div>
                <div className="text-[11px] text-slate-500 mt-1 mb-3">Find chargers in Nearby Finder tab</div>
                <button
                  onClick={() => setActiveTab('finder')}
                  className="px-3 py-1.5 rounded-lg text-xs font-semibold bg-blue-600 text-white hover:bg-blue-500 cursor-pointer transition-colors"
                >
                  Find Chargers
                </button>
              </div>
            ) : (
              bookings.map((booking) => {
                const isPending = booking.status === 'PENDING_PAYMENT';
                const isConfirmed = booking.status === 'CONFIRMED';
                const isActive = booking.status === 'ACTIVE';
                const isCompleted = booking.status === 'COMPLETED';
                const isChargeFinished = !!booking.chargeCompletedAt && !booking.plugOutAt;

                let liveElapsedSec = 0;
                let liveElapsedStr = null;
                if (isActive && booking.sessionStartTime) {
                  const startMs = new Date(booking.sessionStartTime).getTime();
                  liveElapsedSec = Math.max(0, Math.floor((currentTimestamp - startMs) / 1000));
                  const m = Math.floor(liveElapsedSec / 60);
                  const s = liveElapsedSec % 60;
                  liveElapsedStr = `${m}m ${s < 10 ? '0' : ''}${s}s`;
                }

                const simulatedSoc = isActive
                  ? Math.min(100, Math.round(25 + (liveElapsedSec * 1.2)))
                  : isCompleted ? 100 : 25;

                let livePenaltyEstimate = Number(booking.overstayPenalty || 0);
                let liveOverstayMinutes = 0;
                if (isChargeFinished && booking.chargeCompletedAt) {
                  const finishMs = new Date(booking.chargeCompletedAt).getTime();
                  const overdueMs = currentTimestamp - (finishMs + 10 * 60 * 1000);
                  if (overdueMs > 0) {
                    liveOverstayMinutes = Math.floor(overdueMs / 60000);
                    livePenaltyEstimate = liveOverstayMinutes * 10;
                  }
                }

                return (
                  <div
                    key={booking.id}
                    className={`p-4 rounded-xl border bg-[#1E293B] shadow-sm space-y-3 ${
                      isActive
                        ? 'border-emerald-500/70 ring-1 ring-emerald-500/20'
                        : isConfirmed
                        ? 'border-blue-500/50'
                        : 'border-slate-700/70'
                    }`}
                  >
                    <div className="flex items-start justify-between gap-2">
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-1.5">
                          <span className="text-[10px] text-slate-400 font-mono">#{booking.id}</span>
                          <h4 className="text-sm font-bold text-slate-100 truncate">{booking.chargerName}</h4>
                        </div>
                        <div className="text-[11px] text-slate-400 flex items-center gap-1 mt-0.5">
                          <MapPin className="w-3 h-3 text-slate-500 flex-shrink-0" />
                          <span className="truncate">{booking.stationName}</span>
                        </div>
                      </div>
                      <span className={`text-[9px] font-bold uppercase tracking-wider px-2 py-0.5 rounded-full border shrink-0 ${
                        isPending
                          ? 'bg-amber-950/50 text-amber-300 border-amber-800/60 animate-pulse'
                          : isConfirmed
                          ? 'bg-blue-950/50 text-blue-300 border-blue-800/60'
                          : isActive
                          ? 'bg-emerald-950/50 text-emerald-300 border-emerald-800/60'
                          : isCompleted
                          ? 'bg-slate-800 text-slate-300 border-slate-700'
                          : 'bg-rose-950/50 text-rose-300 border-rose-800/60'
                      }`}>
                        {booking.status}
                      </span>
                    </div>

                    {isActive && (
                      <div className="space-y-1.5 p-3 rounded-lg bg-[#111827] border border-slate-800">
                        <div className="flex justify-between text-[11px]">
                          <span className="text-slate-300 flex items-center gap-1">
                            <BatteryCharging className="w-3 h-3 text-emerald-400" /> Live SOC
                          </span>
                          <span className="font-mono font-bold text-emerald-400 tabular-nums">{simulatedSoc}%</span>
                        </div>
                        <div className="w-full h-2 rounded-full bg-slate-700 overflow-hidden">
                          <div style={{ width: `${simulatedSoc}%` }} className="h-full bg-emerald-500 rounded-full transition-all duration-500" />
                        </div>
                        <div className="text-[10px] text-emerald-400 font-mono text-right">
                          Elapsed: {liveElapsedStr || '0m 00s'}
                        </div>
                      </div>
                    )}

                    <div className="grid grid-cols-2 gap-2 p-2.5 rounded-lg bg-[#111827] border border-slate-800 text-[11px]">
                      <div>
                        <span className="text-[9px] text-slate-400 block uppercase font-medium">Window</span>
                        <span className="font-semibold text-slate-200 font-mono tabular-nums">
                          {new Date(booking.startTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })} {ARROW} {new Date(booking.endTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                        </span>
                      </div>
                      <div>
                        <span className="text-[9px] text-slate-400 block uppercase font-medium">Settlement</span>
                        <span className="font-bold text-slate-100 font-mono tabular-nums">
                          {RUPEE}{booking.totalAmount || booking.baseAmount || '0.00'}
                        </span>
                      </div>
                    </div>

                    {(booking.overstayPenalty > 0 || livePenaltyEstimate > 0) && (
                      <div className="p-2.5 rounded-lg bg-amber-950/40 border border-amber-800/60 text-[11px] flex items-center gap-2 text-amber-300">
                        <AlertTriangle className="w-3.5 h-3.5 text-amber-400 flex-shrink-0" />
                        <span>
                          Overstay Penalty: <strong className="font-mono">{RUPEE}{Math.max(booking.overstayPenalty || 0, livePenaltyEstimate)}</strong> {BULLET} {RUPEE}10/min
                        </span>
                      </div>
                    )}

                    <div className="flex flex-wrap gap-2 pt-1">
                      {isPending && (
                        <button
                          onClick={() => handleConfirmPayment(booking.id)}
                          disabled={actionLoading}
                          className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg font-semibold text-xs bg-blue-600 hover:bg-blue-500 text-white disabled:opacity-40 cursor-pointer transition-colors"
                        >
                          <CreditCard className="w-3.5 h-3.5" /> Confirm Payment
                        </button>
                      )}
                      {isConfirmed && (
                        <button
                          onClick={() => handleStartSession(booking.id)}
                          disabled={actionLoading}
                          className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg font-semibold text-xs bg-emerald-600 hover:bg-emerald-500 text-white disabled:opacity-40 cursor-pointer transition-colors"
                        >
                          <Play className="w-3.5 h-3.5" /> Start Charging
                        </button>
                      )}
                      {isActive && !isChargeFinished && (
                        <button
                          onClick={() => handleCompleteCharging(booking.id)}
                          disabled={actionLoading}
                          className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg font-semibold text-xs bg-blue-600 hover:bg-blue-500 text-white disabled:opacity-40 cursor-pointer transition-colors"
                        >
                          <BatteryCharging className="w-3.5 h-3.5" /> Charge Complete
                        </button>
                      )}
                      {isActive && (
                        <>
                          <button
                            onClick={() => handleSimulateOverstay(booking.id)}
                            disabled={actionLoading}
                            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg font-semibold text-xs bg-amber-500/15 hover:bg-amber-500/25 text-amber-300 border border-amber-500/40 disabled:opacity-40 cursor-pointer transition-colors"
                            title="Fast-forward +18m (triggers grace expiry + penalty)"
                          >
                            <FastForward className="w-3.5 h-3.5 text-amber-400" /> Fast-Forward +18m (Demo Overstay Penalty)
                          </button>
                          <button
                            onClick={() => handlePlugOut(booking.id)}
                            disabled={actionLoading}
                            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg font-semibold text-xs bg-slate-800 hover:bg-slate-700 text-slate-100 border border-slate-600 disabled:opacity-40 cursor-pointer transition-colors"
                          >
                            <PowerOff className="w-3.5 h-3.5" /> Plug Out & Settle
                          </button>
                        </>
                      )}
                      {isCompleted && (
                        <div className="text-xs text-slate-400 flex items-center gap-1.5">
                          <CheckCircle2 className="w-4 h-4 text-emerald-400" /> Session settled. Bay released.
                        </div>
                      )}
                    </div>
                  </div>
                );
              })
            )}
          </div>
        )}

        {/* TAB 4: GARAGE */}
        {activeTab === 'vehicles' && (
          <div className="flex-1 overflow-y-auto p-4 space-y-3">
            <div className="flex items-center justify-between shrink-0">
              <h3 className="text-sm font-bold text-slate-100 flex items-center gap-2">
                <Car className="w-4 h-4 text-blue-400" /> EV Garage
              </h3>
              <button
                onClick={() => setShowVehicleModal(true)}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold bg-blue-600 hover:bg-blue-500 text-white cursor-pointer transition-colors"
              >
                <Plus className="w-3.5 h-3.5" /> Add EV
              </button>
            </div>

            {vehicles.length === 0 ? (
              <div className="text-center py-10 bg-[#1E293B]/40 border border-dashed border-slate-700 rounded-xl">
                <Car className="w-8 h-8 text-slate-500 mx-auto mb-2" />
                <div className="text-xs font-semibold text-slate-200">No Vehicles Registered</div>
                <div className="text-[11px] text-slate-500 mt-1 mb-3">Register your EV for personalized range calculations</div>
                <button
                  onClick={() => setShowVehicleModal(true)}
                  className="px-3 py-1.5 rounded-lg text-xs font-semibold bg-blue-600 text-white hover:bg-blue-500 cursor-pointer"
                >
                  + Register First EV
                </button>
              </div>
            ) : (
              vehicles.map((v) => (
                <div key={v.id} className="p-4 rounded-xl bg-[#1E293B] border border-slate-700/70 shadow-sm space-y-3">
                  <div className="flex items-start justify-between">
                    <div>
                      <h4 className="font-bold text-sm text-slate-100">{v.model}</h4>
                      <span className="text-xs font-mono font-semibold text-blue-400">{v.connectorType}</span>
                    </div>
                    <span className="text-[9px] font-semibold uppercase px-2 py-0.5 rounded-full bg-emerald-950/50 text-emerald-400 border border-emerald-800/60">
                      Active
                    </span>
                  </div>
                  <div className="grid grid-cols-2 gap-2 text-xs bg-[#111827] p-2.5 rounded-lg border border-slate-800">
                    <div>
                      <span className="text-[9px] text-slate-400 block uppercase font-medium">Battery</span>
                      <span className="font-semibold text-slate-100 font-mono">{v.usableBatteryKwh} kWh</span>
                    </div>
                    <div>
                      <span className="text-[9px] text-slate-400 block uppercase font-medium">Max Charge</span>
                      <span className="font-semibold text-blue-400 font-mono">{v.maxChargeKw} kW</span>
                    </div>
                    <div className="col-span-2 pt-1 border-t border-slate-800">
                      <span className="text-[9px] text-slate-400 block uppercase font-medium">Consumption</span>
                      <span className="font-semibold text-slate-300 font-mono">{v.baseConsumptionKwhPerKm} kWh/km</span>
                    </div>
                  </div>
                </div>
              ))
            )}
          </div>
        )}
      </div>

      {/* RIGHT MAP PANEL (flex-1) */}
      <div className="flex-1 relative overflow-hidden bg-slate-900">

        {/* City Quick-Jump Pills — placed cleanly at top-3 left-14 (clear of zoom controls) */}
        <div className="absolute top-3 left-14 z-[1000] flex flex-wrap gap-1.5">
          {[
            { name: 'Vadodara', lat: '22.307200', lng: '73.181200' },
            { name: 'Ahmedabad', lat: '23.033800', lng: '72.507400' },
            { name: 'Pune', lat: '18.591200', lng: '73.738900' },
            { name: 'Mumbai', lat: '19.065700', lng: '72.868700' },
          ].map((city) => (
            <button
              key={city.name}
              type="button"
              onClick={() => setCity(city.name, city.lat, city.lng)}
              className={`px-3 py-1 rounded-lg text-xs font-semibold border shadow-md transition-all cursor-pointer backdrop-blur-md ${
                cityPreset === city.name
                  ? 'bg-blue-600 text-white border-blue-500 shadow-blue-900/40'
                  : 'bg-slate-900/85 text-slate-300 border-slate-700/80 hover:bg-slate-800 hover:text-white'
              }`}
            >
              {city.name}
            </button>
          ))}
        </div>

        {/* Clean Status Pill — positioned at bottom-4 left-4 (no overlap with any map controls) */}
        {activeTab === 'finder' && (
          <div className="absolute bottom-4 left-4 z-[1000] flex items-center gap-2 px-3 py-1.5 bg-slate-900/90 backdrop-blur-md border border-slate-700/80 rounded-xl shadow-lg text-xs text-slate-200">
            <span className="w-2.5 h-2.5 rounded-full border border-dashed border-emerald-400 bg-emerald-500/20" />
            <span className="font-medium">
              Click map to place EV marker <span className="text-slate-500 mx-1">{BULLET}</span> Range: <span className="font-mono font-bold text-emerald-400">{Math.round(safeRangeKm)} km</span>
            </span>
            {selectedRouteDest && (
              <>
                <span className="text-slate-600">|</span>
                <span className="font-semibold text-blue-400">Route Active</span>
                <button onClick={() => setSelectedRouteDest(null)} className="text-slate-400 hover:text-slate-200 cursor-pointer text-[10px] underline ml-1">Clear</button>
              </>
            )}
          </div>
        )}

        {/* Full-bleed Map */}
        {activeTab === 'finder' && (
          <MapComponent
            userLat={lat}
            userLng={lng}
            stations={publicStations}
            safeRangeKm={safeRangeKm}
            routeDestination={selectedRouteDest}
            onMapClick={handleMapClick}
            onSelectStation={(st) => {
              setSelectedRouteDest([Number(st.latitude), Number(st.longitude)]);
              toast.info(`Selected ${st.name}`);
            }}
            height="100%"
          />
        )}

        {activeTab === 'trips' && tripPlanResult && (
          <MapComponent
            tripWaypoints={tripPlanResult.routeCoordinates?.map((c) => [c.lat, c.lng])}
            tripStops={tripPlanResult.stops}
            tripStart={{ lat: tripStartLat, lng: tripStartLng, name: tripStartCity }}
            tripDest={{ lat: tripDestLat, lng: tripDestLng, name: tripDestCity }}
            height="100%"
          />
        )}

        {activeTab === 'trips' && !tripPlanResult && (
          <MapComponent
            userLat={tripStartLat}
            userLng={tripStartLng}
            stations={publicStations}
            height="100%"
          />
        )}

        {(activeTab === 'bookings' || activeTab === 'vehicles') && (
          <MapComponent
            userLat={lat}
            userLng={lng}
            stations={publicStations}
            height="100%"
          />
        )}
      </div>

      {/* MODALS */}

      {/* Add Vehicle Modal */}
      {showVehicleModal && (
        <div className="fixed inset-0 z-[9990] flex items-center justify-center p-4 bg-slate-950/75 backdrop-blur-sm">
          <div className="w-full max-w-md bg-[#111827] border border-slate-700 rounded-2xl p-6 shadow-2xl space-y-4 text-slate-100">
            <div className="flex items-center justify-between border-b border-slate-800 pb-3">
              <h3 className="text-base font-bold text-slate-100 flex items-center gap-2">
                <Car className="w-4 h-4 text-blue-400" /> Register New EV
              </h3>
              <button
                onClick={() => setShowVehicleModal(false)}
                className="text-slate-400 hover:text-slate-200 cursor-pointer"
              >
                <X className="w-5 h-5" />
              </button>
            </div>
            <form onSubmit={handleAddVehicle} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">Model Name</label>
                <input
                  type="text" required value={vModel} onChange={(e) => setVModel(e.target.value)}
                  className="w-full px-3 py-2 bg-[#1E293B] border border-slate-700 rounded-xl text-xs text-slate-100 focus:outline-none focus:border-blue-500"
                />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-slate-300 mb-1">Battery (kWh)</label>
                  <input
                    type="number" step="0.1" min="1" max="300" required value={vBattery} onChange={(e) => setVBattery(e.target.value)}
                    className="w-full px-3 py-2 bg-[#1E293B] border border-slate-700 rounded-xl text-xs font-mono text-slate-100"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-slate-300 mb-1">Max Charge (kW)</label>
                  <input
                    type="number" step="0.1" min="1" max="500" required value={vMaxPower} onChange={(e) => setVMaxPower(e.target.value)}
                    className="w-full px-3 py-2 bg-[#1E293B] border border-slate-700 rounded-xl text-xs font-mono text-slate-100"
                  />
                </div>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-slate-300 mb-1">Consumption (kWh/km)</label>
                  <input
                    type="number" step="0.001" min="0.05" max="1.0" required value={vConsumption} onChange={(e) => setVConsumption(e.target.value)}
                    className="w-full px-3 py-2 bg-[#1E293B] border border-slate-700 rounded-xl text-xs font-mono text-slate-100"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-slate-300 mb-1">Connector</label>
                  <select
                    value={vConnector} onChange={(e) => setVConnector(e.target.value)}
                    className="w-full px-3 py-2 bg-[#1E293B] border border-slate-700 rounded-xl text-xs text-slate-100"
                  >
                    <option value="CCS2">CCS2</option>
                    <option value="TYPE2">TYPE2</option>
                    <option value="CHADEMO">CHADEMO</option>
                    <option value="GB_T">GB_T</option>
                  </select>
                </div>
              </div>
              <div className="flex justify-end gap-3 pt-3 border-t border-slate-800">
                <button
                  type="button" onClick={() => setShowVehicleModal(false)}
                  className="px-4 py-2 text-xs font-semibold text-slate-400 hover:text-slate-200 cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="submit" disabled={actionLoading}
                  className="px-4 py-2 rounded-xl text-xs font-semibold bg-blue-600 hover:bg-blue-500 text-white cursor-pointer shadow-md shadow-blue-900/30"
                >
                  {actionLoading ? 'Saving...' : 'Add Vehicle'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Slot Picker Modal */}
      {selectedRecForBooking && (
        <SlotPickerModal
          recommendation={selectedRecForBooking}
          onClose={() => setSelectedRecForBooking(null)}
          onConfirm={handleConfirmReservation}
          loading={actionLoading}
        />
      )}
    </div>
  );
}
