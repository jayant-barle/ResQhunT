import request from 'supertest';
import jwt from 'jsonwebtoken';
import { createApp } from '../app';
import { prisma } from '../config/db';
import { config } from '../config';

const app = createApp();

describe('Resource Inventory & Allocation Tests', () => {
  let coordinatorToken: string;
  let testResourceId: string;
  let testIncidentId: string;

  beforeAll(async () => {
    await prisma.activityLog.deleteMany({});
    await prisma.resourceAllocation.deleteMany({});
    await prisma.reliefResource.deleteMany({});
    await prisma.emergencyRequest.deleteMany({});
    await prisma.user.deleteMany({});

    // Create coordinator user
    const coordinator = await prisma.user.create({
      data: {
        email: 'coord_test@resqhunt.org',
        passwordHash: 'hashed',
        fullName: 'Test Coordinator',
        role: 'COORDINATOR'
      }
    });

    coordinatorToken = jwt.sign(
      { id: coordinator.id, email: coordinator.email, fullName: coordinator.fullName, role: coordinator.role },
      config.jwtSecret
    );

    // Create test emergency incident
    const incident = await prisma.emergencyRequest.create({
      data: {
        requestId: 'sos_resource_test_01',
        originDeviceId: 'dev_test',
        category: 'WATER',
        severity: 'HIGH',
        affectedCount: 5,
        description: 'Need drinking water',
        deliveryState: 'SERVER_RECEIVED'
      }
    });
    testIncidentId = incident.id;

    // Create test relief resource with initial stock = 100
    const resource = await prisma.reliefResource.create({
      data: {
        name: 'Clean Water Cans',
        category: 'WATER',
        totalQuantity: 100,
        availableQuantity: 100,
        unit: 'cans',
        location: 'Depot A',
        isVerified: true
      }
    });
    testResourceId = resource.id;
  });

  afterAll(async () => {
    await prisma.$disconnect();
  });

  test('successfully allocates resource within available stock limits', async () => {
    const res = await request(app)
      .post('/api/resources/allocate')
      .set('Authorization', `Bearer ${coordinatorToken}`)
      .send({
        resourceId: testResourceId,
        incidentId: testIncidentId,
        quantity: 30
      });

    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
    expect(res.body.remainingQuantity).toBe(70);

    const updated = await prisma.reliefResource.findUnique({
      where: { id: testResourceId }
    });
    expect(updated?.availableQuantity).toBe(70);
  });

  test('rejects allocation when requested quantity exceeds available stock', async () => {
    // Current available is 70, requesting 80 should fail
    const res = await request(app)
      .post('/api/resources/allocate')
      .set('Authorization', `Bearer ${coordinatorToken}`)
      .send({
        resourceId: testResourceId,
        incidentId: testIncidentId,
        quantity: 80
      });

    expect(res.status).toBe(400);
    expect(res.body.error).toBe('INSUFFICIENT_STOCK');
    expect(res.body.available).toBe(70);

    // Stock should remain safely unchanged at 70
    const updated = await prisma.reliefResource.findUnique({
      where: { id: testResourceId }
    });
    expect(updated?.availableQuantity).toBe(70);
  });
});
