package net.everythingandroid.smspopup.ui;

import java.util.ArrayList;
import java.util.List;

import net.everythingandroid.smspopup.R;
import net.everythingandroid.smspopup.controls.QmTextWatcher;
import net.everythingandroid.smspopup.controls.SmsPopupPager;
import net.everythingandroid.smspopup.controls.SmsPopupPager.MessageCountChanged;
import net.everythingandroid.smspopup.controls.SmsPopupView;
import net.everythingandroid.smspopup.controls.SmsPopupView.OnReactToMessage;
import net.everythingandroid.smspopup.preferences.ButtonListPreference;
import net.everythingandroid.smspopup.provider.SmsMmsMessage;
import net.everythingandroid.smspopup.provider.SmsPopupContract.QuickMessages;
import net.everythingandroid.smspopup.receiver.ClearAllReceiver;
import net.everythingandroid.smspopup.service.ReminderService;
import net.everythingandroid.smspopup.service.SmsPopupUtilsService;
import net.everythingandroid.smspopup.util.Eula;
import net.everythingandroid.smspopup.util.Log;
import net.everythingandroid.smspopup.util.ManageKeyguard;
import net.everythingandroid.smspopup.util.ManageKeyguard.LaunchOnKeyguardExit;
import net.everythingandroid.smspopup.util.ManageNotification;
import net.everythingandroid.smspopup.util.ManagePreferences.Defaults;
import net.everythingandroid.smspopup.util.ManageWakeLock;
import net.everythingandroid.smspopup.util.SmsPopupUtils;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Display;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;
import android.widget.ViewSwitcher;

import com.commonsware.cwac.wakeful.WakefulIntentService;
import com.viewpagerindicator.CirclePageIndicator;

public class SmsPopupActivity extends Activity {

    private boolean exitingKeyguardSecurely = false;
    private SharedPreferences mPrefs;
    private InputMethodManager inputManager = null;
    private View inputView = null;

    private EditText qrEditText = null;
    private ProgressDialog mProgressDialog = null;

    private LinearLayout mainLayout = null;
    private ViewSwitcher buttonSwitcher = null;
    private SmsPopupPager smsPopupPager = null;
    private CirclePageIndicator pagerIndicator = null;

    private boolean wasVisible = false;
    private boolean replying = false;
    private boolean inbox = false;
    private int privacyMode;
    private boolean privacyAlways = false;
    private boolean useUnlockButton = false;
    private String signatureText;
    private boolean hasNotified = false;

    private static final double WIDTH = 0.9;
    private static final int MAX_WIDTH = 640;
    private static final int DIALOG_DELETE = Menu.FIRST;
    private static final int DIALOG_QUICKREPLY = Menu.FIRST + 1;
    private static final int DIALOG_PRESET_MSG = Menu.FIRST + 2;
    private static final int DIALOG_LOADING = Menu.FIRST + 3;

    private static final int CONTEXT_CLOSE_ID = Menu.FIRST;
    private static final int CONTEXT_DELETE_ID = Menu.FIRST + 1;
    private static final int CONTEXT_REPLY_ID = Menu.FIRST + 2;
    private static final int CONTEXT_QUICKREPLY_ID = Menu.FIRST + 3;
    private static final int CONTEXT_INBOX_ID = Menu.FIRST + 4;
    private static final int CONTEXT_TTS_ID = Menu.FIRST + 5;
    private static final int CONTEXT_VIEWCONTACT_ID = Menu.FIRST + 6;

    private static final int VOICE_RECOGNITION_REQUEST_CODE = 8888;

    private static final int BUTTON_SWITCHER_MAIN_BUTTONS = 0;
    private static final int BUTTON_SWITCHER_UNLOCK_BUTTON = 1;

    private TextView quickreplyTextView;
    private SmsMmsMessage quickReplySmsMessage;

    private Cursor mCursor = null;

    private TextToSpeech androidTts = null;

