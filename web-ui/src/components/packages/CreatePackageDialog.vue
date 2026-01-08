<template>
  <q-dialog ref="modal" v-model="show" @ok="onOk" @cancel="onCancel" @show="onShow" @hide="onHide" no-backdrop-dismiss no-route-dismiss content-classes="p-3" :maximized="$q.screen.lt.md">
    <q-dialog v-model="showPreview">
      <q-card class="p-1 mnw-40em w-100 mxw-80em">
        <q-card-section>
          <div class="text-h6">Preview</div>
        </q-card-section>

        <q-card-section class="q-pt-none">
          <codemirror :value="packageFileStringTruncated" :options="cmOptions"></codemirror>
        </q-card-section>

        <q-card-actions align="right">
          <q-btn flat label="Close" v-close-popup />
        </q-card-actions>
      </q-card>
    </q-dialog>

    <q-card class="p-2 w-100 ">
      <div v-if="showSuccess || showError || showProgress">
        <h6 class="m-0 p-0 pl-2">
          <span slot="title">
            <span v-if="uploadingContent">Uploading Package Content...</span>
            <span v-if="creatingContent">Creating Package...</span>
            <span v-if="showSuccess" class="">Package Created!</span>
            <span class="text-negative" v-if="showError">
              Package Creation Failed!
            </span>
          </span>
          <loader color="primary" v-if="showHourglass"></loader>

          <close-btn class="absolute-top-right m-1 close-btn" @click.native="allDone" v-if="loadedData"></close-btn>
        </h6>
        <div class="text-left p-2 row flex-center" v-if="showSuccess">
          <div class="col">
            Your package has been created with the name
            <span class="text-blue-grey">{{ pkg.packageName }}</span> and version <span class="text-blue-grey">{{ pkg.version }}</span
            ><br />
            for
            <span v-for="(hw, i) in pkg.hardwareIds" :key="i + '_hw'">
              {{ i < pkg.hardwareIds.length - 1 ? (i > 0 ? ',' : '') : pkg.hardwareIds.length > 1 ? ' and ' : ' ' }} <span class="text-blue-grey">{{ hw }}</span>
            </span>
            <br />
          </div>
          <q-icon color="positive" name="check_circle" size="6rem" class="animated zoomIn"></q-icon>
        </div>
        <div class="pt-2" v-if="showError">
          <div class="pl-2">{{ error }}</div>
          <div class="pt-3 flex">
            <q-space />
            <q-btn v-if="isKeyError" color="primary" flat v-close-popup>&nbsp; OK</q-btn>
            <q-btn v-else color="primary" flat @click="retry" icon="refresh">&nbsp; Try again</q-btn>
          </div>
        </div>
        <div class="p-2" v-if="showProgress">
          <div class="mb-1">
            <div v-if="uploadingContent">
              <q-linear-progress v-if="!showError" size="25px" :stripe="progressData.progress < 100" :query="creatingContent" :color="progressColor" :value="progressData.progress / 100">
                <div class="absolute-full flex flex-center">
                  <q-badge color="white" :text-color="progressColor" :label="progressText" />
                </div>
              </q-linear-progress>
            </div>
            <div v-if="creatingContent">
              Package content uploaded. <br />
              <span class="opacity-30">Now verifying and creating the package...</span>
            </div>
            <span class="opacity-30"
              ><em> <q-icon size="1.5em" name="local_cafe" />This might take a minute, so this will be a good time to grab a cup of coffee. </em></span
            >
          </div>
          <div class="q-card no-shadow mt-1 p-1">
            <div class="opacity-50" v-if="showUploadDetail">
              <small class="text-small">Uploaded: {{ $format.humanStorageSize(progressData.uploaded) }} / {{ $format.humanStorageSize(progressData.size) }}</small>
              <br />
              <div v-if="progressData.progress < 100">
                <small class="text-small">
                  Upload speed:
                  <span v-if="isFinite(progressData.upSpeed)">{{ $format.humanStorageSize(progressData.upSpeed) }}/s</span>
                  <span v-if="!isFinite(progressData.upSpeed)">Calculating...</span>
                </small>
                <br />
                <small v-if="progressData.totalTime" class="text-small">
                  Total time taken:
                  <span v-if="progressData.totalTime.h">{{ progressData.totalTime.h }}h&nbsp;</span>
                  <span v-if="progressData.totalTime.m">{{ progressData.totalTime.m }}m&nbsp;</span>
                  <span v-if="progressData.totalTime.s">{{ progressData.totalTime.s }}s&nbsp;</span>
                </small>
                <br />
                <small v-if="progressData.timeRemaining" class="text-small">
                  Time ramaining:
                  <span v-if="progressData.timeRemaining.h">{{ progressData.timeRemaining.h }}h&nbsp;</span>
                  <span v-if="progressData.timeRemaining.m">{{ progressData.timeRemaining.m }}m&nbsp;</span>
                  <span v-if="progressData.timeRemaining.s">{{ progressData.timeRemaining.s }}s&nbsp;</span>
                </small>
              </div>
              <br />
            </div>
            <q-btn @click="showUploadDetail = !showUploadDetail" flat no-caps small :icon="showUploadDetail ? 'keyboard_arrow_up' : 'keyboard_arrow_down'" class="clickable text-primary small">
              <small>&nbsp; {{ showUploadDetail ? 'Show less' : 'Show more' }}</small>
            </q-btn>
          </div>
        </div>
      </div>

      <close-btn v-if="!loadingData" class="absolute-top-right m-1" v-close-popup></close-btn>

      <div v-if="showSteps">
        <h5 class="m-0 p-0" v-if="!loadingData">
          <span slot="title">
            <div v-if="!newVersionOnly" class="text-center">{{ title }}</div>
            <q-icon name="left"></q-icon>
          </span>
        </h5>
        <q-stepper v-model="step" ref="stepper" contracted flat active-color="primary" done-color="primary" animated :bordered="false" @input="stepChanged">
          <q-step :name="1" title="Select package type" icon="settings" :done="step > 1">
            <div class="row justify-center">
              <div class="pt-0 pb-2  text-center w-30vw">
                <h6 class="m-0 mb-1">Select package type</h6>
                <p>
                  <q-btn no-caps outline color="primary" @click="setUploadType('Docker Compose')" class="full-width">
                    <package-icon :packageInfo="dockerComposePkgTemplate" class="mr-1" />
                    Docker Compose
                  </q-btn>
                </p>
                <p>
                  <q-btn no-caps outline color="primary" @click="setUploadType('TorizonCore')" class="full-width">
                    <package-icon :packageInfo="customOSPkgTemplate" class="mr-1" />
                    Custom OS Image
                  </q-btn>
                </p>
                <p>
                  <q-btn no-caps outline color="primary" @click="setUploadType('Other')" class="full-width">
                    <package-icon :packageInfo="otherPkgTemplate" class="mr-1" />
                    Other
                  </q-btn>
                </p>
              </div>
            </div>
          </q-step>
          <template v-if="uploadType === 'TorizonCore'">
            <q-step :name="2" title="Upload OS Image" icon="settings" :done="step > 2">
              <div class="text-center"><package-icon :packageInfo="customOSPkgTemplate" class="mr-1" /> Custom OS Image</div>
              <div class="pt-2 pb-2">
                <div class=" text-center ellipsis">
                  <p>
                    To upload a custom OS package, you must use
                    <q-btn padding="xs" no-caps flat color="primary" type="a" target="_blank" href="https://developer.toradex.com/knowledge-base/torizoncore-builder-tool">
                      TorizonCore Builder
                    </q-btn>
                  </p>

                  <p class="mb-0 pb-0">
                    <q-btn padding="xs" no-caps flat color="primary" type="a" target="_blank" href="https://developer.toradex.com/knowledge-base/signing-and-pushing-torizoncore-images-to-torizon-ota">
                      <span class="ml-1"
                        >Click here to learn how to upload a <strong>TorizonCore package</strong>
                        <q-icon size="1.3em" right name="launch"></q-icon>
                      </span>
                    </q-btn>
                  </p>
                </div>
              </div>
            </q-step>
          </template>
          <template v-else>
            <template v-if="uploadType === 'Other'">
              <q-step :name="1.5" title="Select Components" icon="settings" :done="step > 1.5">
                <ecus-selector :lockbox="false" :package-upload="true" v-model="selectedEcus" :allow-component-creation="true"></ecus-selector>
              </q-step>
            </template>
            <q-step :name="2" title="Upload package" icon="settings" :done="step > 2">
              <div class="pt-2 pb-2">
                <div class="pb-1 opacity-70 text-center" v-if="packageFilename">
                  <q-chip no-caps removable :color="$q.dark.isActive ? 'blue-grey' : 'grey-3'" icon="icon-torizon-package" @remove="packageFilename = ''">
                    &nbsp;
                    <span
                      class="ellipsis"
                      :class="{
                        'mxw-10em ': packageFileHash,
                        'mxw-30em ': !packageFileHash,
                      }"
                      >{{ packageFilename }}</span
                    >
                    &nbsp; &nbsp;
                    <template v-if="packageFileHash">
                      | &nbsp; &nbsp;<small class="ellipsis">MD5 Hash: {{ packageFileHash }}</small>
                    </template>
                  </q-chip>
                  <q-btn v-if="packageFileStringTruncated" flat color="primary" no-caps @click="showPreview = true">Preview</q-btn>
                </div>
                <div class=" text-center ellipsis" v-if="!packageFilename">
                  <template v-if="readingFile">
                    <p
                      class="m-1 p-1"
                      :class="{
                        'bg-grey-2': !$q.dark.isActive,
                        'bg-blue-grey': $q.dark.isActive,
                      }"
                    >
                      <q-spinner-hourglass :color="$q.dark.isActive ? 'grey-1' : 'primary'" size="1.5em"></q-spinner-hourglass>
                      Reading file...
                    </p>
                  </template>
                  <template v-else>
                    <div class="text-negative" v-if="fileError">
                      {{ fileError.message }}
                    </div>
                    <q-file ref="pkgFile" :accept="acceptedFileTypes" @input="readFile($event)" class="p-0" type="file" size="xl" outlined color="primary" label-color="primary" input-class="text-primary pb-1 h-5em" :prefix="fileUploadPrompt" placeholder="Select file or drag it here" autofocus>
                      <template v-slot:prepend>
                        <div class="mt-2 mb-2 text-primary" @click="() => $refs.pkgFile.pickFiles()">
                          <q-icon left color="primary" size="2em" name="post_add" class="pt-2" />
                        </div>
                      </template>
                    </q-file>
                    <p>
                      <small class="text-blue-grey">{{ acceptedFileHint }} </small>
                    </p>
                  </template>
                  <p class="mb-0 pb-0">
                    Learn how to create a Docker compose file:
                  </p>
                  <div class="">
                    <q-btn dense no-caps flat color="primary" type="a" target="_blank" href="https://developer.toradex.com/knowledge-base/visual-studio-code-extension-for-torizon#Generate_a_Docker_Compose_File_to_Deploy_the_Application_With_Torizon_OTA">
                      <span class="ml-1"
                        >Visual Studio Code Extension for Torizon
                        <q-icon size="1.3em" right name="launch"></q-icon>
                      </span>
                    </q-btn>
                  </div>
                  <div class="">
                    <q-btn no-caps dense flat color="primary" type="a" target="_blank" href="https://developer.toradex.com/knowledge-base/multi-containers-torizon-core">
                      <span class="ml-1"
                        >Using Multiple Containers With TorizonCore
                        <q-icon size="1.3em" right name="launch"></q-icon>
                      </span>
                    </q-btn>
                  </div>
                </div>
              </div>
            </q-step>
            <q-step :name="3" title="Create an ad group" caption="Optional" icon="create_new_folder" :done="step > 3">
              <p class="text-center">Add package metadata.</p>
              <div class="row">
                <div class="col-sm-12 col-md-6 p-1 pt-0">
                  <q-input @blur="$v.pkg.packageName.$touch" :error="$v.pkg.packageName.$error" @keyup.enter="onOk" outlined label="Package name" v-model="pkg.packageName" v-if="!newVersionOnly" />
                </div>
                <div class="col-sm-12 col-md-6 p-1">
                  <q-input outlined label="Version" @blur="$v.pkg.version.$touch" @keyup.enter="onOk" :error="$v.pkg.version.$error" v-model="pkg.version" />
                </div>
              </div>
            </q-step>

            <q-step :name="4" title="Create an ad" icon="add_comment">
              <h4 class="text-center m-1"><q-icon color="positive" size="5rem" name="check_circle" /> All done!</h4>
            </q-step>
          </template>
          <template v-slot:navigation>
            <q-stepper-navigation>
              <div class="flex">
                <q-btn v-if="step > 1" flat color="default" @click="previousAction" label="Back" class="q-ml-sm" />
                <q-space />
                <q-btn @click="nextAction" v-if="packageFilename || (step === 1.5 && pkg.hardwareIds.length)" flat :icon="nextIcon.left" :icon-right="nextIcon.right" color="primary" :label="nextLabel" />
              </div>
            </q-stepper-navigation>
          </template>
        </q-stepper>
      </div>
    </q-card>
  </q-dialog>
