<template>
  <q-dialog v-model="show" persistent @hide="onHide" @show="onShow">
    <div>
      <q-card class="p-0 ">
        <!-- <q-card-section v-if="confirmData.title">
                <div class="text-h6">{{confirmData.title}}</div>
        </q-card-section>-->

        <q-card-section class="p-0">
          <!-- <img :url="confirmData.image" /> -->
          <q-img style="max-height: 35em;" src="/statics/svg/xmas-bg.svg">
            <div class="absolute-bottom mt-5">
              <div class="text-h5">We wish you a happy holiday!</div>
              <p>We have created a special visual theme to celebrate this Xmas holiday. Will you like to activate it?</p>
              <!-- <div class="text-h6">Want to send us your feedback?</div> -->
              <!-- <div
                  class="text-subtitle2"
                > We invite you to join our slack channel at <a href="https://toradexlabs.slack.com/messages/ota/"
                class="text-primary" target="__blank">https://toradexlabs.slack.com/messages/ota/</a> to share your honest opinon and suggestions on how we can make this OTA experience better.
                We will also greatly appreciate if you can participate in our survey at <a href="https://www.surveymonkey.com/r/ota_labs_survey"
                class="text-primary" target="__blank">https://www.surveymonkey.com/r/ota_labs_survey</a>.
              </div>-->
              <!-- <div
                  class="text-subtitle2"
                >We hope that you enjoyed using our OTA system.  We would like to hear your honest opinon and suggestions on how we can make it better. We invite you to join the discussion on our slack channel <a href="https://toradexlabs.slack.com/messages/ota/"
                class="text-primary" target="__blank">https://toradexlabs.slack.com/messages/ota/</a>.
                We will greatly I appreciate if you can take a moment to take our survey .
                >We hope that you enjoyed using our OTA system and we would like to hear your honest opinon and suggestions on how we can make it better. We invite you to join the discussion on our slack channel <a href="https://www.surveymonkey.com/r/ota_labs_survey"
              class="text-primary" target="__blank">https://www.surveymonkey.com/r/ota_labs_survey</a>.-->

              <!-- <div
                  class="text-subtitle2"
              >We will like to take you to our external feedback form so you can let us know how we are doing and how we can make it better.</div>-->
            </div>
          </q-img>
        </q-card-section>
        <!-- <q-card-section v-if=".message">
                <q-avatar
                  v-if="confirmData.icon"
                  :icon="confirmData.icon"
                  color="grey-5"
                  text-color="black"
                />
                <span :class="{'pl-2': confirmData.icon}">{{confirmData.message}}</span>
        </q-card-section>-->

        <q-card-actions :align="$q.screen.gt.sm ? 'right' : 'center'">
          <q-btn
            @click="activateXmasTheme()"
            flat
            label="Yes, activate it."
            color="secondary"
            icon="fas fa-poll-h"
            v-close-popup
            :class="{
              'h-divide-bottom-dotted full-width': true,
            }"
          />
          <q-btn
            flat
            v-close-popup
            label="No, thank you."
            :class="{
              'full-width': true,
            }"
          />

          <!-- <q-btn flat label="Later" v-close-popup
            :class="{
              'h-divide-bottom-dotted full-width': $q.screen.lt.md
              }" />
            <q-btn
              @click="gotoFeedbackForm()"
              flat
              label="Proceed"
              color="secondary"
              v-close-popup
               :class="{
              'full-width': $q.screen.lt.md
              }"
          />-->
        </q-card-actions>
      </q-card>
    </div>
  </q-dialog>
</template>

<script>
import { AuthService } from '../services/auth.service';
import { mapActions, mapGetters } from 'vuex';
import { OPTION_MAP } from '../config/user_options';

export default {
  name: 'XmasDialog',
  data() {
    return {
      show: false,
      mode: {},
    };
  },
  mounted() {
    this.$events.$on('dialogs:xmas:open', (data) => {
      this.show = true;
      Object.assign(this, data);
    });
  },
  computed: {
    ...mapGetters({
      userSettings: 'ui/userSettings',
    }),
    user() {
      return this.$store.getters['ui/user'] || {};
    },
    user_settings() {
      return this.userSettings || {};
    },
  },
  methods: {
    ...mapActions({
      saveUserSettings: 'ui/saveUserSettings',
    }),
    onHide() {
      this.mode = {};
    },
    onShow() {
      this.shown = true;
    },
    activateXmasTheme(v) {
      this.saveUserSettings({ activeXmas: true });
    },
  },
};
</script>
