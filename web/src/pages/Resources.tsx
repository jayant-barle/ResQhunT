import React, { useEffect, useState } from 'react';
import { api } from '../api/client';
import { ReliefResource } from '../types';
import { Boxes, PlusCircle, CheckCircle2, AlertTriangle, ShieldCheck, MapPin } from 'lucide-react';

export const Resources: React.FC = () => {
  const [resources, setResources] = useState<ReliefResource[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [showAddModal, setShowAddModal] = useState<boolean>(false);
  const [editingResource, setEditingResource] = useState<ReliefResource | null>(null);

  const [form, setForm] = useState({
    name: '',
    category: 'WATER',
    totalQuantity: 100,
    availableQuantity: 100,
    unit: 'cans',
    location: '',
    isVerified: true
  });
  const [submitting, setSubmitting] = useState<boolean>(false);

  const fetchResources = async () => {
    try {
      setLoading(true);
      const res = await api.getResources();
      setResources(res || []);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchResources();
  }, []);

  const handleOpenAdd = () => {
    setEditingResource(null);
    setForm({
      name: '',
      category: 'WATER',
      totalQuantity: 100,
      availableQuantity: 100,
      unit: 'cans',
      location: '',
      isVerified: true
    });
    setShowAddModal(true);
  };

  const handleOpenEdit = (res: ReliefResource) => {
    setEditingResource(res);
    setForm({
      name: res.name,
      category: res.category,
      totalQuantity: res.totalQuantity,
      availableQuantity: res.availableQuantity,
      unit: res.unit,
      location: res.location,
      isVerified: res.isVerified
    });
    setShowAddModal(true);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    try {
      const payload: any = { ...form };
      if (editingResource) {
        payload.id = editingResource.id;
      }
      const token = localStorage.getItem('resqhunt_token');
      const res = await fetch('/api/resources', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`
        },
        body: JSON.stringify(payload)
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.message || 'Failed to save resource');
      setShowAddModal(false);
      fetchResources();
    } catch (err: any) {
      alert(`Error saving resource: ${err.message}`);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-black text-navy tracking-tight">Relief Supply Inventory</h1>
          <p className="text-sm text-gray-500 font-medium">
            Manage food, water, medical kits, and disaster shelters across relief depots.
          </p>
        </div>
        <button
          onClick={handleOpenAdd}
          className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-navy hover:bg-navy-light text-white text-xs font-bold shadow-sm transition-colors"
        >
          <PlusCircle className="w-4 h-4" /> Add Relief Stock
        </button>
      </div>

      <div className="bg-white rounded-2xl border border-gray-200/80 shadow-sm overflow-hidden">
        {loading ? (
          <div className="py-16 text-center text-gray-400 text-sm">Loading inventory...</div>
        ) : resources.length === 0 ? (
          <div className="py-16 text-center text-gray-400 text-sm">No relief resources recorded.</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-canvas border-b border-gray-200 text-gray-500 font-bold uppercase tracking-wider">
                <tr>
                  <th className="py-3 px-4">Supply Item</th>
                  <th className="py-3 px-4">Category</th>
                  <th className="py-3 px-4">Available / Total Stock</th>
                  <th className="py-3 px-4">Verification State</th>
                  <th className="py-3 px-4">Depot Location</th>
                  <th className="py-3 px-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 font-medium">
                {resources.map((res) => {
                  const percent = Math.round((res.availableQuantity / res.totalQuantity) * 100);
                  return (
                    <tr key={res.id} className="hover:bg-gray-50/60 transition-colors">
                      <td className="py-3.5 px-4 font-bold text-ink">
                        {res.name}
                      </td>
                      <td className="py-3.5 px-4">
                        <span className="px-2 py-0.5 rounded-md bg-navy/5 text-navy font-semibold text-[11px]">
                          {res.category}
                        </span>
                      </td>
                      <td className="py-3.5 px-4">
                        <div className="space-y-1">
                          <div className="flex items-center gap-2 font-bold text-xs">
                            <span className={percent < 20 ? 'text-emergency' : 'text-navy'}>
                              {res.availableQuantity} / {res.totalQuantity} {res.unit}
                            </span>
                            <span className="text-[10px] text-gray-400">({percent}%)</span>
                          </div>
                          <div className="w-32 bg-gray-200 h-1.5 rounded-full overflow-hidden">
                            <div
                              className={`h-full rounded-full ${
                                percent < 20 ? 'bg-emergency' : percent < 50 ? 'bg-amber-500' : 'bg-teal'
                              }`}
                              style={{ width: `${percent}%` }}
                            />
                          </div>
                        </div>
                      </td>
                      <td className="py-3.5 px-4 whitespace-nowrap">
                        {res.isVerified ? (
                          <span className="inline-flex items-center gap-1 text-[11px] font-bold text-teal-dark bg-teal/10 px-2 py-0.5 rounded-md border border-teal/20">
                            <ShieldCheck className="w-3 h-3 text-teal" /> Verified On-Site
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1 text-[11px] font-bold text-amber-700 bg-amber-50 px-2 py-0.5 rounded-md border border-amber-200">
                            <AlertTriangle className="w-3 h-3 text-amber-600" /> Reported Stock
                          </span>
                        )}
                      </td>
                      <td className="py-3.5 px-4 text-gray-500 max-w-xs truncate">
                        <span className="inline-flex items-center gap-1">
                          <MapPin className="w-3 h-3 text-gray-400 shrink-0" />
                          {res.location}
                        </span>
                      </td>
                      <td className="py-3.5 px-4 text-right whitespace-nowrap">
                        <button
                          onClick={() => handleOpenEdit(res)}
                          className="px-3 py-1 rounded-lg bg-gray-100 hover:bg-gray-200 text-ink font-bold"
                        >
                          Edit Stock
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Add / Edit Resource Modal */}
      {showAddModal && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-2xl border border-gray-200 space-y-4">
            <div className="flex items-center justify-between border-b pb-3">
              <h3 className="font-bold text-base text-navy">
                {editingResource ? 'Edit Relief Stock' : 'Add Relief Resource'}
              </h3>
              <button onClick={() => setShowAddModal(false)} className="text-gray-400 hover:text-gray-600 font-bold">
                ✕
              </button>
            </div>
            <form onSubmit={handleSubmit} className="space-y-3.5 text-xs font-semibold">
              <div>
                <label className="block text-gray-600 mb-1">Resource Name</label>
                <input
                  type="text"
                  value={form.name}
                  onChange={(e) => setForm({ ...form, name: e.target.value })}
                  placeholder="e.g. Purified Drinking Water (20L Cans)"
                  className="w-full p-2.5 rounded-lg border border-gray-300 font-medium"
                  required
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-gray-600 mb-1">Category</label>
                  <select
                    value={form.category}
                    onChange={(e) => setForm({ ...form, category: e.target.value })}
                    className="w-full p-2.5 rounded-lg border border-gray-300 font-medium"
                  >
                    <option value="WATER">WATER</option>
                    <option value="FOOD">FOOD</option>
                    <option value="MEDICAL">MEDICAL</option>
                    <option value="SHELTER">SHELTER</option>
                    <option value="OTHER">OTHER</option>
                  </select>
                </div>
                <div>
                  <label className="block text-gray-600 mb-1">Unit</label>
                  <input
                    type="text"
                    value={form.unit}
                    onChange={(e) => setForm({ ...form, unit: e.target.value })}
                    placeholder="cans, kits, packs, tents"
                    className="w-full p-2.5 rounded-lg border border-gray-300 font-medium"
                    required
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-gray-600 mb-1">Total Quantity</label>
                  <input
                    type="number"
                    min="0"
                    value={form.totalQuantity}
                    onChange={(e) => setForm({ ...form, totalQuantity: parseInt(e.target.value, 10) || 0 })}
                    className="w-full p-2.5 rounded-lg border border-gray-300 font-medium"
                    required
                  />
                </div>
                <div>
                  <label className="block text-gray-600 mb-1">Available Quantity</label>
                  <input
                    type="number"
                    min="0"
                    max={form.totalQuantity}
                    value={form.availableQuantity}
                    onChange={(e) => setForm({ ...form, availableQuantity: parseInt(e.target.value, 10) || 0 })}
                    className="w-full p-2.5 rounded-lg border border-gray-300 font-medium"
                    required
                  />
                </div>
              </div>

              <div>
                <label className="block text-gray-600 mb-1">Depot / Warehouse Location</label>
                <input
                  type="text"
                  value={form.location}
                  onChange={(e) => setForm({ ...form, location: e.target.value })}
                  placeholder="e.g. Relief Depot Central Sector 4"
                  className="w-full p-2.5 rounded-lg border border-gray-300 font-medium"
                  required
                />
              </div>

              <div className="flex items-center gap-2 pt-1">
                <input
                  type="checkbox"
                  id="isVerified"
                  checked={form.isVerified}
                  onChange={(e) => setForm({ ...form, isVerified: e.target.checked })}
                  className="rounded text-navy focus:ring-navy"
                />
                <label htmlFor="isVerified" className="text-gray-700">
                  Mark as Independently Verified On-Site
                </label>
              </div>

              <div className="flex justify-end gap-2 pt-3 border-t">
                <button
                  type="button"
                  onClick={() => setShowAddModal(false)}
                  className="px-3 py-1.5 rounded-lg bg-gray-100 text-gray-700"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={submitting}
                  className="px-4 py-1.5 rounded-lg bg-navy text-white font-bold"
                >
                  {submitting ? 'Saving...' : 'Save Stock'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
