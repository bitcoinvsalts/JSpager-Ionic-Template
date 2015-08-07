#import "IonicDeploy.h"
#import <Cordova/CDV.h>
#import "UNIRest.h"
#import "SSZipArchive.h"

@interface IonicDeploy()

@property (nonatomic) NSURLConnection *connectionManager;
@property (nonatomic) NSMutableData *downloadedMutableData;
@property (nonatomic) NSURLResponse *urlResponse;

@property int progress;
@property NSString *callbackId;
@property NSString *appId;
@property NSString *currentUUID;
@property dispatch_queue_t serialQueue;
@property NSString *cordova_js_resource;

@end

static NSOperationQueue *delegateQueue;

typedef struct JsonHttpResponse {
    __unsafe_unretained NSString *message;
    __unsafe_unretained NSDictionary *json;
} JsonHttpResponse;

@implementation IonicDeploy

- (void) pluginInitialize {
    self.cordova_js_resource = [[NSBundle mainBundle] pathForResource:@"www/cordova" ofType:@"js"];
    self.serialQueue = dispatch_queue_create("Deploy Plugin Queue", NULL);
}

- (void)onReset {
    // redirect to latest deploy
    [self doRedirect];
}

- (void) check:(CDVInvokedUrlCommand *)command {
    self.appId = [command.arguments objectAtIndex:0];

    if([self.appId isEqual: @"YOUR_APP_ID"]) {
        [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Please set your app id in app.js for YOUR_APP_ID before using $ionicDeploy"] callbackId:command.callbackId];
        return;
    }

    dispatch_async(self.serialQueue, ^{
        CDVPluginResult* pluginResult = nil;

        NSUserDefaults *prefs = [NSUserDefaults standardUserDefaults];

        NSString *our_version = [[NSUserDefaults standardUserDefaults] objectForKey:@"uuid"];

        NSString *endpoint = [NSString stringWithFormat:@"/api/v1/app/%@/updates/check", self.appId];

        JsonHttpResponse result = [self httpRequest:endpoint];

        NSLog(@"Response: %@", result.message);

        if (result.json != nil && [result.json objectForKey:@"is_first"]) {
            NSString *uuid = [result.json objectForKey:@"uuid"];

            // Save the "deployed" UUID so we can fetch it later
            [prefs setObject: uuid forKey: @"upstream_uuid"];
            [prefs synchronize];

            NSNumber *isFirst = [result.json objectForKey:@"is_first"];

            NSString *updatesAvailable = !isFirst.boolValue && ![uuid isEqualToString:our_version] ? @"true" : @"false";

            NSLog(@"UUID: %@ OUR_UUID: %@", uuid, our_version);
            NSLog(@"Updates Available: %@", updatesAvailable);
            NSLog(@"Is first?: %@", isFirst);
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:updatesAvailable];
        } else if (result.json != nil && [result.json objectForKey:@"uuid"]) {
            NSString *uuid = [result.json objectForKey:@"uuid"];

            // Save the "deployed" UUID so we can fetch it later
            [prefs setObject: uuid forKey: @"upstream_uuid"];
            [prefs synchronize];

            NSString *updatesAvailable = ![uuid isEqualToString:our_version] ? @"true" : @"false";

            NSLog(@"UUID: %@ OUR_UUID: %@", uuid, our_version);
            NSLog(@"Updates Available: %@", updatesAvailable);

            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:updatesAvailable];
        } else {
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:result.message];
        }

        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];

    });
}

- (void) download:(CDVInvokedUrlCommand *)command {
    self.appId = [command.arguments objectAtIndex:0];

    dispatch_async(self.serialQueue, ^{
        // Save this to a property so we can have the download progress delegate thing send
        // progress update callbacks
        self.callbackId = command.callbackId;

        NSString *endpoint = [NSString stringWithFormat:@"/api/v1/app/%@/updates/download", self.appId];

        NSUserDefaults *prefs = [NSUserDefaults standardUserDefaults];

        NSString *upstream_uuid = [[NSUserDefaults standardUserDefaults] objectForKey:@"upstream_uuid"];

        NSLog(@"Upstream UUID: %@", upstream_uuid);

        if (upstream_uuid != nil && [self hasVersion:upstream_uuid]) {
            // Set the current version to the upstream version (we already have this version)
            [prefs setObject:upstream_uuid forKey:@"uuid"];
            [prefs synchronize];

            [self doRedirect];
        } else {
            JsonHttpResponse result = [self httpRequest:endpoint];

            NSString *download_url = [result.json objectForKey:@"download_url"];

            self.downloadManager = [[DownloadManager alloc] initWithDelegate:self];

            NSURL *url = [NSURL URLWithString:download_url];

            NSArray *paths = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, YES);
            NSString *libraryDirectory = [paths objectAtIndex:0];
            NSString *filePath = [NSString stringWithFormat:@"%@/%@", libraryDirectory,@"www.zip"];

            NSLog(@"Queueing Download...");
            [self.downloadManager addDownloadWithFilename:filePath URL:url];
        }
    });
}

