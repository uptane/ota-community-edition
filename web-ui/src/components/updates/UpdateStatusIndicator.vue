<template>
  <div>
    <div v-if="minimized">
      <div
        class="row items-center"
        :class="{
          'text-negative': updateStatus.key === 'failed',
          'text-default': updateStatus.key === 'success',
          'text-positive': updateStatus.key === 'updating',
          'text-info': updateStatus.key === 'scheduled',
          'text-info': updateStatus.key === 'queued',
          'opacity-40': device.deviceStatus === 'NotSeen',
        }"
      >
        <div class="col-auto">
          <q-spinner-hourglass :size="miniSpinnerSize" v-if="isInProgress" class=" q-pr-xs" :color="miniStatusColor" />
          <img :style="{ height: size, width: 'auto' }" v-if="updateStatus.key === 'updating'" :src="`/statics/svg/updating-anim.svg`" class=" q-pr-xs" />
        </div>
        <div class="col ellipsis">
          <q-icon v-if="updateStatus.key === 'scheduled'" class="text-info" name="schedule" size="1.2rem" />
          <q-icon v-if="updateStatus.key === 'success'" class="text-positive" name="verified" size="1.2rem" />
          <q-icon v-if="updateStatus.key === 'failed' || device.deviceStatus === 'Error' || device.deviceStatus === 'Failed'" class="text-negative" name="error" size="1.2rem" />
          <span :class="minTextClass"> {{ updateStatus.label }} &nbsp;</span>
        </div>
      </div>
      <tooltip>
        <div v-if="updateStatus.key === 'scheduled'" class="mxw-20em">
          {{ updateStatus.message }}
        </div>
        <div v-else class="mxw-20em" v-html="updateStatus.message"></div>
      </tooltip>
    </div>
    <div v-else @keypress.x="keyXPressed" tabindex="0" style="outline: none;">
      <div class="row items-center">
        <div class="col-auto q-ml-xs">
          <update-state-icons :size="size" :updateStatus="updateStatus" :device="device" />
        </div>
        <div class="col-auto" v-if="isPendingUpdate || isScheduled">
          <feature-teaser feature="cancel-device-pending-update">
            <q-btn flat rounded color="secondary" size="0.7rem" @click="promptForUpdateCancelation"> <q-icon name="cancel" size="1.8rem"></q-icon>&nbsp; Abort </q-btn>
          </feature-teaser>
        </div>
        <div class="col-auto" v-if="isInFlight && showInFlightCancelButton">
          <feature-teaser feature="cancel-device-pending-update">
            <q-btn flat rounded color="negative" size="0.7rem" @click="promptForInFlightUpdateCancelation"> <q-icon name="cancel" size="1.8rem"></q-icon>&nbsp; Abort </q-btn>
          </feature-teaser>
        </div>
      </div>
      <div class="row w-100">
        <div class="col-12 mt-0 mb-2 ">
          <span class="sublabel"
            ><strong>{{ updateStatus.summary }}:</strong>
            {{ updateStatus.message }}
          </span>
          <q-btn no-caps flat dense color="primary"
            >More info
            <q-popup-proxy :breakpoint="102400">
              <div
                class="q-card p-2 mxh-90vh mnw-40em"
                :class="{
                  'mnw-70em': showUpdateEvents,
                }"
                style="overflow: auto"
              >
                <div class="row">
                  <div
                    class="col"
                    :class="{
                      'v-divide-right': showUpdateEvents,
                    }"
                  >
                    <device-scheduled-update-summary v-if="isScheduled" :device="device" :title="'Scheduled Update Summary'" @close="showUpdateEvents = false"></device-scheduled-update-summary>
                    <device-pending-update-summary v-else-if="isPendingUpdate" :device="device" :title="'Pending Update Summary'" @close="showUpdateEvents = false"></device-pending-update-summary>
                    <device-update-summary v-else :device="device" :correlation-id="((updateEvent || {}).payload || {}).correlationId" :title="'Update Report Summary'" @close="showUpdateEvents = false"></device-update-summary>
                    <div class="text-center" v-if="!showUpdateEvents && !isPendingUpdate && !isScheduled">
                      <q-btn flat dense @click="showUpdateEvents = true" color="primary">
                        Show Update Events
                        <q-icon class="ml-1" name="more"></q-icon>
                      </q-btn>
                    </div>
                  </div>
                  <div
                    class="
                        col"
                    v-if="showUpdateEvents"
                  >
                    <device-update-events :correlationId="((updateEvent || {}).payload || {}).correlationId" :device="device" @close="showUpdateEvents = false" />
                  </div>
                </div>
              </div>
            </q-popup-proxy>
          </q-btn>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { mapGetters, mapActions, mapMutations } from 'vuex';
