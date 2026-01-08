<template>
  <q-card
    class="mxh-75vh mnw-30em overflow-y-auto p-1 overflow-x-hidden"
    style="overflow-x: hidden;"
    :class="{
      'w-75vw mxw-65em mxw-55em': selectedPath.path === '/hardware/no-hardware/request-free-samples',
      'w-75vw mxw-40em': selectedPath.path !== '/hardware/no-hardware/request-free-samples',
    }"
  >
    <q-btn v-if="!selectedPath.hideCloseButton" class="absolute-top-right m-0 z-top" flat round icon="close" @click="$emit('triggerClose', 'Closed via top close button')" />
    <div v-if="!selectedPath.hideStepCounter" class="text-body faded">{{ currentPathCount }}/{{ totalPathCount }}</div>
    <q-card-section>
      <transition-group
        tag="div"
        class=" justify-center items-center position-relative"
        style="overflow-x: hidden;
    display: flex; flex-wrap: nowrap;"
        appear
        :enter-active-class="enterAnimation"
      >
        <!-- :leave-active-class="leaveAnimation" -->
        <!--  SELECTED PATH IS */HARDWARE* -->
        <walkthrough-step key="/hardware" v-if="selectedPath.path == '/hardware'">
          <div class="text-h5 text-center pt-1">Welcome to Torizon!</div>
          <div class="text-center pt-2">
            <div class="text-1">Do you have any <a href="https://www.torizon.io/supported-hardware#torizon-hardware" target="_blank">Toradex-Supported Hardware</a>?</div>
            <div>
              <div class="mt-1">
                <q-btn color="primary" outline size="xl" @click="choosePath('/hardware/no-hardware')" class="mr-2">No </q-btn>
                <q-btn color="primary" size="xl" @click="choosePath('/hardware/torizoncore')">Yes </q-btn>
              </div>
            </div>
          </div>
        </walkthrough-step>
        <!--  SELECTED PATH IS */HARDWARE-ALT* -->
        <walkthrough-step key="/hardware-alt" v-if="selectedPath.path == '/hardware-alt'">
          <div class="text-h5 text-center pt-1">Welcome to Torizon!</div>
          <div class="text-center pt-2">
            <div class="text-1">Do you have any <a href="https://www.torizon.io/supported-hardware#torizon-hardware" target="_blank">Toradex-Supported Hardware</a>?</div>
            <div>
              <div class="mt-1">
                <q-btn color="primary" outline size="xl" @click="choosePath('/hardware/no-hardware')" class="mr-2">No </q-btn>
                <q-btn color="primary" size="xl" @click="choosePath('/hardware-alt/torizoncore')">Yes </q-btn>
              </div>
            </div>
          </div>
        </walkthrough-step>
        <!-- SELECTED PATH IS */HARDWARE/TORIZONCORE* -->
        <walkthrough-step key="/hardware/torizoncoreare" v-else-if="selectedPath.path === '/hardware/torizoncore'">
          <div class="text-h5 text-center pt-1">Welcome to Torizon!</div>
          <div class="text-center pt-2">
            <div class="text-1">
              Is TorizonCore installed in your Toradex module?
            </div>
            <div>
              <div class="mt-1">
                <q-btn color="primary" outline size="xl" @click="choosePath('/hardware/torizoncore/torizoncore-video')" class="mr-2">No </q-btn>
                <q-btn color="primary" size="xl" @click="choosePath('/hardware/torizoncore/torizoncore-video/provisioning-video')">Yes </q-btn>
              </div>
            </div>
          </div>
        </walkthrough-step>
        <!-- SELECTED PATH IS */HARDWARE/TORIZONCORE-ALT* -->
        <walkthrough-step key="/hardware/torizoncoreare" v-else-if="selectedPath.path === '/hardware-alt/torizoncore'">
          <div class="text-h5 text-center pt-1">Welcome to Torizon!</div>
          <div class="text-center pt-2">
            <div class="text-1">
              Is TorizonCore installed in your Toradex module?
            </div>
            <div>
              <div class="mt-1">
                <q-btn color="primary" outline size="xl" @click="choosePath('/hardware-alt/torizoncore/torizoncore-video')" class="mr-2">No </q-btn>
                <q-btn color="primary" size="xl" @click="choosePath('/hardware-alt/torizoncore/torizoncore-video/provisioning')">Yes </q-btn>
              </div>
            </div>
          </div>
        </walkthrough-step>
        <!-- </transition> -->
        <!-- SELECTED PATH IS *HARDWARE-ALT/TORIZONCORE/PROVISIONING* -->
        <walkthrough-step key="/hardware-alt/torizoncore/torizoncore-video/provisioning" v-else-if="selectedPath.path === '/hardware-alt/torizoncore/torizoncore-video/provisioning'">
          <div class="text-h5 text-center pt-1">Welcome to Torizon!</div>
          <div class="text-center pt-2">
            <div class="text-1">
              Provision your first device by clicking this button.
            </div>
          </div>
        </walkthrough-step>
        <!-- SELECTED PATH IS *HARDWARE-ALT/TORIZONCORE/PROVISIONING* -->
        <walkthrough-step key="/hardware-alt/torizoncore/torizoncore-video/provisioning/code-snippet" v-else-if="selectedPath.path === '/hardware-alt/torizoncore/torizoncore-video/provisioning/code-snippet'">
          <div class="text-h5 text-center pt-1">Welcome to Torizon!</div>
          <div class="text-center pt-2">
            <div class="text-1">Copy the command and run it in a terminal <br />on the device you want to provision.</div>
          </div>
        </walkthrough-step>

        <!-- SELECTED PATH IS *HARDWARE/TORIZONCORE/TORIZONCORE-VIDEO* -->

        <walkthrough-step key="/hardware/torizoncore/torizoncore-video" v-else-if="selectedPath.path === '/hardware/torizoncore/torizoncore-video' || selectedPath.path === '/hardware-alt/torizoncore/torizoncore-video'">
          <div class="text-h5 text-center pt-1">Welcome to Torizon!</div>
          <div class="text-left pt-2">
            <div class="text-1">
              To get started, watch our Getting Started guide below and install TorizonCore on your hardware:
            </div>
            <div>
              <q-video :ratio="16 / 9" src="https://www.youtube.com/embed/fuBHSpSqpi0?rel=0" />
            </div>
          </div>
        </walkthrough-step>
        <!-- SELECTED PATH IS *HARDWARE/TORIZONCORE/PROVISIONING-VIDEO* -->
        <walkthrough-step key="/hardware/torizoncore/torizoncore-video/provisioning-video" v-else-if="selectedPath.path === '/hardware/torizoncore/torizoncore-video/provisioning-video'">
          <div class="text-h5 text-center pt-1">Welcome to Torizon!</div>
          <div class="text-left pt-2">
            <div class="text-1">
              Almost done! The final step is to provision your first device. <br />
              Here's a tutorial:
            </div>
            <div>
              <q-video :ratio="16 / 9" src="https://www.youtube.com/embed/5MDNIC3KBCk?rel=0" />
            </div>
          </div>
        </walkthrough-step>
        <!--  SELECTED PATH IS */HARDWARE/NO-HARDWARE* -->
        <walkthrough-step key="/hardware/no-hardware" v-else-if="selectedPath.path === '/hardware/no-hardware'">
          <div class="text-h5 text-center pt-1">Welcome to Torizon!</div>
          <div class="text-center pt-2">
            <div class="text-1">To use the Torizon Platform, you need <a href="https://www.torizon.io/supported-hardware#torizon-hardware" target="_blank">Toradex-Supported Hardware</a>. What are your needs?</div>
            <div class="row items-center justify-center q-gutter-md mt-1">
              <div class="mt-1 col-auto">
                <q-btn
                  :color="!$q.dark.isActive ? 'dark' : 'light'"
                  outline
                  no-caps
                  type="a"
                  class="q-py-md  mxw-10em"
                  href="https://www.torizon.io/supported-hardware#torizon-hardware"
                  target="_blank"
                  @click="
                    addToShowNextForPath('/hardware/no-hardware');
                    logEvent('/hardware/no-hardware/buy-hardware', 'click');
                  "
                >
                  Buy <br />hardware
                </q-btn>
              </div>
              <div class="mt-1 col-auto">
                <q-btn :color="!$q.dark.isActive ? 'dark' : 'light'" outline no-caps class="q-py-md  mxw-10em" @click="choosePath('/hardware/no-hardware/sandbox')"> Enter <br />sandbox </q-btn>
              </div>
              <div class="mt-1 col-auto">
                <q-btn :color="!$q.dark.isActive ? 'dark' : 'light'" outline no-caps class="q-py-md mxw-15em" @click="choosePath('/hardware/no-hardware/request-free-samples')"> Apply for <br />free samples </q-btn>
              </div>

              <div class="mt-1 col-auto">
                <q-btn :color="!$q.dark.isActive ? 'dark' : 'light'" outline no-caps type="a" class="q-py-md  mxw-10em" href="https://www.torizon.io/book-your-demo" target="_blank" @click="addToShowNextForPath('/hardware/no-hardware')"> Book <br />a demo </q-btn>
              </div>
            </div>
          </div>
        </walkthrough-step>
        <!-- SELECTED PATH IS */HARDWARE/NO-HARDWARE/REQUEST-FREE-SAMPLES* -->
        <walkthrough-step key="/hardware/no-hardware/request-free-samples" v-else-if="selectedPath.path === '/hardware/no-hardware/request-free-samples'" style="max-height: 37em;">
          <div class="text-h5 text-center ">Apply for Free Sample</div>
          <free-samples-form :formModel.sync="sampleFormModel" ref="freeSamplesForm" :sampleFormCompleted="selectedPath.sampleFormCompleted" />
        </walkthrough-step>

        <!-- SELECTED PATH IS */HARDWARE/NO-HARDWARE/SANDBOX* -->
        <walkthrough-step key="/hardware/no-hardware/sandbox" v-else-if="selectedPath.path === '/hardware/no-hardware/sandbox'">
          <div class="text-h5 text-center ">Thank you for your interest!</div>
          <div class="text-center pt-2">
            <div class="text-1">
              We are constantly working on improving your experience. <br />
              While this feature isn't available right now, you can continue exploring.
            </div>
          </div>
        </walkthrough-step>

        <!-- SELECTED PATH IS */HARDWARE/NO-HARDWARE/REQUEST-FREE-SAMPLES/CONFIRMATION* OR  */HARDWARE/NO-HARDWARE/SANDBOX/FINISH*
    -->
        <walkthrough-step
          key="/hardware/no-hardware/sandbox/finish"
          v-else-if="selectedPath.path === '/hardware/no-hardware/sandbox/finish' || selectedPath.path === '/hardware/no-hardware/request-free-samples/confirmation' || selectedPath.path === '/hardware-alt/torizoncore/torizoncore-video/provisioning/code-snippet/finish'"
        >
          <div class="text-h5 text-center pt-1">Welcome to Torizon!</div>
          <div class="text-left pt-2">
            <div class="text-1">
              To finish this guide, check out this overview of the platform and learn what you can do:
            </div>
            <div>
              <q-video :ratio="16 / 9" src="https://www.youtube.com/embed/_bgP0-qtNz0?rel=0" />
            </div>
          </div>
        </walkthrough-step>
      </transition-group>
    </q-card-section>

    <q-card-actions align="center" class="justify-center mt-2 pt-2 h-divide-top-dotted">
      <!-- <template v-if="selectedPath && selectedPath !== '/hardware/torizoncore'">
        <q-btn
          flat
          label="Skip this step"
          @click="skipStep"
        />
        <q-space />
      </template> -->
      <template v-if="!selectedPath.hidePreviousButton">
        <q-btn color="primary" outline label="Previous" @click="selectedPath.previousButtonAction ? selectedPath.previousButtonAction($event) : previousStep($event)" />
      </template>
      <template v-if="!selectedPath.hideNextButton || showNextForPath[selectedPath.path]">
        <q-btn
          color="primary"
          :label="nextStepLabel"
          @click="
            ($event) => {
              selectedPath.nextButtonAction ? selectedPath.nextButtonAction($event, { formInstance: $refs.freeSamplesForm }) : nextStep($event);
            }
          "
        />
      </template>
    </q-card-actions>
  </q-card>