    /*
     * *****************************************************************************
     * Main onCreate override
     * *****************************************************************************
     */
    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.popup);

        setupPreferences();
        setupViews();

        if (bundle == null) { // new activity
            initializeMessagesAndWake(getIntent().getExtras());
        } else { // this activity was recreated after being destroyed
            initializeMessagesAndWake(bundle);
        }

        Eula.show(this);
    }

    /*
     * *****************************************************************************
     * Setup methods - these will mostly be run one time only 
     * *****************************************************************************
     */

    private void setupPreferences() {
        // Get shared prefs
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Check if screen orientation should be "user" or "behind" based on prefs
        if (mPrefs.getBoolean(getString(R.string.pref_autorotate_key), Defaults.PREFS_AUTOROTATE)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_BEHIND);
        }

        // Fetch privacy mode
        final boolean privacyMessage =
                mPrefs.getBoolean(getString(R.string.pref_privacy_key), Defaults.PREFS_PRIVACY);
        final boolean privacySender = mPrefs.getBoolean(getString(R.string.pref_privacy_sender_key),
                Defaults.PREFS_PRIVACY_SENDER);
        privacyAlways = mPrefs.getBoolean(getString(R.string.pref_privacy_always_key),
                Defaults.PREFS_PRIVACY_ALWAYS);
        
        if (privacySender && privacyMessage) {
            privacyMode = SmsPopupView.PRIVACY_MODE_HIDE_ALL;
        } else if (privacyMessage) {
            privacyMode = SmsPopupView.PRIVACY_MODE_HIDE_MESSAGE;
        } else {
            privacyMode = SmsPopupView.PRIVACY_MODE_OFF;
        }
        
        useUnlockButton = mPrefs.getBoolean(
                getString(R.string.pref_useUnlockButton_key), Defaults.PREFS_USE_UNLOCK_BUTTON);

        // Fetch quick reply signature
        signatureText = mPrefs.getString(getString(R.string.pref_notif_signature_key), "");
        if (signatureText.length() > 0)
            signatureText = " " + signatureText;
    }

    private void setupViews() {

        // Find main views
        smsPopupPager = (SmsPopupPager) findViewById(R.id.SmsPopupPager);
        pagerIndicator = (CirclePageIndicator) findViewById(R.id.indicator);
        pagerIndicator.setViewPager(smsPopupPager);
        smsPopupPager.setIndicator(pagerIndicator);
        mainLayout = (LinearLayout) findViewById(R.id.MainLayout);
        buttonSwitcher = (ViewSwitcher) findViewById(R.id.ButtonViewSwitcher);

        // Set privacy mode
        smsPopupPager.setPrivacy(privacyMode);
        
        smsPopupPager.setOnReactToMessage(new OnReactToMessage() {

            @Override
            public void onViewMessage(SmsMmsMessage message) {
                viewMessage();
            }

            @Override
            public void onReplyToMessage(SmsMmsMessage message) {
                replyToMessage(message);
            }
            
        });

        final Button unlockButton = (Button) findViewById(R.id.unlockButton);
        
        // If on ICS+ set button to fill_parent (this is set to wrap_content by default). This
        // matches better visually with ICS dialog buttons.
        if (SmsPopupUtils.isICS()) {
            final ViewGroup.LayoutParams unlockLayoutParams = unlockButton.getLayoutParams();
            unlockLayoutParams.width = LayoutParams.FILL_PARENT;
            unlockButton.setLayoutParams(unlockLayoutParams);
        }
        
        unlockButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                viewMessage();
            }
        });

        smsPopupPager.setOnMessageCountChanged(new MessageCountChanged() {

            @Override
            public void onChange(int current, int total) {
                if (total == 1) {
                    pagerIndicator.setVisibility(View.INVISIBLE);
                } else if (total >= 2) {
                    pagerIndicator.setVisibility(View.VISIBLE);
                }
                
                if (hasNotified) {
                    ManageNotification.update(SmsPopupActivity.this,
                            smsPopupPager.getMessage(current), total);
                }
            }
        });

        // See if user wants to show buttons on the popup
        if (!mPrefs.getBoolean(getString(R.string.pref_show_buttons_key),
                Defaults.PREFS_SHOW_BUTTONS)) {

            // Hide button layout
            buttonSwitcher.setVisibility(View.GONE);

        } else {

            // Button 1
            final Button button1 = (Button) findViewById(R.id.button1);
            PopupButton button1Vals =
                    new PopupButton(getApplicationContext(), Integer
                            .parseInt(mPrefs
                                    .getString(getString(R.string.pref_button1_key),
                                            Defaults.PREFS_BUTTON1)));
            button1.setOnClickListener(button1Vals);
            button1.setText(button1Vals.buttonText);
            button1.setVisibility(button1Vals.buttonVisibility);

            // Button 2
            final Button button2 = (Button) findViewById(R.id.button2);
            PopupButton button2Vals =
                    new PopupButton(getApplicationContext(), Integer
                            .parseInt(mPrefs
                                    .getString(getString(R.string.pref_button2_key),
                                            Defaults.PREFS_BUTTON2)));
            button2.setOnClickListener(button2Vals);
            button2.setText(button2Vals.buttonText);
            button2.setVisibility(button2Vals.buttonVisibility);

            // Button 3
            final Button button3 = (Button) findViewById(R.id.button3);
            PopupButton button3Vals =
                    new PopupButton(getApplicationContext(), Integer
                            .parseInt(mPrefs
                                    .getString(getString(R.string.pref_button3_key),
                                            Defaults.PREFS_BUTTON3)));
            button3.setOnClickListener(button3Vals);
            button3.setText(button3Vals.buttonText);
            button3.setVisibility(button3Vals.buttonVisibility);

            /*
             * This is really hacky. There are two types of reply buttons (quick reply and reply).
             * If the user has selected to show both the replies then the text on the buttons should
             * be different. If they only use one then the text can just be "Reply".
             */
            int numReplyButtons = 0;
            if (button1Vals.isReplyButton)
                numReplyButtons++;
            if (button2Vals.isReplyButton)
                numReplyButtons++;
            if (button3Vals.isReplyButton)
                numReplyButtons++;

            if (numReplyButtons == 1) {
                if (button1Vals.isReplyButton)
                    button1.setText(R.string.button_reply);
                if (button2Vals.isReplyButton)
                    button2.setText(R.string.button_reply);
                if (button3Vals.isReplyButton)
                    button3.setText(R.string.button_reply);
            }
        }

        refreshViews();
        resizeLayout();
    }

    private void initializeMessagesAndWake(Bundle b) {
        initializeMessagesAndWake(b, false);
    }

    /**
     * Setup messages within the popup given an intent bundle
     * 
     * @param b
     *            the incoming intent bundle
     * @param newIntent
     *            if this is from onNewIntent or not
     */
    private void initializeMessagesAndWake(Bundle b, boolean newIntent) {

        // Create message from bundle
        SmsMmsMessage message = new SmsMmsMessage(getApplicationContext(), b);

        if (newIntent) {
            smsPopupPager.addMessage(message);   
            wakeApp();
        } else {
            if (message != null) {
                new LoadUnreadMessagesAsyncTask().execute(message);
            }
        }        
    }
    
    private class LoadUnreadMessagesAsyncTask extends AsyncTask<SmsMmsMessage, 
            Void, ArrayList<SmsMmsMessage>> {
        
        ProgressBar mProgressBar;
                
        @Override
        protected void onPreExecute() {
            mProgressBar = (ProgressBar) findViewById(R.id.progress);
            mProgressBar.setVisibility(View.VISIBLE);
            disablePopupButtons(false);
        }

        @Override
        protected ArrayList<SmsMmsMessage> doInBackground(SmsMmsMessage... arg) {
            ArrayList<SmsMmsMessage> messages = SmsPopupUtils.getUnreadMessages(
                    SmsPopupActivity.this, arg[0].getMessageId());
            
            if (messages == null) {
                messages = new ArrayList<SmsMmsMessage>(1);
            }
            
            messages.add(arg[0]);
                        
            return messages;
        }
        
        @Override
        protected void onPostExecute(ArrayList<SmsMmsMessage> result) {
            disablePopupButtons(true);
            mProgressBar.setVisibility(View.GONE);
            smsPopupPager.addMessages(result);
            smsPopupPager.showLast();
            wakeApp();
        }
    }
    
    private void disablePopupButtons(boolean enabled) {
        findViewById(R.id.button1).setEnabled(enabled);
        findViewById(R.id.button2).setEnabled(enabled);
        findViewById(R.id.button3).setEnabled(enabled);
        findViewById(R.id.unlockButton).setEnabled(enabled);
    }
    
    /*
     * *****************************************************************************
     * Methods that will be called several times throughout the life of the activity
     * *****************************************************************************
     */
    private void refreshViews() {
        
        final int currentView = buttonSwitcher.getDisplayedChild();

        ManageKeyguard.initialize(this);
        if (ManageKeyguard.inKeyguardRestrictedInputMode()) {
            
            if (useUnlockButton) {
                if (currentView != BUTTON_SWITCHER_UNLOCK_BUTTON) { 
                    // Show unlock button
                    buttonSwitcher.setDisplayedChild(BUTTON_SWITCHER_UNLOCK_BUTTON);
                }
            }

            // Disable long-press context menu
            unregisterForContextMenu(smsPopupPager);

        } else {
            
            if (currentView != BUTTON_SWITCHER_MAIN_BUTTONS) {
                // Show main popup buttons
                buttonSwitcher.setDisplayedChild(BUTTON_SWITCHER_MAIN_BUTTONS);
            }

            // Enable long-press context menu
            registerForContextMenu(smsPopupPager);

            if (!privacyAlways) {
                // Now keyguard is off, disable privacy mode
                smsPopupPager.setPrivacy(SmsPopupView.PRIVACY_MODE_OFF);                
            }
        }
    }

    private void resizeLayout() {

        // This sets the minimum width of the activity to a minimum of 80% of the screen
        // size only needed because the theme of this activity is "dialog" so it looks
        // like it's floating and doesn't seem to fill_parent like a regular activity
        Display d = getWindowManager().getDefaultDisplay();
        int width = d.getWidth() > MAX_WIDTH ? MAX_WIDTH : (int) (d.getWidth() * WIDTH);
        mainLayout.setMinimumWidth(width);
        mainLayout.invalidate();
    }

    /**
     * Wake up the activity, this will acquire the wakelock (turn on the screen) and sound the
     * notification if needed. This is called once all preparation is done for this activity (end of
     * onCreate()).
     */
    private void wakeApp() {

        // Time to acquire a full WakeLock (turn on screen)
        ManageWakeLock.acquireFull(getApplicationContext());
        ManageWakeLock.releasePartial();

        replying = false;
        inbox = false;

        SmsMmsMessage notifyMessage = smsPopupPager.shouldNotify();
        
        // See if a notification is needed for this set of messages
        if (notifyMessage != null) {
                       
            // Schedule a reminder notification
            ReminderService.scheduleReminder(this, notifyMessage);

            // Run the notification
            ManageNotification.show(this, notifyMessage, smsPopupPager.getPageCount());
            
            hasNotified = true;
        }
    }

    /**
     * Customized activity finish. Ensures the notification is in sync and cancels any scheduled
     * reminders (as the user has interrupted the app.
     */
    private void myFinish() {
        if (Log.DEBUG)
            Log.v("myFinish()");

        if (inbox) {
            ManageNotification.clearAll(getApplicationContext());
        } else {

            // Start a service that will update the notification in the status bar
            Intent i = new Intent(getApplicationContext(), SmsPopupUtilsService.class);
            i.setAction(SmsPopupUtilsService.ACTION_UPDATE_NOTIFICATION);

            if (replying) {
                // Convert current message to bundle
                i.putExtras(smsPopupPager.getActiveMessage().toBundle());

                // We need to know if the user is replying - if so, the entire thread id should
                // be ignored when working out the message tally in the notification bar.
                // We can't rely on the system database as it may take a little while for the
                // reply intent to fire and load up the messaging up (after which the messages
                // will be marked read in the database).
                i.putExtra(SmsMmsMessage.EXTRAS_REPLYING, replying);
            }

            // Start the service
            WakefulIntentService.sendWakefulWork(getApplicationContext(), i);
        }

        // Cancel any reminder notifications
        ReminderService.cancelReminder(getApplicationContext());

        // Finish up the activity
        finish();
    }

    /*
     * *****************************************************************************
     * Method overrides from Activity class
     * *****************************************************************************
     */
    @Override
    protected void onNewIntent(Intent intent) {

        super.onNewIntent(intent);
        if (Log.DEBUG)
            Log.v("SMSPopupActivity: onNewIntent()");
        
        hasNotified = false;

        // Update intent held by activity
        setIntent(intent);

        // Setup messages
        initializeMessagesAndWake(intent.getExtras(), true);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (Log.DEBUG)
            Log.v("SMSPopupActivity: onStart()");
        // ManageWakeLock.acquirePartial(getApplicationContext());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Log.DEBUG)
            Log.v("SMSPopupActivity: onResume()");
        wasVisible = false;
        // Reset exitingKeyguardSecurely bool to false
        exitingKeyguardSecurely = false;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (Log.DEBUG)
            Log.v("SMSPopupActivity: onPause()");

        // Hide the soft keyboard in case it was shown via quick reply
        hideSoftKeyboard();

        // Shutdown Android TTS
        if (androidTts != null) {
            androidTts.shutdown();
        }

        // Dismiss loading dialog
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
        }

        if (wasVisible) {
            // Cancel the receiver that will clear our locks
            ClearAllReceiver.removeCancel(getApplicationContext());
            ClearAllReceiver.clearAll(!exitingKeyguardSecurely);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (Log.DEBUG)
            Log.v("SMSPopupActivity: onStop()");

        // Cancel the receiver that will clear our locks
        ClearAllReceiver.removeCancel(getApplicationContext());
        ClearAllReceiver.clearAll(!exitingKeyguardSecurely);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    /**
     * Create Dialog
     */
    @Override
    protected Dialog onCreateDialog(int id) {
        if (Log.DEBUG)
            Log.v("onCreateDialog()");

        switch (id) {

        /*
         * Delete message dialog
         */
        case DIALOG_DELETE:
            return new AlertDialog.Builder(this).setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(getString(R.string.pref_show_delete_button_dialog_title))
                    .setMessage(getString(R.string.pref_show_delete_button_dialog_text))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int whichButton) {
                            deleteMessage();
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .create();

            /*
             * Quick Reply Dialog
             */
        case DIALOG_QUICKREPLY:
            LayoutInflater factory = getLayoutInflater();
            final View qrLayout = factory.inflate(R.layout.message_quick_reply, null);
            qrEditText = (EditText) qrLayout.findViewById(R.id.QuickReplyEditText);
            final TextView qrCounterTextView = 
                    (TextView) qrLayout.findViewById(R.id.QuickReplyCounterTextView);
            final Button qrSendButton = (Button) qrLayout.findViewById(R.id.send_button);

            final ImageButton voiceRecognitionButton = 
                    (ImageButton) qrLayout.findViewById(R.id.SpeechRecogButton);

            voiceRecognitionButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    final Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);

                    // Check if the device has the ability to do speech
                    // recognition
                    final PackageManager packageManager = SmsPopupActivity.this.getPackageManager();
                    List<ResolveInfo> list = packageManager.queryIntentActivities(intent, 0);

                    if (list.size() > 0) {
                        // TODO: really should allow voice input here without unlocking first 
                        // (quick replies without unlock are OK anyway)
                        exitingKeyguardSecurely = true;
                        ManageKeyguard.exitKeyguardSecurely(new LaunchOnKeyguardExit() {
                            @Override
                            public void LaunchOnKeyguardExitSuccess() {
                                SmsPopupActivity.this.startActivityForResult(
                                        intent, VOICE_RECOGNITION_REQUEST_CODE);
                            }
                        });
                    } else {
                        Toast.makeText(SmsPopupActivity.this, R.string.error_no_voice_recognition,
                                Toast.LENGTH_LONG).show();
                        view.setEnabled(false);
                    }
                }
            });

            qrEditText.addTextChangedListener(new QmTextWatcher(this, qrCounterTextView,
                    qrSendButton));
            qrEditText.setOnEditorActionListener(new OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {

                    // event != null means enter key pressed
                    if (event != null) {
                        // if shift is not pressed then move focus to send button
                        if (!event.isShiftPressed()) {
                            if (v != null) {
                                View focusableView = v.focusSearch(View.FOCUS_RIGHT);
                                if (focusableView != null) {
                                    focusableView.requestFocus();
                                    return true;
                                }
                            }
                        }

                        // otherwise allow keypress through
                        return false;
                    }

                    if (actionId == EditorInfo.IME_ACTION_SEND) {
                        if (v != null) {
                            sendQuickReply(v.getText().toString());
                        }
                        return true;
                    }

                    // else consume
                    return true;
                }
            });

            quickreplyTextView = (TextView) qrLayout.findViewById(R.id.QuickReplyTextView);
            QmTextWatcher.getQuickReplyCounterText(
                    qrEditText.getText().toString(),
                    qrCounterTextView,
                    qrSendButton);

            qrSendButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    sendQuickReply(qrEditText.getText().toString());
                }
            });

            // Construct basic AlertDialog using AlertDialog.Builder
            final AlertDialog qrAlertDialog = new AlertDialog.Builder(this)
                    .setIcon(android.R.drawable.ic_dialog_email)
                    .setTitle(R.string.quickreply_title)
                    .create();

            // Set the custom layout with no spacing at the bottom
            qrAlertDialog.setView(qrLayout, 0, SmsPopupUtils.pixelsToDip(getResources(), 5), 0, 0);
            
            qrAlertDialog.setOnCancelListener(new OnCancelListener() { 
                @Override
                public void onCancel(DialogInterface dialog) {
                    removeDialog(DIALOG_QUICKREPLY);
                }
            });

            // Preset messages button
            Button presetButton = (Button) qrLayout.findViewById(R.id.PresetMessagesButton);
            presetButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    showDialog(DIALOG_PRESET_MSG);
                }
            });

            // Cancel button
            Button cancelButton = (Button) qrLayout.findViewById(R.id.CancelButton);
            cancelButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (qrAlertDialog != null) {
                        hideSoftKeyboard();
                        qrAlertDialog.dismiss();
                        removeDialog(DIALOG_QUICKREPLY);
                    }
                }
            });

            // Ensure this dialog is counted as "editable" (so soft keyboard
            // will always show on top)
            qrAlertDialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);

            qrAlertDialog.setOnDismissListener(new OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    if (Log.DEBUG)
                        Log.v("Quick Reply Dialog: onDissmiss()");
                }
            });

            // Update quick reply views now that they have been created
            updateQuickReplyView("");

            /*
             * TODO: due to what seems like a bug, setting selection to 0 here doesn't seem to work
             * but setting it to 1 first then back to 0 does. I couldn't find a way around this :|
             * To reproduce, comment out the below line and set a quick reply signature, when
             * clicking Quick Reply the cursor will be positioned at the end of the EditText rather
             * than the start.
             */
            if (qrEditText.getText().toString().length() > 0)
                qrEditText.setSelection(1);

            qrEditText.setSelection(0);

            return qrAlertDialog;

            /*
             * Preset messages dialog
             */
        case DIALOG_PRESET_MSG:
            mCursor = getContentResolver().query(QuickMessages.CONTENT_URI, null, null, null, null);
            startManagingCursor(mCursor);

            AlertDialog.Builder mDialogBuilder = new AlertDialog.Builder(this)
                    .setIcon(android.R.drawable.ic_dialog_email)
                    .setTitle(R.string.pref_message_presets_title);
            
            // If user has some presets defined ...
            if (mCursor != null && mCursor.getCount() > 0) {

                mDialogBuilder.setCursor(mCursor, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int item) {
                        if (Log.DEBUG)
                            Log.v("Item clicked = " + item);
                        mCursor.moveToPosition(item);
                        quickReply(mCursor.getString(
                                mCursor.getColumnIndexOrThrow(QuickMessages.QUICKMESSAGE)));
                    }
                }, QuickMessages.QUICKMESSAGE);
            } else { // Otherwise display a placeholder as user has no presets
                MatrixCursor emptyCursor =
                        new MatrixCursor(new String[] { QuickMessages._ID,
                                QuickMessages.QUICKMESSAGE });
                emptyCursor.addRow(new String[] { "0",
                        getString(R.string.message_presets_empty_text) });
                mDialogBuilder.setCursor(emptyCursor, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int item) {}
                }, QuickMessages.QUICKMESSAGE);
            }

            return mDialogBuilder.create();

            /*
             * Loading Dialog
             */
        case DIALOG_LOADING:
            mProgressDialog = new ProgressDialog(this);
            mProgressDialog.setMessage(getString(R.string.loading_message));
            mProgressDialog.setIndeterminate(true);
            mProgressDialog.setCancelable(true);
            return mProgressDialog;
        }

        return null;
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        super.onPrepareDialog(id, dialog);

        if (Log.DEBUG)
            Log.v("onPrepareDialog()");
        // User interacted so remove all locks and cancel reminders
        ClearAllReceiver.removeCancel(getApplicationContext());
        ClearAllReceiver.clearAll(false);
        ReminderService.cancelReminder(getApplicationContext());

        switch (id) {
        case DIALOG_QUICKREPLY:
            showSoftKeyboard(qrEditText);

            // Set width of dialog to fill_parent
            final LayoutParams mLP = dialog.getWindow().getAttributes();

            // TODO: this should be limited in case the screen is large
            mLP.width = LayoutParams.FILL_PARENT;            
            dialog.getWindow().setAttributes(mLP);
            break;

        case DIALOG_PRESET_MSG:
            break;
        }
    }

    /**
     * Handle the results from the recognition activity.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (Log.DEBUG)
            Log.v("onActivityResult");
        if (requestCode == VOICE_RECOGNITION_REQUEST_CODE && resultCode == RESULT_OK) {
            ArrayList<String> matches =
                    data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (Log.DEBUG)
                Log.v("Voice recog text: " + matches.get(0));
            quickReply(matches.get(0));
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (Log.DEBUG)
            Log.v("SMSPopupActivity: onWindowFocusChanged(" + hasFocus + ")");
        if (hasFocus) {
            // This is really hacky, basically a flag that is set if the message was at some
            // point visible. I tried using onResume() or other methods to prevent doing some
            // things 2 times but this seemed to be the only reliable way (?)
            wasVisible = true;
            refreshViews();
            // refreshPrivacy(false);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (Log.DEBUG)
            Log.v("SMSPopupActivity: onSaveInstanceState()");

        // Save values from most recent bundle (ie. most recent message)
//        outState.putAll(bundle);
    }
    
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (Log.DEBUG)
            Log.v("SMSPopupActivity: onRestoreInstanceState()");        
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (Log.DEBUG)
            Log.v("SMSPopupActivity: onConfigurationChanged()");
        resizeLayout();
    }

    /**
     * Create Context Menu (Long-press menu)
     */
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        menu.add(Menu.NONE, CONTEXT_VIEWCONTACT_ID, Menu.NONE, getString(R.string.view_contact));
        menu.add(Menu.NONE, CONTEXT_CLOSE_ID, Menu.NONE, getString(R.string.button_close));
        menu.add(Menu.NONE, CONTEXT_DELETE_ID, Menu.NONE, getString(R.string.button_delete));
        menu.add(Menu.NONE, CONTEXT_REPLY_ID, Menu.NONE, getString(R.string.button_reply));
        menu.add(Menu.NONE, CONTEXT_QUICKREPLY_ID, Menu.NONE, getString(R.string.button_quickreply));
        menu.add(Menu.NONE, CONTEXT_TTS_ID, Menu.NONE, getString(R.string.button_tts));
        menu.add(Menu.NONE, CONTEXT_INBOX_ID, Menu.NONE, getString(R.string.button_inbox));
    }

    /**
     * Context Menu Item Selected
     */
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case CONTEXT_CLOSE_ID:
            closeMessage();
            break;
        case CONTEXT_DELETE_ID:
            showDialog(DIALOG_DELETE);
            break;
        case CONTEXT_REPLY_ID:
            replyToMessage();
            break;
        case CONTEXT_QUICKREPLY_ID:
            quickReply();
            break;
        case CONTEXT_INBOX_ID:
            gotoInbox();
            break;
        case CONTEXT_TTS_ID:
            speakMessage();
            break;
        case CONTEXT_VIEWCONTACT_ID:
            viewContact();
            break;
        }
        return super.onContextItemSelected(item);
    }

    // The Android text-to-speech library OnInitListener (via wrapper class)
    private final OnInitListener androidTtsListener = new OnInitListener() {
        @Override
        public void onInit(int status) {
            if (mProgressDialog != null) {
                mProgressDialog.dismiss();
            }
            if (status == TextToSpeech.SUCCESS) {
                speakMessage();
            } else {
                Toast.makeText(SmsPopupActivity.this, R.string.error_message, Toast.LENGTH_SHORT);
            }
        }
    };

    /*
     * *****************************************************************************
     * Methods to handle messages (speak, close, reply, quick reply etc.)
     * *****************************************************************************
     */

    /**
     * Speak the message out loud using text-to-speech (either via Android text-to-speech or via the
     * free eyes-free text-to-speech library)
     */
    private void speakMessage() {
        // TODO: we should really require the keyguard be unlocked here if we
        // are in privacy mode

        // If not previously initialized...
        if (androidTts == null) {

            // Show a loading dialog
            showDialog(DIALOG_LOADING);

            // User interacted so remove all locks and cancel reminders
            ClearAllReceiver.removeCancel(getApplicationContext());
            ClearAllReceiver.clearAll(false);
            ReminderService.cancelReminder(getApplicationContext());

            // We'll use update notification to stop the sound playing
            ManageNotification.update(
                    this, smsPopupPager.getActiveMessage(), smsPopupPager.getPageCount());

            androidTts = new TextToSpeech(SmsPopupActivity.this, androidTtsListener);

        } else {
            androidTts.speak(smsPopupPager.getActiveMessage().getMessageBody(),
                    TextToSpeech.QUEUE_FLUSH,
                    null);
        }
    }

    /**
     * Close the message window/popup, mark the message read if the user has this option on
     */
    private void closeMessage() {
        Intent i = new Intent(getApplicationContext(), SmsPopupUtilsService.class);
        /*
         * Switched back to mark messageId as read for >v1.0.6 (marking thread as read is slow for
         * really large threads)
         */
        i.setAction(SmsPopupUtilsService.ACTION_MARK_MESSAGE_READ);
        i.putExtras(smsPopupPager.getActiveMessage().toBundle());
        WakefulIntentService.sendWakefulWork(getApplicationContext(), i);

        removeActiveMessage();
    }

    /**
     * Reply to the current message, start the reply intent
     */
    private void replyToMessage(final SmsMmsMessage message, final boolean replyToThread) {
        exitingKeyguardSecurely = true;
        ManageKeyguard.exitKeyguardSecurely(new LaunchOnKeyguardExit() {
            @Override
            public void LaunchOnKeyguardExitSuccess() {
                startActivity(message.getReplyIntent(replyToThread));
                replying = true;
                myFinish();
            }
        });
    }

    private void replyToMessage(SmsMmsMessage message) {
        replyToMessage(message, true);
    }
        
    private void replyToMessage(boolean replyToThread) {
        replyToMessage(smsPopupPager.getActiveMessage(), replyToThread);
    }
    
    private void replyToMessage() {
        replyToMessage(smsPopupPager.getActiveMessage());
    }

    /**
     * View the private message (this basically just unlocks the keyguard and then updates the
     * privacy of the messages).
     */
    private void viewMessage() {
        exitingKeyguardSecurely = true;
        ManageKeyguard.exitKeyguardSecurely(new LaunchOnKeyguardExit() {
            @Override
            public void LaunchOnKeyguardExitSuccess() {
                smsPopupPager.post(new Runnable() {
                    @Override
                    public void run() {
                        smsPopupPager.setPrivacy(SmsPopupView.PRIVACY_MODE_OFF);
                    }  
                });  
            }
        });
    }

    /**
     * Take the user to the messaging app inbox
     */
    private void gotoInbox() {
        exitingKeyguardSecurely = true;
        ManageKeyguard.exitKeyguardSecurely(new LaunchOnKeyguardExit() {
            @Override
            public void LaunchOnKeyguardExitSuccess() {
                Intent i = SmsPopupUtils.getSmsInboxIntent();
                SmsPopupActivity.this.getApplicationContext().startActivity(i);
                inbox = true;
                myFinish();
            }
        });
    }

    /**
     * Delete the current message from the system database
     */
    private void deleteMessage() {
        Intent i = new Intent(SmsPopupActivity.this.getApplicationContext(), 
                SmsPopupUtilsService.class);
        i.setAction(SmsPopupUtilsService.ACTION_DELETE_MESSAGE);
        i.putExtras(smsPopupPager.getActiveMessage().toBundle());
        WakefulIntentService.sendWakefulWork(getApplicationContext(), i);
        removeActiveMessage();
    }

    /**
     * Sends the actual quick reply message
     */
    private void sendQuickReply(String quickReplyMessage) {
        hideSoftKeyboard();
        if (quickReplyMessage != null) {
            if (quickReplyMessage.length() > 0) {
                Intent i = new Intent(SmsPopupActivity.this.getApplicationContext(),
                        SmsPopupUtilsService.class);
                i.setAction(SmsPopupUtilsService.ACTION_QUICKREPLY);
                i.putExtras(quickReplySmsMessage.toBundle());
                i.putExtra(SmsMmsMessage.EXTRAS_QUICKREPLY, quickReplyMessage);
                if (Log.DEBUG)
                    Log.v("Sending message to " + quickReplySmsMessage.getContactName());
                WakefulIntentService.sendWakefulWork(getApplicationContext(), i);
                Toast.makeText(this, R.string.quickreply_sending_toast, Toast.LENGTH_LONG).show();
                dismissDialog(DIALOG_QUICKREPLY);                
                removeActiveMessage();
            } else {
                Toast.makeText(this, R.string.quickreply_nomessage_toast, Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Show the quick reply dialog, resetting the text in the edittext and storing the current
     * SmsMmsMessage (in case another message comes in)
     */
    private void quickReply() {
        quickReply("");
    }

    /**
     * Show the quick reply dialog, if text passed is null or empty then store the current
     * SmsMmsMessage (in case another message comes in)
     */
    private void quickReply(String text) {
        final SmsMmsMessage message = smsPopupPager.getActiveMessage();

        // If this is a MMS or a SMS from email gateway then use regular reply
        if (message.isMms() || message.isEmail()) {
            replyToMessage();
        } else { // Else show the quick reply dialog
            if (text == null || "".equals(text)) {
                quickReplySmsMessage = message;
            }
            updateQuickReplyView(text);
            showDialog(DIALOG_QUICKREPLY);
        }
    }

    /**
     * View contact that has the message address (or create if it doesn't exist)
     */
    private void viewContact() {
        Intent contactIntent = new Intent(ContactsContract.Intents.SHOW_OR_CREATE_CONTACT);

        SmsMmsMessage message = smsPopupPager.getActiveMessage();
        if (message.isMms() || message.isEmail()) {
            contactIntent.setData(Uri.fromParts("mailto", message.getAddress(), null));
        } else {
            contactIntent.setData(Uri.fromParts("tel", message.getAddress(), null));
        }
        startActivity(contactIntent);
    }

    /**
     * Refresh the quick reply view - update the edittext and the counter
     */
    private void updateQuickReplyView(String editText) {
        if (Log.DEBUG) Log.v("updateQuickReplyView - '" + editText + "'");
        if (qrEditText != null && editText != null) {
            qrEditText.setText(editText + signatureText);
            qrEditText.setSelection(editText.length());
        }
        if (quickreplyTextView != null) {

            if (quickReplySmsMessage == null) {
                quickReplySmsMessage = smsPopupPager.getActiveMessage();
            }

            quickreplyTextView.setText(getString(R.string.quickreply_from_text,
                    quickReplySmsMessage.getContactName()));
        }
    }

    /**
     * Removes the active message
     */
    private void removeActiveMessage() {        
        final int status = smsPopupPager.removeActiveMessage();
        if (status == SmsPopupPager.STATUS_NO_MESSAGES_REMAINING)  {
            myFinish();
        }
    }

    /*
     * *****************************************************************************
     * Misc methods 
     * *****************************************************************************
     */

    /**
     * Show the soft keyboard and store the view that triggered it
     */
    private void showSoftKeyboard(View triggerView) {
        if (Log.DEBUG) Log.v("showSoftKeyboard()");
        if (inputManager == null) {
            inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        }
        inputView = triggerView;
        inputManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
    }

    /**
     * Hide the soft keyboard
     */
    private void hideSoftKeyboard() {
        if (inputView == null)
            return;
        if (Log.DEBUG) Log.v("hideSoftKeyboard()");
        if (inputManager == null) {
            inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        }
        inputManager.hideSoftInputFromWindow(inputView.getApplicationWindowToken(), 0);
        inputView = null;
    }

    /*
     * Inner class to handle dynamic button functions on popup
     */
    private class PopupButton implements OnClickListener {
        private int buttonId;
        public boolean isReplyButton;
        public String buttonText;
        public int buttonVisibility = View.VISIBLE;

        public PopupButton(Context mContext, int id) {
            buttonId = id;
            isReplyButton = false;
            if (buttonId == ButtonListPreference.BUTTON_REPLY
                    || buttonId == ButtonListPreference.BUTTON_QUICKREPLY
                    || buttonId == ButtonListPreference.BUTTON_REPLY_BY_ADDRESS) {
                isReplyButton = true;
            }
            String[] buttonTextArray = mContext.getResources().getStringArray(R.array.buttons_text);
            buttonText = buttonTextArray[buttonId];

            if (buttonId == ButtonListPreference.BUTTON_DISABLED) { // Disabled
                buttonVisibility = View.GONE;
            }
        }

        @Override
        public void onClick(View v) {
            switch (buttonId) {
            case ButtonListPreference.BUTTON_DISABLED: // Disabled
                break;
            case ButtonListPreference.BUTTON_CLOSE: // Close
                closeMessage();
                break;
            case ButtonListPreference.BUTTON_DELETE: // Delete
                showDialog(DIALOG_DELETE);
                break;
            case ButtonListPreference.BUTTON_DELETE_NO_CONFIRM:
                // Delete no confirmation
                deleteMessage();
                break;
            case ButtonListPreference.BUTTON_REPLY: // Reply
                replyToMessage(true);
                break;
            case ButtonListPreference.BUTTON_QUICKREPLY: // Quick Reply
                quickReply();
                break;
            case ButtonListPreference.BUTTON_REPLY_BY_ADDRESS: // Quick Reply
                replyToMessage(false);
                break;
            case ButtonListPreference.BUTTON_INBOX: // Inbox
                gotoInbox();
                break;
            case ButtonListPreference.BUTTON_TTS: // Text-to-Speech
                speakMessage();
                break;
            }
        }
    }

}
