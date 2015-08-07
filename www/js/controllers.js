/* global angular, document, window */
'use strict';

angular.module('starter.controllers', [])

.controller('AppCtrl', function($scope,$ionicDeploy,$ionicPopup,$ionicLoading,Chat) {

    $scope.isExpanded = false;
    $scope.hasHeaderFabLeft = false;
    $scope.hasHeaderFabRight = false;

    ////////////////////////////////////////
    // Layout Methods
    ////////////////////////////////////////

    $scope.hideNavBar = function() {
        document.getElementsByTagName('ion-nav-bar')[0].style.display = 'none';
    };

    $scope.showNavBar = function() {
        document.getElementsByTagName('ion-nav-bar')[0].style.display = 'block';
    };

    $scope.noHeader = function() {
        var content = document.getElementsByTagName('ion-content');
        for (var i = 0; i < content.length; i++) {
            if (content[i].classList.contains('has-header')) {
                content[i].classList.toggle('has-header');
            }
        }
    };

    $scope.setExpanded = function(bool) {
        $scope.isExpanded = bool;
    };

    $scope.setHeaderFab = function(location) {
        var hasHeaderFabLeft = false;
        var hasHeaderFabRight = false;

        switch (location) {
            case 'left':
                hasHeaderFabLeft = true;
                break;
            case 'right':
                hasHeaderFabRight = true;
                break;
        }

        $scope.hasHeaderFabLeft = hasHeaderFabLeft;
        $scope.hasHeaderFabRight = hasHeaderFabRight;
    };

    $scope.hasHeader = function() {
        var content = document.getElementsByTagName('ion-content');
        for (var i = 0; i < content.length; i++) {
            if (!content[i].classList.contains('has-header')) {
                content[i].classList.toggle('has-header');
            }
        }
    };

    $scope.hasFooter = function() {
        var content = document.getElementsByTagName('ion-content');
        for (var i = 0; i < content.length; i++) {
            if (!content[i].classList.contains('has-footer')) {
                content[i].classList.toggle('has-footer');
            }
        }
    };

    $scope.hideHeader = function() {
        $scope.hideNavBar();
        $scope.noHeader();
    };

    $scope.showHeader = function() {
        $scope.showNavBar();
        $scope.hasHeader();
    };

    $scope.clearFabs = function() {
        var fabs = document.getElementsByClassName('button-fab');
        if (fabs.length && fabs.length > 1) {
            fabs[0].remove();
        }
    };

    /*
    $ionicDeploy.watch().then(function() {}, function() {},
        function(hasUpdate) {
          if (hasUpdate) {
            $ionicPopup.alert({
                title: 'Update available!',
                template: '<div align=center>Within one minute the app will update and restart itself.<br>Sorry for the inconvenience.</div>'
            })
          }
          $ionicDeploy.update().then(function(res) {
            console.log('Ionic Deploy: Update Success! ', res);
          }, function(err) {
            console.log('Ionic Deploy: Update error! ', err);
          }, function(prog) {
            console.log('Ionic Deploy: Progress... ', prog);
        });
    });
    */

})

.controller('LoginCtrl', function($scope,$ionicDeploy,$http,$timeout,$state,$stateParams,$cordovaOauth,$localStorage,ConfigService,ionicMaterialInk,$ionicPopup) {

    $scope.PagerTitle = ConfigService.PagerTitle;
    $scope.PagerVersion = ConfigService.PagerVersion;

    $scope.$parent.clearFabs();
    $timeout(function() {
        $scope.$parent.hideHeader();
    }, 0);
    ionicMaterialInk.displayEffect();

    $scope.doUpdate = function() {
      $ionicDeploy.update().then(function(res) {
        console.log('Ionic Deploy: Update Success! ', res);
      }, function(err) {
        console.log('Ionic Deploy: Update error! ', err);
      }, function(prog) {
        console.log('Ionic Deploy: Progress... ', prog);
      });
    };

    // Check Ionic Deploy for new code
    $scope.checkForUpdates = function() {
      console.log('Ionic Deploy: Checking for updates');
      $ionicDeploy.check().then(function(hasUpdate) {
        console.log('Ionic Deploy: Update available: ' + hasUpdate);
        $scope.hasUpdate = hasUpdate;
      }, function(err) {
        console.error('Ionic Deploy: Unable to check for updates', err);
      });
    }

    $scope.loginFacebook = function() {
      $cordovaOauth.facebook(ConfigService.FBappKey, ["read_stream","public_profile","user_location","user_photos"]).then(function(result) {
          $localStorage.accessToken = result.access_token;
          $state.go('app.feed');
      }, function(error) {
          $ionicPopup.alert({title:'Facebook Login',template: 'There was a problem logging in. Please try again.'});
          console.log(error);
      });
    };
})


