import React, { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { api } from '../api/client';
import { Zap, ShieldCheck, User, Lock, Mail, AlertCircle, ArrowRight, CheckCircle2 } from 'lucide-react';

export default function AuthPage() {
  const { login } = useAuth();
  const toast = useToast();
  const [isLogin, setIsLogin] = useState(true);
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState('DRIVER');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [successMsg, setSuccessMsg] = useState('');

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSuccessMsg('');
    setLoading(true);

    try {
      if (isLogin) {
        const response = await api.login(email, password);
        toast.success(`Welcome back, ${response.user?.name || 'Driver'}!`);
        login(response.accessToken, response.user);
      } else {
        await api.register(name, email, password, role);
        toast.success('Account created! Logging you in...');
        setSuccessMsg('Account registered successfully! Logging you in...');
        const loginRes = await api.login(email, password);
        login(loginRes.accessToken, loginRes.user);
      }
    } catch (err) {
      const msg = err.message || 'Authentication failed. Please verify your credentials.';
      setError(msg);
      toast.error(msg);
    } finally {
      setLoading(false);
    }
  };

  const handleDemoFill = (demoRole) => {
    if (demoRole === 'ADMIN') {
      setEmail('admin@voltgrid.com');
      setPassword('Admin12345!');
      setIsLogin(true);
    } else if (demoRole === 'OWNER') {
      setEmail('owner.demo@voltgrid.com');
      setPassword('Password123!');
      setName('Demo Station Owner');
      setRole('OWNER');
    } else {
      setEmail('driver.demo@voltgrid.com');
      setPassword('Password123!');
      setName('Demo EV Driver');
      setRole('DRIVER');
    }
  };

  return (
    <div className="min-h-[calc(100vh-3.5rem)] flex items-center justify-center p-4 bg-[#0B1120]">
      <div className="w-full max-w-md">
        {/* Brand Card Header */}
        <div className="text-center mb-6">
          <div className="inline-flex items-center justify-center w-12 h-12 rounded-xl bg-blue-600 text-white shadow-lg shadow-blue-900/30 mb-3">
            <Zap className="w-6 h-6 fill-white" />
          </div>
          <h1 className="text-2xl font-bold text-slate-100 tracking-tight">VoltGrid Platform</h1>
          <p className="text-xs text-slate-400 mt-1">
            Electric Vehicle Charging & Highway Route Optimization
          </p>
        </div>

        {/* Auth Box */}
        <div className="bg-[#111827] border border-slate-800 rounded-2xl shadow-2xl p-6 sm:p-8 text-slate-100">
          {/* Tabs */}
          <div className="flex rounded-xl bg-[#0F172A] p-1 mb-6 border border-slate-800">
            <button
              type="button"
              onClick={() => { setIsLogin(true); setError(''); setSuccessMsg(''); }}
              className={`flex-1 py-2 text-xs font-semibold rounded-lg transition-all cursor-pointer ${
                isLogin
                  ? 'bg-[#1E293B] text-slate-100 border border-slate-700/80 shadow-xs'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              Sign In
            </button>
            <button
              type="button"
              onClick={() => { setIsLogin(false); setError(''); setSuccessMsg(''); }}
              className={`flex-1 py-2 text-xs font-semibold rounded-lg transition-all cursor-pointer ${
                !isLogin
                  ? 'bg-[#1E293B] text-slate-100 border border-slate-700/80 shadow-xs'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              Create Account
            </button>
          </div>

          {error && (
            <div className="mb-4 p-3 rounded-xl bg-rose-950/40 border border-rose-800/60 text-rose-300 text-xs flex items-center gap-2">
              <AlertCircle className="w-4 h-4 flex-shrink-0 text-rose-400" />
              <span>{error}</span>
            </div>
          )}

          {successMsg && (
            <div className="mb-4 p-3 rounded-xl bg-emerald-950/40 border border-emerald-800/60 text-emerald-300 text-xs flex items-center gap-2">
              <CheckCircle2 className="w-4 h-4 flex-shrink-0 text-emerald-400" />
              <span>{successMsg}</span>
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-4">
            {!isLogin && (
              <>
                <div>
                  <label className="block text-xs font-semibold text-slate-300 mb-1.5">Full Name</label>
                  <div className="relative">
                    <User className="w-4 h-4 text-slate-500 absolute left-3 top-3" />
                    <input
                      type="text"
                      required
                      value={name}
                      onChange={(e) => setName(e.target.value)}
                      placeholder="e.g. John Doe"
                      className="w-full pl-9 pr-3 py-2.5 bg-[#1E293B] border border-slate-700 rounded-xl text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500 transition-all"
                    />
                  </div>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-300 mb-1.5">Account Role</label>
                  <div className="grid grid-cols-2 gap-3">
                    <button
                      type="button"
                      onClick={() => setRole('DRIVER')}
                      className={`p-3 rounded-xl border text-left transition-all cursor-pointer ${
                        role === 'DRIVER'
                          ? 'border-blue-500 bg-blue-600/20 text-slate-100 shadow-xs'
                          : 'border-slate-700 bg-[#1E293B] text-slate-400 hover:border-slate-600 hover:text-slate-200'
                      }`}
                    >
                      <div className="font-semibold text-xs flex items-center gap-1.5">
                        <Zap className="w-3.5 h-3.5 text-blue-400" />
                        EV Driver
                      </div>
                      <div className="text-[10px] text-slate-400 mt-1">Book slots & plan routes</div>
                    </button>

                    <button
                      type="button"
                      onClick={() => setRole('OWNER')}
                      className={`p-3 rounded-xl border text-left transition-all cursor-pointer ${
                        role === 'OWNER'
                          ? 'border-blue-500 bg-blue-600/20 text-slate-100 shadow-xs'
                          : 'border-slate-700 bg-[#1E293B] text-slate-400 hover:border-slate-600 hover:text-slate-200'
                      }`}
                    >
                      <div className="font-semibold text-xs flex items-center gap-1.5">
                        <ShieldCheck className="w-3.5 h-3.5 text-blue-400" />
                        Station Host
                      </div>
                      <div className="text-[10px] text-slate-400 mt-1">Host stations & bays</div>
                    </button>
                  </div>
                </div>
              </>
            )}

            <div>
              <label className="block text-xs font-semibold text-slate-300 mb-1.5">Email Address</label>
              <div className="relative">
                <Mail className="w-4 h-4 text-slate-500 absolute left-3 top-3" />
                <input
                  type="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="name@example.com"
                  className="w-full pl-9 pr-3 py-2.5 bg-[#1E293B] border border-slate-700 rounded-xl text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500 transition-all"
                />
              </div>
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-300 mb-1.5">Password</label>
              <div className="relative">
                <Lock className="w-4 h-4 text-slate-500 absolute left-3 top-3" />
                <input
                  type="password"
                  required
                  minLength={8}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="Minimum 8 characters"
                  className="w-full pl-9 pr-3 py-2.5 bg-[#1E293B] border border-slate-700 rounded-xl text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500 transition-all"
                />
              </div>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full mt-2 py-2.5 px-4 rounded-xl font-semibold text-sm bg-blue-600 hover:bg-blue-500 text-white transition-all shadow-md shadow-blue-900/30 disabled:opacity-40 disabled:cursor-not-allowed flex items-center justify-center gap-2 cursor-pointer"
            >
              {loading ? (
                <div className="w-5 h-5 border-2 border-white border-t-transparent rounded-full animate-spin" />
              ) : (
                <>
                  <span>{isLogin ? 'Sign In' : 'Create Account'}</span>
                  <ArrowRight className="w-4 h-4" />
                </>
              )}
            </button>
          </form>

          {/* Quick Demo Pre-fill Links */}
          <div className="mt-6 pt-4 border-t border-slate-800 text-center">
            <span className="text-[11px] text-slate-400 block mb-2 font-medium">Quick Demo Profiles:</span>
            <div className="flex flex-wrap justify-center gap-2">
              <button
                type="button"
                onClick={() => handleDemoFill('DRIVER')}
                className="text-[11px] font-medium px-2.5 py-1 rounded-lg bg-[#1E293B] hover:bg-blue-600/20 hover:text-blue-300 text-slate-300 border border-slate-700 transition-colors cursor-pointer"
              >
                EV Driver Demo
              </button>
              <button
                type="button"
                onClick={() => handleDemoFill('OWNER')}
                className="text-[11px] font-medium px-2.5 py-1 rounded-lg bg-[#1E293B] hover:bg-blue-600/20 hover:text-blue-300 text-slate-300 border border-slate-700 transition-colors cursor-pointer"
              >
                Station Host Demo
              </button>
              <button
                type="button"
                onClick={() => handleDemoFill('ADMIN')}
                className="text-[11px] font-medium px-2.5 py-1 rounded-lg bg-[#1E293B] hover:bg-purple-600/20 hover:text-purple-300 text-slate-300 border border-slate-700 transition-colors cursor-pointer"
              >
                Admin Demo
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
