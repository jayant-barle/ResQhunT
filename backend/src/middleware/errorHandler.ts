import { Request, Response, NextFunction } from 'express';

export const errorHandler = (err: any, req: Request, res: Response, next: NextFunction): void => {
  console.error('[ResQhunT Error]:', err);

  const statusCode = err.status || err.statusCode || 500;
  res.status(statusCode).json({
    error: err.name || 'INTERNAL_SERVER_ERROR',
    message: err.message || 'An unexpected error occurred.',
    details: process.env.NODE_ENV === 'development' ? err.stack : undefined
  });
};
