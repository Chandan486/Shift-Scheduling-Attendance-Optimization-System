/** Mirrors notification-service NotificationResponse (isRead / notificationType are the wire names). */
export interface Notification {
  id: number;
  userId: number;
  title: string;
  message: string;
  notificationType: string;
  referenceId: number | null;
  isRead: boolean;
  createdAt: string;
}

export interface NotificationCount {
  unreadCount: number;
}
