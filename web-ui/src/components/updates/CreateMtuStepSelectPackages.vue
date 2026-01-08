<template>
  <div class="q-item-tile">
    <!-- <div class="text-h6 mb-2">Select Packages</div> -->
    <div class="row mb-1">
      <div class="col-auto mxw-40em">
        <div class="text-1 mr-1">Selected Components</div>
        <div
          class=" mxh-50vh"
          :class="{
            ' overflow-y-auto': !pulseActiveItem,
          }"
        >
          <q-list>
            <q-item
              clickable
              v-ripple
              :key="ecu.id"
              :class="{
                'animated bounceIn': currentEcu && currentEcu.id === ecu.id && pulseActiveItem,
              }"
              @click.native="setCurrentEcu(ecu)"
              :active="currentEcu && currentEcu.hardwareId === ecu.hardwareId"
              v-for="ecu in ecus"
            >
              <q-item-section avatar>
                <q-icon
                  :color="!ecu.package ? '' : 'primary'"
                  :name="ecu.package ? 'task_alt' : 'remove'"
                  :class="{
                    'animated bounceIn': ecu.package,
                  }"
                />
              </q-item-section>

              <q-item-section class="sublabel">{{ ecu.hardwareId }}</q-item-section>
            </q-item>
          </q-list>
        </div>
      </div>
      <div class="col v-divide-left-dashed pl-2 pr-1  mxh-50vh overflow-y-auto" v-if="currentEcu && currentEcu.hardwareId">
        <div class="text-1 mb-1 text-center">
          <span class="opacity-50">Available packages for </span>{{ currentEcu.hardwareId }}
          <small v-if="isLockbox">
            <br />
            <em>
              <span class="faded"> <q-icon name="info" size="1.25em"></q-icon>&nbsp;Only offline-update-ready packages are shown. </span>
              <a href="https://developer.toradex.com/torizon/torizon-platform/torizon-updates/first-steps-with-secure-offline-updates/#uploading-software-packages-to-torizon-platform" target="_blank">Learn more</a>
            </em>
          </small>
        </div>
        <package-selector
          v-model="selectedPackage"
          :ecu="currentEcu"
          :filter-fn="packagesFilterFunction"
          :preselected-package="currentEcu.installedPackage"
          :preselected-package-text="isLockbox ? 'Existing package in this update' : 'Currently installed package'"
          :preselected-version-text="isLockbox ? 'Existing version in this update' : 'Currently installed version'"
          :isFleetUpdate="isFleetUpdate"
          @showBootloaderWarning="setShowBootloaderWarning"
          ref="pkgSelector"
        ></package-selector>
        <template v-if="isCommercialUser">
          <advanced-update-entry :userDefinedCustom.sync="currentEcu.userDefinedCustom" :uri.sync="currentEcu.customUri"></advanced-update-entry>
        </template>
      </div>
      <div
        v-else
        class="
          col
          flex
          flex-center
          opacity-40
          text-1"
      >
        Select component on the left pane to view available packages
      </div>
    </div>
  </div>
</template>

<script>
import { mapActions, mapGetters } from 'vuex';
import Empty from '../common/Empty.vue';
import AdvancedUpdateEntry from 'src/components/updates/AdvancedUpdateEntry.vue';
import PackageSelector from '../packages/PackageSelector.vue';
export default {
  components: { PackageSelector, Empty, AdvancedUpdateEntry },
  name: 'CreateMtuStepSelectPackages',
  props: {
    value: {
      type: Array,
      default: () => [],
    },
    ecus: {
      type: Array,
      default: () => [],
    },
    isLockbox: {
      type: Boolean,
      default: false,
    },
    isFleetUpdate: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      currentEcu: {},
      pulseActiveItem: false,
    };
  },
  mounted() {
    this.setCurrentEcu(this.ecus[0]);
  },
  methods: {
    ...mapActions({}),
    packagesFilterFunction(item) {
      return this.ecuCompatiblePackagesFilter(item) && this.isLockboxDockerComposeAndLockfile(item);
    },
    isLockboxDockerComposeAndLockfile(item) {
      return !this.isLockbox || (!item.isApplicationPackage || item.custom.canonical_compose_file || _.endsWith(item.name, '.lock.yml') || _.endsWith(item.name, '.lock.yaml'));
    },
    ecuCompatiblePackagesFilter(item) {
      return item.hardwareIds.indexOf(this.currentEcu.hardwareId) > -1;
    },
    selectNextEcuWithEmptyPackage() {
      const nextEcu = this.ecus.find((f) => !f.package);
      const isSameEcu = nextEcu.id === this.currentEcu.id;
      if (nextEcu) {
        this.setCurrentEcu(nextEcu);
        this.$refs.pkgSelector.clearDropdownSelection();
      }
      return isSameEcu;
    },
    updateSelection(e) {
      this.$emit('input', e);
    },
    doPulseActiveItem() {
      this.pulseActiveItem = true;
      setTimeout(() => {
        this.pulseActiveItem = false;
      }, 1000);
    },
    setCurrentEcu(e) {
      this.currentEcu = e;
      let currentPackage;
      if (this.isLockbox) {
        if (e.package && e.package.filepath) {
          currentPackage = this.packagesById[e.package.filepath];
        }
      } else {
        currentPackage = !e.image || !e.image.filepath || e.image.filepath === 'unknown' || this.isFleetUpdate ? null : this.packagesById[e.image.filepath];
      }
      this.currentEcu.installedPackage = currentPackage;
      this.doPulseActiveItem();
    },
    setShowBootloaderWarning(v) {
      this.$emit('showBootloaderWarning', v);
    },
  },
  computed: {
    ...mapGetters({
      packages: 'packages/packages',
      packagesById: 'packages/packagesById',
      isCommercialUser: 'users/isCommercialUser',
    }),
    selectedPackage: {
      get() {
        return this.currentEcu && this.currentEcu.package ? this.currentEcu.package : null;
      },
      set(v) {
        this.$set(this.currentEcu, 'package', v);
      },
    },
  },
};
</script>
