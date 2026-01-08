<template>
  <div v-if="userEditable || description" class=" description">
    <div v-if="!noTitle" class="q-item-tile label">
      Description
      <feature-teaser class="inline-block" feature="modify-package">
        <q-btn v-if="userEditable && !editMode" flat dense color="primary" label="Edit" icon="edit" size=".7rem" @click="startEditMode" />
      </feature-teaser>
    </div>
    <div class="q-item-tile sublabel" v-if="loading">
      <small>
        <list-loader class="p-0 mt-0" size=".5rem" />
      </small>
    </div>
    <div class="pt-1 opacity-100" v-else>
      <template v-if="editMode">
        <div class="row mb-3">
          <div class="col-12 q-item-tile sublabel">
            <q-input ref="descriptionInput" type="textarea" autogrow counter v-model="description" placeholder="Provide description for this  package"></q-input>
          </div>
          <div class="col-auto">
            <q-btn
              flat
              dense
              color="default"
              label="Cancel"
              size=".7rem"
              icon="close"
              @click="
                revert();
                editMode = false;
              "
            />
            <q-btn flat dense color="primary" label="Save description" size=".7rem" icon="save" @click="savePkgDescription" />
          </div>
        </div>
      </template>
      <template v-else>
        <div class="row">
          <div class="col q-item-tile sublabel ">
            <div v-if="!description || description.length < 1" class="no-dep pr-1">None</div>
            <template v-else>
              <markdown :content="description" :max-length="5120" warning-message="The provided description was too long, and the rest could not be rendered."></markdown>
            </template>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<script>
import { mapActions } from 'vuex';
import Markdown from '../common/Markdown.vue';
import ListLoader from '../loaders/ListLoader.vue';
export default {
  components: { ListLoader, Markdown },
  name: 'PacakgeDescription',
  props: {
    packageId: {
      type: String,
      default: '',
    },
    noTitle: {
      type: Boolean,
      default: false,
    },
    editable: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      loading: false,
      _description: '',
      oldDescription: null,
      editMode: false,
    };
  },
  computed: {
    description: {
      get() {
        return this.$data._description;
      },
      set(value) {
        this.$data._description = value;
      },
    },

    packageData() {
      return (this.$store.getters['packages/packagesById'] || {})[this.packageId];
    },

    packageCustomDescription() {
      // Return the description from the custom data field of the package
      return ((this.packageData || {}).custom || {})["tdx-description"];
    },

    userEditable() {
      // It is user-editable if there is no description in the custom data field and the component is set as editable
      return !this.packageCustomDescription && this.editable;
    },

    inputRef() {
      return this.$refs && this.$refs.descriptionInput;
    },
  },
  methods: {
    ...mapActions({
      //   getDescription: 'packages/getAllDescriptions',
      getDescription: 'packages/getDescription',
      saveDescription: 'packages/saveDescription',
    }),
    savePkgDescription() {
      this.loading = true;
      this.saveDescription({ packageId: this.packageId, description: this.description })
        .then((data) => {
          this.editMode = false;
        })
        .catch((err) => {})
        .finally(() => {
          this.loading = false;
        });
    },
    preserve() {
      this.oldDescription = this.description;
    },
    revert() {
      this.description = this.oldDescription;
    },

    startEditMode() {
      this.preserve();
      this.editMode = true;
      this.$nextTick(() => {
        this.inputRef && this.inputRef.focus();
      });
    },
  },
  mounted() {
    // If there is no description already, get the description from the server
    if (!this.packageCustomDescription) {
      this.loading = true;
      this.getDescription(this.packageId)
        .then((data) => {
          this.description = data.comment;
        })
        .catch((err) => {})
        .finally(() => {
          this.loading = false;
        });
    } else {
      this.description = this.packageCustomDescription;
    }
  },
};
</script>

<style></style>
