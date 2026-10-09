import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { DemoProvider } from './context/DemoContext';
import { DashboardLayout } from './components/layout/DashboardLayout';
import { Overview } from './pages/Overview';
import { Incidents } from './pages/Incidents';
import { IncidentDetail } from './pages/IncidentDetail';
import { MapPage } from './pages/MapPage';
import { Volunteers } from './pages/Volunteers';
import { Assignments } from './pages/Assignments';
import { Resources } from './pages/Resources';
import { ActivityLog } from './pages/ActivityLog';
import { Settings } from './pages/Settings';
import { Login } from './pages/Login';

export const App: React.FC = () => {
  return (
    <BrowserRouter>
      <AuthProvider>
        <DemoProvider>
          <Routes>
            <Route path="/login" element={<Login />} />
            <Route path="/" element={<DashboardLayout />}>
              <Route index element={<Overview />} />
              <Route path="incidents" element={<Incidents />} />
              <Route path="incidents/:id" element={<IncidentDetail />} />
              <Route path="map" element={<MapPage />} />
              <Route path="volunteers" element={<Volunteers />} />
              <Route path="assignments" element={<Assignments />} />
              <Route path="resources" element={<Resources />} />
              <Route path="activity" element={<ActivityLog />} />
              <Route path="settings" element={<Settings />} />
            </Route>
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </DemoProvider>
      </AuthProvider>
    </BrowserRouter>
  );
};

export default App;
