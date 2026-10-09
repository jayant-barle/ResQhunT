import { Request, Response } from 'express';
import { prisma } from '../config/db';
import { DeliveryState, SeverityLevel } from '../types';

export const getIncidents = async (req: Request, res: Response): Promise<void> => {
  try {
    const { category, severity, status, search, page = '1', limit = '20' } = req.query;

    const pageNum = Math.max(1, parseInt(page as string, 10) || 1);
    const limitNum = Math.min(100, Math.max(1, parseInt(limit as string, 10) || 20));
    const skip = (pageNum - 1) * limitNum;

    const where: any = {};
    if (category) where.category = category as string;
    if (severity) where.severity = severity as string;
    if (status) where.deliveryState = status as string;
    if (search) {
      where.OR = [
        { description: { contains: search as string } },
        { locationAddress: { contains: search as string } },
        { requestId: { contains: search as string } }
      ];
    }

    const [total, incidents] = await Promise.all([
      prisma.emergencyRequest.count({ where }),
      prisma.emergencyRequest.findMany({
        where,
        skip,
        take: limitNum,
        orderBy: [{ priorityScore: 'desc' }, { createdAt: 'desc' }],
        include: {
          assignments: {
            include: {
              volunteer: {
                include: {
                  user: { select: { fullName: true, phone: true } }
                }
              }
            }
          },
          allocations: {
            include: {
              resource: true
            }
          }
        }
      })
    ]);

    res.json({
      incidents,
      total,
      page: pageNum,
      limit: limitNum,
      totalPages: Math.ceil(total / limitNum)
    });
  } catch (error: any) {
    res.status(500).json({ error: 'FETCH_INCIDENTS_FAILED', message: error.message });
  }
};

export const getIncidentById = async (req: Request, res: Response): Promise<void> => {
  try {
    const id = String(req.params.id);

    const incident = await prisma.emergencyRequest.findFirst({
      where: {
        OR: [{ id }, { requestId: id }]
      },
      include: {
        assignments: {
          include: {
            volunteer: {
              include: {
                user: { select: { id: true, fullName: true, phone: true, email: true } }
              }
            },
            assignedByUser: { select: { id: true, fullName: true } }
          }
        },
        allocations: {
          include: {
            resource: true,
            allocatedByUser: { select: { id: true, fullName: true } }
          }
        },
        syncRecords: true
      }
    });

    if (!incident) {
      res.status(404).json({ error: 'NOT_FOUND', message: 'Incident not found.' });
      return;
    }

    res.json({ incident });
  } catch (error: any) {
    res.status(500).json({ error: 'FETCH_INCIDENT_FAILED', message: error.message });
  }
};

export const updateIncidentStatus = async (req: Request, res: Response): Promise<void> => {
  try {
    const id = String(req.params.id);
    const { status, note } = req.body;

    const validStates: DeliveryState[] = [
      'COORDINATOR_ACKNOWLEDGED',
      'ASSIGNED',
      'IN_PROGRESS',
      'RESOLVED'
    ];

    if (!validStates.includes(status)) {
      res.status(400).json({
        error: 'INVALID_STATUS',
        message: `Status must be one of: ${validStates.join(', ')}`
      });
      return;
    }

    const incident = await prisma.emergencyRequest.findFirst({
      where: { OR: [{ id }, { requestId: id }] }
    });

    if (!incident) {
      res.status(404).json({ error: 'NOT_FOUND', message: 'Incident not found.' });
      return;
    }

    const updated = await prisma.emergencyRequest.update({
      where: { id: incident.id },
      data: { deliveryState: status }
    });

    await prisma.activityLog.create({
      data: {
        actionType: 'STATUS_CHANGE',
        entityType: 'INCIDENT',
        entityId: incident.id,
        details: `Status transitioned from ${incident.deliveryState} to ${status}. Note: ${note || 'None'}`,
        performedByUserId: req.user?.id || null
      }
    });

    res.json({ success: true, incident: updated });
  } catch (error: any) {
    res.status(500).json({ error: 'UPDATE_STATUS_FAILED', message: error.message });
  }
};

export const overridePriority = async (req: Request, res: Response): Promise<void> => {
  try {
    const id = String(req.params.id);
    const { newScore, newCategory, reason } = req.body;

    if (newScore === undefined || !newCategory || !reason) {
      res.status(400).json({
        error: 'VALIDATION_ERROR',
        message: 'newScore, newCategory, and justification reason are required.'
      });
      return;
    }

    const scoreNum = Math.min(100, Math.max(0, parseFloat(newScore)));
    const validCategories: SeverityLevel[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];
    if (!validCategories.includes(newCategory)) {
      res.status(400).json({ error: 'INVALID_CATEGORY', message: 'Invalid priority category.' });
      return;
    }

    const incident = await prisma.emergencyRequest.findFirst({
      where: { OR: [{ id }, { requestId: id }] }
    });

    if (!incident) {
      res.status(404).json({ error: 'NOT_FOUND', message: 'Incident not found.' });
      return;
    }

    const updated = await prisma.emergencyRequest.update({
      where: { id: incident.id },
      data: {
        priorityScore: scoreNum,
        priorityCategory: newCategory,
        overrideScore: scoreNum,
        overrideReason: reason
      }
    });

    await prisma.activityLog.create({
      data: {
        actionType: 'PRIORITY_OVERRIDE',
        entityType: 'INCIDENT',
        entityId: incident.id,
        details: `Coordinator manually overrode priority score from ${incident.priorityScore} (${incident.priorityCategory}) to ${scoreNum} (${newCategory}). Justification: ${reason}`,
        performedByUserId: req.user?.id || null
      }
    });

    res.json({ success: true, incident: updated });
  } catch (error: any) {
    res.status(500).json({ error: 'PRIORITY_OVERRIDE_FAILED', message: error.message });
  }
};
