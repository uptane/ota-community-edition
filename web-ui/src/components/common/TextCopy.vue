<template>
  <div class="w-100">
    <div class="text-center" style="position:relative" :class="{}" @click="copyText">
      <!-- 'text-black bg-white': !darkTheme,
            'text-white bg-black': darkTheme -->
      <div class="z-10 cursor-pointer" v-html="tooltipCopyText" v-if="promptPosition === 'top'"></div>
      <div
        class="z-1 w-100 h-100 absolute-top opacity-20"
        :class="{
          'bg-secondary': textCopied,
          ' transparent': !textCopied,
        }"
      ></div>
    </div>

    <div class="border-round text-cmd-copy">
      <q-input :value="content" filled autogrow type="textarea" class="command-snippet" :autofocus="$q.screen.gt.sm" @click="copyText" />
    </div>
    <div>
      <q-btn class="" no-caps v-if="promptPosition === 'bottom'" :label="tooltipCopyText" icon="content_copy" flat @click="copyText" :color="textCopied ? 'positive' : 'primary'"> </q-btn>
    </div>
  </div>
</template>

<script>
import { copyToClipboard } from 'quasar';

export default {
  name: 'TextCopy',
  props: {
    content: '',
    promptPosition: {
      type: String,
      default: 'top',
    },
  },
  data() {
    return {
      textCopied: false,
      tooltipCopyText: 'Click to copy to clipboard',
    };
  },
  methods: {
    copyText() {
      copyToClipboard(this.content)
        .then(() => {
          this.tooltipCopyText = 'Copied!';
          this.textCopied = true;
          setTimeout(() => {
            this.tooltipCopyText = 'Click to copy to clipboard';
            this.textCopied = false;
          }, 4000);
        })
        .catch(() => {
          // fail
        });
    },
  },
};
</script>