import { WS_DATA_REQUEST, DEVICE_UPDATE_STATES } from '../../constants';
import Tooltip from '../common/Tooltip.vue';
import DevicePendingUpdateSummary from '../devices/DevicePendingUpdateSummary.vue';
import DeviceScheduledUpdateSummary from '../devices/DeviceScheduledUpdateSummary.vue';
import DeviceUpdateEvents from '../devices/DeviceUpdateEvents.vue';
import DeviceUpdateSummary from '../devices/DeviceUpdateSummary.vue';
import UpdateStateIcons from './UpdateStateIcons.vue';
export default {
  name: 'UpdateStatusIndicator',
  components: {
    Tooltip,
    DeviceUpdateEvents,
    DeviceUpdateSummary,
    DevicePendingUpdateSummary,
    DeviceScheduledUpdateSummary,
    UpdateStateIcons,
  },
  props: {
    size: {
      type: String,
      default: '5rem',
    },
    device: {
      type: Object,
      default: () => {
        return {};
      },
    },
    minimized: {
      type: Boolean,
      default: false,
    },
    minTextClass: {
      type: String,
      default: '',
    },
    autoUpdateScheduledStatus: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      updateStages: DEVICE_UPDATE_STATES,
      _updateStatus: {},
      showUpdateEvents: false,
      xKeyCount: 0,
      xKeyCountTimeout: null,
      showInFlightCancelButton: false,
      userConfirmedInFlightCancelation: false,
      scheduledUpdateData: [],
      resolvedDevice: {},
    };
  },
  mounted() {
    this.resolvedDevice = { ...this.device };
    this.parseUpdateStatus();
    this.updateScheduledStatus();
    this.$events.$on('devices:partial-update', ({ deviceUuid, partialUpdateData }) => {
      if (deviceUuid !== this.device.uuid) {
        return;
      }
      const minimized = this.minimized;
      this.resolvedDevice = { ...this.resolvedDevice, ...partialUpdateData };
    });
  },
  beforeDestroy() {},
  methods: {
    ...mapActions({
      cancelUpdates: 'devices/cancelUpdates',
      cancelInFlightUpdate: 'devices/cancelInFlightUpdate',
      getScheduledUpdateStatus: 'updates/getScheduledUpdateStatus',
      cancelScheduledUpdate: 'updates/cancelScheduledUpdate',
    }),
    ...mapMutations({
      updateSingleDevice: 'devices/updateSingleDevice',
    }),
    updateScheduledStatus() {
      if (this.autoUpdateScheduledStatus) {
        this.fetchScheduledUpdateStatus();
      }
    },
    async fetchScheduledUpdateStatus() {
      try {
        const resp = await this.getScheduledUpdateStatus({ deviceUuid: this.device.uuid });
        this.scheduledUpdateData = resp;
        this.updateSingleDevice({ uuid: this.device.uuid, updateScheduled: !!this.isUpdateScheduledStage });
      } catch (err) {
        // console.error('Error fetching scheduled update status', err);
      }
      this.parseUpdateStatus();
    },
    parseUpdateStatus() {
      if (this.isUpdateScheduledStage) {
        const status = { ...this.updateStages['Scheduled'] };
        status.additionalInfo = { scheduled: true };
        this.updateStatus = { ...status };
        return;
      }
      this.updateStatus = { ...(this.updateStages[this.resolvedDevice.deviceStatus] || {}) };
    },
    requestWsUpdateData() {
      this.$store.dispatch(
        'ui/requestWsData',
        {
          deviceUuid: this.device.uuid,
          type: WS_DATA_REQUEST.DEVICE_UPDATE_INSTALLATION_EVENTS,
        },
        { root: true },
      );
      this.$store.dispatch(
        'ui/requestWsData',
        {
          deviceUuid: this.device.uuid,
          type: WS_DATA_REQUEST.DEVICE_UPDATE_INSTALLATION_HISTORY,
        },
        { root: true },
      );
    },

    async cancelUpdateRoutine() {
      try {
        if (this.isPendingUpdate || this.isScheduled) {
          await this.cancelUpdates({ deviceUuids: [this.device.uuid] });
        } // If the is already in flight, we can't cancel it unless the user confirms it. So let's check if it's in flight and also check the flag that the user has confirmed the cancellation
        else if (this.isInFlight && this.userConfirmedInFlightCancelation) {
          await this.cancelInFlightUpdate({ deviceUuid: this.device.uuid });
        }
        // If we make it here, we can assume that the update has been cancelled. We should refresh the device data to reflect the new state
        this.updateSingleDevice({ uuid: this.device.uuid, updateScheduled: false, deviceStatus: 'UpToDate' });
        this.scheduledUpdateData = [];
      } catch (err) {
        console.error('Error cancelling update', err);
      }
    },

    promptForUpdateCancelation() {
      this.$events.$emit('dialogs:confirm:open', {
        title: this.isScheduled ? `Cancel this scheduled update?` : `Cancel this pending update?`,
        message: ``,
        color: 'default',
        icon: 'block',
        yesFlat: true,
        yesClass: 'proceed',
        yesLabel: 'Yes',
        yesColor: 'secondary',
        noLabel: 'No',
        yesAction: () => {
          this.cancelUpdateRoutine();
        },
        noFlat: true,
        noAction: () => {},
      });
    },

    promptForInFlightUpdateCancelation() {
      this.$q
        .dialog({
          title: `Cancel this in-flight update?`,
          message: `WARNING: Aborting an update when it may be in progress on a device is an operation with undefined behaviour. You may cause the device to get into an inconsistent state if you do this. If you are sure you want to proceed, type the name of the device into this box:`,
          color: 'default',
          icon: 'block',
          prompt: {
            model: '',
            type: 'text',
            required: true,
            filled: true,
            standout: true,
            dense: true,
            color: 'primary',
            isValid: (val) => val && val === this.device.deviceName,
          },
          ok: {
            flat: true,
            color: 'negative',
            label: 'Yes, abort this update',
          },
          cancel: {
            flat: true,
            label: 'Cancel',
          },
        })
        .onOk(() => {
          this.userConfirmedInFlightCancelation = true;
          // Progress dialog
          let progressDialog = this.$q.dialog({
            title: `Cancelling update...`,
            message: `Please wait while we cancel the update on this device.`,
            color: 'default',
            icon: 'block',
            progress: true,
            persistent: true,
            ok: false,
            cancel: false,
          });
          // this.cancelInFlightUpdate({ deviceUuid: this.device.uuid })
          this.cancelUpdateRoutine({ deviceUuid: this.device.uuid })
            .then(() => {
              this.$q.dialog({
                title: `Update cancelled`,
                message: `The update on this device has been cancelled.`,
                icon: 'check',
                ok: {
                  flat: true,
                  label: 'Ok',
                  color: 'primary',
                },
                cancel: false,
              });
            })
            .catch((err) => {
              this.$q.dialog({
                title: `Error cancelling update`,
                message: `There was an error cancelling the update on this device. Please try again later.`,
                icon: 'block',
                ok: {
                  flat: true,
                  label: 'Close',
                  color: 'primary',
                },
                cancel: false,
              });
            })
            .finally(() => {
              progressDialog.hide();
            });
        });
    },
    keyXPressed() {
      if (!this.isInFlight) {
        return;
      }
      clearTimeout(this.xKeyCountTimeout);
      this.xKeyCountTimeout = setTimeout(() => {
        this.xKeyCount = 0;
      }, 2000);
      this.xKeyCount++;
      if (this.xKeyCount >= 5) {
        this.showInFlightCancelButton = true;
        this.xKeyCount = 0;
      }
      console.log('keyXPressed', this.xKeyCount);
    },
  },
  computed: {
    ...mapGetters({
      updateEvents: 'devices/updateInstallationEvents',
    }),
    miniStatusColor() {
      if (this.device.deviceStatus === 'NotSeen') {
        return 'grey';
      }
      return this.updateStatus.key === 'failed' ? 'negative' : this.updateStatus.key === 'success' ? 'positive' : this.updateStatus.key === 'updating' ? 'primary' : 'info';
    },
    miniSpinnerSize() {
      return '1.2rem';
    },

    updateStatus: {
      get() {
        return this.$data._updateStatus;
      },
      set(val) {
        this.$data._updateStatus = val;
      },
    },
    isUpdateScheduledStage() {
      return this.device.deviceStatus === 'UpdateScheduled';
    },
    activeScheduledUpdate() {
      return this.scheduledUpdateData && this.scheduledUpdateData.find((update) => ['Scheduled', 'Assigned', 'PartiallyCompleted'].includes(update.status));
    },
    isScheduled() {
      return !!this.isUpdateScheduledStage;
    },
    updateEvent() {
      // updateEvents was pre-sorted in decending order by date received before being stored
      let deviceEvents = (this.updateEvents || {})[this.device.uuid] || [];
      // getting the first item in the array means getting the most recent event
      let latestEvent = deviceEvents[0];
      return latestEvent;
    },
    isInProgress() {
      return this.updateStatus.key === 'queued' || this.updateStatus.key === 'updating';
    },
    isInFlight() {
      return this.updateStatus.key === 'updating';
    },
    isPendingUpdate() {
      return this.updateStatus.key === 'queued';
    },
  },

  watch: {
    device: {
      handler(newValue, oldValue) {
        if (newValue) {
          const updateStatusFn = () => {
            this.resolvedDevice = { ...newValue };
            this.parseUpdateStatus();
          };
          // If the device has changed, we need to update the status data and fetch the scheduled update status
          if ((oldValue || {}).uuid !== newValue.uuid) {
            updateStatusFn();
            this.updateScheduledStatus();
            // If device status has changed, we need to update the status data
          } else if ((oldValue || {}).deviceStatus !== newValue.deviceStatus) {
            updateStatusFn();
            // If there's a scheduled update, we need to update the status data
            if (this.isUpdateScheduledStage) {
              this.updateScheduledStatus();
            }
            // Finally, if the updateScheduled flag has changed, we need to update the scheduled status data
          } else if ((oldValue || {}).updateScheduled !== newValue.updateScheduled) {
            this.updateScheduledStatus(); // Fetch the scheduled update status
          }
        }
      },
      immediate: true,
      deep: true,
    },
  },
};
</script>
