<template>
  <q-layout
    view="lHh Lpr lFf"
    class="z-1  styled-scrollbar"
    :class="{
      'dark-theme': darkTheme,
      'light-theme': !darkTheme,
      'is-dashboard': isDashboardPage,
      'left-menu-open': drawerState,
      'left-menu-closed': !drawerState,
      'mini-bar': $q.screen.gt.sm && isMini && miniState,
    }"
    ref="myLayout"
  >
    <q-header v-if="!isDashboardPage || (isDashboardPage && !firstTimer) || $q.screen.lt.md" class="animated slideInDown">
      <q-toolbar class="justify-center">
        <!-- <img src="~assets/svg/dash-bg.svg"  style="height: 100%; width:100%; position: absolute"/> -->
        <q-btn class="menu-btn" flat dense round v-if="this.$q.screen.lt.xl" @click="toggleLeftMenu" aria-label="Menu">
          <q-icon name="menu" />
        </q-btn>

        <q-toolbar-title>
          <div class="flex items-center">
            <q-icon name="cloud_upload" size="2rem" class="q-mr-sm text-white" />
            <span class="text-h6 text-white">OTA Community Edition</span>
            <div class="text-thin">
              <span class="ml-1 self-center gt-sm" v-if="pageTitle">&mdash; {{ pageTitle }}</span>
            </div>
          </div>
        </q-toolbar-title>
        <q-toggle color="white" class="toggle-night-mode text-white mr-2" v-model="darkTheme">
          <span class="gt-sm text-uppercase">Dark mode</span>
        </q-toggle>
        <q-btn flat v-if="isDashboardPage" color="white" class=" mr-1" @click="toggleFirstTimer">
          <span class="gt-sm">{{ firstTimer ? 'Hide Intro' : 'Show Intro' }}</span>
        </q-btn>
        <!-- OrgSelector removed for CE - single namespace mode -->
      </q-toolbar>
      <div
        v-if="showReleaseNotes"
        class="row  animated fadeIn z-10 p-1 pr-2 "
        :class="{
          'bg-grey-4 text-grey-10 ': !$q.dark.isActive,
          'bg-dark-bg text-dark-bg': $q.dark.isActive,
        }"
      >
        <div style="position: relative;" class="col text-center">
          <div class="text-1">Version {{ releaseNotesData.latestVersion }} released!</div>
          <q-btn dense type="a" size="md" class="p-0 m-0 q-pa-xs" color="primary" flat to="about" no-caps>View release notes</q-btn>
        </div>
        <div>
          <q-btn flat @click="hideReleaseNotes" icon="close">Dismiss</q-btn>
        </div>
      </div>
      <div
        v-if="showMessageOfTheDay"
        class="row  animated fadeIn z-10 p-1 pr-2 "
        :class="{
          'bg-grey-4 text-grey-10 ': !$q.dark.isActive,
          'bg-dark-bg text-dark-bg': $q.dark.isActive,
        }"
      >
        <strong>Message of the day</strong>
        <br />Something new is happening with OTA
        <q-btn dense flat size="sm" class="float-right m-0" icon="close"></q-btn>
        <div class="text-center">
          <q-btn no-caps color="primary" flat>
            <small>Click here to permanently hide this message</small>
          </q-btn>
        </div>
      </div>
      <info-banner-group>
        <info-banner v-if="showWebsocketStatus && !wsStatusAcknowledged" @dismiss="dismissWebSocketStatus">
          <template v-slot:icon>
            <q-icon name="info" :color="wsConnected ? 'info' : 'negative'" size="sm" />
          </template>
          <template v-slot:content>
            <div class="text-left">
              <div class="text-1">
                {{ websocketStatusMessage }}
              </div>
            </div>
          </template>
        </info-banner>
        <info-banner v-for="(banner, index) in alertBanners" :key="'banner_' + index" :singleLine="!banner.wrapContent" :additional-classes="banner.cssClasses" :persistent="banner.persistent" @dismiss="dismissAlertBanner(banner)">
          <template v-slot:icon>
            <q-icon :name="banner.icon" :color="banner.iconColor" size="sm" />
          </template>
          <template v-slot:content>
            <div class="text-left text-1 text-wrap" v-html="banner.message"></div>
          </template>
        </info-banner>
      </info-banner-group>
      <div
        v-if="showMaintenanceMessage"
        class="row  animated fadeIn z-10 p-1 pr-2 "
        :class="{
          'bg-grey-4 text-grey-10 ': !$q.dark.isActive,
          'bg-dark-bg text-dark-bg': $q.dark.isActive,
        }"
      >
        <q-btn dense flat size="sm" class="float-right m-0" icon="close" @click="hideMaintenanceMessage = true"></q-btn>
        <div class="p-0">
          <div class="w-90 mxw-20em pt-1 m-auto">
            <q-img position="50% 50%" color="warning" class="m-0" src="/statics/svg/cones.svg"></q-img>
          </div>
          <div class="p-2">
            {{ maintenanceData.message ? maintenanceData.message : 'OTA Server is currently undergoing scheduled maintenance.' }}
          </div>
        </div>
        <div class="text-center">
          <q-btn no-caps color="primary" flat @click="hideMaintenanceMessage = true">
            <small>Click here to hide this message</small>
          </q-btn>
        </div>
      </div>
      <div
        :class="{
          'bg-grey-3 text-grey-10 ': !$q.dark.isActive,
          'bg-darker-bg text-dark-bg': $q.dark.isActive,
        }"
      >
        <q-tabs v-model="currentTab" dense active-color="primary" indicator-color="primary" inline-label align="justify" animated>
          <template v-for="tab in uiTabs">
            <q-tab :key="tab.name" :name="tab.name" :icon="tab.icon" :label="tab.label" content-class="q-py-md" ripple v-if="!(tab.hide && tab.hide()) || !tab.hide">
              <div class="row">
                <div class="q-pa-md col">
                  <slot></slot>
                </div>
                <div v-if="tab.isBeta" class="col-auto">
                  <beta-badge />
                </div>
              </div>
            </q-tab>
          </template>
        </q-tabs>
        <q-separator class="opacity-30" />
      </div>
    </q-header>

    <q-drawer
      v-model="drawerState"
      :mini="miniState"
      show-if-above
      no-swipe-open
      :width="$q.screen.lt.xl ? 300 : parsedMenuDivX"
      :class="{
        'left-menu-div': $q.screen.gt.sm && !miniState,
      }"
    >
      <left-menu></left-menu>
      <resize-handle right v-if="$q.screen.gt.sm && !miniState"></resize-handle>
      <q-resize-observer @resize="onLeftMenuResize" />
    </q-drawer>

    <q-page-container>
      <div class="absolute-top-left " v-if="isDashboardPage" :style="{ zIndex: 5, top: '.5rem', left: miniState && drawerState ? '4.5rem' : drawerState ? '20rem' : $q.screen.gt.sm ? '4.5rem' : '1rem' }">
        <q-btn class="absolute-top-left menu-btn" color="white" flat dense round @click="toggleLeftMenu" aria-label="Menu">
          <q-icon name="menu" />
        </q-btn>
        <div class="fixed-top-right">
          <q-toggle color="white" class="toggle-night-mode text-white mt-1 mr-1" v-model="darkTheme">
            <span class="gt-sm text-uppercase">Dark mode</span>
          </q-toggle>
          <q-btn v-if="isDashboardPage" flat icon="close" color="white" class="mt-1 mr-1" @click="toggleFirstTimer">
            <span class="gt-sm">{{ firstTimer ? 'Hide Intro' : 'Show Intro' }}</span>
          </q-btn>
        </div>
      </div>

      <no-scrollbar-component
        :style="{
          paddingBottom: bottomPadding,
        }"
      >
        <router-view />

        <q-dialog v-model="showConfirm" persistent @hide="resetConfirmDialogData">
          <div style="min-width: 25em;">
            <q-card class="text-center">
              <q-card-section v-if="confirmData.title">
                <div class="text-h6">
                  <q-avatar v-if="confirmData.icon" :icon="confirmData.icon" color="black" text-color="grey-5" />
                  {{ confirmData.title }}
                </div>
              </q-card-section>

              <q-card-section v-if="confirmData.message">
                <span :class="{ 'pl-0': confirmData.icon }">
                  <span v-if="confirmData.htmlMessage" v-html="confirmData.message"> </span>
                  <template v-else>{{ confirmData.message }}</template>
                </span>
              </q-card-section>

              <q-card-actions align="center" class="full-width">
                <q-btn
                  flat
                  @click="confirmData.noAction"
                  :label="confirmData.noLabel"
                  :color="confirmData.noColor"
                  v-close-popup
                  :class="{
                    [confirmData.noClass]: true,
                    'full-width h-divide-top-dotted': $q.screen.lt.md,
                  }"
                />
                <q-btn
                  @click="confirmData.yesAction"
                  flat
                  :label="confirmData.yesLabel"
                  :color="confirmData.yesColor"
                  v-close-popup
                  :class="{
                    [confirmData.yesClass]: true,
                    'full-width h-divide-top-dotted': $q.screen.lt.md,
                  }"
                />
              </q-card-actions>
            </q-card>
          </div>
        </q-dialog>
      </no-scrollbar-component>
      <device-rename-dialog ref="createDlg"></device-rename-dialog>
      <device-provision-dialog ref="provDlg"></device-provision-dialog>
      <create-package-dialog ref="createPkgDlg" />
      <fleet-device-dialog ref="fleetDeviceDlg"></fleet-device-dialog>
      <create-multi-target-update ref="createUpdDlg"></create-multi-target-update>
      <create-fleet-dialog ref="createDlg"></create-fleet-dialog>
      <metrics-manager ref="deviceMetricsSelectorDlg"></metrics-manager>
      <xmas-dialog ref="xmasDlg"></xmas-dialog>
      <feature-intro v-for="(intro, index) in featureIntros" :intro-data="intro" :key="'ft_intro_' + index"></feature-intro>
    </q-page-container>
    <div v-if="activeXmas && isXmas">
      <Snow :active="true" zIndex="20000" :wind="1" :swing="3" speed="m" :color="$q.dark.isActive ? 'white' : 'grey'" style="pointer-events:none;" />
      <div
        class="fixed-bottom-left opacity-10"
        :style="{
          width: '90vh',
          'max-width': '10rem',
          bottom: 0,
          left: $q.screen.gt.sm ? '4rem' : '-2rem',
          'z-index': '1',
          'pointer-events': 'none',
        }"
      >
        <q-img src="statics/svg/snowman.svg" />
      </div>
      <div
        class="fixed-bottom-right opacity-20"
        :style="{
          width: '90vh',
          'max-width': '10rem',
          bottom: '1em',
          right: $q.screen.gt.sm ? '0rem' : '-2rem',
          'z-index': '1',
          'pointer-events': 'none',
        }"
      >
        <q-img src="statics/svg/xmastree.svg" />
      </div>

      <xmas-light />
      <div id="snow-div" class="fixed-top w-100vw h-100vh " style="pointer-events:none;"></div>
    </div>

    <!-- Premium prompt dialog removed for CE -->
    <package-content-viewer :data="showPackageContentData" v-model="showPackageContent"></package-content-viewer>
    <!-- Commercial access and org switching removed for CE -->
    <metrics-data-downloader></metrics-data-downloader>

    <package-quick-install-dialog v-if="packageQuickInstallData" :package-version="packageQuickInstallData" @close="packageQuickInstallData = null"> </package-quick-install-dialog>
  </q-layout>
