<template>
  <q-banner
    style="margin: 0.1em;"
    :class="{
      ['bg-warning']: true,
    }"
    class="text-white"
  >
    <div class=" row  items-center  justify-center">
      <div class="col-auto pr-1"><q-icon size="1.6rem" :name="'warning'"></q-icon>&nbsp;</div>
      <div class="col">
        Be careful as you are doing a major release update. You may need to update other software layers. <br />
        Please read our .
        <a href="https://developer.toradex.com/torizon/torizon-platform/torizon-updates/bootloader-updates-in-torizoncore/" target="_blank">documentation on major TorizonCore updates</a>.
      </div>
    </div>
  </q-banner>
</template>

<script>
export default {
  name: 'BootloaderUpdateWarning',
};
</script>
