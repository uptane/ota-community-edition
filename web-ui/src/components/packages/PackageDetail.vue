<template>
  <div
    id="version-scroll"
    class="p-2"
    style="min-height:90vh;  overflow-y:auto"
    :style="{
      height: parentHeight,
    }"
  >
    <div class="header-wrapper">
      <h6 class="mt-0 mb-1">
        Distribution
      </h6>
      <span v-if="!distribution" class="not-on-ecu opacity-40">This package has not been installed yet.</span>
      <span v-if="distribution" class="on-ecu opacity-90">This package has been installed on {{ distribution }} {{ distribution > 1 ? 'devices' : 'device' }}.</span>
    </div>
    <div class="body-wrapper mt-4">
      <h6 class="mt-0 mb-2">Available versions</h6>
      <q-item class="mb-2 h-divide-top-dashed pt-1 p-0" v-for="(ver, index) in packageData.versions" :key="index" :ref="'pkg_version_' + ver.hash">
        <q-item-label>
          <div class="row">
            <div class="col-auto pr-2">
              <div class="q-item-tile label ellipsis mxw-90">
                <package-version-icon :version="ver"></package-version-icon>
                {{ ver.versionName }}
                <span v-if="!ver.isMajor">
                  <template v-if="ver.buildType"
                    >({{ ver.buildType }})</template
                  ></span
                >
              </div>
              <div class="q-item-tile sublabel created-at pt-1 opacity-100">
                <span class="pr-1 opacity-100">Date Uploaded:</span>
                <formatted-date :date="ver.createdAt"></formatted-date>
              </div>

              <div class="row q-item-tile hash sublabel pt-1 pr-1">
                <span class="pr-1">Hash:</span>
                <span class=" ellipsis col">{{ ver.packageHash }}</span>
              </div>
              <div v-if="ver.isExpired" class="row q-item-tile pt-1 pr-1 opacity-100">
                <span class="q-pr-xs">
                  <q-icon name="warning" color="warning" size="1.2em"> </q-icon>
                </span>
                <span class="col text-warning">
                  This package comes from an expired source, and cannot currently be installed. Check packages page to refresh.
                </span>
              </div>
              <div class="q-item-tile size sublabel pt-1" v-if="ver.hasSize"><span class="pr-1">Size:</span> {{ $format.humanStorageSize(ver.targetLength || 0) }}</div>

              <div v-if="ver.commitBody" class="q-item-tile row q-item-tile sublabel pt-1 pb-1" style="position: relative">
                <div class="col-auto pr-1">Version detail:</div>
                <div
                  class="col"
                  :class="{
                    'pr-5 ellipsis': !showUpdateBody[ver.packageHash],
                  }"
                >
                  {{ ver.commitBody }}
                  <q-btn dense @click="toggleCommitBody(ver)" class="absolute-right text-primary">
                    <q-icon
                      :class="{
                        'rotate-90': showUpdateBody[ver.packageHash],
                        'rotate-270': !showUpdateBody[ver.packageHash],
                      }"
                      name="chevron_left"
                      size="1.5rem"
                    />
                  </q-btn>
                </div>
              </div>
            </div>
            <div class="col-12 mt-1 pr-2 installed-on">
              <div v-if="!ver.installedOnEcus || ver.installedOnEcus < 1" class="q-item-tile sublabel">Not installed on any device</div>
              <div v-if="ver.installedOnEcus && ver.installedOnEcus > 0" class="q-item-tile  sublabel">Installed on {{ ver.installedOnEcus }} {{ ver.installedOnEcus === 1 ? 'device' : 'devices' }}</div>
              <div class="q-item-tile sublabel pt-1" v-if="filteredHwIds(ver.hardwareIds).length">
                <div class="hw-sh pr-1">Supported Components:</div>
                <div v-if="!filteredHwIds(ver.hardwareIds) || !filteredHwIds(ver.hardwareIds).length" class="no-hw pr-1">None</div>
                <template v-else>
                  <q-chip style="margin: 0.1em;" dense v-for="(hid, ind) in filteredHwIds(ver.hardwareIds)" :key="ind">
                    {{ hid }}
                  </q-chip>
                </template>
              </div>
            </div>
            <div class="col-12 mt-1">
              <q-btn flat dense icon="publish" color="primary" @click="installPackageVersion(ver)"
                >Install this version
                <tooltip>
                  Install this package version on a device or fleet
                </tooltip>
              </q-btn>
            </div>
            <div v-if="ver.isApplicationPackage">
              <div class="header-wrapper dependencies mt-2">
                <h6 class="mt-0 mb-0 p-0">Compatibilities</h6>
                <div class="q-item-tile pt-1">
                  <template v-if="ver.compatibleWith && ver.compatibleWith.length">
                    <p>
                      <span class="sublabel">
                        <span v-if="ver.compatibleWith.length == 1">Compatible with {{ ver.compatibleWith.length }} OS package</span>
                        <span v-else>Compatible with {{ ver.compatibleWith.length }} OS packages</span>
                      </span>
                      <q-btn flat dense no-caps color="primary"
                        >Show compatibilities
                        <tooltip>Click to view compatibility information for this version</tooltip>
                        <q-popup-proxy :breakpoint="102400">
                          <div class="p-2 q-card">
                            <h5 class="m-0 mb-1"><span class="opacity-50">Compatibilities for</span> {{ ver.name }}&nbsp;{{ ver.versionName }}</h5>
                            <q-list separated>
                              <q-item dense v-for="(hid, ind) in ver.compatibleWith" :key="ind">
                                <template v-if="!!packagesByHash[hid.sha256]">
                                  <q-item-section avatar>
                                    <package-icon size="2rem" :package-info="packagesByHash[hid.sha256]" />
                                  </q-item-section>
                                  <q-item-section>
                                    <q-item-label> {{ (packagesByHash[hid.sha256] || {}).name }}</q-item-label>
                                    <q-item-label caption lines="1"> {{ (packagesByHash[hid.sha256] || {}).versionName || (packagesByHash[hid.sha256] || {}).version || 'Unknown version' }} </q-item-label>
                                    <q-item-label class="ellipsis"> {{ hid.sha256 }} </q-item-label>
                                  </q-item-section>
                                </template>
                                <template v-else>
                                  <q-item-section avatar>
                                    <q-icon name="img:/statics/svg/unknown-package.svg" color="warning" size="lg" class="mr-1" />
                                  </q-item-section>
                                  <q-item-section>
                                    <q-item-label>Unknown package</q-item-label>
                                    <q-item-label caption lines="1"> <copy-to-clipboard :text="hid.sha256"></copy-to-clipboard>&nbsp; {{ hid.sha256 }} </q-item-label>
                                    <q-item-label class="text-warning"> <q-icon name="warning"></q-icon> This package seems to be missing. Have you deleted this package? If so, please remove this compatibility definition to avoid unexpected behavior. </q-item-label>
                                  </q-item-section>
                                </template>
                              </q-item>
                            </q-list>
                          </div>
                        </q-popup-proxy>
                      </q-btn>
                    </p>
                  </template>
                  <div v-else class="no-dep pr-1 sublabel">
                    <p>There is no compatibility information defined for this version.</p>
                  </div>
                </div>
              </div>
            </div>
          </div>
          <package-comment class="mt-1" :package-id="ver.filepath" :editable="ver.userUploaded" :key="ver.filepath"></package-comment>
          <template v-if="ver.userUploaded">
            <div class="row view-content mt-1">
              <template v-if="ver.isApplicationPackage">
                <feature-teaser class="inline-block" feature="modify-package">
                  <q-btn flat dense color="primary" @click="addDependency(ver)">
                    <q-icon size="1.4em" name="fas fa-puzzle-piece" class="q-mx-sm" /> Configure compatibilities
                    <tooltip>Click to set compatibility information for this version</tooltip>
                  </q-btn>
                </feature-teaser>
                <q-btn flat dense icon="article" color="secondary" @click="viewPackageContent(ver)" v-if="ver.buildType === 'custom' && ver.targetLength < 10000000"
                  >View file content
                  <tooltip>Click to view the content of this package</tooltip>
                </q-btn>
              </template>
              <feature-teaser class="inline-block" feature="delete-package">
                <q-btn flat dense icon="delete" color="negative" @click="doDeletePackage(ver)"
                  >DELETE
                  <tooltip>Click to delete this package</tooltip>
                </q-btn>
              </feature-teaser>
            </div>
          </template>
          <static-delta-info :package-hash="ver.hash"></static-delta-info>
        </q-item-label>
      </q-item>
    </div>
    <package-compatibility-dialog v-model="showDependencyDialog" :package-version="activePackage" :compatibilities="compatibilities" @save="updateVersionCompatibilities"> </package-compatibility-dialog>
  </div>
