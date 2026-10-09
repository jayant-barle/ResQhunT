import { Request, Response } from 'express';
import { prisma } from '../config/db';
import { ResourceCategory } from '../types';

export const getResources = async (req: Request, res: Response): Promise<void> => {
  try {
    const { category, isVerified } = req.query;

    const where: any = {};
    if (category) where.category = category as string;
    if (isVerified !== undefined) where.isVerified = isVerified === 'true';

    const resources = await prisma.reliefResource.findMany({
      where,
      include: {
        allocations: {
          include: {
            incident: {
              select: { id: true, requestId: true, category: true, description: true }
            },
            allocatedByUser: {
              select: { fullName: true }
            }
          },
          orderBy: { timestamp: 'desc' }
        }
      },
      orderBy: { name: 'asc' }
    });

    res.json({ resources });
  } catch (error: any) {
    res.status(500).json({ error: 'FETCH_RESOURCES_FAILED', message: error.message });
  }
};

export const createOrUpdateResource = async (req: Request, res: Response): Promise<void> => {
  try {
    const { id, name, category, totalQuantity, availableQuantity, unit, location, isVerified } = req.body;

    if (!name || !category || totalQuantity === undefined || !unit || !location) {
      res.status(400).json({
        error: 'VALIDATION_ERROR',
        message: 'name, category, totalQuantity, unit, and location are required.'
      });
      return;
    }

    const validCategories: ResourceCategory[] = ['FOOD', 'WATER', 'MEDICAL', 'SHELTER', 'OTHER'];
    if (!validCategories.includes(category)) {
      res.status(400).json({
        error: 'INVALID_CATEGORY',
        message: `category must be one of: ${validCategories.join(', ')}`
      });
      return;
    }

    const total = parseInt(totalQuantity, 10);
    const available = availableQuantity !== undefined ? parseInt(availableQuantity, 10) : total;

    if (total < 0 || available < 0 || available > total) {
      res.status(400).json({
        error: 'INVALID_QUANTITY',
        message: 'Quantities cannot be negative, and available stock cannot exceed total stock.'
      });
      return;
    }

    let resource;
    if (id) {
      resource = await prisma.reliefResource.update({
        where: { id },
        data: {
          name,
          category,
          totalQuantity: total,
          availableQuantity: available,
          unit,
          location,
          isVerified: isVerified !== undefined ? Boolean(isVerified) : true
        }
      });
    } else {
      resource = await prisma.reliefResource.create({
        data: {
          name,
          category,
          totalQuantity: total,
          availableQuantity: available,
          unit,
          location,
          isVerified: isVerified !== undefined ? Boolean(isVerified) : true
        }
      });
    }

    await prisma.activityLog.create({
      data: {
        actionType: id ? 'RESOURCE_UPDATED' : 'RESOURCE_CREATED',
        entityType: 'RESOURCE',
        entityId: resource.id,
        details: `Resource ${name} (${category}) stock set to ${available}/${total} ${unit}. Verified: ${resource.isVerified}`,
        performedByUserId: req.user?.id || null
      }
    });

    res.json({ success: true, resource });
  } catch (error: any) {
    res.status(500).json({ error: 'SAVE_RESOURCE_FAILED', message: error.message });
  }
};

export const allocateResource = async (req: Request, res: Response): Promise<void> => {
  try {
    const { resourceId, incidentId, quantity, notes } = req.body;

    if (!resourceId || !incidentId || quantity === undefined) {
      res.status(400).json({
        error: 'VALIDATION_ERROR',
        message: 'resourceId, incidentId, and quantity are required.'
      });
      return;
    }

    const qtyToAllocate = parseInt(quantity, 10);
    if (isNaN(qtyToAllocate) || qtyToAllocate <= 0) {
      res.status(400).json({
        error: 'INVALID_QUANTITY',
        message: 'Quantity must be a positive integer.'
      });
      return;
    }

    const incident = await prisma.emergencyRequest.findFirst({
      where: { OR: [{ id: incidentId }, { requestId: incidentId }] }
    });
    if (!incident) {
      res.status(404).json({ error: 'NOT_FOUND', message: 'Incident not found.' });
      return;
    }

    // Execute atomic transaction with strict inventory balance verification
    const result = await prisma.$transaction(async (tx) => {
      const resource = await tx.reliefResource.findUnique({
        where: { id: resourceId }
      });

      if (!resource) {
        throw new Error('RESOURCE_NOT_FOUND');
      }

      if (resource.availableQuantity < qtyToAllocate) {
        const err: any = new Error(
          `Insufficient stock. Available: ${resource.availableQuantity} ${resource.unit}, Requested: ${qtyToAllocate} ${resource.unit}.`
        );
        err.code = 'INSUFFICIENT_STOCK';
        err.available = resource.availableQuantity;
        throw err;
      }

      // Decrement stock safely
      const updatedResource = await tx.reliefResource.update({
        where: { id: resourceId },
        data: {
          availableQuantity: {
            decrement: qtyToAllocate
          }
        }
      });

      // Record allocation record
      const allocation = await tx.resourceAllocation.create({
        data: {
          resourceId: resource.id,
          incidentId: incident.id,
          quantity: qtyToAllocate,
          allocatedByUserId: req.user?.id || incident.id,
          notes: notes || null
        }
      });

      // Log transaction
      await tx.activityLog.create({
        data: {
          actionType: 'RESOURCE_ALLOCATED',
          entityType: 'RESOURCE',
          entityId: resource.id,
          details: `Allocated ${qtyToAllocate} ${resource.unit} of ${resource.name} to incident ${incident.requestId}. Remaining: ${updatedResource.availableQuantity}`,
          performedByUserId: req.user?.id || null
        }
      });

      return { allocation, remainingQuantity: updatedResource.availableQuantity };
    });

    res.json({
      success: true,
      allocationId: result.allocation.id,
      remainingQuantity: result.remainingQuantity
    });
  } catch (error: any) {
    if (error.code === 'INSUFFICIENT_STOCK') {
      res.status(400).json({
        error: 'INSUFFICIENT_STOCK',
        message: error.message,
        available: error.available
      });
      return;
    }
    if (error.message === 'RESOURCE_NOT_FOUND') {
      res.status(404).json({ error: 'NOT_FOUND', message: 'Resource not found.' });
      return;
    }
    res.status(500).json({ error: 'ALLOCATION_FAILED', message: error.message });
  }
};
