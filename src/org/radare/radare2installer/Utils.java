/*
radare2 installer for Android
(c) 2012 Pau Oliva Fora <pof[at]eslack[dot]org>
    2015 pancake <pancake[at]nopcode[dot]org>
*/
package org.radare.radare2installer;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.security.NoSuchAlgorithmException;
import java.security.KeyManagementException;
import java.io.IOException;
import java.net.ProtocolException;


import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.widget.*;

import android.view.LayoutInflater;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.widget.ImageView;
import android.widget.TextView;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;

import android.os.Build;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import android.content.Intent;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;

import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import android.os.Environment;

import android.os.StatFs;

import com.stericson.RootTools.*;

public class Utils {

	private Context mContext;
	public static String PKGNAME;

	public Utils(Context context) {
		mContext = context;
		PKGNAME = mContext.getApplicationContext().getPackageName();
	}

	public boolean isInstalled() {
		// return RootTools.exists("/data/data/" + PKGNAME + "/radare2/bin/radare2");
		return (new File("/data/data/" + PKGNAME + "/radare2/bin/radare2")).exists();
	}

	public boolean isAppInstalled(String namespace) {
		try {
			ApplicationInfo info = mContext.getPackageManager().getApplicationInfo(namespace, 0 );
			return true;
		} catch (PackageManager.NameNotFoundException e) {
			return false;
		}
	}

	public void myToast(String myMsg, int myDuration) {
		LayoutInflater inflater = (LayoutInflater) mContext.getSystemService( Context.LAYOUT_INFLATER_SERVICE );
		//LayoutInflater inflater = getLayoutInflater();
		View layout = inflater.inflate(R.layout.toast_layout, null);

		ImageView image = (ImageView) layout.findViewById(R.id.image);
		image.setImageResource(R.drawable.icon);
		TextView text = (TextView) layout.findViewById(R.id.text);
		text.setText(myMsg);

		Toast toast = new Toast(mContext);
		toast.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
		toast.setDuration(myDuration);
		toast.setView(layout);
		toast.show();
	}

