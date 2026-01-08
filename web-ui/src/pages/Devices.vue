<template>
  <q-layout class="devices-page">
    <page-wrapper>
      <!-- <h5 class="m-0 p-1 pl-2">Devices</h5> -->
      <div class="p-0 row">
        <div
          class="app-subheader pt-1 col-12"
          :style="{
            width: appSubHeaderWidth + 'px',
            left: appSubHeaderLeft + 'px',
          }"
        >
          <div class="row items-center" color="none">
            <div :class="{ 'mb-1': $q.screen.lt.md }" class="pl-1 col-md-auto col-sm-12 col-xs-12 justify-between mr-1 m-0 p-0">
              <filter-input :rounded="false" v-model="filter"></filter-input>
            </div>
            <q-space />

            <view-type-selector v-model="viewType" :viewSize.sync="viewSize" :columns.sync="columns" :visibleColumns.sync="visibleColumns" :views="['table', 'dense', 'relaxed']" @input="viewTypeChanged" @update:viewSize="viewSizeChanged"></view-type-selector>

            <div class="row justify-between q-card ml-1 m-0 p-0">
              <q-btn flat class="p-0" color="secondary" icon="settings">
                <tooltip class="gt-sm">&nbsp;Additional view settings</tooltip>
                <q-menu>
                  <q-list separator>
                    <q-item clickable v-ripple @click="showHibernated = !showHibernated">
                      <q-item-section>
                        <q-item-label>Show devices in hibernation </q-item-label>
                      </q-item-section>
                      <q-item-section side>
                        <q-toggle v-model="showHibernated" color="secondary" />
                      </q-item-section>
                    </q-item>
                  </q-list>
                </q-menu>
              </q-btn>
            </div>
            <q-space />

            <div class="row justify-between mr-1 q-card m-0 p-0" style="max-height: 3em;"></div>

            <div class="row justify-between mr-1 q-card m-0 p-0" style="max-height: 3em;">
              <feature-teaser feature="provision-device">
                <q-btn @click="createDevice" flat class="p-0" color="secondary" icon="add">
                  <span class="gt-md">&nbsp;Provision device</span>
                  <tooltip class="gt-md">Provision device</tooltip>
                </q-btn>
              </feature-teaser>
            </div>
            <div class="row justify-between mr-1 q-card m-0 p-0 animated slideInRight" style="max-height: 3em;">
              <reload-btn :busy="reloading" :global-event="'devices:refresh'"></reload-btn>
            </div>
            <h6 class="row justify-between q-card m-0 p-0" v-if="!showFleets">
              <q-btn color="secondary" flat @click="toggleFleets" aria-label="Fleets" label="Fleets" icon="fas fa-layer-group" icon-left="fas fa-chevron-left">
                <tooltip>Show fleets</tooltip>
              </q-btn>
            </h6>
          </div>
          <div class="h-divide-top-dashed mt-1" style="margin-right:0rem;"></div>
        </div>

        <div
          class="col-12"
          style="padding:.0rem; padding-right: .35rem; padding-bottom: 0px;"
          :style="{
            paddingTop: $q.screen.lt.md ? '8em' : '4em',
          }"
        >
          <devices :view-type="viewType" :layout-type="layoutType" :view-size="viewSize" @updated="setTitle" @clear-selected-fleet="selectFleet(null)" @clear-filter="clearFilter" :selected-fleet="selectedFleet" ref="devicesComp" :filter="(filter || '').trim()"></devices>
        </div>
      </div>
    </page-wrapper>
    <q-drawer show-if-above :breakpoint="1300" no-swipe-open :width="$q.screen.lt.md ? 0 : $q.screen.lt.xl ? 300 : parsedMenuDivX" side="right" class="right-menu-div" behavior="desktop" v-model="showFleets" style="position: fixed; top: 0px; bottom: 0px;">
      <resize-handle></resize-handle>
      <q-scroll-area class="fit shadow-5 v-divide-left">
        <device-fleets @fleet-selected="selectFleet" :limit="fleetLimit" ref="deviceGrp"></device-fleets>
      </q-scroll-area>
      <q-resize-observer @resize="onRightMenuResize" />
    </q-drawer>
  </q-layout>
</template>

<script>
import { mapActions, mapGetters } from 'vuex';
import Devices from '../components/devices/Devices';
import DeviceFleets from '../components/devices/DeviceFleets';
import UpdateMenu from '../components/menus/UpdateMenu';
import ReloadBtn from '../components/common/ReloadBtn.vue';
import Tooltip from '../components/common/Tooltip.vue';
import FilterInput from '../components/common/FilterInput.vue';
import PageWrapper from './PageWrapper.vue';

import interact from 'interactjs';
import ResizeHandle from '../components/common/ResizeHandle.vue';
import draggable from 'vuedraggable';
import { mapOrder } from '../utils/Common';
import gtm from '../services/gtm.service';
import ViewTypeSelector from '../components/common/ViewTypeSelector.vue';
import { OptionsService } from '../services/options.service';

