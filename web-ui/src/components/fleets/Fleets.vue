<template>
  <div class="fleets-wrapper">
    <div
      class="row"
      :class="{
        '  q-card': layoutType !== 'cards',
      }"
    >
      <!-- BEGIN PARENT TABLE COL FOR TABLE/DETAIL ROW -->
      <div
        class="col "
        :class="{
          ' pr-1': selectedFleet && this.$q.screen.gt.sm,
        }"
      >
        <div class="row tableRowDiv" ref="tableRowDiv">
          <q-linear-progress v-if="loading" indeterminate size="2px" color="secondary" class="q-mt-sm" />
          <list-pagination v-if="!filter" :pagination.sync="pagination" :warningAcknowledged.sync="tablePaginationWarningAcknowledged" :total="total" records-per-page-label="Fleets per page"></list-pagination>
          <template v-if="fleets && fleets.length > 0">
            <template v-if="!loading && filteredFleets && filteredFleets.length > 0 && filter">
              <div class="col-12 text-center p-1 ">
                <q-icon name="info" size="1.2em"></q-icon>
                Found {{ filteredFleets.length }} {{ filteredFleets.length === 1 ? 'fleet' : 'fleets' }} matching
                <strong>"{{ filter }}"</strong>
                <q-btn flat no-caps color="primary" @click="$emit('clear-selected-fleet', {})">Show all</q-btn>
              </div>
            </template>

            <fleet-table v-if="viewType === 'table'" class="w-100" style="margin-left:.35rem" :data="fleetTableData" :columns="columns" :visible-columns="visibleColumns" :selected-fleet="selectedFleet" @row-click="showFleetDatail" :pagination.sync="pagination"></fleet-table>

            <div
              v-masonry
              :column-width="'#index_0'"
              v-if="viewType !== 'table'"
              :fit-width="true"
              transition-duration="0.8s"
              item-selector=".fleet-card-item"
              class="row"
              :class="{
                'w-100': isThinOrThickItem, //viewType ==='thin'
              }"
            >
              <div
                class="fleet-card-item "
                :class="{
                  'col-sm-12 col-md-4 col-lg-3 col-xl-2': isThinOrThickItem,
                }"
                v-for="(fleet, index) of filteredFleets"
                :key="index"
                :id="'index_' + index"
                :style="{
                  padding: '0rem',
                  'padding-right': isThinOrThickItem ? (isThickItem ? '.4rem' : '.2rem') : '0rem',
                }"
                v-masonry-tile
              >
                <!-- :style="{
              'width':$q.screen.lt.md?'100vw':'auto',
            }" -->
                <drag
                  :transfer-data="fleet"
                  :style="{
                    width: $q.screen.lt.md ? '100vw' : 'auto',
                  }"
                >
                  <fleet-item :layout-type="layoutType" :view-type="viewType" :view-size="viewSize" :fleet="fleet" :hide-icon="!!selectedFleet" :selected="selectedFleet && selectedFleet.id === fleet.id" @item-click="showFleetDatail(fleet)"></fleet-item>
                </drag>
                <div v-if="loading" class="absolute-center opacity-20 w-100 h-100 p-1 items-stretch">
                  <div class="h-100 display-block bg-black self-stretch"></div>
                </div>
              </div>
            </div>
          </template>
          <template v-if="!loading && (!fleets || fleets.length < 1)">
            <q-item>
              <q-item-label>
                <div class="q-item-tile label text-center p-2 opacity-30"><q-icon class="mr-1" name="info" size="1.2em"></q-icon> Nothing here yet</div>
              </q-item-label>
            </q-item>
          </template>
          <template v-if="!loading && (fleets || fleets.length >= 1) && (!filteredFleets || filteredFleets.length < 1) && (filter || selectedFleet)">
            <div class="w-100 ">
              <div class="w-100 flex flex-center" v-if="filter">
                <empty :message="`There is no fleet matching '${filter}'`" noIcon actionText="Show all fleets" @on-action="$emit('clear-filter', {})"></empty>
              </div>
              <div class="w-100 flex flex-center" v-else>
                <empty message="There is no fleet in currently selected fleet" noIcon actionText="Show all fleets" @on-action="$emit('clear-selected-fleet', {})"></empty>
              </div>
            </div>
          </template>
        </div>
        <!-- END PARENT TABLE COL FOR TABLE/DETAIL ROW -->
      </div>

      <!-- BEGIN PARENT TABLE COL FOR TABLE/DETAIL ROW -->
      <transition appear enter-active-class="animated slideInRight" leave-active-class="animated slideOutRight0" class="mnh-100vh">
        <q-card
          class="col-auto fleet-detail-div v-divide-left animated"
          v-if="selectedFleet && this.$q.screen.gt.sm"
          style="touch-action: none"
          :class="{
            pulse: giveAttensionToVersionsView,
          }"
          :style="{
            width: $q.screen.gt.sm && !!selectedFleet ? parsedDetailDivX + '%' : 'auto',
          }"
        >
          <resize-handle></resize-handle>
          <fleet-quick-view :parent-height="tableRowHeight" @hide="selectedFleet = null" @fleet-id-change="findSelectedFleetById($event.id)" :fleet-id="selectedFleet.id"></fleet-quick-view>
        </q-card>
      </transition>
      <!-- END PARENT TABLE COL FOR TABLE/DETAIL ROW -->

      <!-- END RENDER FOR FLEET LIST PAGE -->
    </div>
  </div>