.controller('ChatCtrl', function($scope,$state,$http,$localStorage,$ionicScrollDelegate,Chat,Notification,$ionicPopup) {

    $scope.init = function() {

        if($localStorage.hasOwnProperty("accessToken") == true) {

            if ($localStorage.username) {
              Chat.setUsername($localStorage.username);
              $scope.username = $localStorage.username;
            }
            else {
              $http.get("https://graph.facebook.com/me", { params: { access_token: $localStorage.accessToken, format: "json" }}).then(function(result) {
                $localStorage.username = result.data.name;
                Chat.setUsername(result.data.name);
                $scope.username = result.data.name;
              });
            }
            $scope.$parent.showHeader();
            $scope.hasFooter();
            $scope.messages = Chat.getMessages();
            Notification.hide();
            $ionicScrollDelegate.scrollBottom(true);

            $scope.$watch('newMessage', function(newValue, oldValue) {
              if(typeof newValue != 'undefined'){
                if(newValue != ''){
                  Chat.typing();
                }
                else{
                  Chat.stopTyping();
                }
              }
            });

            $scope.sendMessage = function() {
              if($scope.newMessage){
                Chat.sendMessage($scope.newMessage);
                $scope.newMessage = '';
                $ionicScrollDelegate.scrollBottom(true);
              }
              else{
                alert('Can\'t be empty');
              }
            }
        }
        else {
            $state.go('app.login');
        }
    }
})


.controller('FeedCtrl', function($scope,$stateParams,$ionicPopup,$ionicScrollDelegate,$http,$localStorage,$state,$location,$timeout,$q,ConfigService,ionicMaterialMotion,ionicMaterialInk) {

    $scope.LikePopup = function() {
        $http.get("https://graph.facebook.com/me/likes/"+ConfigService.FBpageID, { params: { access_token: $localStorage.accessToken, format: "json" }}).then(function(result) {
          console.log(JSON.stringify(result));
          if( !result.data.data[0] ) {
            var alertPopup = $ionicPopup.alert({
                title: 'Please subscribe to the FB page:',
                template: '<div align=center><iframe src="http://www.facebook.com/plugins/like.php?href=https://www.facebook.com/'+ConfigService.FBpageID+'&amp;layout=button&amp;show_faces=false&amp;action=like&amp;colorscheme=light" scrolling="no" frameborder="0" style="border:none;overflow:hidden;width:90px;height:20px;" allowTransparency="true"></iframe></div>'
            });
          }
        });
    }

    $scope.loadFeed = function() {

      if ($scope.paging) {

        // If Paging exist it means it's Not the First Page:
        var postsCall = $http.get($scope.paging);

        $q.all([postsCall]).then(function(result) {

            if (result[0].data.data.length < 25 ) {
              $scope.last_page = true;
            }

            $scope.posts = result[0].data.data;
            $scope.paging = result[0].data.paging.next;
            $scope.$broadcast('scroll.infiniteScrollComplete');
            $ionicScrollDelegate.scrollTop();

            $timeout(function() {
                ionicMaterialMotion.fadeSlideIn({
                    selector: '.animate-fade-slide-in .item'
                });
                ionicMaterialInk.displayEffect();
            }, 600);

        }, function(error) {
            console.log("--- Feed Error ---");
            alert("There was a problem getting your data.  Check the logs for details.");
            console.log(error);
        });

      }
      else {

        //First Page it is:
        if (ConfigService.PopupLiker) {
            $scope.LikePopup();
        }
        $scope.last_page = false;

        var pageCall = $http.get("https://graph.facebook.com/"+ConfigService.FBpageID, { params: { access_token: $localStorage.accessToken, fields: "name,cover", format: "json" }});
        var pictureCall = $http.get("https://graph.facebook.com/"+ConfigService.FBpageID+"/picture?width=150&redirect=false", { params: { access_token: $localStorage.accessToken, fields: "", format: "json" }});

        $q.all([pageCall,pictureCall]).then(function(result) {

            $scope.pageData = result[0].data;
            var coverUrl = "url("+result[0].data.cover.source+")";
            $scope.coverImg = {'background-image': coverUrl};
            $scope.profilePic = result[1].data.data.url;

            $timeout(function() {
                ionicMaterialMotion.slideUp({
                    selector: '.slide-up'
                });
            }, 10);

            var postsCall = $http.get("https://graph.facebook.com/"+ConfigService.FBpageID+"/posts", { params: { access_token: $localStorage.accessToken, fields: "message,link,full_picture,created_time,comments.limit(1).summary(true),likes.limit(1).summary(true)", format: "json" }});

            $q.all([postsCall]).then(function(result) {

                $scope.posts = result[0].data.data;
                $scope.paging = result[0].data.paging.next;
                //console.log("RESULT : " + JSON.stringify(paging,null,4));

                $timeout(function() {
                    ionicMaterialMotion.fadeSlideIn({
                        selector: '.animate-fade-slide-in .item'
                    });
                    ionicMaterialInk.displayEffect();
                }, 600);
            })

        }, function(error) {
            console.log("--- Feed Error ---");
            alert("There was a problem getting your data.  Check the logs for details.");
            console.log(error);
        });
      }
    }

    $scope.doOpen = function(url){
        window.open(url, '_blank', 'location=no');
    };

    $scope.doRefresh = function() {
      $scope.paging = "";
      $scope.loadFeed();
      $scope.$broadcast('scroll.refreshComplete');
    }

    $scope.init = function() {

        if($localStorage.hasOwnProperty("accessToken") == true) {

            $scope.$parent.showHeader();
            $scope.$parent.clearFabs();
            $scope.isExpanded = false;
            $scope.$parent.setExpanded(false);
            $scope.$parent.setHeaderFab(false);
            $scope.loadFeed();

        }
        else {
            $state.go('app.login');
        }
    };
})
;
