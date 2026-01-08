<template>
  <div class="devices-wrapper" ref="devicesWrapper" style="padding-bottom: 4em">
    <div
      class="row"
      v-if="layoutType === 'cards'"
      :class="{
        '  q-card': layoutType !== 'cards',
      }"
    >
      <!-- BEGIN PARENT TABLE COL FOR TABLE/DETAIL ROW -->
      <div
        class="col "
        :class="{
          ' pr-1': selectedDevice && this.$q.screen.gt.sm,
        }"
      >
        <q-resize-observer @resize="onDevicesDivResize" />
        <div class="row tableRowDiv" ref="tableRowDiv">
          <q-linear-progress v-if="loading" indeterminate size="2px" color="secondary" class="q-mt-sm" />
          <q-card
            class="device-pagination mt-1 fixed-bottom-right shadow-7"
            :style="{
              'margin-right': paginationRightPadding,
              'z-index': 1000,
            }"
          >
            <q-linear-progress v-if="loading" indeterminate size="2px" color="secondary" class="" />
            <list-pagination v-if="!filter" :pagination.sync="pagination" :warningAcknowledged.sync="deviceTablePaginationWarningAcknowledged" :total="total" records-per-page-label="Devices per page"></list-pagination>
          </q-card>
          <template v-if="devicesData && devicesData.length > 0">
            <template v-if="filter">
              <template v-if="loading">
                <div class="col-12 text-center p-1 ">
                  <q-spinner-hourglass size="2em" color="secondary"></q-spinner-hourglass>
                  Looking for devices matching
                  <strong>" {{ filter }}"</strong>
                  <q-btn flat no-caps color="primary" @click="$emit('clear-filter', {})">Clear filter</q-btn>
                </div>
              </template>
              <template v-else-if="filteredDevices && filteredDevices.length > 0">
                <div class="col-12 text-center p-1 ">
                  <q-icon name="info" size="1.2em"></q-icon>
                  Found {{ filteredDevices.length }}
                  {{ filteredDevices.length === 1 ? 'device' : 'devices' }}
                  matching
                  <strong>"{{ filter }}"</strong>
                  <q-btn flat no-caps color="primary" @click="$emit('clear-filter', {})">Clear filter</q-btn>
                </div>
              </template>
            </template>
            <template v-else-if="!loading && filteredDevices && filteredDevices.length > 0 && selectedFleet">
              <div class="col-12 text-center p-1 ">
                <q-icon name="info" size="1.2em"></q-icon>
                Showing {{ filteredDevices.length }} {{ filteredDevices.length === 1 ? 'device' : 'devices' }} in fleet
                <strong>"{{ selectedFleet.groupName }}".</strong>
                <q-btn flat no-caps color="primary" @click="$emit('clear-selected-fleet', {})">Show all</q-btn>
              </div>
            </template>

            <device-table
              v-if="viewType === 'table'"
              class="w-100"
              style="margin-left:.35rem"
              :rows="deviceTableData"
              :columns="columns"
              :visible-columns="visibleColumns"
              :selected-device="selectedDevice"
              :pagination.sync="pagination"
              @row-click="showDeviceDatail"
              @row-dblclick="showFullDeviceDatail"
            ></device-table>

            <div
              v-masonry
              :column-width="'#index_0'"
              v-if="viewType !== 'table'"
              :fit-width="true"
              transition-duration="0.8s"
              item-selector=".device-card-item"
              class="row"
              :class="{
                'w-100': isThinOrThickItem, //viewType ==='thin'
              }"
            >
              <div
                class="device-card-item "
                :class="{
                  'col-sm-12 col-md-4 col-lg-3 col-xl-2': isThinOrThickItem,
                }"
                v-for="(device, index) of filteredDevices"
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
                <drag :transfer-data="device" :style="{ width: dragStyleWidth }">
                  <device-item :layout-type="layoutType" :view-type="viewType" :view-size="viewSize" :device="device" :hide-icon="!!selectedDevice" :selected="selectedDevice && selectedDevice.uuid === device.uuid" @item-click="showDeviceDatail(device)"></device-item>
                </drag>
              </div>
            </div>
          </template>
          <div v-if="loading && (!devicesData || devicesData.length < 1)" class="full-width">
            <table-skeleton v-if="viewType === 'table'" :rows="20"> </table-skeleton>
            <div class="row q-gutter-md p-0 m-0" v-else>
              <card-skeleton v-for="n in 10" :key="n + '_card_item_skl'" class="col-sm-12 col-md-4 col-lg-3 col-xl-2"></card-skeleton>
            </div>
          </div>
          <template v-if="showProvisioningVideo">
            <div class="flex flex-center w-100">
              <div class="w-100 mt-2">
                <div class="w-70vw h-50-vh mxw-60em m-auto">
                  <q-video :ratio="16 / 9" src="https://www.youtube.com/embed/5MDNIC3KBCk?rel=0&t=34" />
                </div>
              </div>
              <div class="mt-2 text-center">
                <div class="text-h4">Provisioning your first device</div>
                <div class="text-1">
                  This is the first step for you to start your project
                </div>

                <div class="p-1" id="addFirstDeviceBtnDiv">
                  <q-btn ref="addDeviceBtn" id="addFirstDeviceBtn" class="mt-2 clipped-element" color="primary" label="Provision device" icon="add" @click="showDeviceProvisioning" />
                </div>
              </div>
            </div>
          </template>
          <template v-if="!loading && (devicesData || devicesData.length >= 1) && (!filteredDevices || filteredDevices.length < 1) && (filter || selectedFleet)">
            <div class="w-100 ">
              <div class="w-100 flex flex-center" v-if="filter">
                <empty
                  :message="`There is no device matching '${filter}'`"
                  noIcon
                  actionText="Show all devices"
                  @on-action="
                    $emit('clear-filter', {});
                    $emit('clear-selected-fleet', {});
                  "
                ></empty>
              </div>
              <div class="w-100 flex flex-center" v-else>
                <empty message="There is no device in currently selected fleet" noIcon actionText="Show all devices" @on-action="$emit('clear-selected-fleet', {})"></empty>
              </div>
            </div>
          </template>
        </div>
        <!-- END PARENT TABLE COL FOR TABLE/DETAIL ROW -->
      </div>

      <!-- BEGIN PARENT TABLE COL FOR TABLE/DETAIL ROW -->
      <transition appear enter-active-class="animated slideInRight" leave-active-class="animated slideOutRight0" class="mnh-100vh">
        <q-card
          class="col-auto device-detail-div v-divide-left animated h-100vh"
          v-if="selectedDevice && this.$q.screen.gt.sm"
          style="touch-action: none"
          :class="{
            pulse: giveAttensionToVersionsView,
          }"
          :style="{
            width: $q.screen.gt.sm && !!selectedDevice ? parsedDetailDivX + '%' : 'auto',
          }"
        >
          <resize-handle></resize-handle>
          <div
            class="h-100vh overflow-y-auto"
            style="padding-bottom: 12em; position: fixed; z-index: 1; margin-right: 1.2em;"
            :style="{
              width: deviceQuickViewWidth + 'px',
            }"
          >
            <device-quick-view :parent-height="tableRowHeight" :div-width="parsedDetailDivX" @hide="selectedDevice = null" @device-uuid-change="findSelectedDeviceByUuid($event.uuid)" :device-uuid="selectedDevice.uuid"></device-quick-view>
          </div>
        </q-card>
      </transition>
      <!-- END PARENT TABLE COL FOR TABLE/DETAIL ROW -->

      <!-- END RENDER FOR DEVICE LIST PAGE -->
    </div>
  </div>
