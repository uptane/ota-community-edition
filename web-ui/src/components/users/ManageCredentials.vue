<template>
  <div class=" mt-0 mb-2 ">
    <h4 class="m-0">Credentials</h4>
    <p class="mt-1">Click the button below to download credentials file.</p>
    <div class="row q-item-tile label">
      <div class="col-auto text-bold">
        <q-btn :loading="loadingCred" :percentage="credPercent" color="primary" @click="downloadCred">
          Download credentials
          <template v-slot:loading>
            <q-spinner-gears class="on-left" />
            Processing...
          </template>
        </q-btn>
      </div>
    </div>
    <p class="mt-1">Click the button below to revoke and regenerate your credentials.</p>
    <div class="row q-item-tile label">
      <div class="col-auto text-bold">
        <q-btn :loading="rotatingCred" :percentage="credPercent" color="negative" @click="rotateCredentials">
          Rotate credentials
          <template v-slot:loading>
            <q-spinner-gears class="on-left" />
            Processing...
          </template>
        </q-btn>
      </div>
    </div>
  </div>
</template>

<script>
import { exportFile } from 'quasar';
import { mapActions } from 'vuex';
import gtm from '../../services/gtm.service';
import { QSpinnerGears } from 'quasar';

export default {
  name: 'ManageCredentials',
  data() {
    return {
      rotatingCred: false,
      loadingCred: false,
      enable: true,
      credPercent: 0,
    };
  },
  methods: {
    ...mapActions({
      dowloadCredentials: 'users/dowloadCredentials',
      rotateUserCredentials: 'users/rotateCredentials',
    }),
    loadCred() {
      setTimeout(() => {
        if (this.credPercent < 100) {
          this.credPercent += 5;
          this.loadCred();
        }
      }, 100);
    },
    downloadCred() {
      this.loadingCred = true;
      this.enable = false;
      this.loadCred();
      this.dowloadCredentials()
        .catch((e) => {
          if (e.response && e.response.status && e.response.status === 403) {
            this.$events.$emit(`dialogs:premium:open`, {});
          }
        })
        .finally((err) => {
          this.loadingCred = false;
          this.credPercent = 0;
          this.enable = true;
          gtm.logEvent('Account', 'click', 'Download Credential', null);
        });
      return false;
    },
    rotateCredentials() {
      // Steps:
      // prompt user to confirm
      // if confirmed, call rotateUserCredentials
      // While waiting for response, show loading indicator
      // if successful, prompt user to download new credentials
      // if failed and the error is due to user repo retote failure, inform user that they might have taken their signing key offline and show them a link to the docs

      this.$q
        .dialog({
          title: 'Revoke Existing Credentials?',
          message:
            'If you are concerned that your credentials.zip file may have been exposed, you can revoke it and generate new credentials. This will mean any process that is still using the old credentials.zip will no longer have access. For example, all C.I. pipelines, monitoring systems, and auto-provisioning device images will stop working until you provide them with new credentials.',
          cancel: {
            label: 'Cancel',
            color: 'primary',
            flat: true,
          },
          ok: {
            label: 'Revoke and Regenerate',
            color: 'negative',
            flat: false,
          },
          persistent: true,
        })
        .onOk(() => {
          this.rotatingCred = true;
          this.enable = false;
          this.loadCred();
          const loaderDialog = this.$q.dialog({
            title: 'Rotating Credentials',
            message: 'This may take a few moments. Please do not close this window.',
            cancel: false,
            ok: false,
            persistent: true,
            progress: {
              spinner: QSpinnerGears,
              color: 'primary',
            },
          });
          this.rotateUserCredentials()
            .then(() => {
              this.$q
                .dialog({
                  title: 'Credentials Rotated',
                  message: 'Your credentials have been rotated successfully. You can now download your new credentials file.',
                  cancel: {
                    label: 'Close',
                    color: 'primary',
                    flat: true,
                  },
                  ok: {
                    label: 'Download Credentials',
                    color: 'primary',
                    flat: false,
                  },
                  persistent: true,
                })
                .onOk(() => {
                  this.downloadCred();
                });
            })
            .catch((e) => {
              if (e.response && e.response.status && e.response.status === 403) {
                this.$events.$emit(`dialogs:premium:open`, {});
              } else {
                this.$q.dialog({
                  title: 'Partial Success',
                  html: true,
                  message:
                    'Some of the credentials in credentials.zip were rotated, but not all. We were unable to rotate your software signing credentials, because you have already <a href="https://developer.toradex.com/torizon/torizon-platform/torizon-updates/offline-signing-keys/" target="_blank">taken your signing key offline</a>. If you still want to rotate this key, you will need to do it using the command-line tools.<br/><br/>Credentials rotated successfully:<ul><li>TorizonCore Builder API token</li><li>API token for production provisioning</li></ul>',
                  cancel: false,
                  ok: {
                    label: 'Close',
                    color: 'primary',
                    flat: true,
                  },
                  persistent: true,
                });
              }
            })
            .finally((err) => {
              this.rotatingCred = false;
              this.credPercent = 0;
              this.enable = true;
              loaderDialog.hide();
              gtm.logEvent('Account', 'click', 'Rotate Credential', null);
            });
        })
        .onCancel(() => {
          // do nothing
        })
        .onDismiss(() => {
          // do nothing
        });

      // this.rotatingCred = true;
      // this.enable = false;
      // this.loadCred();
      // this.rotateUserCredentials()
      //   .catch(e => {
      //     if (e.response && e.response.status && e.response.status === 403) {
      //       this.$events.$emit(`dialogs:premium:open`, {})
      //     }
      //   })
      //   .finally(err => {
      //     this.rotatingCred = false;
      //     this.credPercent = 0;
      //     this.enable = true;
      //     gtm.logEvent('Account', 'click', 'Rotate Credential', null);
      //   });
      // return false;
    },
  },
};
</script>
