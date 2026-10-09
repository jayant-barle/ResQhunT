import React, { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { useNavigate } from 'react-router-dom';
import { Radio, AlertTriangle } from 'lucide-react';
import { UserRole } from '../types';

export const Login: React.FC = () => {
  const { login, register } = useAuth();
  const navigate = useNavigate();

  const [isRegister, setIsRegister] = useState<boolean>(false);
  const [email, setEmail] = useState<string>('coordinator@resqhunt.org');
  const [password, setPassword] = useState<string>('Password123!');
  const [fullName, setFullName] = useState<string>('');
  const [role, setRole] = useState<UserRole>('COORDINATOR');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState<boolean>(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      if (isRegister) {
        await register(email, password, fullName, role);
      } else {
        await login(email, password);
      }
      navigate('/');
    } catch (err: any) {
      setError(err.message || 'Authentication failed');
    } finally {
      setSubmitting(false);
    }
  };

  const setPresetUser = (presetEmail: string, presetRole: UserRole) => {
    setIsRegister(false);
    setEmail(presetEmail);
    setPassword('Password123!');
    setRole(presetRole);
  };

  return (
    <div className="min-h-screen bg-canvas flex flex-col justify-center items-center p-4">
      <div className="max-w-md w-full bg-white rounded-3xl border border-gray-200/80 shadow-xl p-8 space-y-6">
        {/* Brand */}
        <div className="text-center space-y-2">
          <div className="w-14 h-14 rounded-2xl bg-emergency flex items-center justify-center mx-auto shadow-lg shadow-red-900/30">
            <Radio className="w-8 h-8 text-white animate-pulse" />
          </div>
          <h1 className="text-2xl font-black text-navy tracking-tight">ResQhunT</h1>
          <p className="text-xs text-gray-500 font-medium">When Networks Fail, ResQhunT Connects.</p>
        </div>

        {error && (
          <div className="p-3.5 rounded-xl bg-red-50 border border-red-200 text-red-700 text-xs flex items-center gap-2">
            <AlertTriangle className="w-4 h-4 shrink-0" />
            <span>{error}</span>
          </div>
        )}

        {/* Demo Fast Logins */}
        <div className="space-y-1.5 bg-canvas p-3 rounded-2xl border border-gray-200/60">
          <p className="text-[10px] font-bold text-gray-400 uppercase tracking-wider text-center">
            One-Click Evaluator Presets
          </p>
          <div className="grid grid-cols-3 gap-1.5 text-xs">
            <button
              type="button"
              onClick={() => setPresetUser('coordinator@resqhunt.org', 'COORDINATOR')}
              className="p-1.5 rounded-lg bg-navy hover:bg-navy-light text-white font-bold text-center"
            >
              Coordinator
            </button>
            <button
              type="button"
              onClick={() => setPresetUser('volunteer1@resqhunt.org', 'VOLUNTEER')}
              className="p-1.5 rounded-lg bg-teal text-navy font-bold text-center hover:opacity-90"
            >
              Volunteer
            </button>
            <button
              type="button"
              onClick={() => setPresetUser('citizen@resqhunt.org', 'CITIZEN')}
              className="p-1.5 rounded-lg bg-white border border-gray-300 text-navy font-bold text-center hover:bg-gray-50"
            >
              Citizen
            </button>
          </div>
        </div>

        {/* Form */}
        <form onSubmit={handleSubmit} className="space-y-4 text-xs font-semibold">
          {isRegister && (
            <div>
              <label className="block text-gray-600 mb-1">Full Name</label>
              <input
                type="text"
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                placeholder="Dr. Rajesh Verma"
                className="w-full p-3 rounded-xl border border-gray-300 font-medium"
                required
              />
            </div>
          )}

          <div>
            <label className="block text-gray-600 mb-1">Email Address</label>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="coordinator@resqhunt.org"
              className="w-full p-3 rounded-xl border border-gray-300 font-medium"
              required
            />
          </div>

          <div>
            <label className="block text-gray-600 mb-1">Password</label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
              className="w-full p-3 rounded-xl border border-gray-300 font-medium"
              required
            />
          </div>

          {isRegister && (
            <div>
              <label className="block text-gray-600 mb-1">System Role</label>
              <select
                value={role}
                onChange={(e) => setRole(e.target.value as UserRole)}
                className="w-full p-3 rounded-xl border border-gray-300 font-medium"
              >
                <option value="CITIZEN">Citizen (Victim Beacon)</option>
                <option value="VOLUNTEER">Volunteer (Rescue Operative)</option>
                <option value="COORDINATOR">Coordinator (Incident Triage)</option>
                <option value="ADMIN">System Administrator</option>
              </select>
            </div>
          )}

          <button
            type="submit"
            disabled={submitting}
            className="w-full py-3 rounded-xl bg-emergency hover:bg-emergency-dark text-white font-bold text-sm shadow-md shadow-red-900/30 transition-all"
          >
            {submitting ? 'Authenticating...' : isRegister ? 'Create Account' : 'Sign In'}
          </button>
        </form>

        <div className="text-center pt-2 border-t text-xs text-gray-500">
          <button
            type="button"
            onClick={() => setIsRegister(!isRegister)}
            className="font-bold text-navy hover:underline"
          >
            {isRegister ? 'Already have an account? Sign in' : "Don't have an account? Register here"}
          </button>
        </div>
      </div>
    </div>
  );
};
