<template>
  <div>
    <q-table
      :data="data"
      :columns="columns"
      row-key="name"
      class="sticky-header-column-table"
      :dense="false"
      rows-per-page-label="Records per page"
      :rows-per-page-options="[10, 20, 50, 100]"
      :pagination.sync="updateTablePagination"
      :visible-columns="visibleColumns"
      @row-click="
        (evt, row, index) => {
          rowClicked(row.update);
        }
      "
      :selected.sync="selected"
    >
      <template v-slot:body-cell-name="props">
        <q-td key="name" :props="props" class="cursor-pointer selectable hoverable clickable" :class="{ selected: selectedItem && selectedItem.name === props.row.name }">
          <div class="row ">
            <div class="col-auto name">
              <span class="mr-1">
                <q-icon v-if="props.row.revoked" class="text-negative" name="not_interested" size="1.2rem"></q-icon>
                <q-icon v-else-if="props.row.expired" class="text-warning" name="running_with_errors" size="1.2rem"></q-icon>
                <q-icon v-else class="text-positive" name="verified" size="1.2rem"></q-icon>
              </span>
            </div>
            <div class="col-9 name">
              {{ props.row.name }}
              <div class="text-caption">
                <div v-if="props.row.revoked" class="text-negative text-sm">
                  <!-- <q-icon name="not_interested"></q-icon>  -->
                  Revoked
                </div>
                <div v-else-if="props.row.expired" class="text-warning">
                  <!-- <q-icon name="running_with_errors"></q-icon>  -->
                  Expired
                </div>
              </div>
            </div>
            <tooltip>Click to view</tooltip>
          </div>
        </q-td>
      </template>
      <template v-slot:body-cell-id="props">
        <q-td key="id" :props="props" class="cursor-pointer selectable hoverable clickable" :class="{ selected: selectedItem && selectedItem.name === props.row.name }">
          <div class="q-item-tile sublabel device-id">{{ props.row.id }}</div>
          <tooltip>Click to view</tooltip>
        </q-td>
      </template>
      <template v-slot:body-cell-contents="props">
        <q-td key="contents" :props="props" class="cursor-pointer selectable hoverable clickable" :class="{ selected: selectedItem && selectedItem.name === props.row.name }">
          <div class="q-item-tile sublabel device-id">
            <lockbox-table-package-content :packageData="props.row.packages"></lockbox-table-package-content>
          </div>
          <tooltip>Click to view</tooltip>
        </q-td>
      </template>
      <template v-slot:body-cell-uuid="props">
        <q-td key="uuid" :props="props" class="cursor-pointer selectable hoverable clickable" :class="{ selected: selectedItem && selectedItem.uuid === props.row.uuid }">
          <div class="q-item-tile sublabel device-id">{{ props.row.uuid }}</div>
          <tooltip>Click to view</tooltip>
        </q-td>
      </template>
      <template v-slot:body-cell-status="props">
        <q-td key="status" :props="props" class="cursor-pointer selectable hoverable clickable" :class="{ selected: selectedItem && selectedItem.uuid === props.row.uuid }">
          <div class="q-item-tile sublabel device-id">
            <span v-if="props.row.revoked" class="text-negative">Revoked</span>
            <span v-else-if="props.row.expired" class="text-warning">Expired</span>
            <span v-else>Active</span>
          </div>
        </q-td>
      </template>
      <template v-slot:body-cell-hardwareType="props">
        <q-td key="hardwareType" :props="props" class="cursor-pointer selectable hoverable clickable" :class="{ selected: selectedItem && selectedItem.uuid === props.row.uuid }">
          <div class="q-item-tile sublabel device-id">{{ props.row.hardwareType }}</div>
          <tooltip>Click to view</tooltip>
        </q-td>
      </template>
      <template v-slot:body-cell-expires="props">
        <q-td key="expires" :props="props" class="cursor-pointer selectable hoverable clickable" :class="{ selected: selectedItem && selectedItem.name === props.row.name }">
          <div class="q-item-tile sublabel">
            <formatted-date :date="props.row.expires"></formatted-date>
          </div>
        </q-td>
      </template>

      <template v-slot:body-cell-actions="props">
        <q-td key="actions" :props="props" class="cursor-pointer selectable hoverable clickable text-left" :class="{ selected: selectedItem && selectedItem.name === props.row.name }">
          <q-btn icon="edit" flat dense color="secondary" style="padding: .25rem;" @click.stop="createUpdate(props.row)">
            <tooltip content-class="bg-black" transition-hide="" transition-show="">
              <div class="">Modify</div>
            </tooltip>
          </q-btn>

          <q-btn v-if="!props.row.revoked" icon="not_interested" flat dense color="negative" style="padding: .25rem;" @click.stop="promptForRevoke(props.row.update)">
            <tooltip transition-hide="" transition-show="">
              <div class="">Revoke</div>
            </tooltip>
          </q-btn>
        </q-td>
      </template>
    </q-table>
  </div>
</template>

<script>
import { mapActions } from 'vuex';
import Tooltip from '../common/Tooltip';
import FormattedDate from '../common/FormattedDate';
import { format } from 'quasar';
import LockboxTablePackageContent from './LockboxTablePackageContent.vue';
const { humanStorageSize } = format;

export default {
  name: 'DeviceTable',
  components: {
    Tooltip,
    FormattedDate,
    LockboxTablePackageContent,
  },
  props: {
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
    selectedItem: {
      type: Object,
      default: () => {},
    },
  },
  data() {
    return {
      _pagination: {
        sortBy: 'name',
        descending: true,
        page: 1,
        rowsPerPage: 20,
      },
      selected: [],
    };
  },
  mounted() {
    this.$data._pagination = this.$q.sessionStorage.getItem('updateTablePagination') || {
      sortBy: 'date',
      page: 1,
      rowsPerPage: 20,
    };
  },
  computed: {
    updateTablePagination: {
      get() {
        return this.$data._pagination;
      },
      set(v) {
        this.$set(this.$data, '_pagination', { ...v });
        this.$q.sessionStorage.set('updateTablePagination', v);
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
    }),
    promptForRevoke(item) {
      this.$emit('on-revoke', { item });
    },
    rowClicked(lockbox) {
      this.$emit('row-click', lockbox);
    },
    createUpdate(lockbox) {
      this.$emit('on-modify', { item: lockbox });
    },
    humanStorageSize,
  },
  watch: {
    selectedItem(n) {
      if (n) this.selected = [n];
    },
  },
};
</script>
