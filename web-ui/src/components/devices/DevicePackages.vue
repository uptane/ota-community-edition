<template>
  <div class>
    <div v-if="loading">
      <div class="flex flex-center mnh-100vh">
        <div class="text-center">
          <div>
            <span class="opacity-90 pr-1">Loading packages...</span>
            <loader class flat color="secondary" />
          </div>
        </div>
      </div>
    </div>
    <div v-if="!loading">
      <div class="row p-0 m-0">
        <div
          class="transition-width"
          ref="parentPkgDiv"
          style="transition: all 0.2s ease"
          v-if="!selectedPackage || $q.screen.gt.sm"
          :class="{
            'col-12': !selectedPackage || loadingPackages,
            'col-6': !!selectedPackage && !loadingPackages,
          }"
        >
          <div class="q-card p-2 row">
            <div class="col-12">
              <div class="row">
                <div class="col justify-between">
                  <h5 class="m-0"><q-icon class="mr-1" size="1.8rem" name="fa fa-box"></q-icon><span class="opacity-40">Packages Compatible With </span> &nbsp;{{ device.deviceName }}</h5>
                  <div></div>
                </div>
                <div class="col-auto">
                  <view-type-selector v-model="viewType" :columns.sync="columns" :visibleColumns.sync="visibleColumns" :views="['table']"></view-type-selector>
                </div>
                <div class="col" v-if="!selectedPackage">
                  <div class="pl-1">
                    <filter-input v-model="filter"></filter-input>
                  </div>
                </div>
              </div>
            </div>
            <div class="row w-100">
              <div class="pt-2 mb-1 w-100">
                <device-package-table :rows="filteredPackages" @toggle-package="toggleSelectedPackage" :selected-row.sync="selectedPackage" v-if="viewType === 'table'" :columns="columns" :visible-columns="visibleColumns"></device-package-table>
              </div>
            </div>
          </div>
        </div>

        <transition appear enter-active-class="animated slideInRight" leave-active-class="animated slideOutRight" class="mnh-100vh">
          <div
            class="col-6 pt-0 mb-1"
            v-if="!loadingPackages && !!showPackageVersions"
            :class="{
              'col-12': $q.screen.lt.md,
              'pl-1': $q.screen.gt.sm,
            }"
          >
            <div
              class="q-card h-100
          animated"
              style="overflow-y: auto;"
              :class="{
                pulse: giveAttensionToVersionsView,
              }"
            >
              <device-package-versions :device="device" :selected-package="selectedPackage" @close="selectedPackage = null"></device-package-versions>
            </div>
          </div>
        </transition>
      </div>
    </div>
  </div>
</template>

<script>
import { mapActions, mapGetters } from 'vuex';
import Loader from '../loaders/Loader';
import DevicePackageVersions from './DevicePackageVersions';
import { dom, extend } from 'quasar';
import Tooltip from '../common/Tooltip.vue';
import ViewTypeSelector from '../common/ViewTypeSelector.vue';
import FilterInput from '../common/FilterInput.vue';
import DevicePackageTable from './DevicePackageTable.vue';
const { height, width } = dom;

export default {
  name: 'DevicePackages',
  components: {
    Loader,
    DevicePackageVersions,
    Tooltip,
    ViewTypeSelector,
    FilterInput,
    DevicePackageTable,
  },
  props: {
    device: {
      type: Object,
      default: () => {
        return {};
      },
    },
  },
  data() {
    return {
      loading: false,
      loadingPackages: false,
      showPackageVersions: false,
      parsedPackageVersions: {},
      giveAttensionToVersionsView: false,
      selectedPackage: null,
      viewType: 'table',
      viewSize: 20,
      filter: '',
      visibleColumns: ['name', 'latest', 'numOfVersions', 'installed'],
      columns: [
        {
          required: true,
          label: 'Package Name',
          align: 'left',
          field: (row) => row.name,
          // format: val => this.$date.formatDate(val, 'MM/DD/YYYY @ h:mm:ss A'),
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
          field: (row) => this.$date.formatDate(row.createdAt, 'ddd MMM DD YYYY, h:mm:ss A'),
          format: (val) => `${val}`,
          sortable: true,
          classes: 'ellipsis text-bold',
          style: 'max-width: 100px',
          headerClasses: 'text-bold',
          name: 'latest',
          id: 'latest',
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
        {
          required: true,
          label: 'No. Of Versions',
          align: 'left',
          field: (row) => (row.versions || []).length,
          format: (val) => `${val}`,
          sortable: true,
          classes: 'ellipsis text-bold',
          style: 'max-width: 100px',
          headerClasses: 'text-bold',
          name: 'numOfVersions',
          id: 'numOfVersions',
        },
      ],
    };
  },
  created() {},
  mounted() {
    this.setup();
  },
  beforeDestroy() {},
  computed: {
    ...mapGetters({
      packageGroupsInAllSources: 'packages/packageGroupsInAllSources',
    }),
    packageList() {
      return _.toArray(this.packageGroupsInAllSources);
    },

    filteredPackages() {
      const list = this.packageList.filter((f) => {
        return _.includes(f.hardwareIds, this.device.hardwareType) || _.includes(f.hardwareIds, 'docker-compose');
      });
      return list.filter((f) => JSON.stringify(f).match(new RegExp(this.filter, 'i')));
    },
    pageTitle: {
      get() {
        return this.$store.getters['ui/currentPageTitle'];
      },
      set(val) {
        return this.$store.commit('ui/setCurrentPageTitle', val);
      },
    },

    deviceUuid() {
      return this.device.uuid;
    },
  },
  beforeDestroy() {},

  created() {},
  methods: {
    ...mapActions({}),
    height,
    setup() {
      this.pageTitle = 'Device Packages';
      this.refresh();
      this.$events.$on('devices:refresh', () => {
        this.refresh();
      });
    },
    refresh() {},
    toggleSelectedPackage(update) {
      if (this.selectedPackage && this.selectedPackage.name === update.name) {
        this.selectedPackage = null;
      } else {
        this.selectedPackage = { ...update };
      }
    },

    deviceDeleted() {
      this.$router.replace({ name: 'devices' }).catch((e) => {});
    },
  },
  watch: {
    selectedPackage(p) {
      if (p) {
        this.giveAttensionToVersionsView = true;
        setTimeout(() => {
          this.showPackageVersions = true;
          this.giveAttensionToVersionsView = false;
        }, 200);
      } else {
        this.showPackageVersions = false;
      }
    },
  },
};
</script>
