<template>
  <div class="row p-1">
    <q-card class="col-12">
      <div class="q-card p-2 row w-100 mxw-50em">
        <h4 class="m-0 col-12">Debug Information</h4>
        <div class="col-12 pr-2">
          <h5 class="mb-1">Current User</h5>
          <div class="row q-item-tile label h-divide-bottom-dashed p-1">
            <div class="col-12 text-bold">Provider ID:</div>
            <div class="col-12 ">
              <span class="mr-1 opacity-50">{{ user.sub }}</span>
              <q-icon name="fa fa-clipboard" class="data-copy" color="grey-8" :data-clipboard-text="user.sub">
                <q-tooltip :content-class="textCopied ? 'bg-positive' : 'bg-black'">
                  {{ tooltipCopyText }}
                </q-tooltip>
              </q-icon>
            </div>
          </div>
          <div class="row q-item-tile label h-divide-bottom-dashed p-1">
            <div class="col-12 text-bold">User ID:</div>
            <div class="col-12 ">
              <span class="mr-1 opacity-50">{{ userData.user_id }}</span>
              <q-icon name="fa fa-clipboard" class="data-copy" color="grey-8" :data-clipboard-text="userData.user_id">
                <q-tooltip :content-class="textCopied ? 'bg-positive' : 'bg-black'">
                  {{ tooltipCopyText }}
                </q-tooltip>
              </q-icon>
            </div>
          </div>
          <div class="row q-item-tile label h-divide-bottom-dashed p-1">
            <div class="col-12 text-bold">User Namespace:</div>
            <div class="col-12 ">
              <span class="mr-1 opacity-50">{{ userData.namespace }}</span>
              <q-icon name="fa fa-clipboard" class="data-copy" color="grey-8" :data-clipboard-text="userData.namespace">
                <q-tooltip :content-class="textCopied ? 'bg-positive' : 'bg-black'">
                  {{ tooltipCopyText }}
                </q-tooltip>
              </q-icon>
            </div>
          </div>
          <div class="row q-item-tile label h-divide-bottom-dashed p-1">
            <div class="col-12 text-bold">Access Token:</div>
            <div class="col-12 ">
              <span class="mr-1 opacity-50">{{ limitText(accessToken || '', 32) }}</span>
              <q-icon name="fa fa-clipboard" class="data-copy" color="grey-8" :data-clipboard-text="accessToken">
                <q-tooltip :content-class="textCopied ? 'bg-positive' : 'bg-black'">
                  {{ tooltipCopyText }}
                </q-tooltip>
              </q-icon>
              <reload-btn :busy="refreshingToken" @reload-requested="refreshToken">Refresh</reload-btn>
            </div>
          </div>
          <div class="row q-item-tile label h-divide-bottom-dashed p-1" v-if="isGuestAccess">
            <div class="col-12 text-bold">Organization Guest Access Token:</div>
            <div class="col-12 ">
              <span class="mr-1 opacity-50">{{ limitText(guestAccessToken || '', 32) }}</span>
              <q-icon name="fa fa-clipboard" class="data-copy" color="grey-8" :data-clipboard-text="guestAccessToken">
                <q-tooltip :content-class="textCopied ? 'bg-positive' : 'bg-black'">
                  {{ tooltipCopyText }}
                </q-tooltip>
              </q-icon>
              <reload-btn :busy="refreshingGuestToken" @reload-requested="refreshGuestToken">Refresh</reload-btn>
            </div>
          </div>
          <div class="row q-item-tile label h-divide-bottom-dashed p-1">
            <div class="col-12 text-bold">Initial Namespace Setup Date:</div>
            <div class="col-12 ">
              <span class="mr-1 opacity-50">
                {{ userCreatedDate }}
              </span>
            </div>
          </div>
        </div>
        <div class="col-12 pr-2">
          <h5 class="mb-1">Current Application Modes</h5>
          <div class="row q-item-tile label h-divide-bottom-dashed p-1">
            <div class="col-7 text-bold">User pool:</div>
            <div class="col-auto">{{ $userPool }}</div>
          </div>
          <div class="row q-item-tile label h-divide-bottom-dashed p-1">
            <div class="col-7 text-bold">Demo mode:</div>
            <div class="col-auto">{{ $demoMode ? 'Yes' : 'No' }}</div>
          </div>
          <div class="row q-item-tile label h-divide-bottom-dashed p-1">
            <div class="col-7 text-bold">Dark mode:</div>
            <div class="col-auto">{{ darkMode ? 'Yes' : 'No' }}</div>
          </div>
          <div class="row q-item-tile label h-divide-bottom-dashed p-1">
            <div class="col-7 text-bold">Admin mode:</div>
            <div class="col-auto">{{ adminMode ? 'Yes' : 'No' }}</div>
          </div>
          <div class="row q-item-tile label h-divide-bottom-dashed p-1">
            <div class="col-7 text-bold">Maintenance mode:</div>
            <div class="col-auto">{{ showMaintenanceMessage ? 'Yes' : 'No' }}</div>
          </div>
        </div>
        <div class="col-12 pr-2">
          <h5 class="mb-1">Current Application Session State</h5>
          <div class="row q-item-tile label h-divide-bottom-dashed p-1">
            <div class="col-7 text-bold">First time user:</div>
            <div class="col-auto">{{ firstTimer ? 'Yes' : 'No' }}</div>
          </div>
          <div class="row q-item-tile label h-divide-bottom-dashed p-1">
            <div class="col-7 text-bold">Mini sidebar:</div>
            <div class="col-auto">{{ isMini ? 'Yes' : 'No' }}</div>
          </div>
          <div class="row q-item-tile label h-divide-bottom-dashed p-1 text-secondary">
            <div class="col-7 text-bold">Session started at:</div>
            <div class="col-auto">{{ $date.formatDate(launchedAt, 'MM/DD/YYYY h:mm:ss A') }}</div>
          </div>
          <div class="row q-item-tile label h-divide-bottom-dashed p-1 text-primary">
            <div class="col-7 text-bold">Session uptime:</div>
            <div class="col-auto">{{ uptimeString }}</div>
          </div>
        </div>

        <div class="col-12 pr-2">
          <h5 class="mb-0">Tools</h5>
          <div class="row q-item-tile label h-divide-bottom-dashed p-1">
            <div class="col-7 ">
              <manage-credentials></manage-credentials>
            </div>
          </div>
        </div>
      </div>
    </q-card>
  </div>