export default {
  name: 'PageDevices',
  components: {
    Devices,
    DeviceFleets,
    UpdateMenu,
    ReloadBtn,
    Tooltip,
    FilterInput,
    ResizeHandle,
    draggable,
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
    this.$events.$on('devices:refresh', () => {
      this.reloadDevices();
    });
    this.$events.$on('devices:updated', () => {
      this.redrawMasonry();
    });
    this.$events.$on('devices:redraw', () => {
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

    showHibernated: {
      get() {
        return OptionsService.getSavedOptionOrDefault('showHibernated', false);
      },
      set(v) {
        OptionsService.saveOption('showHibernated', v);
      },
    },

    viewType: {
      get() {
        if (this.$data._viewType) {
          return this.$data._viewType;
        }
        let viewType = !this.$demoMode && this.devices.length > 10 ? 'table' : 'thick';

        if (this.user_settings['deviceViewType'] !== undefined) {
          viewType = this.user_settings['deviceViewType'];
        }
        return viewType;
      },
      set(v) {
        this.$data._viewType = v;
        OptionsService.saveOption('deviceViewType', v);
      },
    },
    devices() {
      return this.$store.getters['devices/devices'];
    },
    columns: {
      get() {
        let columns = this.$store.getters['devices/columns'];
        if (this.user_settings['deviceTableColumns']) {
          try {
            const colOrder = JSON.parse(this.user_settings['deviceTableColumns']);
            columns = mapOrder(columns, colOrder, 'name');
          } catch (e) {
            log('Unable to parse user saved devices table columns metadata', e);
          }
        }
        return columns || this.$store.getters['devices/columns'];
      },
      set(val) {
        OptionsService.saveOption('deviceTableColumns', JSON.stringify((val || []).map((a) => a.name)));
      },
    },
    visibleColumns: {
      get() {
        let columns = this.$store.getters['devices/visibleColumns'];
        if (this.user_settings['deviceTableVisibleColumns']) {
          try {
            columns = JSON.parse(this.user_settings['deviceTableVisibleColumns']);
          } catch (e) {
            log('Unable to parse user saved devices table visible columns metadata', e);
          }
        }
        return columns;
      },
      set(val) {
        this.$store.commit('devices/setVisibleColumns', val);
        OptionsService.saveOption('deviceTableVisibleColumns', JSON.stringify(val));
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
    filterWidth() {
      return this.$store.getters['ui/filterWidth'];
    },
    appSubHeaderLeft() {
      return this.$store.getters['ui/appSubHeaderLeft'];
    },
    appSubHeaderWidth() {
      return this.$store.getters['ui/appSubHeaderWidth'];
    },
    appSubHeaderHeight() {
      return this.$store.getters['ui/appSubHeaderHeight'];
    },
    appSubHeaderPaddingRight() {
      return this.$store.getters['ui/appSubHeaderPaddingRight'];
    },
  },
  methods: {
    ...mapActions({
      fetchDevices: 'devices/fetchDevices',
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
    reloadDevices() {
      this.reloading = true;
      this.fetchDevices().finally((a) => {
        this.reloading = false;
        gtm.logEvent('Devices Page', 'click', 'Reload Devices', null);
      });
    },
    selectFleet(fleet) {
      this.selectedFleet = fleet;
      const query = fleet ? { fleetId: fleet.id } : null;
      this.$router
        .replace({
          name: 'devices',
          query: query,
        })
        .catch((e) => {});
    },
    createUpdate(isFleet) {
      this.$events.$emit(`dialogs:create-device-update:open`, {
        show: true,
        isFleetUpdate: !!isFleet,
      });
    },
    createDevice() {
      this.$refs.devicesComp.showDeviceCreateDialog();
      gtm.logEvent('Devices Page', 'click', 'Create Device', null);
    },
    toggleFleets() {
      this.showFleets = !this.showFleets;
      gtm.logEvent('Devices Page', 'click', this.showFleets ? 'Show Fleets' : 'Hide Fleets', null);
    },
    setTitle() {
      this.pageTitle = this.selectedFleet ? `Devices in "${this.selectedFleet.groupName}"` : 'All Devices';
    },
    clearFilter() {
      this.filter = null;
    },
    viewTypeChanged(type) {
      this.viewType = type;
      this.redrawMasonry();
      gtm.logEvent('Devices Page', 'view change', 'Change Devices View', type);
    },
    redrawMasonry() {
      setTimeout(() => {
        if (typeof this.$redrawVueMasonry === 'function') {
          this.$redrawVueMasonry();
        }
      }, 500);
    },
    viewSizeChanged() {
      this.viewTypeChanged(this.viewType);
    },
    onRightMenuResize(size) {
      this.$store.commit('ui/setRightMenuSize', size);
    },
    setup() {
      this.$store.commit('devices/setDeviceListRefreshRate', 10);
      this.$store.commit('devices/setDeviceRefreshRate', -1);
      this.setTitle();
      this.setupResizable();
    },
  },
  watch: {
    menuDivX(n) {
      this.redrawMasonry();
      clearTimeout(this.menuDivXTimer);
      this.menuDivXTimer = setTimeout(() => {
        OptionsService.saveOption('rightMenuWidth', Math.round(n));
      }, 1000);
    },
    filter(value) {
      value && gtm.logEvent('Devices Page', 'filter', 'Filter Device', value);
    },
  },
};
</script>
