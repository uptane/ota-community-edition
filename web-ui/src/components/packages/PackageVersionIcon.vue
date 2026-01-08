<template>
  <span>
    <q-icon :size="size" v-if="version.buildType === 'custom'" name="img:statics/svg/icons/custom-build.svg" />
    <q-icon :size="size" v-else-if="version.buildType === 'nightly'" name="img:statics/svg/icons/nightly-build.svg" />
    <q-icon :size="size" v-else-if="version.buildType === 'monthly'" name="img:statics/svg/icons/monthly-build.svg" />
    <q-icon :size="size" v-else-if="version.buildType === 'major'" name="img:statics/svg/icons/major-build.svg" />
    <q-icon :size="size" v-else name="img:statics/svg/icons/custom-build.svg" />
  </span>
</template>

<script>
export default {
  name: 'PackageVersionIcon',
  props: {
    version: {
      type: Object,
      default: () => {
        return {};
      },
    },
    size: {
      type: String,
      default: '1.2rem',
    },
  },
};
</script>