</template>

<script>
import { openURL, date } from 'quasar';
import NoScrollbarComponent from '../components/NoScrollbarComponent';
import DeviceRenameDialog from '../components/devices/DeviceRenameDialog';
import DeviceProvisionDialog from '../components/devices/DeviceProvisionDialog';
import CreatePackageDialog from '../components/packages/CreatePackageDialog';
import CreateFleetDialog from '../components/fleets/CreateFleetDialog';
import DeviceQuickViewDialog from '../components/devices/DeviceQuickViewDialog';
import XmasDialog from '../components/XmasDialog';
import XmasLight from '../components/XmasLight';
import LeftMenu from '../components/menus/LeftMenu';
import { EventBus } from '../event-bus';
import Snow from 'vue-niege';
import { mapGetters, mapActions } from 'vuex';
import ResizeHandle from '../components/common/ResizeHandle.vue';
import interact from 'interactjs';
import PackageContentViewer from '../components/packages/PackageContentViewer.vue';
import ReleaseNotes from '../components/common/ReleaseNotes.vue';
// RequestPremiumBtn removed for CE
import CreateMultiTargetUpdate from '../components/updates/CreateMultiTargetUpdate.vue';
import FeatureIntro from '../components/common/FeatureIntro.vue';
import AlertBanner from '../components/common/AlertBanner.vue';
import MetricsManager from 'src/components/common/MetricsManager.vue';
import gtm from '../services/gtm.service';
// Commercial/org-related components removed for CE
import FleetDeviceDialog from '../components/fleets/FleetDeviceDialog.vue';
import MetricsDataDownloader from '../components/common/MetricsDataDownloader.vue';
import InfoBannerGroup from '../components/common/InfoBannerGroup.vue';
import InfoBanner from '../components/common/InfoBanner.vue';
import BetaBadge from '../components/common/BetaBadge.vue';
import PackageQuickInstallDialog from 'src/components/packages/PackageQuickInstallDialog.vue';

