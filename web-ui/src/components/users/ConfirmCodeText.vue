<template>
  <div class="h-100 w-50 flex flex-center">
    <div class="login-text p-5">
      <account-creation-header></account-creation-header>
      <div class="p-5">
        <h4>Confirm Registration</h4>
        <div>
          <p>Enter the confirmation code received via email or text message</p>
          <p>
            Having trouble finding the code?
            <q-btn @click="resendConfirmation" dense flat no-caps color="primary">Resend it.</q-btn>
          </p>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { AuthService } from '../../services/auth.service';
import AccountCreationHeader from './AccountCreationHeader';
export default {
  name: 'ConfirmCodeTextComponent',
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

    gotoPasswordHelp() {
      this.$emit('goto:password-help', '');
    },
    resendConfirmation($e) {
      const username = this.$q.sessionStorage.getItem('email_verification_username');
      if (username) {
        AuthService.resendConfirmation({ username })
          .then((result) => {
            this.$router.push({ name: 'confirmCode' });
          })
          .catch((err) => {
            this.error = err;
            logError(err);
          });
      }
    },
  },
};
</script>
