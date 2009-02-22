package net.everythingandroid.smspopup;

import java.io.ByteArrayInputStream;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;

public class SmsMmsMessage {
	private static final String PREFIX = "net.everythingandroid.smspopup.";
	private static final String EXTRAS_FROM_ADDRESS = PREFIX + "EXTRAS_FROM_ADDRESS";
	private static final String EXTRAS_MESSAGE_BODY = PREFIX + "EXTRAS_MESSAGE_BODY";
	private static final String EXTRAS_TIMESTAMP = PREFIX + "EXTRAS_TIMESTAMP";
	private static final String EXTRAS_UNREAD_COUNT = PREFIX + "EXTRAS_UNREAD_COUNT";
	private static final String EXTRAS_THREAD_ID = PREFIX + "EXTRAS_THREAD_ID";
	private static final String EXTRAS_CONTACT_ID = PREFIX + "EXTRAS_CONTACT_ID";
	private static final String EXTRAS_CONTACT_NAME = PREFIX + "EXTRAS_CONTACT_NAME";
	private static final String EXTRAS_CONTACT_PHOTO = PREFIX + "EXTRAS_CONTACT_PHOTO";
	private static final String EXTRAS_MESSAGE_TYPE = PREFIX + "EXTRAS_MESSAGE_TYPE";
	private static final String EXTRAS_MESSAGE_ID = PREFIX + "EXTRAS_MESSAGE_ID";	
	public static final String EXTRAS_NOTIFY = PREFIX + "EXTRAS_NOTIFY";
	public static final String EXTRAS_REMINDER_COUNT = PREFIX + "EXTRAS_REMINDER_COUNT";
	public static final String EXTRAS_REPLYING = PREFIX + "EXTRAS_REPLYING";
	
	public static final int MESSAGE_TYPE_SMS = 0;
	public static final int MESSAGE_TYPE_MMS = 1;

	private Context context;
	
	private String fromAddress = null;
	private String messageBody = null;
	private long timestamp = 0;
	private int unreadCount = 0;
	private long threadId = 0;
	private String contactId = null;
	private String contactName = null;
	private byte[] contactPhoto = null;
	private int messageType = 0;
	private boolean notify = true;
	private int reminderCount = 0;
	private long messageId = 0;

	/*
	 * Construct SmsMmsMessage with minimal information - this is useful for when
	 * a raw SMS comes in which just contains address, body and timestamp.  We
	 * must then look in the database for the rest of the information 
	 */
	public SmsMmsMessage(Context _context, String _fromAddress, String _messageBody,
	      long _timestamp, int _messageType) {
		context = _context;
		fromAddress = _fromAddress;
		messageBody = _messageBody;
		timestamp = _timestamp;
		messageType = _messageType;
		
		contactId = SMSPopupUtils.getPersonIdFromPhoneNumber(context, fromAddress);
		contactName = SMSPopupUtils.getPersonName(context, contactId, fromAddress);
		contactPhoto = SMSPopupUtils.getPersonPhoto(context, contactId);

		unreadCount = SMSPopupUtils.getUnreadMessagesCount(context);
		threadId = SMSPopupUtils.getThreadIdFromAddress(context, fromAddress);
		
		setMessageId();
		
		if (contactName == null) {
			contactName = context.getString(android.R.string.unknownName);
		}
	}

	/*
	 * Construct SmsMmsMessage for getMmsDetails() - info fetched from the MMS
	 * database table
	 */
	public SmsMmsMessage(Context _context, String _fromAddress, String _messageBody,
	      long _timestamp, long _threadId, int _unreadCount, int _messageType) {
		context = _context;
		fromAddress = _fromAddress;
		messageBody = _messageBody;
		timestamp = _timestamp;
		messageType = _messageType;

		// TODO: I think contactId can come the MMS table, this would save
		// this database lookup
		contactId = SMSPopupUtils.getPersonIdFromPhoneNumber(context, fromAddress);
		
		contactName = SMSPopupUtils.getPersonName(context, contactId, fromAddress);
		contactPhoto = SMSPopupUtils.getPersonPhoto(context, contactId);

		unreadCount = _unreadCount;
		threadId = _threadId;

		setMessageId();
		
		if (contactName == null) {
			contactName = context.getString(android.R.string.unknownName);
		}
	}

	/*
	 * Construct SmsMmsMessage for getSmsDetails() - info fetched from the SMS
	 * database table
	 */
	public SmsMmsMessage(Context _context, String _fromAddress, String _contactId, 
			String _messageBody, long _timestamp, long _threadId,
			int _unreadCount, int _messageType) {
		context = _context;
		fromAddress = _fromAddress;
		messageBody = _messageBody;
		timestamp = _timestamp;
		messageType = _messageType;
		contactId = _contactId;
		
		contactName = SMSPopupUtils.getPersonName(context, contactId, fromAddress);
		contactPhoto = SMSPopupUtils.getPersonPhoto(context, contactId);

		unreadCount = _unreadCount;
		threadId = _threadId;

		setMessageId();
		
		if (contactName == null) {
			contactName = context.getString(android.R.string.unknownName);
		}
	}
	
