package com.luisxl.gowcollection;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;

import java.io.File;

// Ponte do LoadExec: num processo à parte, espera o processo do jogo morrer e abre a GameActivity de novo com o
// outro módulo principal (menu da coletânea -> God of War, e o "Apply" dos mods).
public final class RelaunchActivity extends Activity {
    static final String EXTRA_PID = "pid";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        overridePendingTransition(0, 0);
        final int pid = getIntent().getIntExtra(EXTRA_PID, -1);
        final String principal = getIntent().getStringExtra(GameActivity.EXTRA_PRINCIPAL);
        final Handler h = new Handler(Looper.getMainLooper());
        final long ate = System.currentTimeMillis() + 5000;
        h.post(new Runnable() {
            @Override
            public void run() {
                if (pid > 0 && new File("/proc/" + pid).exists() && System.currentTimeMillis() < ate) {
                    h.postDelayed(this, 40);
                    return;
                }
                Intent i = new Intent(RelaunchActivity.this, GameActivity.class);
                i.putExtra(GameActivity.EXTRA_PRINCIPAL, principal);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(i);
                overridePendingTransition(0, 0);
                finish();
                h.postDelayed(() -> Process.killProcess(Process.myPid()), 500);
            }
        });
    }
}
