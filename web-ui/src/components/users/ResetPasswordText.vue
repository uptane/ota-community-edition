<template>
  <div class="h-100 w-50 flex flex-center">
    <div class="register-text p-5">
      <account-creation-header></account-creation-header>
      <div class="p-5">
        <h4>Reset Your Password</h4>
        <div>
          <p>Enter the code you received and your new password to continue.</p>
          <p>
            Your password must meet our minimum
            <q-btn dense flat no-caps color="primary">password requirement.</q-btn>
          </p>
        </div>
      </div>
    </div>
  </div>
</template>
<script>
import AccountCreationHeader from './AccountCreationHeader';
export default {
  name: 'ChangePasswordTextComponent',
  components: {
    AccountCreationHeader,
  },
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
