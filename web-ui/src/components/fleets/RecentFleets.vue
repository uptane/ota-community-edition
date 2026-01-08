<template>
  <div class="device-fleets-wrapper q-card">
    <q-card-section class="m-0  flex  ">
      <h5 class="m-0 row text-center w-100">
        <div class="col-12">
          <span class="">{{ title }}</span>
        </div>
        <div class="col-12">
          <feature-teaser feature="create-fleet">
            <q-btn @click="showDialog(null)" flat color="secondary">Add new fleet</q-btn>
          </feature-teaser>
        </div>
      </h5>
    </q-card-section>
    <q-linear-progress v-if="loading" indeterminate color="secondary" size="1px" class="" />
    <q-separator v-else class="p-0 m-0" />

    <template>
      <q-list link class="pl-0 pr-0" dense>
        <template v-if="loading">
          <template v-for="index in 7">
            <q-separator :key="index + '_recent_flt_'" class="p-0 m-0" />
            <q-item :key="index + '_recent_flt'" class="mnh-5em q-my-md">
              <q-item-section avatar>
                <q-skeleton type="QAvatar" />
              </q-item-section>

              <q-item-section>
                <q-item-label>
                  <q-skeleton height="17px" type="text" />
                </q-item-label>
                <q-item-label caption>
                  <q-skeleton height="10px" type="text" />
                </q-item-label>
                <q-item-label caption>
                  <q-skeleton height="15px" type="text" />
                </q-item-label>
              </q-item-section>
            </q-item>
          </template>
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
                    <div v-if="fleet.deviceCount === 0" class="q-item-tile sublabel">No device</div>
                    <div v-if="fleet.deviceCount > 0" class="q-item-tile sublabel opacity-40">{{ fleet.deviceCount }} {{ fleet.deviceCount === 1 ? 'device' : 'devices' }}</div>
                  </q-item-label>
                  <q-item-section v-if="fleet.deviceCount > 0" class="absolute-right" style="right: .75rem">
                    <feature-teaser class="inline-block" feature="manage-fleet-update">
                      <q-btn v-if="!fleetDeleteInProgress || fleetDeleteInProgress.id !== fleet.id" icon="publish" flat color="secondary" @click.stop="createUpdate(fleet)">
                        <tooltip>Initiate update for this fleet.</tooltip>
                      </q-btn>
                    </feature-teaser>
                  </q-item-section>
                </q-item>
              </drop>
            </template>
          </template>
          <more-indicator v-if="!loading" :data-length="fleetData.length" text="View all fleets" href="#/fleets"> </more-indicator>
        </template>
      </q-list>
    </template>
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
import Tooltip from '../common/Tooltip.vue';

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
    Tooltip,
  },
  props: {
    title: {
      type: String,
      default: 'Recent Fleets',
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
      return _.sortBy(this.$store.getters['fleets/fleets'], 'createdAt').reverse();
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
    createUpdate(fleet) {
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
      this.addDeviceToFleet(fleet.id, device.uuid)
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
          if (err.response.data.code === 'conflicting_entity') {
            this.$q.notify({
              message: `${device.deviceName} was aready added to this fleet.`,
              color: 'warning',
            });
          } else if (err.response.data.code === 'device_not_activated') {
            this.$q.notify({
              message: `${device.deviceName} must be activated to add it to this fleet.`,
              color: 'negative',
            });
          } else if (err.response.data.code === 'incompatible_hardware') {
            const hardwareId = err.response.data.hardwareId;
            this.$q.notify({
              message: `Only devices with hardware type ${hardwareId} can be added to this fleet.`,
              color: 'negative',
            });
          }
        });
    },
    goToDatail(fleet) {
      this.$router.push({
        name: 'fleet-detail',
        params: { fleetId: fleet.id },
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
      this.goToDatail(fleet);
    },
    showDialog(existing) {
      this.$events.$emit(`dialogs:create-fleet:open`, {
        show: true,
        fleet: existing || {},
      });
      gtm.logEvent('Dashboard', 'click', 'Create Fleet', null);
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
