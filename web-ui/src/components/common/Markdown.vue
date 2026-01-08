<template>
  <div>
    <div v-html="parsedContent"></div>
    <div v-if="isTruncated" class="text-right text-warning">
      <q-icon name="warning" class="text-warning" />
      <span class="text-warning"> {{ warningMessage }} </span>
    </div>
  </div>
</template>

<script>
import showdown from 'showdown';
import DOMPurify from 'dompurify';

export default {
  name: 'Markdown',
  props: {
    content: {
      type: String,
      default: ' ',
    },
    maxLength: {
      type: Number,
      default: 5120,
    },
    warningMessage: {
      type: String,
      default: 'Content truncated.',
    },
  },
  computed: {
    parsedContent() {
      const converter = new showdown.Converter();
      const html = converter.makeHtml(this.truncatedContent);
      // Sanitize the content to prevent XSS attacks
      return DOMPurify.sanitize(html);
    },

    truncatedContent() {
      if (this.content.length > this.maxLength) {
        return `${this.content.substring(0, this.maxLength)}...`;
      }
      return this.content;
    },

    isTruncated() {
      return this.content.length > this.maxLength;
    },
  },
};
</script>

<style></style>
