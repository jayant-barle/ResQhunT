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

export interface MeshEnvelopePayload {
  category: EmergencyCategory;
  severity: SeverityLevel;
  affectedCount: number;
  description: string;
  latitude?: number | null;
  longitude?: number | null;
  locationAccuracy?: number | null;
  locationAddress?: string | null;
  emergencyContacts?: Array<{ name: string; phone: string }>;
  medicalFlags?: {
    requiresTriage?: boolean;
    unconsciousVictims?: boolean;
    severeBleeding?: boolean;
  };
}

export interface MeshIntegrity {
  checksum: string;
  algorithm: 'SHA-256';
}

export interface MeshMessageEnvelope {
  messageId: string;
  requestId: string;
  originDeviceId: string;
  messageType: 'EMERGENCY_SOS' | 'DELIVERY_ACK' | 'RESOURCE_INFO';
  protocolVersion: number;
  createdAt: number;
  expiresAt: number;
  hopCount: number;
  maxHops: number;
  payload: MeshEnvelopePayload;
  integrity: MeshIntegrity;
}

export interface PriorityEvaluation {
  priorityScore: number;
  priorityCategory: SeverityLevel;
  explanation: string;
  ruleVersion: string;
}
