<template>
  <div>
    <q-dialog v-model="showDialog" @hide="cleanup" persistent>
      <q-card class="mnw-30em mxw-50em q-px-md q-pt-sm q-pb-xs">
        <template v-if="hasPermission">
          <q-card-section class="">
            <div class="text-h5 mb-1 text-center">
              Device Pairing Code
              <q-icon name="close" class="cursor-pointer float-right" @click="showDialog = false"></q-icon>
            </div>
            <div>
              <p class="q-px-lg">Enter the code displayed on your device to complete the pairing process.</p>
            </div>
          </q-card-section>
          <q-card-section class="pt-0 q-mb-lg">
            <code-input :length="6" v-model="code" :success="!loading && valid && complete" :complete.sync="complete" @update:complete="codeEntryComplete" @input="submitted = false" @enter="onEnter"></code-input>
            <div v-if="loading" class="row items-center justify-center  text-center">
              <div class="w-20em">
                Verifying code...
                <q-linear-progress indeterminate color="primary" size="sm"></q-linear-progress>
              </div>
            </div>
            <div v-else-if="!valid && submitted" class="text-center justify-center flex">
              <p class="p-0 q-mt-xs text-center text-negative mxw-30em">{{ errorMessage }}</p>
            </div>
          </q-card-section>
          <q-card-actions class="pt-0 q-mb-lg">
            <q-space />
            <q-btn label="Cancel" color="primary" outline @click="showDialog = false"></q-btn>
            <q-btn v-if="complete && !loading" label="Continue" color="primary" @click="processCode"></q-btn>
            <q-space />
          </q-card-actions>
        </template>
        <template v-else>
          <q-card-section class="">
            <div class="text-h5 mb-1 text-center">
              Device Pairing Code
              <q-icon name="close" class="cursor-pointer float-right" @click="showDialog = false"></q-icon>
            </div>
            <div class="row items-center mxw-35em q-ma-md">
              <div class="col-auto mr-1">
                <q-icon name="warning" size="2rem" class="text-warning" />
              </div>
              <div class="col">You do not have permission to provision devices. Please contact your organization administrator.</div>
            </div>
          </q-card-section>
          <q-card-actions class="pt-0 q-mb-lg">
            <q-space />
            <q-btn label="Close" color="primary" outline @click="showDialog = false"></q-btn>
            <q-space />
          </q-card-actions>
        </template>
      </q-card>
    </q-dialog>
    <q-dialog v-model="showProgressDialog" @hide="cleanup" persistent>
      <q-card class="mnw-40em mxw-60em q-px-md q-pt-sm q-pb-xs">
        <q-card-section class="">
          <div class="text-h5 mb-1 text-center">
            <template v-if="statusCode < 2"
              >Device Pairing Code Received</template
            >
            <template v-if="statusCode >= 2"
              >Device Received Provisioning Token</template
            >
            <q-icon name="close" class="cursor-pointer float-right" @click="showProgressDialog = false"></q-icon>
          </div>
          <div class="flex flex-center">
            <p v-if="statusCode < 2" class="q-px-lg text-center mxw-40em">Your device pairing code has been successfully validated! <br />Your device will receive a provisioning token and be added to Torizon Cloud shortly.</p>
            <p v-if="statusCode >= 2" class="q-px-lg text-center mxw-40em">Your device successfully received a provisioning token and it will be added to Torizon Cloud shortly.</p>
          </div>
        </q-card-section>
        <q-card-section class="">
          <div class="row items-center justify-center  text-center w-100 mnw-50em q-gutter-md  mb-2">
            <div class="col">
              <div class="row items-center justify-center  q-gutter-md">
                <div class="col-auto">
                  <q-spinner-hourglass v-if="statusCode === 0" color="primary" size="md"></q-spinner-hourglass>
                  <q-icon v-if="statusCode > 0" name="check_circle" color="positive" size="md"></q-icon>
                </div>
                <div class="col-auto"><div class="">Code validated</div></div>
                <div class="col"><q-separator /></div>
              </div>
            </div>
            <div class="col">
              <div class="row items-center justify-center  q-gutter-md">
                <div class="col-auto">
                  <q-icon v-if="statusCode < 1" name="radio_button_unchecked" class="opacity-40" size="md"></q-icon>
                  <q-spinner-hourglass v-if="statusCode === 1" color="primary" size="md"></q-spinner-hourglass>
                  <q-icon v-if="statusCode > 1" name="check_circle" color="positive" size="md"></q-icon>
                </div>
                <div class="col-auto items-center "><div class="">Waiting for device</div></div>
                <div class="col items-center "><q-separator /></div>
              </div>
            </div>
            <div class="col-auto">
              <div class="row items-center justify-center  q-gutter-md ">
                <div class="col-auto">
                  <q-icon v-if="statusCode < 2" name="radio_button_unchecked" class="opacity-40" size="md"></q-icon>
                  <!-- <q-spinner-hourglass v-if="statusCode === 2" color="primary" size="md"></q-spinner-hourglass> -->
                  <q-icon v-if="statusCode >= 2" name="check_circle" color="positive" size="md"></q-icon>
                </div>
                <div class="col-auto"><div class="">Device received token</div></div>
              </div>
            </div>
          </div>
        </q-card-section>
        <q-card-actions class="pt-0 q-mb-lg">
          <q-space />
          <q-btn label="Close" color="primary" outline @click="showProgressDialog = false"></q-btn>
          <template v-if="statusCode >= 2">
            <q-btn v-if="hasDemoApps" label="View Demo Apps" color="primary" v-close-popup @click="gotoDemoApps"></q-btn>
            <q-btn
              v-else
              label="Add another device"
              color="primary"
              @click="
                showProgressDialog = false;
                showDialog = true;
              "
            ></q-btn>
          </template>
          <q-space />
        </q-card-actions>
      </q-card>
    </q-dialog>
  </div>