import { OptionsService } from '../services/options.service';

const { addToDate } = date;
// Alert banners - empty for CE (no Torizon-specific announcements)
const alertBanners = [];

export default {
  name: 'MainLayout',
  components: {
    NoScrollbarComponent,
    DeviceRenameDialog,
    CreateFleetDialog,
    DeviceQuickViewDialog,
    DeviceProvisionDialog,
    XmasDialog,
    XmasLight,
    CreatePackageDialog,
    LeftMenu,
    Snow,
    ResizeHandle,
    PackageContentViewer,
    ReleaseNotes,
    CreateMultiTargetUpdate,
    FeatureIntro,
    AlertBanner,
    MetricsManager,
    FleetDeviceDialog,
    MetricsDataDownloader,
    InfoBannerGroup,
    InfoBanner,
    BetaBadge,
    PackageQuickInstallDialog,
  },
  data() {
    return {
      drawerData: {},
      showConfirm: false,
      showMessageOfTheDay: false,
      hideMaintenanceMessage: false,
      maintenanceData: null,
      _showFeatureIntro: true,
      showPackageContentData: null,
      showPackageContent: false,
      packageQuickInstallData: null,
      menuDivX: 300,
      menuDivXTimer: 0,
      bottomPadding: '0px',
      showWebsocketStatus: false,
      wsStatusAcknowledged: false,
      confirmData: {
        yesLabel: 'OK',
        noLabel: 'Cancel',
        yesAction: () => {},
        noAction: () => {},
        noColor: 'default',
        noColor: 'default',
        htmlMessage: false,
      },
      rightDrawerOpen: true, //this.$q.platform.is.desktop,
      snowStorm: null,
      featureIntros: [
        {
          title: 'Introducing Synchronous Update',
          optionKey: 'showFeatureIntro_OTA_738',
          slides: [
            {
              image: '/statics/feature-intro/sync-update/{theme}/1.png',
              text: 'Synchronous update lets you update **multiple components** on your devices and fleets at the same time.',
            },
            {
              image: '/statics/feature-intro/sync-update/{theme}/2.png',
              text: 'When you initiate an update on a device or fleet, you will now be asked to select which components to update. Once the components are selected, click **"Continue"**',
            },
            {
              image: '/statics/feature-intro/sync-update/{theme}/3.png',
              text: 'Select packages for each component selected in the previous screen and click **"Continue"**',
            },
            {
              image: '/statics/feature-intro/sync-update/{theme}/4.png',
              text: 'Confirm your selections and click **"Finish"** to queue the updates',
            },
          ],
        },
      ],
    };
  },
  created() {
    this.$q.addressbarColor.set();
    this.drawerState = this.$q.screen.gt.sm;
  },
  mounted() {
    this.listenForConfirmDialog();
    this.listenForPackageContentDialog();
    this.listenForPackageInstallDialog();
    this.setup();
    this.triggerFeatureIntro();
    this.calulateBottomPadding();
    // Remote session events removed for CE - feature not available
  },
  watch: {
    miniBar(newVal) {
      this.updateView();
    },
    drawerState(n) {
      this.updateView();
    },
    miniState(n) {
      this.updateView();
    },
    menuDivX(n) {
      this.$events.$emit('devices:redraw', {});
      clearTimeout(this.menuDivXTimer);
      this.menuDivXTimer = setTimeout(() => {
        this.saveUserSettings({ leftMenuWidth: Math.round(n || 0) });
      }, 1000);
    },
    wsFailureCount(n, o) {
      if (n > 0) {
        this.showWebsocketStatus = true;
      }
    },
  },
  computed: {
    ...mapGetters({
      userData: 'users/userData',
      accountTypeData: 'users/accountTypeData',
      userSettings: 'ui/userSettings',
      releaseNotesData: 'ui/releaseNotes',
      wsStatus: 'ui/wsStatus',
    }),
    alertBanners() {
      return alertBanners
        .map((banner) => {
          banner.dismissed = this.user_settings[banner.optionKey];
          return banner;
        })
        .filter((banner) => !banner.dismissed);
    },
    adminMode: {
      get() {
        return this.$store.getters['ui/adminMode'];
      },
      set(v) {
        this.$store.commit('ui/setAdminMode', v);
      },
    },
    wsConnected() {
      return (this.wsStatus || {}).connected;
    },
    websocketStatusMessage() {
      return this.wsConnected ? 'WebSocket connection has been restored' : 'WebSocket connection has been lost, some features may not work as expcted.';
    },
    wsFailureCount() {
      return (this.wsStatus || {}).failureCount;
    },

    parsedMenuDivX() {
      const min = 300,
        max = 500;
      let w = this.menuDivX;
      if (w > max) {
        w = max;
      } else if (w < min) {
        w = min;
      }
      return w;
    },
    uiTabs() {
      return this.$store.getters['ui/tabs'];
    },
    currentTab: {
      get() {
        return this.$store.getters['ui/currentTab'];
      },
      set(tab) {
        this.$store.commit('ui/setCurrentTab', tab);
      },
    },
    user_settings() {
      return this.userSettings || {};
    },
    user() {
      return this.$store.getters['ui/user'] || {};
    },
    isXmas() {
      return this.$store.getters['ui/isXmas'];
    },
    activeXmas: {
      get() {
        const val = this.user_settings['activeXmas'];
        return val;
      },
      set(val) {
        return this.saveUserSettings({ activeXmas: val });
      },
    },
    darkTheme: {
      get() {
        let darkTheme = this.$q.dark.isActive;
        if (this.user_settings['darkTheme'] !== undefined) {
          darkTheme = this.user_settings['darkTheme'];
        }
        return darkTheme;
      },
      set(val) {
        this.$q.dark.set(val);
        this.saveUserSettings({ darkTheme: val });
      },
    },
    pageTitle() {
      return this.$store.getters['ui/currentPageTitle'];
    },
    isDashboardPage: {
      get() {
        return this.$store.getters['ui/isDashboardPage'];
      },
      set(val) {
        this.$store.commit('ui/setIsDashboardPage', val);
      },
    },
    firstTimer: {
      get() {
        return !this.user_settings['notFirstLogin'] && !this.$demoMode;
      },
      set(v) {
        return this.saveUserSettings({
          notFirstLogin: !v,
        });
      },
    },
    drawerState: {
      get() {
        return this.$store.getters['ui/isLeftDrawerOpen'];
      },
      set(v) {
        return this.$store.commit('ui/setIsLeftDrawerOpen', v);
      },
    },
    isMini: {
      get() {
        return this.$store.getters['ui/isMiniBar'];
      },
      set(v) {
        this.$store.commit('ui/setIsMiniBar', v);
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
    wideDrawer: {
      get() {
        return this.$store.getters['ui/wideDrawer'];
      },
      set(v) {
        this.$store.commit('ui/setWideDrawer', v);
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
    showMaintenanceMessage() {
      if (!this.maintenanceData || !this.maintenanceData['start-time']) {
        return false;
      }
      const startedAt = new Date(this.maintenanceData['start-time']);
      const duration = this.maintenanceData['duration-hours'];
      const expiration = addToDate(startedAt, { hours: duration });
      return expiration > Date.now() && !this.hideMaintenanceMessage;
    },
    showMiniContent() {
      return this.miniBar && this.miniState && this.$q.screen.gt.sm;
    },
    headerBg() {
      /* eslint-disable global-require */
      return '../assets/svg/dash-bg.svg';
    },
    rfqTemplate() {
      /* eslint-disable global-require */
      return '/statics/email-templates/rfq.html';
    },
    versionNotified: {
      get() {
        let releaseNoteVersion = ((this.releaseNotesData || {}).latestVersion || '').replace(/\./g, '_');
        return !!this.user_settings[`version_${releaseNoteVersion}_notifed`];
      },
      set(v) {
        let releaseNoteVersion = ((this.releaseNotesData || {}).latestVersion || '').replace(/\./g, '_');
        return this.saveUserSettings({
          [`version_${releaseNoteVersion}_notifed`]: v,
        });
      },
    },
    currentRouteName() {
      return (this.$route || {}).name;
    },
    showReleaseNotes: {
      get() {
        const validNote = this.releaseNotesData && this.releaseNotesData.latestVersion;
        return this.currentRouteName !== 'about' && validNote && !this.versionNotified;
      },
      set(v) {
        this.versionNotified = !v;
      },
    },
  },
  methods: {
    openURL,
    ...mapActions({
      saveUserSettings: 'ui/saveUserSettings',
      connectWebSocket: 'ui/connectWebSocket',
      fetchHtmlTemplate: 'ui/fetchHtmlTemplate',
    }),
    dismissAlertBanner(banner) {
      this.$set(this.user_settings, banner.optionKey, true);
      OptionsService.saveOption(banner.optionKey, true);
    },
    dismissWebSocketStatus() {
      this.showWebsocketStatus = false;
      this.wsStatusAcknowledged = true;
    },

    calulateBottomPadding() {
      const header = document.querySelector('.q-header') || {};
      const outputsize = () => {
        let padding = header.offsetHeight;
        this.bottomPadding = padding - 40 + 'px';
      };
      outputsize();
      new ResizeObserver(outputsize).observe(header);
    },
    triggerFeatureIntro() {
      // Place holder trigger to show feature intro for a newly introduced feature
      // this.$events.$emit('dialogs:featureIntro:request', { key: 'feature intro key goes here' });
      this.$events.$emit('alert:banner:request', { key: 'alert_OTA_1188' });
    },
    updateUserOption(optionKey, value) {
      this.$set(this.user_settings, optionKey, value);
      this.saveUserSettings({ [optionKey]: value });
    },
    getSavedSettingOrDefault(key, defaultValue) {
      return typeof this.userSettings[key] !== 'undefined' ? this.userSettings[key] : defaultValue;
    },
    onParentDivResize(size) {
      this.$store.commit('ui/setParentDivSize', size);
    },
    onLeftMenuResize(size) {
      this.$store.commit('ui/setLeftMenuSize', size);
    },
    setupResizable() {
      this.menuDivX = this.user_settings['leftMenuWidth'] || 300;
      interact('.left-menu-div aside.q-drawer')
        .resizable({
          // resize from all edges and corners
          edges: { left: false, right: true, bottom: false, top: false },

          modifiers: [
            // keep the edges inside the parent
            interact.modifiers.restrictEdges({
              outer: 'parent',
              endOnly: true,
            }),
          ],

          inertia: true,
        })
        .on('resizemove', (event) => {
          var target = event.target;
          var x = this.menuDivX;
          x += event.deltaRect.right;
          this.menuDivX = Math.round(x);
        });
    },
    updateView() {
      setTimeout(() => {
        this.$events.$emit('devices:updated', {});
      }, 1000);
    },
    toggleFeatureIntro(show, intro) {
      this.$set(this.featureIntros[intro.optionKey], 'requested', show);
      this.updateUserOption(intro.optionKey, show);
    },
    setup() {
      this.setupResizable();
      const checkActiveXmas = () => {
        let active = this.activeXmas;
        if (active === undefined && this.$store.getters['ui/isXmas']) {
          EventBus.$emit('dialogs:xmas:open', {
            mode: {},
          });
        }
      };
      checkActiveXmas();
      this.connectWebSocket();
    },
    hideReleaseNotes() {
      this.showReleaseNotes = false;
    },

    toggleDarkMode(val) {
      this.$q.dark.toggle();
    },
    toggleFirstTimer() {
      this.firstTimer = !this.firstTimer;
      gtm.logEvent('TopBar', 'click', this.firstTimer ? 'Show Intro' : 'Hide Intro', null);
    },
    clearFirstTimer() {
      this.firstTimer = false;
    },
    toggleLeftMenu() {
      if (this.isMini) {
        this.drawerState = true;
        this.miniState = !this.miniState;
        this.wideDrawer = !this.miniState;
      } else {
        this.miniState = false;
        this.drawerState = !this.drawerState;
        this.wideDrawer = this.miniState;
      }
    },

    listenForPackageContentDialog() {
      this.$events.$on(`dialogs:package-content`, (data) => {
        this.showPackageContentData = data;
        this.showPackageContent = true;
      });
    },
    listenForPackageInstallDialog() {
      this.$events.$on(`dialogs:install-package`, (data) => {
        this.packageQuickInstallData = data;
      });
    },
    listenForConfirmDialog() {
      this.$events.$on(`dialogs:confirm:open`, (data) => {
        Object.assign(this.confirmData, data);
        this.showConfirm = true;
      });
    },
    resetConfirmDialogData() {
      this.confirmData = {
        yesLabel: 'OK',
        noLabel: 'Cancel',
        yesAction: () => {},
        noAction: () => {},
        noColor: 'default',
        noColor: 'default',
      };
    },
    // initiateRemoteSessionForDevice removed for CE - feature not available
  },
};
</script>
