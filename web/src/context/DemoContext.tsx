import React, { createContext, useContext, useState } from 'react';
import { api } from '../api/client';

interface DemoContextType {
  isDemoMode: boolean;
  setDemoMode: (enabled: boolean) => void;
  resetDemoData: () => Promise<void>;
  isResetting: boolean;
}

const DemoContext = createContext<DemoContextType | undefined>(undefined);

export const DemoProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [isDemoMode, setIsDemoMode] = useState<boolean>(true);
  const [isResetting, setIsResetting] = useState<boolean>(false);

  const resetDemoData = async () => {
    setIsResetting(true);
    try {
      await api.resetDemoData();
      window.location.reload();
    } catch (err) {
      console.error('Failed to reset demo data:', err);
    } finally {
      setIsResetting(false);
    }
  };

  return (
    <DemoContext.Provider
      value={{
        isDemoMode,
        setDemoMode: setIsDemoMode,
        resetDemoData,
        isResetting
      }}
    >
      {children}
    </DemoContext.Provider>
  );
};

export const useDemo = () => {
  const context = useContext(DemoContext);
  if (!context) throw new Error('useDemo must be used within a DemoProvider');
  return context;
};
