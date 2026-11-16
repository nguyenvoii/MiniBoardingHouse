// Kiểu dữ liệu khớp với DTO của backend (xem README mục 7).
export interface Dashboard {
  propertyId: number | null; asOf: string; totalRooms: number; rentedRooms: number; vacantRooms: number; otherRooms: number;
  occupancyPercent: number; revenueThisMonth: number; billedThisMonth: number; expenseThisMonth: number;
  outstandingDebt: number; unpaidInvoices: number; overdueInvoices: number; expiringSoonContracts: number; contractsEndingIn30Days: number;
}

export interface Period {
  billingPeriodId: number; propertyId: number; monthYear: string; status: string; creationDate: string; roomCount: number; totalAmount: number;
}
export interface EligibleContract { contractId: number; roomId: number; roomCode: string; tenantId: number; tenantName: string; contractStatus: string }
export interface Issue { roomCode: string | null; message: string }
export interface FeeItem { detailId: number; itemName: string; quantity: number; unitPrice: number; subtotal: number }
export interface Fee {
  calculatedFeeId: number; roomId: number; roomCode: string; contractId: number; tenantName: string;
  rentAmount: number; electricAmount: number; waterAmount: number; serviceAmount: number; surchargeAmount: number;
  discountAmount: number; latePenaltyAmount: number; totalCalculatedAmount: number; items: FeeItem[];
}
export interface Calculation { period: Period; fees: Fee[]; warnings: Issue[] }
export interface Anomaly { calculatedFeeId: number; roomCode: string; total: number; average: number }

export interface Invoice {
  invoiceId: number; invoiceCode: string; calculatedFeeId: number; tenantId: number; tenantName: string; roomId: number; roomCode: string;
  monthYear: string; issueDate: string; dueDate: string; totalAmount: number; paidAmount: number; remainingAmount: number;
  paymentStatus: string; shareLink: string | null; pdfUrl: string | null;
}
export interface InvoiceDetail {
  invoice: Invoice; rentAmount: number; electricAmount: number; waterAmount: number; serviceAmount: number;
  surchargeAmount: number; discountAmount: number; latePenaltyAmount: number;
  items: Array<{ itemName: string; quantity: number; unitPrice: number; subtotal: number }>;
}
export interface MonthSummary { monthYear: string; totalAmount: number; paidAmount: number; remainingAmount: number; rooms: Array<{ roomId: number; roomCode: string; invoiceCode: string; totalAmount: number; paidAmount: number; remainingAmount: number; paymentStatus: string }> }

export interface Payment {
  transactionId: number; invoiceId: number; invoiceCode: string; method: string; transactionCode: string | null; amountPaid: number;
  paymentDate: string; receivedByUserId: number | null; receivedByName: string | null; status: string;
}
export interface QrInfo { payment: Payment; bankName: string; accountNumber: string; accountHolder: string; qrImageUrl: string | null; transferContent: string }

export interface Contract {
  contractId: number; contractCode: string; roomId: number; roomCode: string; tenantId: number; tenantName: string; templateId: number | null;
  startDate: string; endDate: string; monthlyRentPrice: number; depositAmount: number; paymentCycleMonths: number; paymentDueDay: number;
  lateFeePerDay: number | null; electricTerms: string | null; waterTerms: string | null; depositRefundConditions: string | null;
  digitalSignatureUrl: string | null; pdfContractUrl: string | null; status: string; createdAt: string;
}
export interface Adjustment { adjustmentId: number; adjustmentType: string; oldValue: string | null; newValue: string | null; adjustedBy: number | null; adjustedAt: string }
export interface ContractDetail { contract: Contract; adjustments: Adjustment[] }

export interface RevenueReport {
  propertyId: number | null; year: number; months: Array<{ month: number; revenue: number; expense: number; profit: number }>;
  totalRevenue: number; totalExpense: number; totalProfit: number;
}

export interface Staff { userId: number; fullName: string; email: string | null; phoneNumber: string | null; status: string; roles: string[] }
export interface ActivityLog { logId: number; userId: number; userFullName: string; actionType: string; description: string; ipAddress: string | null; createdAt: string }
export interface Notification { userNotificationId: number; title: string; content: string; referenceType: string | null; referenceId: number | null; isRead: boolean; createdAt: string }
