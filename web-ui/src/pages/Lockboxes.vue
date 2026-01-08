<template>
  <q-layout class="updates-page">
    <q-page-container class>
      <q-page class>
        <!-- <h5 class="m-0 p-1 pl-2">Devices</h5> -->
        <div class="p-0 row">
          <div class="pt-1 col-12" style="padding:.35rem">
            <div class="row items-center" color="none">
              <div :class="{ 'mb-1': $q.screen.lt.md }" class="pl-1 col-md-auto col-sm-12 col-xs-12 justify-between mr-1 m-0 p-0">
                <filter-input v-model="filter"></filter-input>
              </div>
              <q-space />
              <view-type-selector v-model="viewType" :viewSize.sync="viewSize" :columns.sync="columns" :visibleColumns.sync="visibleColumns" :views="['table', 'dense', 'relaxed']" @input="viewTypeChanged" @update:viewSize="viewSizeChanged"></view-type-selector>
              <div class="row justify-between mr-1 m-0 p-0">
                <q-card class="ml-1 q-pa-sm">
                  <q-checkbox dense color="secondary" v-model="hideRevoked">
                    <span class="text-secondary">HIDE REVOKED</span>
                  </q-checkbox>
                </q-card>
              </div>
              <q-space />

              <div class="row justify-between mr-1 q-card m-0 p-0" style="max-height: 3em;"></div>
              <div class="row justify-between mr-1 q-card m-0 p-0" style="max-height: 3em;">
                <q-btn flat class="p-0" color="secondary" icon="help" type="a" href="https://developer.toradex.com/torizon/how-to/torizon-updates/first-steps-with-secure-offline-updates" target="_blank">
                  <span class="gt-sm">&nbsp;What is this?</span>
                </q-btn>
              </div>
              <div class="row justify-between mr-1 q-card m-0 p-0" style="max-height: 3em;">
                <q-btn @click="createUpdate" flat class="p-0" color="secondary" icon="add">
                  <span class="gt-sm">&nbsp;Define Lockbox</span>
                </q-btn>
              </div>
              <div class="row justify-between mr-1 q-card m-0 p-0 animated slideInRight" style="max-height: 3em;">
                <reload-btn :busy="reloading" :global-event="'devices:refresh'"></reload-btn>
              </div>
            </div>
            <div class="h-divide-top-dashed mt-1" style="margin-right:0rem;"></div>
            <!-- <q-separator class="opacity-10 p-0 m-0"/> -->
          </div>

          <div class="pt-0 col-12" style="padding:.0rem; padding-right: .35rem">
            <lockbox-list :view-type="viewType" :layout-type="layoutType" :view-size="viewSize" @updated="setTitle" @clear-selected-fleet="selectFleet(null)" @clear-filter="filter = ''" :selected-fleet="selectedFleet" :hide-revoked="hideRevoked" ref="devicesComp" :filter="filter" />
          </div>
        </div>
      </q-page>
    </q-page-container>
  </q-layout>
</template>

<script>
import { mapActions, mapGetters } from 'vuex';
import Devices from '../components/devices/Devices';
import Fleets from '../components/fleets/Fleets';
import UpdateMenu from '../components/menus/UpdateMenu';
import ReloadBtn from '../components/common/ReloadBtn.vue';
import Tooltip from '../components/common/Tooltip.vue';
import FilterInput from '../components/common/FilterInput.vue';
import interact from 'interactjs';
import ResizeHandle from '../components/common/ResizeHandle.vue';
import draggable from 'vuedraggable';
import { mapOrder } from '../utils/Common';
import LockboxList from '../components/updates/LockboxList.vue';
import ViewTypeSelector from '../components/common/ViewTypeSelector.vue';
export default {
  name: 'PageDevices',
  components: {
    Devices,
    Fleets,
    UpdateMenu,
    ReloadBtn,
    Tooltip,
    FilterInput,
    ResizeHandle,
    draggable,
    LockboxList,
    ViewTypeSelector,
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

    viewType: {
      get() {
        if (this.$data._viewType) {
          return this.$data._viewType;
        }
        let viewType = !this.$demoMode && this.updatesData.length > 10 ? 'table' : 'thick';

        if (this.user_settings['updateViewType'] !== undefined) {
          viewType = this.user_settings['updateViewType'];
        }
        return viewType;
      },
      set(v) {
        this.$data._viewType = v;
        this.saveUserSettings({ updateViewType: v });
      },
    },
    updatesData() {
      return this.$store.getters['updates/updates'];
    },
    columns: {
      get() {
        let columns = this.$store.getters['updates/columns'];
        if (this.user_settings['updateTableColumns']) {
          try {
            const colOrder = JSON.parse(this.user_settings['updateTableColumns']);
            columns = mapOrder(columns, colOrder, 'name');
          } catch (e) {
            log('Unable to parse user saved devices table columns metadata', e);
          }
        }
        return columns || this.$store.getters['updates/columns'];
      },
      set(val) {
        this.saveUserSettings({ updateTableColumns: JSON.stringify((val || []).map((a) => a.name)) });
      },
    },
    visibleColumns: {
      get() {
        let columns = this.$store.getters['updates/visibleColumns'];
        if (this.user_settings['updatesTableVisibleColumns']) {
          try {
            columns = JSON.parse(this.user_settings['updatesTableVisibleColumns']);
          } catch (e) {
            log('Unable to parse user saved devices table visible columns metadata', e);
          }
        }
        return columns;
      },
      set(val) {
        this.$store.commit('updates/setVisibleColumns', val);
        this.saveUserSettings({ updateTableVisibleColumns: JSON.stringify(val) });
      },
    },
    hideRevoked: {
      get() {
        return this.user_settings['updatesTableHideRevoked'] || false;
      },
      set(val) {
        this.saveUserSettings({ updatesTableHideRevoked: val });
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
      fetchUpdates: 'updates/fetchUpdates',
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
      this.fetchUpdates().finally((a) => {
        this.reloading = false;
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
      this.$events.$emit(`dialogs:create-lockbox:open`, {
        show: true,
      });
    },
    createDevice() {
      this.$refs.devicesComp.showDeviceCreateDialog();
    },
    toggleFleets() {
      this.showFleets = !this.showFleets;
    },
    setTitle() {
      this.pageTitle = 'Lockboxes';
    },
    viewTypeChanged(type) {
      this.viewType = type;
      this.redrawMasonry();
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
        this.saveUserSettings({ rightMenuWidth: Math.round(n) });
      }, 1000);
    },
  },
};
</script>
