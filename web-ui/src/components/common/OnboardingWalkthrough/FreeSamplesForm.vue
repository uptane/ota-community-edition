<template>
  <q-form ref="sampleRequestForm">
    <div v-if="!sampleFormCompleted" class="row  pt-2">
      <div class="col-6 pl-1">
        <div class="mnw-20em">
          <q-select v-model="freeSampleModel.jobRoles" label="What are your roles?" filled class="" :options="jobRoles" multiple :rules="[(val) => (val && val.length > 0) || 'Please select your job roles']" />
        </div>
        <div class="mnw-20em">
          <q-select v-model="freeSampleModel.projectStage" label="What is the current stage of your project?" filled class="" :options="projectStages" :rules="[(val) => (val && val.length > 0) || 'Please select your project stage']" />
        </div>
        <div class="mnw-20em">
          <q-select v-model="freeSampleModel.expectedVolume" label="How many devices do you expect to manage?" filled class="" :options="deviceVolumes" :rules="[(val) => (val && val.length > 0) || 'Please select how many devices you expect to manage']" />
        </div>
      </div>
      <div class="col-12 pl-1">
        <div class="mnw-20em">
          <q-input v-model="freeSampleModel.projectDetail" label="Tell us about your project" filled class="" rows="3" type="textarea" :rules="[(val) => (val && val.length > 0) || 'Please tell us about your project']" />
        </div>
        <div class="mnw-20em">
          <q-input v-model="freeSampleModel.biggestChallenge" label="What is your biggest challenge?" filled class="" rows="3" type="textarea" :rules="[(val) => (val && val.length > 0) || 'Please tell us your biggest challenge in the project']" />
        </div>
      </div>
    </div>
    <div v-else class="">
      <div class="text-h4 text-center pt-4">Thank You</div>
      <div class="text-h1 text-center p-1">
        <q-icon color="positive" name="check_circle_outline"></q-icon>
      </div>
      <div class="text-center pt-0">
        <div class="text-1">
          We have received your request. We will get in touch with you.<br />
          If you have any questions about your order, please <a href="https://www.torizon.io/about-us#locations" target="_blank">contact us</a>.
        </div>
      </div>
    </div>
  </q-form>
</template>

<script>
export default {
  name: 'FreeSamplesForm',
  props: {
    sampleFormCompleted: {
      type: Boolean,
      default: false,
    },
    formModel: {
      type: Object,
      default: () => {
        return {
          jobRoles: [],
          projectStage: '',
          expectedVolume: '',
          projectDetail: '',
          biggestChallenge: '',
        };
      },
    },
  },
  data() {
    return {
      jobRoles: ['Hardware Development', 'Software Application Development', 'Software Operating System Level Developer', 'Procurement', 'Management', 'Student', 'Hobbyst/Maker', 'Others'],
      projectStages: ['Requirements gathering', 'Evaluation', 'In development', 'Pre-production', 'In production'],
      deviceVolumes: ['1 - 20', '21 - 100', '101 - 1000', '1001 - 5000', '5000+'],
    };
  },
  computed: {
    freeSampleModel: {
      get() {
        return this.formModel;
      },
      set(value) {
        this.$emit('update:formModel', value);
      },
    },
  },
  methods: {
    isValid() {
      return new Promise((resolve, reject) => {
        this.$refs.sampleRequestForm.validate().then((valid) => {
          if (valid) {
            resolve(this.freeSampleModel);
          } else {
            reject(false);
          }
        });
      });
    },
  },
};
</script>

<style></style>
