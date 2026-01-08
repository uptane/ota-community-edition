<template>
  <div class="updates-wrapper">
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
          ' pr-1': selectedItem && this.$q.screen.gt.sm,
        }"
      >
        <div class="row tableRowDiv" ref="tableRowDiv">
          <q-linear-progress v-if="loading" indeterminate size="2px" color="secondary" class="q-mt-sm" />
          <div class="p-1 col-md-6 col-lg-4 col-xl-3 col-sm-12 col-xs-12" v-if="loading && !filteredUpdates.length">
            <q-card class>
              <q-item>
                <q-item-label>
                  <list-loader></list-loader>
                </q-item-label>
              </q-item>
            </q-card>
          </div>
          <template v-if="updates && updates.length > 0">
            <template v-if="!loading && filteredUpdates && filteredUpdates.length > 0 && filter">
              <div class="col-12 text-center p-1 ">
                <q-icon name="info" size="1.2em"></q-icon>
                Found {{ filteredUpdates.length }} {{ filteredUpdates.length === 1 ? 'result' : 'results' }} matching
                <strong>"{{ filter }}"</strong>
              </div>
            </template>
            <template v-else-if="!loading && filteredUpdates && filteredUpdates.length > 0 && selectedFleet">
              <div class="col-12 text-center p-1 ">
                <q-icon name="info" size="1.2em"></q-icon>
                Showing {{ filteredUpdates.length }} {{ filteredUpdates.length === 1 ? 'device' : 'devices' }} in fleet
                <strong>"{{ selectedFleet.groupName }}".</strong>
                <q-btn flat no-caps color="primary" @click="$emit('clear-selected-fleet', {})">Show all</q-btn>
              </div>
            </template>

            <lockbox-table
              v-if="viewType === 'table'"
              class="w-100"
              style="margin-left:.35rem"
              :data="updateTableData"
              :columns="columns"
              :visible-columns="visibleColumns"
              :selected-item="selectedItem"
              @row-click="showUpdateDatail"
              @on-revoke="promptForRevoke($event)"
              @on-modify="createOrModifyUpdate($event.item)"
            />

            <div
              v-masonry
              :column-width="'#index_0'"
              v-if="viewType !== 'table'"
              :fit-width="true"
              transition-duration="0.8s"
              item-selector=".update-card-item"
              class="row"
              :class="{
                'w-100': isThinOrThickItem, //viewType ==='thin'
              }"
            >
              <div
                class="update-card-item card-item "
                :class="{
                  'col-sm-12 col-md-4 col-lg-3 col-xl-2': isThinOrThickItem,
                }"
                v-for="(item, index) of filteredUpdates"
                :key="index"
                :id="'index_' + index"
                :style="{
                  padding: '0rem',
                  'padding-right': isThinOrThickItem ? (isThickItem ? '.4rem' : '.2rem') : '0rem',
                }"
                v-masonry-tile
              >
                <lockbox-item :layout-type="layoutType" :view-type="viewType" :view-size="viewSize" :item="item" :hide-icon="!!selectedItem" :selected="selectedItem && selectedItem.name === item.name" @item-click="showUpdateDatail(item)" />
              </div>
            </div>
          </template>
          <template v-if="!loading && (!updates || updates.length < 1)">
            <q-item>
              <q-item-label>
                <div class="q-item-tile label text-center p-2 opacity-30"><q-icon class="mr-1" name="info" size="1.2em"></q-icon> Nothing here yet</div>
              </q-item-label>
            </q-item>
          </template>
          <template v-if="!loading && (updates || updates.length >= 1) && (!filteredUpdates || filteredUpdates.length < 1) && (filter || selectedFleet)">
            <div class="w-100 ">
              <div class="w-100 flex flex-center" v-if="filter">
                <empty :message="`There is no device matching '${filter}'`" noIcon actionText="Show all devices" @on-action="$emit('clear-filter', {})"></empty>
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
          class="col-auto item-detail-div v-divide-left animated"
          v-if="selectedItem && this.$q.screen.gt.sm"
          style="touch-action: none"
          :class="{
            pulse: giveAttensionToVersionsView,
          }"
          :style="{
            width: $q.screen.gt.sm && !!selectedItem ? parsedDetailDivX + '%' : 'auto',
          }"
        >
          <resize-handle></resize-handle>
          <lockbox-quick-view :parent-height="tableRowHeight" @hide="selectedItem = null" :update="selectedItem" :update-size="selectedItem.length" @on-revoke="promptForRevoke($event)" @on-modify="createOrModifyUpdate($event.item)" />
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
import MoreIndicator from '../common/MoreIndicator.vue';
import Empty from '../common/Empty.vue';
// import DeviceQuickView from './DeviceQuickView.vue';
import interact from 'interactjs';
import ResizeHandle from '../common/ResizeHandle.vue';
import Tooltip from '../common/Tooltip.vue';
import FormattedDate from '../common/FormattedDate.vue';
// import DeviceTable from './DeviceTable.vue';
import { dom } from 'quasar';
const { height } = dom;
import { mapOrder } from '../../utils/Common';
import LockboxTable from './LockboxTable.vue';
import LockboxItem from './LockboxItem.vue';
import LockboxQuickView from './LockboxQuickView.vue';

