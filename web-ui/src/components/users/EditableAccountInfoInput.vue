<template>
  <div class="h-divide-bottom-dotted0">
    <div class="row q-item-tile label pt-1 items-center" v-if="!updating">
      <div class="col-3 mxw-10em pr-1">{{ label }}</div>
      <div class="col-9">
        {{ value }}
        <q-btn dense flat @click="beginEdit()" v-if="allowEdit">
          <q-icon size="1rem" class="opacity-30" name="edit" />
        </q-btn>
      </div>
    </div>
    <div class="row q-item-tile label pt-1" v-if="updating">
      <div class="col-12 pr-1 opacity-50">{{ editLabel }}</div>
      <div class="col-12">
        <q-select v-if="type == 'list'" autofocus dense square :options="filteredOptions" v-model="valueUpdate" @filter="filterFn" use-input />
        <q-input v-else autofocus dense square :placeholder="value ? 'Currently: ' + value : placeholder" v-model="valueUpdate" />
      </div>
      <q-btn dense flat @click="saveChanges" color="secondary">Save</q-btn>
      <q-btn
        dense
        flat
        @click="
          valueUpdate = value;
          updating = false;
        "
        >cancel</q-btn
      >
    </div>
  </div>
</template>

<script>
import { AuthService } from '../../services/auth.service';

export default {
  name: 'EditableAccountInfoInput',
  props: {
    label: {
      type: String,
      default: '',
    },
    editLabel: {
      type: String,
      default: '',
    },
    placeholder: {
      type: String,
      default: '',
    },
    field: {
      type: String,
      default: '',
    },
    editField: {
      type: String,
      default: '',
    },
    type: {
      type: String,
      default: '',
    },
    options: {
      type: Array,
      default: () => [],
    },
    allowEdit: {
      type: Boolean,
      default: true,
    },
  },
  data() {
    return {
      updating: false,
      valueUpdate: '',
      filteredOptions: [],
    };
  },
  mounted() {
    this.valueUpdate = this.value;
    this.filteredOptions = this.options;
  },
  computed: {
    value: {
      get() {
        return this.user[this.field];
      },
      set(val) {
        this.user[this.field] = val;
      },
    },

    user() {
      return this.$store.getters['ui/user'] || {};
    },
  },
  methods: {
    saveChanges() {
      this.$emit('save-changes', this.update);
      AuthService.updateUserData({ [this.editField || this.field]: this.update || this.value })
        .then((saved) => {
          this.updating = false;
          // this.nameUpdate = this.user.name;
        })
        .catch((e) => {
          this.updating = false;
        });
    },
    beginEdit() {
      this.updating = true;
    },
    filterFn(val, update) {
      if (val === '') {
        update(() => {
          this.filteredOptions = this.options;
        });
        return;
      } else {
        update(() => {
          const filtered = this.options.filter((a) => a.label.match(new RegExp(val, 'i')) || a.value.match(new RegExp(val, 'i')));
          this.filteredOptions = filtered;
        });
      }
    },
  },
  watch: {
    updated(n, o) {
      if (n) {
        this.updating = false;
        this.valueUpdate = value;
      }
    },
    value(n, o) {
      this.valueUpdate = n;
    },
    valueUpdate(n, o) {
      if (n) {
        this.update = this.type == 'list' ? n.value : n;
      }
    },
  },
};
</script>
