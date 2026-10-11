package com.github.catvod.crawler;

import android.content.Context;
import java.util.HashMap;
import java.util.List;

/**
 * 宿主 App（TVBox / 影视仓）内置的爬虫基类，仅用于编译期。
 * 打包时不要带入 dex，运行时由宿主提供。
 * 自定义爬虫必须继承这个类（注意包名是 crawler，不是 spider），
 * 否则宿主做类型转换时会失败，爬虫无法实例化。
 */
public abstract class Spider {

    public void init(Context context, String extend) throws Exception {
    }

    public void init(Context context) throws Exception {
    }

    public String homeContent(boolean filter) throws Exception {
        return "";
    }

    public String homeVideoContent() throws Exception {
        return "";
    }

    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        return "";
    }

    public String detailContent(List<String> ids) throws Exception {
        return "";
    }

    public String searchContent(String key, boolean quick) throws Exception {
        return "";
    }

    public String searchContent(String key, boolean quick, String pg) throws Exception {
        return "";
    }

    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        return "";
    }
}