</template>

<script>
import { mapActions, mapGetters } from 'vuex';
import { Drag } from 'vue-drag-drop';
import Loader from '../loaders/Loader';
import ListLoader from '../loaders/ListLoader';
import FleetMenu from '../menus/FleetMenu';
import FleetItem from './FleetItem';
import FleetDetail from './FleetDetail';
// import FleetItemSkeleton from '../common/FleetItemSkeleton.vue';
import MoreIndicator from '../common/MoreIndicator.vue';
import Empty from '../common/Empty.vue';
import FleetQuickView from './FleetQuickView.vue';
import interact from 'interactjs';
import ResizeHandle from '../common/ResizeHandle.vue';
import Tooltip from '../common/Tooltip.vue';
import FormattedDate from '../common/FormattedDate.vue';
import FleetTable from './FleetTable.vue';
import ListPagination from '../common/ListPagination.vue';
import { OptionsService } from 'src/services/options.service';

import { dom } from 'quasar';
const { height } = dom;
import { mapOrder } from '../../utils/Common';

export default {
  name: 'FleetList',
  components: {
    Drag,
    Loader,
    ListLoader,
    FleetMenu,
    FleetItem,
    FleetDetail,
    MoreIndicator,
    Empty,
    FleetQuickView,
    ResizeHandle,
    Tooltip,
    FormattedDate,
    FleetTable,
    ListPagination,
  },
  props: {
    title: {
      type: String,
      default: 'Fleets',
    },
    query: {
      type: String,
      default: '',
    },
    filter: {
      type: String,
      default: '',
    },
    layoutType: {
      type: String,
      default: 'cards',
    },
    viewSize: {
      type: Number,
      default: 20,
    },
    viewType: {
      type: String,
      default: 'card',
    },
    selected: {
      type: Object,
      default: () => null,
    },
  },
  data() {
    return {
      selectedFleet: null,
      detailDivX: 60,
      detailDivXTimer: 0,
      tableRowHeight: '',
      giveAttensionToVersionsView: false,
      loading: false,
      total: 0,
    };
  },
  created() {},
  updated() {
    this.$emit('updated', {});
  },
  mounted() {
    this.setupResizable();
    this.getRecords();
  },
  beforeDestroy() {},
  computed: {
    ...mapGetters({
      fleets: 'fleets/fleets',
      stateColumns: 'fleets/columns',
      stateVisibleColumns: 'fleets/visibleColumns',
      devices: 'devices/devices',
      devicesByUuid: 'devices/devicesByUuid',
      userSettings: 'ui/userSettings',
    }),
    user_settings() {
      return this.userSettings || {};
    },
    columns() {
      let columns = this.stateColumns;
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
    visibleColumns() {
      let columns = this.stateVisibleColumns;
      if (this.user_settings['fleetTableVisibleColumns']) {
        try {
          columns = JSON.parse(this.user_settings['fleetTableVisibleColumns']);
        } catch (e) {
          log('Unable to parse user saved fleets table visible columns metadata', e);
        }
      }
      return columns;
    },
    isThickItem() {
      return this.viewType === 'thick';
    },
    isThinItem() {
      return this.viewType === 'thin';
    },
    isThinOrThickItem() {
      return this.isThickItem || this.isThinItem;
    },

    fleetTableData() {
      return this.filteredFleets.map((m) => {
        const devices = m.devices || [];
        return {
          id: m.id,
          name: m.groupName,
          uuid: m.id,
          hardwareType: m.hardwareType,
          createdDate: m.createdAt,
          activatedDate: m.activatedAt,
          lastSeen: m.lastSeen,
          badge: m.badgeData,
          installedTargets: m.installedTargets,
          fleet: m,
          devices,
          device_count: m.deviceCount,
        };
      });
    },
    parsedDetailDivX() {
      const min = 40,
        max = 60;
      let w = this.detailDivX;
      if (w > max) {
        w = max;
      } else if (w < min) {
        w = min;
      }
      return w;
    },

    fleetCtxShow: {
      get() {
        return !!this.ctxFleet;
      },
      set(val) {
        if (!val) {
          this.ctxFleet = null;
        }
      },
    },
    filteredFleets() {
      const dataArray = this.fleets.filter((fleet) => {
        if (!this.filter || this.filter.length < 1) {
          return true;
        }
        const regex = new RegExp(`${this.filter}`, 'gi');
        return this.filter.length > 0 && JSON.stringify(fleet).match(regex) && this.filter;
      });

      return dataArray;
    },
    fleetDeleteInProgress: {
      get() {
        return this.$store.getters['ui/fleetDeleteInProgress'];
      },
      set(val) {
        this.$store.commit('ui/setFleetDeleteInProgress', val);
      },
    },

    pagination: {
      get() {
        const defaultPagination = {
          sort: { name: 'desc' },
          descending: true,
          page: 1,
          limit: 50,
          offset: 0,
          rowsPerPage: 0,
        };
        // Get user saved pagination settings or use default
        let pagination = { ...defaultPagination, ...OptionsService.getSavedOptionOrDefault('fleetTablePagination', defaultPagination) };
        return pagination;
      },
      set(v) {
        OptionsService.saveOption('fleetTablePagination', v);
        this.onDataRequest();
      },
    },
    limit: {
      get() {
        return this.pagination.limit;
      },
      set(v) {
        this.$set(this.pagination, 'limit', v);
      },
    },
    offset: {
      get() {
        return this.pagination.offset;
      },
      set(v) {
        this.$set(this.pagination, 'offset', v);
      },
    },
    sort: {
      get() {
        return this.pagination.sort;
      },
      set(v) {
        this.$set(this.pagination, 'sort', v);
      },
    },
    tablePaginationWarningAcknowledged: {
      get() {
        return OptionsService.getSavedOptionOrDefault('fleetTablePaginationWarningAcknowledged', false);
      },
      set(v) {
        OptionsService.saveOption('fleetTablePaginationWarningAcknowledged', v);
      },
    },
  },

  methods: {
    ...mapActions({
      fetchFleets: 'fleets/fetchFleets',
      deleteFleet: 'fleets/deleteFleet',
      saveUserSettings: 'ui/saveUserSettings',
    }),

    showFleetCreateDialog() {
      this.$events.$emit('dialogs:create-fleet:open', {
        show: true,
      });
    },
    showFleetDatail(fleet, full = false) {
      this.tableRowHeight = height(this.$refs.tableRowDiv) + 'px';
      if (!full) {
        this.$events.$emit('component:show-fleet-detail:open', fleet);
        this.selectedFleet = fleet;
      } else {
        this.$router.push({ name: 'fleet-detail', params: { fleetId: fleet.id } });
      }
    },
    findSelectedFleetById(id) {
      this.selectedFleet = this.fleets.find((f) => f.id === id);
    },
    setupResizable() {
      this.detailDivX = this.user_settings['fleetDevtailDivX'] || 60;
      interact('.fleet-detail-div')
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
          const width = event.target.parentElement.clientWidth;

          var x = width - width * (this.detailDivX / 100);
          x += event.deltaRect.left;
          const pc = ((width - x) / width) * 100;
          this.detailDivX = pc;
        });
    },
    getRecords() {
      return new Promise((resolve, reject) => {
        this.loading = true;
        this.fetchFleets({ filter: '', limit: this.limit, offset: this.offset, sort: this.sort })
          .then((fleets) => {
            this.total = fleets.total;
            resolve(fleets);
          })
          .catch((err) => {
            reject(err);
          })
          .finally(() => {
            this.loading = false;
          });
      });
    },
    queryRecords(filter) {
      this.loading = true;
      this.fetchFleets({
        filter,
        limit: this.limit,
        offset: this.offset,
        storeResults: false,
        sort: this.sort,
      })
        .then((fleets) => {
          this.deviceQueryResult = fleets.values;
        })
        .catch((err) => {})
        .finally(() => {
          this.loading = false;
        });
    },

    onDataRequest() {
      if (this.filter && this.filter.length > 0) {
        this.queryRecords(this.filter);
      } else {
        this.getRecords();
      }
    },
  },
  watch: {
    detailDivX(p) {
      setTimeout(() => {
        this.$events.$emit('fleets:redraw', {});
      }, 400);
      clearTimeout(this.detailDivXTimer);
      this.detailDivXTimer = setTimeout(() => {
        this.saveUserSettings({ fleetDevtailDivX: p });
      }, 1000);
    },
    selectedFleet(p) {
      if (p) {
        this.giveAttensionToVersionsView = true;
        setTimeout(() => {
          this.giveAttensionToVersionsView = false;
        }, 200);
        setTimeout(() => {
          this.$events.$emit('fleets:redraw', {});
        }, 800);
      } else {
        setTimeout(() => {
          this.$events.$emit('fleets:redraw', {});
        }, 1000);
      }
      this.$emit('update:selected', p);
    },
  },
};
</script>