</template>

<script>
import CodeInput from '../common/CodeInput.vue';
import { canAccessFeature } from 'src/config/feature-toggle';
import { mapActions, mapGetters } from 'vuex';
export default {
  name: 'DeviceProvisionCode',
  components: { CodeInput },
  data() {
    return {
      showDialog: false,
      showProgressDialog: false,
      loading: false,
      code: '',
      ip: '',
      complete: false,
      valid: false,
      submitted: false,
      statusCode: 0,
      interval: null,
      submittedCode: '',
      errorMessage: 'The code you entered is not valid, please try again.',
    };
  },
  methods: {
    ...mapActions({
      claimProvisioningCode: 'devices/claimProvisioningCode',
      getProvisioningCodeStatus: 'devices/getProvisioningCodeStatus',
      refreshDelegations: 'packages/refreshDelegations',
    }),
    show() {
      this.showDialog = true;
    },

    gotoDemoApps() {
      this.refreshDelegations({ delegation: 'tdx-containers' });
      // We don't need to wait for the refresh to complete before redirecting to the packages page. If we do, there will be a delay in the UI before the user is redirected and that's not a good user experience.
      this.$router.push({ name: 'packages', query: { 'delegation-sources': 'tdx-containers' } });
    },

    codeEntryComplete(complete) {
      if (complete) {
        // this.valid = true;
      }
    },
    onEnter() {
      if (this.complete) {
        this.processCode();
      }
    },
    processCode() {
      this.loading = true;
      this.claimProvisioningCode({ code: this.code })
        .then((response) => {
          this.errorMessage = '';
          this.submittedCode = this.code;
          this.showDialog = false;
          this.showProgressDialog = true;
          this.statusCode = 1;
          this.checkStatus();
          // Check status every 15 seconds
          this.interval = setInterval(() => {
            this.checkStatus();
          }, 15000);
        })
        .catch((error) => {
          if (error.response.status === 400) {
            this.errorMessage = 'The code you entered may have expired or already claimed.';
          } else if (error.response.status === 403) {
            this.errorMessage = 'You do not have permission to provision devices. Please contact your organization administrator.';
          } else if (error.response.status === 420) {
            const xRatelimitReset = error.response.headers['x-ratelimit-reset'];
            this.errorMessage = `You've done that a bit too often. Try again in ${xRatelimitReset} seconds.`;
          } else {
            this.errorMessage = 'An error occurred while processing your request. Please try again.';
          }
          this.valid = false;
        })
        .finally(() => {
          this.loading = false;
          this.submitted = true;
        });
    },
    checkStatus() {
      this.getProvisioningCodeStatus({ code: this.submittedCode })
        .then((data) => {
          if (data.deviceRetrievedToken > 0) {
            this.statusCode = 2;
            clearInterval(this.interval);
          }
        })
        .catch((error) => {})
        .finally(() => {});
    },
    cleanup() {
      this.code = '';
      this.ip = '';
      this.complete = false;
      this.valid = false;
      this.submitted = false;
      this.loading = false;
    },
  },
  computed: {
    ...mapGetters({
      delegationSources: 'packages/delegations',
    }),
    hasPermission() {
      return canAccessFeature('provision-device');
    },
    hasDemoApps() {
      return this.delegationSources.find((source) => source.name === 'tdx-containers');
    },
  },
  beforeUnmount() {
    clearInterval(this.interval);
  },
  mounted() {
    this.$events.$on(`dialogs:provisioning-code-dialog:open`, () => {
      this.show();
    });
  },
  watch: {
    showProgressDialog(val) {
      if (!val) {
        console.log('clearing interval');
        clearInterval(this.interval);
      }
    },
  },
};
</script>

<style></style>
