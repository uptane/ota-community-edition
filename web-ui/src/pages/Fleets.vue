<template>
  <q-layout class="fleets-page">
    <page-wrapper>
      <!-- <h5 class="m-0 p-1 pl-2">Fleets</h5> -->
      <div class="p-0 row">
        <div class="pt-1 col-12" style="padding:.35rem">
          <div class="row items-center" color="none">
            <div :class="{ 'mb-1': $q.screen.lt.md }" class="pl-1 col-md-auto col-sm-12 col-xs-12 justify-between mr-1 m-0 p-0">
              <filter-input v-model="filter" placeholder="Filter fleets"></filter-input>
            </div>
            <q-space />
            <view-type-selector v-model="viewType" :viewSize.sync="viewSize" :columns.sync="columns" :visibleColumns.sync="visibleColumns" :views="['table']"></view-type-selector>

            <div class="row justify-between mr-1 m-0 p-0"></div>
            <q-space />

            <div class="row justify-between mr-1 q-card m-0 p-0" style="max-height: 3em;"></div>
            <div class="row justify-between mr-1 q-card m-0 p-0" style="max-height: 3em;">
              <feature-teaser feature="create-fleet">
                <q-btn @click="createFleet" flat class="p-0" color="secondary" icon="add">
                  <span class="gt-sm">&nbsp;Add fleet</span>
                </q-btn>
              </feature-teaser>
            </div>
            <div class="row justify-between mr-1 q-card m-0 p-0" style="max-height: 3em;"></div>

            <div class="row justify-between mr-1 q-card m-0 p-0 animated slideInRight" style="max-height: 3em;">
              <reload-btn :busy="reloading" :global-event="'fleets:refresh'"></reload-btn>
            </div>
            <h6 class="row justify-between q-card m-0 p-0" v-if="!showFleets && $q.screen.gt.md">
              <q-btn color="secondary" flat @click="toggleFleets" aria-label="Show fleets" icon="fas fa-layer-group" icon-left="fas fa-chevron-left">
                <tooltip>Show fleets</tooltip>
              </q-btn>
            </h6>
          </div>
          <div class="h-divide-top-dashed mt-1" style="margin-right:0rem;"></div>
          <!-- <q-separator class="opacity-10 p-0 m-0"/> -->
        </div>

        <div class="pt-0 col-12" style="padding:.0rem; padding-right: .35rem">
          <fleets :view-type="viewType" :layout-type="layoutType" :view-size="viewSize" @updated="setTitle" @clear-selected-fleet="selectFleet(null)" @clear-filter="filter = ''" @update:selected="selectFleet" ref="fleetsComp" :filter="filter"></fleets>
        </div>
      </div>
    </page-wrapper>
    <q-drawer show-if-above :breakpoint="1300" no-swipe-open :width="$q.screen.lt.xl ? 300 : parsedMenuDivX" side="right" class="right-menu-div" v-model="showFleets">
      <resize-handle></resize-handle>
      <q-scroll-area class="fit shadow-5 v-divide-left">
        <fleet-other-devices :fleet="selectedFleet"></fleet-other-devices>
      </q-scroll-area>
    </q-drawer>
  </q-layout>
</template>

<script>
import { mapActions, mapGetters } from 'vuex';
import Fleets from '../components/fleets/Fleets';
import FleetDevices from '../components/fleets/FleetDevices';
import UpdateMenu from '../components/menus/UpdateMenu';
import ReloadBtn from '../components/common/ReloadBtn.vue';
import Tooltip from '../components/common/Tooltip.vue';
import FilterInput from '../components/common/FilterInput.vue';
import interact from 'interactjs';
import ResizeHandle from '../components/common/ResizeHandle.vue';
import draggable from 'vuedraggable';
import { mapOrder } from '../utils/Common';
import gtm from '../services/gtm.service';
import Devices from '../components/devices/Devices.vue';
import FleetOtherDevices from '../components/fleets/FleetOtherDevices.vue';
import ViewTypeSelector from '../components/common/ViewTypeSelector.vue';
import PageWrapper from './PageWrapper.vue';

