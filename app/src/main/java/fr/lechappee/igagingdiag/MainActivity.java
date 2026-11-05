package fr.lechappee.igagingdiag;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

/** Diagnostic only. Measures are kept exactly as transmitted by the HID keyboard. */
public class MainActivity extends Activity {
    private static final String ACTION_PERMISSION = "fr.lechappee.igagingdiag.USB_PERMISSION";
    private UsbManager manager;
    private TextView details, lastMeasurement, measurementHistory, log;
    private UsbDevice selected;
    private UsbDeviceConnection connection;
    private UsbInterface claimed;
    private volatile boolean reading;
    private Thread worker;
    private final StringBuilder technicalHistory = new StringBuilder();
    private final StringBuilder acceptedMeasurements = new StringBuilder();
    private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm:ss.SSS", Locale.FRANCE);
    private boolean technicalLogEnabled = true;
    private final MeasurementDecoder measurementDecoder = new MeasurementDecoder();

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (ACTION_PERMISSION.equals(intent.getAction())) {
                boolean permitted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                technical("Autorisation USB : " + (permitted ? "ACCORDEE" : "REFUSEE"));
                if (permitted) startRead();
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(intent.getAction())) {
                technical("Peripherique USB debranche");
                stopRead();
                scan();
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction())) {
                scan();
            }
        }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        manager = (UsbManager) getSystemService(USB_SERVICE);

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(18, 22, 18, 12);

        TextView title = new TextView(this);
        title.setText("iGaging - Diagnostic USB");
        title.setTextSize(22);
        title.setTypeface(null, Typeface.BOLD);
        outer.addView(title);

        TextView help = new TextView(this);
        String configuredTitle = getIntent().getStringExtra("title");
        int configuredCount = getIntent().getIntExtra("spokeCount", 0);
        double configuredRightTarget = getIntent().getDoubleExtra("rightTarget", 0);
        double configuredRatio = getIntent().getDoubleExtra("leftRatio", 0);
        String sessionSummary = configuredTitle == null ? ""
            : "\nMontage : " + configuredTitle + " | " + configuredCount + " rayons"
            + String.format(Locale.FRANCE, " | Cibles D %.2f kgf / G %.2f kgf",
                configuredRightTarget, configuredRightTarget * configuredRatio / 100.0);
        help.setText("Brancher iGaging avec USB OTG, detecter puis autoriser.\n"
            + "Les mesures sont validees uniquement a la reception de ENTER." + sessionSummary);
        outer.addView(help);

        Button scan = new Button(this);
        scan.setText("1 - Detecter USB");
        scan.setOnClickListener(v -> scan());
        outer.addView(scan);
        Button capture = new Button(this);
        capture.setText("2 - Autoriser et capturer HID");
        capture.setOnClickListener(v -> askPermission());
        outer.addView(capture);
        Button stop = new Button(this);
        stop.setText("Arreter la capture");
        stop.setOnClickListener(v -> stopRead());
        outer.addView(stop);

        lastMeasurement = new TextView(this);
        lastMeasurement.setText("--");
        lastMeasurement.setTextSize(48);
        lastMeasurement.setGravity(android.view.Gravity.CENTER);
        lastMeasurement.setTypeface(null, Typeface.BOLD);
        outer.addView(lastMeasurement, new LinearLayout.LayoutParams(-1, 100));

        TextView historyTitle = new TextView(this);
        historyTitle.setText("Historique chronologique");
        historyTitle.setTypeface(null, Typeface.BOLD);
        outer.addView(historyTitle);
        ScrollView historyScroll = new ScrollView(this);
        measurementHistory = new TextView(this);
        measurementHistory.setTextIsSelectable(true);
        measurementHistory.setTextSize(16);
        measurementHistory.setTypeface(Typeface.MONOSPACE);
        historyScroll.addView(measurementHistory);
        outer.addView(historyScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        CheckBox technicalToggle = new CheckBox(this);
        technicalToggle.setText("Journal technique (diagnostic)");
        technicalToggle.setChecked(true);
        technicalToggle.setOnCheckedChangeListener((button, checked) -> {
            technicalLogEnabled = checked;
            if (checked) refreshLog();
            else log.setText("");
        });
        outer.addView(technicalToggle);
        Button clear = new Button(this);
        clear.setText("Effacer historique et journal");
        clear.setOnClickListener(v -> {
            acceptedMeasurements.setLength(0);
            technicalHistory.setLength(0);
            measurementHistory.setText("");
            log.setText("");
            lastMeasurement.setText("--");
        });
        outer.addView(clear);

        details = new TextView(this);
        details.setTextIsSelectable(true);
        details.setTypeface(Typeface.MONOSPACE);
        details.setTextSize(12);
        outer.addView(details);
        ScrollView logScroll = new ScrollView(this);
        log = new TextView(this);
        log.setTextIsSelectable(true);
        log.setTextSize(12);
        log.setTypeface(Typeface.MONOSPACE);
        logScroll.addView(log);
        outer.addView(logScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(outer);
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        scan();
    }

    private void scan() {
        stopRead();
        selected = null;
        StringBuilder info = new StringBuilder("Peripheriques connectes : ")
            .append(manager.getDeviceList().size()).append('\n');
        for (UsbDevice device : manager.getDeviceList().values()) {
            if (selected == null) selected = device;
            info.append(String.format(Locale.US, "VID=%04X PID=%04X classe=%d (%s)\n",
                device.getVendorId(), device.getProductId(), device.getDeviceClass(), device.getDeviceName()));
            for (int i = 0; i < device.getInterfaceCount(); i++) {
                UsbInterface face = device.getInterface(i);
                info.append(String.format(Locale.US, "  Interface %d class=%d subclass=%d protocol=%d\n",
                    face.getId(), face.getInterfaceClass(), face.getInterfaceSubclass(), face.getInterfaceProtocol()));
                for (int j = 0; j < face.getEndpointCount(); j++) {
                    UsbEndpoint ep = face.getEndpoint(j);
                    info.append(String.format(Locale.US, "    EP=0x%02X direction=%s type=%d paquet=%d\n",
                        ep.getAddress(), ep.getDirection() == UsbConstants.USB_DIR_IN ? "IN" : "OUT",
                        ep.getType(), ep.getMaxPacketSize()));
                }
            }
        }
        if (selected == null) info.append("Aucun USB detecte. Verifier OTG et cable.");
        details.setText(info.toString());
        technical("Detection terminee");
    }

    private void askPermission() {
        if (selected == null) {
            technical("Aucun peripherique selectionne");
            return;
        }
        if (manager.hasPermission(selected)) {
            startRead();
            return;
        }
        Intent intent = new Intent(ACTION_PERMISSION).setPackage(getPackageName());
        PendingIntent pending = PendingIntent.getBroadcast(this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        manager.requestPermission(selected, pending);
        technical("Demande d'autorisation envoyee");
    }

    private synchronized void startRead() {
        stopRead();
        if (selected == null || !manager.hasPermission(selected)) {
            technical("Autorisation manquante");
            return;
        }
        UsbInterface face = null;
        UsbEndpoint ep = null;
        for (int pass = 0; pass < 2 && ep == null; pass++) {
            for (int i = 0; i < selected.getInterfaceCount() && ep == null; i++) {
                UsbInterface f = selected.getInterface(i);
                if (pass == 0 && f.getInterfaceClass() != UsbConstants.USB_CLASS_HID) continue;
                for (int j = 0; j < f.getEndpointCount(); j++) {
                    UsbEndpoint candidate = f.getEndpoint(j);
                    if (candidate.getDirection() == UsbConstants.USB_DIR_IN
                        && (candidate.getType() == UsbConstants.USB_ENDPOINT_XFER_INT
                        || candidate.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK)) {
                        face = f;
                        ep = candidate;
                        break;
                    }
                }
            }
        }
        if (ep == null) {
            technical("Aucun endpoint de lecture IN utilisable");
            return;
        }
        connection = manager.openDevice(selected);
        if (connection == null) {
            technical("ECHEC openDevice : acces refuse par Android");
            return;
        }
        if (!connection.claimInterface(face, true)) {
            technical("ECHEC claimInterface : Android conserve peut-etre le clavier HID");
            connection.close();
            connection = null;
            return;
        }
        claimed = face;
        reading = true;
        final UsbEndpoint endpoint = ep;
        technical(String.format(Locale.US, "Capture demarree interface=%d EP=0x%02X",
            face.getId(), endpoint.getAddress()));
        worker = new Thread(() -> {
            byte[] buffer = new byte[Math.max(8, Math.min(endpoint.getMaxPacketSize(), 512))];
            while (reading && connection != null) {
                int count;
                try {
                    count = connection.bulkTransfer(endpoint, buffer, buffer.length, 250);
                } catch (Exception e) {
                    technical("Erreur USB : " + e.getMessage());
                    break;
                }
                if (count > 0) {
                    byte[] bytes = Arrays.copyOf(buffer, count);
                    StringBuilder hex = new StringBuilder();
                    for (byte b : bytes) hex.append(String.format(Locale.US, "%02X ", b & 0xff));
                    technical("USB HEX (" + count + ") : " + hex.toString().trim());
                }
            }
            technical("Boucle de capture arretee");
        }, "igaging-usb-reader");
        worker.start();
    }

    private synchronized void stopRead() {
        reading = false;
        if (connection != null) {
            try {
                if (claimed != null) connection.releaseInterface(claimed);
            } catch (Exception ignored) { }
            try { connection.close(); } catch (Exception ignored) { }
        }
        claimed = null;
        connection = null;
    }

    /** Reads Android key codes, independent of AZERTY/QWERTY, and consumes the HID events. */
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                MeasurementDecoder.DecodeResult result =
                    measurementDecoder.acceptKeyCode(event.getKeyCode());
                if (result.type == MeasurementDecoder.DecodeResult.Type.COMPLETE) {
                    acceptMeasurement(result.value);
                } else if (result.type == MeasurementDecoder.DecodeResult.Type.INVALID) {
                    technical("Mesure invalide ignoree avant ENTER : [" + result.value + "]");
                }
                technical("KEY keyCode=" + event.getKeyCode() + " scanCode=" + event.getScanCode()
                    + " source=" + event.getSource());
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void acceptMeasurement(String value) {
        String line = clock.format(new Date()) + "  " + value + '\n';
        acceptedMeasurements.append(line);
        runOnUiThread(() -> {
            lastMeasurement.setText(value);
            measurementHistory.setText(acceptedMeasurements.toString());
        });
        technical("Mesure valide : " + value);
    }

    private void technical(String line) {
        runOnUiThread(() -> {
            technicalHistory.append(clock.format(new Date())).append("  ").append(line).append('\n');
            if (technicalHistory.length() > 25000) {
                technicalHistory.delete(0, technicalHistory.length() - 18000);
            }
            refreshLog();
        });
    }

    private void refreshLog() {
        if (log != null && technicalLogEnabled) log.setText(technicalHistory.toString());
    }

    @Override public void onDestroy() {
        stopRead();
        unregisterReceiver(receiver);
        super.onDestroy();
    }
}
