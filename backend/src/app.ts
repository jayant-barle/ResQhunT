import express from 'express';
import cors from 'cors';
import routes from './routes';
import { errorHandler } from './middleware/errorHandler';

export const createApp = () => {
  const app = express();

  // Cross-Origin Resource Sharing
  app.use(
    cors({
      origin: '*',
      methods: ['GET', 'POST', 'PATCH', 'PUT', 'DELETE', 'OPTIONS'],
      allowedHeaders: ['Content-Type', 'Authorization']
    })
  );

  // Body Parsing
  app.use(express.json({ limit: '10mb' }));
  app.use(express.urlencoded({ extended: true, limit: '10mb' }));

  // Root endpoint
  app.get('/', (req, res) => {
    res.json({
      name: 'ResQhunT Emergency Rescue API',
      version: '1.0.0',
      tagline: 'When Networks Fail, ResQhunT Connects.',
      endpoints: {
        health: '/api/health',
        auth: '/api/auth',
        sosSync: '/api/sos/sync',
        incidents: '/api/incidents',
        volunteers: '/api/volunteers',
        resources: '/api/resources',
        activity: '/api/activity'
      }
    });
  });

  // Mount API routes
  app.use('/api', routes);

  // Global Error Handler
  app.use(errorHandler);

  return app;
};