export default {
  name: 'PageFleets',
  components: {
    Fleets,
    FleetDevices,
    UpdateMenu,
    ReloadBtn,
    Tooltip,
    FilterInput,
    ResizeHandle,
    draggable,
    Devices,
    FleetOtherDevices,
    ViewTypeSelector,
    PageWrapper,
  },
  data() {
    return {
      filteredColumnOptions: null,
      showFleetDetail: true,
      filter: '',
      reloading: false,
      showFleets: false,
      fleetLimit: 100,
      interval: 0,
      menuDivX: 300,
      menuDivXTimer: 0,
      viewSize: 20,
      layoutType: 'cards',
      selectedFleet: null,
      _viewType: '',
    };
  },
  created() {},
  mounted() {
    this.setup();
    this.$events.$on('fleets:refresh', () => {
      this.reloadFleets();
    });
    this.$events.$on('fleets:updated', () => {
      this.redrawMasonry();
    });
    this.$events.$on('fleets:redraw', () => {
      this.redrawMasonry();
    });
  },
  updated() {},
  computed: {
    ...mapGetters({
      userSettings: 'ui/userSettings',
    }),
    user_settings() {
      return this.userSettings || {};
    },

    viewType: {
      get() {
        if (this.$data._viewType) {
          return this.$data._viewType;
        }
        let viewType = 'table';

        if (this.user_settings['fleetViewType'] !== undefined) {
          viewType = this.user_settings['fleetViewType'];
        }
        return viewType;
      },
      set(v) {
        this.$data._viewType = v;
        this.saveUserSettings({ fleetViewType: v });
      },
    },
    fleetsData() {
      return this.$store.getters['fleets/fleetsData'] || [];
    },
    columns: {
      get() {
        let columns = this.$store.getters['fleets/columns'];
        if (this.user_settings['fleetTableColumns']) {
          try {
            const colOrder = JSON.parse(this.user_settings['fleetTableColumns']);
            columns = mapOrder(columns, colOrder, 'name');
          } catch (e) {
            log('Unable to parse user saved fleets table columns metadata', e);
          }
        }
        return columns || this.$store.getters['fleets/columns'];
      },
      set(val) {
        this.saveUserSettings({ fleetTableColumns: JSON.stringify((val || []).map((a) => a.name)) });
      },
    },
    visibleColumns: {
      get() {
        let columns = this.$store.getters['fleets/visibleColumns'];
        if (this.user_settings['fleetTableVisibleColumns']) {
          try {
            columns = JSON.parse(this.user_settings['fleetTableVisibleColumns']);
          } catch (e) {
            log('Unable to parse user saved fleets table visible columns metadata', e);
          }
        }
        return columns;
      },
      set(val) {
        this.saveUserSettings({ fleetTableVisibleColumns: JSON.stringify(val) });
      },
    },

    darkTheme: {
      get() {
        return this.$store.getters['ui/isDarkTheme'];
      },
      set(val) {
        this.$store.commit('ui/setIsDarkTheme', val);
      },
    },
    isDashboardPage: {
      get() {
        return this.$store.getters['ui/isDashboardPage'];
      },
      set(val) {
        return this.$store.commit('ui/setIsDashboardPage', val);
      },
    },
    pageTitle: {
      get() {
        return this.$store.getters['ui/currentPageTitle'];
      },
      set(val) {
        return this.$store.commit('ui/setCurrentPageTitle', val);
      },
    },
    parsedMenuDivX() {
      let w = this.menuDivX;
      const min = 300,
        max = 500;
      if (w > max) {
        w = max;
      } else if (w < min) {
        w = min;
      }
      return w;
    },
  },
  methods: {
    ...mapActions({
      saveUserSettings: 'ui/saveUserSettings',
      fetchFleets: 'fleets/fetchFleets',
    }),
    setupResizable() {
      this.menuDivX = this.user_settings['rightMenuWidth'] || 300;
      interact('.right-menu-div aside.q-drawer')
        .resizable({
          // resize from all edges and corners
          edges: { left: true, right: false, bottom: false, top: false },

          modifiers: [
            // keep the edges inside the parent
            interact.modifiers.restrictEdges({
              outer: 'parent',
              endOnly: true,
            }),
          ],

          inertia: true,
        })
        .on('resizemove', (event) => {
          var target = event.target;
          var x = this.menuDivX;
          x -= event.deltaRect.left;
          this.menuDivX = Math.round(x);
        });
    },
    reloadFleets() {
      this.reloading = true;
      this.fetchFleets().finally((a) => {
        this.reloading = false;
        gtm.logEvent('Fleets Page', 'click', 'Reload Fleets', null);
      });
    },
    selectFleet(fleet) {
      this.selectedFleet = fleet;
      const query = fleet ? { fleetId: fleet.id } : null;
      this.$router
        .replace({
          name: 'fleets',
          query: query,
        })
        .catch((e) => {});
    },
    createUpdate(isFleet) {
      this.$events.$emit(`dialogs:create-fleet-update:open`, {
        show: true,
        isFleetUpdate: !!isFleet,
      });
    },
    createFleet() {
      this.$refs.fleetsComp.showFleetCreateDialog();
      gtm.logEvent('Fleets Page', 'click', 'Create Fleet', null);
    },
    toggleFleets() {
      this.showFleets = !this.showFleets;
      gtm.logEvent('Fleets Page', 'click', this.showFleets ? 'Show Fleets' : 'Hide Fleets', null);
    },
    setTitle() {
      this.pageTitle = this.selectedFleet ? `Fleets in "${this.selectedFleet.groupName}"` : 'All Fleets';
    },
    viewTypeChanged(type) {
      this.viewType = type;
      this.redrawMasonry();
      gtm.logEvent('Fleets Page', 'view change', 'Change Fleets View', type);
    },
    redrawMasonry() {
      setTimeout(() => {
        if (typeof this.$redrawVueMasonry === 'function') {
          this.$redrawVueMasonry();
        }
      }, 500);
    },
    viewSizeSliderChanged() {
      this.viewTypeChanged(this.viewType);
    },
    setup() {
      this.setTitle();
      this.setupResizable();
    },
  },
  watch: {
    menuDivX(n) {
      this.redrawMasonry();
      clearTimeout(this.menuDivXTimer);
      this.menuDivXTimer = setTimeout(() => {
        this.saveUserSettings({ rightMenuWidth: Math.round(n) });
      }, 1000);
    },
    filter(value) {
      value && gtm.logEvent('Fleets Page', 'filter', 'Filter Fleet', value);
    },
  },
};
</script>
