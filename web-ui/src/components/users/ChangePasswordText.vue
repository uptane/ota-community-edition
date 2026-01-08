<template>
  <div class="register-text p-5  h-100">
    <div class="flex items-center justify-center text-center p-1">
      <img class="self-center" style="height:3rem;" src="~assets/svg/toradex-logo-1.svg" />
      <div class="text-thin self-center pt-1">
        <span style="font-size: 2rem" class="ml-1">OTA Admin</span>
      </div>
    </div>
    <div class="p-5">
      <h4>Change Your Password</h4>
      <div>
        <p>Enter your new password to continue.</p>
        <p>
          Your password must meet our minimum
          <q-btn dense flat no-caps color="primary">password requirement.</q-btn>
        </p>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'ChangePasswordTextComponent',
  data() {
    return {};
  },
  methods: {
    gotoLogin() {
      this.$emit('goto:login');
    },
  },
};
</script>
