<template>
  <div class="mt-2">
    <div @click="toggleAdvanced" class="cursor-pointer text-primary">
      <q-icon name="edit_note" color="default" size="1.2rem" /> Advanced
      <q-icon :name="showAdvancedEntry ? 'keyboard_arrow_up' : 'keyboard_arrow_down'" color="default" size="2rem" />
    </div>
    <div class="mt-1" v-if="showAdvancedEntry">
      <div class="mb-2">
        <p class="p-0 m-0 text-1  opacity-50">Alternate download URL (Optional)</p>
        <div class="row">
          <div class="col">
            <q-input ref="uriInput" outlined class="pb-1" :value="uri" @input="$emit('update:uri', $event)" placeholder="Enter a valid URL"> </q-input>
          </div>
        </div>
      </div>
      <div class="mb-2">
        <p class="p-0 m-0 text-1  opacity-50">Additional custom metadata (Optional)</p>
        <div class="row">
          <div class="col">
            <q-form ref="jsonInputForm">
              <q-input
                outlined
                class="pb-1"
                type="textarea"
                :value="userDefinedCustomText"
                @input="validateJsonInput"
                placeholder="Add custom metadata in JSON format"
                :rules="[(val) => !val.length || isValidJson() || 'Invalid JSON content']"
                hint='Hint: Must be a valid JSON content or string in quotes (e.g. {"a": 1}, "Sample string", etc)'
              >
              </q-input>
            </q-form>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'AdvancedUpdateEntry',
  props: {
    uri: {
      type: String,
      default: '',
    },
    userDefinedCustom: {
      type: String,
      default: null,
    },
  },
  data() {
    return {
      showAdvancedEntry: false,
      tempUserDefinedCustom: null,
    };
  },
  computed: {
    userDefinedCustomText() {
      return this.userDefinedCustom ? JSON.stringify(this.userDefinedCustom) : '';
    },
  },
  methods: {
    toggleAdvanced() {
      this.showAdvancedEntry = !this.showAdvancedEntry;
      if (this.showAdvancedEntry) {
        this.$nextTick(() => {
          this.$refs.uriInput.focus();
        });
      }
    },
    isValidJson() {
      try {
        JSON.parse(this.tempUserDefinedCustom);
      } catch (e) {
        return false;
      }
      return true;
    },
    validateJsonInput(value) {
      this.tempUserDefinedCustom = value;
      this.$refs.jsonInputForm.validate().then((valid) => {
        if (valid) {
          this.setUserDefinedCustom();
        }
      });
    },
    setUserDefinedCustom() {
      let parsed = null;
      try {
        parsed = JSON.parse(this.tempUserDefinedCustom);
      } catch (e) {
        parsed = null;
      }
      this.$emit('update:userDefinedCustom', parsed);
    },
  },
};
</script>

<style></style>
