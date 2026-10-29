import React, { useState, useEffect } from 'react';
import { useAuth } from '../../context/AuthContext';
import { useDemo } from '../../context/DemoContext';
import { Radio, RefreshCw, UserCheck, LogOut, Wifi, WifiOff } from 'lucide-react';

export const Navbar: React.FC = () => {
  const { user, logout, switchRoleQuick } = useAuth();
  const { isDemoMode, setDemoMode, resetDemoData, isResetting } = useDemo();
  const [isOnline, setIsOnline] = useState<boolean>(navigator.onLine);

  useEffect(() => {
    const handleOnline = () => setIsOnline(true);
    const handleOffline = () => setIsOnline(false);
    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);
    return () => {
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
    };
  }, []);

  return (
    <header className="bg-navy text-white shadow-md sticky top-0 z-50">
      {/* Demo Mode Notice Banner */}
      {isDemoMode && (
        <div className="bg-amber-500 text-navy font-bold text-xs py-1 px-4 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="bg-navy text-white text-[10px] px-1.5 py-0.5 rounded uppercase">Demo Mode</span>
            <span>Displaying deterministic disaster scenario records. Live emergency sync is actively monitored.</span>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={resetDemoData}
              disabled={isResetting}
              className="bg-navy text-white hover:bg-navy-light text-xs px-2.5 py-0.5 rounded flex items-center gap-1 transition-colors"
            >
              <RefreshCw className={`w-3 h-3 ${isResetting ? 'animate-spin' : ''}`} />
              {isResetting ? 'Resetting...' : 'Reset Demo Dataset'}
            </button>
            <button
              onClick={() => setDemoMode(false)}
              className="text-navy hover:underline text-xs"
            >
              Dismiss
            </button>
          </div>
        </div>
      )}

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between">
        {/* Brand */}
        <div className="flex items-center gap-3">
          <img
            src="/app-icon.png"
            alt="ResQhunT App Icon"
            className="w-10 h-10 rounded-xl shadow-md object-contain bg-white/10 p-0.5"
          />
          <div>
            <div className="flex items-center gap-2">
              <span className="font-black text-xl tracking-tight text-white">ResQhunT</span>
              <span className="text-[10px] font-semibold tracking-wider uppercase px-2 py-0.5 rounded-full bg-teal/20 text-teal border border-teal/30">
                Command Center
              </span>
            </div>
            <p className="text-[11px] text-gray-300 font-medium">When Networks Fail, ResQhunT Connects.</p>
          </div>
        </div>

        {/* Status Indicators & Role Switcher */}
        <div className="flex items-center gap-4">
          {/* Network Health */}
          <div className="hidden sm:flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-navy-dark text-xs border border-white/10">
            {isOnline ? (
              <>
                <Wifi className="w-3.5 h-3.5 text-teal" />
                <span className="text-gray-200">Gateway Online</span>
              </>
            ) : (
              <>
                <WifiOff className="w-3.5 h-3.5 text-emergency" />
                <span className="text-emergency-light font-bold">Offline Isolated</span>
              </>
            )}
          </div>

          {/* Quick Role Switcher for Hackathon Judges */}
          <div className="hidden md:flex items-center gap-1 bg-navy-dark p-1 rounded-lg border border-white/10 text-xs">
            <span className="text-gray-400 px-1 text-[11px]">Role:</span>
            <button
              onClick={() => switchRoleQuick('COORDINATOR')}
              className={`px-2 py-1 rounded font-medium transition-colors ${
                user?.role === 'COORDINATOR'
                  ? 'bg-emergency text-white font-bold'
                  : 'text-gray-300 hover:text-white'
              }`}
            >
              Coordinator
            </button>
            <button
              onClick={() => switchRoleQuick('VOLUNTEER')}
              className={`px-2 py-1 rounded font-medium transition-colors ${
                user?.role === 'VOLUNTEER'
                  ? 'bg-teal text-navy font-bold'
                  : 'text-gray-300 hover:text-white'
              }`}
            >
              Volunteer
            </button>
            <button
              onClick={() => switchRoleQuick('CITIZEN')}
              className={`px-2 py-1 rounded font-medium transition-colors ${
                user?.role === 'CITIZEN'
                  ? 'bg-white text-navy font-bold'
                  : 'text-gray-300 hover:text-white'
              }`}
            >
              Citizen
            </button>
          </div>

          {/* User Profile & Logout */}
          {user ? (
            <div className="flex items-center gap-3">
              <div className="text-right hidden sm:block">
                <p className="text-xs font-bold text-white">{user.fullName}</p>
                <p className="text-[10px] text-gray-400 uppercase tracking-wider">{user.role}</p>
              </div>
              <button
                onClick={logout}
                title="Logout"
                className="p-2 rounded-lg bg-navy-dark hover:bg-navy-light text-gray-300 hover:text-white transition-colors border border-white/10"
              >
                <LogOut className="w-4 h-4" />
              </button>
            </div>
          ) : (
            <a
              href="/login"
              className="text-xs font-semibold bg-emergency hover:bg-emergency-dark px-3 py-1.5 rounded-lg text-white transition-colors"
            >
              Login
            </a>
          )}
        </div>
      </div>
    </header>
  );
};
