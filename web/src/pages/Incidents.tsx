import React, { useEffect, useState } from 'react';
import { api } from '../api/client';
import { EmergencyRequest } from '../types';
import { StatusBadge } from '../components/common/StatusBadge';
import { PriorityBadge } from '../components/common/PriorityBadge';
import { CategoryBadge } from '../components/common/CategoryBadge';
import { Link } from 'react-router-dom';
import { Search, Filter, AlertTriangle } from 'lucide-react';

export const Incidents: React.FC = () => {
  const [incidents, setIncidents] = useState<EmergencyRequest[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const [category, setCategory] = useState<string>('');
  const [severity, setSeverity] = useState<string>('');
  const [status, setStatus] = useState<string>('');
  const [search, setSearch] = useState<string>('');

  const fetchIncidents = async () => {
    try {
      setLoading(true);
      setError(null);
      const res = await api.getIncidents({ category, severity, status, search });
      setIncidents(res.incidents || []);
    } catch (err: any) {
      setError(err.message || 'Failed to fetch incidents');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchIncidents();
  }, [category, severity, status]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    fetchIncidents();
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-black text-navy tracking-tight">Incident Directory</h1>
          <p className="text-sm text-gray-500 font-medium">
            Monitor, prioritize, and manage all incoming disaster emergency requests.
          </p>
        </div>
      </div>

      {error && (
        <div className="p-4 rounded-xl bg-red-50 border border-red-200 text-red-700 text-sm flex items-center gap-2">
          <AlertTriangle className="w-4 h-4 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* Filter and Search Bar */}
      <div className="bg-white p-4 rounded-2xl border border-gray-200/80 shadow-sm flex flex-col md:flex-row gap-3">
        <form onSubmit={handleSearchSubmit} className="flex-1 relative">
          <Search className="w-4 h-4 text-gray-400 absolute left-3.5 top-3.5" />
          <input
            type="text"
            placeholder="Search description, address, or Request ID..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-gray-200 text-sm focus:outline-none focus:ring-2 focus:ring-navy font-medium"
          />
        </form>

        <div className="flex items-center gap-2 flex-wrap">
          <div className="flex items-center gap-1 bg-canvas px-3 py-1.5 rounded-xl border border-gray-200 text-xs">
            <Filter className="w-3.5 h-3.5 text-gray-400" />
            <select
              value={category}
              onChange={(e) => setCategory(e.target.value)}
              className="bg-transparent font-semibold text-gray-700 focus:outline-none"
            >
              <option value="">All Categories</option>
              <option value="MEDICAL">Medical</option>
              <option value="RESCUE">Rescue</option>
              <option value="FOOD">Food</option>
              <option value="WATER">Water</option>
              <option value="SHELTER">Shelter</option>
              <option value="OTHER">Other</option>
            </select>
          </div>

          <div className="flex items-center gap-1 bg-canvas px-3 py-1.5 rounded-xl border border-gray-200 text-xs">
            <select
              value={severity}
              onChange={(e) => setSeverity(e.target.value)}
              className="bg-transparent font-semibold text-gray-700 focus:outline-none"
            >
              <option value="">All Severities</option>
              <option value="CRITICAL">Critical</option>
              <option value="HIGH">High</option>
              <option value="MEDIUM">Medium</option>
              <option value="LOW">Low</option>
            </select>
          </div>

          <div className="flex items-center gap-1 bg-canvas px-3 py-1.5 rounded-xl border border-gray-200 text-xs">
            <select
              value={status}
              onChange={(e) => setStatus(e.target.value)}
              className="bg-transparent font-semibold text-gray-700 focus:outline-none"
            >
              <option value="">All Statuses</option>
              <option value="SERVER_RECEIVED">Server Received</option>
              <option value="COORDINATOR_ACKNOWLEDGED">Acknowledged</option>
              <option value="ASSIGNED">Assigned</option>
              <option value="IN_PROGRESS">In Progress</option>
              <option value="RESOLVED">Resolved</option>
            </select>
          </div>
        </div>
      </div>

      {/* Incidents Table */}
      <div className="bg-white rounded-2xl border border-gray-200/80 shadow-sm overflow-hidden">
        {loading ? (
          <div className="py-16 text-center text-gray-400 text-sm">Loading incident catalog...</div>
        ) : incidents.length === 0 ? (
          <div className="py-16 text-center text-gray-400 text-sm">No incidents matched your query.</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-canvas border-b border-gray-200 text-gray-500 font-bold uppercase tracking-wider">
                <tr>
                  <th className="py-3 px-4">Priority & Score</th>
                  <th className="py-3 px-4">Category</th>
                  <th className="py-3 px-4">Description & Location</th>
                  <th className="py-3 px-4">Victims</th>
                  <th className="py-3 px-4">Mesh Hops</th>
                  <th className="py-3 px-4">Delivery Status</th>
                  <th className="py-3 px-4 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 font-medium">
                {incidents.map((inc) => (
                  <tr key={inc.id} className="hover:bg-gray-50/60 transition-colors">
                    <td className="py-3.5 px-4 whitespace-nowrap">
                      <PriorityBadge category={inc.priorityCategory} score={inc.priorityScore} />
                    </td>
                    <td className="py-3.5 px-4 whitespace-nowrap">
                      <CategoryBadge category={inc.category} />
                    </td>
                    <td className="py-3.5 px-4 max-w-xs">
                      <p className="font-bold text-ink line-clamp-1">{inc.description}</p>
                      <p className="text-[11px] text-gray-400 line-clamp-1">
                        {inc.locationAddress || `${inc.latitude?.toFixed(4)}, ${inc.longitude?.toFixed(4)}`}
                      </p>
                    </td>
                    <td className="py-3.5 px-4 whitespace-nowrap font-bold text-navy">
                      {inc.affectedCount}
                    </td>
                    <td className="py-3.5 px-4 whitespace-nowrap font-mono text-gray-500">
                      {inc.syncHopCount > 0 ? `${inc.syncHopCount} hop(s)` : 'Direct'}
                    </td>
                    <td className="py-3.5 px-4 whitespace-nowrap">
                      <StatusBadge status={inc.deliveryState} />
                    </td>
                    <td className="py-3.5 px-4 text-right whitespace-nowrap">
                      <Link
                        to={`/incidents/${inc.id}`}
                        className="px-3 py-1.5 rounded-lg bg-navy hover:bg-navy-light text-white font-bold transition-colors"
                      >
                        Inspect
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
};
