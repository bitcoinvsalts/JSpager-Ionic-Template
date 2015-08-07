package com.ionic.deploy;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;
import android.content.res.AssetManager;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

class JsonHttpResponse {
    String message;
    JSONObject json;
}

public class IonicDeploy extends CordovaPlugin {
    String server = "https://apps.ionic.io";
    Context myContext = null;
    String app_id = null;
    boolean debug = true;
    SharedPreferences prefs = null;
    CordovaWebView v = null;

    /**
     * Sets the context of the Command. This can then be used to do things like
     * get file paths associated with the Activity.
     *
     * @param cordova The context of the main Activity.
     * @param webView The CordovaWebView Cordova is running in.
     */
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        this.myContext = this.cordova.getActivity().getApplicationContext();
        this.prefs = getPreferences();
        this.v = webView;
    }

    public Object onMessage(String id, Object data) {
        boolean is_nothing = "file:///".equals(String.valueOf(data));
        boolean is_index = "file:///android_asset/www/index.html".equals(String.valueOf(data));
        boolean is_original = (is_nothing || is_index) ? true : false;

        if("onPageStarted".equals(id) && is_original) {
            final String uuid = prefs.getString("uuid", "NO_DEPLOY_AVAILABLE");

            if(!"NO_DEPLOY_AVAILABLE".equals(uuid)) {
                logMessage("LOAD", "Init Deploy Version");
                this.redirect(uuid, false);
            }
        }
        return null;
    }

    /**
     * Executes the request and returns PluginResult.
     *
     * @param action            The action to execute.
     * @param args              JSONArry of arguments for the plugin.
     * @param callbackContext   The callback id used when calling back into JavaScript.
     * @return                  True if the action was valid, false if not.
     */
    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {

        this.app_id = args.getString(0);
        this.prefs = getPreferences();

        initApp(args.getString(0));
        
        final SharedPreferences prefs = this.prefs;

        if (action.equals("initialize")) {
            // No need to do anything here.
            return true;
        } else if (action.equals("check")) {
            logMessage("CHECK", "Checking for updates");
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    checkForUpdates(callbackContext);
                }
            });
            return true;
        } else if (action.equals("download")) {
            logMessage("DOWNLOAD", "Downloading updates");
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    downloadUpdate(callbackContext);
                }
            });
            return true;
        } else if (action.equals("extract")) {
            logMessage("EXTRACT", "Extracting update");
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    String uuid = prefs.getString("uuid", "");
                    unzip("www.zip", uuid, callbackContext);
                }
            });
            return true;
        } else if (action.equals("redirect")) {
            final String uuid = prefs.getString("uuid", "");
            this.redirect(uuid, true);
            return true;
        } else {
            return false;
        }
    }

    private void initApp(String app_id) {
        this.app_id = app_id;
        SharedPreferences prefs = this.prefs;

        prefs.edit().putString("app_id", this.app_id).apply();
        // Used for keeping track of the order versions were downloaded
        int version_count = prefs.getInt("version_count", 0);
        prefs.edit().putInt("version_count", version_count).apply();
    }

    private void checkForUpdates(CallbackContext callbackContext) {
        String endpoint = "/api/v1/app/" + this.app_id + "/updates/check";

        // Request shared preferences for this app id
        // Also, is there a way to pull the package name and fill it in on build?
        SharedPreferences prefs = this.prefs;

        String our_version = prefs.getString("uuid", "");

        JsonHttpResponse response = httpRequest(endpoint);

        try {
          logMessage("RESP", "Response JSON: " + response.json);

          if (response.json != null) {
              String deployed_version = response.json.getString("uuid");

              prefs.edit().putString("upstream_uuid", deployed_version).apply();

              Boolean updatesAvailable = !deployed_version.equals(our_version);

              callbackContext.success(updatesAvailable.toString());
          }
        } catch (JSONException e) {
          callbackContext.error("Error checking for updates.");
        }

        callbackContext.error(response.message);

    }

    private void downloadUpdate(CallbackContext callbackContext) {
        String endpoint = "/api/v1/app/" + this.app_id + "/updates/download";

        // First, let's check to see if we have the upstream version already
        SharedPreferences prefs = this.prefs;

        String upstream_uuid = prefs.getString("upstream_uuid", "");

        if (upstream_uuid != "" && this.hasVersion(upstream_uuid)) {
            // Set the current version to the upstream uuid
            prefs.edit().putString("uuid", upstream_uuid).apply();
            callbackContext.success("false");
        } else {
            try {
                JsonHttpResponse response = httpRequest(endpoint);

                if (response.json != null) {
                    String url = response.json.getString("download_url");

                    final DownloadTask downloadTask = new DownloadTask(this.myContext, callbackContext);

                    downloadTask.execute(url);
                }
            } catch (JSONException e) {
                callbackContext.error("Error starting download");
            }
        }
    }

    /**
     * Get a list of versions that have been downloaded
     *
     * @return
     */
    private Set<String> getMyVersions() {
        SharedPreferences prefs = this.prefs;

        return prefs.getStringSet("my_versions", new HashSet<String>());
    }

    /**
     * Check to see if we already have the version to be downloaded
     *
     * @param uuid
     * @return
     */
    private boolean hasVersion(String uuid) {
        Set<String> versions = this.getMyVersions();

        logMessage("HASVER", "Checking " + uuid + "...");
        for (String version : versions) {
            String[] version_string = version.split("\\|");
            logMessage("HASVER", version_string[0] + " == " + uuid);
            if (version_string[0].equals(uuid)) {
                logMessage("HASVER", "Yes");
                return true;
            }
        }

        logMessage("HASVER", "No");
        return false;
    }

    /**
     * Save a new version string to our list of versions
     *
     * @param uuid
     */
    private void saveVersion(String uuid) {
        SharedPreferences prefs = this.prefs;

        Integer version_count = prefs.getInt("version_count", 0) + 1;
        prefs.edit().putInt("version_count", version_count).apply();

        uuid = uuid + "|" + version_count.toString();

        Set<String> versions = this.getMyVersions();

        versions.add(uuid);

        prefs.edit().putStringSet("my_versions", versions).apply();

        this.cleanupVersions();
    }

    private void cleanupVersions() {
        // Let's keep 5 versions around for now
        SharedPreferences prefs = this.prefs;

        int version_count = prefs.getInt("version_count", 0);
        Set<String> versions = this.getMyVersions();

        if (version_count > 3) {
            int threshold = version_count - 3;

            for (Iterator<String> i = versions.iterator(); i.hasNext();) {
                String version = i.next();
                String[] version_string = version.split("\\|");
                logMessage("VERSION", version);
                int version_number = Integer.parseInt(version_string[1]);
                if (version_number < threshold) {
                    logMessage("REMOVING", version);
                    i.remove();
                    removeVersion(version_string[0]);
                }
            }

            Integer version_c = versions.size();
            logMessage("VERSIONCOUNT", version_c.toString());
            prefs.edit().putStringSet("my_versions", versions).apply();
        }
    }

    /**
     * Ugly, lazy bit of code to whack old version directories...
     *
     * @param uuid
     */
    private void removeVersion(String uuid) {
        File versionDir = this.myContext.getDir(uuid, Context.MODE_PRIVATE);

        if (versionDir.exists()) {
            String deleteCmd = "rm -r " + versionDir.getAbsolutePath();
            Runtime runtime = Runtime.getRuntime();
            try {
                runtime.exec(deleteCmd);
            } catch (IOException e) {
                logMessage("REMOVE", "Failed to remove " + uuid + ". Error: " + e.getMessage());
            }
        }
    }

    private JsonHttpResponse httpRequest(String endpoint) {
        HttpURLConnection urlConnection = null;
        JsonHttpResponse response = new JsonHttpResponse();

        try {
            URL url = new URL(this.server + endpoint);
            urlConnection = (HttpURLConnection) url.openConnection();

            InputStream in = new BufferedInputStream(urlConnection.getInputStream());
            String result = readStream(in);

            JSONObject json = new JSONObject(result);

            response.json = json;
        } catch (JSONException e) {
            response.message = "Invalid server response";
        } catch (MalformedURLException e) {
            response.message = "Malformed URL";
        } catch (IOException e) {
            response.message = "IO Exception";
        } finally {
            urlConnection.disconnect();
        }

        if(response.message != null) {
            logMessage("HTTPR", "Message: " + response.message);
        }

        return response;
    }

    private SharedPreferences getPreferences() {
        // Request shared preferences for this app id
        SharedPreferences prefs = this.myContext.getSharedPreferences(
                "com.ionic.deploy.preferences", Context.MODE_PRIVATE
        );

        return prefs;
    }

    private String readStream(InputStream is) {
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            int i = is.read();
            while(i != -1) {
                bo.write(i);
                i = is.read();
            }
            return bo.toString();
        } catch (IOException e) {
            return "";
        }
    }

    private void logMessage(String tag, String message) {
        if (this.debug == true) {
            Log.i("IONIC.DEPLOY." + tag, message);
        }
    }

    private void copyFile(String src, File dst) throws IOException {
        AssetManager assetManager = this.myContext.getAssets();

        InputStream in = assetManager.open(src);
        OutputStream out = new FileOutputStream(dst);

        // Make sure that the full path for the target file exists
        dst.getParentFile().mkdirs();

        // Transfer bytes from in to out
        byte[] buf = new byte[1024];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        in.close();
        out.close();
    }

    /**
     * Extract the downloaded archive
     *
     * @param zip
     * @param location
     */
    private void unzip(String zip, String location, CallbackContext callbackContext) {
        SharedPreferences prefs = getPreferences();
        String upstream_uuid = prefs.getString("upstream_uuid", "");

        logMessage("UNZIP", upstream_uuid);

        if (upstream_uuid != "" && this.hasVersion(upstream_uuid)) {
            callbackContext.success("done"); // we have already extracted this version
            return;
        }

        try  {
            FileInputStream inputStream = this.myContext.openFileInput(zip);
            ZipInputStream zipInputStream = new ZipInputStream(inputStream);
            ZipEntry zipEntry = null;

            // Something about the way the CLI creates zip files results in the file sizes not being part
            // of the archive, so we can't get extraction progress via file sizes.
            /*Integer finalSize = (int) new File(this.myContext.getFileStreamPath(zip).getAbsolutePath().toString()).length();
            Log.i("FILE_SIZE", finalSize.toString());*/

            // Get the full path to the internal storage
            String filesDir = this.myContext.getFilesDir().toString();

            // Make the version directory in internal storage
            File versionDir = this.myContext.getDir(location, Context.MODE_PRIVATE);

            logMessage("UNZIP_DIR", versionDir.getAbsolutePath().toString());

            // Figure out how many entries are in the zip so we can calculate extraction progress
            ZipFile zipFile = new ZipFile(this.myContext.getFileStreamPath(zip).getAbsolutePath().toString());
            float entries = new Float(zipFile.size());

            logMessage("ENTRIES", "Total: " + (int) entries);

            float extracted = 0.0f;

            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                File newFile = new File(versionDir + "/" + zipEntry.getName());
                newFile.getParentFile().mkdirs();

                byte[] buffer = new byte[2048];

                FileOutputStream fileOutputStream = new FileOutputStream(newFile);
                BufferedOutputStream outputBuffer = new BufferedOutputStream(fileOutputStream, buffer.length);
                int bits;
                while((bits = zipInputStream.read(buffer, 0, buffer.length)) != -1) {
                    outputBuffer.write(buffer, 0, bits);
                }

                zipInputStream.closeEntry();
                outputBuffer.flush();
                outputBuffer.close();

                extracted += 1;

                float progress = (extracted / entries) * new Float("100.0f");
                logMessage("EXTRACT", "Progress: " + (int) progress + "%");

                PluginResult progressResult = new PluginResult(PluginResult.Status.OK, (int) progress);
                progressResult.setKeepCallback(true);
                callbackContext.sendPluginResult(progressResult);
            }
            zipInputStream.close();

            // We also need to copy cordova.js from the binary www directory so the plugins work
            //copyFile("www/cordova.js", new File(versionDir.getAbsolutePath().toString() + "/cordova.js"));
            //copyFile("www/cordova_plugins.js", new File(versionDir.getAbsolutePath().toString() + "/cordova_plugins.js"));

        } catch(Exception e) {
            //TODO Handle problems..
            logMessage("UNZIP_STEP", "Exception: " + e.getMessage());
        }

        // Save the version we just downloaded as a version on hand
        saveVersion(upstream_uuid);

        String wwwFile = this.myContext.getFileStreamPath(zip).getAbsolutePath().toString();
        if (this.myContext.getFileStreamPath(zip).exists()) {
            String deleteCmd = "rm -r " + wwwFile;
            Runtime runtime = Runtime.getRuntime();
            try {
                runtime.exec(deleteCmd);
                logMessage("REMOVE", "Removed www.zip");
            } catch (IOException e) {
                logMessage("REMOVE", "Failed to remove " + wwwFile + ". Error: " + e.getMessage());
            }
        }

        callbackContext.success("done");
    }


    private void redirect(final String uuid, final boolean recreatePlugins) {
        if (!uuid.equals("")) {
            prefs.edit().putString("uuid", uuid).apply();
            final File versionDir = this.myContext.getDir(uuid, Context.MODE_PRIVATE);
            final String deploy_url = versionDir.toURI() + "index.html";

            cordova.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    logMessage("REDIRECT", "Loading deploy version: " + uuid);
                    prefs.edit().putString("loaded_uuid", uuid).apply();
                    webView.loadUrlIntoView(deploy_url, recreatePlugins);
                }
            });
        }
    }

    private class DownloadTask extends AsyncTask<String, Integer, String> {
        private Context myContext;
        private CallbackContext callbackContext;

        public DownloadTask(Context context, CallbackContext callbackContext) {
            this.myContext = context;
            this.callbackContext = callbackContext;
        }

        @Override
        protected String doInBackground(String... sUrl) {
            InputStream input = null;
            FileOutputStream output = null;
            HttpURLConnection connection = null;
            try {
                URL url = new URL(sUrl[0]);
                connection = (HttpURLConnection) url.openConnection();
                connection.connect();

                // expect HTTP 200 OK, so we don't mistakenly save error report
                // instead of the file
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    return "Server returned HTTP " + connection.getResponseCode()
                            + " " + connection.getResponseMessage();
                }

                // this will be useful to display download percentage
                // might be -1: server did not report the length
                float fileLength = new Float(connection.getContentLength());

                logMessage("DOWNLOAD", "File size: " + fileLength);

                // download the file
                input = connection.getInputStream();
                output = this.myContext.openFileOutput("www.zip", Context.MODE_PRIVATE);

                byte data[] = new byte[4096];
                float total = 0;
                int count;
                while ((count = input.read(data)) != -1) {
                    total += count;

                    output.write(data, 0, count);

                    // Send the current download progress to a callback
                    if (fileLength > 0) {
                        float progress = (total / fileLength) * new Float("100.0f");
                        logMessage("DOWNLOAD", "Progress: " + (int) progress + "%");
                        PluginResult progressResult = new PluginResult(PluginResult.Status.OK, (int) progress);
                        progressResult.setKeepCallback(true);
                        callbackContext.sendPluginResult(progressResult);
                    }
                }
            } catch (Exception e) {
                callbackContext.error("Something failed with the download...");
                return e.toString();
            } finally {
                try {
                    if (output != null)
                        output.close();
                    if (input != null)
                        input.close();
                } catch (IOException ignored) {
                }

                if (connection != null)
                    connection.disconnect();
            }

            // Request shared preferences for this app id
            SharedPreferences prefs = getPreferences();

            // Set the saved uuid to the most recently acquired upstream_uuid
            String uuid = prefs.getString("upstream_uuid", "");

            prefs.edit().putString("uuid", uuid).apply();

            callbackContext.success("true");
            return null;
        }
    }
}
