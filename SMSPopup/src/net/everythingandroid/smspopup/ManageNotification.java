package net.everythingandroid.smspopup;

import java.util.ArrayList;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.AudioManager;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.Settings;

/*
 * This class handles the Notifications (sounds/vibrate/LED)
 */
public class ManageNotification {
	public static final int NOTIFICATION_ALERT = 1337;
	public static final int NOTIFICATION_TEST = 888;
	private static NotificationManager myNM = null;
	private static SharedPreferences myPrefs = null;
	
	// TODO: make LED blink pattern configurable?
	private static final int[] led_pattern = { 1250, 1250 };

	/*
	 * Create the NotificationManager
	 */
	private static synchronized void createNM(Context context) {
		if (myNM == null) {
			myNM = (NotificationManager) context
				.getSystemService(Context.NOTIFICATION_SERVICE);
		}
	}

	/*
	 * Create the PreferenceManager
	 */
	private static synchronized void createPM(Context context) {
		if (myPrefs == null) {
			myPrefs = PreferenceManager.getDefaultSharedPreferences(context);
		}
	}
	
	/*
	 * Show/play the notification given a SmsMmsMessage and a notification ID
	 * (really just NOTIFICATION_ALERT for the main alert and NOTIFICATION_TEST
	 * for the test notification from the preferences screen)
	 */
	public static void show(Context context, SmsMmsMessage message, int notif) {
		notify(context, message, false, notif);
	}

	/*
	 * Default to NOTIFICATION_ALERT if notif is left out
	 */
	public static void show(Context context, SmsMmsMessage message) {
		notify(context, message, false, NOTIFICATION_ALERT);
	}

	/*
	 * Only update the notification given the SmsMmsMessage (ie. do not play
	 * re-play the vibrate/sound, just update the text).
	 */
	public static void update(Context context, SmsMmsMessage message) {
		notify(context, message, true, NOTIFICATION_ALERT);
	}

	/*
	 * The main notfy method, this thing isWAY too long. Needs to be broken up.
	 */
	private static synchronized void notify(Context context, SmsMmsMessage message,
	      boolean onlyUpdate, int notif) {

		// Make sure the PreferenceManager is created
		createPM(context);

		// Check if Notifications are enabled, if not, we're done :)
		if (myPrefs.getBoolean(context.getString(R.string.pref_notif_enabled_key), Boolean
		      .parseBoolean(context.getString(R.string.pref_notif_enabled_default)))) {

			// Make sure the NotificationManager is created
			createNM(context);

			// Get some preferences: vibrate and vibrate_pattern prefs
			boolean vibrate = myPrefs.getBoolean(context.getString(R.string.pref_vibrate_key), Boolean
			      .valueOf(context.getString(R.string.pref_vibrate_default)));
			String vibrate_pattern_raw = myPrefs.getString(context
			      .getString(R.string.pref_vibrate_pattern_key), context
			      .getString(R.string.pref_vibrate_pattern_default));
			String vibrate_pattern_custom_raw = myPrefs.getString(context
			      .getString(R.string.pref_vibrate_pattern_custom_key), context
			      .getString(R.string.pref_vibrate_pattern_default));

			// Get LED preferences
			boolean flashLed = myPrefs.getBoolean(context.getString(R.string.pref_flashled_key),
			      Boolean.valueOf(context.getString(R.string.pref_flashled_default)));
			String flashLedCol = myPrefs.getString(
			      context.getString(R.string.pref_flashled_color_key), context
			            .getString(R.string.pref_flashled_color_default));

			// The default system ringtone
			// ("content://settings/system/notification_sound")
			String defaultRingtone = Settings.System.DEFAULT_NOTIFICATION_URI.toString();

			// Try and parse the user ringtone, use the default if it fails
			Uri alarmSoundURI = Uri.parse(myPrefs.getString(context
			      .getString(R.string.pref_notif_sound_key), defaultRingtone));

			// The notification title, sub-text and text that will scroll
			String contentTitle;
			String contentText;
			String scrollText;
			
			// The default intent when the notification is clicked (Inbox)
			Intent smsIntent = SMSPopupUtils.getSmsIntent();

			// See if user wants some privacy
			boolean privacyMode = myPrefs.getBoolean(context.getString(R.string.pref_privacy_key),
			      Boolean.valueOf(context.getString(R.string.pref_privacy_default)));

			// If we're in privacy mode and the keyguard is on then just display
			// the name of the person, otherwise scroll the name and message
			if (privacyMode && ManageKeyguard.inKeyguardRestrictedInputMode()) {
				scrollText = String.format(context.getString(R.string.notification_scroll_privacy),
				      message.getContactName());
			} else {
				scrollText = String.format(context.getString(R.string.notification_scroll), message
				      .getContactName(),
				      message.getMessageBody());
			}

			// If more than one message waiting ...
			if (message.getUnreadCount() > 1) {
				contentTitle = context.getString(R.string.notification_multiple_title);
				contentText = context.getString(R.string.notification_multiple_text);
				// smsIntent = SMSPopupUtils.getSmsIntent();
			} else { // Else 1 message, set text and intent accordingly
				contentTitle = message.getContactName();
				contentText = message.getMessageBody();
				smsIntent = message.getReplyIntent();
			}

			/*
			 * Ok, let's create our Notification object and set up all its
			 * parameters.
			 */

			// Set the icon, scrolling text and timestamp
			Notification notification =
					new Notification(R.drawable.stat_notify_sms, scrollText, message.getTimestamp());

			// Set auto-cancel flag
			notification.flags = Notification.FLAG_AUTO_CANCEL;
			
			// Set audio stream to ring
			notification.audioStreamType = AudioManager.STREAM_RING;

			// Set up LED pattern and color
			if (flashLed) {
				notification.flags |= Notification.FLAG_SHOW_LIGHTS;
				notification.ledOnMS = led_pattern[0];
				notification.ledOffMS = led_pattern[1];
				int col = Color.parseColor(context.getString(R.string.pref_flashled_color_default));
				try {
					col = Color.parseColor(flashLedCol);
				} catch (IllegalArgumentException e) {
					// No need to do anything here
				}
				notification.ledARGB = col;
			}

			// Set up vibrate pattern
			if (vibrate) {
				long[] vibrate_pattern = null;
				if (context.getString(R.string.pref_vibrate_pattern_custom_val).equals(
				      vibrate_pattern_raw)) {
					vibrate_pattern = parseVibratePattern(vibrate_pattern_custom_raw);
				} else {
					vibrate_pattern = parseVibratePattern(vibrate_pattern_raw);
				}
				if (vibrate_pattern != null) {
					notification.vibrate = vibrate_pattern;
				} else {
					notification.defaults = Notification.DEFAULT_VIBRATE;
				}
			}

			// Notification sound
			notification.sound = alarmSoundURI;

			// Set the PendingIntent if the status message is clicked
			PendingIntent notifIntent = PendingIntent.getActivity(context, 0, smsIntent, 0);

			// Set the messages that show when the status bar is pulled down
			notification.setLatestEventInfo(context, contentTitle, String
			      .format(contentText, message
			      .getUnreadCount()), notifIntent);

			// Set number of events that this notification signifies (unread
			// messages)
			if (message.getUnreadCount() > 1) {
				notification.number = message.getUnreadCount();
			}

			// Set intent to execute if the "clear all" notifications button is
			// pressed -
			// basically stop any future reminders.
			Intent deleteIntent = new Intent(new Intent(context, ReminderReceiver.class));
			deleteIntent.setAction(Intent.ACTION_DELETE);
			PendingIntent pendingDeleteIntent = PendingIntent
			      .getBroadcast(context, 0, deleteIntent, 0);

			notification.deleteIntent = pendingDeleteIntent;

			// Finally: run the notification!
			Log.v("*** Notify running ***");
			myNM.notify(notif, notification);
		}
	}
	
