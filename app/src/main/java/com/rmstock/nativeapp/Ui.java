package com.rmstock.nativeapp;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Colours (light and dark) and small view builders shared by every native screen. */
final class Ui {
    private Ui() {}

    static boolean dark;
    static int BG, CARD, INK, MUTED, LINE, NAVY, NAVY_TEXT, BTN, ACCENT;
    static int[] TAB, TAB_SOFT;
    static int SOFT_BLUE, SOFT_GREEN, SOFT_AMBER, SOFT_PURPLE, SOFT_RED, GOOD, BAD, WARN;

    static void init(Context c) {
        dark = (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        NAVY = Color.rgb(20, 55, 95);
        ACCENT = Color.rgb(31, 157, 126);
        TAB = dark ? new int[]{Color.rgb(110, 168, 255), Color.rgb(76, 208, 138), Color.rgb(255, 176, 74), Color.rgb(190, 146, 255), Color.rgb(255, 140, 120), Color.rgb(167, 186, 198)}
                   : new int[]{Color.rgb(36, 87, 166), Color.rgb(29, 154, 91), Color.rgb(224, 123, 0), Color.rgb(123, 63, 196), Color.rgb(200, 70, 50), Color.rgb(84, 110, 122)};
        TAB_SOFT = new int[TAB.length];
        for (int i = 0; i < TAB.length; i++) TAB_SOFT[i] = (TAB[i] & 0x00FFFFFF) | 0x28000000;
        if (dark) {
            BG = Color.rgb(14, 20, 27); CARD = Color.rgb(25, 34, 45); INK = Color.rgb(231, 237, 243); MUTED = Color.rgb(154, 168, 184);
            LINE = Color.rgb(43, 57, 72); NAVY_TEXT = Color.rgb(132, 184, 235); BTN = Color.rgb(47, 111, 176);
            SOFT_BLUE = Color.rgb(24, 40, 58); SOFT_GREEN = Color.rgb(22, 50, 40); SOFT_AMBER = Color.rgb(58, 48, 26);
            SOFT_PURPLE = Color.rgb(40, 33, 62); SOFT_RED = Color.rgb(62, 30, 32);
            GOOD = Color.rgb(83, 197, 133); BAD = Color.rgb(255, 138, 128); WARN = Color.rgb(240, 180, 60);
        } else {
            BG = Color.rgb(243, 246, 250); CARD = Color.WHITE; INK = Color.rgb(27, 36, 48); MUTED = Color.rgb(93, 107, 124);
            LINE = Color.rgb(225, 231, 238); NAVY_TEXT = Color.rgb(20, 55, 95); BTN = Color.rgb(20, 55, 95);
            SOFT_BLUE = Color.rgb(232, 241, 252); SOFT_GREEN = Color.rgb(228, 246, 238); SOFT_AMBER = Color.rgb(255, 244, 222);
            SOFT_PURPLE = Color.rgb(240, 235, 252); SOFT_RED = Color.rgb(253, 232, 231);
            GOOD = Color.rgb(29, 122, 70); BAD = Color.rgb(179, 38, 30); WARN = Color.rgb(176, 112, 0);
        }
    }

    static int dp(Context c, float n) { return (int) (n * c.getResources().getDisplayMetrics().density + .5f); }

    static GradientDrawable shape(Context c, int fill, int stroke, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, radiusDp));
        if (stroke != 0) g.setStroke(dp(c, 1), stroke);
        return g;
    }

    static LinearLayout.LayoutParams lp(Context c, int w, int h, float weight, float l, float t, float r, float b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h, weight);
        p.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        return p;
    }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    static TextView muted(Context c, String s) {
        TextView t = text(c, s, 13, MUTED, false);
        t.setPadding(0, dp(c, 2), 0, dp(c, 6));
        return t;
    }

    static Button primary(Context c, String s) {
        Button b = new Button(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(15);
        b.setBackground(shape(c, BTN, 0, 12));
        b.setStateListAnimator(null);
        b.setPadding(dp(c, 14), dp(c, 10), dp(c, 14), dp(c, 10));
        return b;
    }

    static Button secondary(Context c, String s) {
        Button b = new Button(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(NAVY_TEXT);
        b.setTextSize(14);
        b.setBackground(shape(c, CARD, LINE, 12));
        b.setStateListAnimator(null);
        return b;
    }

    static EditText field(Context c, String hint) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setTextSize(15);
        e.setTextColor(INK);
        e.setHintTextColor(MUTED);
        e.setBackground(shape(c, CARD, LINE, 10));
        e.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
        return e;
    }

    /** Big bold first line, smaller muted second line (used by the live dashboard cards). */
    static CharSequence twoLine(String big, String small) {
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(big);
        if (small != null && !small.isEmpty()) {
            sb.append('\n');
            int st = sb.length();
            sb.append(small);
            sb.setSpan(new android.text.style.RelativeSizeSpan(0.72f), st, sb.length(), 0);
            sb.setSpan(new android.text.style.StyleSpan(Typeface.NORMAL), st, sb.length(), 0);
            sb.setSpan(new android.text.style.ForegroundColorSpan(MUTED), st, sb.length(), 0);
        }
        return sb;
    }

    static LinearLayout card(Context c, int fill) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
        l.setBackground(shape(c, fill, LINE, 14));
        return l;
    }

    /** One horizontal bar: label on the left, value on the right, a coloured bar under them (fraction 0..1). */
    static LinearLayout barRow(Context c, String label, String value, double fraction, int color) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(c, 5), 0, dp(c, 5));
        LinearLayout top = new LinearLayout(c);
        TextView a = text(c, label, 13, INK, false);
        a.setMaxLines(1);
        a.setEllipsize(android.text.TextUtils.TruncateAt.END);
        top.addView(a, new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(text(c, value, 13, INK, true));
        box.addView(top);
        LinearLayout track = new LinearLayout(c);
        track.setWeightSum(100);
        track.setBackground(shape(c, LINE, 0, 4));
        int w = (int) Math.round(Math.max(0, Math.min(1, fraction)) * 100);
        View fill = new View(c);
        fill.setBackground(shape(c, color, 0, 4));
        track.addView(fill, new LinearLayout.LayoutParams(0, dp(c, 8), w));
        track.addView(new View(c), new LinearLayout.LayoutParams(0, dp(c, 8), 100 - w));
        box.addView(track, lp(c, -1, dp(c, 8), 0, 0, 3, 0, 0));
        return box;
    }
}
