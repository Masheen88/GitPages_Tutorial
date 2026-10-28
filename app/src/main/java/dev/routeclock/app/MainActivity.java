package dev.routeclock.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.AlarmClock;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** RouteClock: private, single-user route ETA and departure-alarm planner. */
public class MainActivity extends Activity {
    private static final int NAVY = Color.rgb(11, 19, 38);
    private static final int PANEL = Color.rgb(23, 36, 61);
    private static final int WHITE = Color.rgb(242, 247, 255);
    private static final int MUTED = Color.rgb(171, 187, 210);
    private static final int CYAN = Color.rgb(49, 205, 197);
    private static final int LOCATION_PERMISSION = 1401;

    private EditText fromField, toField;
    private TextView departText, output, detail, alarmPreview;
    private Button routeButton, alarmButton;
    private Spinner bufferSpinner;
    private final Calendar departure = Calendar.getInstance();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private double toLat = Double.NaN, toLon = Double.NaN;
    private LocationManager locationManager;
    private LocationListener pendingLocationListener;
    private int activeLocationRequest = 0;
    private boolean routeReady = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAVY);
        departure.add(Calendar.MINUTE, 30);
        departure.set(Calendar.SECOND, 0);
        buildUi();
    }

    private int dp(int n) { return (int) (getResources().getDisplayMetrics().density * n + 0.5f); }

    private GradientDrawable shape(int fill, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(15));
        if (stroke != 0) d.setStroke(dp(1), stroke);
        return d;
    }

    private TextView text(String content, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(content);
        t.setTextColor(color);
        t.setTextSize(size);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    private void add(LinearLayout box, View item, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(top);
        box.addView(item, p);
    }

    private Button button(String label, boolean primary) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextSize(15);
        b.setTextColor(primary ? NAVY : WHITE);
        b.setBackground(shape(primary ? CYAN : PANEL, primary ? 0 : Color.rgb(57, 78, 112)));
        b.setMinHeight(dp(52));
        return b;
    }

    private EditText field(String hint) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setTextColor(WHITE);
        e.setHintTextColor(MUTED);
        e.setTextSize(16);
        e.setHint(hint);
        e.setPadding(dp(15), dp(15), dp(15), dp(15));
        e.setBackground(shape(PANEL, Color.rgb(57, 78, 112)));
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        return e;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private void inRow(LinearLayout r, View v, float weight, int left) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(52), weight);
        p.leftMargin = dp(left);
        r.addView(v, p);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(NAVY);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(28), dp(22), dp(30));
        scroll.addView(root);
        setContentView(scroll);

        add(root, text("ROUTECLOCK  •  DRIVE PLANNER", 12, CYAN, true), 0);
        add(root, text("When will I arrive?", 29, WHITE, true), 8);
        add(root, text("Pick when you'll leave. Get an ETA and set a real phone alarm before it's time to go.", 14, MUTED, false), 9);

        add(root, text("FROM", 12, MUTED, true), 26);
        fromField = field("Address, city, or latitude,longitude");
        fromField.setText(getPreferences(0).getString("from", ""));
        add(root, fromField, 7);
        Button locate = button("◎  Use current location as start", false);
        add(root, locate, 8);
        locate.setOnClickListener(v -> requestCurrentLocation());

        add(root, text("TO", 12, MUTED, true), 18);
        toField = field("Enter a destination");
        toField.setText(getPreferences(0).getString("to", ""));
        add(root, toField, 7);
        Button reverse = button("⇅  Swap start & destination (return trip)", false);
        add(root, reverse, 8);
        reverse.setOnClickListener(v -> {
            String a = fromField.getText().toString();
            fromField.setText(toField.getText().toString());
            toField.setText(a);
            invalidateRoute();
        });

        add(root, text("DEPARTURE DATE & TIME", 12, MUTED, true), 24);
        LinearLayout dates = row();
        Button dateButton = button("Choose date", false);
        Button timeButton = button("Choose time", false);
        inRow(dates, dateButton, 1, 0);
        inRow(dates, timeButton, 1, 8);
        add(root, dates, 8);
        dateButton.setOnClickListener(v -> new DatePickerDialog(this, (picker, year, month, day) -> {
            departure.set(Calendar.YEAR, year);
            departure.set(Calendar.MONTH, month);
            departure.set(Calendar.DAY_OF_MONTH, day);
            refreshDeparture();
        }, departure.get(Calendar.YEAR), departure.get(Calendar.MONTH), departure.get(Calendar.DAY_OF_MONTH)).show());
        timeButton.setOnClickListener(v -> new TimePickerDialog(this, (picker, hour, minute) -> {
            departure.set(Calendar.HOUR_OF_DAY, hour);
            departure.set(Calendar.MINUTE, minute);
            refreshDeparture();
        }, departure.get(Calendar.HOUR_OF_DAY), departure.get(Calendar.MINUTE), false).show());
        departText = text("", 17, WHITE, true);
        add(root, departText, 11);

        add(root, text("ALARM BEFORE DEPARTURE", 12, MUTED, true), 24);
        bufferSpinner = new Spinner(this);
        String[] buffers = {"At departure time", "5 minutes before", "10 minutes before", "15 minutes before", "30 minutes before", "60 minutes before"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, buffers);
        bufferSpinner.setAdapter(adapter);
        bufferSpinner.setBackground(shape(PANEL, Color.rgb(57, 78, 112)));
        bufferSpinner.setPadding(dp(10), dp(5), dp(10), dp(5));
        int stored = getPreferences(0).getInt("buffer", 1);
        bufferSpinner.setSelection(Math.min(Math.max(stored, 0), buffers.length - 1));
        add(root, bufferSpinner, 8);
        alarmPreview = text("", 14, MUTED, false);
        add(root, alarmPreview, 9);
        bufferSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                getPreferences(0).edit().putInt("buffer", position).apply();
                refreshAlarmPreview();
            }
            public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });

        routeButton = button("Calculate arrival time", true);
        add(root, routeButton, 23);
        routeButton.setOnClickListener(v -> calculate());
        output = text("Arrival time appears here after calculating.", 24, WHITE, true);
        add(root, output, 23);
        detail = text("Route estimates use OpenStreetMap road data and do not include predicted traffic at your future departure time.", 14, MUTED, false);
        add(root, detail, 8);

        alarmButton = button("⏰  Set departure alarm", false);
        add(root, alarmButton, 24);
        alarmButton.setOnClickListener(v -> setAlarm());
        Button maps = button("↗  Open route in Google Maps", false);
        add(root, maps, 9);
        maps.setOnClickListener(v -> openMaps());
        add(root, text("Route: OSRM · Map data © OpenStreetMap contributors. Clock alarm uses your phone's alarm app. Internet required for route estimates.", 12, MUTED, false), 25);
        refreshDeparture();
    }

    private int bufferMinutes() {
        int[] values = {0, 5, 10, 15, 30, 60};
        return values[bufferSpinner.getSelectedItemPosition() < 0 ? 1 : bufferSpinner.getSelectedItemPosition()];
    }

    private String format(Calendar c) {
        DateFormat f = new SimpleDateFormat("EEE, MMM d • h:mm a", Locale.getDefault());
        return f.format(c.getTime());
    }

    private Calendar alarmWhen() {
        Calendar c = (Calendar) departure.clone();
        c.add(Calendar.MINUTE, -bufferMinutes());
        return c;
    }

    private void refreshDeparture() {
        departText.setText("Leaving: " + format(departure));
        refreshAlarmPreview();
        invalidateRoute();
    }

    private void refreshAlarmPreview() {
        if (alarmPreview != null) alarmPreview.setText("Alarm will ring: " + format(alarmWhen()));
    }

    private void invalidateRoute() {
        routeReady = false;
        toLat = Double.NaN;
        toLon = Double.NaN;
        if (output != null) output.setText("Calculate to see your arrival time");
    }

    private void calculate() {
        String from = fromField.getText().toString().trim();
        String to = toField.getText().toString().trim();
        if (from.isEmpty() || to.isEmpty()) {
            toast("Enter both a start and a destination.");
            return;
        }
        if (departure.getTimeInMillis() < System.currentTimeMillis()) {
            toast("Choose a future departure date and time.");
            return;
        }
        getPreferences(0).edit().putString("from", from).putString("to", to).apply();
        routeButton.setEnabled(false);
        output.setText("Finding your route…");
        detail.setText("Looking up addresses and calculating driving time.");
        dismissKeyboard();
        worker.execute(() -> {
            try {
                Geo a = geocode(from);
                Geo b = geocode(to);
                String url = String.format(Locale.US,
                    "https://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=false",
                    a.lon, a.lat, b.lon, b.lat);
                JSONObject data = new JSONObject(fetch(url));
                JSONArray routes = data.optJSONArray("routes");
                if (routes == null || routes.length() == 0) throw new Exception("No drivable route was found.");
                JSONObject route = routes.getJSONObject(0);
                long seconds = Math.round(route.getDouble("duration"));
                double miles = route.getDouble("distance") / 1609.344;
                ui.post(() -> {
                    routeButton.setEnabled(true);
                    Calendar arrival = (Calendar) departure.clone();
                    arrival.add(Calendar.SECOND, (int) seconds);
                    long mins = (seconds + 59) / 60;
                    long hrs = mins / 60;
                    output.setText("Arrive around " + format(arrival));
                    detail.setText(String.format(Locale.US,
                        "Estimated drive: %s%d min  •  %.1f miles\n%s → %s\nTravel times are estimates; future traffic isn't included.",
                        hrs > 0 ? hrs + " hr " : "", mins % 60, miles, a.name, b.name));
                    toLat = b.lat;
                    toLon = b.lon;
                    routeReady = true;
                });
            } catch (Exception e) {
                ui.post(() -> {
                    routeButton.setEnabled(true);
                    output.setText("Unable to calculate route");
                    detail.setText(e.getMessage() + "\nTry a more specific address, or coordinates such as 38.627,-90.199.");
                });
            }
        });
    }

    private static class Geo {
        double lat, lon;
        String name;
        Geo(double latitude, double longitude, String label) { lat = latitude; lon = longitude; name = label; }
    }

    private Geo geocode(String text) throws Exception {
        // Coordinate entry avoids any address-search ambiguity and is useful for GPS starts.
        if (text.matches("\\s*-?\\d+(?:\\.\\d+)?\\s*,\\s*-?\\d+(?:\\.\\d+)?\\s*")) {
            String[] coords = text.split(",");
            double lat = Double.parseDouble(coords[0].trim());
            double lon = Double.parseDouble(coords[1].trim());
            if (Math.abs(lat) > 90 || Math.abs(lon) > 180) throw new Exception("Invalid GPS coordinates.");
            return new Geo(lat, lon, "GPS coordinates");
        }
        // Photon is OpenStreetMap-based autocomplete/geocoding. Only queried on user tap.
        String url = "https://photon.komoot.io/api/?limit=1&q=" + URLEncoder.encode(text, "UTF-8");
        JSONObject result = new JSONObject(fetch(url));
        JSONArray features = result.optJSONArray("features");
        if (features == null || features.length() == 0) throw new Exception("Address not found: " + text);
        JSONObject feature = features.getJSONObject(0);
        JSONArray c = feature.getJSONObject("geometry").getJSONArray("coordinates");
        JSONObject properties = feature.optJSONObject("properties");
        String name = text;
        if (properties != null) name = properties.optString("name", text) + ", " + properties.optString("city", properties.optString("state", ""));
        return new Geo(c.getDouble(1), c.getDouble(0), name);
    }

    private String fetch(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        try {
            connection.setConnectTimeout(14000);
            connection.setReadTimeout(14000);
            connection.setRequestProperty("User-Agent", "RouteClockPersonal/1.0 (Android; single-user routing planner)");
            connection.setRequestProperty("Accept", "application/json");
            if (connection.getResponseCode() != 200) throw new Exception("Routing server unavailable (HTTP " + connection.getResponseCode() + ").");
            try (InputStream stream = connection.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"))) {
                StringBuilder body = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) body.append(line);
                return body.toString();
            }
        } finally { connection.disconnect(); }
    }

    private void setAlarm() {
        Calendar alarm = alarmWhen();
        long delta = alarm.getTimeInMillis() - System.currentTimeMillis();
        if (delta <= 0) { toast("The alarm time is in the past. Choose a later departure."); return; }
        if (delta > 24L * 60 * 60 * 1000) {
            // Clock apps only receive an hour/minute, not a future date.
            new AlertDialog.Builder(this)
                .setTitle("Alarm more than a day away")
                .setMessage("Your phone's Clock app can set a one-time alarm for the next occurrence of a time, but this app can't pass it a future date. Set this alarm within 24 hours of your trip.")
                .setPositiveButton("OK", null).show();
            return;
        }
        try {
            Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM);
            intent.putExtra(AlarmClock.EXTRA_HOUR, alarm.get(Calendar.HOUR_OF_DAY));
            intent.putExtra(AlarmClock.EXTRA_MINUTES, alarm.get(Calendar.MINUTE));
            intent.putExtra(AlarmClock.EXTRA_MESSAGE, "RouteClock — leave for " + toField.getText().toString().trim());
            intent.putExtra(AlarmClock.EXTRA_SKIP_UI, false);
            startActivity(intent);
        } catch (Exception e) { toast("No compatible Clock app was found on this phone."); }
    }

    private void openMaps() {
        String origin = fromField.getText().toString().trim();
        String destination = toField.getText().toString().trim();
        if (origin.isEmpty() || destination.isEmpty()) { toast("Enter both addresses first."); return; }
        try {
            String href = "https://www.google.com/maps/dir/?api=1&travelmode=driving&origin="
                + URLEncoder.encode(origin, "UTF-8") + "&destination=" + URLEncoder.encode(destination, "UTF-8");
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(href)));
        } catch (Exception e) { toast("Could not open the map application."); }
    }

    private void requestCurrentLocation() {
        activeLocationRequest = 1;
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
            && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION);
            return;
        }
        locateNow();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == LOCATION_PERMISSION) {
            boolean allowed = false;
            for (int r : results) if (r == PackageManager.PERMISSION_GRANTED) allowed = true;
            if (allowed) locateNow(); else toast("Allow location access or enter a starting address manually.");
        }
    }

    @SuppressWarnings("MissingPermission")
    private void locateNow() {
        try {
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (locationManager == null) throw new Exception("Location is unavailable.");
            Location best = null;
            for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                if (!locationManager.isProviderEnabled(provider)) continue;
                Location l = locationManager.getLastKnownLocation(provider);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
            if (best != null && System.currentTimeMillis() - best.getTime() < 5 * 60 * 1000) {
                useLocation(best);
                return;
            }
            toast("Getting current GPS location…");
            String provider = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ? LocationManager.GPS_PROVIDER : LocationManager.NETWORK_PROVIDER;
            if (!locationManager.isProviderEnabled(provider)) throw new Exception("Enable Location on your phone.");
            pendingLocationListener = new LocationListener() {
                @Override public void onLocationChanged(Location location) {
                    if (activeLocationRequest == 1) useLocation(location);
                    stopLocation();
                }
                @Override public void onProviderEnabled(String name) { }
                @Override public void onProviderDisabled(String name) { }
                @Override public void onStatusChanged(String name, int status, Bundle extras) { }
            };
            locationManager.requestLocationUpdates(provider, 0, 0f, pendingLocationListener, Looper.getMainLooper());
            ui.postDelayed(() -> {
                if (activeLocationRequest == 1) { stopLocation(); toast("GPS timed out. Try again or enter an address."); }
            }, 15000);
        } catch (Exception e) { toast(e.getMessage()); }
    }

    private void useLocation(Location location) {
        fromField.setText(String.format(Locale.US, "%.6f,%.6f", location.getLatitude(), location.getLongitude()));
        activeLocationRequest = 0;
        invalidateRoute();
        toast("Current location added as start.");
    }

    private void stopLocation() {
        activeLocationRequest = 0;
        if (locationManager != null && pendingLocationListener != null) {
            locationManager.removeUpdates(pendingLocationListener);
            pendingLocationListener = null;
        }
    }

    private void dismissKeyboard() {
        try { ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(fromField.getWindowToken(), 0); }
        catch (Exception ignored) { }
    }

    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }

    @Override protected void onDestroy() {
        stopLocation();
        worker.shutdownNow();
        super.onDestroy();
    }
}