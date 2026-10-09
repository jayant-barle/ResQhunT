import bcrypt from 'bcryptjs';
import { prisma } from '../config/db';
import { DeterministicPriorityEngine } from '../services/priorityEngine';

export const seedDemoData = async () => {
  console.log('[ResQhunT Seed] Initializing deterministic demo data...');

  // 1. Clean existing records in correct relation order
  await prisma.activityLog.deleteMany({});
  await prisma.resourceAllocation.deleteMany({});
  await prisma.assignment.deleteMany({});
  await prisma.messageSyncRecord.deleteMany({});
  await prisma.reliefResource.deleteMany({});
  await prisma.emergencyRequest.deleteMany({});
  await prisma.volunteer.deleteMany({});
  await prisma.user.deleteMany({});

  const salt = await bcrypt.genSalt(10);
  const passwordHash = await bcrypt.hash('Password123!', salt);

  // 2. Create Users
  const coordinator = await prisma.user.create({
    data: {
      email: 'coordinator@resqhunt.org',
      passwordHash,
      fullName: 'Chief Coordinator Rajesh Verma',
      phone: '+919811002233',
      role: 'COORDINATOR'
    }
  });

  const volunteer1User = await prisma.user.create({
    data: {
      email: 'volunteer1@resqhunt.org',
      passwordHash,
      fullName: 'Aakash Sharma (Paramedic)',
      phone: '+919811004455',
      role: 'VOLUNTEER'
    }
  });

  const volunteer2User = await prisma.user.create({
    data: {
      email: 'volunteer2@resqhunt.org',
      passwordHash,
      fullName: 'Priya Nair (Search & Rescue)',
      phone: '+919811006677',
      role: 'VOLUNTEER'
    }
  });

  const citizenUser = await prisma.user.create({
    data: {
      email: 'citizen@resqhunt.org',
      passwordHash,
      fullName: 'Rohan Mehra',
      phone: '+919811008899',
      role: 'CITIZEN'
    }
  });

  // 3. Create Volunteers
  const vol1 = await prisma.volunteer.create({
    data: {
      userId: volunteer1User.id,
      skills: 'PARAMEDIC,FIRST_AID,TRIAGE',
      isAvailable: true,
      latitude: 28.6152,
      longitude: 77.2098,
      phone: volunteer1User.phone
    }
  });

  const vol2 = await prisma.volunteer.create({
    data: {
      userId: volunteer2User.id,
      skills: 'SEARCH_AND_RESCUE,BOAT_OPERATOR,DRIVING',
      isAvailable: true,
      latitude: 28.6210,
      longitude: 77.2140,
      phone: volunteer2User.phone
    }
  });

  // 4. Create Relief Resources
  const waterRes = await prisma.reliefResource.create({
    data: {
      name: 'Purified Emergency Drinking Water (20L Cans)',
      category: 'WATER',
      totalQuantity: 500,
      availableQuantity: 420,
      unit: 'cans',
      location: 'Central Relief Depot, Connaught Place',
      isVerified: true
    }
  });

  const medicalRes = await prisma.reliefResource.create({
    data: {
      name: 'Advanced Trauma First-Aid Backpacks',
      category: 'MEDICAL',
      totalQuantity: 80,
      availableQuantity: 65,
      unit: 'kits',
      location: 'AIIMS Field Emergency Hub',
      isVerified: true
    }
  });

  const foodRes = await prisma.reliefResource.create({
    data: {
      name: 'High-Calorie Ready-to-Eat Relief Meals',
      category: 'FOOD',
      totalQuantity: 1200,
      availableQuantity: 1100,
      unit: 'packs',
      location: 'Red Cross Community Warehouse',
      isVerified: true
    }
  });

  const shelterRes = await prisma.reliefResource.create({
    data: {
      name: 'All-Weather Disaster Family Tents',
      category: 'SHELTER',
      totalQuantity: 150,
      availableQuantity: 140,
      unit: 'tents',
      location: 'Stadium Relief Encampment',
      isVerified: true
    }
  });

  // 5. Create Clearly Labeled Demo Incidents
  const now = Date.now();

  const eval1 = DeterministicPriorityEngine.evaluate('MEDICAL', 'CRITICAL', 4, now - 45 * 60 * 1000, now);
  const inc1 = await prisma.emergencyRequest.create({
    data: {
      requestId: 'sos_demo_med_01',
      originDeviceId: 'dev_victim_pixel7',
      category: 'MEDICAL',
      severity: 'CRITICAL',
      affectedCount: 4,
      description: '[DEMO] Structural collapse on 2nd floor, 2 victims with head trauma and arterial bleeding',
      latitude: 28.6289,
      longitude: 77.2065,
      locationAccuracy: 8.5,
      locationAddress: 'Market Lane, Gole Market Block C',
      deliveryState: 'COORDINATOR_ACKNOWLEDGED',
      priorityScore: eval1.priorityScore,
      priorityCategory: eval1.priorityCategory,
      syncHopCount: 2,
      createdAt: new Date(now - 45 * 60 * 1000)
    }
  });

  const eval2 = DeterministicPriorityEngine.evaluate('RESCUE', 'CRITICAL', 6, now - 90 * 60 * 1000, now);
  const inc2 = await prisma.emergencyRequest.create({
    data: {
      requestId: 'sos_demo_res_02',
      originDeviceId: 'dev_victim_galaxy_s22',
      category: 'RESCUE',
      severity: 'CRITICAL',
      affectedCount: 6,
      description: '[DEMO] Flash flood surging into basement apartment, family trapped behind locked security gate',
      latitude: 28.6145,
      longitude: 77.2210,
      locationAccuracy: 12.0,
      locationAddress: 'Near Yamuna Flood Basin, Ring Road',
      deliveryState: 'ASSIGNED',
      priorityScore: eval2.priorityScore,
      priorityCategory: eval2.priorityCategory,
      syncHopCount: 3,
      createdAt: new Date(now - 90 * 60 * 1000)
    }
  });

  const eval3 = DeterministicPriorityEngine.evaluate('WATER', 'HIGH', 15, now - 180 * 60 * 1000, now);
  const inc3 = await prisma.emergencyRequest.create({
    data: {
      requestId: 'sos_demo_wat_03',
      originDeviceId: 'dev_volunteer_node_a',
      category: 'WATER',
      severity: 'HIGH',
      affectedCount: 15,
      description: '[DEMO] Community shelter water pipeline ruptured by ground tremor, 15 people without drinking water for 12 hours',
      latitude: 28.6012,
      longitude: 77.2155,
      locationAccuracy: 15.0,
      locationAddress: 'Chanakyapuri Relief Center Shelter #3',
      deliveryState: 'SERVER_RECEIVED',
      priorityScore: eval3.priorityScore,
      priorityCategory: eval3.priorityCategory,
      syncHopCount: 1,
      createdAt: new Date(now - 180 * 60 * 1000)
    }
  });

  // 6. Create Assignments & Allocations for demo realism
  await prisma.assignment.create({
    data: {
      incidentId: inc2.id,
      volunteerId: vol2.id,
      status: 'IN_PROGRESS',
      assignedByUserId: coordinator.id,
      notes: 'Dispatched with inflatable motorized raft'
    }
  });

  await prisma.resourceAllocation.create({
    data: {
      resourceId: medicalRes.id,
      incidentId: inc1.id,
      quantity: 2,
      allocatedByUserId: coordinator.id,
      notes: 'Initial trauma kits dispatched with advance scout'
    }
  });

  // 7. Activity Logs
  await prisma.activityLog.create({
    data: {
      actionType: 'DEMO_INITIALIZED',
      entityType: 'SYSTEM',
      entityId: 'seed_init',
      details: 'Deterministic disaster scenario loaded with 3 incidents, 2 volunteers, and 4 relief resource caches.',
      performedByUserId: coordinator.id
    }
  });

  console.log('[ResQhunT Seed] Demo data seeding complete.');
  return {
    users: 4,
    volunteers: 2,
    incidents: 3,
    resources: 4
  };
};

// Allow direct CLI execution
if (require.main === module) {
  seedDemoData()
    .then(() => process.exit(0))
    .catch((err) => {
      console.error(err);
      process.exit(1);
    });
}
