package com.jeremykenedy.firetv.ui;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ProgressBar;
import android.app.AlertDialog;
import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/** User-initiated stable release checks and normal Android replacement installs. */
public final class UpdatesActivity extends CardActivity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<Row> apps = new ArrayList<>();
    private LinearLayout rows;
    private TextView status;
    private TextView check;
    private ProgressBar progress;
    private String focusPackage;
    private TextView iconStatus, iconAction;
    private boolean iconsBusy;
