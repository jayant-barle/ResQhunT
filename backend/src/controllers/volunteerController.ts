import { Request, Response } from 'express';
import { prisma } from '../config/db';

export const getVolunteers = async (req: Request, res: Response): Promise<void> => {
  try {
    const { available, skill } = req.query;

    const where: any = {};
    if (available !== undefined) {
      where.isAvailable = available === 'true';
    }
    if (skill) {
      where.skills = { contains: skill as string };
    }

    const volunteers = await prisma.volunteer.findMany({
      where,
      include: {
        user: {
          select: {
            id: true,
            fullName: true,
            email: true,
            phone: true
          }
        },
        assignments: {
          where: {
            status: { in: ['PENDING', 'ACCEPTED', 'IN_PROGRESS'] }
          },
          include: {
            incident: {
              select: {
                id: true,
                requestId: true,
                category: true,
                severity: true,
                description: true
              }
            }
          }
        }
      },
      orderBy: { createdAt: 'desc' }
    });

    res.json({ volunteers });
  } catch (error: any) {
    res.status(500).json({ error: 'FETCH_VOLUNTEERS_FAILED', message: error.message });
  }
};

export const updateVolunteerAvailability = async (req: Request, res: Response): Promise<void> => {
  try {
    const id = String(req.params.id);
    const { isAvailable, latitude, longitude } = req.body;

    const volunteer = await prisma.volunteer.findUnique({ where: { id } });
    if (!volunteer) {
      res.status(404).json({ error: 'NOT_FOUND', message: 'Volunteer profile not found.' });
      return;
    }

    // Ensure only the volunteer themselves, coordinator, or admin can update
    if (req.user && req.user.role === 'VOLUNTEER' && volunteer.userId !== req.user.id) {
      res.status(403).json({ error: 'FORBIDDEN', message: 'Cannot edit another volunteer profile.' });
      return;
    }

    const updated = await prisma.volunteer.update({
      where: { id },
      data: {
        isAvailable: isAvailable !== undefined ? Boolean(isAvailable) : volunteer.isAvailable,
        latitude: latitude !== undefined ? parseFloat(latitude) : volunteer.latitude,
        longitude: longitude !== undefined ? parseFloat(longitude) : volunteer.longitude
      }
    });

    res.json({ success: true, volunteer: updated });
  } catch (error: any) {
    res.status(500).json({ error: 'UPDATE_VOLUNTEER_FAILED', message: error.message });
  }
};