</template>

<script>
import { mapActions, mapGetters } from 'vuex';
import Loader from '../loaders/Loader';
import { required, minLength } from 'vuelidate/lib/validators';
import YAML from 'yamljs';
import { isText, isBinary, getEncoding } from 'istextorbinary/edition-es2019/index';
import MD5 from 'crypto-js/md5';
import PackageIcon from './PackageIcon.vue';
import CloseBtn from 'src/components/common/CloseBtn.vue';
import EcusSelector from 'src/components/common/EcusSelector.vue';
import BetaBadge from '../common/BetaBadge.vue';

export default {
  name: 'CreatePackageDialog',
  components: {
    Loader,
    PackageIcon,
    CloseBtn,
    EcusSelector,
    BetaBadge,
  },
  props: {},
  data() {
    return {
      existing: {},
      pkg: {
        hardwareIds: [],
        fleetIds: [],
        tag: 'misc-package',
      },
      packageTags: ['misc-package', 'app-package', 'my-package'],
      showUploadDetail: false,
      packageFilename: '',
      packageFileHash: '',
      show: false,
      loadingHarware: false,
      progressBuffer: 10,
      loadingData: false,
      loadedData: false,
      isTextFile: false,
      packageFile: null,
      packageFileObject: null,
      step: 1,
      showPreview: false,
      error: null,
      isKeyError: false,
      fileError: null,
      uploadType: '',
      readingFile: false,
    };
  },
  validations: {
    pkg: {
      packageName: { required },
      hardwareList: { required },
      version: { required },
      packageFilename: { required },
    },
  },
  methods: {
    ...mapActions({
      createPackage: 'packages/createPackage',
      fetchPackages: 'packages/fetchPackages',
      fetchHardwareIds: 'hardware/fetchHardwareIds',
    }),
    nextAction() {
      if (this.step === 4) {
        this.allDone();
      } else if (this.step === 3) {
        this.onOk();
      } else {
        this.$refs.stepper.next();
      }
    },
    previousAction() {
      if (this.step === 2 && this.uploadType === 'Other') {
        this.step = 1.5;
      } else {
        this.$refs.stepper.previous();
      }
    },
    retry() {
      this.loadingData = false;
      this.error = false;
    },
    onOk() {
      const errNotice = { message: '', color: 'negative' };
      this.pkg = this.pkg || {};
      this.pkg.packageName = ((this.pkg || {}).packageName || '').trim();
      this.pkg.version = ((this.pkg || {}).version || '').trim();
      if (this.pkg.packageName.indexOf(' ') !== -1) {
        this.$v.pkg.packageName.$touch();
        return this.$q.notify({
          ...errNotice,
          message: 'Package name must not have white space',
        });
      }
      if (!this.pkg.packageName) {
        this.$v.pkg.packageName.$touch();
        return this.$q.notify({
          ...errNotice,
          message: 'Package name is required',
        });
      }
      if (!this.pkg.version) {
        this.$v.pkg.version.$touch();
        return this.$q.notify({
          ...errNotice,
          message: 'Package version is required',
        });
      }
      if (this.pkg.version.indexOf(' ') !== -1) {
        this.$v.pkg.version.$touch();
        return this.$q.notify({
          ...errNotice,
          message: 'Package version must not have white space',
        });
      }
      if (!this.pkg.hardwareIds || this.pkg.hardwareIds.length < 1) {
        return this.$q.notify({
          ...errNotice,
          message: 'You must select at least one component type from the list',
        });
      }
      const formData = new FormData();
      const file = new Blob([this.packageFile], {
        type: 'application/octet-stream',
        lastModifiedDate: new Date().toString(),
        name: this.pkg.packageName,
      });
      formData.append('file', file);

      let promise;
      this.loadingData = true;
      const processError = (err) => {
        if (err && err.response && err.response.data && err.response.data.code === 'role_key_not_found') {
          this.isKeyError = true;
          this.error = 'There are no signing keys available online to complete this operation. If you have taken your signing keys offline, this operation is no longer possible through the web UI. Please use offline signing tools (TorizonCore Builder and/or uptane-sign) instead.';
        } else {
          this.isKeyError = false;
          this.error = 'We ran into a problem while trying to process the package. This is possibly a version conflict. Please check and make sure this is a new version of the package.';
        }
        this.loadingData = false;
      };
      const processSuccess = (resp) => {
        this.loadedData = true;
        this.loadingData = false;
        let message = `Package "${this.pkg.packageName}", has been created`;
        this.$q.notify({ message, color: 'positive' });
      };
      promise = this.createPackage({
        data: this.pkg,
        formData,
        hardwareIds: this.pkg.hardwareIds,
      });
      promise
        .then((data) => {
          processSuccess(data);
        })
        .catch((err) => {
          console.log('Error', err);
          processError(err);
        });
    },
    onCancel() {},
    onShow() {
      this.pkg.hardwareIds = [];
    },
    onHide() {
      this.allDone();
    },

    fetchHardwareList() {
      this.loadingHarware = true;
      this.fetchHardwareIds()
        .then((list) => {
          this.loadingHarware = false;
        })
        .catch((err) => {
          this.loadingHarware = false;
        });
    },
    readFile(ev) {
      this.readingFile = true;
      setTimeout(() => {
        const file = ev;
        var reader = new FileReader();
        reader.onload = (event) => {
          this.packageFile = event.target.result;
          try {
            this.packageFileHash = MD5(this.packageFile);
          } catch (error) {}
          this.fileError = '';
          let fileLimit = {
            size: 500,
            message: 'File must be less than 500KB',
          };
          if (this.uploadType === 'Other') {
            fileLimit = {
              message: 'File must be less than 100MB',
              size: 100000,
            };
          }
          if (file.size / 1024 >= fileLimit.size) {
            this.fileError = { message: fileLimit.message + ', current size is: ' + this.$format.humanStorageSize(file.size) };
            this.packageFile = null;
            return;
          }
          try {
            const date = Date.now();
            if (this.uploadType === 'Docker Compose') {
              this.packageFileObject = YAML.parse(this.packageFile);
            }
            this.packageFilename = file.name;
            this.pkg.packageName = this.pkg.packageName || file.name;
            this.pkg.version = this.$date.formatDate(date, 'DD.MM.YY-hmmss');
          } catch (err) {
            console.log('ERR', err);
            this.fileError = { message: 'Malformed YAML file content' };
            this.packageFile = null;
          }
          this.readingFile = false;
        };
        reader.onerror = (err) => {
          this.fileError = {
            message: 'Invalid file',
            error: err,
          };
          this.readingFile = false;
        };
        this.isTextFile = isText(file.name, file.stream());
        if (this.uploadType === 'Docker Compose') {
          reader.readAsBinaryString(file);
        } else {
          reader.readAsArrayBuffer(file);
        }
      }, 500);
    },
    allDone() {
      this.show = false;
      this.step = 1;
      this.existing = {};
      this.packageFilename = '';
      this.show = false;
      this.loadingHarware = false;
      this.progressBuffer = 10;
      this.loadingData = false;
      this.loadedData = false;
      this.error = null;
      this.fileError = null;
      this.readingFile = false;

      this.pkg = {
        hardwareIds: [],
        fleetIds: [],
      };

      this.$v.pkg.packageName.$reset();
      this.$v.pkg.version.$reset();
    },
    setUploadType(type) {
      if (type === 'Other') {
        this.$set(this.pkg, 'hardwareIds', []);
        this.step = 1.5;
      } else if (type === 'Docker Compose') {
        this.$set(this.pkg, 'hardwareIds', ['docker-compose']);
        this.step = 2;
      } else {
        this.step = 2;
      }
      this.uploadType = type;
    },
    stepChanged() {
      if (this.step < 2) {
        this.uploadType = '';
        this.packageFilename = null;
      }
    },
  },
  mounted() {
    this.fetchHardwareList();
    this.$events.$on('dialogs:create-package:open', (data) => {
      Object.assign(this, data);
    });
  },
  computed: {
    ...mapGetters({
      isCommercialUser: 'users/isCommercialUser',
    }),
    customOSPkgTemplate() {
      return {
        isCustom: false,
        isOSPackage: true,
      };
    },
    dockerComposePkgTemplate() {
      return {
        isCustom: true,
        isApplicationPackage: true,
      };
    },
    otherPkgTemplate() {
      return {
        isCustom: true,
        isApplicationPackage: true,
      };
    },
    nextLabel() {
      let label = 'Continue';
      if (this.step === 3) {
        label = 'Upload';
      }
      if (this.step === 4) {
        label = 'Finish';
      }
      return label;
    },
    nextIcon() {
      let label = {
        left: '',
        right: 'keyboard_arrow_right',
      };
      if (this.step === 3) {
        label.left = 'cloud_upload';
        label.right = '';
      }
      if (this.step === 4) {
        label = {
          left: '',
          right: 'check',
        };
      }
      return label;
    },
    title() {
      let name = this.uploadType;
      if (this.uploadType === 'Other') {
        name = '';
      }
      return `Upload ${name} Package`;
    },
    fileUploadPrompt() {
      let prompt = 'ATTACH DOCKER COMPOSE FILE ';
      if (this.uploadType === 'Other') {
        prompt = 'ATTACH PACKAGE FILE ';
      }
      return prompt;
    },
    acceptedFileHint() {
      let hint = '(Accepts valid .yaml or .yml files < 500KB)';
      if (this.uploadType === 'Other') {
        hint = '(Accepts files < 100MB)';
      }
      return hint;
    },
    acceptedFileTypes() {
      let types = '.yaml, .yml';
      if (this.uploadType === 'Other') {
        types = '*';
      }
      return types;
    },
    selectedEcus: {
      get() {
        return this.pkg.hardwareIds.map((id) => ({ hardwareId: id }));
      },
      set(value) {
        this.pkg.hardwareIds = value.map((item) => item.hardwareId);
      },
    },
    packageFileStringTruncated() {
      if (!this.packageFile || (this.uploadType !== 'Docker Compose' && !this.isTextFile)) {
        return false;
      }
      if (this.packageFile && typeof this.packageFile === 'string') {
        let max = 10000;
        return this.packageFile.length <= max ? this.packageFile : this.packageFile.substring(0, max) + '...';
      }
      return '';
    },
    showHourglass() {
      return this.loadingData;
    },
    uploadingContent() {
      return this.progressData.progress < 100;
    },
    creatingContent() {
      return this.progressData.progress >= 100 && !this.progressData.status;
    },
    showCloseButton() {
      return !this.loadingData;
    },
    showProgress() {
      return this.loadingData;
    },
    showError() {
      return this.progressData.status === 'error' && this.error && !this.loadingData;
    },
    showSuccess() {
      return !this.error && this.loadedData;
    },
    showSteps() {
      return !this.error && !this.loadedData && !this.loadingData;
    },
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
    hardwareList: {
      get() {
        return this.$store.getters['hardware/hardwareIds'] || [];
      },
    },
    progressData() {
      const data = (this.$store.getters['packages/packagesUploading'] || [])[0] || {};
      return data;
    },
    progressColor() {
      let color = 'primary';
      if (this.progressData.status === 'error') {
        color = 'negative';
      }
      return color;
    },
    progressText() {
      let text = 'Uploading package file --- ' + Math.round(this.progressData.progress) + '%';
      if (this.progressData.status === 'error') {
        text = 'Error uploading package file';
      } else if (this.progressData.status === 'success') {
        text = 'Package upload successful';
      }
      return text;
    },
    createGroupDialogModel: {
      get() {
        return this.show;
      },
      set(val) {},
    },
    selectedGroup: {
      get() {
        return this.$store.getters['devices/selectedGroup'];
      },
      set(val) {
        this.$store.commit('devices/setSelectedGroup', val);
      },
    },
    existingPackagesOption() {
      const optionsData = [];
      const data = this.$store.getters['packages/preparedPackages'] || {};
      Object.keys(data).forEach((key) => {
        const pkg = data[key];
        pkg.forEach((j) => {
          j.versions
            .filter((t, i) => {
              return j.versions.findIndex((a) => a.name === t.name) === i;
            })
            .forEach((f) => {
              optionsData.push({
                label: f.id.name,
                value: j,
              });
            });
        });
      });
      return optionsData;
    },
    newVersionOnly() {
      return !!(this.existing || {}).packageName;
    },
  },
  watch: {
    existing(n, o) {
      this.pkg.packageName = (n || {}).packageName;
    },
  },
};
</script>

<style>
.CodeMirror-cursor {
  display: none;
}
</style>
