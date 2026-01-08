<template>
  <q-item
    class="q-item-tile row pl-0 ml-0"
    :class="{
      'm-0 p-0': dense,
    }"
  >
    <q-item-section :size="iconSize" class="col-auto">
      <package-icon v-if="knownPackage" :size="iconSize" :package-info="pkg" />
      <q-icon v-else name="img:/statics/svg/unknown-package.svg" :size="iconSize"> </q-icon>
    </q-item-section>
    <q-item-section
      class="col"
      :style="{
        'max-width': maxWidth,
        'min-width': minWidth,
      }"
    >
      <q-item-label
        class="row w-100 text-weight-bold"
        caption
        :class="{
          ellipsis: truncate,
          faded: fadedTitle,
        }"
      >
        <div
          :style="{
            position: 'relative',
          }"
          class="col-auto ellipsis q-pr-sm mxw-90"
        >
          {{ title }}
        </div>
        <span class=" col-auto">
          <copy-to-clipboard :text="title" />
        </span>
      </q-item-label>
      <q-item-label caption class="faded ellipsis w-100"
        ><span v-if="showLabels">Version: </span>
        <span class="col  q-pr-sm">{{ pkg.versionName }}</span>
        <span class="col-auto">
          <copy-to-clipboard :text="pkg.versionName" />
        </span>
      </q-item-label>
      <q-item-label caption class="row   w-100" v-if="showHash">
        <span class="q-mr-sm faded" v-if="showLabels">Hash: </span>
        <span class="col-auto mxw-85 ellipsis q-pr-sm">
          <a v-if="linkToPackage" no-caps class="p-0 m-0 text-primary" :href="'#/packages/?name=' + pkg.name + '&id=' + pkg.hash" @click.stop.prevent="$router.push({ name: 'packages', query: { id: pkg.hash } })">{{ pkg.hash }}</a>
          <span v-else class="faded">{{ pkg.hash }}</span>
        </span>
        <span class="col-auto">
          <copy-to-clipboard :text="pkg.hash" />
        </span>
      </q-item-label>
      <q-item-label caption lines="1">
        <div v-if="showLatestFlag && pkg.latest" class="row q-item-tile  pt-0">
          <q-icon name="task_alt" class="q-mr-sm" size="1.2em" color="positive" />
          <span class="sublabel">Latest version</span>
        </div>
      </q-item-label>
      <q-item-label caption lines="1">
        <slot name="additional-content"></slot>
      </q-item-label>
    </q-item-section>
  </q-item>
</template>

<script>
import PackageIcon from './PackageIcon.vue';
import { copyToClipboard } from 'quasar';
import Tooltip from '../common/Tooltip.vue';
import { EMPTY_STRING_HASH } from '../../constants';
import CopyToClipboard from '../common/CopyToClipboard.vue';
export default {
  components: { PackageIcon, Tooltip, CopyToClipboard },
  name: 'PackageInfo',
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
    fadedTitle: {
      type: Boolean,
      default: false,
    },
    pkg: {
      type: Object,
      default: () => {
        return {};
      },
    },
    linkToPackage: {
      type: Boolean,
      default: false,
    },
    showLatestFlag: {
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
    title() {
      if (!this.pkg.name || this.pkg.hash === EMPTY_STRING_HASH) {
      }
      return this.pkg.name || this.pkg.hash;
    },
    imageHash() {
      return this.pkg.hash;
    },
    emptyPackageHash() {
      const isEmpty = this.imageHash === EMPTY_STRING_HASH;
      return isEmpty;
    },
    knownPackage() {
      return !this.emptyPackageHash;
    },
    rawPkgInfo() {
      return {
        identifier: this.imageFilepath,
        hash: this.imageHash,
      };
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
