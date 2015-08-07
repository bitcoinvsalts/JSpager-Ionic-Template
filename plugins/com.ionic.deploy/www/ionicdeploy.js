var IonicDeploy = {
  init: function(app_id, success, failure) {
    cordova.exec(
      success,
      failure,
      'IonicDeploy',
      'initialize',
      [app_id]
    );
  },
  check: function(app_id, success, failure) {
    cordova.exec(
      success,
      failure,
      'IonicDeploy',
      'check',
      [app_id]
    );
  },
  download: function(app_id, success, failure) {
  	cordova.exec(
  		success,
  		failure,
  		'IonicDeploy',
  		'download',
  		[app_id]
  	);
  },
  extract: function(app_id, success,failure) {
    cordova.exec(
      success,
      failure,
      'IonicDeploy',
      'extract',
      [app_id]
    );
  },
  redirect: function(app_id, success, failure) {
  	cordova.exec(
  		success,
  		failure,
  		'IonicDeploy',
  		'redirect',
  		[app_id]
  	);
  }
}

module.exports = IonicDeploy;
