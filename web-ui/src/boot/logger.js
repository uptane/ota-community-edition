import Vue from 'vue';
import { Logger } from 'beaver-logger';
import { AuthService } from '../services/auth.service';

export default ({ Vue }) => {
  const $logger = Logger({});
  // const $logger = {
  //     info: ()=>{},
  //     warn: ()=>{},
  //     debug: ()=>{},
  //     error: ()=>{},
  //     addHeaderBuilder: ()=>{},
  // }
  // const newConsole = (function(oldConsole){
  //     let c = {};
  //     Object.keys(oldConsole).forEach(function(k){
  //         c[k] = function() {
  //             const m = k==='error'?'log':k;
  //             if($logger[k]){
  //                 $logger[k](...arguments);
  //             };
  //             oldConsole[m].call((k).toUpperCase()+': ', ...(arguments || []));
  //         };
  //     });
  //     return c;
  // }(window.console));
  Vue.prototype.$log = console.log;
  Vue.prototype.$logger = $logger;

  $logger.addHeaderBuilder(function() {
    const token = AuthService.getAccessToken();
    return {
      Authorization: `Bearer ${token}`,
    };
  });
  window.log = function() {
    if (process.env.DEV) {
      console.log(...arguments);
    }
    $logger.info(...arguments);
  };
  window.info = function() {
    if (process.env.DEV) {
      console.info(...arguments);
    }
    $logger.info(arguments);
  };
  window.warn = function() {
    if (process.env.DEV) {
      console.warn(...arguments);
    }
    $logger.warn(...arguments);
  };
  window.debug = function() {
    if (process.env.DEV) {
      console.debug(...arguments);
    }
    $logger.debug(...arguments);
  };
  window.logError = function() {
    if (process.env.DEV) {
      console.error(...arguments);
    }
    $logger.error(...arguments);
  };
  window.logger = $logger;
};
