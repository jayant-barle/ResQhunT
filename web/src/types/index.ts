export type UserRole = 'CITIZEN' | 'VOLUNTEER' | 'COORDINATOR' | 'ADMIN';

export type EmergencyCategory = 'MEDICAL' | 'RESCUE' | 'FOOD' | 'WATER' | 'SHELTER' | 'OTHER';

export type SeverityLevel = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';

export type DeliveryState =
  | 'CREATED'
  | 'STORED_LOCALLY'
  | 'RELAY_PENDING'
  | 'RELAYED_TO_PEER'
  | 'SERVER_RECEIVED'
  | 'COORDINATOR_ACKNOWLEDGED'
  | 'ASSIGNED'
  | 'IN_PROGRESS'
  | 'RESOLVED';

export type AssignmentStatus = 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'IN_PROGRESS' | 'COMPLETED';

export type ResourceCategory = 'FOOD' | 'WATER' | 'MEDICAL' | 'SHELTER' | 'OTHER';

export interface User {
  id: string;
  email: string;
  fullName: string;
  role: UserRole;
  phone?: string | null;
}

export interface Assignment {
  id: string;
  incidentId: string;
  volunteerId: string;
  status: AssignmentStatus;
  notes?: string | null;
  createdAt: string;
  volunteer?: {
    user: {
      fullName: string;
      phone?: string | null;
      email: string;
    };
  };
  assignedByUser?: {
    fullName: string;
  };
}

export interface ResourceAllocation {
  id: string;
  resourceId: string;
  incidentId: string;
  quantity: number;
  timestamp: string;
  notes?: string | null;
  resource?: ReliefResource;
  allocatedByUser?: {
    fullName: string;
  };
}

export interface EmergencyRequest {
  id: string;
  requestId: string;
  originDeviceId: string;
  category: EmergencyCategory;
  severity: SeverityLevel;
  affectedCount: number;
  description: string;
  latitude?: number | null;
  longitude?: number | null;
  locationAccuracy?: number | null;
  locationAddress?: string | null;
  deliveryState: DeliveryState;
  priorityScore: number;
  priorityCategory: SeverityLevel;
  overrideScore?: number | null;
  overrideReason?: string | null;
  syncHopCount: number;
  createdAt: string;
  updatedAt: string;
  assignments?: Assignment[];
  allocations?: ResourceAllocation[];
}

export interface Volunteer {
  id: string;
  userId: string;
  skills: string;
  isAvailable: boolean;
  latitude?: number | null;
  longitude?: number | null;
  phone?: string | null;
  activeAssignmentsCount: number;
  user: {
    id: string;
    fullName: string;
    email: string;
    phone?: string | null;
  };
  assignments?: Array<{
    id: string;
    status: AssignmentStatus;
    incident: {
      id: string;
      requestId: string;
      category: EmergencyCategory;
      description: string;
    };
  }>;
}

export interface ReliefResource {
  id: string;
  name: string;
  category: ResourceCategory;
  totalQuantity: number;
  availableQuantity: number;
  unit: string;
  location: string;
  isVerified: boolean;
  createdAt: string;
  allocations?: ResourceAllocation[];
}

export interface ActivityLog {
  id: string;
  actionType: string;
  entityType: string;
  entityId: string;
  details: string;
  timestamp: string;
  performedByUser?: {
    fullName: string;
    role: string;
    email: string;
  } | null;
}
