<template>
  <q-item class="q-item p-0 m-0 w-100">
    <q-item-label class=" description q-item-label">
      <div v-if="!noTitle" class="q-item-tile label">
        Description
        <feature-teaser class="inline-block" feature="update-device-info">
          <q-btn v-if="editable && !editMode" flat dense :color="color" label="Edit" icon="edit" size=".7rem" @click="setEditMode()" />
        </feature-teaser>
      </div>
      <div class="q-item-tile sublabel" v-if="loading">
        <small>
          <list-loader class="p-0 mt-0" size=".5rem" label="Saving changes..." />
        </small>
      </div>
      <div class="pt-1 opacity-100" v-else>
        <template v-if="editMode">
          <div class="row mb-3">
            <div class="col-12 q-item-tile sublabel">
              <q-input type="textarea" autogrow counter v-model="description" placeholder="Provide description for this device"></q-input>
            </div>
            <div class="col-12 mb-1" v-if="showPreview && editMode">
              <h6 class="m-0 p-0 mb-1">Description Preview:</h6>
              <markdown class="opacity-50" v-if="description" :content="description" />
              <div v-else class="opacity-40">Nothing here yet</div>
              <q-separator class="mt-2 mb-1" />
            </div>
            <div class="col-auto">
              <q-btn flat dense color="default" label="Cancel" size=".7rem" icon="close" @click="revert()" />
              <q-btn flat dense :color="color" label="Save description" size=".7rem" icon="save" @click="saveDeviceDescription" />
              <q-checkbox v-model="showPreview" color="secondary" size="sm" label="Show preview" />
            </div>
          </div>
        </template>
        <template v-else>
          <div class="row">
            <div class="col q-item-tile sublabel ">
              <div v-if="!description || description.length < 1" class="no-dep pr-1">None</div>
              <template v-else>
                <markdown :content="description" />
              </template>
            </div>
          </div>
        </template>
      </div>
    </q-item-label>
  </q-item>
</template>

<script>
import { mapActions } from 'vuex';
import Markdown from '../common/Markdown.vue';
import ListLoader from '../loaders/ListLoader.vue';
export default {
  components: { ListLoader, Markdown },
  name: 'DeviceComment',
  props: {
    device: {
      type: Object,
      default: () => ({}),
    },
    noTitle: {
      type: Boolean,
      default: false,
    },
    editable: {
      type: Boolean,
      default: false,
    },
    color: {
      type: String,
      default: 'primary',
    },
  },
  data() {
    return {
      loading: false,
      description: '',
      editMode: false,
      showPreview: false,
    };
  },
  computed: {
    deviceNotes() {
      return this.device.notes || '';
    },
  },
  methods: {
    ...mapActions({
      patchDevice: 'devices/patchDevice',
    }),
    saveDeviceDescription() {
      this.loading = true;
      this.patchDevice({ uuid: this.device.uuid, data: { notes: this.description } })
        .then((data) => {
          this.clearEditMode();
        })
        .catch((err) => {})
        .finally(() => {
          this.loading = false;
          this.showPreview = false;
        });
    },
    clearEditMode() {
      this.editMode = false;
    },
    setEditMode() {
      this.editMode = true;
      this.description = this.device.notes;
    },
    preserve() {
      // this.oldDescription = this.description;
    },
    revert() {
      this.description = this.device.notes;
      this.resetView();
    },
    resetView() {
      this.clearEditMode();
      this.showPreview = false;
    },
  },
  mounted() {
    this.description = this.device.notes;
  },
  watch: {
    deviceNotes() {
      this.description = this.device.notes;
    },
  },
};
</script>

<style></style>
