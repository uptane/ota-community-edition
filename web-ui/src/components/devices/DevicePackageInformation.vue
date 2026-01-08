<template>
  <div class=" no-shadow">
    <div class="p-0">
      <h6 class="m-0 opacity-60">Device Components</h6>
      <template v-if="!loadingPackageData && noData">
        <div class="opacity-30 ">Not available</div>
      </template>
      <template v-if="loadingPackageData">
        <div class="p-1">
          <loader></loader>
          <span class="opacity-30">Loading</span>
        </div>
      </template>

      <div class="row w-100" v-if="!noData && !loadingPackageData">
        <q-item class="p-0 col-xs-12 col-sm-12 col-md-6 col-lg-4 col-xl-3 mb-1 mnw-30em" v-for="(component, key) in directorAttributes" :key="'device-component' + key">
          <q-item-label>
            <div class="">
              <device-component :maxWidth="'100%'" :minWidth="'20em'" :component="component" :dense="true" />
            </div>
          </q-item-label>
        </q-item>
      </div>
    </div>
  </div>
</template>

<script>
import { mapGetters, mapActions } from 'vuex';
import _ from 'lodash';
import PackageIcon from '../packages/PackageIcon.vue';
import PackageVersionIcon from '../packages/PackageVersionIcon.vue';
import Tooltip from '../common/Tooltip.vue';
import PackageInfo from '../packages/PackageInfo.vue';
import DeviceComponent from './DeviceComponent.vue';
export default {
  components: { PackageIcon, PackageVersionIcon, Tooltip, PackageInfo, DeviceComponent },
  name: 'DevicePackageInformation',
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
      loadingPackageData: false,
      defaultHash: 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
    };
  },
  methods: {
    ...mapActions({
      fetchPackages: 'packages/fetchPackages',
    }),
    viewPackageContent(pkg) {
      this.$events.$emit('dialogs:package-content', pkg);
    },
    setup() {
      this.loadingUpdatedData = true;
    },
  },
  computed: {
    ...mapGetters({
      devices: 'devices/devices',
      packages: 'packages/packages',
      packagesByHash: 'packages/packagesByHash',
      packagesById: 'packages/packagesById',
    }),
    directorAttributes() {
      let primarySorted = _.sortBy(this.device.components || [], ['order', 'hardwareId']);
      return primarySorted;
    },
    packageInfo() {
      const primary = (this.device.directorAttributes || {}).primary || { image: { hash: { sha256: '' } } };
      const secondaries = (this.device.directorAttributes || {}).secondary || [];
      const primaryPackage = this.packagesById[(primary.image || {}).filepath] || { attr: primary };
      const secondaryPackages =
        secondaries.map((m) => {
          return this.packagesById[(m.image || {}).filepath] || { attr: m };
        }) || [];
      return {
        primary: primaryPackage,
        secondaries: secondaryPackages,
      };
    },
    noData() {
      return !this.packageInfo || (!this.packageInfo.primary.name && !this.packageInfo.primary.attr.image.hash.sha256 && (!this.packageInfo.secondaries || this.packageInfo.secondaries.length < 1));
    },
  },
};
</script>
