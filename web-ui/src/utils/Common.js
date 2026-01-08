import _ from 'underscore';
import forge from 'node-forge';

const doLogout = () => {
  document.getElementById('logout').submit();
};
const toMilliseconds = (timestamp) => {
  return new Date(timestamp).getTime();
};

const resetAll = (asyncCollection, isFetching = false) => {
  _.each(asyncCollection, (async) => {
    resetAsync(async, isFetching);
  });
};

const resetAsync = (obj, isFetching = false) => {
  obj.isFetching = isFetching;
  obj.status = null;
  obj.data = null;
  obj.code = null;
};

const handleAsyncSuccess = (response) => {
  return {
    status: 'success',
    code: response.status,
    data: response.data,
    isFetching: false,
  };
};

const handleAsyncError = (error) => {
  error.response = error.response || { status: null, data: {} };

  if (error.response.status === 401) {
    doLogout();
  }

  return {
    status: 'error',
    code: error.response.status,
    data: error.response.data,
    isFetching: false,
  };
};

const distinctBy = (arr, keyFn) => {
  arr = arr || [];
  const newArr = {};
  arr.forEach((a) => {
    newArr[typeof keyFn === 'string' ? a[keyFn] : keyFn(a)] = a;
  });
  return Object.values(newArr);
};
const groupBy = (src, keyFn) => {
  return src.reduce(function(rv, x) {
    let key = typeof keyFn === 'function' ? keyFn(x) : x[keyFn];
    (rv[key] = rv[key] || []).push(x);
    return rv;
  }, {});
};
const calculateKeyId = (key, keyType = 'RSA') => {
  if ((keyType || '').toUpperCase() === 'RSA') {
    let base64Str = key
      .replace('-----BEGIN PUBLIC KEY-----', '')
      .replace('-----END PUBLIC KEY-----', '')
      .replace(/\\n/g, '');
    let keyStr = window.atob(base64Str);
    var md = forge.md.sha256.create();
    md.update(keyStr);
    return md.digest().toHex();
  }
  if ((keyType || '').toUpperCase() === 'ED25519') {
    // The canonical representation of a key is defined in RFC 8032, and can be represented as follows in ASN.1:
    //
    //  SubjectPublicKeyInfo  ::=  SEQUENCE  {
    //    algorithm         AlgorithmIdentifier,
    //    subjectPublicKey  BIT STRING
    //  }
    //  AlgorithmIdentifier  ::=  SEQUENCE  {
    //    algorithm   OBJECT IDENTIFIER,
    //    parameters  ANY DEFINED BY algorithm OPTIONAL
    //  }
    //
    // For Ed25519 there aren't any parameters to worry about, so the identifier is always the same. The other
    // bytes are just ASN.1 encoding details of the length of the object, etc.
    //
    // The keyId of any key in our Uptane implementation is the sha256 of the canonical representation of the pubkey.
    let hexEd25519ObjectIdentifierBytes = '302A300506032B6570032100';
    let hexStr = hexEd25519ObjectIdentifierBytes + key;
    var bytes = '';
    for (let i = 0; i < hexStr.length; i++) {
      bytes += !((i - 1) & 1) ? String.fromCharCode(parseInt(hexStr.substring(i - 1, i + 1), 16)) : '';
    }
    var digest = forge.md.sha256.create();
    digest.update(bytes);
    return digest.digest().toHex();
  }
};
/**
 * Sort an array based on the order of another array
 * @param {Array} arrayToSort
 * @param {Array} order
 * @param {String | Function} keyFn
 * @returns
 */
function mapOrder(arrayToSort, order, keyFn) {
  const array = [...arrayToSort];
  array.sort(function(a, b) {
    const key = typeof keyFn === 'Function' ? keyFn(a, b) : keyFn;
    var A = a[key],
      B = b[key];
    if (order.indexOf(A) > order.indexOf(B)) {
      return 1;
    } else {
      return -1;
    }
  });

  return array;
}
/**
 * Ensure a condition is met before executing a callback. Makes a maximum of 10 attempts if a condition is not met.
 * @param {Function} condition
 * @param {Number} attempts
 * @param {Number} interval
 * @returns {Promise}
 * @example
 * ensureCondition({condition:() => {
 *     return document.getElementById('myElement') !== null;
 * }, callback:() => {
 *    // Do something
 * }}).then(() => {
 *    // Do something
 * }).catch((err) => {
 *   // Do something
 * });
 *
 */
function ensureCondition({ condition, attempts = 10, interval = 500 }) {
  return new Promise((resolve, reject) => {
    if (!condition || typeof condition !== 'function') {
      reject('Invalid arguments passed to ensureCondition');
      return;
    }
    if (attempts <= 0) {
      return reject('Maximum attempts reached');
    }
    if (condition()) {
      return resolve();
    } else {
      setTimeout(() => {
        ensureCondition({ condition, attempts: attempts - 1, interval })
          .then(resolve)
          .catch(reject);
      }, interval);
    }
  });
}
export { mapOrder, doLogout, resetAll, resetAsync, handleAsyncSuccess, handleAsyncError, toMilliseconds, groupBy, distinctBy, calculateKeyId, ensureCondition };
