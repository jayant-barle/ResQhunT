import React from 'react';
import { EmergencyCategory } from '../../types';
import { HeartPulse, LifeBuoy, Droplets, Utensils, Tent, HelpCircle } from 'lucide-react';

interface Props {
  category: EmergencyCategory;
}

export const CategoryBadge: React.FC<Props> = ({ category }) => {
  switch (category) {
    case 'MEDICAL':
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-semibold bg-red-50 text-red-700 border border-red-200">
          <HeartPulse className="w-3.5 h-3.5 text-emergency" />
          Medical
        </span>
      );
    case 'RESCUE':
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-semibold bg-orange-50 text-orange-700 border border-orange-200">
          <LifeBuoy className="w-3.5 h-3.5 text-orange-600" />
          Rescue
        </span>
      );
    case 'WATER':
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-semibold bg-sky-50 text-sky-700 border border-sky-200">
          <Droplets className="w-3.5 h-3.5 text-sky-600" />
          Water
        </span>
      );
    case 'FOOD':
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
          <Utensils className="w-3.5 h-3.5 text-emerald-600" />
          Food
        </span>
      );
    case 'SHELTER':
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-semibold bg-purple-50 text-purple-700 border border-purple-200">
          <Tent className="w-3.5 h-3.5 text-purple-600" />
          Shelter
        </span>
      );
    case 'OTHER':
    default:
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-semibold bg-gray-50 text-gray-700 border border-gray-200">
          <HelpCircle className="w-3.5 h-3.5 text-gray-500" />
          Other
        </span>
      );
  }
};
