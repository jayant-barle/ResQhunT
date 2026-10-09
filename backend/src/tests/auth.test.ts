import request from 'supertest';
import { createApp } from '../app';
import { prisma } from '../config/db';

const app = createApp();

describe('Authentication & Authorization Tests', () => {
  beforeAll(async () => {
    await prisma.activityLog.deleteMany({});
    await prisma.resourceAllocation.deleteMany({});
    await prisma.assignment.deleteMany({});
    await prisma.messageSyncRecord.deleteMany({});
    await prisma.volunteer.deleteMany({});
    await prisma.user.deleteMany({});
  });

  afterAll(async () => {
    await prisma.$disconnect();
  });

  test('registers a new volunteer user and hashes password', async () => {
    const res = await request(app).post('/api/auth/register').send({
      email: 'vol_medic@resqhunt.org',
      password: 'StrongPassword123!',
      fullName: 'Paramedic Jane',
      phone: '+919988776655',
      role: 'VOLUNTEER'
    });

    expect(res.status).toBe(201);
    expect(res.body.token).toBeDefined();
    expect(res.body.user.role).toBe('VOLUNTEER');
    expect(res.body.user.passwordHash).toBeUndefined(); // Sensitive field excluded
  });

  test('authenticates valid login and issues JWT token', async () => {
    const res = await request(app).post('/api/auth/login').send({
      email: 'vol_medic@resqhunt.org',
      password: 'StrongPassword123!'
    });

    expect(res.status).toBe(200);
    expect(res.body.token).toBeDefined();
    expect(res.body.user.email).toBe('vol_medic@resqhunt.org');
  });

  test('rejects login with incorrect password', async () => {
    const res = await request(app).post('/api/auth/login').send({
      email: 'vol_medic@resqhunt.org',
      password: 'WrongPassword'
    });

    expect(res.status).toBe(401);
    expect(res.body.error).toBe('INVALID_CREDENTIALS');
  });
});
