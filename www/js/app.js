
angular.module('starter', ['ionic','ionic.service.core','ionic.service.analytics','ionic.service.deploy','starter.controllers','starter.services','ngStorage','ngCordova','ionic-material','ionMdInput'])

.run(function($rootScope,$ionicLoading,$ionicPlatform,$ionicAnalytics,$cordovaSplashscreen) {

    setTimeout(function() {
      $cordovaSplashscreen.hide()
    }, 1000)

    $ionicPlatform.ready(function() {

        $ionicAnalytics.register();

        // Hide the accessory bar by default (remove this to show the accessory bar above the keyboard
        if (window.cordova && window.cordova.plugins.Keyboard) {
            cordova.plugins.Keyboard.hideKeyboardAccessoryBar(true);
        }
        if (window.StatusBar) {
            // org.apache.cordova.statusbar required
            StatusBar.styleDefault();
        }
    });

    $rootScope.$on('loading:show', function() {
      $ionicLoading.show({template: 'Loading...'})
    })

    $rootScope.$on('loading:hide', function() {
      $ionicLoading.hide()
    })
})

.config(function($stateProvider,$httpProvider,$urlRouterProvider,$ionicConfigProvider,$ionicAppProvider,ConfigServiceProvider) {

    // Turn off caching for demo simplicity's sake
    $ionicConfigProvider.views.maxCache(0);

    // Turn off back button text
    //$ionicConfigProvider.backButton.previousTitleText(false);

    // Identify app
    $ionicAppProvider.identify({
      app_id: ConfigServiceProvider.$get().Ionic_app_id,
      api_key: ConfigServiceProvider.$get().Ionic_app_key
    });

    $httpProvider.interceptors.push(function($rootScope) {
      return {
        request: function(config) {
          $rootScope.$broadcast('loading:show')
          return config
        },
        response: function(response) {
          $rootScope.$broadcast('loading:hide')
          return response
        }
      }
    })

    $stateProvider.state('app', {
        url: '/app',
        abstract: true,
        templateUrl: 'templates/menu.html',
        controller: 'AppCtrl'
    })

    .state('app.login', {
        url: '/login',
        views: {
            'menuContent': {
                templateUrl: 'templates/login.html',
                controller: 'LoginCtrl'
            },
            'fabContent': {
                template: ''
            }
        }
    })

    .state('app.chat', {
        url: '/chat',
        views: {
            'menuContent': {
                templateUrl: 'templates/chat.html',
                controller: 'ChatCtrl'
            },
            'fabContent': {
                template: ''
            }
        }
    })

    .state('app.feed', {
        url: '/feed',
        views: {
            'menuContent': {
                templateUrl: 'templates/feed.html',
                controller: 'FeedCtrl'
            },
            'fabContent': {
                template: ''
            }
        }
    });

    $urlRouterProvider.otherwise('/app/feed');
});
