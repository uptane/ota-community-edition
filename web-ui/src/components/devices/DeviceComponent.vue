<template>
  <q-item
    class="q-item-tile row pl-0 ml-0 items-center justify-center"
    :class="{
      'm-0 p-0': dense,
    }"
  >
    <q-item-section class="col">
      <template v-if="!hideComponentInfo">
        <div class="row q-item-tile label pt-1 ">{{ title }}</div>
        <div class="row q-item-tile sublabel">
          <span class="q-mr-sm">Type: </span>
          <span class="">{{ component.hardwareId }}</span>
        </div>
        <span class="sublabel q-mr-sm">Installed package: </span>
      </template>
      <package-info v-if="packageMatchedByIdAndHash || (packageMatchedByHash && isUniqueHash)" :pkg="pkg" :show-labels="true" :show-hash="true" :max-width="maxWidth" :min-width="minWidth" class="pt-0 mt-0" faded-title link-to-package truncate show-latest-flag></package-info>
      <div class="" v-else-if="packageMatchedByHash && !isUniqueHash">
        <package-info :pkg="pkg" :show-labels="true" :show-hash="true" :max-width="maxWidth" :min-width="minWidth" class="pt-0 mt-0 pb-0 mb-0" faded-title truncate show-latest-flag></package-info>

        <div v-if="!hideWarning" class="row q-item-tile  pt-0">
          <div class="pr-1 ellipsis-2-lines mxw-90">
            <q-icon name="info" color="info" size="1.2em" class="q-mr-xs" /><span class="sublabel">The installed package matches </span>
            <span class="text-primary cursor-pointer"
              >multiple versions
              <tooltip>
                <span class="">Click here to view matched versions.</span>
              </tooltip>
              <q-popup-proxy>
                <q-card class="mxh-40em">
                  <q-card-section>
                    <h5 class="m-0 mb-1">Matched Versions</h5>
                    <package-info v-for="(match, mKey) in matchesByHash" :key="'pkg-matches-by-hash-' + mKey" :pkg="match" link-to-package :show-labels="true" :show-hash="true" :max-width="maxWidth" :min-width="minWidth" truncate class=""></package-info>
                  </q-card-section>
                </q-card>
              </q-popup-proxy> </span
            ><span class="sublabel"> in your repository</span>
          </div>
        </div>
      </div>
      <div v-else class="row pt-0 mt-0">
        <q-item-section class="col-auto">
          <q-icon name="img:/statics/svg/unknown-package.svg" color="warning" size="lg" class="mr-1" />
          <tooltip v-if="!hideWarning">
            <div v-if="emptyPackageHash">This component reports no installed package</div>
            <div v-else>This package is not authorized by your repository</div>
          </tooltip>
        </q-item-section>
        <div class="col  mxw-25em">
          <div class="row q-item-tile sublabel w-100 ">
            <span class="col-auto q-mr-sm">Identifier: </span>
            <span class="col ellipsis">{{ rawPkgInfo.identifier }}</span>
            <span class="col-auto" v-if="rawPkgInfo.identifier !== 'unknown'">
              <copy-to-clipboard :text="rawPkgInfo.identifier" />
            </span>
          </div>
          <div class="row q-item-tile sublabel w-100">
            <span class="col-auto q-mr-sm">Hash:&nbsp;</span>
            <span class="col ellipsis ">{{ rawPkgInfo.hash }}</span>
            <span class="col-auto">
              <copy-to-clipboard :text="rawPkgInfo.hash" />
            </span>
          </div>
          <div class="text-warning" v-if="!hideWarning">
            <div v-if="emptyPackageHash">
              <q-icon name="warning" color="warning" size="xs" class="q-mr-xs" />
              <span class="sublabel"> This component reports no installed package</span>
            </div>
            <div v-else>
              <q-icon name="warning" color="warning" size="xs" class="q-mr-xs" />
              <span class="sublabel"> This package is not authorized by your repository</span>
            </div>
          </div>
        </div>
      </div>
    </q-item-section>
  </q-item>
