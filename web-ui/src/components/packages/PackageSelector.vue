<template>
  <div class="row items-stretch content-stretch w-100  " v-if="!loadingPackage">
    <div class="col-12 ">
      <div class="mb-3">
        <p class="p-0 m-0 text-1 opacity-50">Package sources</p>
        <div v-for="(source, key) in packageSourceOptions" :key="'pkg-opt-' + key" class="row  inline">
          <delegation-source-option-item dense :source="source" />
        </div>
        <nightly-package-warning v-if="showNightlyWarning"></nightly-package-warning>
        <bootloader-update-warning class="mt-1" v-if="showBootloaderWarning"></bootloader-update-warning>
      </div>

      <div v-if="packageGroups && packageGroups.length > 0">
        <div class="mb-3">
          <package-name-selector :packageListOptions="packageGroups" :value="selectedPackageGroup" @input="selectedPackageGroup = $event" :preselected="preselectedPackage" :preselected-text="preselectedPackageText"></package-name-selector>
        </div>

        <package-version-selector :value="selectedPackage" @input="selectedPackage = $event" @cleared="versionSelectorCleared" v-if="!hideVersionSelector" :versionList="versionList" :preselected="preselectedPackage" :preselected-text="preselectedVersionText"></package-version-selector>
      </div>
      <div v-else class="col text-center flex flex-center opacity-40">
        There are no packages available for this component in the source you selected. <br />
        Please select a different package source.
      </div>
    </div>
  </div>
</template>

