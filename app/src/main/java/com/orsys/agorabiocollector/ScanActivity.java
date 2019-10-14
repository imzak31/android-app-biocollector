package com.orsys.agorabiocollector;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.machinezoo.sourceafis.FingerprintMatcher;
import com.machinezoo.sourceafis.FingerprintTemplate;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

import asia.kanopi.fingerscan.Fingerprint;
import asia.kanopi.fingerscan.Status;

public class ScanActivity extends AppCompatActivity {
    private TextView tvStatus;
    private TextView tvError;
    private Context wkContext;
    private int scannerStatus = 7;
    private double score;
    private String NB_EMPLEADO;
    private String MN_ESTIPENDIO;
    private String txEmpleado;
    ImageView ivFinger;
    private Fingerprint fingerprint;

    SharedPreferences preferences = null;
    SharedPreferences.Editor editor = null;


    String authURL = "https://cibus.orsyspr.com/api/auth";
    String validateUrl = "https://cibus.orsyspr.com/api/auth/validate";
    //String bioSendUrl = "https://cibus.orsyspr.com/bio/send/template";
    //String bioSendUrl = "https://cibus.orsyspr.com/bio/send/template/register";
    String bioSendUrl = "https://cibus.orsyspr.com/bio/register";
    String bioQueryUrl = "https://cibus.orsyspr.com/bio/sent";
    String sendPoncheUrl = "https://cibus.orsyspr.com/cibus/register";


    byte[] image;
    FingerprintTemplate imageTemplate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActionBar actionBar = getSupportActionBar();
        setContentView(R.layout.activity_scan);
        tvStatus = (TextView) findViewById(R.id.tvStatus);
        tvStatus.setText("Enter employee number to continue");
        tvError = (TextView) findViewById(R.id.tvError);
        //ivFinger = (ImageView) findViewById(R.id.ivFingerDisplay);
        ivFinger = (ImageView) findViewById(R.id.iv_scanok);

        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        wkContext = this;

        fingerprint = new Fingerprint();

        if(scannerStatus == Status.SCANNER_POWERED_OFF){
            fingerprint.scan(ScanActivity.this, printHandler, updateHandler);
        }

        preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        editor = preferences.edit();

