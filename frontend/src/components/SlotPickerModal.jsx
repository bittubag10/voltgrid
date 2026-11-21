import React, { useState, useMemo, useEffect } from 'react';
import { Clock, Zap, Calendar, X, ArrowRight } from 'lucide-react';

const RUPEE = '\u20B9';
const ARROW = '\u2192';
const BULLET = '\u2022';

export default function SlotPickerModal({
  recommendation,
  onClose,
  onConfirm,
  loading = false,
}) {
  // If current local time is past 23:30, all slots today are in the past -> default to Tomorrow (offset 1)
  const [selectedDayOffset, setSelectedDayOffset] = useState(() => {
    const now = new Date();
    if (now.getHours() >= 23 && now.getMinutes() >= 30) {
      return 1;
    }
    return 0;
  });

  const [selectedSlotTime, setSelectedSlotTime] = useState(null); // e.g. "10:30"
  const [durationMinutes, setDurationMinutes] = useState(45); // 15, 30, 45, 60, 90

  // Generate 3 date options
  const dateOptions = useMemo(() => {
    return [0, 1, 2].map((offset) => {
      const d = new Date();
      d.setDate(d.getDate() + offset);
      return {
        offset,
        label: offset === 0 ? 'Today' : offset === 1 ? 'Tomorrow' : d.toLocaleDateString([], { weekday: 'short' }),
        dateStr: d.toLocaleDateString([], { month: 'short', day: 'numeric' }),
        fullDate: d,
      };
    });
  }, []);

  // Compute next valid 15-minute boundary that is at least 15 minutes in the future
  const nextFutureBoundary = useMemo(() => {
    const now = new Date();
    const minTime = new Date(now.getTime() + 15 * 60 * 1000);
    const rem = minTime.getMinutes() % 15;
    if (rem > 0) {
      minTime.setMinutes(minTime.getMinutes() + (15 - rem));
    }
    minTime.setSeconds(0, 0);
    return minTime;
  }, []);

  // Generate 15-minute aligned slots for the day (from 06:00 to 23:45)
  const availableSlots = useMemo(() => {
    const slots = [];
    const isToday = selectedDayOffset === 0;

    for (let hour = 6; hour < 24; hour++) {
      for (let min = 0; min < 60; min += 15) {
        const timeStr = `${hour.toString().padStart(2, '0')}:${min.toString().padStart(2, '0')}`;
        const slotDate = new Date();
        slotDate.setDate(slotDate.getDate() + selectedDayOffset);
        slotDate.setHours(hour, min, 0, 0);

        // A slot is past if today and slot time is before the next valid boundary (at least 15min in future)
        const isPast = isToday && slotDate.getTime() < nextFutureBoundary.getTime();

        slots.push({
          timeStr,
          dateObj: slotDate,
          isPast,
        });
      }
    }
    return slots;
  }, [selectedDayOffset, nextFutureBoundary]);

  // Synchronize default selected slot whenever availableSlots or selectedDayOffset changes
  useEffect(() => {
    const firstValid = availableSlots.find((s) => !s.isPast);
    if (!firstValid) return;

    if (selectedDayOffset === 0) {
      // For Today: default to next valid 15-min boundary >= 15 min from now
      const boundaryTimeStr = `${nextFutureBoundary.getHours().toString().padStart(2, '0')}:${nextFutureBoundary.getMinutes().toString().padStart(2, '0')}`;
      const matchingBoundary = availableSlots.find((s) => s.timeStr === boundaryTimeStr && !s.isPast);
      setSelectedSlotTime(matchingBoundary ? matchingBoundary.timeStr : firstValid.timeStr);
    } else {
      // For Tomorrow/Day After: default to 09:00 if valid, or first slot
      const defaultMorning = availableSlots.find((s) => s.timeStr === '09:00' && !s.isPast);
      setSelectedSlotTime(defaultMorning ? defaultMorning.timeStr : firstValid.timeStr);
    }
  }, [availableSlots, selectedDayOffset, nextFutureBoundary]);

  const handleSubmit = (e) => {
    e.preventDefault();
    const targetSlot = availableSlots.find((s) => s.timeStr === selectedSlotTime);
    if (!targetSlot) return;

    const start = new Date(targetSlot.dateObj);
    start.setSeconds(0, 0);
    const end = new Date(start.getTime() + durationMinutes * 60 * 1000);
    end.setSeconds(0, 0);

    // Clean UTC ISO-8601 strings for backend Instant deserializer
    onConfirm(start.toISOString(), end.toISOString());
  };

  const selectedSlotObj = availableSlots.find((s) => s.timeStr === selectedSlotTime);
  const calculatedEndTime = selectedSlotObj
    ? new Date(selectedSlotObj.dateObj.getTime() + durationMinutes * 60 * 1000)
    : null;

  return (
    <div className="fixed inset-0 z-[9990] flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-sm animate-in fade-in duration-150">
      <div className="w-full max-w-xl bg-[#111827] border border-slate-700 rounded-2xl p-6 sm:p-7 shadow-2xl space-y-5 max-h-[90vh] overflow-y-auto text-slate-100">
        {/* Modal Header */}
        <div className="flex items-start justify-between border-b border-slate-800 pb-4">
          <div>
            <div className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full bg-blue-600/20 text-blue-400 border border-blue-500/30 text-xs font-semibold mb-1.5">
              <Zap className="w-3.5 h-3.5" />
              15-Minute Slot Reservation
            </div>
            <h3 className="text-xl font-bold text-slate-100">{recommendation.chargerName}</h3>
            <p className="text-xs text-slate-400 mt-0.5">{recommendation.stationName} {BULLET} {recommendation.city}</p>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:text-slate-100 hover:bg-slate-800 transition-colors cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Charger Quick Specs */}
        <div className="grid grid-cols-3 gap-3 p-3.5 rounded-xl bg-[#1E293B] border border-slate-700/70 text-xs">
          <div>
            <span className="text-[10px] text-slate-400 block uppercase font-medium">Connector</span>
            <span className="font-semibold text-slate-100 font-mono">{recommendation.connectorType}</span>
          </div>
          <div>
            <span className="text-[10px] text-slate-400 block uppercase font-medium">Power</span>
            <span className="font-semibold text-emerald-400 font-mono">{recommendation.powerKw} kW</span>
          </div>
          <div>
            <span className="text-[10px] text-slate-400 block uppercase font-medium">Tariff Rate</span>
            <span className="font-semibold text-slate-100 font-mono">{RUPEE}{recommendation.pricePerKwh}/kWh</span>
          </div>
        </div>

        <form onSubmit={handleSubmit} className="space-y-5">
          {/* 1. Date Selector Pills */}
          <div>
            <label className="block text-xs font-semibold text-slate-300 mb-2 flex items-center gap-1.5">
              <Calendar className="w-3.5 h-3.5 text-blue-400" />
              Select Date
            </label>
            <div className="grid grid-cols-3 gap-2.5">
              {dateOptions.map((opt) => (
                <button
                  key={opt.offset}
                  type="button"
                  onClick={() => setSelectedDayOffset(opt.offset)}
                  className={`p-2.5 rounded-xl border text-center transition-all cursor-pointer ${
                    selectedDayOffset === opt.offset
                      ? 'bg-blue-600 border-blue-500 text-white shadow-md shadow-blue-900/30'
                      : 'bg-[#1E293B] border-slate-700 text-slate-300 hover:border-slate-600 hover:text-slate-100'
                  }`}
                >
                  <div className="text-xs font-semibold">{opt.label}</div>
                  <div className={`text-[11px] mt-0.5 ${selectedDayOffset === opt.offset ? 'text-blue-100' : 'text-slate-400'}`}>{opt.dateStr}</div>
                </button>
              ))}
            </div>
          </div>

          {/* 2. 15-Minute Slot Grid */}
          <div>
            <label className="block text-xs font-semibold text-slate-300 mb-2 flex items-center justify-between">
              <span className="flex items-center gap-1.5">
                <Clock className="w-3.5 h-3.5 text-blue-400" />
                Select Start Time
              </span>
              <span className="text-[11px] text-blue-400 font-mono font-semibold">
                {selectedSlotTime ? `${selectedSlotTime} selected` : 'None selected'}
              </span>
            </label>

            <div className="grid grid-cols-4 sm:grid-cols-6 gap-1.5 max-h-44 overflow-y-auto p-2 bg-[#0F172A] rounded-xl border border-slate-800">
              {availableSlots.map((slot) => {
                const isSelected = selectedSlotTime === slot.timeStr;
                return (
                  <button
                    key={slot.timeStr}
                    type="button"
                    disabled={slot.isPast}
                    onClick={() => setSelectedSlotTime(slot.timeStr)}
                    className={`py-2 px-1 text-xs font-mono rounded-lg border text-center transition-all ${
                      slot.isPast
                        ? 'opacity-25 cursor-not-allowed border-transparent bg-slate-900/50 text-slate-600'
                        : isSelected
                        ? 'bg-blue-600 text-white border-blue-500 font-bold shadow-md shadow-blue-900/30 cursor-pointer'
                        : 'bg-[#1E293B] border-slate-700 text-slate-300 hover:border-blue-500 hover:text-blue-400 cursor-pointer'
                    }`}
                  >
                    {slot.timeStr}
                  </button>
                );
              })}
            </div>
          </div>

          {/* 3. Duration Selector Pills */}
          <div>
            <label className="block text-xs font-semibold text-slate-300 mb-2 flex items-center gap-1.5">
              <Clock className="w-3.5 h-3.5 text-blue-400" />
              Charging Duration
            </label>
            <div className="grid grid-cols-5 gap-2">
              {[15, 30, 45, 60, 90].map((mins) => (
                <button
                  key={mins}
                  type="button"
                  onClick={() => setDurationMinutes(mins)}
                  className={`py-2 text-xs font-medium rounded-xl border transition-all cursor-pointer ${
                    durationMinutes === mins
                      ? 'bg-blue-600 border-blue-500 text-white font-semibold shadow-md shadow-blue-900/30'
                      : 'bg-[#1E293B] border-slate-700 text-slate-300 hover:border-slate-600 hover:text-slate-100'
                  }`}
                >
                  {mins} min
                </button>
              ))}
            </div>
          </div>

          {/* 4. Booking Summary Box */}
          {selectedSlotObj && calculatedEndTime && (
            <div className="p-4 rounded-xl bg-blue-950/40 border border-blue-800/60 text-xs space-y-1 text-slate-200">
              <div className="text-[10px] uppercase font-bold text-blue-400 tracking-wider">Reservation Summary</div>
              <div className="text-slate-100 font-semibold text-sm">
                {selectedSlotObj.dateObj.toLocaleDateString([], { month: 'short', day: 'numeric' })} at{' '}
                <span className="font-bold text-blue-400 font-mono">{selectedSlotTime}</span> {ARROW}{' '}
                <span className="font-bold text-blue-400 font-mono">{calculatedEndTime.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span> ({durationMinutes} mins)
              </div>
              <div className="text-slate-400 text-[11px]">
                Bay is held for 5 minutes under <code className="text-amber-400 font-mono font-semibold">PENDING_PAYMENT</code> until confirmed.
              </div>
            </div>
          )}

          {/* Modal Actions */}
          <div className="flex items-center justify-end gap-3 pt-3 border-t border-slate-800">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2.5 rounded-xl text-xs font-medium text-slate-400 hover:text-slate-100 hover:bg-slate-800 transition-colors cursor-pointer"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={loading || !selectedSlotTime}
              className="flex items-center gap-2 px-5 py-2.5 rounded-xl text-xs font-semibold bg-blue-600 hover:bg-blue-500 text-white transition-all shadow-md shadow-blue-900/30 disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer"
            >
              <span>{loading ? 'Holding Bay...' : 'Confirm Reservation'}</span>
              <ArrowRight className="w-4 h-4" />
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
