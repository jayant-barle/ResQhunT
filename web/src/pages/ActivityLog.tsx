import React, { useEffect, useState } from 'react';
import { api } from '../api/client';
import { ActivityLog as ActivityLogType } from '../types';
import { ScrollText, Clock, User, ShieldAlert, CheckCircle2, Boxes } from 'lucide-react';

export const ActivityLog: React.FC = () => {
  const [logs, setLogs] = useState<ActivityLogType[]>([]);
  const [loading, setLoading] = useState<boolean>(true);

  const fetchLogs = async () => {
    try {
      setLoading(true);
      const res = await api.getActivityLogs();
      setLogs(res || []);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchLogs();
  }, []);

  const getActionIcon = (actionType: string) => {
    if (actionType.includes('OVERRIDE')) {
      return <ShieldAlert className="w-4 h-4 text-emergency" />;
    }
    if (actionType.includes('ALLOCATED')) {
      return <Boxes className="w-4 h-4 text-teal" />;
    }
    if (actionType.includes('ASSIGNED')) {
      return <User className="w-4 h-4 text-navy" />;
    }
    return <CheckCircle2 className="w-4 h-4 text-indigo-600" />;
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-black text-navy tracking-tight">System Activity Log</h1>
        <p className="text-sm text-gray-500 font-medium">
          Immutable chronological audit stream of coordinator overrides, supply allocations, and mesh sync events.
        </p>
      </div>

      <div className="bg-white rounded-2xl border border-gray-200/80 shadow-sm p-6 space-y-4">
        {loading ? (
          <div className="py-16 text-center text-gray-400 text-sm">Loading activity records...</div>
        ) : logs.length === 0 ? (
          <div className="py-16 text-center text-gray-400 text-sm">No activity recorded yet.</div>
        ) : (
          <div className="divide-y divide-gray-100">
            {logs.map((log) => (
              <div key={log.id} className="py-3.5 flex items-start gap-3.5 text-xs">
                <div className="p-2 rounded-xl bg-canvas border border-gray-200/60 mt-0.5">
                  {getActionIcon(log.actionType)}
                </div>
                <div className="flex-1 space-y-1">
                  <div className="flex items-center justify-between">
                    <span className="font-bold text-navy text-xs uppercase tracking-wider">
                      {log.actionType.replace(/_/g, ' ')}
                    </span>
                    <span className="text-[11px] text-gray-400 flex items-center gap-1">
                      <Clock className="w-3 h-3" />
                      {new Date(log.timestamp).toLocaleString()}
                    </span>
                  </div>
                  <p className="text-ink font-medium leading-relaxed">{log.details}</p>
                  <p className="text-[11px] text-gray-400">
                    Operative: {log.performedByUser?.fullName || 'Mesh System Gateway'} ({log.performedByUser?.role || 'AUTO'})
                  </p>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};
