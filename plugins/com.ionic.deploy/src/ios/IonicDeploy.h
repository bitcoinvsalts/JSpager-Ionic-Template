#import <Cordova/CDV.h>
#import "DownloadManager.h"
#import "SSZipArchive.h"

//@interface IonicDeploy : CDVPlugin <NSURLConnectionDataDelegate>
@interface IonicDeploy : CDVPlugin <DownloadManagerDelegate, SSZipArchiveDelegate>

@property (strong, nonatomic) DownloadManager *downloadManager;


- (void) check:(CDVInvokedUrlCommand *)command;

- (void) download:(CDVInvokedUrlCommand *)command;

- (void) extract:(CDVInvokedUrlCommand *)command;

- (void) redirect:(CDVInvokedUrlCommand *)command;

- (struct JsonHttpResponse) httpRequest:(NSString *) endpoint;

- (void) doRedirect;

- (NSMutableArray *) getMyVersions;

- (bool) hasVersion:(NSString *) uuid;

- (void) saveVersion:(NSString *) uuid;

- (void) cleanupVersions;

- (void) removeVersion:(NSString *) uuid;

@end

