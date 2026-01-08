<template>
  <div>
    <div class="">
      <div class="no-shadow">
        <h5 class="m-0 row items-center flex flex-center">
          <div class="col">
            <span class=" faded ">Assign </span>{{ device.deviceName }}
            <span class=" faded "> to fleet </span>
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
          <template v-if="!isEmpty(deviceFleets)">
            <div class="row q-item-tile label pt-1">
              <div class="col-auto pr-1">This devices is currently assigned to the following fleets:</div>
            </div>
            <div class="mnh-30em mxh-70vh overflow-auto ">
              <q-item v-for="(fleet, index) in deviceFleets" :key="index" class="h-divide-top animated">
                <q-item-section avatar>
                  <q-icon v-if="deviceFleets[fleet.id] && !(itemStatus[fleet.id] || {}).loading" class="opacity-90" size="2.5rem" name="img:statics/svg/icons/soc-fleet.svg" color="secondary"></q-icon>

                  <img v-else class="opacity-90  rotate-40" style="max-width: 2.5rem" src="statics/svg/icons/soc-fleet.svg" />
                </q-item-section>
                <q-item-section>
                  <q-item-label>
                    <div class="ellipsis">
                      {{ fleet.groupName }}
                    </div>
                  </q-item-label>
                </q-item-section>
                <q-item-section side top>
                  <q-spinner-hourglass v-if="(itemStatus[fleet.id] || {}).loading" color="secondary" size="2.5rem" />
                  <q-btn v-else icon="close" flat color="secondary" :value="!!deviceFleets[fleet.id]" @click="(itemStatus[fleet.id] || {}).loading ? () => {} : itemClicked(fleet)">
                    <tooltip>Remove from this fleet</tooltip>
                  </q-btn>
                </q-item-section>
              </q-item>
            </div>
          </template>
          <q-item v-else>
            <q-item-label>
              <div class="row q-item-tile label pt-1">
                <div class="col-auto pr-1 opacity-40">This device is not assigned to any fleet</div>
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
                <filter-input :placeholder="`Search fleets`" v-model="filter" @input="fetchFilteredFleets"></filter-input>
              </div>
              <div class="col-auto">
                <feature-teaser feature="create-fleet">
                  <q-btn @click="createOrRenameFleet(null)" flat icon="add" color="secondary">Add fleet</q-btn>
                </feature-teaser>
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
              <q-linear-progress v-if="fetchingFleets" indeterminate color="secondary" size="1px" />
              <q-separator v-else class="opacity-100" />
              <div class="mxh-60vh h-100h overflow-auto">
                <div class="p-2" v-if="!filteredFleets || filteredFleets.length < 1">
                  <empty v-if="fetchingFleets" title="Loading" message="Please wait..." no-action no-icon> </empty>
                  <span class="faded" v-else-if="fleets && fleets.length && filter && filter.length">There's no fleet matching this criterial</span>
                  <feature-teaser v-else feature="create-fleet">
                    <empty message="You have not created any fleet" action-text="Add Fleet" action-icon="add" no-icon @on-action="createOrRenameFleet(null)"> </empty>
                  </feature-teaser>
                </div>
                <q-list separator v-else>
                  <template v-for="(fleet, index) of filteredFleets">
                    <q-item
                      class=""
                      :class="{
                        'hoverable clickable': deviceIsActivated,
                      }"
                      @click.native="itemClicked(fleet)"
                      :key="index"
                    >
                      <q-item-section avatar>
                        <q-icon v-if="deviceFleets[fleet.id] && !(itemStatus[fleet.id] || {}).loading" class="opacity-90" size="2.5rem" name="check_circle" color="secondary"></q-icon>
                        <q-icon
                          v-else-if="!deviceIsActivated && !(itemStatus[fleet.id] || {}).loading"
                          class="opacity-90 animated"
                          size="2.5rem"
                          name="block"
                          color="negative"
                          :class="{
                            shakeX: (itemStatus[fleet.id] || {}).shake,
                          }"
                        >
                          <tooltip>This device is not yet activated</tooltip>
                        </q-icon>
                        <img v-else class="opacity-90  rotate-40" style="max-width: 2.5rem" src="statics/svg/icons/soc-fleet.svg" />
                      </q-item-section>
                      <q-item-section>
                        <q-item-label>
                          <div class="ellipsis">
                            {{ fleet.groupName }}
                          </div>
                        </q-item-label>
                      </q-item-section>
                      <q-item-section avatar>
                        <q-spinner-hourglass v-if="(itemStatus[fleet.id] || {}).loading" color="secondary" size="2.5rem" />
                        <q-checkbox v-else indeterminate-value="not set" color="secondary" :value="!!deviceFleets[fleet.id]" @input="itemClicked(fleet)" />
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
    device: {
      type: Object,
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
      fleets: [],
      deviceFleetArray: [],
      fetchingFleets: false,
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
      fetchFleets: 'fleets/fetchFleets',
      fetchDeviceFleets: 'devices/fetchDeviceFleets',
    }),
    isEmpty: _.isEmpty,
    createOrRenameFleet(existing) {
      this.$events.$emit(`dialogs:create-fleet:open`, {
        show: true,
        fleet: existing || {},
      });
    },
    removeFromFleet(fleet) {
      return new Promise((resolve, reject) => {
        this.$set(this.itemStatus[fleet.id], 'loading', true);
        this.removeDeviceFromFleet({ fleetId: fleet.id, deviceUuid: this.device.uuid, skipRefresh: true })
          .then((a) => {
            this.refreshFleets().finally(() => {
              this.notify({
                message: `${this.device.deviceName} was successfully removed from ${fleet.groupName}.`,
                color: 'positive',
              });
              this.$set(this.itemStatus[fleet.id], 'loading', false);
              resolve(a);
            });
          })
          .catch((e) => {
            this.notify({
              message: `Unable to remove ${this.device.deviceName} from ${fleet.groupName}, please try again.`,
              color: 'negative',
            });
            this.$set(this.itemStatus[fleet.id], 'loading', false);
            reject(e);
          });
      });
    },
    addToFleet(fleet) {
      return new Promise((resolve, reject) => {
        this.$set(this.itemStatus[fleet.id], 'loading', true);
        this.addDeviceToFleet({ fleetId: fleet.id, deviceUuid: this.device.uuid, skipRefresh: true })
          .then(async (data) => {
            this.refreshFleets().finally(() => {
              this.notify({
                message: `${this.device.deviceName} was successfully added to ${fleet.groupName}.`,
                color: 'positive',
              });
              this.$set(this.itemStatus[fleet.id], 'loading', false);
              resolve(data);
            });
          })
          .catch((err) => {
            if (err.code === 'conflicting_entity') {
              this.notify({
                message: `${this.device.deviceName} was aready added to this fleet.`,
                color: 'warning',
              });
            } else if (err.code === 'device_not_activated') {
              this.notify({
                message: `${this.device.deviceName} must be activated to add it to this fleet.`,
                color: 'negative',
              });
            } else if (err.code === 'incompatible_hardware') {
              const hardwareId = err.hardwareId;
              this.notify({
                message: `Only ${hardwareId} can be added to this fleet.`,
                color: 'negative',
              });
            }
            this.$set(this.itemStatus[fleet.id], 'loading', false);
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
      if (this.device.deviceStatus === 'NotSeen') {
        this.messageData = {
          message: `${this.device.deviceName} can't be added to a fleet unless it's activated`,
          color: 'negative',
          persistent: true,
        };
      }
      this.refreshFleets();
    },
    emitDone() {
      this.$emit('done', { ...this.device, fleets: this.deviceFleetArray });
    },
    fetchDeviceFleetList() {
      this.fetchDeviceFleets(this.deviceUuid).then((data) => {
        this.deviceFleetArray = data.values;
      });
    },
    onHide() {
      this.fleets = [];
      this.messageData = {};
    },
    refreshFleets() {
      return new Promise((resolve, reject) => {
        this.refreshInProgress.promises.push(resolve);
        if (this.refreshInProgress.timeoutId) {
          clearTimeout(this.refreshInProgress.timeoutId);
        }
        this.refreshInProgress.timeoutId = setTimeout(() => {
          this.fetchDeviceFleetList();
          this.fetchFilteredFleets().finally(() => {
            this.refreshInProgress.timeoutId = null;
            this.refreshInProgress.promises.forEach((res) => res());
            this.refreshInProgress.promises = [];
          });
        }, 800);
      });
    },
    async fetchFilteredFleets() {
      this.fetchingFleets = true;
      try {
        const fleets = await this.fetchFleets({ filter: this.filter, limit: 10, storeResult: false });
        this.fleets = fleets.values;
      } catch (e) {
        console.error('Error fetching fleets', e);
      }
      this.fetchingFleets = false;
    },
    getFleetDevice(fleet) {
      return this.devices.find((f) => f.uuid === fleet.devicesIds[0]) || [];
    },
    itemClicked(fleet) {
      // If the item is loading, do nothing
      if ((this.itemStatus[fleet.id] || {}).loading) {
        this.$set(this.itemStatus[fleet.id], 'shake', true);
        setTimeout(() => {
          this.$set(this.itemStatus[fleet.id], 'shake', false);
        }, 400);
        return;
      }
      if (!this.itemStatus[fleet.id]) {
        this.$set(this.itemStatus, fleet.id, {
          loading: false,
          shake: false,
        });
      }
      if (this.deviceFleets[fleet.id]) {
        this.removeFromFleet(fleet).then((a) => {});
      } else {
        this.addToFleet(fleet)
          .then((a) => {})
          .catch((err) => {});
      }
    },
  },
  mounted() {
    this.refreshFleets();
    this.fetchDeviceFleetList();
  },
  computed: {
    ...mapGetters({}),
    adminMode: {
      get() {
        return this.$store.getters['ui/adminMode'];
      },
      set(v) {
        this.$store.commit('ui/setAdminMode', v);
      },
    },
    deviceUuid() {
      return this.device.uuid;
    },
    deviceIsActivated() {
      return true; //this.device.deviceStatus !== 'NotSeen'
    },
    filteredFleets() {
      return this.fleets;
    },
    currentFleets() {
      return this.fleets.filter((f) => f.checked);
    },
    deviceFleets() {
      return _.keyBy(this.deviceFleetArray, 'id');
    },
  },
  watch: {},
};
</script>
