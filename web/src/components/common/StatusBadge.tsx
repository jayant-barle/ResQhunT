import React from 'react';
import { DeliveryState } from '../../types';
import { CheckCircle2, Clock, Radio, Server, ShieldCheck, UserCheck, Play, Check } from 'lucide-react';

interface Props {
  status: DeliveryState;
}

export const StatusBadge: React.FC<Props> = ({ status }) => {
  switch (status) {
    case 'CREATED':
    case 'STORED_LOCALLY':
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-gray-100 text-gray-700 border border-gray-300">
          <Clock className="w-3.5 h-3.5" />
          Offline Stored
        </span>
      );
    case 'RELAY_PENDING':
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-amber-50 text-amber-700 border border-amber-300 animate-pulse">
          <Radio className="w-3.5 h-3.5" />
          Relay Pending
        </span>
      );
    case 'RELAYED_TO_PEER':
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-blue-50 text-blue-700 border border-blue-300">
          <Radio className="w-3.5 h-3.5" />
          Relayed to Peer
        </span>
      );
    case 'SERVER_RECEIVED':
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-indigo-50 text-indigo-700 border border-indigo-300">
          <Server className="w-3.5 h-3.5" />
          Server Received
        </span>
      );
    case 'COORDINATOR_ACKNOWLEDGED':
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-purple-50 text-purple-700 border border-purple-300">
          <ShieldCheck className="w-3.5 h-3.5" />
          Acknowledged
        </span>
      );
    case 'ASSIGNED':
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-cyan-50 text-cyan-800 border border-cyan-300">
          <UserCheck className="w-3.5 h-3.5" />
          Volunteer Assigned
        </span>
      );
    case 'IN_PROGRESS':
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-amber-50 text-amber-800 border border-amber-300">
          <Play className="w-3.5 h-3.5" />
          In Progress
        </span>
      );
    case 'RESOLVED':
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-emerald-50 text-emerald-800 border border-emerald-300">
          <Check className="w-3.5 h-3.5" />
          Resolved
        </span>
      );
    default:
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-gray-100 text-gray-700">
          <CheckCircle2 className="w-3.5 h-3.5" />
          {status}
        </span>
      );
  }
};