<script>
import _ from 'lodash';
import { mapGetters, mapActions } from 'vuex';
import PackageIcon from '../packages/PackageIcon.vue';
import NightlyPackageWarning from '../common/NightlyPackageWarning.vue';
import PackageVersionIcon from '../packages/PackageVersionIcon.vue';
import PackageNameSelector from './PackageNameSelector.vue';
import PackageVersionSelector from './PackageVersionSelector.vue';
import DelegationSourceOptionItem from './DelegationSourceOptionItem.vue';
import BootloaderUpdateWarning from '../updates/BootloaderUpdateWarning.vue';
export default {
  components: { PackageIcon, NightlyPackageWarning, PackageVersionIcon, PackageNameSelector, PackageVersionSelector, DelegationSourceOptionItem, BootloaderUpdateWarning },
  name: 'PackageSelector',
  props: {
    ecu: {
      type: Object,
      default: () => {
        return {};
      },
    },
    value: {
      type: Object,
      default: () => {
        return {};
      },
    },
    preselectedPackage: {
      type: Object,
      default: () => {
        return {};
      },
    },
    preselectedPackageText: {
      type: String,
      default: () => {
        return '';
      },
    },
    preselectedVersionText: {
      type: String,
      default: () => {
        return '';
      },
    },
    hideExperimentalWarning: {
      type: Boolean,
      default: false,
    },
    hideVersionSelector: {
      type: Boolean,
      default: false,
    },
    filterFn: {
      type: Function,
      default: () => true,
    },
    isFleetUpdate: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      loadingPackage: false,
      packageGroup: null,
      toVersion: null,
      autoSelectWhenSingleVersion: true,
      versionList: [],
    };
  },
  mounted() {},
  computed: {
    ...mapGetters({
      packages: 'packages/packages',
      packagesInSelectedSources: 'packages/packagesInSelectedSources',
      packageGroupsInSelectedSources: 'packages/packageGroupsInSelectedSources',
      packageGroupsInAllSources: 'packages/packageGroupsInAllSources',
      packagesById: 'packages/packagesById',
      packagesByHash: 'packages/packagesByHash',
      packageSourceOptions: 'packages/packageSourceOptions',
      delegations: 'packages/delegations',
      userSettings: 'ui/userSettings',
      updateEvents: 'devices/updateInstallationEvents',
    }),
    selectedDelegationSources: {
      get() {
        return _.toArray(this.getUserOptionOrDefault('selectedDelegationSources', ['tdx-lts', 'tdx-quarterly', 'tdx-monthly', 'custom']) || {});
      },
      set(v) {
        this.setUserOption({ selectedDelegationSources: { ...v } });
      },
    },
    selectedDelegations() {
      return this.selectedDelegationSources.map((d) => this.packages[d]);
    },
    packageGroups() {
      const dataArray = this.packageGroupsInSelectedSources;
      return dataArray.filter(this.filterFn);
    },
    selectedPackageGroup: {
      get() {
        let group = this.packageGroup || this.selectedPackage;
        if (!group) {
          group = this.packageGroupsInAllSources.find((a) => a.name === (this.preselectedPackage || {}).name);
          setTimeout(() => {
            this.createVersionList();
          }, 500);
        }
        return group;
      },
      set(v) {
        this.packageGroup = v;
      },
    },
    selectedPackage: {
      get() {
        return this.value;
      },
      set(v) {
        this.$emit('input', v);
      },
    },

    filteredPackageList() {
      if (this.ecuNotSpecified) {
        return this.packagesInSelectedSources;
      }
      return this.packagesInSelectedSources.filter((a) => {
        return (a.hardwareType && a.hardwareType === this.ecu.hardwareId) || (a.name || '').split('/')[0] === this.ecu.hardwareId;
        //   || a.hardwareType == 'docker-compose'
      });
    },

    ecuNotSpecified() {
      return !this.ecu || !this.ecu.hardwareId;
    },
    ecuPackage() {
      return this.ecu.package;
    },
    noPackageSelectedForEcu() {
      return this.ecuPackage && !this.ecuPackage.name;
    },
    showNightlyWarning() {
      return !this.hideExperimentalWarning && this.packageSourceOptions.find((a) => a.name == 'tdx-nightly') && this.selectedDelegationSources.indexOf('tdx-nightly') > -1;
    },
    showBootloaderWarning() {
      // No need to show bootloader warning if it's a fleet update
      if (this.isFleetUpdate) {
        return false;
      }
      // No need to show bootloader warning if there is no package selected
      // Also, return false if selected package is not OS package
      if (!this.selectedPackage || !this.selectedPackage.isOSPackage) {
        return false;
      }
      // const installedPackage = this.packagesById[this.ecu.package.id];
      // determine if installed package is in user repository
      let pkgHash = ((this.ecu.image || {}).hash || {}).sha256;
      const installedPackage = this.packagesByHash[pkgHash];
      if (!installedPackage) {
        return false;
      }
      // get major designation for installed package if it has custom.ostreeMetadata.oe.tdx-major defined
      let installedMajor = installedPackage.custom && installedPackage.custom.ostreeMetadata ? installedPackage.custom.ostreeMetadata['oe.tdx-major'] : null;
      if (!installedMajor) {
        // get major designation for installed package from version string if it's a custom package
        installedMajor = installedPackage.isToradexPackage ? installedPackage.version[0] : null;
        if (!installedMajor) {
          return false;
        }
      }
      // get major designation for selected package if it has custom.ostreeMetadata.oe.tdx-major defined
      let selectedMajor = this.selectedPackage.custom && this.selectedPackage.custom.ostreeMetadata ? this.selectedPackage.custom.ostreeMetadata['oe.tdx-major'] : null;
      if (!selectedMajor) {
        // get major designation for selected package from version string if it's a custom package
        selectedMajor = this.selectedPackage.isToradexPackage ? this.selectedPackage.version[0] : null;
        if (!selectedMajor) {
          return false;
        }
      }
      // return true if major designation for installed package is different from major designation for selected package
      return installedMajor !== selectedMajor;
    },
  },
  methods: {
    ...mapActions({
      setUserOption: 'ui/setUserOption',
      saveSelectedDelegations: 'packages/saveSelectedDelegations',
    }),
    getUserOptionOrDefault(optionName, defaultValue) {
      const userSettings = this.userSettings[optionName];
      return typeof userSettings !== 'undefined' ? userSettings : defaultValue;
    },
    isEmpty(data) {
      return _.isEmpty(data);
    },
    versionSelectorCleared() {
      this.autoSelectWhenSingleVersion = false;
      setTimeout(() => {
        this.autoSelectWhenSingleVersion = true;
      }, 2000);
    },
    updateSelectedPackage(e) {
      this.selectedPackage = e;
    },
    formatVersion(l) {
      const maxLength = 35;
      const shortVersion = l.id.version.length > maxLength ? l.id.version.substring(0, maxLength) + '...' : l.id.version;
      return {
        label: l.commitSubject ? l.commitSubject : shortVersion,
        shortVersion: shortVersion,
        ...l,
      };
    },
    mapVersions(versions) {
      return (versions || []).map((l) => {
        return this.formatVersion(l);
      });
    },
    clearDropdownSelection() {
      this.packageGroup = null;
      this.updateSelectedPackage(null);
      if (this.packageListOptions && this.packageListOptions.length == 2) {
        this.packageGroup = this.packageListOptions[1];
      }
    },
    createVersionList() {
      const unfiltered = (this.selectedPackageGroup || {}).versions || [];
      let filtered = [];
      if (this.ecuNotSpecified) {
        filtered = unfiltered;
      } else {
        filtered = unfiltered.filter((a) => {
          const hw = a.hardwareIds || [];
          return _.includes(hw, this.ecu.hardwareId);
        });
      }
      filtered = _.sortBy(filtered, 'createdAt').reverse();
      this.versionList = filtered;
      if (filtered && filtered.length == 1 && this.autoSelectWhenSingleVersion) {
        this.updateSelectedPackage(filtered[0]);
      }
    },
  },
  watch: {
    ecu(n, o) {
      if (!this.ecu || !this.ecu.package) {
        this.clearDropdownSelection();
      }
    },
    packageGroup(n, o) {
      if (o && n) {
        this.selectedPackage = null;
      }
    },
    selectedPackageGroup(n, o) {
      this.createVersionList();
    },
    ecuPackage(n, o) {},
    selectedPackage(n, o) {},
    showBootloaderWarning(n, o) {
      this.$emit('showBootloaderWarning', n);
    },
  },
};
</script>

<style lang="scss" scoped>
.q-item {
  &.preselected {
    border-left: 0.2rem solid $positive;
  }
  &.active {
    border-left: 0.2rem solid $primary;
  }
}
</style>