	// store a Key-Value string in preferences
	public void StorePref(String Key, String Value) {
		//SharedPreferences settings = mContext.getSharedPreferences("radare-installer-preferences", mContext.MODE_PRIVATE);
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mContext);
		SharedPreferences.Editor editor = settings.edit();
		editor.putString(Key,Value);
		editor.commit();
	}

	// get the String value from key in preferences, returns unknown if not set
	public String GetPref(String Key) {
		//SharedPreferences settings = mContext.getSharedPreferences("radare-installer-preferences", mContext.MODE_PRIVATE);
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mContext);
		String version = settings.getString(Key, "unknown");
		return version;
	}

	public String GetStoragePath() {
		String storagePath = "";
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mContext);
		boolean use_sdcard = settings.getBoolean("use_sdcard", false);
		if (use_sdcard) {
			File sdCard = Environment.getExternalStorageDirectory();
			storagePath = sdCard.getAbsolutePath() + "/" + PKGNAME + "/";
		} else {
			//storagePath = "/data/data/org.radare.radare2installer/";
			storagePath = mContext.getApplicationInfo().dataDir;
		}
		return storagePath;
	}

	public long getFreeSpace(String partition) {
		try {
			StatFs stat = new StatFs(partition);
			long blockSizeInBytes = stat.getBlockSize();
			long availableBlocks = stat.getAvailableBlocks();
			long freeSpaceInBytes = availableBlocks * blockSizeInBytes;
			return freeSpaceInBytes;
		} catch (Exception e) {
			e.printStackTrace(); 
			return 0;
		}
	}

	public String GetArch() {
		String arch = "arm";
		String cpuabi = Build.CPU_ABI;

		if (cpuabi.matches(".*mips64.*")) arch= "mips64";
		else if (cpuabi.matches(".*mips.*")) arch = "mips";
		else if (cpuabi.matches(".*x86.*")) arch = "x86";
		else if (cpuabi.matches(".*arm64.*")) arch = "aarch64";
		else if (cpuabi.matches(".*arm.*")) arch = "arm";
		return arch;
	}

	public final boolean isInternetAvailable(){
	// check if we are connected to the internet
		ConnectivityManager connectivityManager = (ConnectivityManager)mContext.getSystemService(mContext.CONNECTIVITY_SERVICE);
		NetworkInfo info = connectivityManager.getActiveNetworkInfo();
		if (info == null)
			return false;

		return connectivityManager.getActiveNetworkInfo().isConnected();
	}

	private boolean updateCheckInsecure(String urlStr) {
		boolean update = false;
		try {
			URL url = new URL(urlStr);
			HttpURLConnection urlconn = (HttpURLConnection)url.openConnection();
			urlconn.setRequestMethod("GET");
			urlconn.setInstanceFollowRedirects(true);
			urlconn.getRequestProperties();
			/* 20 seconds connect timeout */
			urlconn.setConnectTimeout(20000);
			urlconn.connect();

			String etag = urlconn.getHeaderField("ETag");
			urlconn.disconnect();
			String ETag = GetPref("ETag");
			if (!ETag.equals(etag)) {
				update = true;
				//if (etag.equals("\"3401f-1fba4e-4c8c2d3f37100\"")) update = false; // for my tests (git)
				//if (etag.equals("\"1a6f2-affb8c-4b366f9fd3640\"")) update = false; // for my tests (stable)
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return update;
	}

	public HttpsURLConnection getGithubConnection(String path) throws
			NoSuchAlgorithmException,
			KeyManagementException,
			MalformedURLException,
			IOException,
			ProtocolException {
		String arch = this.GetArch();
		String http_url = "https://raw.githubusercontent.com/radare/radare2-bin";
		String urlStr = http_url + "/android-" + arch + "/" + path;

		TrustManager tm[] = { new PubKeyManager() };
		SSLContext context = SSLContext.getInstance("TLS");
		context.init (null, tm, null);

		URL url = new URL(urlStr);
		HttpsURLConnection urlconn = (HttpsURLConnection)url.openConnection();
		urlconn.setSSLSocketFactory(context.getSocketFactory());

		urlconn.setRequestMethod("GET");
		urlconn.setInstanceFollowRedirects(true);
		urlconn.getRequestProperties();
		/* 20 seconds connect timeout */
		urlconn.setConnectTimeout(20000);
		return urlconn;
	}

	private boolean updateCheckGithub() {
		boolean update = false;
		try {
			HttpsURLConnection urlconn = getGithubConnection("README.md");
			urlconn.connect();
			String etag = urlconn.getHeaderField("ETag");
			urlconn.disconnect();
			String last_etag = GetPref("ETag");
			update = !etag.equals(last_etag);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return update;
	}

	public boolean UpdateCheck(String urlStr, boolean useGithub) {
		if (useGithub) {
			return updateCheckGithub ();
		}
		return updateCheckInsecure (urlStr);
	}

	public String getGithubREADME() {
		try {
			HttpsURLConnection urlconn = getGithubConnection("README.md");
			// 20 seconds connect timeout 
			BufferedReader br = null;
			if (urlconn.getResponseCode() != 400) {
				br = new BufferedReader(new InputStreamReader((urlconn.getInputStream())));
				String output;
				StringBuilder builder = new StringBuilder();
				System.out.println("Output from Server .... \n");
				while ((output = br.readLine()) != null) 
					builder.append(output+"\n");
				return builder.toString();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public void SendNotification(String title, String message) {
		NotificationManager nm = (NotificationManager) mContext.getSystemService(mContext.NOTIFICATION_SERVICE);

		int icon = R.drawable.icon;
		CharSequence tickerText = title;
		long when = System.currentTimeMillis();
		Notification notification = new Notification( icon, tickerText, when);
		notification.flags |= Notification.FLAG_AUTO_CANCEL;

		Context context = mContext.getApplicationContext();
		CharSequence contentTitle = title;
		CharSequence contentText = message;
		Intent notificationIntent = new Intent(mContext, MainActivity.class);
		PendingIntent contentIntent = PendingIntent.getActivity(mContext, 0, notificationIntent, 0);

		notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);

		//  Send the notification
		nm.notify( 1, notification );
	}

	public String exec(String command) {
		final StringBuffer radare_output = new StringBuffer();
		Command command_out = new Command(0, command)
		{
			@Override
			public void output(int id, String line)
			{
				radare_output.append(line + "\n");
			}
		};
		try {
			RootTools.getShell(RootTools.useRoot);
			RootTools.getShell(RootTools.useRoot).add(command_out).waitForFinish();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return radare_output.toString();
	}

	public void killradare() {
		RootTools.useRoot = false;
		if (RootTools.isProcessRunning("bin/radare2")) {
			RootTools.killProcess("bin/radare2");
		}
	}

	public void sleep(int secs) {
		try {
                        Thread.sleep(secs * 1000);
                } catch (Exception e) {
                        e.printStackTrace();
                }
	}
}
