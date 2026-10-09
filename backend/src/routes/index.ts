import { Router } from 'express';
import { register, login, getMe } from '../controllers/authController';
import { syncMeshEnvelopes, createOnlineSos } from '../controllers/sosController';
import {
  getIncidents,
  getIncidentById,
  updateIncidentStatus,
  overridePriority
} from '../controllers/incidentController';
import {
  getVolunteers,
  updateVolunteerAvailability
} from '../controllers/volunteerController';
import {
  getAssignments,
  createAssignment,
  updateAssignmentStatus
} from '../controllers/assignmentController';
import {
  getResources,
  createOrUpdateResource,
  allocateResource
} from '../controllers/resourceController';
import { getActivityLogs } from '../controllers/activityController';
import { authenticateToken, optionalAuthenticateToken } from '../middleware/auth';
import { requireRoles } from '../middleware/rbac';
import { seedDemoData } from '../seed/seedData';

const router = Router();

// Health Check
router.get('/health', (req, res) => {
  res.json({
    status: 'ONLINE',
    service: 'ResQhunT Backend',
    timestamp: new Date().toISOString()
  });
});

// Demo Data Reset Endpoint (For Hackathon live testing & demonstration)
router.post('/demo/reset', async (req, res) => {
  try {
    const result = await seedDemoData();
    res.json({ success: true, message: 'Demo data reset successfully', stats: result });
  } catch (error: any) {
    res.status(500).json({ error: 'DEMO_RESET_FAILED', message: error.message });
  }
});

// 1. Authentication
router.post('/auth/register', register);
router.post('/auth/login', login);
router.get('/auth/me', authenticateToken, getMe);

// 2. Emergency SOS & Mesh Sync
router.post('/sos/sync', optionalAuthenticateToken, syncMeshEnvelopes);
router.post('/sos', optionalAuthenticateToken, createOnlineSos);

// 3. Incidents & Coordinator Triage
router.get('/incidents', optionalAuthenticateToken, getIncidents);
router.get('/incidents/:id', optionalAuthenticateToken, getIncidentById);
router.patch(
  '/incidents/:id/status',
  authenticateToken,
  requireRoles('COORDINATOR', 'ADMIN', 'VOLUNTEER'),
  updateIncidentStatus
);
router.patch(
  '/incidents/:id/priority-override',
  authenticateToken,
  requireRoles('COORDINATOR', 'ADMIN'),
  overridePriority
);

// 4. Volunteers
router.get('/volunteers', authenticateToken, getVolunteers);
router.patch('/volunteers/:id/availability', authenticateToken, updateVolunteerAvailability);

// 5. Assignments
router.get('/assignments', authenticateToken, getAssignments);
router.post(
  '/assignments',
  authenticateToken,
  requireRoles('COORDINATOR', 'ADMIN'),
  createAssignment
);
router.patch(
  '/assignments/:id/status',
  authenticateToken,
  requireRoles('VOLUNTEER', 'COORDINATOR', 'ADMIN'),
  updateAssignmentStatus
);

// 6. Resources
router.get('/resources', optionalAuthenticateToken, getResources);
router.post(
  '/resources',
  authenticateToken,
  requireRoles('COORDINATOR', 'ADMIN'),
  createOrUpdateResource
);
router.post(
  '/resources/allocate',
  authenticateToken,
  requireRoles('COORDINATOR', 'ADMIN'),
  allocateResource
);

// 7. Activity Logs
router.get('/activity', authenticateToken, getActivityLogs);

export default router;
