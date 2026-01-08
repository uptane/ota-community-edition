<template>
  <div class="h-100 w-50 flex flex-center">
    <div class="register-text p-5">
      <account-creation-header></account-creation-header>
      <div class="p-5">
        <h4>Create a new account</h4>
        <div>
          <p>Enter your full name, valid email, secure password and other required information to continue.</p>
          <p>
            Your password must meet our minimum
            <q-btn dense flat no-caps color="primary">
              password requirement.
              <q-tooltip transition-show="flip-right" transition-hide="flip-left" content-class="bg-black">
                <ul class="m-0 pl-2 p-1">
                  <li>Minimun of 8 characters long</li>
                  <li>At least one uppercase letter (ABC...Z)</li>
                  <li>At least one lowercase letter (abc...z)</li>
                  <li>At least one number (1234567890)</li>
                  <li>At least one symbol (!@#$%^&amp;*&gt;!&lt;/\?)</li>
                </ul>
              </q-tooltip>
            </q-btn>
          </p>
          <p>
            If you already have an account, you can
            <q-btn @click="gotoLogin" dense flat no-caps color="primary">sign in</q-btn>here
          </p>
          <p>You must accept our <q-btn type="a" :href="termsLink" target="_blank" flat padding="xs" no-caps color="primary">Terms of Services</q-btn> in order to create an account.</p>
          <p>
            Be sure to also read our
            <q-btn dense type="a" :href="privacyLink" target="_blank" flat no-caps color="primary">privacy policy.</q-btn>
          </p>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { PRIVACY_POLICY_LINK } from '../../config';
import AccountCreationHeader from './AccountCreationHeader';
export default {
  name: 'RegisterTextComponent',
  components: {
    AccountCreationHeader,
  },
  data() {
    return {
      privacyLink: PRIVACY_POLICY_LINK,
      termsLink: '/statics/html-contents/terms-2021-01-01.html',
    };
  },
  methods: {
    gotoLogin() {
      this.$emit('goto:login');
    },
  },
};
</script>
