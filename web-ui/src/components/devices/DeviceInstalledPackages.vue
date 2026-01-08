<template>
  <div>
    <template v-if="loading">
      <q-spinner-hourglass color="primary" size="1.5em" class="q-ma-auto"></q-spinner-hourglass> Loading...
    </template>
    <template v-else>
      <template v-if="type == 'os'">
        <package-info v-if="!isEmpty(targets.os) && !isEmpty(targets.os.packageInfo)" :pkg="targets.os.packageInfo" :maxWidth="maxWidth" :truncate="truncateLongText" dense icon-size="md"></package-info>
        <span class="opacity-40" v-else> No image version reported</span>
      </template>
      <template v-if="type == 'application'">
        <package-info v-if="!isEmpty(targets.application) && !isEmpty(targets.application.packageInfo)" :pkg="targets.application.packageInfo" :maxWidth="maxWidth" :truncate="truncateLongText" dense icon-size="md"></package-info>
        <span class="opacity-40" v-else> No image version reported</span>
      </template>
      <template v-if="type == 'others'">
        <template v-if="!isEmpty(targets.others)">
          <div v-for="(pkg, index) in targets.others" :key="device.uuid + '_installed_pkg_' + index">
            <div
              class="col-auto row"
              :class="{
                'h-divide-top-dashed': index > 0,
              }"
            >
              <div class="col-auto q-mr-sm q-pr-xs v-divide-right-dashed">
                <span class="opacity-30">{{ index + 1 }}</span>
              </div>
              <div class="col">
                <package-info v-if="!isEmpty(pkg.packageInfo)" :pkg="pkg.packageInfo" :truncate="truncateLongText" :maxWidth="maxWidth" icon-size="md" dense></package-info>
                <span class="opacity-40" v-else>
                  Package info not available
                  <div class="row mxw-20em">
                    <div class="col-auto">Hash:&nbsp;</div>
                    <div class="col ellipsis">{{ pkg.targetInfo.hash }}</div>
                    <div class="col-auto">
                      <copy-to-clipboard :text="pkg.targetInfo.hash" />
                    </div>
                  </div>
                </span>
              </div>
            </div>
          </div>
        </template>
        <span class="opacity-40" v-else> Not available</span>
      </template>
    </template>
  </div>
</template>

<script>
import { mapGetters, mapMutations } from 'vuex';
import CopyToClipboard from '../common/CopyToClipboard.vue';
import PackageInfo from '../packages/PackageInfo.vue';
export default {
  components: { PackageInfo, CopyToClipboard },
  name: 'DeviceInstalledPackages',
  props: {
    device: {
      type: Object,
      default: () => ({}),
    },
    maxWidth: {
      type: String,
      default: '20em',
    },
    truncateLongText: {
      type: String,
      default: false,
    },
    type: {
      type: String,
      default: 'os',
    },
  },
  data() {
    return {
      targets: {
        primary: {},
        secondaries: [],
      },
      loading: true,
      _installedTargets: [],
    };
  },
  computed: {
    ...mapGetters({
      packagesById: 'packages/packagesById',
      packagesByHash: 'packages/packagesByHash',
    }),
    installedTargets: {
      get() {
        return this.$data._installedTargets;
      },
      set(value) {
        this.$data._installedTargets = value;
        this.updateSingleDevice({ uuid: this.device.uuid, installedTargets: value });
      },
    },
  },
  methods: {
    ...mapMutations({
      updateSingleDevice: 'devices/updateSingleDevice',
    }),
    isEmpty(value) {
      return _.isEmpty(value);
    },
    parsePackageInfo() {
      const packages = (this.installedTargets || []).map((a) => {
        let pkg = this.packagesById[a.filename] || this.packagesByHash[a.hash];
        return { targetInfo: a, packageInfo: pkg || {} };
      });
      const primaryPackage = packages.find((f) => f.targetInfo.isPrimary);
      const application = packages.find((f) => f.targetInfo.hardwareId === 'docker-compose');
      const secondaryPackages = packages.filter((f) => !f.targetInfo.isPrimary && f.targetInfo.hardwareId !== 'docker-compose');
      return {
        os: primaryPackage,
        application: application,
        others: secondaryPackages,
      };
    },
    async fetchDeviceInstalledPackages() {
      try {
        const targets = (await this.$store.dispatch('devices/getInstalledTargets', [this.device.uuid])).values[this.device.uuid];
        this.installedTargets = targets || [];
      } catch (e) {
        this.installedTargets = [];
        console.error('Error fetching installed packages', e);
      }
    },
    ensurePackagesLoaded() {
      return new Promise((resolve, reject) => {
        let count = 0;
        let interval = setInterval(() => {
          if (!this.isEmpty(this.packagesById) || !this.isEmpty(this.packagesByHash)) {
            clearInterval(interval);
            this.loading = false;
            this.targets = this.parsePackageInfo();
            resolve();
          }
          count++;
          // We wait for 10 seconds before giving up
          if (count > 100) {
            clearInterval(interval);
            this.loading = false;
            reject('Packages not loaded');
          }
        }, 100);
      });
    },
    async prepareData() {
      this.loading = true;
      // If devices does not have installedPackages property, then try to fetch it
      if (!this.installedTargets) {
        try {
          await this.fetchDeviceInstalledPackages();
        } catch (e) {}
      }
      // Await packages to be loaded before parsing package info
      await this.ensurePackagesLoaded();
    },
  },
  mounted() {
    this.installedTargets = this.device.installedTargets;
    this.prepareData();
  },

  watch: {
    device: {
      handler(newValue, oldValue) {
        if (newValue && oldValue && newValue.uuid !== oldValue.uuid) {
          this.prepareData();
        }
      },
      deep: true,
    },
  },
};
</script>

<style></style>