- (void) extract:(CDVInvokedUrlCommand *)command {
    self.appId = [command.arguments objectAtIndex:0];

    dispatch_async(self.serialQueue, ^{
        self.callbackId = command.callbackId;

        NSArray *paths = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, YES);
        NSString *libraryDirectory = [paths objectAtIndex:0];

        NSString *uuid = [[NSUserDefaults standardUserDefaults] objectForKey:@"uuid"];

        NSString *filePath = [NSString stringWithFormat:@"%@/%@", libraryDirectory, @"www.zip"];
        NSString *extractPath = [NSString stringWithFormat:@"%@/%@/", libraryDirectory, uuid];

        NSLog(@"Path for zip file: %@", filePath);

        NSLog(@"Unzipping...");

        [SSZipArchive unzipFileAtPath:filePath toDestination:extractPath delegate:self];
        BOOL success = [[NSFileManager defaultManager] removeItemAtPath:filePath error:nil];
        NSLog(@"Unzipped...");
        NSLog(@"Removing www.zip %d", success);
    });
}

- (void) redirect:(CDVInvokedUrlCommand *)command {
    self.appId = [command.arguments objectAtIndex:0];

    CDVPluginResult* pluginResult = nil;

    [self doRedirect];

    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}


- (void) doRedirect {
    NSString *uuid = [[NSUserDefaults standardUserDefaults] objectForKey:@"uuid"];

    dispatch_async(self.serialQueue, ^{
    if ( uuid != nil && ![self.currentUUID isEqualToString: uuid] ) {
        NSArray *paths = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, YES);
        NSString *libraryDirectory = [paths objectAtIndex:0];


        NSString *query = [NSString stringWithFormat:@"cordova_js_bootstrap_resource=%@", self.cordova_js_resource];
        
        NSURLComponents *components = [NSURLComponents new];
        components.scheme = @"file";
        components.path = [NSString stringWithFormat:@"%@/%@/index.html", libraryDirectory, uuid];
        components.query = query;

        self.currentUUID = uuid;

        NSLog(@"Redirecting to: %@", components.URL.absoluteString);
        [self.webView loadRequest: [NSURLRequest requestWithURL:components.URL] ];
    }
    });
}

- (struct JsonHttpResponse) httpRequest:(NSString *) endpoint {
    NSString *baseUrl = @"https://apps.ionic.io";
    NSString *url = [NSString stringWithFormat:@"%@%@", baseUrl, endpoint];

    NSDictionary* headers = @{@"accept": @"application/json"};

    UNIHTTPJsonResponse* result = [[UNIRest get:^(UNISimpleRequest *request) {
        [request setUrl: url];
        [request setHeaders:headers];
    }] asJson];

    JsonHttpResponse response;

    NSError *jsonError = nil;

    @try {
        response.message = nil;
        response.json = [NSJSONSerialization JSONObjectWithData:result.rawBody options:kNilOptions error:&jsonError];
    }
    @catch (NSException *exception) {
        response.message = exception.reason;
        NSLog(@"JSON Error: %@", jsonError);
        NSLog(@"Exception: %@", exception.reason);
    }
    @finally {
        NSLog(@"In Finally");
        NSLog(@"JSON Error: %@", jsonError);

        if (jsonError != nil) {
            response.message = [NSString stringWithFormat:@"%@", [jsonError localizedDescription]];
            response.json = nil;
        }
    }

    NSLog(@"Returing?");
    return response;
}

- (NSMutableArray *) getMyVersions {
    NSMutableArray *versions;
    NSArray *versionsLoaded = [[NSUserDefaults standardUserDefaults] arrayForKey:@"my_versions"];
    if (versionsLoaded != nil) {
        versions = [versionsLoaded mutableCopy];
    } else {
        versions = [[NSMutableArray alloc] initWithCapacity:5];
    }

    return versions;
}

- (bool) hasVersion:(NSString *) uuid {
    NSArray *versions = [self getMyVersions];

    NSLog(@"Versions: %@", versions);

    for (id version in versions) {
        NSArray *version_parts = [version componentsSeparatedByString:@"|"];
        NSString *version_uuid = version_parts[1];

        NSLog(@"version_uuid: %@, uuid: %@", version_uuid, uuid);
        if ([version_uuid isEqualToString:uuid]) {
            return true;
        }
    }

    return false;
}

