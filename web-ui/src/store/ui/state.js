import { ALLOWED_USERS_FOR_ROUTES } from '../../config';

export default {
  isDashboardPage: false,
  isDarkTheme: true,
  currentPageTitle: null,
  fleetInProcess: {},
  loadingFleets: false,
  loadingDevices: false,
  loadingPackages: false,
  loadingUpdates: false,
  deviceInProcess: null,
  deviceDeleteInProgress: null,
  fleetDeleteInProgress: null,
  devicesWithUpdateInProgress: {},
  isLeftDrawerOpen: true,
  isMiniBar: true,
  isMiniState: true,
  wideDrawer: false,
  isFirstTimer: false,
  user: {},
  userSettings: {},
  surveyShown: false,
  activeXmas: false,
  allowedUsersForRoutes: ALLOWED_USERS_FOR_ROUTES,
  adminMode: false,
  launchedAt: 0,
  justConfirmedEmail: false,
  loaded: false,
  almostLoaded: false,
  releaseNotes: {},
  releaseNotesMd: '',
  uiLoaderText: 'Checking server for your namespace information',
  uiLoaderType: 'progress-bar',
  supportLevelMap: {
    supported: {
      label: 'supported',
      description: "Expected to work - bugs should be reported, FAE's  and engineers will help debug issues on the community site",
      icon: 'check_circle',
      color: 'positive',
    },
    early_access: {
      label: 'early_access',
      description: "It might work, existing customers or teams are already working with it, but it's still in an early stage. Only FAE's or engineers with free time or personal interest will help on the community site",
      icon: 'warning',
      color: 'warning',
    },
    experimental: {
      label: 'experimental',
      description: "It might work - it is being worked on in house but it's in a very early stage. Only FAE's or engineers with free time or personal interest will help on the community site",
      icon: 'report',
      color: 'negative',
    },
    legacy: {
      label: 'legacy',
      description: "No longer officially supported, only FAE's or engineers with free time or personal interest will help on the community site",
      icon: 'report',
      color: 'negative',
    },
    not_supported: {
      label: 'not_supported',
      description: 'You are 100% on your own',
      icon: 'report',
      color: 'negative',
    },
  },
  tabs: null,
  currentTab: '',
  wsStatus: {
    failureCount: 0,
    connected: false,
  },
  showOnboardingWalkthrough: false,
  parentDivSize: {}, // {width: 0, height: 0}
  rightMenuSize: {}, // {width: 0, height: 0}
  leftMenuSize: {}, // {width: 0, height: 0}
  devicesDivSize: {}, // {width: 0, height: 0}

  currentPageDimensions: {},
};
