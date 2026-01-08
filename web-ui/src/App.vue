<template>
  <div id="q-app">
    <transition :appear="false" enter-active-class="animated fadeIn" leave-active-class="animated fadeOut">
      <div
        id="loader-div"
        v-if="!loaded"
        style="margin: -0rem; width: 100vw; height: 100vh;
      background-color: #121921; background-size: cover; display:flex; flex-flow: column; justify-content:center; align-items: center; "
      >
        <svg-main-loader-first-stage v-if="!almostLoaded" style="width: 300px; height: 300px"></svg-main-loader-first-stage>
        <svg-main-loader-second-stage v-else style="width: 300px; height: 300px" :end="finishAnimation()"></svg-main-loader-second-stage>
        <div class="text-center ml-5 mr-5" v-if="uiLoaderType === 'text'">
          <svg id="loading-text-svg" v-if="!doneLoading" style="border: 2px solid transparent;" height="80" :viewBox="'10 10 ' + loaderWidth + ' 120'">
            <defs>
              <mask x="0" y="0" width="100%" height="100%" id="mask">
                <text text-anchor="middle" height="50" x="50%" y="70%" fill="#ffffff" font-family="Roboto" font-weight="thin" font-size="2em">{{ loadText }}... {{ Math.round(percentLoaded) }}%</text>
              </mask>
            </defs>
            <rect id="rect" :x="percentLoaded + '%'" y="50" width="20" height="80" fill="#ffffff" stroke="white" fill-opacity="0.1" stroke-opacity="0.2" />
            <text text-anchor="middle" height="50" x="50%" y="70%" fill="#ffffff44" font-family="Roboto" font-weight="thin" font-size="2em">{{ loadText }}... {{ Math.round(percentLoaded) }}%</text>
            <rect id="rect" x="0" y="0" :width="percentLoaded + '%'" height="100%" fill="#ffffff" mask="url(#mask)" />
            <g :transform="'translate(' + valuePushed + ', 90)'">
              <image id="moving-man-svg" x="0%" y="0" xlink:href="~/assets/svg/man-pushing.svg" width="50" height="45" />
            </g>
          </svg>
        </div>
      </div>
    </transition>
    <router-view v-if="loaded" />
    <template>
      <walkthrough-dialog v-if="showOnboardingWalkthrough" @close="showOnboardingWalkthrough = false"> </walkthrough-dialog>
    </template>
    <q-resize-observer @resize="onResize" />
    <device-provision-code></device-provision-code>
  </div>
</template>

<script>
/**
 * OTA Community Edition App
 * 
 * Simplified app initialization without authentication or multi-org logic.
 */

import { NO_LOADER_PAGES } from './config';
import { mapGetters, mapActions } from 'vuex';
import { AuthService } from 'src/services/auth.service';
import { OptionsService } from 'src/services/options.service';

import SvgMainLoaderSecondStage from './components/svgs/SvgMainLoaderSecondStage';
import SvgMainLoaderFirstStage from './components/svgs/SvgMainLoaderFirstStage';
import WalkthroughDialog from './components/common/OnboardingWalkthrough/WalkthroughDialog.vue';
import DeviceProvisionCode from 'src/components/devices/DeviceProvisionCode.vue';

