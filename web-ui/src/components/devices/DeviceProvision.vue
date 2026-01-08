<template>
  <div>
    <div class="row">
      <div class="col-12"></div>

      <div class="mt-2 col-12 provision-error" v-if="tokenError">
        <q-banner inline-actions rounded class="bg-negative text-white p-1">
          Could not load device provision token. Please try again in a moment.
          <template v-slot:action>
            <q-btn class="dismiss-btn  cancel-btn " data-id="dismiss-btn" @click="dismissError" flat label="Dismiss" />
          </template>
        </q-banner>
      </div>
      <div v-if="betaFeaturesEnabled" class="col-12 mt-1">
        <p>
          Do you have a device pairing code?
          <q-btn no-caps flat dense type="a" color="primary" @click="useDeviceCode">
            Click here.
          </q-btn>
          <beta-badge dense />
        </p>
      </div>
      <div v-if="!token">
        <slot></slot>
      </div>
      <div class="col-12 mt-1" v-if="token">
        <div class=" pb-1">
          <q-checkbox dense v-model="enableMetrics">Enable device metrics </q-checkbox>
          <q-btn no-caps flat dense type="a" icon="help" color="primary" href="https://developer.toradex.com/torizon/how-to/torizon-updates/device-monitoring-in-torizoncore/" target="_blank"
            >What is this?
            <tooltip> You’ll be able to view device health and monitoring metrics on the device page. This data is for your own use, and will not be shared.</tooltip>
          </q-btn>
        </div>
        <q-input v-model="deviceName" outlined dense label="Device name (Optional)"></q-input>
        <div class="row">
          <div class="col-12 relative-position pl-0">
            <p>
              Run the following command on the device you want to provision:
            </p>
            <q-btn v-if="showX" flat icon="close" data-id="dismiss-btn" class="absolute-top-right dismiss-btn  cancel-btn" label="Dismiss" @click="resetAutoProvision()"></q-btn>
          </div>
          <text-copy :content="provision_command_parsed" class="col-12 pl-0"></text-copy>
        </div>
      </div>
    </div>
    <free-trial-warning-dialog v-model="showNaggingDialog"></free-trial-warning-dialog>
  </div>
</template>

<script>
import ClipboardJS from 'clipboard';
import { mapGetters } from 'vuex';
import Tooltip from '../common/Tooltip.vue';
import TextCopy from '../common/TextCopy.vue';
import FreeTrialWarningDialog from '../users/FreeTrialWarningDialog.vue';
import BetaBadge from '../common/BetaBadge.vue';

const baseUrl = window.location.origin;