</template>

<script>
import { saveSync } from 'save-file';
import ClipboardJS from 'clipboard';
import { mapGetters, mapActions } from 'vuex';
import ManageCredentials from 'src/components/users/ManageCredentials';
import ReloadBtn from 'src/components/common/ReloadBtn.vue';
import { AuthService } from 'src/services/auth.service';

export default {
  name: 'DebugGeneralTab',
  components: {
    ManageCredentials,
    ReloadBtn,
  },
  data() {
    return {
      loadingCred: false,
      credPercent: 0,
      tooltipCopyText: 'Copy to clipboard',
      textCopied: false,
      refreshingToken: false,
      refreshingGuestToken: false,
    };
  },
  created() {},
  mounted() {
    this.pageTitle = 'Debug Info';
    this.setupcopyAction();
  },
  computed: {
    ...mapGetters({
      userData: 'users/userData',
      accessToken: 'users/accessToken',
      guestAccessToken: 'users/guestAccessToken',
      userSettings: 'ui/userSettings',
      isGuestAccess: 'organizations/isGuestAccess',
      hostRepositoryIsDefined: 'organizations/hostRepositoryIsDefined',
    }),
    pageTitle: {
      get() {
        return this.$store.getters['ui/currentPageTitle'];
      },
      set(val) {
        return this.$store.commit('ui/setCurrentPageTitle', val);
      },
    },
    adminMode: {
      get() {
        return this.$store.getters['ui/adminMode'];
      },
      set(v) {
        this.$store.commit('ui/setAdminMode', v);
      },
    },
    userCreatedDate() {
      let date = this.getSavedSettingOrDefault('user_created_timestamp', 'Not Set');
      if (date === 'Not Set') {
        return date;
      }
      return new Date(date);
    },
    user() {
      return this.$store.getters['ui/user'] || {};
    },
    launchedAt() {
      return this.$store.getters['ui/launchedAt'];
    },
    uptime() {
      return Date.now() - this.launchedAt; //this.$store.getters["ui/uptime"];
    },
    uptimeString() {
      var seconds = (this.uptime / 1000).toFixed(1);

      var minutes = (this.uptime / (1000 * 60)).toFixed(1);

      var hours = (this.uptime / (1000 * 60 * 60)).toFixed(1);

      var days = (this.uptime / (1000 * 60 * 60 * 24)).toFixed(1);

      if (seconds < 60) {
        return seconds + ' Sec';
      } else if (minutes < 60) {
        return minutes + ' Min';
      } else if (hours < 24) {
        return hours + ' Hrs';
      } else {
        return days + ' Days';
      }
    },
    isXmas() {
      return this.$store.getters['ui/isXmas'];
    },
    activeXmas: {
      get() {
        const val = this.$store.getters['ui/activeXmas'];
        return val;
      },
      set(val) {
        return this.$store.commit('ui/setActiveXmas', val);
      },
    },
    darkMode: {
      get() {
        return this.$q.dark.isActive;
      },
      set(val) {
        this.$q.dark.set(val);
      },
    },
    firstTimer: {
      get() {
        return (this.$store.getters['ui/isFirstTimer'] && !this.$demoMode) || this.$e2eMode;
      },
      set(v) {
        return this.$store.commit('ui/setIsFirstTimer', v);
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
    showMaintenanceMessage() {
      if (!this.maintenanceData || !this.maintenanceData['start-time']) {
        return false;
      }
      const startedAt = new Date(this.maintenanceData['start-time']);
      const duration = this.maintenanceData['duration-hours'];
      const expiration = addToDate(startedAt, { hours: duration });
      return expiration > Date.now() && !this.hideMaintenanceMessage;
    },
  },
  methods: {
    ...mapActions({}),
    refreshToken() {
      this.refreshingToken = true;
      AuthService.refreshSession()
        .then((data) => {})
        .finally(() => {
          this.refreshingToken = false;
        });
    },
    refreshGuestToken() {
      this.refreshingGuestToken = true;
      AuthService.refreshGuestSession()
        .then((data) => {})
        .finally(() => {
          this.refreshingGuestToken = false;
        });
    },
    getSavedSettingOrDefault(key, defaultValue) {
      return typeof this.userSettings[key] !== 'undefined' ? this.userSettings[key] : defaultValue;
    },
    limitText(text, limit) {
      limit = Math.round(limit / 2);
      return text.slice(0, limit) + '...' + text.slice(text.length - limit - 1);
    },
    downloadCred() {
      this.loadingCred = true;
      const loadCred = () => {
        setTimeout(() => {
          if (this.credPercent < 100) {
            this.credPercent += 5;
            loadCred();
          }
        }, 100);
      };
      loadCred();
      this.$axios({
        url: this.$credentials_link,
        method: 'GET',
        responseType: 'blob',
      })
        .then((response) => {
          this.loadingCred = false;
          this.credPercent = 0;
          saveSync(response.data, 'credentials.zip');
        })
        .catch((err) => {
          this.loadingCred = false;
          this.credPercent = 0;
        });
      return false;
    },
    setupcopyAction() {
      let clipboard = new ClipboardJS('.data-copy');
      clipboard.on('success', (e) => {
        this.tooltipCopyText = 'Copied!';
        this.textCopied = true;
        setTimeout(() => {
          this.tooltipCopyText = 'Copy to clipboard';
          this.textCopied = false;
        }, 4000);
      });
    },
    saveField(name, value, defaultValue) {
      AuthService.updateUserData({ [name]: value || defaultValue })
        .then((saved) => {
          this.updating[name] = false;
        })
        .catch((e) => {
          this.updating[name] = false;
        });
    },
    finishChangePasswordProcess() {
      this.showChangePassword = false;
      this.changedPassword = true;
      setTimeout(() => {
        this.changedPassword = false;
      }, 5000);
    },
    startChangePasswordProcess() {
      this.showChangePassword = true;
      this.changedPassword = false;
    },
    cancelChangePasswordProcess() {
      this.showChangePassword = false;
      this.changedPassword = false;
    },

    imageUploadDone(data) {
      this.updatingAvatar = !this.updatingAvatar;
    },
    signOut() {
      AuthService.signOut()
        .then((result) => {
          if (result) {
            this.$router.push({ name: 'login' });
            this.$router.go();
          }
        })
        .catch((err) => {
          logError(err);
        });
    },
  },
};
</script>

<style></style>
