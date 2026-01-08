<template>
  <q-dialog content-class="shadow-5" :value="value" @input="onChange" @show="onShow" @hide="onHide">
    <q-card class="lighter-card p-1 w-100 mxw-60em">
      <div class="row flex-center mb-2 pl-1 pr-1">
        <div class="col text-h6 ellipsis">{{ (data || {}).name }} (version {{ ((data || {}).id || {}).version }})</div>
        <div class="col-auto text-center">
          <q-btn v-close-popup flat icon="close"></q-btn>
        </div>
      </div>
      <div class="flex flex-center" v-if="loading">
        <div class="row  p-4">
          <div class="col-auto pr-1">
            <loader></loader>
          </div>
          <div class="col pr-1">Fetching package contents</div>
        </div>
      </div>
      <div v-else>
        <div v-if="error">
          <q-banner class="bg-negative text-white">{{ error }}</q-banner>
        </div>
        <div v-else>
          <codemirror :value="packageContent" :options="cmOptions"></codemirror>
        </div>
      </div>
      <div class="text-center">
        <q-btn v-close-popup flat color="primary"> Close</q-btn>
      </div>
    </q-card>
  </q-dialog>
</template>

<script>
import { mapActions } from 'vuex';
import Loader from '../loaders/Loader.vue';
export default {
  components: { Loader },
  name: 'PackageContentViewer',
  props: {
    value: {
      type: Boolean,
      default: false,
    },
    data: {
      type: Object,
      default: () => {},
    },
  },
  data() {
    return {
      loading: false,
      error: null,
      packageContent: null,
    };
  },
  methods: {
    ...mapActions({
      getPackageContent: 'packages/getPackageContent',
    }),
    onShow() {
      this.loading = true;
      this.getPackageContent(this.data)
        .then((a) => {
          this.packageContent = a;
          this.loading = false;
        })
        .catch((e) => {
          this.loading = false;
          console.log('Package content error', e);
          this.error = 'Unable to fetch package content';
        });
    },
    onHide() {
      this.loading = false;
      this.error = null;
      this.packageContent = null;
    },
    onChange(v) {
      this.$emit('input', v);
    },
  },
  computed: {
    cmOptions() {
      return {
        // codemirror options
        // mode: {
        //   filename: this.packageFilename || 'test.yaml'
        // },
        mode: 'text/yaml',
        theme: this.$q.dark.isActive ? 'base16-dark' : 'base16-light',
        inputStyle: 'contenteditable',
        readOnly: true,
        lineWrapping: true,
        fixedGutter: false,
        lineNumbers: true,
        line: true,
      };
    },
  },
};
</script>
