<template>
  <q-dialog :value="value" @input="hide">
    <q-card class=" w-90 mxw-70em">
      <div class="text-center ellipsis text-h6 p-1"><span class="opacity-60">Configure compatibility for&nbsp;</span>{{ (packageVersion || {}).filepath }}</div>
      <div class="h-divide-top"></div>

      <q-card-section class="pl-2 pr-2">
        <div class="row">
          <div class="col  v-divide-right-dashed pr-1 ">
            <package-selector hide-experimental-warning :hide-version-selector="false" v-model="selectedPackage" :filter-fn="hideAppicationPackagesFilter" ref="pkgSelector"></package-selector>
            <div class="mt-2">
              <q-btn color="primary" flat icon="playlist_add" @click="addSelectedPackage">Add Selected</q-btn>
              <q-btn flat icon="close" @click="clearSelection">Clear</q-btn>
            </div>
          </div>
          <div class="col pb-0 mb-0">
            <div class="pl-1 mxh-35em h-90">
              <div class="text-h6 h-divide-bottom-dashed">Compatible OS Packages</div>
              <q-banner v-if="loading" inline-actions>
                <list-loader label="Saving changes"></list-loader>
              </q-banner>
              <q-banner inline-actions v-if="error" class="text-white bg-red">
                {{ error }}
                <template v-slot:action>
                  <q-btn flat color="white" icon="close" @click="error = null" />
                </template>
              </q-banner>
              <q-list separator style="overflow:auto" class="h-100" v-if="addedPackages && addedPackages.length > 0">
                <q-item v-for="(pkg, index) in addedPackages" :key="'pkg_' + index">
                  <template v-if="!pkg.missing">
                    <q-item-section avatar>
                      <package-icon :package-info="pkg" />
                    </q-item-section>
                    <q-item-section>
                      <q-item-label>{{ pkg.name }}</q-item-label>
                      <q-item-label caption lines="1">{{ pkg.versionName }}</q-item-label>
                    </q-item-section>
                  </template>
                  <template v-else>
                    <q-item-section avatar>
                      <q-icon name="img:/statics/svg/unknown-package.svg" color="warning" size="lg" class="mr-1" />
                    </q-item-section>
                    <q-item-section>
                      <q-item-label>Unknown package</q-item-label>
                      <q-item-label caption lines="1"> <copy-to-clipboard :text="pkg.sha256"></copy-to-clipboard>&nbsp; {{ pkg.sha256 }} </q-item-label>
                      <q-item-label caption lines="1" class="text-warning">
                        <q-icon name="warning"></q-icon> This package seems to be missing.
                        <tooltip>
                          This package seems to be missing. Have you deleted this package? If so, please remove this compatibility definition to avoid unexpected behavior.
                        </tooltip>
                      </q-item-label>
                    </q-item-section>
                  </template>
                  <q-item-section side>
                    <q-btn icon="clear" dense flat @click="removeAddedPackage(pkg)" />
                  </q-item-section>
                </q-item>
              </q-list>
              <div v-else>
                <empty no-icon no-action message="There are no compatibilities defined for this package"></empty>
              </div>
            </div>
          </div>
        </div>
      </q-card-section>
      <div class="h-divide-top"></div>
      <q-card-actions align="right">
        <q-btn color="primary" label="Save changes" @click="saveChanges" />
        <q-btn v-close-popup flat color="primary">Cancel</q-btn>
      </q-card-actions>
    </q-card>
  </q-dialog>
</template>

<script>
import { mapActions } from 'vuex';
import PackageSelector from './PackageSelector.vue';
import { mapGetters } from 'vuex';
import PackageKind from './PackageKind.vue';
import PackageIcon from './PackageIcon.vue';
import Empty from '../common/Empty.vue';
import ListLoader from '../loaders/ListLoader.vue';
import Tooltip from '../common/Tooltip.vue';
import CopyToClipboard from '../common/CopyToClipboard.vue';
export default {
  components: { PackageSelector, PackageKind, PackageIcon, Empty, ListLoader, Tooltip, CopyToClipboard },
  name: 'PackageSearchDialog',
  props: {
    value: {
      type: Boolean,
      default: false,
    },
    packageVersion: {
      type: Object,
      default: () => {
        return {};
      },
    },
    compatibilities: {
      type: Array,
      default: () => {
        return [];
      },
    },
  },
  data() {
    return {
      filter: '',
      selectedPackage: null,
      showResult: true,
      packageOptions: [],
      addedPackages: [],
      loading: false,
      error: null,
    };
  },
  computed: {
    ...mapGetters({
      packages: 'packages/packages',
      packagesInSelectedSources: 'packages/packagesInSelectedSources',
      packageGroupsInAllSources: 'packages/packageGroupsInAllSources',
      packagesInAllSources: 'packages/packagesInAllSources',
      packagesByHash: 'packages/packagesByHash',
      userSettings: 'ui/userSettings',
      updateEvents: 'devices/updateInstallationEvents',
    }),
  },
  methods: {
    ...mapActions({
      setCompatibility: 'packages/setCompatibility',
    }),
    hideAppicationPackagesFilter(item) {
      return (
        item.isOSPackage &&
        (!this.addedPackages ||
          !this.addedPackages.find((f) => {
            return !!f && f.sha256 === item.packageHash;
          }))
      );
    },
    clearSelection() {
      this.$refs.pkgSelector.clearDropdownSelection();
    },
    removeAddedPackage(pkg) {
      this.addedPackages.splice(this.addedPackages.findIndex((a) => a.filepath === pkg.filepath), 1);
    },
    addSelectedPackage() {
      if (!this.selectedPackage) {
        return;
      }
      this.addPackage(this.selectedPackage);
      this.clearSelection();
    },
    addPackage(pkg) {
      if (!pkg) {
        return;
      }
      const index = this.addedPackages.findIndex((a) => a.filepath === pkg.filepath);
      if (index != -1) {
        this.addedPackages.splice(index, 1, { ...pkg });
      } else {
        this.addedPackages.push(pkg);
      }
    },
    isAdded(pkg) {
      const index = this.addedPackages.findIndex((a) => a.filepath === pkg.filepath);
      return index != -1;
    },
    saveChanges() {
      this.loading = true;
      this.error = null;
      const compatibilities = this.addedPackages.map((a) => {
        return {
          sha256: a.packageHash,
        };
      });
      this.setCompatibility({
        filepath: this.packageVersion.filepath,
        compatibilities,
      })
        .then((data) => {
          this.$emit('save', compatibilities);
          this.$emit('input', false);
        })
        .catch((err) => {
          this.error = 'Unable to save package compatibility. Please try again.';

          if (err && err.response && err.response.data && err.response.data.code === 'role_key_not_found') {
            this.error = 'There are no signing keys available online to complete this operation. If you have taken your signing keys offline, this operation is no longer possible through the web UI. Please use offline signing tools (TorizonCore Builder and/or uptane-sign) instead.';
          }
          log('Error setting package compatibility', err);
        })
        .finally(() => {
          this.loading = false;
        });
    },
    hide($event) {
      this.$emit('input', $event);
      this.selectedPackage = null;
      this.setAddedPackages();
      this.loading = false;
      this.error = '';
    },
    setAddedPackages() {
      this.addedPackages = this.compatibilities.map((a) => {
        return this.packagesByHash[a.sha256]
          ? { ...this.packagesByHash[a.sha256], ...a.sha256 }
          : {
              ...a,
              missing: true,
            };
      });
    },
  },
  watch: {
    compatibilities(n) {
      this.setAddedPackages();
    },
  },
  mounted() {},
};
</script>
