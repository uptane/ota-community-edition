<template>
  <q-dialog v-model="show" transition-show="slide-up" transition-hide="slide-down" persistent>
    <!-- <q-card class="mnw-80vw "> -->
    <div class="flex-center row" style="position: relative">
      <div class="q-card p-1">
        <div class="row" v-if="introData.title">
          <div class="col">
            <h6 class=" m-0 pl-1 pr-1 text-center">{{ introData.title }}</h6>
          </div>
          <div class="col-auto">
            <q-btn flat dense icon="close" class=" m-0 pl-1 pr-1 text-center" @click="markAsRead"></q-btn>
          </div>
        </div>

        <q-carousel v-model="slide" transition-prev="slide-right" transition-next="slide-left" ref="carousel" swipeable animated infinite control-color="white" navigation navigation-position="top" navigation-icon="radio_button_unchecked" padding :arrows="multiSlide" class="shadow-0 q-card ">
          <template v-slot:navigation-icon="{ active, btnProps, onClick }">
            <div v-if="multiSlide">
              <q-btn v-if="active" size="0.4rem" icon="radio_button_checked" color="primary" flat round dense @click="onClick" />
              <q-btn v-else size="0.4rem" :icon="btnProps.icon" :color="$q.dark.isActive ? 'white' : 'black'" flat round dense @click="onClick" />
            </div>
          </template>
          <template v-for="(slide, index) in parsedSlides">
            <q-carousel-slide
              :name="'slide_' + index"
              class="column no-wrap flex-center"
              :class="{
                'pt-0': !slide.image,
              }"
              :img-src="slide.bgImage"
              :key="'slide_key_' + index"
            >
              <!-- img-src="~assets/svg/dash-bg.svg" -->
              <!-- <div v-html="slide.htmlContent"></div> -->
              <div>
                <q-img v-if="slide.image" :src="slide.image" class="shadow-12 mxh-20em" />
                <div class="q-mt-md text-center mk-html-content" v-html="slide.text"></div>
              </div>
            </q-carousel-slide>
          </template>
        </q-carousel>
        <div class="row q-gutter-xs flex-center pb-1">
          <q-btn flat color="primary" icon="arrow_right" label="Next" v-if="multiSlide" @click="$refs.carousel.next()" />
          <q-btn icon="check" color="primary" @click="markAsRead" label="Got it" />
        </div>
      </div>
    </div>
    <!--   </q-card> -->
  </q-dialog>
</template>

<script>
import Loader from '../loaders/Loader.vue';
import showdown from 'showdown';
import { mapActions, mapGetters } from 'vuex';
export default {
  components: { Loader },
  name: 'FeatureIntro',
  props: {
    introData: {
      type: Object,
      default: () => {
        return {
          title: 'New Feature Alert',
          optionKey: 'new_feature_intro',
          slides: [
            {
              image: '/statics/feature-intro/sync-update/{theme}/1.png',
              text: 'This is a slide with text content.',
            },
            {
              image: '/statics/feature-intro/sync-update/{theme}/2.png',
              text: 'You can use html in text content',
            },
            {
              bgImage: '~assets/svg/dash-bg.svg',
              image: '/statics/feature-intro/sync-update/{theme}/3.png',
              text: 'You can use markdown too',
            },
          ],
        };
      },
    },
  },
  data() {
    return {
      slide: 'slide_0',
      requested: false,
      onCloseAction: () => {},
    };
  },
  computed: {
    ...mapGetters({
      userSettings: 'ui/userSettings',
    }),
    optionKey() {
      return this.introData.optionKey;
    },
    parsedSlides() {
      return (this.introData.slides || []).map((m) => {
        const converter = new showdown.Converter();
        return {
          ...m,
          text: converter.makeHtml(m.text),
          image: (m.image || '').replace('{theme}', this.$q.dark.isActive ? 'dark' : 'light'),
          bgImage: (m.bgImage || '').replace('{theme}', this.$q.dark.isActive ? 'dark' : 'light'),
        };
      });
    },
    multiSlide() {
      return this.parsedSlides && this.parsedSlides.length > 1;
    },
    show: {
      get() {
        return this.requested && this.getSavedSettingOrDefault(this.optionKey, true);
      },
      set(v) {
        this.updateUserOption(this.optionKey, v);
      },
    },
  },
  methods: {
    ...mapActions({
      saveUserSettings: 'ui/saveUserSettings',
    }),

    markAsRead() {
      this.show = false;
      this.onCloseAction();
    },
    updateUserOption(key, value) {
      this.$set(this.userSettings, key, value);
      this.saveUserSettings({ [key]: value });
    },
    getSavedSettingOrDefault(key, defaultValue) {
      return typeof this.userSettings[key] !== 'undefined' ? this.userSettings[key] : defaultValue;
    },
  },
  mounted() {
    this.$events.$on(`dialogs:featureIntro:request`, ({ key, onClose }) => {
      if (key !== null && key == this.optionKey) {
        this.onCloseAction = onClose || (() => {});
        this.requested = true;
      }
    });
  },
};
</script>
