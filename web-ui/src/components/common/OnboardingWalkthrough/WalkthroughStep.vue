<template>
  <div class="full-width onboarding-walkthrough-step">
    <slot></slot>
  </div>
</template>

<script>
export default {
  name: 'WalkthroughStep',
  props: {
    enterAnimation: {
      type: String,
      default: 'animated slideInRight',
    },
    leaveAnimation: {
      type: String,
      default: 'animated slideOutRight',
    },
  },
};
</script>

<style>
.onboarding-walkthrough-step {
  overflow-y: auto;
}
</style>
