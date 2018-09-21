/**
 * FileEncryption.js
 *
 * @overview Easy to use cryptographic operations for Cordova.
 * @author Suhas P R
 * @license MIT
*/
var exec = require('cordova/exec');

var PLUGIN_NAME = 'FileEncryption';

var KEY_FROM_DEVICE = false;
var KEY_FROM_PASSPHRASE = true;

var FileEncryption = {
  encrypt: function (path, password, success, error) {
    //if (!path || arguments.length === 0) return;
    exec(success, error, PLUGIN_NAME, 'encrypt', [path, password, KEY_FROM_DEVICE]);
  },
  decrypt: function (path, password, success, error) {
    //if (!path || arguments.length === 0) return;
    exec(success, error, PLUGIN_NAME, 'decrypt', [path, password, KEY_FROM_DEVICE]);
  },
  encryptP: function (path, password, success, error) {
    //if (!path || arguments.length === 0) return;
    exec(success, error, PLUGIN_NAME, 'encrypt', [path, password, KEY_FROM_PASSPHRASE]);
  },
  decryptP: function (path, password, success, error) {
    //if (!path || arguments.length === 0) return;
    exec(success, error, PLUGIN_NAME, 'decrypt', [path, password, KEY_FROM_PASSPHRASE]);
  },
};

module.exports = FileEncryption;
