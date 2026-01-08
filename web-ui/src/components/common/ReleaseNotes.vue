<template>
  <div>
    <div class="text-h4 mb-1">Release Notes</div>
    <markdown :content="releaseNotesData"></markdown>
  </div>
</template>

<script>
import { mapGetters } from 'vuex';
import Markdown from './Markdown.vue';

export default {
  components: { Markdown },
  name: 'ReleaseNotes',
  computed: {
    ...mapGetters({
      releaseNotesData: 'ui/releaseNotesMd',
    }),
  },
};
</script>
