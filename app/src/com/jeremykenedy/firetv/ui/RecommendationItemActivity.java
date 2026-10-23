package com.jeremykenedy.firetv.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import java.util.List;

public final class RecommendationItemActivity extends CardActivity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Uri uri = getIntent().getData();
        List<String> parts = uri == null ? java.util.Collections.emptyList() : uri.getPathSegments();
        boolean valid = uri != null && "firetvui".equals(uri.getScheme())
                && "recommendation".equals(uri.getAuthority()) && uri.getQuery() == null
                && uri.getFragment() == null && parts.size() == 2 && "item".equals(parts.get(0))
                && RecommendationRules.validIdentity(parts.get(1));
        if (valid && RecommendationService.open(this, parts.get(1))) { finish(); return; }
        title("Recommendation unavailable");
        LinearLayout body = scrollBody();
        body.addView(text("The app has withdrawn this card, or recommendation access is disconnected."
                + " Refresh app recommendations and choose a current card.", 18, INK));
        addButton(body, button("App recommendation setup", new View.OnClickListener() {
            @Override public void onClick(View view) {
                startActivity(new Intent(RecommendationItemActivity.this, RecommendationCardsActivity.class));
                finish();
            }
        }));
    }
}
