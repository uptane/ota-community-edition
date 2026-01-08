<template>
  <span v-if="hasDeltas && oneLine" class="">
    <span v-if="hasDeltaToHash">
      <span
        class="text-secondary"
        v-if="icon && !iconOnRight"
        :class="{
          [iconCssClass]: iconCssClass,
        }"
      >
        &Delta;
      </span>
      &nbsp;<span
        :class="{
          [textCssClass]: textCssClass,
        }"
        >Delta available ({{ $format.humanStorageSize(deltaToHashSize) }})</span
      >
      <span
        class="text-secondary text-large"
        v-if="icon && iconOnRight"
        :class="{
          [iconCssClass]: iconCssClass,
        }"
      >
        &Delta;
      </span>
    </span>
  </span>
  <div v-else-if="hasDeltas" class="col-auto pr-2 mt-2">
    <div class="q-item-tile label ellipsis mxw-90">
      <span class="text-secondary text-large">
        &Delta;
      </span>
      Deltas
    </div>
    <div class="text-bold mt-1 ">From this package to...</div>
    <template v-if="deltasFrom && deltasFrom.length">
      <div v-for="delta in deltasFrom" :key="delta.hash" class="mb-2">
        <div class="q-item-tile sublabel created-at pt-1 opacity-100">
          <span
            class="pr-1 opacity-100"
            :class="{
              'text-warning': delta.package.missing,
            }"
          >
            <a no-caps class="p-0 m-0 text-primary" :href="'#/packages/?id=' + delta.package.hash">{{ delta.package.id }}</a></span
          >
        </div>
        <div class="q-item-tile sublabel created-at opacity-100">
          <span class="pr-1 opacity-100">Size:</span>
          <span class="">{{ $format.humanStorageSize(delta.size) }}</span>
        </div>
      </div>
    </template>
    <div v-else class="q-item-tile sublabel created-at pt-1 opacity-100">
      <span class="pr-1 opacity-100">- None</span>
    </div>
    <div class="text-bold  mt-1 ">To this package from...</div>
    <template v-if="deltasTo && deltasTo.length">
      <div v-for="delta in deltasTo" :key="delta.hash" class="mb-2">
        <div class="q-item-tile sublabel created-at pt-1 opacity-100">
          <span
            class="pr-1 opacity-100"
            :class="{
              'text-warning': delta.package.missing,
            }"
            ><a no-caps class="p-0 m-0 text-primary" :href="'#/packages/?id=' + delta.package.hash">{{ delta.package.id }}</a></span
          >
        </div>
        <div class="q-item-tile sublabel created-at opacity-100">
          <span class="pr-1 opacity-100">Size:</span>
          <span class="">{{ $format.humanStorageSize(delta.size) }}</span>
        </div>
      </div>
    </template>
    <div v-else class="q-item-tile sublabel created-at pt-1 opacity-100">
      <span class="pr-1 opacity-100">- None</span>
    </div>
  </div>
</template>

<script>
import { mapActions, mapGetters } from 'vuex';

export default {
  name: 'StaticDeltaInfo',
  props: {
    packageHash: {
      type: String,
      default: '',
    },
    toHash: {
      type: String,
      default: '',
    },
    oneLine: {
      type: Boolean,
      default: false,
    },
    iconCssClass: {
      type: String,
      default: '',
    },
    textCssClass: {
      type: String,
      default: '',
    },
    icon: {
      type: Boolean,
      default: true,
    },
    iconOnRight: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      show: false,
    };
  },
  computed: {
    ...mapGetters({
      staticDeltas: 'packages/deltas',
      packagesByHash: 'packages/packagesByHash',
    }),
    deltas() {
      return this.staticDeltas[this.packageHash] || {};
    },
    hasDeltas() {
      return (this.deltas && (this.deltas.from || []).length) || (this.deltas.to || []).length;
    },
    deltaToHash() {
      return (this.deltasFrom || []).find((delta) => delta.hash === this.toHash);
    },
    hasDeltaToHash() {
      return !!this.deltaToHash;
    },
    deltaToHashSize() {
      return (this.deltaToHash || {}).size;
    },
    deltasFrom() {
      return (this.deltas.from || []).map((delta) => {
        const hash = delta.hash;
        return {
          ...delta,
          package: this.packagesByHash[hash] || { hash, name: 'Unknown package', missing: true },
        };
      });
    },
    deltasTo() {
      return (this.deltas.to || []).map((delta) => {
        const hash = delta.hash;
        return {
          ...delta,
          package: this.packagesByHash[hash] || { hash, name: 'Unknown package', missing: true },
        };
      });
    },
  },
};
</script>
