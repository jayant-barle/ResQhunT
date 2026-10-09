import React, { useEffect, useState } from 'react';
import { api } from '../api/client';
import { Assignment } from '../types';
import { Link } from 'react-router-dom';
import { ClipboardList, Play, CheckCircle2, XCircle, ExternalLink } from 'lucide-react';

export const Assignments: React.FC = () => {
  const [assignments, setAssignments] = useState<Assignment[]>([]);
  const [loading, setLoading] = useState<boolean>(true);

  const fetchAssignments = async () => {
    try {
      setLoading(true);
      const res = await api.getAssignments();
      setAssignments(res || []);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAssignments();
  }, []);

  const handleUpdateStatus = async (id: string, status: string) => {
    try {
      await api.updateAssignmentStatus(id, status);
      fetchAssignments();
    } catch (err: any) {
      alert(`Failed to update status: ${err.message}`);
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-black text-navy tracking-tight">Mission Assignments</h1>
        <p className="text-sm text-gray-500 font-medium">
          Track field deployment operations, mission statuses, and completion reports.
        </p>
      </div>

      <div className="bg-white rounded-2xl border border-gray-200/80 shadow-sm overflow-hidden">
        {loading ? (
          <div className="py-16 text-center text-gray-400 text-sm">Loading assignments...</div>
        ) : assignments.length === 0 ? (
          <div className="py-16 text-center text-gray-400 text-sm">No assignments active.</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-canvas border-b border-gray-200 text-gray-500 font-bold uppercase tracking-wider">
                <tr>
                  <th className="py-3 px-4">Emergency Incident</th>
                  <th className="py-3 px-4">Assigned Operative</th>
                  <th className="py-3 px-4">Deployment Notes</th>
                  <th className="py-3 px-4">Status</th>
                  <th className="py-3 px-4 text-right">Workflow Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 font-medium">
                {assignments.map((asgn) => (
                  <tr key={asgn.id} className="hover:bg-gray-50/60 transition-colors">
                    <td className="py-3.5 px-4 whitespace-nowrap">
                      <Link
                        to={`/incidents/${asgn.incidentId}`}
                        className="font-bold text-navy hover:underline flex items-center gap-1"
                      >
                        Incident #{asgn.incidentId.slice(0, 8)}... <ExternalLink className="w-3 h-3" />
                      </Link>
                    </td>
                    <td className="py-3.5 px-4 whitespace-nowrap">
                      <p className="font-bold text-ink">{asgn.volunteer?.user.fullName}</p>
                      <p className="text-[11px] text-gray-400">{asgn.volunteer?.user.phone}</p>
                    </td>
                    <td className="py-3.5 px-4 max-w-xs text-gray-600">
                      {asgn.notes || <span className="text-gray-400 italic">None</span>}
                    </td>
                    <td className="py-3.5 px-4 whitespace-nowrap">
                      <span
                        className={`px-2.5 py-1 rounded-full text-xs font-bold ${
                          asgn.status === 'COMPLETED'
                            ? 'bg-emerald-100 text-emerald-800'
                            : asgn.status === 'IN_PROGRESS'
                            ? 'bg-amber-100 text-amber-800'
                            : asgn.status === 'ACCEPTED'
                            ? 'bg-cyan-100 text-cyan-800'
                            : 'bg-gray-100 text-gray-700'
                        }`}
                      >
                        {asgn.status}
                      </span>
                    </td>
                    <td className="py-3.5 px-4 text-right whitespace-nowrap">
                      <div className="flex items-center justify-end gap-1.5">
                        {asgn.status === 'PENDING' && (
                          <button
                            onClick={() => handleUpdateStatus(asgn.id, 'ACCEPTED')}
                            className="px-2.5 py-1 rounded-lg bg-cyan-600 hover:bg-cyan-700 text-white font-bold"
                          >
                            Accept
                          </button>
                        )}
                        {(asgn.status === 'PENDING' || asgn.status === 'ACCEPTED') && (
                          <button
                            onClick={() => handleUpdateStatus(asgn.id, 'IN_PROGRESS')}
                            className="px-2.5 py-1 rounded-lg bg-amber-600 hover:bg-amber-700 text-white font-bold flex items-center gap-1"
                          >
                            <Play className="w-3 h-3" /> Start
                          </button>
                        )}
                        {asgn.status === 'IN_PROGRESS' && (
                          <button
                            onClick={() => handleUpdateStatus(asgn.id, 'COMPLETED')}
                            className="px-2.5 py-1 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white font-bold flex items-center gap-1"
                          >
                            <CheckCircle2 className="w-3 h-3" /> Complete
                          </button>
                        )}
                        {asgn.status === 'PENDING' && (
                          <button
                            onClick={() => handleUpdateStatus(asgn.id, 'DECLINED')}
                            className="px-2.5 py-1 rounded-lg bg-gray-200 hover:bg-gray-300 text-gray-700 font-bold"
                          >
                            Decline
                          </button>
                        )}
                      </div>
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