</template>

<script>
import { mapActions, mapGetters } from 'vuex';
import { Drag } from 'vue-drag-drop';
import Loader from '../loaders/Loader';
import ListLoader from '../loaders/ListLoader';
import DeviceItem from './DeviceItem';
import DeviceDetail from './DeviceDetail';
import DeviceStatus from './DeviceStatus.vue';
import MoreIndicator from '../common/MoreIndicator.vue';
import Empty from '../common/Empty.vue';
import DeviceQuickView from './DeviceQuickView.vue';
import interact from 'interactjs';
import ResizeHandle from '../common/ResizeHandle.vue';
import Tooltip from '../common/Tooltip.vue';
import FormattedDate from '../common/FormattedDate.vue';
import OnlineBadge from './DeviceOnlineBadge.vue';
import DeviceTable from './DeviceTable.vue';
import { dom } from 'quasar';
const { height } = dom;
import { mapOrder } from '../../utils/Common';
import TableSkeleton from 'src/components/common/skeletons/TableSkeleton.vue';
import CardSkeleton from 'src/components/common/skeletons/CardSkeleton.vue';
import gtm from 'src/services/gtm.service';
import { OptionsService } from 'src/services/options.service';
import ListPagination from '../common/ListPagination.vue';

export default {
  name: 'DeviceList',
  components: {
    Drag,
    Loader,
    ListLoader,
    DeviceItem,
    DeviceDetail,
    MoreIndicator,
    Empty,
    DeviceStatus,
    DeviceQuickView,
    ResizeHandle,
    Tooltip,
    FormattedDate,
    OnlineBadge,
    DeviceTable,
    TableSkeleton,
    CardSkeleton,
    ListPagination,
  },
  props: {
    title: {
      type: String,
      default: 'Devices',
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
      default: 'relaxed',
    },
    selectedFleet: {
      type: Object,
      default: () => null,
    },
  },
  data() {
    return {
      selectedDevice: null,
      detailDivX: 60,
      detailDivXTimer: 0,
      tableRowHeight: '',
      showDetail: false,
      giveAttensionToVersionsView: false,
      _loading: true,
      paginationRightPadding: 0,
      deviceQueryResult: [],
      total: 0,
    };
  },
  created() {},
  updated() {
    this.$emit('updated', {});
  },
  mounted() {
    this.setupResizable();
    this.queryDevices();
    // If url has device id, show device quick view
    if (this.$route.params.deviceId) {
      this.showDeviceDatail({
        uuid: this.$route.params.deviceId,
      });
    }
    // If the url path is /pair-device, show device provisioning dialog with code input
    if (this.$route.path === '/pair-device') {
      this.$events.$emit('dialogs:provisioning-code-dialog:open', {});
    }
  },
  beforeDestroy() {},
  computed: {
    ...mapGetters({
      devicesData: 'devices/devices',
      stateColumns: 'devices/columns',
      stateVisibleColumns: 'devices/visibleColumns',
      fleets: 'fleets/fleets',
      userSettings: 'ui/userSettings',
    }),
    showHibernatedDevices() {
      return OptionsService.getSavedOptionOrDefault('showHibernated', false);
    },
    loading: {
      get() {
        return this.onboardQueryParam ? false : this.$data._loading;
      },
      set(val) {
        this.$data._loading = val;
      },
    },
    showProvisioningVideo() {
      // We only show the video if there are no devices and there is no filter, or selected fleet
      return !this.loading && (!this.devicesData || this.devicesData.length < 1) && (!this.filter || this.filter.length < 1) && (!this.selectedFleet || !this.selectedFleet.id);
    },
    limitOptions() {
      let rppo = [{ label: '10', value: 10 }, { label: '20', value: 20 }, { label: '50', value: 50 }, { label: '100', value: 100 }, { label: '200', value: 200 }];
      if (this.total > 200 && this.total <= 500) {
        rppo.push({ label: 'All', value: 0 });
      } else if (this.total > 500) {
        rppo.push({ label: '500', value: 500 });
      }
      return rppo;
    },
    user_settings() {
      return this.userSettings || {};
    },
    dragStyleWidth() {
      return this.$q.screen.lt.md ? '100vw' : 'auto';
    },
    columns() {
      let columns = this.stateColumns;
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
    visibleColumns() {
      let columns = this.stateVisibleColumns;
      if (this.user_settings['deviceTableVisibleColumns']) {
        try {
          columns = JSON.parse(this.user_settings['deviceTableVisibleColumns']);
        } catch (e) {
          log('Unable to parse user saved devices table visible columns metadata', e);
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

    deviceTableData() {
      return this.filteredDevices.map((m) => {
        return {
          ...m,
          name: m.deviceName,
          id: m.deviceId,
          uuid: m.uuid,
          hardwareType: m.hardwareType,
          createdDate: m.createdAt,
          activatedDate: m.activatedAt,
          lastSeen: m.lastSeen,
          badge: m.badgeData,
          deviceStatus: m.deviceStatus,
          installedTargets: m.installedTargets,
          device: m,
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
    deviceQuickViewWidth() {
      return this.$store.getters['ui/deviceQuickViewWidth'];
    },

    deviceCtxShow: {
      get() {
        return !!this.ctxDevice;
      },
      set(val) {
        if (!val) {
          this.ctxDevice = null;
        }
      },
    },
    filteredDevices() {
      if (!this.showHibernatedDevices) {
        return this.devicesData.filter((device) => !device.hibernated);
      }
      return this.devicesData;
    },
    deviceDeleteInProgress: {
      get() {
        return this.$store.getters['ui/deviceDeleteInProgress'];
      },
      set(val) {
        this.$store.commit('ui/setDeviceDeleteInProgress', val);
      },
    },

    pagination: {
      get() {
        const defaultPagination = {
          sort: { name: 'desc' },
          descending: true,
          page: 1,
          limit: 10,
          offset: 0,
          rowsPerPage: 0,
        };
        // Get user saved pagination settings or use default
        let pagination = { ...defaultPagination, ...this.getUserOptionOrDefault('deviceTablePagination', defaultPagination) };
        return pagination;
      },
      set(v) {
        this.setUserOption({ deviceTablePagination: v }).finally(() => {
          this.onDataRequest();
        });
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
    deviceTablePaginationWarningAcknowledged: {
      get() {
        return this.getUserOptionOrDefault('deviceTablePaginationWarningAcknowledged', false);
      },
      set(v) {
        this.setUserOption({ deviceTablePaginationWarningAcknowledged: v });
      },
    },
    onboardQueryParam() {
      return this.$route.query.onboard;
    },
  },

  methods: {
    ...mapActions({
      fetchDevices: 'devices/fetchDevices',
      deleteDevice: 'devices/deleteDevice',
      setUserOption: 'ui/setUserOption',
    }),
    getUserOptionOrDefault(optionName, defaultValue) {
      const userSettings = this.userSettings[optionName];
      return typeof userSettings !== 'undefined' ? userSettings : defaultValue;
    },
    promptForDelete(device, e) {
      e.stopPropagation();
      this.$events.$emit('dialogs:confirm:open', {
        title: `Delete ${device.deviceName}?`,
        message: `This can't be undone`,
        color: 'default',
        icon: 'delete',
        yesFlat: true,
        yesClass: 'delete',
        yesLabel: 'Yes, please!',
        yesColor: 'negative',
        yesAction: () => {
          this.deviceDeleteInProgress = device;
          const name = device.deviceName;
          this.deleteDevice(device.uuid)
            .then((deleted) => {
              this.$q.notify({
                color: 'positive',
                message: `${name} deleted!`,
              });
            })
            .catch((err) => {
              this.deviceDeleteInProgress = null;
              this.$q.notify({
                message: `Unable to delete ${name}!`,
                color: 'negative',
              });
            });
        },
        noFlat: true,
        noLabel: 'No',
        noAction: () => {},
      });
    },
    showDeviceCreateDialog() {
      this.$events.$emit('dialogs:create-device:open', {
        show: true,
      });
    },
    getDevices() {
      return new Promise((resolve, reject) => {
        this.loading = true;
        this.fetchDevices({ filter: '', limit: this.limit, offset: this.offset, sort: this.sort })
          .then((devices) => {
            this.total = devices.total;
            resolve(devices);
          })
          .catch((err) => {
            reject(err);
          })
          .finally(() => {
            this.loading = false;
          });
      });
    },
    queryDevices() {
      this.loading = true;
      this.fetchDevices({
        filter: this.filter,
        groupId: this.selectedFleet ? this.selectedFleet.id : null,
        limit: this.limit,
        offset: this.offset,
        storeResults: true,
        sort: this.sort,
      })
        .then((devices) => {
          this.deviceQueryResult = devices.values;
          this.total = devices.total;
          // If offset is greater than total and offset is greater than zero, reset offset to 0
          if (this.offset > 0 && this.offset >= this.total) {
            this.pagination = { ...this.pagination, offset: 0 }; // Reset offset in pagination object, this will trigger a re-fetch of devices
          }
        })
        .catch((err) => {})
        .finally(() => {
          this.loading = false;
        });
    },
    showDeviceDatail(device, full = false) {
      this.tableRowHeight = height(this.$refs.tableRowDiv) + 'px';
      if (!full && this.$q.screen.gt.sm) {
        const query = this.$route.query;
        this.$router.push({
          name: 'devices-quick-view',
          params: { deviceId: device.uuid },
          query,
        });
        this.$events.$emit('component:show-device-detail:open', device);
        this.selectedDevice = device;
      } else {
      }
    },
    showFullDeviceDatail(device, event) {
      event && event.stopPropagation();
      this.$router.push({
        name: 'device-detail',
        params: { deviceId: device.uuid },
      });
    },
    createUpdate(device, e) {
      e.stopPropagation();
      this.$events.$emit(`dialogs:create-device-update:open`, {
        show: true,
        isFleetUpdate: false,
        selectedDevice: device,
        fromDeviceDetail: true,
        update: { devices: [device] },
      });
    },
    findSelectedDeviceByUuid(uuid) {
      this.selectedDevice = this.devicesData.find((f) => f.uuid === uuid);
    },
    setupResizable() {
      this.detailDivX = this.user_settings['deviceDevtailDivX'] || 60;
      interact('.device-detail-div')
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
    onDataRequest() {
      this.queryDevices();
    },
    showDeviceProvisioning() {
      this.$events.$emit('dialogs:create-device:open', {
        show: true,
      });
      gtm.logEvent('Devices', 'click', 'Create Device', null);
    },

    onDevicesDivResize(size) {
      this.$store.commit('ui/setDevicesDivSize', size);
    },
  },
  watch: {
    detailDivX(p) {
      setTimeout(() => {
        this.$events.$emit('devices:redraw', {});
      }, 400);
      clearTimeout(this.detailDivXTimer);
      this.detailDivXTimer = setTimeout(() => {
        this.setUserOption({ deviceDevtailDivX: p });
      }, 1000);
    },
    selectedDevice(p) {
      if (p) {
        this.showDetail = true;
        this.giveAttensionToVersionsView = true;
        setTimeout(() => {
          this.giveAttensionToVersionsView = false;
        }, 200);
        setTimeout(() => {
          this.$events.$emit('devices:redraw', {});
        }, 800);
      } else {
        this.showDetail = false;
        setTimeout(() => {
          this.$events.$emit('devices:redraw', {});
        }, 1000);
      }
    },
    filter(p) {
      this.queryDevices();
    },
    selectedFleet(p) {
      this.queryDevices();
    },
    // Watch for changes in route device id param and hide device quick view if it is not in the devices list
    '$route.params.deviceId'(p) {
      if (p && this.filteredDevices && this.filteredDevices.length > 0) {
        const device = this.filteredDevices.find((d) => d.uuid === p);
        if (!device) {
          this.selectedDevice = null;
        }
      } else {
        this.selectedDevice = null;
      }
    },
  },
};
</script>
