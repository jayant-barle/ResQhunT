import React, { useEffect, useState } from 'react';
import { api } from '../api/client';
import { Volunteer } from '../types';
import { UserCheck, Phone, Mail, Award, CheckCircle2, XCircle } from 'lucide-react';

export const Volunteers: React.FC = () => {
  const [volunteers, setVolunteers] = useState<Volunteer[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [filterSkill, setFilterSkill] = useState<string>('');

  const fetchVolunteers = async () => {
    try {
      setLoading(true);
      const res = await api.getVolunteers({ skill: filterSkill || undefined });
      setVolunteers(res || []);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchVolunteers();
  }, [filterSkill]);

  const handleToggleAvailability = async (v: Volunteer) => {
    try {
      await api.getMe(); // checks auth
      // Update availability
      await fetch(`/api/volunteers/${v.id}/availability`, {
        method: 'PATCH',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${localStorage.getItem('resqhunt_token')}`
        },
        body: JSON.stringify({ isAvailable: !v.isAvailable })
      });
      fetchVolunteers();
    } catch (err: any) {
      alert(`Failed to update availability: ${err.message}`);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-black text-navy tracking-tight">Rescue Volunteer Roster</h1>
          <p className="text-sm text-gray-500 font-medium">
            Trained field operatives, paramedics, and search & rescue teams.
          </p>
        </div>

        {/* Skill Filter */}
        <div className="flex items-center gap-2">
          <label className="text-xs font-bold text-gray-500">Filter Skill:</label>
          <select
            value={filterSkill}
            onChange={(e) => setFilterSkill(e.target.value)}
            className="p-2 rounded-xl border border-gray-200 text-xs font-semibold focus:outline-none"
          >
            <option value="">All Skills</option>
            <option value="PARAMEDIC">Paramedic</option>
            <option value="FIRST_AID">First Aid</option>
            <option value="SEARCH_AND_RESCUE">Search & Rescue</option>
            <option value="BOAT_OPERATOR">Boat Operator</option>
            <option value="DRIVING">Driving</option>
          </select>
        </div>
      </div>

      {loading ? (
        <div className="py-16 text-center text-gray-400 text-sm">Loading volunteer roster...</div>
      ) : volunteers.length === 0 ? (
        <div className="py-16 text-center text-gray-400 text-sm">No volunteers registered matching criteria.</div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          {volunteers.map((vol) => (
            <div
              key={vol.id}
              className="bg-white rounded-2xl border border-gray-200/80 shadow-sm p-5 space-y-4 hover:shadow-md transition-shadow"
            >
              <div className="flex items-start justify-between gap-3">
                <div>
                  <h3 className="font-bold text-navy text-base">{vol.user.fullName}</h3>
                  <div className="flex items-center gap-1.5 text-xs text-gray-400 mt-0.5">
                    <UserCheck className="w-3.5 h-3.5" />
                    <span>Active Tasks: {vol.activeAssignmentsCount}</span>
                  </div>
                </div>
                <button
                  onClick={() => handleToggleAvailability(vol)}
                  className={`px-2.5 py-1 rounded-full text-xs font-bold flex items-center gap-1 transition-colors ${
                    vol.isAvailable
                      ? 'bg-teal/15 text-teal-dark border border-teal/30 hover:bg-teal/25'
                      : 'bg-gray-100 text-gray-600 border border-gray-200 hover:bg-gray-200'
                  }`}
                >
                  {vol.isAvailable ? (
                    <>
                      <CheckCircle2 className="w-3.5 h-3.5" /> Available
                    </>
                  ) : (
                    <>
                      <XCircle className="w-3.5 h-3.5" /> Off Duty
                    </>
                  )}
                </button>
              </div>

              {/* Skills */}
              <div className="space-y-1.5">
                <span className="text-[11px] font-bold text-gray-400 uppercase tracking-wider flex items-center gap-1">
                  <Award className="w-3 h-3" /> Certified Skills
                </span>
                <div className="flex flex-wrap gap-1.5">
                  {vol.skills.split(',').map((skill, idx) => (
                    <span
                      key={idx}
                      className="px-2 py-0.5 rounded-md bg-navy/5 text-navy text-[11px] font-semibold border border-navy/10"
                    >
                      {skill.trim().replace(/_/g, ' ')}
                    </span>
                  ))}
                </div>
              </div>

              {/* Contacts */}
              <div className="border-t border-gray-100 pt-3 space-y-1 text-xs text-gray-500 font-medium">
                {vol.user.phone && (
                  <div className="flex items-center gap-2">
                    <Phone className="w-3.5 h-3.5 text-gray-400" />
                    <span>{vol.user.phone}</span>
                  </div>
                )}
                <div className="flex items-center gap-2">
                  <Mail className="w-3.5 h-3.5 text-gray-400" />
                  <span className="line-clamp-1">{vol.user.email}</span>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
