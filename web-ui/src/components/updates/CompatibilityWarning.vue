<template>
  <q-card class="mnw-50em">
    <q-card-section class="text-h6 text-center"> <q-icon name="warning" size="1.2em" color="warning"></q-icon> Compatibility Warning </q-card-section>
    <q-card-section class="mxh-80vh " style="overflow:auto;">
      <div class="text-center text-warning mb-1">
        The following compatibility issues were found in your current package selection.
        <!-- <span v-if="isMultipleIncompatibility">Check the box next to each package to override this warning</span>: -->
      </div>
      <div v-for="(packages, filepath) in compatibilityResult.incompatibilities" :key="filepath">
        <div class="ml-1">
          <div>
            <package-info class="p-0 m-0" :pkg="packagesById[filepath]"></package-info>
            <div class="text-warning  col ml-5 mb-1" v-if="compatibilityResult.secondaryOnly">&nbsp;is not compatible with the following OS Package that is currently installed on the device:</div>
            <div class="text-warning  col ml-1 mb-1" v-else>&nbsp;The above package is not compatible with:</div>
          </div>

          <div class="ml-0 pl-0">
            <div class="row" v-for="(pkg, index) in packages" :key="index">
              <div class="col-auto">
                <!-- <q-checkbox
                  v-if="isMultipleIncompatibility"
                  :false-value="null"
                  :true-value="true"
                  v-model="skipCompatibilityCheck[pkg.packageHash]"
                >
                  <package-info
                    class="p-0 m-0"
                    :pkg="pkg"
                  ></package-info>
                </q-checkbox> -->
                <package-info class="p-0 m-0 mb-1" :pkg="pkg">
                  <template v-slot:additional-content>
                    <div v-if="compatibilityResult.secondaryOnly"><span class="sublabel">Device name: </span> {{ pkg.deviceInfo.name }}</div>
                    <div v-else><span class="sublabel">Component Name: </span> {{ pkg.hardwareId }}</div>
                  </template>
                </package-info>
              </div>
            </div>
          </div>
        </div>
      </div>
    </q-card-section>
    <q-card-section class="row h-divide-top mt-1">
      <q-btn no-caps outline color="primary" class="col" @click="$emit('close', skipCompatibilityCheck)" v-close-popup> Close this warning, but I would like to make some changes.</q-btn>
      <q-btn no-caps color="primary" class="col ml-1" @click="$emit('ack', skipCompatibilityCheck)" v-close-popup> I understand that these packages are not compatible, but I would like to continue with the update anyway.</q-btn>
    </q-card-section>
  </q-card>
</template>

<script>
import { mapGetters } from 'vuex';
import PackageIcon from '../packages/PackageIcon.vue';
import PackageInfo from '../packages/PackageInfo.vue';

export default {
  components: { PackageIcon, PackageInfo },
  name: 'CompatibilityWarning',
  props: {
    show: {
      type: Boolean,
      default: false,
    },
    compatibilityResult: {
      type: Object,
      default: () => {
        return {};
      },
    },
  },
  data() {
    return {
      skipCompatibilityCheck: {},
    };
  },
  computed: {
    ...mapGetters({
      packagesByHash: 'packages/packagesByHash',
      packagesById: 'packages/packagesById',
    }),
  },
  methods: {
    getPackageData(packageId) {
      return this.packagesById[packageId];
    },
  },
};
</script>

<style></style>
