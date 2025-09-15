package app.baldphone.neo.calls;

import android.app.Activity;
import android.content.Intent;

import app.baldphone.neo.calls.recent.CallItem;

import com.bald.uriah.baldphone.R;
import com.bald.uriah.baldphone.activities.DialerActivity;
import com.bald.uriah.baldphone.activities.contacts.AddContactActivity;
import com.bald.uriah.baldphone.utils.BDB;
import com.bald.uriah.baldphone.utils.BDialog;
import com.bald.uriah.baldphone.utils.BaldToast;
import com.bald.uriah.baldphone.utils.S;

public class UnknownCallHandler {

    public static void processCallAction(final CallItem call, Activity activity) {
        if (call.number() != null && !call.number().isEmpty()) {
            BDB.from(activity)
                    .setSubText(
                            String.format(
                                    activity.getString(R.string.what_do_you_want_to_do_with___),
                                    call.number()))
                    .setOptions(R.string.call, R.string.add_contact, R.string.message)
                    .addFlag(BDialog.FLAG_OK)
                    .setPositiveButtonListener(
                            params -> {
                                int option = (int) params[0];
                                return switch (option) {
                                    case 0 -> {
                                        DialerActivity.call(call.number(), activity, false);
                                        yield true;
                                    }
                                    case 1 -> {
                                        activity.startActivity(
                                                new Intent(activity, AddContactActivity.class)
                                                        .putExtra(
                                                                AddContactActivity.CONTACT_NUMBER,
                                                                call.number()));
                                        yield true;
                                    }
                                    case 2 -> {
                                        S.sendMessage(call.number(), activity);
                                        yield true;
                                    }
                                    default ->
                                            throw new IllegalArgumentException(
                                                    "Invalid option: " + option);
                                };
                            })
                    .show();
        } else {
            BaldToast.from(activity).setText(R.string.private_number).show();
        }
    }
}
