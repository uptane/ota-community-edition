<template>
  <div class="fixed-center text-center">
    <p>
      <img src="/statics/svg/sad-blink.svg" style="width:40vw;max-width:250px;" />
    </p>
    <p class="opacity-80 text-4">Gone, baby, gone.<strong></strong></p>
    <p class="opacity-40 text-2">Sorry, this page is not available.<strong></strong></p>
    <br />
    <q-btn color="secondary" style="width:200px;" @click="$router.push('/')">Go back</q-btn>
  </div>
</template>

<script>
export default {
  name: 'Error404',
};
</script>
