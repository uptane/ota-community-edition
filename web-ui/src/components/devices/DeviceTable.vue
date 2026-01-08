<template>
  <div>
    <q-table
      :data="rows"
      :columns="columns"
      :row-key="(row) => row.uuid + '--' + row.name"
      class="sticky-header-column-table"
      :dense="false"
      :visible-columns="visibleColumns"
      :pagination.sync="tablePagination"
      binary-state-sort
      :selected.sync="selected"
      @row-click="
        (evt, row, index) => {
          rowClicked(row.device);
        }
      "
      @row-dblclick="
        (evt, row, index) => {
          rowDblclicked(row.device);
        }
      "
      :sort-method="customSort"
    >
      <template v-slot:bottom="scope"> </template>
      <template v-slot:body-cell-name="props">
        <q-td key="name" :props="props" class="cursor-pointer selectable hoverable clickable" :class="{ selected: selectedDevice && selectedDevice.uuid === props.row.uuid }">
          <div class="row ">
            <div class="col-3">
              <device-online-badge :device="props.row" inline></device-online-badge>
            </div>
            <div class="col-9 name">
              {{ props.row.name }}
              <tooltip>Click to view device</tooltip>
            </div>
          </div>
        </q-td>
      </template>
      <template v-slot:body-cell-id="props">
        <q-td key="id" :props="props" class="cursor-pointer selectable hoverable clickable" :class="{ selected: selectedDevice && selectedDevice.uuid === props.row.uuid }">
          <div class="q-item-tile sublabel device-id">{{ props.row.id }}</div>
          <tooltip>Click to view device</tooltip>
        </q-td>
      </template>
      <template v-slot:body-cell-uuid="props">
        <q-td key="uuid" :props="props" class="cursor-pointer selectable hoverable clickable" :class="{ selected: selectedDevice && selectedDevice.uuid === props.row.uuid }">
          <div class="q-item-tile sublabel device-id">{{ props.row.uuid }}</div>
          <tooltip>Click to view device</tooltip>
        </q-td>
      </template>
      <template v-slot:body-cell-status="props">
        <q-td key="status" :props="props" class="cursor-pointer selectable hoverable clickable" :class="{ selected: selectedDevice && selectedDevice.uuid === props.row.uuid }">
          <div class="q-item-tile sublabel device-id">
            <update-status-indicator :device="props.row" size="0.8rem" minimized> </update-status-indicator>
          </div>
        </q-td>
      </template>
      <template v-slot:body-cell-hardwareType="props">
        <q-td key="hardwareType" :props="props" class="cursor-pointer selectable hoverable clickable" :class="{ selected: selectedDevice && selectedDevice.uuid === props.row.uuid }">
          <div class="q-item-tile sublabel device-id">{{ props.row.hardwareType }}</div>
          <tooltip>Click to view device</tooltip>
        </q-td>
      </template>
      <template v-slot:body-cell-fleets="props">
        <q-td key="fleets" :props="props" class="cursor-pointer selectable hoverable clickable" :class="{ selected: selectedDevice && selectedDevice.uuid === props.row.uuid }">
          <div class="q-item-tile sublabel device-id">
            <q-chip :key="fleet.groupId" v-for="fleet in props.row.fleets">{{ fleet.groupName }}</q-chip>
          </div>
        </q-td>
      </template>
      <template v-slot:body-cell-createdDate="props">
        <q-td key="createdDate" :props="props" class="cursor-pointer selectable hoverable clickable" :class="{ selected: selectedDevice && selectedDevice.uuid === props.row.uuid }">
          <div class="q-item-tile sublabel">
            <formatted-date :date="props.row.createdDate"></formatted-date>
          </div>
        </q-td>
      </template>
      <template v-slot:body-cell-osVersion="props">
        <q-td key="osVersion" :props="props" class="cursor-pointer selectable hoverable clickable" :class="{ selected: selectedDevice && selectedDevice.uuid === props.row.uuid }">
          <div class="q-item-tile sublabel">
            <device-installed-packages :device="props.row" truncateLongText max-width="20em" type="os"></device-installed-packages>
          </div>
        </q-td>
      </template>
      <template v-slot:body-cell-appVersion="props">
        <q-td key="appVersion" :props="props" class="cursor-pointer selectable hoverable clickable" :class="{ selected: selectedDevice && selectedDevice.uuid === props.row.uuid }">
          <div class="q-item-tile sublabel">
            <device-installed-packages :device="props.row" type="application" truncateLongText max-width="20em"></device-installed-packages>
          </div>
        </q-td>
      </template>
      <template v-slot:body-cell-otherPackages="props">
        <q-td key="otherPackages" :props="props" class="cursor-pointer selectable hoverable clickable" :class="{ selected: selectedDevice && selectedDevice.uuid === props.row.uuid }">
          <div class="q-item-tile sublabel">
            <device-installed-packages :device="props.row" type="others" truncateLongText max-width="20em"></device-installed-packages>
          </div>
        </q-td>
      </template>
      <template v-slot:body-cell-activatedDate="props">
        <q-td key="activatedDate" :props="props" class="cursor-pointer selectable hoverable clickable" :class="{ selected: selectedDevice && selectedDevice.uuid === props.row.uuid }">
          <div class="q-item-tile sublabel">
            <div class="opacity-50" v-if="props.row.deviceStatus === 'NotSeen'">
              Not Activated
            </div>
            <formatted-date v-else :date="props.row.activatedDate"></formatted-date>
          </div>
        </q-td>
      </template>
      <template v-slot:body-cell-lastSeen="props">
        <q-td key="lastSeen" :props="props" class="cursor-pointer selectable hoverable clickable" :class="{ selected: selectedDevice && selectedDevice.uuid === props.row.uuid }">
          <last-seen-column :row="props.row"></last-seen-column>
        </q-td>
      </template>
      <template v-slot:body-cell-actions="props">
        <q-td key="actions" :props="props" class="cursor-pointer selectable hoverable clickable" :class="{ selected: selectedDevice && selectedDevice.uuid === props.row.uuid }">
          <feature-teaser feature="view-device-detail" class="inline-block">
            <q-btn icon="subtitles" flat dense color="secondary" style="padding: .25rem;" @click="showFullDeviceDatail(props.row.device, $event)">
              <tooltip> View device detail information </tooltip>
            </q-btn>
          </feature-teaser>
          <feature-teaser feature="create-device-update" class="inline-block">
            <q-btn v-if="!deviceDeleteInProgress || deviceDeleteInProgress.uuid !== props.row.uuid" icon="publish" flat dense color="secondary" style="padding: .25rem;" @click="createUpdate(props.row.device, $event)">
              <q-tooltip content-class="bg-black" transition-hide="" transition-show="">
                <div class="">Initiate update</div>
              </q-tooltip>
            </q-btn>
          </feature-teaser>
          <feature-teaser feature="delete-device" class="inline-block">
            <q-btn v-if="!deviceDeleteInProgress || deviceDeleteInProgress.uuid !== props.row.uuid" icon="delete" flat dense color="negative" style="padding: .25rem;" @click="promptForDelete(props.row.device, $event)">
              <q-tooltip content-class="bg-black" transition-hide="" transition-show="">
                <div class="">Delete this device</div>
              </q-tooltip>
            </q-btn>
          </feature-teaser>
        </q-td>
      </template>
    </q-table>
  </div>
