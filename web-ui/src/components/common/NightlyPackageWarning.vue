<template>
  <q-banner
    style="margin: 0.1em;"
    :class="{
      ['bg-negative']: true,
    }"
    class="text-white"
  >
    <div class=" row  items-center  justify-center">
      <div class="col-auto"><q-icon size="1.6rem" :name="'dangerous'"></q-icon>&nbsp;</div>
      <div class="col-auto">Please note that these packages are experimental can be unstable. &nbsp;<a class="text-white" href="https://developer.toradex.com/knowledge-base/toradex-embedded-linux-bsp-support-strategy" target="_blank">Learn more</a></div>
    </div>
  </q-banner>
</template>

<script>
export default {
  name: 'NightlyPackageWarning',
};
</script>
