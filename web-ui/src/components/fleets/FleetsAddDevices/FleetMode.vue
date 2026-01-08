<template>
  <div>
    <div class="">
      <div class="no-shadow">
        <h5 class="m-0 row items-center flex flex-center">
          <div class="col">
            <span class=" faded ">Assign devices</span>
            <span class=" faded "> to </span>{{ fleet.groupName }} <span class=" faded ">fleet </span>
          </div>
          <q-btn class="col-auto" icon="close" flat v-close-popup @click="emitDone"></q-btn>
        </h5>
      </div>
      <div class="row" v-if="!loading && !processCompleted">
        <div class="col-5" v-if="$q.screen.gt.sm">
          <q-banner v-if="messageData && messageData.message" dense inline-actions class="text-white" :class="'bg-' + messageData.color">
            {{ messageData.message }}
            <template v-slot:action v-if="!messageData.persistent">
              <q-btn flat icon="close" color="white" @click="messageData = {}" />
            </template>
          </q-banner>
          <template v-if="!isEmpty(fleetDevices)">
            <div class="row q-item-tile label pt-1">
              <div class="col-auto pr-1">This fleet currently has the following devices assigned to it:</div>
            </div>
            <q-list class="mnh-30em mxh-70vh overflow-auto ">
              <q-item v-for="(device, index) in fleetDevices" :key="index" class="h-divide-top animated">
                <q-item-section avatar>
                  <q-icon v-if="fleetDevices[(device || {}).uuid] && !(itemStatus[(device || {}).uuid] || {}).loading" class="opacity-90" size="2.5rem" name="img:statics/svg/icons/soc.svg" color="secondary"></q-icon>
                  <img v-else class="opacity-90  rotate-40" style="max-width: 2.5rem" src="statics/svg/icons/soc.svg" />
                </q-item-section>
                <q-item-section>
                  <q-item-label>
                    <div class="ellipsis">
                      {{ device.deviceName }}
                    </div>
                    <div class="faded text-caption">
                      <div class="">
                        Last seen: &nbsp;
                        <timeago v-if="device.lastSeen" class=" ellipsis" :datetime="device.lastSeen" :auto-update="10"></timeago>
                        <span v-else>Never</span>
                      </div>
                    </div>
                    <div class="faded  animated rubberBand">
                      <small>
                        <current-device-fleets :device="device"></current-device-fleets>
                      </small>
                    </div>
                  </q-item-label>
                </q-item-section>
                <q-item-section side top>
                  <q-spinner-hourglass v-if="(itemStatus[device.uuid] || {}).loading" color="secondary" size="2.5rem" />

                  <q-btn v-else icon="close" flat color="secondary" :value="!!fleetDevices[device.uuid]" @click="(itemStatus[device.uuid] || {}).loading ? () => {} : itemClicked(device)">
                    <tooltip>Remove from this fleet</tooltip>
                  </q-btn>
                </q-item-section>
              </q-item>
            </q-list>
          </template>
          <q-item v-else>
            <q-item-label>
              <div class="row q-item-tile label pt-1">
                <div class="col-auto pr-1 opacity-40">This fleet does not have any device assigned to it</div>
              </div>
            </q-item-label>
          </q-item>
        </div>
        <div
          class="col pl-1"
          :class="{
            'v-divide-left-dotted': $q.screen.gt.sm,
          }"
        >
          <div class="pt-1 pb-1">
            <div class="row">
              <div class="col">
                <filter-input :placeholder="`Search devices`" v-model="filter" @input="queryDevices"></filter-input>
              </div>
            </div>
            <div class=" pt-1 flex flex-center">
              <div class="">
                <q-option-group v-model="showOnly" :options="showOnlyOptions" color="secondary" inline />
              </div>
            </div>
          </div>
          <div class="pb-2 mnh-20em">
            <div>
              <q-linear-progress v-if="fetchingDevices" indeterminate size="1px" color="secondary" class="q-ma-auto"></q-linear-progress>
              <q-separator v-else class="opacity-100" />
              <div class="mxh-50vh mxh-60vh overflow-auto ">
                <div class="p-2" v-if="!filteredDevices || filteredDevices.length < 1">
                  <empty v-if="fetchingDevices" title="Loading" message="Please wait..." no-action no-icon> </empty>
                  <span class="faded" v-else-if="filter && filter.length">There's no device matching this criterial</span>
                  <feature-teaser v-else feature="provision-device">
                    <empty message="You have not created any fleet" action-text="Provision Device" action-icon="add" no-icon @on-action="$events.$emit('dialogs:create-device:open', { show: true })"> </empty>
                  </feature-teaser>
                </div>
                <q-list separator v-else>
                  <template v-for="(device, index) of filteredDevices">
                    <q-item
                      class=""
                      :class="{
                        'hoverable clickable': deviceIsActivated(device),
                      }"
                      @click.native="(itemStatus[device.uuid] || {}).loading ? () => {} : itemClicked(device)"
                      :key="index"
                    >
                      <q-item-section avatar>
                        <q-icon v-if="fleetDevices[device.uuid] && !(itemStatus[device.uuid] || {}).loading" class="opacity-90" size="2.5rem" name="check_circle" color="secondary"></q-icon>
                        <q-icon
                          v-else-if="!deviceIsActivated(device) && !(itemStatus[device.uuid] || {}).loading"
                          class="opacity-90 animated"
                          size="2.5rem"
                          name="block"
                          color="negative"
                          :class="{
                            shakeX: (itemStatus[device.uuid] || {}).shake,
                          }"
                        >
                          <tooltip>This device is not yet activated</tooltip>
                        </q-icon>
                        <img v-else class="opacity-90  rotate-40" style="max-width: 2.5rem" src="statics/svg/icons/soc.svg" />
                      </q-item-section>
                      <q-item-section>
                        <q-item-label>
                          <div class="ellipsis">
                            {{ device.deviceName }}
                          </div>
                          <div class="faded  text-caption">
                            <div class="">
                              Last seen: &nbsp;
                              <timeago v-if="device.lastSeen" class=" ellipsis" :datetime="device.lastSeen" :auto-update="10"></timeago>
                              <span v-else>Never</span>
                            </div>
                          </div>
                          <div class="faded  animated rubberBand">
                            <small>
                              <current-device-fleets :device="device"></current-device-fleets>
                            </small>
                          </div>
                        </q-item-label>
                      </q-item-section>
                      <q-item-section avatar>
                        <q-spinner-hourglass v-if="(itemStatus[device.uuid] || {}).loading" color="secondary" size="2.5rem" />
                        <q-checkbox v-else indeterminate-value="not set" color="secondary" :value="!!fleetDevices[device.uuid]" @input="(itemStatus[device.uuid] || {}).loading ? () => {} : itemClicked(device)" />
                      </q-item-section>
                    </q-item>
                  </template>
                </q-list>
              </div>
            </div>
          </div>
          <div>
            <div class="flex justify-end">
              <q-btn icon="check" flat color="secondary" label="Done" v-close-popup @click="emitDone" />
            </div>
          </div>
        </div>
      </div>
    </div>

    <q-card-section class="p-2" v-if="loading">
      <div class="no-shadow">
        <h6 class="m-0">
          Saving changes ...
          <loader></loader>
        </h6>
      </div>
    </q-card-section>
    <q-card-section class="p-2" v-if="!loading && processCompleted">
      <div v-if="!noChanges">
        <div class="no-shadow">
          <h4 class="m-0">Completed!</h4>
        </div>
        <div class="pb-2">
          <div>
            <div class="pt-2">{{ device.deviceName }}:</div>
            <div class="pt-1" v-for="(res, index) in result" :key="index">
              <div v-if="res.added">✓ Added to {{ res.fleet.groupName }}</div>
              <div v-if="!res.added">
                ×
                <span style="text-decoration:line-through;">Removed from {{ res.fleet.groupName }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div v-if="noChanges">
        <div class="no-shadow">
          <h6 class="m-0">No changes!</h6>
        </div>
        <div class="pb-2">
          <div>
            <div class="pt-2"><span class="opacity-50">There were no changes to</span> <strong> device → fleet </strong> <span class="opacity-50"> association </span></div>
          </div>
        </div>
      </div>
      <div class="pt-1">
        <div class="flex justify-center">
          <q-btn icon="check" flat color="secondary" label="Okay" v-close-popup @click="onCancel" />
        </div>
      </div>
    </q-card-section>
  </div>
</template>

<script>
import { mapGetters, mapActions } from 'vuex';
import { required, minLength } from 'vuelidate/lib/validators';
import Loader from 'src/components/loaders/Loader';
import FilterInput from 'src/components/common/FilterInput';
import DeviceItem from 'src/components/devices/DeviceItem.vue';
import Empty from 'src/components/common/Empty.vue';
import Tooltip from 'src/components/common/Tooltip.vue';
import CurrentDeviceFleets from './CurrentDeviceFleets.vue';

export default {
  name: 'FleetMode',
  components: {
    Loader,
    FilterInput,
    DeviceItem,
    Empty,
    Tooltip,
    CurrentDeviceFleets,
  },
  props: {
    fleetId: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      show: false,
      mode: 'device', // fleet=devices-to-fleet, device=fleets-to-device
      messageData: {},
      messageTimer: 0,
      loading: false,
      filter: '',
      result: [],
      processCompleted: false,
      noChanges: false,
      showOnly: 'all',
      refreshInProgress: {
        timeoutId: 0,
        promises: [],
      },
      showOnlyOptions: [],
      itemStatus: {},
      filteredDevices: [],
      fleet: {},
      fetchingFleet: false,
      fetchingDevices: false,
    };
  },
  validations: {
    existing: {
      deviceName: { required, minLength: minLength(3) },
    },
  },
  methods: {
    ...mapActions({
      addDeviceToFleet: 'fleets/addDeviceToFleet',
      removeDeviceFromFleet: 'fleets/removeDeviceFromFleet',
      fetchFleetDevices: 'fleets/fetchFleetDevices',
      fetchDevices: 'devices/fetchDevices',
      fetchFleet: 'fleets/fetchFleet',
    }),
    isEmpty: _.isEmpty,
    createOrRenameFleet(existing) {
      this.$events.$emit(`dialogs:create-fleet:open`, {
        show: true,
        fleet: existing || {},
      });
    },
    async queryDevices() {
      this.fetchingDevices = true;
      try {
        const resp = await this.fetchDevices({ filter: this.filter, limit: 10 });
        this.filteredDevices = resp.values;
      } catch (e) {}
      this.fetchingDevices = false;
    },
    async fetchFleetData() {
      this.fetchingFleet = true;
      try {
        const fleet = await this.fetchFleet(this.fleetId);
        this.fleet = fleet;
        try {
          const devices = await this.fetchFleetDevices({ fleetId: this.fleetId });
          this.fleet.devices = devices.values || [];
        } catch (e) {
          console.error(e);
        }
      } catch (e) {
        console.error(e);
      }
      this.fetchingFleet = false;
    },
    removeFromFleet(device) {
      return new Promise((resolve, reject) => {
        this.$set(this.itemStatus[device.uuid], 'loading', true);
        this.removeDeviceFromFleet({ fleetId: this.fleet.id, deviceUuid: device.uuid, skipRefresh: true })
          .then((a) => {
            this.refreshFleets().finally(() => {
              this.notify({
                message: `${device.deviceName} was successfully removed from ${this.fleet.groupName}.`,
                color: 'positive',
              });
              this.$set(this.itemStatus[device.uuid], 'loading', false);
              resolve(a);
            });
          })
          .catch((e) => {
            this.notify({
              message: `Unable to remove ${device.deviceName} from ${this.fleet.groupName}, please try again.`,
              color: 'negative',
            });
            this.$set(this.itemStatus[device.uuid], 'loading', false);
            reject(e);
          });
      });
    },
    addToFleet(device) {
      return new Promise((resolve, reject) => {
        this.$set(this.itemStatus[device.uuid], 'loading', true);
        this.addDeviceToFleet({ fleetId: this.fleet.id, deviceUuid: device.uuid, skipRefresh: true })
          .then((data) => {
            this.refreshFleets().finally(() => {
              this.notify({
                message: `${device.deviceName} was successfully added to ${this.fleet.groupName}.`,
                color: 'positive',
              });
              this.$set(this.itemStatus[device.uuid], 'loading', false);
              resolve(data);
            });
          })
          .catch((err) => {
            if (err.code === 'conflicting_entity') {
              this.notify({
                message: `${device.deviceName} was aready added to this fleet.`,
                color: 'warning',
              });
            } else if (err.code === 'device_not_activated') {
              this.notify({
                message: `${device.deviceName} must be activated to add it to this fleet.`,
                color: 'negative',
              });
            } else if (err.code === 'incompatible_hardware') {
              const hardwareId = err.hardwareId;
              this.notify({
                message: `Only ${hardwareId} can be added to this fleet.`,
                color: 'negative',
              });
            }
            this.$set(this.itemStatus[device.uuid], 'loading', false);
            reject(err);
          });
      });
    },
    notify(data) {
      clearTimeout(this.messageTimer);
      if (this.$q.screen.gt.sm) {
        this.messageData = data;
        this.messageTimer = setTimeout(() => {
          this.messageData = {};
        }, 4000);
      } else {
        this.$q.notify(data);
      }
    },
    onCancel() {
      this.loading = false;
      this.show = false;
      this.processCompleted = false;
    },

    onShow() {
      this.loading = false;
      this.processCompleted = false;
      this.existing = { ...this.device };
      this.refreshFleets();
    },
    onHide() {
      this.fleets = [];
      this.messageData = {};
    },
    emitDone() {
      this.$emit('done', {});
    },
    refreshFleets() {
      return new Promise((resolve, reject) => {
        this.refreshInProgress.promises.push(resolve);
        if (this.refreshInProgress.timeoutId) {
          clearTimeout(this.refreshInProgress.timeoutId);
        }
        this.refreshInProgress.timeoutId = setTimeout(() => {
          this.fetchFleetData({ fleetId: this.fleetId }).finally(() => {
            this.refreshInProgress.timeoutId = null;
            this.refreshInProgress.promises.forEach((res) => res());
            this.refreshInProgress.promises = [];
          });
        }, 800);
      });
    },
    itemClicked(device) {
      const uuid = (device || {}).uuid;
      if (!this.itemStatus[uuid]) {
        this.$set(this.itemStatus, uuid, {
          loading: false,
          shake: false,
        });
      }
      if (this.deviceIsActivated(device)) {
        if (this.fleetDevices[uuid]) {
          this.removeFromFleet(device).then((a) => {});
        } else {
          this.addToFleet(device)
            .then((a) => {})
            .catch((err) => {});
        }
        return;
      }
      this.$set(this.itemStatus[uuid], 'shake', true);
      setTimeout(() => {
        this.$set(this.itemStatus[uuid], 'shake', false);
      }, 400);
    },
    deviceIsActivated(device) {
      return true; //device.deviceStatus !== 'NotSeen'
    },
  },
  mounted() {
    this.fetchFleetData();
    this.queryDevices();
  },
  computed: {
    ...mapGetters({
      fleets: 'fleets/fleets',
      fleetsById: 'fleets/fleetsById',
    }),
    adminMode: {
      get() {
        return this.$store.getters['ui/adminMode'];
      },
      set(v) {
        this.$store.commit('ui/setAdminMode', v);
      },
    },
    currentFleets() {
      return this.fleets.filter((f) => f.checked);
    },
    fleetDevices() {
      return _.keyBy((this.fleet || {}).devices || [], 'uuid');
    },
  },
  watch: {},
};
</script>
