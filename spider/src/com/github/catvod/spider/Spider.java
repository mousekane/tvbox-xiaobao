package com.github.catvod.spider;

import android.content.Context;
import java.util.HashMap;
import java.util.List;

/**
 * TVBox / CatVod 爬虫接口的最小定义。
 * TVBox 通过反射加载 com.github.catvod.spider.<Api> 并调用这些方法，
 * 因此只需要方法签名与官方一致即可。
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

    public boolean manualVideoCheck() {
        return false;
    }

    public void destroy() {
    }
}
