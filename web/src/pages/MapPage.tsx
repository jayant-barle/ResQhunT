import React, { useEffect, useState, useRef } from 'react';
import { api } from '../api/client';
import { EmergencyRequest, ReliefResource } from '../types';
import { PriorityBadge } from '../components/common/PriorityBadge';
import { StatusBadge } from '../components/common/StatusBadge';
import { CategoryBadge } from '../components/common/CategoryBadge';
import { Link } from 'react-router-dom';
import { MapPin, List, Map as MapIcon, Boxes, ExternalLink, ShieldAlert } from 'lucide-react';
import L from 'leaflet';

export const MapPage: React.FC = () => {
  const [incidents, setIncidents] = useState<EmergencyRequest[]>([]);
  const [resources, setResources] = useState<ReliefResource[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [viewMode, setViewMode] = useState<'map' | 'list'>('map');
  const [selectedIncident, setSelectedIncident] = useState<EmergencyRequest | null>(null);

  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapInstanceRef = useRef<L.Map | null>(null);

  useEffect(() => {
    const fetchData = async () => {
      try {
        setLoading(true);
        const [incRes, resRes] = await Promise.all([
          api.getIncidents(),
          api.getResources()
        ]);
        setIncidents(incRes.incidents || []);
        setResources(resRes || []);
      } catch (err) {
        console.error('Failed to fetch map markers:', err);
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, []);

  // Initialize and update Leaflet map
  useEffect(() => {
    if (viewMode !== 'map' || !mapContainerRef.current) return;

    if (!mapInstanceRef.current) {
      // Center on NCR / New Delhi disaster area default
      const map = L.map(mapContainerRef.current).setView([28.6139, 77.2090], 12);

      L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
      }).addTo(map);

      mapInstanceRef.current = map;
    }

    const map = mapInstanceRef.current;

    // Clear existing markers
    map.eachLayer((layer) => {
      if (layer instanceof L.Marker) {
        map.removeLayer(layer);
      }
    });

    // Add Incident markers
    incidents.forEach((inc) => {
      if (inc.latitude && inc.longitude) {
        const isCritical = inc.priorityCategory === 'CRITICAL';
        const color = isCritical ? '#DC2626' : inc.priorityCategory === 'HIGH' ? '#EA580C' : '#2563EB';

        const customIcon = L.divIcon({
          className: 'custom-div-icon',
          html: `<div style="background-color: ${color}; width: 22px; height: 22px; border-radius: 50%; border: 3px solid white; box-shadow: 0 2px 6px rgba(0,0,0,0.4); display: flex; align-items: center; justify-content: center;">
            <div style="background-color: white; width: 6px; height: 6px; border-radius: 50%;"></div>
          </div>`,
          iconSize: [22, 22],
          iconAnchor: [11, 11]
        });

        const marker = L.marker([inc.latitude, inc.longitude], { icon: customIcon }).addTo(map);
        marker.on('click', () => {
          setSelectedIncident(inc);
        });
      }
    });

    // Add Relief Center markers (Fixed depot pins)
    const depotCoords: [number, number][] = [
      [28.6304, 77.2177],
      [28.5672, 77.2100],
      [28.6448, 77.2167]
    ];

    resources.forEach((res, idx) => {
      const coords = depotCoords[idx % depotCoords.length];
      const depotIcon = L.divIcon({
        className: 'custom-depot-icon',
        html: `<div style="background-color: #18B6A4; width: 20px; height: 20px; border-radius: 4px; border: 2px solid white; box-shadow: 0 2px 5px rgba(0,0,0,0.3); display: flex; align-items: center; justify-content: center; color: white; font-size: 10px; font-weight: bold;">R</div>`,
        iconSize: [20, 20],
        iconAnchor: [10, 10]
      });

      const marker = L.marker(coords, { icon: depotIcon }).addTo(map);
      marker.bindPopup(`<b>${res.name}</b><br/>Stock: ${res.availableQuantity} ${res.unit}<br/>Loc: ${res.location}`);
    });

    return () => {
      // Map cleanup on unmount
    };
  }, [viewMode, incidents, resources]);

  return (
    <div className="space-y-6">
      {/* Header and Toggle */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-black text-navy tracking-tight">Disaster Rescue Map</h1>
          <p className="text-sm text-gray-500 font-medium">
            Live geographic coordinate plotting with offline tabular fallback.
          </p>
        </div>

        {/* View Switcher */}
        <div className="flex items-center bg-white p-1 rounded-xl border border-gray-200 shadow-sm text-xs font-bold">
          <button
            onClick={() => setViewMode('map')}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg transition-colors ${
              viewMode === 'map' ? 'bg-navy text-white' : 'text-gray-500 hover:text-ink'
            }`}
          >
            <MapIcon className="w-3.5 h-3.5" /> Interactive Map
          </button>
          <button
            onClick={() => setViewMode('list')}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg transition-colors ${
              viewMode === 'list' ? 'bg-navy text-white' : 'text-gray-500 hover:text-ink'
            }`}
          >
            <List className="w-3.5 h-3.5" /> Coordinate List Fallback
          </button>
        </div>
      </div>

      {/* Main Map or Fallback List */}
      {viewMode === 'map' ? (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <div className="lg:col-span-2 bg-white p-2 rounded-2xl border border-gray-200/80 shadow-sm h-[600px] relative">
            <div ref={mapContainerRef} className="w-full h-full rounded-xl" />
            <div className="absolute bottom-4 left-4 bg-white/95 backdrop-blur-sm p-3 rounded-xl border border-gray-200 shadow-lg text-[11px] font-semibold text-gray-700 space-y-1.5 z-[1000]">
              <div className="flex items-center gap-2">
                <div className="w-3 h-3 rounded-full bg-emergency border-2 border-white" />
                <span>Critical Incident</span>
              </div>
              <div className="flex items-center gap-2">
                <div className="w-3 h-3 rounded-full bg-orange-600 border-2 border-white" />
                <span>High Priority Incident</span>
              </div>
              <div className="flex items-center gap-2">
                <div className="w-3 h-3 rounded bg-teal border-2 border-white" />
                <span>Relief Supply Depot</span>
              </div>
            </div>
          </div>

          {/* Incident Pin Inspector Panel */}
          <div className="bg-white rounded-2xl border border-gray-200/80 shadow-sm p-6 space-y-5">
            <h2 className="text-base font-bold text-navy border-b pb-3 flex items-center gap-2">
              <MapPin className="w-4 h-4 text-emergency" /> Selected Marker
            </h2>
            {selectedIncident ? (
              <div className="space-y-4 text-xs font-medium">
                <div className="flex items-center gap-2 flex-wrap">
                  <PriorityBadge
                    category={selectedIncident.priorityCategory}
                    score={selectedIncident.priorityScore}
                  />
                  <CategoryBadge category={selectedIncident.category} />
                  <StatusBadge status={selectedIncident.deliveryState} />
                </div>

                <div>
                  <h3 className="font-bold text-ink text-sm">{selectedIncident.description}</h3>
                  <p className="text-gray-500 mt-1">
                    {selectedIncident.locationAddress || 'No landmark entered'}
                  </p>
                </div>

                <div className="p-3 rounded-xl bg-canvas space-y-1">
                  <div className="flex justify-between">
                    <span className="text-gray-400">Coordinates:</span>
                    <span className="font-mono font-bold text-navy">
                      {selectedIncident.latitude?.toFixed(5)}, {selectedIncident.longitude?.toFixed(5)}
                    </span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-gray-400">Affected Persons:</span>
                    <span className="font-bold text-navy">{selectedIncident.affectedCount}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-gray-400">Relay Hops:</span>
                    <span className="font-mono text-navy">{selectedIncident.syncHopCount}</span>
                  </div>
                </div>

                <Link
                  to={`/incidents/${selectedIncident.id}`}
                  className="w-full py-2.5 rounded-xl bg-navy hover:bg-navy-light text-white font-bold flex items-center justify-center gap-1.5 transition-colors"
                >
                  Open Full Incident Details <ExternalLink className="w-3.5 h-3.5" />
                </Link>
              </div>
            ) : (
              <div className="py-16 text-center text-gray-400 text-xs">
                Click any incident pin on the map to inspect details and initiate dispatch.
              </div>
            )}
          </div>
        </div>
      ) : (
        /* List-Based Fallback View (Operational when tile server / web is disconnected) */
        <div className="bg-white rounded-2xl border border-gray-200/80 shadow-sm p-6 space-y-4">
          <div className="flex items-center gap-2 text-xs font-bold text-amber-800 bg-amber-50 p-3 rounded-xl border border-amber-200">
            <ShieldAlert className="w-4 h-4 shrink-0" />
            <span>
              Offline Fallback Active: Displaying cached geographic coordinate bearings and landmark locations directly from local records.
            </span>
          </div>

          <div className="divide-y divide-gray-100">
            {incidents.map((inc) => (
              <div key={inc.id} className="py-3.5 flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs">
                <div className="space-y-1">
                  <div className="flex items-center gap-2">
                    <PriorityBadge category={inc.priorityCategory} score={inc.priorityScore} />
                    <CategoryBadge category={inc.category} />
                    <StatusBadge status={inc.deliveryState} />
                  </div>
                  <p className="font-bold text-ink text-sm">{inc.description}</p>
                  <p className="text-gray-500">
                    Coords: {inc.latitude ? `${inc.latitude.toFixed(6)}, ${inc.longitude?.toFixed(6)}` : 'GPS Unset'} • {inc.locationAddress || 'No landmark specified'}
                  </p>
                </div>
                <Link
                  to={`/incidents/${inc.id}`}
                  className="self-start sm:self-center px-3 py-1.5 rounded-lg bg-navy text-white font-bold hover:bg-navy-light"
                >
                  Manage
                </Link>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};