</template>

<script>
import Vue from 'vue';
import { mapActions, mapGetters } from 'vuex';
import PackageVersionIcon from '../packages/PackageVersionIcon';
import FormattedDate from '../common/FormattedDate.vue';
import Tooltip from '../common/Tooltip.vue';
import PackageCompatibilityDialog from './PackageCompatibilityDialog.vue';
import PackageIcon from './PackageIcon.vue';
import PackageComment from './PackageComment.vue';
import { scroll } from 'quasar';
import CopyToClipboard from '../common/CopyToClipboard.vue';
import StaticDeltaInfo from 'src/components/packages/StaticDeltaInfo.vue';
const { setScrollPosition } = scroll;

export default {
  name: 'PackageDetail',
  components: {
    PackageVersionIcon,
    FormattedDate,
    Tooltip,
    PackageCompatibilityDialog,
    PackageIcon,
    PackageComment,
    CopyToClipboard,
    StaticDeltaInfo,
  },
  props: {
    packageData: {
      type: Object,
      default: () => {
        return {};
      },
    },
    parentHeight: {
      type: String,
      default: '',
    },
  },
  data() {
    return {
      installedCount: {},
      showUpdateBody: {},
      showDependencyDialog: false,
      activePackage: null,
    };
  },
  mounted() {
    this.setup();
  },
  methods: {
    ...mapActions({
      deletePackage: 'packages/deletePackage',
      getStaticDeltas: 'packages/getStaticDeltas',
    }),
    setup() {
      this.scrollToVersion();
      // this.fetchDeltas();
    },
    toggleCommitBody(update) {
      Vue.set(this.showUpdateBody, update.packageHash, !this.showUpdateBody[update.packageHash]);
    },
    scrollToVersion(hash) {
      this.$nextTick(() => {
        const routePkgId = hash || this.$route.query.id;
        if (!routePkgId) {
          return;
        }
        const ref = (this.$refs['pkg_version_' + routePkgId] || [])[0];
        if (!ref) return;
        const el = ref.$el;
        const target = document.querySelector('#version-scroll');
        // const target = document.querySelector('#scroll-container');
        const offset = el.offsetTop - 100;
        const duration = 100;
        // setTimeout(() => {
        setScrollPosition(target, offset, duration);
        setTimeout(() => {
          el.classList.add('temp-highlight');
        }, 200);
        setTimeout(() => {
          el.classList.remove('temp-highlight');
        }, 1000);
        // wait for the detail page to render
      });
    },
    addDependency(version) {
      this.activePackage = version;
      this.showDependencyDialog = true;
    },
    updateVersionCompatibilities(deps) {
      this.$set(this.activePackage, 'compatibleWith', deps);
    },
    viewPackageContent(pkg) {
      this.$events.$emit('dialogs:package-content', pkg);
    },
    filteredHwIds(hwIds) {
      return (hwIds || []).filter((f) => !'docker-compose');
    },
    doDeletePackage(ver) {
      this.$q
        .dialog({
          title: 'Confirm',
          message: 'Would you like to delete this package?',
          ok: {
            flat: true,
            label: 'Yes, Delete it',
            color: 'negative',
          },
          cancel: {
            flat: true,
            label: 'No, Cancel',
            color: 'default',
          },
        })
        .onOk(() => {
          this.deletePackage(ver.filepath)
            .then((result) => {
              this.$emit('deleted', ver);
              this.$router.push({ name: 'packages' });
              this.$q.notify({
                message: `The package ${ver.name} was deleted.`,
                color: 'positive',
              });
            })
            .catch((err) => {
              let errorMessage = 'We were unable to delete the package. Please try again later.';

              if (err && err.response && err.response.data && err.response.data.code === 'role_key_not_found') {
                errorMessage = 'There are no signing keys available online to complete this operation. If you have taken your signing keys offline, this operation is no longer possible through the web UI. Please use offline signing tools (TorizonCore Builder and/or uptane-sign) instead.';
              }
              this.$q.dialog({
                title: 'Package Deletion Failed',
                message: errorMessage,
                ok: {
                  flat: true,
                  label: 'OK',
                  color: 'primary',
                },
              });
            });
        });
    },
    fetchDeltas() {
      if (this.packageData && this.packageData.isOStree && !this.packageData.deltas) {
        console.log('Fetching deltas');
        this.getStaticDeltas({ packages: this.packageData.versions })
          .then((deltas) => {
            console.log('Deltas', deltas);
            // this.$set(this.packageData, 'deltas', deltas);
          })
          .catch((err) => {
            console.log('Error fetching deltas', err);
          });
      }
    },

    installPackageVersion(version) {
      this.$events.$emit('dialogs:install-package', version);
    },
  },
  computed: {
    ...mapGetters({
      packageGroupsInAllSources: 'packages/packageGroupsInAllSources',
      packagesInAllSources: 'packages/packagesInAllSources',
      packagesByHash: 'packages/packagesByHash',
      loadingPackages: 'ui/loadingPackages',
    }),
    packageGroups() {
      return _.keyBy(this.packageGroupsInAllSources, 'id');
    },
    compatibilities: {
      get() {
        return (this.activePackage || {}).compatibleWith;
      },
      set(v) {
        this.$set(this.activePackage, 'compatibleWith', v);
      },
    },
    distribution() {
      let dist = 0;
      ((this.packageData || {}).versions || []).forEach((x) => {
        dist += +(x.installedOnEcus || 0);
      });
      return dist;
    },
  },
  watch: {
    packageData: {
      handler: function(newValue) {
        if (newValue) {
          // this.fetchDeltas();
        }
      },
      deep: true,
    },
    loadingPackages(value) {
      if (!value) {
        this.setup();
      }
    },
    $route(to, from) {
      if (this.$route.query.name || this.$route.query.id) {
        this.setup();
      }
    },
  },
};
</script>
