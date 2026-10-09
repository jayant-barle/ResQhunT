import React, { useEffect, useState } from 'react';
import { api } from '../api/client';
import { EmergencyRequest, Volunteer, ReliefResource } from '../types';
import { StatusBadge } from '../components/common/StatusBadge';
import { PriorityBadge } from '../components/common/PriorityBadge';
import { CategoryBadge } from '../components/common/CategoryBadge';
import { Link } from 'react-router-dom';
import {
  AlertTriangle,
  Radio,
  Users,
  Boxes,
  PlusCircle,
  ExternalLink,
  ShieldCheck
} from 'lucide-react';

export const Overview: React.FC = () => {
  const [incidents, setIncidents] = useState<EmergencyRequest[]>([]);
  const [volunteers, setVolunteers] = useState<Volunteer[]>([]);
  const [resources, setResources] = useState<ReliefResource[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const [showCreateModal, setShowCreateModal] = useState<boolean>(false);
  const [newSos, setNewSos] = useState({
    category: 'MEDICAL',
    severity: 'CRITICAL',
    affectedCount: 2,
    description: '',
    latitude: 28.6139,
    longitude: 77.2090,
    locationAddress: ''
  });
  const [submitting, setSubmitting] = useState<boolean>(false);

  const fetchData = async () => {
    try {
      setLoading(true);
      setError(null);
      const [incRes, volRes, resRes] = await Promise.all([
        api.getIncidents(),
        api.getVolunteers(),
        api.getResources()
      ]);
      setIncidents(incRes.incidents || []);
      setVolunteers(volRes || []);
      setResources(resRes || []);
    } catch (err: any) {
      setError(err.message || 'Failed to connect to ResQhunT backend');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
    const interval = setInterval(fetchData, 10000); // Polling every 10s for real-time mesh uploads
    return () => clearInterval(interval);
  }, []);

  const handleCreateSos = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    try {
      await api.createSos(newSos);
      setShowCreateModal(false);
      setNewSos({
        category: 'MEDICAL',
        severity: 'CRITICAL',
        affectedCount: 2,
        description: '',
        latitude: 28.6139,
        longitude: 77.2090,
        locationAddress: ''
      });
      fetchData();
    } catch (err: any) {
      alert(`Error creating emergency: ${err.message}`);
    } finally {
      setSubmitting(false);
    }
  };

  const criticalCount = incidents.filter((i) => i.priorityCategory === 'CRITICAL').length;
  const pendingCount = incidents.filter(
    (i) => i.deliveryState === 'SERVER_RECEIVED' || i.deliveryState === 'STORED_LOCALLY'
  ).length;
  const activeVolunteersCount = volunteers.filter((v) => v.isAvailable).length;

  return (
    <div className="space-y-8">
      {/* Top Header & Fast Action */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-black text-navy tracking-tight">Disaster Rescue Command Center</h1>
          <p className="text-sm text-gray-500 font-medium">
            Real-time incident triage and offline mesh coordination.
          </p>
        </div>
        <button
          onClick={() => setShowCreateModal(true)}
          className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-emergency hover:bg-emergency-dark text-white text-sm font-bold shadow-md shadow-red-900/20 transition-all"
        >
          <PlusCircle className="w-4 h-4" />
          Simulate / Create SOS
        </button>
      </div>

      {error && (
        <div className="p-4 rounded-xl bg-red-50 border border-red-200 text-red-700 text-sm flex items-center gap-2">
          <AlertTriangle className="w-4 h-4 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
        <div className="bg-white p-5 rounded-2xl border border-gray-200/80 shadow-sm flex items-center justify-between">
          <div>
            <p className="text-xs font-bold text-gray-400 uppercase tracking-wider">Total Incidents</p>
            <p className="text-3xl font-black text-navy mt-1">{loading ? '...' : incidents.length}</p>
          </div>
          <div className="w-12 h-12 rounded-xl bg-navy/5 flex items-center justify-center text-navy">
            <Radio className="w-6 h-6" />
          </div>
        </div>

        <div className="bg-white p-5 rounded-2xl border border-red-200/80 shadow-sm flex items-center justify-between">
          <div>
            <p className="text-xs font-bold text-emergency uppercase tracking-wider">Critical Priority</p>
            <p className="text-3xl font-black text-emergency mt-1">{loading ? '...' : criticalCount}</p>
          </div>
          <div className="w-12 h-12 rounded-xl bg-emergency/10 flex items-center justify-center text-emergency">
            <AlertTriangle className="w-6 h-6" />
          </div>
        </div>

        <div className="bg-white p-5 rounded-2xl border border-purple-200/80 shadow-sm flex items-center justify-between">
          <div>
            <p className="text-xs font-bold text-purple-700 uppercase tracking-wider">Awaiting Triage</p>
            <p className="text-3xl font-black text-purple-700 mt-1">{loading ? '...' : pendingCount}</p>
          </div>
          <div className="w-12 h-12 rounded-xl bg-purple-50 flex items-center justify-center text-purple-700">
            <ShieldCheck className="w-6 h-6" />
          </div>
        </div>

        <div className="bg-white p-5 rounded-2xl border border-teal/30 shadow-sm flex items-center justify-between">
          <div>
            <p className="text-xs font-bold text-teal-dark uppercase tracking-wider">Active Volunteers</p>
            <p className="text-3xl font-black text-teal-dark mt-1">{loading ? '...' : activeVolunteersCount}</p>
          </div>
          <div className="w-12 h-12 rounded-xl bg-teal/10 flex items-center justify-center text-teal-dark">
            <Users className="w-6 h-6" />
          </div>
        </div>
      </div>

      {/* Main Grid: Active Incidents & Resource Status */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* Urgent Incidents Table */}
        <div className="lg:col-span-2 bg-white rounded-2xl border border-gray-200/80 shadow-sm p-6 space-y-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <h2 className="text-lg font-bold text-navy">Priority Incident Queue</h2>
              <span className="text-xs font-semibold px-2 py-0.5 rounded-full bg-gray-100 text-gray-600">
                Sorted by Urgency Score
              </span>
            </div>
            <Link
              to="/incidents"
              className="text-xs font-bold text-teal-dark hover:underline flex items-center gap-1"
            >
              View All <ExternalLink className="w-3 h-3" />
            </Link>
          </div>

          {loading ? (
            <div className="py-12 text-center text-gray-400 text-sm">Loading emergency incidents...</div>
          ) : incidents.length === 0 ? (
            <div className="py-12 text-center text-gray-400 text-sm">No active emergency incidents logged.</div>
          ) : (
            <div className="divide-y divide-gray-100">
              {incidents.slice(0, 5).map((inc) => (
                <div key={inc.id} className="py-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3 hover:bg-gray-50/50 p-2 rounded-xl transition-colors">
                  <div className="space-y-1.5 flex-1">
                    <div className="flex items-center gap-2 flex-wrap">
                      <PriorityBadge category={inc.priorityCategory} score={inc.priorityScore} />
                      <CategoryBadge category={inc.category} />
                      <StatusBadge status={inc.deliveryState} />
                      <span className="text-[11px] font-mono text-gray-400">
                        {inc.syncHopCount > 0 ? `${inc.syncHopCount} mesh hops` : 'direct sync'}
                      </span>
                    </div>
                    <p className="text-sm font-semibold text-ink line-clamp-1">{inc.description}</p>
                    <p className="text-xs text-gray-500">
                      Location: {inc.locationAddress || `${inc.latitude?.toFixed(4)}, ${inc.longitude?.toFixed(4)}`} • {inc.affectedCount} person(s) affected
                    </p>
                  </div>
                  <Link
                    to={`/incidents/${inc.id}`}
                    className="self-start sm:self-center px-3 py-1.5 rounded-lg bg-navy hover:bg-navy-light text-white text-xs font-bold transition-colors"
                  >
                    Triage & Dispatch
                  </Link>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Relief Resources Inventory Snapshot */}
        <div className="bg-white rounded-2xl border border-gray-200/80 shadow-sm p-6 space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-bold text-navy flex items-center gap-2">
              <Boxes className="w-5 h-5 text-teal" />
              Relief Resources
            </h2>
            <Link to="/resources" className="text-xs font-bold text-teal-dark hover:underline flex items-center gap-1">
              Manage <ExternalLink className="w-3 h-3" />
            </Link>
          </div>

          {loading ? (
            <div className="py-8 text-center text-gray-400 text-sm">Loading resources...</div>
          ) : resources.length === 0 ? (
            <div className="py-8 text-center text-gray-400 text-sm">No relief resources registered.</div>
          ) : (
            <div className="space-y-3">
              {resources.map((res) => {
                const percent = Math.round((res.availableQuantity / res.totalQuantity) * 100);
                return (
                  <div key={res.id} className="p-3 rounded-xl bg-canvas border border-gray-200/60 space-y-2">
                    <div className="flex items-center justify-between text-xs font-bold text-navy">
                      <span className="line-clamp-1">{res.name}</span>
                      <span className={percent < 20 ? 'text-emergency' : 'text-teal-dark'}>
                        {res.availableQuantity} / {res.totalQuantity} {res.unit}
                      </span>
                    </div>
                    <div className="w-full bg-gray-200 h-2 rounded-full overflow-hidden">
                      <div
                        className={`h-full rounded-full transition-all ${
                          percent < 20 ? 'bg-emergency' : percent < 50 ? 'bg-amber-500' : 'bg-teal'
                        }`}
                        style={{ width: `${percent}%` }}
                      />
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>

      {/* Modal for Simulating / Creating SOS */}
      {showCreateModal && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-2xl max-w-lg w-full p-6 shadow-2xl border border-gray-200 space-y-5">
            <div className="flex items-center justify-between border-b pb-3">
              <h3 className="font-bold text-lg text-navy">Trigger Emergency SOS Request</h3>
              <button
                onClick={() => setShowCreateModal(false)}
                className="text-gray-400 hover:text-gray-600 font-bold"
              >
                ✕
              </button>
            </div>
            <form onSubmit={handleCreateSos} className="space-y-4 text-xs font-semibold">
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-gray-600 mb-1">Category</label>
                  <select
                    value={newSos.category}
                    onChange={(e) => setNewSos({ ...newSos, category: e.target.value })}
                    className="w-full p-2.5 rounded-lg border border-gray-300 font-medium"
                  >
                    <option value="MEDICAL">MEDICAL</option>
                    <option value="RESCUE">RESCUE</option>
                    <option value="FOOD">FOOD</option>
                    <option value="WATER">WATER</option>
                    <option value="SHELTER">SHELTER</option>
                    <option value="OTHER">OTHER</option>
                  </select>
                </div>
                <div>
                  <label className="block text-gray-600 mb-1">Declared Severity</label>
                  <select
                    value={newSos.severity}
                    onChange={(e) => setNewSos({ ...newSos, severity: e.target.value })}
                    className="w-full p-2.5 rounded-lg border border-gray-300 font-medium"
                  >
                    <option value="CRITICAL">CRITICAL</option>
                    <option value="HIGH">HIGH</option>
                    <option value="MEDIUM">MEDIUM</option>
                    <option value="LOW">LOW</option>
                  </select>
                </div>
              </div>

              <div>
                <label className="block text-gray-600 mb-1">Persons Affected</label>
                <input
                  type="number"
                  min="1"
                  value={newSos.affectedCount}
                  onChange={(e) => setNewSos({ ...newSos, affectedCount: parseInt(e.target.value, 10) || 1 })}
                  className="w-full p-2.5 rounded-lg border border-gray-300 font-medium"
                  required
                />
              </div>

              <div>
                <label className="block text-gray-600 mb-1">Description</label>
                <textarea
                  rows={3}
                  value={newSos.description}
                  onChange={(e) => setNewSos({ ...newSos, description: e.target.value })}
                  placeholder="Details of emergency (injuries, traps, urgent needs)..."
                  className="w-full p-2.5 rounded-lg border border-gray-300 font-medium"
                  required
                />
              </div>

              <div>
                <label className="block text-gray-600 mb-1">Location Address / Landmark</label>
                <input
                  type="text"
                  value={newSos.locationAddress}
                  onChange={(e) => setNewSos({ ...newSos, locationAddress: e.target.value })}
                  placeholder="e.g. Connaught Place Inner Circle Block A"
                  className="w-full p-2.5 rounded-lg border border-gray-300 font-medium"
                />
              </div>

              <div className="flex justify-end gap-3 pt-3 border-t">
                <button
                  type="button"
                  onClick={() => setShowCreateModal(false)}
                  className="px-4 py-2 rounded-lg bg-gray-100 text-gray-700 hover:bg-gray-200"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={submitting}
                  className="px-5 py-2 rounded-lg bg-emergency hover:bg-emergency-dark text-white font-bold"
                >
                  {submitting ? 'Submitting...' : 'Dispatch SOS'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
