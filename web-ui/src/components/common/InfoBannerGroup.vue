<template>
  <div
    class="shadow-3 info-banner-group-div"
    :class="{
      'bg-white text-grey-10 ': !$q.dark.isActive,
      'bg-dark-bg text-dark-bg': $q.dark.isActive,
    }"
  >
    <slot></slot>
  </div>
</template>

<script>
export default {
  name: 'InfoBannerGroup',
  components: {},
};
</script>

<style lang="scss" scoped>
// #info-banner-group {
//   position: relative;
//   width: 100%;
//   height: 100%;
// }
// .q-banner {
//   position: absolute;
//   top: 0;
//   left: 0;
//   width: 100%;
// }
.info-banner-group-div {
  box-shadow: 2px 2px 8px 0 rgba(0, 0, 0, 0.05);
  .info-banner:not(:first-child) {
    display: none;
  }
}
</style>
