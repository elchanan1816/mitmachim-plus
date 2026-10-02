package community.mitmachim.lite;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.util.ArrayList;
import java.util.List;

/** Small immutable snapshots, independent of Activity and suitable for JVM testing. */
public final class ForumPage {
    public final List<Item> items = new ArrayList<>();
    public String title = "";
    public String redirect = "";
    public int page = 1;
    public int pages = 1;
    public boolean locked;

    public static final class Item {
        public static final int TOPIC = 0, CATEGORY = 1, POST = 2;
        public int kind, id, number, count;
        public String title = "", author = "", category = "", html = "", link = "", color = "#087F8C";
        public long timestamp;
    }

    public static ForumPage parse(String json, int requestedPage) throws JSONException {
        Object value = new JSONTokener(json).nextValue();
        ForumPage result = new ForumPage();
        result.page = Math.max(1, requestedPage);
        if (value instanceof String) {
            String destination = (String) value;
            java.net.URI uri = UrlPolicy.browser(destination);
            if (uri == null || !UrlPolicy.isForum(uri) || !(destination.startsWith("/") || destination.startsWith("https://"))) throw new JSONException("Invalid redirect");
            result.redirect = destination; return result;
        }
        if (!(value instanceof JSONObject)) throw new JSONException("Expected object");
        JSONObject root = (JSONObject) value;
        if (!root.has("topics") && !root.has("posts") && !root.has("categories") && !root.has("children")
                && !root.has("link")) throw new JSONException("Missing content");
        result.title = text(root, "titleRaw", text(root, "title", text(root, "name", "")));
        result.redirect = text(root, "link", "");
        result.locked = flag(root, "locked");
        JSONObject pagination = root.optJSONObject("pagination");
        if (pagination != null) {
            result.page = Math.max(1, pagination.optInt("currentPage", requestedPage));
            result.pages = Math.max(result.page, Math.min(100000, pagination.optInt("pageCount", result.page)));
        }
        JSONArray categories = root.optJSONArray("categories");
        if (categories == null) categories = root.optJSONArray("children");
        if (categories != null) for (int i = 0; i < Math.min(250, categories.length()); i++) {
            JSONObject obj = categories.optJSONObject(i);
            if (obj == null || obj.optInt("cid", 0) <= 0 || flag(obj, "disabled")) continue;
            Item item = new Item();
            item.kind = Item.CATEGORY; item.id = obj.optInt("cid");
            item.title = text(obj, "name", "קטגוריה"); item.link = text(obj, "link", "");
            item.count = obj.optInt("totalTopicCount", obj.optInt("topic_count", 0));
            result.items.add(item);
        }
        JSONArray topics = root.optJSONArray("topics");
        if (topics != null) for (int i = 0; i < Math.min(250, topics.length()); i++) {
            JSONObject obj = topics.optJSONObject(i);
            if (obj == null || obj.optInt("tid", 0) <= 0 || flag(obj, "deleted")) continue;
            Item item = new Item(); item.kind = Item.TOPIC; item.id = obj.optInt("tid");
            item.title = text(obj, "titleRaw", text(obj, "title", "דיון"));
            item.count = Math.max(0, obj.optInt("postcount", 1) - 1);
            item.timestamp = obj.optLong("lastposttime", obj.optLong("timestamp"));
            JSONObject category = obj.optJSONObject("category");
            item.category = category == null ? "" : text(category, "name", "");
            setUser(item, obj.optJSONObject("user")); result.items.add(item);
        }
        JSONArray posts = root.optJSONArray("posts");
        if (posts != null) for (int i = 0; i < Math.min(250, posts.length()); i++) {
            JSONObject obj = posts.optJSONObject(i);
            if (obj == null || obj.optInt("pid", 0) <= 0 || flag(obj, "deleted")) continue;
            Item item = new Item(); item.kind = Item.POST; item.id = obj.optInt("pid");
            item.number = obj.optInt("index", i) + 1; item.html = text(obj, "content", "");
            if (item.html.length() > 120000) item.html = "<p>הודעה זו ארוכה במיוחד. ניתן לקרוא אותה באתר.</p>";
            item.timestamp = obj.optLong("timestamp"); item.count = obj.optInt("votes", 0);
            setUser(item, obj.optJSONObject("user")); result.items.add(item);
        }
        return result;
    }

    private static void setUser(Item item, JSONObject user) {
        if (user == null) { item.author = "משתמש"; return; }
        item.author = text(user, "displayname", text(user, "username", "משתמש"));
        item.color = text(user, "icon:bgColor", "#087F8C");
    }
    private static boolean flag(JSONObject obj, String key) { return obj.optBoolean(key) || obj.optInt(key) != 0; }
    private static String text(JSONObject obj, String key, String fallback) {
        return obj.isNull(key) ? fallback : obj.optString(key, fallback);
    }
}
