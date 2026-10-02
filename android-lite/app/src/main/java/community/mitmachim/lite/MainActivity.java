package community.mitmachim.lite;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import java.net.URI;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

public final class MainActivity extends Activity {
    private static final int TEAL = 0xFF087F8C, INK = 0xFF17353E, MUTED = 0xFF607D86, BG = 0xFFF3F7F8;
    private static final String RECENT = "recent", CATEGORIES = "categories", CATEGORY = "category", TOPIC = "topic", SAVED = "saved";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<Route> history = new ArrayList<>();
    private final Set<Integer> expanded = new HashSet<>();
    private final LinkedHashMap<String, ForumPage> cache = new LinkedHashMap<String, ForumPage>(8, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, ForumPage> eldest) { return size() > 4; }
    };
    private final LinkedHashMap<String, Spanned> rendered = new LinkedHashMap<String, Spanned>(32, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Spanned> eldest) { return size() > 50; }
    };
    private LinearLayout root, notices, pageBar, tabs;
    private ListView list;
    private TextView toolbarTitle, heading;
    private Button back;
    private volatile ForumClient client;
    private Future<?> task;
    private Route route = new Route(RECENT, 0, 1, "נושאים אחרונים");
    private ForumPage data;
    private Throwable initError;
    private int requestId, fontSize;
    private boolean alive = true;

    static final class Route {
        final String kind, title;
        final int id, page;
        int position, offset;
        Route(String kind, int id, int page, String title) { this.kind = kind; this.id = id; this.page = Math.max(1, page); this.title = title; }
        String key() { return kind + ":" + id + ":" + page; }
        String path() {
            if (kind.equals(RECENT)) return "/api/recent?term=alltime";
            if (kind.equals(CATEGORIES)) return "/api/categories";
            return "/api/" + kind + "/" + id;
        }
        String web() {
            if (kind.equals(SAVED)) return UrlPolicy.BASE;
            return UrlPolicy.BASE + path().substring(4) + (path().contains("?") ? "&" : "?") + "page=" + page;
        }
        JSONObject json() {
            JSONObject json = new JSONObject();
            try { json.put("kind", kind).put("id", id).put("page", page).put("title", title).put("position", position).put("offset", offset); }
            catch (Exception ignored) { }
            return json;
        }
        static Route from(JSONObject json) {
            String kind = json.optString("kind", RECENT);
            if (!kind.equals(RECENT) && !kind.equals(CATEGORIES) && !kind.equals(CATEGORY) && !kind.equals(TOPIC) && !kind.equals(SAVED)) kind = RECENT;
            Route result = new Route(kind, Math.max(0, json.optInt("id")), json.optInt("page", 1), json.optString("title", "מתמחים לייט"));
            result.position = Math.max(0, json.optInt("position")); result.offset = json.optInt("offset"); return result;
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        fontSize = getPreferences(MODE_PRIVATE).getInt("font", 18);
        if (state != null) try {
            route = Route.from(new JSONObject(state.getString("route", "{}")));
            JSONArray routes = new JSONArray(state.getString("history", "[]"));
            for (int i = 0; i < routes.length(); i++) history.add(Route.from(routes.getJSONObject(i)));
        } catch (Exception ignored) { }
        buildUi();
        load(false);
    }

    private void buildUi() {
        root = column(); root.setBackgroundColor(BG); root.setFitsSystemWindows(true);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        setContentView(root);
        LinearLayout toolbar = row(); toolbar.setPadding(dp(5), dp(2), dp(5), dp(2)); toolbar.setBackgroundColor(TEAL);
        back = button("›", () -> onBackPressed()); back.setTextSize(28); back.setTextColor(Color.WHITE);
        back.setBackgroundColor(Color.TRANSPARENT); back.setContentDescription("חזרה למסך הקודם"); toolbar.addView(back, new LinearLayout.LayoutParams(dp(44), dp(48)));
        toolbarTitle = text("מתמחים לייט", 19, Color.WHITE, true);
        toolbar.addView(toolbarTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button more = button("⋮", this::menu); more.setTextSize(25); more.setTextColor(Color.WHITE); more.setBackgroundColor(Color.TRANSPARENT);
        more.setContentDescription("תפריט: רענון, גודל טקסט, שמירה ואודות"); toolbar.addView(more, new LinearLayout.LayoutParams(dp(44), dp(48)));
        root.addView(toolbar);
        notices = column(); root.addView(notices);
        list = new ListView(this); list.setDivider(null); list.setCacheColorHint(Color.TRANSPARENT);
        list.setClipToPadding(false); list.setPadding(dp(8), dp(6), dp(8), dp(6)); list.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        LinearLayout listHeader = column(); listHeader.setPadding(dp(9), dp(9), dp(9), dp(13));
        heading = text("", 22, INK, true); listHeader.addView(heading); list.addHeaderView(listHeader, null, false);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        pageBar = row(); pageBar.setPadding(dp(6), dp(3), dp(6), dp(3)); root.addView(pageBar);
        tabs = row(); tabs.setPadding(dp(4), dp(4), dp(4), dp(5)); tabs.setBackgroundColor(0xFFE3F0F2);
        tab("בית", RECENT); tab("קטגוריות", CATEGORIES); tab("שמורים", SAVED); root.addView(tabs);
    }

    private void tab(String title, String kind) {
        Button button = button(title, () -> navigate(new Route(kind, 0, 1, title), true));
        button.setTextSize(14); button.setBackgroundColor(Color.TRANSPARENT);
        tabs.addView(button, new LinearLayout.LayoutParams(0, dp(44), 1));
    }

    private void navigate(Route next, boolean push) {
        rememberPosition();
        if (push && !route.key().equals(next.key())) { history.add(route); if (history.size() > 30) history.remove(0); }
        route = next; data = null; expanded.clear(); notices.removeAllViews();
        load(false);
    }

    private void load(boolean refresh) {
        final int token = ++requestId;
        if (task != null) task.cancel(true);
        if (client != null) client.cancel();
        toolbarTitle.setText(route.kind.equals(TOPIC) ? "קריאת דיון" : "מתמחים לייט");
        back.setVisibility(history.isEmpty() ? View.INVISIBLE : View.VISIBLE);
        tabs.setVisibility(route.kind.equals(TOPIC) ? View.GONE : View.VISIBLE);
        heading.setText(plain(route.title));
        if (route.kind.equals(SAVED)) { display(savedPage()); return; }
        ForumPage previous = cache.get(route.key());
        if (previous != null && !refresh) { display(previous); return; }
        if (previous != null) display(previous);
        else { data = null; list.setAdapter(new Items(new ArrayList<>())); pageBar.removeAllViews(); }
        notices.removeAllViews();
        LinearLayout loading = row(); loading.setGravity(Gravity.CENTER); loading.setPadding(dp(10), dp(8), dp(10), dp(8));
        ProgressBar progress = new ProgressBar(this); loading.addView(progress, new LinearLayout.LayoutParams(dp(22), dp(22)));
        TextView label = text("טוענים מהפורום…", 14, MUTED, false); label.setPadding(dp(10), 0, dp(10), 0); loading.addView(label); notices.addView(loading);
        final Route requested = route;
        task = executor.submit(() -> {
            ForumPage result = null; Throwable failure = null;
            try {
                if (client == null) client = new ForumClient(ForumTls.create(getApplicationContext()));
                result = client.load(requested.path(), requested.page);
            } catch (Exception e) { failure = e; }
            final ForumPage value = result; final Throwable error = failure;
            runOnUiThread(() -> {
                if (!alive || token != requestId) return;
                notices.removeAllViews();
                if (error != null) { showError(error); return; }
                if (value != null && !value.redirect.isEmpty() && value.items.isEmpty()) {
                    notices.addView(text("העמוד מפנה לקישור באתר. ניתן לפתוח אותו בדפדפן.", 16, INK, false));
                    notices.addView(button("פתיחה באתר", () -> openLink(plain(value.redirect), false))); return;
                }
                cache.put(requested.key(), value); display(value);
            });
        });
    }

    private void display(ForumPage page) {
        data = page; notices.removeAllViews();
        if (!page.title.isEmpty()) heading.setText(plain(page.title));
        else heading.setText(plain(route.title));
        if (route.kind.equals(TOPIC) && page.locked) notices.addView(text("הדיון נעול · אפשר להמשיך לקרוא", 13, MUTED, false));
        if (page.items.isEmpty()) notices.addView(text(route.kind.equals(SAVED) ? "אין עדיין נושאים שמורים. בתוך דיון אפשר לבחור „שמירת נושא” בתפריט." : "לא נמצאו הודעות בעמוד הזה.", 16, MUTED, false));
        list.setAdapter(new Items(page.items)); list.setSelectionFromTop(route.position, route.offset);
        pageBar.removeAllViews();
        if (page.pages > 1 || route.kind.equals(TOPIC)) {
            Button previous = button("הקודם", () -> changePage(data.page - 1)); previous.setEnabled(page.page > 1);
            Button next = button("הבא", () -> changePage(data.page + 1)); next.setEnabled(page.page < page.pages);
            Button number = button("עמוד " + page.page + " / " + page.pages, this::jumpPage); number.setTextSize(13);
            pageBar.addView(previous, new LinearLayout.LayoutParams(0, dp(44), 1));
            pageBar.addView(number, new LinearLayout.LayoutParams(0, dp(44), 1.5f));
            pageBar.addView(next, new LinearLayout.LayoutParams(0, dp(44), 1));
        }
        if (route.kind.equals(TOPIC)) {
            getPreferences(MODE_PRIVATE).edit().putString("last_topic", route.json().toString()).apply();
        }
    }

    private void showError(Throwable error) {
        initError = error;
        String code = ForumClient.errorCode(error);
        LinearLayout box = column(); box.setPadding(dp(12), dp(8), dp(12), dp(8)); box.setBackgroundColor(0xFFFFF3E8);
        box.addView(text("לא הצלחנו לטעון את העמוד", 17, INK, true));
        box.addView(text(ForumClient.explanation(code) + "\nקוד תקלה: " + code, 14, MUTED, false));
        box.addView(button("ניסיון חוזר", () -> load(true))); notices.addView(box);
    }

    private void changePage(int page) {
        if (data == null || page < 1 || page > data.pages) return;
        Route next = new Route(route.kind, route.id, page, heading.getText().toString());
        navigate(next, false);
    }

    private void jumpPage() {
        if (data == null) return;
        LinearLayout box = column(); box.setPadding(dp(18), 0, dp(18), 0);
        EditText input = new EditText(this); input.setInputType(InputType.TYPE_CLASS_NUMBER); input.setText(String.valueOf(data.page)); input.selectAll(); box.addView(input);
        box.addView(button("תחילת הדיון", () -> { changePage(1); }));
        box.addView(button("סוף הדיון", () -> { changePage(data.pages); }));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("מעבר לעמוד").setView(box).setNegativeButton("ביטול", null).setPositiveButton("מעבר", null).create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                try {
                    int page = Integer.parseInt(input.getText().toString());
                    if (page < 1 || page > data.pages) { input.setError("בחרו עמוד בין 1 ל־" + data.pages); return; }
                    dialog.dismiss(); changePage(page);
                } catch (NumberFormatException e) { input.setError("הזינו מספר עמוד"); }
            });
            for (int i = 1; i <= 2; i++) {
                final int selected = i;
                box.getChildAt(i).setOnClickListener(v -> { int page = selected == 1 ? 1 : data.pages; dialog.dismiss(); changePage(page); });
            }
        });
        dialog.show();
    }

    private void menu() {
        PopupMenu popup = new PopupMenu(this, root.getChildAt(0));
        popup.getMenu().add(0, 1, 0, "רענון");
        popup.getMenu().add(0, 2, 1, "גודל טקסט");
        if (route.kind.equals(TOPIC)) {
            popup.getMenu().add(0, 3, 2, isSaved(route.id) ? "הסרה מהשמורים" : "שמירת נושא");
            popup.getMenu().add(0, 4, 3, "לתחילת העמוד"); popup.getMenu().add(0, 5, 4, "לסוף העמוד");
        } else if (!getPreferences(MODE_PRIVATE).getString("last_topic", "").isEmpty()) popup.getMenu().add(0, 7, 5, "המשך הדיון האחרון");
        popup.getMenu().add(0, 6, 6, "פתיחה באתר"); popup.getMenu().add(0, 8, 7, "אודות לייט");
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: rememberPosition(); load(true); break;
                case 2: new AlertDialog.Builder(this).setTitle("גודל טקסט לקריאה").setSingleChoiceItems(new String[]{"קומפקטי", "רגיל", "גדול"}, fontSize == 16 ? 0 : fontSize == 18 ? 1 : 2,
                    (dialog, index) -> { fontSize = new int[]{16, 18, 21}[index]; getPreferences(MODE_PRIVATE).edit().putInt("font", fontSize).apply(); rememberPosition(); if (data != null) display(data); dialog.dismiss(); }).show(); break;
                case 3: toggleSaved(); break;
                case 4: list.smoothScrollToPosition(0); break;
                case 5: list.smoothScrollToPosition(list.getCount() - 1); break;
                case 6: openBrowser(UrlPolicy.browser(route.web())); break;
                case 7: try { navigate(Route.from(new JSONObject(getPreferences(MODE_PRIVATE).getString("last_topic", "{}"))), true); } catch (Exception ignored) { } break;
                case 8: about(); break;
                default: break;
            }
            return true;
        }); popup.show();
    }

    private void about() {
        new AlertDialog.Builder(this).setTitle("מתמחים לייט · אב־טיפוס")
            .setMessage("גרסה " + BuildConfig.VERSION_NAME + "\n\nקורא קל לפורום מתמחים טופ, החל מ־Android 4.4.\n\nבשלב הזה: קריאת דיונים, קטגוריות ושמירה מקומית. אין עדיין התחברות, תגובות או עדכונים מתוך האפליקציה. תמונות ופורמטים מורכבים נפתחים באתר.\n\nפותחה ע״י @רב יהודה פרחים\nבסיוע @הבריסקער רב\nמפורום מתמחים טופ.\n\nלא אפליקציה רשמית של הנהלת הפורום.\n\nמערכת: Android " + android.os.Build.VERSION.RELEASE + " (API " + android.os.Build.VERSION.SDK_INT + ")\nחיבור: TLS 1.2 ומעלה, עם אימות תעודות.\nאין אמון גורף בתעודות משתמש.\n\nAndroid 4.4 הוא מערכת ישנה שאינה מקבלת עדכוני אבטחה רגילים." + (initError == null ? "" : "\nאבחון אחרון: " + ForumClient.errorCode(initError)))
            .setPositiveButton("סגירה", null).show();
    }

    private JSONArray saved() {
        try { return new JSONArray(getPreferences(MODE_PRIVATE).getString("saved", "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }
    private boolean isSaved(int id) {
        JSONArray saved = saved(); for (int i = 0; i < saved.length(); i++) if (saved.optJSONObject(i) != null && saved.optJSONObject(i).optInt("id") == id) return true;
        return false;
    }
    private void toggleSaved() {
        JSONArray result = new JSONArray(), saved = saved(); boolean found = false;
        for (int i = 0; i < saved.length(); i++) {
            JSONObject item = saved.optJSONObject(i); if (item == null) continue;
            if (item.optInt("id") == route.id) found = true; else result.put(item);
        }
        if (!found) {
            if (result.length() >= 100) { Toast.makeText(this, "ניתן לשמור עד 100 נושאים באב־הטיפוס", Toast.LENGTH_LONG).show(); return; }
            Route topic = new Route(TOPIC, route.id, route.page, heading.getText().toString()); result.put(topic.json());
        }
        getPreferences(MODE_PRIVATE).edit().putString("saved", result.toString()).apply();
        Toast.makeText(this, found ? "הנושא הוסר מהשמורים" : "הנושא נשמר במכשיר", Toast.LENGTH_SHORT).show();
    }
    private ForumPage savedPage() {
        ForumPage page = new ForumPage(); JSONArray saved = saved();
        for (int i = 0; i < saved.length(); i++) {
            JSONObject obj = saved.optJSONObject(i); if (obj == null) continue;
            ForumPage.Item item = new ForumPage.Item(); item.kind = ForumPage.Item.TOPIC; item.id = obj.optInt("id"); item.title = obj.optString("title"); item.category = "נשמר במכשיר"; page.items.add(item);
        }
        return page;
    }

    @Override public void onBackPressed() {
        if (history.isEmpty()) { super.onBackPressed(); return; }
        Route previous = history.remove(history.size() - 1); navigate(previous, false);
    }
    private void rememberPosition() {
        if (list == null) return;
        route.position = list.getFirstVisiblePosition(); View first = list.getChildAt(0); route.offset = first == null ? 0 : first.getTop() - list.getPaddingTop();
        if (route.kind.equals(TOPIC) && data != null) getPreferences(MODE_PRIVATE).edit().putString("last_topic", route.json().toString()).apply();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        rememberPosition(); state.putString("route", route.json().toString()); JSONArray routes = new JSONArray();
        for (Route entry : history) routes.put(entry.json()); state.putString("history", routes.toString()); super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() {
        alive = false; requestId++; if (task != null) task.cancel(true); if (client != null) client.cancel(); executor.shutdownNow(); super.onDestroy();
    }

    private void openLink(String value, boolean internal) {
        URI uri = UrlPolicy.browser(value);
        if (uri == null) { Toast.makeText(this, "סוג הקישור הזה אינו נתמך", Toast.LENGTH_SHORT).show(); return; }
        int id = UrlPolicy.routeId(uri, "topic");
        if (internal && id > 0) { navigate(new Route(TOPIC, id, 1, "דיון"), true); return; }
        id = UrlPolicy.routeId(uri, "category");
        if (internal && id > 0) { navigate(new Route(CATEGORY, id, 1, "קטגוריה"), true); return; }
        openBrowser(uri);
    }
    private void openBrowser(URI uri) {
        if (uri == null) return;
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri.toASCIIString()))); }
        catch (Exception e) { Toast.makeText(this, "לא נמצא דפדפן לפתיחת הקישור", Toast.LENGTH_SHORT).show(); }
    }

    private final class Items extends BaseAdapter {
        private final List<ForumPage.Item> items;
        Items(List<ForumPage.Item> items) { this.items = items; }
        @Override public int getCount() { return items.size(); }
        @Override public ForumPage.Item getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return items.get(position).id; }
        @Override public int getViewTypeCount() { return 2; }
        @Override public int getItemViewType(int position) { return items.get(position).kind == ForumPage.Item.POST ? 1 : 0; }
        @Override public boolean isEnabled(int position) { return false; }
        @Override public View getView(int position, View recycled, ViewGroup parent) {
            ForumPage.Item item = getItem(position);
            LinearLayout outer = column(); outer.setPadding(0, dp(3), 0, dp(4));
            LinearLayout card = column(); card.setPadding(dp(13), dp(12), dp(13), dp(12)); card.setBackground(rounded(Color.WHITE, dp(13), 0xFFDCE9ED)); outer.addView(card);
            if (item.kind == ForumPage.Item.POST) bindPost(card, item);
            else bindTopic(card, item);
            return outer;
        }
        private void bindTopic(LinearLayout card, ForumPage.Item item) {
            LinearLayout line = row(); line.setGravity(Gravity.CENTER_VERTICAL);
            String title = plain(item.title);
            TextView avatar = text(item.kind == ForumPage.Item.CATEGORY ? "◈" : initial(plain(item.author).isEmpty() ? title : plain(item.author)), 20, Color.WHITE, true);
            avatar.setGravity(Gravity.CENTER); avatar.setBackground(rounded(item.kind == ForumPage.Item.CATEGORY ? TEAL : userColor(item.color), dp(22), 0));
            line.addView(avatar, new LinearLayout.LayoutParams(dp(40), dp(40)));
            LinearLayout body = column(); body.setPadding(dp(10), 0, dp(10), 0);
            TextView topic = text(title, Math.max(16, fontSize - 1), INK, true); topic.setMaxLines(3); topic.setEllipsize(TextUtils.TruncateAt.END); body.addView(topic);
            String details = item.kind == ForumPage.Item.CATEGORY ? item.count + " נושאים" : plain(item.category) + (item.author.isEmpty() ? "" : " · " + plain(item.author)) + " · " + item.count + " תגובות";
            TextView subtitle = text(details, 12, MUTED, false); subtitle.setMaxLines(2); subtitle.setEllipsize(TextUtils.TruncateAt.END); body.addView(subtitle);
            line.addView(body, new LinearLayout.LayoutParams(0, -2, 1)); line.addView(text("‹", 24, TEAL, false)); card.addView(line);
            card.setContentDescription(title + ", " + details);
            card.setOnClickListener(v -> {
                if (!item.link.isEmpty()) { openLink(plain(item.link), true); return; }
                navigate(new Route(item.kind == ForumPage.Item.CATEGORY ? CATEGORY : TOPIC, item.id, 1, title), true);
            });
        }
        private void bindPost(LinearLayout card, ForumPage.Item item) {
            LinearLayout byline = row(); byline.setGravity(Gravity.CENTER_VERTICAL);
            TextView avatar = text(initial(plain(item.author)), 16, Color.WHITE, true); avatar.setGravity(Gravity.CENTER); avatar.setBackground(rounded(userColor(item.color), dp(18), 0));
            byline.addView(avatar, new LinearLayout.LayoutParams(dp(32), dp(32)));
            TextView author = text(plain(item.author), 15, TEAL, true); author.setPadding(dp(8), 0, dp(8), 0); byline.addView(author, new LinearLayout.LayoutParams(0, -2, 1));
            TextView number = text("#" + item.number, 12, MUTED, false); number.setTextDirection(View.TEXT_DIRECTION_LTR); byline.addView(number); card.addView(byline);
            TextView content = text("", fontSize, INK, false); content.setPadding(0, dp(10), 0, dp(7)); content.setLineSpacing(dp(2), 1f); content.setTextIsSelectable(true);
            content.setLinkTextColor(TEAL); content.setMovementMethod(LinkMovementMethod.getInstance());
            boolean spoiler = ReaderHtml.hasSpoiler(item.html);
            if (spoiler && !expanded.contains(item.id)) {
                content.setText(rich(ReaderHtml.prepare(item.html, false)));
                card.addView(content); card.addView(button("הצגת ההודעה והספוילר", () -> { expanded.add(item.id); rememberPosition(); if (data != null) display(data); }));
            } else { content.setText(rich(ReaderHtml.prepare(item.html, true))); card.addView(content); }
            LinearLayout footer = row();
            TextView date = text(item.timestamp > 0 ? DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(item.timestamp)) : "", 11, MUTED, false);
            footer.addView(date, new LinearLayout.LayoutParams(0, -2, 1));
            if (item.count > 0) footer.addView(text("♥ " + item.count, 12, TEAL, false)); card.addView(footer);
            Button web = button("הודעה באתר", () -> openBrowser(UrlPolicy.browser("/post/" + item.id))); web.setTextSize(12);
            web.setPadding(dp(9), 0, dp(9), 0); card.addView(web, new LinearLayout.LayoutParams(-2, dp(40)));
        }
    }

    private Spanned rich(String html) {
        Spanned cached = rendered.get(html); if (cached != null) return cached;
        String safe = html.replaceAll("(?is)<(script|style|iframe|object)\\b[^>]*>.*?</\\1\\s*>", "");
        Matcher images = Pattern.compile("(?is)<img\\b[^>]*>").matcher(safe); StringBuffer replaced = new StringBuffer();
        while (images.find()) {
            String tag = images.group(); String src = attribute(tag, "src"), alt = attribute(tag, "alt");
            String replacement;
            if (tag.contains("emoji") && !alt.isEmpty()) replacement = TextUtils.htmlEncode(alt);
            else {
                URI uri = UrlPolicy.browser(plain(src));
                replacement = uri == null ? " [תמונה] " : "<p><a href=\"" + TextUtils.htmlEncode(uri.toASCIIString()) + "\">תמונה מצורפת · פתיחה</a></p>";
            }
            images.appendReplacement(replaced, Matcher.quoteReplacement(replacement));
        }
        images.appendTail(replaced);
        SpannableStringBuilder text = new SpannableStringBuilder(Html.fromHtml(replaced.toString()));
        for (URLSpan old : text.getSpans(0, text.length(), URLSpan.class)) {
            int start = text.getSpanStart(old), end = text.getSpanEnd(old); String url = old.getURL(); text.removeSpan(old);
            if (UrlPolicy.browser(url) != null) text.setSpan(new ClickableSpan() {
                @Override public void onClick(View widget) { openLink(url, true); }
            }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        while (text.length() > 0 && Character.isWhitespace(text.charAt(text.length() - 1))) text.delete(text.length() - 1, text.length());
        rendered.put(html, text); return text;
    }
    private static String attribute(String tag, String key) {
        Matcher matcher = Pattern.compile("(?is)\\b" + key + "\\s*=\\s*(['\"])(.*?)\\1").matcher(tag);
        return matcher.find() ? matcher.group(2) : "";
    }
    private static String plain(String value) { return Html.fromHtml(value == null ? "" : value).toString().trim(); }
    private static String initial(String value) { return value.isEmpty() ? "מ" : value.substring(0, value.offsetByCodePoints(0, 1)); }
    private static int userColor(String color) { try { return Color.parseColor(color); } catch (Exception e) { return TEAL; } }
    private LinearLayout column() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private LinearLayout row() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.HORIZONTAL); return view; }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); view.setGravity(Gravity.START);
        view.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG); view.setIncludeFontPadding(false);
        view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL)); return view;
    }
    private Button button(String title, Runnable action) {
        Button button = new Button(this); button.setText(title); button.setAllCaps(false); button.setTextSize(14); button.setTextColor(TEAL);
        button.setMinWidth(0); button.setMinimumWidth(0); button.setMinHeight(0); button.setMinimumHeight(0); button.setPadding(dp(10), dp(5), dp(10), dp(5));
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, rounded(0xFFCCE8EC, dp(10), 0xFFB6D9DF));
        states.addState(new int[]{android.R.attr.state_focused}, rounded(0xFFCCE8EC, dp(10), TEAL));
        states.addState(new int[]{-android.R.attr.state_enabled}, rounded(0xFFE6ECEE, dp(10), 0xFFDCE5E8));
        states.addState(new int[]{}, rounded(0xFFF6FBFC, dp(10), 0xFFD6E6E9)); button.setBackground(states);
        button.setOnClickListener(v -> action.run()); return button;
    }
    private GradientDrawable rounded(int color, int radius, int border) {
        GradientDrawable result = new GradientDrawable(); result.setColor(color); result.setCornerRadius(radius); if (border != 0) result.setStroke(dp(1), border); return result;
    }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
}
