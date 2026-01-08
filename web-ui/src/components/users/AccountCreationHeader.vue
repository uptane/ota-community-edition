<template>
  <div @click="gotoLandingPage()">
    <div v-if="$q.screen.gt.md" class="flex items-center justify-center text-center p-1">
      <img class="self-center" style="height:3rem;" src="~assets/svg/toradex-logo-1.svg" />
      <div class="self-center pt-1 opacity-50">
        <span style="font-size: 2rem" class="ml-1">Platform</span>
      </div>
    </div>

    <div v-if="$q.screen.lt.lg" class="w-100vw mb-0 self-start animated slideInDown login-div-header justify-center text-center">
      <img class="self-center" style="height:4rem;" src="~assets/svg/toradex-logo-1.svg" />
      <div class=" opacity-50">
        <span class="ml-1" style="font-size: 1.4rem">Platform</span>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'AccountCreationHeader',
  methods: {
    gotoLandingPage() {
      this.$router.replace('/');
    },
  },
};
</script>
