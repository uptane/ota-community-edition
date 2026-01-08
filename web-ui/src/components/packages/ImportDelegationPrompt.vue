<template>
  <q-dialog ref="dialog" @hide="onDialogHide">
    <q-card class="q-dialog-plugin">
      <q-card-section>
        <div class="text-h6">
          {{ title }}
        </div>
      </q-card-section>
      <q-card-section>
        <q-form ref="urlPromptForm">
          <p>Enter source URL or <a class="text-primary cursor-pointer" @click="onManualClick">enter details manually</a></p>
          <p>
            <q-input
              v-model="url"
              filled
              type="url"
              autofocus
              placeholder="Package source data URL"
              hint="URL for a JSON formated Package source data"
              :rules="[
                (val) => !!val || 'URL is required',
                (val) => (val && val.length <= 200) || 'URL must be less than 200 characters',
                (val) => (val && val.length >= 10) || 'URL must be more than 10 characters',
                (val) => (val && val.startsWith('http')) || 'URL must start with http or https',
                (val) => /^https?:\/\/(?:www\.)?[-a-zA-Z0-9@:%._\+~#=]{1,256}\.[a-zA-Z0-9()]{1,6}\b(?:[-a-zA-Z0-9()@:%_\+.~#?&\/=]*)$/.test(val) || 'URL must be valid',
              ]"
              lazy-rules
            />
          </p>
        </q-form>
      </q-card-section>
      <q-card-actions align="right">
        <q-btn flat color="default" label="Cancel" @click="onCancelClick" />
        <q-btn color="primary" label="Continue" @click="onOKClick" />
      </q-card-actions>
    </q-card>
  </q-dialog>
</template>

<script>
export default {
  name: 'ImportDelegationPrompt',
  props: {
    title: {
      type: String,
      default: 'Add package source',
    },
    manualRequestLink: {
      type: Object,
      default: () => {
        return {
          action: () => {},
        };
      },
    },
  },
  data() {
    return {
      url: '',
    };
  },

  methods: {
    show() {
      this.$refs.dialog.show();
    },

    hide() {
      this.$refs.dialog.hide();
    },

    onDialogHide() {
      this.$emit('hide');
    },

    onOKClick() {
      this.$refs.urlPromptForm.validate().then((valid) => {
        if (valid) {
          this.$emit('ok', this.url);
          this.hide();
        }
      });
    },

    onCancelClick() {
      this.hide();
    },
    onManualClick() {
      this.$emit('manual');
      if (this.manualRequestLink.action) {
        this.manualRequestLink.action();
        this.hide();
      }
    },
  },
};
</script>
