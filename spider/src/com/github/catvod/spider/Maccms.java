package com.github.catvod.spider;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * csp_Maccms —— 通用苹果 CMS（maccms v10 系）爬虫。
 *
 * 设计目标：零手写规则。
 *  - 分类：从站点首页导航实时发现（分类增减 / ID 变化自动适应）
 *  - 列表 / 详情 / 搜索 / 播放：maccms 标准地址 + 多重选择器兜底，主题小改版一般不受影响
 *  - 站点锁定分类页（如需会员）时返回空列表，不崩溃
 *
 * 站点配置示例：
 * {"key":"maccms_xiaoheimi","name":"小宝影院","type":3,"api":"csp_Maccms",
 *  "searchable":1,"quickSearch":1,"filterable":1,"ext":"https://xiaoheimi.cc"}
 * ext 也支持 JSON：{"host":"https://xiaoheimi.cc","ua":"自定义 UA"}
 */
public class Maccms extends com.github.catvod.crawler.Spider {

    private String host = "";
    private String ua = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    /** tid -> 首页导航发现的真实分类链接（保留站点原始 URL 形态，翻页时复用） */
    private final Map<String, String> cateUrls = new LinkedHashMap<>();
    /** vod_id -> 详情页真实链接（列表解析时记录，详情页 URL 形态变化时仍可用） */
    private final Map<String, String> detailUrls = new LinkedHashMap<>();

    /** 兜底分类：网站当前真实分类地址（2026-10-11 实测：导航已从 vod/show?id= 迁移到 vod/type/id/，
     * 旧地址已失效）。首页导航动态发现失败时使用，保证分类地址永远是网站实际地址。 */
    private static final String[][] FALLBACK_TYPES = {
            {"1", "电影片"}, {"2", "连续剧"}, {"3", "综艺片"}, {"4", "动漫片"}, {"21", "纪录片"}
    };

    // ---------------- 生命周期 ----------------

    @Override
    public void init(android.content.Context context, String extend) {
        String ext = extend == null ? "" : extend.trim();
        if (ext.startsWith("{")) {
            String h = jsonOpt(ext, "host");
            if (!h.isEmpty()) host = h;
            String u = jsonOpt(ext, "ua");
            if (!u.isEmpty()) ua = u;
        } else {
            host = ext;
        }
        host = host.replaceAll("/+$", "");
    }

    // ---------------- 首页 ----------------

    @Override
    public String homeContent(boolean filter) {
        Map<String, String> cates = new LinkedHashMap<>();
        try {
            Document doc = get(host + "/");
            if (!isBlocked(doc)) {
                for (Element a : doc.select("a[href*=vod/show], a[href*=vod/type]")) {
                    String href = a.attr("abs:href");
                    if (href.isEmpty()) continue;
                    String tid = extractTid(href);
                    String name = a.text().trim().replaceAll("\\s+", "");
                    if (tid.isEmpty() || name.isEmpty() || name.length() > 8 || cates.containsKey(tid)) continue;
                    cates.put(tid, name);
                    cateUrls.put(tid, href);
                }
            }
        } catch (Exception ignored) {
        }
        if (cates.isEmpty()) {
            // 动态发现失败：用网站当前真实分类地址兜底
            for (String[] t : FALLBACK_TYPES) {
                cates.put(t[0], t[1]);
                cateUrls.put(t[0], host + "/index.php/vod/type/id/" + t[0] + ".html");
            }
        }
        StringBuilder sb = new StringBuilder("{\"class\":[");
        boolean first = true;
        for (Map.Entry<String, String> e : cates.entrySet()) {
            if (!first) sb.append(',');
            sb.append("{\"type_id\":\"").append(esc(e.getKey()))
              .append("\",\"type_name\":\"").append(esc(e.getValue())).append("\"}");
            first = false;
        }
        return sb.append("],\"filters\":{}}").toString();
    }

    @Override
    public String homeVideoContent() {
        try {
            Document doc = get(host + "/");
            if (isBlocked(doc)) return "{\"list\":[]}";
            return vodListJson(dedupe(parseVodList(doc), 30));
        } catch (Exception e) {
            return "{\"list\":[]}";
        }
    }

