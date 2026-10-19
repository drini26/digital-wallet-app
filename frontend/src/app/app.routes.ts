import { Routes } from '@angular/router';
import { authGuard, guestGuard, managerGuard } from './core/http';

/** The pages: login + callback for everyone, wallet pages for customers, admin pages for managers. */
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'wallet' },
  {
    path: 'login', canActivate: [guestGuard], title: 'Sign in',
    loadComponent: () => import('./pages/login.page').then((m) => m.LoginPage),
  },
  {
    path: 'auth/callback', title: 'Signing in',
    loadComponent: () => import('./pages/callback.page').then((m) => m.CallbackPage),
  },
  {
    path: 'wallet', canActivate: [authGuard], title: 'My wallet',
    loadComponent: () => import('./pages/dashboard.page').then((m) => m.DashboardPage),
  },
  {
    path: 'wallet/transfers', canActivate: [authGuard], title: 'Bank transfers',
    loadComponent: () => import('./pages/transfers.page').then((m) => m.TransfersPage),
  },
  {
    path: 'admin', canActivate: [authGuard, managerGuard], title: 'Operations',
    loadComponent: () => import('./pages/admin/admin-dashboard.page').then((m) => m.AdminDashboardPage),
  },
  {
    path: 'admin/transactions', canActivate: [authGuard, managerGuard], title: 'Transactions',
    loadComponent: () => import('./pages/admin/admin-transactions.page').then((m) => m.AdminTransactionsPage),
  },
  {
    path: 'admin/mock-bank', canActivate: [authGuard, managerGuard], title: 'Mock bank',
    loadComponent: () => import('./pages/admin/mock-bank.page').then((m) => m.MockBankPage),
  },
  { path: '**', redirectTo: 'wallet' },
];