export default {
  name: 'DeviceList',
  components: {
    Drag,
    Loader,
    ListLoader,
    MoreIndicator,
    Empty,
    ResizeHandle,
    Tooltip,
    FormattedDate,
    LockboxTable,
    LockboxItem,
    LockboxQuickView,
  },
  props: {
    title: {
      type: String,
      default: 'Updates',
    },
    query: {
      type: String,
      default: '',
    },
    filter: {
      type: String,
      default: '',
    },
    limit: {
      type: Number,
      default: 50,
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
    selectedFleet: {
      type: Object,
      default: () => null,
    },
    hideRevoked: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      selectedItem: null,
      detailDivX: 60,
      detailDivXTimer: 0,
      tableRowHeight: '',
      showDetail: false,
      giveAttensionToVersionsView: false,
      loading: false,
    };
  },
  created() {},
  updated() {
    this.$emit('updated', {});
  },
  mounted() {
    this.setupResizable();
    this.getUpdates();
    this.$events.$on('update:request-successful', () => {
      this.resetQuickView();
    });
  },
  beforeDestroy() {},
  computed: {
    ...mapGetters({
      updates: 'updates/updates',
      updatesWithPackages: 'updates/updatesWithPackages',
      packagesByHash: 'packages/packagesByHash',
      stateColumns: 'updates/columns',
      stateVisibleColumns: 'updates/visibleColumns',
      userSettings: 'ui/userSettings',
    }),
    user_settings() {
      return this.userSettings || {};
    },
    columns() {
      let columns = this.stateColumns;
      if (this.user_settings['updateTableColumns']) {
        try {
          const colOrder = JSON.parse(this.user_settings['updateTableColumns']);
          columns = mapOrder(columns, colOrder, 'name');
        } catch (e) {
          log('Unable to parse user saved devices table columns metadata', e);
        }
      }
      return columns || this.$store.getters['devices/columns'];
    },
    visibleColumns() {
      let columns = this.stateVisibleColumns;
      if (this.user_settings['updateTableVisibleColumns']) {
        try {
          columns = JSON.parse(this.user_settings['updateTableVisibleColumns']);
        } catch (e) {
          log('Unable to parse user saved update table visible columns metadata', e);
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
    deviceTablePagination: {
      get() {
        return this.$data._pagination;
      },
      set(v) {
        this.$set(this.$data, '_pagination', { ...v });
        this.$q.sessionStorage.set('deviceTablePagination', v);
      },
    },

    updateTableData() {
      const updates = this.filteredUpdates.map((m) => {
        return {
          ...m,
          name: m.name,
          hashes: m.hashes,
          hash: m.hash,
          version: m.version,
          length: m.length,
          update: m,
        };
      });
      return updates;
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
    filteredUpdates() {
      const dataArray = (this.updatesWithPackages || []).filter((update, name) => {
        if (!this.filter || this.filter.length < 1) return true;
        const regex = new RegExp(`${this.filter}`, 'gi');
        return this.filter.length > 0 && JSON.stringify(update).match(regex);
      });
      if (this.hideRevoked) {
        return dataArray.filter((update) => !update.revoked);
      }
      return dataArray;
    },
  },

  methods: {
    ...mapActions({
      fetchUpdates: 'updates/fetchUpdates',
      deleteUpdate: 'updates/deleteUpdate',
      saveUserSettings: 'ui/saveUserSettings',
    }),
    promptForRevoke({ item }) {
      this.$events.$emit('dialogs:confirm:open', {
        title: `Revoke ${item.name}?`,
        message: `This can't be undone`,
        color: 'default',
        icon: 'delete',
        yesFlat: true,
        yesClass: 'delete',
        yesLabel: 'Yes, please!',
        yesColor: 'negative',
        yesAction: () => {
          const name = item.name;
          this.deleteUpdate({ updateName: name })
            .then((deleted) => {
              this.resetQuickView();
              this.$q.notify({
                color: 'positive',
                message: `${name} revoked!`,
              });
            })
            .catch((err) => {
              log('Err', err);
              this.$q.notify({
                message: `Unable to revoke ${name}!`,
                color: 'negative',
              });
            });
        },
        noFlat: true,
        noLabel: 'No',
        noAction: () => {},
      });
    },
    resetQuickView() {
      this.selectedItem = null;
    },
    getUpdates() {
      return new Promise((resolve, reject) => {
        this.loading = true;
        this.fetchUpdates()
          .then((updates) => {
            resolve(updates);
          })
          .catch((err) => {
            reject(err);
          })
          .finally(() => {
            this.loading = false;
          });
      });
    },
    createOrModifyUpdate(update) {
      let data;
      if (update) {
        const hardwareIds = [];
        const selectedEcus = _.map(update.packages || [], (a) => {
          const hwId = (a.hardwareIds || [])[0];
          if (!hwId) return null;
          hardwareIds.push(hwId);
          return {
            hardwareId: hwId,
            package: a,
          };
        }).filter((a) => !!a);
        const devices = selectedEcus.map((e) => {
          return { ecus: [e] };
        });
        data = {
          show: true,
          updateName: update.name,
          selectedEcus,
          update: {
            devices,
          },
        };
      } else {
        data = {
          show: true,
          updateName: '',
          selectedEcus: [],
          update: {
            devices: [{ ecus: [] }],
          },
        };
      }
      this.$events.$emit(`dialogs:create-lockbox:open`, data);
    },
    showUpdateDatail(update) {
      this.tableRowHeight = height(this.$refs.tableRowDiv) + 'px';
      this.$events.$emit('component:show-update-detail:open', update);
      this.selectedItem = update;
    },
    findSelectedDeviceByUuid(uuid) {
      this.selectedEntity = this.updates.find((f) => f.uuid === uuid);
    },
    setupResizable() {
      this.detailDivX = this.user_settings['deviceDevtailDivX'] || 60;
      interact('.item-detail-div')
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
  },
  watch: {
    detailDivX(p) {
      setTimeout(() => {
        this.$events.$emit('devices:redraw', {});
      }, 400);
      clearTimeout(this.detailDivXTimer);
      this.detailDivXTimer = setTimeout(() => {
        this.saveUserSettings({ deviceDevtailDivX: p });
      }, 1000);
    },
    selectedItem(p) {
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
  },
};
</script>
