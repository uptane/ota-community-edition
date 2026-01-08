<template>
  <div
    class="device-fleets-wrapper"
    :class="{
      'q-card': isDashboardPage,
    }"
  >
    <q-card-section class="m-0 text-h5 flex justify-between items-center ">
      <div class="m-0 row w-100">
        <div class="col text-center">
          <span class="pr-1">{{ title }}</span>
        </div>
      </div>
    </q-card-section>
    <q-separator />
    <q-card-section class="m-0 flex justify-between items-center ">
      <div class="row">
        <div class="col" v-if="viewType !== 'list'">
          <filter-input v-model="filter"></filter-input>
        </div>
        <div class="col-auto"></div>
        <feature-teaser feature="create-fleet">
          <q-btn @click="showDialog(null)" flat icon="add" color="secondary">Add fleet</q-btn>
        </feature-teaser>
      </div>
    </q-card-section>
    <q-separator class="m-0" v-if="!isDashboardPage && (fleetData || []).length > 0" style="padding-top:.0em" />

    <!-- <q-card-main> -->
    <template v-if="viewType !== 'list'">
      <!-- <q-separator class="p-0 m-0"/> -->
      <q-list
        class="pl-0 pr-0 "
        :class="{
          'bg-transparent': !isDashboardPage,
        }"
        dense
      >
        <template v-if="loading">
          <!-- <q-separator class="p-0 m-0"/> -->
          <q-item class>
            <q-item-label>
              <list-loader />
            </q-item-label>
          </q-item>
        </template>

        <template>
          <template v-for="(fleet, index) of filteredFleets || []">
            <template v-if="index < limit">
              <drop @drop="deviceAdded(fleet, ...arguments)" @dragover="draggedOver(...arguments)" @dragleave="dragLeave(...arguments)" :key="index">
                <q-separator class="p-0 m-0" :key="index + '_t'" />

                <q-item clickable class="pt-2 pb-2 pl-3 pr-3 hoverable" :class="{ selected: (selectedFleet || {}).id === fleet.id }" @click.native="clicked(fleet)">
                  <template>
                    <q-item-section avatar>
                      <q-icon class="opacity-65  rotate-40" size="2rem" name="img:statics/svg/icons/soc-fleet.svg" color="primary"> </q-icon>
                    </q-item-section>
                    <q-item-section>
                      <q-item-label>
                        <div class="q-item-tile label ellipsis">{{ fleet.groupName }}</div>
                        <div v-if="(fleet.devices || {}).total === 0" class="q-item-tile sublabel opacity-40">No device</div>
                        <div v-if="(fleet.devices || {}).total > 0" class="q-item-tile sublabel opacity-40">
                          {{ (fleet.devices || {}).total }} {{ (fleet.devices || {}).total === 1 ? 'device' : 'devices' }}
                          <!-- <div
                            v-if="getFleetDevice(fleet).hardwareType"
                            dense
                            class="text-italic"
                          > ({{getFleetDevice(fleet).hardwareType}})</div> -->
                        </div>
                      </q-item-label>
                    </q-item-section>
                    <q-item-section v-if="fleetViewData[fleet.id] || fleetInProcess.id === fleet.id">
                      <loader color="secondary"></loader>
                    </q-item-section>
                  </template>

                  <q-item-section avatar @click.native="createUpdate(fleet, $event)">
                    <!-- <q-menu  auto-close >
                      <fleet-menu @show-devices="clicked(fleet)" :fleet="fleet"></fleet-menu>
                    </q-menu> -->
                    <q-btn v-if="(fleet.devices || {}).total > 0 && (!fleetDeleteInProgress || fleetDeleteInProgress.id !== fleet.id)" icon="publish" flat color="secondary"></q-btn>
                    <loader v-if="fleetDeleteInProgress && fleetDeleteInProgress.id === fleet.id"></loader>
                  </q-item-section>
                </q-item>
              </drop>
            </template>
          </template>
        </template>
        <template v-if="!loading">
          <q-separator class="m-0" v-if="(fleetData || []).length > 0" style="padding-top:.0em" />
          <q-item
            link
            @click.native="clicked(null)"
            class="pt-2 pb-2 pl-3 pr-3 flex flex-center"
            :class="{
              'bg-transparent': !isDashboardPage,
            }"
          >
            <div class="row w-100 items-center">
              <div avatar class="col-auto pr-2">
                <q-icon class="opacity-65  rotate-40" size="2rem" name="img:statics/svg/icons/soc-fleet.svg"></q-icon>
              </div>
              <div class="col q-item-tile label">All devices</div>
            </div>
          </q-item>
        </template>
      </q-list>
    </template>

    <template v-if="viewType === 'list'">
      <q-list link class="pl-0 pr-0" dense>
        <template v-if="loading">
          <q-separator class="p-0 m-0" />
          <q-item>
            <q-item-label>
              <list-loader />
            </q-item-label>
          </q-item>
        </template>

        <template v-if="!loading && !fleetData.length">
          <q-separator class="p-0 m-0" />
          <q-item class>
            <q-item-label>
              <div class=" opacity-30 q-item-tile label text-center p-2">
                <q-icon name="info" size="1.5rem" />
                <span class="pl-2">Nothing here yet</span>
              </div>
            </q-item-label>
          </q-item>
        </template>

        <template>
          <template v-for="(fleet, index) of fleetData || []">
            <template v-if="index < limit">
              <q-separator class="p-0 m-0" :key="index + '_t'" />
              <drop @drop="deviceAdded(fleet, ...arguments)" @dragover="draggedOver(...arguments)" @dragleave="dragLeave(...arguments)" :key="index">
                <q-item link @click.native="clicked(fleet)" class="p-1 hoverable dash-list-item">
                  <q-item-section avatar>
                    <img class="opacity-65  rotate-40" style="max-width: 2.5rem" src="statics/svg/icons/soc-fleet.svg" />
                  </q-item-section>

                  <q-item-label>
                    <div class="q-item-tile label">{{ fleet.groupName }}</div>
                    <div v-if="(fleet.devices || {}).total === 0" class="q-item-tile sublabel">No device</div>
                    <div v-if="(fleet.devices || {}).total > 0" class="q-item-tile sublabel opacity-40">{{ (fleet.devices || {}).total }} {{ (fleet.devices || {}).total === 1 ? 'device' : 'devices' }}</div>
                  </q-item-label>
                  <q-item-section v-if="(fleet.devices || {}).total > 0" class="absolute-right" style="right: .75rem" @click.native="createUpdate(fleet, $event)">
                    <q-btn v-if="!fleetDeleteInProgress || fleetDeleteInProgress.id !== fleet.id" icon="publish" flat color="secondary"></q-btn>
                  </q-item-section>
                </q-item>
              </drop>
            </template>
          </template>
          <more-indicator v-if="!loading" :data-length="fleetData.length" text="View all fleets" href="#/fleets"> </more-indicator>
        </template>
      </q-list>
    </template>
    <!-- </q-card-main> -->
  </div>
