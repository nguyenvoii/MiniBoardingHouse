import {
  createElement, ArrowRight, Banknote, Bell, BedDouble, Calculator, Calendar, ChartColumn, Check, ChevronLeft,
  ChevronRight, CircleAlert, Clock, CreditCard, Download, Droplets, Eye, FileText, House, Info, KeyRound, Landmark,
  LayoutDashboard, Lock, LogOut, Mail, Menu, Pencil, Phone, Plus, Printer, QrCode, Receipt, RefreshCw, Route, Search,
  Send, Settings, ShieldCheck, TriangleAlert, User, Users, Wallet, X, Zap, FileSignature, History, Building2, Wrench,
} from 'lucide';

const ICONS = {
  arrowRight: ArrowRight, banknote: Banknote, bell: Bell, bed: BedDouble, calc: Calculator, calendar: Calendar,
  chart: ChartColumn, check: Check, left: ChevronLeft, right: ChevronRight, alert: CircleAlert, clock: Clock,
  card: CreditCard, download: Download, water: Droplets, eye: Eye, file: FileText, home: House, info: Info,
  key: KeyRound, bank: Landmark, dash: LayoutDashboard, lock: Lock, logout: LogOut, mail: Mail, menu: Menu,
  edit: Pencil, phone: Phone, plus: Plus, print: Printer, qr: QrCode, receipt: Receipt, refresh: RefreshCw,
  route: Route, search: Search, send: Send, settings: Settings, shield: ShieldCheck, warn: TriangleAlert, user: User,
  users: Users, wallet: Wallet, x: X, zap: Zap, sign: FileSignature, history: History, building: Building2, wrench: Wrench,
} as const;

export type IconName = keyof typeof ICONS;

/** Biểu tượng cùng một bộ nét (lucide), cùng độ dày. */
export function icon(name: IconName, size = 18): SVGElement {
  const svg = createElement(ICONS[name] as never) as unknown as SVGElement;
  svg.setAttribute('width', String(size));
  svg.setAttribute('height', String(size));
  svg.setAttribute('stroke-width', '2');
  svg.setAttribute('aria-hidden', 'true');
  return svg;
}
