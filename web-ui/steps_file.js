'use strict';
// in this file you can append custom step methods to 'I' object
let assert = require('assert');

module.exports = function() {
  return actor({
    openPageAsDesktop: function(page = '/') {
      this.openPageToSize(page);
    },
    openPageAsMobile: function(page = '/') {
      this.openPageToSize(page, 320, 490);
    },
    openPageToSize: function(page = '/', width = 1200, height = 850) {
      this.amOnPage(page);
      this.resizeWindow(width, height);
      this.wait(1);
    },
    seeElementWidth: function(selector, width) {
      this.executeScript(
        function(el, w) {
          const elem = document.querySelector(el);
          if (elem.offsetWidth !== w) {
            throw `Expected element width to be ${w} but actual width is ${elem.offsetWidth}`;
          }
        },
        selector,
        width,
      );
    },
    seeElementHeight: function(selector, height) {
      this.executeScript(
        function(el, w, h) {
          const elem = document.querySelector(el);
          if (elem.offsetHeight !== h) {
            throw `Expected element height to be ${h} but actual height is ${elem.offsetHeight}`;
          }
        },
        selector,
        height,
      );
    },
    seeElementSize: function(selector, width, height) {
      this.executeScript(
        function(el, w, h) {
          const elem = document.querySelector(el);
          if (elem.offsetWidth !== w || elem.offsetHeight !== h) {
            throw `Expected element width and height to be ${w} and ${h} but actual with and height are ${elem.offsetWidth} and ${elem.offsetHeight}`;
          }
        },
        selector,
        width,
        height,
      );
    },
    doClick: function(selector) {
      this.executeScript(function(el) {
        const fireEvent = function(e, etype) {
          const el = document.querySelector(e);
          if (!el) {
            throw `Unable to click ${e}:  Element not found`;
          }
          // JQuery(el).trigger('click');
          if (el.fireEvent) {
            el.fireEvent('on' + etype);
          } else {
            var evObj = document.createEvent('Events');
            evObj.initEvent(etype, true, false);
            el.dispatchEvent(evObj);
          }
        };
        fireEvent(el, 'click');
      }, selector);
    },
    seeElementIsOutOfView: function(selector) {
      this.executeScript(function(e) {
        const el = document.querySelector(e);
        var isInViewport = function(elem) {
          var bounding = elem.getBoundingClientRect();
          return bounding.top >= 0 && bounding.left >= 0 && bounding.bottom <= (window.innerHeight || document.documentElement.clientHeight) && bounding.right <= (window.innerWidth || document.documentElement.clientWidth);
        };
        if (isInViewport(el)) throw `Expected ${e} to NOT be within ViewPort`;
      }, selector);
    },
    seeElementIsInOfView: function(selector) {
      this.executeScript(function(e) {
        const el = document.querySelector(e);
        var isInViewport = function(elem) {
          var bounding = elem.getBoundingClientRect();
          return bounding.top >= 0 && bounding.left >= 0 && bounding.bottom <= (window.innerHeight || document.documentElement.clientHeight) && bounding.right <= (window.innerWidth || document.documentElement.clientWidth);
        };
        if (!isInViewport(el)) throw `Expected ${e} to be within ViewPort`;
      }, selector);
    },
  });
};
