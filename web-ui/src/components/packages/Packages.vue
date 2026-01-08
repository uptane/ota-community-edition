<template>
  <div>
    <div class="table-view" v-if="viewType === 'table'">
      <div v-if="!loading && !packagesInSelectedSources" class="p-1 flex flex-center mnh-100vh">
        <div class="text-center">
          <div class="opacity-90">You haven't created any packages yet.</div>
        </div>
      </div>
      <div v-if="loading" class="p-1 flex flex-center mnh-100vh">
        <span class="opacity-90 pr-1">Loading packages...</span>
        <loader class flat color="secondary" />
      </div>

      <div class="row  m-1 q-card" v-else>
        <div
          class="col tableRowDiv h-100"
          ref="tableRowDiv"
          :class="{
            concealFromView: $q.screen.lt.md && !!selectedPackage,
            'mr-1': $q.screen.gt.sm && !!selectedPackage,
          }"
        >
          <q-card class="shadow-0" style="height:auto">
            <div v-if="showControls" class=" animated fadeIn">
              <div class="m-0 p-1 pl-2 pr-2 row justify-between items-center relative-position">
                <q-btn-group no-caps unelevated flat outline class="q-card">
                  <q-btn icon="filter_alt" flat no-caps v-model="showFilters" :color="showFilters ? 'default' : 'secondary'" :icon-right="showFilters ? 'keyboard_arrow_up' : 'keyboard_arrow_down'" @click="showFilters = !showFilters">
                    <div class="mr-1" :class="{}">
                      <span v-if="!showFilters" :class="{}">Show&nbsp;</span>
                      <span v-else>Hide&nbsp;</span>
                      <span> Filters </span>
                    </div>
                  </q-btn>

                  <q-btn-dropdown auto-close icon="view_column" flat outline no-caps color="default" :label="'Columns'">
                    <div class="shadow-5 mnw-10em">
                      <div class="q-pa-md pl-1 pr-1 mt-0">
                        <div class="q-gutter-sm" :key="i" v-for="(col, i) in columnOptions">
                          <q-checkbox v-model="visibleColumns" :val="col.name" :label="col.label" color="teal" />
                        </div>
                      </div>
                    </div>
                  </q-btn-dropdown>
                </q-btn-group>
                <template>
                  <div class="pl-1">
                    <filter-input v-model="filter"></filter-input>
                  </div>
                  <q-space />
                </template>
                <div class="row">
                  <feature-teaser feature="create-package">
                    <q-btn icon="add" v-if="!selectedPackage" flat color="secondary" @click="createPackage()">
                      <span v-if="$q.screen.gt.sm">Add new package</span>
                    </q-btn>
                  </feature-teaser>
                  <reload-btn v-if="!selectedPackage" :busy="reloading" @reload-requested="reloadPackages"></reload-btn>
                </div>
              </div>
              <q-separator v-if="!selectedPackage && (showFilters && !isEmpty(packagesInSelectedSources))" />
              <div class="m-0 row" v-if="!selectedPackage && showFilters">
                <div class="col-12 pl-1 pr-1 justify-center ">
                  <div class="row no-wrap pb-2 pt-1">
                    <div class="column mnw-25em">
                      <div class="q-gutter-sm">
                        <div class="row pl-1 text-subtitle1">
                          <div class="col"><q-icon name="fa fa-box-open" class="pr-1" />Package Sources</div>
                          <div class="col-auto">
                            <q-btn dense flat icon="settings" color="primary" @click="manageSource()">
                              <tooltip>
                                <span>Manage package sources</span>
                              </tooltip>
                            </q-btn>
                          </div>
                        </div>
                        <div class=" mxh-20em overflow-x-auto">
                          <template v-for="(source, index) in packageSourceOptions">
                            <delegation-source-option-item :source="source" vertical :key="'chk_' + source.value + '__' + index" />
                          </template>
                        </div>
                      </div>
                    </div>
                    <q-separator vertical inset class="q-mx-md" />
                    <div class="column ">
                      <div class="q-gutter-sm">
                        <div class="pl-1 text-subtitle1 "><q-icon name="local_offer" size="1.5em" class="pr-1" />Tags</div>
                        <div v-if="!isEmpty(packagesInSelectedSources)" class="text-1">
                          <div class=" mxh-20em overflow-x-auto">
                            <div class="flex flex-start">
                              <q-chip
                                :key="tag"
                                v-for="tag in tags"
                                clickable
                                :color="activeTags[tag] || tagHover[tag] ? 'accent' : 'grey'"
                                :outline="!activeTags[tag]"
                                :removable="activeTags[tag]"
                                :class="{
                                  'text-white': activeTags[tag] && !$q.dark.isActive,
                                  active: activeTags[tag],
                                }"
                                @remove="removeFilter(tag)"
                                class="shadow-1 filter-tag"
                                @click="activeTags[tag] ? removeFilter(tag) : addFilter(tag)"
                              >
                                {{ tag }}&nbsp;
                              </q-chip>
                              <q-chip v-if="showClearAll" clickable class="animated  text-white slideInRight shadow-2" :color="'primary'" icon="close" label="Clear all" @click="activeTags = {}"></q-chip>
                            </div>
                          </div>
                        </div>
                        <div class="flex flex-center" v-else>
                          <empty no-icon no-action title="There are no tags available" message=""></empty>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <q-separator />
            <q-table
              :filter="filter"
              :rows-per-page="20"
              :rows-per-page-label="selectedPackage ? '↴' : 'Show'"
              :pagination-label="paginationLabel"
              :rows-per-page-options="rowsPerPageOptions"
              :data="tableData"
              :columns="columns"
              :pagination.sync="pagination"
              row-key="name"
              :filter-method="packagesTableFilterMethod"
              :visible-columns="visibleColumns"
              binary-state-sort
            >
              <template slot="body" slot-scope="props">
                <q-tr @click.native="selectPackage(props.row)" class="hoverable clickable selectable" :class="{ selected: selectedPackage && selectedPackage.filepath === props.row.filepath }" :props="props">
                  <q-td class="" key="name" :props="props">
                    <div class="row ellipsis ">
                      <div class="col-auto" style="padding-top: .5rem; padding-bottom:.5rem">
                        <span class="pr-2">
                          <package-icon size="1.8rem" :package-info="props.row" />
                        </span>
                      </div>
                      <div class="col text-left">
                        <div class="ellipsis text-bold">{{ props.row.name }}</div>
                        <div class="opacity-50 ">
                          <template v-for="(ver, index) in props.row.versions">
                            <span class="pl-0 ml-0" dense :key="ver.packageHash" v-if="index < 1"> <package-version-icon :version="ver"></package-version-icon> {{ ver.versionName || ver.hash }} </span>
                          </template>
                          &nbsp;
                          <span v-if="(props.row.versions || []).length > 1 + 1" dense>+{{ (props.row.versions || []).length - 1 }} more </span>
                        </div>
                      </div>
                    </div>
                  </q-td>
                  <q-td key="build-type" :props="props">{{ props.row.buildType }}</q-td>
                  <q-td key="target-format" :props="props">{{ props.row.targetFormat }}</q-td>

                  <q-td key="hardware-type" :props="props">
                    <q-chip dense :key="'pkg_hwid_' + hw" v-for="hw in props.row.hardwareIds">{{ hw }}</q-chip>
                  </q-td>

                  <q-td key="createdAt" :props="props">
                    <formatted-date :date="props.row.createdAt"></formatted-date>
                  </q-td>

                  <q-td key="tags" :props="props">
                    <q-chip dense :key="'pkg_tag_' + tag" v-for="tag in props.row.name.split('/')">{{ tag }}</q-chip>
                  </q-td>
                  <q-td key="source" :props="props">{{ props.row.source }}</q-td>
                  <q-td key="filepath" :props="props">{{ props.row.filepath }}</q-td>
                </q-tr>
              </template>
              <template v-slot:no-data>
                <div class="flex flex-center text-center p-5 w-100">
                  <empty no-icon no-action title="No Packages" message="There are no packages to show"></empty>
                </div>
              </template>
            </q-table>
          </q-card>
        </div>
        <transition appear enter-active-class="animated slideInRight" leave-active-class="animated slideOutRight" class="mnh-100vh">
          <div
            v-if="!!selectedPackage"
            class="col-auto  package-detail-div"
            :class="{
              'm-0': $q.screen.gt.sm && !!selectedPackage,
            }"
            :style="{
              width: $q.screen.gt.sm && !!selectedPackage ? parsedDetailDivX + '%' : 'auto',
            }"
          >
            <q-card
              class="shadow-0 v-divide-left-dashed h-100 animated"
              :class="{
                pulse: giveAttensionToVersionsView,
              }"
            >
              <resize-handle></resize-handle>
              <div>
                <div class="row  h-divide-bottom">
                  <h5 class="col m-0 p-1">
                    <div class="pr-2 row items-center">
                      <span class="col-auto pr-1">
                        <package-icon :package-info="selectedPackage" size="3.5rem" />
                      </span>
                      <span class="col ellipsis">{{ (selectedPackage || {} || {}).name }}</span>
                    </div>
                  </h5>
                  <div class="col-auto p-1">
                    <q-btn icon="close" flat @click="selectPackage(null)"></q-btn>
                  </div>
                </div>
              </div>
              <div class="p-0">
                <package-detail :parent-height="tableRowHeight" :package-data="selectedPackage || {}" @deleted="packageDeleted"></package-detail>
              </div>
            </q-card>
          </div>
        </transition>
      </div>
    </div>
    <manage-delegations-dialog v-if="showManageDelegationDialog" v-model="showManageDelegationDialog"> </manage-delegations-dialog>
  </div>
