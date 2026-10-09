import React, { useState } from 'react';
import { useDemo } from '../context/DemoContext';
import { useAuth } from '../context/AuthContext';
import { Settings as SettingsIcon, RefreshCw, Radio, Database, Shield, LogOut } from 'lucide-react';

export const Settings: React.FC = () => {
  const { isDemoMode, setDemoMode, resetDemoData, isResetting } = useDemo();
  const { user, logout } = useAuth();
  const [offlineSimulated, setOfflineSimulated] = useState<boolean>(false);

  return (
    <div className="space-y-6 max-w-4xl">
      <div>
        <h1 className="text-2xl font-black text-navy tracking-tight">System Configuration</h1>
        <p className="text-sm text-gray-500 font-medium">
          Manage gateway parameters, demo dataset toggles, and protocol settings.
        </p>
      </div>

      <div className="space-y-5">
        {/* Environment & Node Info */}
        <div className="bg-white rounded-2xl border border-gray-200/80 shadow-sm p-6 space-y-4">
          <h2 className="text-base font-bold text-navy flex items-center gap-2 border-b pb-3">
            <Radio className="w-5 h-5 text-teal" /> Mesh Gateway & Network Parameters
          </h2>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-xs font-medium">
            <div className="p-3.5 rounded-xl bg-canvas border border-gray-200/60">
              <span className="text-gray-400 block mb-1">Store-and-Forward Envelope Version</span>
              <span className="font-bold text-navy text-sm">v1.0 (SHA-256 Guard)</span>
            </div>
            <div className="p-3.5 rounded-xl bg-canvas border border-gray-200/60">
              <span className="text-gray-400 block mb-1">Max Relay Hop Threshold</span>
              <span className="font-bold text-navy text-sm">5 Hops</span>
            </div>
            <div className="p-3.5 rounded-xl bg-canvas border border-gray-200/60">
              <span className="text-gray-400 block mb-1">Nearby Connections Strategy</span>
              <span className="font-bold text-navy text-sm">P2P_CLUSTER (Multi-Peer Ad-hoc)</span>
            </div>
            <div className="p-3.5 rounded-xl bg-canvas border border-gray-200/60">
              <span className="text-gray-400 block mb-1">Authoritative Online Store</span>
              <span className="font-bold text-navy text-sm">PostgreSQL / Prisma ORM</span>
            </div>
          </div>
        </div>

        {/* Demo Mode & Deterministic Testing */}
        <div className="bg-white rounded-2xl border border-gray-200/80 shadow-sm p-6 space-y-4">
          <h2 className="text-base font-bold text-navy flex items-center gap-2 border-b pb-3">
            <Database className="w-5 h-5 text-amber-500" /> Demonstration Dataset
          </h2>
          <p className="text-xs text-gray-600 leading-relaxed font-medium">
            In compliance with hackathon evaluation guidelines, the demo dataset provides pre-seeded disaster incidents clearly identified as test records with realistic geographic coordinates, victim counts, and relief stock.
          </p>

          <div className="flex items-center justify-between p-4 rounded-xl bg-canvas border border-gray-200/60 text-xs">
            <div>
              <p className="font-bold text-navy">Deterministic Disaster Scenario Data</p>
              <p className="text-gray-400 mt-0.5">Cleans and reloads default evaluation incidents, volunteers, and supplies.</p>
            </div>
            <button
              onClick={resetDemoData}
              disabled={isResetting}
              className="px-4 py-2 rounded-xl bg-amber-500 hover:bg-amber-600 text-navy font-bold flex items-center gap-1.5 shadow-sm transition-colors"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${isResetting ? 'animate-spin' : ''}`} />
              {isResetting ? 'Resetting...' : 'Reload Demo Records'}
            </button>
          </div>
        </div>

        {/* Account Details & Session */}
        <div className="bg-white rounded-2xl border border-gray-200/80 shadow-sm p-6 space-y-4">
          <h2 className="text-base font-bold text-navy flex items-center gap-2 border-b pb-3">
            <Shield className="w-5 h-5 text-navy" /> Authenticated Session
          </h2>
          {user ? (
            <div className="flex items-center justify-between text-xs">
              <div>
                <p className="font-bold text-ink">{user.fullName}</p>
                <p className="text-gray-400">{user.email} • Role: {user.role}</p>
              </div>
              <button
                onClick={logout}
                className="px-3 py-1.5 rounded-lg bg-gray-100 hover:bg-gray-200 text-gray-700 font-bold flex items-center gap-1.5"
              >
                <LogOut className="w-3.5 h-3.5" /> Sign Out
              </button>
            </div>
          ) : (
            <p className="text-xs text-gray-400">No user logged in.</p>
          )}
        </div>
      </div>
    </div>
  );
};
