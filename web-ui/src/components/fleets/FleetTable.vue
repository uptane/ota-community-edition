<template>
  <div>
    <q-table
      :data="data"
      :columns="columns"
      row-key="uuid"
      class="sticky-header-column-table"
      :dense="false"
      rows-per-page-label="Records per page"
      :rows-per-page-options="[10, 20, 50, 100, 0]"
      :visible-columns="visibleColumns"
      :pagination.sync="tablePagination"
      binary-state-sort
      @row-click="
        (evt, row, index) => {
          rowClicked(row.fleet);
        }
      "
      :sort-method="customSort"
      :selected.sync="selected"
    >
      <template v-slot:bottom="scope"> </template>
      <template v-slot:body-cell-name="props">
        <q-td key="name" :props="props" class="cursor-pointer selectable hoverable clickable" :class="{ selected: selectedFleet && selectedFleet.id === props.row.id }">
          <div class="row ">
            <div class="name">
              {{ props.row.name }}
              <tooltip>Click to view device</tooltip>
            </div>
          </div>
        </q-td>
      </template>
      <template v-slot:body-cell-id="props">
        <q-td key="id" :props="props" class="cursor-pointer selectable hoverable clickable" :class="{ selected: selectedFleet && selectedFleet.id === props.row.id }">
          <div class="q-item-tile sublabel device-id">{{ props.row.id }}</div>
          <tooltip>Click to view fleet information</tooltip>
        </q-td>
      </template>
      <template v-slot:body-cell-device_count="props">
        <q-td key="device_count" :props="props" class="cursor-pointer selectable hoverable clickable" :class="{ selected: selectedFleet && selectedFleet.id === props.row.id }">
          <div class="q-item-tile sublabel device-id">
            {{ props.row.device_count }}
          </div>
          <tooltip>Click to view fleet information</tooltip>
        </q-td>
      </template>
      <template v-slot:body-cell-uuid="props">
        <q-td key="uuid" :props="props" class="cursor-pointer selectable hoverable clickable" :class="{ selected: selectedFleet && selectedFleet.id === props.row.id }">
          <div class="q-item-tile sublabel device-id">{{ props.row.uuid }}</div>
          <tooltip>Click to view fleet information</tooltip>
        </q-td>
      </template>
      <template v-slot:body-cell-devices="props">
        <q-td key="devices" :props="props" class="cursor-pointer selectable hoverable clickable" :class="{ selected: selectedFleet && selectedFleet.id === props.row.id }">
          <div class="q-item-tile sublabel fleet-devices">
            <template v-for="(device, index) in props.row.devices">
              <q-chip v-if="device && index < 3" :key="index">{{ device.deviceName }}</q-chip>
              <q-chip v-if="index === 3" :key="index">...</q-chip>
            </template>
          </div>
        </q-td>
      </template>
      <template v-slot:body-cell-createdDate="props">
        <q-td key="createdDate" :props="props" class="cursor-pointer selectable hoverable clickable" :class="{ selected: selectedFleet && selectedFleet.id === props.row.id }">
          <div class="q-item-tile sublabel">
            <formatted-date :date="props.row.createdDate"></formatted-date>
          </div>
        </q-td>
      </template>
      <template v-slot:body-cell-actions="props">
        <q-td key="actions" :props="props" class="cursor-pointer selectable hoverable clickable" :class="{ selected: selectedFleet && selectedFleet.id === props.row.id }">
          <feature-teaser class="inline-block" feature="view-fleet">
            <q-btn icon="subtitles" flat dense color="secondary" style="padding: .25rem;" @click="showFullFleetDatail(props.row, $event)">
              <tooltip> View fleet detail information </tooltip>
            </q-btn>
          </feature-teaser>
          <feature-teaser class="inline-block" feature="manage-fleet-update">
            <q-btn icon="publish" flat dense color="secondary" style="padding: .25rem;" @click.stop="createUpdate(props.row.fleet)" :disable="props.row.device_count < 1">
              <tooltip>
                <div v-if="props.row.device_count > 0" class="">Initiate update</div>
                <div v-else class="">No devices to update</div>
              </tooltip>
            </q-btn>
          </feature-teaser>
          <feature-teaser class="inline-block" feature="delete-fleet">
            <q-btn icon="delete" flat dense color="negative" style="padding: .25rem;" @click.stop="promptForDelete(props.row.fleet)">
              <tooltip>
                <div class="">Delete this fleet</div>
              </tooltip>
            </q-btn>
          </feature-teaser>
        </q-td>
      </template>
    </q-table>
  </div>
</template>

<script>
import { mapGetters, mapActions } from 'vuex';
import Tooltip from 'src/components/common/Tooltip.vue';
import FormattedDate from 'src/components/common/FormattedDate.vue';
export default {
  components: { Tooltip, FormattedDate },
  name: 'FleetTable',
  props: {
    selectedFleet: {
      type: Object,
      default: () => ({}),
    },
    data: {
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
          ascending: val.ascending,
        });
      },
    },
    fleetDeleteInProgress: {
      get() {
        return this.$store.getters['ui/fleetDeleteInProgress'];
      },
      set(val) {
        this.$store.commit('ui/setFleetDeleteInProgress', val);
      },
    },
  },
  methods: {
    ...mapActions({
      deleteFleet: 'fleets/deleteFleet',
      setUserOption: 'ui/setUserOption',
    }),
    getUserOptionOrDefault(optionName, defaultValue) {
      const userSettings = this.userSettings[optionName];
      return typeof userSettings !== 'undefined' ? userSettings : defaultValue;
    },
    rowClicked(fleet) {
      this.$emit('row-click', fleet);
    },
    showFullFleetDatail(fleet, event) {
      event.stopPropagation();
      this.$router.push({
        name: 'fleet-detail',
        params: { fleetId: fleet.id },
      });
    },
    createUpdate(fleet) {
      this.$events.$emit(`dialogs:create-device-update:open`, {
        show: true,
        fromFleet: true,
        isFleetUpdate: true,
        update: { fleet },
      });
    },
    showEditDialog(fleet) {
      this.$events.$emit(`dialogs:create-fleet:open`, {
        show: true,
        fleet: fleet || {},
      });
    },
    promptForDelete(fleet) {
      this.$q
        .dialog({
          title: `Delete ${fleet.groupName}?`,
          message: `This can't be undone.`,
          color: 'default',
          icon: 'delete',
          cancel: {
            color: 'primary',
            label: 'Cancel',
            flat: true,
          },
          ok: { label: 'Confirm', flat: true },
          focus: 'cancel',
          persistent: true,
        })
        .onOk(() => {
          this.fleetDeleteInProgress = fleet;
          const name = fleet.groupName;
          this.deleteFleet(fleet.id, name)
            .then((deleted) => {
              this.$q.notify({
                color: 'positive',
                message: `${name} deleted!`,
              });
            })
            .catch((err) => {
              this.fleetDeleteInProgress = null;
              this.$q.notify({
                message: `Unable to delete ${name}!`,
                color: 'negative',
              });
            });
        });
    },

    customSort(rows, sortBy, descending) {
      return rows; // no sorting needed
    },
  },
};
</script>

<style></style>
