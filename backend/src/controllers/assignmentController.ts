import { Request, Response } from 'express';
import { prisma } from '../config/db';
import { AssignmentStatus } from '../types';

export const getAssignments = async (req: Request, res: Response): Promise<void> => {
  try {
    const { status, volunteerId, incidentId } = req.query;

    const where: any = {};
    if (status) where.status = status as string;
    if (volunteerId) where.volunteerId = volunteerId as string;
    if (incidentId) where.incidentId = incidentId as string;

    const assignments = await prisma.assignment.findMany({
      where,
      include: {
        incident: true,
        volunteer: {
          include: {
            user: { select: { fullName: true, phone: true, email: true } }
          }
        },
        assignedByUser: { select: { fullName: true, email: true } }
      },
      orderBy: { createdAt: 'desc' }
    });

    res.json({ assignments });
  } catch (error: any) {
    res.status(500).json({ error: 'FETCH_ASSIGNMENTS_FAILED', message: error.message });
  }
};

export const createAssignment = async (req: Request, res: Response): Promise<void> => {
  try {
    const { incidentId, volunteerId, notes } = req.body;

    if (!incidentId || !volunteerId) {
      res.status(400).json({
        error: 'VALIDATION_ERROR',
        message: 'incidentId and volunteerId are required.'
      });
      return;
    }

    const incident = await prisma.emergencyRequest.findFirst({
      where: { OR: [{ id: incidentId }, { requestId: incidentId }] }
    });
    if (!incident) {
      res.status(404).json({ error: 'NOT_FOUND', message: 'Incident not found.' });
      return;
    }

    const volunteer = await prisma.volunteer.findUnique({
      where: { id: volunteerId },
      include: { user: true }
    });
    if (!volunteer) {
      res.status(404).json({ error: 'NOT_FOUND', message: 'Volunteer not found.' });
      return;
    }

    // Check if volunteer is already assigned to this active incident
    const existingActive = await prisma.assignment.findFirst({
      where: {
        incidentId: incident.id,
        volunteerId: volunteer.id,
        status: { in: ['PENDING', 'ACCEPTED', 'IN_PROGRESS'] }
      }
    });
    if (existingActive) {
      res.status(409).json({
        error: 'ALREADY_ASSIGNED',
        message: 'This volunteer is already actively assigned to this incident.'
      });
      return;
    }

    const [assignment] = await prisma.$transaction([
      prisma.assignment.create({
        data: {
          incidentId: incident.id,
          volunteerId: volunteer.id,
          status: 'PENDING',
          assignedByUserId: req.user?.id || volunteer.userId,
          notes: notes || null
        },
        include: {
          volunteer: { include: { user: true } },
          incident: true
        }
      }),
      prisma.emergencyRequest.update({
        where: { id: incident.id },
        data: { deliveryState: 'ASSIGNED' }
      }),
      prisma.volunteer.update({
        where: { id: volunteer.id },
        data: { activeAssignmentsCount: { increment: 1 } }
      }),
      prisma.activityLog.create({
        data: {
          actionType: 'VOLUNTEER_ASSIGNED',
          entityType: 'ASSIGNMENT',
          entityId: incident.id,
          details: `Volunteer ${volunteer.user.fullName} assigned to incident ${incident.requestId}.`,
          performedByUserId: req.user?.id || null
        }
      })
    ]);

    res.status(201).json({ success: true, assignment });
  } catch (error: any) {
    res.status(500).json({ error: 'CREATE_ASSIGNMENT_FAILED', message: error.message });
  }
};

export const updateAssignmentStatus = async (req: Request, res: Response): Promise<void> => {
  try {
    const id = String(req.params.id);
    const { status, notes } = req.body;

    const validStatuses: AssignmentStatus[] = ['ACCEPTED', 'DECLINED', 'IN_PROGRESS', 'COMPLETED'];
    if (!validStatuses.includes(status)) {
      res.status(400).json({
        error: 'INVALID_STATUS',
        message: `Status must be one of: ${validStatuses.join(', ')}`
      });
      return;
    }

    const assignment = await prisma.assignment.findUnique({
      where: { id },
      include: { volunteer: true, incident: true }
    });

    if (!assignment) {
      res.status(404).json({ error: 'NOT_FOUND', message: 'Assignment not found.' });
      return;
    }

    // Role check: Only the volunteer assigned, coordinator, or admin can update status
    if (req.user && req.user.role === 'VOLUNTEER' && assignment.volunteer.userId !== req.user.id) {
      res.status(403).json({ error: 'FORBIDDEN', message: 'You can only update your own assignments.' });
      return;
    }

    const wasActive = ['PENDING', 'ACCEPTED', 'IN_PROGRESS'].includes(assignment.status);
    const nowFinished = ['DECLINED', 'COMPLETED'].includes(status);

    const transactionOps: any[] = [
      prisma.assignment.update({
        where: { id },
        data: {
          status,
          notes: notes !== undefined ? notes : assignment.notes
        }
      })
    ];

    if (wasActive && nowFinished) {
      transactionOps.push(
        prisma.volunteer.update({
          where: { id: assignment.volunteerId },
          data: { activeAssignmentsCount: { decrement: 1 } }
        })
      );
    }

    if (status === 'IN_PROGRESS') {
      transactionOps.push(
        prisma.emergencyRequest.update({
          where: { id: assignment.incidentId },
          data: { deliveryState: 'IN_PROGRESS' }
        })
      );
    } else if (status === 'COMPLETED') {
      transactionOps.push(
        prisma.emergencyRequest.update({
          where: { id: assignment.incidentId },
          data: { deliveryState: 'RESOLVED' }
        })
      );
    }

    transactionOps.push(
      prisma.activityLog.create({
        data: {
          actionType: 'ASSIGNMENT_STATUS_UPDATE',
          entityType: 'ASSIGNMENT',
          entityId: assignment.id,
          details: `Assignment status updated from ${assignment.status} to ${status}. Notes: ${notes || 'None'}`,
          performedByUserId: req.user?.id || null
        }
      })
    );

    const results = await prisma.$transaction(transactionOps);

    res.json({ success: true, assignment: results[0] });
  } catch (error: any) {
    res.status(500).json({ error: 'UPDATE_ASSIGNMENT_FAILED', message: error.message });
  }
};
