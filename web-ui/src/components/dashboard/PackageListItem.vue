<template>
  <div class="row w-100">
    <div class="col-auto">
      <package-icon size="1.5rem" style="padding-right: .2rem;" :package-info="itemData"></package-icon>
    </div>
    <div class="col">
      <div class="ellipsis text-bold">{{ itemData.name }}</div>
      <div style="padding-top: .0rem;" class="text-caption ellipsis">
        <package-version-icon :version="itemData" size="0.80rem"></package-version-icon>
        <span class="opacity-50">{{ itemData.version }} ({{ itemData.buildType }})</span>
      </div>
    </div>
  </div>
</template>

<script>
import PackageIcon from '../packages/PackageIcon.vue';
import PackageVersionIcon from '../packages/PackageVersionIcon.vue';
export default {
  name: 'PackageListItem',
  components: { PackageVersionIcon, PackageIcon },
  props: {
    itemData: {
      type: Object,
      default: () => {
        return {};
      },
    },
  },
};
</script>
