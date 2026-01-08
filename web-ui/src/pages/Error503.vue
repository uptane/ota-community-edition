<template>
  <div class="fixed-center text-center text-center h-100 w-100">
    <!-- :style="{
          backgroundImage: `url('${maintenanceImg}')`,
          backgroundSize: 'cover',
          backgroundPosition: '100% 100%',
        }" -->

    <!-- <strong class="text-faded">Sorry, nothing here...<strong>(404)</strong></p> -->
    <!-- <q-btn
      color="secondary"
      style="width:200px;"
      @click="$router.push('/')"
    >Go back</q-btn> -->

    <h3 class="flex text-center flex-center">
      <!-- <img class="self-center" style="height:2rem;" src="~assets/svg/toradex-logo-1.svg" /> -->
      <img class="self-center" style="height:6rem;" src="/statics/icon-1240.png" />
      <span class="self-center">OTA Admin</span>
    </h3>
    <h5 class="text-thin">Scheduled Maintenance</h5>
    <div class="row">
      <div class="col-12 pb-1">
        This application is currenly undergoing maintenance. Please check back later.
      </div>
      <div class="col-12">
        <img src="statics/svg/maintenance.svg" />
      </div>
    </div>
    <!-- <small class="absolute-bottom">
    <a href="https://www.freepik.com/free-photos-vectors/city">City vector created by slidesgo - www.freepik.com</a></small> -->
  </div>
</template>

<script>
export default {
  name: 'Error404',
  computed: {
    maintenanceImg() {
      /* eslint-disable global-require */
      return 'statics/svg/maintenance.svg';
    },
  },
};
</script>