        if (preferences.getString("CO_AUTH_TOKEN", null) != null){
            new agoraValidateToken().execute();
        } else {
            new agoraTokenRequest().execute(authURL);
        }

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                setResult(RESULT_CANCELED);
                //fingerprint.turnOffReader();
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        //fingerprint.scan(this, printHandler, updateHandler);
        super.onStart();
    }

    @Override
    protected void onDestroy() {
        fingerprint.turnOffReader();
        super.onDestroy();
    }

    public String img2template(byte[] image) {

        imageTemplate = new FingerprintTemplate()
            .dpi(512)
            .create(image);
        return imageTemplate.serialize();
    }

    Handler updateHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            scannerStatus = msg.getData().getInt("status");
            tvError.setText("");
            switch (scannerStatus) {
                case Status.INITIALISED:
                    tvStatus.setText("Setting up reader");
                    break;
                case Status.SCANNER_POWERED_ON:
                    tvStatus.setText("Reader powered on");
                    break;
                case Status.READY_TO_SCAN:
                    tvStatus.setText("Ready to scan finger");
                    break;
                case Status.FINGER_DETECTED:
                    tvStatus.setText("Finger detected");
                    break;
                case Status.RECEIVING_IMAGE:
                    tvStatus.setText("Receiving image");
                    break;
                case Status.FINGER_LIFTED:
                    tvStatus.setText("Finger has been lifted off reader");
                    fingerprint.scan(ScanActivity.this, printHandler, updateHandler);
                case Status.SCANNER_POWERED_OFF:
                    //fingerprint.scan( wkContext, printHandler, updateHandler);

                    break;
                case Status.SUCCESS:
                    tvStatus.setText("Fingerprint successfully captured");
                    break;
                case Status.ERROR:
                    tvStatus.setText("Error");
                    tvError.setText(msg.getData().getString("errorMessage"));
                    break;
                default:
                    tvStatus.setText(String.valueOf(scannerStatus));
                    tvError.setText(msg.getData().getString("errorMessage"));
                    break;

            }
        }
    };

    Handler printHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            //byte[] image;
            Bitmap bm;

            String errorMessage = "empty";
            int status = msg.getData().getInt("status");
            //Intent intent = new Intent();
            //intent.putExtra("status", status);
            if (status == Status.SUCCESS) {

                image = msg.getData().getByteArray("img");
                bm = BitmapFactory.decodeByteArray(image, 0, image.length);
                //ivFinger.setImageBitmap(bm);
                //intent.putExtra("img", image);
                new agoraBioSendTemplate().execute(img2template(image));

                boolean registration_mode = preferences.getBoolean("registration_mode", false);
                if (registration_mode) {
                    new agoraBioSendImage().execute();
                }

            } else {
                errorMessage = msg.getData().getString("errorMessage");
                //intent.putExtra("errorMessage", errorMessage);
            }
            setResult(RESULT_OK);
            //finish();
        }
    };

    private class agoraTokenRequest extends AsyncTask<String, Void, Void> {

        // Required initialization

        private ProgressDialog Dialog = new ProgressDialog(ScanActivity.this);
        private String data = null;
        private String Content = null;


        protected void onPreExecute() {
            // NOTE: You can call UI Element here.

            //Start Progress Dialog (Message)

            Dialog.setMessage("Please wait..");
            Dialog.show();
            try{
                // Set Request parameter
                data ="username="+ URLEncoder.encode(preferences.getString("app_user", null), "UTF-8") +"&password="+ URLEncoder.encode(preferences.getString("app_pass", null),"UTF-8");

            } catch (UnsupportedEncodingException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        }

        // Call after onPreExecute method
        protected Void doInBackground(String... urls) {

            /************ Make Post Call To Web Server ***********/
            BufferedReader reader=null;
            HttpURLConnection connection;

            // Send data
            try
            {

                // Defined URL  where to send data
                URL url = new URL(urls[0]);

                // Send POST data request

                connection = (HttpURLConnection) url.openConnection();
                connection.setDoOutput(true);
                connection.setRequestMethod("POST");
                OutputStreamWriter wr = new OutputStreamWriter(connection.getOutputStream());

                wr.write( data );
                wr.flush();


                // Get the server response
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line = null;

                // Read Server Response
                while((line = reader.readLine()) != null)
                {
                    // Append server response in string
                    sb.append(line);
                }

                // Append Server Response To Content String
                Content = sb.toString();
            }
            catch(Exception ex)
            {
                ex.printStackTrace();
                Log.v("AUTH", "ERROR REQUESTING:"+ ex.toString());
            }
            finally
            {
                try
                {

                    reader.close();
                }

                catch(Exception ex) {}
            }
            return null;
        }

        protected void onPostExecute(Void unused) {
            // NOTE: You can call UI Element here.

            // Close progress dialog
            Dialog.dismiss();

            if (Content != null) {
                /****************** Start Parse Response JSON Data *************/
                JSONObject jsonResponse;

                try {

                    /****** Creates a new JSONObject with name/value mappings from the JSON string. ********/
                    jsonResponse = new JSONObject(Content);
                    editor.putString("CO_AUTH_TOKEN", jsonResponse.getString("CO_AUTH_TOKEN"));
                    editor.putString("ID_USUARIO", jsonResponse.getString("ID_USUARIO"));
                    editor.commit();

                    Toast.makeText(getApplicationContext(), "User Authorized", Toast.LENGTH_SHORT).show();

                    Log.v("JSON RSPONSE", Content.toString());
                } catch (JSONException e) {
                    Toast.makeText(getApplicationContext(), "Error Authorizing User", Toast.LENGTH_SHORT).show();
                    editor.putString("CO_AUTH_TOKEN", null);
                    editor.putString("ID_USUARIO", null);
                    editor.commit();
                    Log.v("AUTH", "ERROR PARSING JSON:"+ Content.toString());
                    e.printStackTrace();
                }
            }else{
                Toast.makeText(getApplicationContext(), "Error Authorizing User", Toast.LENGTH_SHORT).show();
                editor.putString("CO_AUTH_TOKEN", null);
                editor.putString("ID_USUARIO", null);
                editor.commit();
            }
        }

    }

    private class agoraValidateToken extends AsyncTask<String, Void, Void> {

        // Required initialization

        private ProgressDialog Dialog = new ProgressDialog(ScanActivity.this);
        private String data = null;
        private String Content = null;


        protected void onPreExecute() {
            // NOTE: You can call UI Element here.

            //Start Progress Dialog (Message)

            Dialog.setMessage("Please wait..");
            Dialog.show();

        }

        // Call after onPreExecute method
        protected Void doInBackground(String... urls) {

            /************ Make Post Call To Web Server ***********/
            BufferedReader reader=null;

            // Send data
            try
            {

                // Defined URL  where to send data
                String CO_AUTH_TOKEN = preferences.getString("CO_AUTH_TOKEN", "-1");
                String ID_USUARIO =  preferences.getString("ID_USUARIO", "-1");
                URL url = new URL(validateUrl +"/"+ ID_USUARIO +"/"+ CO_AUTH_TOKEN);

                // Send POST data request

                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");

                // Get the server response
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line = null;

                // Read Server Response
                while((line = reader.readLine()) != null)
                {
                    // Append server response in string
                    sb.append(line);
                }

                // Append Server Response To Content String
                Content = sb.toString();
            }
            catch(Exception ex)
            {
                ex.printStackTrace();
            }
            finally
            {
                try
                {
                    reader.close();
                }

                catch(Exception ex) {}
            }
            return null;
        }

        protected void onPostExecute(Void unused) {
            // NOTE: You can call UI Element here.

            // Close progress dialog
            if(Content.toString().equals("0")){
                new agoraTokenRequest().execute(authURL);
            }else {
                Toast.makeText(getApplicationContext(), "Token Validated", Toast.LENGTH_SHORT).show();
            }
            Dialog.dismiss();
            Log.v("validation respone", Content.toString());
        }

    }

    private class agoraBioSendTemplate extends AsyncTask<String, Void, Void> {

        // Required initialization

        private ProgressDialog Dialog = new ProgressDialog(ScanActivity.this);
        private String data = null;
        private String Content = null;


        protected void onPreExecute() {
            // NOTE: You can call UI Element here.

            //Start Progress Dialog (Message)

            Dialog.setMessage("Please wait..");
            Dialog.show();
            try{
                // Set Request parameter
                data ="username="+ URLEncoder.encode(preferences.getString("app_user", null), "UTF-8") +"&password="+ URLEncoder.encode(preferences.getString("app_pass", null),"UTF-8");

            } catch (UnsupportedEncodingException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        }

        // Call after onPreExecute method
        protected Void doInBackground(String... urls) {

            /************ Make Post Call To Web Server ***********/
            BufferedReader reader=null;
            HttpURLConnection connection;

            // Send data
            try
            {

                // Defined URL  where to send data
                URL url = null;
                boolean registration_mode = preferences.getBoolean("registration_mode",false);

                if(registration_mode){
                    url = new URL(bioSendUrl +"/"+ txEmpleado +"/template/"+ preferences.getString("CO_AUTH_TOKEN", "-1"));
                }else{
                    url = new URL(bioQueryUrl +"/"+ txEmpleado +"/template/"+ preferences.getString("CO_AUTH_TOKEN", "-1"));
                }

                // Send POST data request

                connection = (HttpURLConnection) url.openConnection();
                connection.setDoOutput(true);
                connection.setRequestMethod("POST");
                OutputStreamWriter wr = new OutputStreamWriter(connection.getOutputStream());
                //DataOutputStream wr = new DataOutputStream(connection.getOutputStream());

                wr.write( urls[0] );
                wr.flush();


                // Get the server response
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line = null;

                // Read Server Response
                while((line = reader.readLine()) != null)
                {
                    // Append server response in string
                    sb.append(line);
                }

                // Append Server Response To Content String
                Content = sb.toString();
            }
            catch(Exception ex)
            {
                ex.printStackTrace();
                Log.v("AUTH", "ERROR REQUESTING:"+ ex.toString());
            }
            finally
            {
                try
                {

                    reader.close();
                }

                catch(Exception ex) {}
            }
            return null;
        }

        protected void onPostExecute(Void unused) {
            // NOTE: You can call UI Element here.

            // Close progress dialog
            Dialog.dismiss();

            if (Content != null) {
                /****************** Start Parse Response JSON Data *************/
                JSONObject jsonResponse;

                try {
                    jsonResponse = new JSONObject(Content);

                    int minScore = jsonResponse.getInt("NU_MIN_SCORE");

                    FingerprintTemplate probe = new FingerprintTemplate()
                        .deserialize(jsonResponse.getString("TEMPLATE"));
                    FingerprintMatcher matcher = new FingerprintMatcher()
                            .index(probe);
                    score = matcher.match(imageTemplate);

                    Log.v("Compare Score", new Double(score).toString());
                    if( score >= minScore ) {
                        Toast.makeText(getApplicationContext(), "Identity Confirmed", Toast.LENGTH_SHORT).show();


                        boolean registration_mode = preferences.getBoolean("registration_mode",false);
                        if(!registration_mode) {
                            MN_ESTIPENDIO = jsonResponse.getString("MN_ESTIPENDIO");
                            NB_EMPLEADO = jsonResponse.getString("NB_EMPLEADO");

                            new agoraBioSendPonche().execute();
                        }
                    }else{
                        Toast.makeText(getApplicationContext(), "Unable to confirm identity, please try again", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(getApplicationContext(), preferences.getString("CO_AUTH_TOKEN", "-1"), Toast.LENGTH_SHORT).show();
                    //editor.putString("CO_AUTH_TOKEN", null);
                    //editor.putString("ID_USUARIO", null);
                    //editor.commit();
                    Log.v("AUTH", "ERROR PARSING JSON:"+ Content.toString());
                    e.printStackTrace();
                }
            }else{
                Toast.makeText(getApplicationContext(), preferences.getString("CO_AUTH_TOKEN", "-1"), Toast.LENGTH_SHORT).show();
                //editor.putString("CO_AUTH_TOKEN", null);
                //editor.putString("ID_USUARIO", null);
                //editor.commit();
            }
        }

    }
    private class agoraBioSendImage extends AsyncTask<String, Void, Void> {

        // Required initialization

        private ProgressDialog Dialog = new ProgressDialog(ScanActivity.this);
        private String data = null;
        private String Content = null;


        protected void onPreExecute() {
            // NOTE: You can call UI Element here.

            //Start Progress Dialog (Message)

            Dialog.setMessage("Please wait..");
            Dialog.show();
            try{
                // Set Request parameter
                data ="username="+ URLEncoder.encode(preferences.getString("app_user", null), "UTF-8") +"&password="+ URLEncoder.encode(preferences.getString("app_pass", null),"UTF-8");

            } catch (UnsupportedEncodingException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        }

        // Call after onPreExecute method
        protected Void doInBackground(String... urls) {

            /************ Make Post Call To Web Server ***********/
            BufferedReader reader=null;
            HttpURLConnection connection;

            // Send data
            try
            {

                // Defined URL  where to send data
                URL url = null;
                boolean registration_mode = preferences.getBoolean("registration_mode",false);

                if(registration_mode){
                    url = new URL(bioSendUrl +"/"+ txEmpleado +"/image/"+ preferences.getString("CO_AUTH_TOKEN", "-1"));
                }else{
                    url = new URL(bioQueryUrl +"/"+ txEmpleado +"/image/"+ preferences.getString("CO_AUTH_TOKEN", "-1"));
                }

                // Send POST data request

                connection = (HttpURLConnection) url.openConnection();
                connection.setDoOutput(true);
                connection.setRequestMethod("POST");
                DataOutputStream wr = new DataOutputStream(connection.getOutputStream());

                wr.write( image );
                wr.flush();


                // Get the server response
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line = null;

                // Read Server Response
                while((line = reader.readLine()) != null)
                {
                    // Append server response in string
                    sb.append(line);
                }

                // Append Server Response To Content String
                Content = sb.toString();
            }
            catch(Exception ex)
            {
                ex.printStackTrace();
                Log.v("AUTH", "ERROR REQUESTING:"+ ex.toString());
            }
            finally
            {
                try
                {

                    reader.close();
                }

                catch(Exception ex) {}
            }
            return null;
        }

        protected void onPostExecute(Void unused) {
            // NOTE: You can call UI Element here.

            // Close progress dialog
            Dialog.dismiss();

            if (Content != null) {
                try {
                    Log.v("Image upload", Content);
                    //Toast.makeText(getApplicationContext(), Content, Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(getApplicationContext(), preferences.getString("CO_AUTH_TOKEN", "-1"), Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                }
            }else{
                Toast.makeText(getApplicationContext(), preferences.getString("CO_AUTH_TOKEN", "-1"), Toast.LENGTH_SHORT).show();
            }
        }

    }
    private class agoraBioSendPonche extends AsyncTask<String, Void, Void> {

        // Required initialization

        private ProgressDialog Dialog = new ProgressDialog(ScanActivity.this);
        private String data = null;
        private String Content = null;


        protected void onPreExecute() {
            // NOTE: You can call UI Element here.

            //Start Progress Dialog (Message)

            Dialog.setMessage("Please wait..");
            Dialog.show();
            try{

            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        }

        // Call after onPreExecute method
        protected Void doInBackground(String... urls) {

            /************ Make Post Call To Web Server ***********/
            BufferedReader reader=null;
            HttpURLConnection connection;

            // Send data
            try
            {

                // Defined URL  where to send data
                URL url = null;
                boolean registration_mode = preferences.getBoolean("registration_mode",false);

                if(!registration_mode){
                    url = new URL(sendPoncheUrl +"/"+ txEmpleado +"/"+
                            preferences.getString("ID_USUARIO", "-1") +"/"+
                            new Double(score).toString() +"/"+
                            preferences.getString("CO_AUTH_TOKEN", "-1"));
                }

                // Send POST data request

                connection = (HttpURLConnection) url.openConnection();
                connection.setDoOutput(true);
                connection.setRequestMethod("POST");
                DataOutputStream wr = new DataOutputStream(connection.getOutputStream());

                wr.write( image );
                wr.flush();


                // Get the server response
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line = null;

                // Read Server Response
                while((line = reader.readLine()) != null)
                {
                    // Append server response in string
                    sb.append(line);
                }

                // Append Server Response To Content String
                Content = sb.toString();
            }
            catch(Exception ex)
            {
                ex.printStackTrace();
                Log.v("AUTH", "ERROR REQUESTING:"+ ex.toString());
            }
            finally
            {
                try
                {

                    reader.close();
                }

                catch(Exception ex) {}
            }
            return null;
        }

        protected void onPostExecute(Void unused) {
            // NOTE: You can call UI Element here.

            // Close progress dialog
            Dialog.dismiss();

            Log.v("Ponche Sent", Content);
            if (Content != null) {
                try {
                    AlertDialog.Builder alertadd = new AlertDialog.Builder(ScanActivity.this);
                    LayoutInflater factory = LayoutInflater.from(ScanActivity.this);
                    final View view = factory.inflate(R.layout.dialog_scanok, null);
                    alertadd.setView(view);
                    alertadd.setPositiveButton("Continue", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dlg, int sumthin) {

                        }
                    });

                    alertadd.show();

                    TextView tvNbEmp = view.findViewById(R.id.tv_confirmedName);
                    TextView tvMnEsp = view.findViewById(R.id.tv_confirmedAmount);

                    tvNbEmp.setText(NB_EMPLEADO);
                    tvMnEsp.setText(MN_ESTIPENDIO);

                    //ImageView iv = view.findViewById(R.id.iv_scanok); // id of your imageView element
                    //Bitmap itmap = BitmapFactory.decodeByteArray(image, 0, image.length);
                    //iv.setImageBitmap(itmap);


                } catch (Exception e) {
                    Toast.makeText(getApplicationContext(), preferences.getString("CO_AUTH_TOKEN", "-1"), Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                }
            }else{
                Toast.makeText(getApplicationContext(), preferences.getString("CO_AUTH_TOKEN", "-1"), Toast.LENGTH_SHORT).show();
            }
        }

    }

}