    // ---------------- 分类 ----------------

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            Document doc = get(buildListUrl(tid, pg));
            if (isBlocked(doc)) return pageJson(pg, 1, new ArrayList<Vod>());
            List<Vod> vods = parseVodList(doc);
            int pageCount = parsePageCount(doc);
            int limit = Math.max(vods.size(), 1);
            return "{\"page\":" + safeNum(pg) + ",\"pagecount\":" + pageCount
                    + ",\"limit\":" + limit + ",\"total\":" + (pageCount * limit)
                    + ",\"list\":" + vodsJson(vods) + "}";
        } catch (Exception e) {
            return pageJson(pg, 1, new ArrayList<Vod>());
        }
    }

    private String pageJson(String pg, int pageCount, List<Vod> vods) {
        return "{\"page\":" + safeNum(pg) + ",\"pagecount\":" + pageCount
                + ",\"limit\":20,\"total\":0,\"list\":" + vodsJson(vods) + "}";
    }

    private String safeNum(String pg) {
        try {
            return String.valueOf(Integer.parseInt(pg));
        } catch (Exception e) {
            return "1";
        }
    }

    /** 优先复用首页导航发现的真实链接形态，兜底用 maccms 标准地址 */
    private String buildListUrl(String tid, String pg) {
        String known = cateUrls.get(tid);
        if (known != null && !known.isEmpty()) {
            if (known.contains("page=")) return known.replaceAll("([?&]page=)\\d*", "$1" + pg);
            if (Pattern.compile("/page/\\d+").matcher(known).find())
                return known.replaceAll("/page/\\d+", "/page/" + pg);
            if (known.endsWith(".html")) return known.replace(".html", "/page/" + pg + ".html");
            return known + (known.contains("?") ? "&" : "?") + "page=" + pg;
        }
        return host + "/index.php/vod/type/id/" + tid + "/page/" + pg + ".html";
    }

    // ---------------- 详情 ----------------

    @Override
    public String detailContent(List<String> ids) {
        String id = ids.get(0);
        try {
            String url = id.startsWith("http") ? id
                    : detailUrls.getOrDefault(id, host + "/index.php/vod/detail/id/" + id + ".html");
            Document doc = get(url);
            if (isBlocked(doc)) return "{\"list\":[]}";
            String vid = extractVid(url);
            if (vid.isEmpty()) vid = id;

            String name = firstText(doc, "h1.title, .myui-content__detail .title, .vodInfo h1, h1");
            String pic = firstImg(doc, ".myui-content__thumb img, .vodImg img, .detail-pic img");
            String remarks = firstText(doc, ".myui-content__detail .data, .vodInfo .data, .data");
            String content = firstText(doc, ".myui-content__detail .desc, .vodDesc, .desc, #desc");

            LinkedHashMap<String, List<String[]>> groups = new LinkedHashMap<>();
            Elements panels = doc.select(".myui-panel:has(a[href*=vod/play]), .stui-pannel:has(a[href*=vod/play]), .panel:has(a[href*=vod/play])");
            for (Element panel : panels) {
                Elements links = panel.select("a[href*=vod/play]");
                if (links.isEmpty()) continue;
                String from = firstText(panel, ".myui-panel_hd h3, .panel-heading h3, h3");
                if (from.isEmpty()) from = "线路" + (groups.size() + 1);
                groups.put(from, epList(links));
            }
            if (groups.isEmpty()) {
                Elements links = doc.select("a[href*=vod/play]");
                if (!links.isEmpty()) groups.put("默认线路", epList(links));
            }

            StringBuilder from = new StringBuilder(), purl = new StringBuilder();
            boolean gf = true;
            for (Map.Entry<String, List<String[]>> e : groups.entrySet()) {
                if (!gf) {
                    from.append("$$$");
                    purl.append("$$$");
                }
                from.append(esc(e.getKey()));
                boolean ef = true;
                for (String[] ep : e.getValue()) {
                    if (!ef) purl.append("#");
                    purl.append(esc(ep[0])).append("$").append(esc(ep[1]));
                    ef = false;
                }
                gf = false;
            }

            return "{\"list\":[{" + kv("vod_id", vid) + "," + kv("vod_name", name) + "," + kv("vod_pic", pic)
                    + "," + kv("vod_remarks", remarks) + "," + kv("vod_content", content)
                    + ",\"vod_play_from\":\"" + from + "\",\"vod_play_url\":\"" + purl + "\"}]}";
        } catch (Exception e) {
            return "{\"list\":[]}";
        }
    }

    private List<String[]> epList(Elements links) {
        List<String[]> eps = new ArrayList<>();
        for (Element a : links) {
            String t = a.text().trim();
            String href = a.attr("abs:href");
            if (href.isEmpty()) continue;
            eps.add(new String[]{t.isEmpty() ? "正片" : t, href});
        }
        return eps;
    }

    // ---------------- 搜索 ----------------

    @Override
    public String searchContent(String key, boolean quick) {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) {
        try {
            String wd = URLEncoder.encode(key, "UTF-8");
            String url = host + "/index.php/vod/search/wd/" + wd + ".html";
            if (!"1".equals(pg)) url = host + "/index.php/vod/search/wd/" + wd + "/page/" + pg + ".html";
            Document doc = get(url);
            if (isBlocked(doc)) return "{\"list\":[]}";
            List<Vod> vods = parseVodList(doc);
            if (vods.isEmpty()) vods = parseSearchList(doc);
            return vodListJson(vods);
        } catch (Exception e) {
            return "{\"list\":[]}";
        }
    }

    // ---------------- 播放 ----------------

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            Document doc = get(id);
            String video = extractVideoUrl(doc.html());
            if (!video.isEmpty()) return direct(video);
            Element iframe = doc.selectFirst("iframe[src]");
            if (iframe != null) {
                String src = iframe.attr("abs:src");
                if (!src.isEmpty()) {
                    try {
                        String v2 = extractVideoUrl(get(src).html());
                        if (!v2.isEmpty()) return direct(v2);
                    } catch (Exception ignored) {
                    }
                    return "{\"parse\":1,\"url\":\"" + esc(src) + "\"}";
                }
            }
        } catch (Exception ignored) {
        }
        return "{\"parse\":1,\"url\":\"" + esc(id) + "\"}";
    }

    private String direct(String video) {
        return "{\"parse\":0,\"url\":\"" + esc(video) + "\",\"header\":{\"User-Agent\":\"" + esc(ua) + "\"}}";
    }

    /** 从播放页 HTML 里抠直链（player_data JSON / 裸 m3u8 / mp4） */
    private String extractVideoUrl(String html) {
        Matcher m = Pattern.compile("\"url\"\\s*:\\s*\"((?:https?:)?//[^\"]+?\\.(?:m3u8|mp4)[^\"]*?)\"").matcher(html);
        if (m.find()) return unesc(m.group(1));
        m = Pattern.compile("((?:https?:)?//[^\"'\\s<>]+?\\.(?:m3u8|mp4)(?:\\?[^\"'\\s<>]*)?)").matcher(html);
        if (m.find()) return m.group(1);
        return "";
    }

    // ---------------- 列表解析（多重兜底） ----------------

    private static final String[] LIST_SELS = {
            ".myui-vodlist__thumb", ".myui-vodlist li", ".stui-vodlist__thumb", ".stui-vodlist li",
            ".vodlist_thumb", ".vodlist li", "ul[class*=vodlist] li", ".myui-vodlist__media"
    };

    private List<Vod> parseVodList(Document doc) {
        Elements items = new Elements();
        for (String sel : LIST_SELS) {
            items = doc.select(sel);
            if (!items.isEmpty()) break;
        }
        if (items.isEmpty()) items = doc.select("a[href*=vod/detail]");
        List<Vod> list = new ArrayList<>();
        for (Element it : items) {
            Element a = it.tagName().equals("a") ? it : it.selectFirst("a[href*=vod/detail]");
            if (a == null) a = it.selectFirst("a");
            if (a == null) continue;
            String href = a.attr("abs:href");
            if (href.isEmpty()) continue;
            String id = extractVid(href);
            if (id.isEmpty()) continue;
            String name = a.attr("title").trim();
            if (name.isEmpty()) {
                Element t = it.selectFirst("[title]");
                if (t != null) name = t.attr("title").trim();
            }
            if (name.isEmpty()) name = a.text().trim();
            if (name.isEmpty()) continue;
            String pic = "";
            Element img = it.selectFirst("img");
            if (img != null) {
                pic = img.attr("abs:data-original");
                if (pic.isEmpty()) pic = img.attr("abs:data-src");
                if (pic.isEmpty()) pic = img.attr("abs:src");
            }
            String remarks = "";
            Element rm = it.selectFirst(".text-right, .pic-text, [class*=remarks], .vod_remarks");
            if (rm != null) remarks = rm.text().trim();
            detailUrls.put(id, href);
            list.add(new Vod(id, name, pic, remarks));
        }
        return list;
    }

    /** 搜索页专用形态兜底 */
    private List<Vod> parseSearchList(Document doc) {
        List<Vod> list = new ArrayList<>();
        for (Element it : doc.select(".myui-vodlist__media, ul.searchlist li, .stui-vodlist__media")) {
            Element a = it.selectFirst("a[href*=vod/detail]");
            if (a == null) continue;
            String href = a.attr("abs:href");
            String id = extractVid(href);
            String name = firstText(it, "h4.title, .title, h4");
            if (id.isEmpty() || name.isEmpty()) continue;
            detailUrls.put(id, href);
            list.add(new Vod(id, name, firstImg(it, "img"), ""));
        }
        return list;
    }

    private int parsePageCount(Document doc) {
        int max = 1;
        try {
            for (Element a : doc.select(".myui-page a, .stui-page a, .page a, ul.pagination a")) {
                Matcher m = Pattern.compile("[?&/]page[/=](\\d+)").matcher(a.attr("href"));
                if (m.find()) {
                    try {
                        max = Math.max(max, Integer.parseInt(m.group(1)));
                    } catch (Exception ignored) {
                    }
                }
                try {
                    max = Math.max(max, Integer.parseInt(a.text().trim()));
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return max;
    }

    // ---------------- 基础工具 ----------------

    private Document get(String url) throws Exception {
        return Jsoup.connect(url)
                .userAgent(ua)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .timeout(15000)
                .followRedirects(true)
                .ignoreHttpErrors(true)
                .get();
    }

    /** 站点锁分类 / 需登录时 maccms 会返回"系统提示"页：识别出来返回空列表，不抛异常 */
    private boolean isBlocked(Document doc) {
        if (doc == null) return true;
        String title = doc.title();
        String body = doc.body() == null ? "" : doc.body().text();
        return title.contains("系统提示") || body.contains("升级会员") || body.contains("没有权限访问");
    }

    private String extractTid(String href) {
        String[] pats = {"[?&]id=(\\d+)", "/show/id/(\\d+)", "/type/id/(\\d+)", "/show/(\\d+)", "/type/(\\d+)"};
        for (String p : pats) {
            Matcher m = Pattern.compile(p).matcher(href);
            if (m.find()) return m.group(1);
        }
        return "";
    }

    private String extractVid(String href) {
        Matcher m = Pattern.compile("vod/detail/id/(\\d+)").matcher(href);
        if (m.find()) return m.group(1);
        m = Pattern.compile("[?&]id=(\\d+)").matcher(href);
        if (m.find()) return m.group(1);
        return "";
    }

    private String firstText(Element root, String selectors) {
        for (String sel : selectors.split("\\s*,\\s*")) {
            Element e = root.selectFirst(sel);
            if (e != null) {
                String t = e.text().trim();
                if (!t.isEmpty()) return t;
            }
        }
        return "";
    }

    private String firstImg(Element root, String selectors) {
        for (String sel : selectors.split("\\s*,\\s*")) {
            Element e = root.selectFirst(sel);
            if (e != null) {
                String u = e.attr("abs:data-original");
                if (u.isEmpty()) u = e.attr("abs:data-src");
                if (u.isEmpty()) u = e.attr("abs:src");
                if (!u.isEmpty()) return u;
            }
        }
        return "";
    }

    private List<Vod> dedupe(List<Vod> vods, int limit) {
        LinkedHashMap<String, Vod> map = new LinkedHashMap<>();
        for (Vod v : vods) map.putIfAbsent(v.id, v);
        List<Vod> out = new ArrayList<>(map.values());
        return out.size() > limit ? out.subList(0, limit) : out;
    }

    private String vodsJson(List<Vod> vods) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Vod v : vods) {
            if (!first) sb.append(',');
            sb.append("{").append(kv("vod_id", v.id)).append(",").append(kv("vod_name", v.name))
              .append(",").append(kv("vod_pic", v.pic)).append(",").append(kv("vod_remarks", v.remarks)).append("}");
            first = false;
        }
        return sb.append("]").toString();
    }

    private String vodListJson(List<Vod> vods) {
        return "{\"list\":" + vodsJson(vods) + "}";
    }

    private String kv(String k, String v) {
        return "\"" + k + "\":\"" + esc(v) + "\"";
    }

    private String esc(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 反转义播放页里常见的斜杠转义与 unicode 转义序列 */
    private String unesc(String s) {
        String r = s.replace("\\/", "/");
        Matcher m = Pattern.compile("\\\\u([0-9a-fA-F]{4})").matcher(r);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf((char) Integer.parseInt(m.group(1), 16))));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String jsonOpt(String json, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : "";
    }

    private static class Vod {
        final String id, name, pic, remarks;

        Vod(String id, String name, String pic, String remarks) {
            this.id = id;
            this.name = name;
            this.pic = pic;
            this.remarks = remarks;
        }
    }
}
