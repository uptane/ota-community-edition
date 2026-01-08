<!-- eslint-disable -->
<template>
  <div id="scroll-container" ref="parent" class="no-scrollbar-wrapper2" style="overflow:auto; height: 100vh">
    <!-- <div
      ref="child"
      class="no-scrollbar-wrapper-content"
    > -->
    <slot></slot>
    <!-- </div> -->
  </div>
</template>

<script>
/*  eslint-disable */
export default {
  mounted() {
    document.querySelector('body').style.overflow = 'hidden';
  },
};
</script>