export default {
  components: { Tooltip, TextCopy, FreeTrialWarningDialog, BetaBadge },
  name: 'DeviceProvision',
  props: {
    showX: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      provPercent: 0,
      enableMetrics: true,
      provision_command: `curl -fsSL ${baseUrl}/statics/scripts/provision-device.sh | sudo bash -s -- -t <jwt> <enableMetrics> <autoProvUrl> <gatewayUrl> <deviceName>`,
      token: null,
      gatewayUrl: null,
      autoProvUrl: null,
      tokenError: '',
      tooltipCopyText: 'Click <span class="material-icons">content_copy</span>  to copy to clipboard',
      textCopied: false,
      loading: false,
      tokenData: {},
      deviceName: null,
      showNaggingDialog: false,
    };
  },
  methods: {
    getProvisioningToken() {
      const maskIt = (str, start = 0, end = 0, maxLength = 0) => {
        let newStr = '***';
        try {
          end = end || 1;
          maxLength = maxLength || str.length - start - end;
          var a = str.substring(0, start);
          var b = str.substring(str.length - end);
          let maskLen = str.length - start - end;
          if (maskLen > maxLength) maskLen = maxLength;
          const mask = str.substring(start, maskLen).replace(/./g, '*');
          newStr = a + mask + b;
        } catch (e) {
          logError(e);
        }
        return newStr;
      };
      this.resetAutoProvision(null, true);
      this.loading = true;
      const loadProv = () => {
        setTimeout(() => {
          if (this.provPercent < 100) {
            this.provPercent += 5;
            loadProv();
            this.triggerState();
          }
        }, 100);
      };
      loadProv();
      this.$axios({
        url: '/api/accounts/token',
        method: 'GET',
      })
        .then((response) => {
          this.loading = false;
          this.provPercent = 0;
          if (!this.hasReachedDeviceLimit) {
            this.autoProvUrl = response.data.autoProvUrl;
            this.gatewayUrl = response.data.gatewayUrl;
            this.token = this.$demoMode ? maskIt(response.data.token, 5, 5, 60) : response.data.token;
            this.$store.commit('ui/setUserDeviceProvisionData', response.data);
            this.triggerState();
            this.setupcopyAction();
          }
        })
        .catch((err) => {
          this.loading = false;
          this.provPercent = 0;
          this.tokenError = true;
          this.triggerState();
          this.setupcopyAction();
        })
        .finally((f) => {
          this.showNaggingDialog = true;
          if (this.hasReachedDeviceLimit) {
            this.$events.$emit(`dialogs:premium:open`, {});
          }
        });
      return false;
    },
    setupcopyAction() {
      let clipboard = new ClipboardJS('.docker-cmd-copy');
      const that = this;
      clipboard.on('success', (e) => {
        this.tooltipCopyText = 'Copied!';
        this.textCopied = true;
        setTimeout(() => {
          that.tooltipCopyText = 'Click <span class="material-icons">content_copy</span>  to copy to clipboard';
          this.textCopied = false;
        }, 4000);
      });
    },
    resetAutoProvision($ev, skipEmit = false) {
      if ($ev && $ev.stopPropagation) {
        $ev.stopPropagation();
      }
      this.token = '';
      this.autoProvUrl = '';
      this.gatewayUrl = '';
      this.tokenError = '';
      this.provPercent = 0;
      if (!skipEmit) {
        this.$emit('reset', {});
      }
    },
    triggerState() {
      const data = {
        token: this.token,
        tokenError: this.tokenError,
        loading: this.loading,
        percent: this.provPercent,
      };
      this.$emit('state-change', data);
    },
    dismissError() {
      this.resetAutoProvision();
    },
    useDeviceCode() {
      this.$events.$emit('dialogs:provisioning-code-dialog:open', {});
    },
  },
  computed: {
    ...mapGetters({
      userSettings: 'ui/userSettings',
      user: 'ui/user',
      betaFeaturesEnabled: 'users/betaFeaturesEnabled',
    }),
    user_settings() {
      return this.userSettings || {};
    },
    hasReachedDeviceLimit() {
      return this.user.deviceProvisionData && this.user.deviceProvisionData.numDevices && this.user.deviceProvisionData.deviceLimit && this.user.deviceProvisionData.numDevices >= this.user.deviceProvisionData.deviceLimit;
    },
    isProdEnvironment() {
      return this.autoProvUrl.indexOf('app.torizon.io') !== -1;
    },
    provision_command_parsed() {
      const provisionCommand = this.provision_command
      const autoProvUrl = this.isProdEnvironment ? '' : `-u ${this.autoProvUrl}`;
      const gatewayUrl = this.isProdEnvironment ? '' : `-g ${this.gatewayUrl}`;
      const deviceName = this.deviceName ? `-n "${this.deviceName}"` : '';
      const metrics = this.enableMetrics ? '' : `-p`
      return provisionCommand
        .replace('<autoProvUrl>', autoProvUrl)
        .replace('<gatewayUrl>', gatewayUrl)
        .replace('<deviceName>', deviceName)
        .replace('<enableMetrics>', metrics)
        .replace('<jwt>', this.token);
    },
    darkTheme() {
      let darkTheme = this.$q.dark.isActive;
      if (this.user_settings['darkTheme'] !== undefined) {
        darkTheme = this.user_settings['darkTheme'];
      }
      return darkTheme;
    },
  },
};
</script>
