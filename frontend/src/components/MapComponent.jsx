import React, { useEffect } from 'react';
import { MapContainer, TileLayer, Marker, Popup, Circle, Polyline, useMapEvents, useMap } from 'react-leaflet';
import L from 'leaflet';
import { Zap, Navigation, MapPin } from 'lucide-react';

// Custom Car Pin DivIcon (Daylight Precision: Electric Blue #2563EB)
const createCarIcon = () =>
  L.divIcon({
    className: 'custom-car-marker',
    html: `
      <div style="width: 32px; height: 32px; display: flex; align-items: center; justify-content: center;">
        <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
          <path d="M19 17h2c.6 0 1-.4 1-1v-3c0-.9-.7-1.7-1.5-1.9C18.7 10.6 16 10 16 10s-1.3-1.4-2.2-2.3c-.5-.4-1.1-.7-1.8-.7H5c-.6 0-1.1.4-1.4.9l-1.5 2.8C2.1 11.1 2 11.5 2 12v4c0 .6.4 1 1 1h2"/>
          <circle cx="7" cy="17" r="2"/>
          <path d="M9 17h6"/>
          <circle cx="17" cy="17" r="2"/>
        </svg>
      </div>
    `,
    iconSize: [32, 32],
    iconAnchor: [16, 16],
  });

// Custom Highway Start Pin (Deep Slate #0F172A)
const createStartIcon = () =>
  L.divIcon({
    className: 'custom-start-marker',
    html: `
      <div style="width: 30px; height: 30px; border-radius: 9999px; background: #0F172A; border: 2.5px solid #FFFFFF; box-shadow: 0 4px 10px rgba(15, 23, 42, 0.25); display: flex; align-items: center; justify-content: center; color: #FFFFFF;">
        <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
          <path d="m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
        </svg>
      </div>
    `,
    iconSize: [30, 30],
    iconAnchor: [15, 15],
  });

// Custom Highway Destination Pin (Electric Blue #2563EB)
const createDestIcon = () =>
  L.divIcon({
    className: 'custom-dest-marker',
    html: `
      <div style="width: 30px; height: 30px; border-radius: 9999px; background: #2563EB; border: 2.5px solid #FFFFFF; box-shadow: 0 4px 10px rgba(37, 99, 235, 0.3); display: flex; align-items: center; justify-content: center; color: #FFFFFF;">
        <svg xmlns="http://www.w3.org/2000/svg" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="12" cy="12" r="10"/>
          <circle cx="12" cy="12" r="3"/>
        </svg>
      </div>
    `,
    iconSize: [30, 30],
    iconAnchor: [15, 15],
  });

// Custom Numbered Stop Icon (Stop 1, 2, 3...)
const createStopIcon = (stopNumber) =>
  L.divIcon({
    className: 'custom-stop-marker',
    html: `
      <div style="width: 30px; height: 30px; border-radius: 9999px; background: #2563EB; border: 2.5px solid #FFFFFF; box-shadow: 0 4px 10px rgba(37, 99, 235, 0.35); display: flex; align-items: center; justify-content: center; color: #FFFFFF;">
        <span style="font-size: 12px; font-weight: 800; font-family: monospace;">${stopNumber}</span>
      </div>
    `,
    iconSize: [30, 30],
    iconAnchor: [15, 15],
  });

// Custom Station Pin DivIcon
const createStationIcon = (isSelected = false) =>
  L.divIcon({
    className: `custom-station-marker ${isSelected ? 'selected' : ''}`,
    html: `
      <div style="width: 26px; height: 26px; display: flex; align-items: center; justify-content: center;">
        <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
          <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"></polygon>
        </svg>
      </div>
    `,
    iconSize: [26, 26],
    iconAnchor: [13, 13],
  });

// Click handler component
function MapEvents({ onMapClick }) {
  useMapEvents({
    click(e) {
      if (onMapClick) {
        onMapClick(e.latlng.lat, e.latlng.lng);
      }
    },
  });
  return null;
}

// Center updater
function MapCenterController({ center, zoom }) {
  const map = useMap();
  useEffect(() => {
    if (center && center[0] && center[1]) {
      map.flyTo(center, zoom || map.getZoom(), { duration: 1.2 });
    }
  }, [center, zoom, map]);
  return null;
}

// Bounds fitter for corridor routes
function MapBoundsController({ bounds }) {
  const map = useMap();
  useEffect(() => {
    if (bounds && bounds.length >= 2) {
      map.fitBounds(bounds, { padding: [40, 40], maxZoom: 13 });
    }
  }, [bounds, map]);
  return null;
}

// Invalidate size on mount & resize to prevent gray or broken tiles
function MapResizeController() {
  const map = useMap();
  useEffect(() => {
    const timer = setTimeout(() => {
      map.invalidateSize();
    }, 200);
    const handleResize = () => map.invalidateSize();
    window.addEventListener('resize', handleResize);
    return () => {
      clearTimeout(timer);
      window.removeEventListener('resize', handleResize);
    };
  }, [map]);
  return null;
}