	/*
	 * Construct SmsMmsMessage from an extras bundle
	 */
	public SmsMmsMessage(Context _context, Bundle b) {
		context = _context;
		fromAddress = b.getString(EXTRAS_FROM_ADDRESS);
		messageBody = b.getString(EXTRAS_MESSAGE_BODY);
		timestamp = b.getLong(EXTRAS_TIMESTAMP);
		contactId = b.getString(EXTRAS_CONTACT_ID);
		contactName = b.getString(EXTRAS_CONTACT_NAME);
		contactPhoto = b.getByteArray(EXTRAS_CONTACT_PHOTO);
		unreadCount = b.getInt(EXTRAS_UNREAD_COUNT, 1);
		threadId = b.getLong(EXTRAS_THREAD_ID, 0);
		messageType = b.getInt(EXTRAS_MESSAGE_TYPE, MESSAGE_TYPE_SMS);
		notify = b.getBoolean(EXTRAS_NOTIFY, false);
		reminderCount = b.getInt(EXTRAS_REMINDER_COUNT, 0);
		messageId = b.getLong(EXTRAS_MESSAGE_ID, 0);
	}

	/*
	 * Construct SmsMmsMessage by specifying all data, only used for testing the
	 * notification from the preferences screen
	 */
	public SmsMmsMessage(Context _context, String _fromAddress, String _messageBody,
	      long _timestamp, String _contactId, String _contactName, byte[] _contactPhoto,
	      int _unreadCount, long _threadId, int _messageType) {
		context = _context;
		fromAddress = _fromAddress;
		messageBody = _messageBody;
		timestamp = _timestamp;
		contactId = _contactId;
		contactName = _contactName;
		contactPhoto = _contactPhoto;
		unreadCount = _unreadCount;
		threadId = _threadId;
		messageType = _messageType;
	}
	
	/*
	 * Convert all SmsMmsMessage data to an extras bundle to send via an intent
	 */
	public Bundle toBundle() {
		Bundle b = new Bundle();
		b.putString(EXTRAS_FROM_ADDRESS, fromAddress);
		b.putString(EXTRAS_MESSAGE_BODY, messageBody);
		b.putLong(EXTRAS_TIMESTAMP, timestamp);
		b.putString(EXTRAS_CONTACT_ID, contactId);
		b.putString(EXTRAS_CONTACT_NAME, contactName);
		b.putByteArray(EXTRAS_CONTACT_PHOTO, contactPhoto);
		b.putInt(EXTRAS_UNREAD_COUNT, unreadCount);
		b.putLong(EXTRAS_THREAD_ID, threadId);
		b.putInt(EXTRAS_MESSAGE_TYPE, messageType);
		b.putBoolean(EXTRAS_NOTIFY, notify);
		b.putInt(EXTRAS_REMINDER_COUNT, reminderCount);
		b.putLong(EXTRAS_MESSAGE_ID, messageId);
		return b;
	}

   public Bitmap getContactPhoto() {
		if (contactPhoto == null)
			return null;
		return BitmapFactory.decodeStream(new ByteArrayInputStream(contactPhoto));
	}
   
	public Intent getPopupIntent() {
		Intent popup = new Intent(context, SMSPopupActivity.class);
		popup.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
		popup.putExtras(toBundle());
		return popup;
	}
	
	public Intent getReplyIntent() {
		return SMSPopupUtils.getSmsToIntentFromThreadId(context, threadId);
	}
	
	public void setThreadRead() {
		SMSPopupUtils.setThreadRead(context, threadId);
	}

	public void setMessageRead() {
		setMessageId();
		SMSPopupUtils.setMessageRead(context, messageId, messageType);
	}
	
	public int getUnreadCount() {
		return unreadCount;
	}
	
	public long getTimestamp() {
		return timestamp;
	}
	
	public String getFormattedTimestamp() {
		return SMSPopupUtils.formatTimestamp(context, timestamp);
	}
	
	public String getContactName() {
		if (contactName == null) {
			contactName = context.getString(android.R.string.unknownName);
		}
		return contactName;
	}

	public String getMessageBody() {
		if (messageBody == null) {
			messageBody = "";
		}
		return messageBody;
	}
	
	public long getThreadId() {
		return threadId;
	}
	
	public int getMessageType() {
		return messageType;
	}
	
	public boolean getNotify() {
		return notify;
	}
	
	public int getReminderCount() {
		return reminderCount;
	}

	public void updateReminderCount(int count) {
		reminderCount = count;
	}

	public void incrementReminderCount() {
		reminderCount++;
	}
	
	public void delete() {
		SMSPopupUtils.deleteMessage(context, getMessageId(), messageType);
	}
	
	public void setMessageId() {
		messageId = SMSPopupUtils.findMessageId(context, threadId, timestamp, messageType);
	}
	
	public long getMessageId() {
		if (messageId == 0) {
			setMessageId();
		}
		return messageId;
	}
}