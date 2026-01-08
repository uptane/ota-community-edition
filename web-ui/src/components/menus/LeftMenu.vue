<template>
  <div class="h-100vh v-divide-right">
    <q-scroll-area class="fit">
      <user-info-div></user-info-div>
      <q-list no-border link separator>
        <template v-for="(menu, index) in menuItems">
          <!-- clickable -->
          <menu-item :key="index" :menu="menu" :mini="showMiniContent"></menu-item>
        </template>
      </q-list>
    </q-scroll-area>
    <div class="absolute-bottom self-end p-1">
      <div class=" text-left">
        <small v-if="$isDevUserPool && !showMiniContent">
          |
          <small class="opacity-70">
            <span>Dev</span>
          </small>
        </small>
        <small v-if="$demoMode && !showMiniContent">
          |
          <small class="text-primary opacity-90">
            <span>Demo</span>
          </small>
        </small>
      </div>

      <div v-if="$isDevUserPool && showMiniContent" style="line-height:1 text-center">
        <small>
          <small class="opacity-50">
            <span>Dev</span>
          </small>
        </small>
      </div>
      <div v-if="$demoMode && showMiniContent" style="line-height:1">
        <small>
          <small class="text-primary opacity-90">
            <span>Demo</span>
          </small>
        </small>
      </div>
    </div>
  </div>
</template>

<script>
import { AuthService } from '../../services/auth.service';
import gtm from '../../services/gtm.service';
import { FIRST_TIME_USER_ATTRIBUTE_KEY } from '../../config';
import { openURL } from 'quasar';
import { mapGetters } from 'vuex';
import Tooltip from '../common/Tooltip.vue';
import UserInfoDiv from '../users/UserInfoDiv.vue';
import MenuItem from './MenuItem.vue';
import { canAccessFeature } from 'src/config/feature-toggle.js';
import { isCommercialUser } from '../../store/users/getters';

export default {
  components: {
    Tooltip,
    UserInfoDiv,
    MenuItem,
  },
  props: {},
  data() {
    return {
      openURL,
      menuItems: [
        {
          route: '/',
          routeName: 'dashboard',
          icon: 'dashboard',
          label: 'Dashboard',
          sublabel: 'Get started',
          isActive: () => {
            return this.$route.name === 'dashboard';
          },
        },
        {
          route: '/devices',
          routeName: 'devices',
          icon: 'developer_board',
          label: 'Devices',
          sublabel: 'List of all devices',
          isActive: () => {
            return this.$route.name === 'devices' || this.$route.name === 'device-detail';
          },
        },
        {
          route: '/fleets',
          routeName: 'fleet-manager',
          icon: 'fas fa-layer-group',
          label: 'Fleet Manager',
          sublabel: 'Manage fleets',
          isActive: () => {
            return this.$route.name === 'fleets' || this.$route.name === 'fleet-detail';
          },
        },
        {
          route: '/packages',
          routeName: 'packages',
          icon: `icon-torizon-package`,
          label: 'Packages',
          sublabel: 'All added packages',
          isActive: () => {
            return this.$route.name === 'packages';
          },
        },

        {
          route: '/remote-access',
          routeName: 'remote-access',
          icon: 'wifi_tethering',
          label: 'Remote Access',
          sublabel: 'View and manage remote access sessions',
          hide: () => {
            return !canAccessFeature('use-remote-access');
          },
          isActive: () => {
            return this.$route.name === 'remote-access';
          },
        },
        {
          route: '/monitoring',
          routeName: 'monitoring',
          icon: 'insert_chart',
          label: 'Monitoring',
          sublabel: 'View important information about your devices and fleets',
          hide: () => {
            return true; // !this.adminMode;//!this.allowedUsersForRoutes.report.find(e=> e === this.user.email);
          },
          isActive: () => {
            return this.$route.name === 'monitoring';
          },
        },
        {
          route: '/report',
          routeName: 'report',
          icon: 'insert_chart',
          label: 'Report',
          sublabel: 'View report',
          hide: () => {
            return !this.adminMode; //!this.allowedUsersForRoutes.report.find(e=> e === this.user.email);
          },
          isActive: () => {
            return this.$route.name === 'report';
          },
        },
        {
          route: '/lockboxes',
          routeName: 'lockboxes',
          icon: 'publish',
          label: 'Lockboxes',
          sublabel: 'Manage offline updates',
          hide: () => {
            return !canAccessFeature('view-offline-update');
          },
          isActive: () => {
            return this.$route.name === 'lockboxes';
          },
        },
        {
          route: '/about',
          routeName: 'about',
          icon: 'info',
          label: 'About',
          sublabel: `Info about this version and release notes.`,
          isActive: () => {
            return this.$route.name === 'about';
          },
        },
        {
          route: '/debug',
          routeName: 'debug',
          icon: 'bug_report',
          label: 'Debug',
          sublabel: 'View app specific debug information.',
          isActive: () => {
            return this.$route.name === 'debug';
          },
          hide: () => {
            return !canAccessFeature('view-debug-page');
          },
        },
      ],
    };
  },
  methods: {
    toggleFirstTime() {
      const update = {
        [FIRST_TIME_USER_ATTRIBUTE_KEY]: !this.firstTimer ? '0' : '1',
      };
      AuthService.updateUserData(update)
        .then((result) => {
          this.firstTimer = !this.firstTimer;
        })
        .catch((err) => {});
    },

    toggleMiniBar() {
      this.miniBar = !this.miniBar;
    },
  },
  created() {},
  computed: {
    ...mapGetters({
      isCommercialUser: 'users/isCommercialUser',
    }),
    showMiniContent() {
      return this.miniBar && this.miniState && this.$q.screen.gt.sm;
    },
    adminMode: {
      get() {
        return this.$store.getters['ui/adminMode'];
      },
      set(v) {
        this.$store.commit('ui/setAdminMode', v);
      },
    },
    leftDrawerOpen: {
      get() {
        return this.$store.getters['ui/isLeftDrawerOpen'];
      },
      set(v) {
        this.$store.commit('ui/setIsLeftDrawerOpen', v);
      },
    },
    activeXmas: {
      get() {
        return this.$store.getters['ui/activeXmas'];
      },
      set(v) {
        return this.$store.commit('ui/setActiveXmas', v);
      },
    },
    allowedUsersForRoutes: {
      get() {
        return this.$store.getters['ui/allowedUsersForRoutes'];
      },
      set(v) {
        return this.$store.commit('ui/setAllowedUsersForRoutes', v);
      },
    },
    miniState: {
      get() {
        if (this.$q.screen.gt.lg) {
          return false;
        }
        return this.$store.getters['ui/isMiniState'];
      },
      set(v) {
        this.$store.commit('ui/setIsMiniState', v);
      },
    },
    miniBar: {
      get() {
        return this.$store.getters['ui/isMiniBar'];
      },
      set(v) {
        this.$store.commit('ui/setIsMiniBar', v);
      },
    },
    firstTimer: {
      get() {
        return this.$store.getters['ui/isFirstTimer'];
      },
      set(v) {
        return this.$store.commit('ui/setIsFirstTimer', v);
      },
    },
  },
  watch: {
    miniBar(n) {
      if (n) {
        this.leftDrawerOpen = true;
        this.miniState = true;
      } else {
        this.leftDrawerOpen = false;
      }
    },
  },
};
</script>