</template>

<script>
import PackageDetail from './PackageDetail';
import Loader from '../loaders/Loader';
import ListLoader from '../loaders/ListLoader';
import interact from 'interactjs';
import { groupBy } from '../../utils/Common';
import PackageIcon from './PackageIcon';
import Empty from '../common/Empty.vue';
import { mapActions, mapMutations, mapGetters } from 'vuex';
import ReloadBtn from '../common/ReloadBtn.vue';
import MoreIndicator from '../common/MoreIndicator';
import PackageVersionIcon from './PackageVersionIcon';
import ResizeHandle from '../common/ResizeHandle.vue';
import FormattedDate from '../common/FormattedDate.vue';
import FilterInput from '../common/FilterInput.vue';
import { dom } from 'quasar';
import PackageComment from './PackageComment.vue';
import gtm from '../../services/gtm.service';
import Tooltip from '../common/Tooltip.vue';
import DelegationSourceOptionItem from './DelegationSourceOptionItem.vue';
import ManageDelegationsDialog from './ManageDelegationsDialog.vue';
const { height } = dom;

export default {
  name: 'ComponentPackages',
  components: {
    PackageDetail,
    Loader,
    ListLoader,
    PackageIcon,
    Empty,
    ReloadBtn,
    MoreIndicator,
    PackageVersionIcon,
    ResizeHandle,
    FormattedDate,
    FilterInput,
    PackageComment,
    Tooltip,
    DelegationSourceOptionItem,
    ManageDelegationsDialog,
  },
  props: {
    title: {
      type: String,
      default: 'Package List',
    },
    query: {
      type: String,
      default: '',
    },
    limit: {
      type: Number,
      default: 50,
    },
    viewType: {
      type: String,
      default: 'table',
    },
    contentType: {
      type: String,
      default: 'all',
    },
  },

  data() {
    return {
      selectedPackage: null,
      filter: null,
      _showFilters: false,
      activeTags: {},
      tagHover: {},
      giveAttensionToVersionsView: false,
      tenDays: 10 * 60 * 60 * 24 * 1000,
      hardwareFilter: {},
      packageTypeFilter: {},
      packageTagFilter: {},
      filteredHardware: {},
      detailDivX: 60,
      tableRowHeight: '',
      reloading: false,
      syncingDelegation: {},
      pagination: {
        sortBy: 'name',
        descending: false,
        page: 1,
        rowsPerPage: 20,
      },
      rowsPerPageOptions: [10, 20, 30, 50, 0],
      visibleColumns: [
        'name',
        'build-type',
        // 'distro',
        'hardware-type',
        // 'builtAt',
        'createdAt',
      ],
      packageSourceFilter: {},
      _packageSource: 'toradex',
      showControls: true,
      showManageDelegationDialog: false,
      columns: [
        {
          name: 'name',
          required: true,
          label: 'Package Name',
          align: 'left',
          field: 'name',
          sortable: true,
          classes: 'my-class',
        },

        {
          name: 'build-type',
          label: 'Build Type',
          align: 'left',
          field: 'build-type',
          sortable: true,
          classes: 'my-class',
        },
        {
          name: 'target-format',
          label: 'Target Format',
          align: 'left',
          field: 'target-format',
          sortable: true,
          classes: 'my-class',
        },
        {
          name: 'hardware-type',
          label: 'Supported Component',
          align: 'left',
          field: 'hardware-type',
          sortable: true,
          classes: 'my-class',
        },

        {
          id: 'createdAt',
          name: 'createdAt',
          label: 'Date Uploaded',
          align: 'left',
          field: 'createdAt',
          sortable: true,
          classes: 'my-class',
        },
        {
          name: 'tags',
          label: 'Tags',
          align: 'left',
          field: 'tags',
          sortable: true,
          classes: 'my-class',
        },
        {
          name: 'source',
          label: 'Source',
          align: 'left',
          field: 'source',
          sortable: true,
          classes: 'my-class',
        },
        {
          name: 'filepath',
          label: 'Package ID',
          align: 'left',
          field: 'filepath',
          sortable: true,
          classes: 'my-class',
        },
      ],
      initialDataLoaded: false,
    };
  },
  created() {},
  mounted() {
    this.pageTitle = 'Packages';
    this.setupResizable();
    this.$events.$on('packages:reload', () => {
      // this.initialDataLoaded = false;
      this.getPackages();
    });
    this.setSelectedPackage();
    this.checkUrlDelegationSources();
  },

  computed: {
    ...mapGetters({
      delegations: 'packages/delegations',
      packages: 'packages/packages',
      packagesByHash: 'packages/packagesByHash',
      packagesInSelectedSources: 'packages/packagesInSelectedSources',
      packageGroupsInSelectedSources: 'packages/packageGroupsInSelectedSources',
      packageGroupsInAllSources: 'packages/packageGroupsInAllSources',
      packageSourceOptions: 'packages/packageSourceOptions',
      selectedDelegationSources: 'packages/selectedDelegationSources',
      userSettings: 'ui/userSettings',
      loadingPackages: 'ui/loadingPackages',
    }),
    now() {
      return Date.now();
    },
    tableData() {
      const dataArray = this.packageGroupsInSelectedSources || [];
      return dataArray.filter((f) => {
        const truthy = _.keys(this.activeTags)
          .filter((a) => this.activeTags[a])
          .some((s) => {
            return f.name.indexOf(s) > -1;
          });
        return this.noTagsSelected || truthy;
      });
    },
    tags() {
      return _.uniq(
        _.reduce(
          this.packagesInSelectedSources,
          (acc, p) => {
            acc = [...acc, ...p.name.split('/')];
            return acc;
          },
          [],
        ),
      );
    },
    noTagsSelected() {
      return _.isEmpty(this.activeTags) || !_.some(this.activeTags, (a) => a);
    },
    showFilters: {
      get() {
        return this.getUserOptionOrDefault('showPackageFilters', true);
      },
      set(v) {
        this.setUserOption({ showPackageFilters: v });
      },
    },
    defaultDelegationSyncTimestamp() {
      return 1643299577306; // Jan 27, 2022 at 10:06AM CST (nothing special just Date.now() at that very minute)
    },
    delegationSyncTimestamps: {
      get() {
        return this.getUserOptionOrDefault('delegationSyncTimestamps', {});
      },
      set(v) {
        this.setUserOption({ delegationSyncTimestamps: v });
      },
    },

    columnOptions() {
      return this.columns.filter((c) => !c.required);
    },
    loading: {
      get() {
        const loading = this.$store.getters['ui/loadingPackages'];
        if (!loading) {
          this.initialDataLoaded = true;
        }
        return loading;
      },
      set(val) {
        this.$store.commit('ui/setLoadingPackages', val);
      },
    },
    parsedDetailDivX: {
      get() {
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
      set(v) {
        this.detailDivX = Math.round(v);
      },
    },
    showClearAll() {
      return !this.noTagsSelected;
    },
  },
  methods: {
    ...mapActions({
      fetchPackages: 'packages/fetchPackages',
      refreshDelegations: 'packages/refreshDelegations',
      setUserOption: 'ui/setUserOption',
      saveSelectedDelegations: 'packages/saveSelectedDelegations',
    }),
    checkUrlDelegationSources() {
      // debugger;
      const sources = (this.$route.query['delegation-sources'] || '').split(',').filter((s) => s);

      // If the user has selected sources in the URL, use those
      if (sources.length > 0) {
        this.$store.commit('packages/setSelectedDelegationSources', sources);
      }
    },
    refreshDelegationSource(source) {
      this.$set(this.syncingDelegation, source.name, true);
      this.refreshDelegations({ delegation: source })
        .then((result) => {
          this.$set(this.delegationSyncTimestamps, source.name, Date.now());
          this.delegationSyncTimestamps = this.delegationSyncTimestamps;
        })
        .catch((err) => {})
        .finally(() => {
          this.$set(this.syncingDelegation, source.name, false);
        });
    },
    getUserOptionOrDefault(optionName, defaultValue) {
      const userSettings = this.userSettings[optionName];
      return typeof userSettings !== 'undefined' ? userSettings : defaultValue;
    },
    isEmpty(data) {
      return _.isEmpty(data);
    },
    setSelectedPackage() {
      if (!this.isEmpty(this.packageGroupsInAllSources)) {
        let found;
        if (this.$route.query.name || this.$route.query.id) {
          found = _.find(this.packageGroupsInAllSources, (a) => {
            return a && a.name === this.$route.query.name;
          });
          if (!found && this.$route.query.id) {
            const packageById = this.packagesByHash[this.$route.query.id];
            found = _.find(this.packageGroupsInAllSources, (a) => {
              return a && a.name === packageById.name;
            });
          }
          if (found) {
            this.packageSource = found.packageSource;
            this.delegationType = found.delegationType;
            this.selectedPackage = found;
          }
        }
      }
    },
    packagesTableFilterMethod(rows, terms, cols, getCellValue) {
      return rows.filter((f) => JSON.stringify(f).match(new RegExp(terms, 'igm')));
    },
    reloadPackages() {
      this.getPackages();
      gtm.logEvent('Packages Page', 'click', 'Reload Packages', null);
    },
    addFilter(tag) {
      this.$set(this.activeTags, tag, true);
      gtm.logEvent('Packages Page', 'filter', 'Add Filter Tag', tag);
    },
    removeFilter(tag) {
      this.$set(this.activeTags, tag, false);
      gtm.logEvent('Packages Page', 'filter', 'Remove Filter Tag', tag);
    },
    createPackage(pkg) {
      this.$events.$emit('dialogs:create-package:open', { show: true, existing: pkg });
      gtm.logEvent('Packages Page', 'click', 'Create Package', null);
    },
    getPackages() {
      this.loading = true;
      return new Promise((resolve, reject) => {
        this.fetchPackages()
          .then((data) => {
            resolve(data);
            this.onPageLoad();
          })
          .catch((err) => {
            this.onPageLoad();
            reject();
          })
          .finally(() => {
            this.loading = false;
          });
      });
    },
    selectPackage(pkg) {
      if (this.selectedPackage && this.selectedPackage.filepath && (pkg || {}).filepath === this.selectedPackage.filepath) {
        this.selectedPackage = null;
      } else {
        this.selectedPackage = pkg;
        if (pkg && pkg.name) {
          this.$router.replace(this.$route.path + '?name=' + pkg.name, () => {});
        }
      }
    },
    viewPackageDetail(pkg) {
      this.$router.push({ name: 'packages', query: { name: pkg.name } });
      gtm.logEvent('Packages Page', 'view', 'Package Detail', pkg.name);
    },

    onPageLoad() {},
    paginationLabel(start, end, total) {
      return this.selectedPackage ? `${start} → ${end} / ${total}` : `Showing ${start}  to  ${end}  of  ${total}`;
    },
    setupResizable() {
      interact('.package-detail-div')
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
    packageDeleted() {
      this.selectedPackage = null;
    },
    manageSource() {
      this.showManageDelegationDialog = true;
    },
  },
  watch: {
    selectedPackage(p) {
      if (this.$refs.tableRowDiv) {
        this.tableRowHeight = height(this.$refs.tableRowDiv) + 'px';
        if (p) {
          this.giveAttensionToVersionsView = true;
          this.showControls = false;
          setTimeout(() => {
            this.showPackageVersions = true;
            this.giveAttensionToVersionsView = false;
          }, 200);
        } else {
          this.$router.replace(this.$route.path);
          this.showPackageVersions = false;
          setTimeout(() => {
            this.showControls = true;
          }, 700);
        }
      }
    },
    filter(value) {
      value && gtm.logEvent('Packages Page', 'filter', 'Package Name', value);
    },
    loadingPackages(value) {
      if (!value) {
        this.setSelectedPackage();
      }
    },
    $route(to, from) {
      if (this.$route.query.name || this.$route.query.id) {
        this.selectedPackage = null;
        this.setSelectedPackage();
      }
    },
  },
};
</script>