export default function MapComponent({
  userLat,
  userLng,
  stations = [],
  selectedStationId = null,
  onMapClick,
  onSelectStation,
  safeRangeKm = null,
  routeDestination = null,
  zoom = 13,
  height = '420px',
  className = '',
  // Highway Trip Planner props
  tripWaypoints = null,
  tripStops = [],
  tripStart = null,
  tripDest = null,
}) {
  const center = [Number(userLat) || 22.3072, Number(userLng) || 73.1812];

  // Compute bounding box if tripWaypoints or trip endpoints exist
  const tripBounds = React.useMemo(() => {
    if (tripWaypoints && tripWaypoints.length >= 2) {
      return tripWaypoints.map((p) => [Number(p[0] ?? p.lat), Number(p[1] ?? p.lng)]);
    }
    if (tripStart && tripDest) {
      return [
        [Number(tripStart.lat), Number(tripStart.lng)],
        [Number(tripDest.lat), Number(tripDest.lng)],
      ];
    }
    return null;
  }, [tripWaypoints, tripStart, tripDest]);

  const polylinePositions = React.useMemo(() => {
    if (!tripWaypoints || tripWaypoints.length < 2) return null;
    return tripWaypoints.map((p) => [Number(p[0] ?? p.lat), Number(p[1] ?? p.lng)]);
  }, [tripWaypoints]);

  return (
    <div
      className={`relative overflow-hidden bg-white ${
        className || (height === '100%' ? 'w-full h-full' : 'rounded-2xl border border-slate-200 shadow-sm')
      }`}
      style={{ height }}
    >
      <MapContainer
        center={center}
        zoom={zoom}
        scrollWheelZoom={true}
        style={{ width: '100%', height: '100%' }}
      >
        {/* OpenStreetMap Tile Layer (Free, No API Key) */}
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
          url="https://tile.openstreetmap.org/{z}/{x}/{y}.png"
          maxZoom={19}
        />

        <MapResizeController />
        <MapCenterController center={center} zoom={zoom} />
        {tripBounds && <MapBoundsController bounds={tripBounds} />}
        {onMapClick && <MapEvents onMapClick={onMapClick} />}

        {/* Dynamic Safe-Range Radius Circle */}
        {userLat && userLng && safeRangeKm && safeRangeKm > 0 && (
          <Circle
            center={[Number(userLat), Number(userLng)]}
            radius={safeRangeKm * 1000}
            pathOptions={{
              color: '#2563EB',
              fillColor: '#2563EB',
              fillOpacity: 0.05,
              weight: 1.5,
              dashArray: '5, 5',
            }}
          />
        )}

        {/* Highway Multi-Stop Route Polylines */}
        {polylinePositions && (
          <>
            {/* Outer Subtle Path */}
            <Polyline
              positions={polylinePositions}
              pathOptions={{
                color: '#93C5FD',
                weight: 7,
                opacity: 0.45,
              }}
            />
            {/* Inner Precision Line */}
            <Polyline
              positions={polylinePositions}
              pathOptions={{
                color: '#2563EB',
                weight: 3.5,
                opacity: 0.95,
              }}
            />
          </>
        )}

        {/* Highway Start Pin */}
        {tripStart && tripStart.lat && tripStart.lng && (
          <Marker position={[Number(tripStart.lat), Number(tripStart.lng)]} icon={createStartIcon()}>
            <Popup>
              <div className="text-xs space-y-1">
                <div className="font-bold text-slate-900 flex items-center gap-1">
                  <span>🚩 Trip Origin</span>
                </div>
                <div className="text-slate-700 font-semibold">{tripStart.name || 'Start Point'}</div>
                <div className="text-slate-500 text-[10px] font-mono">
                  ({Number(tripStart.lat).toFixed(4)}, {Number(tripStart.lng).toFixed(4)})
                </div>
              </div>
            </Popup>
          </Marker>
        )}

        {/* Highway Destination Pin */}
        {tripDest && tripDest.lat && tripDest.lng && (
          <Marker position={[Number(tripDest.lat), Number(tripDest.lng)]} icon={createDestIcon()}>
            <Popup>
              <div className="text-xs space-y-1">
                <div className="font-bold text-blue-600 flex items-center gap-1">
                  <span>🎯 Final Destination</span>
                </div>
                <div className="text-slate-700 font-semibold">{tripDest.name || 'Destination Point'}</div>
                <div className="text-slate-500 text-[10px] font-mono">
                  ({Number(tripDest.lat).toFixed(4)}, {Number(tripDest.lng).toFixed(4)})
                </div>
              </div>
            </Popup>
          </Marker>
        )}

        {/* Highway Numbered Charging Stop Pins */}
        {tripStops &&
          tripStops.map((stop) => {
            const sLat = Number(stop.latitude);
            const sLng = Number(stop.longitude);
            if (isNaN(sLat) || isNaN(sLng)) return null;

            return (
              <Marker
                key={`trip-stop-${stop.stopNumber}-${sLat}-${sLng}`}
                position={[sLat, sLng]}
                icon={createStopIcon(stop.stopNumber)}
              >
                <Popup>
                  <div className="text-xs space-y-1.5 min-w-[210px]">
                    <div className="flex items-center justify-between border-b border-slate-100 pb-1.5">
                      <span className="font-extrabold text-blue-600 text-sm">
                        Stop #{stop.stopNumber}
                      </span>
                      <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-blue-50 text-blue-700 border border-blue-200">
                        ⚡ Charge {Math.round(stop.chargeDurationMinutes)} min
                      </span>
                    </div>

                    <div className="font-bold text-slate-900">{stop.stationName}</div>
                    <div className="text-slate-500 text-[11px]">{stop.address || stop.city}</div>

                    <div className="p-2.5 rounded-xl bg-slate-50 border border-slate-200 space-y-1 mt-1">
                      <div className="flex justify-between text-[11px]">
                        <span className="text-slate-500">Bay:</span>
                        <span className="text-slate-900 font-semibold">
                          {stop.chargerName} ({stop.powerKw} kW)
                        </span>
                      </div>
                      <div className="flex justify-between text-[11px]">
                        <span className="text-slate-500">Battery Arrival:</span>
                        <span className="text-amber-700 font-mono font-bold">
                          {Math.round(stop.arrivalBatteryPercent)}% → {Math.round(stop.targetBatteryPercent)}%
                        </span>
                      </div>
                      <div className="flex justify-between text-[11px]">
                        <span className="text-slate-500">Energy & Cost:</span>
                        <span className="text-slate-900 font-mono font-bold">
                          +{Math.round(stop.energyChargedKwh)} kWh (₹{Math.round(stop.estimatedCost)})
                        </span>
                      </div>
                    </div>
                  </div>
                </Popup>
              </Marker>
            );
          })}

        {/* Dynamic Route Polyline to Destination */}
        {userLat && userLng && routeDestination && (
          <Polyline
            positions={[
              [Number(userLat), Number(userLng)],
              [Number(routeDestination[0]), Number(routeDestination[1])],
            ]}
            pathOptions={{
              color: '#2563EB',
              weight: 3.5,
              dashArray: '6, 6',
              opacity: 0.85,
            }}
          />
        )}

        {/* User / Car Location Pin */}
        {userLat && userLng && (
          <Marker position={[Number(userLat), Number(userLng)]} icon={createCarIcon()}>
            <Popup>
              <div className="text-xs space-y-1">
                <div className="font-bold text-blue-600 flex items-center gap-1">
                  <Navigation className="w-3.5 h-3.5" />
                  <span>EV Current Location</span>
                </div>
                <div className="text-slate-600 font-mono text-[11px]">
                  ({Number(userLat).toFixed(4)}, {Number(userLng).toFixed(4)})
                </div>
                {safeRangeKm && (
                  <div className="text-slate-900 font-semibold text-[11px]">
                    Safe Range Bubble: <span className="font-mono text-blue-600">{Math.round(safeRangeKm)} km</span>
                  </div>
                )}
                <div className="text-[10px] text-slate-400">Click anywhere on the map to relocate</div>
              </div>
            </Popup>
          </Marker>
        )}

        {/* Charging Station Pins */}
        {stations.map((st) => {
          const lat = Number(st.latitude);
          const lng = Number(st.longitude);
          if (isNaN(lat) || isNaN(lng)) return null;

          const isSelected = selectedStationId === st.id;

          return (
            <Marker
              key={st.id || `${lat}-${lng}`}
              position={[lat, lng]}
              icon={createStationIcon(isSelected)}
              eventHandlers={{
                click: () => onSelectStation && onSelectStation(st),
              }}
            >
              <Popup>
                <div className="text-xs space-y-1.5 min-w-[170px]">
                  <div className="flex items-center justify-between gap-2 border-b border-slate-100 pb-1">
                    <span className="font-bold text-slate-900 text-sm">{st.name || st.stationName}</span>
                    <span className="text-[9px] uppercase font-bold px-1.5 py-0.5 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200">
                      {st.status || 'Active'}
                    </span>
                  </div>
                  <div className="text-slate-600 flex items-center gap-1 text-[11px]">
                    <MapPin className="w-3.5 h-3.5 text-slate-400 flex-shrink-0" />
                    <span className="truncate">{st.address}, {st.city}</span>
                  </div>
                  {st.chargers && st.chargers.length > 0 && (
                    <div className="text-[11px] text-slate-700 font-medium pt-0.5">
                      ⚡ <span className="font-semibold text-slate-900">{st.chargers.length} Charger Bay{st.chargers.length > 1 ? 's' : ''}</span>
                    </div>
                  )}
                </div>
              </Popup>
            </Marker>
          );
        })}
      </MapContainer>
    </div>
  );
}
