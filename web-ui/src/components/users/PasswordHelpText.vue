<template>
  <div class="h-100 w-50 flex flex-center">
    <div class="login-text p-5">
      <account-creation-header></account-creation-header>
      <div class="p-5">
        <h1>Password Help</h1>
        <div>
          <p>Enter your registered email address to continue.</p>
          <p>
            If you would like to proceed by signing in
            <q-btn @click="gotoLogin" dense flat no-caps color="primary">click here.</q-btn>
          </p>
          <p>
            If you don’t have an account yet, you can
            <q-btn @click="gotoRegister" dense flat no-caps color="primary">create a new account.</q-btn>
          </p>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import AccountCreationHeader from './AccountCreationHeader';

export default {
  name: 'LoginTextComponent',
  components: {
    AccountCreationHeader,
  },
  data() {
    return {};
  },
  methods: {
    gotoRegister() {
      this.$emit('goto:register', '');
    },
    gotoLogin() {
      this.$emit('goto:login', '');
    },
  },
};
</script>
