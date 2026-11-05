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
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

/** Diagnostic only. No tensiometer calibration or automatic decimal reconstruction. */
public class MainActivity extends Activity {
    private static final String ACTION_PERMISSION = "fr.lechappee.igagingdiag.USB_PERMISSION";
    private UsbManager manager;
    private TextView details, log;
    private UsbDevice selected;
    private UsbDeviceConnection connection;
    private UsbInterface claimed;
    private volatile boolean reading;
    private Thread worker;
    private final StringBuilder history = new StringBuilder();
    private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm:ss.SSS", Locale.FRANCE);

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (ACTION_PERMISSION.equals(intent.getAction())) {
                boolean permitted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                append("Autorisation USB : " + (permitted ? "ACCORDÉE" : "REFUSÉE"));
                if (permitted) startRead();
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(intent.getAction())) {
                append("Périphérique USB débranché"); stopRead(); scan();
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
        title.setText("iGaging • Diagnostic USB"); title.setTextSize(22); title.setTypeface(null, Typeface.BOLD);
        outer.addView(title);
        TextView help = new TextView(this);
        help.setText("1. Brancher iGaging avec USB OTG\n2. Détecter puis autoriser\n3. Actionner la pédale et observer les paquets HEX et les touches");
        outer.addView(help);
        Button scan = new Button(this); scan.setText("1 — Détecter USB"); scan.setOnClickListener(v -> scan()); outer.addView(scan);
        Button capture = new Button(this); capture.setText("2 — Autoriser et capturer HID"); capture.setOnClickListener(v -> askPermission()); outer.addView(capture);
        Button stop = new Button(this); stop.setText("Arrêter la capture"); stop.setOnClickListener(v -> stopRead()); outer.addView(stop);
        Button clear = new Button(this); clear.setText("Effacer le journal"); clear.setOnClickListener(v -> { history.setLength(0); log.setText(""); }); outer.addView(clear);
        details = new TextView(this); details.setTextIsSelectable(true); details.setTypeface(Typeface.MONOSPACE); details.setTextSize(12);
        outer.addView(details);
        ScrollView scroll = new ScrollView(this);
        log = new TextView(this); log.setTextIsSelectable(true); log.setTextSize(12); log.setTypeface(Typeface.MONOSPACE);
        scroll.addView(log); outer.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(outer);
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_PERMISSION); filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        scan();
    }

    private void scan() {
        stopRead(); selected = null;
        StringBuilder info = new StringBuilder("Périphériques connectés : " + manager.getDeviceList().size() + "\n");
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
        if (selected == null) info.append("Aucun USB détecté. Vérifier OTG et câble.");
        details.setText(info.toString()); append("Détection terminée");
    }

    private void askPermission() {
        if (selected == null) { append("Aucun périphérique sélectionné"); return; }
        if (manager.hasPermission(selected)) { startRead(); return; }
        Intent intent = new Intent(ACTION_PERMISSION).setPackage(getPackageName());
        PendingIntent pending = PendingIntent.getBroadcast(this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        manager.requestPermission(selected, pending);
        append("Demande d'autorisation envoyée");
    }

    private synchronized void startRead() {
        stopRead();
        if (selected == null || !manager.hasPermission(selected)) { append("Autorisation manquante"); return; }
        UsbInterface face = null; UsbEndpoint ep = null;
        // Prefer a HID interface with an IN interrupt endpoint, but accept other IN endpoints.
        for (int pass = 0; pass < 2 && ep == null; pass++) {
            for (int i = 0; i < selected.getInterfaceCount() && ep == null; i++) {
                UsbInterface f = selected.getInterface(i);
                if (pass == 0 && f.getInterfaceClass() != UsbConstants.USB_CLASS_HID) continue;
                for (int j = 0; j < f.getEndpointCount(); j++) {
                    UsbEndpoint candidate = f.getEndpoint(j);
                    if (candidate.getDirection() == UsbConstants.USB_DIR_IN &&
                        (candidate.getType() == UsbConstants.USB_ENDPOINT_XFER_INT ||
                         candidate.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK)) {
                        face = f; ep = candidate; break;
                    }
                }
            }
        }
        if (ep == null) { append("Aucun endpoint de lecture IN utilisable"); return; }
        connection = manager.openDevice(selected);
        if (connection == null) { append("ÉCHEC openDevice : accès refusé par Android"); return; }
        if (!connection.claimInterface(face, true)) {
            append("ÉCHEC claimInterface : Android conserve peut-être le clavier HID");
            connection.close(); connection = null; return;
        }
        claimed = face; reading = true;
        final UsbEndpoint endpoint = ep;
        append(String.format(Locale.US, "Capture démarrée interface=%d EP=0x%02X", face.getId(), endpoint.getAddress()));
        worker = new Thread(() -> {
            byte[] buffer = new byte[Math.max(8, Math.min(endpoint.getMaxPacketSize(), 512))];
            while (reading && connection != null) {
                int count;
                try { count = connection.bulkTransfer(endpoint, buffer, buffer.length, 250); }
                catch (Exception e) { append("Erreur USB : " + e.getMessage()); break; }
                if (count > 0) {
                    byte[] bytes = Arrays.copyOf(buffer, count);
                    StringBuilder hex = new StringBuilder();
                    for (byte b : bytes) hex.append(String.format(Locale.US, "%02X ", b & 0xff));
                    append("USB HEX (" + count + ") : " + hex.toString().trim());
                }
            }
            append("Boucle de capture arrêtée");
        }, "igaging-usb-reader");
        worker.start();
    }

    private synchronized void stopRead() {
        reading = false;
        if (connection != null) {
            try { if (claimed != null) connection.releaseInterface(claimed); } catch (Exception ignored) { }
            try { connection.close(); } catch (Exception ignored) { }
        }
        claimed = null; connection = null;
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            append("KEY keyCode=" + event.getKeyCode() + " scanCode=" + event.getScanCode()
                + " unicode=" + event.getUnicodeChar() + " source=" + event.getSource());
        }
        return super.dispatchKeyEvent(event);
    }

    private void append(String line) {
        runOnUiThread(() -> {
            history.append(clock.format(new Date())).append("  ").append(line).append('\n');
            if (history.length() > 25000) history.delete(0, history.length() - 18000);
            if (log != null) log.setText(history.toString());
        });
    }

    @Override public void onDestroy() {
        stopRead(); unregisterReceiver(receiver); super.onDestroy();
    }
}
