# 通用苹果 CMS 爬虫（csp_Maccms）

为 TVBox / CatVod 生态编写的通用爬虫，适配苹果 CMS（maccms v10 系）站点。

## 设计目标：零手写规则

- **分类**：从站点首页导航实时发现，分类增减 / ID 变化自动适应，不用再改配置
- **列表 / 详情 / 搜索 / 播放**：maccms 标准地址 + 多重选择器兜底，主题小改版一般不受影响
- **站点锁定分类页**（如需会员）时返回空列表，不崩溃

换源只需改站点 `ext` 的域名：

```json
{
  "key": "maccms_xiaoheimi",
  "name": "小宝影院",
  "type": 3,
  "api": "csp_Maccms",
  "searchable": 1,
  "quickSearch": 1,
  "filterable": 1,
  "ext": "https://xiaoheimi.cc"
}
```

`ext` 也支持 JSON：`{"host":"https://xiaoheimi.cc","ua":"自定义 UA"}`

## 构建

推送到 `main` 分支（改动 `spider/` 目录）或在 Actions 页手动触发，
`build-spider` 工作流会自动：javac 编译 → d8 打 DEX → 生成 `jar/maccms-spider.jar` 并提交回仓库。

本地构建：`bash spider/build.sh`（需要 JDK + Android build-tools 的 `d8` 在 PATH 中）。

## 文件

- `src/com/github/catvod/spider/Spider.java` —— TVBox 爬虫接口最小定义（仅方法签名）
- `src/com/github/catvod/spider/Maccms.java` —— 通用爬虫实现（TVBox 里 `api: csp_Maccms` 即对应此类）
- `build.sh` —— 构建脚本