</template>

<script>
import { mapActions, mapGetters } from 'vuex';
import DeviceOnlineBadge from './DeviceOnlineBadge';
import Tooltip from '../common/Tooltip';
import FormattedDate from '../common/FormattedDate';
import DeviceStatus from './DeviceStatus';
import UpdateStatusIndicator from '../updates/UpdateStatusIndicator.vue';
import DeviceInstalledPackages from './DeviceInstalledPackages.vue';
import LastSeenColumn from 'src/components/devices/LastSeenColumn.vue';

export default {
  name: 'DeviceTable',
  components: {
    Tooltip,
    DeviceStatus,
    FormattedDate,
    DeviceOnlineBadge,
    UpdateStatusIndicator,
    DeviceInstalledPackages,
    LastSeenColumn,
  },
  props: {
    rows: {
      type: Array,
      default: () => [],
    },
    columns: {
      type: Array,
      default: () => [],
    },
    visibleColumns: {
      type: Array,
      default: () => [],
    },
    selectedDevice: {
      type: Object,
      default: () => {},
    },
    pagination: {
      type: Object,
      default: () => {},
    },
  },
  data() {
    return {
      selected: [],
      mPagination: {
        page: 1,
        rowsPerPage: 0,
      },
    };
  },
  mounted() {},
  computed: {
    ...mapGetters({
      userSettings: 'ui/userSettings',
    }),
    tablePagination: {
      get() {
        const sortEntry = Object.entries(this.pagination.sort || {})[0];
        const sortBy = sortEntry[0] || 'name';
        const descending = sortEntry[1] && sortEntry[1] === 'desc';
        return { ...this.mPagination, sortBy, descending };
      },
      set(val) {
        this.mPagination = val;
        const sort = { [val.sortBy]: val.descending ? 'desc' : 'asc' };
        this.$emit('update:pagination', {
          ...this.pagination,
          sort,
          descending: val.descending,
        });
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

    deviceDeleteInProgress: {
      get() {
        return this.$store.getters['ui/deviceDeleteInProgress'];
      },
      set(val) {
        this.$store.commit('ui/setDeviceDeleteInProgress', val);
      },
    },
  },
  methods: {
    ...mapActions({
      deleteDevice: 'devices/deleteDevice',
      setUserOption: 'ui/setUserOption',
    }),
    customSort(rows, sortBy, descending) {
      return rows;
    },
    getUserOptionOrDefault(optionName, defaultValue) {
      const userSettings = this.userSettings[optionName];
      return typeof userSettings !== 'undefined' ? userSettings : defaultValue;
    },
    updatePagination(pagination) {
      this.$emit('update:pagination', pagination);
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
    rowClicked(device) {
      this.$emit('row-click', device);
    },
    rowDblclicked(device) {
      this.$emit('row-dblclick', device);
    },
    showFullDeviceDatail(device, event) {
      event.stopPropagation();
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
  },
  watch: {
    selectedDevice(n) {
      if (n) this.selected = [n];
    },
  },
};
</script>
