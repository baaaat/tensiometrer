package fr.lechappee.igagingdiag;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;

/** First screen: validates wheel parameters before starting the acquisition screen. */
public class ConfigurationActivity extends Activity {
    private EditText title;
    private EditText leftSpoke;
    private EditText rightSpoke;
    private EditText rightTarget;
    private EditText ratio;
    private Spinner spokeCount;
    private RadioGroup firstSide;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 20, 28, 20);

        TextView heading = label("Configuration de la roue", 25);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(heading);
        title = textField("Nom du montage", "Client / roue / reference atelier");
        root.addView(title);
        root.addView(label("Tensiometre : Wheel Fanatyk Digital / iGaging USB", 16));
        leftSpoke = textField("Rayon gauche", "Categorie et epaisseur de la table officielle");
        rightSpoke = textField("Rayon droit", "Categorie et epaisseur de la table officielle");
        root.addView(leftSpoke);
        root.addView(rightSpoke);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(label("Nombre de rayons", 16));
        spokeCount = new Spinner(this);
        spokeCount.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
            new String[] { "20", "24", "28", "32", "36" }));
        spokeCount.setSelection(3);
        row.addView(spokeCount, new LinearLayout.LayoutParams(150, -2));
        root.addView(row);

        root.addView(label("Premier rayon mesure", 16));
        firstSide = new RadioGroup(this);
        firstSide.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton right = radio("Droite", true);
        right.setId(1);
        RadioButton left = radio("Gauche", false);
        left.setId(2);
        firstSide.addView(right);
        firstSide.addView(left);
        root.addView(firstSide);

        rightTarget = numberField("Tension cible droite (kgf)", "114");
        ratio = numberField("Ratio gauche / droite (%)", "72");
        root.addView(rightTarget);
        root.addView(ratio);
        TextView targetPreview = label("Cible gauche calculee : 82.08 kgf", 16);
        root.addView(targetPreview);
        ratio.setOnFocusChangeListener((view, focused) -> updatePreview(targetPreview));
        rightTarget.setOnFocusChangeListener((view, focused) -> updatePreview(targetPreview));

        Button start = new Button(this);
        start.setText("Demarrer les mesures");
        start.setOnClickListener(view -> startSurvey());
        root.addView(start);
        root.addView(label("Les conversions kgf exigent une table Wheel Fanatyk officielle integree.", 13));
        setContentView(root);
    }

    private TextView label(String value, float size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setPadding(0, 5, 0, 5);
        return view;
    }

    private EditText textField(String hint, String placeholder) {
        EditText field = new EditText(this);
        field.setHint(hint + " - " + placeholder);
        field.setSingleLine(true);
        return field;
    }

    private EditText numberField(String hint, String value) {
        EditText field = textField(hint, "");
        field.setText(value);
        field.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        return field;
    }

    private RadioButton radio(String value, boolean checked) {
        RadioButton button = new RadioButton(this);
        button.setText(value);
        button.setChecked(checked);
        button.setGravity(Gravity.CENTER_VERTICAL);
        return button;
    }

    private void updatePreview(TextView preview) {
        try {
            double target = Double.parseDouble(rightTarget.getText().toString().replace(',', '.'));
            double percentage = Double.parseDouble(ratio.getText().toString().replace(',', '.'));
            preview.setText(String.format(Locale.FRANCE, "Cible gauche calculee : %.2f kgf",
                target * percentage / 100.0));
        } catch (NumberFormatException ignored) { }
    }

    private void startSurvey() {
        try {
            int count = Integer.parseInt(spokeCount.getSelectedItem().toString());
            double target = Double.parseDouble(rightTarget.getText().toString().replace(',', '.'));
            double percentage = Double.parseDouble(ratio.getText().toString().replace(',', '.'));
            WheelConfig config = new WheelConfig(title.getText().toString(),
                leftSpoke.getText().toString(), rightSpoke.getText().toString(), count,
                firstSide.getCheckedRadioButtonId() == 2
                    ? WheelConfig.FirstSide.LEFT : WheelConfig.FirstSide.RIGHT,
                target, percentage);
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("title", config.title);
            intent.putExtra("leftSpoke", config.leftSpoke);
            intent.putExtra("rightSpoke", config.rightSpoke);
            intent.putExtra("spokeCount", config.spokeCount);
            intent.putExtra("firstSide", config.firstSide.name());
            intent.putExtra("rightTarget", config.rightTargetKgf);
            intent.putExtra("leftRatio", config.leftRatioPercent);
            startActivity(intent);
        } catch (IllegalArgumentException error) {
            Toast.makeText(this, "Configuration invalide : " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
