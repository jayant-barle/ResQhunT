import request from 'supertest';
import { createApp } from '../app';
import { prisma } from '../config/db';

const app = createApp();

describe('Idempotent SOS Sync Integration Tests', () => {
  beforeAll(async () => {
    await prisma.activityLog.deleteMany({});
    await prisma.messageSyncRecord.deleteMany({});
    await prisma.emergencyRequest.deleteMany({});
  });

  afterAll(async () => {
    await prisma.$disconnect();
  });

  test('successfully ingests a valid mesh envelope and computes priority', async () => {
    const payload = {
      envelopes: [
        {
          messageId: 'msg_test_001',
          requestId: 'sos_req_test_001',
          originDeviceId: 'device_phone_a',
          messageType: 'EMERGENCY_SOS',
          protocolVersion: 1,
          createdAt: Date.now(),
          expiresAt: Date.now() + 86400000,
          hopCount: 1,
          maxHops: 5,
          payload: {
            category: 'MEDICAL',
            severity: 'CRITICAL',
            affectedCount: 2,
            description: 'Severe injury requiring immediate medical triage',
            latitude: 28.6139,
            longitude: 77.2090
          },
          integrity: {
            checksum: 'mock_checksum_hash_123',
            algorithm: 'SHA-256'
          }
        }
      ]
    };

    const res = await request(app).post('/api/sos/sync').send(payload);

    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
    expect(res.body.processed).toBe(1);
    expect(res.body.duplicates).toBe(0);

    const saved = await prisma.emergencyRequest.findUnique({
      where: { requestId: 'sos_req_test_001' }
    });
    expect(saved).not.toBeNull();
    expect(saved?.deliveryState).toBe('SERVER_RECEIVED');
    expect(saved?.priorityCategory).toBe('CRITICAL');
  });

  test('suppresses duplicate message envelopes with identical messageId', async () => {
    const duplicatePayload = {
      envelopes: [
        {
          messageId: 'msg_test_001', // Identical messageId from previous test
          requestId: 'sos_req_test_001',
          originDeviceId: 'device_phone_a',
          messageType: 'EMERGENCY_SOS',
          protocolVersion: 1,
          createdAt: Date.now(),
          expiresAt: Date.now() + 86400000,
          hopCount: 2,
          maxHops: 5,
          payload: {
            category: 'MEDICAL',
            severity: 'CRITICAL',
            affectedCount: 2,
            description: 'Duplicate transmission'
          },
          integrity: {
            checksum: 'mock_checksum_hash_123',
            algorithm: 'SHA-256'
          }
        }
      ]
    };

    const res = await request(app).post('/api/sos/sync').send(duplicatePayload);

    expect(res.status).toBe(200);
    expect(res.body.duplicates).toBe(1);
    expect(res.body.processed).toBe(0);

    // Verify only 1 record exists in database
    const records = await prisma.emergencyRequest.findMany({
      where: { requestId: 'sos_req_test_001' }
    });
    expect(records.length).toBe(1);
  });
});