</template>

<script>
import Vue from 'vue';
import { mapGetters, mapActions } from 'vuex';
import CreateFleetDialog from './CreateFleetDialog';
import Loader from '../loaders/Loader';
import ListLoader from '../loaders/ListLoader';
import FleetMenu from '../menus/FleetMenu';
import { Drop } from 'vue-drag-drop';
import MoreIndicator from '../common/MoreIndicator.vue';
import FilterInput from '../common/FilterInput';
import gtm from '../../services/gtm.service';

export default {
  name: 'ComponentDevices',
  components: {
    CreateFleetDialog,
    FleetMenu,
    Loader,
    ListLoader,
    MoreIndicator,
    Drop,
    FilterInput,
  },
  props: {
    title: {
      type: String,
      default: 'Fleets',
    },
    viewType: {
      type: String,
      default: 'cards',
    },
    query: {
      type: String,
      default: '',
    },
    limit: {
      type: Number,
      default: 50,
    },
    selectedFleet: {
      type: Object,
      default: () => {},
    },
  },
  data() {
    return {
      showCreateOrModify: false,
      fleetViewData: {},
      filter: '',
    };
  },
  created() {},
  mounted() {
    this.getFleets();
  },
  computed: {
    ...mapGetters({
      devices: 'devices/devices',
    }),
    fleetInProcess() {
      return this.$store.getters['ui/fleetInProcess'];
    },
    fleetData() {
      return this.$store.getters['fleets/fleets'];
    },
    isDashboardPage: {
      get() {
        return this.$store.getters['ui/isDashboardPage'];
      },
      set(val) {
        this.$store.commit('ui/setIsDashboardPage', val);
      },
    },
    loading: {
      get() {
        return this.$store.getters['ui/loadingFleets'];
      },
      set(val) {
        this.$store.commit('ui/setLoadingFleets', val);
      },
    },
    fleetDeleteInProgress: {
      get() {
        return this.$store.getters['ui/fleetDeleteInProgress'];
      },
      set(val) {
        this.$store.commit('ui/setfleetDeleteInProgress', val);
      },
    },
    filteredFleets() {
      const dataArray = this.fleetData.filter((fleet) => {
        if (!this.filter || this.filter.length < 1) return true;
        const regex = new RegExp(`${this.filter}`, 'gi');
        return (fleet.groupName || '').match(regex) || (fleet.id || '').match(regex);
      });

      return dataArray;
    },
  },
  watch: {
    selectedFleet(n) {
      if (!n) {
        this.$router.replace(this.$route.path).catch((e) => {});
      }
    },
  },
  methods: {
    ...mapActions({
      fetchFleets: 'fleets/fetchFleets',
      addDeviceToFleet: 'fleets/addDeviceToFleet',
    }),
    getFleetDevice(fleet) {
      return this.devices.find((f) => f.uuid === fleet.devices.values[0]) || [];
    },
    onPageLoad() {
      if (this.$route.query.fleetId && this.fleetData && this.fleetData.length) {
        const fleet = this.fleetData.find((a) => a.id === this.$route.query.fleetId);
        if (fleet) {
          this.clicked(fleet);
        }
      }
    },
    getFleets() {
      this.loading = true;
      this.fetchFleets()
        .then((fleetData) => {
          this.onPageLoad();
          this.loading = false;
        })
        .catch((err) => {
          this.onPageLoad();
          this.loading = false;
        });
      // this.getUnfleeted();
    },
    showMenu(fleet, $event) {
      $event.stopPropagation();
      // fleet.showMenu = true;
      // this.$refs['menu_'+fleet.id].show();
    },
    createUpdate(fleet, $event) {
      $event.stopPropagation();
      this.$events.$emit(`dialogs:create-device-update:open`, {
        show: true,
        fromFleet: true,
        isFleetUpdate: true,
        update: { fleet },
      });
    },
    deviceAdded(fleet, device, transferData, nativeEvent) {
      this.removeDropHighlight(transferData.target);
      this.addUpdateHighlight(transferData.target);
      Vue.set(this.fleetViewData, fleet.id, true);
      this.addDeviceToFleet({ fleetId: fleet.id, deviceUuid: device.uuid })
        .then((data) => {
          this.removeUpdateHighlight(transferData.target);
          this.$q.notify({
            message: `${device.deviceName} was successfully added to ${fleet.groupName}.`,
            color: 'positive',
          });
          Vue.set(this.fleetViewData, fleet.id, false);
          this.fetchFleets().catch((e) => {});
        })
        .catch((err) => {
          Vue.set(this.fleetViewData, fleet.id, false);
          this.removeUpdateHighlight(transferData.target);
          if (err.code === 'conflicting_entity') {
            this.$q.notify({
              message: `${device.deviceName} was aready added to this fleet.`,
              color: 'warning',
            });
          } else if (err.code === 'device_not_activated') {
            this.$q.notify({
              message: `${device.deviceName} must be activated to add it to this fleet.`,
              color: 'negative',
            });
          } else if (err.code === 'incompatible_hardware') {
            const hardwareId = err.hardwareId;
            this.$q.notify({
              message: `Only ${hardwareId} can be added to this fleet.`,
              color: 'negative',
            });
          }
        });
    },
    draggedOver(myArg, transferData, nativeEvent) {
      this.addDropHighlight(transferData.target);
    },
    dragLeave(myArg, transferData, nativeEvent) {
      this.removeDropHighlight(transferData.target);
    },
    removeDropHighlight(target) {
      this.$jq(target)
        .closest('.q-item')
        .removeClass('dragged-over');
    },
    addDropHighlight(target) {
      this.removeUpdateHighlight(target);
      this.$jq(target)
        .closest('.q-item')
        .addClass('dragged-over');
    },
    addUpdateHighlight(target) {
      this.$jq(target)
        .closest('.q-item')
        .addClass('dragged-dropped');
    },
    removeUpdateHighlight(target) {
      this.$jq(target)
        .closest('.q-item')
        .removeClass('dragged-dropped');
    },
    clicked(fleet) {
      this.selectFleet(fleet);
      const query = fleet ? { fleetId: fleet.id } : null;
      this.$router
        .replace({
          name: 'devices',
          query: query,
        })
        .catch((e) => {});
    },
    showDialog(existing) {
      this.$events.$emit(`dialogs:create-fleet:open`, {
        show: true,
        fleet: existing || {},
      });
      gtm.logEvent('Devices Page', 'click', 'Create Fleet', null);
    },
    selectFleet(fleet) {
      let selectedFleet = null;
      if (!fleet) {
        selectedFleet = null;
      } else if (!this.selectedFleet || this.selectedFleet.id !== fleet.id) {
        selectedFleet = fleet;
      } else {
        selectedFleet = null;
      }
      this.$emit('fleet-selected', selectedFleet);
    },
  },
};
</script>
