import React from 'react';
import { NavLink } from 'react-router-dom';
import {
  LayoutDashboard,
  AlertOctagon,
  Map,
  Users,
  ClipboardList,
  Boxes,
  ScrollText,
  Settings
} from 'lucide-react';

const navItems = [
  { name: 'Overview', to: '/', icon: LayoutDashboard },
  { name: 'Incidents', to: '/incidents', icon: AlertOctagon },
  { name: 'Rescue Map', to: '/map', icon: Map },
  { name: 'Volunteers', to: '/volunteers', icon: Users },
  { name: 'Assignments', to: '/assignments', icon: ClipboardList },
  { name: 'Relief Resources', to: '/resources', icon: Boxes },
  { name: 'Activity Log', to: '/activity', icon: ScrollText },
  { name: 'Settings', to: '/settings', icon: Settings },
];

export const Sidebar: React.FC = () => {
  return (
    <aside className="w-64 bg-white border-r border-gray-200 flex flex-col shrink-0 min-h-[calc(100vh-4rem)]">
      <div className="p-4 border-b border-gray-100">
        <div className="flex items-center gap-2 px-3 py-2 bg-canvas rounded-lg border border-gray-200/60">
          <div className="w-2.5 h-2.5 rounded-full bg-teal animate-ping" />
          <span className="text-xs font-semibold text-gray-700">Mesh Gateway Listening</span>
        </div>
      </div>

      <nav className="p-3 space-y-1 flex-1">
        {navItems.map((item) => {
          const Icon = item.icon;
          return (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                `flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-sm font-semibold transition-all ${
                  isActive
                    ? 'bg-navy text-white shadow-sm'
                    : 'text-gray-600 hover:text-ink hover:bg-canvas'
                }`
              }
            >
              <Icon className="w-4 h-4" />
              <span>{item.name}</span>
            </NavLink>
          );
        })}
      </nav>

      {/* Protocol Version Footer */}
      <div className="p-4 border-t border-gray-100 text-xs text-gray-400">
        <p className="font-semibold text-gray-600">Mesh Protocol: v1.0</p>
        <p className="text-[11px]">SHA-256 Envelope Guard</p>
      </div>
    </aside>
  );
};
