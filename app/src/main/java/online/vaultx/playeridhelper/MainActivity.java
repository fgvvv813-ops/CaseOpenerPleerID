package online.vaultx.playeridhelper;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Independent companion app. Never reads or modifies another application's data. */
public final class MainActivity extends Activity {
    private EditText playerId;
    private TextView status;

    private Button button(String title, LinearLayout root, View.OnClickListener click) {
        Button b = new Button(this);
        b.setText(title);
        b.setAllCaps(false);
        b.setOnClickListener(click);
        root.addView(b);
        return b;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Color.WHITE);
        scroll.addView(root);

        TextView heading = new TextView(this);
        heading.setText("VaultX · Player ID");
        heading.setTextSize(26);
        heading.setTextColor(Color.rgb(40, 30, 80));
        root.addView(heading);

        TextView description = new TextView(this);
        description.setText("Узнай свой Player ID через привязку в Telegram-боте VaultX. Затем сохрани его здесь для быстрого доступа.\n\nЭто отдельное приложение: оригинальная игра и сохранения не затрагиваются.");
        description.setTextSize(16);
        description.setTextColor(Color.DKGRAY);
        root.addView(description);

        playerId = new EditText(this);
        playerId.setHint("Player ID");
        playerId.setSingleLine(true);
        playerId.setText(getPreferences(MODE_PRIVATE).getString("player_id", ""));
        root.addView(playerId);

        status = new TextView(this);
        status.setText("Сохранённый здесь ID не подтверждает владение аккаунтом.");
        status.setTextColor(Color.DKGRAY);
        root.addView(status);

        button("Сохранить Player ID", root, v -> {
            String id = playerId.getText().toString().trim();
            if (id.isEmpty() || id.length() > 100) {
                Toast.makeText(this, "Проверь Player ID", Toast.LENGTH_SHORT).show();
                return;
            }
            getPreferences(MODE_PRIVATE).edit().putString("player_id", id).apply();
            status.setText("Player ID сохранён на этом устройстве.");
        });

        button("Скопировать Player ID", root, v -> {
            String id = playerId.getText().toString().trim();
            if (id.isEmpty()) { Toast.makeText(this, "Сначала введи ID", Toast.LENGTH_SHORT).show(); return; }
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("Player ID", id));
            Toast.makeText(this, "Player ID скопирован", Toast.LENGTH_SHORT).show();
        });

        button("Узнать / подтвердить ID через VaultX", root, v ->
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/VaultX_co_BOT?start=playerid")))
        );

        TextView note = new TextView(this);
        note.setText("\nAndroid не разрешает читать внутренние данные чужой игры. Поэтому приложение не может автоматически извлечь настоящий Player ID; подтверждение выполняется через VaultX.");
        note.setTextColor(Color.DKGRAY);
        root.addView(note);

        setContentView(scroll);
    }
}
