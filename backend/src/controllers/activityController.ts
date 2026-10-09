import { Request, Response } from 'express';
import { prisma } from '../config/db';

export const getActivityLogs = async (req: Request, res: Response): Promise<void> => {
  try {
    const { entityType, limit = '50' } = req.query;

    const limitNum = Math.min(100, Math.max(1, parseInt(limit as string, 10) || 50));
    const where: any = {};
    if (entityType) {
      where.entityType = entityType as string;
    }

    const logs = await prisma.activityLog.findMany({
      where,
      take: limitNum,
      orderBy: { timestamp: 'desc' },
      include: {
        performedByUser: {
          select: { fullName: true, role: true, email: true }
        }
      }
    });

    res.json({ logs });
  } catch (error: any) {
    res.status(500).json({ error: 'FETCH_ACTIVITY_LOGS_FAILED', message: error.message });
  }
};
