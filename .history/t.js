// Twitter (X) AdBlock Script for Shadowrocket
// 作用：剔除 GraphQL 时间轴、搜索结果、通知中的推广内容

if ($response.body) {
    let body = $response.body;
    try {
        let obj = JSON.parse(body);
        
        // 1. 处理 GraphQL 架构 (Timeline 和搜索)
        if (obj.data) {
            // 递归查找并删除包含 "promoted" 的 entry
            filterTwitterObject(obj);
        } 
        // 2. 处理传统 1.1 架构
        else if (obj.modules || obj.instructions) {
            filterClassicTwitter(obj);
        }

        $done({ body: JSON.stringify(obj) });
    } catch (e) {
        // 如果 JSON 解析失败（可能是二进制数据），直接返回原内容
        $done({});
    }
} else {
    $done({});
}

// 递归过滤函数
function filterTwitterObject(o) {
    if (Array.isArray(o)) {
        for (let i = o.length - 1; i >= 0; i--) {
            if (isPromoted(o[i])) {
                o.splice(i, 1);
            } else {
                filterTwitterObject(o[i]);
            }
        }
    } else if (typeof o === 'object' && o !== null) {
        for (let key in o) {
            if (isPromoted(o[key])) {
                delete o[key];
            } else {
                filterTwitterObject(o[key]);
            }
        }
    }
}

// 判定是否为推广内容
function isPromoted(item) {
    if (!item) return false;
    // 检查常见的推广标记位
    const isAd = item.promotedMetadata || 
                 item.adsMetadata || 
                 (item.content && item.content.promotedMetadata) ||
                 (item.item && item.item.promotedMetadata);
    return isAd;
}

// 传统接口过滤
function filterClassicTwitter(obj) {
    if (obj.instructions) {
        obj.instructions.forEach(ins => {
            if (ins.addEntries) {
                ins.addEntries.entries = ins.addEntries.entries.filter(e => !e.content?.item?.content?.tweet?.promotedMetadata);
            }
        });
    }
}