</template>

<script>
import { mapGetters, mapActions } from 'vuex';
import { copyToClipboard } from 'quasar';
import PackageIcon from 'src/components/packages/PackageIcon.vue';
import PackageInfo from 'src/components/packages/PackageInfo.vue';
import Tooltip from 'src/components/common/Tooltip.vue';
import { EMPTY_STRING_HASH } from 'src/constants';
import CopyToClipboard from '../common/CopyToClipboard.vue';
export default {
  components: { PackageIcon, PackageInfo, Tooltip, CopyToClipboard },
  name: 'DeviceComponent',
  props: {
    truncate: {
      type: Boolean,
      default: false,
    },
    maxWidth: {
      type: String,
      default: '20em',
    },
    minWidth: {
      type: String,
      default: '20em',
    },
    iconSize: {
      type: String,
      default: 'lg',
    },
    showHash: {
      type: Boolean,
      default: false,
    },
    showLabels: {
      type: Boolean,
      default: false,
    },
    dense: {
      type: Boolean,
      default: false,
    },
    component: {
      type: Object,
      default: () => {
        return {};
      },
    },
    hideComponentInfo: {
      type: Boolean,
      default: false,
    },
    hideWarning: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      copyIndicator: {},
    };
  },
  computed: {
    ...mapGetters({
      packagesByHash: 'packages/packagesByHash',
      packagesByIdAndHash: 'packages/packagesByIdAndHash',
      packagesInAllSources: 'packages/packagesInAllSources',
    }),
    image() {
      return this.component.image;
    },
    imageHash() {
      if (!this.image) return '';
      if (_.isObject(this.image.hash)) {
        return (this.image.hash || {}).sha256;
      }
      return this.image.hash;
    },
    imageFilepath() {
      return (this.component.image || {}).filepath;
    },
    imageIdAndHash() {
      return `${this.imageFilepath}-${this.imageHash}`;
    },
    pkgByIdAndHash() {
      return this.packagesByIdAndHash[this.imageIdAndHash];
    },
    pkgByHashOnly() {
      return this.packagesByHash[this.imageHash];
    },
    pkg() {
      return this.pkgByIdAndHash || this.pkgByHashOnly || this.rawPkgInfo;
    },
    installedPackageName() {
      return this.pkg.name;
    },
    installedPackageVersion() {
      return this.pkg.versionName;
    },
    installedPackageHash() {
      return this.pkg.hash;
    },
    packageMatchedByHash() {
      return !!this.pkgByHashOnly;
    },
    packageMatchedByIdAndHash() {
      return !!this.pkgByIdAndHash;
    },

    isUniqueHash() {
      return this.matchesByHash.length === 1;
    },
    matchesByHash() {
      const matches = this.packagesInAllSources.filter((pkg) => pkg.hash === this.imageHash) || [];
      return matches;
      // return [...matches, ...matches, ...matches];
    },
    emptyPackageHash() {
      const isEmpty = this.imageHash === EMPTY_STRING_HASH;
      return isEmpty;
    },
    rawPkgInfo() {
      return {
        identifier: this.imageFilepath,
        hash: this.imageHash,
      };
    },
    title() {
      let title = this.component.hardwareId;
      if (this.component.isBaseOS) {
        title = 'Base OS';
      } else if (this.component.isApplication) {
        title = 'Application Stack';
      } else if (this.component.isBootloader) {
        title = 'Bootloader Subsystem';
      } else if (this.component.isRemoteAccess) {
        title = 'Remote Access Agent';
      } else {
        title = 'Custom Subsystem';
      }
      return title;
    },
  },
  methods: {
    copyToClipboard(text, itemId) {
      copyToClipboard(text)
        .then(() => {
          this.$set(this.copyIndicator, itemId, true);
        })
        .finally(() => {
          setTimeout(() => {
            this.$set(this.copyIndicator, itemId, false);
          }, 2000);
        });
    },
  },
};
</script>

<style></style>
