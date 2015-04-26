package hackmed.pillar.pillarapp;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBar;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.os.Build;
import android.widget.Button;

import com.microsoft.band.BandClient;
import com.microsoft.band.BandClientManager;
import com.microsoft.band.BandDeviceInfo;
import com.microsoft.band.BandException;
import com.microsoft.band.ConnectionResult;
import com.microsoft.band.notification.VibrationType;

import java.util.concurrent.TimeUnit;


public class MainActivity extends ActionBarActivity {




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        /*if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, new PlaceholderFragment())
                    .commit();
        }*/

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent i = new Intent(this, SettingsActivity.class);
            startActivity(i);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment {


        private BandDeviceInfo[] mPairedBands;
        private Button mButtonConnect;
        private Button mButtonChooseBand;

        private int mSelectedBandIndex = 0;


        private Button mButtonVibrate;


        public PlaceholderFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_main, container, false);



            mButtonConnect = (Button) rootView.findViewById(R.id.buttonConnect);
            mButtonConnect.setOnClickListener(mButtonConnectClickListener);

            mButtonChooseBand = (Button) rootView.findViewById(R.id.buttonChooseBand);
            mButtonChooseBand.setOnClickListener(mButtonChooseBandClickListener);

            mButtonVibrate = (Button) rootView.findViewById(R.id.buttonVibrate);
            mButtonVibrate.setOnClickListener(mButtonVibrateClickListener);


            return rootView;
        }

        @Override
        public void onResume() {
            super.onResume();

            mPairedBands = BandClientManager.getInstance().getPairedBands();

            // If one or more bands were removed, making our band selection invalid,
            // reset the selection to the first in the list.
            if (mSelectedBandIndex >= mPairedBands.length) {
                mSelectedBandIndex = 0;
            }
            refreshControls();
        }



        //
        // If there are multiple bands, the "choose band" button is enabled and
        // launches a dialog where we can select the band to use.
        //
        private View.OnClickListener mButtonChooseBandClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View button) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

                String[] names = new String[mPairedBands.length];
                for (int i = 0; i < names.length; i++) {
                    names[i] = mPairedBands[i].getName();
                }

                builder.setItems(names, null);
                builder.setItems(names, new AlertDialog.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        mSelectedBandIndex = which;
                        dialog.dismiss();
                        refreshControls();

                    }
                });

                builder.setTitle("Select band:");
                builder.show();
            }
        };


        //
        // The connect call must be done on a background thread because it
        // involves a callback that must be handled on the UI thread.
        //
        private class ConnectTask extends AsyncTask<BandClient, Void, ConnectionResult> {
            @Override
            protected ConnectionResult doInBackground(BandClient... clientParams) {
                try {
                    return clientParams[0].connect().await();
                } catch (InterruptedException e) {
                    return ConnectionResult.TIMEOUT;
                } catch (BandException e) {
                    return ConnectionResult.INTERNAL_ERROR;
                }
            }

            protected void onPostExecute(ConnectionResult result) {
                if (result != ConnectionResult.OK) {
                    Util.showExceptionAlert(getActivity(), "Connect", new Exception("Connection failed: result=" + result.toString()));
                }
                refreshControls();
            }
        }


        // Handle connect/disconnect requests.
        //
        private View.OnClickListener mButtonConnectClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View button) {
                if (Model.getInstance().isConnected()) {
                    try {
                        Model.getInstance().getClient().disconnect().await(2, TimeUnit.SECONDS);
                        refreshControls();
                    } catch (Exception ex) {
                        Util.showExceptionAlert(getActivity(), "Disconnect", ex);
                    }
                } else {
                    // Always recreate our BandClient since the selection might
                    // have changed. This is safe since we aren't connected.
                    BandClient client = BandClientManager.getInstance().create(getActivity(), mPairedBands[mSelectedBandIndex]);
                    Model.getInstance().setClient(client);

                    mButtonConnect.setEnabled(false);

                    // Connect must be called on a background thread.
                    new ConnectTask().execute(Model.getInstance().getClient());
                }
            }
        };

        public final void onFragmentSelected() {
            if (isVisible()) {
                refreshControls();
            }
        }

        private View.OnClickListener mButtonVibrateClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View button) {
                try {
                    Model.getInstance()
                            .getClient()
                            .getNotificationManager()
                            .vibrate(VibrationType.NOTIFICATION_ALARM)
                            .await();
                } catch (Exception e) {
                    Util.showExceptionAlert(getActivity(), "Vibrate band", e);
                }
            }
        };



        private void refreshControls() {
            switch (mPairedBands.length) {
                case 0:
                    mButtonChooseBand.setText("No paired bands");
                    mButtonChooseBand.setEnabled(false);
                    mButtonConnect.setEnabled(false);
                    break;

                case 1:
                    mButtonChooseBand.setText(mPairedBands[mSelectedBandIndex].getName());
                    mButtonChooseBand.setEnabled(false);
                    mButtonConnect.setEnabled(true);
                    break;

                default:
                    mButtonChooseBand.setText(mPairedBands[mSelectedBandIndex].getName());
                    mButtonChooseBand.setEnabled(true);
                    mButtonConnect.setEnabled(true);
                    break;
            }

            boolean connected = Model.getInstance().isConnected();

            if (connected) {


                // must disconnect before changing the band selection
                mButtonChooseBand.setEnabled(false);
            } else {
            }

            mButtonVibrate.setEnabled(connected);

        }

    }
}
