import { Request, Response } from 'express';
import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import { prisma } from '../config/db';
import { config } from '../config';
import { UserRole } from '../types';

export const register = async (req: Request, res: Response): Promise<void> => {
  try {
    const { email, password, fullName, phone, role } = req.body;

    if (!email || !password || !fullName) {
      res.status(400).json({ error: 'VALIDATION_ERROR', message: 'Email, password, and fullName are required.' });
      return;
    }

    const existingUser = await prisma.user.findUnique({ where: { email } });
    if (existingUser) {
      res.status(409).json({ error: 'USER_EXISTS', message: 'An account with this email already exists.' });
      return;
    }

    const salt = await bcrypt.genSalt(10);
    const passwordHash = await bcrypt.hash(password, salt);

    const validRole: UserRole = ['CITIZEN', 'VOLUNTEER', 'COORDINATOR', 'ADMIN'].includes(role)
      ? role
      : 'CITIZEN';

    const user = await prisma.user.create({
      data: {
        email,
        passwordHash,
        fullName,
        phone: phone || null,
        role: validRole
      }
    });

    if (validRole === 'VOLUNTEER') {
      await prisma.volunteer.create({
        data: {
          userId: user.id,
          skills: 'FIRST_AID,GENERAL_SUPPORT',
          phone: phone || null
        }
      });
    }

    const token = jwt.sign(
      { id: user.id, email: user.email, fullName: user.fullName, role: user.role },
      config.jwtSecret,
      { expiresIn: '7d' }
    );

    res.status(201).json({
      token,
      user: {
        id: user.id,
        email: user.email,
        fullName: user.fullName,
        role: user.role,
        phone: user.phone
      }
    });
  } catch (error: any) {
    res.status(500).json({ error: 'REGISTRATION_FAILED', message: error.message });
  }
};

export const login = async (req: Request, res: Response): Promise<void> => {
  try {
    const { email, password } = req.body;

    if (!email || !password) {
      res.status(400).json({ error: 'VALIDATION_ERROR', message: 'Email and password are required.' });
      return;
    }

    const user = await prisma.user.findUnique({ where: { email } });
    if (!user) {
      res.status(401).json({ error: 'INVALID_CREDENTIALS', message: 'Invalid email or password.' });
      return;
    }

    const isMatch = await bcrypt.compare(password, user.passwordHash);
    if (!isMatch) {
      res.status(401).json({ error: 'INVALID_CREDENTIALS', message: 'Invalid email or password.' });
      return;
    }

    const token = jwt.sign(
      { id: user.id, email: user.email, fullName: user.fullName, role: user.role },
      config.jwtSecret,
      { expiresIn: '7d' }
    );

    res.json({
      token,
      user: {
        id: user.id,
        email: user.email,
        fullName: user.fullName,
        role: user.role,
        phone: user.phone
      }
    });
  } catch (error: any) {
    res.status(500).json({ error: 'LOGIN_FAILED', message: error.message });
  }
};

export const getMe = async (req: Request, res: Response): Promise<void> => {
  if (!req.user) {
    res.status(401).json({ error: 'UNAUTHORIZED', message: 'Not logged in.' });
    return;
  }

  const user = await prisma.user.findUnique({
    where: { id: req.user.id },
    select: { id: true, email: true, fullName: true, role: true, phone: true, createdAt: true }
  });

  if (!user) {
    res.status(404).json({ error: 'NOT_FOUND', message: 'User not found.' });
    return;
  }

  res.json({ user });
};
