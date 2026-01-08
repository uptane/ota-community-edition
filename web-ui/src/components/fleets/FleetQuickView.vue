<template>
  <div class="fleet-quick-view  ml-1">
    <q-card-section v-if="loadingUpdatedData" class="mnh-20em flex flex-center p-0">
      <div class="text-center text-1 p-5"><q-spinner-hourglass size="2em" color="secondary"></q-spinner-hourglass> Fetching fleet information ...</div>
    </q-card-section>
    <q-card-section v-else class="pt-2 pb-2 p-0">
      <div class="p-0 row pt-0 mt-0">
        <h5 class="m-0 pl-2 pr-2 col-12 pb-1">
          <div class="row">
            <div class="col-auto pr-2">
              <q-btn flat class="pr" color="secondary" icon="keyboard_arrow_left" @click="previousFleet">
                <tooltip>View previous fleet</tooltip>
              </q-btn>
              <q-btn flat class="pl" color="secondary" icon="keyboard_arrow_right" @click="nextFleet">
                <tooltip>View next fleet</tooltip>
              </q-btn>
            </div>
            <div class="gt-xs col">
              <div class="row w-00">
                <div class="col-auto ellipsis">
                  {{ fleet.groupName }}
                </div>
                <div class="col-auto">
                  <feature-teaser feature="manage-fleet">
                    <q-btn flat @click="showEditDialog" color="secondary" icon="edit">
                      <tooltip>Rename this fleet</tooltip>
                    </q-btn>
                  </feature-teaser>
                </div>
              </div>
            </div>
            <div class="col-auto pl-2">
              <q-btn class="absolute-top-right mt-1 mr-1" flat @click="hideDetail" icon="close">
                <tooltip>Hide detail</tooltip>
              </q-btn>
            </div>
            <div class="lt-sm col-12 text-center pt-2">{{ fleet.groupName }}</div>
          </div>
        </h5>
      </div>
      <div
        class="row pl-1 pr-0 mnh-80vh"
        style="overflow-y:auto; "
        :style="{
          height: parentHeight,
        }"
      >
        <div class="col-12 col-xl-8 mb-2">
          <fleet-information ref="fleetInfo" :fleet="fleet"></fleet-information>
        </div>

        <div class="col-12 col-xl-4">
          <div class="row">
            <div class="col-12 col-auto">
              <fleet-actions @deleted="hideDetail" :fleet="fleet" :actions="actions" @view-updated="refresh"></fleet-actions>
            </div>
          </div>
        </div>
      </div>
    </q-card-section>
  </div>
</template>

<script>
import { mapActions, mapGetters } from 'vuex';
import Loader from '../loaders/Loader';
import FleetInformation from './FleetInformation.vue';
import Tooltip from '../common/Tooltip.vue';
import FleetActions from './FleetActions.vue';

export default {
  name: 'FleetQuickView',
  components: {
    Loader,
    FleetInformation,
    Tooltip,
    FleetActions,
  },
  props: {
    parentHeight: {
      type: String,
      default: '',
    },
    fleetId: {
      type: String,
      default: '',
    },
  },
  data() {
    return {
      loadingUpdatedData: true,
      loadingPackageData: false,
      currentFleetId: '',
      fleet: {},
    };
  },
  methods: {
    ...mapActions({
      fetchFleet: 'fleets/fetchFleet',
      deleteFleet: 'fleets/deleteFleet',
      getNextFleet: 'fleets/getNext',
      getPreviousFleet: 'fleets/getPrevious',
    }),
    nextFleet() {
      const fleet = this.getNextFleet(this.fleet.id).then((fleet) => {
        this.currentFleetId = fleet.id;
        this.setup();
      });
    },
    previousFleet() {
      this.getPreviousFleet(this.fleet.id).then((fleet) => {
        this.currentFleetId = fleet.id;
        this.setup();
      });
    },
    showFullFleetDatail() {
      this.$router.push({
        name: 'fleet-detail',
        params: { fleetId: this.fleet.id },
      });
    },
    loadData() {
      this.loadingUpdatedData = true;
      this.fetchFleet({ id: this.activeId, forceFetch: true })
        .then((fleet) => {
          this.fleet = fleet;
        })
        .catch(() => {
          this.fleet = {};
        })
        .finally(() => {
          this.loadingUpdatedData = false;
        });
    },
    setup() {
      this.$events.$on('fleets:refresh', () => {
        this.loadData();
      });
      this.loadData();
    },
    showEditDialog() {
      this.$events.$emit(`dialogs:create-fleet:open`, {
        show: true,
        fleet: this.fleet || {},
      });
    },
    hideDetail() {
      this.$emit('hide', {});
    },
    refresh() {
      this.$refs.fleetInfo && this.$refs.fleetInfo.refresh();
    },
  },
  mounted() {
    this.setup();
  },
  computed: {
    ...mapGetters({
      fleets: 'fleets/fleets',
      devicesByUuid: 'devices/devicesByUuid',
    }),
    activeId() {
      return this.currentFleetId || this.fleetId;
    },
    fleetDeleteInProgress: {
      get() {
        return this.$store.getters['ui/fleetDeleteInProgress'];
      },
      set(val) {
        this.$store.commit('ui/setFleetDeleteInProgress', val);
      },
    },
    actions() {
      const actions = this.fleetDevices.length > 0 ? ['update'] : [];
      return actions.concat(['devices', 'rename', 'view', 'hibernate', 'wakeup', 'delete']);
    },
    fleetDevices() {
      return this.fleet.devices || [];
    },
  },
  watch: {
    activeId(n) {
      this.$emit('fleet-id-change', { id: this.activeId });
    },
    fleetId(n) {
      if (n) {
        this.loadData();
      }
    },
  },
};
</script>
