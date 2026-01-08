// require lib
import Vue from 'vue';
import VueCodemirror from 'vue-codemirror';

// language files
import 'codemirror/mode/javascript/javascript.js';
import 'codemirror/mode/yaml/yaml.js';

// require styles
import 'codemirror/lib/codemirror.css';

// import theme style
import 'codemirror/theme/base16-dark.css';
import 'codemirror/theme/base16-light.css';
// require more codemirror resource...

// you can set default global options and events when use
Vue.use(VueCodemirror /* {
  options: { theme: 'base16-dark', ... },
  events: ['scroll', ...]
} */);
