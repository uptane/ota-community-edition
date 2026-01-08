<template>
  <q-table
    :data="rows"
    :columns="columns"
    class="q-card mb-3"
    :visible-columns="visibleColumns"
    :rows-per-page-options="[10, 20, 30, 40, 50, 100]"
    row-key="name"
    selection="single"
    :selected.sync="selectedArray"
    @row-click="
      (evt, row, index) => {
        rowClicked(row);
      }
    "
  >
    <template v-slot:body-selection>
      <!-- <q-toggle v-model="scope.selected" /> -->
    </template>
    <template v-slot:body-cell-name="props">
      <q-td :props="props">
        <package-icon size="1.5rem" :package-info="props.row" class="mr-1"></package-icon>
        <span>{{ props.row.name }}</span>
      </q-td>
    </template>
    <template v-slot:body-cell-installed="props">
      <q-td :props="props">
        <div class="text-bold ellipsis">
          <q-icon v-if="props.row.active" class="pr-1 animated bounceIn" size="1.4em" color="positive" name="check_circle" />
          {{ props.row.active ? 'Yes' : 'No' }}
        </div>
      </q-td>
    </template>
  </q-table>
</template>

<script>
import { date } from 'quasar';
import PackageIcon from '../packages/PackageIcon.vue';
export default {
  components: { PackageIcon },
  name: 'DevicePackageTable',
  props: {
    selectedRow: {
      type: Object,
      default: () => null,
    },
    rows: {
      type: Array,
      default: () => [],
    },
    visibleColumns: {
      type: Array,
      default: () => ['name', 'numOfVersions', 'latest', 'installed'],
    },
    columns: {
      type: Array,
      default: () => [
        {
          required: true,
          label: 'Package Name',
          align: 'left',
          field: (row) => row.name,
          sortable: true,
          classes: 'ellipsis text-bold',
          style: 'max-width: 100px',
          headerClasses: 'text-bold',
          name: 'name',
          id: 'name',
        },

        {
          required: false,
          label: 'Latest Version',
          align: 'left',
          field: (row) => date.formatDate(row.createdAt, 'ddd MMM DD YYYY, h:mm:ss A'),
          format: (val) => `${val}`,
          sortable: true,
          classes: 'ellipsis text-bold',
          style: 'max-width: 100px',
          headerClasses: 'text-bold',
          name: 'latest',
          id: 'latest',
        },
        {
          required: true,
          label: 'No. Of Versions',
          align: 'left',
          field: (row) => (row.versions || []).length,
          format: (val) => `${val}`,
          sortable: true,
          classes: 'ellipsis text-bold w-10',
          style: 'width: 30px',
          headerClasses: 'text-bold',
          name: 'numOfVersions',
          id: 'numOfVersions',
        },
        {
          required: false,
          label: 'Currently Installed',
          align: 'left',
          field: (row) => row.active,
          format: (val) => `${val ? 'Yes' : 'No'}`,
          sortable: true,
          classes: 'ellipsis text-bold',
          style: 'max-width: 100px',
          headerClasses: 'text-bold',
          name: 'installed',
          id: 'installed',
        },
      ],
    },
  },
  data() {
    return {};
  },
  computed: {
    selectedArray: {
      get() {
        if (this.selectedRow) {
          return [this.selectedRow];
        }
        return [];
      },
      set(v) {
        this.$emit('update:selectedRow'(v || [])[0]);
      },
    },
  },
  methods: {
    rowClicked(update) {
      this.$emit('toggle-package', update);
    },
  },
};
</script>
