<template>
  <div>
    <template v-for="(target, index) in packageData">
      <div
        class="ellipsis w-100"
        :key="index"
        v-if="index < 3"
        :class="{
          'h-divide-top-dotted': index > 0,
        }"
      >
        <package-icon size="1.2em" :packageInfo="target"></package-icon> {{ target.packageName }}:{{ target.versionName }} <span class="faded" :key="index + 'more'" v-if="packageData.length > 3 && index === 2">(+ {{ packageData.length + -(index + 1) }} more)</span>
      </div>
    </template>
  </div>
</template>

<script>
import PackageIcon from '../packages/PackageIcon.vue';
export default {
  components: { PackageIcon },
  name: 'LockboxTablePackageContent',
  props: {
    packageData: {
      type: Array,
      required: true,
      default: () => [],
    },
  },
};
</script>

<style></style>
