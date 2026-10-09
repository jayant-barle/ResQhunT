import { Request, Response } from 'express';
import { v4 as uuidv4 } from 'uuid';
import { prisma } from '../config/db';
import { DeterministicPriorityEngine } from '../services/priorityEngine';
import { MeshMessageEnvelope, EmergencyCategory, SeverityLevel } from '../types';

export const syncMeshEnvelopes = async (req: Request, res: Response): Promise<void> => {
  try {
    const { envelopes } = req.body;

    if (!Array.isArray(envelopes) || envelopes.length === 0) {
      res.status(400).json({
        error: 'INVALID_PAYLOAD',
        message: 'Expected an array of mesh envelopes in `envelopes` field.'
      });
      return;
    }

    const results: any[] = [];
    let duplicateCount = 0;
    let processedCount = 0;

    for (const env of envelopes as MeshMessageEnvelope[]) {
      if (!env.messageId || !env.requestId || !env.payload) {
        continue; // Skip malformed envelopes
      }

      // 1. Deduplication check: Has this exact messageId already been synced?
      const existingSync = await prisma.messageSyncRecord.findUnique({
        where: { messageId: env.messageId }
      });

      if (existingSync) {
        duplicateCount++;
        continue;
      }

      // 2. Check if the underlying SOS requestId already exists in the central system
      let emergency = await prisma.emergencyRequest.findUnique({
        where: { requestId: env.requestId }
      });

      if (!emergency) {
        // Calculate priority score using the deterministic engine
        const evaluation = DeterministicPriorityEngine.evaluate(
          env.payload.category as EmergencyCategory,
          env.payload.severity as SeverityLevel,
          env.payload.affectedCount || 1,
          env.createdAt || Date.now()
        );

        emergency = await prisma.emergencyRequest.create({
          data: {
            requestId: env.requestId,
            originDeviceId: env.originDeviceId || 'UNKNOWN_DEVICE',
            category: env.payload.category,
            severity: env.payload.severity,
            affectedCount: env.payload.affectedCount || 1,
            description: env.payload.description || 'Emergency assistance requested',
            latitude: env.payload.latitude ?? null,
            longitude: env.payload.longitude ?? null,
            locationAccuracy: env.payload.locationAccuracy ?? null,
            locationAddress: env.payload.locationAddress ?? null,
            deliveryState: 'SERVER_RECEIVED',
            priorityScore: evaluation.priorityScore,
            priorityCategory: evaluation.priorityCategory,
            syncHopCount: env.hopCount || 1
          }
        });

        // Record activity log
        await prisma.activityLog.create({
          data: {
            actionType: 'SOS_SYNCED',
            entityType: 'INCIDENT',
            entityId: emergency.id,
            details: `Emergency ${emergency.requestId} synced via mesh relay with hop count ${env.hopCount || 1}. Priority: ${evaluation.priorityCategory} (${evaluation.priorityScore})`,
            performedByUserId: req.user?.id || null
          }
        });
      } else {
        // Already recorded incident, update hop count if this path is shorter
        if (env.hopCount && env.hopCount < emergency.syncHopCount) {
          await prisma.emergencyRequest.update({
            where: { id: emergency.id },
            data: { syncHopCount: env.hopCount }
          });
        }
      }

      // 3. Record the unique messageId in MessageSyncRecord
      await prisma.messageSyncRecord.create({
        data: {
          messageId: env.messageId,
          requestId: env.requestId,
          originDeviceId: env.originDeviceId || 'UNKNOWN_DEVICE',
          hopCount: env.hopCount || 1,
          syncedByUserId: req.user?.id || null
        }
      });

      processedCount++;
      results.push({
        requestId: emergency.requestId,
        status: emergency.deliveryState,
        priorityScore: emergency.priorityScore,
        priorityCategory: emergency.priorityCategory,
        receivedAt: new Date().toISOString()
      });
    }

    res.json({
      success: true,
      processed: processedCount,
      duplicates: duplicateCount,
      results
    });
  } catch (error: any) {
    console.error('Error during mesh sync:', error);
    res.status(500).json({ error: 'SYNC_FAILED', message: error.message });
  }
};

export const createOnlineSos = async (req: Request, res: Response): Promise<void> => {
  try {
    const {
      requestId,
      category,
      severity,
      affectedCount,
      description,
      latitude,
      longitude,
      locationAccuracy,
      locationAddress
    } = req.body;

    if (!category || !severity || !description) {
      res.status(400).json({
        error: 'VALIDATION_ERROR',
        message: 'category, severity, and description are required.'
      });
      return;
    }

    const uniqueRequestId = requestId || `sos_${uuidv4()}`;

    // Check idempotency
    const existing = await prisma.emergencyRequest.findUnique({
      where: { requestId: uniqueRequestId }
    });

    if (existing) {
      res.status(200).json({
        message: 'Incident already exists with this requestId (idempotent)',
        incident: existing
      });
      return;
    }

    const evaluation = DeterministicPriorityEngine.evaluate(
      category as EmergencyCategory,
      severity as SeverityLevel,
      affectedCount || 1,
      Date.now()
    );

    const emergency = await prisma.emergencyRequest.create({
      data: {
        requestId: uniqueRequestId,
        originDeviceId: req.user?.id || 'DIRECT_WEB_CLIENT',
        category,
        severity,
        affectedCount: affectedCount || 1,
        description,
        latitude: latitude ?? null,
        longitude: longitude ?? null,
        locationAccuracy: locationAccuracy ?? null,
        locationAddress: locationAddress ?? null,
        deliveryState: 'SERVER_RECEIVED',
        priorityScore: evaluation.priorityScore,
        priorityCategory: evaluation.priorityCategory,
        syncHopCount: 0
      }
    });

    await prisma.activityLog.create({
      data: {
        actionType: 'SOS_CREATED_ONLINE',
        entityType: 'INCIDENT',
        entityId: emergency.id,
        details: `Direct online SOS created. Category: ${category}, Severity: ${severity}. Priority: ${evaluation.priorityCategory}`,
        performedByUserId: req.user?.id || null
      }
    });

    res.status(201).json({
      success: true,
      incident: emergency,
      evaluation
    });
  } catch (error: any) {
    res.status(500).json({ error: 'CREATE_SOS_FAILED', message: error.message });
  }
};
