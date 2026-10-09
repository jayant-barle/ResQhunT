import React from 'react';
import { SeverityLevel } from '../../types';
import { AlertTriangle, AlertCircle, Info, ShieldAlert } from 'lucide-react';

interface Props {
  category: SeverityLevel;
  score?: number;
}

export const PriorityBadge: React.FC<Props> = ({ category, score }) => {
  switch (category) {
    case 'CRITICAL':
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-bold bg-red-100 text-emergency-dark border border-red-300 shadow-sm animate-pulse">
          <ShieldAlert className="w-3.5 h-3.5" />
          CRITICAL {score !== undefined && `(${score})`}
        </span>
      );
    case 'HIGH':
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-bold bg-orange-100 text-orange-800 border border-orange-300">
          <AlertTriangle className="w-3.5 h-3.5" />
          HIGH {score !== undefined && `(${score})`}
        </span>
      );
    case 'MEDIUM':
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-amber-100 text-amber-800 border border-amber-300">
          <AlertCircle className="w-3.5 h-3.5" />
          MEDIUM {score !== undefined && `(${score})`}
        </span>
      );
    case 'LOW':
    default:
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-emerald-100 text-emerald-800 border border-emerald-300">
          <Info className="w-3.5 h-3.5" />
          LOW {score !== undefined && `(${score})`}
        </span>
      );
  }
};