</template>

<script>
import { type } from '@amcharts/amcharts4/core';
import WalkthroughStep from './WalkthroughStep.vue';
import FreeSamplesForm from './FreeSamplesForm.vue';
export default {
  components: { WalkthroughStep, FreeSamplesForm },
  name: 'WalkthroughSteps',
  props: {
    show: {
      type: Boolean,
      default: false,
    },
    selectedPath: {
      type: Object,
      default: () => null,
    },
    paths: {
      type: Array,
      default: () => [],
    },
    sampleFormCompleted: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      view: 'landing',
      // freeSampleModel: {
      //   firstname: '',
      //   lastname: '',
      //   email: '',
      //   phone: '',
      //   company: '',
      //   country: '',
      //   detail: '',
      //   subscribeNewsletter: false,
      //   acceptPolicy: false,
      // },
      enterAnimation: 'animated slideInRight',
      leaveAnimation: 'animated slideOutLeft',
      showNextForPath: {},
      sampleFormModel: {
        jobRoles: [],
        projectStage: '',
        expectedVolume: '',
        projectDetail: '',
        biggestChallenge: '',
      },
    };
  },
  computed: {
    currentPathCount() {
      // if (this.selectedPath.path == '/hardware') return 1;
      return (this.selectedPath.path || '').split('/').length - 1;
    },
    totalPathCount() {
      return Object.keys(this.paths).length;
    },
    nextStepLabel() {
      if (typeof this.selectedPath.nextButtonLabel === 'function') {
        return this.selectedPath.nextButtonLabel(this.walkthroughStateData || {});
      }
      return this.selectedPath.nextButtonLabel || 'Next';
    },
    walkthroughStateData() {
      return {
        view: this.view,
        selectedPath: this.selectedPath,
        paths: this.paths,
      };
    },
    formInstance() {
      return this.$refs.freeSamplesForm;
    },
  },
  methods: {
    onShowChanged(val) {
      this.$emit('close', val);
    },
    previousStep() {
      this.backwardAnimation();
      this.$emit('previousStep', {});
      setTimeout(() => {
        this.forwardAnimation();
      }, 1000);
    },
    nextStep() {
      this.forwardAnimation();
      this.$emit('nextStep', {});
    },
    skipStep(step) {
      this.$emit('skipStep', step);
    },
    choosePath(path) {
      this.$emit('choosePath', path);
    },
    forwardAnimation() {
      this.enterAnimation = 'animated slideInRight';
      this.leaveAnimation = 'animated slideOutLeft';
    },
    backwardAnimation() {
      this.enterAnimation = 'animated slideInLeft';
      this.leaveAnimation = 'animated slideOutRight';
    },
    addToShowNextForPath(path) {
      this.$set(this.showNextForPath, path, true);
    },
    logEvent(path, type) {
      this.$emit('logEvent', {
        path,
        type,
      });
    },
  },
};
</script>

<style></style>