	public static void clear(Context context) {
		clear(context, NOTIFICATION_ALERT);
	}

	public static synchronized void clear(Context context, int notif) {
		createNM(context);
		if (myNM != null) {
			Log.v("Notification cleared");
			myNM.cancel(notif);
		}		
	}

	public static synchronized void clearAll(Context context, boolean reply) {
		createPM(context);

		if (reply || myPrefs.getBoolean(
		      context.getString(R.string.pref_markread_key),
		      Boolean.parseBoolean(context
		            .getString(R.string.pref_markread_default)))) {
			createNM(context);
			if (myNM != null) {
				myNM.cancelAll();
				Log.v("All notifications cleared");
			}
		}	
	}
	
	public static void clearAll(Context context) {
		clearAll(context, false);
	}

	/*
	 * Parse the user provided custom vibrate pattern into a long[]
	 */
	//TODO: tidy this up
	public static long[] parseVibratePattern(String stringPattern) {
		ArrayList<Long> arrayListPattern = new ArrayList<Long>();
		Long l;
		String[] splitPattern = stringPattern.split(",");
		int VIBRATE_PATTERN_MAX_SECONDS = 60000;
		int VIBRATE_PATTERN_MAX_PATTERN = 30;

		for (int i = 0; i < splitPattern.length; i++) {
			try {
				l = Long.parseLong(splitPattern[i].trim());
			} catch (NumberFormatException e) {
				return null;
			}
			if (l > VIBRATE_PATTERN_MAX_SECONDS) {
				return null;
			}
			arrayListPattern.add(l);
		}
		
		// TODO: can i just cast the whole ArrayList into long[]?
		int size = arrayListPattern.size();
		if (size > 0 && size < VIBRATE_PATTERN_MAX_PATTERN) {
			long[] pattern = new long[size];
			for (int i = 0; i < pattern.length; i++) {
				pattern[i] = arrayListPattern.get(i);
			}
			return pattern;
		}
		
		return null;
	}	
}