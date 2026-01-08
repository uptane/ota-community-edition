<template>
  <div>
    <div class="row items-center" v-if="editMode">
      <q-input v-model="tempValue" :placeholder="value || placeholder" dense outlined class="col" v-if="type === 'text'" @keypress="onKeyPress">
        <template v-slot:prepend>
          <label class="col-auto text-1 pr-1 opacity-40" v-if="label">{{ label }}:</label>
        </template>
        <template v-slot:append>
          <q-btn round dense flat color="primary" icon="check" @click="onSave" />
          <q-btn round dense flat icon="close" @click="onCancel" />
        </template>
      </q-input>
      <q-select v-model="tempValue" v-else-if="type === 'select'" :emit-value="emitValue" :map-options="mapOptions" :display-value="(keyedOptions[tempValue] || {}).label || tempValue" dense outlined class="col" :options="options" @keypress="onKeyPress">
        <template v-slot:selected>
          <div class="ellipsis col">{{ (keyedOptions[tempValue] || {}).label || tempValue }}</div>
        </template>
        <template v-slot:prepend>
          <label class="col-auto text-1 pr-1 opacity-40" v-if="label">{{ label }}:</label>
        </template>
        <template v-slot:append>
          <q-btn round dense flat color="primary" icon="check" @click="onSave" />
          <q-btn round dense flat icon="close" @click="onCancel" />
        </template>
      </q-select>
    </div>
    <div class="row items-center" v-else>
      <label class="col-auto pr-1 opacity-40" v-if="label">{{ label }}:</label>
      <div class="col-auto pr-1 opacity-40" v-if="value == null || value == undefined">{{ emptyIndicator }}</div>
      <div class="col-auto pr-1" v-else>{{ (keyedOptions[value] || {}).label || value }}</div>
      <div class="col-auto" v-if="!readonly">
        <q-btn round dense flat color="primary" size=".8em" @click="activateEditMode" icon="edit_note" />
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'InlineEdit',
  props: {
    value: {
      required: true,
    },
    label: {
      type: String,
      required: true,
    },
    type: {
      type: String,
      default: 'text',
    },
    options: {
      type: Array,
      default: () => [],
    },
    placeholder: {
      type: String,
      default: '',
    },
    emptyIndicator: {
      type: String,
      default: 'Not set',
    },
    readonly: {
      type: Boolean,
      default: false,
    },
    emitValue: {
      type: Boolean,
      default: true,
    },
    mapOptions: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      editMode: false,
      tempValue: null,
    };
  },
  methods: {
    activateEditMode() {
      this.tempValue = this.value;
      this.editMode = true;
    },
    onSave() {
      this.$emit('input', this.tempValue);
      this.$emit('save', this.tempValue);
      this.editMode = false;
    },
    onCancel() {
      this.tempValue = this.value;
      this.editMode = false;
    },
    onKeyPress(e) {
      if (e.keyCode === 13) {
        this.onSave();
      }
    },
  },
  computed: {
    keyedOptions() {
      return _.keyBy(this.options, 'value');
    },
  },
  watch: {
    value(newValue) {
      this.tempValue = newValue;
      this.editMode = false;
    },
    tempValue(newValue) {
      this.$emit('input', this.value);
    },
  },
};
</script>

<style></style>