- (void) saveVersion:(NSString *) uuid {
    NSUserDefaults *prefs = [NSUserDefaults standardUserDefaults];
    NSMutableArray *versions = [self getMyVersions];

    int versionCount = (int) [[NSUserDefaults standardUserDefaults] integerForKey:@"version_count"];

    if (versionCount) {
        versionCount += 1;
    } else {
        versionCount = 1;
    }

    [prefs setInteger:versionCount forKey:@"version_count"];
    [prefs synchronize];

    NSString *versionString = [NSString stringWithFormat:@"%i|%@", versionCount, uuid];

    [versions addObject:versionString];

    [prefs setObject:versions forKey:@"my_versions"];
    [prefs synchronize];

    [self cleanupVersions];
}

- (void) cleanupVersions {
    NSUserDefaults *prefs = [NSUserDefaults standardUserDefaults];
    NSMutableArray *versions = [self getMyVersions];

    int versionCount = (int) [[NSUserDefaults standardUserDefaults] integerForKey:@"version_count"];

    if (versionCount && versionCount > 3) {
        NSInteger threshold = versionCount - 3;

        NSInteger count = [versions count];
        for (NSInteger index = (count - 1); index >= 0; index--) {
            NSString *versionString = versions[index];
            NSArray *version_parts = [versionString componentsSeparatedByString:@"|"];
            NSInteger version_number = [version_parts[0] intValue];
            if (version_number < threshold) {
                [versions removeObjectAtIndex:index];
                [self removeVersion:version_parts[1]];
            }
        }

        NSLog(@"Version Count: %i", (int) [versions count]);
        [prefs setObject:versions forKey:@"my_versions"];
        [prefs synchronize];
    }
}

- (void) removeVersion:(NSString *) uuid {
    NSArray *paths = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, YES);
    NSString *libraryDirectory = [paths objectAtIndex:0];

    NSString *pathToFolder = [NSString stringWithFormat:@"%@/%@/", libraryDirectory, uuid];

    BOOL success = [[NSFileManager defaultManager] removeItemAtPath:pathToFolder error:nil];

    NSLog(@"Removed Version %@ success? %d", uuid, success);
}

/* Delegate Methods for the DownloadManager */

- (void)downloadManager:(DownloadManager *)downloadManager downloadDidReceiveData:(Download *)download;
{
    // download failed
    // filename is retrieved from `download.filename`
    // the bytes downloaded thus far is `download.progressContentLength`
    // if the server reported the size of the file, it is returned by `download.expectedContentLength`

    self.progress = ((100.0 / download.expectedContentLength) * download.progressContentLength);

    NSLog(@"Download Progress: %.0f%%", ((100.0 / download.expectedContentLength) * download.progressContentLength));

    CDVPluginResult* pluginResult = nil;

    pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsInt:self.progress];
    [pluginResult setKeepCallbackAsBool:TRUE];

    [self.commandDelegate sendPluginResult:pluginResult callbackId:self.callbackId];
}

- (void)didFinishLoadingAllForManager:(DownloadManager *)downloadManager
{
    // Save the upstream_uuid (what we just downloaded) to the uuid preference
    NSUserDefaults *prefs = [NSUserDefaults standardUserDefaults];
    NSString *uuid = [[NSUserDefaults standardUserDefaults] objectForKey:@"uuid"];
    NSString *upstream_uuid = [[NSUserDefaults standardUserDefaults] objectForKey:@"upstream_uuid"];

    [prefs setObject: upstream_uuid forKey: @"uuid"];
    [prefs synchronize];

    NSLog(@"UUID is: %@ and upstream_uuid is: %@", uuid, upstream_uuid);

    [self saveVersion:upstream_uuid];

    NSLog(@"Download Finished...");
    CDVPluginResult* pluginResult = nil;
    pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"true"];

    [self.commandDelegate sendPluginResult:pluginResult callbackId:self.callbackId];
}

/* Delegate Methods for SSZipArchive */

- (void)zipArchiveProgressEvent:(NSInteger)loaded total:(NSInteger)total {
    float progress = ((100.0 / total) * loaded);
    NSLog(@"Zip Extraction: %.0f%%", progress);

    CDVPluginResult* pluginResult = nil;

    pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsInt:progress];
    [pluginResult setKeepCallbackAsBool:TRUE];

    [self.commandDelegate sendPluginResult:pluginResult callbackId:self.callbackId];

    if (progress == 100) {
        CDVPluginResult* pluginResult = nil;

        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"done"];

        [self.commandDelegate sendPluginResult:pluginResult callbackId:self.callbackId];
    }
}

@end
