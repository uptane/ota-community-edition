<template>
  <span>
    <template v-if="packageInfo.isCustom">
      <q-icon v-if="packageInfo.isOSPackage" :size="size" :name="'img:statics/svg/custom-os-package.svg'"></q-icon>
      <q-icon v-else :size="size" :name="'img:statics/svg/custom-package.svg'"></q-icon>
    </template>
    <q-icon v-else :size="size" :name="`img:statics/svg/torizon-package-${packageInfo.buildType || 'release'}.svg`"></q-icon>
  </span>
</template>

<script>
export default {
  name: 'PackageIcon',
  props: {
    packageInfo: {
      type: Object,
      default: () => {
        return {};
      },
    },
    size: {
      type: String,
      default: '2.5rem',
    },
  },
  computed: {},
};
</script>
