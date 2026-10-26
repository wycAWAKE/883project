import { Bell, Calendar, ChatDotRound, Document, Reading, Tools } from '@element-plus/icons-vue'

export const moduleGroups = ['Learning', 'Facilities', 'Administration']

// Shared by the portal, sidebar and router. Availability is not a permission rule.
export const campusModules = [
  {
    id: 'M1', name: 'FAQ Assistance', group: 'Learning', path: '/faq', icon: ChatDotRound,
    description: 'Find answers to campus questions, with sources and guidance on where to get help.',
    status: 'Under development', component: () => import('../views/modules/FaqModule.vue'),
  },
  {
    id: 'M2', name: 'Course Recommendations', group: 'Learning', path: '/courses', icon: Reading,
    description: 'Explore courses that fit your interests, goals and completed studies.',
    status: 'Under development', component: () => import('../views/modules/CourseModule.vue'),
  },
  {
    id: 'M3', name: 'Room Booking', group: 'Facilities', path: '/bookings', icon: Calendar,
    description: 'Check room availability and manage your campus room bookings.',
    status: 'Under development', component: () => import('../views/modules/BookingModule.vue'),
  },
  {
    id: 'M4', name: 'Fault Reporting', group: 'Facilities', path: '/repairs', icon: Tools,
    description: 'Report a facilities issue and follow its progress through to resolution.',
    status: 'Under development', component: () => import('../views/modules/RepairModule.vue'),
  },
  {
    id: 'M5', name: 'Document Applications', group: 'Administration', path: '/applications', icon: Document,
    description: 'Submit campus document applications and supporting materials for review.',
    status: 'Under development', component: () => import('../views/modules/ApplicationModule.vue'),
  },
  {
    id: 'M6', name: 'Progress Notifications', group: 'Administration', path: '/notifications', icon: Bell,
    description: 'Follow application updates, review processing history and read your notifications.',
    status: 'Under development', component: () => import('../views/modules/NotificationModule.vue'),
  },
]
