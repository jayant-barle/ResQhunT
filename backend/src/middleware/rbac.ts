import { Request, Response, NextFunction } from 'express';
import { UserRole } from '../types';

export const requireRoles = (...allowedRoles: UserRole[]) => {
  return (req: Request, res: Response, next: NextFunction): void => {
    if (!req.user) {
      res.status(401).json({ error: 'UNAUTHORIZED', message: 'Authentication required.' });
      return;
    }

    // ADMIN has superset privileges
    if (req.user.role === 'ADMIN' || allowedRoles.includes(req.user.role)) {
      next();
      return;
    }

    res.status(403).json({
      error: 'FORBIDDEN',
      message: `Operation requires one of the following roles: [${allowedRoles.join(', ')}]. Your role: ${req.user.role}`
    });
  };
};
