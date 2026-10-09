import React, { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { api } from '../api/client';
import { EmergencyRequest, Volunteer, ReliefResource } from '../types';
import { StatusBadge } from '../components/common/StatusBadge';
import { PriorityBadge } from '../components/common/PriorityBadge';
import { CategoryBadge } from '../components/common/CategoryBadge';
import {
  ArrowLeft,
  MapPin,
  Clock,
  Radio,
  UserCheck,
  Boxes,
  ShieldAlert,
  CheckCircle2,
  AlertTriangle,
  Send
} from 'lucide-react';

export const IncidentDetail: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const [incident, setIncident] = useState<EmergencyRequest | null>(null);
  const [volunteers, setVolunteers] = useState<Volunteer[]>([]);
  const [resources, setResources] = useState<ReliefResource[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Modals
  const [showAssignModal, setShowAssignModal] = useState<boolean>(false);
  const [selectedVolunteer, setSelectedVolunteer] = useState<string>('');
  const [assignNotes, setAssignNotes] = useState<string>('');

  const [showAllocateModal, setShowAllocateModal] = useState<boolean>(false);
  const [selectedResource, setSelectedResource] = useState<string>('');
  const [allocateQty, setAllocateQty] = useState<number>(1);
  const [allocateNotes, setAllocateNotes] = useState<string>('');

  const [showOverrideModal, setShowOverrideModal] = useState<boolean>(false);
  const [overrideScore, setOverrideScore] = useState<number>(95);
  const [overrideCategory, setOverrideCategory] = useState<string>('CRITICAL');
  const [overrideReason, setOverrideReason] = useState<string>('');

  const [actionLoading, setActionLoading] = useState<boolean>(false);

  const fetchIncident = async () => {
    if (!id) return;
    try {
      setLoading(true);
      setError(null);
      const [inc, vols, resList] = await Promise.all([
        api.getIncidentById(id),
        api.getVolunteers({ available: true }),
        api.getResources()
      ]);
      setIncident(inc);
      setVolunteers(vols);
      setResources(resList);
      if (inc) {
        setOverrideScore(inc.priorityScore);
        setOverrideCategory(inc.priorityCategory);
      }
    } catch (err: any) {
      setError(err.message || 'Failed to fetch incident');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchIncident();
  }, [id]);

  const handleAcknowledge = async () => {
    if (!incident) return;
    setActionLoading(true);
    try {
      await api.updateIncidentStatus(incident.id, 'COORDINATOR_ACKNOWLEDGED', 'Incident acknowledged by coordinator');
      await fetchIncident();
    } catch (err: any) {
      alert(`Error acknowledging: ${err.message}`);
    } finally {
      setActionLoading(false);
    }
  };

  const handleAssignVolunteer = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!incident || !selectedVolunteer) return;
    setActionLoading(true);
    try {
      await api.createAssignment(incident.id, selectedVolunteer, assignNotes);
      setShowAssignModal(false);
      setAssignNotes('');
      await fetchIncident();
    } catch (err: any) {
      alert(`Error assigning: ${err.message}`);
    } finally {
      setActionLoading(false);
    }
  };

  const handleAllocateResource = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!incident || !selectedResource) return;
    setActionLoading(true);
    try {
      await api.allocateResource(selectedResource, incident.id, allocateQty, allocateNotes);
      setShowAllocateModal(false);
      setAllocateNotes('');
      await fetchIncident();
    } catch (err: any) {
      alert(`Error allocating: ${err.message}`);
    } finally {
      setActionLoading(false);
    }
  };

  const handleOverridePriority = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!incident || !overrideReason) return;
    setActionLoading(true);
    try {
      await api.overridePriority(incident.id, overrideScore, overrideCategory, overrideReason);
      setShowOverrideModal(false);
      setOverrideReason('');
      await fetchIncident();
    } catch (err: any) {
      alert(`Error overriding priority: ${err.message}`);
    } finally {
      setActionLoading(false);
    }
  };

  const handleResolve = async () => {
    if (!incident) return;
    if (!window.confirm('Are you sure you want to mark this emergency as RESOLVED?')) return;
    setActionLoading(true);
    try {
      await api.updateIncidentStatus(incident.id, 'RESOLVED', 'All rescue operations completed successfully');
      await fetchIncident();
    } catch (err: any) {
      alert(`Error resolving: ${err.message}`);
    } finally {
      setActionLoading(false);
    }
  };

  if (loading) {
    return <div className="py-20 text-center text-gray-400">Loading incident records...</div>;
  }

  if (!incident) {
    return (
      <div className="py-20 text-center space-y-4">
        <p className="text-gray-500 font-bold">Incident not found.</p>
        <Link to="/incidents" className="text-sm text-navy font-bold hover:underline">
          Return to directory
        </Link>
      </div>
    );
  }

  return (
    <div className="space-y-8">
      {/* Back button & Title */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div className="space-y-1">
          <Link
            to="/incidents"
            className="inline-flex items-center gap-1.5 text-xs font-bold text-gray-500 hover:text-navy transition-colors"
          >
            <ArrowLeft className="w-3.5 h-3.5" /> Back to Incidents
          </Link>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-black text-navy tracking-tight">
              Emergency {incident.requestId}
            </h1>
            <StatusBadge status={incident.deliveryState} />
          </div>
        </div>

        {/* Action Buttons */}
        <div className="flex items-center gap-2 flex-wrap">
          {incident.deliveryState === 'SERVER_RECEIVED' && (
            <button
              onClick={handleAcknowledge}
              disabled={actionLoading}
              className="px-4 py-2 rounded-xl bg-purple-700 hover:bg-purple-800 text-white text-xs font-bold shadow-sm transition-colors flex items-center gap-1.5"
            >
              <CheckCircle2 className="w-4 h-4" /> Acknowledge Incident
            </button>
          )}

          <button
            onClick={() => setShowAssignModal(true)}
            disabled={actionLoading || incident.deliveryState === 'RESOLVED'}
            className="px-4 py-2 rounded-xl bg-navy hover:bg-navy-light text-white text-xs font-bold shadow-sm transition-colors flex items-center gap-1.5"
          >
            <UserCheck className="w-4 h-4" /> Dispatch Volunteer
          </button>

          <button
            onClick={() => setShowAllocateModal(true)}
            disabled={actionLoading || incident.deliveryState === 'RESOLVED'}
            className="px-4 py-2 rounded-xl bg-teal-dark hover:bg-teal text-white text-xs font-bold shadow-sm transition-colors flex items-center gap-1.5"
          >
            <Boxes className="w-4 h-4" /> Allocate Relief Stock
          </button>

          <button
            onClick={() => setShowOverrideModal(true)}
            disabled={actionLoading}
            className="px-4 py-2 rounded-xl bg-gray-100 hover:bg-gray-200 text-gray-700 text-xs font-bold transition-colors flex items-center gap-1.5"
          >
            <ShieldAlert className="w-4 h-4" /> Override Priority
          </button>

          {incident.deliveryState !== 'RESOLVED' && (
            <button
              onClick={handleResolve}
              disabled={actionLoading}
              className="px-4 py-2 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-bold shadow-sm transition-colors flex items-center gap-1.5"
            >
              <CheckCircle2 className="w-4 h-4" /> Mark Resolved
            </button>
          )}
        </div>
      </div>

      {error && (
        <div className="p-4 rounded-xl bg-red-50 border border-red-200 text-red-700 text-sm flex items-center gap-2">
          <AlertTriangle className="w-4 h-4" />
          <span>{error}</span>
        </div>
      )}

      {/* Overview Cards */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left Column: Core Emergency Details */}
        <div className="lg:col-span-2 space-y-6">
          <div className="bg-white rounded-2xl border border-gray-200/80 shadow-sm p-6 space-y-5">
            <h2 className="text-base font-bold text-navy border-b pb-3">Incident Overview</h2>

            <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 text-xs">
              <div className="p-3 rounded-xl bg-canvas">
                <span className="text-gray-400 font-semibold block mb-1">Category</span>
                <CategoryBadge category={incident.category} />
              </div>
              <div className="p-3 rounded-xl bg-canvas">
                <span className="text-gray-400 font-semibold block mb-1">Severity</span>
                <PriorityBadge category={incident.severity} />
              </div>
              <div className="p-3 rounded-xl bg-canvas">
                <span className="text-gray-400 font-semibold block mb-1">Affected Persons</span>
                <span className="text-base font-black text-navy">{incident.affectedCount}</span>
              </div>
              <div className="p-3 rounded-xl bg-canvas">
                <span className="text-gray-400 font-semibold block mb-1">Mesh Hop Count</span>
                <span className="text-base font-black text-navy">{incident.syncHopCount}</span>
              </div>
            </div>

            <div>
              <h3 className="text-xs font-bold text-gray-500 uppercase tracking-wider mb-2">Description</h3>
              <p className="p-4 rounded-xl bg-canvas border border-gray-200/60 text-sm text-ink font-medium leading-relaxed">
                {incident.description}
              </p>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-xs font-medium">
              <div className="flex items-start gap-2.5">
                <MapPin className="w-4 h-4 text-emergency shrink-0 mt-0.5" />
                <div>
                  <span className="font-bold text-ink block">Coordinates & Landmark</span>
                  <span className="text-gray-500">
                    {incident.latitude ? `${incident.latitude.toFixed(6)}, ${incident.longitude?.toFixed(6)}` : 'GPS Unset'}
                  </span>
                  <p className="text-gray-600 mt-0.5">{incident.locationAddress || 'No landmark specified'}</p>
                </div>
              </div>

              <div className="flex items-start gap-2.5">
                <Clock className="w-4 h-4 text-gray-400 shrink-0 mt-0.5" />
                <div>
                  <span className="font-bold text-ink block">Logged Timestamp</span>
                  <span className="text-gray-500">{new Date(incident.createdAt).toLocaleString()}</span>
                  <span className="block text-[11px] text-gray-400 font-mono mt-0.5">Device: {incident.originDeviceId}</span>
                </div>
              </div>
            </div>

            {incident.overrideScore && (
              <div className="p-3.5 rounded-xl bg-amber-50 border border-amber-200 text-xs space-y-1">
                <div className="flex items-center gap-1.5 font-bold text-amber-800">
                  <ShieldAlert className="w-4 h-4" />
                  <span>Coordinator Priority Override Active</span>
                </div>
                <p className="text-amber-900 font-medium">
                  Score set to {incident.overrideScore} ({incident.priorityCategory}). Justification: {incident.overrideReason}
                </p>
              </div>
            )}
          </div>

          {/* Active Assignments List */}
          <div className="bg-white rounded-2xl border border-gray-200/80 shadow-sm p-6 space-y-4">
            <h2 className="text-base font-bold text-navy flex items-center gap-2">
              <UserCheck className="w-5 h-5 text-navy" /> Assigned Rescue Personnel
            </h2>
            {incident.assignments && incident.assignments.length > 0 ? (
              <div className="divide-y divide-gray-100">
                {incident.assignments.map((asgn) => (
                  <div key={asgn.id} className="py-3 flex items-center justify-between gap-4 text-xs font-medium">
                    <div>
                      <p className="font-bold text-ink">{asgn.volunteer?.user.fullName}</p>
                      <p className="text-gray-400">{asgn.volunteer?.user.phone || asgn.volunteer?.user.email}</p>
                      {asgn.notes && <p className="text-gray-500 italic mt-0.5">"{asgn.notes}"</p>}
                    </div>
                    <span className="px-2.5 py-1 rounded-full text-xs font-semibold bg-cyan-100 text-cyan-800">
                      {asgn.status}
                    </span>
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-xs text-gray-400 italic">No volunteers assigned to this emergency yet.</p>
            )}
          </div>
        </div>

        {/* Right Column: Priority Engine Evaluation & Supply Allocations */}
        <div className="space-y-6">
          {/* Priority Score Breakdown */}
          <div className="bg-white rounded-2xl border border-gray-200/80 shadow-sm p-6 space-y-4">
            <h2 className="text-base font-bold text-navy flex items-center gap-2">
              <Radio className="w-5 h-5 text-emergency" /> Priority Engine
            </h2>
            <div className="text-center p-4 rounded-xl bg-canvas border border-gray-200/60">
              <span className="text-xs font-bold text-gray-400 block uppercase">Composite Score</span>
              <span className="text-4xl font-black text-navy">{incident.priorityScore}</span>
              <span className="text-xs text-gray-400 block">/ 100</span>
              <div className="mt-2">
                <PriorityBadge category={incident.priorityCategory} />
              </div>
            </div>
            <p className="text-[11px] text-gray-500 leading-relaxed font-medium">
              Deterministic rule-based engine: evaluated based on declared severity, medical triage status, number of victims, and waiting time.
            </p>
          </div>

          {/* Allocated Resources */}
          <div className="bg-white rounded-2xl border border-gray-200/80 shadow-sm p-6 space-y-4">
            <h2 className="text-base font-bold text-navy flex items-center gap-2">
              <Boxes className="w-5 h-5 text-teal" /> Allocated Relief Supplies
            </h2>
            {incident.allocations && incident.allocations.length > 0 ? (
              <div className="divide-y divide-gray-100 text-xs font-medium">
                {incident.allocations.map((alloc) => (
                  <div key={alloc.id} className="py-2.5 flex items-center justify-between">
                    <div>
                      <p className="font-bold text-ink">{alloc.resource?.name}</p>
                      <p className="text-gray-400">{new Date(alloc.timestamp).toLocaleTimeString()}</p>
                    </div>
                    <span className="font-bold text-teal-dark bg-teal/10 px-2.5 py-1 rounded-lg">
                      {alloc.quantity} {alloc.resource?.unit}
                    </span>
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-xs text-gray-400 italic">No relief supplies allocated to this incident yet.</p>
            )}
          </div>
        </div>
      </div>

      {/* Modal: Dispatch Volunteer */}
      {showAssignModal && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-2xl border border-gray-200 space-y-4">
            <div className="flex items-center justify-between border-b pb-3">
              <h3 className="font-bold text-base text-navy">Dispatch Volunteer</h3>
              <button onClick={() => setShowAssignModal(false)} className="text-gray-400 hover:text-gray-600 font-bold">
                ✕
              </button>
            </div>
            <form onSubmit={handleAssignVolunteer} className="space-y-4 text-xs font-semibold">
              <div>
                <label className="block text-gray-600 mb-1">Available Volunteers</label>
                <select
                  value={selectedVolunteer}
                  onChange={(e) => setSelectedVolunteer(e.target.value)}
                  className="w-full p-2.5 rounded-lg border border-gray-300 font-medium"
                  required
                >
                  <option value="">Select a volunteer...</option>
                  {volunteers.map((v) => (
                    <option key={v.id} value={v.id}>
                      {v.user.fullName} ({v.skills})
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-gray-600 mb-1">Mission Instructions / Equipment Notes</label>
                <textarea
                  rows={2}
                  value={assignNotes}
                  onChange={(e) => setAssignNotes(e.target.value)}
                  placeholder="e.g. Equip medical kit #3, rendezvous at North checkpoint"
                  className="w-full p-2.5 rounded-lg border border-gray-300 font-medium"
                />
              </div>

              <div className="flex justify-end gap-2 pt-3 border-t">
                <button
                  type="button"
                  onClick={() => setShowAssignModal(false)}
                  className="px-3 py-1.5 rounded-lg bg-gray-100 text-gray-700"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={actionLoading}
                  className="px-4 py-1.5 rounded-lg bg-navy text-white font-bold"
                >
                  Confirm Dispatch
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal: Allocate Relief Supplies */}
      {showAllocateModal && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-2xl border border-gray-200 space-y-4">
            <div className="flex items-center justify-between border-b pb-3">
              <h3 className="font-bold text-base text-navy">Allocate Relief Stock</h3>
              <button onClick={() => setShowAllocateModal(false)} className="text-gray-400 hover:text-gray-600 font-bold">
                ✕
              </button>
            </div>
            <form onSubmit={handleAllocateResource} className="space-y-4 text-xs font-semibold">
              <div>
                <label className="block text-gray-600 mb-1">Relief Item</label>
                <select
                  value={selectedResource}
                  onChange={(e) => setSelectedResource(e.target.value)}
                  className="w-full p-2.5 rounded-lg border border-gray-300 font-medium"
                  required
                >
                  <option value="">Select a resource...</option>
                  {resources.map((r) => (
                    <option key={r.id} value={r.id}>
                      {r.name} ({r.availableQuantity} {r.unit} available)
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-gray-600 mb-1">Quantity to Allocate</label>
                <input
                  type="number"
                  min="1"
                  value={allocateQty}
                  onChange={(e) => setAllocateQty(parseInt(e.target.value, 10) || 1)}
                  className="w-full p-2.5 rounded-lg border border-gray-300 font-medium"
                  required
                />
              </div>

              <div>
                <label className="block text-gray-600 mb-1">Allocation Notes</label>
                <input
                  type="text"
                  value={allocateNotes}
                  onChange={(e) => setAllocateNotes(e.target.value)}
                  placeholder="e.g. Distributed by relief truck A"
                  className="w-full p-2.5 rounded-lg border border-gray-300 font-medium"
                />
              </div>

              <div className="flex justify-end gap-2 pt-3 border-t">
                <button
                  type="button"
                  onClick={() => setShowAllocateModal(false)}
                  className="px-3 py-1.5 rounded-lg bg-gray-100 text-gray-700"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={actionLoading}
                  className="px-4 py-1.5 rounded-lg bg-teal-dark text-white font-bold"
                >
                  Allocate Stock
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal: Override Priority Score */}
      {showOverrideModal && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-2xl border border-gray-200 space-y-4">
            <div className="flex items-center justify-between border-b pb-3">
              <h3 className="font-bold text-base text-navy">Coordinator Priority Override</h3>
              <button onClick={() => setShowOverrideModal(false)} className="text-gray-400 hover:text-gray-600 font-bold">
                ✕
              </button>
            </div>
            <form onSubmit={handleOverridePriority} className="space-y-4 text-xs font-semibold">
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-gray-600 mb-1">Priority Category</label>
                  <select
                    value={overrideCategory}
                    onChange={(e) => setOverrideCategory(e.target.value)}
                    className="w-full p-2.5 rounded-lg border border-gray-300 font-medium"
                  >
                    <option value="CRITICAL">CRITICAL</option>
                    <option value="HIGH">HIGH</option>
                    <option value="MEDIUM">MEDIUM</option>
                    <option value="LOW">LOW</option>
                  </select>
                </div>
                <div>
                  <label className="block text-gray-600 mb-1">Score (0 - 100)</label>
                  <input
                    type="number"
                    min="0"
                    max="100"
                    value={overrideScore}
                    onChange={(e) => setOverrideScore(parseFloat(e.target.value) || 0)}
                    className="w-full p-2.5 rounded-lg border border-gray-300 font-medium"
                    required
                  />
                </div>
              </div>

              <div>
                <label className="block text-gray-600 mb-1">Justification Reason (Mandatory Audit Log)</label>
                <textarea
                  rows={3}
                  value={overrideReason}
                  onChange={(e) => setOverrideReason(e.target.value)}
                  placeholder="Explain why the automated priority score is being overridden..."
                  className="w-full p-2.5 rounded-lg border border-gray-300 font-medium"
                  required
                />
              </div>

              <div className="flex justify-end gap-2 pt-3 border-t">
                <button
                  type="button"
                  onClick={() => setShowOverrideModal(false)}
                  className="px-3 py-1.5 rounded-lg bg-gray-100 text-gray-700"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={actionLoading}
                  className="px-4 py-1.5 rounded-lg bg-emergency text-white font-bold"
                >
                  Save Override
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