export default {
  components: {
    SvgMainLoaderFirstStage,
    SvgMainLoaderSecondStage,
    WalkthroughDialog,
    DeviceProvisionCode,
  },
  name: 'App',
  data() {
    return {
      noloader: false,
      almostLoaded: false,
      message: '',
      timer: 0,
      forceLoaded: false,
      percentLoaded: 0,
      finishedAnimation: false,
    };
  },
  methods: {
    ...mapActions({
      fetchPackages: 'packages/fetchPackages',
      getRoles: 'users/getRoles',
      fetchDevices: 'devices/fetchDevices',
      fetchFleets: 'fleets/fetchFleets',
      initializeUI: 'ui/initializeUI',
      saveUserSettings: 'ui/saveUserSettings',
    }),
    beginLoadingData() {
      // In CE mode, we don't need to fetch user data - just load the app data
      this.setSelectedDelegationSources();
      this.almostLoaded = true;
      this.loadFinalData();
    },
    setSelectedDelegationSources() {
      const urlSources = (this.$route.query['delegation-sources'] || '').split(',').filter((s) => s);

      let sources = [];
      if (urlSources.length > 0) {
        sources = urlSources;
      } else {
        const userSavedSources = OptionsService.getSavedOptionOrDefault('selectedDelegationSources', null);
        sources = userSavedSources !== null ? _.toArray(userSavedSources) : this.$store.getters['packages/defaultSelectedDelegationSources'] || [];
      }
      this.$store.commit('packages/setSelectedDelegationSources', sources);
    },
    loadFinalData() {
      this.percentLoaded = 98;
      const finalizeLoad = () => {
        // Get roles (CE mode returns default roles)
        Promise.all([this.getRoles()]).finally(() => {
          this.initializeUI().finally(() => {
            // Load packages, devices, and fleets
            this.fetchPackages().catch((err) => {
              console.error('Error fetching packages: ', err);
            });
            this.fetchDevices()
              .then(() => {
                this.triggerOnboardingWalkthrough();
              })
              .catch((err) => {
                console.error('Error fetching devices: ', err);
              });
            this.fetchFleets().catch((err) => {
              console.error('Error fetching fleets: ', err);
            });
          });
        });
      };
      
      setTimeout(() => {
        clearInterval(this.timer);
      }, 8000);
      
      // In CE mode, skip org loading and go straight to finalizeLoad
      finalizeLoad();
    },
    triggerOnboardingWalkthrough() {
      if (this.$route.path.includes('packages')) {
        this.showOnboardingWalkthrough = false;
        this.startGuideDisabled = true;
      } else {
        if (this.devices.length < 1 && !this.startGuideDisabled) {
          this.showOnboardingWalkthrough = true;
        }
      }
    },
    animateProgress() {
      this.timer = setInterval(() => {
        if (this.percentLoaded >= 70 && !this.almostLoaded) {
          this.percentLoaded = 70;
        }
        if (this.percentLoaded < 93 && this.almostLoaded) {
          this.incrementTo(93);
        }
        this.percentLoaded += 0.1;
        if (this.percentLoaded > 100 && !this.uiLoaded) {
          this.percentLoaded = 100;
        }
      }, 80);
    },
    incrementTo(val) {
      this.percentLoaded += 0.1;
      if (this.percentLoaded < val) {
        setTimeout(() => {
          this.incrementTo(val);
        }, 10);
      }
    },
    finishAnimation() {
      setTimeout(() => {
        this.finishedAnimation = true;
      }, 7000);
    },
    onResize(size) {
      this.$store.commit('ui/setParentDivSize', size);
    },
  },
  computed: {
    ...mapGetters({
      uiLoaded: 'ui/loaded',
      uiLoaderText: 'ui/uiLoaderText',
      uiLoaderType: 'ui/uiLoaderType',
      launchedAt: 'ui/launchedAt',
      devices: 'devices/devices',
    }),
    startGuideDisabled: {
      get() {
        return OptionsService.getSavedOptionOrDefault('startGuideDisabled', false);
      },
      set(val) {
        OptionsService.saveOption('startGuideDisabled', val);
      },
    },
    showOnboardingWalkthrough: {
      get() {
        return this.$store.getters['ui/showOnboardingWalkthrough'];
      },
      set(val) {
        this.$store.commit('ui/setShowOnboardingWalkthrough', val);
      },
    },
    loaderWidth() {
      return 200 + this.loadText.length * 12;
    },
    loaded() {
      const page = (location.href || '').split('#')[1];
      this.noloader = (location.href.match(/noloader=([^&]*)/) || [])[1] || NO_LOADER_PAGES.indexOf(page) !== -1;

      if (this.noloader) {
        return true;
      }
      return (this.uiLoaded && this.finishedAnimation) || this.forceLoaded;
    },
    doneLoading() {
      return this.percentLoaded >= 100;
    },
    valuePushed() {
      return Math.round((this.percentLoaded * this.loaderWidth) / 100) - 45;
    },
    loadText() {
      return this.uiLoaderText || 'Loading OTA Community Edition';
    },
  },
  beforeDestroy() {
    clearTimeout(this.timer);
  },
  mounted() {
    this.$store.commit('ui/setLaunchedAt', Date.now());
    this.animateProgress();
    // In CE mode, we're always "logged in" - start loading immediately
    AuthService.init().then(() => {
      this.beginLoadingData();
    });
  },
};
</script>
